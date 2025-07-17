FROM gradle:8.5-jdk17 as build

WORKDIR /app

COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon

COPY src ./src

RUN gradle build --no-daemon -x test

FROM eclipse-temurin:17-jre

WORKDIR /app

RUN mkdir -p /app/logs

COPY --from=build /app/build/libs/*.jar app.jar

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -Xms512m -Xmx1g"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]