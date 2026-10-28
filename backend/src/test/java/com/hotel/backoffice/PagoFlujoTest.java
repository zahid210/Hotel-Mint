package com.hotel.backoffice;

import com.hotel.backoffice.dto.request.PagoRequestDTO;
import com.hotel.backoffice.dto.response.CuentaReservaDTO;
import com.hotel.backoffice.dto.response.PagoResponseDTO;
import com.hotel.backoffice.entity.Habitacion;
import com.hotel.backoffice.entity.Habitacion.EstadoHabitacion;
import com.hotel.backoffice.entity.Habitacion.TipoHabitacion;
import com.hotel.backoffice.entity.Huesped;
import com.hotel.backoffice.entity.Huesped.TipoDocumento;
import com.hotel.backoffice.entity.Pago.EstadoPago;
import com.hotel.backoffice.entity.Reserva;
import com.hotel.backoffice.entity.Reserva.EstadoReserva;
import com.hotel.backoffice.entity.Usuario;
import com.hotel.backoffice.entity.Usuario.Rol;
import com.hotel.backoffice.repository.HabitacionRepository;
import com.hotel.backoffice.repository.HuespedRepository;
import com.hotel.backoffice.repository.ReservaRepository;
import com.hotel.backoffice.repository.UsuarioRepository;
import com.hotel.backoffice.service.PagoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PagoFlujoTest {

    @Autowired
    private PagoService pagoService;

    @Autowired
    private ReservaRepository reservaRepo;

    @Autowired
    private HabitacionRepository habitacionRepo;

    @Autowired
    private HuespedRepository huespedRepo;

    @Autowired
    private UsuarioRepository usuarioRepo;

    private Reserva crearReservaDemo() {
        Habitacion hab = habitacionRepo.save(Habitacion.builder()
                .numero("P" + (System.nanoTime() % 1000000))
                .piso(1)
                .tipo(TipoHabitacion.SIMPLE)
                .precioNoche(BigDecimal.valueOf(100.00))
                .estado(EstadoHabitacion.OCUPADA)
                .build());

        Huesped hue = huespedRepo.save(Huesped.builder()
                .nombre("Juan")
                .apellido("Perez")
                .tipoDocumento(TipoDocumento.DNI)
                .nroDocumento("DOC" + System.currentTimeMillis())
                .build());

        Usuario user = usuarioRepo.save(Usuario.builder()
                .nombre("Recepcionista")
                .email("recep" + System.currentTimeMillis() + "@hotel.com")
                .password("PasswordSegura123!")
                .rol(Rol.RECEPCIONISTA)
                .activo(true)
                .build());

        return reservaRepo.save(Reserva.builder()
                .habitacion(hab)
                .huesped(hue)
                .usuario(user)
                .fechaEntrada(LocalDate.now())
                .fechaSalida(LocalDate.now().plusDays(2))
                .horaEntradaAcordada(LocalTime.of(15, 0))
                .horaSalidaAcordada(LocalTime.of(12, 0))
                .estado(EstadoReserva.CHECKIN)
                .build());
    }

    @Test
    @DisplayName("P2-09: Registra pago parcial y pago total cerrando la cuenta")
    @WithMockUser(username = "recep_test@hotel.com", roles = "RECEPCIONISTA")
    void flujoCompletoPagosYCuenta() {
        Reserva reserva = crearReservaDemo();
        BigDecimal totalEstancia = reserva.calcularTotalEstancia(); // 2 noches x 100 = 200

        // 1. Estado de cuenta inicial: saldo pendiente = totalEstancia
        CuentaReservaDTO cuentaInicial = pagoService.obtenerEstadoCuenta(reserva.getId());
        assertEquals(0, cuentaInicial.totalPagado().compareTo(BigDecimal.ZERO));
        assertEquals(0, cuentaInicial.saldoPendiente().compareTo(totalEstancia));
        assertFalse(cuentaInicial.pagadoCompletamente());

        // 2. Registrar primer pago de S/ 100
        PagoResponseDTO pago1 = pagoService.registrarPago(reserva.getId(),
                new PagoRequestDTO(BigDecimal.valueOf(100.00), "EFECTIVO", "REC-001"));
        assertEquals("PAGADO", pago1.estadoPago());
        assertEquals(0, pago1.monto().compareTo(BigDecimal.valueOf(100.00)));

        CuentaReservaDTO cuentaParcial = pagoService.obtenerEstadoCuenta(reserva.getId());
        assertEquals(0, cuentaParcial.totalPagado().compareTo(BigDecimal.valueOf(100.00)));
        assertEquals(0, cuentaParcial.saldoPendiente().compareTo(BigDecimal.valueOf(100.00)));
        assertFalse(cuentaParcial.pagadoCompletamente());

        // 3. Registrar segundo pago completando la estancia
        PagoResponseDTO pago2 = pagoService.registrarPago(reserva.getId(),
                new PagoRequestDTO(BigDecimal.valueOf(100.00), "TARJETA", "TX-9988"));
        assertEquals("PAGADO", pago2.estadoPago());

        CuentaReservaDTO cuentaCompleta = pagoService.obtenerEstadoCuenta(reserva.getId());
        assertEquals(0, cuentaCompleta.totalPagado().compareTo(totalEstancia));
        assertEquals(0, cuentaCompleta.saldoPendiente().compareTo(BigDecimal.ZERO));
        assertTrue(cuentaCompleta.pagadoCompletamente());
        assertEquals(2, cuentaCompleta.pagos().size());
    }

    @Test
    @DisplayName("P2-09: Reembolsar un pago cambia su estado a REEMBOLSADO")
    @WithMockUser(username = "admin_reembolso@hotel.com", roles = "ADMIN")
    void reembolsarPago() {
        Reserva reserva = crearReservaDemo();
        PagoResponseDTO pago = pagoService.registrarPago(reserva.getId(),
                new PagoRequestDTO(BigDecimal.valueOf(50.00), "TRANSFERENCIA", "TRF-123"));

        PagoResponseDTO reembolsado = pagoService.reembolsarPago(pago.id());
        assertEquals("REEMBOLSADO", reembolsado.estadoPago());

        CuentaReservaDTO cuenta = pagoService.obtenerEstadoCuenta(reserva.getId());
        // El pago reembolsado ya no cuenta como PAGADO para el saldo
        assertEquals(0, cuenta.totalPagado().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("P2-09: No permite registrar pagos en reservas canceladas")
    @WithMockUser(roles = "RECEPCIONISTA")
    void noPermitePagosEnReservaCancelada() {
        Reserva reserva = crearReservaDemo();
        reserva.setEstado(EstadoReserva.CANCELADA);
        reservaRepo.save(reserva);

        assertThrows(IllegalStateException.class, () -> {
            pagoService.registrarPago(reserva.getId(),
                    new PagoRequestDTO(BigDecimal.valueOf(50.00), "EFECTIVO", null));
        });
    }
}
