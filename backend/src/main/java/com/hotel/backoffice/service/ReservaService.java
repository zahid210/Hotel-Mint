package com.hotel.backoffice.service;

import com.hotel.backoffice.dto.request.ReservaRequestDTO;
import com.hotel.backoffice.dto.response.ReservaResponseDTO;
import com.hotel.backoffice.entity.*;
import com.hotel.backoffice.entity.Habitacion.EstadoHabitacion;
import com.hotel.backoffice.entity.Huesped.TipoDocumento;
import com.hotel.backoffice.entity.Reserva.EstadoReserva;
import com.hotel.backoffice.exception.*;
import com.hotel.backoffice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ReservaService {

    private final ReservaRepository    reservaRepo;
    private final HabitacionRepository habitacionRepo;
    private final HuespedRepository    huespedRepo;
    private final UsuarioRepository    usuarioRepo;
    private final PedidoRepository     pedidoRepo;
    private final AuditoriaService     auditoriaService;
    private final Clock                clock;

    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listarTodas() {
        return reservaRepo.findAllByOrderByFechaEntradaDesc()
                .stream().map(ReservaResponseDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ReservaResponseDTO> listarPaginado(String estado, int page, int size) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(Math.max(page, 0), boundedSize,
                        org.springframework.data.domain.Sort.by("fechaEntrada").descending());

        if (estado != null && !estado.isBlank()) {
            EstadoReserva estadoEnum = EstadoReserva.valueOf(estado.toUpperCase(Locale.ROOT));
            return reservaRepo.findByEstado(estadoEnum, pageable).map(ReservaResponseDTO::from);
        }
        return reservaRepo.findAll(pageable).map(ReservaResponseDTO::from);
    }

    // Filtrar por estado desde el controller
    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listarPorEstado(String estado) {
        try {
            EstadoReserva estadoEnum = EstadoReserva.valueOf(estado.toUpperCase(Locale.ROOT));
            return reservaRepo.findByEstadoOrderByFechaEntradaDesc(estadoEnum)
                    .stream()
                    .map(ReservaResponseDTO::from)
                    .toList();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Estado inválido: '" + estado + "'. Valores permitidos: "
                            + java.util.Arrays.toString(EstadoReserva.values())
            );
        }
    }

    @Transactional(readOnly = true)
    public ReservaResponseDTO obtenerPorId(UUID id) {
        return reservaRepo.findById(id)
                .map(ReservaResponseDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada: " + id));
    }

    public ReservaResponseDTO crearReserva(ReservaRequestDTO dto) {

        // 1. Validar fechas
        if (!dto.fechaSalida().isAfter(dto.fechaEntrada())) {
            throw new IllegalArgumentException(
                    "La fecha de salida debe ser posterior a la entrada."
            );
        }

        // 2. Obtener habitación (con lock pesimista para evitar que dos
        //    reservas solapadas se creen a la vez sobre la misma habitación)
        Habitacion hab = habitacionRepo.findWithLockById(dto.habitacionId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Habitación no encontrada")
                );

        // 2b. Validar capacidad
        if (dto.numHuespedes() > hab.getCapacidad()) {
            throw new IllegalArgumentException(
                    "El número de huéspedes excede la capacidad de la habitación ("
                            + hab.getCapacidad() + ")."
            );
        }

        // 3. Validar estado de habitación
        //    Nota: una habitación OCUPADA (con huésped hoy) sí puede reservarse
        //    para fechas futuras sin solapamiento; eso lo decide `findConflictos`
        //    por rango de fechas abajo, no el estado físico.
        if (hab.getEstado() == EstadoHabitacion.MANTENIMIENTO) {
            throw new HabitacionNoDisponibleException(
                    "La habitación está en mantenimiento y no puede reservarse."
            );
        }
        if (hab.getEstado() == EstadoHabitacion.LIMPIEZA
                && !dto.fechaEntrada().isAfter(LocalDate.now())) {
            throw new HabitacionNoDisponibleException(
                    "La habitación está en limpieza. " +
                            "La fecha de entrada debe ser a partir de mañana."
            );
        }

        // 4. Verificar conflictos de fechas
        List<Reserva> conflictos = reservaRepo.findConflictos(
                dto.habitacionId(), dto.fechaEntrada(), dto.fechaSalida()
        );
        if (!conflictos.isEmpty()) {
            throw new HabitacionNoDisponibleException(
                    "La habitación ya tiene una reserva en esas fechas."
            );
        }

        // 5. Obtener o crear huésped
        Huesped huesped = huespedRepo.findByNroDocumento(dto.nroDocumento())
                .orElseGet(() -> huespedRepo.save(
                        Huesped.builder()
                                .nombre(dto.nombreHuesped())
                                .apellido(dto.apellidoHuesped())
                                .tipoDocumento(TipoDocumento.valueOf(dto.tipoDocumento()))
                                .nroDocumento(dto.nroDocumento())
                                .build()
                ));

        // 6. Usuario autenticado que registra la reserva
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (auth != null ? auth.getName() : null);
        Usuario usuario = (email != null)
                ? usuarioRepo.findByEmail(email).orElseThrow(() ->
                        new ResourceNotFoundException("Usuario no encontrado: " + email))
                : null;

        // 7. Resolver horas acordadas
        // Si el recepcionista las especifica → se usan las del formulario
        // Si no las especifica (null)        → defaults del hotel
        LocalTime horaEntrada = (dto.horaEntradaAcordada() != null)
                ? dto.horaEntradaAcordada()
                : LocalTime.of(15, 0);          // default: 15:00

        LocalTime horaSalida = (dto.horaSalidaAcordada() != null)
                ? dto.horaSalidaAcordada()
                : LocalTime.of(12, 0);          // default: 12:00

        // 8. Crear y persistir reserva
        Reserva reserva = Reserva.builder()
                .habitacion(hab)
                .huesped(huesped)
                .usuario(usuario)
                .fechaEntrada(dto.fechaEntrada())
                .fechaSalida(dto.fechaSalida())
                .horaEntradaAcordada(horaEntrada)
                .horaSalidaAcordada(horaSalida)
                .numHuespedes(dto.numHuespedes())
                .estado(EstadoReserva.CONFIRMADA)
                .notas(dto.notas())
                .build();

        Reserva guardada = reservaRepo.save(reserva);
        auditoriaService.registrar("CREAR_RESERVA", "Reserva", guardada.getId(),
                "Reserva creada para huésped " + huesped.getNombre() + " " + huesped.getApellido()
                        + " en habitación " + hab.getNumero());
        return ReservaResponseDTO.from(guardada);
    }

    // ── CHECK-IN: registra el timestamp real ─────────────────────
    public ReservaResponseDTO realizarCheckIn(UUID id) {
        Reserva reserva = reservaRepo.findByIdAndEstado(id, EstadoReserva.CONFIRMADA)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reserva no encontrada o no está CONFIRMADA"
                ));

        // El check-in solo corresponde el día de llegada o después.
        if (reserva.getFechaEntrada().isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException(
                    "No se puede registrar la entrada antes de la fecha de llegada del huésped."
            );
        }

        // Evitar doble ocupación física de la habitación.
        EstadoHabitacion estadoHab = reserva.getHabitacion().getEstado();
        if (estadoHab == EstadoHabitacion.OCUPADA) {
            throw new IllegalArgumentException(
                    "La habitación ya está ocupada por otro huésped."
            );
        }
        if (estadoHab == EstadoHabitacion.MANTENIMIENTO) {
            throw new IllegalArgumentException(
                    "La habitación está en mantenimiento y no puede recibir huéspedes."
            );
        }

        // NIVEL 1: timestamp exacto del check-in
        reserva.setCheckinReal(LocalDateTime.now(clock));

        // Transición de estados
        reserva.setEstado(EstadoReserva.CHECKIN);
        reserva.getHabitacion().setEstado(EstadoHabitacion.OCUPADA);

        auditoriaService.registrar("CHECKIN", "Reserva", id,
                "Check-in realizado en habitación " + reserva.getHabitacion().getNumero());

        return ReservaResponseDTO.from(reserva);
    }

    // ── CHECK-OUT: registra timestamp + calcula late checkout ─────
    public ReservaResponseDTO realizarCheckOut(UUID id) {
        Reserva reserva = reservaRepo.findByIdAndEstado(id, EstadoReserva.CHECKIN)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reserva no encontrada o no está en CHECKIN"
                ));

        // P1-05: Validar que no existan pedidos PENDIENTE antes de cerrar la cuenta
        if (pedidoRepo.existsByReservaIdAndEstado(id, Pedido.EstadoPedido.PENDIENTE)) {
            throw new IllegalStateException(
                    "No se puede realizar el check-out: existen pedidos pendientes asociados a la habitación. Entréguelos o cancélelos antes de proceder."
            );
        }

        LocalDateTime ahora = LocalDateTime.now(clock);

        // NIVEL 1 + NIVEL 2: timestamp real + cálculo de horas extra
        // El método interno compara ahora vs horaSalidaAcordada
        reserva.calcularCargoLateCheckout(ahora);

        // Transición de estados
        reserva.setEstado(EstadoReserva.CHECKOUT);
        reserva.getHabitacion().setEstado(EstadoHabitacion.LIMPIEZA);

        auditoriaService.registrar("CHECKOUT", "Reserva", id,
                "Check-out realizado en habitación " + reserva.getHabitacion().getNumero()
                        + ". Total estancia: " + reserva.calcularTotalEstancia());

        return ReservaResponseDTO.from(reserva);
    }

    public ReservaResponseDTO cancelarReserva(UUID id) {
        Reserva reserva = reservaRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
        if (reserva.getEstado() == EstadoReserva.CHECKOUT
                || reserva.getEstado() == EstadoReserva.CANCELADA) {
            throw new IllegalArgumentException(
                    "No se puede cancelar una reserva en estado " + reserva.getEstado() + "."
            );
        }
        if (reserva.getEstado() == EstadoReserva.CHECKIN) {
            reserva.getHabitacion().setEstado(EstadoHabitacion.LIMPIEZA);
        }
        reserva.setEstado(EstadoReserva.CANCELADA);

        auditoriaService.registrar("CANCELAR", "Reserva", id,
                "Cancelación de reserva en habitación " + reserva.getHabitacion().getNumero());

        return ReservaResponseDTO.from(reserva);
    }
}