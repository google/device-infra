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

package com.google.wireless.qa.mobileharness.shared.api.spec;

import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;

/** Specs for AndroidSetWifiDecorator. */
public interface AndroidSetWifiDecoratorSpec {
  @ParamAnnotation(
      required = false,
      help =
          "Use the default ssid and psk to connect. The default ssid and psk should be already set "
              + "on the lab config. The default value is false.")
  public static final String PARAM_USE_DEFAULT_SSID = "use_default_ssid";

  @ParamAnnotation(
      required = false,
      help =
          "The ssid of wifi will be connected. It will attempt to connect wifi if wifi_ssid has "
              + "been set. Need to leave this param empty when use_default_ssid is true.")
  public static final String PARAM_WIFI_SSID = "wifi_ssid";

  @ParamAnnotation(required = false, help = "The password of wifi.")
  public static final String PARAM_WIFI_PSK = "wifi_psk";

  @ParamAnnotation(required = false, help = "Whether to scan for hidden SSID")
  public static final String PARAM_WIFI_SCAN_SSID = "wifi_scan_ssid";

  /** The default retry num if failed to connect to the wifi. */
  public static final int DEFAULT_RETRY_NUM = 0;

  @ParamAnnotation(
      required = false,
      help =
          "Nums of retrying to connect to the wifi. Default value is "
              + DEFAULT_RETRY_NUM
              + ". 5 is max allowed. "
              + "For normal usage, we do suggest not to set it larger than 2 to save waiting time.")
  public static final String PARAM_WIFI_RETRY_NUM = "wifi_retry_num";

  @ParamAnnotation(
      required = false,
      help =
          "Whether the wifi SSID is optional. If true, missing SSID will only log a warning and "
              + "skip setup. Default value is false.")
  public static final String PARAM_WIFI_SSID_OPTIONAL = "wifi_ssid_optional";
}
