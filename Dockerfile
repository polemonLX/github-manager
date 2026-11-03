# Runtime image only: the CI release job builds and tests target/github-manager.jar first,
# so the image needs no Maven, no sources and no GitHub Packages token.
# Base image is multi-arch (linux/arm64 for the Raspberry Pi, linux/amd64 for local runs).
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY target/github-manager.jar app.jar

USER nobody

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
