package com.mercury;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DossierParserTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void extractsOnlyExplicitServiceExceptions() throws Exception {
        Path dossier = temporaryDirectory.resolve("security-dossier.md");
        Files.writeString(dossier, """
                # Security review
                Audit note: Exempt 'legacy-cache' until the migration is complete.
                The service 'read-only-proxy' is exempt for the approved transition.
                An approved exception for service `audit-sidecar` is recorded.
                The web service was discussed, but no exception was approved.
                """);

        assertEquals(List.of("legacy-cache", "read-only-proxy", "audit-sidecar"),
                new DossierParser().getApprovedExceptions(dossier.toString()));
    }
}