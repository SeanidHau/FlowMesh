# 作用：构建通知审计服务的可执行 JAR，并以非 root 用户运行。
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN ./mvnw -pl services/notification-audit-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/notification-audit-service/target/notification-audit-service-0.1.0-SNAPSHOT.jar app.jar
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 flowmesh
USER 10001
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
