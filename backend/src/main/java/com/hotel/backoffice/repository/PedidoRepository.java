package com.hotel.backoffice.repository;

import com.hotel.backoffice.entity.Pedido;
import com.hotel.backoffice.entity.Pedido.EstadoPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

    @Override
    @org.springframework.data.jpa.repository.EntityGraph(
            attributePaths = {"reserva", "habitacion", "usuario", "items", "items.menuItem"})
    Optional<Pedido> findById(UUID id);

    @org.springframework.data.jpa.repository.EntityGraph(
            attributePaths = {"reserva", "habitacion", "usuario", "items", "items.menuItem"})
    List<Pedido> findAllByOrderByCreatedAtDesc();

    @org.springframework.data.jpa.repository.EntityGraph(
            attributePaths = {"reserva", "habitacion", "usuario", "items", "items.menuItem"})
    List<Pedido> findByEstadoOrderByCreatedAtDesc(EstadoPedido estado);

    @org.springframework.data.jpa.repository.EntityGraph(
            attributePaths = {"reserva", "habitacion", "usuario", "items", "items.menuItem"})
    List<Pedido> findByReservaIdOrderByCreatedAtDesc(UUID reservaId);

    boolean existsByReservaIdAndEstado(UUID reservaId, EstadoPedido estado);

    // ── Reportes: consumo servido (ENTREGADO) contado por fecha de checkout ──
    // Consistente con los ingresos de habitación, que también se atribuyen
    // al día en que la reserva llegó a CHECKOUT.
    @Query("""
        SELECT COALESCE(SUM(p.total), 0)
        FROM Pedido p
        WHERE p.estado = com.hotel.backoffice.entity.Pedido.EstadoPedido.ENTREGADO
          AND p.reserva.checkoutReal >= :inicio
          AND p.reserva.checkoutReal <  :fin
    """)
    BigDecimal sumarConsumoEntre(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin")    LocalDateTime fin
    );
}