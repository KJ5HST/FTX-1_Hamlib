/*
 * FTX1-Hamlib - Hamlib-compatible daemon for FTX-1
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 */
package com.yaesu.hamlib.audio;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages audio device discovery and selection.
 * <p>
 * Discovers audio devices on the system and identifies FTX-1 USB audio
 * interfaces and virtual audio devices (VB-Cable, BlackHole, PulseAudio).
 * </p>
 */
public class AudioDeviceManager {

    // FTX-1 USB audio device patterns by platform
    private static final String[] FTX1_PATTERNS = {
        "ftx-1", "ftx1", "yaesu", "usb audio"
    };

    // Virtual audio device patterns by platform
    private static final String[] VIRTUAL_PATTERNS_MACOS = {
        "blackhole", "soundflower", "loopback"
    };

    private static final String[] VIRTUAL_PATTERNS_WINDOWS = {
        "vb-audio", "cable", "virtual", "voicemeeter"
    };

    private static final String[] VIRTUAL_PATTERNS_LINUX = {
        "pulse", "pipewire", "jack", "null"
    };

    private final AudioStreamConfig config;

    /**
     * Creates a new AudioDeviceManager with default configuration.
     */
    public AudioDeviceManager() {
        this(new AudioStreamConfig());
    }

    /**
     * Creates a new AudioDeviceManager with the specified configuration.
     *
     * @param config the audio stream configuration
     */
    public AudioDeviceManager(AudioStreamConfig config) {
        this.config = config;
    }

    /**
     * Discovers all audio devices that support the configured format.
     *
     * @return list of available audio devices
     */
    public List<AudioDeviceInfo> discoverDevices() {
        List<AudioDeviceInfo> devices = new ArrayList<>();
        AudioFormat format = config.toAudioFormat();

        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);

            // Check for capture capability
            if (supportsCapture(mixer, format)) {
                AudioDeviceInfo.DeviceType type = classifyDevice(mixerInfo);
                devices.add(new AudioDeviceInfo(mixerInfo, type, AudioDeviceInfo.Capability.CAPTURE));
            }

            // Check for playback capability
            if (supportsPlayback(mixer, format)) {
                AudioDeviceInfo.DeviceType type = classifyDevice(mixerInfo);
                // Check if we already added this as capture - if so, upgrade to DUPLEX
                boolean found = false;
                for (int i = 0; i < devices.size(); i++) {
                    AudioDeviceInfo existing = devices.get(i);
                    if (existing.getMixerInfo().equals(mixerInfo)) {
                        if (existing.getCapability() == AudioDeviceInfo.Capability.CAPTURE) {
                            devices.set(i, new AudioDeviceInfo(mixerInfo, type, AudioDeviceInfo.Capability.DUPLEX));
                        }
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    devices.add(new AudioDeviceInfo(mixerInfo, type, AudioDeviceInfo.Capability.PLAYBACK));
                }
            }
        }

