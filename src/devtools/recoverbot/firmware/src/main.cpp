#include <Adafruit_PWMServoDriver.h>
#include <M5Unified.h>
#include <Preferences.h>
#include <RecoverBotCore.h>
#include <stdarg.h>
#include <Wire.h>

#include "app_state.h"  // NOLINT
#include "calibration_store.h"  // NOLINT
#include "commands.h"  // NOLINT
#include "servo_controller.h"  // NOLINT
#include "ui.h"  // NOLINT

// ============================================================================
// RecoverBot Firmware
// Target: M5Stack Core3 (ESP32) + PCA9685 servo boards.
//
// What this firmware does (feature list)
// ----------------------------------------------------------------------------
// 1) Board auto-detection
//    - Scans I2C addresses 0x40 ~ 0x47 on Wire1 (Core3 Port A)
//    - For each detected PCA9685 board, initializes it at 50Hz servo frequency
//
// 2) Per-servo calibration (persisted in ESP32 NVS / Preferences)
//    - Each (boardAddr, channel) can have calibrated pressTick + releaseTick
//    - Stored as a packed uint32: (pressTick<<16) | releaseTick
//    - Key format: "c_<ADDR>_<CH>" e.g. "c_40_00"
//    - On boot: loads calibrations for all detected boards/channels
//    - On boot: sets every channel to its releaseTick (calibrated if present)
//
// 3) Safe "press then auto-release" servo actuation
//    - press_ms <boardHex> <ch> <ms>  : press for ms then auto-release
//    - press    <boardHex> <ch>       : press using max duration cap
//    - release  <boardHex> <ch>       : go to releaseTick immediately
//    - A safety cap FORCE_MAX_PRESS_MS prevents "stuck press" forever
//
// 4) RAW tick mode (guarded, timed unlock)
//    - raw on [sec]   : enable RAW mode for N seconds (default 300s)
//    - raw off        : disable RAW mode immediately
//    - raw_tick <boardHex> <ch> <tick>
//        Writes a tick value directly, but ONLY when RAW is enabled
//        AND tick is within [g_rawTickMin, g_rawTickMax] (configurable)
//    - RAW mode auto-locks after expiration
//
// 5) Serial command interface
//    - Type commands over Serial @115200
//    - "help" prints all available commands
//
// 6) Dual logging: Serial + on-device screen
//    - logMsg(...) prints the same line to Serial and an LVGL scrollable log
//    - When the log view is at the bottom, new lines auto-follow
//
// 7) Idle logo screensaver (optional UI)
//    - If no "event" happens for IDLE_SHOW_LOGO_MS (default 30s),
//      the firmware shows the embedded PNG logo.
//    - While the logo is shown:
//        * Any new log line will automatically return to log screen
//        * A screen tap will return to log screen
//
// Notes / Assumptions
// ----------------------------------------------------------------------------
// - PCA9685 servo frequency is set to 50Hz.
// - "tick" means PCA9685 PWM OFF count [0..4095] within one 20ms period.
// - LVGL owns the log UI; M5GFX still owns the physical display driver.
//
// Dependencies (Arduino Library Manager)
// ----------------------------------------------------------------------------
// - M5Unified (by M5Stack)
// - LVGL
// - Adafruit PWM Servo Driver Library (Adafruit_PWMServoDriver.h)
// - Adafruit BusIO (required by Adafruit PWM Servo Driver Library)
// ============================================================================


// ===== PCA9685 scan range =====
// 0x40 is the PCA9685 base address when all address pins are low, but this
// build reserves it for other CoreS3 modules on the same I2C bus.
static const uint8_t PCA9685_SCAN_FIRST_ADDR = 0x41;
static const uint8_t PCA9685_SCAN_LAST_ADDR  = 0x47;

// ===== RAW tick guard rails =====
uint32_t g_rawUnlockWindowMs = 300000UL;  // default 5 minutes
uint16_t g_rawTickMin        = 250;       // safe lower bound
uint16_t g_rawTickMax        = 450;       // safe upper bound

bool     g_rawEnabled = false;
uint32_t g_rawExpire  = 0;   // 0 => no expiry

Adafruit_PWMServoDriver* pwmBoards[MAX_BOARDS];
uint8_t boardAddresses[MAX_BOARDS];
uint8_t numBoards = 0;

ServoState servoStates[MAX_BOARDS][MAX_CHANNELS];

// Debug: last tick written (not required for functionality)
uint16_t lastTicks[MAX_BOARDS][MAX_CHANNELS];

CalEntry g_cal[MAX_BOARDS][MAX_CHANNELS];

Preferences prefs;  // NVS namespace: "recoverbot"


// ---------------------------------------------------------
// Idle logo control (simple "screensaver")
// ---------------------------------------------------------
static const uint32_t IDLE_SHOW_LOGO_MS = 30000UL;
static uint32_t g_lastEventMs = 0;

// Treat any interaction or servo action as an "event" to reset idle timer.
void markEvent() {
  g_lastEventMs = millis();
}

