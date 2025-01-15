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

/** Spec for AndroidForegroundServiceMessenger. */
public interface AndroidForegroundServiceMessengerSpec {
  @ParamAnnotation(
      required = true,
      help =
          "The package and the name of the Android foreground service, in the format of"
              + " 'com.yourcompany.yourservice/.YourService'. The service will be started with 'adb"
              + " shell am start-foreground-service -n com.yourcompany.yourservice/.YourService"
              + " --ei port'.The port will be used for transmitting data between the service and"
              + " the device via socket.")
  String ANDROID_SERVICE_PACKAGE_AND_NAME = "android_service_package_and_name";
}
