package com.ywjno.tools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaMethodFinderTest {

    @TempDir
    Path tempDir;

    private ByteArrayOutputStream setupLogCapture() {
        ByteArrayOutputStream logContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(logContent));
        return logContent;
    }

    private void resetLogCapture() {
        System.setErr(System.err);
    }

    @Test
    void shouldFindMethodCalls() throws IOException {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        copyTestClassFile(classesDir);

        try (ByteArrayOutputStream logContent = setupLogCapture()) {
            int exitCode = new CommandLine(new JavaMethodFinder())
                    .execute("-c", "java.lang.String", "-m", "toString", "-s", classesDir.toString());

            assertEquals(0, exitCode);
            String output = logContent.toString();
            assertTrue(output.contains("java.lang.String#toString"));
            assertTrue(output.contains("- com.example.TestClass#testMethod (L8)"));
            assertTrue(output.contains("- com.example.TestClass#testMethod (L10)"));
        } finally {
            resetLogCapture();
        }
    }

    @Test
    void shouldHandleInvalidClassPath() throws IOException {
        try (ByteArrayOutputStream logContent = setupLogCapture()) {
            System.setOut(new PrintStream(logContent));
            int exitCode = new CommandLine(new JavaMethodFinder())
                    .execute(
                            "-c", "java.lang.String",
                            "-m", "toString",
                            "-s", "/invalid/path");

            assertEquals(1, exitCode);
            String output = logContent.toString();
            assertTrue(output.contains("Could not scan folder"));
        } finally {
            resetLogCapture();
        }
    }

    @Test
    void shouldHandleVerboseMode() throws IOException {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        copyTestClassFile(classesDir);

        try (ByteArrayOutputStream logContent = setupLogCapture()) {
            int exitCode = new CommandLine(new JavaMethodFinder())
                    .execute("-c", "java.lang.String", "-m", "toString", "-s", classesDir.toString(), "-v");

            assertEquals(0, exitCode);
            String output = logContent.toString();
            assertTrue(output.contains("DEBUG"));
        } finally {
            resetLogCapture();
        }
    }

    private void copyTestClassFile(Path targetDir) {
        try {
            URL resource = getClass().getClassLoader().getResource("com/example/TestClass.class");
            Path sourceFile = Paths.get(resource.toURI());
            Path targetFile = targetDir.resolve("TestClass.class");
            Files.copy(sourceFile, targetFile);
        } catch (URISyntaxException | IOException ignore) {
        }
    }
}
