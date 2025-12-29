# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FTX1-Hamlib - Hamlib-compatible rigctld emulator for the Yaesu FTX-1 transceiver. Provides both CLI and GUI interfaces for local and remote radio control, with bidirectional audio streaming for digital modes.

Author: Terrell Deppe (KJ5HST) | License: LGPL v2.1

## Build Commands

```bash
cd ftx1-hamlib
mvn clean package              # Build uber-JAR with all dependencies
java -jar target/ftx1-hamlib-1.2.0.jar --gui  # Launch GUI
java -jar target/ftx1-hamlib-1.2.0.jar -r /dev/cu.SLAB_USBtoUART -t 4532  # Daemon mode
```

**Dependencies**: ftx1-cat, net-audio

## Architecture

### Package Structure

```
com.yaesu.hamlib
├── HamlibFTX1.java           # CLI entry point
├── RigctlCommandHandler.java # Hamlib command translation
├── audio/client/
│   └── HamlibAudioClient.java # CLI audio client wrapper
├── client/
│   └── RigctlClient.java     # Client for remote rigctld
├── discovery/
│   ├── DiscoveryServer.java  # UDP broadcast for auto-discovery
│   └── DiscoveryClient.java  # Finds servers on LAN
├── gui/
│   ├── HamlibGUI.java        # Main Swing GUI
│   └── AudioControlPanel.java # Audio streaming controls
├── i18n/
│   └── Messages.java         # 9-language support
├── server/
│   └── RigctldServer.java    # TCP server (port 4532)
└── util/
    ├── FTX1AudioHelper.java  # FTX-1 USB audio device detection
    ├── NetworkUtils.java     # IP address utilities
    └── SerialPortDetector.java # COM port discovery
```

### Operating Modes

1. **Local Mode (Server)**: Connect directly to FTX-1 via serial port
   - Runs rigctld server on port 4532
   - Runs audio server on port 4533
   - Radio connected via USB serial

2. **Remote Mode (Client)**: Connect to remote ftx1-hamlib instance
   - Connects to rigctld server on remote host
   - Connects to audio server for bidirectional streaming
   - Uses virtual audio devices for WSJT-X integration

### Audio Streaming

Uses the `net-audio` library for TCP audio streaming:

```java
// Server side (local to radio)
AudioStreamServer server = new AudioStreamServer(4533);
server.setCaptureDevice(FTX1AudioHelper.findCaptureDevice(deviceManager));
server.setPlaybackDevice(FTX1AudioHelper.findPlaybackDevice(deviceManager));
server.start();

// Client side (remote)
AudioStreamClient client = new AudioStreamClient(host, 4533);
VirtualAudioBridge bridge = new VirtualAudioBridge(deviceManager);
client.setCaptureDevice(bridge.findBestCaptureDevice());
client.setPlaybackDevice(bridge.findBestPlaybackDevice());
client.connect();
```

### FTX-1 Device Detection

The `FTX1AudioHelper` class provides FTX-1 specific device detection:

```java
AudioDeviceManager mgr = new AudioDeviceManager();
AudioDeviceInfo capture = FTX1AudioHelper.findCaptureDevice(mgr);  // RX from radio
AudioDeviceInfo playback = FTX1AudioHelper.findPlaybackDevice(mgr); // TX to radio
```

## Rigctld Protocol

Implements standard Hamlib rigctld protocol on port 4532:

| Command | Description |
|---------|-------------|
| `f` | Get frequency |
| `F <hz>` | Set frequency |
| `m` | Get mode |
| `M <mode> <width>` | Set mode |
| `t` | Get PTT state |
| `T <0\|1>` | Set PTT |
| `v` | Get VFO |
| `V <VFOA\|VFOB>` | Set VFO |
| `s` | Get split |
| `S <0\|1>` | Set split |

Extended commands with `\` prefix for additional FTX-1 features.

## Internationalization

Supports 9 languages via resource bundles in `src/main/resources/i18n/`:
- English (default)
- German, French, Spanish, Italian
- Japanese, Chinese, Korean
- Portuguese

```java
Messages.setLocale(Locale.GERMAN);
String label = Messages.get("audio.server.start");
```

## Testing

```bash
# Requires FTX-1 hardware connected
export FTX1_PORT=/dev/cu.SLAB_USBtoUART
./test-ftx1-hamlib.sh $FTX1_PORT -v    # Basic tests
./test-ftx1-hamlib.sh $FTX1_PORT --tx  # Include PTT tests (use dummy load!)
```

## WSJT-X Integration

1. Start ftx1-hamlib in GUI mode
2. Configure audio devices (FTX-1 USB audio)
3. Start rigctld server (port 4532)
4. Start audio server (port 4533)

In WSJT-X Settings:
- Radio: Hamlib NET rigctl
- Network Server: localhost:4532
- Audio Input/Output: FTX-1 USB Audio (local) or virtual audio (remote)
