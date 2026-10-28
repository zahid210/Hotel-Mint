package com.hotel.backoffice.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider       tokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String token = extraerToken(request);

        if (StringUtils.hasText(token)) {
            String email = tokenProvider.getEmailSiValido(token);
            if (email != null) {
                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                    // No autenticar cuentas desactivadas: aunque el token siga válido,
                    // un usuario desactivado pierde el acceso de inmediato
                    if (userDetails.isEnabled()) {
                        var auth = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                } catch (org.springframework.security.core.AuthenticationException ex) {
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"codigo\":\"NO_AUTORIZADO\",\"mensaje\":\"Usuario no encontrado o sesión inválida.\"}");
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    // ── Extrae el token del header: "Bearer <token>" ─────────
    private String extraerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}