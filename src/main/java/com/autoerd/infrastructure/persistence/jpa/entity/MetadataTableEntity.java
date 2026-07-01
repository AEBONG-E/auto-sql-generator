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
        name = "metadata_table",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_metadata_table_current", columnNames = {"project_id", "active_table_name"})
        },
        indexes = {
                @Index(name = "idx_metadata_table_lookup", columnList = "project_id, schema_name, table_name, is_current, is_deleted"),
                @Index(name = "idx_metadata_table_source_file", columnList = "source_file_id, is_deleted")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(exclude = {"project", "sourceFile"})
public class MetadataTableEntity extends SoftDeletableAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private MetadataProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_file_id", nullable = false)
    private MetadataSourceFileEntity sourceFile;

    @Column(name = "schema_name", nullable = false, length = 255)
    @Builder.Default
    private String schemaName = "default";

    @Column(name = "table_name", nullable = false, length = 255)
    private String tableName;

    @Column(name = "table_description", length = 1000)
    private String tableDescription;

    @Column(name = "current_version_no", nullable = false)
    @Builder.Default
    private Integer currentVersionNo = 1;

    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private Boolean isCurrent = Boolean.TRUE;

    @Column(name = "active_table_name", insertable = false, updatable = false, length = 255)
    private String activeTableName;
}
