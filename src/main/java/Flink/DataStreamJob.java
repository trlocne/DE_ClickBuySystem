package Flink;

import Deserializer.BuyDatasetDeserializationSchema;
import Deserializer.ClickDatasetDeserializationSchema;
import com.events.BuyEvent;
import com.events.ClickEvent;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class DataStreamJob {
    private static final String jdbcUrl = "jdbc:postgresql://localhost:5432/postgres";
    private static final String username = "postgres";
    private static final String password = "postgres";

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<BuyEvent> buyStream = KafkaSource.<BuyEvent>builder()
                .setBootstrapServers("localhost:9092")
                .setTopics("buy_events")
                .setGroupId("Buy-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new BuyDatasetDeserializationSchema())
                .build();

        DataStream<BuyEvent> buyEvents = env.fromSource(buyStream, WatermarkStrategy.noWatermarks(), "Buy Events")
                .filter(new FilterFunction<BuyEvent>() {
                    @Override
                    public boolean filter(BuyEvent value) throws Exception {
                        return value != null &&
                               value.getSessionId() != null &&
                               !value.getSessionId().toString().isEmpty();
                    }
                });

        buyEvents.print();

        JdbcExecutionOptions jobExecutionOptions = new JdbcExecutionOptions.Builder()
                .withBatchSize(1000)
                .withBatchIntervalMs(200)
                .withMaxRetries(5)
                .build();

        JdbcConnectionOptions connectionOptions = new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                .withUrl(jdbcUrl)
                .withDriverName("org.postgresql.Driver")
                .withUsername(username)
                .withPassword(password)
                .build();

        buyEvents.addSink(JdbcSink.sink(
                "CREATE TABLE IF NOT EXISTS buy_events ( " +
                        "idx SERIAL PRIMARY KEY, " +
                        "session_id VARCHAR(255), " +
                        "timestamp VARCHAR(255), " +
                        "item_id VARCHAR(255), " +
                        "price INT, " +
                        "quantity INT " +
                        ")",
                (JdbcStatementBuilder<BuyEvent>) (ps, t) -> {

                },
                jobExecutionOptions,
                connectionOptions
        )).name("Create Buy dataset table");

        buyEvents.addSink(JdbcSink.sink(
                "INSERT INTO buy_events (session_id, timestamp, item_id, price, quantity) " +
                        "VALUES (?, ?, ?, ?, ?) ",
                (JdbcStatementBuilder<BuyEvent>) (ps, t) -> {
                    ps.setString(2, (t.getTimestamp() != null && !t.getTimestamp().isEmpty()) ? t.getTimestamp().toString() : null);
                    ps.setString(3, (t.getItemId() != null && !t.getItemId().isEmpty()) ? t.getItemId().toString() : null);
                    ps.setInt(4, t.getPrice());
                    ps.setInt(5, t.getQuantity());
                    ps.setString(1, (t.getSessionId() != null && !t.getSessionId().isEmpty()) ? t.getSessionId().toString() : null);
                },
                jobExecutionOptions,
                connectionOptions
        )).name("Insert Buy dataset");

        ///-----------------------------ClickDatset---------------------------------------///

         KafkaSource<ClickEvent> clickStream = KafkaSource.<ClickEvent>builder()
                 .setBootstrapServers("localhost:9092")
                 .setTopics("click_events")
                 .setGroupId("Click-group")
                 .setStartingOffsets(OffsetsInitializer.latest())
                 .setValueOnlyDeserializer(new ClickDatasetDeserializationSchema())
                 .build();

         DataStream<ClickEvent> clickEvents = env.fromSource(clickStream, WatermarkStrategy.noWatermarks(), "Click Events")
                                 .filter(new FilterFunction<ClickEvent>() {
                                         @Override
                                         public boolean filter(ClickEvent value) throws Exception {
                                         return value != null &&
                                                 value.getSessionId() != null &&
                                                 !value.getSessionId().toString().isEmpty();
                                         }
                                 });

         clickEvents.print();

         clickEvents.addSink(JdbcSink.sink(
                 "CREATE TABLE IF NOT EXISTS click_events ( " +
                         "idx SERIAL PRIMARY KEY, " +
                         "session_id VARCHAR(255), " +
                         "timestamp VARCHAR(255), " +
                         "item_id VARCHAR(255), " +
                         "category VARCHAR(255) " +
                         ")",
                 (JdbcStatementBuilder<ClickEvent>) (ps, t) -> {

                 },
                 jobExecutionOptions,
                 connectionOptions
         )).name("Create Click dataset table");

         clickEvents.addSink(JdbcSink.sink(
                 "INSERT INTO click_events (session_id, timestamp, item_id, category) " +
                         "VALUES (?, ?, ?, ?) ",
                 (JdbcStatementBuilder<ClickEvent>) (ps, t) -> {
                     ps.setString(1, (t.getSessionId() != null && !t.getSessionId().isEmpty()) ? t.getSessionId().toString() : null);
                     ps.setString(2, (t.getTimestamp() != null && !t.getTimestamp().isEmpty()) ? t.getTimestamp().toString() : null);
                     ps.setString(3, (t.getItemId() != null && !t.getItemId().isEmpty()) ? t.getItemId().toString() : null);
                     ps.setString(4, (t.getCategory() != null && !t.getCategory().isEmpty()) ? t.getCategory().toString() : null);
                 },
                 jobExecutionOptions,
                 connectionOptions
         )).name("Insert Click dataset");

        env.execute("Đồ án đa ngành");
    }
}
