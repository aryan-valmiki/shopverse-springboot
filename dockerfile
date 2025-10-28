# ========================
# STEP 1: Build the JAR
# ========================
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and configuration
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (for faster rebuilds)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy project source
COPY src src

# Build application (skip tests for speed)
RUN ./mvnw clean package -DskipTests

# ========================
# STEP 2: Run the app
# ========================
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copy the jar built in the previous step
COPY --from=builder /app/target/*.jar app.jar

# Expose the port Spring Boot uses
EXPOSE 8080

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]
