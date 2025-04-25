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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbOutputUtil.convertPropertyValueToBoolean;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty.DoNotAddToDimension;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty.KeepDimensionValueCase;
import com.google.devtools.mobileharness.platform.android.shared.constant.Splitters;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.device.BaseDevice;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Value;
import java.lang.annotation.Annotation;

/** Helper class for Android Device. */
public class AndroidDeviceHelper {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String PROPERTY_NAME_CACHED_ABI = "cached_abi";

  public static final String PROPERTY_NAME_CACHED_SCREEN_DENSITY = "cached_screen_density";

  public static final String PROPERTY_NAME_CACHED_SDK_VERSION = "cached_sdk_version";

  /** Output of adb command may contain daemon lines before or after the starter line. */
  private static final String OUTPUT_ADB_DAEMON = "* daemon";

  private static final ImmutableSet<AndroidProperty> RETAIN_CAPS_PROPERTIES =
      getAnnotatedAndroidProperties(KeepDimensionValueCase.class);

  public static final ImmutableSet<AndroidProperty> PROP_NOT_SET_AS_DIMENSION =
      getAnnotatedAndroidProperties(DoNotAddToDimension.class);

  private final AndroidAdbUtil androidAdbUtil;

  public AndroidDeviceHelper() {
    this(new AndroidAdbUtil());
  }

  public AndroidDeviceHelper(AndroidAdbUtil androidAdbUtil) {
    this.androidAdbUtil = androidAdbUtil;
  }

