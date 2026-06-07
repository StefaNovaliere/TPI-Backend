package com.tpi.users.controllers;

import com.tpi.users.dtos.DepositRequest;
import com.tpi.users.dtos.PortfolioDTO;
import com.tpi.users.dtos.SettlementRequest;
import com.tpi.users.models.Usuario;
import com.tpi.users.services.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users") // Versionado de API
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** Datos del usuario autenticado (resuelto desde el JWT de Keycloak). */
    @GetMapping("/me")
    public PortfolioDTO me(@AuthenticationPrincipal Jwt jwt) {
        Usuario usuario = userService.getByUsername(jwt.getClaimAsString("preferred_username"));
        return userService.getPortfolio(usuario.getId());
    }

    /** Ingreso de dinero (ARS) a la cuenta (Requerimiento implícito de saldo). */
    @PostMapping("/{id}/deposit")
    public ResponseEntity<Map<String, Object>> deposit(@PathVariable Long id,
                                                        @Valid @RequestBody DepositRequest request) {
        BigDecimal nuevoSaldo = userService.ingresarDinero(id, request.amount());
        return ResponseEntity.ok(Map.of(
                "usuarioId", id,
                "saldoArs", nuevoSaldo,
                "mensaje", "Ingreso exitoso"));
    }

    /** Consulta del portfolio y saldo de un usuario (Requerimiento Funcional 2). */
    @GetMapping("/{id}/portfolio")
    public PortfolioDTO getPortfolio(@PathVariable Long id) {
        return userService.getPortfolio(id);
    }

    /**
     * Endpoint interno usado por el microservicio de órdenes para liquidar una
     * operación de compra-venta de forma atómica.
     */
    @PostMapping("/settlement")
    public ResponseEntity<Void> settle(@Valid @RequestBody SettlementRequest request) {
        userService.liquidar(request);
        return ResponseEntity.ok().build();
    }
}
