package com.dong.dongrag.assistant.tool;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
public class ToolCallTraceContext {

    private final InheritableThreadLocal<Consumer<String>> logCallbackHolder = new InheritableThreadLocal<>();
    private final InheritableThreadLocal<List<String>> logListHolder = new InheritableThreadLocal<>();

    public void start(Consumer<String> callback) {
        logCallbackHolder.set(callback);
        logListHolder.set(new ArrayList<>());
    }

    public void log(String message) {
        List<String> logList = logListHolder.get();
        if (logList != null) {
            logList.add(message);
        }
        Consumer<String> callback = logCallbackHolder.get();
        if (callback != null) {
            callback.accept(message);
        }
    }

    public List<String> snapshot() {
        List<String> logs = logListHolder.get();
        return logs == null ? List.of() : new ArrayList<>(logs);
    }

    public void clear() {
        logCallbackHolder.remove();
        logListHolder.remove();
    }
}
