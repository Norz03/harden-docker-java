package com.mercury;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.File;
import java.util.Map;

public class LockfileReader {
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, String> loadLockfile(String lockfilePath) throws Exception {
        return mapper.readValue(new File(lockfilePath), new TypeReference<Map<String, String>>() { });
    }
}