# Stage 1: Build the application using Maven and Java 25
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app
# Copy the pom.xml and source code
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
# We can just copy the source to keep it simple
COPY src ./src

# Create a minimal mvnw if not present, but let's just use maven image if simpler. 
# We'll rely on a locally built jar for now or build inside docker.
# Using maven directly for build stage is easier but Alpine JDK has no maven by default.
# Let's install maven:
RUN apk add --no-cache maven

RUN mvn clean package -DskipTests

# Stage 2: Create the minimal runtime image
# To keep strictly to the distroless/minimal requirement:
FROM eclipse-temurin:25-jre-alpine

# Install fonts for OpenHtmlToPdf rendering
RUN apk add --no-cache ttf-dejavu fontconfig ttf-liberation

WORKDIR /app

# Add a non-root user
RUN addgroup -g 65532 -S spring && adduser -u 65532 -S spring -G spring
RUN mkdir -p /app/data/pdfs && chown -R spring:spring /app/data
USER 65532:65532

# Copy the built jar
COPY --from=builder /app/target/hireops-engine-0.0.1-SNAPSHOT.jar app.jar

# Expose port and start
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
