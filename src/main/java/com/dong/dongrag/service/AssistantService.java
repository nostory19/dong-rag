package com.dong.dongrag.service;

import com.dong.dongrag.model.dto.assistant.AssistantChatRequest;
import reactor.core.publisher.Flux;

public interface AssistantService {

    Flux<String> chat(AssistantChatRequest request);
}
