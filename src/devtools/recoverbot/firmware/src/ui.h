#ifndef THIRD_PARTY_DEVICEINFRA_SRC_DEVTOOLS_RECOVERBOT_FIRMWARE_UI_H_
#define THIRD_PARTY_DEVICEINFRA_SRC_DEVTOOLS_RECOVERBOT_FIRMWARE_UI_H_

#include <stddef.h>

void initLogUi();
void serviceLogUi();
void appendLogToUi(const char* line, bool forceFollow);
bool isLogoShown();
bool prepareLogUiForMessage();
void finishLogUiMessage(bool wasLogoShown);
void showLogoScreen();
void handleTouchToExitLogo();

#endif  // THIRD_PARTY_DEVICEINFRA_SRC_DEVTOOLS_RECOVERBOT_FIRMWARE_UI_H_
