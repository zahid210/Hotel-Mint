# ════════════════════════════════════════════════════════════════
# Hotel Bahía — Backend API (Spring Boot)
# ════════════════════════════════════════════════════════════════

# ── Etapa 1: Build backend (Spring Boot) ───────────────────────
FROM eclipse-temurin:21.0.6_7-jdk-alpine AS builder
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

COPY src ./src
RUN ./mvnw package -q

# ── Etapa 2: Runtime ───────────────────────────────────────────
FROM eclipse-temurin:21.0.6_7-jre-alpine
WORKDIR /app

RUN addgroup -S hotel && adduser -S hotel -G hotel
USER hotel

COPY --chown=hotel:hotel --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Duser.timezone=America/Lima", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
