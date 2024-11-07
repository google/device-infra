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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Job;
import com.google.devtools.mobileharness.api.model.proto.Job.DeviceAllocationPriority;
import com.google.devtools.mobileharness.api.model.proto.Job.DeviceRequirement;
import com.google.devtools.mobileharness.api.model.proto.Job.DeviceRequirements;
import com.google.devtools.mobileharness.api.model.proto.Job.JobFeature;
import com.google.devtools.mobileharness.api.model.proto.Test.TestIdName;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.Annotations.AtsModeAbstractScheduler;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.Annotations.JobSyncServiceVersionChecker;
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
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.checker.ServiceSideVersionChecker;
import com.google.devtools.mobileharness.shared.version.proto.Version.VersionCheckResponse;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Priority;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Timeout;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class JobSyncService extends JobSyncServiceGrpc.JobSyncServiceImplBase {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration MIN_JOB_EXPIRATION_TIME = Duration.ofMinutes(5L);
  private static final Duration JOB_CLEANUP_INTERVAL = Duration.ofMinutes(5L);

  private final AbstractScheduler scheduler;
  private final ServiceSideVersionChecker versionChecker;
  private final ListeningScheduledExecutorService scheduledThreadPool;

  /** Key is job ID. */
  private final Map<String, JobExpirationInfo> jobExpirationInfos = new ConcurrentHashMap<>();

  @Inject
  JobSyncService(
      @AtsModeAbstractScheduler AbstractScheduler scheduler,
      @JobSyncServiceVersionChecker ServiceSideVersionChecker versionChecker,
      ListeningScheduledExecutorService scheduledThreadPool) {
    this.scheduler = scheduler;
    this.versionChecker = versionChecker;
    this.scheduledThreadPool = scheduledThreadPool;
  }

  void start() {
    // Starts job cleaner.
    logFailure(
        scheduledThreadPool.scheduleWithFixedDelay(
            threadRenaming(this::cleanUpJobs, () -> "job-sync-service-job-cleaner"),
            JOB_CLEANUP_INTERVAL,
            JOB_CLEANUP_INTERVAL),
        Level.WARNING,
        "Error when cleaning up jobs");
  }

  void cleanUpJobs() {
    Iterator<Entry<String, JobExpirationInfo>> iterator = jobExpirationInfos.entrySet().iterator();
    while (iterator.hasNext()) {
      Entry<String, JobExpirationInfo> job = iterator.next();
      if (job.getValue().isExpired()) {
        logger.atInfo().log(
            "Job [%s] is expired, remove it from scheduler, expiration_info=[%s]",
            job.getKey(), job.getValue());
        scheduler.removeJob(job.getKey(), /* removeDevices= */ false);
        iterator.remove();
      }
    }
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

  private OpenJobResponse doOpenJob(OpenJobRequest request) throws MobileHarnessException {
    logger.atInfo().log("OpenJobRequest: %s", shortDebugString(request));

    versionChecker.checkStub(request.getVersionCheckRequest());

    // Creates JobScheduleUnit
    JobFeature jobFeature = request.getFeature();
    DeviceRequirements deviceRequirements = jobFeature.getDeviceRequirements();
    checkArgument(deviceRequirements.getDeviceRequirementCount() > 0);
    DeviceRequirement primaryDeviceRequirement = deviceRequirements.getDeviceRequirement(0);
    Map<String, String> primaryDeviceDimensions = primaryDeviceRequirement.getDimensionsMap();
    Timing masterTiming = new Timing();
    JobLocator jobLocator = new JobLocator(request.getId(), request.getName());
    JobScheduleUnit jobScheduleUnit =
        new JobScheduleUnit(
            jobLocator,
            jobFeature.getUser(),
            JobType.newBuilder()
                .setDevice(primaryDeviceRequirement.getDeviceType())
                .setDriver(jobFeature.getDriver())
                .addAllDecorator(Lists.reverse(primaryDeviceRequirement.getDecoratorList()))
                .build(),
            createJobSetting(request.getSetting(), jobFeature.getDeviceAllocationPriority()),
            masterTiming);
    jobScheduleUnit.params().addAll(request.getParamMap());
    jobScheduleUnit.dimensions().addAll(primaryDeviceDimensions);
    jobScheduleUnit.subDeviceSpecs().getSubDevice(0).dimensions().addAll(primaryDeviceDimensions);
    deviceRequirements.getDeviceRequirementList().stream()
        .skip(1L)
        .forEach(
            secondaryDeviceRequirement ->
                jobScheduleUnit
                    .subDeviceSpecs()
                    .addSubDevice(
                        secondaryDeviceRequirement.getDeviceType(),
                        secondaryDeviceRequirement.getDimensionsMap(),
                        secondaryDeviceRequirement.getDecoratorList()));
    jobScheduleUnit
        .subDeviceSpecs()
        .addSharedDimensionNames(deviceRequirements.getSharedDimensionList());

    // Creates TestScheduleUnit.
    ImmutableList<TestScheduleUnit> testScheduleUnits =
        request.getTestList().stream()
            .map(
                testIdName ->
                    new TestScheduleUnit(
                        new TestLocator(testIdName.getId(), testIdName.getName(), jobLocator),
                        masterTiming))
            .collect(toImmutableList());

    // Adds job and tests to scheduler if they don't exist.
    jobExpirationInfos.putIfAbsent(
        jobLocator.getId(),
        JobExpirationInfo.create(
            createJobExpirationTime(Duration.ofMillis(request.getKeepAliveTimeoutMs()))));
    scheduler.addJob(jobScheduleUnit);
    for (TestScheduleUnit testScheduleUnit : testScheduleUnits) {
      scheduler.addTest(testScheduleUnit);
    }

    return OpenJobResponse.newBuilder()
        .setVersionCheckResponse(
            VersionCheckResponse.newBuilder()
                .setServiceVersion(Version.MASTER_V5_VERSION.toString()))
        .build();
  }

  private static JobSetting createJobSetting(
      Job.JobSetting jobSetting, DeviceAllocationPriority deviceAllocationPriority) {
    return JobSetting.newBuilder()
        .setTimeout(createJobTimeout(jobSetting.getTimeout()))
        .setRetry(jobSetting.getRetry())
        .setPriority(createPriority(jobSetting.getPriority(), deviceAllocationPriority))
        .build();
  }

  private static Timeout createJobTimeout(Job.Timeout jobTimeout) {
    Timeout.Builder result = Timeout.newBuilder();
    if (jobTimeout.getJobTimeoutMs() != 0L) {
      result.setJobTimeoutMs(jobTimeout.getJobTimeoutMs());
    }
    if (jobTimeout.getTestTimeoutMs() != 0L) {
      result.setTestTimeoutMs(jobTimeout.getTestTimeoutMs());
    }
    if (jobTimeout.getStartTimeoutMs() != 0L) {
      result.setStartTimeoutMs(jobTimeout.getStartTimeoutMs());
    }
    return result.build();
  }

  private static Priority createPriority(
      Job.Priority jobPriority, DeviceAllocationPriority deviceAllocationPriority) {
    switch (deviceAllocationPriority) {
      case DEVICE_ALLOCATION_PRIORITY_INTERACTIVE:
        return Priority.MAX;
      case DEVICE_ALLOCATION_PRIORITY_LOW:
        return Priority.LOW;
      default:
        return Priority.forNumber(jobPriority.getNumber());
    }
  }

  private static Duration createJobExpirationTime(Duration clientJobExpirationTime) {
    if (clientJobExpirationTime.compareTo(MIN_JOB_EXPIRATION_TIME) < 0) {
      logger.atInfo().log(
          "Client side job expiration time is less than %s, use %s instead",
          MIN_JOB_EXPIRATION_TIME, MIN_JOB_EXPIRATION_TIME);
      return MIN_JOB_EXPIRATION_TIME;
    } else {
      return clientJobExpirationTime;
    }
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
    heartbeatJob(request.getJobId());

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
    heartbeatJob(request.getJobId());

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
    heartbeatJob(request.getJobId());

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
    jobExpirationInfos.remove(request.getJobId());

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

  private void heartbeatJob(String jobId) {
    jobExpirationInfos.computeIfPresent(
        jobId, (id, jobExpirationInfo) -> jobExpirationInfo.heartbeat());
  }

  @AutoValue
  abstract static class JobExpirationInfo {

    abstract Duration expirationTime();

    abstract Instant lastHeartbeatTimestamp();

    private static JobExpirationInfo create(Duration expirationTime) {
      return new AutoValue_JobSyncService_JobExpirationInfo(expirationTime, Instant.now());
    }

    private JobExpirationInfo heartbeat() {
      return new AutoValue_JobSyncService_JobExpirationInfo(expirationTime(), Instant.now());
    }

    private boolean isExpired() {
      return lastHeartbeatTimestamp().plus(expirationTime()).isBefore(Instant.now());
    }
  }
}
