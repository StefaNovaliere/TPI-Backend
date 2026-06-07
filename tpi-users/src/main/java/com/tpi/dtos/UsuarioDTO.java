package com.tpi.users.dtos;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class UsuarioDTO {
    private Long id;
    private String username;
    private BigDecimal saldo;
    private List<String> acciones; 
}
