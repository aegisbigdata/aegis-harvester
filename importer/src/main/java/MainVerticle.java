import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import model.Constants;
import model.ImportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

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

    private Future<Void> startServer(JsonObject config) {
        Future<Void> future = Future.future();
        Integer port = config.getInteger("port");

        Router router = Router.router(vertx);
        router.get("/").handler(this::indexHandler);
        router.get("/weather").handler(this::weatherHandler);

        vertx.createHttpServer().requestHandler(router::accept).listen(port);
        LOG.info("Listening on port " + port.toString());

        return future;
    }

    private Future<JsonObject> loadConfig() {
        Future<JsonObject> future = Future.future();

        ConfigRetriever.create(vertx).getConfig(ar -> {
            if(ar.failed()) {
                future.failed();
            } else {
                future.complete(ar.result());
            }
        });

        return future;
    }

    private Future<JsonObject> bootstrapVerticle(JsonObject config) {
        Future<JsonObject> future = Future.future();
        future.complete(config);
        DeploymentOptions options = new DeploymentOptions().setConfig(config);
        vertx.deployVerticle(ImporterVerticle.class.getName(), options);
        return future;
    }

    private void indexHandler(RoutingContext context) {
        JsonObject response = new JsonObject();
        response.put("status", "ok");
        context.response().setStatusCode(200);
        context.response().putHeader("Content-Type", "application/json");
        context.response().end(response.encode());
    }

    private void weatherHandler(RoutingContext context) {
        String bbox = context.request().getParam("bbox");
        String locationId = context.request().getParam("location");

        JsonObject response = new JsonObject();
        context.response().putHeader("Content-Type", "application/json");

        if (bbox == null && locationId == null) {
            response.put("message", "Please provide a bounding box (bbox) or location ID (location)");
            context.response().setStatusCode(400);
        } else if (bbox != null && locationId != null) {
            response.put("message", "Please provide only one parameter (bbox or location)");
            context.response().setStatusCode(400);
        } else if (bbox != null) {
            vertx.eventBus().send(Constants.MSG_IMPORT, Json.encode(new ImportRequest(Constants.TYPE_BBOX, bbox)));
            response.put("status", "ok");
            context.response().setStatusCode(200);
        } else {
            vertx.eventBus().send(Constants.MSG_IMPORT, Json.encode(new ImportRequest(Constants.TYPE_LOCATION, locationId)));
            response.put("status", "ok");
            context.response().setStatusCode(200);
        }

        context.response().end(response.encode());
    }
}
