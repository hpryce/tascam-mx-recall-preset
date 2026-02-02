# Tascam MX Preset Tool

A command-line tool to list and recall presets on Tascam MX-8A and DCP series mixers (MM-4D/IN, MM-4D/OUT) over Ethernet.

## Features

- **List presets** — Display all saved presets with current preset indicator
- **Recall by name** — Load a preset by its name

## Installation

### Pre-built binaries

Download the latest native binary from [Releases](https://github.com/hpryce/tascam-mx-recall-preset/releases):

- `tascam-preset-linux-x86_64` — For x86_64 Linux
- `tascam-preset-linux-aarch64` — For Raspberry Pi 4 and other ARM64 Linux

```bash
# Download and make executable
chmod +x tascam-preset-linux-aarch64
./tascam-preset-linux-aarch64 list --host 192.168.1.100
```

### Build from source

Requires Java 21 or later.

```bash
./gradlew build
./gradlew run --args="list --host 192.168.1.100"
```

### Build native image locally

Requires GraalVM 21 or later with `native-image` installed.

```bash
./gradlew nativeCompile
./app/build/native/nativeCompile/tascam-preset list --host 192.168.1.100
```

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
