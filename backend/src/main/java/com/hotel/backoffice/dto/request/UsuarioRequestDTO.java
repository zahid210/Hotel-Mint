package com.hotel.backoffice.dto.request;

import jakarta.validation.constraints.*;

public record UsuarioRequestDTO(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        @NotBlank @Email(message = "Email inválido")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 12, message = "La contraseña debe tener mínimo 12 caracteres")
        String password,

        @Pattern(regexp = "ADMIN|RECEPCIONISTA|LIMPIEZA", flags = Pattern.Flag.CASE_INSENSITIVE,
                 message = "El rol debe ser ADMIN, RECEPCIONISTA o LIMPIEZA")
        String rol
) {}