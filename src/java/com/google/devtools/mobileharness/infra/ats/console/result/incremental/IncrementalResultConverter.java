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

package com.google.devtools.mobileharness.infra.ats.console.result.incremental;

import com.android.tradefed.config.proto.ConfigurationDescription.Metadata;
import com.android.tradefed.invoker.proto.InvocationContext.Context;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.util.tradefed.TestRecordProtoUtil;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;

/** Converts Tradefed incremental proto store files into console report models. */
public class IncrementalResultConverter {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject
  IncrementalResultConverter() {}

  /**
   * Parses {@code invocation.pb} from a single Tradefed job's incremental store directory, if
   * present.
   */
  public Optional<InvocationPlan> parseInvocation(Path jobStoreDir) throws MobileHarnessException {
    Path invocationFile = jobStoreDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME);
    if (!Files.isRegularFile(invocationFile)) {
      return Optional.empty();
    }

    TestRecord record = readTestRecord(invocationFile);
    Context context = unpackContext(record, invocationFile);
    ImmutableMap<String, Integer> plannedCountsByModule =
        parsePlannedModules(context, invocationFile);
    return Optional.of(new InvocationPlan(context, plannedCountsByModule));
  }

  /**
   * Parsed invocation context and planned unit counts per module ID.
   *
   * @param context unpacked Tradefed invocation {@link Context}
   * @param plannedCountsByModule planned unit count keyed by module ID in execution plan order
   */
  public record InvocationPlan(
      Context context, ImmutableMap<String, Integer> plannedCountsByModule) {
    public InvocationPlan {
      Objects.requireNonNull(context);
      Objects.requireNonNull(plannedCountsByModule);
    }
  }

  private static TestRecord readTestRecord(Path protoFile) throws MobileHarnessException {
    try {
      return TestRecordProtoUtil.readFromFile(protoFile.toFile());
    } catch (IOException e) {
      throw new MobileHarnessException(
          ExtErrorId.TEST_RECORD_READ_PROTO_FILE_ERROR,
          String.format("Failed to read test record proto file [%s]", protoFile),
          e);
    }
  }

  private static Context unpackContext(TestRecord record, Path protoFile)
      throws MobileHarnessException {
    if (!record.hasDescription()) {
      return Context.getDefaultInstance();
    }
    Any description = record.getDescription();
    if (!description.is(Context.class)) {
      throw new MobileHarnessException(
          ExtErrorId.TEST_RECORD_INVALID_PROTO_CONTEXT,
          String.format(
              "Invalid description type URL [%s] in proto file [%s]; expected [%s]",
              description.getTypeUrl(), protoFile, Context.getDescriptor().getFullName()));
    }
    try {
      return description.unpack(Context.class);
    } catch (InvalidProtocolBufferException e) {
      throw new MobileHarnessException(
          ExtErrorId.TEST_RECORD_INVALID_PROTO_CONTEXT,
          String.format("Failed to unpack Context from proto file [%s]", protoFile),
          e);
    }
  }

  private static ImmutableMap<String, Integer> parsePlannedModules(
      Context context, Path invocationFile) {
    Map<String, Integer> plannedCountsByModule = new LinkedHashMap<>();
    for (Metadata metadata : context.getMetadataList()) {
      if (!metadata.getKey().equals(XtsConstants.PLANNED_MODULES_ATTR)) {
        continue;
      }
      for (String entry : metadata.getValueList()) {
        if (entry.isEmpty()) {
          continue;
        }
        int idx = entry.lastIndexOf(':');
        if (idx <= 0) {
          logger.atWarning().log(
              "Skipping malformed %s entry [%s] in [%s]",
              XtsConstants.PLANNED_MODULES_ATTR, entry, invocationFile);
          continue;
        }
        String moduleId = entry.substring(0, idx);
        if (moduleId.isBlank()) {
          logger.atWarning().log(
              "Skipping %s entry with blank module ID [%s] in [%s]",
              XtsConstants.PLANNED_MODULES_ATTR, entry, invocationFile);
          continue;
        }
        int count;
        try {
          count = Integer.parseInt(entry.substring(idx + 1));
        } catch (NumberFormatException e) {
          logger.atWarning().log(
              "Skipping %s entry with invalid count [%s] in [%s]",
              XtsConstants.PLANNED_MODULES_ATTR, entry, invocationFile);
          continue;
        }
        if (count <= 0) {
          logger.atWarning().log(
              "Skipping %s entry with non-positive count [%s] in [%s]",
              XtsConstants.PLANNED_MODULES_ATTR, entry, invocationFile);
          continue;
        }
        plannedCountsByModule.merge(moduleId, count, Integer::sum);
      }
    }
    return ImmutableMap.copyOf(plannedCountsByModule);
  }
}
