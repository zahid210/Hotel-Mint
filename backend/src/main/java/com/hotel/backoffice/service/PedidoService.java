package com.hotel.backoffice.service;

import com.hotel.backoffice.dto.request.PedidoRequestDTO;
import com.hotel.backoffice.dto.response.PedidoResponseDTO;
import com.hotel.backoffice.entity.*;
import com.hotel.backoffice.entity.Pedido.EstadoPedido;
import com.hotel.backoffice.entity.Reserva.EstadoReserva;
import com.hotel.backoffice.exception.ResourceNotFoundException;
import com.hotel.backoffice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PedidoService {

    private final PedidoRepository     pedidoRepo;
    private final ReservaRepository    reservaRepo;
    private final MenuItemRepository   menuRepo;
    private final UsuarioRepository    usuarioRepo;

    // ── Listar ───────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listar(String estado, UUID reservaId) {
        if (reservaId != null) {
            return pedidoRepo.findByReservaIdOrderByCreatedAtDesc(reservaId)
                    .stream().map(PedidoResponseDTO::from).toList();
        }
        if (estado != null && !estado.isBlank()) {
            return pedidoRepo.findByEstadoOrderByCreatedAtDesc(parseEstado(estado))
                    .stream().map(PedidoResponseDTO::from).toList();
        }
        return pedidoRepo.findAllByOrderByCreatedAtDesc()
                .stream().map(PedidoResponseDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PedidoResponseDTO> listarPaginado(String estado, int page, int size) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(Math.max(page, 0), boundedSize,
                        org.springframework.data.domain.Sort.by("createdAt").descending());

        if (estado != null && !estado.isBlank()) {
            return pedidoRepo.findByEstado(parseEstado(estado), pageable).map(PedidoResponseDTO::from);
        }
        return pedidoRepo.findAll(pageable).map(PedidoResponseDTO::from);
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO obtener(UUID id) {
        return pedidoRepo.findById(id)
                .map(PedidoResponseDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado: " + id));
    }

    // ── Crear pedido ─────────────────────────────────────────
    public PedidoResponseDTO crear(PedidoRequestDTO dto) {
        Reserva reserva = reservaRepo.findById(dto.reservaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reserva no encontrada: " + dto.reservaId()));

        // Un pedido solo tiene sentido si el huésped está en la habitación
        if (reserva.getEstado() != EstadoReserva.CHECKIN) {
            throw new IllegalArgumentException(
                    "Solo se pueden pedir viandas a huéspedes con entrada registrada."
            );
        }

        if (dto.items() == null || dto.items().isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos un plato del menú.");
        }

        // 1. Congelar precios actuales del menú y agrupar cantidades
        Map<Integer, PedidoRequestDTO.ItemPedido> porPlato = new LinkedHashMap<>();
        for (PedidoRequestDTO.ItemPedido item : dto.items()) {
            if (item.cantidad() == null || item.cantidad() <= 0) continue;
            porPlato.merge(item.menuItemId(), item,
                    (a, b) -> new PedidoRequestDTO.ItemPedido(
                            a.menuItemId(), a.cantidad() + b.cantidad()));
        }
        if (porPlato.isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos un plato del menú.");
        }

        Pedido           nuevo      = Pedido.builder()
                .reserva(reserva)
                .habitacion(reserva.getHabitacion())
                .usuario(usuarioActual())
                .estado(EstadoPedido.PENDIENTE)
                .notas(dto.notas() != null ? dto.notas().trim() : null)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (PedidoRequestDTO.ItemPedido item : porPlato.values()) {
            MenuItem plato = menuRepo.findById(item.menuItemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Plato no encontrado en el menú: " + item.menuItemId()));
            if (!Boolean.TRUE.equals(plato.getDisponible())) {
                throw new IllegalArgumentException(
                        "El plato '" + plato.getNombre() + "' no está disponible.");
            }

            PedidoItem pi = PedidoItem.builder()
                    .pedido(nuevo)
                    .menuItem(plato)
                    .cantidad(item.cantidad())
                    .precioUnitario(plato.getPrecio())
                    .build();
            pi.setSubtotal(pi.calcularSubtotal());
            total = total.add(pi.getSubtotal());
            nuevo.getItems().add(pi);
        }
        nuevo.setTotal(total.setScale(2, java.math.RoundingMode.HALF_UP));

        return PedidoResponseDTO.from(pedidoRepo.save(nuevo));
    }

    // ── Cambiar estado ───────────────────────────────────────
    //  PENDIENTE → ENTREGADO: se suma el total al consumo de la reserva
    //  PENDIENTE → CANCELADO: sin efecto
    //  ENTREGADO → CANCELADO: se descuenta el consumo (corrección de un pedido
    //                         que se marcó entregado por error)
    public PedidoResponseDTO cambiarEstado(UUID id, String estado) {
        Pedido pedido = pedidoRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pedido no encontrado: " + id));
        EstadoPedido nuevo = parseEstado(estado);
        EstadoPedido actual = pedido.getEstado();

        if (nuevo == EstadoPedido.PENDIENTE) {
            throw new IllegalArgumentException("No se puede volver a poner un pedido como pendiente.");
        }
        if (nuevo == actual && actual != EstadoPedido.ENTREGADO) {
            throw new IllegalArgumentException("El pedido ya está " + actual.name() + ".");
        }
        if (actual == EstadoPedido.ENTREGADO && nuevo == EstadoPedido.ENTREGADO) {
            throw new IllegalArgumentException("El pedido ya fue entregado.");
        }
        if (actual == EstadoPedido.CANCELADO) {
            throw new IllegalArgumentException("Un pedido cancelado no se puede modificar.");
        }

        Reserva reserva = reservaRepo.findWithLockById(pedido.getReserva().getId())
                .orElse(pedido.getReserva());

        // Solo se cobra al consumo a un huésped que sigue en el hotel.
        if (nuevo == EstadoPedido.ENTREGADO
                && reserva.getEstado() != EstadoReserva.CHECKIN) {
            throw new IllegalArgumentException(
                    "No se puede entregar el pedido: el huésped ya no está en la habitación."
            );
        }

        pedido.setEstado(nuevo);

        // Actualizar consumo acumulado de la reserva
        BigDecimal actualCon = reserva.getTotalConsumo() != null
                ? reserva.getTotalConsumo() : BigDecimal.ZERO;

        if (nuevo == EstadoPedido.ENTREGADO) {
            reserva.setTotalConsumo(actualCon.add(pedido.getTotal())
                    .setScale(2, java.math.RoundingMode.HALF_UP));
        } else if (actual == EstadoPedido.ENTREGADO && nuevo == EstadoPedido.CANCELADO) {
            BigDecimal nuevoCon = actualCon.subtract(pedido.getTotal())
                    .max(BigDecimal.ZERO);
            reserva.setTotalConsumo(nuevoCon.setScale(2, java.math.RoundingMode.HALF_UP));
        }

        return PedidoResponseDTO.from(pedidoRepo.save(pedido));
    }

    private Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (auth != null) ? auth.getName() : null;
        if (email == null) {
            throw new IllegalArgumentException("Sesión no válida.");
        }
        return usuarioRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + email));
    }

    private EstadoPedido parseEstado(String estado) {
        try {
            return EstadoPedido.valueOf(estado.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Estado inválido: debe ser PENDIENTE, ENTREGADO o CANCELADO.");
        }
    }
}