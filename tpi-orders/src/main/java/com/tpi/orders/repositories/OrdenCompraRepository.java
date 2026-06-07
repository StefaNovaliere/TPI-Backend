package com.tpi.orders.repositories;

import com.tpi.orders.models.OrdenCompra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Long> {
    List<OrdenCompra> findByUsuarioIdOrderByFechaCreacionDesc(Long usuarioId);
}
