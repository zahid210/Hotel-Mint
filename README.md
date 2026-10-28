# Hotel Mint / Hotel Bahía — Sistema de Gestión Hotelera

Sistema integral para gestión operativa hotelera (PMS / Backoffice) con arquitectura desacoplada en tres capas: Nginx Reverse Proxy con TLS + SPA React 19, Backend API Spring Boot 4 / Java 21, y MariaDB 11.

---

## 1. Arquitectura del Sistema

```
                      INTERNET / CLIENTES
                              │
                      [Puertos 80 / 443]
                              ▼
        ┌──────────────────────────────────────────────┐
        │       Nginx (Contenedor Frontend/Proxy)      │
        │  • TLS / HSTS / Redirección 301 HTTP → HTTPS │
        │  • Servidor de estáticos React 19 SPA        │
        │  • Reverse Proxy a /api/v1/*                 │
        │  • Security Headers (CSP, X-Frame-Options)   │
        └──────────────────────┬───────────────────────┘
                               │ (Red interna Docker hotel-net)
                               ▼
        ┌──────────────────────────────────────────────┐
        │       Spring Boot API (Contenedor Backend)   │
        │  • Java 21 + Spring Security 7 + JWT         │
        │  • Puerto 8080 (No publicado al host)        │
        │  • Rate Limiter Caffeine (IP + Email)        │
        │  • Auditoría persistente y Claves Idempotentes│
        │  • Máquina de estados de reservas y pagos    │
        └──────────────────────┬───────────────────────┘
                               │ (Red interna Docker hotel-net)
                               ▼
        ┌──────────────────────────────────────────────┐
        │          MariaDB 11 (Contenedor DB)          │
        │  • Puerto 3306 (No publicado al host)        │
        │  • Migraciones de esquema con Flyway         │
        │  • Validaciones JPA / Hibernate estricto     │
        └──────────────────────────────────────────────┘
```

---

## 2. Requisitos Previos

- **Docker** 24.0+ y **Docker Compose** v2.20+
- Para desarrollo local en host (opcional):
  - **Java JDK 21** (`openjdk-21-jdk-headless`)
  - **Node.js 22+** y **npm**
  - **OpenSSL** (para generación de certificados)

---

## 3. Configuración de Variables de Entorno

Copie el archivo de ejemplo a `.env` y ajuste los valores requeridos antes de iniciar:

```bash
cp .env.example .env
```

### Variables Obligatorias:
| Variable | Descripción | Ejemplo / Requisito |
|---|---|---|
| `DB_PASSWORD` | Contraseña del usuario MariaDB | `clave_secreta_db_123` |
| `DB_ROOT_PASSWORD` | Contraseña del usuario root MariaDB | `clave_root_super_segura` |
| `JWT_SECRET` | Clave secreta para firma JWT | Mínimo 32 caracteres criptográficos |
| `APP_ADMIN_PASSWORD` | Contraseña inicial del usuario admin | Mínimo 12 caracteres |

### Variables Opcionales:
| Variable | Descripción | Valor por defecto |
|---|---|---|
| `DB_NAME` | Nombre de base de datos | `hotel_bahia` |
| `DB_USER` | Usuario de base de datos | `hotel` |
| `APP_ADMIN_EMAIL` | Email del administrador principal | `zahidmatos@hotel.com` |
| `APP_ADMIN_NOMBRE` | Nombre del administrador | `Zahid Matos` |
| `APP_TIMEZONE` | Zona horaria del hotel | `America/Lima` |
| `FRONTEND_PORT` | Puerto HTTP expuesto | `80` |
| `FRONTEND_SSL_PORT` | Puerto HTTPS expuesto | `443` |
| `APP_SEED_DEMO` | Carga de reservas ficticias de prueba | `false` |

> [!IMPORTANT]
> El archivo `docker-compose.yml` utiliza la sintaxis estricta `${VARIABLE:?error}`. Si alguna variable obligatoria está vacía o ausente en `.env`, el arranque fallará de inmediato sin exponer secretos conocidos.

---

## 4. Despliegue Rápido con Docker Compose

1. **Generar certificados SSL:**
   ```bash
   ./scripts/generate-certs.sh
   ```
   *(Si no se generan previamente, el script `docker-entrypoint.sh` creará automáticamente certificados autofirmados durante el primer inicio del contenedor frontend).*

2. **Iniciar los servicios en segundo plano:**
   ```bash
   docker compose up -d --build
   ```

3. **Verificar el estado de salud de los contenedores:**
   ```bash
   docker compose ps
   ```

4. **Acceder a la aplicación:**
   - Web: `https://localhost` (o `http://localhost`, redirige a HTTPS automáticamente).
   - Iniciar sesión con el email de admin configurado y su contraseña.

---

## 5. Runbook Operativo

### 5.1 Copias de Seguridad (Backup de Base de Datos)
Para generar un volcado comprimido y cifrado de la base de datos MariaDB:
```bash
./scripts/backup-db.sh
```
Los respaldos se almacenan automáticamente en la carpeta `backups/` con timestamp: `backup_hotel_bahia_YYYYMMDD_HHMMSS.sql.gz`.

### 5.2 Restauración de Base de Datos
Para restaurar la base de datos desde un archivo de volcado existente:
```bash
./scripts/restore-db.sh backups/backup_hotel_bahia_YYYYMMDD_HHMMSS.sql.gz
```

### 5.3 Migraciones de Base de Datos (Flyway)
El esquema de base de datos es versionado y gestionado mediante Flyway en `backend/src/main/resources/db/migration/`:
- `V1__init_schema.sql`: Esquema completo inicial (tablas `habitaciones`, `huespedes`, `usuarios`, `reservas`, `menu_items`, `pedidos`, `pedido_items`, `pagos`, `auditorias`, `idempotencia_claves`).
- Hibernate está configurado en modo `validate` (`ddl-auto: validate`), asegurando que la aplicación nunca altere esquemas en producción dinámicamente.

