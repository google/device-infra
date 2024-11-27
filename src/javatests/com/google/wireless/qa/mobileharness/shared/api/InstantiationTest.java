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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.reflect.Modifier.isAbstract;
import static java.util.Arrays.stream;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.inject.Guice;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ConstraintsForTesting;
import com.google.wireless.qa.mobileharness.shared.api.decorator.Decorator;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.driver.DriverFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfoMocker;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.DriverDecoratorSpecMapper;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Tests to instantiate all {@link Driver}s and {@link Decorator}s. */
@RunWith(JUnit4.class)
public class InstantiationTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String DRIVER_DECORATOR_SPEC_MAPPER_PATH =
      "google3/third_party/deviceinfra/src/java/com/google/wireless/qa/mobileharness"
          + "/shared/model/job/in/spec/DriverDecoratorSpecMapper.java";
  private static final List<Class<? extends Driver>> driverClasses = new ArrayList<>();
  private static final List<Class<? extends Decorator>> decoratorClasses = new ArrayList<>();

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  private final TestInfo testInfo = TestInfoMocker.mockTestInfo();

  @Mock private Driver decoratedDriver;

  private Device device;

  @Inject private DriverFactory driverFactory;

  @BeforeClass
  public static void fetchDriverDecorator() throws Exception {
    driverClasses.addAll(
        listClasses("com.google.wireless.qa.mobileharness.shared.api.driver", Driver.class));
    decoratorClasses.addAll(
        listClasses("com.google.wireless.qa.mobileharness.shared.api.decorator", Decorator.class));
  }

  @Before
  public void setUp() throws Exception {
    when(testInfo.getTmpFileDir())
        .thenReturn(tmpFolder.newFolder("test_tmp_file_dir").getAbsolutePath());
    when(decoratedDriver.getTest()).thenReturn(testInfo);

    Guice.createInjector().injectMembers(this);
  }

  @Test
  public void instantiateDrivers() throws Exception {
    for (Class<? extends Driver> driverClass : driverClasses) {
      if (mockDataModel(driverClass)) {
        try {
          driverFactory.createDriver(device, testInfo, driverClass, /* driverWrapper= */ null);
        } catch (Throwable e) {
          throw addHelp(e, driverClass);
        }
      }
      cleanupMockedDataModel();
    }
  }

  @Test
  public void instantiateDecorators() throws Exception {
    for (Class<? extends Decorator> decoratorClass : decoratorClasses) {
      if (mockDataModel(decoratorClass)) {
        try {
          driverFactory.decorateDriver(
              decoratedDriver,
              testInfo,
              ImmutableList.of(decoratorClass),
              /* driverWrapper= */ null,
              /* decoratorExtender= */ null);
        } catch (Throwable e) {
          throw addHelp(e, decoratorClass);
        }
      }
      cleanupMockedDataModel();
    }
  }

  @Test
  public void checkDriverDecoratorSpec() throws Exception {
    Map<String, String> driverDecoratorSpecMapFromReflection = new HashMap<>();
    for (Class<? extends Decorator> decoratorClass : decoratorClasses) {
      if (JobSpecHelper.isSpecConfigable(decoratorClass)) {
        driverDecoratorSpecMapFromReflection.put(
            decoratorClass.getSimpleName(),
            JobSpecHelper.getSpecClass(decoratorClass).getSimpleName());
      }
    }
    for (Class<? extends Driver> driverClass : driverClasses) {
      if (JobSpecHelper.isSpecConfigable(driverClass)) {
        driverDecoratorSpecMapFromReflection.put(
            driverClass.getSimpleName(), JobSpecHelper.getSpecClass(driverClass).getSimpleName());
      }
    }

    try {
      assertThat(driverDecoratorSpecMapFromReflection)
          .isEqualTo(DriverDecoratorSpecMapper.getDriverDecoratorSpecMap());
    } catch (Throwable e) {
      throw addSpecMismatchHelp(e);
    }
  }

  /**
   * Gets {@link ConstraintsForTesting} of the given driver/decorator if any, and mocks {@link
   * Device} and other data models based on it.
   *
   * @return false if {@link ConstraintsForTesting} of the driver/decorator requires to skip the
   *     instantiation test for the driver/decorator
   */
  private boolean mockDataModel(Class<? extends Driver> driverClass)
      throws MobileHarnessException, NoSuchMethodException {
    ConstraintsForTesting constraints =
        stream(driverClass.getDeclaredConstructors())
            .map(constructor -> constructor.getAnnotation(ConstraintsForTesting.class))
            .map(Optional::ofNullable)
            .flatMap(Optional::stream)
            .findFirst()
            .orElse(
                Dummy.class.getDeclaredConstructor().getAnnotation(ConstraintsForTesting.class));

    device = mock(constraints.deviceClass());
    when(device.getDeviceId()).thenReturn("fake_device_id");
    when(device.getDeviceTypes()).thenReturn(ImmutableSet.copyOf(constraints.deviceTypes()));
    when(decoratedDriver.getDevice()).thenReturn(device);

    for (String jobFileTag : constraints.jobFileTags()) {
      testInfo.jobInfo().files().add(jobFileTag, "fake_value");
    }

    if (!constraints.enableInstantiationTest()) {
      logger.atInfo().log("Class %s disable instantiation test", driverClass.getSimpleName());
    }

    return constraints.enableInstantiationTest();
  }

  private void cleanupMockedDataModel() throws MobileHarnessException {
    Files jobFiles = testInfo.jobInfo().files();
    for (String jobFileTag : jobFiles.getAll().keySet()) {
      jobFiles.replaceAll(jobFileTag, ImmutableList.of());
    }
  }

  /**
   * Lists all top-level non-interface non-abstract classes whose type is {@code type} in the given
   * package.
   */
  private static <T> ImmutableList<Class<? extends T>> listClasses(
      String packageName, Class<T> type) throws IOException {
    @SuppressWarnings("unchecked")
    ImmutableList<Class<? extends T>> result =
        ClassPath.from(InstantiationTest.class.getClassLoader())
            .getTopLevelClasses(packageName)
            .stream()
            .map(ClassInfo::load)
            .filter(type::isAssignableFrom)
            .filter(not(Class::isInterface))
            .filter(clazz -> !isAbstract(clazz.getModifiers()))
            .map(clazz -> (Class<? extends T>) clazz)
            .collect(toImmutableList());

    logger.atInfo().log(
        "%s classes in package %s:%s",
        type.getSimpleName(),
        packageName,
        result.stream()
            .map(clazz -> String.format("\n\t%s", clazz.getSimpleName()))
            .collect(joining()));
    return result;
  }

  private static IllegalStateException addHelp(Throwable e, Class<? extends Driver> driverClass) {
    IllegalStateException result =
        new IllegalStateException(
            String.format(
                "Failed to instantiate class %s. Please check its constructor or its module, or add"
                    + " @ConstraintsForTesting to its constructor. See the cause for more details.",
                driverClass.getSimpleName()),
            e);
    result.setStackTrace(new StackTraceElement[0]);
    return result;
  }

  private static IllegalStateException addSpecMismatchHelp(Throwable e) {
    IllegalStateException result =
        new IllegalStateException(
            String.format(
                "If you add/edit/remove a Driver/Decorator which implements the SpecConfigable"
                    + " interface, please make corresponding modifications in the static config"
                    + " file, file address %s",
                DRIVER_DECORATOR_SPEC_MAPPER_PATH),
            e);
    result.setStackTrace(new StackTraceElement[0]);
    return result;
  }

  /** A dummy class to get the default instance of {@link ConstraintsForTesting}. */
  private static class Dummy {

    @ConstraintsForTesting
    private Dummy() {}
  }
}
