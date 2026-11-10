FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /build

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B -DskipTests package \
 && cp target/*.jar application.jar

FROM eclipse-temurin:25-jre-jammy AS extract
WORKDIR /extract
COPY --from=build /build/application.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre-jammy
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app

COPY --from=extract /extract/extracted/dependencies/ ./
COPY --from=extract /extract/extracted/spring-boot-loader/ ./
COPY --from=extract /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract /extract/extracted/application/ ./

USER app
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar application.jar"]