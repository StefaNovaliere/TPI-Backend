package com.tpi.history.models;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** Registro de una operación realizada en el sistema (orden o trade). */
@Entity
@Table(name = "operaciones")
@Data
public class Operacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tipo; // ORDEN_COMPRA, ORDEN_VENTA, TRADE

    @Column(nullable = false)
    private Long usuarioId;

    // En un TRADE, la contraparte (vendedor si usuarioId es comprador).
    private Long contraparteId;

    private String simbolo;
    private Integer cantidad;
    private BigDecimal precioUnitarioArs;
    private BigDecimal totalArs;
    private String estado;

    @Column(nullable = false)
    private Instant fecha = Instant.now();
}
