# The optional Sekund server as a container (see docs/server.md).
#
# Build the app first, then the image (from the repository root):
#   ./gradlew :server:installDist
#   docker build -t sekund-server server
#
# Run it with a folder for the database and server.json:
#   docker run -d -p 8080:8080 -v sekund-data:/data -e PUBLIC_URL=https://sekund.example.org sekund-server
#
# The Gradle build isn't done inside Docker because the repository's build also configures the Android
# modules, which would need the Android SDK in the image.
FROM eclipse-temurin:21-jre

RUN useradd --system --home-dir /data --shell /usr/sbin/nologin sekund \
    && mkdir -p /data \
    && chown sekund /data

COPY build/install/server/ /opt/sekund-server/

# Settings: /data/server.json if it exists; these environment variables win over it.
ENV SEKUND_SERVER_CONFIG=/data/server.json \
    DATABASE_URL=jdbc:sqlite:/data/sekund-server.db \
    PORT=8080

VOLUME /data
EXPOSE 8080
USER sekund
WORKDIR /data
ENTRYPOINT ["/opt/sekund-server/bin/server"]
