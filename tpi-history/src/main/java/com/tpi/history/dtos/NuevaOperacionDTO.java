package com.tpi.history.dtos;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Operación entrante a registrar (la envía el microservicio de órdenes). */
public record NuevaOperacionDTO(
        @NotNull String tipo,
        @NotNull Long usuarioId,
        Long contraparteId,
        String simbolo,
        Integer cantidad,
        BigDecimal precioUnitarioArs,
        BigDecimal totalArs,
        String estado
) {}
