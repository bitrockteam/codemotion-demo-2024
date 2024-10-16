package it.bitrock.codemotion2024;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.java.tuple.Tuple2;

import java.util.function.Function;

public class Tuple2Serializer<A, B, F1, F2> implements SerializationSchema<Tuple2<A, B>> {
    private final String field0Name;
    private final Function<Tuple2<A, B>, F1> field0Function;
    private final String field1Name;
    private final Function<Tuple2<A, B>, F2> field1Function;

    public Tuple2Serializer(String field0Name,
                            SerializedFunction<Tuple2<A, B>, F1> field0Function,
                            String field1Name,
                            SerializedFunction<Tuple2<A, B>, F2> field1Function) {
        this.field0Name = field0Name;
        this.field0Function = field0Function;
        this.field1Name = field1Name;
        this.field1Function = field1Function;
    }

    final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public byte[] serialize(Tuple2<A, B> element) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.putIfAbsent(field0Name, objectMapper.convertValue(field0Function.apply(element), JsonNode.class));
        objectNode.putIfAbsent(field1Name, objectMapper.convertValue(field1Function.apply(element), JsonNode.class));
        return objectNode.toString().getBytes();
    }
}