FROM eclipse-temurin:17-jre
# ffmpeg：将 B 站提取的 AAC 音频转码为 MP3
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
COPY behavior-consumer/target/*.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
