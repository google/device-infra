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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Test.TestIdName;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.Annotations.AtsModeAbstractScheduler;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler.JobWithTests;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler.JobsAndAllocations;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceGrpc;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.AddExtraTestsRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.AddExtraTestsResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.CheckJobsRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.CheckJobsResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.CloseJobRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.CloseJobResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.CloseTestRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.CloseTestResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.GetAllocationsRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.GetAllocationsResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.GetAllocationsResponse.Allocation;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.KillJobRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.KillJobResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.OpenJobRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.OpenJobResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.UpsertDeviceTempRequiredDimensionsRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.UpsertDeviceTempRequiredDimensionsResponse;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestScheduleUnit;
import io.grpc.stub.StreamObserver;
import java.util.Map.Entry;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class JobSyncService extends JobSyncServiceGrpc.JobSyncServiceImplBase {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AbstractScheduler scheduler;

  @Inject
  JobSyncService(@AtsModeAbstractScheduler AbstractScheduler scheduler) {
    this.scheduler = scheduler;
  }

  @Override
  public void openJob(OpenJobRequest request, StreamObserver<OpenJobResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doOpenJob,
        JobSyncServiceGrpc.getServiceDescriptor(),
        JobSyncServiceGrpc.getOpenJobMethod());
  }

  private OpenJobResponse doOpenJob(OpenJobRequest request) {
    logger.atInfo().log("OpenJobRequest: %s", shortDebugString(request));
    // TODO: Implements it.
    throw new UnsupportedOperationException();
  }

  @Override
  public void addExtraTests(
      AddExtraTestsRequest request, StreamObserver<AddExtraTestsResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doAddExtraTests,
        JobSyncServiceGrpc.getServiceDescriptor(),
        JobSyncServiceGrpc.getAddExtraTestsMethod());
  }

  private AddExtraTestsResponse doAddExtraTests(AddExtraTestsRequest request)
      throws MobileHarnessException {
    logger.atInfo().log("AddExtraTestsRequest: %s", shortDebugString(request));

    // Job name will be ignored by addTest().
    JobLocator jobLocator = new JobLocator(request.getJobId(), /* name= */ "");
    for (TestIdName testIdName : request.getTestList()) {
      // Ignores whether the test has already been added.
      if (!scheduler.addTest(
          new TestScheduleUnit(
              new TestLocator(testIdName.getId(), testIdName.getName(), jobLocator)))) {
        logger.atInfo().log("Test [%s] has been added, ignored", shortDebugString(testIdName));
      }
    }

    return AddExtraTestsResponse.getDefaultInstance();
  }

  @Override
  public void getAllocations(
      GetAllocationsRequest request, StreamObserver<GetAllocationsResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doGetAllocations,
        JobSyncServiceGrpc.getServiceDescriptor(),
        JobSyncServiceGrpc.getGetAllocationsMethod());
  }

  private GetAllocationsResponse doGetAllocations(GetAllocationsRequest request) {
    // Queries a snapshot from scheduler.
    JobsAndAllocations jobsAndAllocations = scheduler.getJobsAndAllocations();

    // Creates result.
    ImmutableSet<String> clientTestIds = ImmutableSet.copyOf(request.getTestIdList());
    ImmutableMap<String, Allocation> testAllocations =
        jobsAndAllocations.testAllocations().entrySet().stream()
            .filter(entry -> clientTestIds.contains(entry.getKey()))
            .collect(
                toImmutableMap(
                    Entry::getKey,
                    entry ->
                        Allocation.newBuilder()
                            .setTestId(entry.getValue().getTest().id())
                            .addAllDeviceLocator(
                                entry.getValue().getAllDevices().stream()
                                    .map(DeviceLocator::toProto)
                                    .collect(toImmutableList()))
                            .build()));
    ImmutableSet<String> allocatingAndAllocatedTestIds =
        Optional.ofNullable(jobsAndAllocations.jobsWithTests().get(request.getJobId()))
            .map(JobWithTests::tests)
            .map(ImmutableMap::keySet)
            .orElse(ImmutableSet.of());
    SetView<String> badTestIds = Sets.difference(clientTestIds, allocatingAndAllocatedTestIds);
    SetView<String> allocatingTestIds =
        Sets.difference(allocatingAndAllocatedTestIds, testAllocations.keySet());

    // TODO: stats, quota_result, is_being_killed, suspended_test_id.
    return GetAllocationsResponse.newBuilder()
        .addAllAllocation(testAllocations.values())
        .addAllBadTestId(badTestIds)
        .addAllAllocatingTestId(allocatingTestIds)
        .build();
  }

  @Override
  public void closeTest(
      CloseTestRequest request, StreamObserver<CloseTestResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doCloseTest,
        JobSyncServiceGrpc.getServiceDescriptor(),
        JobSyncServiceGrpc.getCloseTestMethod());
  }

  private CloseTestResponse doCloseTest(CloseTestRequest request) {
    logger.atInfo().log("CloseTestRequest: %s", shortDebugString(request));

    // Test name and job name will be ignored.
    scheduler.unallocate(
        new TestLocator(
            request.getTestId(),
            /* name= */ "",
            new JobLocator(request.getJobId(), /* name= */ "")),
        /* removeDevices= */ false,
        /* closeTest= */ true);
    // TODO: device_usage_duration.
    return CloseTestResponse.getDefaultInstance();
  }

  @Override
  public void closeJob(CloseJobRequest request, StreamObserver<CloseJobResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doCloseJob,
        JobSyncServiceGrpc.getServiceDescriptor(),
        JobSyncServiceGrpc.getCloseJobMethod());
  }

  private CloseJobResponse doCloseJob(CloseJobRequest request) {
    logger.atInfo().log("CloseJobRequest: %s", shortDebugString(request));

    scheduler.removeJob(request.getJobId(), /* removeDevices= */ false);
    return CloseJobResponse.getDefaultInstance();
  }

  @Override
  public void checkJobs(
      CheckJobsRequest request, StreamObserver<CheckJobsResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doCheckJobs,
        JobSyncServiceGrpc.getServiceDescriptor(),
        JobSyncServiceGrpc.getCheckJobsMethod());
  }

  private CheckJobsResponse doCheckJobs(CheckJobsRequest request) {
    ImmutableSet<String> jobIds = scheduler.getJobsAndAllocations().jobsWithTests().keySet();
    return CheckJobsResponse.newBuilder()
        .addAllJobId(Sets.intersection(jobIds, ImmutableSet.copyOf(request.getJobIdList())))
        .build();
  }

  @Override
  public void upsertDeviceTempRequiredDimensions(
      UpsertDeviceTempRequiredDimensionsRequest request,
      StreamObserver<UpsertDeviceTempRequiredDimensionsResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doUpsertDeviceTempRequiredDimensions,
        JobSyncServiceGrpc.getServiceDescriptor(),
        JobSyncServiceGrpc.getUpsertDeviceTempRequiredDimensionsMethod());
  }

  private UpsertDeviceTempRequiredDimensionsResponse doUpsertDeviceTempRequiredDimensions(
      UpsertDeviceTempRequiredDimensionsRequest request) {
    // TODO: Implements it.
    throw new UnsupportedOperationException();
  }

  @Override
  public void killJob(KillJobRequest request, StreamObserver<KillJobResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doKillJob,
        JobSyncServiceGrpc.getServiceDescriptor(),
        JobSyncServiceGrpc.getKillJobMethod());
  }

  private KillJobResponse doKillJob(KillJobRequest request) {
    // TODO: Implements it.
    throw new UnsupportedOperationException();
  }
}
