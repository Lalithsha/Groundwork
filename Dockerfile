FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp verify

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S groundwork && adduser -S groundwork -G groundwork
COPY --from=build --chown=groundwork:groundwork /app/target/*.jar app.jar
USER groundwork
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
