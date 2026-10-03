package com.mercury;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DossierParser {
    private static final Pattern EXEMPT_DIRECTIVE = Pattern.compile(
            "(?im)^\\s*(?:[-*]\\s*)?exempt\\s+(?:service\\s+)?(?!for\\b|from\\b|unless\\b)([a-z0-9][a-z0-9_.-]*)(?=$|\\s|[.,;:!?])");
        private static final Pattern QUOTED_EXEMPT_DIRECTIVE = Pattern.compile(
            "(?i)\\bexempt\\s+(?:service\\s+)?(['\"`])([a-z0-9][a-z0-9_.-]*)\\1");
    private static final Pattern SERVICE_EXEMPTION = Pattern.compile(
            "(?i)\\bservice\\s+['\"`]?([a-z0-9][a-z0-9_.-]*)['\"`]?\\s+(?:is\\s+)?(?:exempt|excepted)\\b");
    private static final Pattern EXCEPTION_FOR = Pattern.compile(
            "(?i)\\b(?:approved|granted)\\s+exception\\s+for\\s+(?:service\\s+)?['\"`]?([a-z0-9][a-z0-9_.-]*)['\"`]?(?=$|\\s|[.,;:!?])");

    public List<String> getApprovedExceptions(String dossierPath) throws Exception {
        String dossier = Files.readString(Paths.get(dossierPath), StandardCharsets.UTF_8);
        Set<String> exceptions = new LinkedHashSet<>();
        collectMatches(dossier, EXEMPT_DIRECTIVE, exceptions);
        collectMatches(dossier, QUOTED_EXEMPT_DIRECTIVE, exceptions, 2);
        collectMatches(dossier, SERVICE_EXEMPTION, exceptions);
        collectMatches(dossier, EXCEPTION_FOR, exceptions);
        return List.copyOf(exceptions);
    }

    private static void collectMatches(String text, Pattern pattern, Set<String> matches) {
        collectMatches(text, pattern, matches, 1);
    }

    private static void collectMatches(String text, Pattern pattern, Set<String> matches, int nameGroup) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            matches.add(matcher.group(nameGroup));
        }
    }
}