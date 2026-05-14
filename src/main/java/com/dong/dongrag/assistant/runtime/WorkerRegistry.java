package com.dong.dongrag.assistant.runtime;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WorkerRegistry {

    private final Map<String, DomainWorker> byType;

    public WorkerRegistry(List<DomainWorker> workers) {
        this.byType = workers.stream().collect(Collectors.toMap(
                DomainWorker::type,
                Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException("Duplicate DomainWorker type: " + a.type());
                }
        ));
    }

    public DomainWorker resolve(String type) {
        if (type == null) {
            return null;
        }
        return byType.get(type);
    }

    public Set<String> registeredTypes() {
        return Collections.unmodifiableSet(byType.keySet());
    }
}
