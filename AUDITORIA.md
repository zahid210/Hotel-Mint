# Auditoría técnica — Hotel Mint / Hotel Bahía

**Repositorio:** `/home/kali/Desktop/Hotel-Mint`  
**Rama / commit:** `main` · `dcb5b74`  
**Fecha:** 26/10/2026  
**Modalidad:** auditoría de solo lectura sobre el código; no se modificó código ni configuración. Este informe es el único archivo añadido.  
**Veredicto:** arquitectura correcta para un MVP, pero **no apta para producción** sin resolver los hallazgos P0/P1.

---

## 1. Resumen ejecutivo

Se revisaron 60 archivos Java (~3.650 líneas) y 30 archivos TypeScript/TSX (~4.549 líneas), además de configuración Docker, Nginx, perfiles de Spring, pruebas y dependencias.

Se identificaron **26 hallazgos**: **2 P0**, **10 P1**, **10 P2** y **4 P3**.

Los riesgos más graves son:

1. **P0-01 — Secretos y credenciales por defecto:** el stack Docker crea un administrador y usuarios demo con contraseñas conocidas, y `.env.example` contiene un `JWT_SECRET` fijo. Copiar el ejemplo tal como indica el propio archivo permite **forjar un token de administrador**.
2. **P0-02 — Puertos de base de datos y API publicados:** MariaDB queda en `3307` y Spring Boot en `8080`, accesibles desde la red, saltándose Nginx.
3. **P1-03 — Estados de habitación sin máquina de estados:** limpieza o recepción pueden marcar como libre una habitación ocupada, habilitando doble ocupación.
4. **P1-04 — Dashboard elige la reserva equivocada:** una reserva futura `CONFIRMADA` puede tapar a la reserva actual en `CHECKIN`, mostrando huésped y acciones incorrectas.
5. **P1-05 — Check-out con pedidos pendientes:** los pedidos `PENDIENTE` quedan varados y nunca se cobran.
6. **P1-07 — Reportes con doble conteo:** una estancia que cruza de mes se contabiliza completa en ambos meses.
7. **P1-10 — Red de seguridad insuficiente:** 12 pruebas, sin CI, sin pruebas de servicios/repositorios/frontend y build Docker con `-DskipTests`.

**Conclusión:** el proyecto tiene una separación de capas razonable y buenas prácticas puntuales, pero los problemas de secretos, integridad de estados, concurrencia y reportes afectan directamente a la operación y al dinero. La corrección debe empezar por contener los P0 y luego estabilizar la lógica de negocio antes de añadir funcionalidades.

---

## 2. Alcance, metodología y limitaciones

### 2.1 Alcance

- Backend: entidades, DTOs, controladores, servicios, repositorios, seguridad, configuración, Dockerfile y pruebas.
- Frontend: App, autenticación, stores, servicios, hooks y páginas/componentes de todas las features.
- Infraestructura: `docker-compose.yml`, `.env.example`, perfiles YAML, Nginx, Dockerfiles, scripts y `.gitignore`.
- Dependencias: `pom.xml`, `package-lock.json`, `npm audit` y consultas OSV.
- Git: estado del árbol, rama, último commit y archivos versionados.

### 2.2 Método

- Inspección estática del código real, no solo de la documentación.
- Ejecución de `docker compose config` con un `JWT_SECRET` ficticio: **el compose parsea correctamente**.
- `npm audit --package-lock-only` y `npm audit --package-lock-only --omit=dev`.
- Consultas a la API de OSV para las coordenadas Maven relevantes.
- Revisión manual de cálculos de reportes, estados y transiciones.

### 2.3 Limitaciones explícitas

| Actividad | Estado | Motivo |
|---|---|---|
| `npm ci` / build / lint / typecheck frontend | **No ejecutado** | Sin `node_modules`; el intento offline en copia aislada falló porque la caché no contenía `zustand-5.0.11.tgz`. |
| `mvn test` / build backend | **No ejecutado** | No existe `~/.m2/repository`; descargar dependencias no estaba autorizado. |
| Pruebas contra base de datos | **No ejecutado** | No hay MariaDB escuchando en `3306`/`3307`. |
| Pruebas de carga/concurrencia | **No ejecutado** | Requiere entorno y fixtures; los hallazgos de concurrencia se confirman por inspección, no se reprodujeron. |
| Escaneo dinámico / pentest | **No ejecutado** | Fuera del alcance de una auditoría de solo lectura. |
| Build de imágenes Docker | **No ejecutado** | El daemon está disponible, pero no se autorizó descargar/construir. |

**No se modificó ningún archivo de código ni de configuración del repositorio.** `git status --porcelain` solo debe mostrar este informe como archivo nuevo. Las pruebas de dependencias y el `compose config` se hicieron sin alterar el árbol de trabajo.

**Criterio de confirmación:** “Confirmado” significa evidencia directa en código o configuración. Cuando el impacto no se pudo reproducir (por ejemplo, una carrera), se indica expresamente. No se incluyeron sospechas sin evidencia.

---

## 3. Tabla resumen de hallazgos

| ID | Prioridad | Problema | Archivo | Estado de verificación |
|---|---|---|---|---|
| P0-01 | P0 | Credenciales y secretos por defecto/de ejemplo permiten acceso total | `docker-compose.yml`, `.env.example`, `DataInitializer.java` | Confirmado (código + compose) |
| P0-02 | P0 | MariaDB y backend publicados en todas las interfaces | `docker-compose.yml` | Confirmado (compose config) |
| P1-01 | P1 | JWT en `localStorage`, 24 h, sin revocación ni refresh | `useAuthStore.ts`, `JwtTokenProvider.java` | Confirmado (código) |
| P1-02 | P1 | HTTP sin TLS ni cabeceras de seguridad | `nginx.conf`, `docker-compose.yml` | Confirmado (config) |
| P1-03 | P1 | Cambio arbitrario de estado de habitación; invariantes saltables | `HabitacionService.java`, `HabitacionController.java` | Confirmado (código) |
| P1-04 | P1 | Dashboard selecciona una reserva activa incorrecta | `useDashboard.ts`, `ReservaRepository.java` | Confirmado (código) |
| P1-05 | P1 | Check-out deja pedidos pendientes sin resolver ni cobrar | `ReservaService.java`, `PedidoService.java` | Confirmado (código) |
| P1-06 | P1 | Sin bloqueo optimista; carreras en consumo y estados | Entidades y `PedidoService.java` | Confirmado por inspección; no reproducido |
| P1-07 | P1 | Reportes duplican ingresos/reservas y usan fechas planificadas | `ReporteService.java`, `ReservaRepository.java` | Confirmado (código + cálculo manual) |
| P1-08 | P1 | Dependencias con vulnerabilidades altas | `pom.xml`, `package-lock.json` | Confirmado (npm audit + OSV) |
| P1-09 | P1 | Sin migraciones; `ddl-auto: update` y migración destructiva | `application-docker.yml`, `DataInitializer.java` | Confirmado (config/código) |
| P1-10 | P1 | Cobertura insuficiente, tests dependientes de BD y sin CI | `src/test`, `application-test.yaml`, `Dockerfile` | Confirmado (inventario); ejecución no verificada |
| P2-01 | P2 | Rate limiter en memoria: bloqueo de cuentas, memoria y no escala | `LoginRateLimiter.java` | Confirmado (código) |
| P2-02 | P2 | Sin auditoría de acciones sensibles | Servicios/entidades | Confirmado (ausencia) |
| P2-03 | P2 | Sin paginación y N+1 en pedidos; dashboard carga todo | Servicios y `useDashboard.ts` | Confirmado por inspección; impacto no medido |
| P2-04 | P2 | Consulta a BD en cada request y usuario borrado → 500 | `JwtAuthFilter.java`, `UserDetailsServiceImpl.java` | Confirmado (código) |
| P2-05 | P2 | Mapeo de errores incompleto y contrato inconsistente | `GlobalExceptionHandler.java`, `api.ts` | Confirmado (código) |
| P2-06 | P2 | Contraseñas débiles y riesgo de bloquear al último admin | DTOs, `UsuarioService.java` | Confirmado (código) |
| P2-07 | P2 | Desincronización visual de habitación al cancelar | `useDashboard.ts`, `ReservaService.java` | Confirmado (código) |
| P2-08 | P2 | Sin idempotencia en creación de reservas/pedidos | Controladores y servicios | Confirmado (ausencia) |
| P2-09 | P2 | No hay registro/cobro de pagos; `Pago` sin uso | `Pago.java`, `ReservaService.java` | Confirmado (código) |
| P2-10 | P2 | Endurecimiento Docker/operación insuficiente | Compose y Dockerfiles | Confirmado (config) |
| P3-01 | P3 | Código muerto y restos de refactor | Varios | Confirmado (código) |
| P3-02 | P3 | Documentación y runbook ausentes | Raíz del repo | Confirmado (inventario) |
| P3-03 | P3 | Zona horaria global mutable; difícil de testear | `TimezoneConfig.java`, servicios | Confirmado (código) |
| P3-04 | P3 | CORS con defaults de localhost y credenciales habilitadas | `CorsConfig.java` | Confirmado (código) |

