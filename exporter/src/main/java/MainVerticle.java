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

    private Future<Void> startServer(JsonObject config) {
        Future<Void> future = Future.future();
        Integer port = config.getInteger("port");

        Router router = Router.router(vertx);
        router.get("/export").handler(this::handleExport);

        vertx.createHttpServer().requestHandler(router::accept).listen(port);
        LOG.info("Listening on port " + port.toString());

        return future;
    }

    private void handleExport(RoutingContext context) {
        String projectId = config().getJsonObject("aegis").getString("projectId");
        String folder = config().getJsonObject("aegis").getString("folder");
        String url = config().getJsonObject("aegis").getString("url");  //test server
        String email = config().getJsonObject("aegis").getString("user");
        String password = config().getJsonObject("aegis").getString("password");

        String fileParam = config().getString("http.fileParam");

        HopsworksAdapter hopsworksAdapter = new HopsworksAdapter(email,password,url);
        hopsworksAdapter.actionUploadFile(projectId, folder, context.request().getParam(fileParam));
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
}
