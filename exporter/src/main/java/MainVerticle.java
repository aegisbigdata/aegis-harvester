import de.fokus.fraunhofer.hopsworks.adapter.HopsworksAdapter;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Future<Void> future) {

        LOG.info("Launching exporter...");

        Future<Void> steps = loadConfig()
            .compose(this::startServer);

        steps.setHandler(handler -> {
            if (handler.succeeded()) {
                LOG.info("Exporter successfully launched");
            } else {
                LOG.error("Failed to launch exporter: " + handler.cause());
            }
        });
    }

    private Future<JsonObject> loadConfig() {
        Future<JsonObject> future = Future.future();

        ConfigRetriever.create(vertx).getConfig(handler -> {
            if(handler.succeeded()) {
                future.complete(handler.result());
            } else {
                future.fail("Failed to load config: " + handler.cause());
            }
        });

        return future;
    }

    private Future<Void> startServer(JsonObject config) {
        Future<Void> future = Future.future();
        Integer port = config.getInteger("http.port");

        Router router = Router.router(vertx);
        router.get("/export").handler(this::handleExport);

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

    private void handleExport(RoutingContext context) {
        // TODO load proper config
        String projectId = config().getJsonObject("aegis").getString("projectId");
        String folder = config().getJsonObject("aegis").getString("folder");
        String url = config().getJsonObject("aegis").getString("url");  //test server
        String email = config().getJsonObject("aegis").getString("user");
        String password = config().getJsonObject("aegis").getString("password");

        String filePath = context.request().getParam("payload");

        vertx.executeBlocking(future -> {
            HopsworksAdapter hopsworksAdapter = new HopsworksAdapter(email,password,url);
            hopsworksAdapter.actionUploadFile(projectId, folder, filePath);
            future.complete();
        }, result -> {
            if (result.failed()) {
                LOG.info("Failed to export file [{}] to HopsWorks: ", filePath, result.cause());
            }
        });
    }
}