---

## 4. Arquitectura y estructura

### 4.1 Vista general

```text
Navegador
   │ HTTP :80
   ▼
Nginx (SPA React + proxy /api)
   │ HTTP interno :8080
   ▼
Spring Boot 4.0.3 / Java 21
   │ JDBC :3306
   ▼
MariaDB 11
```

- **Backend:** Spring Boot 4.0.3, Java 21, Spring Security, JPA/Hibernate, JWT (jjwt 0.12.6), MariaDB.
- **Frontend:** React 19, TypeScript 5.9, Vite 8, Zustand 5, Axios, Tailwind 3.
- **Infraestructura:** tres contenedores; Nginx sirve la SPA y hace proxy a `/api`.
- **Backend:** arquitectura por capas (`controller` → `service` → `repository` → `entity`), DTOs de request/response y excepciones centralizadas.
- **Frontend:** organización por features (`auth`, `dashboard`, `reservas`, `pedidos`, `menu`, `reportes`, `usuarios`), stores de Zustand y servicios por dominio.

### 4.2 Fortalezas

- Separación de responsabilidades clara y coherente.
- Uso de DTOs y Bean Validation en las entradas principales.
- Contraseñas con BCrypt; no se encontraron contraseñas en texto plano en la base.
- Consultas JPA parametrizadas; no se observó concatenación de SQL.
- `open-in-view: false` y `ddl-auto: validate` en el perfil base.
- Bloqueo pesimista al crear reservas (`findWithLockById`), que evita solapamientos en creación concurrente.
- Backend Docker ejecutado como usuario no root.
- Frontend con rutas lazy, interceptor global de 401 y separación de servicios.
- Pruebas unitarias de la lógica de late checkout.
- No hay `.env` real ni claves privadas versionadas; `.gitignore` las excluye.

### 4.3 Debilidades estructurales

- No hay contrato OpenAPI ni tipos compartidos entre backend y frontend.
- No hay migraciones versionadas; el esquema depende de Hibernate.
- No hay CI/CD ni pipeline de calidad.
- El inicializador de datos demo se ejecuta en el perfil `docker`, no solo en local.
- No hay auditoría, pagos ni observabilidad.
- El frontend replica reglas de estado que el backend no garantiza.
- La documentación raíz no existe; solo hay `backend/HELP.md` genérico y el README de plantilla de Vite.

---

## 5. Seguridad

### 5.1 Autenticación y sesión

- Login con `AuthenticationManager` y BCrypt: correcto.
- JWT firmado con HMAC y expiración: correcto a nivel criptográfico.
- El token se guarda en `localStorage` mediante `zustand/persist`: **P1-01**.
- No hay revocación, `jti`, lista negra ni refresh token: **P1-01**.
- El registro público no existe; `AuthController.register` está restringido a `ADMIN`. **No es un hallazgo.**
- La desactivación de un usuario sí invalida el acceso porque el filtro recarga el usuario y comprueba `isEnabled()`. Un cambio de contraseña **no** invalida tokens existentes.
- Un usuario eliminado de la BD puede provocar un 500 en el filtro: **P2-04**.

### 5.2 Autorización

- Hay `@PreAuthorize` por controlador y por método; los roles están bien separados en general.
- El punto débil no es el rol, sino la **ausencia de reglas de transición** en habitaciones: **P1-03**.
- Limpieza solo puede escribir `LIBRE`, pero puede hacerlo sobre una habitación `OCUPADA`.
- Recepción y administración pueden escribir cualquier estado sin validar la reserva asociada.

### 5.3 Transporte y secretos

- Todo el stack es HTTP; no hay TLS, HSTS ni cabeceras de seguridad: **P1-02**.
- Credenciales y JWT viajan en claro dentro de la red.
- `.env.example` contiene un `JWT_SECRET` fijo y contraseñas conocidas: **P0-01**.
- La base y la API están publicadas en el host: **P0-02**.
- CORS está restringido a orígenes explícitos, pero con defaults de localhost y `allowCredentials(true)`: **P3-04**.

### 5.4 Superficie de ataque

- Login sin CAPTCHA; rate limiter solo por email y en memoria: **P2-01**.
- Sin auditoría de accesos ni de cambios: **P2-02**.
- Sin bloqueo por IP ni límite global; un atacante puede bloquear cuentas conocidas.
- Dependencias con vulnerabilidades altas: **P1-08**.

---

## 6. Lógica de negocio y calidad

### 6.1 Reservas y habitaciones

- La creación de reservas valida fechas, capacidad, mantenimiento, limpieza y solapamiento; usa bloqueo pesimista. Es una de las partes mejor resueltas.
- El check-in valida que la habitación no esté `OCUPADA` ni en `MANTENIMIENTO`.
- El check-out calcula late checkout y pasa la habitación a `LIMPIEZA`.
- **No hay máquina de estados central:** cualquier operador puede alterar el estado físico y romper las validaciones anteriores (**P1-03**).
- No hay bloqueo optimista ni pesimista en check-in/check-out (**P1-06**).
- La cancelación de una reserva en `CHECKIN` deja la habitación en `LIMPIEZA` en backend, pero el frontend la pinta `LIBRE` (**P2-07**).

### 6.2 Pedidos y consumo

- Un pedido solo se crea para reservas en `CHECKIN`.
- Al pasar a `ENTREGADO` se suma el total a `Reserva.totalConsumo`.
- Al cancelar un pedido ya entregado se resta el consumo.
- El check-out **no comprueba pedidos pendientes**: quedan varados y no se pueden entregar después (**P1-05**).
- La actualización de `totalConsumo` es read-modify-write sin bloqueo (**P1-06**).
- No hay idempotencia: un doble POST puede duplicar pedidos (**P2-08**).

### 6.3 Reportes

- El resumen filtra reservas por solapamiento con el período.
- Los ingresos se calculan solo sobre reservas en `CHECKOUT`, pero se imputa **la estancia completa** al período, no las noches dentro del período.
- Una estancia que cruza de mes se cuenta completa en ambos meses (**P1-07**).
- La ocupación usa `fechaSalida` planificada, no `checkoutReal`; las noches extra por late checkout no se reflejan en ocupación.
- `ingresoTotal` ya incluye consumo; `ingresoConsumo` es un subconjunto. El frontend lo muestra como subtítulo, pero el contrato puede inducir a sumarlos.

### 6.4 Errores y validación

- Buenos manejadores para 404, 409, 403, 401, validación, JSON inválido y errores de integridad.
- Faltan manejadores para parámetros de tipo incorrecto, parámetros faltantes y violaciones de constraints; terminan en 500 (**P2-05**).
- El frontend asume siempre `error.response.data.mensaje`; si el backend devuelve otro formato, muestra “Error de conexión” (**P2-05**).

---

## 7. Frontend, UX y rendimiento

- El dashboard carga habitaciones, **todas** las reservas y la hora del servidor en cada refresh (**P2-03**).
- La selección de reserva activa usa `find` sobre una lista ordenada por `fechaEntrada DESC`; una reserva futura puede ganar a la actual (**P1-04**).
- La actualización optimista de habitaciones no coincide con el backend al cancelar (**P2-07**).
- El interceptor de 401 cierra sesión correctamente y distingue 403; buena práctica.
- Las rutas pesadas son lazy; buena práctica.
- No hay ErrorBoundary global; un error de render no controlado puede dejar la SPA en blanco. No se marca como hallazgo porque no se reprodujo.
- No hay pruebas de frontend.
- No se evaluó accesibilidad formalmente; no se afirman incumplimientos WCAG.

---

## 8. Pruebas, build, CI y dependencias

### 8.1 Inventario de pruebas

