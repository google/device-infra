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

package com.google.devtools.mobileharness.shared.util.logging;

import com.google.common.flogger.MetadataKey;
import java.util.Optional;
import java.util.logging.LogRecord;

/** See {@link com.google.common.flogger.backend.system.LogDataExtractor}. */
public class LogDataExtractor {

  /**
   * See {@link com.google.common.flogger.backend.system.LogDataExtractor#getSingleMetadataValue}.
   */
  public static <T> Optional<T> getSingleMetadataValue(LogRecord record, MetadataKey<T> key) {
    if (record instanceof com.google.common.flogger.backend.system.AbstractLogRecord) {
      return Optional.ofNullable(
          ((com.google.common.flogger.backend.system.AbstractLogRecord) record)
              .getMetadataProcessor()
              .getSingleValue(key));
    }
    return Optional.empty();
  }

  private LogDataExtractor() {}
}
