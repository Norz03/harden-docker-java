package com.mercury;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComposeHardenerTest {
    private static final String NGINX_PIN = "nginx@sha256:0d17b565c37bcbd895e9d92315a05c1c3c9a29f762b011a10c54a66cd53c9b31";

    @TempDir
    Path temporaryDirectory;

    @Test
    void hardensServicesAndPreservesApprovedExceptions() {
        Map<String, Object> web = new LinkedHashMap<>();
        web.put("image", "nginx:latest");
        web.put("user", "root");
        web.put("ports", new ArrayList<>(List.of("80:80", "0.0.0.0:443:443", "8080:80/udp")));

        Map<String, Object> cache = new LinkedHashMap<>();
        cache.put("image", "redis:7");
        cache.put("ports", new ArrayList<>(List.of("6379:6379")));

        Map<String, Object> services = new LinkedHashMap<>();
        services.put("web", web);
        services.put("legacy-cache", cache);
        Map<String, String> lockfile = Map.of("nginx:latest", NGINX_PIN);

        ComposeHardener.HardeningSummary summary = ComposeHardener.hardenServices(
                services, lockfile, List.of("legacy-cache"));

        assertEquals(2, summary.servicesProcessed());
        assertEquals(1, summary.exceptionsGranted());
        assertEquals(NGINX_PIN, web.get("image"));
        assertEquals("1000", web.get("user"));
        assertEquals(List.of("ALL"), web.get("cap_drop"));
        assertEquals(List.of("127.0.0.1:80:80", "127.0.0.1:443:443", "127.0.0.1:8080:80/udp"),
                web.get("ports"));
        assertEquals("redis:7", cache.get("image"));
        assertTrue(!cache.containsKey("cap_drop"));
    }

    @Test
    void rejectsImagesWithoutValidDigestLockEntries() {
        Map<String, Object> service = new LinkedHashMap<>();
        service.put("image", "nginx:latest");
        Map<String, Object> services = Map.of("web", service);

        assertThrows(IllegalArgumentException.class,
                () -> ComposeHardener.hardenServices(services, Map.of(), List.of()));
    }

    @Test
    void failsWhenAnExplicitDossierPathDoesNotExist() throws Exception {
        Path compose = temporaryDirectory.resolve("compose.yml");
        Path lockfile = temporaryDirectory.resolve("images.json");
        Files.writeString(compose, "services:\n  web:\n    image: nginx:latest\n");
        Files.writeString(lockfile, "{\"nginx:latest\":\"" + NGINX_PIN + "\"}");

        assertThrows(Exception.class, () -> ComposeHardener.run(new String[]{
                compose.toString(),
                temporaryDirectory.resolve("hardened.yml").toString(),
                lockfile.toString(),
                temporaryDirectory.resolve("missing-dossier.md").toString()
        }));
    }
}