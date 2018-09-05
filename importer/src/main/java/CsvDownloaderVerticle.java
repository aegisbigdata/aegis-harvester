import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import model.*;

public class CsvDownloaderVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(CsvDownloaderVerticle.class);

    private HttpClient httpClient;

    @Override
    public void start(Future<Void> future) {
        vertx.eventBus().consumer(Constants.MSG_DOWNLOAD_CSV, this::startDownload);

        httpClient = HttpClients.createDefault();
        future.complete();
    }

    private void startDownload(Message<String> message) {
        CSVDownloadRequest csvDownloadRequest = Json.decodeValue(message.body(), CSVDownloadRequest.class);

        LOG.debug("CSV_DOWNLOADER pID := [{}]", csvDownloadRequest.getPipeId());
        LOG.debug("csv_downloader [{}]", csvDownloadRequest.toString());

        String baseFileName = csvDownloadRequest.getBaseFileName();
        String url = csvDownloadRequest.getUrl();

        getCsvFileFromUrl(url.replaceAll(" ", "%20"), baseFileName).setHandler(csvHandler -> {
            if (csvHandler.succeeded()) {
                JsonObject payload = new JsonObject().put("csv", csvHandler.result());

                DataSendRequest dataSendRequest =
                        new DataSendRequest(
                              csvDownloadRequest.getPipeId(),
                              csvDownloadRequest.getHopsProjectId(),
                              csvDownloadRequest.getHopsDataset(),
                              csvDownloadRequest.getDataType(),
                              csvDownloadRequest.getBaseFileName(),
                              payload.toString(),
                              csvDownloadRequest.getUser(),
                              csvDownloadRequest.getPassword(),
                              csvDownloadRequest.getMetadata()
                        );

                vertx.eventBus().send(Constants.MSG_SEND_DATA, Json.encode(dataSendRequest));
            } else {
                LOG.error("CSV handling failed: {} with pipeID [{}]", csvHandler.cause(), csvDownloadRequest.getPipeId());
            }
        });
    }

    private Future<String> getCsvFileFromUrl(String url, String fileName) {
        Future<String> resultFuture = Future.future();
        LOG.debug("Downloading file from [{}]", url);

        vertx.executeBlocking(httpFuture -> {
            File csvFile = new File(config().getString("tmpDir") + fileName);

            try {
                String url_ = url.replaceAll(" ", "%20");

                HttpClient httpClient = HttpClients.createDefault();

                LOG.debug("Issue HEAD Request to [{}]", url_);
                HttpHead httpHead = new HttpHead(url_);

                HttpResponse response = httpClient.execute(httpHead);

                LOG.debug("HEAD Content-Length : [{}]", response.getHeaders("Content-Length"));

                String content_length = "";
                if(response.getHeaders("Content-Length").length != 0) {
                    content_length = response.getHeaders("Content-Length")[0].getValue();
                } else if(response.getHeaders("Content-Range").length != 0) {
                    String content_range = response.getHeaders("Content-Range")[0].getValue();
                    content_length = content_range.substring(content_range.lastIndexOf("/")+1, content_range.length()-1);
                }

                int status = response.getStatusLine().getStatusCode();

                LOG.debug("HEAD Statuscode : [{}]", status);
                LOG.debug("HEAD Content-Length : [{}]", content_length);

                // TODO status 302 not found check location header (https?)
                if(status != 200 && status != 405) {
                    resultFuture.fail("Issue HEAD : Failed to download file: statuscode " + status);
                } else if(content_length.equals("") || content_length.equals("*")) {
                    resultFuture.fail("Issue HEAD : Failed to download file: No Content-Length");
                } else if(Integer.parseInt(content_length) > 100000) {
                    resultFuture.fail("Issue HEAD : Failed to download file: File to big");
                } else {
                    LOG.debug("Issue GET Request to [{}]", url_);
                    HttpGet httpGet = new HttpGet(url_);

                    response = httpClient.execute(httpGet);
                    String body = EntityUtils.toString(response.getEntity());
                    status = response.getStatusLine().getStatusCode();

                    if(status == 200) {
                        resultFuture.complete(body);
                    } else {
                        resultFuture.fail("Failed to download file: " + status);
                    }
                }
            } catch (IOException e) {
                resultFuture.fail("Failed to download file: " + e.getMessage());
            }
        }, result -> {
        });

        return resultFuture;
    }
}
