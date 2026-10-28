package com.hotel.backoffice.service;

import com.hotel.backoffice.dto.request.*;
import com.hotel.backoffice.dto.response.UsuarioResponseDTO;
import com.hotel.backoffice.entity.Usuario;
import com.hotel.backoffice.entity.Usuario.Rol;
import com.hotel.backoffice.exception.ResourceNotFoundException;
import com.hotel.backoffice.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UsuarioService {

    private final UsuarioRepository   repo;
    private final PasswordEncoder     encoder;
    private final AuditoriaService    auditoriaService;

    // ── Listar (sin contraseñas) ─────────────────────────────
    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listar() {
        return repo.findAll().stream()
                .map(UsuarioResponseDTO::from)
                .toList();
    }

    // ── Crear usuario ────────────────────────────────────────
    public UsuarioResponseDTO crear(UsuarioRequestDTO dto) {
        if (repo.findByEmail(dto.email()).isPresent()) {
            throw new IllegalArgumentException("El email ya está registrado.");
        }
        if (dto.password() == null || dto.password().length() < 12) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 12 caracteres.");
        }

        Rol rol = parseRol(dto.rol());

        Usuario nuevo = Usuario.builder()
                .nombre(dto.nombre())
                .email(dto.email())
                .password(encoder.encode(dto.password()))
                .rol(rol)
                .activo(true)
                .build();

        return UsuarioResponseDTO.from(repo.save(nuevo));
    }

    // ── Editar datos (sin contraseña) ────────────────────────
    public UsuarioResponseDTO actualizar(UUID id, UsuarioUpdateDTO dto) {
        Usuario u = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Proteger al último administrador activo de quedar desactivado o degradado
        if (u.getRol() == Rol.ADMIN && Boolean.TRUE.equals(u.getActivo())) {
            boolean desactivando = dto.activo() != null && !dto.activo();
            boolean degradandoRol = dto.rol() != null && parseRol(dto.rol()) != Rol.ADMIN;
            if (desactivando || degradandoRol) {
                long adminsActivos = repo.countByRolAndActivoTrue(Rol.ADMIN);
                if (adminsActivos <= 1) {
                    throw new IllegalStateException("No se puede desactivar o degradar al último administrador activo.");
                }
            }
        }

        // Email único (excepto si no cambia)
        if (dto.email() != null && !dto.email().equals(u.getEmail())) {
            if (repo.findByEmail(dto.email()).isPresent()) {
                throw new IllegalArgumentException("El email ya está registrado.");
            }
            u.setEmail(dto.email());
        }

        if (dto.nombre() != null)  u.setNombre(dto.nombre());
        if (dto.activo() != null)  u.setActivo(dto.activo());
        if (dto.rol() != null)     u.setRol(parseRol(dto.rol()));

        Usuario guardado = repo.save(u);
        auditoriaService.registrar("ACTUALIZAR_USUARIO", "Usuario", guardado.getId(),
                "Usuario " + guardado.getEmail() + " actualizado: rol=" + guardado.getRol() + ", activo=" + guardado.getActivo());
        return UsuarioResponseDTO.from(guardado);
    }

    // ── Reset contraseña ─────────────────────────────────────
    public UsuarioResponseDTO resetPassword(UUID id, PasswordDTO dto) {
        Usuario u = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        if (dto.password() == null || dto.password().length() < 12) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 12 caracteres.");
        }
        u.setPassword(encoder.encode(dto.password()));
        Usuario guardado = repo.save(u);
        auditoriaService.registrar("RESET_PASSWORD", "Usuario", guardado.getId(),
                "Contraseña restablecida para usuario " + guardado.getEmail());
        return UsuarioResponseDTO.from(guardado);
    }

    private Rol parseRol(String valor) {
        if (valor == null || valor.isBlank()) return Rol.RECEPCIONISTA;
        try {
            return Rol.valueOf(valor.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Rol inválido: debe ser ADMIN, RECEPCIONISTA o LIMPIEZA.");
        }
    }
}