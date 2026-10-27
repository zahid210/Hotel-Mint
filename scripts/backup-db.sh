#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# Hotel Bahía — Respaldo seguro de base de datos MariaDB
# Uso:
#   ./scripts/backup-db.sh
# ─────────────────────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="$ROOT/backups"
ENV_FILE="$ROOT/.env"

if [ -f "$ENV_FILE" ]; then
    # shellcheck disable=SC1090
    set -a; source "$ENV_FILE"; set +a
fi

DB_NAME="${DB_NAME:-hotel_bahia}"
DB_ROOT_PASSWORD="${DB_ROOT_PASSWORD:-}"
DB_CONTAINER="hotel-bahia-db"

mkdir -p "$BACKUP_DIR"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="$BACKUP_DIR/backup_${DB_NAME}_${TIMESTAMP}.sql.gz"

echo "• Iniciando respaldo de '$DB_NAME'..."

# Verificar si el contenedor Docker está corriendo
if docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
    echo "• Ejecutando dump desde contenedor Docker '$DB_CONTAINER'..."
    docker exec -e MARIADB_PWD="${DB_ROOT_PASSWORD}" "$DB_CONTAINER" \
        mariadb-dump -u root --single-transaction --quick --routines --triggers "$DB_NAME" | gzip > "$BACKUP_FILE"
elif command -v mariadb-dump >/dev/null 2>&1 || command -v mysqldump >/dev/null 2>&1; then
    DUMP_BIN="$(command -v mariadb-dump || command -v mysqldump)"
    echo "• Ejecutando dump local con $DUMP_BIN..."
    MARIADB_PWD="${DB_ROOT_PASSWORD:-${DB_PASSWORD:-}}" "$DUMP_BIN" \
        -u "${DB_USER:-root}" -h "${DB_HOST:-127.0.0.1}" -P "${DB_PORT:-3306}" \
        --single-transaction --quick --routines --triggers "$DB_NAME" | gzip > "$BACKUP_FILE"
else
    echo "❌ Error: No se encontró el contenedor '$DB_CONTAINER' ni un cliente mariadb-dump/mysqldump local."
    exit 1
fi

if [ -s "$BACKUP_FILE" ]; then
    SIZE="$(du -h "$BACKUP_FILE" | cut -f1)"
    echo "✔ Respaldo completado exitosamente: $BACKUP_FILE ($SIZE)"
else
    echo "❌ Error: El archivo de respaldo está vacío."
    rm -f "$BACKUP_FILE"
    exit 1
fi
