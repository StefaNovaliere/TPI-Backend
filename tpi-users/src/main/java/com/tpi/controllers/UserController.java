package com.tpi.users.controllers;

import com.tpi.users.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/users") // Versionado según apunte 14
public class UserController {

    @Autowired
    private UserService userService;

    // Endpoint para ingresar dinero (Consigna 1)
    @PostMapping("/{id}/deposit")
    public ResponseEntity<String> deposit(@PathVariable Long id, @RequestParam BigDecimal amount) {
        userService.ingresarDinero(id, amount);
        return ResponseEntity.ok("Ingreso exitoso. Nuevo saldo actualizado.");
    }

    // Endpoint para consultar el portfolio (Consigna 3.2)
    @GetMapping("/{id}/portfolio")
    public ResponseEntity<?> getPortfolio(@PathVariable Long id) {
        // Lógica para devolver el DTO del portfolio
        return ResponseEntity.ok("Datos del portfolio");
    }
}
