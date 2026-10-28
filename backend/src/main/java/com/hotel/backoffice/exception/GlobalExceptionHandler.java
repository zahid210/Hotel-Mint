package com.hotel.backoffice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorResponse(String codigo, String mensaje) {}

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NO_ENCONTRADO", ex.getMessage()));
    }

    @ExceptionHandler(HabitacionNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> handleNoDisponible(HabitacionNoDisponibleException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("NO_DISPONIBLE", ex.getMessage()));
    }

    @ExceptionHandler(ThrottledException.class)
    public ResponseEntity<ErrorResponse> handleThrottled(ThrottledException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse("DEMASIADOS_INTENTOS", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccesoDenegado(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("SIN_PERMISOS", "No tiene permisos para esta acción."));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleCredenciales(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("CREDENCIALES_INVALIDAS", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDACION_FALLIDA", mensaje));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("SOLICITUD_INVALIDA", ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        String detalle = String.format("El parámetro '%s' con valor '%s' no tiene el formato esperado.", ex.getName(), ex.getValue());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("PARAMETRO_INVALIDO", detalle));
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(org.springframework.web.bind.MissingServletRequestParameterException ex) {
        String detalle = String.format("El parámetro requerido '%s' no fue proporcionado.", ex.getParameterName());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("PARAMETRO_FALTANTE", detalle));
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(jakarta.validation.ConstraintViolationException ex) {
        String detalle = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("RESTRICCION_VIOLADA", detalle));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("ESTADO_INVALIDO", ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(org.springframework.dao.OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICTO_CONCURRENCIA",
                        "El registro fue modificado simultáneamente por otra operación. Por favor, reintente."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConflictoDatos(DataIntegrityViolationException ex) {
        log.warn("Conflicto de datos: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICTO_DATOS",
                        "El registro ya existe o viola restricciones de datos."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNoLegible(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("JSON_INVALIDO", "Formato de solicitud inválido."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Error no controlado", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("ERROR_INTERNO", "Contacte al administrador."));
    }
}