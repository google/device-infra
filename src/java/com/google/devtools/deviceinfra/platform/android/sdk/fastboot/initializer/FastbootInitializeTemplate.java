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

package com.google.devtools.deviceinfra.platform.android.sdk.fastboot.initializer;

import com.google.devtools.mobileharness.shared.util.flags.Flags;

/**
 * A template defines the fastboot initialization steps.
 *
 * <p>Sub-classes can or need to override some methods to handle the initialization, or its default
 * implementation will be used.
 */
public abstract class FastbootInitializeTemplate {

  /** Initializes fastboot environment and returns {@link FastbootParam} storing the info. */
  public abstract FastbootParam initializeFastboot();

  protected String getFastbootPathFromUser() {
    return Flags.instance().fastbootPathFromUser.getNonNull();
  }
}
