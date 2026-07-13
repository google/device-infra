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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAflagsDecoratorSpec;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 * Uses {@code aflags} to override flags prior to running a test. If any flags were changed, the
 * device will be rebooted in order for the new values to take effect.
 *
 * <p>On teardown, any flags will be reverted to their previous state. If the flag was changed from
 * a default value, the override will be cleared (in order for the default value to take hold, and
 * to allow it to be server-overridable again). If the flag was previously overridden to a different
 * value, it will be set back to that value again.
 *
 * <p>If the current flag state matches the target state, the flag will not be overridden. If an
 * override is still desired in this case (e.g., to prevent a default-set flag being
 * server-overridden), the {@code force_set} option can be used.
 *
 * <p>Example usage in ATP: {@code aflags_overrides=namespace/flag_name=true,other_flag=false}.
 */
@DecoratorAnnotation(help = "For flipping read/write aflags on an Android device.")
public class AndroidAflagsDecorator extends LifecycleDecorator
    implements SpecConfigable<AndroidAflagsDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Timeout for waiting for reboot after modifying flags. */
  private static final Duration WAIT_FOR_REBOOT_TIMEOUT = Duration.ofMinutes(3);

  private final Adb adb;
  private final SystemStateManager systemStateManager;

  private final Map<String, AFlagsFeatureFlag.State> flagsToRestore = new HashMap<>();

  @Inject
  AndroidAflagsDecorator(
      Driver decoratedDriver, TestInfo testInfo, Adb adb, SystemStateManager systemStateManager) {
    super(decoratedDriver, testInfo);
    this.adb = adb;
    this.systemStateManager = systemStateManager;
  }

  @Override
  protected void setUp(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    AndroidAflagsDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this, deviceId);

    List<String> flagValues = spec.getAflagsOverridesList();
    if (flagValues.isEmpty()) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("No aflags_overrides option provided, skipping AndroidAflagsDecorator.");
      return;
    }

    boolean forceSet = spec.getForceSet();

    // Become root to ensure adb shell commands have necessary permissions.
    systemStateManager.becomeRoot(getDevice());

    Map<String, AFlagsFeatureFlag.State> targetFlags = parseDeviceConfigFlags(flagValues, testInfo);
    ImmutableMap<String, AFlagsFeatureFlag> initialFlags = listFlags(deviceId, testInfo);

    Map<String, AFlagsFeatureFlag.State> flagsToSet = new HashMap<>();

    for (Map.Entry<String, AFlagsFeatureFlag.State> flag : targetFlags.entrySet()) {
      String flagName = flag.getKey();
      AFlagsFeatureFlag currentValue = initialFlags.get(flagName);
      if (currentValue == null) {
        testInfo.log().atWarning().alsoTo(logger).log("Could not find flag %s to set", flagName);
        continue;
      }

      if (currentValue.getMutability() != AFlagsFeatureFlag.Mutability.READ_WRITE) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Flag %s is not read-write, skipping", flagName);
        continue;
      }

      if (currentValue.getCurrentState() != flag.getValue()) {
        flagsToSet.put(flagName, flag.getValue());

        // If the previous value wasn't locally overridden, use "NONE" as the previous value
        // to use the "unset" path (restoring the default/server flag instead).
        AFlagsFeatureFlag.State previousValue =
            (currentValue.getSetter() == AFlagsFeatureFlag.Setter.LOCAL)
                ? currentValue.getCurrentState()
                : AFlagsFeatureFlag.State.NONE;

        flagsToRestore.put(flagName, previousValue);
      } else {
        // If we're forcing the set, and the flag hasn't been locally overridden (e.g., it's
        // default or server), force the value anyway.
        if (forceSet && currentValue.getSetter() != AFlagsFeatureFlag.Setter.LOCAL) {
          flagsToSet.put(flagName, flag.getValue());
          flagsToRestore.put(flagName, AFlagsFeatureFlag.State.NONE);
        }
      }
    }

    if (!flagsToSet.isEmpty()) {
      updateFlags(deviceId, flagsToSet, testInfo);
      rebootDevice(testInfo);
    } else {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("No flags changed. Skipping reboot for device %s.", deviceId);
    }
  }

  @Override
  protected void tearDown(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    if (!flagsToRestore.isEmpty()) {
      String deviceId = getDevice().getDeviceId();
      testInfo.log().atInfo().alsoTo(logger).log("Restoring aflags on device %s", deviceId);
      try {
        systemStateManager.becomeRoot(getDevice());
        updateFlags(deviceId, flagsToRestore, testInfo);
        rebootDevice(testInfo);
      } catch (MobileHarnessException e) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Failed to restore flags on device %s: %s", deviceId, e.getMessage());
      }
    }
  }

  /** Parses a list of input flag override strings into a map of flag names to target states. */
  private Map<String, AFlagsFeatureFlag.State> parseDeviceConfigFlags(
      List<String> values, TestInfo testInfo) {
    Map<String, AFlagsFeatureFlag.State> ret = new HashMap<>();
    for (String line : values) {
      try {
        DeviceFeatureFlag flag = new DeviceFeatureFlag(line);
        ret.put(flag.getFlagName(), parseDeviceConfigFlagValue(flag.getFlagValue()));
      } catch (MobileHarnessException ex) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Could not parse flag %s, skipping: %s", line, ex.getMessage());
      }
    }
    return ret;
  }

  /** Converts a string representation of a flag value into an AFlagsFeatureFlag.State enum. */
  private AFlagsFeatureFlag.State parseDeviceConfigFlagValue(String value)
      throws MobileHarnessException {
    if (Ascii.equalsIgnoreCase(value, "true") || Ascii.equalsIgnoreCase(value, "enabled")) {
      return AFlagsFeatureFlag.State.ENABLED;
    } else if (Ascii.equalsIgnoreCase(value, "false")
        || Ascii.equalsIgnoreCase(value, "disabled")) {
      return AFlagsFeatureFlag.State.DISABLED;
    } else {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_AFLAGS_DECORATOR_FLAG_FORMAT_CHECK_FAILURE,
          String.format("Could not parse %s as boolean (true/false/enabled/disabled)", value));
    }
  }

  /** Queries the device via `aflags list` to retrieve the current state of all feature flags. */
  private ImmutableMap<String, AFlagsFeatureFlag> listFlags(String deviceId, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    String values = runCommand(deviceId, "aflags list", testInfo);
    if (Strings.isNullOrEmpty(values)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_AFLAGS_DECORATOR_LIST_FLAGS_ERROR,
          String.format("aflags list returned empty on device %s", deviceId));
    }

    try (ByteArrayInputStream stream =
            new ByteArrayInputStream(values.getBytes(Charset.defaultCharset()));
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
      return reader
          .lines()
          .map(
              line -> {
                try {
                  return new AFlagsFeatureFlag(line);
                } catch (MobileHarnessException ex) {
                  testInfo
                      .log()
                      .atWarning()
                      .alsoTo(logger)
                      .log("Could not parse flag line %s: %s", line, ex.getMessage());
                  return null;
                }
              })
          .filter(Objects::nonNull)
          .collect(toImmutableMap(AFlagsFeatureFlag::getFlagName, v -> v, (v1, v2) -> v1));
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_AFLAGS_DECORATOR_LIST_FLAGS_ERROR,
          String.format("Failed to parse aflags list on device %s", deviceId),
          e);
    }
  }

  /** Executes the necessary `aflags enable/disable/unset` commands for the specified flags. */
  private void updateFlags(
      String deviceId, Map<String, AFlagsFeatureFlag.State> flagsToSet, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    for (Map.Entry<String, AFlagsFeatureFlag.State> flag : flagsToSet.entrySet()) {
      switch (flag.getValue()) {
        case DISABLED ->
            runCommand(deviceId, String.format("aflags disable '%s'", flag.getKey()), testInfo);
        case ENABLED ->
            runCommand(deviceId, String.format("aflags enable '%s'", flag.getKey()), testInfo);
        case NONE ->
            runCommand(deviceId, String.format("aflags unset '%s'", flag.getKey()), testInfo);
      }
    }
  }

  /** Helper method to execute an adb shell command and log the output. */
  @CanIgnoreReturnValue
  private String runCommand(String deviceId, String command, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    testInfo.log().atInfo().alsoTo(logger).log("Running adb shell command: %s", command);
    try {
      String output = adb.runShell(deviceId, command);
      testInfo.log().atInfo().alsoTo(logger).log("Command output: %s", output);
      return output;
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_AFLAGS_DECORATOR_UPDATE_FLAG_ERROR,
          String.format("Command %s failed on device %s", command, deviceId),
          e);
    }
  }

  /** Reboots the device and waits for it to become ready. */
  private void rebootDevice(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    testInfo.log().atInfo().alsoTo(logger).log("Waiting for device %s to reboot... ", deviceId);

    try {
      systemStateManager.reboot(getDevice(), testInfo.log(), WAIT_FOR_REBOOT_TIMEOUT);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_AFLAGS_DECORATOR_REBOOT_ERROR,
          "Exception during reboot: " + e.getMessage(),
          e);
    }

    testInfo.log().atInfo().alsoTo(logger).log("Device %s has rebooted.", deviceId);
  }

  /** A feature flag description representing the properties parsed from `aflags list`. */
  public static class AFlagsFeatureFlag {
    /** The state of a flag (i.e., whether it is enabled or disabled). */
    public enum State {
      DISABLED,
      ENABLED,
      NONE
    }

    /** Where this flag was last set from. */
    public enum Setter {
      DEFAULT,
      LOCAL,
      UNKNOWN
    }

    /** Whether this flag is mutable or read-only. */
    public enum Mutability {
      READ_ONLY,
      READ_WRITE
    }

    private final String flagName;
    private final State currentState;
    private final State pendingState;
    private final Setter setter;
    private final Mutability mutability;
    private final String container;

    public AFlagsFeatureFlag(
        String flagName,
        State currentState,
        State pendingState,
        Setter setter,
        Mutability mutability,
        String container) {
      this.flagName = flagName;
      this.currentState = currentState;
      this.pendingState = pendingState;
      this.setter = setter;
      this.mutability = mutability;
      this.container = container;
    }

    /** Parses a single line from `aflags list` output into a structured flag object. */
    public AFlagsFeatureFlag(String flagString) throws MobileHarnessException {
      List<String> parts = Splitter.on(PATTERN).splitToList(flagString.trim());
      if (parts.size() < 6) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_AFLAGS_DECORATOR_FLAG_LIST_FORMAT_INVALID,
            "Expected at least six space-delimited parts in the flag string: " + flagString);
      }
      flagName = parts.get(0);
      currentState = parseCurrentState(parts.get(1));
      pendingState = parsePendingState(parts.get(2));
      setter = parseSetter(parts.get(3));
      mutability = parseMutability(parts.get(4));
      container = parts.get(5);
    }

    public String getFlagName() {
      return flagName;
    }

    public State getCurrentState() {
      return currentState;
    }

    public State getPendingState() {
      return pendingState;
    }

    public Setter getSetter() {
      return setter;
    }

    public Mutability getMutability() {
      return mutability;
    }

    public String getContainer() {
      return container;
    }

    private static State parseCurrentState(String state) throws MobileHarnessException {
      return switch (Ascii.toLowerCase(state)) {
        case "enabled" -> State.ENABLED;
        case "disabled" -> State.DISABLED;
        default ->
            throw new MobileHarnessException(
                AndroidErrorId.ANDROID_AFLAGS_DECORATOR_FLAG_LIST_FORMAT_INVALID,
                "Unknown current state " + state);
      };
    }

    private static State parsePendingState(String pendingState) throws MobileHarnessException {
      return switch (Ascii.toLowerCase(pendingState)) {
        case "->enabled" -> State.ENABLED;
        case "->disabled" -> State.DISABLED;
        case "-" -> State.NONE;
        default ->
            throw new MobileHarnessException(
                AndroidErrorId.ANDROID_AFLAGS_DECORATOR_FLAG_LIST_FORMAT_INVALID,
                "Unknown pending state " + pendingState);
      };
    }

    private static Mutability parseMutability(String mutability) throws MobileHarnessException {
      return switch (Ascii.toLowerCase(mutability)) {
        case "read-only" -> Mutability.READ_ONLY;
        case "read-write" -> Mutability.READ_WRITE;
        default ->
            throw new MobileHarnessException(
                AndroidErrorId.ANDROID_AFLAGS_DECORATOR_FLAG_LIST_FORMAT_INVALID,
                "Unknown mutability " + mutability);
      };
    }

    private static Setter parseSetter(String setter) {
      return switch (Ascii.toLowerCase(setter)) {
        case "default" -> Setter.DEFAULT;
        case "local" -> Setter.LOCAL;
        default -> Setter.UNKNOWN;
      };
    }

    private static final Pattern PATTERN = Pattern.compile("\\s+");
  }

  /** Helper class to parse input flag override strings provided in job parameters. */
  public static class DeviceFeatureFlag {
    // Matches both namespace/name=value and name=value formats.
    private static final Pattern FLAG_PATTERN =
        Pattern.compile("^(?:(?<namespace>[^\\s/=]+)/)?(?<name>[^\\s/=]+)=(?<value>.*)$");

    private final String namespace;
    private final String flagName;
    private final String flagValue;

    public DeviceFeatureFlag(String flagString) throws MobileHarnessException {
      Matcher match = FLAG_PATTERN.matcher(flagString.trim());
      if (!match.matches()) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_AFLAGS_DECORATOR_FLAG_OVERRIDE_FORMAT_INVALID,
            String.format("Failed to parse flag data: %s", flagString));
      }
      namespace = match.group("namespace");
      flagName = match.group("name");
      flagValue = match.group("value");
    }

    public String getNamespace() {
      return namespace;
    }

    public String getFlagName() {
      return flagName;
    }

    public String getFlagValue() {
      return flagValue;
    }
  }
}
