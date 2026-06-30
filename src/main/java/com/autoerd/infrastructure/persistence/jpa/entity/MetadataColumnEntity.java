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
        name = "metadata_column",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_metadata_column_table_name", columnNames = {"table_id", "column_name"})
        },
        indexes = {
                @Index(name = "idx_metadata_column_table_ordinal", columnList = "table_id, ordinal_position"),
                @Index(name = "idx_metadata_column_name", columnList = "column_name")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(exclude = "table")
public class MetadataColumnEntity extends SoftDeletableAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private MetadataTableEntity table;

    @Column(name = "ordinal_position", nullable = false)
    private Integer ordinalPosition;

    @Column(name = "column_name", nullable = false, length = 255)
    private String columnName;

    @Column(name = "column_comment", length = 1000)
    private String columnComment;

    @Column(name = "data_type", nullable = false, length = 100)
    private String dataType;

    @Column(name = "column_type", length = 255)
    private String columnType;

    @Column(name = "key_type", nullable = false, length = 20)
    @Builder.Default
    private String keyType = "NONE";

    @Column(name = "nullable_yn", nullable = false, length = 1)
    @Builder.Default
    private String nullableYn = "Y";

    @Column(name = "auto_increment_yn", nullable = false, length = 1)
    @Builder.Default
    private String autoIncrementYn = "N";

    @Column(name = "default_value", length = 1000)
    private String defaultValue;
}
