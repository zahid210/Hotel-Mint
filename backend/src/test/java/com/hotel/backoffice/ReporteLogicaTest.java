package com.hotel.backoffice;

import com.hotel.backoffice.entity.Habitacion;
import com.hotel.backoffice.entity.Reserva;
import com.hotel.backoffice.entity.Reserva.EstadoReserva;
import com.hotel.backoffice.service.ReporteService;
import com.hotel.backoffice.service.ReporteService.NochesEIngreso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReporteLogicaTest {

    private ReporteService reporteService;

    @BeforeEach
    void setUp() {
        // ReporteService unit test focusing on calculation logic
        reporteService = new ReporteService(null, null, null);
    }

    private Reserva crearReserva(LocalDate entrada, LocalDate salida, BigDecimal precioNoche, EstadoReserva estado) {
        Habitacion hab = Habitacion.builder()
                .numero("101")
                .precioNoche(precioNoche)
                .tipo(Habitacion.TipoHabitacion.SIMPLE)
                .build();

        return Reserva.builder()
                .habitacion(hab)
                .fechaEntrada(entrada)
                .fechaSalida(salida)
                .horaEntradaAcordada(LocalTime.of(15, 0))
                .horaSalidaAcordada(LocalTime.of(12, 0))
                .estado(estado)
                .build();
    }

    @Test
    void estanciaCruzandoMesProrrateaIngresosYNochesSinDuplicacion() {
        // Estancia del 30 de enero al 2 de febrero (3 noches: 30 ene, 31 ene, 01 feb)
        // Precio por noche = 100
        Reserva reserva = crearReserva(
                LocalDate.of(2026, 1, 30),
                LocalDate.of(2026, 2, 2),
                BigDecimal.valueOf(100),
                EstadoReserva.CHECKOUT
        );

        // Reporte de Enero (1 al 31 de enero)
        LocalDate inicioEnero = LocalDate.of(2026, 1, 1);
        LocalDate finEnero = LocalDate.of(2026, 1, 31);
        NochesEIngreso resultadoEnero = reporteService.calcularNochesEIngreso(reserva, inicioEnero, finEnero);

        assertEquals(2, resultadoEnero.nochesEnPeriodo(), "En enero corresponden 2 noches (30 y 31)");
        assertEquals(new BigDecimal("200.00"), resultadoEnero.ingresoEnPeriodo(), "En enero corresponden 200");

        // Reporte de Febrero (1 al 28 de febrero)
        LocalDate inicioFebrero = LocalDate.of(2026, 2, 1);
        LocalDate finFebrero = LocalDate.of(2026, 2, 28);
        NochesEIngreso resultadoFebrero = reporteService.calcularNochesEIngreso(reserva, inicioFebrero, finFebrero);

        assertEquals(1, resultadoFebrero.nochesEnPeriodo(), "En febrero corresponde 1 noche (1 feb)");
        assertEquals(new BigDecimal("100.00"), resultadoFebrero.ingresoEnPeriodo(), "En febrero corresponden 100");

        // Suma total entre enero y febrero debe dar exactamente 3 noches y 300 soles
        assertEquals(3, resultadoEnero.nochesEnPeriodo() + resultadoFebrero.nochesEnPeriodo());
        assertEquals(new BigDecimal("300.00"), resultadoEnero.ingresoEnPeriodo().add(resultadoFebrero.ingresoEnPeriodo()));
    }

    @Test
    void checkoutElMismoDiaDeInicioNoGeneraNochesEnPeriodo() {
        // Huésped que llegó el 28 de enero y hace checkout el 1 de febrero a las 11:00 am
        Reserva reserva = crearReserva(
                LocalDate.of(2026, 1, 28),
                LocalDate.of(2026, 2, 1),
                BigDecimal.valueOf(100),
                EstadoReserva.CHECKOUT
        );

        // En el reporte de Febrero (1 al 28 de feb)
        LocalDate inicioFebrero = LocalDate.of(2026, 2, 1);
        LocalDate finFebrero = LocalDate.of(2026, 2, 28);
        NochesEIngreso resultadoFebrero = reporteService.calcularNochesEIngreso(reserva, inicioFebrero, finFebrero);

        assertEquals(0, resultadoFebrero.nochesEnPeriodo(), "No durmió en febrero; salió la mañana del 1 de feb");
        assertEquals(BigDecimal.ZERO, resultadoFebrero.ingresoEnPeriodo());
    }

    @Test
    void lateCheckoutConNocheExtraSeComputaCorrectamente() {
        // Estancia acordada 14 al 16 de marzo (2 noches), pero salió el 17 de marzo con late checkout (+1 noche extra)
        Reserva reserva = crearReserva(
                LocalDate.of(2026, 3, 14),
                LocalDate.of(2026, 3, 16),
                BigDecimal.valueOf(100),
                EstadoReserva.CHECKOUT
        );
        reserva.calcularCargoLateCheckout(LocalDateTime.of(2026, 3, 17, 2, 0));

        LocalDate inicioMarzo = LocalDate.of(2026, 3, 1);
        LocalDate finMarzo = LocalDate.of(2026, 3, 31);
        NochesEIngreso resultado = reporteService.calcularNochesEIngreso(reserva, inicioMarzo, finMarzo);

        assertEquals(3, resultado.nochesEnPeriodo(), "Debe computar 2 noches pactadas + 1 noche de late checkout");
        assertEquals(new BigDecimal("300.00"), resultado.ingresoEnPeriodo(), "Debe totalizar 300 con la noche extra");
    }
}
