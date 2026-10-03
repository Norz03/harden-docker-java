package com.mercury;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LockfileReaderTest {
    @Test
    void loadsImageDigestsFromProjectLockfile() throws Exception {
        assertEquals(2, new LockfileReader().loadLockfile("compose-images.json").size());
    }
}