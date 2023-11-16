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

package com.google.devtools.mobileharness.infra.ats.console.result.xml;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

/** Util class for processing CTS result XML file. */
public class XmlResultUtil {

  // XML constants
  // <Result> element
  @VisibleForTesting static final String START_TIME_ATTR = "start";
  @VisibleForTesting static final String END_TIME_ATTR = "end";
  @VisibleForTesting static final String START_DISPLAY_TIME_ATTR = "start_display";
  @VisibleForTesting static final String END_DISPLAY_TIME_ATTR = "end_display";
  @VisibleForTesting static final String DEVICES_ATTR = "devices";
  @VisibleForTesting static final String HOST_NAME_ATTR = "host_name";
  @VisibleForTesting static final String OS_NAME_ATTR = "os_name";
  @VisibleForTesting static final String OS_VERSION_ATTR = "os_version";
  @VisibleForTesting static final String OS_ARCH_ATTR = "os_arch";
  @VisibleForTesting static final String JAVA_VENDOR_ATTR = "java_vendor";
  @VisibleForTesting static final String JAVA_VERSION_ATTR = "java_version";
  @VisibleForTesting static final String SUITE_NAME_ATTR = "suite_name";
  @VisibleForTesting static final String SUITE_VERSION_ATTR = "suite_version";
  @VisibleForTesting static final String SUITE_PLAN_ATTR = "suite_plan";
  @VisibleForTesting static final String SUITE_BUILD_ATTR = "suite_build_number";
  @VisibleForTesting static final String SUITE_CTS_VERIFIER_MODE_ATTR = "suite_cts_verifier_mode";

  // Values of device properties need to be shown in the <Build> element. Mimic from
  // {@code com.android.compatibility.common.tradefed.targetprep.DeviceInfoCollector},
  // {@code com.android.cts.verifier.TestResultsReport}
  private static final String ABI = "ro.product.cpu.abi";
  private static final String ABI2 = "ro.product.cpu.abi2";
  private static final String ABIS = "ro.product.cpu.abilist";
  private static final String ABIS_32 = "ro.product.cpu.abilist32";
  private static final String ABIS_64 = "ro.product.cpu.abilist64";
  private static final String BOARD = "ro.product.board";
  private static final String BOOTIMAGE_FINGERPRINT = "ro.bootimage.build.fingerprint";
  private static final String BRAND = "ro.product.brand";
  private static final String DEVICE = "ro.product.device";
  private static final String FINGERPRINT = "ro.build.fingerprint";
  private static final String ID = "ro.build.id";
  private static final String MANUFACTURER = "ro.product.manufacturer";
  private static final String MODEL = "ro.product.model";
  private static final String PRODUCT = "ro.product.name";
  private static final String REFERENCE_FINGERPRINT = "ro.build.reference.fingerprint";
  private static final String SERIAL = "ro.serialno";
  private static final String TAGS = "ro.build.tags";
  private static final String TYPE = "ro.build.type";
  private static final String VENDOR_FINGERPRINT = "ro.vendor.build.fingerprint";
  private static final String VERSION_BASE_OS = "ro.build.version.base_os";
  private static final String VERSION_INCREMENTAL = "ro.build.version.incremental";
  private static final String VERSION_RELEASE = "ro.build.version.release";
  private static final String VERSION_RELEASE_OR_CODENAME = "ro.build.version.release_or_codename";
  private static final String VERSION_SDK = "ro.build.version.sdk";
  private static final String VERSION_SECURITY_PATCH = "ro.build.version.security_patch";

  // Suite version for different sdk versions. From
  // https://source.android.com/docs/compatibility/cts/downloads
  private static final ImmutableMap<Integer, String> SUITE_VERSION =
      ImmutableMap.<Integer, String>builder()
          .put(33, "13.0_r1")
          .put(32, "12.1_r3")
          .put(31, "12.0_r5")
          .put(30, "11.0_r9")
          .put(29, "10.0_r13")
          .buildOrThrow();

  private final AndroidAdbUtil androidAdbUtil;

  public XmlResultUtil() {
    this(new AndroidAdbUtil());
  }

