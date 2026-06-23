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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Utility class for collecting device information. This is used to enforce consistent property
 * collection host-side and device-side for CTS reports.
 *
 * <p>Note that properties across sources can differ, e.g. {@code android.os.Build} properties
 * sometimes deviate from the read-only properties that they're based on.
 */
public final class DevicePropertyInfo {

  @Nullable private final String abi;
  @Nullable private final String abi2;
  @Nullable private final String abis;
  @Nullable private final String abis32;
  @Nullable private final String abis64;
  @Nullable private final String board;
  @Nullable private final String brand;
  @Nullable private final String device;
  @Nullable private final String fingerprint;
  @Nullable private final String vendorFingerprint;
  @Nullable private final String bootimageFingerprint;
  @Nullable private final String id;
  @Nullable private final String manufacturer;
  @Nullable private final String model;
  @Nullable private final String product;
  @Nullable private final String referenceFingerprint;
  @Nullable private final String serial;
  @Nullable private final String tags;
  @Nullable private final String type;
  @Nullable private final String versionBaseOs;
  @Nullable private final String versionRelease;
  @Nullable private final String versionSdk;
  @Nullable private final String versionSecurityPatch;
  @Nullable private final String versionIncremental;
  @Nullable private final String versionSdkFull;

  private DevicePropertyInfo(Builder builder) {
    this.abi = builder.abi;
    this.abi2 = builder.abi2;
    this.abis = builder.abis;
    this.abis32 = builder.abis32;
    this.abis64 = builder.abis64;
    this.board = builder.board;
    this.brand = builder.brand;
    this.device = builder.device;
    this.fingerprint = builder.fingerprint;
    this.vendorFingerprint = builder.vendorFingerprint;
    this.bootimageFingerprint = builder.bootimageFingerprint;
    this.id = builder.id;
    this.manufacturer = builder.manufacturer;
    this.model = builder.model;
    this.product = builder.product;
    this.referenceFingerprint = builder.referenceFingerprint;
    this.serial = builder.serial;
    this.tags = builder.tags;
    this.type = builder.type;
    this.versionBaseOs = builder.versionBaseOs;
    this.versionRelease = builder.versionRelease;
    this.versionSdk = builder.versionSdk;
    this.versionSecurityPatch = builder.versionSecurityPatch;
    this.versionIncremental = builder.versionIncremental;
    this.versionSdkFull = builder.versionSdkFull;
  }

  @Nullable
  public String abi() {
    return abi;
  }

  @Nullable
  public String abi2() {
    return abi2;
  }

  @Nullable
  public String abis() {
    return abis;
  }

  @Nullable
  public String abis32() {
    return abis32;
  }

  @Nullable
  public String abis64() {
    return abis64;
  }

  @Nullable
  public String board() {
    return board;
  }

  @Nullable
  public String brand() {
    return brand;
  }

  @Nullable
  public String device() {
    return device;
  }

  @Nullable
  public String fingerprint() {
    return fingerprint;
  }

  @Nullable
  public String vendorFingerprint() {
    return vendorFingerprint;
  }

  @Nullable
  public String bootimageFingerprint() {
    return bootimageFingerprint;
  }

  @Nullable
  public String id() {
    return id;
  }

  @Nullable
  public String manufacturer() {
    return manufacturer;
  }

  @Nullable
  public String model() {
    return model;
  }

  @Nullable
  public String product() {
    return product;
  }

  @Nullable
  public String referenceFingerprint() {
    return referenceFingerprint;
  }

  @Nullable
  public String serial() {
    return serial;
  }

  @Nullable
  public String tags() {
    return tags;
  }

  @Nullable
  public String type() {
    return type;
  }

  @Nullable
  public String versionBaseOs() {
    return versionBaseOs;
  }

  @Nullable
  public String versionRelease() {
    return versionRelease;
  }

  @Nullable
  public String versionSdk() {
    return versionSdk;
  }

  @Nullable
  public String versionSecurityPatch() {
    return versionSecurityPatch;
  }

  @Nullable
  public String versionIncremental() {
    return versionIncremental;
  }

