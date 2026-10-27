package com.hotel.backoffice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "reservas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "habitacion_id", nullable = false)
    private Habitacion habitacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "huesped_id", nullable = false)
    private Huesped huesped;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // ── Fechas acordadas ──────────────────────────────────────
    @Column(name = "fecha_entrada", nullable = false)
    private LocalDate fechaEntrada;

    @Column(name = "fecha_salida", nullable = false)
    private LocalDate fechaSalida;

    // ── Horas acordadas (manuales desde el formulario) ────────
    // Default hotel: check-in 15:00 / check-out 12:00
    @Column(name = "hora_entrada_acordada", nullable = false)
    @Builder.Default
    private LocalTime horaEntradaAcordada = LocalTime.of(15, 0);

    @Column(name = "hora_salida_acordada", nullable = false)
    @Builder.Default
    private LocalTime horaSalidaAcordada = LocalTime.of(12, 0);

    // ── Timestamps reales de operación (Nivel 1) ──────────────
    @Column(name = "checkin_real")
    private LocalDateTime checkinReal;

    @Column(name = "checkout_real")
    private LocalDateTime checkoutReal;

    // ── Cargo por día extra (Nivel 2 modificado) ─────────────
    // 0 si salió antes de medianoche de la fecha de salida
    // 1 noche completa si salió después de las 00:00
    @Column(name = "horas_extra")
    @Builder.Default
    private Integer horasExtra = 0;

    @Column(name = "cargo_horas_extra", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal cargoHorasExtra = BigDecimal.ZERO;

    // ── Servicio de habitación ─────────────────────────────────
    // Consumo acumulado de los pedidos ENTREGADO. Se suma al total
    // que se cobra al hacer checkout (ver calcularTotalEstancia()).
    @Column(name = "total_consumo", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalConsumo = BigDecimal.ZERO;

    // ── Campos de negocio estándar ────────────────────────────
    @Column(name = "num_huespedes", nullable = false)
    @Builder.Default
    private Short numHuespedes = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoReserva estado = EstadoReserva.CONFIRMADA;

    private String notas;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        // Blindaje: garantiza que nunca se persistan nulos
        if (this.horaEntradaAcordada == null)
            this.horaEntradaAcordada = LocalTime.of(15, 0);
        if (this.horaSalidaAcordada == null)
            this.horaSalidaAcordada = LocalTime.of(12, 0);
        if (this.cargoHorasExtra == null)
            this.cargoHorasExtra = BigDecimal.ZERO;
        if (this.totalConsumo == null)
            this.totalConsumo = BigDecimal.ZERO;
        if (this.horasExtra == null)
            this.horasExtra = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── NIVEL 2 MODIFICADO: Regla de medianoche ───────────────
    //
    // Política del hotel:
    //   - Si el huésped hace checkout ANTES de las 00:00 de su
    //     fecha de salida → sin cargo adicional
    //   - Si el huésped permanece DESPUÉS de las 00:00 de su
    //     fecha de salida → se cobra 1 noche completa
    //
    // Ejemplos:
    //   fechaSalida = 2026-03-16, checkoutReal = 2026-03-16T11:30
    //   → dentro del mismo día → SIN CARGO
    //
    //   fechaSalida = 2026-03-16, checkoutReal = 2026-03-17T02:00
    //   → pasó la medianoche del 16 → 1 NOCHE COMPLETA
    //
    //   fechaSalida = 2026-03-16, checkoutReal = 2026-03-18T10:00
    //   → 2 días extra → 2 NOCHES COMPLETAS
    //
    public void calcularCargoLateCheckout(LocalDateTime momentoCheckout) {
        this.checkoutReal = momentoCheckout;

        // Blindaje: asegurar valores no nulos antes de operar
        if (this.horaSalidaAcordada == null)
            this.horaSalidaAcordada = LocalTime.of(12, 0);
        if (this.cargoHorasExtra == null)
            this.cargoHorasExtra = BigDecimal.ZERO;

        // Medianoche de la fecha de salida acordada
        // Ej: fechaSalida 2026-03-16 → medianoche = 2026-03-17T00:00:00
        LocalDateTime medianocheSalida = this.fechaSalida
                .plusDays(1)
                .atStartOfDay();

        if (momentoCheckout.isBefore(medianocheSalida)) {
            // ── Salió antes de medianoche: sin cargo ─────────
            this.horasExtra      = 0;
            this.cargoHorasExtra = BigDecimal.ZERO;
        } else {
            // ── Pasó la medianoche: cobrar noches completas ──
            // Cuenta cuántas noches completas ha pasado
            // desde la fecha de salida:
            //   salida 2026-03-16, checkout 2026-03-17T02:00 → 1 noche
            //   salida 2026-03-16, checkout 2026-03-18T10:00 → 2 noches
            long nochesExtra = ChronoUnit.DAYS.between(
                    this.fechaSalida,            // día acordado de salida
                    momentoCheckout.toLocalDate() // día del checkout real
            );

            // Mínimo 1 noche extra si pasó la medianoche
            if (nochesExtra < 1) {
                nochesExtra = 1;
            }

            this.horasExtra = (int) nochesExtra;

            // Blindaje en precioNoche antes de multiplicar
            BigDecimal precio = (this.habitacion != null
                    && this.habitacion.getPrecioNoche() != null)
                    ? this.habitacion.getPrecioNoche()
                    : BigDecimal.ZERO;

            this.cargoHorasExtra = precio
                    .multiply(BigDecimal.valueOf(nochesExtra))
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }

    // ── Noches facturables basadas en horas reales de ocupación ──
//
// Fórmula: ceil(horas_totales / 24)
//
// Esto resuelve casos como:
//   Entrada 07:00 → Salida 20:00 del día siguiente = 37h = 2 noches
//   Entrada 15:00 → Salida 12:00 del día siguiente = 21h = 1 noche
//
// Mínimo siempre: 1 noche (nunca se cobra menos aunque sean pocas horas)
//
    public long calcularNochesFacturables() {
        // Construir los datetime completos de entrada y salida
        LocalTime entrada = (this.horaEntradaAcordada != null)
                ? this.horaEntradaAcordada
                : LocalTime.of(15, 0);

        LocalTime salida = (this.horaSalidaAcordada != null)
                ? this.horaSalidaAcordada
                : LocalTime.of(12, 0);

        LocalDateTime dtEntrada = this.fechaEntrada.atTime(entrada);
        LocalDateTime dtSalida  = this.fechaSalida.atTime(salida);

        // Horas totales de ocupación
        long minutos = ChronoUnit.MINUTES.between(dtEntrada, dtSalida);

        // Redondeo hacia arriba: 21h = 1 noche, 37h = 2 noches
        long noches = (long) Math.ceil(minutos / 60.0 / 24.0);

        // Mínimo 1 noche siempre
        return Math.max(noches, 1);
    }

    public BigDecimal calcularTotalEstancia() {
        BigDecimal precio = (this.habitacion != null
                && this.habitacion.getPrecioNoche() != null)
                ? this.habitacion.getPrecioNoche()
                : BigDecimal.ZERO;

        // Usar noches facturables (basadas en horas reales)
        long noches = calcularNochesFacturables();

        BigDecimal totalNoches = precio
                .multiply(BigDecimal.valueOf(noches));

        BigDecimal extra = (this.cargoHorasExtra != null)
                ? this.cargoHorasExtra
                : BigDecimal.ZERO;

        // Consumos de servicio de habitación (pedidos ENTREGADO)
        BigDecimal consumo = (this.totalConsumo != null)
                ? this.totalConsumo
                : BigDecimal.ZERO;

        return totalNoches.add(extra).add(consumo)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public enum EstadoReserva {
        PENDIENTE, CONFIRMADA, CHECKIN, CHECKOUT, CANCELADA
    }
}