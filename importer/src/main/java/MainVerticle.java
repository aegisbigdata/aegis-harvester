import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static model.Constants.*;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private JsonObject config;
    private String jobFile;

    @Override
    public void start() {
        LOG.info("Launching importer...");

        Future<Void> steps = loadConfig()
                .compose(handler -> bootstrapVerticle())
                .compose(handler -> startServer());

        steps.setHandler(handler -> {
            if (handler.succeeded()) {
                LOG.info("Importer successfully launched");
            } else {
                LOG.error("Failed to launch importer: " + handler.cause());
            }
        });
    }

    @Override
    public void stop() {
        LOG.info("Shutting down...");

        String jobFile = config.getString("tmpDir") + "/" + JOB_FILE_NAME;
        vertx.fileSystem().delete(jobFile, handler -> {
           if (handler.failed())
               LOG.warn("Failed to clean up file [{}]", jobFile);
        });
    }

    private Future<Void> loadConfig() {
        Future<Void> future = Future.future();

        ConfigRetriever.create(vertx).getConfig(handler -> {
            if (handler.succeeded()) {
                config = handler.result();
                jobFile = config.getString("tmpDir") + "/" + JOB_FILE_NAME;
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

        vertx.deployVerticle(ImporterVerticle.class.getName(), options, handler -> {
            if (handler.succeeded()) {
                future.complete();
            } else {
                future.fail("Failed to deploy importer verticle: " + handler.cause());
            }
        });

        return future;
    }

    private Future<Void> startServer() {
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
        getRunningJobsFromFile(jobFile).setHandler(handler -> {
            JsonObject response = new JsonObject();

            if (handler.succeeded()) {
                context.response().setStatusCode(200);
                response.put("running", handler.result());
            } else {
                context.response().setStatusCode(500);
            }

            context.response().setStatusCode(200);
            context.response().putHeader("Content-Type", "application/json");
            context.response().end(response.encode());
        });
    }

    private void weatherHandler(RoutingContext context) {
        List<String> runningJobs = new ArrayList<>();
        getRunningJobsFromFile(jobFile).setHandler(handler -> {
            if (handler.succeeded()) {
                runningJobs.addAll(handler.result());
            } else {
                LOG.warn("Could not retrieve running jobs, collisions may occur");
            }

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
                } else if (request.getDurationInHours() != 0 && request.getDurationInHours() * 60 < request.getFrequencyInMinutes()) {
                    response.put("message", "Frequency lower than total duration");
                    context.response().setStatusCode(400);
                } else {
                    vertx.eventBus().send(Constants.MSG_IMPORT, context.getBodyAsString());

                    writeJobToFile(jobFile, request.getPipeId());
                    context.response().setStatusCode(202);
                }
            } catch (DecodeException e) {
                response.put("message", "Invalid JSON provided");
                context.response().setStatusCode(400);
            }

            context.response().end(response.encode());
        });
    }

    private Future<List<String>> getRunningJobsFromFile(String filePath) {
        Future<List<String>> runningJobs = Future.future();

        vertx.<List<String>>executeBlocking(handler -> {
            List<String> jobs = new ArrayList<>();

            try (Stream<String> stream = Files.lines(Paths.get(filePath))) {
                stream.forEach(jobs::add);
                LOG.debug("Running jobs: {}", jobs);
            } catch (IOException e) {
                LOG.error("Failed to read job file: {}", e.getMessage());
            }

            handler.complete(jobs);
        }, result -> {
            if (result.succeeded()) {
                runningJobs.complete(result.result());
            } else {
                runningJobs.fail("Failed to read job file " + result.cause());
            }
        });

        return runningJobs;
    }

    private void writeJobToFile(String filePath, String jobId) {
        vertx.fileSystem().open(filePath, new OpenOptions().setAppend(true), ar -> {
            if (ar.succeeded()) {
                AsyncFile ws = ar.result();
                Buffer chunk = Buffer.buffer(jobId + System.lineSeparator());
                ws.write(chunk);
            } else {
                LOG.error("Could not open file [{}]", filePath);
            }
        });
    }
}
