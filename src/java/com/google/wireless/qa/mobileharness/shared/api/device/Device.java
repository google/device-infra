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

package com.google.wireless.qa.mobileharness.shared.api.device;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.devtools.mobileharness.api.model.lab.DeviceInfo;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLogType;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.infra.container.sandbox.device.DeviceSandboxController;
import com.google.devtools.mobileharness.infra.container.sandbox.device.UnsupportedDeviceSandboxController;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import com.google.wireless.qa.mobileharness.shared.proto.CommunicationList;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Device specific operations.
 *
 * <p>All sub-class should have a constructor with the device ID as it only parameter.
 *
 * <p>Device instances (sub-class of {@link BaseDevice}) should <b>ONLY</b> be created by MH infra
 * (e.g., MH device manager). It is because {@link BaseDevice} is actually only a stub which talks
 * to a machine-unique singleton device info manager in MH lab. If you try to instantiate {@link
 * Device} yourselves, the behavior is unknown.
 */
public interface Device {

  /** Returns the device information. */
  DeviceInfo info();

  /**
   * Returns the device ID.
   *
   * <p>Use <tt>{@link #info()}.deviceId()</tt> instead.
   */
  String getDeviceId();

  /** Returns the device control ID. */
  String getDeviceControlId();

  /** Returns the device UUID. */
  String getDeviceUuid();

  /** Gets a copy of the supported device types. */
  Set<String> getDeviceTypes();

  /** Gets a copy of the supported drivers, which are sub-classes of {@code Driver}. */
  Set<String> getDriverTypes();

  /** Gets a copy of the supported decorators, which are sub-classes of {@code Decorator}. */
  Set<String> getDecoratorTypes();

  /** Gets the list of the owners for this device. */
  List<String> getOwners();

  /** Gets the list of the executors for this device. */
  List<String> getExecutors();

  /**
   * Adds additional dimension to the device.
   *
   * <p>Use <tt>{@link #info()}.dimensions().supported().add(name, value)</tt> instead.
   *
   * @param name name of the dimension
   * @param value dimension value
   */
  void addDimension(String name, String value);

  /**
   * Adds additional dimension to the device the given {@link Dimension.Name}.
   *
   * <p>Use <tt>{@link #info()}.dimensions().supported().add(name, value)</tt> instead.
   */
  void addDimension(Dimension.Name name, String value);

  /**
   * Adds additional required dimension to the device.
   *
   * <p>Use <tt>{@link #info()}.dimensions().required().add(name, value)</tt> instead.
   *
   * @param name name of the dimension
   * @param value dimension value
   */
  void addRequiredDimension(String name, String value);

  /**
   * Adds additional required dimension to the device the given {@link Dimension.Name}.
   *
   * <p>Use <tt>{@link #info()}.dimensions().required().add(name, value)</tt> instead.
   */
  void addRequiredDimension(Dimension.Name name, String value);

  /**
   * Gets the dimension values of the given dimension name.
   *
   * <p>Use <tt>{@link #info()}.dimensions().supported().get(name)</tt> instead.
   *
   * @return a list of dimension value, or an empty list if dimension not found
   */
  List<String> getDimension(String name);

  /**
   * Gets the dimension values of the given {@link Dimension.Name}.
   *
   * <p>Use <tt>{@link #info()}.dimensions().supported().get(name)</tt> instead.
   *
   * @return a list of dimension value, or an empty list if dimension not found
   */
  List<String> getDimension(Dimension.Name name);

  /**
   * Gets the required dimension values of the given dimension name.
   *
   * <p>Use <tt>{@link #info()}.dimensions().required().get(name)</tt> instead.
   *
   * @return a list of dimension value, or an empty list if required dimension not found
   */
  List<String> getRequiredDimension(String name);

  /**
   * Gets the required dimension values of the given {@link Dimension.Name}.
   *
   * <p>Use <tt>{@link #info()}.dimensions().required().get(name)</tt> instead.
   *
   * @return a list of dimension value, or an empty list if dimension not found
   */
  List<String> getRequiredDimension(Dimension.Name name);

