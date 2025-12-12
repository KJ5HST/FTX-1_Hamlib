/*
 * Jamlib-FTX1 - Hamlib-compatible daemon for FTX-1
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 */
package com.yaesu.jamlib;

import com.yaesu.ftx1.FTX1;
import com.yaesu.ftx1.exception.CatException;
import com.yaesu.ftx1.model.HeadType;
import com.yaesu.ftx1.model.MeterType;
import com.yaesu.ftx1.model.OperatingMode;
import com.yaesu.ftx1.model.VFO;

/**
 * Handler for rigctl protocol commands.
 * <p>
 * Implements the rigctld protocol commands, translating them to FTX-1 CAT operations.
 * </p>
 */
public class RigctlCommandHandler {

    private static final int RPRT_OK = 0;
    private static final int RPRT_EINVAL = -1;
    private static final int RPRT_EPROTO = -2;
    private static final int RPRT_ENAVAIL = -11;

    private final FTX1 rig;
    private final boolean verbose;

    public RigctlCommandHandler(FTX1 rig, boolean verbose) {
        this.rig = rig;
        this.verbose = verbose;
    }

    /**
     * Handle a rigctl command and return the response.
     *
     * @param command The command string
     * @return Response string (with newline)
     */
    public String handleCommand(String command) {
        if (command == null || command.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }

        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0];
        String args = parts.length > 1 ? parts[1] : "";

