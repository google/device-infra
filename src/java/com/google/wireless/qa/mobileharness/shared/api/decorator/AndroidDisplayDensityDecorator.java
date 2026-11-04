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

import static java.util.Comparator.comparingLong;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupResult;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.SetupOnlyDecorator;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decorator for recording the density and size of every built-in physical display of an Android
 * device, e.g., the inner and outer screens of a foldable device.
 *
 * <p>The native {@code screen_density} dimension only reflects {@code ro.sf.lcd_density}, which is
 * a single value even for foldable devices with two physical screens. This decorator parses {@code
 * adb shell dumpsys display} and writes the following test properties, which show up in Sponge as
 * {@code dimension_*} properties:
 *
 * <ul>
 *   <li>{@code dimension_display_count}: number of built-in physical displays.
 *   <li>{@code dimension_inner_screen_density} / {@code dimension_inner_screen_size}: the largest
 *       built-in display (only when the device has 2+ built-in displays).
 *   <li>{@code dimension_outer_screen_density} / {@code dimension_outer_screen_size}: the smallest
 *       built-in display (only when the device has 2+ built-in displays).
 * </ul>
 *
 * <p>The properties are only attached to the current test; the device dimensions used for device
 * allocation are NOT modified. Failures to collect the info are logged and never fail the test.
 */
@DecoratorAnnotation(
    help =
        "Records the density and size of each built-in display (e.g. inner/outer screens of a"
            + " foldable device) into dimension_* test properties, parsed from 'adb shell dumpsys"
            + " display'. Never fails the test.")
public class AndroidDisplayDensityDecorator extends SetupOnlyDecorator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Matches one {@code DisplayDeviceInfo{...}} entry in {@code dumpsys display}, for example:
   *
   * <pre>
   * DisplayDeviceInfo{"Built-in Screen": uniqueId="local:4619827259835644672", 1812 x 2176,
   *     modeId 1, ..., density 420, 403.411 x 399.737 dpi, ...}
   * </pre>
   */
  private static final Pattern DISPLAY_DEVICE_INFO_PATTERN =
      Pattern.compile(
          "DisplayDeviceInfo\\{\"(?<name>[^\"]*)\": uniqueId=\"(?<uniqueId>[^\"]*)\","
              + " (?<width>\\d+) x (?<height>\\d+),.*?, density (?<density>\\d+),");

  /** Unique ID prefix of built-in physical displays (created by LocalDisplayAdapter). */
  private static final String BUILT_IN_DISPLAY_UNIQUE_ID_PREFIX = "local:";

  @VisibleForTesting static final String DIMENSION_DISPLAY_COUNT = "display_count";
  @VisibleForTesting static final String DIMENSION_INNER_SCREEN_DENSITY = "inner_screen_density";
  @VisibleForTesting static final String DIMENSION_INNER_SCREEN_SIZE = "inner_screen_size";
  @VisibleForTesting static final String DIMENSION_OUTER_SCREEN_DENSITY = "outer_screen_density";
  @VisibleForTesting static final String DIMENSION_OUTER_SCREEN_SIZE = "outer_screen_size";

  /** A built-in physical display parsed from {@code dumpsys display}. */
  @VisibleForTesting
  record PhysicalDisplay(String name, String uniqueId, int width, int height, int density) {
    long area() {
      return (long) width * height;
    }

    /** Same format as the native {@code screen_size} dimension, e.g., "2448x1848". */
    String size() {
      return width + "x" + height;
    }
  }

  private final AndroidAdbUtil androidAdbUtil;

  /**
   * Constructor. Do NOT modify the parameter list. This constructor is required by the lab server
   * framework.
   */
  public AndroidDisplayDensityDecorator(Driver decoratedDriver, TestInfo testInfo) {
    this(decoratedDriver, testInfo, new AndroidAdbUtil());
  }

  @VisibleForTesting
  AndroidDisplayDensityDecorator(
      Driver decoratedDriver, TestInfo testInfo, AndroidAdbUtil androidAdbUtil) {
    super(decoratedDriver, testInfo);
    this.androidAdbUtil = androidAdbUtil;
  }

  @Override
  protected SetupResult setUp(SetupContext context) throws InterruptedException {
    TestInfo testInfo = context.testInfo();
    String deviceId = getDevice().getDeviceId();

    String dumpsysOutput;
    try {
      dumpsysOutput = androidAdbUtil.dumpSys(deviceId, DumpSysType.DISPLAY);
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to run 'dumpsys display' on device %s, skip display density info", deviceId);
      return SetupResult.continueDecorated();
    }

    ImmutableMap<String, String> dimensions = getDisplayDimensions(dumpsysOutput);
    for (Map.Entry<String, String> dimension : dimensions.entrySet()) {
      testInfo
          .properties()
          .add(PropertyName.Test.PREFIX_DIMENSION + dimension.getKey(), dimension.getValue());
    }
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Display dimensions of device %s: %s", deviceId, dimensions);
    return SetupResult.continueDecorated();
  }

  /**
   * Converts the output of {@code dumpsys display} to dimensions (without the {@code dimension_}
   * prefix).
   */
  @VisibleForTesting
  static ImmutableMap<String, String> getDisplayDimensions(String dumpsysOutput) {
    ImmutableList<PhysicalDisplay> displays = parseBuiltInDisplays(dumpsysOutput);
    ImmutableMap.Builder<String, String> dimensions = ImmutableMap.builder();
    dimensions.put(DIMENSION_DISPLAY_COUNT, String.valueOf(displays.size()));
    if (displays.size() >= 2) {
      PhysicalDisplay inner = displays.stream().max(comparingLong(PhysicalDisplay::area)).get();
      PhysicalDisplay outer = displays.stream().min(comparingLong(PhysicalDisplay::area)).get();
      dimensions
          .put(DIMENSION_INNER_SCREEN_DENSITY, String.valueOf(inner.density()))
          .put(DIMENSION_INNER_SCREEN_SIZE, inner.size())
          .put(DIMENSION_OUTER_SCREEN_DENSITY, String.valueOf(outer.density()))
          .put(DIMENSION_OUTER_SCREEN_SIZE, outer.size());
    }
    return dimensions.buildOrThrow();
  }

  /**
   * Parses the built-in physical displays from the output of {@code dumpsys display}.
   *
   * <p>The same display may be printed multiple times (e.g., in "Display Devices" and "Logical
   * Displays" sections), so displays are de-duplicated by unique ID, keeping the first occurrence.
   */
  @VisibleForTesting
  static ImmutableList<PhysicalDisplay> parseBuiltInDisplays(String dumpsysOutput) {
    Map<String, PhysicalDisplay> displays = new LinkedHashMap<>();
    Matcher matcher = DISPLAY_DEVICE_INFO_PATTERN.matcher(dumpsysOutput);
    while (matcher.find()) {
      String uniqueId = matcher.group("uniqueId");
      if (!uniqueId.startsWith(BUILT_IN_DISPLAY_UNIQUE_ID_PREFIX)) {
        continue;
      }
      displays.putIfAbsent(
          uniqueId,
          new PhysicalDisplay(
              matcher.group("name"),
              uniqueId,
              Integer.parseInt(matcher.group("width")),
              Integer.parseInt(matcher.group("height")),
              Integer.parseInt(matcher.group("density"))));
    }
    return ImmutableList.copyOf(displays.values());
  }
}
