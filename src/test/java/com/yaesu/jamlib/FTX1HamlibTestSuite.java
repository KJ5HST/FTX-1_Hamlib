/*
 * FTX-1 Hamlib Command Test Suite
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 *
 * Tests all supported Hamlib commands for FTX-1.
 * Requires actual radio connection for live tests.
 */
package com.yaesu.jamlib;

import com.yaesu.ftx1.FTX1;
import com.yaesu.ftx1.exception.CatException;
import com.yaesu.ftx1.model.HeadType;
import com.yaesu.ftx1.model.MeterType;
import com.yaesu.ftx1.model.OperatingMode;
import com.yaesu.ftx1.model.VFO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for FTX-1 Hamlib commands.
 * <p>
 * Tests are organized by command category matching the Hamlib/rigctl structure.
 * </p>
 * <p>
 * To run live tests, set environment variable:
 * <pre>
 * export FTX1_PORT=/dev/cu.SLAB_USBtoUART
 * mvn test
 * </pre>
 * </p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("FTX-1 Hamlib Command Tests")
public class FTX1HamlibTestSuite {

    private static FTX1 rig;
    private static RigctlCommandHandler handler;
    private static String serialPort;
    private static final int BAUD_RATE = 38400;

    // Test frequencies (in Hz)
    private static final long FREQ_20M = 14_074_000;
    private static final long FREQ_40M = 7_074_000;
    private static final long FREQ_2M = 146_520_000;
    private static final long FREQ_70CM = 446_000_000;

    @BeforeAll
    static void setup() {
        serialPort = System.getenv("FTX1_PORT");
        if (serialPort != null && !serialPort.isEmpty()) {
            try {
                System.out.println("Connecting to FTX-1 on " + serialPort);
                rig = FTX1.connect(serialPort, BAUD_RATE);
                handler = new RigctlCommandHandler(rig, true);
                System.out.println("Connected. Head type: " + rig.getHeadType());
            } catch (Exception e) {
                System.err.println("Failed to connect: " + e.getMessage());
                rig = null;
            }
        } else {
            System.out.println("FTX1_PORT not set - skipping live tests");
        }
    }

    @AfterAll
    static void teardown() {
        if (rig != null) {
            rig.close();
            System.out.println("Disconnected from FTX-1");
        }
    }

    // ========================================================================
    // Connection & Info Tests
    // ========================================================================

    @Nested
    @DisplayName("Connection & Info Commands")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ConnectionTests {

        @Test
        @Order(1)
        @DisplayName("get_info (_) - Get rig info string")
        void testGetInfo() {
            assumeConnected();
            String response = handler.handleCommand("_");
            assertNotNull(response);
            assertTrue(response.startsWith("FTX-1"), "Response should start with FTX-1");
            System.out.println("get_info: " + response.trim());
        }

        @Test
        @Order(2)
        @DisplayName("dump_caps (1) - Dump rig capabilities")
        void testDumpCaps() {
            assumeConnected();
            String response = handler.handleCommand("1");
            assertNotNull(response);
            assertTrue(response.contains("Model name:"), "Response should contain model name");
            assertTrue(response.contains("FTX-1"), "Response should contain FTX-1");
            assertTrue(response.contains("Yaesu"), "Response should contain manufacturer");
            System.out.println("dump_caps:\n" + response);
        }

        @Test
        @Order(3)
        @DisplayName("Head type detection")
        void testHeadTypeDetection() {
            assumeConnected();
            HeadType headType = rig.getHeadType();
            assertNotNull(headType);
            assertNotEquals(HeadType.UNKNOWN, headType);
            System.out.println("Head type: " + headType.getDisplayName());
            System.out.println("Power range: " + headType.getMinPowerWatts() + "-" + headType.getMaxPowerWatts() + "W");
        }
    }

    // ========================================================================
    // Frequency Tests
    // ========================================================================

