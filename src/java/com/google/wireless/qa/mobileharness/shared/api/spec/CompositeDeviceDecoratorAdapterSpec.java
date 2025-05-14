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

package com.google.wireless.qa.mobileharness.shared.api.spec;

import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;

/** Specs for CompositeDeviceDecoratorAdapter. */
@SuppressWarnings("InterfaceWithOnlyStatics") // This interface is implemented by some decorators
public interface CompositeDeviceDecoratorAdapterSpec {

  @ParamAnnotation(
      required = true,
      help =
          "The list of subdecorators to invoke for every managed device within a"
              + " CompositeDevice, separated by +")
  public static final String PARAM_COMPOSITE_DEVICE_SUBDECORATORS =
      "composite_device_subdecorators";
}
