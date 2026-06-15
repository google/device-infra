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

package com.google.devtools.mobileharness.platform.android.xts.common.util;

import com.google.auto.value.AutoValue;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Utility class for collecting device information. This is used to enforce consistent property
 * collection host-side and device-side for CTS reports.
 *
 * <p>Note that properties across sources can differ, e.g. {@code android.os.Build} properties
 * sometimes deviate from the read-only properties that they're based on.
 */
@AutoValue
public abstract class DevicePropertyInfo {

  @Nullable
  abstract String abi();

  @Nullable
  abstract String abi2();

  @Nullable
  abstract String abis();

  @Nullable
  abstract String abis32();

  @Nullable
  abstract String abis64();

  @Nullable
  abstract String board();

  @Nullable
  abstract String brand();

  @Nullable
  abstract String device();

  @Nullable
  abstract String fingerprint();

  @Nullable
  abstract String vendorFingerprint();

  @Nullable
  abstract String bootimageFingerprint();

  @Nullable
  abstract String id();

  @Nullable
  abstract String manufacturer();

  @Nullable
  abstract String model();

  @Nullable
  abstract String product();

  @Nullable
  abstract String referenceFingerprint();

  @Nullable
  abstract String serial();

  @Nullable
  abstract String tags();

  @Nullable
  abstract String type();

  @Nullable
  abstract String versionBaseOs();

  @Nullable
  abstract String versionRelease();

  @Nullable
  abstract String versionSdk();

  @Nullable
  abstract String versionSecurityPatch();

  @Nullable
  abstract String versionIncremental();

  @Nullable
  abstract String versionSdkFull();

  /** Builder for {@link DevicePropertyInfo}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder abi(@Nullable String abi);

    public abstract Builder abi2(@Nullable String abi2);

    public abstract Builder abis(@Nullable String abis);

    public abstract Builder abis32(@Nullable String abis32);

    public abstract Builder abis64(@Nullable String abis64);

    public abstract Builder board(@Nullable String board);

    public abstract Builder brand(@Nullable String brand);

    public abstract Builder device(@Nullable String device);

    public abstract Builder fingerprint(@Nullable String fingerprint);

    public abstract Builder vendorFingerprint(@Nullable String vendorFingerprint);

    public abstract Builder id(@Nullable String id);

    public abstract Builder manufacturer(@Nullable String manufacturer);

    public abstract Builder model(@Nullable String model);

    public abstract Builder product(@Nullable String product);

    public abstract Builder referenceFingerprint(@Nullable String referenceFingerprint);

    public abstract Builder serial(@Nullable String serial);

    public abstract Builder tags(@Nullable String tags);

    public abstract Builder type(@Nullable String type);

    public abstract Builder versionBaseOs(@Nullable String versionBaseOs);

    public abstract Builder versionRelease(@Nullable String versionRelease);

    public abstract Builder versionSdk(@Nullable String versionSdk);

    public abstract Builder versionSecurityPatch(@Nullable String versionSecurityPatch);

    public abstract Builder versionIncremental(@Nullable String versionIncremental);

    public abstract Builder bootimageFingerprint(@Nullable String bootimageFingerprint);

    public abstract Builder versionSdkFull(@Nullable String versionSdkFull);

    public abstract DevicePropertyInfo build();
  }

  public static Builder newBuilder() {
    return new AutoValue_DevicePropertyInfo.Builder().bootimageFingerprint("unknown");
  }

  /**
   * Return a {@code Map} with property keys prepended with a given prefix string. This is intended
   * to be used to generate entries for {@code} Build tag attributes in CTS test results.
   */
  public Map<String, String> getPropertytMapWithPrefix(String prefix) {
    Map<String, String> propertyMap = new HashMap<>();

    propertyMap.put(prefix + "abi", abi());
    propertyMap.put(prefix + "abi2", abi2());
    propertyMap.put(prefix + "abis", abis());
    propertyMap.put(prefix + "abis_32", abis32());
    propertyMap.put(prefix + "abis_64", abis64());
    propertyMap.put(prefix + "board", board());
    propertyMap.put(prefix + "brand", brand());
    propertyMap.put(prefix + "device", device());
    propertyMap.put(prefix + "fingerprint", fingerprint());
    propertyMap.put(prefix + "vendor_fingerprint", vendorFingerprint());
    propertyMap.put(prefix + "bootimage_fingerprint", bootimageFingerprint());
    propertyMap.put(prefix + "id", id());
    propertyMap.put(prefix + "manufacturer", manufacturer());
    propertyMap.put(prefix + "model", model());
    propertyMap.put(prefix + "product", product());
    propertyMap.put(prefix + "reference_fingerprint", referenceFingerprint());
    propertyMap.put(prefix + "serial", serial());
    propertyMap.put(prefix + "tags", tags());
    propertyMap.put(prefix + "type", type());
    propertyMap.put(prefix + "version_base_os", versionBaseOs());
    propertyMap.put(prefix + "version_release", versionRelease());
    propertyMap.put(prefix + "version_sdk", versionSdk());
    propertyMap.put(prefix + "version_security_patch", versionSecurityPatch());
    propertyMap.put(prefix + "version_incremental", versionIncremental());
    propertyMap.put(prefix + "version_sdk_full", versionSdkFull());

    return propertyMap;
  }
}