    @Nested
    @DisplayName("Frequency Commands")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class FrequencyTests {

        private long originalFreq;

        @BeforeEach
        void saveFrequency() throws CatException {
            if (rig != null) {
                originalFreq = rig.getFrequency(VFO.MAIN);
            }
        }

        @AfterEach
        void restoreFrequency() throws CatException {
            if (rig != null) {
                rig.setFrequency(VFO.MAIN, originalFreq);
            }
        }

        @Test
        @Order(1)
        @DisplayName("get_freq (f) - Get frequency")
        void testGetFreq() {
            assumeConnected();
            String response = handler.handleCommand("f");
            assertNotNull(response);
            long freq = Long.parseLong(response.trim());
            assertTrue(freq > 0, "Frequency should be positive");
            System.out.println("get_freq: " + freq + " Hz");
        }

        @Test
        @Order(2)
        @DisplayName("set_freq (F) - Set frequency to 20m")
        void testSetFreq20m() {
            assumeConnected();
            String response = handler.handleCommand("F " + FREQ_20M);
            assertEquals("RPRT 0\n", response, "Should return success");

            // Verify
            String getResponse = handler.handleCommand("f");
            long freq = Long.parseLong(getResponse.trim());
            assertEquals(FREQ_20M, freq, "Frequency should be set to 20m");
            System.out.println("set_freq 20m: OK");
        }

        @Test
        @Order(3)
        @DisplayName("set_freq (F) - Set frequency to 40m")
        void testSetFreq40m() {
            assumeConnected();
            String response = handler.handleCommand("F " + FREQ_40M);
            assertEquals("RPRT 0\n", response);

            String getResponse = handler.handleCommand("f");
            long freq = Long.parseLong(getResponse.trim());
            assertEquals(FREQ_40M, freq);
            System.out.println("set_freq 40m: OK");
        }

        @Test
        @Order(4)
        @DisplayName("set_freq (F) - Set frequency to 2m")
        void testSetFreq2m() {
            assumeConnected();
            String response = handler.handleCommand("F " + FREQ_2M);
            assertEquals("RPRT 0\n", response);

            String getResponse = handler.handleCommand("f");
            long freq = Long.parseLong(getResponse.trim());
            assertEquals(FREQ_2M, freq);
            System.out.println("set_freq 2m: OK");
        }

        @Test
        @Order(5)
        @DisplayName("set_freq (F) - Set frequency to 70cm")
        void testSetFreq70cm() {
            assumeConnected();
            String response = handler.handleCommand("F " + FREQ_70CM);
            assertEquals("RPRT 0\n", response);

            String getResponse = handler.handleCommand("f");
            long freq = Long.parseLong(getResponse.trim());
            assertEquals(FREQ_70CM, freq);
            System.out.println("set_freq 70cm: OK");
        }
    }

    // ========================================================================
    // Mode Tests
    // ========================================================================

