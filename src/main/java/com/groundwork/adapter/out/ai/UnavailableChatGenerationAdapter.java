package com.groundwork.adapter.out.ai;

import com.groundwork.application.port.out.ChatGenerationPort;

import java.util.function.Consumer;

public final class UnavailableChatGenerationAdapter implements ChatGenerationPort {
    @Override public String generate(String prompt) { throw new IllegalStateException("No chat provider is configured"); }
    @Override public void stream(String prompt, Consumer<String> tokenConsumer) { throw new IllegalStateException("No chat provider is configured"); }
    @Override public boolean isAvailable() { return false; }
}
