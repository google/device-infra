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

package com.google.wireless.qa.mobileharness.shared.api.decorator.util;

import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.google.wireless.qa.mobileharness.shared.api.decorator.constant.StepSkippableDecoratorConstants;
import com.google.wireless.qa.mobileharness.shared.api.decorator.constant.StepSkippableDecoratorConstants.ExecutionMode;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Optional;

/**
 * Utility class for storing and retrieving strongly-typed Protobuf message states in {@link
 * TestInfo} properties for PhaseSkippableDecorator and its component single-phase decorators.
 *
 * <p>Uses Text Proto format for serialization to ensure high human-readability in test logs and
 * properties, while supporting schema evolution with unknown field tolerance.
 */
public final class PhaseSkippableDecoratorUtil {

  private static final String STATE_PREFIX = "phase_skippable_decorator_state";
  private static final String KEY_SEPARATOR = "::";

  /** Stores a strongly-typed Protobuf message scoped to its message class namespace in TestInfo. */
  public static <M extends Message> void setState(TestInfo testInfo, String deviceId, M message) {
    String namespacedKey = createNamespacedKey(deviceId, message.getClass());
    getRootTest(testInfo).properties().add(namespacedKey, encodeProto(message));
  }

  /**
   * Retrieves a strongly-typed Protobuf message scoped to its message class namespace from
   * TestInfo.
   *
   * @param defaultInstance The default instance of the expected Protobuf message (e.g.,
   *     FooState.getDefaultInstance()), used to resolve namespace and parse the Text Proto.
   */
  public static <M extends Message> Optional<M> getState(
      TestInfo testInfo, String deviceId, M defaultInstance) throws TextFormat.ParseException {
    String namespacedKey = createNamespacedKey(deviceId, defaultInstance.getClass());
    Optional<String> encoded = getRootTest(testInfo).properties().getOptional(namespacedKey);
    if (encoded.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(decodeProto(encoded.get(), defaultInstance));
  }

  /** Relays all phase-skippable states from sourceTest to targetTest (e.g., in session plugins). */
  public static void relayStates(TestInfo sourceTest, TestInfo targetTest) {
    getRootTest(sourceTest).properties().getAll().entrySet().stream()
        .filter(entry -> entry.getKey().startsWith(STATE_PREFIX + KEY_SEPARATOR))
        .forEach(
            entry -> getRootTest(targetTest).properties().add(entry.getKey(), entry.getValue()));
  }

  /**
   * Gets the execution mode from job properties.
   *
   * @return The execution mode specified in {@link
   *     StepSkippableDecoratorConstants#PROP_EXECUTION_MODE} or {@link ExecutionMode#FULL} if
   *     unspecified.
   */
  public static ExecutionMode getExecutionMode(JobInfo jobInfo) throws MobileHarnessException {
    Optional<String> modeStr =
        jobInfo.properties().getOptional(StepSkippableDecoratorConstants.PROP_EXECUTION_MODE);
    if (modeStr.isEmpty()) {
      return ExecutionMode.FULL;
    }
    try {
      return ExecutionMode.valueOf(modeStr.get());
    } catch (IllegalArgumentException e) {
      throw new MobileHarnessException(
          ExtErrorId.PHASE_SKIPPABLE_DECORATOR_UNKNOWN_EXECUTION_MODE,
          "Unknown execution mode: " + modeStr.get(),
          e);
    }
  }

  private static String createNamespacedKey(String deviceId, Class<? extends Message> protoClass) {
    return String.join(KEY_SEPARATOR, STATE_PREFIX, deviceId, protoClass.getName());
  }

  private static String encodeProto(Message message) {
    return TextFormat.printer().printToString(message);
  }

  // Safe to ignore unchecked warning because builder is created from defaultInstance of type M.
  @SuppressWarnings("unchecked")
  private static <M extends Message> M decodeProto(String encoded, M defaultInstance)
      throws TextFormat.ParseException {
    Message.Builder builder = defaultInstance.newBuilderForType();
    TextFormat.Parser.newBuilder().setAllowUnknownFields(true).build().merge(encoded, builder);
    return (M) builder.build();
  }

  private static TestInfo getRootTest(TestInfo testInfo) {
    TestInfo root = testInfo.getRootTest();
    return root != null ? root : testInfo;
  }

  private PhaseSkippableDecoratorUtil() {}
}
