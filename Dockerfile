# Root Dockerfile for backend deployment with frontend
# This is used by the GitHub Actions workflows for building the Docker image

# Stage 1: Build frontend
FROM node:18-alpine AS frontend-build

WORKDIR /app

# Copy frontend files
COPY frontend/package*.json ./
COPY frontend/tsconfig*.json ./
COPY frontend/vite.config.ts ./
COPY frontend/index.html ./
COPY frontend/src ./src

# Install dependencies and build
RUN npm install && npm run build

# Stage 2: Build backend
FROM maven:3.9-eclipse-temurin-21 AS backend-build

WORKDIR /app

# Copy backend pom.xml and download dependencies
COPY backend/pom.xml ./
RUN mvn dependency:go-offline -B

# Copy backend source code
COPY backend/src ./src

# Shared assets at /assets. The SPA shell is served for /mentat and /foobar.
RUN rm -rf ./src/main/resources/static
COPY --from=frontend-build /app/dist/index.html ./src/main/resources/static/spa/index.html
COPY --from=frontend-build /app/dist/assets ./src/main/resources/static/assets

# Build backend (which will include frontend static files)
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the built JAR from build stage
COPY --from=backend-build /app/target/*.jar app.jar

# Expose port (application runs on 8080 per application.properties)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
