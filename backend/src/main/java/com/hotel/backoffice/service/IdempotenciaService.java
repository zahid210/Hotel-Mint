package com.hotel.backoffice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.backoffice.entity.IdempotenciaClave;
import com.hotel.backoffice.repository.IdempotenciaClaveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotenciaService {

    private final IdempotenciaClaveRepository repo;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Transactional(readOnly = true)
    public Optional<IdempotenciaClave> buscar(String clave) {
        if (clave == null || clave.isBlank()) return Optional.empty();
        return repo.findById(clave.trim());
    }

    @Transactional
    public void registrar(String clave, String entidad, String recursoId, int statusCode, Object respuesta) {
        if (clave == null || clave.isBlank()) return;
        try {
            String json = objectMapper.writeValueAsString(respuesta);
            IdempotenciaClave registro = IdempotenciaClave.builder()
                    .clave(clave.trim())
                    .entidad(entidad)
                    .recursoId(recursoId)
                    .statusCode(statusCode)
                    .respuestaJson(json)
                    .build();
            repo.save(registro);
        } catch (Exception e) {
            log.error("Error al persistir clave de idempotencia: {}", e.getMessage());
        }
    }

    public <T> T deserializar(String json, Class<T> targetClass) {
        try {
            return objectMapper.readValue(json, targetClass);
        } catch (Exception e) {
            throw new RuntimeException("Error al deserializar respuesta idempotente", e);
        }
    }
}
