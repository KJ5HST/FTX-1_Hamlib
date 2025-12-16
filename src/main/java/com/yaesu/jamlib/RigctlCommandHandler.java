/*
 * Jamlib-FTX1 - Hamlib-compatible daemon for FTX-1
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 *
 * ACKNOWLEDGMENTS:
 *   Jeremy Miller (KO4SSD) - RIT/XIT using RC/TC commands, EX0306 tuning steps
 *   See: https://github.com/Hamlib/Hamlib/pull/1826
 */
package com.yaesu.jamlib;

import com.yaesu.ftx1.FTX1;
import com.yaesu.ftx1.exception.CatException;
import com.yaesu.ftx1.model.HeadType;
import com.yaesu.ftx1.model.MeterType;
import com.yaesu.ftx1.model.OperatingMode;
import com.yaesu.ftx1.model.VFO;

import java.util.Map;
import java.util.Objects;

/**
 * Handler for rigctl protocol commands.
 * <p>
 * Implements the rigctld protocol commands, translating them to FTX-1 CAT operations.
 * Thread-safe: all rig operations are synchronized.
 * </p>
 */
public class RigctlCommandHandler {

    private static final int RPRT_OK = 0;
    private static final int RPRT_EINVAL = -1;
    private static final int RPRT_EPROTO = -2;
    private static final int RPRT_ENIMPL = -4;
    private static final int RPRT_ENAVAIL = -11;

    // Bidirectional mode mappings (Hamlib <-> OperatingMode)
    private static final Map<String, OperatingMode> HAMLIB_TO_MODE = Map.ofEntries(
            Map.entry("LSB", OperatingMode.LSB),
            Map.entry("USB", OperatingMode.USB),
            Map.entry("CW", OperatingMode.CW_U),
            Map.entry("CWR", OperatingMode.CW_L),
            Map.entry("AM", OperatingMode.AM),
            Map.entry("FM", OperatingMode.FM),
            Map.entry("RTTY", OperatingMode.RTTY_L),
            Map.entry("RTTYR", OperatingMode.RTTY_U),
            Map.entry("PKTLSB", OperatingMode.DATA_L),
            Map.entry("PKTUSB", OperatingMode.DATA_U),
            Map.entry("PKTFM", OperatingMode.DATA_FM)
    );

    private static final Map<OperatingMode, String> MODE_TO_HAMLIB = Map.ofEntries(
            Map.entry(OperatingMode.LSB, "LSB"),
            Map.entry(OperatingMode.USB, "USB"),
            Map.entry(OperatingMode.CW_U, "CW"),
            Map.entry(OperatingMode.CW_L, "CWR"),
            Map.entry(OperatingMode.AM, "AM"),
            Map.entry(OperatingMode.FM, "FM"),
            Map.entry(OperatingMode.RTTY_L, "RTTY"),
            Map.entry(OperatingMode.RTTY_U, "RTTYR"),
            Map.entry(OperatingMode.DATA_L, "PKTLSB"),
            Map.entry(OperatingMode.DATA_U, "PKTUSB"),
            Map.entry(OperatingMode.DATA_FM, "PKTFM")
    );

    private final FTX1 rig;
    private final Object rigLock;  // Synchronization lock for rig operations
    private final boolean verbose;

