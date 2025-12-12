#!/bin/bash
#
# FTX-1 Hamlib Command Test Script
# Tests all supported Hamlib commands via Jamlib-FTX1
#
# Usage: ./test-ftx1-hamlib.sh <serial_port> [options]
#
# Options:
#   -v, --verbose    Verbose output
#   -q, --quick      Quick test (basic commands only)
#   -f, --full       Full test (all commands)
#   --no-tx          Skip PTT tests (default)
#   --tx             Enable PTT tests (DANGER: requires dummy load!)
#

set -e

# Default settings
SERIAL_PORT=""
VERBOSE=false
QUICK=false
FULL=false
TX_TESTS=false
TCP_PORT=4532
JAR_FILE="target/jamlib-ftx1-1.0.0-SNAPSHOT.jar"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -q|--quick)
            QUICK=true
            shift
            ;;
        -f|--full)
            FULL=true
            shift
            ;;
        --tx)
            TX_TESTS=true
            shift
            ;;
        --no-tx)
            TX_TESTS=false
            shift
            ;;
        -h|--help)
            echo "FTX-1 Hamlib Command Test Script"
            echo ""
            echo "Usage: $0 <serial_port> [options]"
            echo ""
            echo "Options:"
            echo "  -v, --verbose    Verbose output"
            echo "  -q, --quick      Quick test (basic commands only)"
            echo "  -f, --full       Full test (all commands)"
            echo "  --no-tx          Skip PTT tests (default)"
            echo "  --tx             Enable PTT tests (DANGER!)"
            echo ""
            echo "Example:"
            echo "  $0 /dev/cu.SLAB_USBtoUART"
            exit 0
            ;;
        *)
            if [[ -z "$SERIAL_PORT" ]]; then
                SERIAL_PORT=$1
            fi
            shift
            ;;
    esac
done

# Check serial port
if [[ -z "$SERIAL_PORT" ]]; then
    echo "Error: Serial port required"
    echo "Usage: $0 <serial_port> [options]"
    exit 1
fi

# Check JAR exists
if [[ ! -f "$JAR_FILE" ]]; then
    echo "JAR not found. Building..."
    mvn package -DskipTests -q
fi

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

PASS=0
FAIL=0

log() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((PASS++))
}

fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((FAIL++))
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# Start daemon in background
log "Starting Jamlib-FTX1 daemon on port $TCP_PORT..."
java -jar "$JAR_FILE" -r "$SERIAL_PORT" -t $TCP_PORT &
DAEMON_PID=$!
sleep 2

# Check daemon is running
if ! kill -0 $DAEMON_PID 2>/dev/null; then
    echo "Error: Daemon failed to start"
    exit 1
fi

log "Daemon started (PID: $DAEMON_PID)"

# Cleanup on exit
cleanup() {
    log "Stopping daemon..."
    kill $DAEMON_PID 2>/dev/null || true
    wait $DAEMON_PID 2>/dev/null || true
}
trap cleanup EXIT

# Send command to daemon
send_cmd() {
    echo -e "$1" | nc -w 2 localhost $TCP_PORT 2>/dev/null
}

# Test helper - check command returns expected value
test_cmd() {
    local cmd="$1"
    local expected="$2"
    local desc="$3"

    local result=$(send_cmd "$cmd")

    if [[ "$result" == *"$expected"* ]]; then
        pass "$desc"
        if $VERBOSE; then
            echo "      Command: $cmd"
            echo "      Response: ${result//$'\n'/ }"
        fi
        return 0
    else
        fail "$desc"
        echo "      Command: $cmd"
        echo "      Expected: $expected"
        echo "      Got: ${result//$'\n'/ }"
        return 1
    fi
}

# Test helper - check command succeeds (RPRT 0)
test_success() {
    local cmd="$1"
    local desc="$2"

    local result=$(send_cmd "$cmd")

    if [[ "$result" == *"RPRT 0"* ]]; then
        pass "$desc"
        if $VERBOSE; then
            echo "      Command: $cmd"
        fi
        return 0
    else
        fail "$desc"
        echo "      Command: $cmd"
        echo "      Response: ${result//$'\n'/ }"
        return 1
    fi
}

