package com.tpi.history.services;

import com.tpi.history.dtos.NuevaOperacionDTO;
import com.tpi.history.models.Operacion;
import com.tpi.history.repositories.OperacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class HistoryService {

    private final OperacionRepository repository;

    public HistoryService(OperacionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Operacion registrar(NuevaOperacionDTO dto) {
        Operacion op = new Operacion();
        op.setTipo(dto.tipo());
        op.setUsuarioId(dto.usuarioId());
        op.setContraparteId(dto.contraparteId());
        op.setSimbolo(dto.simbolo());
        op.setCantidad(dto.cantidad());
        op.setPrecioUnitarioArs(dto.precioUnitarioArs());
        op.setTotalArs(dto.totalArs());
        op.setEstado(dto.estado());
        op.setFecha(Instant.now());
        return repository.save(op);
    }

    /** Historial completo de un usuario, ordenado por fecha (Requerimiento Funcional 5). */
    @Transactional(readOnly = true)
    public List<Operacion> historialUsuario(Long usuarioId) {
        return repository.findByUsuarioIdOrContraparteIdOrderByFechaDesc(usuarioId, usuarioId);
    }

    /** Todas las transacciones (trades) ejecutadas (Requerimiento Funcional 6, ADMIN). */
    @Transactional(readOnly = true)
    public List<Operacion> todasLasTransacciones() {
        return repository.findByTipoOrderByFechaDesc("TRADE");
    }
}
