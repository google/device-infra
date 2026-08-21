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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.shared.usmf.UsmfBinary;
import com.google.devtools.mobileharness.shared.usmf.UsmfBinary.CommandInvocation;
import com.google.devtools.mobileharness.shared.usmf.UsmfEnvironment;
import com.google.devtools.mobileharness.shared.usmf.builtin.adb.MockAndroidDevice.DeviceStatus;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * High-level controller for a mocked {@code adb} binary deployed via USMF.
 *
 * <p>Provides multi-device state management, runtime topology alterations (e.g., disconnecting,
 * reconnecting, or toggling online/offline device states), and property overrides ({@code setprop}
 * / {@code getprop}).
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * MockAdbController controller =
 *     MockAdbController.builder(usmfEnvironment)
 *         .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
 *         .addDevice(MockAndroidDevice.defaultDevice("emulator-5556"))
 *         .buildAndDeploy();
 *
 * flags.set("adb", controller.getAdbPath());
 *
 * // Simulate transient hardware disconnection:
 * controller.disconnectDevice("emulator-5554");
 * }</pre>
 */
public final class MockAdbController {

  private static final String DEFAULT_RULES_RESOURCE_NAME = "mock_adb_rules.star";
  private static final String BINARY_NAME = "adb";
  private static final String DEFAULT_DEVICE_SERIAL = "emulator-5554";

  private final UsmfBinary usmfBinary;

  /**
   * Creates a {@link Builder} to configure a {@link MockAdbController} with a custom parent
   * directory.
   *
   * @param sandboxParentDir the parent directory where the mock sandbox will be created
   * @param sandboxName the unique name for the sandbox folder
   */
  public static Builder builder(Path sandboxParentDir, String sandboxName) {
    return new Builder(UsmfBinary.builder(BINARY_NAME, sandboxParentDir, sandboxName));
  }

  /**
   * Creates a {@link Builder} to configure a {@link MockAdbController} managed by {@link
   * UsmfEnvironment}.
   *
   * @param environment the test {@link UsmfEnvironment} rule
   */
  public static Builder builder(UsmfEnvironment environment) {
    return new Builder(
        checkNotNull(environment, "environment cannot be null").createBinary(BINARY_NAME));
  }

  /**
   * Creates a {@link Builder} wrapping a configured {@link UsmfBinary.Builder}.
   *
   * @param binaryBuilder the underlying {@link UsmfBinary.Builder}
   */
  public static Builder builder(UsmfBinary.Builder binaryBuilder) {
    return new Builder(binaryBuilder);
  }

  private MockAdbController(UsmfBinary usmfBinary) {
    this.usmfBinary = checkNotNull(usmfBinary);
  }

  /** Returns the absolute path of the generated mock {@code adb} executable. */
  public String getAdbPath() {
    return usmfBinary.getPath();
  }

  /** Returns the underlying {@link UsmfBinary} instance. */
  public UsmfBinary getUsmfBinary() {
    return usmfBinary;
  }

  /**
   * Reads all command execution logs from this mock sandbox, sorted chronologically.
   *
   * @throws IOException if fails to read logs from the file system
   */
  public ImmutableList<CommandInvocation> readCommandInvocations() throws IOException {
    return usmfBinary.readCommandInvocations();
  }

  /**
   * Returns an immutable map of all configured devices in the sandbox, keyed by serial number.
   *
   * @throws IOException if fails to read state from the file system
   */
  public ImmutableMap<String, MockAndroidDevice> getAllDevices() throws IOException {
    JsonObject state = readState();
    if (!state.has("devices") || !state.get("devices").isJsonObject()) {
      return ImmutableMap.of();
    }
    JsonObject devicesJson = state.getAsJsonObject("devices");
    ImmutableMap.Builder<String, MockAndroidDevice> builder = ImmutableMap.builder();
    for (Map.Entry<String, JsonElement> entry : devicesJson.entrySet()) {
      String serial = entry.getKey();
      if (entry.getValue().isJsonObject()) {
        builder.put(
            serial, MockAndroidDevice.fromJsonObject(serial, entry.getValue().getAsJsonObject()));
      }
    }
    return builder.buildOrThrow();
  }

  /**
   * Reads the current state of a specific device from the mock sandbox.
   *
   * @param serial the serial number of the device to query
   * @return the {@link MockAndroidDevice}, or {@link Optional#empty()} if not present
   * @throws IOException if fails to read state from the file system
   */
  public Optional<MockAndroidDevice> getDevice(String serial) throws IOException {
    checkNotNull(serial, "serial cannot be null");
    JsonObject state = readState();
    if (state.has("devices") && state.get("devices").isJsonObject()) {
      JsonObject devicesJson = state.getAsJsonObject("devices");
      if (devicesJson.has(serial) && devicesJson.get(serial).isJsonObject()) {
        return Optional.of(
            MockAndroidDevice.fromJsonObject(serial, devicesJson.getAsJsonObject(serial)));
      }
    }
    return Optional.empty();
  }