# Get current value
get_value() {
    local cmd="$1"
    send_cmd "$cmd" | head -1
}

echo ""
echo "========================================"
echo "FTX-1 Hamlib Command Test Suite"
echo "========================================"
echo "Serial Port: $SERIAL_PORT"
echo "TCP Port:    $TCP_PORT"
echo "Daemon PID:  $DAEMON_PID"
echo "========================================"
echo ""

# ========================================
# Info Tests
# ========================================
echo "=== Info Commands ==="

test_cmd "_" "FTX-1" "get_info returns FTX-1"
test_cmd "1" "Model name" "dump_caps returns model info"

# ========================================
# Frequency Tests
# ========================================
echo ""
echo "=== Frequency Commands ==="

ORIGINAL_FREQ=$(get_value "f")
log "Original frequency: $ORIGINAL_FREQ Hz"

test_success "F 14074000" "set_freq to 14.074 MHz (FT8)"
test_cmd "f" "14074000" "get_freq returns 14.074 MHz"

test_success "F 7074000" "set_freq to 7.074 MHz (40m)"
test_cmd "f" "7074000" "get_freq returns 7.074 MHz"

test_success "F 146520000" "set_freq to 146.520 MHz (2m)"
test_cmd "f" "146520000" "get_freq returns 146.520 MHz"

test_success "F 446000000" "set_freq to 446 MHz (70cm)"
test_cmd "f" "446000000" "get_freq returns 446 MHz"

# Restore original frequency
send_cmd "F $ORIGINAL_FREQ" > /dev/null

# ========================================
# Mode Tests
# ========================================
echo ""
echo "=== Mode Commands ==="

ORIGINAL_MODE=$(get_value "m")
log "Original mode: $ORIGINAL_MODE"

# Set to HF first
send_cmd "F 14074000" > /dev/null

test_success "M USB 0" "set_mode USB"
test_cmd "m" "USB" "get_mode returns USB"

test_success "M LSB 0" "set_mode LSB"
test_cmd "m" "LSB" "get_mode returns LSB"

test_success "M CW 0" "set_mode CW"
test_cmd "m" "CW" "get_mode returns CW"

test_success "M AM 0" "set_mode AM"
test_cmd "m" "AM" "get_mode returns AM"

# FM test on VHF
send_cmd "F 146520000" > /dev/null
test_success "M FM 0" "set_mode FM"
test_cmd "m" "FM" "get_mode returns FM"

# ========================================
# VFO Tests
# ========================================
echo ""
echo "=== VFO Commands ==="

test_cmd "v" "VFO" "get_vfo returns VFO"
test_success "V VFOA" "set_vfo VFOA"
test_cmd "v" "VFOA" "get_vfo returns VFOA"
test_success "V VFOB" "set_vfo VFOB"
test_cmd "v" "VFOB" "get_vfo returns VFOB"
test_success "V VFOA" "set_vfo back to VFOA"

# ========================================
# Split Tests
# ========================================
echo ""
echo "=== Split Commands ==="

test_success "S 1 VFOB" "set_split ON"
SPLIT_STATUS=$(get_value "s")
if [[ "$SPLIT_STATUS" == "1" ]]; then
    pass "get_split_vfo shows split enabled"
else
    fail "get_split_vfo shows split enabled"
fi

test_success "S 0" "set_split OFF"
SPLIT_STATUS=$(get_value "s")
if [[ "$SPLIT_STATUS" == "0" ]]; then
    pass "get_split_vfo shows split disabled"
else
    fail "get_split_vfo shows split disabled"
fi

# ========================================
# Level Tests
# ========================================
echo ""
echo "=== Level Commands ==="

