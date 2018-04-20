import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

public class TransformationVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Future<Void> future) {
        vertx.eventBus().consumer("transform", message -> {

            JsonObject request = (JsonObject) message.body();
            JsonArray list = request.getJsonArray("values");
            Iterator<Object> listItr = list.iterator();

            StringBuilder sb = new StringBuilder();
            sb.append("City,Time,Latitude,Longitude,Temperature\n");

            while (listItr.hasNext()) {
                JsonObject obj = (JsonObject) listItr.next();

                String city = obj.getString("name");
                String latitude = obj.getJsonObject("coord").getDouble("Lat").toString();
                String longitude = obj.getJsonObject("coord").getDouble("Lon").toString();
                String temp = obj.getJsonObject("main").getDouble("temp").toString();
                Long timeStamp = obj.getLong("dt");
                String date = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date(timeStamp * 1000L));

                sb.append(city).append(",");
                sb.append(date).append(",");
                sb.append(latitude).append(",");
                sb.append(longitude).append(",");
                sb.append(temp).append("\n");
            }

            writeLineToFile(sb.toString());
        });
    }

    private void writeLineToFile(String data) {
        String file = config().getString("filePath");

        vertx.fileSystem().writeFile(file, Buffer.buffer(data), result -> {
            if (result.failed()) {
                LOG.error("Failed to write line [{}] to file: {}", data, file);
            }
        });
    }
}
