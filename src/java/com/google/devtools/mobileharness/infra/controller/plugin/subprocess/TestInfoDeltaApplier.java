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

/*
 * Copyright 2026 Google LLC
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

package com.google.devtools.mobileharness.infra.controller.plugin.subprocess;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.DimensionEntry;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.ExceptionDetail;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.FileEntry;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.RunTestEventResponse;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.SubTestDetail;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.TestMessageDetail;
import com.google.devtools.mobileharness.shared.util.comm.messaging.message.TestMessageInfo;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageManager;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import javax.annotation.Nullable;

/** Applies mutation deltas returned by the subprocess worker to the live {@link TestInfo}. */
public final class TestInfoDeltaApplier {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Applies the given {@link RunTestEventResponse} deltas to the authoritative {@link TestInfo}.
   */
  public static void applyDelta(TestInfo testInfo, RunTestEventResponse response) {
    applyDelta(testInfo, /* device= */ null, response);
  }

  /**
   * Applies the given {@link RunTestEventResponse} deltas to the live {@link TestInfo} and {@link
   * Device}.
   */
  public static void applyDelta(
      TestInfo testInfo, @Nullable Device device, RunTestEventResponse response) {
    // 1. Properties
    testInfo.properties().addAll(response.getAddedPropertiesMap());
    for (String removedKey : response.getRemovedPropertyKeysList()) {
      testInfo.properties().remove(removedKey);
    }

    // 2. Status & Result
    if (response.hasOverrideStatus()) {
      try {
        testInfo.status().set(TestStatus.valueOf(response.getOverrideStatus()));
      } catch (IllegalArgumentException e) {
        logger.atWarning().withCause(e).log(
            "Unknown TestStatus override from plugin worker: %s", response.getOverrideStatus());
      }
    }
    if (response.hasOverrideResult()) {
      try {
        testInfo.result().set(TestResult.valueOf(response.getOverrideResult()));
      } catch (IllegalArgumentException e) {
        logger.atWarning().withCause(e).log(
            "Unknown TestResult override from plugin worker: %s", response.getOverrideResult());
      }
    }

    // 3. Files
    for (FileEntry fileEntry : response.getAddedFilesList()) {
      try {
        testInfo.files().add(fileEntry.getTag(), fileEntry.getPath());
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to add file tag %s -> %s to TestInfo", fileEntry.getTag(), fileEntry.getPath());
      }
    }

    for (FileEntry fileEntry : response.getAddedRemoteGenFilesList()) {
      testInfo.remoteGenFiles().add(fileEntry.getPath());
    }

    // 4. Sub-tests
    for (SubTestDetail subTest : response.getAddedSubTestsList()) {
      if (testInfo.subTests().getById(subTest.getSubTestId()) == null) {
        try {
          testInfo.subTests().add(subTest.getSubTestId(), subTest.getSubTestName());
        } catch (MobileHarnessException e) {
          logger.atWarning().withCause(e).log(
              "Failed to register sub test %s -> %s on TestInfo",
              subTest.getSubTestId(), subTest.getSubTestName());
        }
      }
    }

    // 5. Device dimensions
    if (device != null) {
      for (DimensionEntry dimensionEntry : response.getAddedDimensionsList()) {
        device.addDimension(dimensionEntry.getName(), dimensionEntry.getValue());
      }
    }

    // 6. Outgoing test messages
    for (TestMessageDetail msg : response.getOutgoingTestMessagesList()) {
      try {
        TestMessageManager.getInstance()
            .sendMessageToTest(
                TestMessageInfo.of(
                    msg.getRootTestId(),
                    msg.getMessageMap(),
                    ImmutableList.copyOf(msg.getSubTestIdChainList()),
                    /* isRemote= */ false));
      } catch (Exception e) {
        logger.atWarning().withCause(e).log(
            "Failed to route test message from worker to host: %s", msg);
      }
    }

    // 7. Logs
    for (String logRecord : response.getLogRecordsList()) {
      testInfo.log().atInfo().log("%s", logRecord);
    }

    // 8. Warnings
    for (ExceptionDetail warning : response.getWarningsList()) {
      String message =
          String.format(
              "Warning from plugin worker [%s]: %s%s",
              warning.getExceptionClass(),
              warning.getMessage(),
              warning.getStackTrace().isEmpty() ? "" : "\n" + warning.getStackTrace());
      testInfo
          .warnings()
          .addAndLog(new MobileHarnessException(BasicErrorId.NON_MH_EXCEPTION, message));
    }
  }

  private TestInfoDeltaApplier() {}
}
