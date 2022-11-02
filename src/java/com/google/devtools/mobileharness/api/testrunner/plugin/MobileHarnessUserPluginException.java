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

package com.google.devtools.mobileharness.api.testrunner.plugin;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import javax.annotation.Nullable;

/**
 * MH user plugins can use this exception as MH test result cause and MH test warnings.
 *
 * @see com.google.devtools.mobileharness.api.model.job.out.Result
 * @see com.google.devtools.mobileharness.api.model.job.out.Warnings
 */
public class MobileHarnessUserPluginException extends MobileHarnessException {

  public MobileHarnessUserPluginException(String message) {
    super(BasicErrorId.USER_PLUGIN_ERROR, message, null);
  }

  public MobileHarnessUserPluginException(String message, @Nullable Throwable cause) {
    super(BasicErrorId.USER_PLUGIN_ERROR, message, cause);
  }
}
