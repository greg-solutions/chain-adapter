# Stage 1 (to create a "build" image, ~140MB)
FROM openjdk:8-jdk-alpine3.7 AS builder
RUN java -version

ARG JAR_FILE=/build/libs/chain-adapter-all.jar
ARG APP_NAME=chain-adapter
ARG BUILD_ARGS=''
COPY . /dist
WORKDIR /dist

RUN ./gradlew build $BUILD_ARGS

# Stage 2 (to create a downsized "container executable", ~87MB)
FROM openjdk:8u181-jre-alpine

COPY --from=builder $JAR_FILE ./opt/$APP_NAME.jar
COPY --from=builder /dist/deploy /opt/$APP_NAME/deploy

WORKDIR /opt/chain-adapter
## Wait for script (see https://github.com/ufoscout/docker-compose-wait/)

ADD https://github.com/ufoscout/docker-compose-wait/releases/download/2.5.0/wait /opt/$APP_NAME/wait
RUN chmod +x /opt/$APP_NAME/wait
CMD /opt/$APP_NAME/wait && java -jar /opt/$APP_NAME/$APP_NAME.jar