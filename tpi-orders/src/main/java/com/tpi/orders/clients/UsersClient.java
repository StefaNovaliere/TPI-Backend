package com.tpi.orders.clients;

import com.tpi.orders.dtos.SettlementRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Cliente REST hacia el microservicio de usuarios. */
@Component
public class UsersClient {

    private final RestClient restClient;

    public UsersClient(@Value("${services.users-uri}") String usersUri) {
        this.restClient = RestClient.builder().baseUrl(usersUri).build();
    }

    /**
     * Solicita la liquidación atómica de una operación (mover dinero y acciones).
     * Lanza una excepción si la liquidación no es posible (ej. saldo insuficiente),
     * lo que detiene el emparejamiento.
     */
    public void liquidar(SettlementRequest req) {
        restClient.post()
                .uri("/api/v1/users/settlement")
                .header(HttpHeaders.AUTHORIZATION, AuthForwarding.currentBearerToken())
                .body(req)
                .retrieve()
                .toBodilessEntity();
    }
}
