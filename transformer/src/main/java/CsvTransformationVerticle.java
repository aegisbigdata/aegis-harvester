import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import model.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CsvTransformationVerticle extends AbstractVerticle {


    private static final Logger LOG = LoggerFactory.getLogger(CsvTransformationVerticle.class);

    @Override
    public void start(Future<Void> future) {
        vertx.eventBus().consumer(DataType.CSV.getEventBusAddress(), this::transformCsv);
        future.complete();
    }

    private void transformCsv(Message<String> message) {
        // TODO transform CSV and send result
    }
}
