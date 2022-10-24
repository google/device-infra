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
 * Decorator for adding additional functionality to drivers. Don't forget to call the
 * setUp/run/reset/tearDown methods of {@link Decorator#getDecorated()} in the overrided methods.
 *
 * <p>A decorator could be constructed in one of the following two ways by {@linkplain
 * com.google.wireless.qa.mobileharness.shared.api.driver.DriverFactory DriverFactory} in
 * {@linkplain com.google.devtools.mobileharness.infra.controller.test.local.LocalDirectTestRunner
 * LocalTestRunner}:
 *
 * <ol>
 *   <li>Invoking its constructor with two parameters {@link Driver} and {@linkplain
 *       com.google.wireless.qa.mobileharness.shared.model.job.TestInfo TestInfo} if it does not
 *       have a module.
 *       <p>The following is an example:
 *       <pre>
 * public class MyDecorator extends BaseDecorator {
 *   <b>public MyDecorator(Driver driver, TestInfo testInfo)</b> {
 *     ...
 *   }
 *   ...
 * }</pre>
 *   <li>Dependency injection with its module if it has one.
 *       <p>The following is an example:
 *       <pre>
 * public class MyDecorator extends BaseDecorator {
 *   <b>&#064;Inject</b>
 *   <b>public MyDecorator(&#064;Assisted Driver driver, &#064;Assisted TestInfo testInfo,
 *          OtherParam1 otherParam1, OtherParam2 otherParam2, OtherParam3 ...)</b> {
 *     ...
 *   }
 *   ...
 * }</pre>
 *       <p>Additionally, in this way, you need to write a {@linkplain com.google.inject.Module
 *       Module} in {@link com.google.wireless.qa.mobileharness.shared.api.module} for the decorator
 *       with the name "{@code <your-decorator-name>Module}". For example:
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
 * </ol>
 */
public interface Decorator extends Driver {
  Driver getDecorated();
}
