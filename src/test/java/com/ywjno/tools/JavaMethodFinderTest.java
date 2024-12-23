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

    @Test
    void shouldFindMethodCalls() throws IOException {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        copyTestClassFile(classesDir);

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(outContent));
            int exitCode = new CommandLine(new JavaMethodFinder())
                    .execute("-c", "java.lang.String", "-m", "toString", "-s", classesDir.toString());

            assertEquals(0, exitCode);
            String output = outContent.toString();
            assertTrue(output.contains("java.lang.String#toString"));
            assertTrue(output.contains("- com.example.TestClass#testMethod (L8)"));
            assertTrue(output.contains("- com.example.TestClass#testMethod (L10)"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void shouldHandleInvalidClassPath() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(outContent));
            int exitCode = new CommandLine(new JavaMethodFinder())
                    .execute(
                            "-c", "java.lang.String",
                            "-m", "toString",
                            "-s", "/invalid/path");

            assertEquals(1, exitCode);
            String output = outContent.toString();
            assertTrue(output.contains("Could not scan folder"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void shouldHandleVerboseMode() throws IOException {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        copyTestClassFile(classesDir);

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        try {
            int exitCode = new CommandLine(new JavaMethodFinder())
                    .execute("-c", "java.lang.String", "-m", "toString", "-s", classesDir.toString(), "-v");

            assertEquals(0, exitCode);
            String output = outContent.toString();
            assertTrue(output.contains("[DEBUG]"));
        } finally {
            System.setOut(System.out);
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
