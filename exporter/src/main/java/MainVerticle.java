import de.fokus.fraunhofer.hopsworks.adapter.HopsworksAdapter;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.net.URI;

import java.io.IOException;

import java.util.Iterator;

import model.Constants;
import model.UploadRequest;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private JsonObject config;

    @Override
    public void start(Future<Void> future) {

        LOG.info("Launching exporter...");

        Future<Void> steps = loadConfig()
            .compose(handler -> bootstrapVerticles())
            .compose(handler -> startServer());

        steps.setHandler(handler -> {
            if (handler.succeeded()) {
                LOG.info("Exporter successfully launched");
            } else {
                LOG.error("Failed to launch exporter: " + handler.cause());
            }
        });
    }

    private Future<JsonObject> loadConfig() {
        LOG.info("Loading config...");

        Future<JsonObject> future = Future.future();

        ConfigRetriever.create(vertx).getConfig(handler -> {
            if (handler.succeeded()) {
                LOG.info("Config successfully loaded");
                config = handler.result();
                future.complete(handler.result());
            } else {
                LOG.error("Failed to load config, cause {}", handler.cause());
                future.fail("Failed to load config: " + handler.cause());
            }
        });

        return future;
    }

    private Future<Void> bootstrapVerticles() {
        LOG.info("Deploying aggregation verticle...");

        Future<Void> future = Future.future();

        DeploymentOptions options = new DeploymentOptions()
                .setConfig(config)
                .setWorker(true);

        vertx.deployVerticle(UploadVerticle.class.getName(), options, handler -> {
            if (handler.succeeded()) {
                LOG.info("Upload verticle successfully deployed");
            } else {
                future.fail("Failed to deploy upload verticle: " + handler.cause());
            }
        });

        vertx.deployVerticle(UploadVerticle.class.getName(), options, handler -> {
            if (handler.succeeded()) {
                LOG.info("Upload verticle successfully deployed");
            } else {
                future.fail("Failed to deploy upload verticle: " + handler.cause());
            }
        });

        vertx.deployVerticle(UploadVerticle.class.getName(), options, handler -> {
            if (handler.succeeded()) {
                LOG.info("Upload verticle successfully deployed");
            } else {
                future.fail("Failed to deploy upload verticle: " + handler.cause());
            }
        });

        future.complete();

        return future;
    }

    private Future<Void> startServer() {
        LOG.info("Starting server...");

        Future<Void> future = Future.future();
        Integer port = config.getInteger("http.port");

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.post("/export").handler(this::handleExport);

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

      JsonObject message = context.getBodyAsJson();
      String pipeId = message.getString("pipeId");
      Integer hopsProjectId = message.getInteger("hopsProjectId");
      String filePath = message.getString("payload");
      String metadata = message.getString("metadata");

      String hopsDataset = message.getString("hopsDataset");

      LOG.info("Received request with pipeId [{}]", pipeId);

      String url = config.getJsonObject("aegis").getString("url");  // test server
      String url_metadata = config.getJsonObject("metadata-store").getString("url"); // aegis metadata store

      String email;
      String password;

      if(message.getString("user") != null && message.getString("password") != null) {
          email = message.getString("user");
          password = message.getString("password");
      } else {
          email = config.getJsonObject("aegis").getString("user");
          password = config.getJsonObject("aegis").getString("password");
      }

      UploadRequest request
              = new UploadRequest(pipeId, hopsProjectId, hopsDataset, filePath, metadata, url, url_metadata, email, password);
      vertx.eventBus().send(Constants.MSG_UPLOAD, Json.encode(request));

      context.response()
              .setStatusCode(202) // accepted
              .putHeader("Content-Type", "application/json")
              .end();
    }
}
