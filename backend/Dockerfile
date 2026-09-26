# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/pulsemind-engine-1.0.0.jar app.jar
# Sample service is mounted / copied for watching
COPY --from=build /app/pom.xml /tmp/pom.xml
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
