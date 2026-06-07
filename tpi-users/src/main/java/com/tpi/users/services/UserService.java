package com.tpi.users.services;

import com.tpi.users.dtos.HoldingDTO;
import com.tpi.users.dtos.PortfolioDTO;
import com.tpi.users.dtos.SettlementRequest;
import com.tpi.users.models.Cuenta;
import com.tpi.users.models.Portafolio;
import com.tpi.users.models.Usuario;
import com.tpi.users.repositories.CuentaRepository;
import com.tpi.users.repositories.PortafolioRepository;
import com.tpi.users.repositories.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class UserService {

    private final UsuarioRepository usuarioRepository;
    private final CuentaRepository cuentaRepository;
    private final PortafolioRepository portafolioRepository;

    public UserService(UsuarioRepository usuarioRepository,
                       CuentaRepository cuentaRepository,
                       PortafolioRepository portafolioRepository) {
        this.usuarioRepository = usuarioRepository;
        this.cuentaRepository = cuentaRepository;
        this.portafolioRepository = portafolioRepository;
    }

    public Usuario getByUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Usuario no encontrado: " + username));
    }

    /** Ingreso de dinero (ARS). Los usuarios tienen "dinero infinito". */
    @Transactional
    public BigDecimal ingresarDinero(Long usuarioId, BigDecimal monto) {
        Cuenta cuenta = cuentaRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cuenta no encontrada para el usuario " + usuarioId));
        cuenta.setSaldoArs(cuenta.getSaldoArs().add(monto));
        cuentaRepository.save(cuenta);
        return cuenta.getSaldoArs();
    }

    @Transactional(readOnly = true)
    public PortfolioDTO getPortfolio(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Usuario no encontrado: " + usuarioId));
        Cuenta cuenta = cuentaRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cuenta no encontrada para el usuario " + usuarioId));
        List<HoldingDTO> tenencias = portafolioRepository.findByUsuarioId(usuarioId).stream()
                .map(p -> new HoldingDTO(p.getSimbolo(), p.getCantidad()))
                .toList();
        return new PortfolioDTO(usuario.getId(), usuario.getUsername(), cuenta.getSaldoArs(), tenencias);
    }

    /**
     * Liquida una operación de compra-venta de forma ATÓMICA (Consideración 7).
     * Debita al comprador, acredita al vendedor y transfiere las acciones.
     * Si algo no se cumple (saldo o tenencias insuficientes), se lanza una
     * excepción y la transacción hace rollback completo.
     */
    @Transactional
    public void liquidar(SettlementRequest req) {
        Cuenta cuentaComprador = cuentaRepository.findByUsuarioId(req.compradorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cuenta del comprador no encontrada"));
        Cuenta cuentaVendedor = cuentaRepository.findByUsuarioId(req.vendedorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cuenta del vendedor no encontrada"));

        // 1) El comprador debe tener saldo suficiente (en ARS).
        if (cuentaComprador.getSaldoArs().compareTo(req.totalArs()) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Saldo insuficiente del comprador");
        }

        // 2) El vendedor debe poseer las acciones que vende.
        Portafolio tenenciaVendedor = portafolioRepository
                .findByUsuarioIdAndSimbolo(req.vendedorId(), req.simbolo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "El vendedor no posee acciones de " + req.simbolo()));
        if (tenenciaVendedor.getCantidad() < req.cantidad()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El vendedor no posee suficientes acciones de " + req.simbolo());
        }

        // 3) Movimiento de dinero.
        cuentaComprador.setSaldoArs(cuentaComprador.getSaldoArs().subtract(req.totalArs()));
        cuentaVendedor.setSaldoArs(cuentaVendedor.getSaldoArs().add(req.totalArs()));
        cuentaRepository.save(cuentaComprador);
        cuentaRepository.save(cuentaVendedor);

        // 4) Movimiento de acciones: se descuentan del vendedor.
        tenenciaVendedor.setCantidad(tenenciaVendedor.getCantidad() - req.cantidad());
        if (tenenciaVendedor.getCantidad() == 0) {
            portafolioRepository.delete(tenenciaVendedor);
        } else {
            portafolioRepository.save(tenenciaVendedor);
        }

        // 5) ... y se acreditan al comprador (creando la tenencia si no existía).
        Portafolio tenenciaComprador = portafolioRepository
                .findByUsuarioIdAndSimbolo(req.compradorId(), req.simbolo())
                .orElseGet(() -> {
                    Portafolio p = new Portafolio();
                    p.setUsuarioId(req.compradorId());
                    p.setSimbolo(req.simbolo());
                    p.setCantidad(0);
                    return p;
                });
        tenenciaComprador.setCantidad(tenenciaComprador.getCantidad() + req.cantidad());
        portafolioRepository.save(tenenciaComprador);
    }
}
