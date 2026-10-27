package com.hotel.backoffice.service;

import com.hotel.backoffice.dto.response.HabitacionResponseDTO;
import com.hotel.backoffice.entity.Habitacion;
import com.hotel.backoffice.entity.Habitacion.EstadoHabitacion;
import com.hotel.backoffice.entity.Reserva.EstadoReserva;
import com.hotel.backoffice.exception.ResourceNotFoundException;
import com.hotel.backoffice.repository.HabitacionRepository;
import com.hotel.backoffice.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class HabitacionService {

    private final HabitacionRepository repo;
    private final ReservaRepository reservaRepo;

    public List<HabitacionResponseDTO> listarTodas() {
        return repo.findAllByOrderByNumeroAsc()
                .stream().map(HabitacionResponseDTO::from).toList();
    }

    public List<HabitacionResponseDTO> listarLibres() {
        return repo.findByEstadoOrderByNumeroAsc(EstadoHabitacion.LIBRE)
                .stream().map(HabitacionResponseDTO::from).toList();
    }

    @Transactional
    public HabitacionResponseDTO cambiarEstado(Integer id, String nuevoEstado) {
        Habitacion h = repo.findWithLockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Habitación no encontrada: " + id));

        EstadoHabitacion destino;
        try {
            destino = EstadoHabitacion.valueOf(nuevoEstado.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Estado inválido: '" + nuevoEstado + "'. Valores permitidos: "
                            + java.util.Arrays.toString(EstadoHabitacion.values())
            );
        }

        EstadoHabitacion actual = h.getEstado();
        if (actual == destino) {
            return HabitacionResponseDTO.from(h);
        }

        // Ningún operador puede marcar manualmente una habitación como OCUPADA.
        // La ocupación solo ocurre mediante el registro formal de Check-In.
        if (destino == EstadoHabitacion.OCUPADA) {
            throw new IllegalArgumentException(
                    "El estado OCUPADA solo puede asignarse automáticamente mediante el registro de Check-In."
            );
        }

        // Validación de permisos por rol
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean esLimpieza = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_LIMPIEZA"));
        boolean esAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        // Limpieza solo puede ejecutar la transición específica LIMPIEZA -> LIBRE
        if (esLimpieza && !esAdmin) {
            if (actual != EstadoHabitacion.LIMPIEZA || destino != EstadoHabitacion.LIBRE) {
                throw new IllegalArgumentException(
                        "El personal de limpieza solo puede pasar habitaciones de LIMPIEZA a LIBRE."
                );
            }
        }

        // Si la habitación está actualmente OCUPADA:
        // No se puede liberar ni cambiar estado si existe un check-in activo
        if (actual == EstadoHabitacion.OCUPADA) {
            boolean tieneCheckInActivo = reservaRepo.existsByHabitacionIdAndEstado(id, EstadoReserva.CHECKIN);
            if (tieneCheckInActivo) {
                throw new IllegalStateException(
                        "No se puede cambiar el estado de una habitación ocupada con un huésped activo. Realice el Check-Out o cancele la reserva primero."
                );
            }
        }

        // Máquina de estados: validar transiciones lógicas entre estados físicos
        boolean transicionValida = switch (actual) {
            case LIBRE -> (destino == EstadoHabitacion.MANTENIMIENTO || destino == EstadoHabitacion.LIMPIEZA);
            case LIMPIEZA -> (destino == EstadoHabitacion.LIBRE || destino == EstadoHabitacion.MANTENIMIENTO);
            case MANTENIMIENTO -> (destino == EstadoHabitacion.LIMPIEZA || destino == EstadoHabitacion.LIBRE);
            case OCUPADA -> (destino == EstadoHabitacion.LIMPIEZA || destino == EstadoHabitacion.LIBRE || destino == EstadoHabitacion.MANTENIMIENTO);
        };

        if (!transicionValida) {
            throw new IllegalStateException(
                    "Transición de estado no permitida: de " + actual + " a " + destino + "."
            );
        }

        h.setEstado(destino);
        return HabitacionResponseDTO.from(h);
    }
}