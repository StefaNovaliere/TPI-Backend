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

    private BigDecimal saldoArs; // Dinero en pesos

    @OneToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}
