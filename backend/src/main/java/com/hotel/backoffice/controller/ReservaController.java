package com.hotel.backoffice.controller;

import com.hotel.backoffice.dto.request.ReservaRequestDTO;
import com.hotel.backoffice.dto.response.ReservaResponseDTO;
import com.hotel.backoffice.service.ReservaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservas")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService service;
    private final com.hotel.backoffice.service.IdempotenciaService idempotenciaService;

    @GetMapping
    public ResponseEntity<?> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null) {
            return ResponseEntity.ok(service.listarPaginado(estado, page, size != null ? size : 20));
        }
        if (estado != null && !estado.isBlank()) {
            return ResponseEntity.ok(service.listarPorEstado(estado));
        }
        return ResponseEntity.ok(service.listarTodas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservaResponseDTO> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(service.obtenerPorId(id));
    }

    @PostMapping
    public ResponseEntity<ReservaResponseDTO> crear(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReservaRequestDTO dto) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existente = idempotenciaService.buscar(idempotencyKey);
            if (existente.isPresent()) {
                ReservaResponseDTO cached = idempotenciaService.deserializar(
                        existente.get().getRespuestaJson(), ReservaResponseDTO.class);
                return ResponseEntity.status(existente.get().getStatusCode()).body(cached);
            }
        }

        ReservaResponseDTO creada = service.crearReserva(dto);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotenciaService.registrar(idempotencyKey, "Reserva", creada.id().toString(),
                    HttpStatus.CREATED.value(), creada);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PatchMapping("/{id}/checkin")
    public ResponseEntity<ReservaResponseDTO> checkIn(@PathVariable UUID id) {
        return ResponseEntity.ok(service.realizarCheckIn(id));
    }

    @PatchMapping("/{id}/checkout")
    public ResponseEntity<ReservaResponseDTO> checkOut(@PathVariable UUID id) {
        return ResponseEntity.ok(service.realizarCheckOut(id));
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<ReservaResponseDTO> cancelar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.cancelarReserva(id));
    }
}