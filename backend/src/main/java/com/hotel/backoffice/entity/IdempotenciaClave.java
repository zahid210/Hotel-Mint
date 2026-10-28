package com.hotel.backoffice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "idempotencia_claves")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotenciaClave {

    @Id
    @Column(length = 100)
    private String clave;

    @Column(nullable = false, length = 50)
    private String entidad;

    @Column(name = "recurso_id", nullable = false, length = 100)
    private String recursoId;

    @Column(name = "status_code", nullable = false)
    private Integer statusCode;

    @Column(name = "respuesta_json", columnDefinition = "LONGTEXT")
    private String respuestaJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
