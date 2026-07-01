package com.autoerd.domain.metadata.policy;

import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;

import java.util.List;

public interface RelationInferencePolicy {

    List<TableRelation> inferRelations(List<TableSchema> schemas);
}
