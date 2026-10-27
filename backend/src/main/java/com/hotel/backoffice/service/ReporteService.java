package com.hotel.backoffice.service;

import com.hotel.backoffice.dto.response.ReporteDTO.*;
import com.hotel.backoffice.entity.Reserva;
import com.hotel.backoffice.entity.Reserva.EstadoReserva;
import com.hotel.backoffice.repository.HabitacionRepository;
import com.hotel.backoffice.repository.PedidoRepository;
import com.hotel.backoffice.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteService {

    private final ReservaRepository    reservaRepo;
    private final HabitacionRepository habitacionRepo;
    private final PedidoRepository     pedidoRepo;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public record NochesEIngreso(long nochesEnPeriodo, BigDecimal ingresoEnPeriodo) {}

    // ── Reporte completo por rango de fechas ──────────────────
    public ReporteCompleto generarReporte(LocalDate inicio, LocalDate fin) {
        if (inicio.isAfter(fin)) {
            throw new IllegalArgumentException(
                    "La fecha de inicio no puede ser posterior a la de fin."
            );
        }

        List<Reserva> enPeriodo    = reservaRepo.findEnPeriodo(inicio, fin);
        int           totalHabs    = (int) habitacionRepo.count();
        int           diasPeriodo  = (int) ChronoUnit.DAYS.between(inicio, fin) + 1;

        // Filtrar reservas que realmente tienen al menos 1 noche en el período
        List<Reserva> activasConNoches = enPeriodo.stream()
                .filter(r -> calcularNochesEIngreso(r, inicio, fin).nochesEnPeriodo() > 0)
                .toList();

        // Checkouts cobrados que tuvieron noches en el período
        List<Reserva> facturadas = activasConNoches.stream()
                .filter(r -> r.getEstado() == EstadoReserva.CHECKOUT)
                .toList();

        // Capacidad por tipo de habitación (para ocupación por tipo)
        Map<String, Long> habsPorTipo = habitacionRepo.countPorTipo().stream()
                .collect(Collectors.toMap(
                        fila -> ((Enum<?>) fila[0]).name(),
                        fila -> (Long) fila[1]
                ));

        return new ReporteCompleto(
                inicio.format(FMT),
                fin.format(FMT),
                calcularResumen(
                        reservaRepo.countActivasEnPeriodo(inicio, fin),
                        reservaRepo.countCanceladasEnPeriodo(inicio, fin),
                        facturadas, activasConNoches, totalHabs, inicio, fin, diasPeriodo),
                calcularOcupacionPorDia(activasConNoches, totalHabs, inicio, fin),
                calcularRendimientoPorTipo(facturadas, activasConNoches, habsPorTipo, diasPeriodo, inicio, fin),
                calcularTopHabitaciones(facturadas, inicio, fin),
                totalHabs
        );
    }

    // ── Resumen del período ───────────────────────────────────
    private ResumenPeriodo calcularResumen(
            long reservasActivas,
            long cancelaciones,
            List<Reserva> facturadas,
            List<Reserva> enPeriodo,
            int totalHabs,
            LocalDate inicio,
            LocalDate fin,
            int diasPeriodo) {

        long nochesVendidas = 0;
        BigDecimal ingresoHabitaciones = BigDecimal.ZERO;

        for (Reserva r : facturadas) {
            NochesEIngreso ni = calcularNochesEIngreso(r, inicio, fin);
            nochesVendidas += ni.nochesEnPeriodo();
            ingresoHabitaciones = ingresoHabitaciones.add(ni.ingresoEnPeriodo());
        }

        // Consumo de servicio de habitación servido (ENTREGADO) en el período
        BigDecimal ingresoConsumo = pedidoRepo.sumarConsumoEntre(
                inicio.atStartOfDay(),
                fin.plusDays(1).atStartOfDay()
        );
        if (ingresoConsumo == null) ingresoConsumo = BigDecimal.ZERO;

        BigDecimal ingresoTotal = ingresoHabitaciones.add(ingresoConsumo);

        BigDecimal ingresoPorNoche = nochesVendidas > 0
                ? ingresoTotal.divide(BigDecimal.valueOf(nochesVendidas), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Ocupación real: noches efectivas de CHECKIN y CHECKOUT dentro del período
        long ocupacionesTotales = enPeriodo.stream()
                .filter(r -> r.getEstado() == EstadoReserva.CHECKIN || r.getEstado() == EstadoReserva.CHECKOUT)
                .mapToLong(r -> calcularNochesEIngreso(r, inicio, fin).nochesEnPeriodo())
                .sum();

        double ocupacionPromedio = totalHabs > 0 && diasPeriodo > 0
                ? (double) ocupacionesTotales / (totalHabs * diasPeriodo) * 100
                : 0;

        return new ResumenPeriodo(
                enPeriodo.size(),
                (int) reservasActivas,
                facturadas.size(),
                (int) cancelaciones,
                ingresoTotal,
                ingresoConsumo,
                ingresoPorNoche,
                Math.min(Math.round(ocupacionPromedio * 10.0) / 10.0, 100),
                nochesVendidas
        );
    }

    // ── Ocupación día por día ─────────────────────────────────
    private List<OcupacionDia> calcularOcupacionPorDia(
            List<Reserva> enPeriodo,
            int totalHabs,
            LocalDate inicio,
            LocalDate fin) {

        List<Reserva> ocupacionReal = enPeriodo.stream()
                .filter(r -> r.getEstado() == EstadoReserva.CHECKIN || r.getEstado() == EstadoReserva.CHECKOUT)
                .toList();

        int dias = (int) ChronoUnit.DAYS.between(inicio, fin) + 1;
        LocalDate finExclusivo = fin.plusDays(1);

        long[]       ocupadas = new long[dias];
        BigDecimal[] ingreso  = new BigDecimal[dias];
        Arrays.fill(ingreso, BigDecimal.ZERO);

        for (Reserva r : ocupacionReal) {
            LocalDate entrada = r.getFechaEntrada();
            LocalDate salida = resolverSalidaEfectiva(r);

            LocalDate desde = entrada.isBefore(inicio) ? inicio : entrada;
            LocalDate hasta = salida.isAfter(finExclusivo) ? finExclusivo : salida;

            if (!hasta.isAfter(desde)) continue;

            int desdeIdx = (int) ChronoUnit.DAYS.between(inicio, desde);
            int hastaIdx = (int) ChronoUnit.DAYS.between(inicio, hasta);

            ocupadas[desdeIdx]++;
            if (hastaIdx < dias) {
                ocupadas[hastaIdx]--;
            }

            if (r.getEstado() == EstadoReserva.CHECKOUT) {
                long nochesTotales = Math.max(1, ChronoUnit.DAYS.between(entrada, salida));
                BigDecimal precioNoche = (r.getHabitacion() != null && r.getHabitacion().getPrecioNoche() != null)
                        ? r.getHabitacion().getPrecioNoche()
                        : BigDecimal.ZERO;
                BigDecimal totalHabitacionYExtra = precioNoche.multiply(BigDecimal.valueOf(r.calcularNochesFacturables()))
                        .add(r.getCargoHorasExtra() != null ? r.getCargoHorasExtra() : BigDecimal.ZERO);
                BigDecimal valorNoche = totalHabitacionYExtra.divide(BigDecimal.valueOf(nochesTotales), 4, RoundingMode.HALF_UP);

                ingreso[desdeIdx] = ingreso[desdeIdx].add(valorNoche);
                if (hastaIdx < dias) {
                    ingreso[hastaIdx] = ingreso[hastaIdx].subtract(valorNoche);
                }
            }
        }

        List<OcupacionDia> resultado = new ArrayList<>(dias);
        long       acumOcupadas = 0;
        BigDecimal acumIngreso  = BigDecimal.ZERO;

        for (int i = 0; i < dias; i++) {
            acumOcupadas += ocupadas[i];
            acumIngreso   = acumIngreso.add(ingreso[i]);

            LocalDate dia = inicio.plusDays(i);
            double pct = totalHabs > 0
                    ? Math.min((double) acumOcupadas / totalHabs * 100, 100)
                    : 0;

            resultado.add(new OcupacionDia(
                    dia.format(FMT),
                    (int) acumOcupadas,
                    totalHabs,
                    Math.round(pct * 10.0) / 10.0,
                    acumIngreso.setScale(2, RoundingMode.HALF_UP)
            ));
        }

        return resultado;
    }

    // ── Rendimiento por tipo de habitación ────────────────────
    private List<RendimientoTipo> calcularRendimientoPorTipo(
            List<Reserva> facturadas,
            List<Reserva> enPeriodo,
            Map<String, Long> habsPorTipo,
            int diasPeriodo,
            LocalDate inicio,
            LocalDate fin) {

        Map<String, Long> nochesOcupPorTipo = new HashMap<>();
        for (Reserva r : enPeriodo) {
            if (r.getEstado() != EstadoReserva.CHECKIN && r.getEstado() != EstadoReserva.CHECKOUT) {
                continue;
            }
            long noches = calcularNochesEIngreso(r, inicio, fin).nochesEnPeriodo();
            if (noches == 0) continue;

            String tipo = r.getHabitacion().getTipo().name();
            nochesOcupPorTipo.merge(tipo, noches, Long::sum);
        }

        Map<String, List<Reserva>> porTipo = facturadas.stream()
                .collect(Collectors.groupingBy(r -> r.getHabitacion().getTipo().name()));

        return porTipo.entrySet().stream()
                .map(e -> {
                    String tipo = e.getKey();
                    List<Reserva> lista = e.getValue();

                    long noches = 0;
                    BigDecimal ingreso = BigDecimal.ZERO;
                    for (Reserva r : lista) {
                        NochesEIngreso ni = calcularNochesEIngreso(r, inicio, fin);
                        noches += ni.nochesEnPeriodo();
                        ingreso = ingreso.add(ni.ingresoEnPeriodo());
                    }

                    long capacidad = habsPorTipo.getOrDefault(tipo, 0L);
                    double ocupacionPct = capacidad > 0 && diasPeriodo > 0
                            ? Math.min(100.0,
                            (double) nochesOcupPorTipo.getOrDefault(tipo, 0L)
                                    / (capacidad * diasPeriodo) * 100)
                            : 0;

                    return new RendimientoTipo(
                            tipo,
                            lista.size(),
                            noches,
                            ingreso,
                            Math.round(ocupacionPct * 10.0) / 10.0
                    );
                })
                .sorted(Comparator.comparing(RendimientoTipo::ingresoTotal).reversed())
                .toList();
    }

    // ── Top habitaciones más rentables ────────────────────────
    private List<HabitacionTop> calcularTopHabitaciones(
            List<Reserva> facturadas,
            LocalDate inicio,
            LocalDate fin) {

        Map<String, List<Reserva>> porHab = facturadas.stream()
                .collect(Collectors.groupingBy(r -> r.getHabitacion().getNumero()));

        return porHab.values().stream()
                .map(lista -> {
                    Reserva primera = lista.getFirst();

                    long noches = 0;
                    BigDecimal ingreso = BigDecimal.ZERO;
                    for (Reserva r : lista) {
                        NochesEIngreso ni = calcularNochesEIngreso(r, inicio, fin);
                        noches += ni.nochesEnPeriodo();
                        ingreso = ingreso.add(ni.ingresoEnPeriodo());
                    }

                    return new HabitacionTop(
                            primera.getHabitacion().getNumero(),
                            primera.getHabitacion().getTipo().name(),
                            lista.size(),
                            noches,
                            ingreso
                    );
                })
                .sorted(Comparator.comparing(HabitacionTop::ingresoTotal).reversed())
                .limit(10)
                .toList();
    }

    // ── Helper centralizado de noches e ingresos prorrateados ─
    public NochesEIngreso calcularNochesEIngreso(Reserva r, LocalDate inicio, LocalDate fin) {
        LocalDate entrada = r.getFechaEntrada();
        LocalDate salida = resolverSalidaEfectiva(r);

        // Noches totales de la estancia
        long nochesTotales = Math.max(1, ChronoUnit.DAYS.between(entrada, salida));

        // Rango del período semiabierto: [inicio, finExclusivo)
        LocalDate finExclusivo = fin.plusDays(1);
        LocalDate desde = entrada.isBefore(inicio) ? inicio : entrada;
        LocalDate hasta = salida.isAfter(finExclusivo) ? finExclusivo : salida;

        long nochesEnPeriodo = Math.max(0, ChronoUnit.DAYS.between(desde, hasta));
        if (nochesEnPeriodo == 0) {
            return new NochesEIngreso(0, BigDecimal.ZERO);
        }

        if (r.getEstado() != EstadoReserva.CHECKOUT) {
            return new NochesEIngreso(nochesEnPeriodo, BigDecimal.ZERO);
        }

        BigDecimal precioNoche = (r.getHabitacion() != null && r.getHabitacion().getPrecioNoche() != null)
                ? r.getHabitacion().getPrecioNoche()
                : BigDecimal.ZERO;

        BigDecimal totalHabitacionYExtra = precioNoche.multiply(BigDecimal.valueOf(r.calcularNochesFacturables()))
                .add(r.getCargoHorasExtra() != null ? r.getCargoHorasExtra() : BigDecimal.ZERO);

        BigDecimal valorPorNoche = totalHabitacionYExtra.divide(BigDecimal.valueOf(nochesTotales), 4, RoundingMode.HALF_UP);
        BigDecimal ingresoHabitacion = valorPorNoche.multiply(BigDecimal.valueOf(nochesEnPeriodo))
                .setScale(2, RoundingMode.HALF_UP);

        return new NochesEIngreso(nochesEnPeriodo, ingresoHabitacion);
    }

    private LocalDate resolverSalidaEfectiva(Reserva r) {
        LocalDate salida = r.getFechaSalida();
        if (r.getEstado() == EstadoReserva.CHECKOUT) {
            if (r.getHorasExtra() != null && r.getHorasExtra() > 0) {
                salida = salida.plusDays(r.getHorasExtra());
            } else if (r.getCheckoutReal() != null && r.getCheckoutReal().toLocalDate().isAfter(salida)) {
                salida = r.getCheckoutReal().toLocalDate();
            }
        }
        if (!salida.isAfter(r.getFechaEntrada())) {
            salida = r.getFechaEntrada().plusDays(1);
        }
        return salida;
    }
}
