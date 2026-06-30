package com.autoerd.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ColumnDef {
    private final int ordinal;
    private final String columnName;
    private final String columnComment;
    private final String dataType;
    private final String columnType;
    private final KeyType keyType;
    private final boolean nullable;
    private final boolean autoIncrement;
    private final String defaultValue;
}
