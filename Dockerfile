FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21

COPY build/libs/*.jar /app/

WORKDIR /app

# Use ENTRYPOINT to allow passing arguments to the JAR
ENTRYPOINT ["java", "-jar", "app.jar"]

# Default CMD is empty, but can be overridden with arguments
CMD []
