# Java Method Finder (JMF)

A command-line tool to find method invocations in Java bytecode. JMF helps you locate where specific methods are being
called across your Java project.

## Features

- Find all invocations of a specific method in a given class
- Support scanning compiled Java class files
- Provide detailed output with line numbers
- Optional verbose mode for debugging
- Easy-to-use command-line interface

## Prerequisites

- Java 8 or higher
- Maven

## Installation

1. Clone the repository:

```bash
git clone https://github.com/ywjno/javamethodfinder.git
```

2. Build the project:

```bash
mvn clean package
```

This will create an executable jar file in the `target` directory named `jmf.jar`.

## Usage

Basic usage:

```bash
java -jar jmf.jar -c com.example.TargetClass -m targetMethod
```

### Command-line Options

| Option          | Description                                                                  |
|-----------------|------------------------------------------------------------------------------|
| `-c, --class`   | The fully qualified name of the target class to find method calls (required) |
| `-m, --method`  | The name of the target method to find its invocations (required)             |
| `-s, --scan`    | The root directory to scan for class files (default: ./target/classes)       |
| `-v, --verbose` | Enable verbose output for debugging                                          |
| `-h, --help`    | Show this help message and exit                                              |

### Examples

Find all calls to `targetMethod()` method in `com.example.TargetClass`:

```bash
java -jar jmf.jar -c com.example.TargetClass -m targetMethod
```

Scan a specific directory with verbose output:

```bash
java -jar jmf.jar -c com.example.TargetClass -m targetMethod -s ./build/classes -v
```

### Output Format

The tool outputs results in the following format:

```
com.example.TargetClass#targetMethod
 - com.example.CallerClass#callerMethod (L123)
 - com.example.AnotherClass#someMethod (L45)
```

Each line shows:

- The calling class and method
- The line number where the call occurs (prefixed with 'L')

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
