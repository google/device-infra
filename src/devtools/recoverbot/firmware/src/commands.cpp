#include "commands.h"  // NOLINT

#include <Arduino.h>
#include <stdlib.h>
#include <string.h>

#include "app_state.h"  // NOLINT
#include "calibration_store.h"  // NOLINT
#include "servo_controller.h"  // NOLINT

// ---------------------------------------------------------
// Status / Help
// ---------------------------------------------------------
void printStatus() {
  uint32_t now = millis();
  logMsg("=== Status ===");
  logMsg("Boards: %u", numBoards);
  logMsg("RAW Config: win=%lu ms, min=%u, max=%u",
         g_rawUnlockWindowMs, g_rawTickMin, g_rawTickMax);

  if (g_rawEnabled) {
    if (g_rawExpire) {
      int32_t remain = (int32_t)(g_rawExpire - now);
      logMsg("RAW: ON (expires in %d ms)", remain);
    } else {
      logMsg("RAW: ON (no expiry)");
    }
  } else {
    logMsg("RAW: OFF");
  }

  int activeCount = 0;
  for (int b = 0; b < (int)numBoards; ++b)
    for (int c = 0; c < MAX_CHANNELS; ++c)
      if (servoStates[b][c].active) activeCount++;

  logMsg("Active servos: %d", activeCount);
  for (int b = 0; b < (int)numBoards; ++b) {
    for (int c = 0; c < MAX_CHANNELS; ++c) {
      if (servoStates[b][c].active) {
        int32_t remain = (int32_t)(servoStates[b][c].releaseTime - now);
        logMsg(" - 0x%02X ch%d remaining=%d ms", boardAddresses[b], c,
               remain);
      }
    }
  }
  logMsg("=============");
}

void printHelp() {
  logMsg("=== Commands ===");
  logMsg("ping");
  logMsg("status");
  logMsg("help");
  logMsg("");
  logMsg("press_ms <boardHex> <ch> <ms>");
  logMsg("press    <boardHex> <ch>        (auto-release at 100s cap)");
  logMsg("release  <boardHex> <ch>");
  logMsg("");
  logMsg("cal_set   <boardHex> <ch> <pressTick> <releaseTick>   (ticks %u~%u)",
         g_rawTickMin, g_rawTickMax);
  logMsg("cal_get   <boardHex> <ch>");
  logMsg("cal_reset <boardHex> <ch>");
  logMsg("cal_list");
  logMsg("");
  logMsg("raw on [sec]   | raw off");
  logMsg("raw_tick <boardHex> <ch> <tick> (ticks %u~%u)", g_rawTickMin,
         g_rawTickMax);
  logMsg("");
  logMsg("cfg_set   <win|min|max> <val>");
  logMsg("cfg_get");
  logMsg("cfg_reset");
  logMsg("================");
}


// ---------------------------------------------------------
// Serial command parsing
// ---------------------------------------------------------
bool readLine(char* buf, size_t cap) {
  static size_t pos = 0;
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\r') continue;  // Windows friendly
    if (c == '\n') {
      buf[pos] = '\0';
      pos = 0;
      return true;
    }
    if (pos < cap - 1) buf[pos++] = c;
  }
  return false;
}

int tokenize(char* line, char* argv[], int maxArgv) {
  int argc = 0;
  char* saveptr;
  char* tok = strtok_r(line, " ", &saveptr);
  while (tok && argc < maxArgv) {
    argv[argc++] = tok;
    tok = strtok_r(NULL, " ", &saveptr);
  }
  return argc;
}


// ---------------------------------------------------------
// Command handlers
// ---------------------------------------------------------
void cmd_ping(int /*argc*/, char** /*argv*/) {
  logMsg("This is auto recovery controller");
}
void cmd_status(int /*argc*/, char** /*argv*/) { printStatus(); }
void cmd_help(int /*argc*/, char** /*argv*/) { printHelp(); }

void cmd_press_ms(int argc, char** argv) {
  if (argc < 4) { logMsg("ERR: press_ms <boardHex> <ch> <ms>"); return; }
  uint8_t board = static_cast<uint8_t>(strtoul(argv[1], nullptr, 16)); // NOLINT
  uint8_t ch = static_cast<uint8_t>(strtoul(argv[2], nullptr, 10)); // NOLINT
  uint32_t ms = static_cast<uint32_t>(strtoul(argv[3], nullptr, 10)); // NOLINT
  pressServo(board, ch, ms);
}