- 3 archivos, 12 pruebas:
  - `ReservaLogicaTest`: 8 pruebas unitarias de noches/late checkout.
  - `AuthSecurityTest`: 3 pruebas de seguridad de registro.
  - `BackofficeApplicationTests`: 1 prueba de contexto.
- No hay pruebas de:
  - servicios de reservas, pedidos, habitaciones, reportes o usuarios;
  - repositorios o consultas JPQL;
  - controladores más allá del registro;
  - concurrencia;
  - frontend;
  - migraciones o datos iniciales.

### 8.2 Configuración de pruebas

`backend/src/test/resources/application-test.yaml` no define datasource. Hereda el de `application.yaml`, que apunta a `jdbc:mariadb://localhost:3306/hotel_bahia` con usuario `hotel`/`hotel_bahia`. Por tanto, **las pruebas de contexto y seguridad requieren una MariaDB local**, y fallan en un entorno limpio.

### 8.3 Build

- `backend/Dockerfile:14` ejecuta `./mvnw package -DskipTests`: la imagen se construye sin pruebas.
- No hay workflows en `.github`.
- El frontend no tiene script `test`; solo `dev`, `build`, `lint`, `preview`.

### 8.4 Dependencias

**Backend:** Spring Boot 4.0.3 arrastra Tomcat 11.0.18, Spring Framework 7.0.5, Spring Security 7.0.3, Jackson 3.0.4 y MariaDB JDBC 3.5.7. OSV reportó **28 advisories** en las cinco coordenadas consultadas:

| Componente | Advisories | Versión objetivo orientativa |
|---|---:|---|
| Tomcat Embed Core 11.0.18 | 16 | 11.0.25+ |
| Jackson Databind 3.0.4 | 5 | 3.1.4+ |
| MariaDB Java Client 3.5.7 | 4 | 3.5.9 |
| Spring Security Core 7.0.3 | 2 | 7.0.5 |
| Spring Core 7.0.5 | 1 | 7.0.8 |

Las versiones objetivo deben validarse contra la última versión compatible de Spring Boot 4.0.x; si el BOM no las incluye, se fijan explícitamente.

**Frontend:** `npm audit` reportó **17 vulnerabilidades** (12 high, 4 moderate, 1 low). En runtime (`--omit=dev`) quedan **4**: 3 high y 1 moderate.

| Paquete | Severidad | Tipo | Observación |
|---|---|---|---|
| `axios` | High | Directa | Múltiples advisories; rango afectado hasta 1.19.0 |
| `zod` | High | Directa | Consumo de memoria con colecciones grandes; `<4.6.6` |
| `form-data` | High | Transitiva | Inyección CRLF; llega vía axios |
| `follow-redirects` | Moderate | Transitiva | Fuga de cabeceras de autenticación en redirecciones |

Las vulnerabilidades de `vite`, `postcss`, `brace-expansion`, `js-yaml`, etc. son de desarrollo/build, pero también deben corregirse para proteger la cadena de suministro.

---

## 9. Hallazgos detallados

### 9.1 P0 — Críticos

#### P0-01 — Credenciales y secretos por defecto/de ejemplo permiten acceso total

- **Prioridad:** P0
- **Ubicación:** `docker-compose.yml:20-23,47,52`; `.env.example:10-11,16,24`; `backend/.../config/DataInitializer.java:165-174`.
- **Descripción:** el compose usa valores conocidos para la base (`hotel`/`hotel_bahia`, root `root_local`), para el admin (`hotel_bahia`) y para el JWT. Además, el inicializador crea siempre usuarios demo `recep@hotel.com`/`recep123` y `limp@hotel.com`/`limp123` si la tabla de usuarios está vacía, también bajo el perfil `docker`.
- **Evidencia:**
  - `docker-compose.yml:52`: `APP_ADMIN_PASSWORD: ${APP_ADMIN_PASSWORD:-hotel_bahia}`.
  - `.env.example:16`: `JWT_SECRET=dev-solo-cambiar-esto-openssl-rand-base64-48-por-favor!!`.
  - `DataInitializer.java:165-174`: creación de recepción y limpieza con contraseñas fijas.
  - El propio `.env.example` indica `cp .env.example .env`, por lo que el camino documentado conserva el secreto conocido.
- **Impacto:** con el `JWT_SECRET` del ejemplo se puede firmar un token con rol `ADMIN`; con las credenciales demo se puede entrar directamente. Un despliegue nuevo queda comprometido sin necesidad de explotar una vulnerabilidad de código.
- **Corrección recomendada:**
  - Eliminar todos los defaults sensibles; usar `${VAR:?error}` para JWT, admin y BD.
  - Generar secretos aleatorios en el despliegue y rotarlos.
  - No crear usuarios demo en `docker`; moverlos a un perfil `demo` con flag explícito y desactivado por defecto.
  - Invalidar todos los tokens existentes tras rotar el secreto.
  - Aplicar mínimo privilegio al usuario de BD.
- **Ejemplo:**
  ```yaml
  environment:
    JWT_SECRET:       ${JWT_SECRET:?JWT_SECRET obligatorio}
    APP_ADMIN_PASSWORD: ${APP_ADMIN_PASSWORD:?APP_ADMIN_PASSWORD obligatorio}
    DATABASE_PASSWORD:  ${DATABASE_PASSWORD:?DATABASE_PASSWORD obligatorio}
    DB_ROOT_PASSWORD:   ${DB_ROOT_PASSWORD:?DB_ROOT_PASSWORD obligatorio}
  ```
  ```java
  @Profile("demo")
  @ConditionalOnProperty(name = "app.seed-demo", havingValue = "true")
  ```
- **Prueba de verificación:** levantar el stack sin `.env` y comprobar que **falla cerrado**; iniciar con `.env.example` y verificar que el login demo ya no existe; firmar un token con el secreto de ejemplo y comprobar `401`.

---

#### P0-02 — MariaDB y backend publicados en todas las interfaces

- **Prioridad:** P0 (condicional a que el host sea alcanzable desde la LAN/Internet)
- **Ubicación:** `docker-compose.yml:17-18,54-55`; `frontend/nginx.conf:2`.
- **Descripción:** el compose publica `3307:3306` y `8080:8080` sin bind a `127.0.0.1`. La API y la base quedan accesibles directamente, sin pasar por Nginx, sin TLS y con credenciales por defecto.
- **Evidencia:** `docker compose config` confirma los puertos publicados; solo el frontend debería ser el punto de entrada.
- **Impacto:** un atacante en la red puede atacar MariaDB o la API directamente, saltarse el proxy, la futura terminación TLS y los controles de borde. Combinado con P0-01, el compromiso es total.
- **Corrección recomendada:**
  - Eliminar `ports` de `db` y `backend`; usar `expose` y la red interna de Compose.
  - Publicar solo `80/443` del frontend.
  - Si se necesita administración local, bindear a `127.0.0.1`.
  - Añadir firewall/security group y segmentación de red.
- **Ejemplo:**
  ```yaml
  db:
    expose: ["3306"]
    # ports: ["127.0.0.1:3307:3306"]  # solo si es imprescindible
  backend:
    expose: ["8080"]
  frontend:
    ports: ["80:80", "443:443"]
  ```
- **Prueba de verificación:** `ss -ltnp` no muestra `3307` ni `8080`; desde otro host, `nc -vz host 3307` y `nc -vz host 8080` fallan; solo `80/443` responden.

---

### 9.2 P1 — Altos

#### P1-01 — JWT en `localStorage`, 24 h, sin revocación ni refresh

- **Prioridad:** P1
- **Ubicación:** `frontend/src/store/useAuthStore.ts:16-35`; `frontend/src/services/api.ts:22-27`; `backend/.../security/JwtTokenProvider.java:38-45`; `backend/src/main/resources/application.yaml:32`.
- **Descripción:** el token se persiste en `localStorage` bajo `hotel-auth` y se envía como `Bearer`. La expiración por defecto es 24 h. No hay `jti`, lista de revocación, refresh token ni endpoint de logout que invalide el token.
- **Evidencia:** `persist(..., { name: 'hotel-auth' })`; `expiration-ms: 86400000`; `logout()` solo limpia el store.
- **Impacto:** cualquier XSS puede exfiltrar el token y usarlo durante 24 h. Un token robado sigue siendo válido aunque el usuario cierre sesión o cambie su contraseña.
- **Corrección recomendada:** migrar a cookie `HttpOnly; Secure; SameSite=Strict` para el acceso, con access token corto (5–15 min) y refresh rotatorio; añadir revocación por sesión (`jti`/tabla de sesiones) y logout server-side; CSP estricta.
- **Ejemplo:**
  ```java
  ResponseCookie cookie = ResponseCookie.from("access", token)
      .httpOnly(true).secure(true).sameSite("Strict")
      .path("/api").maxAge(Duration.ofMinutes(15)).build();
  ```
