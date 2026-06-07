package com.tpi.market.services;

import com.tpi.market.dtos.CotizacionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Obtiene cotizaciones desde una API externa (Stooq) y las convierte a ARS.
 * Si la API externa no responde, usa una tabla de precios de respaldo (mock)
 * para que la demostración funcione siempre.
 */
@Service
public class MarketService {

    private static final Logger log = LoggerFactory.getLogger(MarketService.class);

    // Precios de respaldo (en USD) por si la API externa no está disponible.
    private static final Map<String, BigDecimal> PRECIOS_MOCK = Map.of(
            "NVDA", new BigDecimal("122.50"),
            "AAPL", new BigDecimal("210.30"),
            "MSFT", new BigDecimal("430.10"),
            "GOOGL", new BigDecimal("178.20"),
            "AMZN", new BigDecimal("185.00"),
            "TSLA", new BigDecimal("250.75")
    );

    private final String externalUrl;
    private final BigDecimal usdArsRate;
    private final RestClient restClient = RestClient.create();

    public MarketService(@Value("${market.external-url}") String externalUrl,
                         @Value("${market.usd-ars-rate}") BigDecimal usdArsRate) {
        this.externalUrl = externalUrl;
        this.usdArsRate = usdArsRate;
    }

    public CotizacionDTO obtenerCotizacion(String simbolo) {
        String sym = simbolo.toUpperCase();
        BigDecimal precioUsd = consultarApiExterna(sym);
        String fuente = "API externa (Stooq)";

        if (precioUsd == null) {
            precioUsd = PRECIOS_MOCK.get(sym);
            fuente = "mock de respaldo";
        }
        if (precioUsd == null) {
            return null; // Símbolo desconocido.
        }

        BigDecimal precioArs = precioUsd.multiply(usdArsRate).setScale(2, RoundingMode.HALF_UP);
        return new CotizacionDTO(sym, precioUsd, "USD", precioArs, fuente);
    }

    /** Consulta la API externa. Devuelve el precio en USD o null si falla. */
    private BigDecimal consultarApiExterna(String simbolo) {
        try {
            String url = externalUrl.replace("{symbol}", simbolo.toLowerCase());
            String csv = restClient.get().uri(url).retrieve().body(String.class);
            if (csv == null) return null;
            // CSV: Symbol,Date,Time,Open,High,Low,Close,Volume
            String[] lineas = csv.strip().split("\n");
            if (lineas.length < 2) return null;
            String[] cols = lineas[1].split(",");
            if (cols.length < 7 || cols[6].equalsIgnoreCase("N/D")) return null;
            return new BigDecimal(cols[6].trim()); // columna Close
        } catch (Exception e) {
            log.warn("No se pudo consultar la API externa para {}: {}", simbolo, e.getMessage());
            return null;
        }
    }
}
