package com.tpi.orders.models;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** Una operación concreta de compra-venta (resultado de emparejar dos órdenes). */
@Entity
@Table(name = "trades")
@Data
public class Trade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long compradorId;

    @Column(nullable = false)
    private Long vendedorId;

    @Column(nullable = false)
    private String simbolo;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(nullable = false)
    private BigDecimal precioUnitarioArs;

    @Column(nullable = false)
    private BigDecimal totalArs;

    private Long ordenCompraId;
    private Long ordenVentaId;

    @Column(nullable = false)
    private Instant fecha = Instant.now();
}
