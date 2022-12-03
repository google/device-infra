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

/** Spec for AndroidRealDevice. */
public interface AndroidRealDeviceSpec {

  @ParamAnnotation(
      required = false,
      help =
          "Whether to kill the device daemon before running each test. By default, it is false so "
              + "device daemon will be started. If enabled, you need to make "
              + "sure your test can deal with off/lock screen. So use it at your own risk.")
  public static final String PARAM_KILL_DAEMON_ON_TEST = "kill_daemon_on_test";
}
