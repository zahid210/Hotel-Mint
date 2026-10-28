package com.hotel.backoffice.config;

import com.hotel.backoffice.entity.*;
import com.hotel.backoffice.entity.Habitacion.*;
import com.hotel.backoffice.entity.Huesped.TipoDocumento;
import com.hotel.backoffice.entity.Pedido.EstadoPedido;
import com.hotel.backoffice.entity.Reserva.EstadoReserva;
import com.hotel.backoffice.entity.Usuario.Rol;
import com.hotel.backoffice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@Profile({"local", "docker"})
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final HabitacionRepository habitRepo;
    private final UsuarioRepository    usuarioRepo;
    private final MenuItemRepository   menuRepo;
    private final PedidoItemRepository pedidoItemRepo;
    private final HuespedRepository    huespedRepo;
    private final ReservaRepository    reservaRepo;
    private final PedidoRepository     pedidoRepo;
    private final PasswordEncoder      encoder;

    @Value("${app.admin.email:zahidmatos@hotel.com}")   private String adminEmail;
    @Value("${app.admin.nombre:Zahid Matos}")           private String adminNombre;
    @Value("${app.admin.password:}")                    private String adminPassword;
    @Value("${app.seed-demo:false}")                    private boolean seedDemo;

    @Override
    public void run(String... args) {
        seedHabitaciones();
        seedMenu();

        List<Usuario> usuarios = seedUsuarios();
        if (seedDemo) {
            log.info("Sembrando datos ficticios de demostración (reservas, huéspedes, pedidos)...");
            seedDemoOperacion(usuarios);
        } else {
            log.info("app.seed-demo=false: No se sembraron datos ficticios de reservas ni pedidos.");
        }
    }

    // ── Habitaciones (50, piso 1-5) ────────────────────────────
    private void seedHabitaciones() {
        if (habitRepo.count() > 0) return;

        habitRepo.saveAll(List.of(
                hab("101", 1, TipoHabitacion.SIMPLE,   2,  80.00),
                hab("102", 1, TipoHabitacion.SIMPLE,   2,  80.00),
                hab("103", 1, TipoHabitacion.DOBLE,    4, 120.00),
                hab("104", 1, TipoHabitacion.DOBLE,    4, 120.00),
                hab("105", 1, TipoHabitacion.FAMILIAR, 6, 180.00),
                hab("106", 1, TipoHabitacion.SIMPLE,   2,  80.00),
                hab("107", 1, TipoHabitacion.DOBLE,    4, 120.00),
                hab("108", 1, TipoHabitacion.SUITE,    2, 250.00),
                hab("109", 1, TipoHabitacion.FAMILIAR, 6, 180.00),
                hab("110", 1, TipoHabitacion.SIMPLE,   2,  80.00),

                hab("201", 2, TipoHabitacion.SUITE,    2, 250.00),
                hab("202", 2, TipoHabitacion.SIMPLE,   2,  80.00),
                hab("203", 2, TipoHabitacion.DOBLE,    4, 120.00),
                hab("204", 2, TipoHabitacion.FAMILIAR, 6, 180.00),
                hab("205", 2, TipoHabitacion.DOBLE,    4, 120.00),
                hab("206", 2, TipoHabitacion.SIMPLE,   2,  80.00),
                hab("207", 2, TipoHabitacion.SUITE,    2, 250.00),
                hab("208", 2, TipoHabitacion.DOBLE,    4, 120.00),
                hab("209", 2, TipoHabitacion.FAMILIAR, 6, 180.00),
                hab("210", 2, TipoHabitacion.SIMPLE,   2,  80.00),

                hab("301", 3, TipoHabitacion.SUITE,    2, 250.00),
                hab("302", 3, TipoHabitacion.SIMPLE,   2,  80.00),
                hab("303", 3, TipoHabitacion.DOBLE,    4, 120.00),
                hab("304", 3, TipoHabitacion.FAMILIAR, 6, 180.00),
                hab("305", 3, TipoHabitacion.SIMPLE,   2,  80.00),
                hab("306", 3, TipoHabitacion.DOBLE,    4, 120.00),
                hab("307", 3, TipoHabitacion.SUITE,    2, 250.00),
                hab("308", 3, TipoHabitacion.SIMPLE,   2,  80.00),
                hab("309", 3, TipoHabitacion.FAMILIAR, 6, 180.00),
                hab("310", 3, TipoHabitacion.DOBLE,    4, 120.00),

                hab("401", 4, TipoHabitacion.SIMPLE,   2,  80.00),
                hab("402", 4, TipoHabitacion.SIMPLE,   2,  80.00),
                hab("403", 4, TipoHabitacion.DOBLE,    4, 120.00),
                hab("404", 4, TipoHabitacion.DOBLE,    4, 120.00),
                hab("405", 4, TipoHabitacion.FAMILIAR, 6, 180.00),
                hab("406", 4, TipoHabitacion.SUITE,    2, 250.00),
                hab("407", 4, TipoHabitacion.SIMPLE,   2,  80.00),
                hab("408", 4, TipoHabitacion.DOBLE,    4, 120.00),
                hab("409", 4, TipoHabitacion.FAMILIAR, 6, 180.00),
                hab("410", 4, TipoHabitacion.SUITE,    2, 250.00),

                hab("501", 5, TipoHabitacion.SUITE,    2, 280.00),
                hab("502", 5, TipoHabitacion.SUITE,    2, 280.00),
                hab("503", 5, TipoHabitacion.FAMILIAR, 6, 200.00),
                hab("504", 5, TipoHabitacion.FAMILIAR, 6, 200.00),
                hab("505", 5, TipoHabitacion.DOBLE,    4, 140.00),
                hab("506", 5, TipoHabitacion.DOBLE,    4, 140.00),
                hab("507", 5, TipoHabitacion.SIMPLE,   2,  90.00),
                hab("508", 5, TipoHabitacion.SIMPLE,   2,  90.00),
                hab("509", 5, TipoHabitacion.SUITE,    2, 280.00),
                hab("510", 5, TipoHabitacion.SUITE,    2, 280.00)
        ));
    }

    // ── Menú de bocaditos ──────────────────────────────────────
    private void seedMenu() {
        if (menuRepo.count() > 0) return;
        menuRepo.saveAll(menuBocaditos());
    }

    // ── Usuarios (admin + demo recepción/limpieza) ─────────────
    private List<Usuario> seedUsuarios() {
        List<Usuario> existentes = usuarioRepo.findAll();
        if (!existentes.isEmpty()) return existentes;

        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("No se creó el usuario administrador inicial: "
                    + "define APP_ADMIN_PASSWORD (perfil local/docker).");
        } else {
            usuarioRepo.save(Usuario.builder()
                    .nombre(adminNombre)
                    .email(adminEmail)
                    .password(encoder.encode(adminPassword))
                    .rol(Rol.ADMIN)
                    .activo(true)
                    .build());
            log.info("Usuario administrador inicial creado: {}", adminEmail);
        }

        if (seedDemo) {
            log.warn("app.seed-demo=true: Creando usuarios demo (recepción/limpieza) con credenciales por defecto.");
            usuarioRepo.save(Usuario.builder()
                    .nombre("María Quispe")
                    .email("recep@hotel.com")
                    .password(encoder.encode("recep123"))
                    .rol(Rol.RECEPCIONISTA)
                    .activo(true)
                    .build());

            usuarioRepo.save(Usuario.builder()
                    .nombre("Juan Huamán")
                    .email("limp@hotel.com")
                    .password(encoder.encode("limp123"))
                    .rol(Rol.LIMPIEZA)
                    .activo(true)
                    .build());
        }

        return usuarioRepo.findAll();
    }

    // ── Datos ficticios de operación (reservas, pedidos, estados) ──
    private void seedDemoOperacion(List<Usuario> usuarios) {
        if (reservaRepo.count() > 0) return;

        Map<String, Habitacion> habs = habitRepo.findAllByOrderByNumeroAsc().stream()
                .collect(Collectors.toMap(Habitacion::getNumero, Function.identity()));
        Map<String, MenuItem>   menu = menuRepo.findAll().stream()
                .collect(Collectors.toMap(MenuItem::getNombre, Function.identity()));

        Usuario recepcionista = usuarios.stream()
                .filter(u -> u.getRol() == Rol.RECEPCIONISTA)
                .findFirst().orElse(usuarios.getFirst());
        Usuario admin = usuarios.stream()
                .filter(u -> u.getRol() == Rol.ADMIN)
                .findFirst().orElse(recepcionista);

        LocalDate hoy = LocalDate.now();
        LocalTime HORA_IN = LocalTime.of(15, 0);
        LocalTime HORA_OUT = LocalTime.of(12, 0);

        // ── Huéspedes ficticios ──────────────────────────────
        Map<String, Huesped> huesped = huespedes().stream()
                .collect(Collectors.toMap(Huesped::getNroDocumento, Function.identity()));
        huespedRepo.saveAll(huesped.values());

        // ── Definición de reservas────────────────────────────
        // record interno: (habitación, dni huésped, entrada, salida, estado,
        //                  huéspedes, horas extra, notas, consumo ya facturado)
        record Rev(String hab, String dni, LocalDate in, LocalDate out,
                   EstadoReserva estado, short n, int horasExtra,
                   String notas, BigDecimal consumo) {}
        List<Rev> revs = List.of(
                // ── Check-outs de este mes (habitaciones ya libres) ──
                new Rev("103", "42812345", hoy.minusDays(6), hoy.minusDays(3),  EstadoReserva.CHECKOUT, (short) 2, 0, "Familiy a la playa", BigDecimal.ZERO),
                new Rev("105", "45671234", hoy.minusDays(9), hoy.minusDays(7),  EstadoReserva.CHECKOUT, (short) 4, 0, "", new BigDecimal("19.00")),
                new Rev("109", "98765432", hoy.minusDays(15), hoy.minusDays(12), EstadoReserva.CHECKOUT, (short) 5, 0, "", new BigDecimal("12.00")),
                new Rev("110", "46987654", hoy.minusDays(22), hoy.minusDays(20), EstadoReserva.CHECKOUT, (short) 2, 0, "", BigDecimal.ZERO),
                new Rev("203", "40123876", hoy.minusDays(19), hoy.minusDays(18), EstadoReserva.CHECKOUT, (short) 2, 0, "", new BigDecimal("9.00")),
                new Rev("206", "001234567", hoy.minusDays(26), hoy.minusDays(24), EstadoReserva.CHECKOUT, (short) 2, 0, "", BigDecimal.ZERO),
                new Rev("209", "48765432", hoy.minusDays(5), hoy.minusDays(1),   EstadoReserva.CHECKOUT, (short) 4, 0, "", new BigDecimal("13.00")),
                new Rev("210", "39876543", hoy.minusDays(3), hoy.minusDays(2),  EstadoReserva.CHECKOUT, (short) 1, 0, "", BigDecimal.ZERO),
                new Rev("306", "46120987", hoy.minusDays(27), hoy.minusDays(24), EstadoReserva.CHECKOUT, (short) 3, 0, "", BigDecimal.ZERO),
                // Late check-out (1 noche extra en el cargo)
                new Rev("304", "46987654", hoy.minusDays(20), hoy.minusDays(18), EstadoReserva.CHECKOUT, (short) 3, 1, "Pasó la medianoche", new BigDecimal("15.00")),
                // Check-out HOY → habitaciones quedan en limpieza
                new Rev("308", "47543210", hoy.minusDays(2), hoy, EstadoReserva.CHECKOUT, (short) 2, 0, "", new BigDecimal("4.50")),
                new Rev("401", "654321xx", hoy.minusDays(1), hoy, EstadoReserva.CHECKOUT, (short) 1, 0, "", new BigDecimal("6.50")),

                // ── Huéspedes en hotel (check-in hecho, habitación OCUPADA) ──
                new Rev("104", "42812345", hoy.minusDays(1), hoy.plusDays(1), EstadoReserva.CHECKIN, (short) 2, 0, "", new BigDecimal("6.50")),
                new Rev("108", "12345678", hoy.minusDays(2), hoy,             EstadoReserva.CHECKIN, (short) 2, 0, "", new BigDecimal("6.50")),
                new Rev("205", "001234567", hoy.minusDays(3), hoy.plusDays(2), EstadoReserva.CHECKIN, (short) 2, 0, "", new BigDecimal("12.00")),
                new Rev("301", "98765432", hoy, hoy.plusDays(2),             EstadoReserva.CHECKIN, (short) 1, 0, "", BigDecimal.ZERO),
                new Rev("403", "48765432", hoy.minusDays(1), hoy.plusDays(3), EstadoReserva.CHECKIN, (short) 2, 0, "", BigDecimal.ZERO),
                new Rev("501", "654321xx", hoy.minusDays(2), hoy.plusDays(1), EstadoReserva.CHECKIN, (short) 2, 0, "", new BigDecimal("6.00")),
                // Excedida: sigue en hotel pero su fecha de salida ya pasó
                new Rev("102", "39876543", hoy.minusDays(3), hoy.minusDays(1), EstadoReserva.CHECKIN, (short) 1, 0, "Pide quedarse unos días más", BigDecimal.ZERO),

                // ── Confirmadas para hoy (check-in pendiente) ──
                new Rev("101", "47543210", hoy, hoy.plusDays(1), EstadoReserva.CONFIRMADA, (short) 2, 0, "", BigDecimal.ZERO),
                new Rev("201", "40123876", hoy, hoy.plusDays(3), EstadoReserva.CONFIRMADA, (short) 2, 0, "", BigDecimal.ZERO),
                new Rev("305", "46120987", hoy, hoy.plusDays(2), EstadoReserva.CONFIRMADA, (short) 2, 0, "", BigDecimal.ZERO),

                // ── Confirmadas futuras ──────────────────────
                new Rev("107", "45671234", hoy.plusDays(3), hoy.plusDays(6), EstadoReserva.CONFIRMADA, (short) 2, 0, "", BigDecimal.ZERO),
                new Rev("302", "39876543", hoy.plusDays(5), hoy.plusDays(7), EstadoReserva.CONFIRMADA, (short) 1, 0, "", BigDecimal.ZERO),
                new Rev("404", "40123876", hoy.plusDays(7), hoy.plusDays(10), EstadoReserva.CONFIRMADA, (short) 2, 0, "", BigDecimal.ZERO),
                new Rev("504", "98765432", hoy.plusDays(10), hoy.plusDays(14), EstadoReserva.CONFIRMADA, (short) 5, 0, "", BigDecimal.ZERO),

                // ── Canceladas ───────────────────────────────
                new Rev("202", "46987654", hoy.minusDays(4), hoy.minusDays(2), EstadoReserva.CANCELADA, (short) 1, 0, "Problemas de salud", BigDecimal.ZERO),
                new Rev("307", "47543210", hoy.plusDays(1), hoy.plusDays(4), EstadoReserva.CANCELADA, (short) 2, 0, "Canceló antes de llegar", BigDecimal.ZERO)
        );

        record Ped(String hab, String dni, EstadoPedido estado, String notas,
                   Map<String, Integer> items) {}
        List<Ped> peds = List.of(
                // Pedidos de huéspedes en hotel
                new Ped("104", "42812345", EstadoPedido.PENDIENTE, "", Map.of("Papitas fritas", 2, "Inca Kola personal", 1)),
                new Ped("108", "12345678", EstadoPedido.ENTREGADO, "", Map.of("Galleta rellena de cacao", 1, "Coca-Cola personal", 1)),
                new Ped("108", "12345678", EstadoPedido.CANCELADO, "Cobra doble", Map.of("Agua de cebada", 1)),
                new Ped("205", "001234567", EstadoPedido.ENTREGADO, "", Map.of("Gatorade", 1, "Chizitos", 2)),
                new Ped("301", "98765432", EstadoPedido.PENDIENTE, "", Map.of("Agua mineral", 1, "Café instantáneo", 1)),
                new Ped("403", "48765432", EstadoPedido.PENDIENTE, "", Map.of("Cuates", 3)),
                new Ped("501", "654321xx", EstadoPedido.ENTREGADO, "", Map.of("Chocotejas", 2)),
                new Ped("102", "39876543", EstadoPedido.PENDIENTE, "Huésped excedido", Map.of("Agua de cebada", 1)),
                // Pedidos ya servidos en check-outs
                new Ped("105", "45671234", EstadoPedido.ENTREGADO, "", Map.of("Cuates", 2, "Inca Kola personal", 3)),
                new Ped("109", "98765432", EstadoPedido.ENTREGADO, "", Map.of("Gatorade", 1, "Agua mineral", 2)),
                new Ped("203", "40123876", EstadoPedido.ENTREGADO, "", Map.of("Papitas fritas", 1, "Sprite personal", 1)),
                new Ped("209", "48765432", EstadoPedido.ENTREGADO, "", Map.of("Chocotejas", 3, "Agua de cebada", 2)),
                new Ped("304", "46987654", EstadoPedido.ENTREGADO, "", Map.of("Coca-Cola personal", 3, "Café instantáneo", 1)),
                new Ped("308", "47543210", EstadoPedido.ENTREGADO, "", Map.of("Agua mineral", 1, "Galleta vainilla", 1)),
                new Ped("401", "654321xx", EstadoPedido.ENTREGADO, "", Map.of("Chizitos", 1, "Inca Kola personal", 1))
        );

        // ── Persistir reservas ────────────────────────────────
        Map<String, Reserva> porHab = new HashMap<>();
        List<Reserva> reservas = new ArrayList<>();
        for (Rev r : revs) {
            Habitacion hab = habs.get(r.hab());
            Huesped  hp   = huesped.get(r.dni());

            Reserva res = Reserva.builder()
                    .habitacion(hab)
                    .huesped(hp)
                    .usuario(recepcionista)
                    .fechaEntrada(r.in())
                    .fechaSalida(r.out())
                    .horaEntradaAcordada(HORA_IN)
                    .horaSalidaAcordada(HORA_OUT)
                    .numHuespedes(r.n())
                    .estado(r.estado())
                    .notas(r.notas().isBlank() ? null : r.notas())
                    .horasExtra(r.horasExtra())
                    .cargoHorasExtra(r.horasExtra() > 0
                            ? hab.getPrecioNoche().multiply(BigDecimal.valueOf(r.horasExtra()))
                            : BigDecimal.ZERO)
                    .totalConsumo(r.consumo())
                    .build();

            // Timestamps reales de operación
            if (r.estado() == EstadoReserva.CHECKIN || r.estado() == EstadoReserva.CHECKOUT) {
                res.setCheckinReal(r.in().atTime(14, 30));
            }
            if (r.estado() == EstadoReserva.CHECKOUT) {
                // Checkout dentro del día de salida; los late-checkouts cruzan la medianoche
                LocalDateTime checkout = r.horasExtra() > 0
                        ? r.out().plusDays(r.horasExtra()).atTime(2, 0)
                        : r.out().atTime(11, 30);
                res.setCheckoutReal(checkout);
            }

            reservaRepo.save(res);
            porHab.put(r.hab(), res);
            reservas.add(res);
        }

        // ── Persistir pedidos, sumando consumo ENTREGADO ─────
        Map<Reserva, BigDecimal> consumoPorReserva = new HashMap<>();
        for (Ped p : peds) {
            Reserva res = porHab.get(p.hab());
            Pedido pedido = Pedido.builder()
                    .reserva(res)
                    .habitacion(res.getHabitacion())
                    .usuario(recepcionista)
                    .estado(p.estado())
                    .notas(p.notas().isBlank() ? null : p.notas())
                    .build();

            BigDecimal total = BigDecimal.ZERO;
            for (Map.Entry<String, Integer> e : p.items().entrySet()) {
                MenuItem mi = menu.get(e.getKey());
                PedidoItem item = PedidoItem.builder()
                        .pedido(pedido)
                        .menuItem(mi)
                        .cantidad(e.getValue())
                        .precioUnitario(mi.getPrecio())
                        .build();
                item.setSubtotal(item.calcularSubtotal());
                total = total.add(item.getSubtotal());
                pedido.getItems().add(item);
            }
            pedido.setTotal(total.setScale(2, RoundingMode.HALF_UP));
            pedidoRepo.save(pedido);

            if (p.estado() == EstadoPedido.ENTREGADO) {
                consumoPorReserva.merge(res,
                        pedido.getTotal(), BigDecimal::add);
            }
        }

        // Sincronizar totalConsumo con lo efectivamente servido (ENTREGADO)
        for (Map.Entry<Reserva, BigDecimal> e : consumoPorReserva.entrySet()) {
            e.getKey().setTotalConsumo(
                    e.getValue().setScale(2, RoundingMode.HALF_UP));
        }
        reservaRepo.saveAll(reservas);

        // ── Estado físico de las habitaciones según la operación ──
        for (Reserva r : reservas) {
            String num = r.getHabitacion().getNumero();
            Habitacion hab = habs.get(num);
            if (r.getEstado() == EstadoReserva.CHECKIN) {
                hab.setEstado(EstadoHabitacion.OCUPADA);
            } else if (r.getEstado() == EstadoReserva.CHECKOUT
                    && r.getFechaSalida().equals(hoy)) {
                hab.setEstado(EstadoHabitacion.LIMPIEZA);
            } else if (r.getEstado() == EstadoReserva.CANCELADA) {
                hab.setEstado(EstadoHabitacion.LIBRE);
            }
        }
        // Ajustes puntuales para pantalla representativa
        habs.get("106").setEstado(EstadoHabitacion.LIMPIEZA);
        habs.get("208").setEstado(EstadoHabitacion.LIMPIEZA);
        habs.get("407").setEstado(EstadoHabitacion.MANTENIMIENTO);
        habs.get("506").setEstado(EstadoHabitacion.MANTENIMIENTO);
        habitRepo.saveAll(habs.values());

        log.info("Seed demo operativo: {} reservas, {} huéspedes, {} pedidos.",
                reservas.size(), huesped.size(), peds.size());
    }

    // ── Huéspedes ficticios ────────────────────────────────────
    private List<Huesped> huespedes() {
        return List.of(
                h("Carlos",  "Mendoza Torres",   TipoDocumento.DNI,       "42812345", "Perú"),
                h("Lucía",   "Fernández Rojas",  TipoDocumento.DNI,       "45671234", "Perú"),
                h("Robert",  "Wilson",           TipoDocumento.PASAPORTE, "12345678", "EE.UU."),
                h("Ana",     "Gutiérrez Díaz",   TipoDocumento.DNI,       "46987654", "Perú"),
                h("Marco",   "Sánchez Quispe",   TipoDocumento.DNI,       "40123876", "Perú"),
                h("Sofía",   "Ramírez",          TipoDocumento.CE,        "001234567", "Chile"),
                h("Jorge",   "Castro León",      TipoDocumento.DNI,       "48765432", "Perú"),
                h("Emma",    "Johnson",          TipoDocumento.PASAPORTE, "98765432", "Reino Unido"),
                h("Rosa",    "Huamán Condori",   TipoDocumento.DNI,       "39876543", "Perú"),
                h("Diego",   "Alvarado Ponce",   TipoDocumento.DNI,       "47543210", "Perú"),
                h("Valentina","Rossi",           TipoDocumento.PASAPORTE, "654321xx", "Italia"),
                h("Pedro",   "Quispe Luna",      TipoDocumento.DNI,       "46120987", "Perú")
        );
    }

    // ── Helpers ────────────────────────────────────────────────
    private Habitacion hab(String num, int piso, TipoHabitacion tipo,
                           int cap, double precio) {
        return Habitacion.builder()
                .numero(num).piso(piso).tipo(tipo).capacidad(cap)
                .precioNoche(BigDecimal.valueOf(precio))
                .estado(EstadoHabitacion.LIBRE)
                .build();
    }

    private MenuItem bocadito(String nombre, String descripcion, String categoria, double precio) {
        return MenuItem.builder()
                .nombre(nombre)
                .descripcion(descripcion)
                .categoria(categoria)
                .precio(BigDecimal.valueOf(precio))
                .disponible(true)
                .build();
    }

    private Huesped h(String nombre, String apellido, TipoDocumento tipo,
                      String nroDoc, String nacionalidad) {
        return Huesped.builder()
                .nombre(nombre)
                .apellido(apellido)
                .tipoDocumento(tipo)
                .nroDocumento(nroDoc)
                .nacionalidad(nacionalidad)
                .build();
    }

    // Bocaditos del menú inicial (snacks, galletas, dulces, gaseosas, bebidas)
    private List<MenuItem> menuBocaditos() {
        return List.of(
                bocadito("Cuates", "Bolsa 100 g", "SNACKS", 3.50),
                bocadito("Chizitos", "Bolsa 100 g", "SNACKS", 2.50),
                bocadito("Papitas fritas", "Bolsa 150 g", "SNACKS", 5.00),
                bocadito("Gusanitos", "Bolsa 100 g", "SNACKS", 2.50),
                bocadito("Canchita serrana", "Bolsa 120 g", "SNACKS", 4.00),
                bocadito("Maní confitado", "Bolsa 150 g", "SNACKS", 3.00),

                bocadito("Galleta soda", "Paquete 2 unidades", "GALLETAS", 1.50),
                bocadito("Galleta vainilla", "Paquete 2 unidades", "GALLETAS", 2.00),
                bocadito("Galleta rellena de cacao", "Paquete 2 unidades", "GALLETAS", 2.50),
                bocadito("Galleta con mermelada", "Paquete 2 unidades", "GALLETAS", 2.00),
                bocadito("Galleta integral", "Paquete 2 unidades", "GALLETAS", 2.00),

                bocadito("Chocolate oscuro", "Barra 50 g", "DULCES", 3.50),
                bocadito("Caramelos surtidos", "Frasco 30 g", "DULCES", 2.00),
                bocadito("Chupetines", "Unidad", "DULCES", 1.00),
                bocadito("Chocotejas", "Unidad", "DULCES", 3.00),
                bocadito("Toffee", "Bolsa 50 g", "DULCES", 1.50),

                bocadito("Inca Kola personal", "Botella 500 ml fría", "GASEOSAS", 4.00),
                bocadito("Coca-Cola personal", "Botella 500 ml fría", "GASEOSAS", 4.00),
                bocadito("Sprite personal", "Botella 500 ml fría", "GASEOSAS", 4.00),
                bocadito("Kola Real personal", "Botella 500 ml fría", "GASEOSAS", 3.50),
                bocadito("Guaraná personal", "Botella 500 ml fría", "GASEOSAS", 3.50),

                bocadito("Agua mineral", "Botella 625 ml fría", "BEBIDAS", 2.50),
                bocadito("Jugo de naranja", "Vaso 350 ml", "BEBIDAS", 5.00),
                bocadito("Gatorade", "Botella 500 ml fría", "BEBIDAS", 7.00),
                bocadito("Café instantáneo", "Taza con azúcar a elección", "BEBIDAS", 3.00),
                bocadito("Agua de cebada", "Vaso 350 ml", "BEBIDAS", 2.00)
        );
    }
}