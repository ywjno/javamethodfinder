package com.ywjno.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "jmf", description = "Java Method Finder", mixinStandardHelpOptions = true)
public class JavaMethodFinder implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(JavaMethodFinder.class);

    private final List<String> results;

    @CommandLine.Option(
            names = {"-c", "--class"},
            description = "The fully qualified name of the target class to find method calls",
            required = true)
    private String targetClassName;

    @CommandLine.Option(
            names = {"-m", "--method"},
            description = "The name of the target method to find its invocations",
            required = true)
    private String targetMethodName;

    @CommandLine.Option(
            names = {"-s", "--scan"},
            description = "The root directory to scan for class files (default: ./target/classes)",
            defaultValue = "./target/classes")
    private String scanFolder;

    @CommandLine.Option(
            names = {"-v", "--verbose"},
            description = "Enable verbose output for debugging",
            defaultValue = "false")
    private boolean verbose;

    public JavaMethodFinder() {
        results = new ArrayList<>();
    }

    private void logDebug(String message) {
        if (verbose) {
            logger.debug(message);
        }
    }

    private List<String> getResults() {
        return results;
    }

    @Override
    public Integer call() throws Exception {
        return scanFolder(this::printResults);
    }

    private int scanFolder(Runnable printResults) {
        Path targetFolder = Paths.get(scanFolder).toAbsolutePath();
        try {
            logDebug("Start scanning folder: " + targetFolder.normalize());
            Files.walkFileTree(targetFolder, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toFile().getName().endsWith(".class")) {
                        logDebug("Analyzing class file: " + file.getFileName());
                        analyzeClass(file);
                    }
                    return super.visitFile(file, attrs);
                }
            });
            printResults.run();
            return 0;
        } catch (IOException e) {
            logger.error("Could not scan folder: {}", targetFolder.normalize());
            return 1;
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage());
            return 1;
        }
    }

    private void analyzeClass(Path classFile) {
        if (!classFile.toFile().canRead()) {
            throw new IllegalArgumentException(
                    "Could not read class file: " + classFile.toAbsolutePath().normalize());
        }

        try (InputStream is = Files.newInputStream(classFile)) {
            ClassReader reader = new ClassReader(is);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9) {
                private String currentClassName;

                @Override
                public void visit(
                        int version, int access, String name, String signature, String superName, String[] interfaces) {
                    this.currentClassName = name;
                    logDebug("Visiting class: " + name);
                }

                @Override
                public MethodVisitor visitMethod(
                        int access, String name, String descriptor, String signature, String[] exceptions) {
                    String currentMethodName = name;
                    logDebug("Visiting method: " + currentClassName + "#" + name);

                    return new MethodVisitor(Opcodes.ASM9) {
                        private int currentLine;

                        @Override
                        public void visitLineNumber(int line, Label start) {
                            this.currentLine = line;
                        }

                        @Override
                        public void visitMethodInsn(
                                int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            String jvmClassName = targetClassName.replace('.', '/');
                            if (!currentClassName.equals(jvmClassName)
                                    && owner.equals(jvmClassName)
                                    && name.equals(targetMethodName)) {
                                String result = String.format(
                                        "%s#%s (L%d)",
                                        currentClassName.replace('/', '.'), currentMethodName, currentLine);
                                logDebug("Found method call: " + result);
                                getResults().add(result);
                            }
                        }
                    };
                }
            };

            reader.accept(visitor, 0);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Could not read class file: " + classFile.toAbsolutePath().normalize(), e);
        }
    }

    private void printResults() {
        logger.info("{}#{}", targetClassName, targetMethodName);
        if (!results.isEmpty()) {
            for (String result : getResults()) {
                logger.info(" - {}", result);
            }
        } else {
            logger.info("No results");
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JavaMethodFinder()).execute(args);
        System.exit(exitCode);
    }
}
