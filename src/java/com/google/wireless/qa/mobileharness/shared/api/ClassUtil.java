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

package com.google.wireless.qa.mobileharness.shared.api;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.test.local.annotation.DoNotSubscribeTestEvent;
import com.google.devtools.mobileharness.shared.util.reflection.ClassConstants;
import com.google.wireless.qa.mobileharness.shared.api.annotation.StepAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.Decorator;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.validator.env.EnvValidator;
import com.google.wireless.qa.mobileharness.shared.util.ReflectionUtil;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Util for using reflection to get lab side class based on class name. */
public final class ClassUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Gets the sub-class of {@link Device} according to the given class simple name. The target class
   * should be in package {@link com.google.wireless.qa.mobileharness.shared.api.device}.
   *
   * @param deviceClassSimpleName simple name of the target device class
   * @throws MobileHarnessException if can not find the class
   */
  public static Class<? extends Device> getDeviceClass(String deviceClassSimpleName)
      throws MobileHarnessException {
    return getDeviceClass(
        deviceClassSimpleName, ClassConstants.SHARED_API_PACKAGE_NAME + ".device");
  }

  /**
   * Gets the sub-class of {@link Device} according to the given class simple name and package name.
   *
   * @param deviceClassSimpleName simple name of the target device class
   * @param packageName name of the package containing the target device class
   * @throws MobileHarnessException if can not find the class
   */
  public static Class<? extends Device> getDeviceClass(
      String deviceClassSimpleName, String packageName) throws MobileHarnessException {
    try {
      return ReflectionUtil.getClass(deviceClassSimpleName, Device.class, packageName);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.CLASS_DEVICE_CLASS_NOT_FOUND,
          "Device \"" + deviceClassSimpleName + "\" does not exist",
          e);
    }
  }

  /**
   * Gets the sub-class of {@link Driver} according to the given class simple name. The target class
   * should be in package {@link com.google.wireless.qa.mobileharness.shared.api.driver}.
   *
   * @param driverClassSimpleName simple name of the target driver class
   * @throws MobileHarnessException if can not find the class
   */
  public static Class<? extends Driver> getDriverClass(String driverClassSimpleName)
      throws MobileHarnessException {
    try {
      return ReflectionUtil.getClass(
          driverClassSimpleName, Driver.class, ClassConstants.SHARED_API_PACKAGE_NAME + ".driver");
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.CLASS_DRIVER_CLASS_NOT_FOUND,
          "Driver \"" + driverClassSimpleName + "\" does not exist",
          e);
    }
  }

  /**
   * Gets the sub-class of {@link Decorator} according to the given class simple name. The target
   * class should be in package {@link com.google.wireless.qa.mobileharness.shared.api.decorator}.
   *
   * @param decoratorClassSimpleName simple name of the target driver decorator class
   * @throws MobileHarnessException if can not find the class
   */
  public static Class<? extends Decorator> getDecoratorClass(String decoratorClassSimpleName)
      throws MobileHarnessException {
    try {
      return ReflectionUtil.getClass(
          decoratorClassSimpleName,
          Decorator.class,
          ClassConstants.SHARED_API_PACKAGE_NAME + ".decorator");
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.CLASS_DECORATOR_CLASS_NOT_FOUND,
          "Driver decorator \"" + decoratorClassSimpleName + "\" does not exist",
          e);
    }
  }

  /**
   * Gets the sub-classes of {@link Decorator}s according to the given class simple names. The
   * target classes should be in package {@link
   * com.google.wireless.qa.mobileharness.shared.api.decorator}.
   *
   * @param decoratorClassSimpleNames simple names of the target driver decorator classes
   * @throws MobileHarnessException if can not find the classes
   */
  public static List<Class<? extends Decorator>> getDecoratorClasses(
      List<String> decoratorClassSimpleNames) throws MobileHarnessException {
    List<Class<? extends Decorator>> decoratorClasses =
        new ArrayList<>(decoratorClassSimpleNames.size());
    for (String decorator : decoratorClassSimpleNames) {
      decoratorClasses.add(ClassUtil.getDecoratorClass(decorator));
    }
    return decoratorClasses;
  }

  /**
   * Gets the env validator class of the given driver or decorator. The validator class should be a
   * subclass of {@link EnvValidator} and in package {@link
   * com.google.wireless.qa.mobileharness.shared.api.validator.env}.
   *
   * @return the validator class for the given driver, or null if can not find the test validator
   *     class
   */
  public static Optional<Class<? extends EnvValidator>> getEnvValidatorClass(
      String driverOrDecoratorSimpleClassName) {
    try {
      return Optional.of(
          ReflectionUtil.getClass(
              driverOrDecoratorSimpleClassName + EnvValidator.class.getSimpleName(),
              EnvValidator.class,
              ClassConstants.SHARED_API_PACKAGE_NAME + ".validator.env"));
    } catch (MobileHarnessException e) {
      logger.atInfo().log(
          "No env validator for driver/decorator \"%s\" (expected class with name %s)",
          driverOrDecoratorSimpleClassName,
          driverOrDecoratorSimpleClassName + EnvValidator.class.getSimpleName());
      return Optional.empty();
    }
  }

  /**
   * Adds the given class with its superclasses/interfaces and all classes with their
   * superclasses/interfaces of its STEPs to the result which only contains the classes that have
   * been added by this method.
   *
   * <p>A step of a class is a field of the class with {@link StepAnnotation}. The field could be
   * inherited from the superclass/interfaces or composited indirectly. The field could have any
   * visibility.
   *
   * @see StepAnnotation
   */
  public static void getClassesWithAllSteps(Class<?> clazz, Set<Class<?>> result) {
    if (clazz != null && !result.contains(clazz)) {
      // Adds the given class itself to the result.
      result.add(clazz);

      // Adds all classes of the composited steps to the result.
      Arrays.stream(clazz.getDeclaredFields())
          .filter(field -> field.getAnnotation(StepAnnotation.class) != null)
          .map(Field::getType)
          .forEach(stepClazz -> getClassesWithAllSteps(stepClazz, result));

      // Adds all classes of the inherited steps to the result.
      getClassesWithAllSteps(clazz.getSuperclass(), result);
      Arrays.stream(clazz.getInterfaces())
          .forEach(classInterface -> getClassesWithAllSteps(classInterface, result));
    }
  }

  /**
   * Adds the given object and its decorated driver (if any) and all of its STEPs to the result
   * which only contains the objects that have been added by this method.
   *
   * <p>A step of a class is a field of the class with {@link StepAnnotation}. The field could be
   * inherited from the superclass or composited indirectly. The field could have any visibility.
   *
   * @throws MobileHarnessException if failed to get a step of the object
   * @see StepAnnotation
   */
  @VisibleForTesting
  static void getObjectsWithDecoratedAndAllSteps(Object object, Set<Object> result)
      throws MobileHarnessException {
    if (object != null && !result.contains(object)) {
      // Adds the given object itself to the result.
      if (!object.getClass().isAnnotationPresent(DoNotSubscribeTestEvent.class)) {
        result.add(object);
      }

      // Adds all steps to the result.
      Class<?> clazz = object.getClass();
      do {
        for (Field field : clazz.getDeclaredFields()) {
          if (field.getAnnotation(StepAnnotation.class) != null) {
            field.setAccessible(true);
            try {
              Object step = field.get(object);
              getObjectsWithDecoratedAndAllSteps(step, result);
            } catch (IllegalAccessException e) {
              throw new MobileHarnessException(
                  BasicErrorId.CLASS_STEP_FIELD_ACCESS_ERROR,
                  String.format("Failed to get step %s of object %s", field, object),
                  e);
            }
          }
        }
        clazz = clazz.getSuperclass();
      } while (clazz != null);

      // Handles the decorated driver if any.
      if (object instanceof Decorator) {
        getObjectsWithDecoratedAndAllSteps(((Decorator) object).getDecorated(), result);
      }
    }
  }

  /** Gets all event subscribers of a driver (e.g., itself, its decorators, its steps). */
  public static Set<Object> getAllSubscribersOfDriver(Driver driver) throws MobileHarnessException {
    Set<Object> result = new HashSet<>();
    getObjectsWithDecoratedAndAllSteps(driver, result);
    return result;
  }

  private ClassUtil() {}
}
