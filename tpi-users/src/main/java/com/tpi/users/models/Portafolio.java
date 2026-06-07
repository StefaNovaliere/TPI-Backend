package com.tpi.users.models;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Una tenencia del portfolio de un usuario: cuántas acciones de un símbolo posee.
 */
@Entity
@Table(name = "portafolios",
       uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "simbolo"}))
@Data
public class Portafolio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String simbolo; // Ej: NVDA

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;
}
