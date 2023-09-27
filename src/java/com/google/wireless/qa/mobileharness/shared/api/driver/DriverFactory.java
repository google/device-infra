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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.CommonLibraryModule;
import com.google.wireless.qa.mobileharness.shared.api.decorator.Decorator;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.util.ReflectionUtil;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Simple factory for creating {@link Driver} instances. */
public class DriverFactory {

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
    // Gets the module class if any.
    Optional<Class<? extends Module>> moduleClass = getModuleClass(driverClass);

    // Gets the public Xxx(Device, Test) constructor if any.
    Optional<? extends Constructor<? extends Driver>> driverConstructor =
        getConstructor(driverClass, Device.class, TestInfo.class);

    if (moduleClass.isPresent() || driverConstructor.isEmpty()) {
      // Uses Guice to instantiate the driver.
      return injectDriver(driverClass, moduleClass.orElse(null), testInfo, device);
    } else {
      // Calls the constructor.
      try {
        return driverConstructor.get().newInstance(device, testInfo);
      } catch (ReflectiveOperationException e) {
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
   * Creates a new {@link Driver} instance according to the given {@link Driver} class type.
   *
   * @param device the {@link Device} owning this new {@link Driver}
   * @param testInfo the test info
   * @param driverClass {@link Driver} class type
   * @param driverWrapper wrapper for wrapping the driver
   * @return a new {@link Driver} instance associated with the given {@link Device}
   * @throws MobileHarnessException if fails to create a new {@link Driver} instance
   */
  @CanIgnoreReturnValue
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
   *     driver. Its first parameter represents the decorated driver and its return value will
   *     replace the decorated driver.
   * @return a new {@link Driver} instance associated with the given {@link Device} and decorated by
   *     the given decorators
   * @throws MobileHarnessException if fails to create a new {@link Driver} instance
   */
  @CanIgnoreReturnValue
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
    // Gets the module class if any.
    Optional<Class<? extends Module>> moduleClass = getModuleClass(decoratorClass);

    // Gets the public Xxx(Driver, TestInfo) constructor if any.
    Optional<? extends Constructor<? extends Decorator>> decoratorConstructor =
        getConstructor(decoratorClass, Driver.class, TestInfo.class);

    if (moduleClass.isPresent() || decoratorConstructor.isEmpty()) {
      // Uses Guice to instantiate the decorator.
      return injectDecorator(decoratorClass, moduleClass.orElse(null), testInfo, decoratedDriver);
    } else {
      // Calls the constructor.
      try {
        return decoratorConstructor.get().newInstance(decoratedDriver, testInfo);
      } catch (ReflectiveOperationException e) {
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
   * "AcidRemoteDriverModule".
   *
   * @param driverOrDecoratorClass the driver or decorator
   * @return the module class or empty
   */
  private static Optional<Class<? extends Module>> getModuleClass(
      Class<? extends Driver> driverOrDecoratorClass) {
    try {
      Class<? extends Module> moduleClass =
          ReflectionUtil.getClass(
              driverOrDecoratorClass.getSimpleName() + Module.class.getSimpleName(),
              Module.class,
              "com.google.wireless.qa.mobileharness.shared.api.module");
      return Optional.of(moduleClass);
    } catch (MobileHarnessException e) {
      return Optional.empty();
    }
  }

  // Gets a public constructor with two parameters if any.
  private static <T> Optional<Constructor<T>> getConstructor(
      Class<T> clazz, Class<?> parameterType1, Class<?> parameterType2) {
    try {
      return Optional.of(clazz.getConstructor(parameterType1, parameterType2));
    } catch (NoSuchMethodException e) {
      return Optional.empty();
    }
  }

  private static <T extends Driver> T injectDriver(
      Class<T> driverClass,
      @Nullable Class<? extends Module> moduleClass,
      TestInfo testInfo,
      Device device)
      throws MobileHarnessException {
    return injectInstance(
        driverClass,
        moduleClass,
        /* context= */ ImmutableMap.of(TestInfo.class, testInfo, Device.class, device));
  }

  private static <T extends Decorator> T injectDecorator(
      Class<T> decoratorClass,
      @Nullable Class<? extends Module> moduleClass,
      TestInfo testInfo,
      Driver decoratedDriver)
      throws MobileHarnessException {
    return injectInstance(
        decoratorClass,
        moduleClass,
        /* context= */ ImmutableMap.of(
            TestInfo.class,
            testInfo,
            Driver.class,
            decoratedDriver,
            Device.class,
            decoratedDriver.getDevice()));
  }

  /**
   * Uses Guice to instantiate a class, with an optional module class, and a context map containing
   * instances bound in the injector.
   *
   * @param clazz the class to instantiate
   * @param moduleClass an optional module class
   * @param context a map containing instances bound in the injector
   */
  private static <T> T injectInstance(
      Class<T> clazz,
      @Nullable Class<? extends Module> moduleClass,
      ImmutableMap<Class<?>, Object> context)
      throws MobileHarnessException {
    try {
      Optional<Module> module = injectModule(moduleClass);
      ContextModule contextModule = new ContextModule(context);
      Injector injector =
          Guice.createInjector(
              Stage.PRODUCTION,
              Stream.concat(Stream.of(contextModule, new CommonLibraryModule()), module.stream())
                  .collect(toImmutableList()));
      return injector.getInstance(clazz);
    } catch (RuntimeException e) {
      throw new MobileHarnessException(
          ErrorCode.REFLECTION_INSTANTIATION_ERROR,
          String.format("Reflection error when creating %s", clazz),
          e);
    }
  }

  /** Uses an empty injector to instantiate the module class of a driver/decorator. */
  private static Optional<Module> injectModule(@Nullable Class<? extends Module> moduleClass) {
    return Optional.ofNullable(moduleClass)
        .map(clazz -> Guice.createInjector(Stage.PRODUCTION).getInstance(clazz));
  }

  private static class ContextModule extends AbstractModule {

    private final ImmutableMap<Class<?>, Object> context;

    private ContextModule(ImmutableMap<Class<?>, Object> context) {
      this.context = context;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void configure() {
      context.forEach((clazz, instance) -> bind((Class<Object>) clazz).toInstance(instance));
    }
  }
}
