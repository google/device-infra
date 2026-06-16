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

package com.google.devtools.mobileharness.infra.ats.common;

import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.proto.SessionRequestInfo;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Option;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.TargetPreparer;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAdbShellDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAtsDynamicConfigPusherDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidDeviceSettingsDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidFilePullerDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.ApkPreconditionCheckDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.DeviceInfoCollectorDecoratorSpec;
import java.util.List;

/**
 * Mappers to translate Tradefed TargetPreparer definitions into Mobile Harness lifecycle
 * decorators.
 */
public final class XtsPreconditionsMapper {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String TF_APK_PRECONDITION_CHECK =
      "com.android.compatibility.common.tradefed.targetprep.ApkPreconditionCheck";
  private static final String TF_DEVICE_FILE_COLLECTOR =
      "com.android.compatibility.common.tradefed.targetprep.DeviceFileCollector";
  private static final String TF_DEVICE_INFO_COLLECTOR =
      "com.android.compatibility.common.tradefed.targetprep.DeviceInfoCollector";
  private static final String TF_DYNAMIC_CONFIG_PUSHER =
      "com.android.compatibility.common.tradefed.targetprep.DynamicConfigPusher";
  private static final String TF_PACKAGE_DISABLER =
      "com.android.compatibility.common.tradefed.targetprep.PackageDisabler";
  private static final String TF_REPORT_INTEGRITY_COLLECTOR =
      "com.android.compatibility.common.tradefed.targetprep.ReportIntegrityCollector";
  private static final String TF_REPORT_LOG_COLLECTOR =
      "com.android.compatibility.common.tradefed.targetprep.ReportLogCollector";
  private static final String TF_RUN_COMMAND =
      "com.android.tradefed.targetprep.RunCommandTargetPreparer";
  private static final String TF_SETTINGS_PREPARER =
      "com.android.compatibility.common.tradefed.targetprep.SettingsPreparer";
  private static final String TF_STAY_AWAKE =
      "com.android.compatibility.common.tradefed.targetprep.StayAwakePreparer";
  private static final String TF_WIFI_CHECK =
      "com.android.compatibility.common.tradefed.targetprep.WifiCheck";

  private XtsPreconditionsMapper() {}

