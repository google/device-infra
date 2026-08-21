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

package com.google.devtools.mobileharness.shared.usmf.builtin.adb;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Models an Android device managed within the USMF mock ADB environment.
 *
 * <p>Encapsulates device serial number, connection/authorization status, system properties
 * (accessible via {@code getprop}), and installed package names.
 */
public final class MockAndroidDevice {

  /** Connection and authorization status of a mock Android device. */
  public enum DeviceStatus {
    DEVICE("device"),
    OFFLINE("offline"),
    UNAUTHORIZED("unauthorized"),
    DISCONNECTED("disconnected");

    private final String statusString;

    DeviceStatus(String statusString) {
      this.statusString = statusString;
    }

    public String getStatusString() {
      return statusString;
    }

    /**
     * Parses a {@link DeviceStatus} from a raw string, defaulting to {@link #DEVICE} if
     * unrecognized.
     */
    public static DeviceStatus fromString(String status) {
      if (status == null) {
        return DEVICE;
      }
      for (DeviceStatus ds : values()) {
        if (ds.statusString.equalsIgnoreCase(status.trim())) {
          return ds;
        }
      }
      return DEVICE;
    }
  }

  private final String serial;
  private final DeviceStatus status;
  private final ImmutableMap<String, String> properties;
  private final ImmutableSet<String> installedPackages;

  /** Creates a new {@link Builder} with the specified serial number. */
  public static Builder builder(String serial) {
    return new Builder(serial);
  }

  /**
   * Creates a default mock device with standard emulator properties.
   *
   * @param serial the serial number for the device
   */
  public static MockAndroidDevice defaultDevice(String serial) {
    return builder(serial)
        .setStatus(DeviceStatus.DEVICE)
        .setProperty("ro.product.model", "sdk_gphone64_x86_64")
        .setProperty("ro.product.brand", "google")
        .setProperty("ro.product.manufacturer", "Google")
        .setProperty("ro.product.name", "sdk_gphone64_x86_64")
        .setProperty("ro.product.device", "emulator64_x86_64")
        .setProperty("ro.product.board", "goldfish_x86_64")
        .setProperty("ro.build.version.sdk", "34")
        .setProperty("ro.build.version.release", "14")
        .setProperty("ro.build.type", "userdebug")
        .setProperty("ro.build.tags", "release-keys")
        .setProperty("sys.boot_completed", "1")
        .setProperty("dev.bootcomplete", "1")
        .setProperty("init.svc.bootanim", "stopped")
        .setProperty("gsm.sim.state", "READY")
        .addPackage("com.google.android.youtube")
        .addPackage("com.android.settings")
        .build();
  }

  /**
   * Creates a mock Pixel 7 device preset.
   *
   * @param serial the serial number for the device
   */
  public static MockAndroidDevice pixel7(String serial) {
    return builder(serial)
        .setStatus(DeviceStatus.DEVICE)
        .setProperty("ro.product.model", "Pixel 7")
        .setProperty("ro.product.brand", "google")
        .setProperty("ro.product.manufacturer", "Google")
        .setProperty("ro.product.name", "panther")
        .setProperty("ro.product.device", "panther")
        .setProperty("ro.product.board", "panther")
        .setProperty("ro.build.version.sdk", "34")
        .setProperty("ro.build.version.release", "14")
        .setProperty("ro.build.type", "userdebug")
        .setProperty("ro.build.tags", "release-keys")
        .setProperty("ro.build.id", "UQ1A.240105.004")
        .setProperty("ro.build.flavor", "panther-userdebug")
        .setProperty("sys.boot_completed", "1")
        .setProperty("dev.bootcomplete", "1")
        .setProperty("init.svc.bootanim", "stopped")
        .setProperty("gsm.sim.state", "READY")
        .addPackage("com.google.android.youtube")
        .addPackage("com.google.android.apps.photos")
        .addPackage("com.android.settings")
        .build();
  }

  /**
   * Deserializes a {@link MockAndroidDevice} from a state JSON object with the specified serial.
   *
   * @param serial the device serial number
   * @param json the JSON object representing device state
   */
  public static MockAndroidDevice fromJsonObject(String serial, JsonObject json) {
    checkNotNull(serial, "serial cannot be null");
    checkNotNull(json, "json cannot be null");
    Builder builder = builder(serial);

    if (json.has("status") && json.get("status").isJsonPrimitive()) {
      builder.setStatus(DeviceStatus.fromString(json.get("status").getAsString()));
    }
    if (json.has("props") && json.get("props").isJsonObject()) {
      JsonObject propsObj = json.getAsJsonObject("props");
      for (Map.Entry<String, JsonElement> entry : propsObj.entrySet()) {
        if (entry.getValue().isJsonPrimitive()) {
          builder.setProperty(entry.getKey(), entry.getValue().getAsString());
        }
      }
    }
    if (json.has("installed_packages") && json.get("installed_packages").isJsonArray()) {
      JsonArray pkgs = json.getAsJsonArray("installed_packages");
      for (JsonElement elem : pkgs) {
        if (elem.isJsonPrimitive()) {
          builder.addPackage(elem.getAsString());
        }
      }
    }
    return builder.build();
  }

