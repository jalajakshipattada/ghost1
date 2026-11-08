# Build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
ENV MAVEN_OPTS="-Xmx400m -XX:+TieredCompilation -XX:TieredStopAtLevel=1"
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -B

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-Xmx384m"
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
