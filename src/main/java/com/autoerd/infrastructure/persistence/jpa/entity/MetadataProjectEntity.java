package com.autoerd.infrastructure.persistence.jpa.entity;

import com.autoerd.domain.sql.model.SqlDialect;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@Entity
@Table(
        name = "metadata_project",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_metadata_project_project_key", columnNames = "project_key")
        },
        indexes = {
                @Index(name = "idx_metadata_project_status", columnList = "project_status, deleted_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MetadataProjectEntity extends SoftDeletableAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", nullable = false, length = 100)
    private String projectKey;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(name = "project_status", nullable = false, length = 30)
    @Builder.Default
    private String projectStatus = "ACTIVE";

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "sql_dialect", nullable = false, length = 20)
    @Builder.Default
    private SqlDialect sqlDialect = SqlDialect.MYSQL;
}
