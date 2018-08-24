import de.fokus.fraunhofer.hopsworks.adapter.HopsworksAdapter;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.http.entity.StringEntity;

import java.net.URI;

import java.io.IOException;

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
        LOG.info("Loading config...");

        Future<JsonObject> future = Future.future();

        ConfigRetriever.create(vertx).getConfig(handler -> {
            if (handler.succeeded()) {
                LOG.info("Config successfully loaded");
                future.complete(handler.result());
            } else {
                LOG.error("Failed to load config, cause {}", handler.cause());
                future.fail("Failed to load config: " + handler.cause());
            }
        });

        return future;
    }

    private Future<Void> startServer(JsonObject config) {
        LOG.info("Starting server...");

        Future<Void> future = Future.future();
        Integer port = config.getInteger("http.port");

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.post("/export").handler(context ->
                handleExport(context, config)
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

    private JsonObject uploadMetadata(String url, JsonObject metadata) {
        JsonObject jsonResponse = new JsonObject();

        HttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);

        try {
            StringEntity entity = new StringEntity(metadata.toString());

            httpPost.setEntity(entity);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");

            HttpResponse response = httpClient.execute(httpPost);
            JsonObject body = new JsonObject(EntityUtils.toString(response.getEntity()));
            int status = response.getStatusLine().getStatusCode();

            jsonResponse.put("status", status);
            jsonResponse.put("body", body);
        } catch (IOException e) {
            jsonResponse.put("status", -1);
            jsonResponse.put("body", "{\"status\":\"error\",\"message\":\"e.getMessage()\"}");
        }

        return jsonResponse;
    }

    private void handleExport(RoutingContext context, JsonObject config) {
        LOG.debug("Received request with body {}", context.getBodyAsString());

        JsonObject message = context.getBodyAsJson();
        String pipeId = message.getString("pipeId");
        Integer hopsProjectId = message.getInteger("hopsProjectId");
        String hopsDataset = "upload/" + message.getString("hopsDataset");
        String filePath = message.getString("payload");
        String metadata = message.getString("metadata");

        LOG.info("Received request with pipeId [{}]", pipeId);

        context.response()
                .setStatusCode(202) // accepted
                .putHeader("Content-Type", "application/json")
                .end();

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

        // upload file
        vertx.executeBlocking(future -> {
            if (Files.exists(Paths.get(filePath))) {

                HopsworksAdapter hopsworksAdapter = new HopsworksAdapter(email, password, url);
                hopsworksAdapter.actionUploadFile(hopsProjectId.toString(), hopsDataset, filePath);

                LOG.info("Uploaded file [{}] to hopsworks with pipeId [{}]", filePath, pipeId);

                vertx.fileSystem().delete(filePath, deleteHandler -> {
                            if (deleteHandler.failed())
                                LOG.warn("Failed to clean up file [{}] : ", filePath, deleteHandler.cause());
                        });

                future.complete();
            } else {
                future.fail("File not found: " + filePath);
            }
        }, result -> {
            if (result.failed()) {
                LOG.info("Failed to export file [{}] to HopsWorks with pipeId [{}] : ", filePath, pipeId, result.cause());
            }
        });

        JsonObject metadataJson = new JsonObject(metadata);

        // upload metadata catalog + dataset + distribution
        // FIXME : error with "umlauts" when send to meta data store
        vertx.executeBlocking(future -> {
            JsonObject response = uploadMetadata(url_metadata + "/catalogs", metadataJson.getJsonObject("catalog"));

            int status = response.getInteger("status");
            JsonObject body = response.getJsonObject("body");

            //LOG.debug("STATUS : [{}]", status);
            //LOG.debug("BODY : [{}]", body);

            if (status == 400 && body.getString("message") != null && body.getString("message").equals("Entity does already exist")) {
                // TODO : update, do PUT
            } else if (status < 200 || status >= 400  ) {
                future.fail("[Catalog] Metadata Store API returned status code " + status + " and message \"" + body.getString("message") + "\"");
            }

            response = uploadMetadata(url_metadata + "/datasets", metadataJson.getJsonObject("dataset"));

            status = response.getInteger("status");
            body = response.getJsonObject("body");

            //LOG.debug("STATUS : [{}]", status);
            //LOG.debug("BODY : [{}]", body);

            if (status == 400 && body.getString("message") != null && body.getString("message").equals("Entity does already exist")) {
                // TODO : update, do PUT
            } else if (status < 200 || status >= 400) {
                future.fail("[Dataset] Metadata Store API returned status code " + status + " and message \"" + body.getString("message") + "\"");
            }

            response = uploadMetadata(url_metadata + "/datasets/" + metadataJson.getJsonObject("dataset").getInteger("id").toString() + "/distributions", metadataJson.getJsonObject("distribution"));

            status = response.getInteger("status");
            body = response.getJsonObject("body");

            //LOG.debug("STATUS : [{}]", status);
            //LOG.debug("BODY : [{}]", body);

            if (status == 400 && body.getString("message") != null && body.getString("message").equals("Entity does already exist")) {
                // TODO : update, do PUT
            } else if (status < 200 || status >= 400) {
                future.fail("[Distribution] Metadata Store API returned status code " + status + " and message \"" + body.getString("message") + "\"");
            } else {
                future.complete();
            }
        }, result -> {
            if(result.succeeded()) {
                LOG.info("Uploading Metadata to Metadata Store succeeded");
            } else {
                LOG.error("Uploading Metadata to Metadata Store failed: " + result.cause());
            }
        });
    }
}