  @VisibleForTesting
  XmlResultUtil(AndroidAdbUtil androidAdbUtil) {
    this.androidAdbUtil = androidAdbUtil;
  }

  /**
   * Prepares attributes for the CTS XML <Result> element.
   *
   * @param startTime start time of the suite run
   * @param endTime end time of the suite run
   * @param devices serial number for devices under test
   */
  public ImmutableMap<String, String> prepareResultElementAttrs(
      Instant startTime, Instant endTime, ImmutableList<String> devices) {
    ImmutableMap.Builder<String, String> attrs = ImmutableMap.builder();

    attrs.put(START_TIME_ATTR, Long.toString(startTime.toEpochMilli()));
    attrs.put(END_TIME_ATTR, Long.toString(endTime.toEpochMilli()));
    attrs.put(START_DISPLAY_TIME_ATTR, toReadableDateString(startTime));
    attrs.put(END_DISPLAY_TIME_ATTR, toReadableDateString(endTime));

    attrs.putAll(getResultSuiteAttrs(devices.stream().findFirst().orElse(null)));

    // Device Info
    String deviceList = "";
    if (!devices.isEmpty()) {
      deviceList = Joiner.on(",").join(devices);
    }
    attrs.put(DEVICES_ATTR, deviceList);

    // Host Info
    attrs.put(HOST_NAME_ATTR, getHostName());
    attrs.put(OS_NAME_ATTR, getSystemProperty("os.name"));
    attrs.put(OS_VERSION_ATTR, getSystemProperty("os.version"));
    attrs.put(OS_ARCH_ATTR, getSystemProperty("os.arch"));
    attrs.put(JAVA_VENDOR_ATTR, getSystemProperty("java.vendor"));
    attrs.put(JAVA_VERSION_ATTR, getSystemProperty("java.version"));

    return attrs.buildOrThrow();
  }

