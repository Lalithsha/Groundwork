package com.groundwork.application.port.out;

import java.util.function.Consumer;

public interface ChatGenerationPort {
    String generate(String prompt);
    void stream(String prompt, Consumer<String> tokenConsumer);
    boolean isAvailable();
}