- **Prueba de verificación:** `localStorage.getItem('hotel-auth')` devuelve `null`; tras logout, reutilizar el token anterior devuelve `401`; cambiar la contraseña invalida sesiones previas.

---

#### P1-02 — HTTP sin TLS ni cabeceras de seguridad

- **Prioridad:** P1
- **Ubicación:** `frontend/nginx.conf:1-3`; `docker-compose.yml:74-75`.
- **Descripción:** Nginx escucha solo en `80`, sin `ssl`, sin redirección a HTTPS y sin cabeceras `Strict-Transport-Security`, `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options` ni `Referrer-Policy`.
- **Evidencia:** no hay directivas `ssl_certificate` ni `add_header` en `nginx.conf`.
- **Impacto:** credenciales, JWT y datos de huéspedes viajan en claro; riesgo de sniffing y MITM en la red del hotel. Sin CSP, un XSS tiene más facilidad para ejecutarse; sin `X-Frame-Options`, hay riesgo de clickjacking.
- **Corrección recomendada:** terminar TLS en Nginx o en un proxy delante; redirigir 80→443; añadir HSTS y cabeceras; si se usan cookies, `Secure` y `SameSite`; restringir CORS al origen real de producción.
- **Ejemplo:**
  ```nginx
  server {
    listen 443 ssl http2;
    ssl_certificate     /etc/nginx/certs/fullchain.pem;
    ssl_certificate_key /etc/nginx/certs/privkey.pem;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options DENY always;
    add_header Referrer-Policy no-referrer always;
    add_header Content-Security-Policy "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline';" always;
  }
  ```
- **Prueba de verificación:** `curl -I http://host` devuelve `301` a HTTPS; `curl -I https://host` incluye las cabeceras; SSL Labs sin avisos graves; origen no listado en CORS es rechazado.

---

#### P1-03 — Estados de habitación sin máquina de estados; invariantes saltables

- **Prioridad:** P1
- **Ubicación:** `backend/.../service/HabitacionService.java:33-59`; `backend/.../controller/HabitacionController.java:29`; `backend/.../config/SecurityConfig.java:44-50`.
- **Descripción:** `PATCH /api/v1/habitaciones/{id}/estado` permite a `ADMIN`, `RECEPCIONISTA` y `LIMPIEZA` escribir cualquier estado. La única restricción es que limpieza no puede poner un estado distinto de `LIBRE`; no se valida el estado actual, la reserva asociada ni las transiciones.
- **Evidencia:** `h.setEstado(estado)` sin comprobar `h.getEstado()` ni `reservaRepo.existsByHabitacionIdAndEstado(id, CHECKIN)`. Una persona de limpieza puede marcar `LIBRE` una habitación `OCUPADA`; recepción puede hacer lo mismo.
- **Impacto:** doble ocupación, pérdida de ingresos, reportes inconsistentes y posibilidad de check-in de otro huésped en una habitación ya ocupada, porque `realizarCheckIn` solo mira si el estado es `OCUPADA`.
- **Corrección recomendada:** implementar una máquina de estados en el servicio, derivar el estado de la reserva y restringir transiciones por rol. Limpieza solo `LIMPIEZA → LIBRE`; recepción no puede liberar una habitación con `CHECKIN` activo; auditar cada cambio.
- **Ejemplo:**
  ```java
  if (estado == EstadoHabitacion.LIBRE
      && reservaRepo.existsByHabitacionIdAndEstado(id, EstadoReserva.CHECKIN)) {
      throw new IllegalStateException("No se puede liberar una habitación ocupada.");
  }
  if (esLimpieza && !(actual == EstadoHabitacion.LIMPIEZA && estado == EstadoHabitacion.LIBRE)) {
      throw new IllegalArgumentException("Transición no permitida para limpieza.");
  }
  ```
- **Prueba de verificación:** matriz rol × estado: limpieza `OCUPADA → LIBRE` devuelve `409`; recepción `CHECKIN → LIBRE` devuelve `409`; tras check-out, `LIMPIEZA → LIBRE` funciona; cada cambio genera auditoría.

---

#### P1-04 — Dashboard selecciona una reserva activa incorrecta

- **Prioridad:** P1
- **Ubicación:** `frontend/src/features/dashboard/hooks/useDashboard.ts:67-70`; `backend/.../service/ReservaService.java:35-38`; `backend/.../repository/ReservaRepository.java:21`.
- **Descripción:** el dashboard usa `reservas.find(...)` y se queda con la primera reserva `CHECKIN` o `CONFIRMADA` de la habitación. La lista llega ordenada por `fechaEntrada DESC`, por lo que una reserva futura `CONFIRMADA` puede aparecer antes que la reserva actual en `CHECKIN`.
- **Evidencia:** `findAllByOrderByFechaEntradaDesc()` y `reservas.find(r => r.habitacionId === hab.id && (r.estado === 'CHECKIN' || r.estado === 'CONFIRMADA'))`.
- **Impacto:** el dashboard muestra el huésped equivocado, oculta una salida pendiente y ofrece acciones de check-in/check-out sobre la reserva incorrecta. El detalle lateral y el panel de acciones usan `reservaActiva`, por lo que el error se propaga a la operación.
- **Corrección recomendada:** priorizar `CHECKIN` sobre `CONFIRMADA`, luego la fecha más cercana; idealmente, exponer un endpoint backend de “estancia actual por habitación” con una única fuente de verdad.
- **Ejemplo:**
  ```ts
  const prioridad = (r: Reserva) => r.estado === 'CHECKIN' ? 0 : 1
  const activa = reservas
    .filter(r => r.habitacionId === hab.id && (r.estado === 'CHECKIN' || r.estado === 'CONFIRMADA'))
    .sort((a, b) => prioridad(a) - prioridad(b) || a.fechaEntrada.localeCompare(b.fechaEntrada))[0] ?? null
  ```
- **Prueba de verificación:** fixture con una reserva `CHECKIN` de ayer y una `CONFIRMADA` futura en la misma habitación; el dashboard debe mostrar la `CHECKIN` y el check-out debe actuar sobre ella.

---

#### P1-05 — Check-out deja pedidos pendientes sin resolver ni cobrar

- **Prioridad:** P1
- **Ubicación:** `backend/.../service/ReservaService.java:200-220`; `backend/.../service/PedidoService.java:56-116,123-165`; `frontend/src/features/pedidos/PedidosPage.tsx:91`.
- **Descripción:** el check-out no comprueba si la reserva tiene pedidos `PENDIENTE`. Después del check-out, `PedidoService.cambiarEstado` impide pasar un pedido a `ENTREGADO` porque la reserva ya no está en `CHECKIN`; esos pedidos quedan varados y su importe nunca entra en `totalConsumo`.
- **Evidencia:** el check-out solo cambia `EstadoReserva.CHECKOUT` y la habitación a `LIMPIEZA`; la validación de entrega exige `reserva.getEstado() == CHECKIN`.
- **Impacto:** pérdida de ingresos por consumo, pedidos sin resolver, reportes que no reflejan la realidad y operadores que no saben si cobrar o cancelar.
- **Corrección recomendada:** bloquear el check-out si hay pendientes (409) y obligar a resolverlos; o cancelarlos automáticamente con motivo y auditoría, o incluirlos en la cuenta y marcarlos entregados. El frontend debe mostrar un modal de resolución antes de confirmar la salida.
- **Ejemplo:**
  ```java
  if (pedidoRepo.existsByReservaIdAndEstado(id, EstadoPedido.PENDIENTE)) {
      throw new IllegalStateException(
          "Hay pedidos pendientes. Resuélvalos antes del check-out.");
  }
  ```
- **Prueba de verificación:** reserva en `CHECKIN` con un pedido `PENDIENTE`; check-out devuelve `409`; tras cancelar el pedido, el check-out funciona; `totalConsumo` coincide con los pedidos entregados.

---

#### P1-06 — Sin bloqueo optimista; carreras en consumo y estados

