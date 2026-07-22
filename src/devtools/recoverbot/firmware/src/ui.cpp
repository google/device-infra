#include "ui.h"  // NOLINT

#include <lvgl.h>
#include <M5Unified.h>
#include <RecoverBotCore.h>

#include "./logo.h"  // NOLINT

extern uint8_t numBoards;
extern bool g_rawEnabled;
extern void markEvent();

#define LOGO_DATA logo_240_png
#define LOGO_LEN logo_240_png_len

static const int LOGO_W = 240;
static const int LOGO_H = 240;

static const size_t LVGL_DRAW_BUF_PIXELS = 320 * 20;
static const size_t SCREEN_LOG_LINES = 48;
static const size_t SCREEN_LOG_LINE_CHARS = 72;
static const size_t LOG_TEXT_CAP =
    SCREEN_LOG_LINES * (SCREEN_LOG_LINE_CHARS + 1) + 1;

static lv_color_t g_lvDrawBuf[LVGL_DRAW_BUF_PIXELS];
static lv_obj_t* g_statusLabel = nullptr;
static lv_obj_t* g_logPanel = nullptr;
static lv_obj_t* g_logLabel = nullptr;
static char g_screenLog[SCREEN_LOG_LINES][SCREEN_LOG_LINE_CHARS];
static size_t g_screenLogStart = 0;
static size_t g_screenLogCount = 0;
static char g_logText[LOG_TEXT_CAP];
static size_t g_logTextLen = 0;
static uint32_t g_lastLvglTickMs = 0;
static uint32_t g_lastLvglHandlerMs = 0;
static uint32_t g_logDirtySinceMs = 0;
static bool g_logDirty = false;
static bool g_forceFollowPending = false;
static bool g_logoShown = false;

// Keep display work out of the servo command path. Log messages are appended
// to the in-memory ring immediately, then coalesced into one LVGL update after
// a short quiet window. LVGL itself only needs a modest service rate because
// this UI has no latency-sensitive animations.
static const uint32_t LOG_FLUSH_DEBOUNCE_MS = 50;
static const uint32_t LVGL_HANDLER_INTERVAL_MS = 20;

void lvglDisplayFlush(lv_disp_drv_t* disp, const lv_area_t* area,
                      lv_color_t* colorP) {
  uint32_t w = static_cast<uint32_t>(area->x2 - area->x1 + 1);
  uint32_t h = static_cast<uint32_t>(area->y2 - area->y1 + 1);

  M5.Display.startWrite();
  M5.Display.pushImage(area->x1, area->y1, w, h,
                       reinterpret_cast<uint16_t*>(&colorP->full));
  M5.Display.endWrite();

  lv_disp_flush_ready(disp);
}

void lvglTouchRead(lv_indev_drv_t* /*drv*/, lv_indev_data_t* data) {
  int32_t x = 0;
  int32_t y = 0;
  if (M5.Display.getTouch(&x, &y)) {
    data->state = LV_INDEV_STATE_PR;
    data->point.x = x;
    data->point.y = y;
    markEvent();
  } else {
    data->state = LV_INDEV_STATE_REL;
  }
}

void logPanelEvent(lv_event_t* event) {
  lv_event_code_t code = lv_event_get_code(event);
  if (code == LV_EVENT_SCROLL || code == LV_EVENT_SCROLL_BEGIN) {
    markEvent();
  }
}

void updateStatusLabel() {
  if (!g_statusLabel) return;

  static char status[48];
  snprintf(status, sizeof(status), "Boards:%u RAW:%s LOG:%u/%u", numBoards,
           g_rawEnabled ? "ON" : "OFF",
           static_cast<unsigned>(g_screenLogCount),
           static_cast<unsigned>(SCREEN_LOG_LINES));
  lv_label_set_text(g_statusLabel, status);
}

size_t screenLogPhysicalIndex(size_t logicalIndex) {
  return (g_screenLogStart + logicalIndex) % SCREEN_LOG_LINES;
}

