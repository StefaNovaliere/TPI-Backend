package com.tpi.orders.models;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ordenes_venta")
@Data
public class OrdenVenta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private String simbolo;

    @Column(nullable = false)
    private Integer cantidad;

    // Cantidad que todavía queda disponible para vender (se decrementa al emparejar).
    @Column(nullable = false)
    private Integer cantidadRestante;

    // Precio unitario mínimo que el vendedor acepta (en ARS).
    @Column(nullable = false)
    private BigDecimal precioMinArs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoOrden estado;

    @Column(nullable = false)
    private Instant fechaCreacion = Instant.now();
}