        return devices;
    }

    /**
     * Discovers capture-capable devices only.
     *
     * @return list of devices that can capture audio
     */
    public List<AudioDeviceInfo> discoverCaptureDevices() {
        List<AudioDeviceInfo> devices = new ArrayList<>();
        for (AudioDeviceInfo device : discoverDevices()) {
            if (device.supportsCapture()) {
                devices.add(device);
            }
        }
        return devices;
    }

    /**
     * Discovers playback-capable devices only.
     *
     * @return list of devices that can play audio
     */
    public List<AudioDeviceInfo> discoverPlaybackDevices() {
        List<AudioDeviceInfo> devices = new ArrayList<>();
        for (AudioDeviceInfo device : discoverDevices()) {
            if (device.supportsPlayback()) {
                devices.add(device);
            }
        }
        return devices;
    }

    /**
     * Attempts to find the FTX-1 USB audio device for capture.
     *
     * @return the FTX-1 device info, or null if not found
     */
    public AudioDeviceInfo findFTX1CaptureDevice() {
        for (AudioDeviceInfo device : discoverCaptureDevices()) {
            if (matchesPatterns(device, FTX1_PATTERNS)) {
                return device;
            }
        }
        return null;
    }

    /**
     * Attempts to find the FTX-1 USB audio device for playback.
     *
     * @return the FTX-1 device info, or null if not found
     */
    public AudioDeviceInfo findFTX1PlaybackDevice() {
        for (AudioDeviceInfo device : discoverPlaybackDevices()) {
            if (matchesPatterns(device, FTX1_PATTERNS)) {
                return device;
            }
        }
        return null;
    }

    /**
     * Attempts to find a virtual audio device for capture.
     *
     * @return the virtual device info, or null if not found
     */
    public AudioDeviceInfo findVirtualCaptureDevice() {
        String[] patterns = getVirtualPatterns();
        for (AudioDeviceInfo device : discoverCaptureDevices()) {
            if (matchesPatterns(device, patterns)) {
                return device;
            }
        }
        return null;
    }

    /**
     * Attempts to find a virtual audio device for playback.
     *
     * @return the virtual device info, or null if not found
     */
    public AudioDeviceInfo findVirtualPlaybackDevice() {
        String[] patterns = getVirtualPatterns();
        for (AudioDeviceInfo device : discoverPlaybackDevices()) {
            if (matchesPatterns(device, patterns)) {
                return device;
            }
        }
        return null;
    }

    /**
     * Opens a TargetDataLine for audio capture from the specified device.
     *
     * @param device the device to open
     * @return the opened TargetDataLine
     * @throws LineUnavailableException if the line cannot be opened
     */
    public TargetDataLine openCaptureLine(AudioDeviceInfo device) throws LineUnavailableException {
        AudioFormat format = config.toAudioFormat();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        Mixer mixer = AudioSystem.getMixer(device.getMixerInfo());
        TargetDataLine line = (TargetDataLine) mixer.getLine(info);
        line.open(format);
        return line;
    }

    /**
     * Opens a SourceDataLine for audio playback to the specified device.
     *
     * @param device the device to open
     * @return the opened SourceDataLine
     * @throws LineUnavailableException if the line cannot be opened
     */
    public SourceDataLine openPlaybackLine(AudioDeviceInfo device) throws LineUnavailableException {
        AudioFormat format = config.toAudioFormat();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        Mixer mixer = AudioSystem.getMixer(device.getMixerInfo());
        SourceDataLine line = (SourceDataLine) mixer.getLine(info);
        line.open(format);
        return line;
    }

    /**
     * Gets the configuration used by this manager.
     */
    public AudioStreamConfig getConfig() {
        return config;
    }

    /**
     * Gets platform-specific setup instructions for virtual audio devices.
     *
     * @return setup instructions as a string
     */
    public static String getVirtualAudioSetupInstructions() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("mac")) {
            return """
                macOS Virtual Audio Setup:
                1. Install BlackHole: brew install blackhole-2ch
                2. In System Settings > Sound, BlackHole should appear
                3. In WSJT-X Audio settings, select BlackHole 2ch for both input and output
                4. In this client, select BlackHole 2ch for both capture and playback
                """;
        } else if (os.contains("win")) {
            return """
                Windows Virtual Audio Setup:
                1. Download VB-Cable from https://vb-audio.com/Cable/
                2. Install VB-Cable (requires admin rights)
                3. In WSJT-X Audio settings, select CABLE Output for input, CABLE Input for output
                4. In this client, select CABLE Input for capture, CABLE Output for playback
                """;
        } else {
            return """
                Linux Virtual Audio Setup:
                Using PulseAudio:
                1. Create a null sink: pactl load-module module-null-sink sink_name=virtual
                2. In WSJT-X, select the virtual sink monitor for input, virtual sink for output
                3. In this client, select the virtual sink for capture/playback

                Using JACK:
                1. Start JACK server: jackd -d alsa
                2. Use qjackctl to connect WSJT-X to this client
                """;
        }
    }

    private boolean supportsCapture(Mixer mixer, AudioFormat format) {
        DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, format);
        return mixer.isLineSupported(targetInfo);
    }

    private boolean supportsPlayback(Mixer mixer, AudioFormat format) {
        DataLine.Info sourceInfo = new DataLine.Info(SourceDataLine.class, format);
        return mixer.isLineSupported(sourceInfo);
    }

    private AudioDeviceInfo.DeviceType classifyDevice(Mixer.Info mixerInfo) {
        String name = mixerInfo.getName().toLowerCase();
        String desc = mixerInfo.getDescription().toLowerCase();
        String combined = name + " " + desc;

        // Check for virtual device patterns
        String[] virtualPatterns = getVirtualPatterns();
        for (String pattern : virtualPatterns) {
            if (combined.contains(pattern)) {
                return AudioDeviceInfo.DeviceType.VIRTUAL;
            }
        }

        // Check for FTX-1 or other hardware
        for (String pattern : FTX1_PATTERNS) {
            if (combined.contains(pattern)) {
                return AudioDeviceInfo.DeviceType.HARDWARE;
            }
        }

        return AudioDeviceInfo.DeviceType.UNKNOWN;
    }

    private boolean matchesPatterns(AudioDeviceInfo device, String[] patterns) {
        String name = device.getName().toLowerCase();
        String desc = device.getDescription().toLowerCase();
        String combined = name + " " + desc;

        for (String pattern : patterns) {
            if (combined.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private String[] getVirtualPatterns() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("mac")) {
            return VIRTUAL_PATTERNS_MACOS;
        } else if (os.contains("win")) {
            return VIRTUAL_PATTERNS_WINDOWS;
        } else {
            return VIRTUAL_PATTERNS_LINUX;
        }
    }
}
