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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;

/**
 * Decorator for adding additional functionality to drivers.
 *
 * <p>There are two ways to instantiate a Decorator class:
 *
 * <ol>
 *   <li>(preferred) Define an {@linkplain javax.inject.Inject @Inject} constructor in the Decorator
 *       class.
 *       <p>For example:
 *       <pre>
 * public class MyDecorator extends BaseDecorator {
 *   <b>&#064;Inject</b>
 *   MyDecorator(<b>Driver decoratedDriver, TestInfo testInfo,</b> OtherParam1 otherParam1,
 *       OtherParam2 otherParam2, OtherParam3 ...) {
 *     ...
 *   }
 * }</pre>
 *       <p>In the example above, the {@code Driver} and {@code TestInfo} can appear in any place in
 *       the parameter list.
 *       <p>Instances of the following types will be provided by the test runner:
 *       <ul>
 *         <li>{@linkplain com.google.wireless.qa.mobileharness.shared.model.job.TestInfo TestInfo}
 *         <li>{@link Driver} (the decorated driver)
 *         <li>{@linkplain com.google.wireless.qa.mobileharness.shared.api.device.Device Device}
 *         <li>Common types bound in {@linkplain
 *             com.google.wireless.qa.mobileharness.shared.api.CommonLibraryModule
 *             CommonLibraryModule}:
 *             <ul>
 *               <li>{@linkplain java.time.Clock Clock}
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
 * public class <b>MyDecoratorModule</b> extends AbstractModule {
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
 *   <li>Define a public constructor with a parameter list [Driver, TestInfo] in the Decorator
 *       class.
 *       <p>For example:
 *       <pre>
 * public class MyDecorator extends BaseDecorator {
 *   <b>public MyDecorator(Driver decoratedDriver, TestInfo testInfo)</b> {
 *     ...
 *   }
 * }</pre>
 * </ol>
 */
public interface Decorator extends Driver {
  Driver getDecorated();
}