  @Nullable
  public String versionSdkFull() {
    return versionSdkFull;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof DevicePropertyInfo) {
      DevicePropertyInfo that = (DevicePropertyInfo) o;
      return Objects.equals(this.abi, that.abi())
          && Objects.equals(this.abi2, that.abi2())
          && Objects.equals(this.abis, that.abis())
          && Objects.equals(this.abis32, that.abis32())
          && Objects.equals(this.abis64, that.abis64())
          && Objects.equals(this.board, that.board())
          && Objects.equals(this.brand, that.brand())
          && Objects.equals(this.device, that.device())
          && Objects.equals(this.fingerprint, that.fingerprint())
          && Objects.equals(this.vendorFingerprint, that.vendorFingerprint())
          && Objects.equals(this.bootimageFingerprint, that.bootimageFingerprint())
          && Objects.equals(this.id, that.id())
          && Objects.equals(this.manufacturer, that.manufacturer())
          && Objects.equals(this.model, that.model())
          && Objects.equals(this.product, that.product())
          && Objects.equals(this.referenceFingerprint, that.referenceFingerprint())
          && Objects.equals(this.serial, that.serial())
          && Objects.equals(this.tags, that.tags())
          && Objects.equals(this.type, that.type())
          && Objects.equals(this.versionBaseOs, that.versionBaseOs())
          && Objects.equals(this.versionRelease, that.versionRelease())
          && Objects.equals(this.versionSdk, that.versionSdk())
          && Objects.equals(this.versionSecurityPatch, that.versionSecurityPatch())
          && Objects.equals(this.versionIncremental, that.versionIncremental())
          && Objects.equals(this.versionSdkFull, that.versionSdkFull());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        abi,
        abi2,
        abis,
        abis32,
        abis64,
        board,
        brand,
        device,
        fingerprint,
        vendorFingerprint,
        bootimageFingerprint,
        id,
        manufacturer,
        model,
        product,
        referenceFingerprint,
        serial,
        tags,
        type,
        versionBaseOs,
        versionRelease,
        versionSdk,
        versionSecurityPatch,
        versionIncremental,
        versionSdkFull);
  }

  /** Builder for {@link DevicePropertyInfo}. */
  public static final class Builder {
    @Nullable private String abi;
    @Nullable private String abi2;
    @Nullable private String abis;
    @Nullable private String abis32;
    @Nullable private String abis64;
    @Nullable private String board;
    @Nullable private String brand;
    @Nullable private String device;
    @Nullable private String fingerprint;
    @Nullable private String vendorFingerprint;
    @Nullable private String bootimageFingerprint;
    @Nullable private String id;
    @Nullable private String manufacturer;
    @Nullable private String model;
    @Nullable private String product;
    @Nullable private String referenceFingerprint;
    @Nullable private String serial;
    @Nullable private String tags;
    @Nullable private String type;
    @Nullable private String versionBaseOs;
    @Nullable private String versionRelease;
    @Nullable private String versionSdk;
    @Nullable private String versionSecurityPatch;
    @Nullable private String versionIncremental;
    @Nullable private String versionSdkFull;

    public Builder() {}

    public Builder abi(@Nullable String abi) {
      this.abi = abi;
      return this;
    }

    public Builder abi2(@Nullable String abi2) {
      this.abi2 = abi2;
      return this;
    }

    public Builder abis(@Nullable String abis) {
      this.abis = abis;
      return this;
    }

    public Builder abis32(@Nullable String abis32) {
      this.abis32 = abis32;
      return this;
    }

    public Builder abis64(@Nullable String abis64) {
      this.abis64 = abis64;
      return this;
    }

    public Builder board(@Nullable String board) {
      this.board = board;
      return this;
    }

    public Builder brand(@Nullable String brand) {
      this.brand = brand;
      return this;
    }

    public Builder device(@Nullable String device) {
      this.device = device;
      return this;
    }

    public Builder fingerprint(@Nullable String fingerprint) {
      this.fingerprint = fingerprint;
      return this;
    }

    public Builder vendorFingerprint(@Nullable String vendorFingerprint) {
      this.vendorFingerprint = vendorFingerprint;
      return this;
    }

    public Builder bootimageFingerprint(@Nullable String bootimageFingerprint) {
      this.bootimageFingerprint = bootimageFingerprint;
      return this;
    }

    public Builder id(@Nullable String id) {
      this.id = id;
      return this;
    }

    public Builder manufacturer(@Nullable String manufacturer) {
      this.manufacturer = manufacturer;
      return this;
    }

    public Builder model(@Nullable String model) {
      this.model = model;
      return this;
    }

    public Builder product(@Nullable String product) {
      this.product = product;
      return this;
    }

    public Builder referenceFingerprint(@Nullable String referenceFingerprint) {
      this.referenceFingerprint = referenceFingerprint;
      return this;
    }

    public Builder serial(@Nullable String serial) {
      this.serial = serial;
      return this;
    }

    public Builder tags(@Nullable String tags) {
      this.tags = tags;
      return this;
    }

    public Builder type(@Nullable String type) {
      this.type = type;
      return this;
    }

    public Builder versionBaseOs(@Nullable String versionBaseOs) {
      this.versionBaseOs = versionBaseOs;
      return this;
    }

    public Builder versionRelease(@Nullable String versionRelease) {
      this.versionRelease = versionRelease;
      return this;
    }

    public Builder versionSdk(@Nullable String versionSdk) {
      this.versionSdk = versionSdk;
      return this;
    }

    public Builder versionSecurityPatch(@Nullable String versionSecurityPatch) {
      this.versionSecurityPatch = versionSecurityPatch;
      return this;
    }

    public Builder versionIncremental(@Nullable String versionIncremental) {
      this.versionIncremental = versionIncremental;
      return this;
    }

    public Builder versionSdkFull(@Nullable String versionSdkFull) {
      this.versionSdkFull = versionSdkFull;
      return this;
    }

    public DevicePropertyInfo build() {
      return new DevicePropertyInfo(this);
    }
  }

  public static Builder newBuilder() {
    return new Builder().bootimageFingerprint("unknown");
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
