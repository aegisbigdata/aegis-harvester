import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import model.Constants;
import model.DataSendRequest;
import model.DataType;
import model.TransformationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OwmTransformationVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private static final String CSV_HEADERS = "Location,Time,Geopoint,Avg. Temperature,Pressure,Humidity," +
            "Min. Temperature,Max. Temperature,Visibility,Wind Speed,Wind Direction,Cloudiness";

    @Override
    public void start(Future<Void> future) {
        vertx.eventBus().consumer(DataType.OWM.getEventBusAddress(), this::handleTransformation);

        future.complete();
    }

    private void handleTransformation(Message<String> message) {
        TransformationRequest request = Json.decodeValue(message.body(), TransformationRequest.class);
        LOG.debug("Transforming {}", request);

        JsonObject payload = new JsonObject(request.getPayload());
        String location = payload.getString("name");

        List<String> csvValues = new ArrayList<>();
        csvValues.add(location);

        Long timeStamp = payload.getLong("dt");
        csvValues.add(timeStamp != null
                ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timeStamp * 1000L)).replace(" ", "T")
                : "");

        JsonObject coordinates = payload.getJsonObject("coord");
        if (coordinates != null) {
            String geoPoint = "\"";

            // keys have different case depending on transformationRequest type (bbox vs locationId)
            geoPoint += (coordinates.getDouble("Lon") != null
                    ? coordinates.getDouble("Lon") != null ? coordinates.getDouble("Lon").toString() : ""
                    : coordinates.getDouble("lon") != null ? coordinates.getDouble("lon").toString() : "");

            geoPoint += ", ";

            geoPoint += (coordinates.getDouble("Lat") != null
                    ? coordinates.getDouble("Lat") != null ? coordinates.getDouble("Lat").toString() : ""
                    : coordinates.getDouble("lat") != null ? coordinates.getDouble("lat").toString() : "");

            geoPoint += "\"";

            csvValues.add(geoPoint);

        } else {
            // make sure not to break csv structure on missing values
            addEmptyValues(csvValues,2);
        }

        JsonObject main = payload.getJsonObject("main");
        if (main != null) {
            csvValues.add(main.getDouble("temp") != null ? main.getDouble("temp").toString() : "");
            csvValues.add(main.getDouble("pressure") != null ? main.getDouble("pressure").toString() : "");
            csvValues.add(main.getDouble("humidity") != null ? main.getDouble("humidity").toString() : "");
            csvValues.add(main.getDouble("temp_min") != null ? main.getDouble("temp_min").toString() : "");
            csvValues.add(main.getDouble("temp_max") != null ? main.getDouble("temp_max").toString() : "");
            csvValues.add(main.getDouble("visibility") != null ? main.getDouble("visibility").toString() : "");
        } else {
            // make sure not to break csv structure on missing values
            addEmptyValues(csvValues,6);
        }

        JsonObject wind = payload.getJsonObject("wind");
        if (wind != null) {
            csvValues.add(wind.getDouble("speed") != null ? wind.getDouble("speed").toString() : "");
            csvValues.add(wind.getDouble("deg") != null ? wind.getDouble("deg").toString() : "");
        } else {
            addEmptyValues(csvValues, 2);
        }

        JsonObject clouds = payload.getJsonObject("clouds");
        if (clouds != null) {
            csvValues.add(clouds.getDouble("all") != null ? clouds.getDouble("all").toString() : "");
        } else {
            addEmptyValues(csvValues, 1);
        }

        LOG.debug("owm aggregate {}", request.getAggregate());

        String csv = String.join(",", csvValues) + "\n";
        DataSendRequest sendRequest =
                new DataSendRequest(request.getPipeId(), request.getHopsProjectId(), request.getHopsDataset(), location, CSV_HEADERS, csv, request.getAggregate(), request.getHopsUserName(), request.getHopsPassword(), request.getMetadata(), request.getTargetFileName());

        vertx.eventBus().send(Constants.MSG_SEND, Json.encode(sendRequest));
    }

    private void addEmptyValues(List<String> list, int count) {
        for (int i = 0; i < count; i++)
            list.add("");
    }
}
