package com.dong.dongrag.assistant.runtime;

import com.dong.dongrag.assistant.model.TaskPlan;
import com.dong.dongrag.assistant.model.WorkerResult;

/**
 * Specialist worker invoked by {@link MultiAgentOrchestratorService}.
 */
public interface DomainWorker {

    String type();

    WorkerResult execute(TaskPlan.SubTask subTask, String originalUserMessage, AgentRunContext ctx);
}
