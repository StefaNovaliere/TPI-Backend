package com.tpi.users.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Pedido de ingreso de dinero (en ARS) a la cuenta de un usuario. */
public record DepositRequest(
        @NotNull @Positive BigDecimal amount
) {}
