FROM tomcat:11.0.22-jdk21

# Guard-display video uploads are transcoded to H.264 MP4 when FFmpeg is available.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
