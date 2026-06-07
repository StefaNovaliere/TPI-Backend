package com.tpi.orders.clients;

import com.tpi.orders.dtos.OperacionHistorialDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Cliente REST hacia el microservicio de historial. */
@Component
public class HistoryClient {

    private static final Logger log = LoggerFactory.getLogger(HistoryClient.class);

    private final RestClient restClient;

    public HistoryClient(@Value("${services.history-uri}") String historyUri) {
        this.restClient = RestClient.builder().baseUrl(historyUri).build();
    }

    /**
     * Registra una operación en el historial. El registro es "best-effort":
     * si el servicio de historial falla, no se aborta la operación de compra.
     */
    public void registrar(OperacionHistorialDTO operacion) {
        try {
            restClient.post()
                    .uri("/api/v1/history/operations")
                    .header(HttpHeaders.AUTHORIZATION, AuthForwarding.currentBearerToken())
                    .body(operacion)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("No se pudo registrar la operación en el historial: {}", e.getMessage());
        }
    }
}
