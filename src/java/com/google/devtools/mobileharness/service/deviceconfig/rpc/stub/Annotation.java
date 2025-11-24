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

package com.google.devtools.mobileharness.service.deviceconfig.rpc.stub;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Annotations used in the RPC stub package of the device config service. */
public final class Annotation {

  /** Annotation used to identify which implementation should bind to device config stubby stubs. */
  @BindingAnnotation
  @Retention(RetentionPolicy.RUNTIME)
  public @interface DeviceConfigStubbyStub {}

  /** Annotation used to identify which implementation should bind to device config GRPC stubs. */
  @BindingAnnotation
  @Retention(RetentionPolicy.RUNTIME)
  public @interface DeviceConfigGrpcStub {}

  private Annotation() {}
}
