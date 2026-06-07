package com.tpi.history.repositories;

import com.tpi.history.models.Operacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OperacionRepository extends JpaRepository<Operacion, Long> {

    /** Todas las operaciones de un usuario (como titular o como contraparte), por fecha. */
    List<Operacion> findByUsuarioIdOrContraparteIdOrderByFechaDesc(Long usuarioId, Long contraparteId);

    /** Todas las operaciones de un tipo (ej. TRADE) ordenadas por fecha. */
    List<Operacion> findByTipoOrderByFechaDesc(String tipo);
}
