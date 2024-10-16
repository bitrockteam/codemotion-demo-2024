package it.bitrock.codemotion2024;

import it.bitrock.codemotion2024.models.Vehicle;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

import java.util.Map;

@Slf4j
public class FleetVehicleSinkJob {

    static final Map<String, String> envVariables = System.getenv();
    public static final String KAFKA_BOOTSTRAP_SERVERS = envVariables.getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
    public static final String VEHICLE_INPUT_TOPIC = envVariables.getOrDefault("VEHICLE_INPUT_TOPIC", "MISSING_INPUT_TOPIC");
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
                .setTopics(VEHICLE_INPUT_TOPIC)
                .setGroupId("fleet-vehicle-group")
                .setStartingOffsets(OffsetsInitializer.earliest())
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

        SinkFunction<Vehicle> vehiclePostgresSink = JdbcSink.sink(
                "insert into vehicle (plate, model_id, color) " +
                        "values (?, ?, ?) " +
                        "on conflict(plate) do update set model_id = (?), color = (?)",
                (statement, vehicle) -> {
                    statement.setString(1, vehicle.getPlate());
                    statement.setInt(2, vehicle.getModelId());
                    statement.setString(3, vehicle.getColor());

                    statement.setInt(4, vehicle.getModelId());
                    statement.setString(5, vehicle.getColor());
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

        env.fromSource(fleetSource, WatermarkStrategy.forMonotonousTimestamps(), "fleetVehicleSource")
                .addSink(vehiclePostgresSink);

        env.execute();
    }
}
