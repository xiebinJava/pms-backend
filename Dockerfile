FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app
RUN useradd --system --uid 10001 --create-home pms
COPY --from=build /workspace/target/pms-backend-0.1.0.jar /app/pms-backend.jar
USER 10001

ENV SPRING_PROFILES_ACTIVE=oceanbase
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/pms-backend.jar"]
