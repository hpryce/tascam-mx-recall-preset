# Tascam MX Preset Tool

A command-line tool to list and recall presets on Tascam MX-8A and DCP series mixers (MM-4D/IN, MM-4D/OUT) over Ethernet.

## Features

- **List presets** — Display all saved presets with current preset indicator
- **Recall by name** — Load a preset by its name

## Building

### With Java (JVM)

Requires Java 21 or later.

```bash
./gradlew build
./gradlew run --args="list --host 192.168.1.100"
```

### Native image (GraalVM)

Requires GraalVM 21 or later with `native-image` installed.

```bash
./gradlew nativeCompile
./app/build/native/nativeCompile/tascam-preset list --host 192.168.1.100
```

The native image is a standalone binary with no JVM dependency — ideal for Raspberry Pi or embedded systems.

## Usage

```bash
# List all presets
tascam-preset list --host 192.168.1.100

# With custom port
tascam-preset list --host 192.168.1.100 -p 54726
```

If the mixer has a password configured, the tool will prompt for it on stdin.

## Documentation

- [Protocol Specification](docs/protocol.md) — Details of the Tascam MX-DCP preset protocol

## License

MIT License — see [LICENSE](LICENSE) for details.
