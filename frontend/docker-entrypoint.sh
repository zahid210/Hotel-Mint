#!/bin/sh
set -e

CERTS_DIR="/etc/nginx/certs"
mkdir -p "$CERTS_DIR"

if [ ! -f "$CERTS_DIR/fullchain.pem" ] || [ ! -f "$CERTS_DIR/privkey.pem" ]; then
    echo "• Certificados TLS no encontrados en $CERTS_DIR."
    echo "• Generando certificado autofirmado para inicialización en desarrollo..."
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
        -keyout "$CERTS_DIR/privkey.pem" \
        -out "$CERTS_DIR/fullchain.pem" \
        -subj "/C=PE/ST=Lima/L=Lima/O=HotelBahia/CN=localhost" 2>/dev/null
    chmod 600 "$CERTS_DIR/privkey.pem"
    chmod 644 "$CERTS_DIR/fullchain.pem"
    echo "✔ Certificados SSL/TLS generados exitosamente."
fi

exec "$@"
