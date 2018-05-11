import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import model.Constants;
import model.WriteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start() {
        LOG.info("Launching aggregator...");

        Future<Void> steps = loadConfig()
                .compose(this::bootstrapVerticle)
                .compose(this::startServer);

        steps.setHandler(handler -> {
            if (handler.succeeded()) {
                LOG.info("Aggregator successfully launched");
            } else {
                handler.cause().printStackTrace();
                LOG.error("Failed to launch aggregator: " + handler.cause());
            }
        });
    }

    private Future<JsonObject> loadConfig() {
        Future<JsonObject> future = Future.future();

        ConfigRetriever.create(vertx).getConfig(handler -> {
            if (handler.succeeded()) {
                future.complete(handler.result());
            } else {
                future.fail("Failed to load config: " + handler.cause());
            }
        });

        return future;
    }

    private Future<JsonObject> bootstrapVerticle(JsonObject config) {

        Future<JsonObject> future = Future.future();

        DeploymentOptions options = new DeploymentOptions()
                .setConfig(config)
                .setWorker(true);

        vertx.deployVerticle(AggregationVerticle.class.getName(), options, handler -> {
            if (handler.succeeded()) {
                future.complete(config);
            } else {
                future.fail("Failed to deploy aggregation verticle: " + handler.cause());
            }
        });

        return future;
    }

    private Future<Void> startServer(JsonObject config) {
        Future<Void> future = Future.future();
        Integer port = config.getInteger("http.port");

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.post("/aggregate").handler(context ->
                handleAggregation(context, config)
        );

        vertx.createHttpServer().requestHandler(router::accept)
                .listen(port, handler -> {
                    if (handler.succeeded()) {
                        future.complete();
                        LOG.info("Listening on port " + port.toString());
                    } else {
                        future.fail("Failed to start server: " + handler.cause());
                    }
                });

        return future;
    }

    private void handleAggregation(RoutingContext context, JsonObject config) {
        LOG.debug("Received request with body {}", context.getBodyAsString());
        JsonObject message = context.getBodyAsJson();

        String pipeId = message.getString("pipeId");
        String filePath = config.getString("fileDir") + "/"
                + pipeId;

        WriteRequest request = new WriteRequest(pipeId, filePath, message.getString("payload"));
        vertx.eventBus().send(Constants.MSG_DATA, Json.encode(request));

        context.response()
                .setStatusCode(202) // accepted
                .putHeader("Content-Type", "application/json")
                .end();
    }
}
