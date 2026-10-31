# 本番のイメージ（デプロイ設計書 2 章）。ECS Fargate（ARM）でも手元（x86）でも同じ Dockerfile で作れる
# API だけを入れる。画面は S3 + CloudFront で配信する
FROM eclipse-temurin:25.0.5_8-jdk-noble AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY config config
COPY src/main src/main
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25.0.5_8-jre-noble
RUN useradd --system --no-create-home app
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
USER app
# 画像は必ず S3 に保存する。S3_BUCKET がなければ起動しない（StorageConfig）
ENV STORAGE_TYPE=s3
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
