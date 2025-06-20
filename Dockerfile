FROM openjdk:21-jdk-slim

# ffmpeg 설치
RUN apt-get update && \
    apt-get install -y ffmpeg && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

VOLUME /tmp

# 빌드된 JAR 복사 (Gradle 기준)
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

EXPOSE 15460

ENTRYPOINT ["java", "-Xmx2g", "-jar", "/app.jar"]