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
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.http.entity.StringEntity;

import java.net.URI;

import java.io.IOException;

import java.util.Iterator;

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

    private boolean uploadMetadata(String filePath, String url, Integer hopsProjectId, String hopsDataset, String email, String password, String url_metadata, String metadata) {

        boolean success = true;

        Integer hopsFileId = 0;
        Integer hopsDatasetId = 0;

        // get hopsFileId and hopsDatasetId
        try {
            HttpClient httpClient = HttpClients.createDefault();

            LOG.debug("Issue POST Request to [{}]", url + "/auth/login");
            HttpPost httpPost = new HttpPost(url + "/auth/login");

            StringEntity entity = new StringEntity("email=" + email + "&password=" + password + "&otp=");

            httpPost.setEntity(entity);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");

            HttpResponse response = httpClient.execute(httpPost);
            String body = EntityUtils.toString(response.getEntity());
            int status = response.getStatusLine().getStatusCode();

            LOG.debug("Issue GET Request to [{}]", url + "/project/" + hopsProjectId.toString() + "/dataset/getContent/" + hopsDataset);
            HttpGet httpGet = new HttpGet(url + "/project/" + hopsProjectId + "/dataset/getContent/" + hopsDataset);

            response = httpClient.execute(httpGet);
            body = EntityUtils.toString(response.getEntity());
            status = response.getStatusLine().getStatusCode();

            if(status < 200 || status >= 400) {
                success = false;
            } else {
                for(Iterator<Object> it = new JsonArray(body).iterator(); it.hasNext();) {
                    JsonObject element = new JsonObject(it.next().toString());

                    if(filePath.contains(element.getString("name"))) {
                        hopsFileId = element.getInteger("id");
                        hopsDatasetId = element.getInteger("parentId");
                    }
                }
            }
        } catch (IOException e) {
            success = false;
            LOG.error("IOException [{}]", e.getMessage());
        }

        //LOG.debug("hopsFileId [{}]", hopsFileId);
        //LOG.debug("hopsDatasetId [{}]", hopsDatasetId);

        // upload metadata
        if(hopsFileId > 0 && hopsDatasetId > 0) {
            try {
                HttpClient httpClient = HttpClients.createDefault();

                LOG.debug("Issue POST Request to [{}]", url_metadata + "/datasets/" + hopsDatasetId + "/distributions");
                HttpPost httpPost = new HttpPost(url_metadata + "/datasets/" + hopsDatasetId + "/distributions");

                JsonObject metadataJson = new JsonObject(metadata);
                metadataJson.put("id", hopsFileId);

                StringEntity entity = new StringEntity(metadataJson.toString());

                httpPost.setEntity(entity);
                httpPost.setHeader("Accept", "application/json");
                httpPost.setHeader("Content-type", "application/json");

                HttpResponse response = httpClient.execute(httpPost);
                String body = EntityUtils.toString(response.getEntity());
                int status = response.getStatusLine().getStatusCode();

                //LOG.debug("POST BODY {}", body);
                //LOG.debug("POST STATUS {}", status);

                if(status < 200 || status >= 400) {
                    success = false;
                }
            } catch (IOException e) {
                success = false;
                LOG.error("IOException [{}]", e.getMessage());
            }
        }

        return success;
    }

    private void handleExport(RoutingContext context, JsonObject config) {
        LOG.debug("Received request with body {}", context.getBodyAsString());

        JsonObject message = context.getBodyAsJson();
        String pipeId = message.getString("pipeId");
        Integer hopsProjectId = message.getInteger("hopsProjectId");
        String hopsDataset = message.getString("hopsDataset");
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
                hopsworksAdapter.actionUploadFile(hopsProjectId.toString(), "upload/" + hopsDataset, filePath);

                LOG.info("Uploaded file [{}] to hopsworks with pipeId [{}]", filePath, pipeId);

                vertx.fileSystem().delete(filePath, deleteHandler -> {
                            if (deleteHandler.failed())
                                LOG.warn("Failed to clean up file [{}] : ", filePath, deleteHandler.cause());
                        });

                if(!metadata.equals("{}")) {
                    boolean success = uploadMetadata(filePath, url, hopsProjectId, hopsDataset, email, password, url_metadata, metadata);
                    if(success) {
                        LOG.debug("Successfully uploaded metadata");
                        future.complete();
                    } else {
                        future.fail("Error when uploading metadata");
                    }
                } else {
                    future.complete();
                }
            } else {
                future.fail("File not found: " + filePath);
            }
        }, result -> {
            if (result.failed()) {
                LOG.info("Failed to export file [{}] to HopsWorks with pipeId [{}] : ", filePath, pipeId, result.cause());
            }
        });

    }
}
