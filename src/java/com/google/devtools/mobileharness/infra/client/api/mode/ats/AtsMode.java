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

package com.google.devtools.mobileharness.infra.client.api.mode.ats;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.DeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.Annotations.AtsModeAbstractScheduler;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.Annotations.AtsModeDeviceQuerier;
import com.google.devtools.mobileharness.infra.client.api.mode.local.LocalDeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.mode.local.LocalDeviceAllocator.DeviceVerifier;
import com.google.devtools.mobileharness.infra.client.api.mode.local.LocalDeviceAllocator.EmptyDeviceVerifier;
import com.google.devtools.mobileharness.infra.client.api.mode.remote.RemoteTestRunner;
import com.google.devtools.mobileharness.infra.client.api.proto.ResourceFederationProto.ResourceFederation;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.ServiceProvider;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunnerSetting;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver;
import com.google.devtools.mobileharness.shared.labinfo.LabInfoService;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import io.grpc.BindableService;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * ATS mode which supports allocating devices from multiple hosts in an in-memory scheduler.
 *
 * <p>The {@link #initialize} method much be called before it is used.
 */
@Singleton
public class AtsMode implements ExecMode, ServiceProvider {

  private final RemoteDeviceManager remoteDeviceManager;
  private final DeviceQuerier deviceQuerier;
  private final AbstractScheduler scheduler;
  private final ListeningExecutorService threadPool;
  private final LabInfoService labInfoService;
  private final LabRecordManager labRecordManager;
  private final LabRecordService labRecordService;
  private final JobSyncService jobSyncService;
  private final DeviceVerifier deviceVerifier = new EmptyDeviceVerifier();

  @Inject
  AtsMode(
      RemoteDeviceManager remoteDeviceManager,
      @AtsModeDeviceQuerier DeviceQuerier deviceQuerier,
      @AtsModeAbstractScheduler AbstractScheduler scheduler,
      ListeningExecutorService threadPool,
      LabInfoService labInfoService,
      LabRecordManager labRecordManager,
      LabRecordService labRecordService,
      JobSyncService jobSyncService) {
    this.remoteDeviceManager = remoteDeviceManager;
    this.deviceQuerier = deviceQuerier;
    this.scheduler = scheduler;
    this.threadPool = threadPool;
    this.labInfoService = labInfoService;
    this.labRecordManager = labRecordManager;
    this.labRecordService = labRecordService;
    this.jobSyncService = jobSyncService;
  }

  @Override
  public void initialize(EventBus globalInternalBus) {
    remoteDeviceManager.start();
    scheduler.start();
    labRecordManager.start();
    jobSyncService.start();
  }

  @Override
  public DeviceAllocator createDeviceAllocator(JobInfo jobInfo, EventBus globalInternalBus) {
    return new LocalDeviceAllocator(
        jobInfo,
        deviceVerifier,
        threadPool,
        /* proxyDeviceManager= */ null, // RemoteTestRunner does not use proxied devices
        immediateFuture(scheduler));
  }

  @Override
  public DeviceQuerier createDeviceQuerier() {
    return deviceQuerier;
  }

  @Override
  public DirectTestRunner createTestRunner(
      DirectTestRunnerSetting setting,
      ListeningExecutorService threadPool,
      FileResolver fileResolver)
      throws MobileHarnessException {
    return new RemoteTestRunner(
        setting,
        threadPool,
        ResourceFederation.getDefaultInstance(), // The resource federation is empty.
        /* supportImpersonation= */ false,
        fileResolver);
  }

  @Override
  public ImmutableList<BindableService> provideServices() {
    return ImmutableList.of(labInfoService, labRecordService, jobSyncService);
  }

  @Override
  public ImmutableList<BindableService> provideServicesForWorkers() {
    return ImmutableList.of(remoteDeviceManager.getLabSyncService());
  }

  @Override
  public ImmutableList<BindableService> provideDualModeServices() {
    return ImmutableList.of();
  }
}
