package it.bitrock.codemotion2024;

import java.io.Serializable;
import java.util.function.Function;

public interface SerializedFunction<I, O> extends Serializable, Function<I, O> {
}
