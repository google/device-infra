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

import static com.google.devtools.mobileharness.infra.client.api.util.resourcefederation.ResourceFederationUtil.getServerMap;
import static com.google.devtools.mobileharness.infra.client.api.util.serverlocator.ServerLocatorUtil.createGrpcDirectStubConfiguration;
import static com.google.devtools.mobileharness.infra.client.api.util.stub.StubUtils.getLabServerGrpcTarget;
import static com.google.devtools.mobileharness.infra.client.api.util.stub.StubUtils.getTestEngineGrpcTarget;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.client.api.mode.remote.LabServerLocator;
import com.google.devtools.mobileharness.infra.client.api.proto.ResourceFederationProto.ResourceFederation;
import com.google.devtools.mobileharness.infra.client.api.proto.ResourceFederationProto.ServerResourceType;
import com.google.devtools.mobileharness.infra.client.api.proto.ServerLocatorProto.ServerLocator;
import com.google.devtools.mobileharness.infra.client.api.util.stub.GrpcStubManager;
import com.google.devtools.mobileharness.infra.client.api.util.stub.StubFactory;
import com.google.devtools.mobileharness.infra.client.api.util.stub.StubFactoryModule;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineLocator;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.ExecTestStub;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.PrepareTestStub;
import com.google.devtools.mobileharness.shared.constant.environment.MobileHarnessServerEnvironment;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.factory.FileTransferClientFactories;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.factory.FileTransferClientFactory;
import com.google.devtools.mobileharness.shared.util.comm.stub.StubConfigurationProto.StubConfiguration;
import com.google.devtools.mobileharness.shared.version.rpc.stub.VersionStub;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.wireless.qa.mobileharness.shared.model.lab.LabLocator;
import java.util.Map;
import javax.annotation.Nullable;

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

  /**
   * Gets the stub for talking to test engine server ExecTestService.
   *
   * @param labServerLocator the lab locator to find the target lab server
   * @param testEngineLocator the test engine locator to find the target test engine server
   * @param resourceFederation additional server resource to find the target test engine server.
   */
  public ExecTestStub getTestEngineExecTestStub(
      LabServerLocator labServerLocator,
      TestEngineLocator testEngineLocator,
      ResourceFederation resourceFederation) {
    ImmutableMap<ServerResourceType, ServerLocator> map = getServerMap(resourceFederation);
    if (map.containsKey(ServerResourceType.GRPC_RELAY)) {
      return getTestEngineExecTestStubWithGrpcRelay(
          labServerLocator, testEngineLocator, map.get(ServerResourceType.GRPC_RELAY));
    }
    return getTestEngineExecTestStub(
        labServerLocator, testEngineLocator, getMobileHarnessEnvironment(map));
  }

  @VisibleForTesting
  ExecTestStub getTestEngineExecTestStub(
      LabServerLocator labServerLocator,
      TestEngineLocator testEngineLocator,
      MobileHarnessServerEnvironment mhEnvironment) {
    return grpcStubManager.getExecTestGrpcStub(
        getTestEngineGrpcTarget(labServerLocator, testEngineLocator));
  }

  private ExecTestStub getTestEngineExecTestStubWithGrpcRelay(
      LabServerLocator labServerLocator,
      TestEngineLocator testEngineLocator,
      ServerLocator relayLocator) {
    // Call grpc client -> grpc relay -> lab server.
    return stubFactory.createExecTestStub(
        createGrpcDirectStubConfiguration(
            relayLocator.getGrpcServerLocator(), labServerLocator, testEngineLocator));
  }

  /**
   * Gets the stub for talking to lab server PrepareTestService.
   *
   * @param labServerLocator the lab locator to find the target lab server
   * @param resourceFederation additional server resource to find the target lab server.
   */
  public PrepareTestStub getPrepareTestStub(
      LabServerLocator labServerLocator, ResourceFederation resourceFederation) {
    ImmutableMap<ServerResourceType, ServerLocator> map = getServerMap(resourceFederation);
    if (map.containsKey(ServerResourceType.GRPC_RELAY)) {
      return getPrepareTestStubWithGrpcRelay(
          labServerLocator, map.get(ServerResourceType.GRPC_RELAY));
    }
    return getPrepareTestStub(labServerLocator, getMobileHarnessEnvironment(map));
  }

  @VisibleForTesting
  PrepareTestStub getPrepareTestStub(
      LabServerLocator labServerLocator, MobileHarnessServerEnvironment mhEnvironment) {
    return grpcStubManager.getPrepareTestStub(getLabServerGrpcTarget(labServerLocator));
  }

  private PrepareTestStub getPrepareTestStubWithGrpcRelay(
      LabServerLocator labServerLocator, ServerLocator relayLocator) {
    // Call grpc client -> grpc relay -> lab server.
    return stubFactory.createPrepareTestStub(
        createGrpcDirectStubConfiguration(relayLocator.getGrpcServerLocator(), labServerLocator));
  }

  /**
   * Gets the stub for talking to lab server VersionService.
   *
   * @param labServerLocator locator to find the targeted lab.
   * @param resourceFederation additional server resource to find the targeted lab.
   */
  public VersionStub getLabVersionStub(
      LabServerLocator labServerLocator, ResourceFederation resourceFederation) {
    ImmutableMap<ServerResourceType, ServerLocator> map = getServerMap(resourceFederation);
    if (map.containsKey(ServerResourceType.GRPC_RELAY)) {
      return getLabVersionStubWithGrpcRelay(
          labServerLocator, map.get(ServerResourceType.GRPC_RELAY));
    }
    return getLabVersionStub(labServerLocator, getMobileHarnessEnvironment(map));
  }

  @VisibleForTesting
  VersionStub getLabVersionStub(
      LabServerLocator labServerLocator, MobileHarnessServerEnvironment mhEnvironment) {
    return grpcStubManager.getVersionStub(getLabServerGrpcTarget(labServerLocator));
  }

  private VersionStub getLabVersionStubWithGrpcRelay(
      LabServerLocator labServerLocator, ServerLocator relayLocator) {
    // Call grpc client -> grpc relay -> lab server.
    return stubFactory.createVersionStub(
        createGrpcDirectStubConfiguration(relayLocator.getGrpcServerLocator(), labServerLocator));
  }

  /**
   * Gets factory for creating a client to transfer files with lab server or test engine.
   *
   * <p>In most cases (CloudRpc is enabled), if {@code testEngineLocator} is not null, the client
   * will talk to the test engine.
   */
  public FileTransferClientFactory getFileTransferClientFactory(
      LabServerLocator labServerLocator, @Nullable TestEngineLocator testEngineLocator) {
    return getFileTransferClientFactory(
        labServerLocator, testEngineLocator, MobileHarnessServerEnvironment.DEFAULT);
  }

  /**
   * Gets factory for creating a client to transfer files with lab server or test engine.
   *
   * <p>In most cases (CloudRpc is enabled), if {@code testEngineLocator} is not null, the client
   * will talk to the test engine.
   */
  public FileTransferClientFactory getFileTransferClientFactory(
      LabServerLocator labServerLocator,
      @Nullable TestEngineLocator testEngineLocator,
      MobileHarnessServerEnvironment mhEnvironment) {
    return getFileTransferClientFactoryWithDirectTarget(labServerLocator, testEngineLocator);
  }

  /** Gets factory for creating a client to transfer files with lab server or test engine. */
  public FileTransferClientFactory getFileTransferClientFactory(
      LabServerLocator labServerLocator,
      @Nullable TestEngineLocator testEngineLocator,
      ResourceFederation resourceFederation) {
    ImmutableMap<ServerResourceType, ServerLocator> map = getServerMap(resourceFederation);
    if (map.containsKey(ServerResourceType.GRPC_RELAY)) {
      return getFileTransferClientFactoryWithGrpcRelay(
          labServerLocator, testEngineLocator, map.get(ServerResourceType.GRPC_RELAY));
    }
    return getFileTransferClientFactory(
        labServerLocator, testEngineLocator, getMobileHarnessEnvironment(map));
  }

  /**
   * Gets factory for creating a client to transfer files with lab server or test engine.
   *
   * <p>In most cases (CloudRpc is enabled), if {@code testEngineLocator} is not null, the client
   * will talk to the test engine.
   *
   * <p><b>WARNING</b>: for a per-test-lab-server test, this method will return an invalid stub.
   * TODO: removes the method and all calls from MH plugins.
   */
  public FileTransferClientFactory getFileTransferClientFactory(
      LabLocator labLocator, @Nullable TestEngineLocator testEngineLocator) {
    return getFileTransferClientFactory(
        LabServerLocator.longRunningLabServer(labLocator.toNewLabLocator()), testEngineLocator);
  }

  private FileTransferClientFactory getFileTransferClientFactoryWithGrpcRelay(
      LabServerLocator labServerLocator,
      @Nullable TestEngineLocator testEngineLocator,
      ServerLocator relayLocator) {
    // Call grpc client -> grpc relay -> lab server.
    return (testEngineLocator == null)
        ? FileTransferClientFactories.fromStub(
            stubFactory.createCloudFileTransferStub(
                createGrpcDirectStubConfiguration(
                    relayLocator.getGrpcServerLocator(), labServerLocator)))
        : FileTransferClientFactories.fromStub(
            stubFactory.createCloudFileTransferStub(
                createGrpcDirectStubConfiguration(
                    relayLocator.getGrpcServerLocator(), labServerLocator, testEngineLocator)));
  }

  @SuppressWarnings("unused") // deviceinfra:internal-only
  private FileTransferClientFactory getFileTransferClientFactoryWithDirectTarget(
      LabServerLocator labServerLocator, @Nullable TestEngineLocator testEngineLocator) {
    StubConfiguration stubConfiguration =
        (testEngineLocator != null)
            ? createGrpcDirectStubConfiguration(labServerLocator, testEngineLocator)
            : createGrpcDirectStubConfiguration(labServerLocator);
    return FileTransferClientFactories.fromStub(
        stubFactory.createCloudFileTransferStub(stubConfiguration));
  }

  @VisibleForTesting
  static MobileHarnessServerEnvironment getMobileHarnessEnvironment(
      Map<ServerResourceType, ServerLocator> serverResources) {
    return MobileHarnessServerEnvironment.DEFAULT;
  }
}
