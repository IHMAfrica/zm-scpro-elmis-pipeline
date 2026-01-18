FROM gradle:9.2.1-jdk25 AS build

WORKDIR /app

COPY build.gradle.kts ./
COPY settings.gradle.kts ./
COPY src ./src

RUN gradle bootJar --no-daemon || return 1

FROM eclipse-temurin:25-jre-alpine AS app

RUN apk --no-cache add bash curl

WORKDIR /app

COPY --from=build /app/build/libs/*.jar ./app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]