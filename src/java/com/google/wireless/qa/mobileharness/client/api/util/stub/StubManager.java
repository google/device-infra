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

package com.google.wireless.qa.mobileharness.client.api.util.stub;

import static com.google.devtools.mobileharness.infra.client.api.util.stub.StubUtils.getLabServerGrpcTarget;
import static com.google.devtools.mobileharness.infra.client.api.util.stub.StubUtils.getTestEngineGrpcTarget;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.mobileharness.infra.client.api.mode.remote.LabServerLocator;
import com.google.devtools.mobileharness.infra.client.api.util.stub.GrpcStubManager;
import com.google.devtools.mobileharness.infra.client.api.util.stub.StubFactory;
import com.google.devtools.mobileharness.infra.client.api.util.stub.StubFactoryModule;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineLocator;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.ExecTestStub;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.PrepareTestStub;
import com.google.devtools.mobileharness.shared.constant.environment.MobileHarnessServerEnvironment;
import com.google.devtools.mobileharness.shared.version.rpc.stub.VersionStub;
import com.google.inject.Guice;
import com.google.inject.Injector;

/** Shared master and lab server service stubs. */
public class StubManager {

  private final StubFactory stubFactory;

  private final GrpcStubManager grpcStubManager;

  /** Lazy initializer for the singleton instance. */
  private static class SingletonHolder {
    private static final StubManager singleton;

    static {
      singleton = new StubManager(GrpcStubManager.getInstance());
    }
  }

  public static StubManager getInstance() {
    return SingletonHolder.singleton;
  }

  @VisibleForTesting
  StubManager(GrpcStubManager grpcStubManager) {
    this.grpcStubManager = grpcStubManager;

    Injector injector = Guice.createInjector(new StubFactoryModule());
    stubFactory = injector.getInstance(StubFactory.class);
  }

  public ExecTestStub getTestEngineExecTestStub(
      LabServerLocator labServerLocator,
      TestEngineLocator testEngineLocator,
      MobileHarnessServerEnvironment mhEnvironment) {
    return grpcStubManager.getExecTestGrpcStub(
        getTestEngineGrpcTarget(labServerLocator, testEngineLocator));
  }

  /**
   * Gets the stub for talking to lab server PrepareTestService.
   *
   * @param labServerLocator the lab locator to find the target lab server
   */
  public PrepareTestStub getPrepareTestStub(
      LabServerLocator labServerLocator, MobileHarnessServerEnvironment mhEnvironment) {
    return grpcStubManager.getPrepareTestStub(getLabServerGrpcTarget(labServerLocator));
  }

  /**
   * Gets the stub for talking to lab server version service.
   *
   * @param labServerLocator locator to find the targeted lab
   */
  public VersionStub getLabVersionStub(
      LabServerLocator labServerLocator, MobileHarnessServerEnvironment mhEnvironment) {
    return grpcStubManager.getVersionStub(getLabServerGrpcTarget(labServerLocator));
  }
}
