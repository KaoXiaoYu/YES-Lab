FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY backend/pom.xml ./pom.xml
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY config /config
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
COPY LICENSE /usr/share/licenses/openlims/LICENSE
RUN apk add --no-cache curl \
    && addgroup -S -g 10001 openlims \
    && adduser -S -D -H -u 10001 -G openlims openlims
WORKDIR /app
COPY --from=build /workspace/target/openlims-api-*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
