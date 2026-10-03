package com.mercury;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.util.Map;

public class YamlProcessor {
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public Map<String, Object> readComposeFile(String path) throws Exception {
        return mapper.readValue(new File(path), new TypeReference<Map<String, Object>>() { });
    }

    public void writeComposeFile(Map<String, Object> data, String path) throws Exception {
        mapper.writeValue(new File(path), data);
    }
}