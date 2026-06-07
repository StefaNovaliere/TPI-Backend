package com.tpi.orders.models;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ordenes_compra")
@Data
public class OrdenCompra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private String simbolo;

    @Column(nullable = false)
    private Integer cantidad;

    // Precio unitario máximo que el comprador está dispuesto a pagar (en ARS).
    @Column(nullable = false)
    private BigDecimal precioMaxArs;

    @Column(nullable = false)
    private Integer cantidadComprada = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoOrden estado;

    @Column(nullable = false)
    private Instant fechaCreacion = Instant.now();
}