    @Nested
    @DisplayName("Mode Commands")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ModeTests {

        private OperatingMode originalMode;

        @BeforeEach
        void saveMode() throws CatException {
            if (rig != null) {
                originalMode = rig.getMode(VFO.MAIN);
            }
        }

        @AfterEach
        void restoreMode() throws CatException {
            if (rig != null) {
                rig.setMode(VFO.MAIN, originalMode);
            }
        }

        @Test
        @Order(1)
        @DisplayName("get_mode (m) - Get operating mode")
        void testGetMode() {
            assumeConnected();
            String response = handler.handleCommand("m");
            assertNotNull(response);
            String[] lines = response.split("\n");
            assertTrue(lines.length >= 1, "Should return mode");
            System.out.println("get_mode: " + lines[0]);
        }

        @Test
        @Order(2)
        @DisplayName("set_mode (M) - Set USB mode")
        void testSetModeUSB() {
            assumeConnected();
            String response = handler.handleCommand("M USB 0");
            assertEquals("RPRT 0\n", response);

            String getResponse = handler.handleCommand("m");
            assertTrue(getResponse.contains("USB"));
            System.out.println("set_mode USB: OK");
        }

        @Test
        @Order(3)
        @DisplayName("set_mode (M) - Set LSB mode")
        void testSetModeLSB() {
            assumeConnected();
            String response = handler.handleCommand("M LSB 0");
            assertEquals("RPRT 0\n", response);

            String getResponse = handler.handleCommand("m");
            assertTrue(getResponse.contains("LSB"));
            System.out.println("set_mode LSB: OK");
        }

        @Test
        @Order(4)
        @DisplayName("set_mode (M) - Set CW mode")
        void testSetModeCW() {
            assumeConnected();
            String response = handler.handleCommand("M CW 0");
            assertEquals("RPRT 0\n", response);

            String getResponse = handler.handleCommand("m");
            assertTrue(getResponse.contains("CW"));
            System.out.println("set_mode CW: OK");
        }

        @Test
        @Order(5)
        @DisplayName("set_mode (M) - Set AM mode")
        void testSetModeAM() {
            assumeConnected();
            String response = handler.handleCommand("M AM 0");
            assertEquals("RPRT 0\n", response);

            String getResponse = handler.handleCommand("m");
            assertTrue(getResponse.contains("AM"));
            System.out.println("set_mode AM: OK");
        }

        @Test
        @Order(6)
        @DisplayName("set_mode (M) - Set FM mode")
        void testSetModeFM() {
            assumeConnected();
            // First set to VHF/UHF for FM
            handler.handleCommand("F " + FREQ_2M);
            String response = handler.handleCommand("M FM 0");
            assertEquals("RPRT 0\n", response);

            String getResponse = handler.handleCommand("m");
            assertTrue(getResponse.contains("FM"));
            System.out.println("set_mode FM: OK");
        }

        @Test
        @Order(7)
        @DisplayName("set_mode (M) - Set PKTUSB (DATA-U) mode")
        void testSetModePKTUSB() {
            assumeConnected();
            handler.handleCommand("F " + FREQ_20M);  // Set to HF
            String response = handler.handleCommand("M PKTUSB 0");
            assertEquals("RPRT 0\n", response);

            String getResponse = handler.handleCommand("m");
            assertTrue(getResponse.contains("DATA") || getResponse.contains("PKT"));
            System.out.println("set_mode PKTUSB: OK");
        }

        @Test
        @Order(8)
        @DisplayName("set_mode (M) - Set RTTY mode")
        void testSetModeRTTY() {
            assumeConnected();
            String response = handler.handleCommand("M RTTY 0");
            assertEquals("RPRT 0\n", response);

            String getResponse = handler.handleCommand("m");
            assertTrue(getResponse.contains("RTTY"));
            System.out.println("set_mode RTTY: OK");
        }
    }

    // ========================================================================
    // VFO Tests
    // ========================================================================

    @Nested
    @DisplayName("VFO Commands")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class VFOTests {

        @Test
        @Order(1)
        @DisplayName("get_vfo (v) - Get active VFO")
        void testGetVfo() {
            assumeConnected();
            String response = handler.handleCommand("v");
            assertNotNull(response);
            assertTrue(response.contains("VFO"), "Should return VFO name");
            System.out.println("get_vfo: " + response.trim());
        }

        @Test
        @Order(2)
        @DisplayName("set_vfo (V) - Set VFO A")
        void testSetVfoA() {
            assumeConnected();
            String response = handler.handleCommand("V VFOA");
            assertEquals("RPRT 0\n", response);

            String getResponse = handler.handleCommand("v");
            assertTrue(getResponse.contains("VFOA"));
            System.out.println("set_vfo VFOA: OK");
        }

        @Test
        @Order(3)
        @DisplayName("set_vfo (V) - Set VFO B")
        void testSetVfoB() {
            assumeConnected();
            String response = handler.handleCommand("V VFOB");
            assertEquals("RPRT 0\n", response);

            String getResponse = handler.handleCommand("v");
            assertTrue(getResponse.contains("VFOB"));
            System.out.println("set_vfo VFOB: OK");

            // Restore to VFO A
            handler.handleCommand("V VFOA");
        }
    }

    // ========================================================================
    // Split Tests
    // ========================================================================

