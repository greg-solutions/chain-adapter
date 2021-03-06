FROM openjdk:8u181-jre-alpine

ARG JAR_FILE=/build/libs/chain-adapter-all.jar
WORKDIR /opt/chain-adapter
COPY ${JAR_FILE} /opt/chain-adapter/chain-adapter.jar

## Wait for script (see https://github.com/ufoscout/docker-compose-wait/)

ADD https://github.com/ufoscout/docker-compose-wait/releases/download/2.5.0/wait /opt/chain-adapter/wait
RUN chmod +x /opt/chain-adapter/wait
CMD /opt/chain-adapter/wait && java -jar /opt/chain-adapter/chain-adapter.jar
