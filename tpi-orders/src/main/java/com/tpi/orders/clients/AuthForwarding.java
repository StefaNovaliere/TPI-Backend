package com.tpi.orders.clients;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Utilidad para reenviar el token Bearer del usuario a otros microservicios. */
final class AuthForwarding {

    private AuthForwarding() {}

    static String currentBearerToken() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getHeader("Authorization");
        }
        return null;
    }
}