- **Prioridad:** P1
- **Ubicación:** entidades `Reserva`, `Pedido`, `Habitacion` (sin `@Version`); `backend/.../service/PedidoService.java:143-160`; `ReservaService.java:190,206`; `ReservaRepository.java`.
- **Descripción:** la creación de reservas usa bloqueo pesimista, pero las transiciones posteriores (check-in, check-out, entrega/cancelación de pedidos, cambio de estado de habitación) no tienen bloqueo optimista ni pesimista. `totalConsumo` se actualiza leyendo, sumando y guardando.
- **Evidencia:** no existe `@Version` en ninguna entidad; `findByIdAndEstado` no usa `@Lock`; el consumo se modifica con `reserva.setTotalConsumo(actualCon.add(...))`.
- **Impacto:** dos entregas concurrentes de pedidos distintos pueden perder una actualización de `totalConsumo`; dos transiciones simultáneas pueden dejar estados inconsistentes; los reintentos pueden duplicar efectos.
- **Corrección recomendada:** añadir `@Version` a las entidades críticas; usar bloqueo pesimista en transiciones de estado; actualizar el consumo de forma atómica; añadir constraints únicos para pedidos/consumos.
- **Ejemplo:**
  ```java
  @Version
  private Long version;

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Reserva r where r.id = :id")
  Optional<Reserva> findWithLockById(@Param("id") UUID id);
  ```
- **Prueba de verificación:** prueba concurrente con dos hilos entregando pedidos distintos de la misma reserva; el total final debe ser la suma exacta; una transición conflictiva debe devolver `409` y no sobrescribir silenciosamente.

---

#### P1-07 — Reportes duplican ingresos/reservas y usan fechas planificadas

- **Prioridad:** P1
- **Ubicación:** `backend/.../service/ReporteService.java:40-55,88-100,103-124,160-205`; `backend/.../repository/ReservaRepository.java:49-61`.
- **Descripción:** `findEnPeriodo` incluye toda reserva que solape el período usando `fechaSalida >= inicio`. Luego `calcularResumen` imputa el **total completo** de la estancia a cualquier período en el que solape. Una estancia del 30 de enero al 2 de febrero se cuenta completa en enero y completa en febrero. Además, la ocupación usa `fechaSalida` planificada, no `checkoutReal`.
- **Evidencia:** `WHERE r.fechaEntrada <= :fin AND r.fechaSalida >= :inicio`; `ingresoTotal = facturadas.stream().map(Reserva::calcularTotalEstancia)`; `ocupacionReal` no usa `checkoutReal`.
- **Impacto:** ingresos y número de reservas inflados en reportes mensuales; ocupación incorrecta si hay salidas anticipadas o late checkout; decisiones de negocio erróneas. `ingresoTotal` también incluye consumo, y `ingresoConsumo` es un subconjunto, lo que puede confundir al consumidor de la API.
- **Corrección recomendada:** definir períodos semiabiertos (`[inicio, finExclusivo)`), prorratear ingresos por noche dentro del período, separar ingreso de habitación y consumo, y calcular ocupación con `checkoutReal` cuando exista. Documentar que `ingresoConsumo` no se suma a `ingresoTotal`.
- **Ejemplo:**
  ```java
  @Query("""
    SELECT r FROM Reserva r
    WHERE r.estado <> 'CANCELADA'
      AND r.fechaEntrada < :finExclusivo
      AND r.fechaSalida  > :inicio
  """)
  ```
  ```java
  long nochesEnPeriodo = Math.max(0,
      ChronoUnit.DAYS.between(max(entrada, inicio), min(salida, finExclusivo)));
  ```
- **Prueba de verificación:** fixture 30 ene → 2 feb; la suma de ingresos de enero y febrero debe ser el total una sola vez; la ocupación de enero+febrero debe sumar 3 noches; un late checkout debe sumar la noche extra a ocupación y a ingreso.

---

#### P1-08 — Dependencias con vulnerabilidades altas

- **Prioridad:** P1
- **Ubicación:** `backend/pom.xml`; `frontend/package.json`; `package-lock.json`.
- **Descripción:** el backend usa versiones transitivas con advisories de Tomcat, Spring, Jackson y MariaDB. El frontend tiene 4 vulnerabilidades de runtime, incluidas `axios` y `zod` directas.
- **Evidencia:** `npm audit`: 17 totales, 4 de runtime (3 high, 1 moderate). OSV: 28 advisories en Tomcat 11.0.18 (16), Jackson 3.0.4 (5), MariaDB JDBC 3.5.7 (4), Spring Security 7.0.3 (2), Spring Core 7.0.5 (1).
- **Impacto:** según el CVE y la configuración, puede haber DoS, SSRF, contaminación de prototipos, divulgación de información o ejecución remota. No se demostró explotación en este contexto, pero la superficie existe.
- **Corrección recomendada:** actualizar Spring Boot al último 4.0.x y fijar las versiones corregidas; actualizar `axios` y `zod`; ejecutar SCA en CI; eliminar dependencias no usadas; revisar alertas de imágenes Docker.
- **Ejemplo:**
  ```xml
  <properties>
    <tomcat.version>11.0.25</tomcat.version>
    <spring-framework.version>7.0.8</spring-framework.version>
    <spring-security.version>7.0.5</spring-security.version>
  </properties>
  ```
  ```bash
  npm install axios@latest zod@^4.6.6
  npm audit --omit=dev --audit-level=high
  ```
- **Prueba de verificación:** `npm audit --omit=dev` sin high/critical; escaneo OSV/Trivy sin high/critical; `mvn dependency:tree` confirma las versiones; suite de regresión en verde.

---

#### P1-09 — Sin migraciones; `ddl-auto: update` y migración destructiva

- **Prioridad:** P1
- **Ubicación:** `backend/src/main/resources/application-docker.yml:12`; `application.yaml:16`; `backend/.../config/DataInitializer.java:115-142`; `pom.xml` (sin Flyway/Liquibase).
- **Descripción:** el perfil Docker usa `ddl-auto: update`, que modifica el esquema en caliente y no es reproducible. El perfil base usa `validate`, pero Docker lo sobrescribe. Además, `DataInitializer.seedMenu()` elimina elementos del menú sin pedidos y deshabilita los que tienen historial, como “migración” automática en cada arranque.
- **Evidencia:** `ddl-auto: update`; `menuRepo.delete(m)` y `m.setDisponible(false)` dentro de `seedMenu`; no hay carpeta `db/migration` ni dependencia de migraciones.
- **Impacto:** deriva de esquema entre entornos, despliegues no reproducibles, imposibilidad de rollback y pérdida de datos del menú. Un arranque en producción puede alterar datos sin intervención humana.
- **Corrección recomendada:** introducir Flyway/Liquibase con baseline; cambiar a `ddl-auto: validate`; separar seed de migración; ejecutar seeds solo en perfil `demo` y con flag; respaldar antes de migrar.
- **Ejemplo:**
  ```yaml
  spring:
    jpa:
      hibernate:
        ddl-auto: validate
    flyway:
      enabled: true
      locations: classpath:db/migration
  ```
- **Prueba de verificación:** base vacía → `flyway migrate` crea el esquema; segundo arranque no cambia nada; el perfil de producción no ejecuta seeds; el menú histórico no se modifica.

---

#### P1-10 — Cobertura insuficiente, tests dependientes de BD y sin CI

- **Prioridad:** P1
- **Ubicación:** `backend/src/test/java/...`; `backend/src/test/resources/application-test.yaml:6-11`; `backend/Dockerfile:14`; ausencia de `.github`.
- **Descripción:** hay 12 pruebas, de las cuales solo 8 son unitarias puras. Las pruebas de contexto y seguridad usan `@SpringBootTest` y `@ActiveProfiles("test")`, pero `application-test.yaml` no define datasource, por lo que heredan MariaDB local. Docker compila con `-DskipTests`. No hay CI ni pruebas de frontend.
- **Evidencia:** 3 archivos de test; `application-test.yaml` solo define JWT y `ddl-auto: update`; `Dockerfile:14` `-DskipTests`; no existe `.github/workflows`.
- **Impacto:** los bugs P1 de estados, checkout, reportes y concurrencia no están cubiertos; cualquier refactor puede romper la facturación sin que nadie lo detecte. Los tests no son reproducibles en un entorno limpio.
- **Corrección recomendada:** Testcontainers con MariaDB para integración; pruebas de servicios y controladores; pruebas de frontend con Vitest/Testing Library; CI con `mvn verify`, `npm run build`, `npm run lint` y `npm test`; umbral de cobertura en rutas críticas; no saltar tests en el pipeline de imagen.
- **Ejemplo:**
  ```yaml
  # .github/workflows/ci.yml
  - run: ./mvnw verify
  - run: npm ci && npm run lint && npm run build
  ```
  ```java
  @Testcontainers
  @SpringBootTest
  class ReservaServiceIT {
      @Container static MariaDBContainer<?> db = new MariaDBContainer<>("mariadb:11");
  }
  ```
