# ==========================================
# STAGE 1: Build (JDK 21 + Maven)
# ==========================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml ./
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# ==========================================
# STAGE 2: Runtime (JRE 21)
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/platform-0.0.1-SNAPSHOT.jar ./app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