  /**
   * Adds all the system properties to its dimensions. If a property not found, will not add the
   * property the dimensions.
   */
  @CanIgnoreReturnValue
  public boolean updateAndroidPropertyDimensions(BaseDevice device)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    boolean isDimensionChanged = false;
    for (AndroidProperty key : AndroidProperty.values()) {
      if (PROP_NOT_SET_AS_DIMENSION.contains(key)) {
        continue;
      }
      ImmutableSet<String> oldValues =
          ImmutableSet.copyOf(device.getDimension(Ascii.toLowerCase(key.name())));
      String value = getPropertyValue(deviceId, key);
      if (!value.isEmpty()) {
        ImmutableSet<String> values = maybeLowerCaseProperty(key, value);
        if (!oldValues.equals(values)) {
          logger.atInfo().log(
              "Dimension %s=%s (was: %s), device_id=%s",
              Ascii.toLowerCase(key.name()), values, oldValues, deviceId);
          device.updateDimension(Ascii.toLowerCase(key.name()), values.toArray(new String[0]));
          isDimensionChanged = true;
        }
      }

      switch (key) {
        case ABI:
          device.setProperty(PROPERTY_NAME_CACHED_ABI, value);
          break;
        case RELEASE_VERSION:
          // Expose major version as a dimension, as many clients do not care about minor version.
          String majorVersion = extractMajorVersionFromFullVersion(value);
          logger.atInfo().log(
              "Dimension %s=%s, device_id=%s",
              Ascii.toLowerCase(Dimension.Name.RELEASE_VERSION_MAJOR.toString()),
              Ascii.toLowerCase(majorVersion),
              deviceId);
          device.updateDimension(
              Dimension.Name.RELEASE_VERSION_MAJOR, Ascii.toLowerCase(majorVersion));
          break;
        case SCREEN_DENSITY:
          try {
            device.setProperty(
                PROPERTY_NAME_CACHED_SCREEN_DENSITY, Integer.toString(Integer.parseInt(value)));
          } catch (NumberFormatException e) {
            logger.atWarning().log(
                "Failed to parse device %s screen density '%s' from device property: %s",
                deviceId, value, MoreThrowables.shortDebugString(e));
          }
          break;
        case SDK_VERSION:
          try {
            device.setProperty(
                PROPERTY_NAME_CACHED_SDK_VERSION, Integer.toString(Integer.parseInt(value)));
          } catch (NumberFormatException e) {
            logger.atWarning().log(
                "Failed to parse device %s sdk version '%s' from device property: %s",
                deviceId, value, MoreThrowables.shortDebugString(e));
          }
          break;
        default:
          break;
      }
    }
    isDimensionChanged |= updatePersistTestHarnessRequiredDimension(device);
    return isDimensionChanged;
  }

  /**
   * Updates the required dimension persist_test_harness:false based on the device property
   * persist.sys.test_harness.
   *
   * <p>The value for the required dimension persist_test_harness can only be set to false.
   *
   * <p>If the persist_test_harness:false required dimension is not set, and the
   * persist.sys.test_harness property not true, then the persist_test_harness:false required
   * dimension will be added to prevent the device from tests to enable the test harness.
   *
   * <p>If the persist_test_harness:false required dimension is set, and the
   * persist.sys.test_harness property is true, then the persist_test_harness:false required
   * dimension will be removed.
   */
  @CanIgnoreReturnValue
  public boolean updatePersistTestHarnessRequiredDimension(BaseDevice device)
      throws InterruptedException, MobileHarnessException {
    boolean isDimensionChanged = false;
    if (Flags.instance().keepTestHarnessFalse.getNonNull()) {
      String deviceId = device.getDeviceId();

      String key = Ascii.toLowerCase(AndroidProperty.PERSIST_TEST_HARNESS.name());
      ImmutableSet<String> oldValues = ImmutableSet.copyOf(device.getRequiredDimension(key));
      boolean value =
          convertPropertyValueToBoolean(
                  getPropertyValue(deviceId, AndroidProperty.PERSIST_TEST_HARNESS))
              .orElse(false);
      if (value) {
        if (!oldValues.isEmpty()) {
          device.info().dimensions().required().remove(key);
          isDimensionChanged = true;
          logger.atInfo().log(
              "Required dimension %s=%s removed, device_id=%s", key, oldValues, deviceId);
        }
      } else if (oldValues.isEmpty()) {
        device.updateRequiredDimension(key, Value.FALSE);
        isDimensionChanged = true;
        logger.atInfo().log("Required dimension %s=%s added, device_id=%s", key, "false", deviceId);
      }
    }
    return isDimensionChanged;
  }

  /**
   * Returns an ImmutableSet containing the original value of a property (only if the property is
   * annotated with {@link KeepDimensionValueCase}), and the lower-cased value of the property
   * (always), in order.
   */
  public ImmutableSet<String> maybeLowerCaseProperty(AndroidProperty key, String value) {
    ImmutableSet.Builder<String> valuesBuilder = ImmutableSet.builder();
    if (RETAIN_CAPS_PROPERTIES.contains(key)) {
      valuesBuilder.add(value);
    }
    return valuesBuilder.add(Ascii.toLowerCase(value)).build();
  }

  /**
   * Extracts first two elements and returns them dot-separated, from a 3-element version string.
   * Example: "6.0.1" -> "6.0". If the input does not contain two dot characters, returns the
   * argument unchanged.
   */
  private static String extractMajorVersionFromFullVersion(String fullVersion) {
    // Finding a character is faster than parsing regular expressions.
    int firstDotIndex = fullVersion.indexOf('.');
    if (firstDotIndex != -1) {
      int secondDotIndex = fullVersion.indexOf('.', firstDotIndex + 1);
      if (secondDotIndex > firstDotIndex && secondDotIndex < fullVersion.length() - 1) {
        return fullVersion.substring(0, secondDotIndex);
      }
    }
    return fullVersion;
  }

  /** Gets property value. */
  public String getPropertyValue(String deviceId, AndroidProperty key)
      throws MobileHarnessException, InterruptedException {
    String propertyValue = androidAdbUtil.getProperty(deviceId, key);
    if (propertyValue.startsWith(OUTPUT_ADB_DAEMON)) {
      propertyValue =
          Splitters.LINE_SPLITTER
              .omitEmptyStrings()
              .trimResults()
              .splitToStream(propertyValue)
              .filter(line -> !line.startsWith(OUTPUT_ADB_DAEMON))
              .collect(joining("\n"));
    }
    return propertyValue;
  }

  /** Gets all enum items of {@link AndroidProperty} that are annotated by the given annotation. */
  private static ImmutableSet<AndroidProperty> getAnnotatedAndroidProperties(
      Class<? extends Annotation> annotation) {
    return stream(AndroidProperty.class.getFields())
        .filter(field -> field.isAnnotationPresent(annotation))
        .map(
            field -> {
              try {
                return (AndroidProperty) field.get(null);
              } catch (IllegalAccessException e) {
                throw new AssertionError(e);
              }
            })
        .collect(toImmutableSet());
  }
}
