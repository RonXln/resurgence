FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build
COPY settings.xml /root/.m2/settings.xml
COPY advx/pom.xml advx/pom.xml
RUN mvn -q -f advx/pom.xml dependency:go-offline

COPY advx advx
RUN mvn -q -f advx/pom.xml test package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY --from=builder /build/advx/target/*.jar /app/resurgence.jar

RUN mkdir -p /tmp/resurrect
EXPOSE 8000

ENTRYPOINT ["java", "-jar", "/app/resurgence.jar"]