// ---------------------------------------------------------
// Logging: Serial + Screen (screen always stays coherent)
// ---------------------------------------------------------
void logMsg(const char* format, ...) {
  // If logo is currently shown, switch back to log screen first.
  // NOTE: Do this BEFORE markEvent() so we don't lose the state.
  bool wasLogoShown = prepareLogUiForMessage();

  markEvent();

  char buf[192];
  va_list args;
  va_start(args, format);
  vsnprintf(buf, sizeof(buf), format, args);
  va_end(args);

  Serial.println(buf);
  appendLogToUi(buf, wasLogoShown);
  finishLogUiMessage(wasLogoShown);
}


// ---------------------------------------------------------
// PCA9685 / servo helpers
// ---------------------------------------------------------
bool isPCA9685Present(uint8_t addr) {
  Wire1.beginTransmission(addr);
  return Wire1.endTransmission() == 0;
}

int getBoardIndex(uint8_t boardAddr) {
  for (int i = 0; i < (int)numBoards; ++i) {
    if (boardAddresses[i] == boardAddr) return i;
  }
  return -1;
}

bool isTickSafe(uint16_t t) {
  return recoverbot::isTickSafe(t, g_rawTickMin, g_rawTickMax);
}


void loadGlobalConfig() {
  g_rawUnlockWindowMs = prefs.getUInt("cfg_win", 300000UL);
  g_rawTickMin        = prefs.getUShort("cfg_min", 250);
  g_rawTickMax        = prefs.getUShort("cfg_max", 450);
}

void saveGlobalConfig() {
  prefs.putUInt("cfg_win", g_rawUnlockWindowMs);
  prefs.putUShort("cfg_min", g_rawTickMin);
  prefs.putUShort("cfg_max", g_rawTickMax);
}

void resetGlobalConfig() {
  prefs.remove("cfg_win");
  prefs.remove("cfg_min");
  prefs.remove("cfg_max");
  g_rawUnlockWindowMs = 300000UL;
  g_rawTickMin        = 250;
  g_rawTickMax        = 450;
}

// ---------------------------------------------------------
// Arduino setup/loop (Core3)
// ---------------------------------------------------------
void setup() {
  auto cfg = M5.config();
  M5.begin(cfg);  // Core3 power management / screen init
  M5.Display.setRotation(1);
  M5.Display.setTextSize(1);

  Serial.begin(115200);
  delay(200);

  // Initialize idle timer baseline
  g_lastEventMs = millis();

  initLogUi();
  logMsg("RecoverBot booting...");

  // Core3 Port A I2C: SDA=2, SCL=1
  Wire1.begin(2, 1, 100000);
  delay(500);

  // Open NVS namespace
  prefs.begin("recoverbot", false);

  // Detect PCA9685 boards on Wire1
  for (uint8_t addr = PCA9685_SCAN_FIRST_ADDR;
       addr <= PCA9685_SCAN_LAST_ADDR && numBoards < MAX_BOARDS; ++addr) {
    if (!isPCA9685Present(addr)) continue;

    uint8_t idx = numBoards;

    // Adafruit library expects TwoWire& (reference), NOT pointer
    pwmBoards[idx] = new Adafruit_PWMServoDriver(addr, Wire1);
    boardAddresses[idx] = addr;

    // Init arrays for this board
    for (int c = 0; c < MAX_CHANNELS; ++c) {
      servoStates[idx][c].active = false;
      servoStates[idx][c].releaseTime = 0;
      lastTicks[idx][c] = 0;
      g_cal[idx][c].has = false;
    }

    // Increment numBoards BEFORE loading calibration
    numBoards++;

    pwmBoards[idx]->begin();
    pwmBoards[idx]->setPWMFreq(50);

    logMsg("Found Board: 0x%02X", addr);
  }

  if (numBoards == 0) {
    logMsg("ERROR: No PCA9685 boards detected on Wire1 (Port A)!");
    return;
  }

  // Load global configuration
  loadGlobalConfig();

  // Load calibration for detected boards
  loadAllCalForDetectedBoards();

  // Set all channels to RELEASE position (calibrated if exists else default)
  for (int b = 0; b < (int)numBoards; ++b) {
    for (int ch = 0; ch < MAX_CHANNELS; ++ch) {
      uint16_t pressT, relT;
      getPressAndReleaseTicks(boardAddresses[b], static_cast<uint8_t>(ch),
                                pressT, relT);
      applyTicks(b, static_cast<uint8_t>(ch), relT);
    }
  }

  logMsg("System Ready. Boards=%u. Type 'help'.", numBoards);
}

void loop() {
  M5.update();

  // Tap screen to exit logo (only active while logo is shown)
  handleTouchToExitLogo();
  serviceLogUi();

  // Serial command input (one line per command)
  static char line[180];
  if (readLine(line, sizeof(line))) {
    dispatchCommand(line);
  }

  // Periodic release checks (auto-release when time is up)
  checkAndReleaseServos();

  // Idle => show logo once and keep it until an event occurs (or tap)
  uint32_t now = millis();
  if (!isLogoShown() && (now - g_lastEventMs) >= IDLE_SHOW_LOGO_MS) {
    showLogoScreen();
  }

  delay(1);
}
