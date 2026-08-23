package com.groundwork.adapter.out.ai;

import com.groundwork.application.port.out.EmbeddingPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline embedding implementation used for local development and deterministic
 * tests. It hashes normalized terms into a unit vector; it must not be presented
 * as a semantic model in production quality reports.
 */
public final class DeterministicEmbeddingAdapter implements EmbeddingPort {

    private final int dimensions;

    public DeterministicEmbeddingAdapter(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public List<double[]> embed(List<String> texts) {
        List<double[]> embeddings = new ArrayList<>(texts.size());
        for (String text : texts) embeddings.add(embedLocally(text));
        return embeddings;
    }

    private double[] embedLocally(String text) {
        double[] vector = new double[dimensions];
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String token : normalized.split("[^\\p{L}\\p{N}_./:-]+")) {
            if (token.isBlank()) continue;
            byte[] digest = digest(token);
            int index = Math.floorMod(toInt(digest, 0), dimensions);
            double sign = (digest[4] & 1) == 0 ? 1.0 : -1.0;
            vector[index] += sign;
        }
        normalize(vector);
        return vector;
    }

    private byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JVM", exception);
        }
    }

    private int toInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24) |
            ((bytes[offset + 1] & 0xff) << 16) |
            ((bytes[offset + 2] & 0xff) << 8) |
            (bytes[offset + 3] & 0xff);
    }

    private void normalize(double[] vector) {
        double magnitude = 0;
        for (double value : vector) magnitude += value * value;
        if (magnitude == 0) return;
        double scale = 1.0 / Math.sqrt(magnitude);
        for (int i = 0; i < vector.length; i++) vector[i] *= scale;
    }

    @Override public String modelName() { return "groundwork-local-hashing"; }
    @Override public String modelVersion() { return "1"; }
    @Override public int dimensions() { return dimensions; }
}