  /**
   * Adds or replaces a device in the active mock sandbox state.
   *
   * @param device the mock device configuration to register
   * @throws IOException if fails to update state on the file system
   */
  public void addDevice(MockAndroidDevice device) throws IOException {
    checkNotNull(device, "device cannot be null");
    JsonObject state = readState();
    JsonObject devicesJson = getOrCreateDevicesObject(state);
    devicesJson.add(device.getSerial(), device.toJsonObject());
    usmfBinary.writeState(state);
  }

  /**
   * Completely removes a device from the active mock sandbox state.
   *
   * @param serial the serial number of the device to remove
   * @throws IOException if fails to update state on the file system
   */
  public void removeDevice(String serial) throws IOException {
    checkNotNull(serial, "serial cannot be null");
    JsonObject state = readState();
    if (state.has("devices") && state.get("devices").isJsonObject()) {
      state.getAsJsonObject("devices").remove(serial);
      usmfBinary.writeState(state);
    }
  }

  /**
   * Updates the connection/authorization status of a device in the mock sandbox.
   *
   * @param serial the device serial number
   * @param status the target {@link DeviceStatus}
   * @throws IOException if fails to update state on the file system
   */
  public void setDeviceStatus(String serial, DeviceStatus status) throws IOException {
    checkNotNull(serial, "serial cannot be null");
    checkNotNull(status, "status cannot be null");
    JsonObject state = readState();
    JsonObject devicesJson = getOrCreateDevicesObject(state);
    JsonObject devJson;
    if (devicesJson.has(serial) && devicesJson.get(serial).isJsonObject()) {
      devJson = devicesJson.getAsJsonObject(serial);
    } else {
      devJson = MockAndroidDevice.defaultDevice(serial).toJsonObject();
      devicesJson.add(serial, devJson);
    }
    devJson.addProperty("status", status.getStatusString());
    usmfBinary.writeState(state);
  }

  /**
   * Sets whether a device is online ({@link DeviceStatus#DEVICE}) or offline ({@link
   * DeviceStatus#OFFLINE}).
   *
   * @param serial the device serial number
   * @param online {@code true} for {@link DeviceStatus#DEVICE}, {@code false} for {@link
   *     DeviceStatus#OFFLINE}
   * @throws IOException if fails to update state on the file system
   */
  public void setDeviceOnline(String serial, boolean online) throws IOException {
    setDeviceStatus(serial, online ? DeviceStatus.DEVICE : DeviceStatus.OFFLINE);
  }

  /**
   * Simulates disconnecting a device by setting its status to {@link DeviceStatus#DISCONNECTED}.
   *
   * <p>Disconnected devices are omitted from {@code adb devices} outputs and cause {@code adb -s}
   * dispatches to fail with {@code device not found}.
   *
   * @param serial the device serial number
   * @throws IOException if fails to update state on the file system
   */
  public void disconnectDevice(String serial) throws IOException {
    setDeviceStatus(serial, DeviceStatus.DISCONNECTED);
  }

  /**
   * Reconnects a disconnected or offline device, restoring its status to {@link
   * DeviceStatus#DEVICE}.
   *
   * @param serial the device serial number
   * @throws IOException if fails to update state on the file system
   */
  public void reconnectDevice(String serial) throws IOException {
    setDeviceStatus(serial, DeviceStatus.DEVICE);
  }

  /**
   * Sets or overrides a system property (accessible via {@code adb shell getprop}) for a device.
   *
   * @param serial the device serial number
   * @param key the property key (e.g., "ro.product.model")
   * @param value the property value
   * @throws IOException if fails to update state on the file system
   */
  public void setDeviceProperty(String serial, String key, String value) throws IOException {
    checkNotNull(serial, "serial cannot be null");
    checkNotNull(key, "property key cannot be null");
    checkNotNull(value, "property value cannot be null");
    JsonObject state = readState();
    JsonObject devicesJson = getOrCreateDevicesObject(state);
    JsonObject devJson;
    if (devicesJson.has(serial) && devicesJson.get(serial).isJsonObject()) {
      devJson = devicesJson.getAsJsonObject(serial);
    } else {
      devJson = MockAndroidDevice.defaultDevice(serial).toJsonObject();
      devicesJson.add(serial, devJson);
    }
    JsonObject propsJson;
    if (devJson.has("props") && devJson.get("props").isJsonObject()) {
      propsJson = devJson.getAsJsonObject("props");
    } else {
      propsJson = new JsonObject();
      devJson.add("props", propsJson);
    }
    propsJson.addProperty(key, value);
    usmfBinary.writeState(state);
  }

