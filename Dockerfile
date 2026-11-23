# Build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
ENV MAVEN_OPTS="-Xmx400m -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.http.retryHandler.requestSentEnabled=true"

# Install resilient Maven mirror & repository settings to prevent 429 Too Many Requests
RUN mkdir -p /root/.m2
COPY settings.xml /root/.m2/settings.xml
COPY settings.xml .
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -B -s settings.xml

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-Xmx384m"
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
