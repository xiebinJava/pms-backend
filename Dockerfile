FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:25-jre-jammy

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home pms \
    && mkdir -p /var/lib/pms/uploads \
    && chown -R 10001:10001 /var/lib/pms
COPY --from=build /workspace/target/pms-backend-0.1.0.jar /app/pms-backend.jar
COPY docker/healthcheck-backend.sh /usr/local/bin/healthcheck-backend.sh
RUN chmod 0755 /usr/local/bin/healthcheck-backend.sh
USER 10001

ENV SPRING_PROFILES_ACTIVE=oceanbase
ENV JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=/tmp"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 CMD ["/usr/local/bin/healthcheck-backend.sh"]
ENTRYPOINT ["java", "-jar", "/app/pms-backend.jar"]
