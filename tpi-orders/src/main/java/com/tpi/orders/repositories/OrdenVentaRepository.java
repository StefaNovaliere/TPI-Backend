package com.tpi.orders.repositories;

import com.tpi.orders.models.EstadoOrden;
import com.tpi.orders.models.OrdenVenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface OrdenVentaRepository extends JpaRepository<OrdenVenta, Long> {

    List<OrdenVenta> findByUsuarioIdOrderByFechaCreacionDesc(Long usuarioId);

    /**
     * Órdenes de venta compatibles con una compra: mismo símbolo, todavía con
     * acciones disponibles y con precio mínimo <= precio máximo del comprador.
     * Se ordenan por mejor precio para el comprador (más barato primero) y luego
     * por antigüedad (prioridad precio-tiempo).
     */
    List<OrdenVenta> findBySimboloAndEstadoAndPrecioMinArsLessThanEqualOrderByPrecioMinArsAscFechaCreacionAsc(
            String simbolo, EstadoOrden estado, BigDecimal precioMaxArs);
}
