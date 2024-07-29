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

package com.google.devtools.mobileharness.shared.util.logging.flogger.backend;

import com.google.common.flogger.LogContext;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LogMessageFormatter;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.backend.system.AbstractLogRecord;

class MobileHarnessLogRecord extends AbstractLogRecord {

  public MobileHarnessLogRecord(LogData data, Metadata scope) {
    super(data, scope);
    setThrown(getMetadataProcessor().getSingleValue(LogContext.Key.LOG_CAUSE));
    @SuppressWarnings("unused")
    String unused = getMessage();
  }

  public MobileHarnessLogRecord(RuntimeException error, LogData data, Metadata scope) {
    super(error, data, scope);
  }

  @Override
  protected LogMessageFormatter getLogMessageFormatter() {
    return MobileHarnessMessageFormatter.getInstance();
  }
}
