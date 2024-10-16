package it.bitrock.codemotion2024;

import it.bitrock.codemotion2024.models.Vehicle;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.Map;

@Slf4j
public class FleetVisibleVehicleJob {

    static final Map<String, String> envVariables = System.getenv();
    public static final String KAFKA_BOOTSTRAP_SERVERS = envVariables.getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
    public static final String FLEET_INPUT_TOPIC = envVariables.getOrDefault("FLEET_INPUT_TOPIC", "MISSING_INPUT_TOPIC");
    public static final String FLEET_VISIBLE_OUTPUT_TOPIC = envVariables.getOrDefault("FLEET_VISIBLE_OUTPUT_TOPIC", "MISSING_OUTPUT_TOPIC");

    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<Vehicle> fleetSource = KafkaSource.<Vehicle>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setTopics(FLEET_INPUT_TOPIC)
                .setGroupId("fleet-direction-counter")
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

        KafkaSink<Vehicle> visibleVehiclesSink = KafkaSink.<Vehicle>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(FLEET_VISIBLE_OUTPUT_TOPIC)
                                .setKeySerializationSchema((SerializationSchema<Vehicle>) vehicle -> vehicle.getPlate().getBytes())
                                .setValueSerializationSchema(new JsonSerializationSchema<>())
                                .build()
                )
                .build();

        env.fromSource(fleetSource, WatermarkStrategy.forMonotonousTimestamps(), "fleetSource")
                .filter(vehicle -> vehicle == null || vehicle.isVisible())
                .sinkTo(visibleVehiclesSink);

        env.execute();
    }

}