        try {
            return dispatch(cmd, args);
        } catch (CatException e) {
            if (verbose) {
                System.err.println("CAT error: " + e.getMessage());
            }
            return "RPRT " + RPRT_EPROTO + "\n";
        } catch (Exception e) {
            if (verbose) {
                System.err.println("Error: " + e.getMessage());
            }
            return "RPRT " + RPRT_EINVAL + "\n";
        }
    }

    private String dispatch(String cmd, String args) throws CatException {
        switch (cmd) {
            // Frequency
            case "f":
            case "get_freq":
                return getFreq();

            case "F":
            case "set_freq":
                return setFreq(args);

            // Mode
            case "m":
            case "get_mode":
                return getMode();

            case "M":
            case "set_mode":
                return setMode(args);

            // PTT
            case "t":
            case "get_ptt":
                return getPtt();

            case "T":
            case "set_ptt":
                return setPtt(args);

            // VFO
            case "v":
            case "get_vfo":
                return getVfo();

            case "V":
            case "set_vfo":
                return setVfo(args);

            // Levels
            case "l":
            case "get_level":
                return getLevel(args);

            case "L":
            case "set_level":
                return setLevel(args);

            // Raw command
            case "w":
            case "send_cmd":
                return sendCmd(args);

            // Info
            case "_":
            case "get_info":
                return getInfo();

            case "1":
            case "dump_caps":
                return dumpCaps();

            // Split
            case "s":
            case "get_split_vfo":
                return getSplitVfo();

            case "S":
            case "set_split_vfo":
                return setSplitVfo(args);

            // Functions
            case "u":
            case "get_func":
                return getFunc(args);

            case "U":
            case "set_func":
                return setFunc(args);

            // Help
            case "?":
            case "help":
                return getHelp();

            case "q":
            case "quit":
            case "exit":
                return "RPRT " + RPRT_OK + "\n";

            // Extended commands (backslash prefix)
            case "\\dump_state":
                return dumpState();

            case "\\get_powerstat":
                return getPowerStat();

            case "\\set_powerstat":
                return setPowerStat(args);

            case "\\chk_vfo":
                return chkVfo();

            case "\\get_vfo_info":
                return getVfoInfo(args);

            case "\\get_rig_info":
                return getRigInfo();

            case "\\get_split_mode":
                return getSplitMode();

            case "\\set_split_mode":
                return setSplitMode(args);

            case "\\get_split_freq":
                return getSplitFreq();

            case "\\set_split_freq":
                return setSplitFreq(args);

            case "\\get_split_freq_mode":
                return getSplitFreqMode();

            case "\\set_split_freq_mode":
                return setSplitFreqMode(args);

            case "\\get_clock":
                return getClock();

            case "\\set_clock":
                return setClock(args);

            case "\\get_lock_mode":
                return getLockMode();

            case "\\set_lock_mode":
                return setLockMode(args);

            case "\\send_morse":
                return sendMorse(args);

            case "\\stop_morse":
                return stopMorse();

            case "\\wait_morse":
                return waitMorse();

            case "\\send_voice_mem":
                return sendVoiceMem(args);

            case "\\halt":
                return halt();

            case "\\pause":
                return pause(args);

            default:
                if (verbose) {
                    System.err.println("Unknown command: " + cmd);
                }
                return "RPRT " + RPRT_EINVAL + "\n";
        }
    }

    // ========================================================================
    // Frequency Commands
    // ========================================================================

    private String getFreq() throws CatException {
        long freq = rig.getFrequency(VFO.MAIN);
        return freq + "\n";
    }

    private String setFreq(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        // Parse as double first to handle floating point format (e.g., "28074055.000000")
        double freqDouble = Double.parseDouble(args.trim());
        long freq = Math.round(freqDouble);
        rig.setFrequency(VFO.MAIN, freq);
        return "RPRT " + RPRT_OK + "\n";
    }

    // ========================================================================
    // Mode Commands
    // ========================================================================

    private String getMode() throws CatException {
        OperatingMode mode = rig.getMode(VFO.MAIN);
        // Return mode and passband (0 = default)
        return mode.name() + "\n0\n";
    }

    private String setMode(String args) throws CatException {
        String[] parts = args.split("\\s+");
        if (parts.length < 1) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        String modeName = parts[0].toUpperCase();

        // Map Hamlib mode names to OperatingMode
        OperatingMode mode;
        try {
            mode = mapHamlibMode(modeName);
        } catch (IllegalArgumentException e) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }

        rig.setMode(VFO.MAIN, mode);
        return "RPRT " + RPRT_OK + "\n";
    }

    private OperatingMode mapHamlibMode(String name) {
        switch (name) {
            case "LSB": return OperatingMode.LSB;
            case "USB": return OperatingMode.USB;
            case "CW": return OperatingMode.CW_U;
            case "CWR": return OperatingMode.CW_L;
            case "AM": return OperatingMode.AM;
            case "FM": return OperatingMode.FM;
            case "RTTY": return OperatingMode.RTTY_L;
            case "RTTYR": return OperatingMode.RTTY_U;
            case "PKTLSB": return OperatingMode.DATA_L;
            case "PKTUSB": return OperatingMode.DATA_U;
            case "PKTFM": return OperatingMode.DATA_FM;
            default:
                return OperatingMode.valueOf(name);
        }
    }

    // ========================================================================
    // PTT Commands
    // ========================================================================

    private String getPtt() throws CatException {
        boolean ptt = rig.isPtt();
        return (ptt ? "1" : "0") + "\n";
    }

    private String setPtt(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        int ptt = Integer.parseInt(args.trim());
        rig.setPtt(ptt > 0);
        return "RPRT " + RPRT_OK + "\n";
    }

    // ========================================================================
    // VFO Commands
    // ========================================================================

    private String getVfo() throws CatException {
        VFO vfo = rig.getActiveVfo();
        String hamlibVfo = (vfo == VFO.MAIN) ? "VFOA" : "VFOB";
        return hamlibVfo + "\n";
    }

    private String setVfo(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        String vfoName = args.trim().toUpperCase();
        VFO vfo;
        if (vfoName.equals("VFOA") || vfoName.equals("MAIN") || vfoName.equals("A")) {
            vfo = VFO.MAIN;
        } else if (vfoName.equals("VFOB") || vfoName.equals("SUB") || vfoName.equals("B")) {
            vfo = VFO.SUB;
        } else {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        rig.setActiveVfo(vfo);
        return "RPRT " + RPRT_OK + "\n";
    }

    // ========================================================================
    // Level Commands
    // ========================================================================

    private String getLevel(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }

        String level = args.trim().toUpperCase();

        switch (level) {
            case "RFPOWER":
            case "RF":
                double power = rig.getPower();
                double maxPower = rig.getMaxPower();
                return String.format("%.2f\n", power / maxPower);

            case "AF":
                int afGain = rig.getAFGain(VFO.MAIN);
                return String.format("%.2f\n", afGain / 255.0);

            case "SQL":
                int sql = rig.getSquelch(VFO.MAIN);
                return String.format("%.2f\n", sql / 100.0);

            case "STRENGTH":
            case "S":
                int smeter = rig.getSmeter();
                return smeter + "\n";

            case "SWR":
                int swr = rig.getMeter(MeterType.SWR);
                return String.format("%.1f\n", swr / 10.0);

            case "ALC":
                int alc = rig.getMeter(MeterType.ALC);
                return alc + "\n";

            case "COMP":
                int comp = rig.getMeter(MeterType.COMP);
                return comp + "\n";

            default:
                return "RPRT " + RPRT_EINVAL + "\n";
        }
    }

    private String setLevel(String args) throws CatException {
        String[] parts = args.split("\\s+");
        if (parts.length < 2) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }

        String level = parts[0].toUpperCase();
        String value = parts[1];

        switch (level) {
            case "RFPOWER":
            case "RF":
                double powerNorm = Double.parseDouble(value);
                double watts = powerNorm * rig.getMaxPower();
                rig.setPower(watts);
                return "RPRT " + RPRT_OK + "\n";

            case "AF":
                double afNorm = Double.parseDouble(value);
                rig.setAFGain(VFO.MAIN, (int)(afNorm * 255));
                return "RPRT " + RPRT_OK + "\n";

            case "SQL":
                double sqlNorm = Double.parseDouble(value);
                rig.setSquelch(VFO.MAIN, (int)(sqlNorm * 100));
                return "RPRT " + RPRT_OK + "\n";

            default:
                return "RPRT " + RPRT_EINVAL + "\n";
        }
    }

    // ========================================================================
    // Split Commands
    // ========================================================================

    private String getSplitVfo() throws CatException {
        boolean split = rig.isSplitEnabled();
        return (split ? "1" : "0") + "\nVFOB\n";
    }

    private String setSplitVfo(String args) throws CatException {
        String[] parts = args.split("\\s+");
        if (parts.length < 1) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        int split = Integer.parseInt(parts[0]);
        rig.setSplit(split > 0);
        return "RPRT " + RPRT_OK + "\n";
    }

    // ========================================================================
    // Function Commands
    // ========================================================================

    private String getFunc(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }

        String func = args.trim().toUpperCase();

        switch (func) {
            case "TUNER":
                // Only available with SPA-1
                if (rig.hasSpa1()) {
                    // Would need to add getTuner() to FTX1 API
                    return "0\n";  // TODO: implement when API available
                }
                return "RPRT " + RPRT_ENAVAIL + "\n";

            case "LOCK":
                boolean locked = rig.isLocked();
                return (locked ? "1" : "0") + "\n";

            default:
                return "RPRT " + RPRT_EINVAL + "\n";
        }
    }

    private String setFunc(String args) throws CatException {
        String[] parts = args.split("\\s+");
        if (parts.length < 2) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }

        String func = parts[0].toUpperCase();
        int value = Integer.parseInt(parts[1]);

        switch (func) {
            case "TUNER":
                // Only available with SPA-1
                if (rig.hasSpa1()) {
                    // Would need to add setTuner() to FTX1 API
                    return "RPRT " + RPRT_ENAVAIL + "\n";  // TODO: implement
                }
                return "RPRT " + RPRT_ENAVAIL + "\n";

            case "LOCK":
                rig.setLocked(value > 0);
                return "RPRT " + RPRT_OK + "\n";

            default:
                return "RPRT " + RPRT_EINVAL + "\n";
        }
    }

    // ========================================================================
    // Raw Command
    // ========================================================================

    private String sendCmd(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }

        // Strip quotes if present
        String cmd = args.trim();
        if (cmd.startsWith("\"") && cmd.endsWith("\"")) {
            cmd = cmd.substring(1, cmd.length() - 1);
        }

        // Strip trailing semicolon - sendRawCommand adds it automatically
        if (cmd.endsWith(";")) {
            cmd = cmd.substring(0, cmd.length() - 1);
        }

        String response = rig.sendRawCommand(cmd);
        return response + "\n";
    }

    // ========================================================================
    // Info Commands
    // ========================================================================

    private String getInfo() {
        HeadType headType = rig.getHeadType();
        return "FTX-1 " + headType.getDisplayName() + "\n";
    }

    private String dumpCaps() {
        HeadType headType = rig.getHeadType();

        StringBuilder sb = new StringBuilder();
        sb.append("Caps dump for model: 1051\n");
        sb.append("Model name:\tFTX-1\n");
        sb.append("Mfg name:\tYaesu\n");
        sb.append("Backend version:\t1.0\n");
        sb.append("Backend status:\tBeta\n");
        sb.append("Rig type:\tTransceiver\n");
        sb.append("PTT type:\tRig capable\n");
        sb.append("Port type:\tRS-232\n");
        sb.append("Serial speed:\t38400\n");
        sb.append("Head type:\t").append(headType.getDisplayName()).append("\n");
        sb.append("Min power:\t").append(headType.getMinPowerWatts()).append("W\n");
        sb.append("Max power:\t").append(headType.getMaxPowerWatts()).append("W\n");
        sb.append("Has tuner:\t").append(headType.hasInternalTuner() ? "Y" : "N").append("\n");

        return sb.toString();
    }

    private String getHelp() {
        StringBuilder sb = new StringBuilder();
        sb.append("Commands:\n");
        sb.append("  f, get_freq          Get frequency (Hz)\n");
        sb.append("  F, set_freq FREQ     Set frequency (Hz)\n");
        sb.append("  m, get_mode          Get mode and passband\n");
        sb.append("  M, set_mode MODE PB  Set mode and passband\n");
        sb.append("  t, get_ptt           Get PTT status (0/1)\n");
        sb.append("  T, set_ptt 0|1       Set PTT\n");
        sb.append("  v, get_vfo           Get current VFO\n");
        sb.append("  V, set_vfo VFOA|VFOB Set VFO\n");
        sb.append("  s, get_split_vfo     Get split status\n");
        sb.append("  S, set_split_vfo 0|1 Set split\n");
        sb.append("  l, get_level LEVEL   Get level (RFPOWER, AF, SQL, STRENGTH, SWR)\n");
        sb.append("  L, set_level LVL VAL Set level\n");
        sb.append("  u, get_func FUNC     Get function (LOCK)\n");
        sb.append("  U, set_func FUNC 0|1 Set function\n");
        sb.append("  w, send_cmd CMD      Send raw CAT command\n");
        sb.append("  _, get_info          Get rig info\n");
        sb.append("  1, dump_caps         Dump capabilities\n");
        sb.append("  q, quit              Exit\n");

        return sb.toString();
    }

    // ========================================================================
    // Extended Commands (backslash prefix for rigctld protocol)
    // ========================================================================

    /**
     * Returns the rig power status.
     * WSJT-X uses this to check if the rig is powered on.
     */
    private String getPowerStat() {
        // 1 = power on (we're connected, so assume on)
        return "1\n";
    }

    /**
     * Sets the rig power status.
     * We don't actually support remote power control, but accept the command.
     */
    private String setPowerStat(String args) {
        // Accept but ignore - we can't remotely power on/off
        return "RPRT " + RPRT_OK + "\n";
    }

    /**
     * Checks if VFO mode is enabled.
     * Returns 0 for "targetable VFO mode" or 1 for "VFO mode enabled"
     */
    private String chkVfo() {
        // 0 = VFOA/VFOB targetable mode (what we support)
        return "0\n";
    }

    /**
     * Gets detailed VFO info (frequency, mode, width).
     */
    private String getVfoInfo(String args) throws CatException {
        VFO vfo = VFO.MAIN;
        if (!args.isEmpty()) {
            String vfoName = args.trim().toUpperCase();
            if (vfoName.equals("VFOB") || vfoName.equals("SUB") || vfoName.equals("B")) {
                vfo = VFO.SUB;
            }
        }

        long freq = rig.getFrequency(vfo);
        OperatingMode mode = rig.getMode(vfo);
        String hamlibVfo = (vfo == VFO.MAIN) ? "VFOA" : "VFOB";

        // Format: Freq=... Mode=... Width=... VFO=...
        StringBuilder sb = new StringBuilder();
        sb.append("Freq=").append(freq).append("\n");
        sb.append("Mode=").append(mapModeToHamlib(mode)).append("\n");
        sb.append("Width=0\n");  // 0 = default passband
        sb.append("VFO=").append(hamlibVfo).append("\n");

        return sb.toString();
    }

    /**
     * Gets rig info in key=value format.
     */
    private String getRigInfo() {
        HeadType headType = rig.getHeadType();

        StringBuilder sb = new StringBuilder();
        sb.append("Model=FTX-1\n");
        sb.append("Mfg=Yaesu\n");
        sb.append("HeadType=").append(headType.getDisplayName()).append("\n");
        sb.append("MinPower=").append(headType.getMinPowerWatts()).append("\n");
        sb.append("MaxPower=").append(headType.getMaxPowerWatts()).append("\n");
        sb.append("HasTuner=").append(headType.hasInternalTuner() ? "1" : "0").append("\n");

        return sb.toString();
    }

    /**
     * Gets split mode and passband.
     */
    private String getSplitMode() throws CatException {
        OperatingMode mode = rig.getMode(VFO.SUB);
        return mapModeToHamlib(mode) + "\n0\n";  // mode + passband
    }

    /**
     * Sets split mode and passband.
     */
    private String setSplitMode(String args) throws CatException {
        String[] parts = args.split("\\s+");
        if (parts.length < 1) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        OperatingMode mode = mapHamlibMode(parts[0].toUpperCase());
        rig.setMode(VFO.SUB, mode);
        return "RPRT " + RPRT_OK + "\n";
    }

    /**
     * Gets split TX frequency.
     */
    private String getSplitFreq() throws CatException {
        long freq = rig.getFrequency(VFO.SUB);
        return freq + "\n";
    }

    /**
     * Sets split TX frequency.
     */
    private String setSplitFreq(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        // Parse as double to handle floating point format
        double freqDouble = Double.parseDouble(args.trim());
        long freq = Math.round(freqDouble);
        rig.setFrequency(VFO.SUB, freq);
        return "RPRT " + RPRT_OK + "\n";
    }

    /**
     * Gets split frequency and mode together.
     */
    private String getSplitFreqMode() throws CatException {
        long freq = rig.getFrequency(VFO.SUB);
        OperatingMode mode = rig.getMode(VFO.SUB);
        return freq + "\n" + mapModeToHamlib(mode) + "\n0\n";  // freq + mode + passband
    }

    /**
     * Sets split frequency and mode together.
     */
    private String setSplitFreqMode(String args) throws CatException {
        String[] parts = args.split("\\s+");
        if (parts.length < 2) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        // Parse as double to handle floating point format
        double freqDouble = Double.parseDouble(parts[0]);
        long freq = Math.round(freqDouble);
        OperatingMode mode = mapHamlibMode(parts[1].toUpperCase());

        rig.setFrequency(VFO.SUB, freq);
        rig.setMode(VFO.SUB, mode);
        return "RPRT " + RPRT_OK + "\n";
    }

    /**
     * Gets rig clock (not supported, return current time).
     */
    private String getClock() {
        // Return current system time in ISO format
        return java.time.LocalDateTime.now().toString() + "\n";
    }

    /**
     * Sets rig clock (not supported).
     */
    private String setClock(String args) {
        // Accept but ignore - FTX-1 doesn't have a settable clock
        return "RPRT " + RPRT_OK + "\n";
    }

    /**
     * Gets lock mode status.
     */
    private String getLockMode() throws CatException {
        boolean locked = rig.isLocked();
        return (locked ? "1" : "0") + "\n";
    }

    /**
     * Sets lock mode.
     */
    private String setLockMode(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        int lock = Integer.parseInt(args.trim());
        rig.setLocked(lock > 0);
        return "RPRT " + RPRT_OK + "\n";
    }

    /**
     * Sends Morse code (CW keying).
     * Note: Requires rig to be in CW mode.
     */
    private String sendMorse(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        // FTX-1 supports CW keyer via KY command
        // KYtext; sends the text
        try {
            rig.sendRawCommand("KY" + args.trim());
            return "RPRT " + RPRT_OK + "\n";
        } catch (CatException e) {
            return "RPRT " + RPRT_ENAVAIL + "\n";
        }
    }

    /**
     * Stops Morse sending.
     */
    private String stopMorse() throws CatException {
        // KY command with empty clears buffer
        try {
            rig.sendRawCommand("KY");
            return "RPRT " + RPRT_OK + "\n";
        } catch (CatException e) {
            return "RPRT " + RPRT_ENAVAIL + "\n";
        }
    }

    /**
     * Waits for Morse to complete.
     */
    private String waitMorse() {
        // No way to know when CW keying is done, just return OK
        return "RPRT " + RPRT_OK + "\n";
    }

    /**
     * Plays voice memory.
     */
    private String sendVoiceMem(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        // FTX-1 supports voice memory playback via PB command
        // PBn; plays memory n (1-5)
        try {
            int mem = Integer.parseInt(args.trim());
            if (mem < 1 || mem > 5) {
                return "RPRT " + RPRT_EINVAL + "\n";
            }
            rig.sendRawCommand("PB" + mem);
            return "RPRT " + RPRT_OK + "\n";
        } catch (NumberFormatException e) {
            return "RPRT " + RPRT_EINVAL + "\n";
        } catch (CatException e) {
            return "RPRT " + RPRT_ENAVAIL + "\n";
        }
    }

    /**
     * Halts rig operations (emergency stop).
     */
    private String halt() throws CatException {
        // Turn off PTT as emergency stop
        try {
            rig.setPtt(false);
        } catch (CatException e) {
            // Ignore errors
        }
        return "RPRT " + RPRT_OK + "\n";
    }

    /**
     * Pauses for specified milliseconds.
     */
    private String pause(String args) {
        if (!args.isEmpty()) {
            try {
                int ms = Integer.parseInt(args.trim());
                Thread.sleep(Math.min(ms, 5000));  // Max 5 second pause
            } catch (NumberFormatException | InterruptedException e) {
                // Ignore
            }
        }
        return "RPRT " + RPRT_OK + "\n";
    }

    /**
     * Maps OperatingMode to Hamlib mode name.
     */
    private String mapModeToHamlib(OperatingMode mode) {
        switch (mode) {
            case LSB: return "LSB";
            case USB: return "USB";
            case CW_U: return "CW";
            case CW_L: return "CWR";
            case AM: return "AM";
            case FM: return "FM";
            case RTTY_L: return "RTTY";
            case RTTY_U: return "RTTYR";
            case DATA_L: return "PKTLSB";
            case DATA_U: return "PKTUSB";
            case DATA_FM: return "PKTFM";
            default: return mode.name();
        }
    }

    /**
     * Dumps the rig state in rigctld protocol format.
     * This is the main command WSJT-X uses to discover rig capabilities.
     */
    private String dumpState() {
        HeadType headType = rig.getHeadType();
        double minFreq = 1800000;   // 1.8 MHz (160m)
        double maxFreq = 54000000;  // 54 MHz (6m)

        StringBuilder sb = new StringBuilder();

        // Protocol version
        sb.append("0\n");  // Protocol version 0

        // Rig model
        sb.append("1051\n");  // FTX-1 model number

        // ITU region (0 = any)
        sb.append("0\n");

        // RX frequency ranges: start end modes low_power high_power vfo ant
        // Format: freq_start freq_end mode_list low_power high_power vfo antenna
        sb.append(String.format("%.0f %.0f 0x%x %d %d 0x%x 0x%x\n",
                minFreq, maxFreq,
                0x8ff,  // All modes: LSB, USB, CW, AM, FM, RTTY, DATA
                -1, -1,  // Power range (use -1 for not specified)
                0x3,     // VFO: VFOA | VFOB
                0x1));   // Antenna 1
        sb.append("0 0 0 0 0 0 0\n");  // End of RX ranges

        // TX frequency ranges
        sb.append(String.format("%.0f %.0f 0x%x %d %d 0x%x 0x%x\n",
                minFreq, maxFreq,
                0x8ff,  // All modes
                (int)(headType.getMinPowerMilliwatts()),
                (int)(headType.getMaxPowerMilliwatts()),
                0x3,     // VFO: VFOA | VFOB
                0x1));   // Antenna 1
        sb.append("0 0 0 0 0 0 0\n");  // End of TX ranges

        // Tuning steps: mode step
        sb.append("0x8ff 1\n");     // All modes, 1 Hz step
        sb.append("0x8ff 10\n");    // All modes, 10 Hz step
        sb.append("0x8ff 100\n");   // All modes, 100 Hz step
        sb.append("0x8ff 1000\n");  // All modes, 1 kHz step
        sb.append("0 0\n");         // End of tuning steps

        // Filters: mode width
        sb.append("0x3 2400\n");    // SSB 2.4 kHz
        sb.append("0xc 500\n");     // CW 500 Hz
        sb.append("0x20 6000\n");   // AM 6 kHz
        sb.append("0x40 12000\n");  // FM 12 kHz
        sb.append("0 0\n");         // End of filters

        // Max RIT/XIT
        sb.append("9999\n");   // Max RIT
        sb.append("9999\n");   // Max XIT
        sb.append("0\n");      // Max IF shift

        // Announces (none)
        sb.append("0\n");

        // Preamp levels
        sb.append("0\n");  // No preamp settings

        // Attenuator levels
        sb.append("0\n");  // No attenuator settings

        // has_get_func bitmask
        sb.append("0x%x\n".formatted(0));  // Functions we can get

        // has_set_func bitmask
        sb.append("0x%x\n".formatted(0));  // Functions we can set

        // has_get_level bitmask (STRENGTH, RFPOWER, SWR)
        sb.append("0x%x\n".formatted(0x4 | 0x8 | 0x1000));

        // has_set_level bitmask (RFPOWER)
        sb.append("0x%x\n".formatted(0x8));

        // has_get_parm (none)
        sb.append("0\n");

        // has_set_parm (none)
        sb.append("0\n");

        return sb.toString();
    }
}
