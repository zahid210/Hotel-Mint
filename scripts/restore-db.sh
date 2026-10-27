#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# Hotel Bahía — Restauración de respaldo de base de datos MariaDB
# Uso:
#   ./scripts/restore-db.sh backups/backup_hotel_bahia_YYYYMMDD_HHMMSS.sql.gz
# ─────────────────────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/.env"

if [ -f "$ENV_FILE" ]; then
    # shellcheck disable=SC1090
    set -a; source "$ENV_FILE"; set +a
fi

DB_NAME="${DB_NAME:-hotel_bahia}"
DB_ROOT_PASSWORD="${DB_ROOT_PASSWORD:-}"
DB_CONTAINER="hotel-bahia-db"

BACKUP_FILE="${1:-}"

if [ -z "$BACKUP_FILE" ]; then
    echo "Uso: $0 <archivo_respaldo.sql.gz>"
    echo "Archivos disponibles en backups/:"
    ls -lh "$ROOT/backups"/*.sql.gz 2>/dev/null || echo " (No hay respaldos todavía)"
    exit 1
fi

if [ ! -f "$BACKUP_FILE" ]; then
    echo "❌ Archivo no encontrado: $BACKUP_FILE"
    exit 1
fi

echo "⚠️  ADVERTENCIA: Esta operación sobrescribirá los datos actuales de '$DB_NAME'."
read -rp "¿Desea continuar? (escriba 'si' para confirmar): " CONFIRM
if [ "$CONFIRM" != "si" ]; then
    echo "Operación cancelada."
    exit 0
fi

echo "• Restaurando respaldo en '$DB_NAME'..."

if docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
    echo "• Restaurando en contenedor Docker '$DB_CONTAINER'..."
    gunzip -c "$BACKUP_FILE" | docker exec -i -e MARIADB_PWD="${DB_ROOT_PASSWORD}" "$DB_CONTAINER" \
        mariadb -u root "$DB_NAME"
elif command -v mariadb >/dev/null 2>&1 || command -v mysql >/dev/null 2>&1; then
    CLIENT_BIN="$(command -v mariadb || command -v mysql)"
    echo "• Restaurando en instancia local con $CLIENT_BIN..."
    gunzip -c "$BACKUP_FILE" | MARIADB_PWD="${DB_ROOT_PASSWORD:-${DB_PASSWORD:-}}" "$CLIENT_BIN" \
        -u "${DB_USER:-root}" -h "${DB_HOST:-127.0.0.1}" -P "${DB_PORT:-3306}" "$DB_NAME"
else
    echo "❌ Error: No se encontró el contenedor '$DB_CONTAINER' ni un cliente mariadb/mysql local."
    exit 1
fi

echo "✔ Base de datos '$DB_NAME' restaurada exitosamente desde $BACKUP_FILE."
