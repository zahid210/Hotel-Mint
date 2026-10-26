# ════════════════════════════════════════════════════════════════
# Hotel Bahía — Frontend SPA (React + Nginx)
# ════════════════════════════════════════════════════════════════

# ── Etapa 1: Build (Node 22) ───────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . ./
ARG VITE_API_URL=/api/v1
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# ── Etapa 2: Runtime Nginx ─────────────────────────────────────
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# Limpiar archivos estáticos por defecto de nginx
RUN rm -rf ./*

COPY --from=build /app/dist ./
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
