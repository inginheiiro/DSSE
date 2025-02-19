FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the JAR file
COPY target/challenge-0.0.1-SNAPSHOT.jar app.jar

ENV JAVA_OPTS=""
ENV SPRING_PROFILES_ACTIVE="production"

# Healthcheck
HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:8080/health || exit 1

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]