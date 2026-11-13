FROM eclipse-temurin:25-jre

WORKDIR /app

COPY target/EduTrack-0.0.1-SNAPSHOT.jar app.jar
COPY target/classes/applicationinsights.json /app/applicationinsights.json

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