    @Nested
    @DisplayName("Split Commands")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class SplitTests {

        @Test
        @Order(1)
        @DisplayName("get_split_vfo (s) - Get split status")
        void testGetSplit() {
            assumeConnected();
            String response = handler.handleCommand("s");
            assertNotNull(response);
            String[] lines = response.split("\n");
            assertTrue(lines.length >= 1);
            assertTrue(lines[0].equals("0") || lines[0].equals("1"));
            System.out.println("get_split_vfo: " + lines[0]);
        }

        @Test
        @Order(2)
        @DisplayName("set_split_vfo (S) - Enable split")
        void testSetSplitOn() {
            assumeConnected();
            String response = handler.handleCommand("S 1 VFOB");
            assertEquals("RPRT 0\n", response);

            String getResponse = handler.handleCommand("s");
            assertTrue(getResponse.startsWith("1"));
            System.out.println("set_split_vfo 1: OK");
        }

        @Test
        @Order(3)
        @DisplayName("set_split_vfo (S) - Disable split")
        void testSetSplitOff() {
            assumeConnected();
            String response = handler.handleCommand("S 0");
            assertEquals("RPRT 0\n", response);

            String getResponse = handler.handleCommand("s");
            assertTrue(getResponse.startsWith("0"));
            System.out.println("set_split_vfo 0: OK");
        }
    }

    // ========================================================================
    // Level Tests
    // ========================================================================

    @Nested
    @DisplayName("Level Commands")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class LevelTests {

        @Test
        @Order(1)
        @DisplayName("get_level RFPOWER - Get RF power level")
        void testGetLevelRFPower() {
            assumeConnected();
            String response = handler.handleCommand("l RFPOWER");
            assertNotNull(response);
            double power = Double.parseDouble(response.trim());
            assertTrue(power >= 0.0 && power <= 1.0, "Power should be 0-1 normalized");
            System.out.println("get_level RFPOWER: " + power);
        }

        @Test
        @Order(2)
        @DisplayName("set_level RFPOWER - Set RF power to 50%")
        void testSetLevelRFPower() {
            assumeConnected();
            String response = handler.handleCommand("L RFPOWER 0.5");
            assertEquals("RPRT 0\n", response);

            String getResponse = handler.handleCommand("l RFPOWER");
            double power = Double.parseDouble(getResponse.trim());
            assertTrue(Math.abs(power - 0.5) < 0.1, "Power should be around 50%");
            System.out.println("set_level RFPOWER 0.5: OK (actual: " + power + ")");
        }

        @Test
        @Order(3)
        @DisplayName("get_level AF - Get AF gain level")
        void testGetLevelAF() {
            assumeConnected();
            String response = handler.handleCommand("l AF");
            assertNotNull(response);
            double af = Double.parseDouble(response.trim());
            assertTrue(af >= 0.0 && af <= 1.0, "AF should be 0-1 normalized");
            System.out.println("get_level AF: " + af);
        }

        @Test
        @Order(4)
        @DisplayName("set_level AF - Set AF gain to 50%")
        void testSetLevelAF() {
            assumeConnected();
            String response = handler.handleCommand("L AF 0.5");
            assertEquals("RPRT 0\n", response);
            System.out.println("set_level AF 0.5: OK");
        }

        @Test
        @Order(5)
        @DisplayName("get_level SQL - Get squelch level")
        void testGetLevelSQL() {
            assumeConnected();
            String response = handler.handleCommand("l SQL");
            assertNotNull(response);
            double sql = Double.parseDouble(response.trim());
            assertTrue(sql >= 0.0 && sql <= 1.0, "SQL should be 0-1 normalized");
            System.out.println("get_level SQL: " + sql);
        }

        @Test
        @Order(6)
        @DisplayName("get_level STRENGTH - Get S-meter reading")
        void testGetLevelStrength() {
            assumeConnected();
            String response = handler.handleCommand("l STRENGTH");
            assertNotNull(response);
            int strength = Integer.parseInt(response.trim());
            System.out.println("get_level STRENGTH: " + strength + " dB");
        }

        @Test
        @Order(7)
        @DisplayName("get_level SWR - Get SWR meter")
        void testGetLevelSWR() {
            assumeConnected();
            String response = handler.handleCommand("l SWR");
            assertNotNull(response);
            double swr = Double.parseDouble(response.trim());
            System.out.println("get_level SWR: " + swr);
        }

        @Test
        @Order(8)
        @DisplayName("get_level ALC - Get ALC meter")
        void testGetLevelALC() {
            assumeConnected();
            String response = handler.handleCommand("l ALC");
            assertNotNull(response);
            System.out.println("get_level ALC: " + response.trim());
        }

        @Test
        @Order(9)
        @DisplayName("get_level COMP - Get COMP meter")
        void testGetLevelCOMP() {
            assumeConnected();
            String response = handler.handleCommand("l COMP");
            assertNotNull(response);
            System.out.println("get_level COMP: " + response.trim());
        }
    }

