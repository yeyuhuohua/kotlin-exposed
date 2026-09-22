# 后端镜像：构建阶段用 Gradle Wrapper 打出 fat jar，运行阶段只带 JRE。
# 构建上下文为 backend/ 目录：docker build -t hr-backend backend/

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
# 本机的 gradle.properties 被 Git 忽略且可能含本机 JDK 路径，镜像里一律用示例配置。
COPY gradle.properties.example gradle.properties
RUN ./gradlew --version --no-daemon
COPY src/ src/
RUN ./gradlew buildFatJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
