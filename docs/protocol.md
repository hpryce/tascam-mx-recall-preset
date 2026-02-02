# Tascam MX-DCP Protocol — Preset Extension

This document describes the **preset recall protocol** for Tascam MX-8A and DCP series devices (MM-4D/IN, MM-4D/OUT). This extends the [official protocol specification](https://www.tascam.eu/en/docs/MX_DCP_Protocol_v100.pdf) which does not document preset commands.

## Connection

- **Port:** TCP 54726 (fixed)
- **Protocol:** Text-based, ASCII with UTF-8 for names
- **Line endings:** CR+LF (`\r\n` / `0x0D 0x0A`)

### Login Sequence

```
Client: \r\n
Server: Enter Password\r\n
Client: <password>\r\n          (empty if no password set)
Server: Login Successful\r\n
```

If login fails or another client is connected:
```
Server: Another User Already Connected\r\n
```

### Timeout
The connection will timeout after 3 minutes of inactivity. Send periodic GET requests (e.g., `GET DEVICE/NAME`) to keep the session alive.

---

## Command Format

```
<COMMAND_TYPE> <PARAMETER1> <PARAMETER2> ... <PARAMETER_N>\r\n
```

- **Command types:** `SET`, `GET`, `NOTIFY`, `METER`
- **Delimiter:** Single space (`0x20`)
- **Parameters:** `KEY:VALUE` pairs (for SET/NOTIFY) or just `KEY` (for GET)
- **CID parameter:** Optional command ID for request/response matching

### Response Format

Success:
```
OK <COMMAND_TYPE> <PARAMETER1:VALUE1> ... CID:<id> \r\n
```

Failure:
```
OK <COMMAND_TYPE> <PARAMETER1>:ERRX CID:<id> \r\n
```

Error codes:
- `ERR1` — Invalid key
- `ERR2` — Invalid value
- `ERR5` — Empty preset slot (no data)

Unrecognized command:
```
NG <ORIGINAL_COMMAND>\r\n
```

---

## Preset Commands

### Get Current Preset

```
GET PRESET/CUR PRESET/NAME CID:<id>\r\n
```

Response:
```
OK GET PRESET/CUR:1 PRESET/NAME:"My Preset" CID:<id> \r\n
```

| Key | Value | Description |
|-----|-------|-------------|
| `PRESET/CUR` | `1`-`50` | Currently active preset number |
| `PRESET/NAME` | `"<string>"` | Name of currently active preset (UTF-8 in quotes) |
| `PRESET/CMP` | `SAME` / `DIFF` | Whether current settings match saved preset |

### List Preset Slots

Query individual preset slots (50 total).

**Example — Populated slot:**

Request:
```
GET PRESET/1/NAME PRESET/1/LOCK PRESET/1/CLEARED CID:1001\r\n
```

Response:
```
OK GET PRESET/1/NAME:"My Preset" PRESET/1/LOCK:OFF PRESET/1/CLEARED:FALSE CID:1001 \r\n
```

**Example — Empty slot:**

Request:
```
GET PRESET/3/NAME PRESET/3/LOCK PRESET/3/CLEARED CID:1002\r\n
```

Response:
```
OK GET PRESET/3/NAME:ERR5 PRESET/3/LOCK:ERR5 PRESET/3/CLEARED:TRUE CID:1002 \r\n
```

| Key | Value | Description |
|-----|-------|-------------|
| `PRESET/<n>/NAME` | `"<string>"` or `ERR5` | Preset name (ERR5 if empty) |
| `PRESET/<n>/LOCK` | `ON` / `OFF` / `ERR5` | Whether preset is locked |
| `PRESET/<n>/CLEARED` | `TRUE` / `FALSE` | Whether slot is empty |

### Recall (Load) Preset

Request:
```
SET PRESET/LOAD:1 CID:1003\r\n
```

Response:
```
OK SET CID:1003 \r\n
```

After a successful recall, the device also sends an asynchronous NOTIFY:
```
NOTIFY PRESET/CUR:1 PRESET/NAME:"Weekday Mass"\r\n
```

| Parameter | Value | Description |
|-----------|-------|-------------|
| `<n>` | `1`-`50` | Preset number to load |

---

## Example Session

```
# Login
Client: \r\n
Server: Enter Password\r\n
Client: \r\n
Server: Login Successful\r\n

# Get current preset
Client: GET PRESET/CUR PRESET/NAME CID:1001\r\n
Server: OK GET PRESET/CUR:2 PRESET/NAME:"Sunday Service" CID:1001 \r\n

# List preset 1
Client: GET PRESET/1/NAME PRESET/1/CLEARED CID:1002\r\n
Server: OK GET PRESET/1/NAME:"Weekday Mass" PRESET/1/CLEARED:FALSE CID:1002 \r\n

# Recall preset 1
Client: SET PRESET/LOAD:1 CID:1003\r\n
Server: OK SET CID:1003 \r\n
Server: NOTIFY PRESET/CUR:1 PRESET/NAME:"Weekday Mass"\r\n

# Verify
Client: GET PRESET/CUR CID:1004\r\n
Server: OK GET PRESET/CUR:1 CID:1004 \r\n
```

---

## Notes

- The device supports 50 preset slots
- Multiple parameters can be combined in a single GET request (space-separated)
- Maximum command length is 1024 bytes including CR+LF
- NOTIFY messages are sent asynchronously and may arrive between command/response pairs
- Only one TCP client can be connected at a time