    // ========================================================================
    // Function Tests
    // ========================================================================

    @Nested
    @DisplayName("Function Commands")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class FunctionTests {

        @Test
        @Order(1)
        @DisplayName("get_func LOCK - Get lock status")
        void testGetFuncLock() {
            assumeConnected();
            String response = handler.handleCommand("u LOCK");
            assertNotNull(response);
            String status = response.trim();
            assertTrue(status.equals("0") || status.equals("1"));
            System.out.println("get_func LOCK: " + status);
        }

        @Test
        @Order(2)
        @DisplayName("set_func LOCK 1 - Enable lock")
        void testSetFuncLockOn() {
            assumeConnected();
            String response = handler.handleCommand("U LOCK 1");
            assertEquals("RPRT 0\n", response);

            String getResponse = handler.handleCommand("u LOCK");
            assertEquals("1\n", getResponse);
            System.out.println("set_func LOCK 1: OK");
        }

        @Test
        @Order(3)
        @DisplayName("set_func LOCK 0 - Disable lock")
        void testSetFuncLockOff() {
            assumeConnected();
            String response = handler.handleCommand("U LOCK 0");
            assertEquals("RPRT 0\n", response);

            String getResponse = handler.handleCommand("u LOCK");
            assertEquals("0\n", getResponse);
            System.out.println("set_func LOCK 0: OK");
        }

        @Test
        @Order(4)
        @DisplayName("get_func TUNER - Get tuner status (SPA-1 only)")
        void testGetFuncTuner() {
            assumeConnected();
            String response = handler.handleCommand("u TUNER");
            if (rig.hasSpa1()) {
                assertNotNull(response);
                System.out.println("get_func TUNER: " + response.trim());
            } else {
                assertTrue(response.contains("RPRT -11"), "Should return ENAVAIL for Field Head");
                System.out.println("get_func TUNER: Not available (Field Head)");
            }
        }
    }

    // ========================================================================
    // PTT Tests (CAUTION: These cause transmission!)
    // ========================================================================

