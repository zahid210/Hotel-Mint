package com.hotel.backoffice.repository;

import com.hotel.backoffice.entity.Pago;
import com.hotel.backoffice.entity.Pago.EstadoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PagoRepository extends JpaRepository<Pago, UUID> {

    List<Pago> findByReservaIdOrderByFechaPagoDesc(UUID reservaId);

    List<Pago> findByReservaIdAndEstadoPago(UUID reservaId, EstadoPago estadoPago);

    @Query("""
        SELECT COALESCE(SUM(p.monto), 0)
        FROM Pago p
        WHERE p.reserva.id = :reservaId
          AND p.estadoPago = :estado
    """)
    BigDecimal sumarPorReservaYEstado(
            @Param("reservaId") UUID reservaId,
            @Param("estado")    EstadoPago estado
    );

    @Query("""
        SELECT COALESCE(SUM(p.monto), 0)
        FROM Pago p
        WHERE p.estadoPago = :estado
          AND p.fechaPago >= :inicio
          AND p.fechaPago <  :fin
    """)
    BigDecimal sumarCobradoEntre(
            @Param("estado") EstadoPago estado,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin")    LocalDateTime fin
    );
}