  private ImmutableMap<String, String> getResultSuiteAttrs(@Nullable String serial) {
    ImmutableMap.Builder<String, String> attrs = ImmutableMap.builder();
    attrs.put(SUITE_NAME_ATTR, "CTS_VERIFIER");
    attrs.put(SUITE_CTS_VERIFIER_MODE_ATTR, "automated");
    int sdkVersion = 0;
    if (serial != null) {
      try {
        sdkVersion = androidAdbUtil.getIntProperty(serial, AndroidProperty.SDK_VERSION);
      } catch (MobileHarnessException e) {
        // Ignored
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }
    attrs.put(SUITE_VERSION_ATTR, SUITE_VERSION.getOrDefault(sdkVersion, "unknown"));
    attrs.put(SUITE_PLAN_ATTR, "verifier");
    attrs.put(SUITE_BUILD_ATTR, "0");
    return attrs.buildOrThrow();
  }

  /** Prepares attributes which are about device info for the CTS XML <Build> element. */
  public ImmutableMap<String, String> prepareBuildElementAttrs(String serial) {
    int sdkVersion = 0;
    try {
      sdkVersion = androidAdbUtil.getIntProperty(serial, AndroidProperty.SDK_VERSION);
    } catch (MobileHarnessException e) {
      // Ignored
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
    DevicePropertyInfo devicePropertyInfo =
        DevicePropertyInfo.of(
            ABI,
            ABI2,
            ABIS,
            ABIS_32,
            ABIS_64,
            BOARD,
            BOOTIMAGE_FINGERPRINT,
            BRAND,
            DEVICE,
            FINGERPRINT,
            ID,
            MANUFACTURER,
            MODEL,
            PRODUCT,
            REFERENCE_FINGERPRINT,
            SERIAL,
            TAGS,
            TYPE,
            VENDOR_FINGERPRINT,
            VERSION_BASE_OS,
            VERSION_INCREMENTAL,
            sdkVersion >= 30 ? VERSION_RELEASE_OR_CODENAME : VERSION_RELEASE,
            VERSION_SDK,
            VERSION_SECURITY_PATCH);

    ImmutableMap.Builder<String, String> attrs = ImmutableMap.builder();
    for (Map.Entry<String, String> entry : devicePropertyInfo.getPropertyMap().entrySet()) {
      String propValue = "";
      try {
        propValue = androidAdbUtil.getProperty(serial, ImmutableList.of(entry.getValue()));
      } catch (MobileHarnessException e) {
        // Ignored
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
      attrs.put(entry.getKey(), propValue);
    }
    return attrs.buildOrThrow();
  }

  /**
   * Return the given time as a {@link String} suitable for displaying.
   *
   * <p>Example: Fri Aug 20 21:21:56 PDT 2022
   *
   * @param time the epoch time in ms since midnight Jan 1, 1970
   */
  private static String toReadableDateString(Instant time) {
    return new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.getDefault())
        .format(new Timestamp(time.toEpochMilli()));
  }

  @VisibleForTesting
  String getHostName() {
    String hostName = "";
    try {
      hostName = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException ignored) {
      // ignored
    }
    return hostName;
  }

  @VisibleForTesting
  String getSystemProperty(String key) {
    @Nullable String propValue = System.getProperty(key);
    if (propValue == null) {
      return "";
    }
    return propValue;
  }

  @AutoValue
  abstract static class DevicePropertyInfo {
    // Device property names
    static DevicePropertyInfo of(
        String abi,
        String abi2,
        String abis,
        String abis32,
        String abis64,
        String board,
        String bootimageFingerprint,
        String brand,
        String device,
        String fingerprint,
        String id,
        String manufacturer,
        String model,
        String product,
        String referenceFingerprint,
        String serial,
        String tags,
        String type,
        String vendorFingerprint,
        String versionBaseOs,
        String versionIncremental,
        String versionRelease,
        String versionSdk,
        String versionSecurityPatch) {
      return new com.google.devtools.mobileharness.infra.ats.console.result.xml
          .AutoValue_XmlResultUtil_DevicePropertyInfo(
          abi,
          abi2,
          abis,
          abis32,
          abis64,
          board,
          bootimageFingerprint,
          brand,
          device,
          fingerprint,
          id,
          manufacturer,
          model,
          product,
          referenceFingerprint,
          serial,
          tags,
          type,
          vendorFingerprint,
          versionBaseOs,
          versionIncremental,
          versionRelease,
          versionSdk,
          versionSecurityPatch);
    }

    abstract String abi();

    abstract String abi2();

    abstract String abis();

    abstract String abis32();

    abstract String abis64();

    abstract String board();

    abstract String bootimageFingerprint();

    abstract String brand();

    abstract String device();

    abstract String fingerprint();

    abstract String id();

    abstract String manufacturer();

    abstract String model();

    abstract String product();

    abstract String referenceFingerprint();

    abstract String serial();

    abstract String tags();

    abstract String type();

    abstract String vendorFingerprint();

    abstract String versionBaseOs();

    abstract String versionIncremental();

    abstract String versionRelease();

    abstract String versionSdk();

    abstract String versionSecurityPatch();

    /**
     * Returns a {@code Map} which is intended to be used to generate entries for <Build> element
     * tag attributes in CTS test results.
     */
    ImmutableMap<String, String> getPropertyMap() {
      return ImmutableMap.<String, String>builder()
          .put("build_abi", abi())
          .put("build_abi2", abi2())
          .put("build_abis", abis())
          .put("build_abis_32", abis32())
          .put("build_abis_64", abis64())
          .put("build_board", board())
          .put("build_bootimage_fingerprint", bootimageFingerprint())
          .put("build_brand", brand())
          .put("build_device", device())
          .put("build_fingerprint", fingerprint())
          .put("build_id", id())
          .put("build_manufacturer", manufacturer())
          .put("build_model", model())
          .put("build_product", product())
          .put("build_reference_fingerprint", referenceFingerprint())
          .put("build_serial", serial())
          .put("build_tags", tags())
          .put("build_type", type())
          .put("build_vendor_fingerprint", vendorFingerprint())
          .put("build_version_base_os", versionBaseOs())
          .put("build_version_incremental", versionIncremental())
          .put("build_version_release", versionRelease())
          .put("build_version_sdk", versionSdk())
          .put("build_version_security_patch", versionSecurityPatch())
          .buildOrThrow();
    }
  }
}