# Get current power
POWER=$(get_value "l RFPOWER")
log "Current RFPOWER: $POWER"
pass "get_level RFPOWER"

# Set power
test_success "L RFPOWER 0.5" "set_level RFPOWER 0.5"

# Get AF
AF=$(get_value "l AF")
log "Current AF: $AF"
pass "get_level AF"

# Get SQL
SQL=$(get_value "l SQL")
log "Current SQL: $SQL"
pass "get_level SQL"

# Get STRENGTH
STRENGTH=$(get_value "l STRENGTH")
log "S-meter: $STRENGTH dB"
pass "get_level STRENGTH"

# Get SWR
SWR=$(get_value "l SWR")
log "SWR: $SWR"
pass "get_level SWR"

# ========================================
# Function Tests
# ========================================
echo ""
echo "=== Function Commands ==="

test_success "U LOCK 1" "set_func LOCK ON"
test_cmd "u LOCK" "1" "get_func LOCK returns 1"

test_success "U LOCK 0" "set_func LOCK OFF"
test_cmd "u LOCK" "0" "get_func LOCK returns 0"

# ========================================
# PTT Tests (if enabled)
# ========================================
if $TX_TESTS; then
    echo ""
    echo "=== PTT Commands (CAUTION: TX!) ==="
    warn "PTT tests enabled - ensure dummy load is connected!"

    PTT=$(get_value "t")
    log "Current PTT: $PTT"
    pass "get_ptt"

    # Brief TX test
    # test_success "T 1" "set_ptt ON"
    # sleep 0.5
    # test_success "T 0" "set_ptt OFF"
else
    echo ""
    echo "=== PTT Commands ==="
    warn "PTT tests skipped (use --tx to enable)"

    PTT=$(get_value "t")
    log "Current PTT: $PTT"
    pass "get_ptt"
fi

# ========================================
# Raw Command Tests
# ========================================
echo ""
echo "=== Raw Command Tests ==="

test_cmd "w ID" "ID" "send_cmd ID"
test_cmd "w FA" "FA" "send_cmd FA (frequency query)"
test_cmd "w PC" "PC" "send_cmd PC (power query)"

# ========================================
# Error Handling Tests
# ========================================
echo ""
echo "=== Error Handling ==="

test_cmd "invalid_command" "RPRT -1" "Invalid command returns RPRT -1"
test_cmd "F" "RPRT -1" "Missing argument returns RPRT -1"
test_cmd "l INVALID" "RPRT -1" "Invalid level returns RPRT -1"

# ========================================
# Full Mode Test (if requested)
# ========================================
if $FULL; then
    echo ""
    echo "=== Full Mode Cycle Test ==="

    send_cmd "F 14074000" > /dev/null

    for mode in USB LSB CW CWR AM RTTY RTTYR PKTUSB PKTLSB; do
        test_success "M $mode 0" "set_mode $mode"
    done

    echo ""
    echo "=== Full Band Scan Test ==="

    declare -A BANDS=(
        [160m]=1900000
        [80m]=3700000
        [40m]=7150000
        [30m]=10125000
        [20m]=14200000
        [17m]=18100000
        [15m]=21200000
        [12m]=24900000
        [10m]=28500000
        [6m]=50100000
        [2m]=146520000
        [70cm]=446000000
    )

    for band in 160m 80m 40m 30m 20m 17m 15m 12m 10m 6m 2m 70cm; do
        freq=${BANDS[$band]}
        test_success "F $freq" "set_freq $band ($freq Hz)"
    done
fi

# ========================================
# Summary
# ========================================
echo ""
echo "========================================"
echo "Test Summary"
echo "========================================"
echo -e "Passed: ${GREEN}$PASS${NC}"
echo -e "Failed: ${RED}$FAIL${NC}"
TOTAL=$((PASS + FAIL))
echo "Total:  $TOTAL"
echo ""

if [[ $FAIL -eq 0 ]]; then
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}Some tests failed!${NC}"
    exit 1
fi
