package com.tpi.users.models;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "cuentas")
@Data
public class Cuenta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Saldo siempre en Pesos Argentinos (ARS), según la consigna.
    @Column(nullable = false)
    private BigDecimal saldoArs = BigDecimal.ZERO;

    @Column(name = "usuario_id", nullable = false, unique = true)
    private Long usuarioId;
}
