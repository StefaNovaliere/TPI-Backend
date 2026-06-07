package com.tpi.users.services;

import com.tpi.users.models.Cuenta;
import com.tpi.users.repositories.CuentaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class UserService {

    @Autowired
    private CuentaRepository cuentaRepository;

    @Transactional
    public void ingresarDinero(Long usuarioId, BigDecimal monto) {
        Cuenta cuenta = cuentaRepository.findByUsuarioId(usuarioId);
        // Según consigna: Dinero infinito, solo sumamos al saldo actual
        cuenta.setSaldoArs(cuenta.getSaldoArs().add(monto));
        cuentaRepository.save(cuenta);
    }
    
    // Aquí irían métodos para descontar saldo en compras y sumar en ventas
}
