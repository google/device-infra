#include "servo_controller.h"  // NOLINT

#include <Adafruit_PWMServoDriver.h>
#include <Arduino.h>

#include "app_state.h"  // NOLINT
#include "calibration_store.h"  // NOLINT

// ---------------------------------------------------------
// Core servo actions
// ---------------------------------------------------------
void applyTicks(int boardIndex, uint8_t channel, uint16_t ticks) {
  pwmBoards[boardIndex]->setPWM(channel, 0, ticks);
  lastTicks[boardIndex][channel] = ticks;

  // Servo activity counts as an event (prevents idle logo during active usage)
  markEvent();
}

void releaseServo(uint8_t boardAddr, uint8_t channel) {
  if (channel >= MAX_CHANNELS) { logMsg("ERR: invalid channel"); return; }
  int index = getBoardIndex(boardAddr);
  if (index < 0) { logMsg("ERR: board not found"); return; }

  uint16_t pressT, relT;
  getPressAndReleaseTicks(boardAddr, channel, pressT, relT);

  applyTicks(index, channel, relT);
  servoStates[index][channel].active = false;

  logMsg("OK,RELEASE,0x%02X,ch=%u", boardAddr, channel);
}

void pressServo(uint8_t boardAddr, uint8_t channel, uint32_t duration) {
  if (channel >= MAX_CHANNELS) { logMsg("ERR: invalid channel"); return; }
  int index = getBoardIndex(boardAddr);
  if (index < 0) { logMsg("ERR: board not found"); return; }

  // Safety cap
  if (duration > FORCE_MAX_PRESS_MS) duration = FORCE_MAX_PRESS_MS;

  uint16_t pressT, relT;
  getPressAndReleaseTicks(boardAddr, channel, pressT, relT);

  applyTicks(index, channel, pressT);
  servoStates[index][channel].active = true;
  servoStates[index][channel].releaseTime = millis() + duration;

  logMsg("OK,PRESS,0x%02X,ch=%u,ms=%lu,tick=%u", boardAddr, channel,
         duration, pressT);
}

void checkAndReleaseServos() {
  uint32_t now = millis();
  for (int b = 0; b < (int)numBoards; ++b) {
    for (int c = 0; c < MAX_CHANNELS; ++c) {
      if (servoStates[b][c].active && now >= servoStates[b][c].releaseTime) {
        uint16_t pressT, relT;
        getPressAndReleaseTicks(boardAddresses[b], static_cast<uint8_t>(c),
                                pressT, relT);
        applyTicks(b, static_cast<uint8_t>(c), relT);
        servoStates[b][c].active = false;
        logMsg("AUTO_RELEASE,0x%02X,ch=%d", boardAddresses[b], c);
      }
    }
  }
}

void setRawTicks(uint8_t boardAddr, uint8_t channel, uint16_t ticks) {
  if (channel >= MAX_CHANNELS) { logMsg("ERR: invalid channel"); return; }
  int index = getBoardIndex(boardAddr);
  if (index < 0) { logMsg("ERR: board not found"); return; }

  // Auto-lock RAW if expired
  if (!g_rawEnabled || (g_rawExpire && millis() > g_rawExpire)) {
    g_rawEnabled = false;
    logMsg("ERR: raw disabled");
    return;
  }

  if (!isTickSafe(ticks)) {
    logMsg("ERR: raw tick out of range [%u,%u]", g_rawTickMin, g_rawTickMax);
    return;
  }

  applyTicks(index, channel, ticks);
  servoStates[index][channel].active = false;

  logMsg("OK,RAW_TICK,0x%02X,ch=%u,tick=%u", boardAddr, channel, ticks);
}
