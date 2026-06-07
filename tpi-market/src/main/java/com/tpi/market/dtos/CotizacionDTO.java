package com.tpi.market.dtos;

import java.math.BigDecimal;

/**
 * Cotización de una acción.
 * - precio/moneda: valor en la moneda original de la cotización (ej. USD).
 * - precioArs: el mismo valor convertido a Pesos Argentinos.
 * - fuente: de dónde se obtuvo (API externa o mock de respaldo).
 */
public record CotizacionDTO(
        String simbolo,
        BigDecimal precio,
        String moneda,
        BigDecimal precioArs,
        String fuente
) {}
