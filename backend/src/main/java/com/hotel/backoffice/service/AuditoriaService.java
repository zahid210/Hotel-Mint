package com.hotel.backoffice.service;

import com.hotel.backoffice.entity.Auditoria;
import com.hotel.backoffice.repository.AuditoriaRepository;
import com.hotel.backoffice.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final AuditoriaRepository repo;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String accion, String entidad, Object entidadId, String detalles) {
        try {
            String actor = "SISTEMA";
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                actor = auth.getName();
            }

            String ip = "unknown";
            try {
                HttpServletRequest req = requestProvider.getIfAvailable();
                if (req != null) {
                    ip = ClientIpResolver.getClientIp(req);
                }
            } catch (Exception ignored) {}

            Auditoria audit = Auditoria.builder()
                    .actor(actor)
                    .accion(accion)
                    .entidad(entidad)
                    .entidadId(entidadId != null ? entidadId.toString() : null)
                    .detalles(detalles)
                    .ip(ip)
                    .build();

            repo.save(audit);
            log.info("AUDIT: [actor={}, accion={}, entidad={}, id={}, ip={}] {}",
                    actor, accion, entidad, entidadId, ip, detalles);
        } catch (Exception e) {
            log.error("Error al registrar auditoría: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<Auditoria> listar(Pageable pageable) {
        return repo.findAllByOrderByCreatedAtDesc(pageable);
    }
}
