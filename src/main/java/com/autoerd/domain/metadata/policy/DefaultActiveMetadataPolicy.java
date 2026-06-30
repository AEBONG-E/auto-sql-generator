package com.autoerd.domain.metadata.policy;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultActiveMetadataPolicy implements ActiveMetadataPolicy {

    @Override
    public List<String> resolveCurrentTableNames(List<String> candidateTableNames) {
        return List.copyOf(candidateTableNames);
    }
}
