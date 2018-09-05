import de.fokus.fraunhofer.hopsworks.adapter.HopsworksAdapter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.eventbus.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.http.entity.StringEntity;


import java.io.IOException;

import java.util.Iterator;

import model.*;

public class UploadVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(UploadVerticle.class);

    @Override
    public void start(Future<Void> future) {
        LOG.info("Launching UploadVerticle");

        vertx.eventBus().consumer(Constants.MSG_UPLOAD, this::handleUpload);

        future.complete();
    }

    private void createDataset(String url, String email, String password, Integer hopsProjectId, JsonObject metadataJson, String dataset) {

        String hopsDataset = dataset;

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

            LOG.debug("Issue POST Request to [{}]", url + "/project/" + hopsProjectId + "/dataset/createTopLevelDataSet");
            httpPost = new HttpPost(url + "/project/" + hopsProjectId + "/dataset/createTopLevelDataSet");

            JsonObject content = new JsonObject();
            content.put("name", hopsDataset);
            content.put("description", "");
            content.put("searchable", true);
            content.put("generateReadme", false);

            entity = new StringEntity(content.toString());

            httpPost.setEntity(entity);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");

            response = httpClient.execute(httpPost);
            body = EntityUtils.toString(response.getEntity());
            status = response.getStatusLine().getStatusCode();

            LOG.debug("hopsDataset : {}", hopsDataset);

            LOG.debug("POST BODY {}", body);
            LOG.debug("POST STATUS {}", status);
        } catch (IOException e) {
            LOG.error("IOException [{}]", e.getMessage());
        }
    }

    private boolean uploadMetadata(String filePath, String url, Integer hopsProjectId, String hopsDataset, String email, String password, String url_metadata, JsonObject metadataJson) {

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
                return false;
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
            LOG.error("IOException [{}]", e.getMessage());
            return false;
        }

        //LOG.debug("hopsFileId [{}]", hopsFileId);
        //LOG.debug("hopsDatasetId [{}]", hopsDatasetId);

        // upload metadata
        if(hopsFileId > 0 && hopsDatasetId > 0) {
            try {
                HttpClient httpClient = HttpClients.createDefault();

                LOG.debug("Issue GET Request to [{}]", url_metadata + "/datasets/" + hopsDatasetId);
                HttpGet httpGet = new HttpGet(url_metadata + "/datasets/" + hopsDatasetId);

                HttpResponse response = httpClient.execute(httpGet);
                String body = EntityUtils.toString(response.getEntity());
                int status = response.getStatusLine().getStatusCode();

                HttpPost httpPost;
                StringEntity entity;

                if(status == 200) {
                    LOG.debug("Existing Metadata of Dataset [{}]", hopsDatasetId);

                    /*LOG.debug("Issue PUT Request to [{}]", url_metadata + "/datasets" + hopsDatasetId);
                    HttpPut httpPut = new HttpPut(url_metadata + "/datasets" + hopsDatasetId);

                    metadataJson.getJsonObject("dataset").put("id", hopsDatasetId);

                    entity = new StringEntity(metadataJson.getJsonObject("dataset").toString());

                    httpPut.setEntity(entity);
                    httpPut.setHeader("Accept", "application/json");
                    httpPut.setHeader("Content-type", "application/json");

                    response = httpClient.execute(httpPut);
                    body = EntityUtils.toString(response.getEntity());
                    status = response.getStatusLine().getStatusCode();

                    LOG.debug("POST BODY {}", body);
                    LOG.debug("POST STATUS {}", status);

                    if(status < 200 || status >= 400) {
                        return false;
                    }*/
                } else {
                    LOG.debug("Issue POST Request to [{}]", url_metadata + "/datasets");
                    httpPost = new HttpPost(url_metadata + "/datasets");

                    metadataJson.getJsonObject("dataset").put("id", hopsDatasetId);

                    entity = new StringEntity(metadataJson.getJsonObject("dataset").toString(), "UTF-8");

                    httpPost.setEntity(entity);
                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-type", "application/json");

                    response = httpClient.execute(httpPost);
                    body = EntityUtils.toString(response.getEntity());
                    status = response.getStatusLine().getStatusCode();

                    LOG.debug("POST BODY {}", body);
                    LOG.debug("POST STATUS {}", status);

                    if(status < 200 || status >= 400) {
                        return false;
                    }
                }

                LOG.debug("Issue POST Request to [{}]", url_metadata + "/datasets/" + hopsDatasetId + "/distributions");
                httpPost = new HttpPost(url_metadata + "/datasets/" + hopsDatasetId + "/distributions");

                metadataJson.getJsonObject("distribution").put("id", hopsFileId);

                entity = new StringEntity(metadataJson.getJsonObject("distribution").toString(), "UTF-8");

                httpPost.setEntity(entity);
                httpPost.setHeader("Accept", "application/json");
                httpPost.setHeader("Content-type", "application/json");

                response = httpClient.execute(httpPost);
                body = EntityUtils.toString(response.getEntity());
                status = response.getStatusLine().getStatusCode();

                LOG.debug("POST BODY {}", body);
                LOG.debug("POST STATUS {}", status);

                if(status < 200 || status >= 400) {
                    return false;
                }
            } catch (IOException e) {
                LOG.error("IOException [{}]", e.getMessage());
                return false;
            }
        }

        return true;
    }

    private void handleUpload(Message<String> message) {
        UploadRequest request = Json.decodeValue(message.body(), UploadRequest.class);

        LOG.debug("Received request with body {}", request.toString());

        //JsonObject message = context.getBodyAsJson();
        String pipeId = request.getPipeId();
        Integer hopsProjectId = request.getHopsProjectId();
        String filePath = request.getFilePath();
        String metadata = request.getMetadata();
        JsonObject metadataJson = new JsonObject(metadata);

        String hopsDataset = request.getHopsDataset();

        LOG.info("Received request with pipeId [{}]", pipeId);

        String url = request.getUrl();
        String url_metadata = request.getUrl_metadata();

        String email = request.getUser();
        String password = request.getPassword();

        // upload file
        vertx.executeBlocking(future -> {
            if (Files.exists(Paths.get(filePath))) {

                String dataset = "";

                if(hopsDataset == null) {
                    if(metadataJson.getJsonObject("dataset") != null) {
                        String title = metadataJson.getJsonObject("dataset").getString("title")
                            .replaceAll("%", "Percent")
                            .replaceAll(" ", "_")
                            .replaceAll("[^A-Za-z0-9_]", "")
                            .replaceAll("[_]+", "_");

                        if(title.substring(0,1).equals("_")) {
                            title.replaceFirst("_", "");
                        }

                        dataset = title.substring(0, Math.min(title.length(), 88)); // 88 chars is the limit for hopsworks
                        createDataset(url, email, password, hopsProjectId, metadataJson.getJsonObject("dataset"), dataset);
                    } else {
                        future.fail("No hopsDataset provided");
                    }
                } else {
                    dataset = hopsDataset;
                }

                HopsworksAdapter hopsworksAdapter = new HopsworksAdapter(email, password, url);
                hopsworksAdapter.actionUploadFile(hopsProjectId.toString(), "upload/" + dataset, filePath);

                LOG.info("Uploaded file [{}] to hopsworks with pipeId [{}]", filePath, pipeId);

                vertx.fileSystem().delete(filePath, deleteHandler -> {
                            if (deleteHandler.failed())
                                LOG.warn("Failed to clean up file [{}] : ", filePath, deleteHandler.cause());
                        });

                if(!metadataJson.getJsonObject("distribution").equals("{}")) {
                    boolean success = uploadMetadata(filePath, url, hopsProjectId, dataset, email, password, url_metadata, metadataJson);

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
