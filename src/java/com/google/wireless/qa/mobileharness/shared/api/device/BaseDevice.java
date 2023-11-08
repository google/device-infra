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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.ext.devicemanagement.device.BaseDeviceHelper;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.lab.DeviceInfo;
import com.google.devtools.mobileharness.api.model.lab.LiteDeviceInfoFactory;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLogType;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.infra.container.annotation.ProcessIncompatible;
import com.google.devtools.mobileharness.infra.controller.device.DeviceInfoManager;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.ClassUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ValidatorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.Decorator;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.api.validator.Validator;
import com.google.wireless.qa.mobileharness.shared.api.validator.ValidatorFactory;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.CommunicationTypeValue;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import com.google.wireless.qa.mobileharness.shared.proto.Communication;
import com.google.wireless.qa.mobileharness.shared.proto.CommunicationList;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Base class for all kinds of devices.
 *
 * <p>Device instances (sub-class of {@link BaseDevice}) should <b>ONLY</b> be created by MH infra
 * (e.g., MH device manager). It is because {@link BaseDevice} is actually only a stub which talks
 * to a machine-unique singleton device info manager in MH lab. If you try to instantiate {@link
 * Device} yourselves, the behavior is unknown.
 */
@ProcessIncompatible({
  "genFileDirRoot",
})
public abstract class BaseDevice implements Device {

  /** Device property name of the device communication details. */
  public static final String PROP_COMMUNICATION = "communication";

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DeviceInfo deviceInfo;

  /** Generated file directory root, which looks like: "/var/www/mh_gen_file_dir/". */
  private static String genFileDirRoot;

  /** File utility class to support local file operations. */
  private final LocalFileUtil fileUtil = new LocalFileUtil();

  /** Factory for creating {@code Validator} instances. */
  private final ValidatorFactory validatorFactory;

  /** Optional API configurations. */
  @Nullable private final ApiConfig apiConfig;

  public BaseDevice(String deviceId) {
    this(deviceId, /* managedDeviceInfo= */ true);
  }

  /**
   * <b>WARNING</b>: In most cases you should <b>NOT</b> use this method. Please use {@link
   * #BaseDevice(String)} instead.
   *
   * @param managedDeviceInfo if true, this device instance will read/write device information of a
   *     device which is managed by MH DeviceManager and whose id is the given device id. If false,
   *     this device instance will create and use a new private device information data structure.
   */
  public BaseDevice(String deviceId, boolean managedDeviceInfo) {
    this(deviceId, null, new ValidatorFactory(), managedDeviceInfo);
  }

  public BaseDevice(
      String deviceId, @Nullable ApiConfig apiConfig, ValidatorFactory validatorFactory) {
    this(deviceId, apiConfig, validatorFactory, /* managedDeviceInfo= */ true);
  }

  /**
   * <b>WARNING</b>: In most cases you should <b>NOT</b> use this method. Please use {@link
   * #BaseDevice(String, ApiConfig, ValidatorFactory)} instead.
   *
   * @param managedDeviceInfo if true, this device instance will read/write device information of a
   *     device which is managed by MH DeviceManager and whose id is the given device id. If false,
   *     this device instance will create and use a new private device information data structure.
   */
  public BaseDevice(
      String deviceId,
      @Nullable ApiConfig apiConfig,
      ValidatorFactory validatorFactory,
      boolean managedDeviceInfo) {
    this(
        apiConfig,
        validatorFactory,
        managedDeviceInfo
            ? DeviceInfoManager.getInstance().getOrCreate(deviceId, apiConfig)
            : LiteDeviceInfoFactory.create(deviceId));
  }

  @VisibleForTesting
  BaseDevice(
      @Nullable ApiConfig apiConfig, ValidatorFactory validatorFactory, DeviceInfo deviceInfo) {
    this.apiConfig = apiConfig;
    this.validatorFactory = validatorFactory;
    this.deviceInfo = deviceInfo;
  }

  @Override
  public DeviceInfo info() {
    return deviceInfo;
  }

  @Override
  public String getDeviceId() {
    return getDeviceControlId();
  }

  @Override
  public String getDeviceControlId() {
    return info().deviceId().controlId();
  }

  @Override
  public String getDeviceUuid() {
    return info().deviceId().uuid();
  }

  @Override
  public ImmutableSet<String> getDeviceTypes() {
    return info().deviceTypes().getAll();
  }

