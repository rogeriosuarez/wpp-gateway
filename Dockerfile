# =========================
# Stage 1 — Build (Java 21)
# =========================
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests


# =========================
# Stage 2 — Runtime (Java 21)
# =========================
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=k3s

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
