package com.tpi.orders.controllers;

import com.tpi.orders.dtos.NuevaOrdenCompraDTO;
import com.tpi.orders.dtos.NuevaOrdenVentaDTO;
import com.tpi.orders.dtos.ResultadoCompraDTO;
import com.tpi.orders.models.OrdenCompra;
import com.tpi.orders.models.OrdenVenta;
import com.tpi.orders.services.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrdersController {

    private final OrderService orderService;

    public OrdersController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Registrar y resolver una orden de compra (Requerimiento Funcional 3). */
    @PostMapping("/buy")
    public ResponseEntity<ResultadoCompraDTO> comprar(@Valid @RequestBody NuevaOrdenCompraDTO dto) {
        return ResponseEntity.ok(orderService.procesarCompra(dto));
    }

    /** Registrar una orden de venta (Requerimiento Funcional 4). */
    @PostMapping("/sell")
    public ResponseEntity<OrdenVenta> vender(@Valid @RequestBody NuevaOrdenVentaDTO dto) {
        return ResponseEntity.ok(orderService.registrarVenta(dto));
    }

    /** Órdenes de compra de un usuario. */
    @GetMapping("/buy/user/{usuarioId}")
    public List<OrdenCompra> comprasDeUsuario(@PathVariable Long usuarioId) {
        return orderService.comprasDeUsuario(usuarioId);
    }

    /** Órdenes de venta de un usuario. */
    @GetMapping("/sell/user/{usuarioId}")
    public List<OrdenVenta> ventasDeUsuario(@PathVariable Long usuarioId) {
        return orderService.ventasDeUsuario(usuarioId);
    }

    /** Ver todas las acciones a la venta en el mercado (Libro de Órdenes). */
    @GetMapping("/sell/all")
    public List<OrdenVenta> verMercado() {
        return orderService.listarTodoElMercado();
    }
}
