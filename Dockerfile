FROM gcr.io/distroless/java21-debian12

COPY build/libs/*.jar /app/

WORKDIR /app

# Use ENTRYPOINT to allow passing arguments to the JAR
ENTRYPOINT ["java", "-jar", "app.jar"]

# Default CMD is empty, but can be overridden with arguments
CMD []
