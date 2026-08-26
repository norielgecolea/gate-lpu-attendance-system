FROM tomcat:11.0.22-jdk21

# Guard-display video uploads are transcoded to H.264 MP4 when FFmpeg is available.
# postgresql-client-17 must match the Postgres 17 server (older pg_dump refuses to dump it).
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gnupg ffmpeg \
    && curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc | gpg --dearmor -o /usr/share/keyrings/postgresql.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/postgresql.gpg] http://apt.postgresql.org/pub/repos/apt $(. /etc/os-release && echo "$VERSION_CODENAME")-pgdg main" > /etc/apt/sources.list.d/pgdg.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends postgresql-client-17 \
    && rm -rf /var/lib/apt/lists/*
