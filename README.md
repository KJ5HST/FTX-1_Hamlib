# FTX-1 Hamlib

Hamlib-compatible emulator for Yaesu FTX-1 using the Java ftx1-cat library.
Visit https://github.com/KJ5HST/FTX-1_CAT for more details on ftx1-cat.

## Acknowledgments

Special thanks to **Jeremy Miller (KO4SSD)** for his critical discoveries in
[Hamlib PR #1826](https://github.com/Hamlib/Hamlib/pull/1826):

- **RIT/XIT**: Standard RT/XT commands return `?` on FTX-1 firmware. Jeremy discovered
  that RC (Receiver Clarifier) and TC (Transmit Clarifier) commands work correctly.
- **Tuning Steps**: Mode-specific dial steps via EX0306 extended menu command.

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

This creates an uber-jar at `target/ftx1-hamlib-1.2.0.jar` with all dependencies included.

## Usage

### GUI Mode (Default)

```bash
java -jar ftx1-hamlib-1.2.0.jar
```

Or explicitly:
```bash
java -jar ftx1-hamlib-1.2.0.jar --gui
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
java -jar ftx1-hamlib-1.2.0.jar -r /dev/cu.SLAB_USBtoUART
```

This opens an interactive prompt where you can enter rigctl commands directly.

### Daemon Mode

```bash
java -jar ftx1-hamlib-1.2.0.jar -r /dev/cu.SLAB_USBtoUART -t 4532
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

### Core Commands

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
| `j, get_rit` | Get RIT offset (Hz) - uses RC command |
| `J, set_rit OFFSET` | Set RIT offset (Hz) - uses RC command |
| `z, get_xit` | Get XIT offset (Hz) - uses TC command |
| `Z, set_xit OFFSET` | Set XIT offset (Hz) - uses TC command |
| `s, get_split_vfo` | Get split status |
| `S, set_split_vfo 0\|1` | Set split |

### Memory Commands

| Command | Description |
|---------|-------------|
| `e, get_mem` | Get memory channel number |
| `E, set_mem CH` | Set memory channel number |
| `h, get_channel CH` | Get channel data |

### CTCSS/DCS Commands

| Command | Description |
|---------|-------------|
| `c, get_ctcss_tone` | Get CTCSS tone (deci-Hz) |
| `C, set_ctcss_tone TONE` | Set CTCSS tone (deci-Hz) |
| `d, get_dcs_code` | Get DCS code |
| `D, set_dcs_code CODE` | Set DCS code |

### Tuning Step

| Command | Description |
|---------|-------------|
| `n, get_ts` | Get tuning step |
| `N, set_ts STEP` | Set tuning step |

### Levels (l/L commands)

| Level | Description |
|-------|-------------|
| `RFPOWER` | TX power (normalized 0-1) |
| `AF` | AF gain (normalized 0-1) |
| `RF` | RF gain (normalized 0-1) |
| `SQL` | Squelch (normalized 0-1) |
| `STRENGTH` | S-meter reading (dB) |
| `SWR` | SWR meter |
| `ALC` | ALC meter |
| `COMP` | Compression meter |
| `MICGAIN` | Microphone gain (normalized 0-1) |
| `KEYSPD` | CW keyer speed (4-60 WPM) |
| `VOXGAIN` | VOX gain (normalized 0-1) |
| `VOXDELAY` | VOX delay (ms) |
| `NR` | Noise reduction level (normalized 0-1) |
| `NB` | Noise blanker level (normalized 0-1) |
| `NOTCHF` | Manual notch frequency (Hz) |
| `AGC` | AGC mode |
| `ATT` | Attenuator (0 or 12 dB) |
| `PREAMP` | Preamp (0=IPO, 10=AMP1, 20=AMP2) |
| `MONITOR_GAIN` | Monitor level (normalized 0-1) |
| `BKINDL` | Break-in delay (tenths of seconds) |

### Functions (u/U commands)

| Function | Description |
|----------|-------------|
| `LOCK` | Dial lock |
| `COMP` | Speech processor on/off |
| `VOX` | VOX on/off |
| `TONE` | CTCSS encode on/off |
| `TSQL` | Tone squelch on/off |
| `NB` | Noise blanker on/off |
| `NR` | Noise reduction on/off |
| `ANF` | Auto notch filter on/off |
| `APF` | Audio peak filter on/off |
| `MON` | Monitor on/off |
| `RIT` | RIT on/off |
| `XIT` | XIT on/off |
| `SBKIN` | Semi break-in on/off |
| `FBKIN` | Full break-in (QSK) on/off |

### Other Commands

| Command | Description |
|---------|-------------|
| `w, send_cmd CMD` | Send raw CAT command |
| `_, get_info` | Get rig info |
| `1, dump_caps` | Dump capabilities |
| `q, quit` | Exit |

### Extended Commands (rigctld protocol)

| Command | Description |
|---------|-------------|
| `\dump_state` | Dump rig state (for WSJT-X) |
| `\get_powerstat` | Get power status |
| `\set_powerstat` | Set power status |
| `\get_vfo_info` | Get VFO details |
| `\get_rig_info` | Get rig info (key=value) |
| `\get_split_freq` | Get split TX frequency |
| `\set_split_freq` | Set split TX frequency |
| `\get_split_mode` | Get split TX mode |
| `\set_split_mode` | Set split TX mode |
| `\send_morse MSG` | Send CW message |
| `\stop_morse` | Stop CW sending |
| `\wait_morse` | Wait for CW completion |
| `\send_voice_mem N` | Play voice memory (1-5) |
| `\halt` | Emergency stop (PTT off) |

## Examples

### Using with rigctl client

Start the daemon:
```bash
java -jar ftx1-hamlib-1.2.0.jar -r /dev/cu.SLAB_USBtoUART -t 4532
```

Connect with rigctl:
```bash
rigctl -m 2 -r localhost:4532
```

### Interactive session

```
$ java -jar ftx1-hamlib-1.2.0.jar -r /dev/cu.SLAB_USBtoUART
FTX1-Hamlib 1.2.0 - Interactive Mode
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

## Audio Streaming for Remote WSJT-X

FTX-1 Hamlib includes bidirectional audio streaming support, enabling remote operation with WSJT-X and other digital mode applications. This allows you to run WSJT-X on a computer that is not physically connected to the FTX-1.

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              RADIO SIDE                                     │
│  ┌─────────────┐    USB     ┌─────────────────────────────────────────┐    │
│  │   FTX-1     │◄──────────►│           ftx1-hamlib Server            │    │
│  │   Radio     │  Serial+   │  ┌─────────────┐  ┌──────────────────┐  │    │
│  └─────────────┘   Audio    │  │ RigctldServer│  │AudioStreamServer │  │    │
│                             │  │  (TCP:4532) │  │   (TCP:4533)     │  │    │
│                             │  └─────────────┘  └──────────────────┘  │    │
│                             └───────────┬───────────────┬─────────────┘    │
└─────────────────────────────────────────┼───────────────┼──────────────────┘
                                          │               │
                                     CAT Control      Audio Stream
                                          │               │
┌─────────────────────────────────────────┼───────────────┼──────────────────┐
│                              CLIENT SIDE                │                   │
│                             └───────────┴───────────────┘                   │
│                                         │                                   │
│                             ┌───────────▼───────────┐                       │
│                             │  ftx1-hamlib Client   │                       │
│                             │   (AudioStreamClient) │                       │
│                             └───────────┬───────────┘                       │
│                                         │                                   │
│                             ┌───────────▼───────────┐                       │
│                             │  Virtual Audio Device │                       │
│                             │ (BlackHole/VB-Cable)  │                       │
│                             └───────────┬───────────┘                       │
│                                         │                                   │
│                             ┌───────────▼───────────┐                       │
│                             │       WSJT-X          │                       │
│                             │  (Hamlib NET rigctl)  │                       │
│                             └───────────────────────┘                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Server Setup (Radio Side)

1. **Connect FTX-1** via USB to the server computer
2. **Launch ftx1-hamlib GUI**:
   ```bash
   java -jar ftx1-hamlib-1.2.0.jar
   ```
3. **Connect to radio** using the Connection panel
4. **Start Hamlib** on the Hamlib panel (default port 4532)
5. **Configure Audio Streaming** tab:
   - Select FTX-1 USB audio device for Capture (RX)
   - Select FTX-1 USB audio device for Playback (TX)
   - Click "Start Audio Server" (default port 4533)

### Client Setup (Remote WSJT-X Side)

#### Step 1: Install Virtual Audio Device

| Platform | Software | Installation |
|----------|----------|--------------|
| **macOS** | BlackHole | `brew install blackhole-2ch` |
| **Windows** | VB-Cable | Download from [vb-audio.com/Cable](https://vb-audio.com/Cable/) |
| **Linux** | PulseAudio | `pactl load-module module-null-sink sink_name=virtual` |

#### Step 2: Run Audio Client

```bash
# Basic usage
java -cp ftx1-hamlib-1.2.0.jar com.yaesu.hamlib.audio.client.HamlibAudioClient <server-ip>

# With explicit port
java -cp ftx1-hamlib-1.2.0.jar com.yaesu.hamlib.audio.client.HamlibAudioClient -h 192.168.1.100 -p 4533

# List available audio devices
java -cp ftx1-hamlib-1.2.0.jar com.yaesu.hamlib.audio.client.HamlibAudioClient --list

# Show setup instructions
java -cp ftx1-hamlib-1.2.0.jar com.yaesu.hamlib.audio.client.HamlibAudioClient --instructions
```

#### Audio Client Options

| Option | Description |
|--------|-------------|
| `-h, --host HOST` | Server hostname or IP |
| `-p, --port PORT` | Server audio port (default: 4533) |
| `-c, --capture NAME` | Capture device name |
| `-o, --playback NAME` | Playback device name |
| `-l, --list` | List available audio devices |
| `-i, --instructions` | Show setup instructions |
| `-d, --diagnostic` | Run diagnostic report |
| `--help` | Show help |

#### Step 3: Configure WSJT-X

1. **Audio Settings** (File → Settings → Audio):
   - Soundcard Input: `BlackHole 2ch` (macOS) / `CABLE Output` (Windows)
   - Soundcard Output: `BlackHole 2ch` (macOS) / `CABLE Input` (Windows)

2. **Radio Settings** (File → Settings → Radio):
   - Rig: `Hamlib NET rigctl`
   - Network Server: `<server-ip>:4532` (e.g., `192.168.1.100:4532`)
   - Click "Test CAT" to verify connection

### Audio Protocol Specifications

| Parameter | Value |
|-----------|-------|
| Sample Rate | 48000 Hz (WSJT-X compatible) |
| Bit Depth | 16-bit signed PCM |
| Channels | Mono |
| Frame Size | 20ms (960 samples) |
| Network Port | 4533 (default) |
| Target Latency | < 200ms |

### Troubleshooting

**No virtual audio devices found:**
- Run `--diagnostic` to see available devices
- Ensure virtual audio software is installed and running
- On macOS, check System Settings → Sound for BlackHole

**High latency or dropouts:**
- Check network connection stability
- Reduce network congestion
- The buffer automatically adjusts (target: 100ms)

**WSJT-X not decoding:**
- Verify audio levels (not too high/low)
- Check that correct virtual device is selected in WSJT-X
- Ensure audio client shows "Streaming" status

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    FTX-1 Hamlib                         │
├─────────────────────────────────────────────────────────┤
│  FTX1Hamlib          - Main class, CLI parsing          │
│  RigctldServer       - TCP server for emulator mode     │
│  RigctlCommandHandler - rigctl protocol implementation  │
│  AudioStreamServer   - TCP server for audio streaming   │
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
| **RIT/XIT** | Clarifier control using RC/TC commands |
| **Split** | Split enable/disable |
| **Memory** | Channel get/set, channel read |
| **CTCSS/DCS** | Tone and code get/set |
| **Levels** | RFPOWER, AF, RF, SQL, MICGAIN, KEYSPD, VOX, NR, NB, AGC, ATT, PREAMP |
| **Functions** | LOCK, COMP, VOX, TONE, TSQL, NB, NR, ANF, APF, MON, SBKIN, FBKIN |
| **PTT** | Get/set PTT status (disabled by default) |
| **Raw Commands** | Direct CAT command passthrough |
| **Error Handling** | Invalid commands, missing args |

## VARA and Winlink Express Setup

FTX-1 Hamlib can be used with VARA (HF or FM) and Winlink Express for digital messaging.

### Overview

Three components need configuration:
1. **CAT Control** → FTX-1 Hamlib (handles frequency/mode/PTT)
2. **Audio** → FTX-1's USB audio interface (separate from CAT)
3. **VARA** → Connects to both

### Step 1: Start FTX-1 Hamlib

```bash
java -jar ftx1-hamlib-1.2.0.jar -r /dev/cu.SLAB_USBtoUART -t 4532
```

Or use GUI mode and click "Start" on the Hamlib emulator.

### Step 2: Configure VARA

In VARA HF (or VARA FM):

1. **Settings → Soundcard**
   - **Input**: Select `USB Audio CODEC` (the FTX-1's built-in sound interface)
   - **Output**: Select `USB Audio CODEC` (same device)

2. **Settings → PTT**
   - Select **CAT** for PTT method
   - Configure as:
     - **Host**: `127.0.0.1`
     - **Port**: `4532`
   - Some VARA versions have direct rigctld support; if not, you may need to use Omnirig as a bridge

### Step 3: Configure Winlink Express

1. Open Winlink Express
2. Go to **VARA HF Winlink** session (or VARA FM)
3. **Settings → Radio Setup**: For PTT, you can let VARA handle it (recommended) or configure Hamlib here as well

### Step 4: Radio Settings

On your FTX-1:
- Set mode to **DATA-U** (USB Data) or **USB** depending on preference
- Adjust **DATA MOD** menu settings for proper audio levels if needed
- Keep the radio's VOX **OFF** (use CAT PTT instead)

### Audio Level Tips

- **TX Audio**: Start with VARA's drive level at 50%, adjust until ALC barely moves
- **RX Audio**: Adjust FTX-1's AF gain or VARA's input level for proper decode

### Alternative: VOX PTT

If CAT PTT gives you trouble, you can use VOX:
1. Enable VOX on the FTX-1 (`U VOX 1` via CAT or menu)
2. Set VARA PTT to "None" or "VOX"
3. This is simpler but slightly less reliable than CAT PTT

### VARA Troubleshooting

| Issue | Solution |
|-------|----------|
| No TX audio | Check USB audio device is selected, not your computer's speakers |
| PTT not working | Verify FTX-1 Hamlib is running and test with `T 1` command |
| VARA not decoding | Check FTX-1 is in DATA-U or USB mode, verify audio levels |
| Wrong audio device | FTX-1 shows as "USB Audio CODEC" on most systems |

## License

Copyright (c) 2025 Terrell Deppe (KJ5HST)

This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License.
