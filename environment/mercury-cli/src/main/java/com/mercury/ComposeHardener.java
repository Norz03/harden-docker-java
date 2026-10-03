package com.mercury;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ComposeHardener {
    private static final Pattern DIGEST_PIN = Pattern.compile(".+@sha256:[a-fA-F0-9]{64}$");
    private static final Pattern PORT = Pattern.compile("[0-9]+(?:-[0-9]+)?");

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            System.err.println("Error processing compose file: " + e.getMessage());
            System.exit(1);
        }
    }

    static void run(String[] args) throws Exception {
        if (args.length > 5) {
            throw new IllegalArgumentException(
                    "Usage: ComposeHardener [input] [output] [lockfile] [dossier] [report]");
        }

        Path inputPath = args.length > 0 ? Paths.get(args[0]) : defaultInputPath();
        Path outputPath = args.length > 1 ? Paths.get(args[1]) : defaultOutputPath();
        Path lockfilePath = args.length > 2 ? Paths.get(args[2]) : defaultLockfilePath();
        Path dossierPath = args.length > 3 ? Paths.get(args[3]) : defaultDossierPath();
        Path reportPath = args.length > 4
                ? Paths.get(args[4])
                : outputPath.resolveSibling("hardening-report.json");

        YamlProcessor yamlProcessor = new YamlProcessor();
        Map<String, Object> compose = yamlProcessor.readComposeFile(inputPath.toString());
        Map<String, String> lockfile = new LockfileReader().loadLockfile(lockfilePath.toString());
        List<String> exceptions = args.length > 3 || Files.isRegularFile(dossierPath)
                ? new DossierParser().getApprovedExceptions(dossierPath.toString())
                : List.of();

        Object serviceData = compose.get("services");
        if (!(serviceData instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Compose file must contain a services mapping.");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> services = (Map<String, Object>) serviceData;
        HardeningSummary summary = hardenServices(services, lockfile, exceptions);

        createParent(outputPath);
        createParent(reportPath);
        yamlProcessor.writeComposeFile(compose, outputPath.toString());

        Map<String, Integer> report = new LinkedHashMap<>();
        report.put("services_processed", summary.servicesProcessed());
        report.put("exceptions_granted", summary.exceptionsGranted());
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(reportPath.toFile(), report);

        System.out.printf("Hardened %d services; %d exception(s) applied.%n",
                summary.servicesProcessed(), summary.exceptionsGranted());
        System.out.println("Compose: " + outputPath.toAbsolutePath());
        System.out.println("Report:  " + reportPath.toAbsolutePath());
    }

    static HardeningSummary hardenServices(
            Map<String, Object> services,
            Map<String, String> lockfile,
            List<String> exceptions) {
        int exceptionsGranted = 0;

        for (Map.Entry<String, Object> entry : services.entrySet()) {
            String serviceName = entry.getKey();
            if (exceptions.contains(serviceName)) {
                exceptionsGranted++;
                continue;
            }

            if (!(entry.getValue() instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("Service '" + serviceName + "' must be a mapping.");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) entry.getValue();
            Object imageValue = config.get("image");
            if (!(imageValue instanceof String image) || image.isBlank()) {
                throw new IllegalArgumentException("Service '" + serviceName + "' must declare an image.");
            }

            String pinnedImage = lockfile.get(image);
            if (pinnedImage == null || !DIGEST_PIN.matcher(pinnedImage).matches()) {
                throw new IllegalArgumentException(
                        "No valid SHA-256 lockfile entry found for image '" + image + "'.");
            }

            config.put("image", pinnedImage);
            config.put("user", "1000");
            config.put("cap_drop", List.of("ALL"));
            if (config.containsKey("ports")) {
                config.put("ports", hardenPorts(config.get("ports"), serviceName));
            }
        }

        return new HardeningSummary(services.size(), exceptionsGranted);
    }

    private static List<String> hardenPorts(Object value, String serviceName) {
        if (!(value instanceof List<?> ports)) {
            throw new IllegalArgumentException("Ports for service '" + serviceName + "' must be a list.");
        }

        List<String> safePorts = new ArrayList<>();
        for (Object port : ports) {
            if (port instanceof Map<?, ?> mapping) {
                String target = requiredPort(mapping.get("target"), serviceName);
                Object publishedValue = mapping.get("published");
                String published = publishedValue == null
                        ? target
                        : requiredPort(publishedValue, serviceName);
                Object protocolValue = mapping.get("protocol");
                String protocol = protocolValue == null ? "" : "/" + protocolValue;
                safePorts.add(bindLoopback(published + ":" + target + protocol, serviceName));
            } else {
                safePorts.add(bindLoopback(String.valueOf(port), serviceName));
            }
        }
        return safePorts;
    }

    private static String requiredPort(Object value, String serviceName) {
        if (value == null) {
            throw new IllegalArgumentException("A port mapping for service '" + serviceName
                    + "' is missing its target or published port.");
        }
        return String.valueOf(value);
    }

    private static String bindLoopback(String value, String serviceName) {
        String[] protocolParts = value.trim().split("/", 2);
        String protocol = protocolParts.length == 2 ? protocolParts[1] : "";
        if (!protocol.isEmpty() && !List.of("tcp", "udp", "sctp").contains(protocol)) {
            throw new IllegalArgumentException("Unsupported port protocol '" + protocol
                    + "' on service '" + serviceName + "'.");
        }

        String[] parts = protocolParts[0].split(":", -1);
        String published;
        String target;
        if (parts.length == 1) {
            published = parts[0];
            target = parts[0];
        } else if (parts.length == 2) {
            published = parts[0];
            target = parts[1];
        } else if (parts.length == 3) {
            published = parts[1];
            target = parts[2];
        } else {
            throw new IllegalArgumentException("Unsupported port mapping '" + value
                    + "' on service '" + serviceName + "'.");
        }

        if (!PORT.matcher(published).matches() || !PORT.matcher(target).matches()) {
            throw new IllegalArgumentException("Invalid port mapping '" + value
                    + "' on service '" + serviceName + "'.");
        }
        return "127.0.0.1:" + published + ":" + target
                + (protocol.isEmpty() ? "" : "/" + protocol);
    }

    private static void createParent(Path path) throws Exception {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static Path defaultInputPath() {
        return deployedLayoutExists()
                ? Paths.get("/app/fixtures/docker-compose.yml")
                : Paths.get("../fixtures/docker-compose.yml");
    }

    private static Path defaultOutputPath() {
        return deployedLayoutExists()
                ? Paths.get("/app/output/hardened-compose.yml")
                : Paths.get("../output/hardened-compose.yml");
    }

    private static Path defaultLockfilePath() {
        return deployedLayoutExists()
                ? Paths.get("/app/mercury-cli/compose-images.json")
                : Paths.get("compose-images.json");
    }

    private static Path defaultDossierPath() {
        return deployedLayoutExists()
                ? Paths.get("/app/docs/mercury-security-dossier.md")
                : Paths.get("../docs/mercury-security-dossier.md");
    }

    private static boolean deployedLayoutExists() {
        return Files.isRegularFile(Paths.get("/app/fixtures/docker-compose.yml"));
    }

    record HardeningSummary(int servicesProcessed, int exceptionsGranted) {
    }
}