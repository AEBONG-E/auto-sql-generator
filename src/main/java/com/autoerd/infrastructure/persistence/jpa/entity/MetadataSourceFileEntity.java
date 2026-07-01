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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
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
        name = "metadata_source_file",
        indexes = {
                @Index(name = "idx_metadata_source_file_project", columnList = "project_id, deleted_at"),
                @Index(name = "idx_metadata_source_file_uploaded_at", columnList = "uploaded_at"),
                @Index(name = "idx_metadata_source_file_checksum", columnList = "upload_checksum")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(exclude = "project")
public class MetadataSourceFileEntity extends SoftDeletableAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private MetadataProjectEntity project;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", length = 255)
    private String storedFilename;

    @Column(name = "upload_checksum", length = 64)
    private String uploadChecksum;

    @Column(name = "source_type", nullable = false, length = 30)
    @Builder.Default
    private String sourceType = "XLSX";

    @Column(name = "import_status", nullable = false, length = 30)
    @Builder.Default
    private String importStatus = "IMPORTED";

    @Column(name = "header_signature", length = 500)
    private String headerSignature;

    @Column(name = "table_count", nullable = false)
    @Builder.Default
    private Integer tableCount = 0;

    @Column(name = "column_count", nullable = false)
    @Builder.Default
    private Integer columnCount = 0;

    @Column(name = "relation_count", nullable = false)
    @Builder.Default
    private Integer relationCount = 0;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onPersist() {
        super.onCreate();
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
    }
}
