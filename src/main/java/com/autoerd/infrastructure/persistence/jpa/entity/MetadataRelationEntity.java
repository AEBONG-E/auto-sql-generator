package com.autoerd.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@Entity
@Table(
        name = "metadata_relation",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_metadata_relation_current",
                        columnNames = {
                                "project_id",
                                "from_table_id",
                                "from_column_id",
                                "to_table_id",
                                "to_column_id",
                                "is_current"
                        }
                )
        },
        indexes = {
                @Index(name = "idx_metadata_relation_project", columnList = "project_id, is_current, is_deleted"),
                @Index(name = "idx_metadata_relation_from", columnList = "from_table_id, from_column_id"),
                @Index(name = "idx_metadata_relation_to", columnList = "to_table_id, to_column_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(exclude = {"project", "sourceFile", "fromTable", "fromColumn", "toTable", "toColumn"})
public class MetadataRelationEntity extends SoftDeletableAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private MetadataProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_file_id", nullable = false)
    private MetadataSourceFileEntity sourceFile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_table_id", nullable = false)
    private MetadataTableEntity fromTable;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_column_id", nullable = false)
    private MetadataColumnEntity fromColumn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_table_id", nullable = false)
    private MetadataTableEntity toTable;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_column_id", nullable = false)
    private MetadataColumnEntity toColumn;

    @Column(name = "relation_type", nullable = false, length = 30)
    private String relationType;

    @Column(name = "inference_source", nullable = false, length = 30)
    @Builder.Default
    private String inferenceSource = "HEURISTIC";

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal confidenceScore = new BigDecimal("1.00");

    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private Boolean isCurrent = Boolean.TRUE;
}
