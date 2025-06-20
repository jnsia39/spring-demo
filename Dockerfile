FROM openjdk:21-jdk

VOLUME /tmp

ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

EXPOSE 15460
ENTRYPOINT ["java", "-Xmx2g", "-jar", "/app.jar"]
