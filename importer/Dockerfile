FROM openjdk:jre-alpine

ENV VERTICLE_FILE importer-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles
ENV LOG_DIR /log

EXPOSE 8080

RUN addgroup -S vertx && adduser -S -g vertx vertx

# Copy your fat jar to the container
COPY target/$VERTICLE_FILE $VERTICLE_HOME/
COPY conf/config.prod.json $VERTICLE_HOME/config.json

RUN chown -R vertx $VERTICLE_HOME
RUN chmod -R g+w $VERTICLE_HOME

RUN mkdir $LOG_DIR && chown -R vertx $LOG_DIR && chmod -R g+w $LOG_DIR
VOLUME $LOG_DIR

USER vertx

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["exec java -Xmx2048m -jar $VERTICLE_FILE -conf config.json"]
