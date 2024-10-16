package it.bitrock.codemotion2024;

import it.bitrock.codemotion2024.models.Location;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.triggers.EventTimeTrigger;
import org.apache.flink.streaming.api.windowing.triggers.PurgingTrigger;
import it.bitrock.codemotion2024.models.Vehicle;

import java.util.Map;
import java.util.Objects;

@Slf4j
public class FleetDirectionCounterJob {

    static final Map<String, String> envVariables = System.getenv();
    public static final Integer WINDOW_UPDATE_INTERVAL = Integer.parseInt(envVariables.getOrDefault("WINDOW_UPDATE_INTERVAL", "5"));
    public static final String KAFKA_BOOTSTRAP_SERVERS = envVariables.getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
    public static final String FLEET_INPUT_TOPIC = envVariables.getOrDefault("FLEET_INPUT_TOPIC", "MISSING_INPUT_TOPIC");
    public static final String DIRECTION_COUNT_OUTPUT_TOPIC = envVariables.getOrDefault("DIRECTION_COUNT_OUTPUT_TOPIC", "MISSING_OUTPUT_TOPIC");

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

        KafkaSink<Tuple2<String, Integer>> directionsVehiclesSink = KafkaSink.<Tuple2<String, Integer>>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(DIRECTION_COUNT_OUTPUT_TOPIC)
                                .setKeySerializationSchema((SerializationSchema<Tuple2<String, Integer>>) vehicle -> vehicle.f0.getBytes())
                                .setValueSerializationSchema(new Tuple2Serializer<>("direction", t -> t.f0, "vehicles_count", t -> t.f1))
                                .build()
                )
                .build();

        env.fromSource(fleetSource, WatermarkStrategy.forMonotonousTimestamps(), "fleetSource")
                .filter(Objects::nonNull)
                .keyBy(vehicle -> vehicle.getPlate())
                .window(TumblingEventTimeWindows.of(Time.seconds(WINDOW_UPDATE_INTERVAL)))
                .trigger(PurgingTrigger.of(EventTimeTrigger.create()))
                .reduce(new ReduceFunction<Vehicle>() {
                    @Override
                    public Vehicle reduce(Vehicle value1, Vehicle value2) throws Exception {
                        return value2;
                    }
                })
                .map(
                        vehicle -> {
                            String direction = getDirection(vehicle);
                            return new Tuple2<>(direction, 1);
                        },
                        Types.TUPLE(Types.STRING, Types.INT)
                )
                .keyBy(tuple -> tuple.f0)
                .window(TumblingEventTimeWindows.of(Time.seconds(WINDOW_UPDATE_INTERVAL)))
                .trigger(PurgingTrigger.of(EventTimeTrigger.create()))
                .sum(1)
                .sinkTo(directionsVehiclesSink);

        env.execute();
    }

    private static String getDirection(Vehicle vehicle) {
        Location current = vehicle.getCurrent();
        Location waypoint = vehicle.getWaypoint();

        if (waypoint.getLat() >= current.getLat() && waypoint.getLng() >= current.getLng())
            return "NE";
        else if (waypoint.getLat() >= current.getLat() && waypoint.getLng() < current.getLng())
            return "NW";
        else if (waypoint.getLat() < current.getLat() && waypoint.getLng() <= current.getLng())
            return "SW";
        else
            return "SE";
    }

}