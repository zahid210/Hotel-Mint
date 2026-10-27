package com.hotel.backoffice.dto.response;

import com.hotel.backoffice.entity.Reserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record ReservaResponseDTO(
        UUID          id,
        Integer       habitacionId,
        String        habitacionNumero,
        String        habitacionEstado,
        String        tipoHabitacion,
        BigDecimal    precioNoche,
        String        nombreHuesped,
        String        apellidoHuesped,
        String        nroDocumento,
        LocalDate     fechaEntrada,
        LocalDate     fechaSalida,
        Short         numHuespedes,
        String        estado,
        String        notas,
        LocalDateTime createdAt,

        // ── Nivel 1: horas acordadas ──────────────────────────────
        LocalTime     horaEntradaAcordada,
        LocalTime     horaSalidaAcordada,

        // ── Nivel 1: timestamps reales ────────────────────────────
        LocalDateTime checkinReal,
        LocalDateTime checkoutReal,

        // ── Nivel 2: late check-out ───────────────────────────────
        Integer       horasExtra,
        BigDecimal    cargoHorasExtra,

        // ── Totales calculados ────────────────────────────────────
        Long          noches,             // días entre entrada y salida
        BigDecimal    totalEstancia,      // noches × precio + cargoExtra + consumos
        BigDecimal    totalConsumo        // pedidos de servicio de habitación
) {
    public static ReservaResponseDTO from(Reserva r) {
        // ── Noches facturables (basadas en horas reales) ──────────
        // Este es el número que se muestra en el panel y se cobra
        long nochesFacturables = r.calcularNochesFacturables();

        return new ReservaResponseDTO(
                r.getId(),
                r.getHabitacion().getId(),
                r.getHabitacion().getNumero(),
                r.getHabitacion().getEstado() != null ? r.getHabitacion().getEstado().name() : null,
                r.getHabitacion().getTipo().name(),
                r.getHabitacion().getPrecioNoche(),
                r.getHuesped().getNombre(),
                r.getHuesped().getApellido(),
                r.getHuesped().getNroDocumento(),
                r.getFechaEntrada(),
                r.getFechaSalida(),
                r.getNumHuespedes(),
                r.getEstado().name(),
                r.getNotas(),
                r.getCreatedAt(),

                // Nivel 1
                r.getHoraEntradaAcordada(),
                r.getHoraSalidaAcordada(),
                r.getCheckinReal(),
                r.getCheckoutReal(),

                // Nivel 2
                r.getHorasExtra(),
                r.getCargoHorasExtra() != null
                        ? r.getCargoHorasExtra()
                        : java.math.BigDecimal.ZERO,

                // Noches facturables — ya no es solo fechaSalida - fechaEntrada
                nochesFacturables,
                r.calcularTotalEstancia(),
                r.getTotalConsumo() != null
                        ? r.getTotalConsumo()
                        : java.math.BigDecimal.ZERO
        );
    }
}