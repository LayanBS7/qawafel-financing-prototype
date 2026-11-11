# Build context is the repository root (the sample Excel file is bundled into the jar).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/pom.xml backend/pom.xml
RUN mvn -q -B -f backend/pom.xml dependency:go-offline
COPY backend backend
COPY sample-data sample-data
RUN mvn -q -B -f backend/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/backend/target/financing-prototype-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