- **Prueba de verificación:** CI ejecuta todos los tests en un runner limpio; una rotura intencionada en checkout/reportes hace fallar el pipeline; cobertura mínima acordada (p. ej. 70 % en servicios, 100 % en cálculos financieros).

---

### 9.3 P2 — Medios

#### P2-01 — Rate limiter en memoria: bloqueo de cuentas, memoria y no escala

- **Prioridad:** P2
- **Ubicación:** `backend/.../security/LoginRateLimiter.java:14-49`; `AuthService.java:27-45`.
- **Descripción:** se permiten 5 fallos por email en 15 minutos. No hay límite por IP ni global, el mapa no tiene TTL para emails que nunca vuelven a intentar, y el estado es local al proceso.
- **Evidencia:** `ConcurrentHashMap<String, Deque<Long>> intentos`; `bloqueado(email)`; `registrarFallo` crea una entrada por email; no hay integración con Redis/Caffeine.
- **Impacto:** un atacante puede bloquear cuentas conocidas con 5 intentos; puede hacer password spraying desde una IP contra muchas cuentas; el mapa crece con emails aleatorios; con varias instancias el límite no se comparte.
- **Corrección recomendada:** rate limiting distribuido (Redis + Bucket4j) por email **y** IP, con ventana deslizante, TTL, límite global y backoff exponencial; métricas y CAPTCHA tras varios fallos.
- **Ejemplo:**
  ```java
  Bucket bucket = buckets.builder()
      .addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(15))))
      .build();
  ```
- **Prueba de verificación:** 6 intentos fallidos → `429`; emails aleatorios no aumentan memoria sin límite; dos instancias comparten el contador; una IP con muchos emails distintos recibe `429`.

---

#### P2-02 — Sin auditoría de acciones sensibles

- **Prioridad:** P2
- **Ubicación:** entidades y servicios; no hay tabla de auditoría.
- **Descripción:** se guardan `createdAt`/`updatedAt`, pero no quién hizo el cambio, desde qué IP, ni el valor anterior/posterior. Check-in, check-out, cancelaciones, cambios de estado de habitación, precios y usuarios no dejan traza.
- **Impacto:** imposible investigar fraude, discrepancias de caja o cambios no autorizados; difícil cumplir requisitos de trazabilidad.
- **Corrección recomendada:** tabla append-only `auditoria` con actor, acción, entidad, id, datos antes/después, IP y timestamp; registrar eventos de seguridad; no permitir updates/delete sobre la tabla.
- **Ejemplo:**
  ```java
  audit.registrar(usuario, "CHECKOUT", "Reserva", id,
      Map.of("estado", anterior), Map.of("estado", nuevo));
  ```
- **Prueba de verificación:** cada operación sensible genera una fila; el usuario y la IP son correctos; la tabla rechaza modificaciones.

---

#### P2-03 — Sin paginación y N+1 en pedidos; dashboard carga todo

- **Prioridad:** P2
- **Ubicación:** `ReservaService.java:36`; `PedidoService.java:44`; `UsuarioService.java:29`; `useDashboard.ts:42-46`; `Pedido.java:24-50`; `PedidoResponseDTO`.
- **Descripción:** los listados usan `findAll` y devuelven todos los registros. El dashboard pide todas las reservas en cada refresh. `PedidoResponseDTO.from` accede a relaciones `LAZY` (`reserva.huesped`, `habitacion`, `usuario`, `items`, `items.menuItem`), lo que genera N+1 al listar pedidos.
- **Impacto:** con 50 habitaciones y pocos pedidos el rendimiento es aceptable; al crecer el histórico, el dashboard y los listados se degradan linealmente y la base recibe muchas consultas.
- **Corrección recomendada:** paginación y filtros server-side; proyecciones/resúmenes para el dashboard; `@EntityGraph` o `@BatchSize` en pedidos; límites por defecto.
- **Ejemplo:**
  ```java
  Page<Reserva> findByEstado(EstadoReserva estado, Pageable pageable);
  ```
  ```java
  @EntityGraph(attributePaths = {"items", "items.menuItem", "reserva.huesped", "habitacion", "usuario"})
  List<Pedido> findAllByOrderByCreatedAtDesc();
  ```
- **Prueba de verificación:** con 10.000 reservas, el dashboard responde en un tiempo acotado; el número de queries por listado de pedidos es constante; la API rechaza `size` arbitrariamente grande.

---

#### P2-04 — Consulta a BD en cada request y usuario borrado → 500

- **Prioridad:** P2
- **Ubicación:** `backend/.../security/JwtAuthFilter.java:32-45`; `backend/.../security/UserDetailsServiceImpl.java:20-21`.
- **Descripción:** cada request con token consulta `usuarioRepo.findByEmail`. Si el usuario ya no existe, `orElseThrow` lanza `UsernameNotFoundException` dentro del filtro; al estar antes de `ExceptionTranslationFilter`, puede terminar en 500.
- **Impacto:** carga innecesaria en BD; un usuario eliminado o una condición de carrera produce un error 500 en lugar de 401; facilita ruido y posibles fugas de stack en logs.
- **Corrección recomendada:** cachear `UserDetails` con TTL corto, capturar `AuthenticationException` en el filtro y devolver 401; usar claims para identidad y una tabla de revocación para invalidar.
- **Ejemplo:**
  ```java
  try {
      userDetails = userDetailsService.loadUserByUsername(email);
  } catch (UsernameNotFoundException e) {
      SecurityContextHolder.clearContext();
      response.sendError(401);
      return;
  }
  ```
- **Prueba de verificación:** usuario eliminado + token válido → `401`, no `500`; un request autenticado no ejecuta más de una consulta de usuario (o ninguna con caché).

---

#### P2-05 — Mapeo de errores incompleto y contrato inconsistente

- **Prioridad:** P2
- **Ubicación:** `backend/.../exception/GlobalExceptionHandler.java:80-87`; `frontend/src/services/api.ts:47`.
- **Descripción:** no hay handlers para `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException` ni `ConstraintViolationException`; caen en el handler genérico y devuelven 500. El frontend solo lee `data.mensaje`.
- **Impacto:** errores de cliente se reportan como fallos del servidor; mensajes poco útiles; logs contaminados.
- **Corrección recomendada:** añadir handlers específicos con 400 y códigos estables; usar `ProblemDetail`; el frontend debe aceptar `mensaje`, `message` o `detail` con fallback.
- **Ejemplo:**
  ```java
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ErrorResponse> tipoInvalido(MethodArgumentTypeMismatchException ex) {
      return ResponseEntity.badRequest()
          .body(new ErrorResponse("PARAMETRO_INVALIDO", ex.getName()));
  }
  ```
- **Prueba de verificación:** `GET /api/v1/reservas?estado=INVALIDO` devuelve 400 con código; el frontend muestra el mensaje del backend y no “Error de conexión”.

---

#### P2-06 — Contraseñas débiles y riesgo de bloquear al último admin

- **Prioridad:** P2
- **Ubicación:** `UsuarioRequestDTO.java:13`, `PasswordDTO.java:7`, `RegisterRequestDTO.java:13`; `UsuarioService.java:65-75`; `UsuarioController.java`.
- **Descripción:** la política mínima es 6 caracteres, sin complejidad, sin comprobación de filtraciones y sin historial. `actualizar` permite cambiar rol y `activo` de cualquier usuario, incluido el último administrador o uno mismo.
- **Impacto:** contraseñas fáciles de adivinar; bloqueo accidental o malicioso del sistema al desactivar al último admin; recuperación manual.
- **Corrección recomendada:** mínimo 12 caracteres, comprobación contra listas de filtraciones, hash fuerte (Argon2id o BCrypt coste 12), cambio de contraseña propia con contraseña actual, prohibir desactivar/degradar al último admin activo y exigir MFA para admin.
- **Ejemplo:**
  ```java
  long adminsActivos = repo.countByRolAndActivoTrue(Rol.ADMIN);
  if (u.getRol() == Rol.ADMIN && adminsActivos == 1
      && (!Boolean.TRUE.equals(dto.activo()) || !"ADMIN".equals(dto.rol()))) {
      throw new IllegalStateException("No se puede desactivar al último administrador.");
  }
  ```
