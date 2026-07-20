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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Enums;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidDumpSysSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.List;
import java.util.Locale;

/**
 * Driver decorator for retrieving Android dumpsys log of a test. It can save the log in file and
 * send to client or test result service, or just save in the {@link TestInfo}.
 *
 * <p>To run multiple dumpsys commands, the user will provide a list of `dumpsys` commands by
 * specifying a comma-separated list in the `dumpsys_type` parameter, e.g. "dumpsys_type":
 * "uimode,display,batterystats". Suffixes and log file names are in the same format, in order. For
 * example, suppose the user provides the following:
 *
 * <ul>
 *   <li>"dumpsys_type": "uimode,display,batterystats",
 *   <li>"dumpsys_suffix": "input,all,all",
 *   <li>"dumpsys_log_file_name": "uimode_info.txt,display_info.txt,battery_info.txt",
 * </ul>
 *
 * <p>The decorator will:
 *
 * <ul>
 *   <li>Run `uimode input` and store the contents in "uimode_info.txt"
 *   <li>Run `display all` and store the contents in "display_info.txt"
 *   <li>Run `batterystats all` and store the contents in "battery_info.txt"
 * </ul>
 *
 * <p>An empty entry in `dumpsys_type` (e.g. ",," in "input,,") will run the default dumpsys
 * command, `dumpsys activity`. dumpsys_suffix will be populated with the default if the
 * corresponding entry is empty or missing. A log file will be generated if the corresponding
 * dumpsys_log_file_name entry is empty or missing.
 */
@DecoratorAnnotation(
    help =
        "For retrieving Android dumpsys log and send them back to client or test result service.")
public final class AndroidDumpSysDecorator extends LifecycleDecorator {

  /** Template which adds header and footer to the dumpsys log. */
  private static final String LOG_TEMPLATE =
      "========= Beginning of \"%s %s\" log%n" + "%s%n========= End of dumpsys log";

  private static final Splitter SPLITTER = Splitter.on(",").trimResults();

  private final SystemStateManager systemStateManager;

  /** {@code AndroidAdbUtil} for dumpsys command. */
  private final AndroidAdbUtil adbUtil;

  /** {@code FileUtil} for writing device log to file. */
  private final LocalFileUtil fileUtil;

  /** Logger for this device. */
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private ImmutableList<DumpSysCommandInfo> dumpSysCommands;
  private boolean logToFile;

  /**
   * Constructor. Do NOT modify the parameter list. This constructor is required by the lab server
   * framework.
   */
  public AndroidDumpSysDecorator(Driver decoratedDriver, TestInfo testInfo) {
    this(
        decoratedDriver,
        testInfo,
        new SystemStateManager(),
        new AndroidAdbUtil(),
        new LocalFileUtil());
  }