void rebuildLogText() {
  g_logTextLen = 0;
  g_logText[0] = '\0';

  for (size_t i = 0; i < g_screenLogCount; ++i) {
    size_t idx = screenLogPhysicalIndex(i);
    int written = snprintf(g_logText + g_logTextLen,
                           LOG_TEXT_CAP - g_logTextLen, "%s\n",
                           g_screenLog[idx]);
    if (written <= 0) break;

    size_t used = static_cast<size_t>(written);
    if (used >= LOG_TEXT_CAP - g_logTextLen) {
      g_logTextLen = LOG_TEXT_CAP - 1;
      g_logText[g_logTextLen] = '\0';
      break;
    }
    g_logTextLen += used;
  }
}

void scrollLogToBottom() {
  if (!g_logPanel) return;
  lv_obj_update_layout(g_logPanel);
  lv_obj_scroll_to_y(g_logPanel, LV_COORD_MAX, LV_ANIM_OFF);
}

void flushPendingLogUi() {
  if (!g_logDirty || !g_logLabel) return;

  bool wasAtBottom = true;
  if (g_logPanel) {
    lv_obj_update_layout(g_logPanel);
    wasAtBottom = lv_obj_get_scroll_bottom(g_logPanel) <= 6;
  }

  rebuildLogText();
  lv_label_set_text_static(g_logLabel, g_logText);
  updateStatusLabel();

  if (g_forceFollowPending || wasAtBottom) scrollLogToBottom();

  g_logDirty = false;
  g_forceFollowPending = false;
}

void appendLogToUi(const char* line, bool forceFollow) {
  if (!g_logLabel) return;
  if (!recoverbot::shouldShowLineOnScreen(line)) return;

  size_t idx;
  if (g_screenLogCount < SCREEN_LOG_LINES) {
    idx = screenLogPhysicalIndex(g_screenLogCount);
    g_screenLogCount++;
  } else {
    idx = g_screenLogStart;
    g_screenLogStart = (g_screenLogStart + 1) % SCREEN_LOG_LINES;
  }

  snprintf(g_screenLog[idx], SCREEN_LOG_LINE_CHARS, "%s", line);
  if (!g_logDirty) g_logDirtySinceMs = millis();
  g_logDirty = true;
  g_forceFollowPending = g_forceFollowPending || forceFollow;
}

bool isLogoShown() { return g_logoShown; }

bool prepareLogUiForMessage() {
  bool wasLogoShown = g_logoShown;
  if (g_logoShown) {
    g_logoShown = false;
  }
  return wasLogoShown;
}

void finishLogUiMessage(bool wasLogoShown) {
  if (!wasLogoShown) return;
  // The pending log flush will restore the LVGL screen. Avoid a synchronous
  // render here because logMsg() is also called by press/release operations.
  g_forceFollowPending = true;
}

