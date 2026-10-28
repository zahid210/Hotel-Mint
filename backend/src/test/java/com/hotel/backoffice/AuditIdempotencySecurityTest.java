package com.hotel.backoffice;

import com.hotel.backoffice.dto.request.PasswordDTO;
import com.hotel.backoffice.dto.request.UsuarioRequestDTO;
import com.hotel.backoffice.dto.request.UsuarioUpdateDTO;
import com.hotel.backoffice.entity.Auditoria;
import com.hotel.backoffice.entity.Habitacion;
import com.hotel.backoffice.entity.Habitacion.EstadoHabitacion;
import com.hotel.backoffice.entity.Habitacion.TipoHabitacion;
import com.hotel.backoffice.entity.Usuario;
import com.hotel.backoffice.entity.Usuario.Rol;
import com.hotel.backoffice.repository.AuditoriaRepository;
import com.hotel.backoffice.repository.HabitacionRepository;
import com.hotel.backoffice.repository.UsuarioRepository;
import com.hotel.backoffice.security.LoginRateLimiter;
import com.hotel.backoffice.service.HabitacionService;
import com.hotel.backoffice.service.IdempotenciaService;
import com.hotel.backoffice.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditIdempotencySecurityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepo;

    @Autowired
    private HabitacionService habitacionService;

    @Autowired
    private HabitacionRepository habitacionRepo;

    @Autowired
    private AuditoriaRepository auditoriaRepo;

    @Autowired
    private IdempotenciaService idempotenciaService;

    @Autowired
    private LoginRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        auditoriaRepo.deleteAll();
    }

    @Test
    @DisplayName("P2-06: Protege al único administrador activo de ser desactivado")
    void protegeUltimoAdminActivoDeDesactivacion() {
        // Garantizar que solo haya 1 admin activo
        usuarioRepo.findAll().forEach(u -> {
            if (u.getRol() == Rol.ADMIN) {
                u.setActivo(false);
                usuarioRepo.save(u);
            }
        });

        Usuario admin = usuarioRepo.save(Usuario.builder()
                .nombre("Admin Unico")
                .email("admin_unico_" + System.currentTimeMillis() + "@test.com")
                .password("PasswordSegura123!")
                .rol(Rol.ADMIN)
                .activo(true)
                .build());

        // Intentar desactivarlo
        UsuarioUpdateDTO dtoDesactivar = new UsuarioUpdateDTO(null, null, null, false);
        assertThrows(IllegalStateException.class, () -> {
            usuarioService.actualizar(admin.getId(), dtoDesactivar);
        });

        // Intentar degradar su rol a RECEPCIONISTA
        UsuarioUpdateDTO dtoDegradar = new UsuarioUpdateDTO(null, null, "RECEPCIONISTA", null);
        assertThrows(IllegalStateException.class, () -> {
            usuarioService.actualizar(admin.getId(), dtoDegradar);
        });
    }

    @Test
    @DisplayName("P2-06: Rechaza contraseñas con menos de 12 caracteres")
    void rechazaContrasenaCorta() {
        assertThrows(IllegalArgumentException.class, () -> {
            usuarioService.crear(new UsuarioRequestDTO(
                    "Nuevo", "corto_" + System.currentTimeMillis() + "@test.com", "corta123", "RECEPCIONISTA"
            ));
        });

        Usuario u = usuarioRepo.save(Usuario.builder()
                .nombre("User")
                .email("user_" + System.currentTimeMillis() + "@test.com")
                .password("PasswordSegura123!")
                .rol(Rol.RECEPCIONISTA)
                .activo(true)
                .build());

        assertThrows(IllegalArgumentException.class, () -> {
            usuarioService.resetPassword(u.getId(), new PasswordDTO("corta"));
        });
    }

    @Test
    @DisplayName("P2-02: Registra auditoría en cambio de estado de habitación")
    @WithMockUser(username = "admin_auditor@hotel.com", roles = "ADMIN")
    void registraAuditoriaEnCambioEstadoHabitacion() {
        Habitacion hab = habitacionRepo.save(Habitacion.builder()
                .numero("999A")
                .piso(9)
                .tipo(TipoHabitacion.SIMPLE)
                .capacidad(2)
                .precioNoche(BigDecimal.valueOf(100.0))
                .estado(EstadoHabitacion.LIBRE)
                .build());

        habitacionService.cambiarEstado(hab.getId(), "LIMPIEZA");

        List<Auditoria> logs = auditoriaRepo.findAll();
        assertFalse(logs.isEmpty(), "Debe existir al menos un registro de auditoría");
        Auditoria log = logs.stream()
                .filter(a -> "CAMBIO_ESTADO".equals(a.getAccion()))
                .findFirst()
                .orElse(null);

        assertNotNull(log);
        assertEquals("Habitacion", log.getEntidad());
        assertEquals(hab.getId().toString(), log.getEntidadId());
        assertEquals("admin_auditor@hotel.com", log.getActor());
    }

    @Test
    @DisplayName("P2-08: Clave de idempotencia almacena y recupera respuesta")
    void servicioIdempotenciaAlmacenaYRecupera() {
        String key = "test-key-" + System.currentTimeMillis();
        RecordRespuesta dummy = new RecordRespuesta("OK", 123);

        idempotenciaService.registrar(key, "Prueba", "123", 200, dummy);

        var encontrado = idempotenciaService.buscar(key);
        assertTrue(encontrado.isPresent());
        assertEquals(200, encontrado.get().getStatusCode());

        RecordRespuesta recuperado = idempotenciaService.deserializar(
                encontrado.get().getRespuestaJson(), RecordRespuesta.class);
        assertEquals("OK", recuperado.mensaje());
        assertEquals(123, recuperado.codigo());
    }

    @Test
    @DisplayName("P2-01: Rate limiter bloquea tras 5 intentos para email y 20 para IP")
    void rateLimiterBloqueo() {
        String email = "ataque_" + System.currentTimeMillis() + "@test.com";
        String ip = "192.168.1.100";

        for (int i = 0; i < 4; i++) {
            rateLimiter.registrarFallo(email, ip);
            assertFalse(rateLimiter.bloqueado(email, ip));
        }

        rateLimiter.registrarFallo(email, ip); // 5to fallo
        assertTrue(rateLimiter.bloqueado(email, ip));

        rateLimiter.limpiar(email, ip);
        assertFalse(rateLimiter.bloqueado(email, ip));
    }

    @Test
    @DisplayName("P2-05: Parámetros inválidos devuelven 400 con código estandarizado")
    @WithMockUser(roles = "ADMIN")
    void parametroInvalidoDevuelve400() throws Exception {
        mvc.perform(get("/api/v1/auditorias?page=no-es-numero"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    record RecordRespuesta(String mensaje, int codigo) {}
}