  /**
   * Maps Tradefed TargetPreparer entries from suite preconditions to Mobile Harness decorators and
   * attaches them to the given SubDeviceSpec.Builder or JobConfig.Builder.
   */
  public static void mapAndAttachDecorators(
      SubDeviceSpec.Builder subDeviceSpecBuilder,
      JobConfig.Builder jobConfigBuilder,
      List<TargetPreparer> targetPreparers,
      SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException {
    if (targetPreparers == null || targetPreparers.isEmpty()) {
      return;
    }

    for (TargetPreparer preparer : targetPreparers) {
      String clazz = preparer.getClazz();
      if (clazz.isEmpty()) {
        continue;
      }
      switch (clazz) {
        case TF_SETTINGS_PREPARER -> attachSettingsPreparer(subDeviceSpecBuilder, preparer);
        case TF_APK_PRECONDITION_CHECK ->
            attachApkPreconditionCheck(subDeviceSpecBuilder, preparer, sessionRequestInfo);
        case TF_WIFI_CHECK -> attachWifiCheck(subDeviceSpecBuilder, jobConfigBuilder, preparer);
        case TF_DEVICE_INFO_COLLECTOR ->
            attachDeviceInfoCollector(subDeviceSpecBuilder, preparer, sessionRequestInfo);
        case TF_REPORT_INTEGRITY_COLLECTOR ->
            attachReportIntegrityCollector(subDeviceSpecBuilder, preparer);
        case TF_REPORT_LOG_COLLECTOR ->
            attachReportLogCollector(subDeviceSpecBuilder, jobConfigBuilder, preparer);
        case TF_DEVICE_FILE_COLLECTOR -> attachDeviceFileCollector(subDeviceSpecBuilder, preparer);
        case TF_DYNAMIC_CONFIG_PUSHER -> attachDynamicConfigPusher(subDeviceSpecBuilder, preparer);
        case TF_RUN_COMMAND -> attachRunCommand(subDeviceSpecBuilder, preparer);
        case TF_STAY_AWAKE -> attachStayAwakePreparer(subDeviceSpecBuilder);
        case TF_PACKAGE_DISABLER -> attachPackageDisabler(subDeviceSpecBuilder, preparer);
        default ->
            logger.atInfo().log("Unsupported or unmapped root-level target preparer: %s", clazz);
      }
    }
  }

  private static void attachSettingsPreparer(
      SubDeviceSpec.Builder subDeviceSpecBuilder, TargetPreparer preparer)
      throws MobileHarnessException {
    String deviceSetting = getOptionValue(preparer, "device-setting");
    String setValue = getOptionValue(preparer, "set-value");
    String settingType = getOptionValue(preparer, "setting-type");

    if (Strings.isNullOrEmpty(deviceSetting)) {
      return;
    }

    if (deviceSetting.equals("screen-always-on") || "screen_always_on".equals(deviceSetting)) {
      AndroidDeviceSettingsDecoratorSpec spec =
          AndroidDeviceSettingsDecoratorSpec.newBuilder()
              .setScreenAlwaysOn(parseBoolean(setValue, true))
              .build();
      subDeviceSpecBuilder
          .getDecoratorsBuilder()
          .addContent(
              JobConfig.Driver.newBuilder()
                  .setName("AndroidDeviceSettingsDecorator")
                  .setParam(printProto(spec))
                  .build());
      return;
    }

    if (deviceSetting.equals("screen-adaptive-brightness")
        || deviceSetting.equals("screen_adaptive_brightness")) {
      AndroidDeviceSettingsDecoratorSpec spec =
          AndroidDeviceSettingsDecoratorSpec.newBuilder()
              .setScreenAdaptiveBrightness(parseBoolean(setValue, true))
              .build();
      subDeviceSpecBuilder
          .getDecoratorsBuilder()
          .addContent(
              JobConfig.Driver.newBuilder()
                  .setName("AndroidDeviceSettingsDecorator")
                  .setParam(printProto(spec))
                  .build());
      return;
    }

    // Generic ADB shell command fallback
    if (Strings.isNullOrEmpty(settingType)) {
      settingType = "global";
    }
    String cmd =
        String.format(
            "settings put %s %s%s",
            settingType, deviceSetting, setValue != null ? " " + setValue : "");
    AndroidAdbShellDecoratorSpec spec =
        AndroidAdbShellDecoratorSpec.newBuilder().setAdbShellBeforeTest(cmd).build();
    subDeviceSpecBuilder
        .getDecoratorsBuilder()
        .addContent(
            JobConfig.Driver.newBuilder()
                .setName("AndroidAdbShellDecorator")
                .setParam(printProto(spec))
                .build());
  }

  private static void attachApkPreconditionCheck(
      SubDeviceSpec.Builder subDeviceSpecBuilder,
      TargetPreparer preparer,
      SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException {
    String apkName = getOptionValue(preparer, "apk");
    String packageName = getOptionValue(preparer, "package");

    if (Strings.isNullOrEmpty(apkName)) {
      return;
    }

    String xtsType = sessionRequestInfo.getXtsType();
    String xtsRootDir = sessionRequestInfo.getXtsRootDir();
    String xtsTestDir = xtsRootDir + "/android-" + xtsType + "/testcases";

    ApkPreconditionCheckDecoratorSpec.Builder specBuilder =
        ApkPreconditionCheckDecoratorSpec.newBuilder().setApk(apkName).setXtsTestDir(xtsTestDir);

    if (!Strings.isNullOrEmpty(packageName)) {
      specBuilder.setPackageName(packageName);
    }

    subDeviceSpecBuilder
        .getDecoratorsBuilder()
        .addContent(
            JobConfig.Driver.newBuilder()
                .setName("ApkPreconditionCheckDecorator")
                .setParam(printProto(specBuilder.build()))
                .build());
  }

  private static void attachWifiCheck(
      SubDeviceSpec.Builder subDeviceSpecBuilder,
      JobConfig.Builder jobConfigBuilder,
      TargetPreparer preparer) {
    subDeviceSpecBuilder
        .getDecoratorsBuilder()
        .addContent(JobConfig.Driver.newBuilder().setName("AndroidSetWifiDecorator").build());

    boolean hasSsid = false;
    for (Option option : preparer.getOptionsList()) {
      String name = option.getName();
      if (name.equals("wifi-ssid")) {
        jobConfigBuilder.getParamsBuilder().putContent("wifi_ssid", option.getValue());
        hasSsid = true;
      } else if (name.equals("wifi-psk")) {
        jobConfigBuilder.getParamsBuilder().putContent("wifi_psk", option.getValue());
      }
    }
    if (!hasSsid) {
      jobConfigBuilder.getParamsBuilder().putContent("wifi_ssid_optional", "true");
    }
  }

  private static void attachDeviceInfoCollector(
      SubDeviceSpec.Builder subDeviceSpecBuilder,
      TargetPreparer preparer,
      SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException {
    String apk = getOptionValue(preparer, "apk");
    String packageName = getOptionValue(preparer, "package");
    String srcDir = getOptionValue(preparer, "src-dir");
    String destDir = getOptionValue(preparer, "dest-dir");

    String xtsType = sessionRequestInfo.getXtsType();
    String xtsRootDir = sessionRequestInfo.getXtsRootDir();
    String xtsTestDir = xtsRootDir + "/android-" + xtsType + "/testcases";

    DeviceInfoCollectorDecoratorSpec.Builder specBuilder =
        DeviceInfoCollectorDecoratorSpec.newBuilder().setXtsTestDir(xtsTestDir);

    if (!Strings.isNullOrEmpty(apk)) {
      specBuilder.setApk(apk);
    } else {
      specBuilder.setApk("CtsDeviceInfo.apk");
    }

    if (!Strings.isNullOrEmpty(packageName)) {
      specBuilder.setPackageName(packageName);
    }

    if (!Strings.isNullOrEmpty(srcDir)) {
      specBuilder.setSrcDir(srcDir);
    }

    if (!Strings.isNullOrEmpty(destDir)) {
      specBuilder.setDestDir(destDir);
    }

    subDeviceSpecBuilder
        .getDecoratorsBuilder()
        .addContent(
            JobConfig.Driver.newBuilder()
                .setName("DeviceInfoCollectorDecorator")
                .setParam(printProto(specBuilder.build()))
                .build());
  }

  private static void attachReportIntegrityCollector(
      SubDeviceSpec.Builder subDeviceSpecBuilder, TargetPreparer preparer) {
    subDeviceSpecBuilder
        .getDecoratorsBuilder()
        .addContent(
            JobConfig.Driver.newBuilder().setName("ReportIntegrityCollectorDecorator").build());
  }

  private static void attachReportLogCollector(
      SubDeviceSpec.Builder subDeviceSpecBuilder,
      JobConfig.Builder jobConfigBuilder,
      TargetPreparer preparer) {
    subDeviceSpecBuilder
        .getDecoratorsBuilder()
        .addContent(JobConfig.Driver.newBuilder().setName("ReportLogCollectorDecorator").build());

    for (Option option : preparer.getOptionsList()) {
      String name = option.getName();
      if (name.equals("src-dir") || "report_log_src_dir".equals(name)) {
        jobConfigBuilder.getParamsBuilder().putContent("report_log_src_dir", option.getValue());
      } else if (name.equals("dest-dir") || "report_log_dest_dir".equals(name)) {
        jobConfigBuilder.getParamsBuilder().putContent("report_log_dest_dir", option.getValue());
      } else if (name.equals("temp-dir") || "report_log_temp_dir".equals(name)) {
        jobConfigBuilder.getParamsBuilder().putContent("report_log_temp_dir", option.getValue());
      } else {
        jobConfigBuilder.getParamsBuilder().putContent(name, option.getValue());
      }
    }
    jobConfigBuilder.getParamsBuilder().putContent("report_log_device_dir", "true");
  }

  private static void attachDeviceFileCollector(
      SubDeviceSpec.Builder subDeviceSpecBuilder, TargetPreparer preparer)
      throws MobileHarnessException {
    String srcFile = getOptionValue(preparer, "src-file");
    String destFile = getOptionValue(preparer, "dest-file");

    if (Strings.isNullOrEmpty(srcFile) || Strings.isNullOrEmpty(destFile)) {
      return;
    }

    AndroidFilePullerDecoratorSpec.Builder specBuilder =
        AndroidFilePullerDecoratorSpec.newBuilder()
            .setFilePathOnDevice(srcFile)
            .setPulledFileDir(destFile);

    for (Option option : preparer.getOptionsList()) {
      if ("property".equals(option.getName())) {
        String key = option.getKey();
        String val = option.getValue();
        if (!Strings.isNullOrEmpty(key)) {
          specBuilder.putProperty(key, val);
        } else if (!Strings.isNullOrEmpty(val) && val.contains("=")) {
          int index = val.indexOf('=');
          String propName = val.substring(0, index).trim();
          String propValue = val.substring(index + 1).trim();
          if (!propName.isEmpty()) {
            specBuilder.putProperty(propName, propValue);
          }
        }
      }
    }

    subDeviceSpecBuilder
        .getDecoratorsBuilder()
        .addContent(
            JobConfig.Driver.newBuilder()
                .setName("AndroidFilePullerDecorator")
                .setParam(printProto(specBuilder.build()))
                .build());
  }

  private static void attachDynamicConfigPusher(
      SubDeviceSpec.Builder subDeviceSpecBuilder, TargetPreparer preparer)
      throws MobileHarnessException {
    String configFilename = getOptionValue(preparer, "config-filename");
    String dynamicResourceName = getOptionValue(preparer, "dynamic-resource-name");
    String extractFromResourceStr = getOptionValue(preparer, "extract-from-resource");

    AndroidAtsDynamicConfigPusherDecoratorSpec.Builder specBuilder =
        AndroidAtsDynamicConfigPusherDecoratorSpec.newBuilder();

    if (!Strings.isNullOrEmpty(configFilename)) {
      specBuilder.setConfigFilename(configFilename);
    }
    if (!Strings.isNullOrEmpty(dynamicResourceName)) {
      specBuilder.setDynamicResourceName(dynamicResourceName);
    }
    if (!Strings.isNullOrEmpty(extractFromResourceStr)) {
      specBuilder.setExtractFromResource(parseBoolean(extractFromResourceStr, false));
    }

    subDeviceSpecBuilder
        .getDecoratorsBuilder()
        .addContent(
            JobConfig.Driver.newBuilder()
                .setName("AndroidAtsDynamicConfigPusherDecorator")
                .setParam(printProto(specBuilder.build()))
                .build());
  }

  private static void attachRunCommand(
      SubDeviceSpec.Builder subDeviceSpecBuilder, TargetPreparer preparer)
      throws MobileHarnessException {
    StringBuilder beforeCommands = new StringBuilder();
    StringBuilder afterCommands = new StringBuilder();

    for (Option option : preparer.getOptionsList()) {
      String name = option.getName();
      String value = option.getValue();
      if (Strings.isNullOrEmpty(value)) {
        continue;
      }
      if ("run-command".equals(name)) {
        if (beforeCommands.length() > 0) {
          beforeCommands.append(',');
        }
        beforeCommands.append(value.replace(",", "\\,"));
      } else if ("teardown-command".equals(name)) {
        if (afterCommands.length() > 0) {
          afterCommands.append(',');
        }
        afterCommands.append(value.replace(",", "\\,"));
      }
    }

    if (beforeCommands.length() == 0 && afterCommands.length() == 0) {
      return;
    }

    AndroidAdbShellDecoratorSpec.Builder specBuilder = AndroidAdbShellDecoratorSpec.newBuilder();
    if (beforeCommands.length() > 0) {
      specBuilder.setAdbShellBeforeTest(beforeCommands.toString());
    }
    if (afterCommands.length() > 0) {
      specBuilder.setAdbShellAfterTest(afterCommands.toString());
    }

    subDeviceSpecBuilder
        .getDecoratorsBuilder()
        .addContent(
            JobConfig.Driver.newBuilder()
                .setName("AndroidAdbShellDecorator")
                .setParam(printProto(specBuilder.build()))
                .build());
  }

  private static void attachStayAwakePreparer(SubDeviceSpec.Builder subDeviceSpecBuilder)
      throws MobileHarnessException {
    AndroidDeviceSettingsDecoratorSpec spec =
        AndroidDeviceSettingsDecoratorSpec.newBuilder().setScreenAlwaysOn(true).build();
    subDeviceSpecBuilder
        .getDecoratorsBuilder()
        .addContent(
            JobConfig.Driver.newBuilder()
                .setName("AndroidDeviceSettingsDecorator")
                .setParam(printProto(spec))
                .build());
  }

  private static void attachPackageDisabler(
      SubDeviceSpec.Builder subDeviceSpecBuilder, TargetPreparer preparer)
      throws MobileHarnessException {
    StringBuilder commands = new StringBuilder();
    for (Option option : preparer.getOptionsList()) {
      String name = option.getName();
      String value = option.getValue();
      if (Strings.isNullOrEmpty(value)) {
        continue;
      }
      if ("package".equals(name)) {
        if (commands.length() > 0) {
          commands.append(',');
        }
        commands.append(String.format("pm disable-user --user 0 %s", value));
      }
    }

    if (commands.length() == 0) {
      return;
    }

    AndroidAdbShellDecoratorSpec spec =
        AndroidAdbShellDecoratorSpec.newBuilder()
            .setAdbShellBeforeTest(commands.toString())
            .build();
    subDeviceSpecBuilder
        .getDecoratorsBuilder()
        .addContent(
            JobConfig.Driver.newBuilder()
                .setName("AndroidAdbShellDecorator")
                .setParam(printProto(spec))
                .build());
  }

  private static String getOptionValue(TargetPreparer preparer, String optionName) {
    for (Option option : preparer.getOptionsList()) {
      if (option.getName().equals(optionName)) {
        return option.getValue();
      }
    }
    return null;
  }

  private static boolean parseBoolean(String val, boolean defaultValue) {
    if (Strings.isNullOrEmpty(val)) {
      return defaultValue;
    }
    if (Ascii.equalsIgnoreCase("1", val) || "true".equalsIgnoreCase(val)) {
      return true;
    }
    if (Ascii.equalsIgnoreCase("0", val) || "false".equalsIgnoreCase(val)) {
      return false;
    }
    return Boolean.parseBoolean(val);
  }

  private static String printProto(MessageOrBuilder spec) throws MobileHarnessException {
    try {
      return JsonFormat.printer().print(spec);
    } catch (Exception e) {
      throw new MobileHarnessException(
          InfraErrorId.XTS_ILLEGAL_DEVICE_SPEC, "Failed to format decorator spec as JSON", e);
    }
  }
}