    public RigctlCommandHandler(FTX1 rig, boolean verbose) {
        this.rig = Objects.requireNonNull(rig, "rig must not be null");
        this.rigLock = rig;  // Use rig itself as lock for cross-handler synchronization
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
        return switch (cmd) {
            // Frequency
            case "f", "get_freq" -> getFreq();
            case "F", "set_freq" -> setFreq(args);

            // Mode
            case "m", "get_mode" -> getMode();
            case "M", "set_mode" -> setMode(args);

            // PTT
            case "t", "get_ptt" -> getPtt();
            case "T", "set_ptt" -> setPtt(args);

            // VFO
            case "v", "get_vfo" -> getVfo();
            case "V", "set_vfo" -> setVfo(args);

            // RIT/XIT (Jeremy Miller's discovery - uses RC/TC commands)
            case "j", "get_rit" -> getRit();
            case "J", "set_rit" -> setRit(args);
            case "z", "get_xit" -> getXit();
            case "Z", "set_xit" -> setXit(args);

            // Memory
            case "e", "get_mem" -> getMem();
            case "E", "set_mem" -> setMem(args);
            case "h", "get_channel" -> getChannel(args);
            case "H", "set_channel" -> setChannel(args);

            // CTCSS/DCS
            case "c", "get_ctcss_tone" -> getCtcssTone();
            case "C", "set_ctcss_tone" -> setCtcssTone(args);
            case "d", "get_dcs_code" -> getDcsCode();
            case "D", "set_dcs_code" -> setDcsCode(args);

            // Tuning Step
            case "n", "get_ts" -> getTs();
            case "N", "set_ts" -> setTs(args);

            // Levels
            case "l", "get_level" -> getLevel(args);
            case "L", "set_level" -> setLevel(args);

            // Raw command
            case "w", "send_cmd" -> sendCmd(args);

            // Info
            case "_", "get_info" -> getInfo();
            case "1", "dump_caps" -> dumpCaps();

            // Split
            case "s", "get_split_vfo" -> getSplitVfo();
            case "S", "set_split_vfo" -> setSplitVfo(args);

            // Functions
            case "u", "get_func" -> getFunc(args);
            case "U", "set_func" -> setFunc(args);

            // Help
            case "?", "help" -> getHelp();

            case "q", "quit", "exit" -> "RPRT " + RPRT_OK + "\n";

            // Extended commands (backslash prefix)
            case "\\dump_state" -> dumpState();
            case "\\get_powerstat" -> getPowerStat();
            case "\\set_powerstat" -> setPowerStat(args);
            case "\\chk_vfo" -> chkVfo();
            case "\\get_vfo_info" -> getVfoInfo(args);
            case "\\get_rig_info" -> getRigInfo();
            case "\\get_split_mode" -> getSplitMode();
            case "\\set_split_mode" -> setSplitMode(args);
            case "\\get_split_freq" -> getSplitFreq();
            case "\\set_split_freq" -> setSplitFreq(args);
            case "\\get_split_freq_mode" -> getSplitFreqMode();
            case "\\set_split_freq_mode" -> setSplitFreqMode(args);
            case "\\get_clock" -> getClock();
            case "\\set_clock" -> setClock(args);
            case "\\get_lock_mode" -> getLockMode();
            case "\\set_lock_mode" -> setLockMode(args);
            case "\\send_morse" -> sendMorse(args);
            case "\\stop_morse" -> stopMorse();
            case "\\wait_morse" -> waitMorse();
            case "\\send_voice_mem" -> sendVoiceMem(args);
            case "\\halt" -> halt();
            case "\\pause" -> pause(args);

            default -> {
                if (verbose) {
                    System.err.println("Unknown command: " + cmd);
                }
                yield "RPRT " + RPRT_EINVAL + "\n";
            }
        };
    }

    // ========================================================================
    // Frequency Commands
    // ========================================================================

    private String getFreq() throws CatException {
        synchronized (rigLock) {
            long freq = rig.getFrequency(VFO.MAIN);
            return freq + "\n";
        }
    }

    private String setFreq(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        // Parse as double first to handle floating point format (e.g., "28074055.000000")
        double freqDouble = Double.parseDouble(args.trim());
        long freq = Math.round(freqDouble);
        synchronized (rigLock) {
            rig.setFrequency(VFO.MAIN, freq);
        }
        return "RPRT " + RPRT_OK + "\n";
    }

    // ========================================================================
    // Mode Commands
    // ========================================================================

    private String getMode() throws CatException {
        synchronized (rigLock) {
            OperatingMode mode = rig.getMode(VFO.MAIN);
            String hamlibMode = mapModeToHamlib(mode);
            return hamlibMode + "\n0\n";
        }
    }

    private String setMode(String args) throws CatException {
        String[] parts = args.split("\\s+");
        if (parts.length < 1 || parts[0].isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        String modeName = parts[0].toUpperCase();

        // Map Hamlib mode names to OperatingMode
        OperatingMode mode = mapHamlibMode(modeName);
        if (mode == null) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }

        synchronized (rigLock) {
            rig.setMode(VFO.MAIN, mode);
        }
        return "RPRT " + RPRT_OK + "\n";
    }

