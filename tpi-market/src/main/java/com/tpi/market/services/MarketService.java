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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
            "TSLA", new BigDecimal("250.75"),
            "ABNB", new BigDecimal("145.75")
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
        String fuente = "API externa (Yahoo)";

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
            // Yahoo prefiere minúsculas, pero el símbolo debe ser exacto (ej: aapl)
            String url = externalUrl.replace("{symbol}", simbolo.toLowerCase());
            log.info("Consultando Yahoo Finance: {}", url);

            // 1. Es VITAL el User-Agent para evitar el error 429
            String responseBody = restClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(String.class);

            if (responseBody == null) return null;

            // 2. Yahoo devuelve un JSON complejo. Vamos a navegarlo:
            // Estructura: chart -> result[0] -> meta -> regularMarketPrice
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);

            JsonNode result = root.path("chart").path("result").get(0);
            if (result != null && result.has("meta")) {
                double precio = result.path("meta").path("regularMarketPrice").asDouble();
                log.info("Precio obtenido para {}: {}", simbolo, precio);
                return BigDecimal.valueOf(precio);
            }

            return null;
        } catch (Exception e) {
            log.warn("No se pudo consultar Yahoo Finance para {}: {}", simbolo, e.getMessage());
            return null;
        }
    }


}
