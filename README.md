# FTX-1 Hamlib

Hamlib-compatible emulator for Yaesu FTX-1 using the Java ftx1-cat library. 
Visit https://github.com/KJ5HST/FTX-1_CAT for more details on ftx1-cat.

## Overview

FTX-1 Hamlib provides a rigctld-compatible interface for the Yaesu FTX-1 transceiver. It can run as:
- A graphical application with internationalization support (9 languages)
- An interactive command-line tool (like rigctl)
- A TCP server (like rigctld) for network control

This allows any Hamlib-compatible application to control the FTX-1 via the standard rigctld protocol.

## Requirements

- Java 17 or later
- FTX-1 connected via USB serial (CP2102 adapter)

## Building

```bash
mvn clean package
```

This creates an uber-jar at `target/ftx1-hamlib-1.0.3.jar` with all dependencies included.

## Usage

### GUI Mode (Default)

```bash
java -jar ftx1-hamlib-1.0.3.jar
```

Or explicitly:
```bash
java -jar ftx1-hamlib-1.0.3.jar --gui
```

The GUI provides:
- Connection management (serial port selection, baud rate, refresh button)
- Hamlib emulator control (start/stop TCP server)
- Real-time radio status (frequency, mode, VFO, PTT, power, S-meter)
- Command input with quick-command dropdown
- Quick controls (mode selector, PTT button, lock toggle)
- Response log
- **Communications Monitor** - Real-time view of CAT command traffic (TX/RX)
- **Internationalization** - Support for 9 languages (see below)

#### Communications Monitor

The GUI includes a dedicated "Comm Monitor" tab that displays all CAT communications between FTX-1 Hamlib and the FTX-1:

- **TX** - Commands sent to the radio
- **RX** - Responses received from the radio
- Optional timestamps (HH:mm:ss.SSS format)
- Terminal-style display with dark background
- Enable/disable monitoring via checkbox or View menu

Example output:
```
11:19:45.123 [TX] FA
11:19:45.145 [RX] FA014074000
11:19:45.200 [TX] MD0
11:19:45.222 [RX] MD01
```

This is useful for debugging, learning CAT commands, or monitoring radio activity.

#### Internationalization (i18n)

The GUI supports 9 languages, sorted by amateur radio activity/popularity:

| Language | Native Name |
|----------|-------------|
| English | English |
| Japanese | 日本語 |
| German | Deutsch |
| Russian | Русский |
| Spanish | Español |
| Italian | Italiano |
| French | Français |
| Arabic | العربية |
| Hebrew | עברית |

To change the language:
1. Go to **Settings → Language**
2. Select your preferred language
3. Restart the application

Select **Default (System)** to use your operating system's language setting.

### Interactive Mode

```bash
java -jar ftx1-hamlib-1.0.3.jar -r /dev/cu.SLAB_USBtoUART
```

This opens an interactive prompt where you can enter rigctl commands directly.

### Daemon Mode

```bash
java -jar ftx1-hamlib-1.0.3.jar -r /dev/cu.SLAB_USBtoUART -t 4532
```

This starts a TCP server on port 4532 that accepts rigctld protocol connections.

### Command-Line Options

| Option | Description |
|--------|-------------|
| `-g, --gui` | Launch graphical interface |
| `-r, --rig-file PORT` | Serial port (required for CLI) |
| `-s, --serial-speed BAUD` | Baud rate (default: 38400) |
| `-t, --port PORT` | TCP port for daemon mode (default: 4532) |
| `-T, --listen-addr ADDR` | Listen address (default: 0.0.0.0) |
| `-v, --verbose` | Verbose output |
| `-h, --help` | Show help |
| `-V, --version` | Show version |

## Supported Commands

| Command | Description |
|---------|-------------|
| `f, get_freq` | Get frequency (Hz) |
| `F, set_freq FREQ` | Set frequency (Hz) |
| `m, get_mode` | Get mode and passband |
| `M, set_mode MODE PB` | Set mode and passband |
| `t, get_ptt` | Get PTT status (0/1) |
| `T, set_ptt 0\|1` | Set PTT |
| `v, get_vfo` | Get current VFO |
| `V, set_vfo VFOA\|VFOB` | Set VFO |
| `s, get_split_vfo` | Get split status |
| `S, set_split_vfo 0\|1` | Set split |
| `l, get_level LEVEL` | Get level (RFPOWER, AF, SQL, STRENGTH, SWR) |
| `L, set_level LVL VAL` | Set level |
| `u, get_func FUNC` | Get function (LOCK) |
| `U, set_func FUNC 0\|1` | Set function |
| `w, send_cmd CMD` | Send raw CAT command |
| `_, get_info` | Get rig info |
| `1, dump_caps` | Dump capabilities |
| `q, quit` | Exit |

