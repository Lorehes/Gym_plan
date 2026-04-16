# user-service — Eclipse Temurin 17 JRE
# 빌드 선행: ./gradlew :services:user-service:bootJar
FROM eclipse-temurin:17-jre-jammy
RUN groupadd -r gymplan && useradd -r -g gymplan gymplan
USER gymplan
WORKDIR /app
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
