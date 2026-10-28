package com.hotel.backoffice.service;

import com.hotel.backoffice.dto.request.PagoRequestDTO;
import com.hotel.backoffice.dto.response.CuentaReservaDTO;
import com.hotel.backoffice.dto.response.PagoResponseDTO;
import com.hotel.backoffice.entity.Pago;
import com.hotel.backoffice.entity.Pago.EstadoPago;
import com.hotel.backoffice.entity.Pago.MetodoPago;
import com.hotel.backoffice.entity.Reserva;
import com.hotel.backoffice.entity.Reserva.EstadoReserva;
import com.hotel.backoffice.entity.Usuario;
import com.hotel.backoffice.exception.ResourceNotFoundException;
import com.hotel.backoffice.repository.PagoRepository;
import com.hotel.backoffice.repository.ReservaRepository;
import com.hotel.backoffice.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PagoService {

    private final PagoRepository    pagoRepo;
    private final ReservaRepository reservaRepo;
    private final UsuarioRepository usuarioRepo;
    private final AuditoriaService  auditoriaService;
    private final Clock             clock;

    // ── Registrar un pago para una reserva ────────────────────
    public PagoResponseDTO registrarPago(UUID reservaId, PagoRequestDTO dto) {
        Reserva reserva = reservaRepo.findById(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada: " + reservaId));

        if (reserva.getEstado() == EstadoReserva.CANCELADA) {
            throw new IllegalStateException("No se pueden registrar pagos en una reserva cancelada.");
        }

        MetodoPago metodo = MetodoPago.valueOf(dto.metodoPago().toUpperCase(Locale.ROOT));

        // Obtener usuario autenticado
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Usuario usuario = null;
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            usuario = usuarioRepo.findByEmail(auth.getName()).orElse(null);
        }

        Pago pago = Pago.builder()
                .reserva(reserva)
                .monto(dto.monto().setScale(2, RoundingMode.HALF_UP))
                .metodoPago(metodo)
                .estadoPago(EstadoPago.PAGADO)
                .referencia(dto.referencia())
                .fechaPago(LocalDateTime.now(clock))
                .usuario(usuario)
                .build();

        Pago guardado = pagoRepo.save(pago);

        auditoriaService.registrar("REGISTRO_PAGO", "Pago", guardado.getId(),
                "Pago de S/ " + guardado.getMonto() + " registrado con " + metodo + " para reserva en hab. "
                        + reserva.getHabitacion().getNumero());

        return PagoResponseDTO.from(guardado);
    }

    // ── Reembolsar un pago ───────────────────────────────────
    public PagoResponseDTO reembolsarPago(UUID pagoId) {
        Pago pago = pagoRepo.findById(pagoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado: " + pagoId));

        if (pago.getEstadoPago() == EstadoPago.REEMBOLSADO) {
            throw new IllegalStateException("El pago ya fue reembolsado.");
        }

        pago.setEstadoPago(EstadoPago.REEMBOLSADO);
        Pago guardado = pagoRepo.save(pago);

        auditoriaService.registrar("REEMBOLSO_PAGO", "Pago", guardado.getId(),
                "Pago de S/ " + guardado.getMonto() + " marcado como REEMBOLSADO");

        return PagoResponseDTO.from(guardado);
    }

    // ── Listar pagos de una reserva ──────────────────────────
    @Transactional(readOnly = true)
    public List<PagoResponseDTO> listarPorReserva(UUID reservaId) {
        return pagoRepo.findByReservaIdOrderByFechaPagoDesc(reservaId)
                .stream()
                .map(PagoResponseDTO::from)
                .toList();
    }

    // ── Obtener estado de cuenta consolidado ─────────────────
    @Transactional(readOnly = true)
    public CuentaReservaDTO obtenerEstadoCuenta(UUID reservaId) {
        Reserva reserva = reservaRepo.findById(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada: " + reservaId));

        BigDecimal totalEstancia = reserva.calcularTotalEstancia();
        BigDecimal totalConsumo = reserva.getTotalConsumo() != null ? reserva.getTotalConsumo() : BigDecimal.ZERO;

        BigDecimal totalPagado = pagoRepo.sumarPorReservaYEstado(reservaId, EstadoPago.PAGADO);
        BigDecimal saldoPendiente = totalEstancia.subtract(totalPagado);
        if (saldoPendiente.compareTo(BigDecimal.ZERO) < 0) {
            saldoPendiente = BigDecimal.ZERO;
        }

        boolean pagadoCompletamente = totalPagado.compareTo(totalEstancia) >= 0;

        List<PagoResponseDTO> pagos = pagoRepo.findByReservaIdOrderByFechaPagoDesc(reservaId)
                .stream()
                .map(PagoResponseDTO::from)
                .toList();

        return new CuentaReservaDTO(
                reserva.getId(),
                reserva.getHabitacion().getNumero(),
                reserva.getHuesped().getNombre() + " " + reserva.getHuesped().getApellido(),
                reserva.getEstado().name(),
                totalEstancia,
                totalConsumo,
                totalPagado,
                saldoPendiente,
                pagadoCompletamente,
                pagos
        );
    }
}