  /**
   * Reads the active state database inside the mock sandbox workspace.
   *
   * @throws IOException if fails to read state from the file system
   */
  private JsonObject readState() throws IOException {
    return usmfBinary.readState();
  }

  private static JsonObject getOrCreateDevicesObject(JsonObject state) {
    if (state.has("devices") && state.get("devices").isJsonObject()) {
      return state.getAsJsonObject("devices");
    }
    JsonObject devicesJson = new JsonObject();
    state.add("devices", devicesJson);
    return devicesJson;
  }

  static String loadDefaultRules() {
    try (InputStream is =
        MockAdbController.class.getResourceAsStream(DEFAULT_RULES_RESOURCE_NAME)) {
      checkNotNull(is, "Resource %s not found in classpath", DEFAULT_RULES_RESOURCE_NAME);
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load default mock adb rules", e);
    }
  }

  /** Builder for constructing and deploying {@link MockAdbController} instances. */
  public static final class Builder {
    private final UsmfBinary.Builder binaryBuilder;
    private final Map<String, MockAndroidDevice> initialDevices = new LinkedHashMap<>();
    private boolean hasExplicitDevices = false;
    private String customRulesContent = null;

    private Builder(UsmfBinary.Builder binaryBuilder) {
      this.binaryBuilder = checkNotNull(binaryBuilder, "binaryBuilder cannot be null");
    }

    /** Registers an initial {@link MockAndroidDevice} to be present in the mock ADB environment. */
    @CanIgnoreReturnValue
    public Builder addDevice(MockAndroidDevice device) {
      checkNotNull(device, "device cannot be null");
      this.hasExplicitDevices = true;
      this.initialDevices.put(device.getSerial(), device);
      return this;
    }

    /** Registers multiple initial {@link MockAndroidDevice}s. */
    @CanIgnoreReturnValue
    public Builder addDevices(MockAndroidDevice... devices) {
      checkNotNull(devices, "devices cannot be null");
      for (MockAndroidDevice device : devices) {
        addDevice(device);
      }
      return this;
    }

    /** Registers multiple initial {@link MockAndroidDevice}s. */
    @CanIgnoreReturnValue
    public Builder addDevices(Iterable<MockAndroidDevice> devices) {
      checkNotNull(devices, "devices cannot be null");
      for (MockAndroidDevice device : devices) {
        addDevice(device);
      }
      return this;
    }

    /** Clears all initial devices, resulting in an environment with zero connected devices. */
    @CanIgnoreReturnValue
    public Builder clearDevices() {
      this.hasExplicitDevices = true;
      this.initialDevices.clear();
      return this;
    }

    /** Overrides the Starlark rules script with custom content. */
    @CanIgnoreReturnValue
    public Builder setRules(String rulesContent) {
      this.customRulesContent = checkNotNull(rulesContent, "rulesContent cannot be null");
      return this;
    }

    /** Overrides the Starlark rules script by reading from a file path. */
    @CanIgnoreReturnValue
    public Builder setRules(Path rulesFile) throws IOException {
      checkNotNull(rulesFile, "rulesFile cannot be null");
      this.customRulesContent = Files.readString(rulesFile);
      return this;
    }

    /** Overrides the parent directory of the generated mock binary executable wrapper. */
    @CanIgnoreReturnValue
    public Builder overrideBinaryFileParentDir(Path binaryFileParentDir) {
      binaryBuilder.overrideBinaryFileParentDir(binaryFileParentDir);
      return this;
    }

    /**
     * Builds and deploys the mock ADB environment, initializing the device state database.
     *
     * @throws IOException if any I/O errors occur during deployment
     */
    public MockAdbController buildAndDeploy() throws IOException {
      String rulesToUse = customRulesContent != null ? customRulesContent : loadDefaultRules();
      binaryBuilder.setRules(rulesToUse);

      UsmfBinary binary = binaryBuilder.buildAndDeploy();

      // Populate initial device state.
      JsonObject state = new JsonObject();
      JsonObject devicesJson = new JsonObject();

      Map<String, MockAndroidDevice> devicesToRegister = new LinkedHashMap<>(initialDevices);
      if (!hasExplicitDevices) {
        devicesToRegister.put(
            DEFAULT_DEVICE_SERIAL, MockAndroidDevice.pixel7(DEFAULT_DEVICE_SERIAL));
      }

      for (MockAndroidDevice dev : devicesToRegister.values()) {
        devicesJson.add(dev.getSerial(), dev.toJsonObject());
      }
      state.add("devices", devicesJson);
      binary.writeState(state);

      return new MockAdbController(binary);
    }
  }
}