  @VisibleForTesting
  AndroidDumpSysDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      SystemStateManager systemStateManager,
      AndroidAdbUtil adbUtil,
      LocalFileUtil fileUtil) {
    super(decoratedDriver, testInfo);
    this.systemStateManager = systemStateManager;
    this.adbUtil = adbUtil;
    this.fileUtil = fileUtil;
  }

  @Override
  protected void setUp(SetupContext context) {
    TestInfo testInfo = context.testInfo();
    JobInfo jobInfo = testInfo.jobInfo();
    logToFile = jobInfo.params().isTrue(AndroidDumpSysSpec.PARAM_LOG_TO_FILE);
    dumpSysCommands = makeDumpSysCommands(jobInfo);
  }

  @Override
  protected void tearDown(TeardownContext context) throws InterruptedException {
    TestInfo testInfo = context.testInfo();
    if (testInfo.getRootTest().resultWithCause().get().type() == TestResult.PASS
        && !testInfo.jobInfo().params().getBool(AndroidDumpSysSpec.PARAM_DUMPSYS_ON_PASS, true)) {
      testInfo.log().atInfo().alsoTo(logger).log("Skip dumpsys when test passed");
      return;
    }
    if (dumpSysCommands == null) {
      return;
    }
    String deviceId = getDevice().getDeviceId();
    // Only invokes the dumpsys command when the device is detected.
    MobileHarnessException deviceOnlineException = null;
    boolean isDeviceOnline = false;
    try {
      isDeviceOnline = systemStateManager.isOnline(deviceId);
    } catch (MobileHarnessException e) {
      deviceOnlineException = e;
    }

    if (deviceOnlineException != null || !isDeviceOnline) {
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_DUMPSYS_DECORATOR_DEVICE_NOT_FOUND,
                  "Skip dumpsys because the device is disconnected",
                  deviceOnlineException),
              logger);
    } else {
      testInfo.log().atInfo().alsoTo(logger).log("Start to dumpsys");
      String rawLog = null;
      MobileHarnessException dumpSysError = null;
      for (DumpSysCommandInfo dumpSysCommand : dumpSysCommands) {
        DumpSysType dumpSysType = dumpSysCommand.type();
        String dumpSysTypeStr = dumpSysType.getTypeValue();
        String dumpSysSuffix = dumpSysCommand.suffix();
        String dumpSysLogName = dumpSysCommand.logName();
        try {
          // If type = none or all, suffix will be ignored.
          if (dumpSysType.equals(DumpSysType.NONE) || dumpSysType.equals(DumpSysType.ALL)) {
            rawLog = adbUtil.dumpSys(deviceId);
          } else {
            rawLog = adbUtil.dumpSys(deviceId, dumpSysType, dumpSysSuffix);
          }
        } catch (MobileHarnessException e) {
          // Does NOT throw out the exception, otherwise the test will be marked as ERROR, which
          // may already pass.
          testInfo
              .warnings()
              .addAndLog(
                  new MobileHarnessException(
                      AndroidErrorId.ANDROID_DUMPSYS_DECORATOR_DUMPSYS_COMMAND_ERROR,
                      String.format("Failed to run command dumpsys on device %s", deviceId),
                      e),
                  logger);
          dumpSysError = e;
        }
        if (dumpSysError == null) {
          String fullLog = String.format(LOG_TEMPLATE, dumpSysTypeStr, dumpSysSuffix, rawLog);
          if (logToFile) {
            try {
              // Logs to file only.
              String logFilePath = testInfo.getGenFileDir() + "/" + dumpSysLogName;
              testInfo.log().atInfo().log("Writing dumpsys log to file");
              fileUtil.writeToFile(logFilePath, fullLog);
              testInfo.log().atInfo().alsoTo(logger).log("Dumpsys log saved to %s", logFilePath);
            } catch (MobileHarnessException e) {
              testInfo
                  .warnings()
                  .addAndLog(
                      new MobileHarnessException(
                          AndroidErrorId.ANDROID_DUMPSYS_DECORATOR_WRITE_LOG_FILE_ERROR,
                          "Failed to write dumpsys log to file.",
                          e),
                      logger);
            }
          } else {
            // Logs in TestInfo only.
            testInfo.log().atInfo().log("%s", fullLog);
            logger.atInfo().log(
                "\n%s",
                String.format(LOG_TEMPLATE, dumpSysTypeStr, dumpSysSuffix, StrUtil.tail(rawLog)));
          }
        }
      }
    }
  }

  /**
   * Parses the dumpsys parameters from job info and matches command properties to each dumpsys
   * command.
   *
   * <p>Generate comamnds from the list of dumpsys types, based on the Mobile Harness param
   * `AndroidDumpSysSpec.PARAM_DUMPSYS_TYPE` (as a comma-separated list of commands). If there is an
   * empty command, use the default comamnd, `PARAM_DUMPSYS_TYPE_DEFAULT`. Suffixes and log file
   * names are applied in order. If there are none, apply defaults.
   *
   * @param jobInfo the jobInfo containing input parameters for this decorator.
   * @return a list of dumpsys commands and settings.
   */
  private static ImmutableList<DumpSysCommandInfo> makeDumpSysCommands(JobInfo jobInfo) {
    ImmutableList.Builder<DumpSysCommandInfo> dumpSysCommands = ImmutableList.builder();
    String dumpSysTypeParam =
        jobInfo
            .params()
            .get(
                AndroidDumpSysSpec.PARAM_DUMPSYS_TYPE,
                AndroidDumpSysSpec.PARAM_DUMPSYS_TYPE_DEFAULT);
    List<String> dumpSysTypes = SPLITTER.splitToList(dumpSysTypeParam);

    String dumpSysSuffixParam =
        jobInfo
            .params()
            .get(
                AndroidDumpSysSpec.PARAM_DUMPSYS_SUFFIX,
                AndroidDumpSysSpec.PARAM_DUMPSYS_SUFFIX_DEFAULT);
    List<String> dumpSysSuffixes = SPLITTER.splitToList(dumpSysSuffixParam);

    String dumpSysLogNameParam =
        jobInfo
            .params()
            .get(
                AndroidDumpSysSpec.PARAM_LOG_FILE_NAME,
                AndroidDumpSysSpec.PARAM_LOG_FILE_NAME_DEFAULT);
    List<String> dumpSysLogNameInputs = SPLITTER.splitToList(dumpSysLogNameParam);

    for (int i = 0; i < dumpSysTypes.size(); i++) {
      String dumpSysSuffix =
          dumpSysSuffixes.size() > i && !dumpSysSuffixes.get(i).isEmpty()
              ? dumpSysSuffixes.get(i)
              : AndroidDumpSysSpec.PARAM_DUMPSYS_SUFFIX_DEFAULT;
      String dumpSysLogName =
          dumpSysLogNameInputs.size() > i && !dumpSysLogNameInputs.get(i).isEmpty()
              ? dumpSysLogNameInputs.get(i)
              : String.format("dumpsys_%s.log", i);

      String dumpSysTypeInput = dumpSysTypes.get(i);
      DumpSysType dumpSysType =
          Enums.getIfPresent(DumpSysType.class, dumpSysTypeInput.toUpperCase(Locale.ROOT))
              .or(DumpSysType.ACTIVITY);
      dumpSysCommands.add(new DumpSysCommandInfo(dumpSysType, dumpSysSuffix, dumpSysLogName));
    }

    return dumpSysCommands.build();
  }

  record DumpSysCommandInfo(DumpSysType type, String suffix, String logName) {}
}
