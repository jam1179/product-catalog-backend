FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system catalog && useradd --system --gid catalog catalog
COPY --from=build /build/target/product-catalog-1.0.0.jar app.jar
USER catalog
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
