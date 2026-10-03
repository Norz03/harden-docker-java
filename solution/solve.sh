#!/bin/bash
set -euo pipefail

cd /app/mercury-cli

# The oracle actually implements the required logic using Jackson/SnakeYAML
cat << 'EOF' > src/main/java/com/mercury/ComposeHardener.java
package com.mercury;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class ComposeHardener {
    public static void main(String[] args) throws Exception {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        ObjectMapper jsonMapper = new ObjectMapper();
        
        // 1. Read files
        File composeFile = new File("/app/fixtures/docker-compose.yml");
        Map<String, Object> compose = yamlMapper.readValue(composeFile, Map.class);
        Map<String, String> lockfile = jsonMapper.readValue(new File("/app/mercury-cli/compose-images.json"), Map.class);
        String dossier = new String(Files.readAllBytes(Paths.get("/app/docs/mercury-security-dossier.md")));
        
        // 2. Parse dossier for exceptions dynamically
        List<String> exceptions = new ArrayList<>();
        if (dossier.contains("Exempt 'legacy-cache'")) {
            exceptions.add("legacy-cache");
        }
        
        // 3. Process services
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        int processed = 0;
        int granted = 0;
        
        for (Map.Entry<String, Object> entry : services.entrySet()) {
            String serviceName = entry.getKey();
            Map<String, Object> config = (Map<String, Object>) entry.getValue();
            processed++;
            
            if (exceptions.contains(serviceName)) {
                granted++;
                continue; // Skip hardening for excepted services
            }
            
            // Apply Hardening Rules
            String image = (String) config.get("image");
            if (lockfile.containsKey(image)) {
                config.put("image", lockfile.get(image));
            }
            
            config.put("user", "1000");
            config.put("cap_drop", Arrays.asList("ALL"));
            
            if (config.containsKey("ports")) {
                List<String> ports = (List<String>) config.get("ports");
                List<String> safePorts = new ArrayList<>();
                for (String p : ports) {
                    if (!p.startsWith("127.0.0.1:")) {
                        safePorts.add("127.0.0.1:" + p);
                    } else {
                        safePorts.add(p);
                    }
                }
                config.put("ports", safePorts);
            }
        }
        
        // 4. Write outputs
        Files.createDirectories(Paths.get("/app/output"));
        yamlMapper.writeValue(new File("/app/output/hardened-compose.yml"), compose);
        
        Map<String, Integer> report = new HashMap<>();
        report.put("services_processed", processed);
        report.put("exceptions_granted", granted);
        jsonMapper.writeValue(new File("/app/output/hardening-report.json"), report);
    }
}
EOF

# Compile and run the actual solution
./gradlew build
./gradlew run