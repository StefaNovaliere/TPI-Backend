package com.tpi.orders.services;

import com.tpi.orders.clients.HistoryClient;
import com.tpi.orders.clients.UsersClient;
import com.tpi.orders.dtos.*;
import com.tpi.orders.models.EstadoOrden;
import com.tpi.orders.models.OrdenCompra;
import com.tpi.orders.models.OrdenVenta;
import com.tpi.orders.models.Trade;
import com.tpi.orders.repositories.OrdenCompraRepository;
import com.tpi.orders.repositories.OrdenVentaRepository;
import com.tpi.orders.repositories.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrdenCompraRepository compraRepository;
    private final OrdenVentaRepository ventaRepository;
    private final TradeRepository tradeRepository;
    private final UsersClient usersClient;
    private final HistoryClient historyClient;

    public OrderService(OrdenCompraRepository compraRepository,
                        OrdenVentaRepository ventaRepository,
                        TradeRepository tradeRepository,
                        UsersClient usersClient,
                        HistoryClient historyClient) {
        this.compraRepository = compraRepository;
        this.ventaRepository = ventaRepository;
        this.tradeRepository = tradeRepository;
        this.usersClient = usersClient;
        this.historyClient = historyClient;
    }

    /** Registra una orden de venta, dejándola disponible para emparejar. */
    @Transactional
    public OrdenVenta registrarVenta(NuevaOrdenVentaDTO dto) {
        OrdenVenta venta = new OrdenVenta();
        venta.setUsuarioId(dto.usuarioId());
        venta.setSimbolo(dto.simbolo().toUpperCase());
        venta.setCantidad(dto.cantidad());
        venta.setCantidadRestante(dto.cantidad());
        venta.setPrecioMinArs(dto.precioMinArs());
        venta.setEstado(EstadoOrden.ABIERTA);
        venta.setFechaCreacion(Instant.now());
        venta = ventaRepository.save(venta);

        historyClient.registrar(new OperacionHistorialDTO(
                "ORDEN_VENTA", venta.getUsuarioId(), null, venta.getSimbolo(),
                venta.getCantidad(), venta.getPrecioMinArs(), null, venta.getEstado().name()));
        return venta;
    }

    /**
     * Registra una orden de compra y ejecuta el MOTOR DE EMPAREJAMIENTO de forma
     * inmediata (Especificación del motor 1). Busca órdenes de venta compatibles,
     * ejecuta las operaciones (que liquidan dinero y acciones de forma atómica en
     * el microservicio de usuarios) y devuelve el resultado.
     */
    @Transactional
    public ResultadoCompraDTO procesarCompra(NuevaOrdenCompraDTO dto) {
        String simbolo = dto.simbolo().toUpperCase();

        OrdenCompra compra = new OrdenCompra();
        compra.setEstado(EstadoOrden.ABIERTA);
        compra.setUsuarioId(dto.usuarioId());
        compra.setSimbolo(simbolo);
        compra.setCantidad(dto.cantidad());
        compra.setPrecioMaxArs(dto.precioMaxArs());
        compra.setCantidadComprada(0);
        compra.setFechaCreacion(Instant.now());
        compra = compraRepository.save(compra);

        // Órdenes de venta compatibles (mismo símbolo, precio <= máximo, abiertas),
        // priorizadas por mejor precio y antigüedad.
        List<OrdenVenta> candidatas = ventaRepository
                .findBySimboloAndEstadoAndPrecioMinArsLessThanEqualOrderByPrecioMinArsAscFechaCreacionAsc(
                        simbolo, EstadoOrden.ABIERTA, dto.precioMaxArs());

        int pendiente = dto.cantidad();
        BigDecimal totalGastado = BigDecimal.ZERO;
        List<TradeDTO> operaciones = new ArrayList<>();

        for (OrdenVenta venta : candidatas) {
            if (pendiente == 0) break;
            if (venta.getUsuarioId().equals(dto.usuarioId())) continue; // no auto-emparejar

            int cantidadFill = Math.min(pendiente, venta.getCantidadRestante());
            // El precio de ejecución es el de la orden de venta (la que estaba "en el libro").
            BigDecimal precioEjecucion = venta.getPrecioMinArs();
            BigDecimal totalFill = precioEjecucion.multiply(BigDecimal.valueOf(cantidadFill));

            try {
                // Liquidación atómica en el microservicio de usuarios (transacción).
                usersClient.liquidar(new SettlementRequest(
                        dto.usuarioId(), venta.getUsuarioId(), simbolo, cantidadFill, totalFill));
            } catch (RestClientResponseException e) {
                // No se pudo liquidar (ej. saldo insuficiente): se detiene el emparejamiento.
                log.warn("Liquidación rechazada (orden venta {}): {}", venta.getId(), e.getStatusText());
                break;
            }

            // Actualizamos la orden de venta.
            venta.setCantidadRestante(venta.getCantidadRestante() - cantidadFill);
            venta.setEstado(venta.getCantidadRestante() == 0 ? EstadoOrden.COMPLETADA : EstadoOrden.ABIERTA);
            ventaRepository.save(venta);

            // Registramos el trade.
            Trade trade = new Trade();
            trade.setCompradorId(dto.usuarioId());
            trade.setVendedorId(venta.getUsuarioId());
            trade.setSimbolo(simbolo);
            trade.setCantidad(cantidadFill);
            trade.setPrecioUnitarioArs(precioEjecucion);
            trade.setTotalArs(totalFill);
            trade.setOrdenCompraId(compra.getId());
            trade.setOrdenVentaId(venta.getId());
            trade.setFecha(Instant.now());
            tradeRepository.save(trade);

            operaciones.add(new TradeDTO(trade.getCompradorId(), trade.getVendedorId(), simbolo,
                    cantidadFill, precioEjecucion, totalFill, venta.getId(), trade.getFecha()));

            // Historial del trade (visible para comprador y, por contraparte, para el vendedor).
            historyClient.registrar(new OperacionHistorialDTO(
                    "TRADE", dto.usuarioId(), venta.getUsuarioId(), simbolo,
                    cantidadFill, precioEjecucion, totalFill, "EJECUTADA"));

            pendiente -= cantidadFill;
            totalGastado = totalGastado.add(totalFill);
        }

        int comprado = dto.cantidad() - pendiente;
        compra.setCantidadComprada(comprado);
        if (comprado == 0) {
            compra.setEstado(EstadoOrden.RECHAZADA);
        } else if (pendiente == 0) {
            compra.setEstado(EstadoOrden.COMPLETADA);
        } else {
            compra.setEstado(EstadoOrden.PARCIAL);
        }
        compraRepository.save(compra);

        // Historial de la orden de compra.
        historyClient.registrar(new OperacionHistorialDTO(
                "ORDEN_COMPRA", dto.usuarioId(), null, simbolo,
                dto.cantidad(), dto.precioMaxArs(), totalGastado, compra.getEstado().name()));

        return new ResultadoCompraDTO(compra.getId(), compra.getEstado().name(), simbolo,
                dto.cantidad(), comprado, totalGastado, operaciones);
    }

    @Transactional(readOnly = true)
    public List<OrdenCompra> comprasDeUsuario(Long usuarioId) {
        return compraRepository.findByUsuarioIdOrderByFechaCreacionDesc(usuarioId);
    }

    @Transactional(readOnly = true)
    public List<OrdenVenta> ventasDeUsuario(Long usuarioId) {
        return ventaRepository.findByUsuarioIdOrderByFechaCreacionDesc(usuarioId);
    }
}
