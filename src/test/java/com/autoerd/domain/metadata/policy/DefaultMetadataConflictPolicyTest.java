package com.autoerd.domain.metadata.policy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMetadataConflictPolicyTest {

    private final DefaultMetadataConflictPolicy policy = new DefaultMetadataConflictPolicy();

    @Test
    void shouldReturnOnlyIncomingTableNamesThatAlreadyExist() {
        List<String> existing = List.of("orders", "customer");
        List<String> incoming = List.of("orders", "product");

        List<String> conflicts = policy.resolveOverwrittenTableNames(existing, incoming);

        assertThat(conflicts).containsExactly("orders");
    }

    @Test
    void noOverlapShouldReturnEmptyList() {
        List<String> existing = List.of("orders");
        List<String> incoming = List.of("product");

        List<String> conflicts = policy.resolveOverwrittenTableNames(existing, incoming);

        assertThat(conflicts).isEmpty();
    }

    @Test
    void emptyExistingShouldNeverConflict() {
        List<String> conflicts = policy.resolveOverwrittenTableNames(List.of(), List.of("orders", "customer"));

        assertThat(conflicts).isEmpty();
    }

    @Test
    void allIncomingMatchingExistingShouldAllBeReportedAsConflicts() {
        List<String> existing = List.of("orders", "customer", "product");
        List<String> incoming = List.of("customer", "product");

        List<String> conflicts = policy.resolveOverwrittenTableNames(existing, incoming);

        assertThat(conflicts).containsExactlyInAnyOrder("customer", "product");
    }
}