void initLogUi() {
  lv_init();

  static lv_disp_draw_buf_t drawBuf;
  lv_disp_draw_buf_init(&drawBuf, g_lvDrawBuf, nullptr, LVGL_DRAW_BUF_PIXELS);

  static lv_disp_drv_t dispDrv;
  lv_disp_drv_init(&dispDrv);
  dispDrv.hor_res = M5.Display.width();
  dispDrv.ver_res = M5.Display.height();
  dispDrv.flush_cb = lvglDisplayFlush;
  dispDrv.draw_buf = &drawBuf;
  lv_disp_drv_register(&dispDrv);

  static lv_indev_drv_t indevDrv;
  lv_indev_drv_init(&indevDrv);
  indevDrv.type = LV_INDEV_TYPE_POINTER;
  indevDrv.read_cb = lvglTouchRead;
  lv_indev_drv_register(&indevDrv);

  lv_obj_t* screen = lv_scr_act();
  lv_obj_set_style_bg_color(screen, lv_color_hex(0x050706), 0);
  lv_obj_set_style_bg_opa(screen, LV_OPA_COVER, 0);

  lv_obj_t* header = lv_label_create(screen);
  lv_label_set_text(header, "RecoverBot");
  lv_obj_set_style_text_color(header, lv_color_hex(0xCDEED4), 0);
  lv_obj_set_style_text_font(header, &lv_font_montserrat_14, 0);
  lv_obj_align(header, LV_ALIGN_TOP_LEFT, 6, 5);

  g_statusLabel = lv_label_create(screen);
  lv_obj_set_style_text_color(g_statusLabel, lv_color_hex(0x8B968E), 0);
  lv_obj_set_style_text_font(g_statusLabel, &lv_font_montserrat_12, 0);
  lv_obj_align(g_statusLabel, LV_ALIGN_TOP_RIGHT, -6, 7);
  updateStatusLabel();

  g_logPanel = lv_obj_create(screen);
  lv_obj_set_size(g_logPanel, M5.Display.width() - 4, M5.Display.height() - 27);
  lv_obj_align(g_logPanel, LV_ALIGN_BOTTOM_MID, 0, 0);
  lv_obj_set_style_bg_color(g_logPanel, lv_color_hex(0x020303), 0);
  lv_obj_set_style_bg_opa(g_logPanel, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(g_logPanel, 0, 0);
  lv_obj_set_style_radius(g_logPanel, 0, 0);
  lv_obj_set_style_pad_left(g_logPanel, 5, 0);
  lv_obj_set_style_pad_right(g_logPanel, 3, 0);
  lv_obj_set_style_pad_top(g_logPanel, 4, 0);
  lv_obj_set_style_pad_bottom(g_logPanel, 4, 0);
  lv_obj_set_scrollbar_mode(g_logPanel, LV_SCROLLBAR_MODE_AUTO);
  lv_obj_set_style_width(g_logPanel, 3, LV_PART_SCROLLBAR);
  lv_obj_set_style_bg_color(g_logPanel, lv_color_hex(0x9FE8A7),
                            LV_PART_SCROLLBAR);
  lv_obj_set_style_bg_opa(g_logPanel, LV_OPA_COVER, LV_PART_SCROLLBAR);
  lv_obj_add_event_cb(g_logPanel, logPanelEvent, LV_EVENT_ALL, nullptr);

  g_logLabel = lv_label_create(g_logPanel);
  lv_label_set_long_mode(g_logLabel, LV_LABEL_LONG_WRAP);
  lv_obj_set_width(g_logLabel, M5.Display.width() - 18);
  lv_obj_set_style_text_color(g_logLabel, lv_color_hex(0xDCE6DE), 0);
  lv_obj_set_style_text_font(g_logLabel, &lv_font_montserrat_12, 0);
  lv_obj_set_style_text_line_space(g_logLabel, 1, 0);
  lv_label_set_text_static(g_logLabel, g_logText);

  g_lastLvglTickMs = millis();
  g_lastLvglHandlerMs = g_lastLvglTickMs;
  lv_timer_handler();
}

void serviceLogUi() {
  uint32_t now = millis();
  lv_tick_inc(now - g_lastLvglTickMs);
  g_lastLvglTickMs = now;

  if (g_logoShown) return;

  bool flushed = false;
  if (g_logDirty && (now - g_logDirtySinceMs) >= LOG_FLUSH_DEBOUNCE_MS) {
    flushPendingLogUi();
    flushed = true;
  }

  if (flushed || (now - g_lastLvglHandlerMs) >= LVGL_HANDLER_INTERVAL_MS) {
    lv_timer_handler();
    g_lastLvglHandlerMs = now;
  }
}

void showLogoScreen() {
  int x = (M5.Display.width() - LOGO_W) / 2;
  int y = (M5.Display.height() - LOGO_H) / 2;
  if (x < 0) x = 0;
  if (y < 0) y = 0;

  M5.Display.fillScreen(TFT_WHITE);
  M5.Display.fillRect(x, y, LOGO_W, LOGO_H, TFT_WHITE);
  M5.Display.drawPng(LOGO_DATA, LOGO_LEN, x, y);

  g_logoShown = true;
}

void handleTouchToExitLogo() {
  if (!g_logoShown) return;

  int32_t x, y;
  if (M5.Display.getTouch(&x, &y)) {
    g_logoShown = false;
    markEvent();
    appendLogToUi("Back to logs (tap)", true);
    lv_obj_invalidate(lv_scr_act());
    lv_timer_handler();
  }
}
