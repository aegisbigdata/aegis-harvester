import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
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

    private JsonObject config;

    @Override
    public void start() {
        LOG.info("Launching aggregator...");

        Future<Void> steps = loadConfig()
                .compose(handler -> bootstrapVerticle())
                .compose(handler -> startServer());

        steps.setHandler(handler -> {
            if (handler.succeeded()) {
                LOG.info("Aggregator successfully launched");
            } else {
                handler.cause().printStackTrace();
                LOG.error("Failed to launch aggregator: " + handler.cause());
            }
        });
    }

    private Future<Void> loadConfig() {
        Future<Void> future = Future.future();

        ConfigRetriever.create(vertx).getConfig(handler -> {
            if (handler.succeeded()) {
                config = handler.result();
                future.complete();
            } else {
                future.fail("Failed to load config: " + handler.cause());
            }
        });

        return future;
    }

    private Future<Void> bootstrapVerticle() {
        Future<Void> future = Future.future();

        DeploymentOptions options = new DeploymentOptions()
                .setConfig(config)
                .setWorker(true);

        vertx.deployVerticle(AggregationVerticle.class.getName(), options, handler -> {
            if (handler.succeeded()) {
                future.complete();
            } else {
                future.fail("Failed to deploy aggregation verticle: " + handler.cause());
            }
        });

        return future;
    }

    private Future<Void> startServer() {
        Future<Void> future = Future.future();
        Integer port = config.getInteger("http.port");

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.post("/aggregate").handler(this::handleAggregation);

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

    private void handleAggregation(RoutingContext context) {
        LOG.debug("Received request with body {}", context.getBodyAsString());

        JsonObject message = context.getBodyAsJson();
        String pipeId = message.getString("pipeId");
        Integer hopsProjectId = message.getInteger("hopsProjectId");
        String hopsDataset = message.getString("hopsDataset");
        String baseFileName = message.getString("baseFileName");
        String csvHeaders = message.getString("csvHeaders");
        String csvData = message.getString("payload");
        Boolean aggregate = message.getBoolean("aggregate");

        WriteRequest request
                = new WriteRequest(pipeId, hopsProjectId, hopsDataset, baseFileName, csvHeaders, csvData, aggregate);
        vertx.eventBus().send(Constants.MSG_AGGREGATE, Json.encode(request));

        context.response()
                .setStatusCode(202) // accepted
                .putHeader("Content-Type", "application/json")
                .end();
    }
}
