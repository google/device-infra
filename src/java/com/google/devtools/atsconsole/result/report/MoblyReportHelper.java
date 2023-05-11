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

package com.google.devtools.atsconsole.result.report;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.atsconsole.result.proto.ReportProto.Attribute;
import com.google.devtools.atsconsole.result.proto.ReportProto.AttributeList;
import com.google.devtools.atsconsole.result.xml.XmlConstants;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.protobuf.TextFormat;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/** Util to help generating a Mobly report. */
public class MoblyReportHelper {

  /**
   * Device build related info need to be shown in the <Build> element in the report. Mimic from
   * {@code com.android.compatibility.common.tradefed.targetprep.DeviceInfoCollector}, {@code
   * com.android.tradefed.invoker.InvocationExecution} for device_kernel_info, system_img_info and
   * vendor_img_info.
   */
  public enum DeviceBuildInfo {
    ABI("build_abi", "ro.product.cpu.abi"),
    ABI2("build_abi2", "ro.product.cpu.abi2"),
    ABIS("build_abis", "ro.product.cpu.abilist"),
    ABIS_32("build_abis_32", "ro.product.cpu.abilist32"),
    ABIS_64("build_abis_64", "ro.product.cpu.abilist64"),
    BOARD("build_board", "ro.product.board"),
    BOOTIMAGE_FINGERPRINT("build_bootimage_fingerprint", "ro.bootimage.build.fingerprint"),
    BRAND("build_brand", "ro.product.brand"),
    DEVICE("build_device", "ro.product.device"),
    FINGERPRINT("build_fingerprint", "ro.build.fingerprint"),
    ID("build_id", "ro.build.id"),
    MANUFACTURER("build_manufacturer", "ro.product.manufacturer"),
    MODEL("build_model", "ro.product.model"),
    PRODUCT("build_product", "ro.product.name"),
    REFERENCE_FINGERPRINT("build_reference_fingerprint", "ro.build.reference.fingerprint"),
    SERIAL("build_serial", "ro.serialno"),
    TAGS("build_tags", "ro.build.tags"),
    TYPE("build_type", "ro.build.type"),
    VENDOR_FINGERPRINT("build_vendor_fingerprint", "ro.vendor.build.fingerprint"),
    VERSION_BASE_OS("build_version_base_os", "ro.build.version.base_os"),
    VERSION_INCREMENTAL("build_version_incremental", "ro.build.version.incremental"),
    VERSION_RELEASE("build_version_release", "ro.build.version.release"),
    VERSION_SDK("build_version_sdk", "ro.build.version.sdk"),
    VERSION_SECURITY_PATCH("build_version_security_patch", "ro.build.version.security_patch"),
    SYSTEM_IMG_INFO(XmlConstants.SYSTEM_IMG_INFO_ATTR, "ro.system.build.fingerprint"),
    VENDOR_IMG_INFO(XmlConstants.VENDOR_IMG_INFO_ATTR, "ro.vendor.build.fingerprint");

    private final String attributeName;
    private final String propName;

    private DeviceBuildInfo(String attributeName, String propName) {
      this.attributeName = attributeName;
      this.propName = propName;
    }

    public String getAttributeName() {
      return attributeName;
    }

    public String getPropName() {
      return propName;
    }
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String MOBLY_REPORT_BUILD_ATTR_TEXTPROTO_FILE_NAME =
      "mobly_run_build_attributes.textproto";

  private static final Pattern PROP_NAME_TO_VALUE_PATTERN =
      Pattern.compile("\\[(?<propname>[\\s\\S]+)\\]:\\s+\\[(?<propvalue>[\\s\\S]*)\\]");

  private final Adb adb;
  private final LocalFileUtil localFileUtil;

  @Inject
  MoblyReportHelper(Adb adb, LocalFileUtil localFileUtil) {
    this.adb = adb;
    this.localFileUtil = localFileUtil;
  }

  /**
   * Collects the info for the device with {@code serial}, which are added in the <Build> element in
   * the report.
   *
   * <p>The info will be stored in a text proto file (based on proto message {@link AttributeList})
   * with file name {@link #MOBLY_REPORT_BUILD_ATTR_TEXTPROTO_FILE_NAME} in the directory {@code
   * outputDir}.
   */
  public void generateBuildAttributesFile(String serial, Path outputDir)
      throws MobileHarnessException, InterruptedException {
    ImmutableList<Attribute> attributes =
        generateBuildAttributes(serial).entrySet().stream()
            .map(e -> Attribute.newBuilder().setKey(e.getKey()).setValue(e.getValue()).build())
            .collect(toImmutableList());

    localFileUtil.writeToFile(
        outputDir.resolve(MOBLY_REPORT_BUILD_ATTR_TEXTPROTO_FILE_NAME).toAbsolutePath().toString(),
        TextFormat.printer()
            .printToString(AttributeList.newBuilder().addAllAttribute(attributes).build()));
  }

  /**
   * Collects the info for the device with {@code serial}, which are added in the <Build> element in
   * the report.
   */
  @VisibleForTesting
  ImmutableMap<String, String> generateBuildAttributes(String serial)
      throws MobileHarnessException, InterruptedException {
    String allProps = adb.runShellWithRetry(serial, "getprop");
    ImmutableSet<String> targetProps = getTargetProps();

    Map<String, String> propNameToValueMap = new HashMap<>();
    Splitter.onPattern("\r\n|\n|\r")
        .trimResults()
        .omitEmptyStrings()
        .splitToStream(allProps)
        .forEach(
            line -> {
              Matcher matcher = PROP_NAME_TO_VALUE_PATTERN.matcher(line);
              if (matcher.find()) {
                String propName = matcher.group("propname");
                String propValue = matcher.group("propvalue");
                if (targetProps.contains(propName)) {
                  propNameToValueMap.put(propName, propValue);
                }
              }
            });

    ImmutableMap.Builder<String, String> buildAttributesMap = ImmutableMap.builder();
    for (DeviceBuildInfo deviceBuildInfo : DeviceBuildInfo.values()) {
      buildAttributesMap.put(
          deviceBuildInfo.getAttributeName(),
          propNameToValueMap.getOrDefault(deviceBuildInfo.getPropName(), ""));
    }

    String kernelInfoResult = "";
    try {
      kernelInfoResult = adb.runShellWithRetry(serial, "uname -a").trim();
    } catch (MobileHarnessException e) {
      logger.atInfo().log(
          "Failed to get kernel info for device %s: %s",
          serial, MoreThrowables.shortDebugString(e, 0));
    }
    if (!Strings.isNullOrEmpty(kernelInfoResult)) {
      buildAttributesMap.put(XmlConstants.DEVICE_KERNEL_INFO_ATTR, kernelInfoResult);
    }

    return buildAttributesMap.buildOrThrow();
  }

  private static ImmutableSet<String> getTargetProps() {
    ImmutableSet.Builder<String> props = ImmutableSet.builder();
    for (DeviceBuildInfo deviceBuildInfo : DeviceBuildInfo.values()) {
      props.add(deviceBuildInfo.getPropName());
    }
    return props.build();
  }
}
