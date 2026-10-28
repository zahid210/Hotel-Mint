package com.hotel.backoffice.controller;

import com.hotel.backoffice.dto.request.PedidoEstadoDTO;
import com.hotel.backoffice.dto.request.PedidoRequestDTO;
import com.hotel.backoffice.dto.response.PedidoResponseDTO;
import com.hotel.backoffice.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pedidos")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService service;
    private final com.hotel.backoffice.service.IdempotenciaService idempotenciaService;

    @GetMapping
    public ResponseEntity<?> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) UUID   reservaId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null) {
            return ResponseEntity.ok(service.listarPaginado(estado, page, size != null ? size : 20));
        }
        return ResponseEntity.ok(service.listar(estado, reservaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponseDTO> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @PostMapping
    public ResponseEntity<PedidoResponseDTO> crear(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PedidoRequestDTO dto) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existente = idempotenciaService.buscar(idempotencyKey);
            if (existente.isPresent()) {
                PedidoResponseDTO cached = idempotenciaService.deserializar(
                        existente.get().getRespuestaJson(), PedidoResponseDTO.class);
                return ResponseEntity.status(existente.get().getStatusCode()).body(cached);
            }
        }

        PedidoResponseDTO creado = service.crear(dto);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotenciaService.registrar(idempotencyKey, "Pedido", creado.id().toString(),
                    HttpStatus.CREATED.value(), creado);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<PedidoResponseDTO> cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody PedidoEstadoDTO dto) {
        return ResponseEntity.ok(service.cambiarEstado(id, dto.estado()));
    }
}