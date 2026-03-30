FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY checkstyle.xml .
COPY src ./src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/target/booking-app-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
