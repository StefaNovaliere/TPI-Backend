package com.tpi.orders.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * Respuesta inmediata a una orden de compra (Especificación del motor 1):
 * indica si se aceptó/rechazó, cuánto se compró y a qué precio total.
 */
public record ResultadoCompraDTO(
        Long ordenCompraId,
        String estado,
        String simbolo,
        Integer cantidadSolicitada,
        Integer cantidadComprada,
        BigDecimal totalGastadoArs,
        List<TradeDTO> operaciones
) {}