  private MockAndroidDevice(
      String serial,
      DeviceStatus status,
      ImmutableMap<String, String> properties,
      ImmutableSet<String> installedPackages) {
    this.serial = checkNotNull(serial);
    this.status = checkNotNull(status);
    this.properties = checkNotNull(properties);
    this.installedPackages = checkNotNull(installedPackages);
  }

  /** Returns the device serial number. */
  public String getSerial() {
    return serial;
  }

  /** Returns the current device connection and authorization status. */
  public DeviceStatus getStatus() {
    return status;
  }

  /** Returns whether the device is currently online and ready to accept commands. */
  public boolean isOnline() {
    return status == DeviceStatus.DEVICE;
  }

  /** Returns an immutable map of system properties for this device. */
  public ImmutableMap<String, String> getProperties() {
    return properties;
  }

  /**
   * Returns the value of a specific system property, or {@code null} if not defined.
   *
   * @param key the property key (e.g., "ro.product.model")
   */
  public String getProperty(String key) {
    return properties.get(key);
  }

  /** Returns an immutable set of installed package names on this device. */
  public ImmutableSet<String> getInstalledPackages() {
    return installedPackages;
  }

  /** Serializes this device model into a JSON object matching the USMF state schema. */
  public JsonObject toJsonObject() {
    JsonObject json = new JsonObject();
    json.addProperty("status", status.getStatusString());

    JsonObject propsJson = new JsonObject();
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      propsJson.addProperty(entry.getKey(), entry.getValue());
    }
    json.add("props", propsJson);

    JsonArray packagesJson = new JsonArray();
    for (String pkg : installedPackages) {
      packagesJson.add(pkg);
    }
    json.add("installed_packages", packagesJson);
    return json;
  }

  /** Creates a {@link Builder} initialized with the state of this device. */
  public Builder toBuilder() {
    Builder builder = new Builder(serial);
    builder.setStatus(status);
    builder.setProperties(properties);
    builder.addPackages(installedPackages);
    return builder;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof MockAndroidDevice other)) {
      return false;
    }
    return Objects.equals(serial, other.serial)
        && status == other.status
        && Objects.equals(properties, other.properties)
        && Objects.equals(installedPackages, other.installedPackages);
  }

  @Override
  public int hashCode() {
    return Objects.hash(serial, status, properties, installedPackages);
  }

  @Override
  public String toString() {
    return String.format(
        "MockAndroidDevice{serial=%s, status=%s, propertiesCount=%d, packagesCount=%d}",
        serial, status, properties.size(), installedPackages.size());
  }

  /** Builder for constructing {@link MockAndroidDevice} instances. */
  public static final class Builder {
    private final String serial;
    private DeviceStatus status = DeviceStatus.DEVICE;
    private final Map<String, String> properties = new LinkedHashMap<>();
    private final Set<String> installedPackages = new LinkedHashSet<>();

    private Builder(String serial) {
      this.serial = checkNotNull(serial, "serial cannot be null");
    }

    /** Sets the connection and authorization status of the device. */
    @CanIgnoreReturnValue
    public Builder setStatus(DeviceStatus status) {
      this.status = checkNotNull(status, "status cannot be null");
      return this;
    }

    /** Sets or overrides a system property on the device. */
    @CanIgnoreReturnValue
    public Builder setProperty(String key, String value) {
      checkNotNull(key, "property key cannot be null");
      checkNotNull(value, "property value cannot be null");
      this.properties.put(key, value);
      return this;
    }

    /** Sets multiple system properties on the device. */
    @CanIgnoreReturnValue
    public Builder setProperties(Map<String, String> properties) {
      checkNotNull(properties, "properties cannot be null");
      this.properties.putAll(properties);
      return this;
    }

    /** Adds an installed package to the device. */
    @CanIgnoreReturnValue
    public Builder addPackage(String packageName) {
      checkNotNull(packageName, "packageName cannot be null");
      this.installedPackages.add(packageName);
      return this;
    }

    /** Adds multiple installed packages to the device. */
    @CanIgnoreReturnValue
    public Builder addPackages(Iterable<String> packageNames) {
      checkNotNull(packageNames, "packageNames cannot be null");
      for (String pkg : packageNames) {
        addPackage(pkg);
      }
      return this;
    }

    /** Builds the configured {@link MockAndroidDevice} instance. */
    public MockAndroidDevice build() {
      return new MockAndroidDevice(
          serial, status, ImmutableMap.copyOf(properties), ImmutableSet.copyOf(installedPackages));
    }
  }
}
