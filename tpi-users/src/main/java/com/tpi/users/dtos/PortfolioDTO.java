package com.tpi.users.dtos;

import java.math.BigDecimal;
import java.util.List;

/** Vista del portfolio y saldo de un usuario (Requerimiento Funcional 2). */
public record PortfolioDTO(
        Long usuarioId,
        String username,
        BigDecimal saldoArs,
        List<HoldingDTO> tenencias
) {}
