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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.util.Types;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.ClassUtil;
import com.google.wireless.qa.mobileharness.shared.api.decorator.Decorator;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.util.ReflectionUtil;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

/** Simple factory for creating {@link Driver} instances. */
public class DriverFactory {

  /** The base driver factory. */
  private interface BaseDriverFactory<T extends Driver> {

    T create(Device device, TestInfo testInfo);
  }

  /** The base decorator factory. */
  private interface BaseDecoratorFactory<T extends Decorator> {

    T create(Driver driver, TestInfo testInfo);
  }

  /**
   * Creates a new {@link Driver} instance according to the given {@link Driver} class type.
   *
   * @param device the {@link Device} owning this new {@link Driver}
   * @param testInfo the test info
   * @param driverClass {@link Driver} class type
   * @return a new {@link Driver} instance associated with the given {@link Device}
   * @throws MobileHarnessException if fails to create a new {@link Driver} instance
   */
  @VisibleForTesting
  Driver createDriver(Device device, TestInfo testInfo, Class<? extends Driver> driverClass)
      throws MobileHarnessException {
    // Gets the module class of the driver.
    Optional<Class<? extends Module>> moduleClass = getModuleClass(driverClass);
    if (moduleClass.isPresent()) {
      // Creates the driver with the module.
      return createDriverWithModule(device, testInfo, driverClass, moduleClass.get());
    } else {
      // Creates the driver by invoking the constructor directly.
      try {
        return driverClass
            .getConstructor(Device.class, TestInfo.class)
            .newInstance(device, testInfo);
      } catch (IllegalArgumentException | ReflectiveOperationException e) {
        throw new MobileHarnessException(
            ErrorCode.REFLECTION_INSTANTIATION_ERROR,
            String.format(
                "Failed to create driver %s, maybe forget to add driver module to runtime_deps"
                    + " of lab or local-mode client? Please refer to the javadoc of Driver.",
                driverClass.getSimpleName()),
            e);
      }
    }
  }

  /**
   * Given a driver class and a decorator class list: [Decorator1, Decorator2, ... DecoratorN], will
   * create a driver instance like: new DecoratorN(...new Decorator2(new Decorator1(new
   * Driver(Device)))).
   *
   * @param device the {@code Device} owning this new {@code Driver}
   * @param testInfo the test info
   * @param driverClass {@code Driver} class type
   * @param decoratorClasses a list of {@code Decorator} class types
   * @param driverWrapper wrapper for wrapping each driver/decorator
   * @param decoratorExtender decorator extender for extending a decorator when it decorates another
   *     driver. Its first parameter represents the decoratored driver and its return value will
   *     replace the decorated driver.
   * @return a new {@link Driver} instance associated with the given {@link Device} and decorated by
   *     the given decorators
   * @throws MobileHarnessException if fails to create a new {@link Driver} instance
   */
  public Driver createDriver(
      Device device,
      TestInfo testInfo,
      Class<? extends Driver> driverClass,
      List<Class<? extends Decorator>> decoratorClasses,
      @Nullable BiFunction<Driver, String, Decorator> driverWrapper,
      @Nullable BiFunction<Driver, Class<? extends Decorator>, Decorator> decoratorExtender)
      throws MobileHarnessException {
    Driver driver = createDriver(device, testInfo, driverClass, driverWrapper);
    return decorateDriver(driver, testInfo, decoratorClasses, driverWrapper, decoratorExtender);
  }

  /**
   * Creates a new {@link Driver} instance according to the given {@link Driver} class type.
   *
   * @param device the {@link Device} owning this new {@link Driver}
   * @param testInfo the test info
   * @param driverClass {@link Driver} class type
   * @param driverWrapper wrapper for wrapping the driver
   * @return a new {@link Driver} instance associated with the given {@link Device}
   * @throws MobileHarnessException if fails to create a new {@link Driver} instance
   */
  public Driver createDriver(
      Device device,
      TestInfo testInfo,
      Class<? extends Driver> driverClass,
      @Nullable BiFunction<Driver, String, Decorator> driverWrapper)
      throws MobileHarnessException {
    Driver driver = createDriver(device, testInfo, driverClass);
    return driverWrapper == null
        ? driver
        : driverWrapper.apply(driver, driver.getClass().getSimpleName());
  }

  /**
   * Given a driver and a decorator class list: [Decorator1, Decorator2, ... DecoratorN], will
   * create a driver instance like: new DecoratorN(...new Decorator2(new Decorator1(driver))).
   *
   * @param driver {@code Driver} to decorate
   * @param testInfo the test info
   * @param decoratorClasses a list of {@code Decorator} class types
   * @param driverWrapper wrapper for wrapping each driver/decorator
   * @param decoratorExtender decorator extender for extending a decorator when it decorates another
   *     driver. Its first parameter represents the decoratored driver and its return value will
   *     replace the decorated driver.
   * @return a new {@link Driver} instance associated with the given {@link Device} and decorated by
   *     the given decorators
   * @throws MobileHarnessException if fails to create a new {@link Driver} instance
   */
  public Driver decorateDriver(
      Driver driver,
      TestInfo testInfo,
      List<Class<? extends Decorator>> decoratorClasses,
      @Nullable BiFunction<Driver, String, Decorator> driverWrapper,
      @Nullable BiFunction<Driver, Class<? extends Decorator>, Decorator> decoratorExtender)
      throws MobileHarnessException {
    for (Class<? extends Decorator> decoratorClass : decoratorClasses) {
      driver = decoratorExtender == null ? driver : decoratorExtender.apply(driver, decoratorClass);
      driver = decorateDriver(driver, testInfo, decoratorClass);
      driver =
          driverWrapper == null
              ? driver
              : driverWrapper.apply(driver, driver.getClass().getSimpleName());
    }
    return driver;
  }

