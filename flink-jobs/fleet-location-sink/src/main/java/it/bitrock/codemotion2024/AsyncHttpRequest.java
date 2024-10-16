package it.bitrock.codemotion2024;

import com.fasterxml.jackson.databind.JsonNode;
import it.bitrock.codemotion2024.models.Vehicle;
import it.bitrock.codemotion2024.models.VehiclePosition;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

@Slf4j
class AsyncHttpRequest extends RichAsyncFunction<Vehicle, VehiclePosition> {

    private transient HttpClient httpClient;
    private final String serviceUrl;
    private final String apiKey;

    private final SerializedFunction<JsonNode, VehiclePosition> jsonToVehiclePosition = (jsonNode) -> {
        JsonNode properties = jsonNode.get("features").get(0).get("properties");
        String fullAddress = properties.get("full_address").asText();
        String city = properties.get("context").get("place").get("name").asText();
        String zone = properties.get("context").get("region").get("name").asText();
        return new VehiclePosition("", city, zone, fullAddress);
    };

    public AsyncHttpRequest(String url, String apiKey) {
        this.serviceUrl = url;
        this.apiKey = apiKey;
    }

    @Override
    public void timeout(Vehicle input, ResultFuture<VehiclePosition> resultFuture) throws Exception {
        log.warn("Timeout while recovering position for vehicle {}", input.getPlate());
        resultFuture.complete(Collections.emptyList());
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void asyncInvoke(Vehicle in, final ResultFuture<VehiclePosition> resultFuture) throws Exception {
        log.info("Retrieving vehicle position for vehicle {}", in.getPlate());
        long start = System.currentTimeMillis();
        String url = String.format(serviceUrl + "?longitude=%s&latitude=%s&access_token=%s", in.getCurrent().getLng(), in.getCurrent().getLat(), apiKey);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).header("accept", "application/json").build();
        CompletableFuture<HttpResponse<Supplier<VehiclePosition>>> completableFuture = httpClient.sendAsync(request, new JsonBodyHandler<>(jsonToVehiclePosition));

        CompletableFuture.supplyAsync((Supplier<Optional<VehiclePosition>>) () -> {
            try {
                return Optional.of(completableFuture.get().body().get());
            } catch (InterruptedException | ExecutionException e) {
                log.error("Failed to retrieve vehicle position for vehicle {}", in.getPlate(), e);
                return Optional.empty();
            }
        }).thenAccept((Optional<VehiclePosition> result) -> {
            result.ifPresent(vehiclePosition -> {
                log.info("Retrieved vehicle position for vehicle {} in {}", in.getPlate(), System.currentTimeMillis() - start);
                resultFuture.complete(Collections.singleton(new VehiclePosition(in.getPlate(), vehiclePosition.getCity(), vehiclePosition.getZone(), vehiclePosition.getFullAddress())));
            });
        });
    }
}