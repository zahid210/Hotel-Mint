package com.hotel.backoffice;

import com.hotel.backoffice.entity.Usuario;
import com.hotel.backoffice.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarioRepo;

    private String emailUnico() {
        return "test" + System.currentTimeMillis() + "@auditoria.test";
    }

    private String cuerpo(String email) {
        return "{\"nombre\":\"Prueba\",\"email\":\"" + email
                + "\",\"password\":\"PasswordSegura123!\"}";
    }

    @Test
    void registerSinTokenEsRechazado() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(emailUnico())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "RECEPCIONISTA")
    void registerComoRecepcionistaEsProhibido() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(emailUnico())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerComoAdminCreaRecepcionista() throws Exception {
        String email = emailUnico();

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(email)))
                .andExpect(status().isCreated());

        Usuario creado = usuarioRepo.findByEmail(email)
                .orElseThrow(() -> new AssertionError("No se creó el usuario"));
        assertEquals(Usuario.Rol.RECEPCIONISTA, creado.getRol());
        usuarioRepo.delete(creado);
    }
}