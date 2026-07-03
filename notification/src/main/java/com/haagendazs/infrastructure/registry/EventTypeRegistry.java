package com.haagendazs.infrastructure.registry;

import com.haagendazs.domain.model.EventTypeDefinition;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EventTypeRegistry {

    private final ConcurrentHashMap<String, EventTypeDefinition> byCode = new ConcurrentHashMap<>();

    public void register(EventTypeDefinition definition) {
        byCode.put(definition.getCode(), definition);
    }

    public Optional<EventTypeDefinition> getByCode(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    public Collection<EventTypeDefinition> getAllDefinitions() {
        return byCode.values();
    }
}
