# Tascam MX Preset Tool

A command-line tool to list and recall presets on Tascam MX-8A and DCP series mixers (MM-4D/IN, MM-4D/OUT) over Ethernet.

## Features

- **List presets** — Display all saved presets with current preset indicator
- **Recall by name** — Load a preset by its name
- **Config file** — Store host, port, and password in `~/.tascam-preset.conf`
- **Debug mode** — View raw protocol messages

## Building

### Executable JAR

Requires Java 21 or later.

```bash
./gradlew shadowJar
java -jar app/build/libs/tascam-preset.jar list --host 192.168.1.100
```

### Run directly with Gradle

```bash
./gradlew run --args="list --host 192.168.1.100"
```

### Native image (GraalVM)

Requires GraalVM 21 or later with `native-image` installed.

```bash
./gradlew nativeCompile --no-configuration-cache
./app/build/native/nativeCompile/tascam-preset list --host 192.168.1.100
```

The native image is a standalone binary with no JVM dependency — ideal for Raspberry Pi or embedded systems.

## Usage

```bash
# List all presets
tascam-preset list --host 192.168.1.100

# Recall a preset by name
tascam-preset recall --host 192.168.1.100 "My Preset"

# Recall with custom verification wait (seconds, default 5)
tascam-preset recall --host 192.168.1.100 -w 2 "My Preset"

# Recall without verification (faster, but no guarantee preset loaded)
tascam-preset recall --host 192.168.1.100 -w 0 "My Preset"

# With custom port
tascam-preset list --host 192.168.1.100 -p 54726

# With custom read timeout (seconds, default 10)
tascam-preset list --host 192.168.1.100 -t 30

# Enable debug output (raw protocol messages)
tascam-preset --debug list --host 192.168.1.100
```

If the mixer has a password configured, the tool will prompt for it on stdin (unless set in config file).

## Configuration

Create `~/.tascam-preset.conf` to set defaults:

```properties
host=192.168.1.100
port=54726
password=secret
```

All fields are optional. Command-line arguments override config file values.

### Output Format

The `list` command shows all presets, with the current preset marked with `*`:

```
* 1: "Default Mix"
  2: "Quiet Mode"
  3: "Backup Config" [locked]
```

## Documentation

- [Protocol Specification](docs/protocol.md) — Details of the Tascam MX-DCP preset protocol

## License

MIT License — see [LICENSE](LICENSE) for details.
