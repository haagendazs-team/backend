package com.haagendazs.infrastructure.registry;

import com.haagendazs.domain.model.EventTypeDefinition;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EventTypeRegistry {

    private final ConcurrentHashMap<String, EventTypeDefinition> byCode = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EventTypeDefinition> byStreamKey = new ConcurrentHashMap<>();

    public void register(EventTypeDefinition definition) {
        byCode.put(definition.getCode(), definition);
        byStreamKey.put(definition.getStreamKey(), definition);
    }

    public Optional<EventTypeDefinition> getByCode(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    public Optional<EventTypeDefinition> getByStreamKey(String streamKey) {
        return Optional.ofNullable(byStreamKey.get(streamKey));
    }

    public Collection<EventTypeDefinition> getAllDefinitions() {
        return byCode.values();
    }

    public Set<String> getAllStreamKeys() {
        return byStreamKey.keySet();
    }
}
