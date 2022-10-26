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

package com.google.devtools.mobileharness.infra.container.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a class is process-incompatible for Mobile Harness container.
 *
 * <p>A class is process-incompatible means two instances of the class in different processes can
 * not work normally together as they do in one process.
 *
 * <p>There are many reasons that can make a class process-incompatible, included but not limited
 * to:
 *
 * <ol>
 *   <li>The class contains a static/non-static <b>stateful</b> field/lock which will be used by
 *       instances from <b>different</b> processes and the field is not based on Mobile Harness
 *       container state synchronization mechanism. For example:
 *       <ol>
 *         <li>A subclass of Device contains a non-final integer which will be set and get by both
 *             setUp() and postRunTest() methods. (setUp() will be invoked in lab server and
 *             postRunTest() will be invoked in container)
 *         <li>Fastboot uses a static semaphore to control device flashing concurrency.
 *         <li>RecoveryRepo uses a static map to cache downloaded GCS file paths.
 *       </ol>
 *   <li>The class contains a field which is process-incompatible. (e.g., AndroidRealDevice contains
 *       Fastboot)
 *   <li>The class needs to be a global singleton but it is only a in-process singleton. (e.g., Adb
 *       does ADB initialization work in its lazy initialization method)
 * </ol>
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ProcessIncompatible {

  /**
   * @return the reasons why the class is process-incompatible, such as field names
   */
  String[] value() default {};
}
