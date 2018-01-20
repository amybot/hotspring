FROM openjdk:8-jre-slim

WORKDIR /app

ADD target/hotspring*.jar hotspring.jar

ENTRYPOINT ["/usr/bin/java", "-Xmx2048M", "-jar", "/app/hotspring.jar"]