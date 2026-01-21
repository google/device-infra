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

package com.google.devtools.mobileharness.platform.android.device;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.devicemanager.detector.flag.DetectorFlag;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.wireless.qa.mobileharness.shared.api.device.BaseDevice;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Helper class for Android Desktop Device. */
public class AndroidDesktopDeviceHelper {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String SHIVAS_CLI_PATH =
      "/usr/local/google/mobileharness/ate/cipd_packages/shivas";
  public static final String SA_PATH =
      "/usr/local/google/mobileharness/runtime_files/recovery/skylab-drone.json";
  private static final String PARIS_CHECK_HEALTH_RESOURCE_FILE_PATH = "paris-check-health";
  private static final Supplier<Optional<String>> PARIS_CHECK_HEALTH_SCRIPT_PATH =
      Suppliers.memoize(AndroidDesktopDeviceHelper::getParisCheckHealthScriptPath);
  private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(60);
  private final CommandExecutor commandExecutor;
  private static final ConcurrentHashMap<String, ReentrantLock> parisLocks =
      new ConcurrentHashMap<>();

  private final Random random;
  private static final double LATEST_PROBABILITY = 0.1;

  private enum ParisVersion {
    LATEST,
    PROD,
  }

  public AndroidDesktopDeviceHelper() {
    this.commandExecutor = new CommandExecutor();
    this.random = new Random();
  }

  @VisibleForTesting
  public AndroidDesktopDeviceHelper(CommandExecutor executor, ResUtil resUtil) {
    this.commandExecutor = executor;
    this.random = new Random();
  }

  @VisibleForTesting
  public AndroidDesktopDeviceHelper(CommandExecutor executor) {
    this.commandExecutor = executor;
    this.random = new Random();
  }

  private String getHostName(String deviceId) {
    if (isNullOrEmpty(deviceId)) {
      return "";
    }
    String[] parts = deviceId.split(":", 2);
    return parts[0];
  }

  /** Get android desktop scheduling labels from shivas. */
  private JsonElement getSchedulingLabels(String deviceId)
      throws MobileHarnessException, InterruptedException {
    String hostName = getHostName(deviceId);
    Command command =
        Command.of(
            SHIVAS_CLI_PATH,
            "internal-print-bot-info",
            "-by-hostname",
            "-service-account-json",
            SA_PATH,
            hostName);
    logger.atInfo().log("shivas command: %s", command);
    try {
      CommandResult r = commandExecutor.exec(command);
      if (r.exitCode() == 0 && r.stdout() != null) {
        return JsonParser.parseString(r.stdoutWithoutTrailingLineTerminator());
      } else {
        logger.atWarning().log(
            "shivas command non zero exit: %s, stdout: %s, stderr: %s ",
            r.exitCode(),
            r.stdoutWithoutTrailingLineTerminator(),
            r.stderrWithoutTrailingLineTerminator());
      }
    } catch (JsonSyntaxException e) {
      throw new MobileHarnessException(
          ExtErrorId.ANDROID_DESKTOP_DEVICE_HELPER_INFRA_ERROR,
          "Failed to parse shivas output as json: " + e.getMessage(),
          e);
    }
    return JsonNull.INSTANCE;
  }

  @CanIgnoreReturnValue
  public boolean updateSchedulingDimensions(BaseDevice device)
      throws MobileHarnessException, InterruptedException {
    JsonElement schedulingLabels = getSchedulingLabels(device.getDeviceId());
    logger.atInfo().log("updating scheduling dimensions");
    return updateDimensionsFromJsonObject(device, schedulingLabels);
  }

