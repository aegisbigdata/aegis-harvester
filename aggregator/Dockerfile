FROM openjdk:jre-alpine

ENV VERTICLE_FILE aggregator-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles
ENV AGGREGATION_DIR /aggregated

EXPOSE 8125

RUN addgroup -S vertx && adduser -S -g vertx vertx

# Copy your fat jar to the container
COPY target/$VERTICLE_FILE $VERTICLE_HOME/
COPY conf/config.json $VERTICLE_HOME/

RUN chown -R vertx $VERTICLE_HOME
RUN chmod -R g+w $VERTICLE_HOME

RUN mkdir $AGGREGATION_DIR && chown -R vertx $AGGREGATION_DIR && chmod -R g+w $AGGREGATION_DIR
VOLUME $AGGREGATION_DIR

USER vertx

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["exec java -Xmx2048m -jar $VERTICLE_FILE -conf config.json"]
