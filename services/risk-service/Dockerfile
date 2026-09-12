# 作用：使用 Maven Wrapper 构建 risk 可执行 JAR，并生成最小运行镜像。
FROM maven:3.9-eclipse-temurin-21@sha256:a972570be789ee5c9fa23446a8914ac7327560b5c022f662cfa9452aef829f18 AS build

WORKDIR /workspace
COPY . .
RUN ./mvnw -pl services/risk-service -am package -DskipTests

FROM eclipse-temurin:21-jre-noble@sha256:d35199d74a3b2dff1bfb435d9adde4ef974e74a7d2a4fcb3079063c55926edb5

WORKDIR /app
COPY --from=build /workspace/services/risk-service/target/risk-service-0.1.0-SNAPSHOT.jar app.jar
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 flowmesh
USER 10001

EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
