package com.hotel.backoffice.controller;

import com.hotel.backoffice.dto.request.*;
import com.hotel.backoffice.dto.response.AuthResponseDTO;
import com.hotel.backoffice.entity.Usuario;
import com.hotel.backoffice.exception.ResourceNotFoundException;
import com.hotel.backoffice.repository.UsuarioRepository;
import com.hotel.backoffice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService        authService;
    private final UsuarioRepository  usuarioRepo;

    public record MeResponse(String email, String nombre, String rol) {}

    @PostMapping("/login")
    public AuthResponseDTO login(@Valid @RequestBody LoginRequestDTO dto, jakarta.servlet.http.HttpServletRequest request) {
        String ip = com.hotel.backoffice.util.ClientIpResolver.getClientIp(request);
        return authService.login(dto, ip);
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponseDTO register(@Valid @RequestBody RegisterRequestDTO dto) {
        return authService.register(dto);
    }

    // Usado como healthcheck autenticado y para revalidar la sesión
    @GetMapping("/me")
    public MeResponse me(Authentication auth) {
        Usuario u = usuarioRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + auth.getName()));
        return new MeResponse(u.getEmail(), u.getNombre(), u.getRol().name());
    }
}