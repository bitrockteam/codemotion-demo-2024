package it.bitrock.codemotion2024;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.util.function.Function;
import java.util.function.Supplier;

public class JsonBodyHandler<T> implements HttpResponse.BodyHandler<Supplier<T>> {

    private static final ObjectMapper om = new ObjectMapper();
    private final Function<JsonNode, T> mapper;

    public JsonBodyHandler(Function<JsonNode, T> mapper) {
        this.mapper = mapper;
    }

    @Override
    public HttpResponse.BodySubscriber<Supplier<T>> apply(HttpResponse.ResponseInfo responseInfo) {
        return asJSON(mapper);
    }


    public static <W> HttpResponse.BodySubscriber<Supplier<W>> asJSON(Function<JsonNode, W> mapper) {
        HttpResponse.BodySubscriber<InputStream> upstream = HttpResponse.BodySubscribers.ofInputStream();

        return HttpResponse.BodySubscribers.mapping(
                upstream,
                inputStream -> toSupplierOfType(inputStream, mapper));
    }

    public static <W> Supplier<W> toSupplierOfType(InputStream inputStream, Function<JsonNode, W> mapper) {
        return () -> {
            try (InputStream stream = inputStream) {
                JsonNode jsonNode = om.readTree(stream);
                return mapper.apply(jsonNode);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }
}
