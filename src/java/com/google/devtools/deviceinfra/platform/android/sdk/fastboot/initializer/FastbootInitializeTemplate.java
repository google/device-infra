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

import com.google.devtools.deviceinfra.shared.util.flags.Flags;
import java.util.Optional;

/**
 * A template defines the fastboot initialization steps.
 *
 * <p>Sub-classes can or need to override some of methods to handle the initialization, or its
 * default implementation will be used.
 */
public abstract class FastbootInitializeTemplate {

  private static final FastbootParam DEFAULT_FASTBOOT_PARAM_WITH_ERROR =
      FastbootParam.builder()
          .setInitializationError(
              "Failed to initialize user given fastboot tools and built-in fastboot tools. Please"
                  + " point --fastboot to a valid fastboot binary.")
          .build();

  /** Initializes fastboot environment and returns {@link FastbootParam} storing the info. */
  public FastbootParam initializeFastboot() {
    if (!Flags.instance().fastbootPathFromUser.getNonNull().isEmpty()) {
      return initializeFastbootToolsFromUser().orElse(DEFAULT_FASTBOOT_PARAM_WITH_ERROR);
    }

    // If user doesn't provide fastboot tools, try to initialize built-in fastboot tools.
    return initializeBuiltInFastbootTools().orElse(DEFAULT_FASTBOOT_PARAM_WITH_ERROR);
  }

  /** Initializes fastboot tools given from the user. */
  protected abstract Optional<FastbootParam> initializeFastbootToolsFromUser();

  /**
   * Initializes built-in fastboot tools.
   *
   * <p>By default it does nothing.
   */
  protected Optional<FastbootParam> initializeBuiltInFastbootTools() {
    return Optional.empty();
  }

  protected String getFastbootPathFromUser() {
    return Flags.instance().fastbootPathFromUser.getNonNull();
  }
}