void cmd_press(int argc, char** argv) {
  if (argc < 3) { logMsg("ERR: press <boardHex> <ch>"); return; }
  uint8_t board = static_cast<uint8_t>(strtoul(argv[1], nullptr, 16)); // NOLINT
  uint8_t ch = static_cast<uint8_t>(strtoul(argv[2], nullptr, 10)); // NOLINT
  pressServo(board, ch, FORCE_MAX_PRESS_MS);
}

void cmd_release(int argc, char** argv) {
  if (argc < 3) { logMsg("ERR: release <boardHex> <ch>"); return; }
  uint8_t board = static_cast<uint8_t>(strtoul(argv[1], nullptr, 16)); // NOLINT
  uint8_t ch = static_cast<uint8_t>(strtoul(argv[2], nullptr, 10)); // NOLINT
  releaseServo(board, ch);
}

void cmd_raw_on(int argc, char** argv) {
  uint32_t sec = 0;
  if (argc >= 2) sec = strtoul(argv[1], nullptr, 10); // NOLINT
  if (sec == 0) sec = g_rawUnlockWindowMs / 1000;
  g_rawEnabled = true;
  g_rawExpire  = millis() + sec * 1000UL;
  logMsg("OK,RAW_ON,sec=%lu", sec);
}

void cmd_raw_off(int /*argc*/, char** /*argv*/) {
  g_rawEnabled = false;
  g_rawExpire = 0;
  logMsg("OK,RAW_OFF");
}

void cmd_raw_tick(int argc, char** argv) {
  if (argc < 4) { logMsg("ERR: raw_tick <boardHex> <ch> <tick>"); return; }
  uint8_t board = static_cast<uint8_t>(strtoul(argv[1], nullptr, 16)); // NOLINT
  uint8_t ch = static_cast<uint8_t>(strtoul(argv[2], nullptr, 10)); // NOLINT
  uint16_t tick = static_cast<uint16_t>(strtoul(argv[3], nullptr, 10)); // NOLINT
  setRawTicks(board, ch, tick);
}

void cmd_cal_set(int argc, char** argv) {
  if (argc < 5) {
    logMsg("ERR: cal_set <boardHex> <ch> <pressTick> <releaseTick>");
    return;
  }
  uint8_t board = static_cast<uint8_t>(strtoul(argv[1], nullptr, 16)); // NOLINT
  uint8_t ch = static_cast<uint8_t>(strtoul(argv[2], nullptr, 10)); // NOLINT
  uint16_t pressT = static_cast<uint16_t>(strtoul(argv[3], nullptr, 10)); // NOLINT
  uint16_t relT = static_cast<uint16_t>(strtoul(argv[4], nullptr, 10)); // NOLINT

  if (ch >= MAX_CHANNELS) { logMsg("ERR: invalid channel"); return; }
  if (!isTickSafe(pressT) || !isTickSafe(relT)) {
    logMsg("ERR: ticks must be in [%u,%u]", g_rawTickMin, g_rawTickMax);
    return;
  }

  int bi = getBoardIndex(board);
  if (bi < 0) { logMsg("ERR: board not found"); return; }

  g_cal[bi][ch].has = true;
  g_cal[bi][ch].pressTick = pressT;
  g_cal[bi][ch].releaseTick = relT;

  saveOneCal(board, ch, pressT, relT);
  logMsg("OK,CAL_SET,0x%02X,ch=%u,press=%u,release=%u", board, ch, pressT,
         relT);
}

void cmd_cal_get(int argc, char** argv) {
  if (argc < 3) { logMsg("ERR: cal_get <boardHex> <ch>"); return; }
  uint8_t board = static_cast<uint8_t>(strtoul(argv[1], nullptr, 16)); // NOLINT
  uint8_t ch = static_cast<uint8_t>(strtoul(argv[2], nullptr, 10)); // NOLINT
  if (ch >= MAX_CHANNELS) { logMsg("ERR: invalid channel"); return; }
  int bi = getBoardIndex(board);
  if (bi < 0) { logMsg("ERR: board not found"); return; }

  uint16_t pressT, relT;
  getPressAndReleaseTicks(board, ch, pressT, relT);

  logMsg("OK,CAL_GET,0x%02X,ch=%u,press=%u,release=%u,source=%s",
         board, ch, pressT, relT, g_cal[bi][ch].has ? "calibrated" : "default");
}