  /**
   * Creates a new {@link Decorator} instance to decorate the given {@link Driver}.
   *
   * @param decoratedDriver {@link Driver} instance to be decorated
   * @param testInfo the test info
   * @param decoratorClass the {@link Decorator} class type
   * @return a new {@link Decorator} instance decorating the given {@link Driver}
   * @throws MobileHarnessException if fails to create a new {@link Decorator} instance
   */
  @VisibleForTesting
  protected Decorator decorateDriver(
      Driver decoratedDriver, TestInfo testInfo, Class<? extends Decorator> decoratorClass)
      throws MobileHarnessException {
    // Gets the module class of the decorator.
    Optional<Class<? extends Module>> moduleClass = getModuleClass(decoratorClass);
    if (moduleClass.isPresent()) {
      // Creates the decorator with the module.
      return createDecoratorWithModule(
          decoratedDriver, testInfo, decoratorClass, moduleClass.get());
    } else {
      // Creates the decorator by invoking the constructor directly.
      try {
        return decoratorClass
            .getConstructor(Driver.class, TestInfo.class)
            .newInstance(decoratedDriver, testInfo);
      } catch (IllegalArgumentException | ReflectiveOperationException e) {
        throw new MobileHarnessException(
            ErrorCode.REFLECTION_INSTANTIATION_ERROR,
            String.format(
                "Failed to create decorator %s, maybe forget to add decorator module to"
                    + " runtime_deps of lab or local-mode client? Please refer to the javadoc of"
                    + " Decorator.",
                decoratorClass.getSimpleName()),
            e);
      }
    }
  }

  /**
   * Gets the module class of the given driver or decorator. The module class should be a sub-class
   * of {@link Module}, in the package {@link com.google.wireless.qa.mobileharness.shared.api
   * .module} and with the name {@code "<driver-or-decorator-name>Module"} like
   * "AndroidMoretoModule".
   *
   * @param driverOrDecoratorClass the driver or decorator
   * @return the module class or empty
   */
  public Optional<Class<? extends Module>> getModuleClass(
      Class<? extends Driver> driverOrDecoratorClass) {
    try {
      Class<? extends Module> moduleClass =
          ReflectionUtil.getClass(
              driverOrDecoratorClass.getSimpleName() + Module.class.getSimpleName(),
              Module.class,
              ClassUtil.class.getPackage().getName() + ".module");
      return Optional.of(moduleClass);
    } catch (MobileHarnessException e) {
      return Optional.empty();
    }
  }

  private <T extends Driver> T createDriverWithModule(
      Device device, TestInfo testInfo, Class<T> driverClass, Class<? extends Module> moduleClass)
      throws MobileHarnessException {
    try {
      Module module = moduleClass.getConstructor().newInstance();
      @SuppressWarnings("unchecked")
      Key<BaseDriverFactory<T>> factoryKey =
          (Key<BaseDriverFactory<T>>)
              Key.get(
                  Types.newParameterizedTypeWithOwner(
                      DriverFactory.class, BaseDriverFactory.class, driverClass));
      Injector injector =
          Guice.createInjector(
              Stage.PRODUCTION,
              module,
              new AbstractModule() {

                @Override
                protected void configure() {
                  install(new FactoryModuleBuilder().build(factoryKey));
                  bind(TestInfo.class).toInstance(testInfo);
                  bind(Device.class).toInstance(device);
                }
              });
      BaseDriverFactory<T> factory = injector.getInstance(factoryKey);
      return factory.create(device, testInfo);
    } catch (ReflectiveOperationException e) {
      throw new MobileHarnessException(
          ErrorCode.REFLECTION_INSTANTIATION_ERROR,
          "Reflection error when creating driver with module: " + driverClass.getSimpleName(),
          e);
    }
  }

  private <T extends Decorator> T createDecoratorWithModule(
      Driver decoratedDriver,
      TestInfo testInfo,
      Class<T> decoratorClass,
      Class<? extends Module> moduleClass)
      throws MobileHarnessException {
    try {
      Module module = moduleClass.getConstructor().newInstance();
      @SuppressWarnings("unchecked")
      Key<BaseDecoratorFactory<T>> factoryKey =
          (Key<BaseDecoratorFactory<T>>)
              Key.get(
                  Types.newParameterizedTypeWithOwner(
                      DriverFactory.class, BaseDecoratorFactory.class, decoratorClass));
      Injector injector =
          Guice.createInjector(
              Stage.PRODUCTION,
              module,
              new AbstractModule() {

                @Override
                protected void configure() {
                  install(new FactoryModuleBuilder().build(factoryKey));
                  bind(TestInfo.class).toInstance(testInfo);
                  bind(Device.class).toInstance(decoratedDriver.getDevice());
                }
              });
      BaseDecoratorFactory<T> factory = injector.getInstance(factoryKey);
      return factory.create(decoratedDriver, testInfo);
    } catch (ReflectiveOperationException e) {
      throw new MobileHarnessException(
          ErrorCode.REFLECTION_INSTANTIATION_ERROR,
          "Reflection error when creating decorator with module: " + decoratorClass.getSimpleName(),
          e);
    }
  }
}
