# 作用：使用 Maven Wrapper 构建 workflow 可执行 JAR，并生成最小运行镜像。
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY . .
RUN ./mvnw -pl services/workflow-service -am package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/services/workflow-service/target/workflow-service-0.1.0-SNAPSHOT.jar app.jar
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 flowmesh
USER 10001

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
