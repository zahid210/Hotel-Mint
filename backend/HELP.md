# Hotel Mint — Backend API

Backend del sistema de gestión hotelera construido con Spring Boot 4, Java 21, Spring Data JPA, Spring Security y Flyway.

## Comandos Principales

- **Ejecutar tests y empaquetar:**
  ```bash
  ./mvnw clean verify
  ```
- **Ejecutar tests únicamente:**
  ```bash
  ./mvnw test
  ```
- **Iniciar localmente:**
  ```bash
  ./mvnw spring-boot:run
  ```

## Estructura de Módulos

- `config/`: Configuración de seguridad, CORS, inyección de `Clock` y Flyway.
- `controller/`: Endpoints REST (`/api/v1/*`) para autenticación, habitaciones, reservas, pedidos, menú, reportes, pagos y auditorías.
- `dto/`: Objetos de transferencia de datos con validaciones Bean Validation (`@NotNull`, `@Size(min = 12)`, etc.).
- `entity/`: Entidades JPA con bloqueo optimista (`@Version`) y relaciones optimizadas.
- `repository/`: Repositorios Spring Data JPA con `@EntityGraph` para evitar problemas N+1.
- `security/`: Filtro JWT, Rate Limiter con caché Caffeine y UserDetailsService.
- `service/`: Lógica de negocio (estados, cálculo de late checkout, pagos, auditoría).
- `resources/db/migration/`: Migraciones versionadas con Flyway.
