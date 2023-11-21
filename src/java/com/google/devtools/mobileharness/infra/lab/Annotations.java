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

package com.google.devtools.mobileharness.infra.lab;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Binding annotations for UTRS */
class Annotations {

  private Annotations() {}

  /** Annotation for binding main thread pool. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @BindingAnnotation
  @interface MainThreadPool {}

  /** Annotation for binding device manager thread pool. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @BindingAnnotation
  @interface DeviceManagerThreadPool {}

  /** Annotation for binding LabServer thread pool. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @BindingAnnotation
  @interface LabServerRpcThreadPool {}

  /** Annotation for binding local gRpc thread pool. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @BindingAnnotation
  @interface LocalGrpcThreadPool {}

  /** Annotation for binding debug thread pool. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @BindingAnnotation
  @interface DebugThreadPool {}

  /** Annotation for binding file resolver thread pool. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @BindingAnnotation
  public @interface FileResolverThreadPool {}

  /** Annotation for binding RPC port flag value. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @BindingAnnotation
  @interface RpcPort {}

  /** Annotation for binding GlobalInternalBus. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @BindingAnnotation
  public @interface GlobalEventBus {}

  /** Annotation for binding SandboxUtil. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @BindingAnnotation
  public @interface Sandbox {}

  /** Annotation for binding cloud rpc DNS address. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @BindingAnnotation
  public @interface CloudRpcDnsAddress {}

  /** Annotation for binding cloud rpc shard name. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @BindingAnnotation
  public @interface CloudRpcShardName {}

  /** Annotation for binding lab rpc port. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @BindingAnnotation
  public @interface LabRpcPort {}

  /** Annotation for binding flag for enable/disable stubby rpc. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @BindingAnnotation
  public @interface ServViaStubby {}

  /** Annotation for binding flag for enable/disable cloud rpc. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @BindingAnnotation
  public @interface ServViaCloudRpc {}

  /** Annotation for binding device runner. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @BindingAnnotation
  public @interface DeviceRunner {}
}