## Examples

### Using with rigctl client

Start the daemon:
```bash
java -jar ftx1-hamlib-1.0.3.jar -r /dev/cu.SLAB_USBtoUART -t 4532
```

Connect with rigctl:
```bash
rigctl -m 2 -r localhost:4532
```

### Interactive session

```
$ java -jar ftx1-hamlib-1.0.3.jar -r /dev/cu.SLAB_USBtoUART
FTX-1 Hamlib 1.0.3 - Interactive Mode
Type 'help' for commands, 'quit' to exit

Rig command: f
14074000

Rig command: m
USB
0

Rig command: l STRENGTH
-54

Rig command: quit
```

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    FTX-1 Hamlib                         │
├─────────────────────────────────────────────────────────┤
│  FTX1Hamlib          - Main class, CLI parsing          │
│  RigctldServer       - TCP server for emulator mode     │
│  RigctlCommandHandler - rigctl protocol implementation  │
├─────────────────────────────────────────────────────────┤
│                    ftx1-cat library                     │
│  FTX1                - High-level radio control API     │
│  CatProtocol         - Low-level CAT command handling   │
├─────────────────────────────────────────────────────────┤
│                    jSerialComm                          │
│  Serial port communication                              │
└─────────────────────────────────────────────────────────┘
```

## Head Type Support

FTX-1 Hamlib automatically detects the connected configuration using a two-stage process:

### Stage 1: PC Command Format
The PC (Power Control) command response identifies the head type:
- **PC1xxx** - Field Head
- **PC2xxx** - Optima/SPA-1 (5-100W with internal antenna tuner)

### Stage 2: Power Probe (Field Head Only)
For Field Head, the library probes the actual power source by attempting to set 8W:
- If the radio **accepts** 8W → **12V power** (0.5-10W range)
- If the radio **rejects** 8W → **Battery power** (0.5-6W range)

This works because the FTX-1 enforces hardware power limits based on the actual power source.
The probe saves and restores your original power setting.

### Detected Configurations

| Configuration | Power Range | Tuner |
|---------------|-------------|-------|
| Field Head (Battery) | 0.5-6W | No |
| Field Head (12V) | 0.5-10W | No |
| Optima/SPA-1 | 5-100W | Yes |

Note: The radio ID is always 0840 for all FTX-1 configurations.

The `dump_caps` command shows the detected head type and power limits.

## Testing

### Shell Script Test Suite

Run the comprehensive test suite using the shell script:

```bash
# Basic test
./test-ftx1-hamlib.sh /dev/cu.SLAB_USBtoUART

# Verbose output
./test-ftx1-hamlib.sh /dev/cu.SLAB_USBtoUART -v

# Full test (all modes and bands)
./test-ftx1-hamlib.sh /dev/cu.SLAB_USBtoUART -f

# Enable PTT tests (CAUTION: requires dummy load!)
./test-ftx1-hamlib.sh /dev/cu.SLAB_USBtoUART --tx
```

### JUnit Test Suite

Run the JUnit test suite with Maven:

```bash
# Set serial port environment variable
export FTX1_PORT=/dev/cu.SLAB_USBtoUART

# Run tests
mvn test
```

The test suite includes:

| Category | Tests |
|----------|-------|
| **Info** | get_info, dump_caps, head type detection |
| **Frequency** | Get/set on all bands (160m-70cm) |
| **Mode** | All modes (USB, LSB, CW, AM, FM, RTTY, PKTUSB, etc.) |
| **VFO** | VFO selection and switching |
| **Split** | Split enable/disable |
| **Levels** | RFPOWER, AF, SQL, STRENGTH, SWR, ALC, COMP |
| **Functions** | LOCK, TUNER (SPA-1 only) |
| **PTT** | Get/set PTT status (disabled by default) |
| **Raw Commands** | Direct CAT command passthrough |
| **Error Handling** | Invalid commands, missing args |

## License

Copyright (c) 2025 Terrell Deppe (KJ5HST)

This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License.