  /**
   * Removes all existing dimension values with the given name, adds new dimensions values with the
   * name.
   *
   * <p>Notes for customized dimensions from lab/device config, you can not remove/change the
   * dimension values defined in the config. But you can add more new values to them.
   *
   * <p>Use <tt>{@link #info()}.dimensions().supported().replace(name, Arrays.asList(values))</tt>
   * instead.
   *
   * @param name dimension name
   * @param values new dimension values
   * @return whether the dimensions are changed
   */
  boolean updateDimension(String name, String... values);

  /**
   * Removes all existing dimension values with the given Dimension.Name, adds new dimensions values
   * with {@link Dimension.Name}.
   *
   * <p>Notes for customized dimensions from lab/device config, you can not remove/change the
   * dimension values defined in the config. But you can add more new values to them.
   *
   * <p>Use <tt>{@link #info()}.dimensions().supported().replace(name, Arrays.asList(values))</tt>
   * instead.
   *
   * @param name dimension name
   * @param values new dimension values
   * @return whether the dimensions are changed
   */
  boolean updateDimension(Dimension.Name name, String... values);

  /**
   * Removes all existing required dimension values with the given name, adds new required
   * dimensions values with the name.
   *
   * <p>Notes for customized required dimensions from lab/device config, you can not remove/change
   * the required dimension values defined in the config. But you can add more new values to them.
   *
   * <p>Use <tt>{@link #info()}.dimensions().required().replace(name, Arrays.asList(values))</tt>
   * instead.
   *
   * @param name dimension name
   * @param values new dimension values
   * @return whether the dimensions are changed
   */
  boolean updateRequiredDimension(String name, String... values);

  /**
   * Removes all existing required dimension values with the given Dimension.Name, adds new required
   * dimensions values with {@link Dimension.Name}.
   *
   * <p>Notes for customized required dimensions from lab/device config, you can not remove/change
   * the required dimension values defined in the config. But you can add more new values to them.
   *
   * <p>Use <tt>{@link #info()}.dimensions().required().replace(name, Arrays.asList(values))</tt>
   * instead.
   *
   * @param name dimension name
   * @param values new dimension values
   * @return whether the dimensions are changed
   */
  boolean updateRequiredDimension(Dimension.Name name, String... values);

  /**
   * Gets device (supported, not required) dimensions.
   *
   * <p>Use <tt>{@link #info()}.dimensions().supported().getAll()</tt> instead.
   *
   * @return device specified dimensions
   */
  Set<StrPair> getDimensions();

  /**
   * Gets device required dimensions.
   *
   * <p>Use <tt>{@link #info()}.dimensions().required().getAll()</tt> instead.
   *
   * @return device specified required dimensions
   */
  Set<StrPair> getRequiredDimensions();

  /** Checks whether device has dimension (name, value). */
  default boolean hasDimension(Dimension.Name name, String value) {
    return getDimension(name).contains(value);
  }

  /**
   * Associates the specified property value with the specified property key. If the property map
   * previously contained a mapping for the key, the old value is replaced by the specified value.
   *
   * <p>Use <tt>{@link #info()}.properties().put(key, value)</tt> or <tt>{@link
   * #info()}.properties().remove(key)</tt> instead.
   *
   * @param key property key with which the specified property value is to be associated
   * @param value property value to be associated with the specified property key
   * @return the previous property value associated with <tt>key</tt>, or <tt>null</tt> if there was
   *     no mapping for <tt>key</tt>. (A <tt>null</tt> return can also indicate that the map
   *     previously associated <tt>null</tt> with <tt>key</tt>, if the implementation supports
   *     <tt>null</tt> values.)
   */
  @Nullable
  default String setProperty(String key, @Nullable String value) {
    if (value == null) {
      return info().properties().remove(key).orElse(null);
    } else {
      return info().properties().put(key, value).orElse(null);
    }
  }

  /**
   * Returns the property value to which the specified key is mapped, or {@code null} if the
   * property map does not contain the key.
   *
   * <p>Use <tt>{@link #info()}.properties().get(key)</tt> instead.
   *
   * @param key the key whose associated property value is to be returned
   * @return the property value to which the specified property key is mapped, or {@code null} if
   *     the property map contains no mapping for the key
   */
  @Nullable
  default String getProperty(String key) {
    return info().properties().get(key).orElse(null);
  }

