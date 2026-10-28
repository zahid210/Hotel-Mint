package com.hotel.backoffice.controller;

import com.hotel.backoffice.entity.Auditoria;
import com.hotel.backoffice.service.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auditorias")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    @GetMapping
    public ResponseEntity<Page<Auditoria>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), boundedSize);
        return ResponseEntity.ok(auditoriaService.listar(pageable));
    }
}
