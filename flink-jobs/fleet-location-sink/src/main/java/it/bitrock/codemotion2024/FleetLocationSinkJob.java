package it.bitrock.codemotion2024;

import it.bitrock.codemotion2024.models.Vehicle;
import it.bitrock.codemotion2024.models.VehiclePosition;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.async.AsyncRetryStrategy;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.triggers.EventTimeTrigger;
import org.apache.flink.streaming.api.windowing.triggers.PurgingTrigger;
import org.apache.flink.streaming.util.retryable.AsyncRetryStrategies;
import org.apache.flink.streaming.util.retryable.RetryPredicates;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public class FleetLocationSinkJob {

    static final Map<String, String> envVariables = System.getenv();
    public static final Integer WINDOW_UPDATE_INTERVAL = Integer.parseInt(envVariables.getOrDefault("WINDOW_UPDATE_INTERVAL", "900"));
    public static final String GEOLOCATION_URL = envVariables.getOrDefault("GEOLOCATION_URL", "localhost:8080");
    public static final String API_KEY = envVariables.getOrDefault("API_KEY", "api-key");
    public static final String KAFKA_BOOTSTRAP_SERVERS = envVariables.getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
    public static final String FLEET_INPUT_TOPIC = envVariables.getOrDefault("FLEET_INPUT_TOPIC", "MISSING_INPUT_TOPIC");
    public static final String DATABASE_HOST = envVariables.getOrDefault("DATABASE_HOST", "localhost");
    public static final String DATABASE_PORT = envVariables.getOrDefault("DATABASE_PORT", "5432");
    public static final String DATABASE_NAME = envVariables.getOrDefault("DATABASE_NAME", "fleet_demo");
    public static final String DATABASE_USER = envVariables.getOrDefault("DATABASE_USER", "admin");
    public static final String DATABASE_PASSWORD = envVariables.getOrDefault("DATABASE_PASSWORD", "password");


    public static void main(String[] args) throws Exception {


        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // fleet source - vehicles
        KafkaSource<Vehicle> fleetSource = KafkaSource.<Vehicle>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setTopics(FLEET_INPUT_TOPIC)
                .setGroupId("fleet-vehicle-stats-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new JsonDeserializationSchema<>(Vehicle.class) {
                    @Override
                    public Vehicle deserialize(byte[] message) {
                        try {
                            return super.deserialize(message);
                        } catch (Exception e) {
                            log.error("cannot deserialize message", e);
                        }
                        return null;
                    }
                })
                .build();

        SinkFunction<VehiclePosition> positionPostgresSink = JdbcSink.sink(
                "insert into vehicle_position (plate, city, zone, full_address) " +
                        "values (?, ?, ?, ?) " +
                        "on conflict(plate) do update set " +
                        "city = (?), zone = (?), full_address = (?)",
                (statement, trip) -> {
                    statement.setString(1, trip.getPlate());
                    statement.setString(2, trip.getCity());
                    statement.setString(3, trip.getZone());
                    statement.setString(4, trip.getFullAddress());

                    statement.setString(5, trip.getCity());
                    statement.setString(6, trip.getZone());
                    statement.setString(7, trip.getFullAddress());
                },
                JdbcExecutionOptions.builder()
                        .withBatchSize(1000)
                        .withBatchIntervalMs(200)
                        .withMaxRetries(5)
                        .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl("jdbc:postgresql://" + DATABASE_HOST + ":" + DATABASE_PORT + "/" + DATABASE_NAME)
                        .withDriverName("org.postgresql.Driver")
                        .withUsername(DATABASE_USER)
                        .withPassword(DATABASE_PASSWORD)
                        .build()
        );

        // get positions
        AsyncRetryStrategy<VehiclePosition> asyncRetryStrategy =
                new AsyncRetryStrategies.FixedDelayRetryStrategyBuilder<VehiclePosition>(3, 100L)
                        .ifResult(RetryPredicates.EMPTY_RESULT_PREDICATE)
                        .ifException(RetryPredicates.HAS_EXCEPTION_PREDICATE)
                        .build();


        SingleOutputStreamOperator<Vehicle> platesLocations = env.fromSource(fleetSource, WatermarkStrategy.forMonotonousTimestamps(), "fleetVehicleSource")
                .filter(Objects::nonNull)
                .keyBy(vehicle -> vehicle.getPlate())
                .window(TumblingEventTimeWindows.of(Time.seconds(WINDOW_UPDATE_INTERVAL)))
                .trigger(PurgingTrigger.of(EventTimeTrigger.create()))
                .reduce(new ReduceFunction<Vehicle>() {
                    @Override
                    public Vehicle reduce(Vehicle value1, Vehicle value2) throws Exception {
                        return value2;
                    }
                });


        AsyncDataStream
                .unorderedWaitWithRetry(platesLocations, new AsyncHttpRequest(GEOLOCATION_URL, API_KEY), 5000, TimeUnit.MILLISECONDS, 320, asyncRetryStrategy)
                .addSink(positionPostgresSink);

        env.execute();
    }
}