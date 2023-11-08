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
 * <p>There are two ways to instantiate a Driver class:
 *
 * <ol>
 *   <li>(preferred) Define an {@linkplain javax.inject.Inject @Inject} constructor in the Driver
 *       class.
 *       <p>For example:
 *       <pre>
 * public class MyDriver extends BaseDriver {
 *   <b>&#064;Inject</b>
 *   MyDriver(<b>Device device, TestInfo testInfo,</b> OtherParam1 otherParam1,
 *       OtherParam2 otherParam2, OtherParam3 ...) {
 *     ...
 *   }
 * }</pre>
 *       <p>In the example above, the {@code Device} and {@code TestInfo} can appear in any place in
 *       the parameter list.
 *       <p>Instances of the following types will be provided by the test runner:
 *       <ul>
 *         <li>{@link TestInfo}
 *         <li>{@link Device}
 *         <li>Common types bound in {@linkplain
 *             com.google.wireless.qa.mobileharness.shared.api.CommonLibraryModule
 *             CommonLibraryModule}:
 *             <ul>
 *               <li>{@linkplain java.time.Clock Clock}
 *               <li>{@linkplain
 *                   com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache
 *                   DeviceCache}
 *               <li>{@linkplain java.util.concurrent.Executor Executor} / {@linkplain
 *                   java.util.concurrent.ExecutorService ExecutorService} / {@linkplain
 *                   com.google.common.util.concurrent.ListeningExecutorService
 *                   ListeningExecutorService}
 *               <li>Sleeper ({@linkplain com.google.common.time.Sleeper Guava} / {@linkplain
 *                   com.google.devtools.mobileharness.shared.util.time.Sleeper deviceinfra})
 *               <li>{@linkplain com.google.common.base.Ticker Ticker}
 *               <li>{@linkplain com.google.common.time.TimeSource TimeSource}
 *             </ul>
 *       </ul>
 *       <p>Additionally, if necessary, you can write a {@linkplain com.google.inject.Module Module}
 *       class whose name is {@code "<your_driver_name>Module"} in the package {@link
 *       com.google.wireless.qa.mobileharness.shared.api.module}, to inject other parameters of the
 *       constructor.
 *       <p>For example:
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
 *       <p>The module class itself should be able to be injected by an empty injector. In another
 *       word, it should either have a constructor taking no arguments, or have an {@linkplain
 *       javax.inject.Inject @Inject} constructor which does not need a corresponding module.
 *   <li>Define a public constructor with a parameter list [Device, TestInfo] in the Driver class.
 *       <p>For example:
 *       <pre>
 * public class MyDriver extends BaseDriver {
 *   <b>public MyDriver(Device device, TestInfo testInfo)</b> {
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
