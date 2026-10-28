package com.hotel.backoffice.repository;

import com.hotel.backoffice.entity.Reserva;
import com.hotel.backoffice.entity.Reserva.EstadoReserva;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservaRepository extends JpaRepository<Reserva, UUID> {

    @Override
    @EntityGraph(attributePaths = {"habitacion", "huesped"})
    Optional<Reserva> findById(UUID id);

    @EntityGraph(attributePaths = {"habitacion", "huesped"})
    List<Reserva> findAllByOrderByFechaEntradaDesc();

    @EntityGraph(attributePaths = {"habitacion", "huesped"})
    org.springframework.data.domain.Page<Reserva> findAll(org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"habitacion", "huesped"})
    Optional<Reserva> findByIdAndEstado(UUID id, EstadoReserva estado);

    boolean existsByHabitacionIdAndEstado(Integer habitacionId, EstadoReserva estado);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reserva r WHERE r.id = :id")
    Optional<Reserva> findWithLockById(@Param("id") UUID id);

    // --- Antes de crear una reserva: evitar solapamiento de fechas ---
    @Query("""
        SELECT r FROM Reserva r
        JOIN FETCH r.habitacion
        JOIN FETCH r.huesped
        WHERE r.habitacion.id = :habId
          AND r.estado NOT IN ('CHECKOUT','CANCELADA')
          AND r.fechaEntrada < :salida
          AND r.fechaSalida  > :entrada
    """)
    List<Reserva> findConflictos(
            @Param("habId")    Integer habId,
            @Param("entrada")  LocalDate entrada,
            @Param("salida")   LocalDate salida
    );

    // --- Filtrar por estado (Ej: ver solo las 'CONFIRMADA') ---
    @EntityGraph(attributePaths = {"habitacion", "huesped"})
    List<Reserva> findByEstadoOrderByFechaEntradaDesc(EstadoReserva estado);

    @EntityGraph(attributePaths = {"habitacion", "huesped"})
    org.springframework.data.domain.Page<Reserva> findByEstado(EstadoReserva estado, org.springframework.data.domain.Pageable pageable);

    // --- Reportes: filtrar en BD en vez de cargar todo ---

    // Reservas del período (todo menos canceladas) con solapamiento de fechas
    @Query("""
        SELECT r FROM Reserva r
        JOIN FETCH r.habitacion
        JOIN FETCH r.huesped
        WHERE r.estado <> 'CANCELADA'
          AND r.fechaEntrada <= :fin
          AND r.fechaSalida  >= :inicio
        ORDER BY r.fechaEntrada DESC
    """)
    List<Reserva> findEnPeriodo(
            @Param("inicio") LocalDate inicio,
            @Param("fin")    LocalDate fin
    );

    // N.º de reservas "activas" (en hotel o confirmadas) que se solapan
    // con el período, para que el resumen no mezcle datos globales.
    @Query("""
        SELECT COUNT(r) FROM Reserva r
        WHERE r.estado IN ('CHECKIN', 'CONFIRMADA')
          AND r.fechaEntrada <= :fin
          AND r.fechaSalida  > :inicio
    """)
    long countActivasEnPeriodo(
            @Param("inicio") LocalDate inicio,
            @Param("fin")    LocalDate fin
    );

    // N.º de reservas canceladas dentro del período
    @Query("""
        SELECT COUNT(r) FROM Reserva r
        WHERE r.estado = 'CANCELADA'
          AND r.fechaEntrada <= :fin
          AND r.fechaSalida  > :inicio
    """)
    long countCanceladasEnPeriodo(
            @Param("inicio") LocalDate inicio,
            @Param("fin")    LocalDate fin
    );
}