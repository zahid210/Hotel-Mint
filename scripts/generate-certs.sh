#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# Hotel Bahía — Generación de certificados SSL autofirmados para dev
# ─────────────────────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CERTS_DIR="$ROOT/certs"

mkdir -p "$CERTS_DIR"

echo "• Generando certificado SSL autofirmado en $CERTS_DIR..."
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout "$CERTS_DIR/privkey.pem" \
    -out "$CERTS_DIR/fullchain.pem" \
    -subj "/C=PE/ST=Lima/L=Lima/O=HotelBahia/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

chmod 600 "$CERTS_DIR/privkey.pem"
chmod 644 "$CERTS_DIR/fullchain.pem"

echo "✔ Certificados listos:"
echo "   - Clave privada: $CERTS_DIR/privkey.pem"
echo "   - Certificado:   $CERTS_DIR/fullchain.pem"
