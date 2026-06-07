package com.tpi.users.models;

import jakarta.persistence.*;
import lombok.Data;
import java.util.List;

@Entity
@Table(name = "usuarios")
@Data
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String username;
    private String email;
    private String keycloakId; // Para vinculación con OAuth2

    @OneToOne(mappedBy = "usuario", cascade = CascadeType.ALL)
    private Cuenta cuenta;

    @OneToMany(mappedBy = "usuario")
    private List<Portafolio> portafolio;
}