  @Override
  public ImmutableSet<String> getDriverTypes() {
    return info().supportedDrivers().getAll();
  }

  @Override
  public ImmutableSet<String> getDecoratorTypes() {
    return info().supportedDecorators().getAll();
  }

  @Override
  public void addDimension(String name, String value) {
    info().dimensions().supported().add(name, value);
  }

  @Override
  public void addDimension(Dimension.Name name, String value) {
    info().dimensions().supported().add(name, value);
  }

  @Override
  public void addRequiredDimension(String name, String value) {
    info().dimensions().required().add(name, value);
  }

  @Override
  public void addRequiredDimension(Dimension.Name name, String value) {
    info().dimensions().required().add(name, value);
  }

  @Override
  public List<String> getDimension(String name) {
    return info().dimensions().supported().get(name);
  }

  @Override
  public List<String> getDimension(Dimension.Name name) {
    return info().dimensions().supported().get(name);
  }

  @Override
  public List<String> getRequiredDimension(String name) {
    return info().dimensions().required().get(name);
  }

  @Override
  public List<String> getRequiredDimension(Dimension.Name name) {
    return info().dimensions().required().get(name);
  }

  @Override
  public ImmutableSet<StrPair> getDimensions() {
    return info().dimensions().supported().getAll().entries().stream()
        .map(
            entry ->
                StrPair.newBuilder().setName(entry.getKey()).setValue(entry.getValue()).build())
        .collect(toImmutableSet());
  }

  @Override
  public ImmutableSet<StrPair> getRequiredDimensions() {
    return info().dimensions().required().getAll().entries().stream()
        .map(
            entry ->
                StrPair.newBuilder().setName(entry.getKey()).setValue(entry.getValue()).build())
        .collect(toImmutableSet());
  }

  @CanIgnoreReturnValue
  @Override
  public boolean updateDimension(String name, String... values) {
    return info().dimensions().supported().replace(name, Arrays.asList(values));
  }

  @CanIgnoreReturnValue
  @Override
  public boolean updateDimension(Dimension.Name name, String... values) {
    return info().dimensions().supported().replace(name, Arrays.asList(values));
  }

  @CanIgnoreReturnValue
  @Override
  public boolean updateRequiredDimension(String name, String... values) {
    return info().dimensions().required().replace(name, Arrays.asList(values));
  }

  @Override
  public boolean updateRequiredDimension(Dimension.Name name, String... values) {
    return info().dimensions().required().replace(name, Arrays.asList(values));
  }

  /**
   * Removes all existing dimension values with the given name.
   *
   * <p>Notes for customized dimensions from lab/device config, you can not remove the dimension
   * values defined in the config.
   *
   * <p>Use <tt>{@link #info()}.dimensions().supported().remove(name)</tt> instead.
   *
   * @param name dimension name
   * @return whether the dimensions are changed
   */
  @CanIgnoreReturnValue
  public boolean removeDimension(String name) {
    return info().dimensions().supported().remove(name);
  }

  /**
   * Removes all existing dimension values with the given Dimension.Name.
   *
   * <p>Notes for customized dimensions from lab/device config, you can not remove the dimension
   * values defined in the config.
   *
   * <p>Use <tt>{@link #info()}.dimensions().supported().remove(name)</tt> instead.
   *
   * @param name dimension name
   * @return whether the dimensions are changed
   */
  @CanIgnoreReturnValue
  public boolean removeDimension(Dimension.Name name) {
    return info().dimensions().supported().remove(name);
  }

  @Override
  public List<String> getOwners() {
    return apiConfig == null
        ? ImmutableList.of()
        : apiConfig.getOwners(info().deviceId().controlId());
  }

  @Override
  public List<String> getExecutors() {
    return apiConfig == null
        ? ImmutableList.of()
        : apiConfig.getExecutors(info().deviceId().controlId());
  }

  @Override
  public Duration getSetupTimeout() throws MobileHarnessException, InterruptedException {
    return Duration.ofMinutes(5L);
  }

  @Override
  public void setUp() throws MobileHarnessException, InterruptedException {
    ImmutableListMultimap<Dimension.Name, String> extraDimensions = null;
    if (supportsContainer()) {
      extraDimensions =
          ImmutableListMultimap.of(Dimension.Name.DEVICE_SUPPORTS_CONTAINER, Dimension.Value.TRUE);
    }

    BaseDeviceHelper.setUp(this, BaseDevice.class, extraDimensions);
  }

