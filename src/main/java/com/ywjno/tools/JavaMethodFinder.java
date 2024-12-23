package com.ywjno.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private final Queue<String> results = new ConcurrentLinkedQueue<>();

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

    private void logDebug(String message) {
        if (verbose) {
            logger.debug(message);
        }
    }

    @Override
    public Integer call() throws Exception {
        return scanFolder(this::printResults);
    }

    private int scanFolder(Runnable printResults) {
        Path targetFolder = Paths.get(scanFolder).toAbsolutePath();

        ExecutorService executor =
                Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        try (Stream<Path> folder = Files.walk(targetFolder)) {
            List<Future<?>> futures = folder.filter(
                            path -> path.toFile().getName().endsWith(".class"))
                    .map(file -> executor.submit(() -> {
                        try {
                            analyzeClass(file);
                        } catch (IllegalArgumentException e) {
                            logger.error(e.getMessage());
                        }
                    }))
                    .collect(Collectors.toList());

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    logger.error("Error during analysis: {}", e.getMessage());
                    return 1;
                }
            }

            printResults.run();
            return 0;
        } catch (IOException e) {
            logger.error("Could not scan folder: {}", targetFolder.normalize());
            return 1;
        } finally {
            executor.shutdown();
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
                                results.add(result);
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
            results.stream().sorted().forEach(result -> logger.info(" - {}", result));
        } else {
            logger.info("No results");
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JavaMethodFinder()).execute(args);
        System.exit(exitCode);
    }
}
