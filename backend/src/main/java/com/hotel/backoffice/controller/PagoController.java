package com.hotel.backoffice.controller;

import com.hotel.backoffice.dto.request.PagoRequestDTO;
import com.hotel.backoffice.dto.response.CuentaReservaDTO;
import com.hotel.backoffice.dto.response.PagoResponseDTO;
import com.hotel.backoffice.service.PagoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService pagoService;

    @PostMapping("/reservas/{id}/pagos")
    @ResponseStatus(HttpStatus.CREATED)
    public PagoResponseDTO registrarPago(
            @PathVariable UUID id,
            @Valid @RequestBody PagoRequestDTO dto) {
        return pagoService.registrarPago(id, dto);
    }

    @GetMapping("/reservas/{id}/pagos")
    public ResponseEntity<List<PagoResponseDTO>> listarPagos(@PathVariable UUID id) {
        return ResponseEntity.ok(pagoService.listarPorReserva(id));
    }

    @GetMapping("/reservas/{id}/cuenta")
    public ResponseEntity<CuentaReservaDTO> obtenerEstadoCuenta(@PathVariable UUID id) {
        return ResponseEntity.ok(pagoService.obtenerEstadoCuenta(id));
    }

    @PatchMapping("/pagos/{id}/reembolsar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagoResponseDTO> reembolsarPago(@PathVariable UUID id) {
        return ResponseEntity.ok(pagoService.reembolsarPago(id));
    }
}
