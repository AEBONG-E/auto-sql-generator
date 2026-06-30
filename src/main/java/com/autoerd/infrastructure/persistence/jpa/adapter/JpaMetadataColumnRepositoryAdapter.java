package com.autoerd.infrastructure.persistence.jpa.adapter;

import com.autoerd.domain.metadata.repository.MetadataColumnRepositoryPort;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataColumnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JpaMetadataColumnRepositoryAdapter implements MetadataColumnRepositoryPort {

    private final MetadataColumnRepository columnRepository;

    @Override
    public List<Long> findActiveColumnIdsByTableId(Long tableId) {
        return columnRepository.findAllByTableIdAndIsDeletedFalseOrderByOrdinalPositionAsc(tableId)
                .stream().map(e -> e.getId()).toList();
    }
}
