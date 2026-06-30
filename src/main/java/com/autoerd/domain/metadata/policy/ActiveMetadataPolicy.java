package com.autoerd.domain.metadata.policy;

import java.util.List;

public interface ActiveMetadataPolicy {

    List<String> resolveCurrentTableNames(List<String> candidateTableNames);
}
