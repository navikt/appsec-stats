# Use distroless Java image as base image
FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21

WORKDIR /app

# Copy the prebuilt distribution (run: ./gradlew clean installDist)
COPY build/install/app/ /app/

# Run without the shell script (since we dont have a shell)
ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.nav.security.MainKt"]
CMD []
