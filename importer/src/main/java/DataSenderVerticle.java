import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import model.Constants;
import model.DataSendRequest;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class DataSenderVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(DataSenderVerticle.class);

    private HttpClient httpClient;

    @Override
    public void start(Future<Void> future) {
        vertx.eventBus().consumer(Constants.MSG_SEND_DATA, this::sendWeatherData);

        httpClient = HttpClients.createDefault();
        future.complete();
    }


    private Future<Void> sendWeatherData(Message<String> message) {
        Future<Void> future = Future.future();

        try {
            DataSendRequest request = Json.decodeValue(message.body(), DataSendRequest.class);
            LOG.debug("Received {}", request.toString());

            String url = "http://"
                    + config().getString("target.host") + ":"
                    + config().getInteger("target.port")
                    + config().getString("target.endpoint");

            vertx.executeBlocking(handler -> {
                try {
                    HttpPost postRequest = new HttpPost(url);
                    postRequest.setHeader("Content-Type", "application/json");

                    HttpEntity entity = new ByteArrayEntity(message.body().getBytes("UTF-8"));
                    postRequest.setEntity(entity);

                    HttpResponse response = httpClient.execute(postRequest);
                    int status = response.getStatusLine().getStatusCode();

                    if (status < 200 || status > 400) {
                        LOG.warn("POST request returned status [{}]", status);
                        handler.fail("Bad status code: " + status);
                    } else {
                        handler.complete();
                    }
                } catch (IOException e) {
                    handler.fail("POST request to [{}] failed: " + e.getMessage());
                }
            }, res -> {
                if (res.succeeded()) {
                    future.complete();
                } else {
                    LOG.debug("POST request to [{}] returned [{}]", url, res.result());
                    future.fail(res.cause());
                }
            });
        } catch (DecodeException e) {
            LOG.error("Invalid Request received: {}", message.body());
            future.fail("Invalid Request received");
        }

        return future;
    }
}
