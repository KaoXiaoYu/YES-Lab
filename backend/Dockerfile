FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY backend/pom.xml ./pom.xml
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl \
    && addgroup -S -g 10001 yeslab \
    && adduser -S -D -H -u 10001 -G yeslab yeslab
WORKDIR /app
COPY --from=build /workspace/target/yes-lab-api-*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
