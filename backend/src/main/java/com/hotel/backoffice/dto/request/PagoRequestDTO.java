package com.hotel.backoffice.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record PagoRequestDTO(
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto mínimo es 0.01")
        BigDecimal monto,

        @NotBlank(message = "El método de pago es obligatorio")
        @Pattern(regexp = "EFECTIVO|TARJETA|TRANSFERENCIA", flags = Pattern.Flag.CASE_INSENSITIVE,
                 message = "El método debe ser EFECTIVO, TARJETA o TRANSFERENCIA")
        String metodoPago,

        String referencia
) {}
