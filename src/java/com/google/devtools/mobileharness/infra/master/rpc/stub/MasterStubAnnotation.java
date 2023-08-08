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

package com.google.devtools.mobileharness.infra.master.rpc.stub;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Annotations used in the RPC stub module for talking Mobile Harness Master. */
public final class MasterStubAnnotation {

  /** Annotation used to identify which implementation should bind to master grpc stub. */
  @BindingAnnotation
  @Retention(RetentionPolicy.RUNTIME)
  public @interface GrpcStub {}

  /** Annotation used to identify which implementation should bind to master stubby stub. */
  @BindingAnnotation
  @Retention(RetentionPolicy.RUNTIME)
  public @interface StubbyStub {}

  private MasterStubAnnotation() {}
}
