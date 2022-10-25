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

import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/**
 * Provides environment on a device for running a special kind of tests. A device can load different
 * drivers to run test using different tools such as: Android Instrumentation, IOS UIAutomation,
 * WebDriver, SkyTree, ...
 *
 * <p>A driver could be constructed in one of the following two ways by {@link DriverFactory} in
 * {@linkplain com.google.devtools.mobileharness.infra.controller.test.local.LocalDirectTestRunner
 * LocalTestRunner}:
 *
 * <ol>
 *   <li>Invoking its constructor with two parameters {@link Device} and {@link TestInfo} if it does
 *       not have a module.
 *       <p>The following is an example:
 *       <pre>
 * public class MyDriver extends BaseDriver {
 *   <b>public MyDriver(Device device, TestInfo testInfo)</b> {
 *     ...
 *   }
 *   ...
 * }</pre>
 *   <li>Dependency injection with its module if it has one.
 *       <p>The following is an example:
 *       <pre>
 * public class MyDriver extends BaseDriver {
 *   <b>&#064;Inject</b>
 *   <b>public MyDriver(&#064;Assisted Device device, &#064;Assisted TestInfo testInfo,
 *       OtherParam1 otherParam1, OtherParam2 otherParam2, OtherParam3 ...)</b> {
 *     ...
 *   }
 *   ...
 * }</pre>
 *       <p>Additionally, in this way, you need to write a {@linkplain com.google.inject.Module
 *       Module} in {@link com.google.wireless.qa.mobileharness.shared.api.module} for the driver
 *       with the name "{@code <your-driver-name>Module}". For example:
 *       <pre>
 * public class <b>MyDriverModule</b> extends AbstractModule {
 *   &#064;Override
 *   protected void configure() {
 *     install(new OtherParam1Module());
 *     install(new OtherParam2Module());
 *     bind(OtherParam3.class).to(OtherParam3Impl.class);
 *     ...
 *   }
 * }</pre>
 * </ol>
 */
public interface Driver {
  /** Returns the device who is using this driver. */
  Device getDevice();

  /** Returns the test info of the test. */
  TestInfo getTest();

  /**
   * Runs a single test on device using the driver.
   *
   * <p>Before invoking this method, the status of the {@code TestInfo} is set to {@code
   * TestStatus.RUNNING}. And after the test is finished, the status will be set to {@code
   * TestStatus.DONE}. You don't need to change the {@code TestStatus} inside this method. You only
   * need to set the {@code TestResult} to tell whether the test is PASS/FAIL.
   *
   * <p>You don't need to handle any unexpected exception inside this method, either. Just throws
   * them out and {@code DeviceRunner} will handle them:
   *
   * <ol>
   *   <li>If the test is timeout and the execution of this method is interrupted, the {@code
   *       TestInfo} will be set to {@code TestStatus.TIMEOUT}.
   *   <li>If any unexpected exceptions are thrown out, {@code DeviceRunner} will add the error
   *       message to the logger and {@code TestInfo}. And set {@code TestInfo} to {@code
   *       TestStatus.ERROR}.
   * </ol>
   *
   * <p>Don't invoke {@link Device#reboot()} inside this method. If you want to reboot the device
   * after running test, you should use setMaxContinuouslyFailedTestNum() to do that.
   *
   * @param testInfo single test to run
   * @throws MobileHarnessException if error occurs while executing the test
   * @throws InterruptedException if the current thread or its sub-thread is {@linkplain
   *     Thread#interrupt() interrupted} by another thread
   */
  void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException;

  /**
   * @deprecated Use {@link #run(TestInfo)} instead.
   */
  @Deprecated
  void run(com.google.wireless.qa.mobileharness.shared.api.job.TestInfo testInfo)
      throws MobileHarnessException, InterruptedException;
}
