package com.autoerd.domain.metadata.policy;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DefaultMetadataConflictPolicy implements MetadataConflictPolicy {

    @Override
    public List<String> resolveOverwrittenTableNames(List<String> existingActiveTables, List<String> incomingTables) {
        Set<String> existing = new HashSet<>(existingActiveTables);
        return incomingTables.stream()
                .filter(existing::contains)
                .collect(Collectors.toList());
    }
}