  /**
   * Use <tt>{@link #info()}.properties().getInteger(key)</tt> instead.
   *
   * @return the integer property value, or empty if the value is not an integer or not present
   */
  default Optional<Integer> getIntegerProperty(String key) {
    try {
      return Optional.of(Integer.parseInt(getProperty(key)));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  /**
   * Use <tt>{@link #info()}.properties().getBoolean(key)</tt> instead.
   *
   * @return the boolean property value, or false if the value is not a boolean or not present
   */
  default boolean getBooleanProperty(String key) {
    return Boolean.parseBoolean(getProperty(key));
  }

  /** Adds the communications to device property and the communication types to dimension. */
  default void setCommunicationDimensionAndProperty(CommunicationList communicationList) {}

  /**
   * Returns the communication property of the device, indexed by key {@link PROP_COMMUNICATION}.
   *
   * @return the communication property
   */
  default Optional<CommunicationList> getCommunicationProperty() {
    return Optional.empty();
  }

  /**
   * Use <tt>{@link #info()}.properties().has(key)</tt> instead.
   *
   * @return whether the device has the given property
   */
  default boolean hasProperty(String key) {
    return getProperty(key) != null;
  }

  /**
   * Returns a immutable copy of the device property table.
   *
   * <p>Use <tt>{@link #info()}.properties().getAll()</tt> instead.
   */
  default Map<String, String> getProperties() {
    return info().properties().getAll();
  }

  /**
   * Clears all properties.
   *
   * <p>Use <tt>{@link #info()}.properties().clear()</tt> instead.
   */
  default void clearProperties() {
    info().properties().clear();
  }

  /** Gets the timeout value for {@link #setUp()}. */
  Duration getSetupTimeout() throws MobileHarnessException, InterruptedException;

  /**
   * Initializes the device. Prepares the supported device types, dimensions, drivers/decorators. MH
   * will assign task to this device only after it successfully pass this method.
   *
   * @throws MobileHarnessException If fails to initialize the device, you can throw out this
   *     exception to prevent this device from being assigned to any task. The device instance will
   *     go through the {@link #tearDown()} and get destroyed. If the device remains detectable, MH
   *     Device Manager framework will create new Device object for the device and try this {@link
   *     #setUp()} again, until it succeeds or device disconnected.
   */
  void setUp() throws MobileHarnessException, InterruptedException;

  /**
   * Checks and update the device types, dimensions, and supported drivers/decorators.
   *
   * <p>MH Device Manager framework will invoked this method periodically when the device is IDLE,
   * to make sure the device info in scheduler and frontend are up to date.
   *
   * <p>You can also set/unset the {@link #isPrepping()} state to make the device
   * unallocatable/allocatable. For example, when the device checking logic finds the device is
   * battery low, you can set the {@link #isPrepping()} to true to give some time for the device to
   * recharge. And when the regular device checking finds the battery level is back to normal, you
   * can set the {@link #isPrepping()} to false so it can be allocated by users.
   *
   * @return whether the device is changed, to notify the Device Manager to sync the new device info
   * @throws MobileHarnessException if fails to check the device, will cause the device to {@link
   *     #tearDown()}, and {@link #reboot()} if {@link #canReboot()} is {@code true}
   */
  boolean checkDevice() throws MobileHarnessException, InterruptedException;

  /** Returns whether the device is is not yet ready for use; e.g. needs to recharge its battery. */
  boolean isPrepping();

  /**
   * Preparation before loading driver and run test.
   *
   * @param testInfo the test going to run on this device
   * @throws MobileHarnessException if fails to do the preparation
   */
  void preRunTest(TestInfo testInfo) throws MobileHarnessException, InterruptedException;

  /**
   * Operations after a test and before resetting/reloading the driver. If any {@link
   * MobileHarnessException} is thrown out, will cause the device to reboot.
   *
   * @param testInfo the test just finished with this device
   * @throws MobileHarnessException if fails to do the operation
   */
  PostTestDeviceOp postRunTest(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException;

  /**
   * Cleans up when the device becomes undetectable/disconnected. Should have no effect if this
   * method is invoked for the second time.
   *
   * @throws MobileHarnessException if fails to clean up
   * @throws InterruptedException if the current thread or its sub-thread is {@linkplain
   *     Thread#interrupt() interrupted} by another thread
   */
  void tearDown() throws MobileHarnessException, InterruptedException;

  /**
   * Returns whether the device can be rebooted. If false, it is useless to implement {@link
   * #reboot()} because it will never be invoked by the framework.
   */
  boolean canReboot() throws InterruptedException;

  /**
   * Restarts the device. After rebooting, the device must be undetectable for a while to make the
   * framework recycle the old device runner thread. "A while" is >= (the latency of
   * Detector.detectDevices() + 2s interval) * 2. To enable this function, you MUST override {@link
   * #canReboot()} and make it return true. Otherwise, this method will never be invoked by the
   * framework.
   *
   * <p>MH will call this method in 2 ways:
   *
   * <ul>
   *   <li>If a test is time out, MH will always reboot the device to avoid any leftover process to
   *       run on the device.
   * </ul>
   *
   * @throws MobileHarnessException if fails to restart the device
   * @throws InterruptedException if the current thread or its sub-thread is {@linkplain
   *     Thread#interrupt() interrupted} by another thread
   */
  void reboot() throws MobileHarnessException, InterruptedException;

  /**
   * Takes screen shot on device and uploads it to host machine.
   *
   * @return device screenshot full path on host machine, which looks like:
   *     <PublicGenDir>/screen_shot_files/<DeviceID>/<Timestamp>.png
   * @throws MobileHarnessException if fails to get screen shot
   * @throws InterruptedException if the current thread or its sub-thread is {@linkplain
   *     Thread#interrupt() interrupted} by another thread
   */
  String takeScreenshot() throws MobileHarnessException, InterruptedException;

  /**
   * Gets the device log based on the log type.
   *
   * @return the generated log full path on the host machine, which looks like:
   *     <PublicGenDir>/<DeviceId>/<RandomId>.<Type>
   * @throws MobileHarnessException if fails to get the log
   * @throws InterruptedException if the current thread or its sub-thread is {@linkplain
   *     Thread#interrupt() interrupted} by another thread
   */
  String getDeviceLog(DeviceLogType deviceLogType)
      throws MobileHarnessException, InterruptedException;

  /** Gets general file directory. */
  String getGenFileDir() throws MobileHarnessException;

  default DeviceFeature toFeature() {
    return DeviceFeature.newBuilder()
        .addAllOwner(getOwners())
        .addAllType(getDeviceTypes())
        .addAllDriver(getDriverTypes())
        .addAllDecorator(getDecoratorTypes())
        .setCompositeDimension(
            DeviceCompositeDimension.newBuilder()
                .addAllSupportedDimension(
                    getDimensions().stream()
                        .map(
                            dimension ->
                                DeviceDimension.newBuilder()
                                    .setName(dimension.getName())
                                    .setValue(dimension.getValue())
                                    .build())
                        .collect(toImmutableList()))
                .addAllRequiredDimension(
                    getRequiredDimensions().stream()
                        .map(
                            dimension ->
                                DeviceDimension.newBuilder()
                                    .setName(dimension.getName())
                                    .setValue(dimension.getValue())
                                    .build())
                        .collect(toImmutableList()))
                .build())
        .build();
  }

  /** Gets the controller for passing through this device to a MH sandbox. */
  default DeviceSandboxController getSandboxController() {
    return new UnsupportedDeviceSandboxController(
        info().deviceId().controlId(), getClass().getSimpleName());
  }

  /** Sets up this device for MH sandbox. */
  default void setUpForSandbox(List<String> deviceTypes) {
    // By default, it does nothing.
  }

  /** Returns whether this device supports MH container. */
  default boolean supportsContainer() {
    // By default, device does not supports container-mode tests.
    return false;
  }
}
