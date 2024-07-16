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

import com.google.common.flogger.LogContext;
import com.google.common.flogger.LogSite;
import com.google.common.flogger.LogSites;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.system.AbstractLogRecord;
import com.google.common.flogger.context.Tags;
import java.util.logging.LogRecord;
import javax.annotation.Nullable;

/** See {@link com.google.common.flogger.backend.system.LogDataExtractor}. */
public class LogDataExtractor {

  /**
   * See {@link com.google.common.flogger.backend.system.LogDataExtractor#getSingleMetadataValue}.
   */
  @Nullable
  public static <T> T getSingleMetadataValue(LogRecord record, MetadataKey<T> key) {
    if (record instanceof AbstractLogRecord) {
      return ((AbstractLogRecord) record).getMetadataProcessor().getSingleValue(key);
    }
    return null;
  }

  /** See {@link com.google.common.flogger.backend.system.LogDataExtractor#getLogSite}. */
  public static LogSite getLogSite(LogRecord record) {
    if (record instanceof AbstractLogRecord) {
      return ((AbstractLogRecord) record).getLogData().getLogSite();
    }
    // In theory the class and/or method name in a log record can be null...
    if (record.getSourceClassName() != null && record.getSourceMethodName() != null) {
      // This should almost never happen so it's not worth optimizing to avoid extra allocations.
      return LogSites.logSiteFrom(
          new StackTraceElement(
              record.getSourceClassName(),
              record.getSourceMethodName(),
              null,
              LogSite.UNKNOWN_LINE));
    }
    return LogSite.INVALID;
  }

  /** See {@link com.google.common.flogger.backend.system.LogDataExtractor#getTags}. */
  // NOTE: At some stage "Tags" will not be added to the log-site metadata and will instead come
  // directly from the scoped logging context (not via the Metadata API at all). At that point
  // LogContext.Key.TAGS can be deleted and this method switched access tags directly.
  public static Tags getTags(LogRecord record) {
    if (record instanceof AbstractLogRecord) {
      Tags tags =
          ((AbstractLogRecord) record).getMetadataProcessor().getSingleValue(LogContext.Key.TAGS);
      if (tags != null) {
        return tags;
      }
    }
    return Tags.empty();
  }

  private LogDataExtractor() {}
}
