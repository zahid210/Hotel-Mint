# Hotel Mint — Frontend SPA

Single Page Application construida con React 19, TypeScript 5, Vite 8 y Tailwind CSS.

## Comandos de Desarrollo

- **Instalar dependencias:**
  ```bash
  npm install
  ```
- **Iniciar servidor de desarrollo (Vite):**
  ```bash
  npm run dev
  ```
- **Verificación de linting:**
  ```bash
  npm run lint
  ```
- **Compilar para producción:**
  ```bash
  npm run build
  ```

## Estructura de Características (Feature-driven)

- `src/features/auth/`: Inicio de sesión y manejo de credenciales.
- `src/features/dashboard/`: Grilla operativa de habitaciones, estados en vivo y check-in/out rápido.
- `src/features/reservas/`: Calendario y listado de reservas, creación con clave de idempotencia.
- `src/features/pedidos/`: Servicio de habitaciones (Room Service) y catálogo de bocaditos/bebidas.
- `src/features/reportes/`: Métricas de ocupación, ingresos y rendimiento financiero.
- `src/features/usuarios/`: Gestión administrativa de operadores y control de roles.
- `src/services/`: Clientes API Axios con inyección de Bearer tokens y manejo unificado de errores.