- **Prueba de verificación:** contraseña de 6 caracteres → 400; desactivar al último admin → 409; cambiar la propia contraseña sin la actual → 400; con la actual → 204.

---

#### P2-07 — Desincronización visual de habitación al cancelar

- **Prioridad:** P2
- **Ubicación:** `frontend/src/features/dashboard/hooks/useDashboard.ts:13-17,114-122`; `backend/.../service/ReservaService.java:222-238`.
- **Descripción:** el frontend mapea `CANCELADA → LIBRE`. El backend, al cancelar una reserva en `CHECKIN`, pone la habitación en `LIMPIEZA`. La UI no recarga la habitación y queda mostrando `LIBRE`.
- **Impacto:** la habitación aparece disponible cuando requiere limpieza; puede provocar una asignación prematura o confusión operativa.
- **Corrección recomendada:** tras una mutación, recargar habitaciones/reservas; o eliminar el mapeo local y usar el estado devuelto por el backend. Lo ideal es que la respuesta incluya el estado actualizado de la habitación.
- **Ejemplo:**
  ```ts
  const actualizada = await accion()
  setReservas(...)
  await cargar() // fuente de verdad
  ```
- **Prueba de verificación:** cancelar una reserva en `CHECKIN`; la habitación debe mostrarse `LIMPIEZA` y seguir así tras recargar.

---

#### P2-08 — Sin idempotencia en creación de reservas/pedidos

- **Prioridad:** P2
- **Ubicación:** `ReservaController.java:37`; `PedidoController.java:36`; servicios de creación.
- **Descripción:** no hay `Idempotency-Key` ni restricción única por solicitud. Un doble clic, un timeout con reintento o un proxy pueden crear dos reservas o dos pedidos.
- **Impacto:** duplicidad de cargos, pedidos y ocupación; corrección manual.
- **Corrección recomendada:** aceptar una clave de idempotencia generada por el cliente, persistirla con restricción única y devolver el recurso existente; deshabilitar el botón mientras se procesa; constraints de unicidad para pedidos.
- **Ejemplo:**
  ```java
  @PostMapping
  public ResponseEntity<ReservaResponseDTO> crear(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody ReservaRequestDTO dto) { ... }
  ```
- **Prueba de verificación:** dos POST con la misma clave devuelven el mismo `id` y solo existe un registro; dos POST sin clave pero con el mismo payload siguen el comportamiento definido y documentado.

---

#### P2-09 — No hay registro/cobro de pagos; `Pago` sin uso

- **Prioridad:** P2
- **Ubicación:** `backend/.../entity/Pago.java`; `ReservaService.java:200-220`.
- **Descripción:** existe la entidad `Pago`, pero no hay repositorio, servicio, controlador ni flujo que la use. El check-out no registra pago, método, importe ni estado; no hay cuenta cerrada.
- **Impacto:** no se puede conciliar caja, no se sabe si una estancia está pagada, y los reportes de ingresos no distinguen cobrado de facturado.
- **Corrección recomendada:** implementar el flujo de pagos (efectivo/tarjeta/transferencia), estado `PENDIENTE/PAGADO/REEMBOLSADO`, recibo y cierre de cuenta; el check-out debe exigir pago o marcar deuda; incluir pagos en los reportes.
- **Ejemplo:**
  ```java
  Pago pago = Pago.builder()
      .reserva(reserva).monto(total)
      .metodoPago(MetodoPago.EFECTIVO)
      .estadoPago(EstadoPago.PAGADO).build();
  pagoRepo.save(pago);
  ```
- **Prueba de verificación:** check-out sin pago queda marcado como pendiente; con pago genera un registro; el reporte separa facturado, cobrado y pendiente.

---

#### P2-10 — Endurecimiento Docker/operación insuficiente

- **Prioridad:** P2
- **Ubicación:** `docker-compose.yml`; `backend/Dockerfile`; `frontend/Dockerfile`; `nginx.conf`.
- **Descripción:** las imágenes usan tags flotantes (`mariadb:11`, `nginx:alpine`, `node:22-alpine`, `eclipse-temurin:21-*`), no hay límites de CPU/memoria, Nginx corre como root en el contenedor, no hay backup automatizado ni observabilidad (métricas, trazas, logs centralizados).
- **Impacto:** builds no reproducibles, riesgo de suministro, un contenedor puede consumir todos los recursos del host y no hay plan de recuperación.
- **Corrección recomendada:** fijar imágenes por digest, usar Nginx unprivileged, `read_only` + `tmpfs`, límites de recursos, backups programados de MariaDB, logs centralizados, métricas y healthchecks internos; escanear imágenes en CI.
- **Ejemplo:**
  ```yaml
  frontend:
    image: nginxinc/nginx-unprivileged:1.27-alpine
    read_only: true
    tmpfs: ["/tmp"]
    deploy:
      resources:
        limits: { cpus: "0.50", memory: 256M }
  ```
- **Prueba de verificación:** `docker compose config` muestra límites; Trivy no reporta críticos; un backup restaurado en un entorno limpio reproduce los datos; el contenedor no escribe fuera de los volúmenes permitidos.

---

### 9.4 P3 — Bajos

#### P3-01 — Código muerto y restos de refactor

- **Prioridad:** P3
- **Ubicación:** `backend/.../entity/Pago.java`; `PedidoService.java:84`; `frontend/src/lib/format.ts:17`; `AuthController.java:12`.
- **Descripción:** `Pago` no se usa; `List<Pedido> duplicado` no se usa; `diffNoches` no se usa; `ResponseEntity` está importado sin uso en `AuthController`.
- **Impacto:** confusión, mantenimiento innecesario, falsas expectativas sobre funcionalidad existente.
- **Corrección recomendada:** eliminar el código muerto o implementar la funcionalidad; activar `-Werror`/lint para imports y variables sin uso.
- **Ejemplo:** borrar `duplicado` y `diffNoches`; eliminar el import; decidir si `Pago` se implementa en P2-09 o se elimina.
- **Prueba de verificación:** compilación sin warnings relevantes; `grep` no encuentra referencias; lint limpio.

---

#### P3-02 — Documentación y runbook ausentes

- **Prioridad:** P3
- **Ubicación:** raíz del repositorio; `backend/HELP.md`; `frontend/README.md`.
- **Descripción:** no hay README raíz; el HELP del backend es genérico y el README del frontend es la plantilla de Vite. No hay guía de variables de entorno, despliegue, backup, recuperación, roles ni flujo operativo.
- **Impacto:** onboarding lento, errores de configuración, dependencia de conocimiento tribal.
- **Corrección recomendada:** README raíz con arquitectura, requisitos, `.env`, arranque, pruebas, seguridad y troubleshooting; runbook de backup/restore y rotación de secretos; documentar la API.
- **Ejemplo:** secciones `Quick start`, `Configuration`, `Operations`, `Security`, `Troubleshooting`, `API`.
- **Prueba de verificación:** una persona nueva puede levantar el stack y ejecutar pruebas siguiendo solo el README; revisión de documentación en cada release.

---

#### P3-03 — Zona horaria global mutable; difícil de testear

- **Prioridad:** P3
- **Ubicación:** `backend/.../config/TimezoneConfig.java:12-19`; usos de `LocalDate.now()`/`LocalDateTime.now()` en servicios y entidades.
- **Descripción:** se cambia `TimeZone.setDefault(...)` globalmente al arrancar y luego se usa `now()` sin `Clock`. Es funcional, pero el estado global afecta a toda la JVM y a las pruebas.
- **Impacto:** pruebas sensibles al entorno, posibles diferencias entre local y Docker, dificultad para simular fechas límite.
- **Corrección recomendada:** inyectar `Clock` con `ZoneId` y usarlo en servicios; evitar mutar el default global; en pruebas, usar un `Clock.fixed`.
- **Ejemplo:**
  ```java
  @Bean Clock clock(@Value("${app.timezone}") String tz) {
      return Clock.system(ZoneId.of(tz));
  }
  LocalDate hoy = LocalDate.now(clock);
  ```
- **Prueba de verificación:** pruebas con `TZ=UTC` y `APP_TIMEZONE=America/Lima` dan los mismos resultados de negocio; los límites de check-in/check-out se simulan con reloj fijo.

---

#### P3-04 — CORS con defaults de localhost y credenciales habilitadas

