# Tascam MX Preset Tool

A command-line tool to list and recall presets on Tascam MX-8A and DCP series mixers (MM-4D/IN, MM-4D/OUT) over Ethernet.

## Features

- **List presets** — Display all saved presets with current preset indicator
- **Recall by name** — Load a preset by its name

## Requirements

- Java 21 or later
- Network access to the Tascam mixer (TCP port 54726)

## Usage

```bash
# Build
./gradlew build

# List all presets
./gradlew run --args="list --host 192.168.1.100"

# Recall a preset by name
./gradlew run --args="recall-by-name 'My Preset' --host 192.168.1.100"
```

If the mixer has a password configured, the tool will prompt for it on stdin.

## Documentation

- [Protocol Specification](docs/protocol.md) — Details of the Tascam MX-DCP preset protocol

## License

MIT License — see [LICENSE](LICENSE) for details.
