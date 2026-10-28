package com.hotel.backoffice.dto.request;

import jakarta.validation.constraints.*;

public record PasswordDTO(
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 12, message = "La contraseña debe tener mínimo 12 caracteres")
        String password
) {}