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

package com.google.devtools.mobileharness.infra.ats.console.result.mobly;

/**
 * A container interface for a single Mobly document. A document is defined by a single yaml
 * document.
 *
 * <p>Tis class should mirror the Mobly file at {@code records.py}. Any changes made to the Mobly
 * file should be mirrored here carefully.
 */
public interface MoblyYamlDocEntry {

  /** Used to differentiate between the different Mobly documents. See {@code records.py} */
  public enum Type {
    RECORD,
    SUMMARY,
  }

  /**
   * Retrieves the Mobly entry type. This is in relation to the 'Type' field of each Mobly document
   * when parsing the yaml summary results file.
   *
   * <p>This needs to be set by any subclass implementing this interface.
   */
  public Type getType();
}
