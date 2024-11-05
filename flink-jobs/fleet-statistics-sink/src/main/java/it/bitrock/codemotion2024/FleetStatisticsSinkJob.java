package it.bitrock.codemotion2024;

import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.util.Collector;
import it.bitrock.codemotion2024.models.Location;
import it.bitrock.codemotion2024.models.VehicleStats;
import it.bitrock.codemotion2024.models.Vehicle;

import java.util.Map;
import java.util.Objects;

@Slf4j
public class FleetStatisticsSinkJob {

    static final Map<String, String> envVariables = System.getenv();
    public static final String KAFKA_BOOTSTRAP_SERVERS = envVariables.getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
    public static final String FLEET_INPUT_TOPIC = envVariables.getOrDefault("FLEET_INPUT_TOPIC", "MISSING_INPUT_TOPIC");
    public static final String DATABASE_HOST = envVariables.getOrDefault("DATABASE_HOST", "localhost");
    public static final String DATABASE_PORT = envVariables.getOrDefault("DATABASE_PORT", "5432");
    public static final String DATABASE_NAME = envVariables.getOrDefault("DATABASE_NAME", "fleet_demo");
    public static final String DATABASE_USER = envVariables.getOrDefault("DATABASE_USER", "admin");
    public static final String DATABASE_PASSWORD = envVariables.getOrDefault("DATABASE_PASSWORD", "password");
    public static final Long CHECKPOINT_INTERVAL_MS = Long.parseLong(envVariables.getOrDefault("CHECKPOINT_INTERVAL_MS", "5000"));


    public static void main(String[] args) throws Exception {


        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(CHECKPOINT_INTERVAL_MS);
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);

        // fleet source - vehicles
        KafkaSource<Vehicle> fleetSource = KafkaSource.<Vehicle>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setTopics(FLEET_INPUT_TOPIC)
                .setGroupId("fleet-statistics-sink-job")
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

        SinkFunction<VehicleStats> tripsPostgresSink = JdbcSink.sink(
                "insert into vehicle_stats (plate, total_distance, avg_speed) " +
                        "values (?,?,?) " +
                        "on conflict(plate) do update set " +
                        "total_distance = (?), avg_speed = (?)",
                (statement, trip) -> {
                    statement.setString(1, trip.getPlate());
                    statement.setDouble(2, trip.getTotalDistance());
                    statement.setDouble(3, trip.getAverageSpeed());

                    statement.setDouble(4, trip.getTotalDistance());
                    statement.setDouble(5, trip.getAverageSpeed());
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
                .filter(Objects::nonNull)
                .keyBy(Vehicle::getPlate)
                .process(new KeyedProcessFunction<String, Vehicle, VehicleStats>() {
                    final ValueStateDescriptor<Long> nEventsStateDescriptor = new ValueStateDescriptor<>("nEvents", TypeInformation.of(Long.class));
                    final ValueStateDescriptor<Location> currentWaypointStateDescriptor = new ValueStateDescriptor<>("currentWaypoint", TypeInformation.of(Location.class));
                    final ValueStateDescriptor<Double> currentSegmentStateDescriptor = new ValueStateDescriptor<>("currentSegment", TypeInformation.of(Double.class));
                    final ValueStateDescriptor<Double> avgSpeedStateDescriptor = new ValueStateDescriptor<>("avgSpeed", TypeInformation.of(Double.class));
                    final ValueStateDescriptor<Double> totalDistanceStateDescriptor = new ValueStateDescriptor<>("totalDistance", TypeInformation.of(Double.class));

                    @Override
                    public void processElement(Vehicle vehicle,
                                               KeyedProcessFunction<String, Vehicle, VehicleStats>.Context ctx,
                                               Collector<VehicleStats> out) throws Exception {
                        RuntimeContext runtimeContext = getRuntimeContext();

                        ValueState<Long> nSegments = runtimeContext.getState(nEventsStateDescriptor);
                        ValueState<Location> currentWaypoint = runtimeContext.getState(currentWaypointStateDescriptor);
                        ValueState<Double> currentSegment = runtimeContext.getState(currentSegmentStateDescriptor);
                        ValueState<Double> currentAvgSpeed = runtimeContext.getState(avgSpeedStateDescriptor);
                        ValueState<Double> totalDistance = runtimeContext.getState(totalDistanceStateDescriptor);

                        if (nSegments.value() == null) {
                            initState(runtimeContext, vehicle);
                        } else {
                            double newAvgSpeed = (currentAvgSpeed.value() * nSegments.value() + vehicle.getSpeed()) / (nSegments.value() + 1);
                            currentAvgSpeed.update(newAvgSpeed);
                            nSegments.update(nSegments.value() + 1);
                            Location savedWaypoint = currentWaypoint.value();
                            Location vehicleWaypoint = vehicle.getWaypoint();
                            if (!(savedWaypoint.getLat() == vehicleWaypoint.getLat() && savedWaypoint.getLng() == vehicleWaypoint.getLng())) {
                                totalDistance.update(totalDistance.value() + currentSegment.value());
                            } else {
                                Double segmentDiff = currentSegment.value() - (vehicle.getDistance() / 1000);
                                totalDistance.update(totalDistance.value() + segmentDiff);
                            }
                            currentSegment.update(vehicle.getDistance() / 1000);
                            currentWaypoint.update(vehicle.getWaypoint());
                        }
                        VehicleStats vehicleStats = new VehicleStats(
                                vehicle.getPlate(),
                                totalDistance.value(),
                                currentAvgSpeed.value()
                        );
                        out.collect(vehicleStats);
                    }

                    private void initState(RuntimeContext runtimeContext, Vehicle vehicle) throws Exception {
                        runtimeContext.getState(nEventsStateDescriptor).update(1L);
                        runtimeContext.getState(currentWaypointStateDescriptor).update(vehicle.getWaypoint());
                        runtimeContext.getState(currentSegmentStateDescriptor).update(vehicle.getDistance() / 1000);
                        runtimeContext.getState(avgSpeedStateDescriptor).update(vehicle.getSpeed());
                        runtimeContext.getState(totalDistanceStateDescriptor).update(0.0);
                    }
                })
                .addSink(tripsPostgresSink);

        env.execute();
    }
}