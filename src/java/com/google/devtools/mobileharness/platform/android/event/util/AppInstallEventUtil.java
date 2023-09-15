/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.platform.android.event.util;

import com.google.common.collect.ImmutableMap;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Util class for constructing {@code TestMessageEvent} while installing apps. */
public final class AppInstallEventUtil {
  private AppInstallEventUtil() {}

  /** Key to be put in message map to indicate starting of app installing process. */
  public static final String APP_INSTALL_START_EVENT_KEY = "_app_install_start_event";

  /** Key to be put in message map to indicate finishing of app installing process. */
  public static final String APP_INSTALL_FINISH_EVENT_KEY = "_app_install_finish_event";

  /** Key to be put in message map to indicate the package name of the app being installed. */
  public static final String APP_INSTALL_PACKAGE_NAME = "_app_install_package_name";

  /**
   * Creates message to indicate that an app starts to be installed. Device dimensions will be added
   * to the message map, but if there is duplicate dimension key, only one (randomly) will be saved.
   *
   * @param deviceDimensions Dimensions of the device that the {@code TestMessageEvent} applies to.
   * @param packageName Package name of the app.
   * @return message passed to {@code TestMessageEvent}'s constructor.
   */
  public static ImmutableMap<String, String> createStartMessage(
      Set<StrPair> deviceDimensions, String packageName) {
    Map<String, String> message = new HashMap<>();
    message.put(APP_INSTALL_START_EVENT_KEY, "");
    message.put(APP_INSTALL_PACKAGE_NAME, packageName);
    deviceDimensions.forEach(strPair -> message.put(strPair.getName(), strPair.getValue()));
    return ImmutableMap.copyOf(message);
  }

  /**
   * Creates message to indicate that the process of installing app has finished. Device dimensions
   * will be added to the message map, but if there is duplicate dimension key, only one (randomly)
   * will be saved.
   *
   * @param deviceDimensions Dimensions of the device that the {@code TestMessageEvent} applies to.
   * @param packageName Package name of the app.
   * @return message passed to {@code TestMessageEvent}'s constructor.
   */
  public static ImmutableMap<String, String> createFinishMessage(
      Set<StrPair> deviceDimensions, String packageName) {
    Map<String, String> message = new HashMap<>();
    message.put(APP_INSTALL_FINISH_EVENT_KEY, "");
    message.put(APP_INSTALL_PACKAGE_NAME, packageName);
    deviceDimensions.forEach(strPair -> message.put(strPair.getName(), strPair.getValue()));
    return ImmutableMap.copyOf(message);
  }
}
