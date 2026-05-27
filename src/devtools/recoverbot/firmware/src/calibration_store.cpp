#include "calibration_store.h"  // NOLINT

#include <Preferences.h>
#include <RecoverBotCore.h>

#include "app_state.h"  // NOLINT

// ---------------------------------------------------------
// Calibration load/save (NVS)
// ---------------------------------------------------------
void loadOneCal(uint8_t boardAddr, uint8_t ch) {
  int b = getBoardIndex(boardAddr);
  if (b < 0 || ch >= MAX_CHANNELS) return;

  char key[16];
  recoverbot::buildCalKey(key, sizeof(key), boardAddr, ch);

  // Stored as uint32: (press<<16) | release. 0 => not set.
  uint32_t packed = prefs.getUInt(key, 0);
  recoverbot::ServoTicks ticks = {};
  if (!recoverbot::unpackCalibration(packed, g_rawTickMin, g_rawTickMax,
                                     &ticks)) {
    g_cal[b][ch].has = false;
    return;
  }

  g_cal[b][ch].has = true;
  g_cal[b][ch].pressTick = ticks.press;
  g_cal[b][ch].releaseTick = ticks.release;
}

void loadAllCalForDetectedBoards() {
  for (int b = 0; b < (int)numBoards; ++b) {
    for (int ch = 0; ch < MAX_CHANNELS; ++ch) {
      loadOneCal(boardAddresses[b], static_cast<uint8_t>(ch));
    }
  }
}

void saveOneCal(uint8_t boardAddr, uint8_t ch, uint16_t pressT, uint16_t relT) {
  char key[16];
  recoverbot::buildCalKey(key, sizeof(key), boardAddr, ch);
  uint32_t packed = recoverbot::packCalibration(pressT, relT);
  prefs.putUInt(key, packed);
}

void deleteOneCal(uint8_t boardAddr, uint8_t ch) {
  char key[16];
  recoverbot::buildCalKey(key, sizeof(key), boardAddr, ch);
  prefs.remove(key);

  int b = getBoardIndex(boardAddr);
  if (b >= 0 && ch < MAX_CHANNELS) g_cal[b][ch].has = false;
}

void getPressAndReleaseTicks(uint8_t boardAddr, uint8_t channel,
                             uint16_t& pressT, uint16_t& relT) {
  int b = getBoardIndex(boardAddr);
  if (b >= 0 && channel < MAX_CHANNELS && g_cal[b][channel].has) {
    pressT = g_cal[b][channel].pressTick;
    relT   = g_cal[b][channel].releaseTick;
    return;
  }

  recoverbot::ServoTicks ticks = recoverbot::defaultTicksForChannel(channel);
  pressT = ticks.press;
  relT   = ticks.release;
}
