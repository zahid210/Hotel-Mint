package com.hotel.backoffice.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CuentaReservaDTO(
        UUID reservaId,
        String habitacionNumero,
        String huespedNombreCompleto,
        String estadoReserva,
        BigDecimal totalEstancia,
        BigDecimal totalConsumo,
        BigDecimal totalPagado,
        BigDecimal saldoPendiente,
        boolean pagadoCompletamente,
        List<PagoResponseDTO> pagos
) {}
