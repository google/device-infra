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

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.MetadataKey.KeyValueHandler;
import com.google.common.flogger.backend.BaseMessageFormatter;
import com.google.common.flogger.backend.KeyValueFormatter;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LogMessageFormatter;
import com.google.common.flogger.backend.MessageUtils;
import com.google.common.flogger.backend.MetadataHandler;
import com.google.common.flogger.backend.MetadataKeyValueHandlers;
import com.google.common.flogger.backend.MetadataProcessor;
import com.google.devtools.mobileharness.shared.util.logging.flogger.FloggerFormatterConstants;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

class MobileHarnessMessageFormatter extends LogMessageFormatter {

  private static final MobileHarnessMessageFormatter INSTANCE = new MobileHarnessMessageFormatter();

  public static MobileHarnessMessageFormatter getInstance() {
    return INSTANCE;
  }

  private static final boolean WITH_CONTEXT = FloggerFormatterConstants.withContext();

  private final MetadataHandler<KeyValueHandler> handler =
      MetadataKeyValueHandlers.getDefaultHandler(ImmutableSet.of());

  @CanIgnoreReturnValue
  @Override
  public StringBuilder append(LogData logData, MetadataProcessor metadata, StringBuilder buffer) {
    BaseMessageFormatter.appendFormattedMessage(logData, buffer);
    if (WITH_CONTEXT) {
      appendContext(metadata, handler, buffer);
    }
    return buffer;
  }

  @Override
  public String format(LogData logData, MetadataProcessor metadata) {
    if (mustBeFormatted(logData, metadata)) {
      return append(logData, metadata, new StringBuilder()).toString();
    } else {
      return getLiteralLogMessage(logData);
    }
  }

  private static void appendContext(
      MetadataProcessor metadataProcessor,
      MetadataHandler<KeyValueHandler> metadataHandler,
      StringBuilder buffer) {
    KeyValueFormatter kvf =
        new KeyValueFormatter(
            FloggerFormatterConstants.CONTEXT_PREFIX,
            FloggerFormatterConstants.CONTEXT_SUFFIX,
            buffer);
    metadataProcessor.process(metadataHandler, kvf);
    kvf.done();
  }

  private static String getLiteralLogMessage(LogData logData) {
    return MessageUtils.safeToString(logData.getLiteralArgument());
  }

  private static boolean mustBeFormatted(LogData logData, MetadataProcessor metadata) {
    return logData.getTemplateContext() != null || metadata.keyCount() > 0;
  }

  private MobileHarnessMessageFormatter() {}
}