  private boolean updateDimensionsFromJsonObject(BaseDevice device, JsonElement schedulingLabels) {
    boolean hasDimensionChange = false;
    try {
      Set<Map.Entry<String, JsonElement>> entries = new HashSet<>();
      if (schedulingLabels != null
          && !schedulingLabels.isJsonNull()
          && schedulingLabels.isJsonObject()) {
        JsonObject schedulingLabelsJsonObject = schedulingLabels.getAsJsonObject();
        JsonElement dimensionsElement = schedulingLabelsJsonObject.get("Dimensions");
        JsonElement stateElement = schedulingLabelsJsonObject.get("State");

        if (dimensionsElement != null && dimensionsElement.isJsonObject()) {
          entries.addAll(dimensionsElement.getAsJsonObject().entrySet());
        }
        if (stateElement != null && stateElement.isJsonObject()) {
          entries.addAll(stateElement.getAsJsonObject().entrySet());
        }

        // Iterate over the Set using the enhanced for loop
        for (Map.Entry<String, JsonElement> entry : entries) {
          String key = entry.getKey();
          JsonElement value = entry.getValue();
          if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            List<String> valuesList = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
              JsonElement element = array.get(i);
              if (element != null && element.isJsonPrimitive()) {
                valuesList.add(element.getAsString());
              }
            }
            if (!valuesList.isEmpty()) {
              hasDimensionChange |= device.updateDimension(key, valuesList.toArray(new String[0]));
            } else {
              logger.atWarning().log("Ignoring empty array scheduling labels for key: %s", key);
            }
          } else if (value.isJsonPrimitive()) {
            hasDimensionChange |= device.updateDimension(key, value.getAsString());
          }
        }
      } else {
        logger.atWarning().log("Ignoring null or non-object scheduling labels");
      }
    } catch (
        @SuppressWarnings("CatchingUnchecked")
        RuntimeException e) {
      logger.atWarning().withCause(e).log("Failed to update dimensions from json object");
    }
    return hasDimensionChange;
  }

  public String getDeviceDutState(String deviceId)
      throws MobileHarnessException, InterruptedException {
    String hostName = getHostName(deviceId);
    Command command =
        Command.of(SHIVAS_CLI_PATH, "dut-state", "-service-account-json", SA_PATH, hostName);
    logger.atInfo().log("shivas command: %s", command);
    CommandResult r = commandExecutor.exec(command);
    logger.atInfo().log("shivas output: %s", r.stdoutWithoutTrailingLineTerminator());
    if (r.exitCode() == 0 && r.stdout() != null) {
      String[] parts = r.stdout().split(":", 2);
      return parts[1].trim();
    }
    return "";
  }

  static Optional<String> getParisCheckHealthScriptPath() {
    String parisPath = DetectorFlag.parisPath.getNonNull();
    if (!parisPath.isEmpty()) {
      return Optional.of(parisPath);
    }
    try {
      ResUtil resUtil = new ResUtil();
      parisPath =
          resUtil.getResourceFile(
              AndroidDesktopDeviceHelper.class, PARIS_CHECK_HEALTH_RESOURCE_FILE_PATH);
      return Optional.of(parisPath);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to get paris check health script path.");
      return Optional.empty();
    }
  }

  private ParisVersion getParisVersion() {
    double randomDouble = random.nextDouble();
    if (randomDouble < LATEST_PROBABILITY) {
      return ParisVersion.LATEST;
    }
    return ParisVersion.PROD;
  }

  private CommandResult runParisCommand(String deviceId, Command command)
      throws MobileHarnessException, InterruptedException {
    CommandResult r = null;
    ReentrantLock lock =
        parisLocks.computeIfAbsent(deviceId, k -> new ReentrantLock(true)); // fair lock
    logger.atInfo().log("using paris lock for device %s", deviceId);
    boolean lockAcquired = lock.tryLock(LOCK_TIMEOUT.toSeconds(), SECONDS);
    if (!lockAcquired) {
      logger.atWarning().log(
          "Timeout waiting to acquire lock for device %s after %s", deviceId, LOCK_TIMEOUT);
      throw new MobileHarnessException(
          ExtErrorId.ANDROID_DESKTOP_DEVICE_HELPER_INFRA_ERROR,
          String.format(
              "Timeout waiting to acquire lock for device %s after %s seconds",
              deviceId, LOCK_TIMEOUT));
    }
    logger.atInfo().log("Lock acquired for device %s", deviceId);
    try {
      r = commandExecutor.exec(command);
      logger.atInfo().log(
          "PARIS exit code: %s, stdout: %s, stderr: %s", r.exitCode(), r.stdout(), r.stderr());
    } finally {
      logger.atInfo().log("Releasing lock for device %s", deviceId);
      lock.unlock();
    }
    return r;
  }

  public boolean isDeviceHealthy(String deviceId)
      throws MobileHarnessException, InterruptedException {
    Command command = Command.of(PARIS_CHECK_HEALTH_SCRIPT_PATH.get().get(), "--device", deviceId);
    ParisVersion parisVersion = getParisVersion();
    if (parisVersion == ParisVersion.LATEST) {
      command = command.argsAppended("--latest-paris");
    }
    CommandResult r = runParisCommand(deviceId, command);
    if (r.exitCode() == 0) {
      return true;
    }
    logger.atWarning().log("PARIS command failed with exit code: %s", r.exitCode());
    return false;
  }

  public boolean runParisRepair(String deviceId, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    Command command = Command.of(PARIS_CHECK_HEALTH_SCRIPT_PATH.get().get(), "--device", deviceId);
    logger.atInfo().log("Running PARIS command: %s", command);
    String repairType =
        testInfo
            .properties()
            .getOptional("dimension_dut_state")
            .filter(state -> state.equals("unknown") || state.equals("deploying"))
            .map(state -> "--deploy")
            .orElse("--repair");
    Duration cmdTimeout = testInfo.timer().remainingTimeJava().minusMinutes(2);
    command = command.argsAppended(repairType).timeout(cmdTimeout);
    ParisVersion parisVersion = getParisVersion();
    if (parisVersion == ParisVersion.LATEST) {
      command = command.argsAppended("--latest-paris");
    }
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "Running PARIS command with %s seconds timeout: %s",
            command.getTimeout().get().getRemainingTime().toSeconds(), command);
    CommandResult r = runParisCommand(deviceId, command);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("PARIS exit code: %s, stdout: %s, stderr: %s", r.exitCode(), r.stdout(), r.stderr());
    if (r.exitCode() == 0) {
      return true;
    } else {
      logger.atWarning().log("PARIS command failed with exit code: %s", r.exitCode());
    }
    return false;
  }
}
