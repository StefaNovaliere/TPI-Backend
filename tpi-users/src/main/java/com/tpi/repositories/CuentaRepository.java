package com.tpi.users.repositories;

import com.tpi.users.models.Cuenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CuentaRepository extends JpaRepository<Cuenta, Long> {
    Cuenta findByUsuarioId(Long usuarioId);
}
