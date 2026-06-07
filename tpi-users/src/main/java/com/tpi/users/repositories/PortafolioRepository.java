package com.tpi.users.repositories;

import com.tpi.users.models.Portafolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortafolioRepository extends JpaRepository<Portafolio, Long> {
    List<Portafolio> findByUsuarioId(Long usuarioId);
    Optional<Portafolio> findByUsuarioIdAndSimbolo(Long usuarioId, String simbolo);
}
