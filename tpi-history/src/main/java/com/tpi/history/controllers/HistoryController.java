package com.tpi.history.controllers;

import com.tpi.history.dtos.NuevaOperacionDTO;
import com.tpi.history.models.Operacion;
import com.tpi.history.services.HistoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    /** Endpoint interno: registra una operación (lo llama el servicio de órdenes). */
    @PostMapping("/operations")
    public ResponseEntity<Operacion> registrar(@Valid @RequestBody NuevaOperacionDTO dto) {
        return ResponseEntity.ok(historyService.registrar(dto));
    }

    /** Historial completo de un usuario, ordenado por fecha (Req. Funcional 5). */
    @GetMapping("/users/{usuarioId}")
    public List<Operacion> historialUsuario(@PathVariable Long usuarioId) {
        return historyService.historialUsuario(usuarioId);
    }

    /** Historial completo de transacciones — sólo ADMIN (Req. Funcional 6). */
    @GetMapping("/transactions")
    public List<Operacion> todasLasTransacciones() {
        return historyService.todasLasTransacciones();
    }
}
