import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import model.Constants;
import model.ImportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static model.Constants.TYPE_BBOX;
import static model.Constants.TYPE_LOCATION;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private final List<String> runningJobs = new ArrayList<>();

    @Override
    public void start() {
        LOG.info("Launching importer...");

        Future<Void> steps = loadConfig()
                .compose(this::bootstrapVerticle)
                .compose(this::startServer);

        steps.setHandler(handler -> {
            if (handler.succeeded()) {
                LOG.info("Importer successfully launched");
            } else {
                LOG.error("Failed to launch importer: " + handler.cause());
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

        vertx.deployVerticle(ImporterVerticle.class.getName(), options, handler -> {
            if (handler.succeeded()) {
                future.complete(config);
            } else {
                future.fail("Failed to deploy importer verticle: " + handler.cause());
            }
        });

        return future;
    }

    private Future<Void> startServer(JsonObject config) {
        Future<Void> future = Future.future();
        Integer port = config.getInteger("http.port");

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.get("/running").handler(this::runningJobshandler);
        router.post("/weather").handler(this::weatherHandler);

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

    private void runningJobshandler(RoutingContext context) {
        JsonObject response = new JsonObject();
        response.put("running", runningJobs);

        context.response().setStatusCode(200);
        context.response().putHeader("Content-Type", "application/json");
        context.response().end(response.encode());
    }

    private void weatherHandler(RoutingContext context) {

        JsonObject response = new JsonObject();
        context.response().putHeader("Content-Type", "application/json");

        try {
            ImportRequest request = Json.decodeValue(context.getBody().toString(), ImportRequest.class);

            if (request.getPipeId() == null || runningJobs.contains(request.getPipeId())) {
                response.put("message", "Please provide a unique pipe ID (pipeId)");
                context.response().setStatusCode(400);
            } else if (!TYPE_BBOX.equals(request.getType()) && !TYPE_LOCATION.equals(request.getType())) {
                response.put("message", "Unknown location type provided (" + request.getType() + ")");
                context.response().setStatusCode(400);
            } else if (request.getDurationInHours() * 60 < request.getFrequencyInMinutes()) {
                response.put("message", "Frequency lower than total duration");
                context.response().setStatusCode(400);
            } else {
                runningJobs.add(request.getPipeId());

                DeliveryOptions options = new DeliveryOptions();
                options.setSendTimeout(request.getFrequencyInMinutes() * 60000 + 3000); // allow a reply to be late 30 seconds

                vertx.eventBus().send(Constants.MSG_IMPORT, context.getBodyAsString(), reply -> {
                    if (reply.succeeded())
                        runningJobs.remove(reply.result().body().toString());
                });

                response.put("status", "ok");
                context.response().setStatusCode(200);
            }
        } catch (DecodeException e) {
            e.printStackTrace();
            response.put("message", "Invalid JSON provided");
            context.response().setStatusCode(400);
        }

        context.response().end(response.encode());
    }
}
