package com.hotel.backoffice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/server")
@RequiredArgsConstructor
public class ServerTimeController {

    private final Clock clock;

    private static final DateTimeFormatter FMT_FECHA     =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FMT_FECHA_HORA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @GetMapping("/time")
    public ResponseEntity<ServerTimeResponse> getServerTime() {
        ZonedDateTime ahora = ZonedDateTime.now(clock);
        return ResponseEntity.ok(new ServerTimeResponse(
                ahora.format(FMT_FECHA),           // "2026-03-15"
                ahora.format(FMT_FECHA_HORA),      // "2026-03-15T14:32:00"
                ahora.getHour(),                   // 14
                ahora.getMinute(),                 // 32
                ahora.getZone().getId()            // "America/Lima"
        ));
    }

    public record ServerTimeResponse(
            String fecha,
            String fechaHora,
            int    hora,
            int    minuto,
            String zona
    ) {}
}