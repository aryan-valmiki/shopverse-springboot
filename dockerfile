# Use official lightweight JDK image
FROM eclipse-temurin:17-jdk-alpine

# Set working directory inside container
WORKDIR /app

# Copy Maven wrapper and project descriptor
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Give permission to mvnw
RUN chmod +x mvnw

# Download dependencies for caching
RUN ./mvnw dependency:go-offline -B

# Copy project source
COPY src src

# Build the application (skip tests)
RUN ./mvnw clean package -DskipTests

# Expose Spring Boot port
EXPOSE 8080

# Find the built jar file dynamically and run it
CMD ["sh", "-c", "java -jar $(find target -name '*.jar' | head -n 1)"]
