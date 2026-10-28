package com.hotel.backoffice;

import com.hotel.backoffice.entity.Habitacion;
import com.hotel.backoffice.entity.Habitacion.EstadoHabitacion;
import com.hotel.backoffice.entity.Pedido;
import com.hotel.backoffice.entity.Reserva;
import com.hotel.backoffice.entity.Reserva.EstadoReserva;
import com.hotel.backoffice.repository.HabitacionRepository;
import com.hotel.backoffice.repository.PedidoRepository;
import com.hotel.backoffice.repository.ReservaRepository;
import com.hotel.backoffice.service.HabitacionService;
import com.hotel.backoffice.service.ReservaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class HabitacionYReservaIntegridadTest {

    private HabitacionRepository habitRepo;
    private ReservaRepository reservaRepo;
    private PedidoRepository pedidoRepo;
    private HabitacionService habitacionService;
    private ReservaService reservaService;

    @BeforeEach
    void setUp() {
        habitRepo = Mockito.mock(HabitacionRepository.class);
        reservaRepo = Mockito.mock(ReservaRepository.class);
        pedidoRepo = Mockito.mock(PedidoRepository.class);
        com.hotel.backoffice.service.AuditoriaService auditMock = Mockito.mock(com.hotel.backoffice.service.AuditoriaService.class);

        habitacionService = new HabitacionService(habitRepo, reservaRepo, auditMock);
        reservaService = new ReservaService(reservaRepo, habitRepo, null, null, pedidoRepo, auditMock, java.time.Clock.systemDefaultZone());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(String rol) {
        var auth = new UsernamePasswordAuthenticationToken(
                "test@hotel.com",
                "pass",
                List.of(new SimpleGrantedAuthority(rol))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void nadiePuedeMarcarOcupadaManualmente() {
        autenticarComo("ROLE_ADMIN");
        Habitacion hab = Habitacion.builder().id(1).numero("101").estado(EstadoHabitacion.LIBRE).build();
        when(habitRepo.findWithLockById(1)).thenReturn(Optional.of(hab));

        assertThrows(IllegalArgumentException.class, () ->
                habitacionService.cambiarEstado(1, "OCUPADA")
        );
    }

    @Test
    void personalLimpiezaNoPuedeLiberarHabitacionOcupada() {
        autenticarComo("ROLE_LIMPIEZA");
        Habitacion hab = Habitacion.builder().id(1).numero("101").estado(EstadoHabitacion.OCUPADA).build();
        when(habitRepo.findWithLockById(1)).thenReturn(Optional.of(hab));

        assertThrows(IllegalArgumentException.class, () ->
                habitacionService.cambiarEstado(1, "LIBRE")
        );
    }

    @Test
    void recepcionNoPuedeLiberarHabitacionConHuespedEnCheckIn() {
        autenticarComo("ROLE_RECEPCIONISTA");
        Habitacion hab = Habitacion.builder().id(1).numero("101").estado(EstadoHabitacion.OCUPADA).build();
        when(habitRepo.findWithLockById(1)).thenReturn(Optional.of(hab));
        when(reservaRepo.existsByHabitacionIdAndEstado(1, EstadoReserva.CHECKIN)).thenReturn(true);

        assertThrows(IllegalStateException.class, () ->
                habitacionService.cambiarEstado(1, "LIBRE")
        );
    }

    @Test
    void personalLimpiezaPuedePasarDeLimpiezaALibre() {
        autenticarComo("ROLE_LIMPIEZA");
        Habitacion hab = Habitacion.builder().id(1).numero("101").estado(EstadoHabitacion.LIMPIEZA).precioNoche(BigDecimal.valueOf(100)).tipo(Habitacion.TipoHabitacion.SIMPLE).build();
        when(habitRepo.findWithLockById(1)).thenReturn(Optional.of(hab));

        var resp = habitacionService.cambiarEstado(1, "LIBRE");
        assertEquals("LIBRE", resp.estado());
        assertEquals(EstadoHabitacion.LIBRE, hab.getEstado());
    }

    @Test
    void checkOutBloqueadoSiExistenPedidosPendientes() {
        UUID reservaId = UUID.randomUUID();
        Habitacion hab = Habitacion.builder().id(1).numero("101").estado(EstadoHabitacion.OCUPADA).precioNoche(BigDecimal.valueOf(100)).tipo(Habitacion.TipoHabitacion.SIMPLE).build();
        Reserva reserva = Reserva.builder()
                .id(reservaId)
                .habitacion(hab)
                .estado(EstadoReserva.CHECKIN)
                .fechaEntrada(LocalDate.now().minusDays(1))
                .fechaSalida(LocalDate.now())
                .horaSalidaAcordada(LocalTime.of(12, 0))
                .build();

        when(reservaRepo.findByIdAndEstado(reservaId, EstadoReserva.CHECKIN)).thenReturn(Optional.of(reserva));
        when(pedidoRepo.existsByReservaIdAndEstado(reservaId, Pedido.EstadoPedido.PENDIENTE)).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                reservaService.realizarCheckOut(reservaId)
        );
        assertTrue(ex.getMessage().contains("pedidos pendientes"));
    }

    @Test
    void checkOutExitosoSiNoHayPedidosPendientes() {
        UUID reservaId = UUID.randomUUID();
        Habitacion hab = Habitacion.builder().id(1).numero("101").estado(EstadoHabitacion.OCUPADA).precioNoche(BigDecimal.valueOf(100)).tipo(Habitacion.TipoHabitacion.SIMPLE).build();
        com.hotel.backoffice.entity.Huesped huesped = com.hotel.backoffice.entity.Huesped.builder()
                .nombre("Juan").apellido("Pérez").nroDocumento("12345678").tipoDocumento(com.hotel.backoffice.entity.Huesped.TipoDocumento.DNI).build();

        Reserva reserva = Reserva.builder()
                .id(reservaId)
                .habitacion(hab)
                .huesped(huesped)
                .estado(EstadoReserva.CHECKIN)
                .fechaEntrada(LocalDate.now().minusDays(1))
                .fechaSalida(LocalDate.now())
                .horaEntradaAcordada(LocalTime.of(15, 0))
                .horaSalidaAcordada(LocalTime.of(12, 0))
                .build();

        when(reservaRepo.findByIdAndEstado(reservaId, EstadoReserva.CHECKIN)).thenReturn(Optional.of(reserva));
        when(pedidoRepo.existsByReservaIdAndEstado(reservaId, Pedido.EstadoPedido.PENDIENTE)).thenReturn(false);

        var resp = reservaService.realizarCheckOut(reservaId);
        assertEquals("CHECKOUT", resp.estado());
        assertEquals(EstadoHabitacion.LIMPIEZA, hab.getEstado());
        assertEquals("LIMPIEZA", resp.habitacionEstado());
    }
}
