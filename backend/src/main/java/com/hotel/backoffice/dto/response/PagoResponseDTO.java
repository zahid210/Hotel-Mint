package com.hotel.backoffice.dto.response;

import com.hotel.backoffice.entity.Pago;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PagoResponseDTO(
        UUID id,
        UUID reservaId,
        BigDecimal monto,
        String metodoPago,
        String estadoPago,
        String referencia,
        LocalDateTime fechaPago,
        String registradoPor
) {
    public static PagoResponseDTO from(Pago p) {
        return new PagoResponseDTO(
                p.getId(),
                p.getReserva().getId(),
                p.getMonto(),
                p.getMetodoPago() != null ? p.getMetodoPago().name() : null,
                p.getEstadoPago() != null ? p.getEstadoPago().name() : null,
                p.getReferencia(),
                p.getFechaPago(),
                p.getUsuario() != null ? p.getUsuario().getNombre() : "SISTEMA"
        );
    }
}
