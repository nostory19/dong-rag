package com.dong.dongrag.assistant.agent;

public interface ComplaintWorkerAgent {

    String type();

    String handle(String message, Long groupId, int topK, String conversationId);
}