    private OperatingMode mapHamlibMode(String name) {
        OperatingMode mode = HAMLIB_TO_MODE.get(name);
        if (mode != null) {
            return mode;
        }
        // Try direct enum lookup as fallback
        try {
            return OperatingMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String mapModeToHamlib(OperatingMode mode) {
        return MODE_TO_HAMLIB.getOrDefault(mode, mode.name());
    }

    // ========================================================================
    // PTT Commands
    // ========================================================================

    private String getPtt() throws CatException {
        synchronized (rigLock) {
            boolean ptt = rig.isPtt();
            return (ptt ? "1" : "0") + "\n";
        }
    }

    private String setPtt(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        int ptt = Integer.parseInt(args.trim());
        synchronized (rigLock) {
            rig.setPtt(ptt > 0);
        }
        return "RPRT " + RPRT_OK + "\n";
    }

    // ========================================================================
    // VFO Commands
    // ========================================================================

    private String getVfo() throws CatException {
        synchronized (rigLock) {
            VFO vfo = rig.getActiveVfo();
            return (vfo == VFO.MAIN ? "VFOA" : "VFOB") + "\n";
        }
    }

    private String setVfo(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        String vfoName = args.trim().toUpperCase();
        VFO vfo = switch (vfoName) {
            case "VFOA", "MAIN", "A" -> VFO.MAIN;
            case "VFOB", "SUB", "B" -> VFO.SUB;
            default -> null;
        };
        if (vfo == null) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        synchronized (rigLock) {
            rig.setActiveVfo(vfo);
        }
        return "RPRT " + RPRT_OK + "\n";
    }

    // ========================================================================
    // RIT/XIT Commands (Jeremy Miller's Discovery)
    // Uses RC/TC commands since RT/XT return '?' on FTX-1 firmware
    // ========================================================================

    private String getRit() throws CatException {
        synchronized (rigLock) {
            int rit = rig.getRit();
            return rit + "\n";
        }
    }

    private String setRit(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        int rit = Integer.parseInt(args.trim());
        synchronized (rigLock) {
            rig.setRit(rit);
        }
        return "RPRT " + RPRT_OK + "\n";
    }

    private String getXit() throws CatException {
        synchronized (rigLock) {
            int xit = rig.getXit();
            return xit + "\n";
        }
    }

    private String setXit(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        int xit = Integer.parseInt(args.trim());
        synchronized (rigLock) {
            rig.setXit(xit);
        }
        return "RPRT " + RPRT_OK + "\n";
    }

    // ========================================================================
    // Memory Commands
    // ========================================================================

    private String getMem() throws CatException {
        synchronized (rigLock) {
            int channel = rig.getMemoryChannel();
            return channel + "\n";
        }
    }

    private String setMem(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        int channel = Integer.parseInt(args.trim());
        synchronized (rigLock) {
            rig.setMemoryChannel(channel);
        }
        return "RPRT " + RPRT_OK + "\n";
    }

    private String getChannel(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        int channel = Integer.parseInt(args.trim());
        synchronized (rigLock) {
            String data = rig.readMemoryChannel(channel);
            return data + "\n";
        }
    }

    private String setChannel(String args) throws CatException {
        // Full channel write is complex - return not implemented
        return "RPRT " + RPRT_ENAVAIL + "\n";
    }

    // ========================================================================
    // CTCSS/DCS Commands
    // ========================================================================

    private String getCtcssTone() throws CatException {
        synchronized (rigLock) {
            double tone = rig.getCtcssTone(true);  // TX tone
            // Return tone in deci-Hz (Hamlib format)
            return String.format("%.0f\n", tone * 10);
        }
    }

    private String setCtcssTone(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        // Hamlib sends tone in deci-Hz
        int toneDeciHz = Integer.parseInt(args.trim());
        double toneHz = toneDeciHz / 10.0;
        synchronized (rigLock) {
            rig.setCtcssToneHz(true, toneHz);
        }
        return "RPRT " + RPRT_OK + "\n";
    }

    private String getDcsCode() throws CatException {
        synchronized (rigLock) {
            int code = rig.getDcsCode(true);  // TX code
            return code + "\n";
        }
    }

    private String setDcsCode(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        int code = Integer.parseInt(args.trim());
        synchronized (rigLock) {
            rig.setDcsCode(true, code);
        }
        return "RPRT " + RPRT_OK + "\n";
    }

    // ========================================================================
    // Tuning Step Commands
    // ========================================================================

    private String getTs() throws CatException {
        // FTX-1 uses mode-specific steps via EX0306
        // Return 10 Hz as default
        return "10\n";
    }

    private String setTs(String args) throws CatException {
        // Tuning step is mode-specific on FTX-1
        // Accept but don't do anything - use EX0306 for mode-specific steps
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

        synchronized (rigLock) {
            return switch (level) {
                case "RFPOWER" -> {
                    double power = rig.getPower();
                    double maxPower = rig.getMaxPower();
                    yield String.format("%.2f\n", power / maxPower);
                }
                case "AF" -> {
                    int afGain = rig.getAFGain(VFO.MAIN);
                    yield String.format("%.2f\n", afGain / 255.0);
                }
                case "RF" -> {
                    int rfGain = rig.getRFGain(VFO.MAIN);
                    yield String.format("%.2f\n", rfGain / 255.0);
                }
                case "SQL" -> {
                    int sql = rig.getSquelch(VFO.MAIN);
                    yield String.format("%.2f\n", sql / 100.0);
                }
                case "STRENGTH", "S" -> {
                    int smeter = rig.getSmeter();
                    yield smeter + "\n";
                }
                case "SWR" -> {
                    int swr = rig.getMeter(MeterType.SWR);
                    yield String.format("%.1f\n", swr / 10.0);
                }
                case "ALC" -> {
                    int alc = rig.getMeter(MeterType.ALC);
                    yield alc + "\n";
                }
                case "COMP" -> {
                    int comp = rig.getMeter(MeterType.COMP);
                    yield comp + "\n";
                }
                case "MICGAIN" -> {
                    int micGain = rig.getMicGain();
                    yield String.format("%.2f\n", micGain / 100.0);
                }
                case "KEYSPD" -> {
                    int keySpeed = rig.getKeyerSpeed();
                    yield keySpeed + "\n";
                }
                case "VOXGAIN" -> {
                    int voxGain = rig.getVoxGain();
                    yield String.format("%.2f\n", voxGain / 100.0);
                }
                case "VOXDELAY" -> {
                    int voxDelay = rig.getVoxDelay();
                    yield voxDelay + "\n";
                }
                case "NR" -> {
                    int nrLevel = rig.getNoiseReductionLevel(VFO.MAIN);
                    yield String.format("%.2f\n", nrLevel / 15.0);
                }
                case "NB" -> {
                    int nbLevel = rig.getNoiseBlankerLevel(VFO.MAIN);
                    yield String.format("%.2f\n", nbLevel / 15.0);
                }
                case "NOTCHF" -> {
                    int notchFreq = rig.getManualNotchFrequency(VFO.MAIN);
                    yield notchFreq + "\n";
                }
                case "AGC" -> {
                    int agc = rig.getAgcMode(VFO.MAIN);
                    yield agc + "\n";
                }
                case "ATT" -> {
                    boolean attOn = rig.isAttenuatorOn(VFO.MAIN);
                    yield (attOn ? "12" : "0") + "\n";  // 12dB attenuator
                }
                case "PREAMP" -> {
                    int preamp = rig.getPreamp(VFO.MAIN);
                    yield (preamp * 10) + "\n";  // 0=IPO, 10=AMP1, 20=AMP2
                }
                case "MONITOR_GAIN" -> {
                    int monLevel = rig.getMonitorLevel(VFO.MAIN);
                    yield String.format("%.2f\n", monLevel / 100.0);
                }
                case "BKINDL" -> {
                    int bkindl = rig.getBreakInDelay();
                    yield bkindl + "\n";
                }
                default -> "RPRT " + RPRT_EINVAL + "\n";
            };
        }
    }

    private String setLevel(String args) throws CatException {
        String[] parts = args.split("\\s+");
        if (parts.length < 2) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }

        String level = parts[0].toUpperCase();
        String value = parts[1];

        synchronized (rigLock) {
            return switch (level) {
                case "RFPOWER" -> {
                    double powerNorm = Double.parseDouble(value);
                    double watts = powerNorm * rig.getMaxPower();
                    rig.setPower(watts);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "AF" -> {
                    double afNorm = Double.parseDouble(value);
                    rig.setAFGain(VFO.MAIN, (int) (afNorm * 255));
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "RF" -> {
                    double rfNorm = Double.parseDouble(value);
                    rig.setRFGain(VFO.MAIN, (int) (rfNorm * 255));
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "SQL" -> {
                    double sqlNorm = Double.parseDouble(value);
                    rig.setSquelch(VFO.MAIN, (int) (sqlNorm * 100));
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "MICGAIN" -> {
                    double micNorm = Double.parseDouble(value);
                    rig.setMicGain((int) (micNorm * 100));
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "KEYSPD" -> {
                    int keySpeed = Integer.parseInt(value);
                    rig.setKeyerSpeed(keySpeed);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "VOXGAIN" -> {
                    double voxNorm = Double.parseDouble(value);
                    rig.setVoxGain((int) (voxNorm * 100));
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "VOXDELAY" -> {
                    int voxDelay = Integer.parseInt(value);
                    rig.setVoxDelay(voxDelay);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "NR" -> {
                    double nrNorm = Double.parseDouble(value);
                    rig.setNoiseReductionLevel(VFO.MAIN, (int) (nrNorm * 15));
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "NB" -> {
                    double nbNorm = Double.parseDouble(value);
                    rig.setNoiseBlankerLevel(VFO.MAIN, (int) (nbNorm * 15));
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "NOTCHF" -> {
                    int notchFreq = Integer.parseInt(value);
                    rig.setManualNotchFrequency(VFO.MAIN, notchFreq);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "AGC" -> {
                    int agc = Integer.parseInt(value);
                    rig.setAgcMode(VFO.MAIN, agc);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "ATT" -> {
                    int att = Integer.parseInt(value);
                    rig.setAttenuator(VFO.MAIN, att > 0);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "PREAMP" -> {
                    int preamp = Integer.parseInt(value);
                    rig.setPreamp(VFO.MAIN, preamp / 10);  // 0, 10, 20 -> 0, 1, 2
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "MONITOR_GAIN" -> {
                    double monNorm = Double.parseDouble(value);
                    rig.setMonitorLevel(VFO.MAIN, (int) (monNorm * 100));
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "BKINDL" -> {
                    int bkindl = Integer.parseInt(value);
                    rig.setBreakInDelay(bkindl);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                default -> "RPRT " + RPRT_EINVAL + "\n";
            };
        }
    }

    // ========================================================================
    // Split Commands
    // ========================================================================

    private String getSplitVfo() throws CatException {
        synchronized (rigLock) {
            boolean split = rig.isSplitEnabled();
            return (split ? "1" : "0") + "\nVFOB\n";
        }
    }

    private String setSplitVfo(String args) throws CatException {
        String[] parts = args.split("\\s+");
        if (parts.length < 1 || parts[0].isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        int split = Integer.parseInt(parts[0]);
        synchronized (rigLock) {
            rig.setSplit(split > 0);
        }
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

        synchronized (rigLock) {
            return switch (func) {
                case "TUNER" -> {
                    // Only available with SPA-1
                    if (rig.hasSpa1()) {
                        yield "0\n";  // TODO: implement when API available
                    }
                    yield "RPRT " + RPRT_ENAVAIL + "\n";
                }
                case "LOCK" -> {
                    boolean locked = rig.isLocked();
                    yield (locked ? "1" : "0") + "\n";
                }
                case "COMP" -> {
                    boolean compOn = rig.isProcessorOn();
                    yield (compOn ? "1" : "0") + "\n";
                }
                case "VOX" -> {
                    boolean voxOn = rig.isVoxOn();
                    yield (voxOn ? "1" : "0") + "\n";
                }
                case "TONE" -> {
                    // CTCSS encode on/off
                    int mode = rig.getCtcssMode();
                    yield (mode == 1 ? "1" : "0") + "\n";  // 1 = ENCODE
                }
                case "TSQL" -> {
                    // Tone squelch on/off
                    int mode = rig.getCtcssMode();
                    yield (mode == 2 ? "1" : "0") + "\n";  // 2 = TSQ
                }
                case "NB" -> {
                    boolean nbOn = rig.isNoiseBlankerOn(VFO.MAIN);
                    yield (nbOn ? "1" : "0") + "\n";
                }
                case "NR" -> {
                    boolean nrOn = rig.isNoiseReductionOn(VFO.MAIN);
                    yield (nrOn ? "1" : "0") + "\n";
                }
                case "ANF" -> {
                    boolean anfOn = rig.isAutoNotchOn(VFO.MAIN);
                    yield (anfOn ? "1" : "0") + "\n";
                }
                case "APF" -> {
                    boolean apfOn = rig.isApfOn(VFO.MAIN);
                    yield (apfOn ? "1" : "0") + "\n";
                }
                case "MON", "MN" -> {
                    int monLevel = rig.getMonitorLevel(VFO.MAIN);
                    yield (monLevel > 0 ? "1" : "0") + "\n";
                }
                case "RIT" -> {
                    int rit = rig.getRit();
                    yield (rit != 0 ? "1" : "0") + "\n";
                }
                case "XIT" -> {
                    int xit = rig.getXit();
                    yield (xit != 0 ? "1" : "0") + "\n";
                }
                case "SBKIN" -> {
                    int breakIn = rig.getBreakInMode();
                    yield (breakIn == 1 ? "1" : "0") + "\n";  // 1 = semi
                }
                case "FBKIN" -> {
                    int breakIn = rig.getBreakInMode();
                    yield (breakIn == 2 ? "1" : "0") + "\n";  // 2 = full (QSK)
                }
                default -> "RPRT " + RPRT_EINVAL + "\n";
            };
        }
    }

    private String setFunc(String args) throws CatException {
        String[] parts = args.split("\\s+");
        if (parts.length < 2) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }

        String func = parts[0].toUpperCase();
        int value = Integer.parseInt(parts[1]);

        synchronized (rigLock) {
            return switch (func) {
                case "TUNER" -> "RPRT " + RPRT_ENAVAIL + "\n";  // TODO: implement
                case "LOCK" -> {
                    rig.setLocked(value > 0);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "COMP" -> {
                    rig.setProcessorOn(value > 0);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "VOX" -> {
                    rig.setVoxOn(value > 0);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "TONE" -> {
                    // CTCSS encode: 0=off, 1=encode
                    rig.setCtcssMode(value > 0 ? 1 : 0);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "TSQL" -> {
                    // Tone squelch: 0=off, 2=TSQ
                    rig.setCtcssMode(value > 0 ? 2 : 0);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "NB" -> {
                    // 0=off, otherwise set to level 8 (middle)
                    rig.setNoiseBlankerLevel(VFO.MAIN, value > 0 ? 8 : 0);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "NR" -> {
                    // 0=off, otherwise set to level 8 (middle)
                    rig.setNoiseReductionLevel(VFO.MAIN, value > 0 ? 8 : 0);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "ANF" -> {
                    rig.setAutoNotch(VFO.MAIN, value > 0);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "APF" -> {
                    rig.setApfOn(VFO.MAIN, value > 0);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "MON", "MN" -> {
                    // 0=off, otherwise set to level 50 (middle)
                    rig.setMonitorLevel(VFO.MAIN, value > 0 ? 50 : 0);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "RIT" -> {
                    // RIT on/off - clear to 0 if off
                    if (value == 0) {
                        rig.setRit(0);
                    }
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "XIT" -> {
                    // XIT on/off - clear to 0 if off
                    if (value == 0) {
                        rig.setXit(0);
                    }
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "SBKIN" -> {
                    // Semi break-in: 0=off, 1=semi
                    rig.setBreakInMode(value > 0 ? 1 : 0);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                case "FBKIN" -> {
                    // Full break-in (QSK): 0=off, 2=full
                    rig.setBreakInMode(value > 0 ? 2 : 0);
                    yield "RPRT " + RPRT_OK + "\n";
                }
                default -> "RPRT " + RPRT_EINVAL + "\n";
            };
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

        synchronized (rigLock) {
            String response = rig.sendRawCommand(cmd);
            return response + "\n";
        }
    }

    // ========================================================================
    // Info Commands
    // ========================================================================

    private String getInfo() {
        synchronized (rigLock) {
            HeadType headType = rig.getHeadType();
            return "FTX-1 " + headType.getDisplayName() + "\n";
        }
    }

    private String dumpCaps() {
        synchronized (rigLock) {
            HeadType headType = rig.getHeadType();

            StringBuilder sb = new StringBuilder(512);
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
        sb.append("  j, get_rit           Get RIT offset (Hz)\n");
        sb.append("  J, set_rit OFFSET    Set RIT offset (Hz)\n");
        sb.append("  z, get_xit           Get XIT offset (Hz)\n");
        sb.append("  Z, set_xit OFFSET    Set XIT offset (Hz)\n");
        sb.append("  e, get_mem           Get memory channel\n");
        sb.append("  E, set_mem CH        Set memory channel\n");
        sb.append("  h, get_channel CH    Get channel data\n");
        sb.append("  c, get_ctcss_tone    Get CTCSS tone (deci-Hz)\n");
        sb.append("  C, set_ctcss_tone    Set CTCSS tone (deci-Hz)\n");
        sb.append("  d, get_dcs_code      Get DCS code\n");
        sb.append("  D, set_dcs_code      Set DCS code\n");
        sb.append("  n, get_ts            Get tuning step\n");
        sb.append("  N, set_ts STEP       Set tuning step\n");
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
            vfo = switch (vfoName) {
                case "VFOB", "SUB", "B" -> VFO.SUB;
                default -> VFO.MAIN;
            };
        }

        synchronized (rigLock) {
            long freq = rig.getFrequency(vfo);
            OperatingMode mode = rig.getMode(vfo);
            String hamlibVfo = (vfo == VFO.MAIN) ? "VFOA" : "VFOB";

            StringBuilder sb = new StringBuilder(128);
            sb.append("Freq=").append(freq).append("\n");
            sb.append("Mode=").append(mapModeToHamlib(mode)).append("\n");
            sb.append("Width=0\n");
            sb.append("VFO=").append(hamlibVfo).append("\n");
            return sb.toString();
        }
    }

    /**
     * Gets rig info in key=value format.
     */
    private String getRigInfo() {
        synchronized (rigLock) {
            HeadType headType = rig.getHeadType();

            StringBuilder sb = new StringBuilder(256);
            sb.append("Model=FTX-1\n");
            sb.append("Mfg=Yaesu\n");
            sb.append("HeadType=").append(headType.getDisplayName()).append("\n");
            sb.append("MinPower=").append(headType.getMinPowerWatts()).append("\n");
            sb.append("MaxPower=").append(headType.getMaxPowerWatts()).append("\n");
            sb.append("HasTuner=").append(headType.hasInternalTuner() ? "1" : "0").append("\n");

            return sb.toString();
        }
    }

    /**
     * Gets split mode and passband.
     */
    private String getSplitMode() throws CatException {
        synchronized (rigLock) {
            OperatingMode mode = rig.getMode(VFO.SUB);
            return mapModeToHamlib(mode) + "\n0\n";
        }
    }

    /**
     * Sets split mode and passband.
     */
    private String setSplitMode(String args) throws CatException {
        String[] parts = args.split("\\s+");
        if (parts.length < 1 || parts[0].isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        OperatingMode mode = mapHamlibMode(parts[0].toUpperCase());
        if (mode == null) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        synchronized (rigLock) {
            rig.setMode(VFO.SUB, mode);
        }
        return "RPRT " + RPRT_OK + "\n";
    }

    /**
     * Gets split TX frequency.
     */
    private String getSplitFreq() throws CatException {
        synchronized (rigLock) {
            long freq = rig.getFrequency(VFO.SUB);
            return freq + "\n";
        }
    }

    /**
     * Sets split TX frequency.
     */
    private String setSplitFreq(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        double freqDouble = Double.parseDouble(args.trim());
        long freq = Math.round(freqDouble);
        synchronized (rigLock) {
            rig.setFrequency(VFO.SUB, freq);
        }
        return "RPRT " + RPRT_OK + "\n";
    }

    /**
     * Gets split frequency and mode together.
     */
    private String getSplitFreqMode() throws CatException {
        synchronized (rigLock) {
            long freq = rig.getFrequency(VFO.SUB);
            OperatingMode mode = rig.getMode(VFO.SUB);
            return freq + "\n" + mapModeToHamlib(mode) + "\n0\n";
        }
    }

    /**
     * Sets split frequency and mode together.
     */
    private String setSplitFreqMode(String args) throws CatException {
        String[] parts = args.split("\\s+");
        if (parts.length < 2) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        double freqDouble = Double.parseDouble(parts[0]);
        long freq = Math.round(freqDouble);
        OperatingMode mode = mapHamlibMode(parts[1].toUpperCase());
        if (mode == null) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }

        synchronized (rigLock) {
            rig.setFrequency(VFO.SUB, freq);
            rig.setMode(VFO.SUB, mode);
        }
        return "RPRT " + RPRT_OK + "\n";
    }

    /**
     * Gets rig clock (not supported, return current time).
     */
    private String getClock() {
        return java.time.LocalDateTime.now().toString() + "\n";
    }

    /**
     * Sets rig clock (not supported).
     */
    private String setClock(String args) {
        return "RPRT " + RPRT_OK + "\n";
    }

    /**
     * Gets lock mode status.
     */
    private String getLockMode() throws CatException {
        synchronized (rigLock) {
            boolean locked = rig.isLocked();
            return (locked ? "1" : "0") + "\n";
        }
    }

    /**
     * Sets lock mode.
     */
    private String setLockMode(String args) throws CatException {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        int lock = Integer.parseInt(args.trim());
        synchronized (rigLock) {
            rig.setLocked(lock > 0);
        }
        return "RPRT " + RPRT_OK + "\n";
    }

    /**
     * Sends Morse code (CW keying).
     */
    private String sendMorse(String args) {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        try {
            synchronized (rigLock) {
                rig.sendRawCommand("KY" + args.trim());
            }
            return "RPRT " + RPRT_OK + "\n";
        } catch (CatException e) {
            return "RPRT " + RPRT_ENAVAIL + "\n";
        }
    }

    /**
     * Stops Morse sending.
     */
    private String stopMorse() {
        try {
            synchronized (rigLock) {
                rig.sendRawCommand("KY");
            }
            return "RPRT " + RPRT_OK + "\n";
        } catch (CatException e) {
            return "RPRT " + RPRT_ENAVAIL + "\n";
        }
    }

    /**
     * Waits for Morse to complete.
     */
    private String waitMorse() {
        return "RPRT " + RPRT_OK + "\n";
    }

    /**
     * Plays voice memory.
     */
    private String sendVoiceMem(String args) {
        if (args.isEmpty()) {
            return "RPRT " + RPRT_EINVAL + "\n";
        }
        try {
            int mem = Integer.parseInt(args.trim());
            if (mem < 1 || mem > 5) {
                return "RPRT " + RPRT_EINVAL + "\n";
            }
            synchronized (rigLock) {
                rig.sendRawCommand("PB" + mem);
            }
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
    private String halt() {
        try {
            synchronized (rigLock) {
                rig.setPtt(false);
            }
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
                Thread.sleep(Math.min(ms, 5000));
            } catch (NumberFormatException | InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return "RPRT " + RPRT_OK + "\n";
    }

    /**
     * Dumps the rig state in rigctld protocol format.
     * This is the main command WSJT-X uses to discover rig capabilities.
     */
    private String dumpState() {
        HeadType headType;
        synchronized (rigLock) {
            headType = rig.getHeadType();
        }
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
