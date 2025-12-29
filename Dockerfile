# Estágio 1: Build usando uma imagem oficial do Maven + Java
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Estágio 2: Runtime usando apenas o JRE
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# Copia o arquivo JAR gerado no estágio de build
COPY --from=builder /app/target/*.jar app.jar

# Ativa o perfil 'k3s' para execução no cluster
ENV SPRING_PROFILES_ACTIVE=k3s

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]