  @CanIgnoreReturnValue
  @Override
  public boolean checkDevice() throws MobileHarnessException, InterruptedException {
    return false;
  }

  @Override
  public boolean isPrepping() {
    return false;
  }

  @Override
  public void preRunTest(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    // Does nothing.
  }

  @CanIgnoreReturnValue
  @Override
  public PostTestDeviceOp postRunTest(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    // Removes the device cache after tests finish, otherwise device status may be wrong. b/32101092
    DeviceCache.getInstance().invalidateCache(info().deviceId().controlId());
    return PostTestDeviceOp.NONE;
  }

  @Override
  public boolean canReboot() throws InterruptedException {
    return false;
  }

  @Override
  public void reboot() throws MobileHarnessException, InterruptedException {
    if (!canReboot()) {
      throw new MobileHarnessException(ErrorCode.DEVICE_NOT_SUPPORTED, "Can not be rebooted");
    }
  }

  @Override
  public void tearDown() throws MobileHarnessException, InterruptedException {
    // Do nothing.
  }

  @Override
  public String takeScreenshot() throws MobileHarnessException, InterruptedException {
    throw new MobileHarnessException(ErrorCode.NOT_IMPLEMENTED, "Unsupported screenshot");
  }

  @Override
  public String getDeviceLog(DeviceLogType deviceLogType)
      throws MobileHarnessException, InterruptedException {
    throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
        InfraErrorId.LAB_GET_DEVICE_LOG_METHOD_UNSUPPORTED, "Unsupported getDeviceLog");
  }

  /** Gets the optional api configurations. */
  public ApiConfig getApiConfig() {
    return apiConfig;
  }

  @Override
  public void setCommunicationDimensionAndProperty(CommunicationList communicationList) {
    removeDimension(Dimension.Name.COMMUNICATION_TYPE.name());
    for (Communication communication : communicationList.getCommunicationList()) {
      if (communication.hasAdb()) {
        addDimension(
            Ascii.toLowerCase(Dimension.Name.COMMUNICATION_TYPE.name()),
            CommunicationTypeValue.ADB.name());
      } else if (communication.hasUsb()) {
        addDimension(
            Ascii.toLowerCase(Dimension.Name.COMMUNICATION_TYPE.name()),
            CommunicationTypeValue.USB.name());
      } else if (communication.hasSsh()) {
        addDimension(
            Ascii.toLowerCase(Dimension.Name.COMMUNICATION_TYPE.name()),
            CommunicationTypeValue.SSH.name());
      } else if (communication.hasAdbOverUsb()) {
        // TODO: OmniLab doesn't support duplicated dimension keys.
        addDimension(
            Ascii.toLowerCase(Dimension.Name.COMMUNICATION_TYPE.name()),
            CommunicationTypeValue.USB.name());
        addDimension(
            Ascii.toLowerCase(Dimension.Name.COMMUNICATION_TYPE.name()),
            CommunicationTypeValue.ADB.name());
      } else if (communication.hasVideo()) {
        addDimension(
            Ascii.toLowerCase(Dimension.Name.COMMUNICATION_TYPE.name()),
            CommunicationTypeValue.VIDEO.name());
      } else {
        logger.atWarning().log("Unknown communication: %s", communication);
        continue;
      }
    }
    setProperty(PROP_COMMUNICATION, communicationList.toString());
  }

  /**
   * Returns the communication property of the device, indexed by key {@link PROP_COMMUNICATION}.
   *
   * @return the communication property
   */
  @Override
  public Optional<CommunicationList> getCommunicationProperty() {
    if (!hasProperty(PROP_COMMUNICATION)) {
      return Optional.empty();
    }
    CommunicationList.Builder communication = CommunicationList.newBuilder();
    String propertyCommunication = getProperty(PROP_COMMUNICATION);
    try {
      TextFormat.merge(propertyCommunication, communication);
      return Optional.of(communication.build());
    } catch (ParseException e) {
      logger.atWarning().withCause(e).log(
          "Failed to parse device communication: %s", propertyCommunication);
      return Optional.empty();
    }
  }

  /** Add supported device type. */
  public void addSupportedDeviceType(String deviceType) {
    info().deviceTypes().add(deviceType);
  }

  @Override
  public String getGenFileDir() throws MobileHarnessException {
    checkNotNull(genFileDirRoot, "genFileDirRoot isn't set.");
    String genFileDir = PathUtil.join(genFileDirRoot, info().deviceId().controlId());
    fileUtil.prepareDir(genFileDir);
    fileUtil.grantFileOrDirFullAccess(genFileDir);
    return genFileDir;
  }

  /** Explicitly add supported device types in MH sandbox. */
  @Override
  public void setUpForSandbox(List<String> deviceTypes) {
    for (String deviceType : deviceTypes) {
      addSupportedDeviceType(deviceType);
    }
  }

  /**
   * Add supported device type according to the driver name. If the driver class is not found, just
   * ignores it.
   */
  public void addSupportedDriver(String driverClassSimpleName) throws InterruptedException {
    Class<? extends Driver> driverClass;
    try {
      driverClass = ClassUtil.getDriverClass(driverClassSimpleName);
    } catch (MobileHarnessException e) {
      logger.atInfo().log("Driver %s not found. Ignored.", driverClassSimpleName);
      return;
    }
    addSupportedDriver(driverClass);
  }

  /**
   * Adds one supported driver. We will use the corresponding validator to check whether the current
   * system environment supports it.If the driver is not supported, it will be ignored and can not
   * be added.
   */
  public void addSupportedDriver(Class<? extends Driver> driverClass) throws InterruptedException {
    if (info().supportedDrivers().contains(driverClass.getSimpleName())) {
      logger.atWarning().log("Driver %s already exists", driverClass.getCanonicalName());
      return;
    }
    try {
      // Runs the validator.
      Optional<Class<? extends Validator>> validatorClass =
          ClassUtil.getValidatorClass(driverClass.getSimpleName());
      if (validatorClass.isPresent()) {
        validatorFactory.createValidator(validatorClass.get()).validateEnv(this);
      }

      // Runs the validator methods.
      validateEnvByAllValidatorMethods(driverClass);
    } catch (MobileHarnessException e) {
      logger.atInfo().log(
          "Driver %s not supported: %s", driverClass.getSimpleName(), e.getMessage());
      return;
    }
    info().supportedDrivers().add(driverClass.getSimpleName());
  }

  /**
   * Removes one supported driver according to the driver name. If the driver class is not found,
   * just ignores it.
   */
  protected final void removeSupportedDriver(String driverClassSimpleName) {
    Class<? extends Driver> driverClass;
    try {
      driverClass = ClassUtil.getDriverClass(driverClassSimpleName);
    } catch (MobileHarnessException e) {
      logger.atInfo().log("Driver %s not found. Ignored.", driverClassSimpleName);
      return;
    }
    removeSupportedDriver(driverClass);
  }

  /** Removes one supported driver. */
  protected final void removeSupportedDriver(Class<? extends Driver> driverClass) {
    if (!info().supportedDrivers().contains(driverClass.getSimpleName())) {
      logger.atWarning().log(
          "Ignore removing %s as it is not in supported driver list %s.",
          driverClass.getCanonicalName(), info().supportedDrivers());
      return;
    }
    info().supportedDrivers().remove(driverClass.getSimpleName());
  }

  /**
   * Adds one supported decorator according to the decorator name. If the decorator class is not
   * found, just ignores it.
   */
  public void addSupportedDecorator(String decoratorClassSimpleName) throws InterruptedException {
    Class<? extends Decorator> decoratorClass;
    try {
      decoratorClass = ClassUtil.getDecoratorClass(decoratorClassSimpleName);
    } catch (MobileHarnessException e) {
      logger.atInfo().log("Decorator %s not found. Ignored.", decoratorClassSimpleName);
      return;
    }
    addSupportedDecorator(decoratorClass);
  }

  /**
   * Adds one supported decorator. We will use the corresponding validator if exists to check
   * whether the current system environment supports it. If the decorator is not supported, it will
   * be ignored and can not be added.
   */
  public void addSupportedDecorator(Class<? extends Decorator> decoratorClass)
      throws InterruptedException {
    if (info().supportedDecorators().contains(decoratorClass.getSimpleName())) {
      logger.atWarning().log("Decorator %s already exists", decoratorClass.getCanonicalName());
      return;
    }
    try {
      // Runs the validator.
      Optional<Class<? extends Validator>> validatorClass =
          ClassUtil.getValidatorClass(decoratorClass.getSimpleName());
      // If there is no Validator for the given decorator type, will skip the validation.
      if (validatorClass.isPresent()) {
        validatorFactory.createValidator(validatorClass.get()).validateEnv(this);
      }

      // Runs the validator methods.
      validateEnvByAllValidatorMethods(decoratorClass);
    } catch (MobileHarnessException e) {
      logger.atInfo().log("%s not supported: %s", decoratorClass.getSimpleName(), e.getMessage());
      return;
    }
    info().supportedDecorators().add(decoratorClass.getSimpleName());
  }

  /**
   * Removes one supported decorator according to the decorator name. If the decorator class is not
   * found, just ignores it.
   */
  public final void removeSupportedDecorator(String decoratorClassSimpleName) {
    Class<? extends Decorator> decoratorClass;
    try {
      decoratorClass = ClassUtil.getDecoratorClass(decoratorClassSimpleName);
    } catch (MobileHarnessException e) {
      logger.atInfo().log("Decorator %s not found. Ignored.", decoratorClassSimpleName);
      return;
    }
    removeSupportedDecorator(decoratorClass);
  }

  /** Removes one supported decorator. */
  public final void removeSupportedDecorator(Class<? extends Decorator> decoratorClass) {
    if (!info().supportedDecorators().contains(decoratorClass.getSimpleName())) {
      logger.atWarning().log(
          "Ignore removing %s as it is not in supported decorator list %s.",
          decoratorClass.getCanonicalName(), info().supportedDecorators());
      return;
    }
    info().supportedDecorators().remove(decoratorClass.getSimpleName());
  }

  /** Set general file directory. */
  public static void setGenFileDirRoot(String genFileRootDir) {
    BaseDevice.genFileDirRoot = genFileRootDir;
  }

  /**
   * Validates the environment of the given device by all environment validator methods declared in
   * the given class.
   *
   * <p>The method could be used for testing environment validator methods.
   *
   * @param device the device
   * @param clazz the class in which the environment validator methods declared will be invoked
   * @throws MobileHarnessException if the environment is invalid, the given class has an invalid
   *     environment validator method, failed to run an environment validator method or an invoked
   *     environment validator method throws an unchecked exception
   * @throws InterruptedException if an invoked environment validator method throws it
   * @see ValidatorAnnotation
   */
  @VisibleForTesting
  public static void validateEnvByValidatorMethods(Device device, Class<?> clazz)
      throws MobileHarnessException, InterruptedException {
    for (Method method : clazz.getDeclaredMethods()) {
      if (ClassUtil.isValidatorMethod(method, ValidatorAnnotation.Type.ENVIRONMENT)) {
        logger.atInfo().log("Validate env for %s with method: %s", clazz, method);
        method.setAccessible(true);
        try {
          method.invoke(/* obj= */ null, device);
        } catch (IllegalAccessException | IllegalArgumentException e) {
          throw new MobileHarnessException(
              ErrorCode.DEVICE_ENVIRONMENT_VALIDATE_ERROR,
              String.format("Failed to run environment validator %s: %s", method, e),
              e);
        } catch (InvocationTargetException e) {
          Throwable targetException = e.getTargetException();
          if (targetException instanceof InterruptedException) {
            throw (InterruptedException) targetException;
          }
          if (targetException instanceof MobileHarnessException) {
            throw (MobileHarnessException) targetException;
          }
          throw new MobileHarnessException(
              ErrorCode.DEVICE_ENVIRONMENT_VALIDATE_ERROR,
              String.format(
                  "Exception thrown by environment validator %s: %s", method, targetException),
              targetException);
        }
      }
    }
  }

  /** Validates the environment for a driver or decorator with its all validator methods. */
  private void validateEnvByAllValidatorMethods(Class<?> clazz)
      throws MobileHarnessException, InterruptedException {
    Set<Class<?>> annotationValidatorClasses = new HashSet<>();
    ClassUtil.getClassesWithAllSteps(clazz, annotationValidatorClasses);

    for (Class<?> c : annotationValidatorClasses) {
      validateEnvByValidatorMethods(this, c);
    }
  }

  @Override
  public String toString() {
    return String.format("%s(%s)", getClass().getSimpleName(), getDeviceId());
  }
}
