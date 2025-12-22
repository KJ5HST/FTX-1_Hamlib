/*
 * FTX1-Hamlib - Hamlib-compatible daemon for FTX-1
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 */
package com.yaesu.hamlib.util;

import com.yaesu.audio.AudioDeviceInfo;
import com.yaesu.audio.AudioDeviceManager;

import java.util.List;

/**
 * Helper utilities for finding FTX-1 USB audio devices.
 * <p>
 * The FTX-1 presents itself as a USB audio device for digital mode operation.
 * This helper provides methods to automatically detect the FTX-1 audio interface.
 * </p>
 */
public final class FTX1AudioHelper {

    /** Device name patterns that identify FTX-1 USB audio */
    private static final String[] FTX1_PATTERNS = {
        "ftx-1", "ftx1", "yaesu"
    };

    private FTX1AudioHelper() {
        // Utility class
    }

    /**
     * Finds the FTX-1 USB audio device for capture (RX audio from radio).
     *
     * @param deviceManager the audio device manager
     * @return the FTX-1 capture device, or null if not found
     */
    public static AudioDeviceInfo findCaptureDevice(AudioDeviceManager deviceManager) {
        List<AudioDeviceInfo> devices = deviceManager.discoverCaptureDevices();
        return deviceManager.findDeviceByPattern(devices, FTX1_PATTERNS);
    }

    /**
     * Finds the FTX-1 USB audio device for playback (TX audio to radio).
     *
     * @param deviceManager the audio device manager
     * @return the FTX-1 playback device, or null if not found
     */
    public static AudioDeviceInfo findPlaybackDevice(AudioDeviceManager deviceManager) {
        List<AudioDeviceInfo> devices = deviceManager.discoverPlaybackDevices();
        return deviceManager.findDeviceByPattern(devices, FTX1_PATTERNS);
    }

    /**
     * Checks if an FTX-1 USB audio device is available.
     *
     * @param deviceManager the audio device manager
     * @return true if FTX-1 audio is available for both capture and playback
     */
    public static boolean isAvailable(AudioDeviceManager deviceManager) {
        return findCaptureDevice(deviceManager) != null
            && findPlaybackDevice(deviceManager) != null;
    }
}
