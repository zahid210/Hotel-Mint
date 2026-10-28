package com.hotel.backoffice.service;

import com.hotel.backoffice.dto.request.*;
import com.hotel.backoffice.dto.response.AuthResponseDTO;
import com.hotel.backoffice.entity.Usuario;
import com.hotel.backoffice.entity.Usuario.Rol;
import com.hotel.backoffice.exception.*;
import com.hotel.backoffice.repository.UsuarioRepository;
import com.hotel.backoffice.security.JwtTokenProvider;
import com.hotel.backoffice.security.LoginRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository   repo;
    private final PasswordEncoder     encoder;
    private final JwtTokenProvider    tokenProvider;
    private final AuthenticationManager authManager;
    private final LoginRateLimiter    rateLimiter;

    // ── LOGIN ────────────────────────────────────────────────
    public AuthResponseDTO login(LoginRequestDTO dto) {
        return login(dto, null);
    }

    public AuthResponseDTO login(LoginRequestDTO dto, String ip) {
        if (rateLimiter.bloqueado(dto.email(), ip)) {
            throw new ThrottledException(
                    "Demasiados intentos fallidos. Intente nuevamente en 15 minutos."
            );
        }

        try {
            // 1. Validar credenciales con Spring Security
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.email(), dto.password())
            );
        } catch (AuthenticationException e) {
            rateLimiter.registrarFallo(dto.email(), ip);
            throw new BadCredentialsException("Email o contraseña incorrectos.");
        }

        rateLimiter.limpiar(dto.email(), ip);

        // 2. Cargar usuario para armar la respuesta
        Usuario usuario = repo.findByEmail(dto.email())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // 3. Generar token JWT
        String token = tokenProvider.generarToken(
                usuario.getEmail(),
                usuario.getRol().name(),
                usuario.getNombre()
        );

        return new AuthResponseDTO(token, usuario.getEmail(),
                usuario.getNombre(), usuario.getRol().name());
    }

    // ── REGISTER ─────────────────────────────────────────────
    public AuthResponseDTO register(RegisterRequestDTO dto) {
        if (repo.findByEmail(dto.email()).isPresent()) {
            throw new IllegalArgumentException("El email ya está registrado.");
        }

        Rol rol;
        if (dto.rol() == null || dto.rol().isBlank()) {
            rol = Rol.RECEPCIONISTA;
        } else {
            try {
                rol = Rol.valueOf(dto.rol().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Rol inválido: debe ser ADMIN, RECEPCIONISTA o LIMPIEZA."
                );
            }
        }

        Usuario nuevo = Usuario.builder()
                .nombre(dto.nombre())
                .email(dto.email())
                .password(encoder.encode(dto.password()))
                .rol(rol)
                .activo(true)
                .build();

        repo.save(nuevo);

        String token = tokenProvider.generarToken(nuevo.getEmail(),
                nuevo.getRol().name(), nuevo.getNombre());

        return new AuthResponseDTO(token, nuevo.getEmail(),
                nuevo.getNombre(), nuevo.getRol().name());
    }
}