### 5.4 Rotación de Credenciales y Secretos
- **Cambio de clave de Admin:** Un administrador puede resetear su contraseña desde la interfaz o vía `PATCH /api/v1/usuarios/{id}/password`. Las contraseñas deben contener al menos 12 caracteres.
- **Protección del último Administrador:** El sistema impide desactivar o degradar de rol al único administrador activo en el sistema.
- **Rotación de JWT_SECRET:** Actualice `JWT_SECRET` en el archivo `.env` y reinicie el servicio backend (`docker compose up -d backend`). Esto invalidará todos los tokens existentes de forma inmediata.

---

## 6. Módulos y Lógica de Negocio

### 6.1 Habitaciones y Máquina de Estados
Las habitaciones transicionan siguiendo un ciclo físico estricto:
- `LIBRE` ↔ `MANTENIMIENTO`
- `LIBRE` ↔ `LIMPIEZA`
- `OCUPADA` → `LIMPIEZA` (al hacer checkout o cancelación de estadía activa)
- El rol `ROLE_LIMPIEZA` únicamente tiene permiso para transicionar habitaciones de `LIMPIEZA` a `LIBRE`.
- No es posible pasar manualmente una habitación a `LIBRE` si cuenta con un `CHECKIN` activo.

### 6.2 Reservas y Regla de Medianoche (Late Check-out)
- **Check-in:** Se registra el timestamp exacto (`checkinReal`). Solo se permite registrar el check-in en o después de la fecha acordada de llegada.
- **Check-out:**
  - Verifica que no existan pedidos pendientes (`PENDIENTE`) en servicio de habitación.
  - Compara el momento de salida contra la medianoche de la fecha acordada (`fechaSalida + 1 día a las 00:00`). Si el huésped sale pasada la medianoche, se factura una noche adicional completa (`cargoHorasExtra`).
  - La habitación pasa automáticamente a `LIMPIEZA`.

### 6.3 Servicio de Habitación (Pedidos)
- Permite ordenar productos del menú vinculados a la reserva del huésped.
- Precios unitarios congelados al momento de creación del pedido.
- Al entregarse (`ENTREGADO`), el importe se suma automáticamente al `totalConsumo` de la reserva.
- Previene check-out si hay pedidos pendientes.

### 6.4 Módulo de Pagos y Cierre de Cuenta
- Entidad `Pago` vinculada a la reserva con métodos: `EFECTIVO`, `TARJETA`, `TRANSFERENCIA`.
- Estados de pago: `PENDIENTE`, `PAGADO`, `REEMBOLSADO`.
- Consulta de cuenta consolidada (`GET /api/v1/reservas/{id}/cuenta`) con cálculo en tiempo real de `totalEstancia`, `totalPagado`, `saldoPendiente` y flag `pagadoCompletamente`.

### 6.5 Idempotencia y Prevención de Duplicados
- Los endpoints de creación `POST /api/v1/reservas` y `POST /api/v1/pedidos` aceptan la cabecera HTTP `Idempotency-Key`.
- El frontend genera automáticamente UUIDs únicos por envío y deshabilita los botones de guardado durante el procesamiento para evitar dobles cobros o reservas duplicadas por doble clic o intermitencia de red.

### 6.6 Auditoría de Operaciones Sensibles
- Registro centralizado en la tabla `auditorias` con usuario, acción, entidad, ID, detalle, fecha e IP del cliente (resolviendo cabeceras `X-Forwarded-For` y `X-Real-IP`).
- Operaciones auditadas: Check-in, Check-out, Cancelación, Cambios de estado en habitaciones, Modificaciones de usuarios y Registro de pagos.
- Consulta administrativa mediante `GET /api/v1/auditorias` (con paginación).

### 6.7 Rate Limiter de Autenticación
- Control de intentos fallidos en memoria con caché Caffeine acotada y expiración a 15 minutos (evita fugas de memoria).
- Bloqueo por Email (máximo 5 intentos fallidos) y por IP de origen (máximo 20 intentos fallidos).

---

## 7. Pruebas y Control de Calidad

### Pruebas de Backend
Las pruebas de integración utilizan base de datos H2 en memoria configurada en `application-test.yaml`, permitiendo ejecución hermética y veloz:
```bash
cd backend
./mvnw clean verify
```

### Pruebas y Build de Frontend
```bash
cd frontend
npm run lint
npm run build
```

### Integración Continua (CI)
El flujo de trabajo en `.github/workflows/ci.yml` ejecuta automáticamente en cada push y pull request a la rama `main`:
1. Compilación, análisis y suite de 30 pruebas unitarias e integración en Spring Boot.
2. Chequeo de ESLint y compilación de producción con Vite en el frontend.

---

## 8. Solución de Problemas (Troubleshooting)

- **El navegador indica "Certificado no confiable":**
  Los certificados dev creados por `scripts/generate-certs.sh` o el contenedor son autofirmados. En producción, coloque los certificados emitidos por su CA (ej. Let's Encrypt) en el directorio `certs/` (`cert.pem` y `key.pem`).
- **Error `DB_PASSWORD es obligatorio` al levantar Docker:**
  Asegúrese de haber creado el archivo `.env` a partir de `.env.example` y haber asignado contraseñas válidas.
- **Puerto 80 o 443 en conflicto en el host:**
  Puede reasignar los puertos públicos en `.env` modificando `FRONTEND_PORT` o `FRONTEND_SSL_PORT` (ej. `8080:80` y `8443:443`).
