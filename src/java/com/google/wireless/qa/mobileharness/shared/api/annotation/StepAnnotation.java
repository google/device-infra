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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Flag annotation which indicates that a field is a step, which has {@linkplain ParamAnnotation
 * parameter} or {@linkplain FileAnnotation file} specs which will be parsed for a {@linkplain
 * com.google.wireless.qa.mobileharness.shared.api.device.Device device}/{@linkplain com.google
 * .wireless.qa.mobileharness.shared.api.driver.Driver driver}/{@linkplain
 * com.google.wireless.qa.mobileharness.shared.api .decorator.Decorator decorator}, has {@linkplain
 * com.google.wireless.qa.mobileharness .shared.model.job.JobInfo job}/{@linkplain
 * com.google.wireless.qa.mobileharness.shared.api.device.Device environment} validator methods and
 * can receive {@linkplain
 * com.google.wireless.qa.mobileharness.shared.comm.message.event.TestMessageEvent test messages}.
 *
 * <p>A step of a device/driver/decorator is a field annotated by {@link StepAnnotation} with any
 * visibility, which is declared in the device/driver/decorator or its superclasses/interfaces, or
 * declared in another step of the device/driver/decorator.
 *
 * @see ParamAnnotation
 * @see FileAnnotation
 * @see ValidatorAnnotation
 * @see com.google.wireless.qa.mobileharness.shared.comm.message.event.TestMessageEvent
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StepAnnotation {}
