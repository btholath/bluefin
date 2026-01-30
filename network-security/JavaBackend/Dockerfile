FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Copy the config file to the build folder (optional, but good practice)
COPY application.properties .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
# 1. Copy the JAR
COPY --from=build /app/target/backend-service-1.0-SNAPSHOT.jar app.jar
# 2. CRITICAL FIX: Copy the config file to the runtime folder
COPY application.properties .

CMD ["java", "-jar", "app.jar"]