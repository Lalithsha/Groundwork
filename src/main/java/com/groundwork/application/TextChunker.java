package com.groundwork.application;

import com.groundwork.domain.model.TextChunk;

import java.util.List;

public interface TextChunker {
    List<TextChunk> chunk(String text);
}
