# Use official lightweight JDK image
FROM eclipse-temurin:17-jdk-alpine

# Set working directory inside container
WORKDIR /app

# Copy Maven wrapper and project descriptor
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Give execution permission to mvnw
RUN chmod +x mvnw

# Download dependencies (improves caching)
RUN ./mvnw dependency:go-offline -B

# Copy project source code
COPY src src

# Build the application (skip tests to save time)
RUN ./mvnw clean package -DskipTests

# Expose port 8080 (Spring Boot default)
EXPOSE 8080

# Run the Spring Boot JAR file
ENTRYPOINT ["java", "-jar", "target/*.jar"]
