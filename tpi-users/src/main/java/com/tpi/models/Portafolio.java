package com.tpi.users.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "portafolios")
@Data
public class Portafolio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String simbolo; // Ej: NVDA
    private Integer cantidad;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}
