package com.example.hdfs;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class LdapEntryPointTest {
    @Test
    void allLaunchPathsStopBeforeHdfsWhenLdapConfigurationIsMissing() throws Exception {
        assertBlocked("Main");
        assertBlocked("com.example.hdfs.HdfsFileLister");
        assertBlocked("com.example.hdfs.HdfsFileLister", "analyze");
        assertBlocked("com.example.iplog.IpLogAnalysisDriver");
    }

    private void assertBlocked(String... arguments) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", executable).toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        java.util.Collections.addAll(command, arguments);
        Path output = Files.createTempFile("ldap-entrypoint-", ".log");
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().keySet().removeIf(key -> key.equalsIgnoreCase("LDAP_CA_CERT"));
            builder.redirectErrorStream(true).redirectOutput(output.toFile());
            process = builder.start();
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Entry point must fail promptly");
            String log = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            assertNotEquals(0, process.exitValue(), log);
            assertTrue(log.contains("Set LDAP_CA_CERT"), log);
            assertFalse(log.contains("Connected to"), log);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly().waitFor();
            }
            Files.deleteIfExists(output);
        }
    }
}