void cmd_cal_reset(int argc, char** argv) {
  if (argc < 3) { logMsg("ERR: cal_reset <boardHex> <ch>"); return; }
  uint8_t board = static_cast<uint8_t>(strtoul(argv[1], nullptr, 16)); // NOLINT
  uint8_t ch = static_cast<uint8_t>(strtoul(argv[2], nullptr, 10)); // NOLINT
  if (ch >= MAX_CHANNELS) { logMsg("ERR: invalid channel"); return; }
  int bi = getBoardIndex(board);
  if (bi < 0) { logMsg("ERR: board not found"); return; }

  deleteOneCal(board, ch);
  logMsg("OK,CAL_RESET,0x%02X,ch=%u", board, ch);
}

void cmd_cal_list(int /*argc*/, char** /*argv*/) {
  logMsg("==================================");
  logMsg("=== CAL LIST (calibrated only) ===");
  for (int b = 0; b < (int)numBoards; ++b) {
    for (int ch = 0; ch < MAX_CHANNELS; ++ch) {
      if (g_cal[b][ch].has) {
        logMsg("0x%02X,ch=%d,press=%u,release=%u", boardAddresses[b], ch,
               g_cal[b][ch].pressTick, g_cal[b][ch].releaseTick);
      }
    }
  }
  logMsg("==================================");
}

void cmd_cfg_set(int argc, char** argv) {
  if (argc < 3) { logMsg("ERR: cfg_set <win|min|max> <val>"); return; }
  uint32_t val = strtoul(argv[2], nullptr, 10); // NOLINT
  if (strcmp(argv[1], "win") == 0) {
    g_rawUnlockWindowMs = val * 1000UL;
  } else if (strcmp(argv[1], "min") == 0) {
    g_rawTickMin = (uint16_t)val;
  } else if (strcmp(argv[1], "max") == 0) {
    g_rawTickMax = (uint16_t)val;
  } else {
    logMsg("ERR: unknown key"); return;
  }
  saveGlobalConfig();
  logMsg("OK,CFG_SET,%s=%lu", argv[1], val);
}

void cmd_cfg_get(int /*argc*/, char** /*argv*/) {
  logMsg("=== Config ===");
  logMsg("win: %lu ms", g_rawUnlockWindowMs);
  logMsg("min: %u", g_rawTickMin);
  logMsg("max: %u", g_rawTickMax);
}

void cmd_cfg_reset(int /*argc*/, char** /*argv*/) {
  resetGlobalConfig();
  logMsg("OK,CFG_RESET");
}

struct Cmd { const char* name; void (*fn)(int, char**); };

const Cmd g_cmds[] = {
  { "ping",      cmd_ping      },
  { "status",    cmd_status    },
  { "help",      cmd_help      },
  { "press_ms",  cmd_press_ms  },
  { "press",     cmd_press     },
  { "release",   cmd_release   },
  { "raw_tick",  cmd_raw_tick  },
  { "raw_on",    cmd_raw_on    },
  { "raw_off",   cmd_raw_off   },
  { "cal_set",   cmd_cal_set   },
  { "cal_get",   cmd_cal_get   },
  { "cal_reset", cmd_cal_reset },
  { "cal_list",  cmd_cal_list  },
  { "cfg_set",   cmd_cfg_set   },
  { "cfg_get",   cmd_cfg_get   },
  { "cfg_reset", cmd_cfg_reset },
};
const size_t g_cmdCount = sizeof(g_cmds) / sizeof(g_cmds[0]);

void dispatchCommand(char* line) {
  // Auto-lock RAW if expired
  if (g_rawEnabled && g_rawExpire && millis() > g_rawExpire) {
    g_rawEnabled = false;
  }

  char* argv[12];
  int argc = tokenize(line, argv, 12);
  if (!argc) return;

  // Any command input is an event
  markEvent();

  // Allow "raw on/off" form (user convenience)
  if (strcmp(argv[0], "raw") == 0) {
    if (argc >= 2 && strcmp(argv[1], "on") == 0) {
      if (argc >= 3) {
        char* fakeArgv[2] = { (char*)"raw_on", argv[2] };
        cmd_raw_on(2, fakeArgv);
      } else {
        char* fakeArgv[1] = { (char*)"raw_on" };
        cmd_raw_on(1, fakeArgv);
      }
      return;
    } else if (argc >= 2 && strcmp(argv[1], "off") == 0) {
      cmd_raw_off(0, nullptr);
      return;
    } else {
      logMsg("ERR: raw [on [sec] | off]");
      return;
    }
  }

  // Lookup normal commands
  for (size_t i = 0; i < g_cmdCount; ++i) {
    if (strcmp(argv[0], g_cmds[i].name) == 0) {
      g_cmds[i].fn(argc, argv);
      return;
    }
  }

  logMsg("ERR: unknown cmd: %s", argv[0]);
}
