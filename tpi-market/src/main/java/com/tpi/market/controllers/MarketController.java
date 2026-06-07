package com.tpi.market.controllers;

import com.tpi.market.dtos.CotizacionDTO;
import com.tpi.market.services.MarketService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/quotes")
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    /**
     * Consulta pública de la cotización de una acción por su símbolo
     * (Requerimiento Funcional 1 / Requerimiento de seguridad 3).
     */
    @GetMapping("/{simbolo}")
    public CotizacionDTO getCotizacion(@PathVariable String simbolo) {
        CotizacionDTO cotizacion = marketService.obtenerCotizacion(simbolo);
        if (cotizacion == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No se encontró cotización para el símbolo " + simbolo);
        }
        return cotizacion;
    }
}
