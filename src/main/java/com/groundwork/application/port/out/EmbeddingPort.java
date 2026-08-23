package com.groundwork.application.port.out;

import java.util.List;

public interface EmbeddingPort {
    List<double[]> embed(List<String> texts);
    String modelName();
    String modelVersion();
    int dimensions();

    default double[] embed(String text) {
        return embed(List.of(text)).getFirst();
    }
}