- **Prioridad:** P3
- **Ubicación:** `backend/.../config/CorsConfig.java:25-44`.
- **Descripción:** se permiten orígenes `localhost`/`127.0.0.1` por defecto y `allowCredentials(true)`. Con JWT en cabecera, las credenciales no son necesarias; si se migra a cookies, CORS y CSRF deben revisarse juntos.
- **Impacto:** configuración de producción incompleta si no se define `FRONTEND_URL`; riesgo de confusión al habilitar credenciales sin necesidad.
- **Corrección recomendada:** configuración por perfil, origen exacto de producción, `allowCredentials(false)` mientras se use Bearer, y revisión de CSRF si se adoptan cookies.
- **Ejemplo:**
  ```java
  config.setAllowedOrigins(List.of(frontendUrl));
  config.setAllowCredentials(false); // true solo con cookies y CSRF
  ```
- **Prueba de verificación:** preflight desde origen no listado falla; desde el origen correcto funciona; no se permiten comodines con credenciales.

---

## 10. Plan de corrección por fases

### Fase 0 — Contención (24–48 h)

**Objetivo:** eliminar el compromiso inmediato.

1. Rotar `JWT_SECRET`, contraseña de admin, usuarios demo y credenciales de BD.
2. Eliminar defaults sensibles del compose y hacer que falle si faltan variables.
3. Quitar `ports` de `db` y `backend`; exponer solo `80/443`.
4. Poner TLS y cabeceras de seguridad.
5. Actualizar dependencias con vulnerabilidades altas.
6. Configurar backup de MariaDB y probar restauración.

**Criterio de salida:** sin credenciales conocidas; sin `3307`/`8080` accesibles; HTTPS; `npm audit --omit=dev` y OSV sin high/critical; backup restaurado.

**Esfuerzo:** S–M · **Riesgo:** bajo.

### Fase 1 — Integridad del negocio (1–2 semanas)

**Objetivo:** evitar pérdidas de dinero y estados inconsistentes.

1. Máquina de estados de habitaciones y permisos por transición (P1-03).
2. Corregir selección de reserva activa en dashboard (P1-04).
3. Resolver pedidos pendientes en check-out (P1-05).
4. Añadir `@Version` y bloqueos en transiciones (P1-06).
5. Corregir reportes: períodos semiabiertos, prorrateo, `checkoutReal` (P1-07).
6. Corregir desincronización al cancelar (P2-07).

**Criterio de salida:** pruebas de integración de estados, checkout y reportes; conciliación manual enero/febrero; sin doble ocupación posible desde la API.

**Esfuerzo:** M–L · **Riesgo:** medio (cambios de lógica).

### Fase 2 — Calidad y operación (2–6 semanas)

1. Flyway/Liquibase y `ddl-auto: validate` (P1-09).
2. Testcontainers, pruebas de servicios/controladores/frontend y CI (P1-10).
3. Auditoría de acciones sensibles (P2-02).
4. Rate limiting distribuido (P2-01).
5. Paginación, proyecciones y solución de N+1 (P2-03).
6. Errores 400 y contrato frontend (P2-05).
7. Política de contraseñas y protección del último admin (P2-06).
8. Idempotencia (P2-08).

**Criterio de salida:** CI verde con cobertura mínima; migraciones reproducibles; auditoría operativa; listados paginados.

**Esfuerzo:** L · **Riesgo:** medio.

### Fase 3 — Producto y endurecimiento (backlog)

1. Flujo de pagos y cierre de cuenta (P2-09).
2. Endurecimiento Docker/observabilidad (P2-10).
3. Documentación/runbook (P3-02).
4. Limpieza de código muerto (P3-01).
5. `Clock` inyectable (P3-03).
6. CORS por entorno (P3-04).

**Criterio de salida:** cuenta cobrable, operación observable, documentación completa.

**Esfuerzo:** M–L · **Riesgo:** bajo.

---

## 11. Criterios de aceptación y verificación

### 11.1 Seguridad

- [ ] `docker compose config` falla si faltan `JWT_SECRET`, `APP_ADMIN_PASSWORD`, `DATABASE_PASSWORD` o `DB_ROOT_PASSWORD`.
- [ ] `ss -ltnp` no muestra `3307` ni `8080`; solo `80/443`.
- [ ] `curl -I http://host` redirige a HTTPS; HTTPS incluye HSTS, CSP, `X-Content-Type-Options`, `X-Frame-Options` y `Referrer-Policy`.
- [ ] `localStorage` no contiene el token; logout invalida la sesión en servidor.
- [ ] `npm audit --omit=dev --audit-level=high` y OSV no reportan high/critical.
- [ ] Usuario eliminado con token válido devuelve 401, no 500.
- [ ] Limpieza no puede liberar una habitación ocupada; recepción no puede saltarse la máquina de estados.

### 11.2 Integridad y negocio

- [ ] Reserva `CHECKIN` + reserva futura `CONFIRMADA` en la misma habitación: el dashboard muestra la actual.
- [ ] Check-out con pedido `PENDIENTE` devuelve 409 o resuelve explícitamente; no deja pedidos varados.
- [ ] Dos entregas concurrentes no pierden consumo; el total es la suma exacta.
- [ ] Reporte enero+febrero de una estancia que cruza mes suma el ingreso una sola vez y la ocupación correcta.
- [ ] Cancelar una reserva en `CHECKIN` deja la habitación en `LIMPIEZA` también en la UI.

### 11.3 Calidad

- [ ] `./mvnw verify` pasa en un runner limpio con Testcontainers.
- [ ] `npm ci && npm run lint && npm run build` pasa.
- [ ] Cobertura mínima acordada; rutas financieras con pruebas dedicadas.
- [ ] Migración desde base vacía y desde base existente probada; segundo arranque idempotente.
- [ ] Backup restaurado en entorno limpio.

### 11.4 Comandos útiles

```bash
# Puertos expuestos
docker compose config | grep -E '3307|8080'
ss -ltnp | grep -E ':3307|:8080'

# Cabeceras
curl -sI https://localhost | grep -Ei 'strict-transport|content-security|x-frame|x-content-type|referrer'

# Dependencias
cd frontend && npm audit --omit=dev --audit-level=high
cd backend && ./mvnw dependency:tree

# Pruebas
cd backend && ./mvnw verify
cd frontend && npm run lint && npm run build
```

---

## 12. Limitaciones, conclusiones y anexos

### 12.1 Limitaciones

- No se ejecutaron builds, tests, lint ni typecheck por falta de dependencias/caché y autorización para descargarlas.
- No se ejecutaron pruebas de concurrencia ni de carga; P1-06 se confirma por inspección, no por reproducción.
- No se ejecutó un pentest ni un escaneo dinámico.
- Las cifras de vulnerabilidades son una foto del momento; pueden variar al actualizar dependencias.
- No se evaluó accesibilidad ni compatibilidad de navegadores de forma formal.
- No se inspeccionó el historial completo de Git en busca de secretos antiguos; solo el estado actual y los archivos versionados.

### 12.2 Lo que se descartó como hallazgo

- **Registro público:** no existe; `AuthController.register` está restringido a `ADMIN`.
- **Inyección SQL:** no se encontró concatenación de SQL; se usan repositorios JPA y parámetros.
- **Secretos reales versionados:** no hay `.env` ni claves privadas en el árbol actual; solo el ejemplo con valores conocidos (que sigue siendo P0-01).
- **Backend como root:** el Dockerfile usa `USER hotel`; buena práctica.
- **CORS comodín:** no se usa `*`; los orígenes son explícitos, aunque con defaults de localhost (P3-04).
- **`Pago` como vulnerabilidad:** es código muerto, no un fallo de seguridad por sí mismo (P3-01/P2-09).

### 12.3 Conclusión

El proyecto demuestra una comprensión sólida de la arquitectura por capas, la seguridad básica con Spring Security/JWT, la validación de DTOs y la organización del frontend por features. Para un MVP o una demo, es un buen punto de partida.

Para producción, el sistema **no está listo** por cuatro razones: credenciales y secretos conocidos, exposición directa de la base/API, reglas de estado que pueden romperse desde la API y reportes que no son financieramente confiables. La buena noticia es que los P0/P1 se pueden contener en pocos días y corregir de forma incremental sin reescribir la arquitectura.

**Prioridad recomendada:** Fase 0 inmediata; Fase 1 antes de operar con huéspedes reales; Fase 2 antes de escalar o auditar; Fase 3 como mejora continua.