    @Nested
    @DisplayName("PTT Commands (CAUTION: TX)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @Disabled("PTT tests disabled by default - enable manually with proper load")
    class PTTTests {

        @Test
        @Order(1)
        @DisplayName("get_ptt (t) - Get PTT status")
        void testGetPtt() {
            assumeConnected();
            String response = handler.handleCommand("t");
            assertNotNull(response);
            String status = response.trim();
            assertTrue(status.equals("0") || status.equals("1"));
            System.out.println("get_ptt: " + status);
        }

        @Test
        @Order(2)
        @DisplayName("set_ptt (T) - PTT on/off cycle")
        @Disabled("Enable only with dummy load connected!")
        void testSetPtt() throws Exception {
            assumeConnected();

            // PTT ON
            String response = handler.handleCommand("T 1");
            assertEquals("RPRT 0\n", response);

            // Brief TX
            Thread.sleep(500);

            // PTT OFF
            response = handler.handleCommand("T 0");
            assertEquals("RPRT 0\n", response);

            System.out.println("set_ptt cycle: OK");
        }
    }

    // ========================================================================
    // Raw Command Tests
    // ========================================================================

    @Nested
    @DisplayName("Raw Command (send_cmd)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class RawCommandTests {

        @Test
        @Order(1)
        @DisplayName("send_cmd (w) - Send ID command")
        void testSendCmdID() {
            assumeConnected();
            String response = handler.handleCommand("w ID");
            assertNotNull(response);
            assertTrue(response.startsWith("ID"), "Should return ID response");
            System.out.println("send_cmd ID: " + response.trim());
        }

        @Test
        @Order(2)
        @DisplayName("send_cmd (w) - Send FA query")
        void testSendCmdFA() {
            assumeConnected();
            String response = handler.handleCommand("w FA");
            assertNotNull(response);
            assertTrue(response.startsWith("FA"), "Should return FA response");
            System.out.println("send_cmd FA: " + response.trim());
        }

        @Test
        @Order(3)
        @DisplayName("send_cmd (w) - Send PC query (power)")
        void testSendCmdPC() {
            assumeConnected();
            String response = handler.handleCommand("w PC");
            assertNotNull(response);
            assertTrue(response.startsWith("PC"), "Should return PC response");
            System.out.println("send_cmd PC: " + response.trim());
        }
    }

    // ========================================================================
    // Error Handling Tests
    // ========================================================================

    @Nested
    @DisplayName("Error Handling")
    class ErrorTests {

        @Test
        @DisplayName("Invalid command returns error")
        void testInvalidCommand() {
            assumeConnected();
            String response = handler.handleCommand("invalid_cmd");
            assertTrue(response.contains("RPRT -1"), "Should return EINVAL");
        }

        @Test
        @DisplayName("Empty command returns error")
        void testEmptyCommand() {
            assumeConnected();
            String response = handler.handleCommand("");
            assertTrue(response.contains("RPRT -1"), "Should return EINVAL");
        }

        @Test
        @DisplayName("Missing argument returns error")
        void testMissingArgument() {
            assumeConnected();
            String response = handler.handleCommand("F");  // set_freq without frequency
            assertTrue(response.contains("RPRT -1"), "Should return EINVAL");
        }

        @Test
        @DisplayName("Invalid level returns error")
        void testInvalidLevel() {
            assumeConnected();
            String response = handler.handleCommand("l INVALID_LEVEL");
            assertTrue(response.contains("RPRT -1"), "Should return EINVAL");
        }
    }

    // ========================================================================
    // All Modes Test
    // ========================================================================

    @Nested
    @DisplayName("All Mode Cycle Test")
    class AllModesTest {

        @Test
        @DisplayName("Cycle through all HF modes")
        void testAllHFModes() {
            assumeConnected();

            // Set to HF frequency first
            handler.handleCommand("F " + FREQ_20M);

            List<String> modes = Arrays.asList(
                    "USB", "LSB", "CW", "CWR", "AM", "RTTY", "RTTYR", "PKTUSB", "PKTLSB"
            );

            StringBuilder results = new StringBuilder();
            results.append("HF Mode cycle test:\n");

            for (String mode : modes) {
                String setResponse = handler.handleCommand("M " + mode + " 0");
                String getResponse = handler.handleCommand("m");
                String actualMode = getResponse.split("\n")[0];

                boolean success = setResponse.equals("RPRT 0\n");
                results.append(String.format("  %s -> %s [%s]\n",
                        mode, actualMode, success ? "OK" : "FAIL"));
            }

            System.out.println(results);
        }

        @Test
        @DisplayName("Cycle through VHF/UHF modes")
        void testAllVHFModes() {
            assumeConnected();

            // Set to VHF frequency
            handler.handleCommand("F " + FREQ_2M);

            List<String> modes = Arrays.asList(
                    "FM", "USB", "LSB", "CW", "AM", "PKTFM"
            );

            StringBuilder results = new StringBuilder();
            results.append("VHF Mode cycle test:\n");

            for (String mode : modes) {
                String setResponse = handler.handleCommand("M " + mode + " 0");
                String getResponse = handler.handleCommand("m");
                String actualMode = getResponse.split("\n")[0];

                boolean success = setResponse.equals("RPRT 0\n");
                results.append(String.format("  %s -> %s [%s]\n",
                        mode, actualMode, success ? "OK" : "FAIL"));
            }

            System.out.println(results);
        }
    }

    // ========================================================================
    // Band Scan Test
    // ========================================================================

    @Nested
    @DisplayName("Band Scan Test")
    class BandScanTest {

        @Test
        @DisplayName("Scan all amateur bands")
        void testAllBands() {
            assumeConnected();

            long[] bandFrequencies = {
                    1_900_000,    // 160m
                    3_700_000,    // 80m
                    7_150_000,    // 40m
                    10_125_000,   // 30m
                    14_200_000,   // 20m
                    18_100_000,   // 17m
                    21_200_000,   // 15m
                    24_900_000,   // 12m
                    28_500_000,   // 10m
                    50_100_000,   // 6m
                    146_520_000,  // 2m
                    446_000_000   // 70cm
            };

            String[] bandNames = {
                    "160m", "80m", "40m", "30m", "20m", "17m",
                    "15m", "12m", "10m", "6m", "2m", "70cm"
            };

            StringBuilder results = new StringBuilder();
            results.append("Band scan test:\n");

            for (int i = 0; i < bandFrequencies.length; i++) {
                String setResponse = handler.handleCommand("F " + bandFrequencies[i]);
                String getResponse = handler.handleCommand("f");
                long actualFreq = Long.parseLong(getResponse.trim());

                boolean success = setResponse.equals("RPRT 0\n") &&
                                  actualFreq == bandFrequencies[i];
                results.append(String.format("  %s (%,d Hz) [%s]\n",
                        bandNames[i], bandFrequencies[i], success ? "OK" : "FAIL"));
            }

            System.out.println(results);
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private void assumeConnected() {
        Assumptions.assumeTrue(rig != null, "Radio not connected - skipping test");
    }

    // ========================================================================
    // Main method for standalone execution
    // ========================================================================

    public static void main(String[] args) {
        System.out.println("FTX-1 Hamlib Command Test Suite");
        System.out.println("================================");
        System.out.println();

        if (args.length < 1) {
            System.out.println("Usage: java FTX1HamlibTestSuite <serial_port>");
            System.out.println("Example: java FTX1HamlibTestSuite /dev/cu.SLAB_USBtoUART");
            System.exit(1);
        }

        String port = args[0];
        System.out.println("Connecting to: " + port);

        try {
            FTX1 rig = FTX1.connect(port, BAUD_RATE);
            RigctlCommandHandler handler = new RigctlCommandHandler(rig, true);

            System.out.println("Connected! Head type: " + rig.getHeadType());
            System.out.println();

            // Run quick tests
            runQuickTests(handler, rig);

            rig.close();
            System.out.println("\nAll tests completed.");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runQuickTests(RigctlCommandHandler handler, FTX1 rig) {
        System.out.println("=== Quick Test Suite ===\n");

        // Info
        System.out.println("[INFO]");
        System.out.println("  get_info: " + handler.handleCommand("_").trim());

        // Frequency
        System.out.println("\n[FREQUENCY]");
        System.out.println("  get_freq: " + handler.handleCommand("f").trim() + " Hz");

        // Mode
        System.out.println("\n[MODE]");
        System.out.println("  get_mode: " + handler.handleCommand("m").split("\n")[0]);

        // VFO
        System.out.println("\n[VFO]");
        System.out.println("  get_vfo: " + handler.handleCommand("v").trim());

        // Split
        System.out.println("\n[SPLIT]");
        System.out.println("  get_split: " + handler.handleCommand("s").split("\n")[0]);

        // PTT
        System.out.println("\n[PTT]");
        System.out.println("  get_ptt: " + handler.handleCommand("t").trim());

        // Levels
        System.out.println("\n[LEVELS]");
        System.out.println("  RFPOWER: " + handler.handleCommand("l RFPOWER").trim());
        System.out.println("  AF: " + handler.handleCommand("l AF").trim());
        System.out.println("  SQL: " + handler.handleCommand("l SQL").trim());
        System.out.println("  STRENGTH: " + handler.handleCommand("l STRENGTH").trim() + " dB");

        // Functions
        System.out.println("\n[FUNCTIONS]");
        System.out.println("  LOCK: " + handler.handleCommand("u LOCK").trim());

        // Raw command
        System.out.println("\n[RAW]");
        System.out.println("  ID: " + handler.handleCommand("w ID").trim());
    }
}
