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

package com.google.devtools.mobileharness.infra.master.rpc.stub.grpc;

import com.google.devtools.mobileharness.infra.master.rpc.stub.JobSyncStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.MasterStubAnnotation.GrpcStub;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/** The Guice module to provide the grpc stubs used for calling master job sync service APIs. */
public class JobSyncGrpcStubModule extends AbstractModule {

  @Override
  protected void configure() {

    bind(JobSyncStub.class)
        .annotatedWith(GrpcStub.class)
        .to(JobSyncGrpcStub.class)
        .in(Singleton.class);
  }
}
