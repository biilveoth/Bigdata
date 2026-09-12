package com.example.iplog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LogParserTest {
    @Test
    void extractsIpv4FromCommonLogFormat() {
        String line = "192.168.1.10 - - [24/Aug/2026:10:15:20] \"GET /index.html HTTP/1.1\" 200 1024";
        assertEquals("192.168.1.10", LogParser.extractIp(line).orElseThrow(AssertionError::new));
    }

    @Test
    void extractsCompressedIpv6() {
        assertEquals("2001:db8::1", LogParser.extractIp("2001:db8::1 GET /").orElseThrow(AssertionError::new));
    }

    @Test
    void rejectsInvalidLinesAndAddresses() {
        assertFalse(LogParser.extractIp("").isPresent());
        assertFalse(LogParser.extractIp("not-an-ip GET /").isPresent());
        assertFalse(LogParser.extractIp("999.1.1.1 GET /").isPresent());
        assertFalse(LogParser.extractIp("192.168.01.1 GET /").isPresent());
    }
}