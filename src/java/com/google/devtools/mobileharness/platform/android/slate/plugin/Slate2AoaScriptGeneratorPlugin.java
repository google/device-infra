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

package com.google.devtools.mobileharness.platform.android.slate.plugin;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/** Lab plugin to convert SLATE execution trace logs into an AOA 2.0 script. */
@Plugin(type = PluginType.LAB)
public final class Slate2AoaScriptGeneratorPlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @ParamAnnotation(required = false, help = "Whether Slate2Aoa script generation is enabled.")
  public static final String ENABLE_SLATE2AOA_SCRIPT_GENERATION =
      "enable_slate2aoa_script_generation";

  @ParamAnnotation(required = false, help = "Device taxonomy stage (e.g. DVT, EVT, PVT).")
  public static final String PARAM_DEVICE_STAGE = "aoa_device_stage";

  @ParamAnnotation(required = false, help = "SIM configuration (sim or no-sim).")
  public static final String PARAM_SIM_CONFIG = "aoa_sim_config";

  /** Output artifact filename where extracted AOA 2.0 commands are saved. */
  private static final String AOA_SCRIPT_FILENAME = "aoa_script.txt";

  // Regular expressions to match UI interactions from SLATE execution logs.
  // Matches touch click coordinates -> "click x y".
  private static final Pattern TAP_PATTERN =
      Pattern.compile(".*(?:tap|click).*?(\\d+)\\s*,\\s*(\\d+).*$", Pattern.CASE_INSENSITIVE);
  // Matches swipe coordinates -> "swipe x1 y1 duration x2 y2".
  private static final Pattern SWIPE_PATTERN =
      Pattern.compile(
          ".*(?:swipe|scroll).*?(\\d+)\\s*,\\s*(\\d+).*?(\\d+)\\s*,\\s*(\\d+)(?:.*?(?:duration|time)\\s*(\\d+))?.*$",
          Pattern.CASE_INSENSITIVE);
  // Matches text entry commands -> "write text".
  private static final Pattern WRITE_PATTERN =
      Pattern.compile(".*(?:write|type|input).*?[\"']([^\"']+)[\"'].*", Pattern.CASE_INSENSITIVE);
  // Matches wait intervals -> "sleep millis".
  private static final Pattern SLEEP_PATTERN =
      Pattern.compile(".*(?:sleep|wait).*?(\\d+).*", Pattern.CASE_INSENSITIVE);
  // Matches hardware home button navigation -> "home".
  private static final Pattern HOME_PATTERN =
      Pattern.compile(
          ".*(?:NAVIGATE_HOME|press\\.?home|keyevent\\s+(?:KEYCODE_)?HOME).*",
          Pattern.CASE_INSENSITIVE);
  // Matches hardware back button navigation -> "back".
  private static final Pattern BACK_PATTERN =
      Pattern.compile(
          ".*(?:NAVIGATE_BACK|press\\.?back|keyevent\\s+(?:KEYCODE_)?BACK).*",
          Pattern.CASE_INSENSITIVE);
  // Matches enter key events -> "key 66".
  private static final Pattern ENTER_PATTERN =
      Pattern.compile(
          ".*(?:KEYBOARD_ENTER|press\\.?enter|keyevent\\s+(?:KEYCODE_)?(?:ENTER|66)).*",
          Pattern.CASE_INSENSITIVE);

  private final LocalFileUtil localFileUtil;

  public Slate2AoaScriptGeneratorPlugin() {
    this(new LocalFileUtil());
  }

  @VisibleForTesting
  @Inject
  Slate2AoaScriptGeneratorPlugin(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  /**
   * Event callback invoked when a local test finishes. Extracts UI actions from slate_run.log and
   * writes the resulting AOA script and device taxonomy into test properties.
   */
  @Subscribe
  public void onTestEnding(LocalTestEndingEvent event) throws MobileHarnessException {
    TestInfo testInfo = event.getTest();
    // Only generate script when underlying test navigation passes.
    if (!isTestPassed(testInfo)) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log("Underlying test execution did not pass. Skipping AOA script generation.");
      return;
    }

    Device device = event.getLocalDevices().values().stream().findFirst().orElse(null);
    if (device == null) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log("No local device found in event. Cannot resolve taxonomy or generate AOA script.");
      return;
    }

    // Resolve hardware stage and SIM state and record them in job test properties.
    String deviceStage = resolveDeviceStage(testInfo, device);
    String simConfig = resolveSimConfig(testInfo, device);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Resolved Device Taxonomy - Stage: [%s], SIM Config: [%s]", deviceStage, simConfig);

    testInfo.properties().add("aoa_device_stage", deviceStage);
    testInfo.properties().add("aoa_sim_config", simConfig);

    Path slateLogPath = Path.of(testInfo.getGenFileDir()).resolve("slate_run.log");
    File logFile = slateLogPath.toFile();
    if (!logFile.exists()) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log("SLATE log file not found at %s. Cannot generate AOA script.", logFile);
      return;
    }

    Path aoaScriptPath = Path.of(testInfo.getGenFileDir()).resolve(AOA_SCRIPT_FILENAME);
    // Parse execution log into sequential AOA 2.0 instructions.
    List<String> aoaCommands = parseSlateLogToAoaCommands(logFile);
    if (aoaCommands.isEmpty()) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log("No AOA commands extracted from SLATE logs. Skipping AOA script generation.");
      return;
    }
    writeAoaScript(aoaScriptPath, aoaCommands);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Successfully generated %d AOA commands into %s", aoaCommands.size(), aoaScriptPath);
  }

  /** Verifies whether the test completed with a PASS result. */
  private boolean isTestPassed(TestInfo testInfo) {
    return testInfo.resultWithCause().get().type().equals(TestResult.PASS);
  }

  /**
   * Resolves the hardware stage taxonomy (e.g. DVT, EVT, PVT). Checks parameter override first,
   * otherwise checks the hardware_sku device dimension.
   */
  private String resolveDeviceStage(TestInfo testInfo, Device device) {
    String paramStage = testInfo.jobInfo().params().get(PARAM_DEVICE_STAGE);
    if (paramStage != null && !paramStage.trim().isEmpty()) {
      return paramStage.trim();
    }
    List<String> skuDimensions = device.getDimension("hardware_sku");
    if (skuDimensions != null && !skuDimensions.isEmpty()) {
      return skuDimensions.get(0);
    }
    return "UNKNOWN_STAGE";
  }

  /**
   * Resolves SIM card state (sim vs no-sim). Checks parameter override first, otherwise checks the
   * sim_state device dimension.
   */
  private String resolveSimConfig(TestInfo testInfo, Device device) {
    String paramSim = testInfo.jobInfo().params().get(PARAM_SIM_CONFIG);
    if (paramSim != null && !paramSim.trim().isEmpty()) {
      return paramSim.trim();
    }
    List<String> simStateDimensions = device.getDimension("sim_state");
    if (simStateDimensions != null && !simStateDimensions.isEmpty()) {
      String state = simStateDimensions.get(0).toLowerCase(Locale.ROOT);
      if (state.contains("ready") || state.contains("loaded")) {
        return "sim";
      }
    }
    return "no-sim";
  }

  /**
   * Reads raw SLATE output logs and converts matched UI actions (click, swipe, write, sleep, home,
   * back, enter) into WebAOA / AOA 2.0 instructions.
   */
  @VisibleForTesting
  List<String> parseSlateLogToAoaCommands(File logFile) throws MobileHarnessException {
    List<String> commands = new ArrayList<>();
    for (String line : localFileUtil.readLineListFromFile(logFile.getAbsolutePath())) {
      Matcher tapMatcher = TAP_PATTERN.matcher(line);
      if (tapMatcher.matches()) {
        commands.add(String.format("click %s %s", tapMatcher.group(1), tapMatcher.group(2)));
        continue;
      }

      Matcher swipeMatcher = SWIPE_PATTERN.matcher(line);
      if (swipeMatcher.matches()) {
        String duration = swipeMatcher.group(5) != null ? swipeMatcher.group(5) : "300";
        commands.add(
            String.format(
                "swipe %s %s %s %s %s",
                swipeMatcher.group(1),
                swipeMatcher.group(2),
                duration,
                swipeMatcher.group(3),
                swipeMatcher.group(4)));
        continue;
      }

      Matcher writeMatcher = WRITE_PATTERN.matcher(line);
      if (writeMatcher.matches()) {
        commands.add(String.format("write %s", writeMatcher.group(1)));
        continue;
      }

      Matcher sleepMatcher = SLEEP_PATTERN.matcher(line);
      if (sleepMatcher.matches()) {
        commands.add(String.format("sleep %s", sleepMatcher.group(1)));
        continue;
      }

      Matcher homeMatcher = HOME_PATTERN.matcher(line);
      if (homeMatcher.matches()) {
        commands.add("home");
        continue;
      }

      Matcher backMatcher = BACK_PATTERN.matcher(line);
      if (backMatcher.matches()) {
        commands.add("back");
        continue;
      }

      Matcher enterMatcher = ENTER_PATTERN.matcher(line);
      if (enterMatcher.matches()) {
        commands.add("key 66");
      }
    }
    return commands;
  }

  /** Writes out extracted AOA commands to disk separated by line breaks. */
  private void writeAoaScript(Path outFile, List<String> commands) throws MobileHarnessException {
    localFileUtil.writeToFile(outFile.toString(), String.join("\n", commands) + "\n");
  }
}
