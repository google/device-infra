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

package com.google.wireless.qa.mobileharness.shared.api.annotation;

import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the constructor of a driver/decorator requires the test runner to provide data
 * model that satisfies certain constraints.
 */
@Target(ElementType.CONSTRUCTOR)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConstraintsForTesting {

  /** The constructor will cast the {@link Device} parameter to the given type. */
  Class<? extends Device> deviceClass() default Device.class;

  /**
   * The constructor needs {@link Device#getDeviceTypes()} to return the given types to make a
   * driver/decorator instantiation in unit tests succeeds.
   */
  String[] deviceTypes() default {};

  /**
   * The constructor needs {@code testInfo.jobInfo().files()} to contain the given tags to make a
   * driver/decorator instantiation in unit tests succeeds.
   */
  String[] jobFileTags() default {};

  /**
   * Whether to test instantiation of the driver/decorator in unit tests. In some cases, the
   * driver/decorator can only be instantiated in lab production environment (e.g., the constructor
   * needs credential keys), then this field can be set to {@code false}. Of course, it is not
   * recommended since the constructor can always inject a provider instead.
   */
  boolean enableInstantiationTest() default true;
}
