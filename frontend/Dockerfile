# ════════════════════════════════════════════════════════════════
# Hotel Bahía — Frontend SPA (React + Nginx con TLS)
# ════════════════════════════════════════════════════════════════

# ── Etapa 1: Build (Node 22) ───────────────────────────────────
FROM node:22.14.0-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . ./
ARG VITE_API_URL=/api/v1
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# ── Etapa 2: Runtime Nginx ─────────────────────────────────────
FROM nginx:1.27.4-alpine
RUN apk add --no-cache openssl
WORKDIR /usr/share/nginx/html

# Limpiar archivos estáticos por defecto de nginx
RUN rm -rf ./*

COPY --from=build /app/dist ./
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 80 443

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
