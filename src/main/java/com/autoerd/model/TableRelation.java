package com.autoerd.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TableRelation {
    private final String fromTable;
    private final String fromColumn;
    private final String toTable;
    private final String toColumn;
    private final RelationType type;
}
