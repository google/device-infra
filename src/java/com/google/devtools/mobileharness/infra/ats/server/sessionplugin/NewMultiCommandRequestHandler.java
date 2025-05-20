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

package com.google.devtools.mobileharness.infra.ats.server.sessionplugin;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Throwables.getStackTraceAsString;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.shared.util.error.MoreThrowables.shortDebugString;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toJavaDuration;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Predicate.not;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.Files;
import com.google.devtools.deviceinfra.shared.util.file.remote.constant.RemoteFileType;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.SessionResultHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName.Job;
import com.google.devtools.mobileharness.infra.ats.common.XtsTypeLoader;
import com.google.devtools.mobileharness.infra.ats.common.jobcreator.XtsJobCreator;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.ShardingMode;
import com.google.devtools.mobileharness.infra.ats.console.command.parser.CommandLineParser;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandInfo;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandInfo.DeviceDimension;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.ErrorReason;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.NewMultiCommandRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail.RequestState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestContext;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestResource;
import com.google.devtools.mobileharness.infra.ats.server.util.AtsServerSessionUtil;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.lab.common.dir.DirUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfo.TradefedInvocation;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoFileUtil;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoFileUtil.XtsTradefedRuntimeInfoFileDetail;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Handler for ATS server's create test jobs request. */
final class NewMultiCommandRequestHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Timeout setting for slow commands. */
  private static final Duration SLOW_CMD_TIMEOUT = Duration.ofMinutes(10);

  private static final Duration UNZIP_TIMEOUT = Duration.ofHours(1);

  private static final DateTimeFormatter TIMESTAMP_DIR_NAME_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu.MM.dd_HH.mm.ss.SSS").withZone(ZoneId.systemDefault());
  private static final String OUTPUT_MANIFEST_FILE_NAME = "FILES";
  private static final Pattern ANDROID_XTS_ZIP_FILENAME_REGEX =
      Pattern.compile("android-[a-z]+\\.zip");
  @VisibleForTesting static final String XTS_TF_JOB_PROP = "xts-tradefed-job";
  private static final String ACLOUD_FILENAME = "acloud_prebuilt";

  @VisibleForTesting
  static final String REQUEST_ERROR_MESSAGE_FOR_TRADEFED_INVOCATION_ERROR =
      "Tradefed invocation had an error.";

  private static final ImmutableSet<ErrorId> INVALID_RESOURCE_ERROR_IDS =
      ImmutableSet.of(
          BasicErrorId.LOCAL_MOUNT_ZIP_TO_DIR_ERROR,
          BasicErrorId.LOCAL_FILE_UNZIP_ERROR,
          InfraErrorId.ATS_SERVER_INVALID_TEST_RESOURCE);

  private final SessionRequestHandlerUtil sessionRequestHandlerUtil;
  private final SessionResultHandlerUtil sessionResultHandlerUtil;
  private final LocalFileUtil localFileUtil;
  private final XtsTradefedRuntimeInfoFileUtil xtsTradefedRuntimeInfoFileUtil;
  private final CommandExecutor commandExecutor;
  private final Clock clock;
  private final XtsTypeLoader xtsTypeLoader;
  private final XtsJobCreator xtsJobCreator;
  private final Sleeper sleeper;
  private final AtsServerSessionUtil atsServerSessionUtil;

  // Cache for storing validated sessionRequestInfo for each command generated by addTradefedJobs(),
  // and mainly used by addNonTradefedJobs() to reduce duplicate sessionRequestInfo generation that
  // involves time-consuming file I/O.
  private final ConcurrentHashMap<CommandInfo, SessionRequestInfo> sessionRequestInfoCache =
      new ConcurrentHashMap<>();

  private volatile String mountedXtsRootDir = "";

  @Inject
  NewMultiCommandRequestHandler(
      SessionRequestHandlerUtil sessionRequestHandlerUtil,
      SessionResultHandlerUtil sessionResultHandlerUtil,
      LocalFileUtil localFileUtil,
      XtsTradefedRuntimeInfoFileUtil xtsTradefedRuntimeInfoFileUtil,
      CommandExecutor commandExecutor,
      Clock clock,
      XtsTypeLoader xtsTypeLoader,
      XtsJobCreator xtsJobCreator,
      Sleeper sleeper,
      AtsServerSessionUtil atsServerSessionUtil) {
    this.sessionRequestHandlerUtil = sessionRequestHandlerUtil;
    this.sessionResultHandlerUtil = sessionResultHandlerUtil;
    this.localFileUtil = localFileUtil;
    this.xtsTradefedRuntimeInfoFileUtil = xtsTradefedRuntimeInfoFileUtil;
    this.commandExecutor = commandExecutor;
    this.clock = clock;
    this.xtsTypeLoader = xtsTypeLoader;
    this.xtsJobCreator = xtsJobCreator;
    this.sleeper = sleeper;
    this.atsServerSessionUtil = atsServerSessionUtil;
  }

  CreateJobsResult createTradefedJobs(NewMultiCommandRequest request, SessionInfo sessionInfo)
      throws InterruptedException {
    if (request.getCommandsList().isEmpty()) {
      return CreateJobsResult.of(
          RequestState.ERROR,
          ErrorReason.INVALID_REQUEST,
          "COMMAND_NOT_AVAILABLE",
          ImmutableMap.of(),
          ImmutableList.of());
    }
    ImmutableList.Builder<JobInfo> jobInfoBuilder = ImmutableList.builder();
    ImmutableMap.Builder<String, CommandDetail> commandDetailsBuilder = ImmutableMap.builder();
    for (CommandInfo commandInfo : request.getCommandsList()) {
      try {
        ImmutableList<JobInfo> jobInfos =
            createXtsTradefedTestJob(request, commandInfo, sessionInfo, commandDetailsBuilder);
        jobInfoBuilder.addAll(jobInfos);
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to create tradefed jobs for command [%s]. Interrupt the session [%s].",
            commandInfo.getCommandLine(), sessionInfo.getSessionId());
        ErrorReason errorReason =
            INVALID_RESOURCE_ERROR_IDS.contains(e.getErrorId())
                ? ErrorReason.INVALID_RESOURCE
                : ErrorReason.INVALID_REQUEST;
        return CreateJobsResult.of(
            RequestState.ERROR,
            errorReason,
            String.format(
                "INVALID_COMMAND_%s with error: %s",
                commandInfo.getCommandLine(), shortDebugString(e)),
            commandDetailsBuilder.buildKeepingLast(),
            jobInfoBuilder.build());
      }
    }
    return CreateJobsResult.of(
        RequestState.RUNNING,
        null,
        null,
        commandDetailsBuilder.buildKeepingLast(),
        jobInfoBuilder.build());
  }

  CreateJobsResult createNonTradefedJobs(NewMultiCommandRequest request, SessionInfo sessionInfo)
      throws InterruptedException {
    if (request.getCommandsList().isEmpty()) {
      return CreateJobsResult.of(
          RequestState.ERROR,
          ErrorReason.INVALID_REQUEST,
          "COMMAND_NOT_AVAILABLE",
          ImmutableMap.of(),
          ImmutableList.of());
    }

    ImmutableList.Builder<JobInfo> jobInfoBuilder = ImmutableList.builder();
    ImmutableMap.Builder<String, CommandDetail> commandDetailsBuilder = ImmutableMap.builder();
    for (CommandInfo commandInfo : request.getCommandsList()) {
      try {
        jobInfoBuilder.addAll(
            createNonTradefedJobs(request, commandInfo, sessionInfo, commandDetailsBuilder));
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to create non-tradefed jobs for command [%s]. Interrupt the session [%s].",
            commandInfo.getCommandLine(), sessionInfo.getSessionId());
        ErrorReason errorReason =
            INVALID_RESOURCE_ERROR_IDS.contains(e.getErrorId())
                ? ErrorReason.INVALID_RESOURCE
                : ErrorReason.INVALID_REQUEST;
        return CreateJobsResult.of(
            RequestState.ERROR,
            errorReason,
            String.format(
                "INVALID_COMMAND_%s with error: %s",
                commandInfo.getCommandLine(), shortDebugString(e)),
            commandDetailsBuilder.buildKeepingLast(),
            jobInfoBuilder.build());
      }
    }
    return CreateJobsResult.of(
        RequestState.RUNNING,
        null,
        null,
        commandDetailsBuilder.buildKeepingLast(),
        jobInfoBuilder.build());
  }

  private ImmutableList<JobInfo> createNonTradefedJobs(
      NewMultiCommandRequest request,
      CommandInfo commandInfo,
      SessionInfo sessionInfo,
      ImmutableMap.Builder<String, CommandDetail> commandDetailsBuilder)
      throws InterruptedException, MobileHarnessException {
    SessionRequestInfo sessionRequestInfo =
        getSessionRequestInfo(request, commandInfo, sessionInfo);
    sessionRequestInfo = sessionRequestHandlerUtil.addNonTradefedModuleInfo(sessionRequestInfo);

    SetMultimap<String, String> commandToJobsMap = getCommandToJobsMap(sessionInfo);

    ImmutableList<JobInfo> jobInfos;
    try {
      jobInfos = xtsJobCreator.createXtsNonTradefedJobs(sessionRequestInfo);
    } catch (MobileHarnessException e) {
      if (XtsJobCreator.isSkippableException(e)) {
        logger.atInfo().log(
            "Unable to create non-tradefed jobs for command [%s] due to skippable exception: [%s].",
            commandInfo.getCommandLine(), shortDebugString(e));
        return ImmutableList.of();
      }
      throw e;
    }

    Optional<CommandDetail.Builder> commandDetail;
    String commandId = getCommandId(commandInfo, request);
    if (!commandToJobsMap.containsKey(commandId)) {
      commandDetail =
          Optional.of(
              CommandDetail.newBuilder()
                  .addAllDeviceSerials(sessionRequestInfo.deviceSerials())
                  .setCommandLine(commandInfo.getCommandLine())
                  .setOriginalCommandInfo(commandInfo)
                  .setCreateTime(Timestamps.fromMillis(clock.millis()))
                  .setStartTime(Timestamps.fromMillis(clock.millis()))
                  .setUpdateTime(Timestamps.fromMillis(clock.millis()))
                  .setRequestId(sessionInfo.getSessionId())
                  .setCommandAttemptId(getCommandAttemptId(commandId, sessionInfo.getSessionId()))
                  .setId(commandId)
                  .setState(CommandState.RUNNING));
    } else {
      commandDetail = Optional.empty();
    }

    for (JobInfo jobInfo : jobInfos) {
      try {
        reformatResourcePathForNonTradefedJob(jobInfo);
      } catch (MobileHarnessException e) {
        commandDetail.ifPresent(
            builder -> {
              builder.setState(CommandState.ERROR);
              commandDetailsBuilder.put(commandId, builder.build());
            });
        throw e;
      }
      jobInfo.properties().add(XtsPropertyName.Job.XTS_COMMAND_ID, commandId);
    }
    commandDetail.ifPresent(builder -> commandDetailsBuilder.put(commandId, builder.build()));
    return jobInfos;
  }

  private static SetMultimap<String, String> getCommandToJobsMap(SessionInfo sessionInfo) {
    SetMultimap<String, String> commandToJobsMap = HashMultimap.create();

    for (JobInfo jobInfo : sessionInfo.getAllJobs()) {
      jobInfo
          .properties()
          .getOptional(XtsPropertyName.Job.XTS_COMMAND_ID)
          .ifPresent(commandId -> commandToJobsMap.put(commandId, jobInfo.locator().getId()));
    }
    return commandToJobsMap;
  }

  private void reformatResourcePathForNonTradefedJob(JobInfo jobInfo)
      throws MobileHarnessException, InterruptedException {
    ImmutableMultimap<String, String> resources = jobInfo.files().getAll();
    for (String tag : resources.keySet()) {
      ImmutableCollection<String> files = resources.get(tag);
      Set<String> newFiles = new HashSet<>();
      for (String file : files) {
        newFiles.add(replacePathForRemoteRunner(file));
      }
      jobInfo.files().replaceAll(tag, newFiles);
    }
  }

  private ImmutableList<JobInfo> createXtsTradefedTestJob(
      NewMultiCommandRequest request,
      CommandInfo commandInfo,
      SessionInfo sessionInfo,
      ImmutableMap.Builder<String, CommandDetail> commandDetailsBuilder)
      throws InterruptedException, MobileHarnessException {
    SessionRequestInfo sessionRequestInfo;
    CommandDetail.Builder commandDetailBuilder = CommandDetail.newBuilder();
    commandDetailBuilder.setCommandLine(commandInfo.getCommandLine());
    commandDetailBuilder.setOriginalCommandInfo(commandInfo);
    commandDetailBuilder.setCreateTime(Timestamps.fromMillis(clock.millis()));
    commandDetailBuilder.setStartTime(Timestamps.fromMillis(clock.millis()));
    commandDetailBuilder.setUpdateTime(Timestamps.fromMillis(clock.millis()));
    commandDetailBuilder.setRequestId(sessionInfo.getSessionId());
    String commandId = getCommandId(commandInfo, request);
    commandDetailBuilder.setId(commandId);
    commandDetailBuilder.setCommandAttemptId(
        getCommandAttemptId(commandId, sessionInfo.getSessionId()));
    // Set initial state.
    commandDetailBuilder.setState(CommandState.UNKNOWN_STATE);

    // Validates request and generate a sessionRequestInfo that is needed to create a jobInfo.
    try {
      sessionRequestInfo = getSessionRequestInfo(request, commandInfo, sessionInfo);
    } catch (MobileHarnessException e) {
      commandDetailBuilder.setState(CommandState.ERROR);
      commandDetailsBuilder.put(commandId, commandDetailBuilder.build());
      throw e;
    }
    commandDetailBuilder.addAllDeviceSerials(sessionRequestInfo.deviceSerials());

    ImmutableList<JobInfo> jobInfoList;
    try {
      jobInfoList = xtsJobCreator.createXtsTradefedTestJob(sessionRequestInfo);
    } catch (MobileHarnessException e) {
      if (XtsJobCreator.isSkippableException(e)) {
        logger.atInfo().log(
            "Unable to create tradefed jobs for command [%s] due to skippable exception: [%s].",
            commandInfo.getCommandLine(), shortDebugString(e));
        commandDetailsBuilder.put(commandId, commandDetailBuilder.build());
        return ImmutableList.of();
      }
      commandDetailBuilder.setState(CommandState.ERROR);
      commandDetailsBuilder.put(commandId, commandDetailBuilder.build());
      throw e;
    }
    for (JobInfo jobInfo : jobInfoList) {
      try {
        insertAdditionalTestResource(jobInfo, request);
      } catch (MobileHarnessException e) {
        commandDetailBuilder.setState(CommandState.ERROR);
        commandDetailsBuilder.put(commandId, commandDetailBuilder.build());
        throw e;
      }
      commandDetailBuilder.setState(CommandState.RUNNING);
      jobInfo.properties().add(XtsPropertyName.Job.XTS_COMMAND_ID, commandId);
      jobInfo.properties().add(XTS_TF_JOB_PROP, "true");
      logger.atInfo().log(
          "Added job [%s] to the session %s",
          jobInfo.locator().getId(), sessionInfo.getSessionId());
    }

    CommandDetail commandDetail = commandDetailBuilder.build();
    commandDetailsBuilder.put(commandDetail.getId(), commandDetail);
    return jobInfoList;
  }

  private void insertAdditionalTestResource(JobInfo jobInfo, NewMultiCommandRequest request)
      throws MobileHarnessException, InterruptedException {
    for (TestResource testResource : request.getTestResourcesList()) {
      URL testResourceUrl = getTestResourceUrl(testResource);

      if (testResourceUrl.getProtocol().equals("file")) {
        if (!ANDROID_XTS_ZIP_FILENAME_REGEX.matcher(testResource.getName()).matches()) {
          logger.atInfo().log(
              "Adding additional test resource: %s %s",
              testResource.getPath(), testResourceUrl.getPath());
          jobInfo
              .files()
              .add(testResource.getName(), replacePathForRemoteRunner(testResourceUrl.getPath()));
        }
      }
    }
  }

  // Hash commandId from command line.
  private String getCommandId(CommandInfo commandInfo, NewMultiCommandRequest request) {
    if (request.getPrevTestContext().hasCommandLine()
        && !request.getPrevTestContext().getCommandLine().isEmpty()) {
      return UUID.nameUUIDFromBytes(request.getPrevTestContext().getCommandLine().getBytes(UTF_8))
          .toString();
    }
    return UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
  }

  private String getCommandAttemptId(String commandId, String sessionId) {
    String mergedId = sessionId + "_" + commandId;
    return UUID.nameUUIDFromBytes(mergedId.getBytes(UTF_8)).toString();
  }

  private String replacePathForRemoteRunner(String path)
      throws MobileHarnessException, InterruptedException {
    if (path.startsWith(RemoteFileType.ATS_FILE_SERVER.prefix())) {
      return path;
    }
    if (PathUtil.basename(path).equals(ACLOUD_FILENAME)
        && !path.startsWith(Flags.instance().atsStoragePath.getNonNull())) {
      String acloudPath =
          PathUtil.join(
              Flags.instance().atsStoragePath.getNonNull(), "mh_resources", ACLOUD_FILENAME);
      if (!localFileUtil.isFileOrDirExist(acloudPath)) {
        localFileUtil.prepareDir(Path.of(acloudPath).getParent().toString());
        localFileUtil.copyFileOrDir(path, acloudPath);
      }
      path = acloudPath;
    }
    return PathUtil.join(
        RemoteFileType.ATS_FILE_SERVER.prefix(),
        PathUtil.makeRelative(Flags.instance().atsStoragePath.getNonNull(), path));
  }

  @SuppressWarnings("ThrowSpecificExceptions")
  private SessionRequestInfo getSessionRequestInfo(
      NewMultiCommandRequest request, CommandInfo commandInfo, SessionInfo sessionInfo)
      throws MobileHarnessException, InterruptedException {
    try {
      return sessionRequestInfoCache.computeIfAbsent(
          commandInfo,
          key -> {
            try {
              return generateSessionRequestInfo(request, key, sessionInfo);
            } catch (MobileHarnessException | InterruptedException e) {
              throw new RuntimeException(e);
            }
          });
    } catch (RuntimeException e) {
      if (e.getCause() instanceof MobileHarnessException) {
        throw (MobileHarnessException) e.getCause();
      } else if (e.getCause() instanceof InterruptedException) {
        throw (InterruptedException) e.getCause();
      } else {
        throw e;
      }
    }
  }

  private SessionRequestInfo generateSessionRequestInfo(
      NewMultiCommandRequest request, CommandInfo commandInfo, SessionInfo sessionInfo)
      throws MobileHarnessException, InterruptedException {
    List<String> deviceSerials = new ArrayList<>();
    for (DeviceDimension entry : commandInfo.getDeviceDimensionsList()) {
      if (entry.getName().equals("device_serial")) {
        deviceSerials.add(entry.getValue());
      }
      // TODO: need to handle non device serial case.
    }
    String androidXtsZipPath = "";
    ImmutableList.Builder<TestResource> fileTestResources = ImmutableList.builder();
    for (TestResource testResource : request.getTestResourcesList()) {
      URL testResourceUrl = getTestResourceUrl(testResource);

      if (testResourceUrl.getProtocol().equals("file")) {
        if (ANDROID_XTS_ZIP_FILENAME_REGEX.matcher(testResource.getName()).matches()) {
          androidXtsZipPath = testResourceUrl.getPath();
        } else {
          fileTestResources.add(testResource);
        }
      }
    }
    if (androidXtsZipPath.isEmpty()) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.ATS_SERVER_INVALID_REQUEST_ERROR,
          String.format(
              "Didn't find valid android xts zip file in request resources: %s, session ID: %s ",
              request.getTestResourcesList(), sessionInfo.getSessionId()),
          /* cause= */ null);
    }
    String xtsRootDir =
        PathUtil.join(
            DirUtil.getPublicGenDir(),
            "session_" + sessionInfo.getSessionId(),
            Files.getNameWithoutExtension(androidXtsZipPath));
    if (mountedXtsRootDir.isEmpty()) {
      localFileUtil.prepareDir(xtsRootDir);
      mountOrUnzipXtsZip(androidXtsZipPath, xtsRootDir);
      mountedXtsRootDir = xtsRootDir;
    }
    final String androidXtsZipPathCopy = androidXtsZipPath;
    String xtsType =
        xtsTypeLoader.getXtsType(
            xtsRootDir,
            () ->
                String.format(
                    "Please make sure your XTS zip file %s only contains one xts type.",
                    androidXtsZipPathCopy));

    SessionRequestInfo.Builder sessionRequestInfoBuilder =
        CommandLineParser.getInstance().parseCommandLine(commandInfo.getCommandLine());
    sessionRequestInfoBuilder.setCommandLineArgs(commandInfo.getCommandLine());
    sessionRequestInfoBuilder.setXtsType(xtsType);
    sessionRequestInfoBuilder.setXtsRootDir(xtsRootDir);
    sessionRequestInfoBuilder.setAndroidXtsZip(replacePathForRemoteRunner(androidXtsZipPath));
    sessionRequestInfoBuilder.setDeviceSerials(deviceSerials);
    sessionRequestInfoBuilder.setEnvVars(
        ImmutableMap.copyOf(request.getTestEnvironment().getEnvVarsMap()));
    sessionRequestInfoBuilder.setRemoteRunnerFilePathPrefix(
        RemoteFileType.ATS_FILE_SERVER.prefix());
    sessionRequestInfoBuilder.setIsXtsDynamicDownloadEnabled(
        commandInfo.getEnableXtsDynamicDownload());

    if (request.hasPrevTestContext()) {
      for (TestResource testResource : request.getPrevTestContext().getTestResourceList()) {
        URL testResourceUrl = getTestResourceUrl(testResource);
        logger.atInfo().log("testResourceUrl: %s", testResourceUrl);
        if (testResourceUrl.getProtocol().equals("file")
            && XtsConstants.RESULT_ZIP_FILENAME_PATTERN.matcher(testResource.getName()).matches()) {
          Path prevResultZipPath;
          try {
            prevResultZipPath = Path.of(testResourceUrl.getPath());
            localFileUtil.checkFile(prevResultZipPath);
          } catch (MobileHarnessException e) {
            logger.atWarning().withCause(e).log(
                "Failed to parse previous session's output file, skip processing result for"
                    + " previous session: %s. Will rerun the command directly.",
                request.getRetryPreviousSessionId());
            break;
          }
          sessionRequestInfoBuilder.setRetryResultDir(prevResultZipPath.getParent().toString());
          String prevSessionId = prevResultZipPath.getParent().getParent().getFileName().toString();
          sessionRequestInfoBuilder.setRetrySessionId(prevSessionId).setTestPlan("retry");
          break;
        }
      }
    }

    // Insert timeout.
    sessionRequestInfoBuilder
        .setJobTimeout(toJavaDuration(request.getTestEnvironment().getInvocationTimeout()))
        .setStartTimeout(toJavaDuration(request.getQueueTimeout()))
        .setIsAtsServerRequest(true);

    if (commandInfo.getShardingMode() != ShardingMode.SHARDING_MODE_UNSPECIFIED) {
      sessionRequestInfoBuilder.setShardingMode(commandInfo.getShardingMode());
    }

    // TODO: Add consolidate the test environment and existing session request info
    // fields.
    return sessionRequestInfoBuilder
        .setAtsServerTestResources(fileTestResources.build())
        .setAtsServerTestEnvironment(request.getTestEnvironment())
        .build();
  }

  /**
   * Copies xTS tradefed and non-tradefed generated logs/results into proper locations within the
   * given xts root dir. Also update the request's command states based on the results.
   */
  @VisibleForTesting
  HandleResultProcessingResult handleResultProcessing(
      SessionInfo sessionInfo, RequestDetail.Builder requestDetail) throws InterruptedException {
    return handleResultProcessingInternal(sessionInfo, requestDetail);
  }

  private HandleResultProcessingResult handleResultProcessingInternal(
      SessionInfo sessionInfo, RequestDetail.Builder requestDetail) throws InterruptedException {
    NewMultiCommandRequest request = requestDetail.getOriginalRequest();
    Collection<CommandDetail> commandDetails = requestDetail.getCommandDetailsMap().values();
    URL outputUrl = null;
    String outputFileUploadUrl = request.getTestEnvironment().getOutputFileUploadUrl();
    Map<String, JobInfo> jobIdToJobMap = new HashMap<>();
    for (JobInfo jobInfo : sessionInfo.getAllJobs()) {
      jobIdToJobMap.put(jobInfo.locator().getId(), jobInfo);
    }
    SetMultimap<String, String> commandToJobsMap = getCommandToJobsMap(sessionInfo);
    try {
      outputUrl = URI.create(outputFileUploadUrl).toURL();
    } catch (IllegalArgumentException | MalformedURLException e) {
      logger.atWarning().withCause(e).log("Unable to create URL from %s", outputFileUploadUrl);
      return HandleResultProcessingResult.of(
          RequestState.ERROR,
          ErrorReason.RESULT_PROCESSING_ERROR,
          "Unable to create URL from " + outputFileUploadUrl,
          ImmutableMap.of(),
          ImmutableMap.of());
    }

    if (!outputUrl.getProtocol().equals("file")) {
      logger.atWarning().log("Unsupported outputurl: %s", outputUrl);
      // Currently only supports local URL.
      return HandleResultProcessingResult.of(
          RequestState.ERROR,
          ErrorReason.RESULT_PROCESSING_ERROR,
          "Unsupported outputurl: " + outputUrl,
          ImmutableMap.of(),
          ImmutableMap.of());
    }

    ImmutableMap.Builder<String, TestContext> testContextMapBuilder = ImmutableMap.builder();
    ImmutableMap.Builder<String, CommandDetail> commandDetailMapBuilder = ImmutableMap.builder();
    for (CommandDetail commandDetail : commandDetails) {
      CommandDetail.Builder commandDetailBuilder = commandDetail.toBuilder();
      String commandId = commandDetail.getId();
      Path outputDirPath =
          Path.of(outputUrl.getPath()).resolve(sessionInfo.getSessionId()).resolve(commandId);
      String resultDirectoryName =
          TIMESTAMP_DIR_NAME_FORMATTER.format(Instant.now()) + "_" + getRandom4Digits();
      Path resultDir = outputDirPath.resolve(resultDirectoryName);
      Path logDir = outputDirPath.resolve("logs");
      ImmutableList<JobInfo> jobs =
          commandToJobsMap.get(commandId).stream()
              .map(jobIdToJobMap::get)
              .collect(toImmutableList());
      try {
        SessionRequestInfo sessionRequestInfo =
            getSessionRequestInfo(request, commandDetail.getOriginalCommandInfo(), sessionInfo);
        Optional<Result> processResult =
            sessionResultHandlerUtil.processResult(
                resultDir,
                logDir,
                /* latestResultLink= */ null,
                /* latestLogLink= */ null,
                jobs,
                sessionRequestInfo);
        if (processResult.isPresent() && processResult.get().hasSummary()) {
          long failedModuleCount =
              processResult.get().getModuleInfoList().stream()
                  .filter(module -> module.getFailedTests() > 0)
                  .count();
          commandDetailBuilder
              .setPassedTestCount(processResult.get().getSummary().getPassed())
              .setFailedTestCount(processResult.get().getSummary().getFailed())
              .setTotalModuleCount(processResult.get().getSummary().getModulesTotal())
              .setFailedModuleCount(failedModuleCount)
              .setTotalTestCount(
                  commandDetailBuilder.getPassedTestCount()
                      + commandDetailBuilder.getFailedTestCount());
        }
        Path resultZip = outputDirPath.resolve(resultDirectoryName + ".zip");
        if (localFileUtil.isFileExist(resultZip)) {
          // Make sure the context command line is the original command line, not a retry command.
          String contextCommandLine = commandDetail.getCommandLine();
          if (request.hasPrevTestContext()
              && !request.getPrevTestContext().getCommandLine().isEmpty()) {
            contextCommandLine = request.getPrevTestContext().getCommandLine();
          }
          TestContext testContext =
              TestContext.newBuilder()
                  .setCommandLine(contextCommandLine)
                  .putAllEnvVar(request.getTestEnvironment().getEnvVarsMap())
                  .addTestResource(
                      TestResource.newBuilder()
                          .setName(resultZip.getFileName().toString())
                          .setUrl("file://" + resultZip)
                          .build())
                  .build();
          // TODO: filter context files.
          testContextMapBuilder.put(commandId, testContext);
        }

        if (localFileUtil.isDirExist(resultDir)) {
          // Remove dedicated result directory and move its files to '/<session_id>/<command_id>/'
          // level.
          localFileUtil.mergeDir(resultDir, outputDirPath);
        }

        createOutputManifestFile(outputDirPath);

        if (request.hasRetryPreviousSessionId()) {
          Path prevResultDir =
              Path.of(outputUrl.getPath())
                  .resolve(request.getRetryPreviousSessionId())
                  .resolve(commandId);
          sessionResultHandlerUtil.copyRetryFiles(
              prevResultDir.toString(), outputDirPath.toString());
          // After copying the retry files, regenerate the screenshots metadata file if needed.
          sessionResultHandlerUtil.generateScreenshotsMetadataFile(
              sessionRequestInfo, outputDirPath);
        }
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to process result for session %s", sessionInfo.getSessionId());
        setCommandError(commandDetailBuilder, ErrorReason.RESULT_PROCESSING_ERROR, e);
      }

      if (commandDetailBuilder.getState() == CommandState.UNKNOWN_STATE
          || commandDetailBuilder.getState() == CommandState.RUNNING) {
        if (hasCommandPassed(commandDetailBuilder.build())
            || hasCommandFailed(commandDetailBuilder.build())) {
          commandDetailBuilder.setState(CommandState.COMPLETED);
        } else {
          setCommandError(
              commandDetailBuilder,
              ErrorReason.RESULT_PROCESSING_ERROR,
              "No valid test cases found in the result.");
        }
        checkTradefedInvocationError(commandDetailBuilder, jobs, logDir);
      }
      commandDetailBuilder
          .setEndTime(Timestamps.fromMillis(clock.millis()))
          .setUpdateTime(Timestamps.fromMillis(clock.millis()));
      commandDetailMapBuilder.put(commandId, commandDetailBuilder.build());
    }

    // Record OLC server session logs if no command recorded it due to empty command list or result
    // processing failures.
    if (sessionInfo
        .getSessionProperty(SessionProperties.PROPERTY_KEY_SERVER_SESSION_LOG_PATH)
        .isEmpty()) {
      logger.atInfo().log(
          "Setting OLC session log in session's directory because no command recorded the OLC log."
              + " Session: %s",
          sessionInfo.getSessionId());
      Path serverSessionLogsDir =
          Path.of(outputUrl.getPath())
              .resolve(sessionInfo.getSessionId())
              .resolve("olc_server_session_logs");
      try {
        localFileUtil.prepareDir(serverSessionLogsDir);
        sessionInfo.putSessionProperty(
            SessionProperties.PROPERTY_KEY_SERVER_SESSION_LOG_PATH,
            serverSessionLogsDir.resolve("olc_server_session_log.txt").toString());
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to create server session logs dir for session: %s", sessionInfo.getSessionId());
      }
    }

    // Determine the error reason and error message for the request level, based on error reason and
    // error message from commands.
    ImmutableMap<String, CommandDetail> commandDetailsMap =
        commandDetailMapBuilder.buildKeepingLast();
    ErrorReason errorReason = requestDetail.getErrorReason();
    String errorMessage = requestDetail.getErrorMessage();
    Optional<CommandDetail> commandDetailWithError =
        commandDetailsMap.values().stream()
            .filter(commandDetail -> commandDetail.getState() == CommandState.ERROR)
            .findFirst();
    if (commandDetailWithError.isPresent()
        && commandDetailWithError.get().getErrorReason() != ErrorReason.UNKNOWN_REASON) {
      errorReason = commandDetailWithError.get().getErrorReason();
      errorMessage =
          errorReason == ErrorReason.TRADEFED_INVOCATION_ERROR
              ? REQUEST_ERROR_MESSAGE_FOR_TRADEFED_INVOCATION_ERROR
              : commandDetailWithError.get().getErrorMessage();
    }

    return HandleResultProcessingResult.of(
        RequestState.RUNNING,
        errorReason,
        errorMessage,
        commandDetailMapBuilder.buildKeepingLast(),
        testContextMapBuilder.buildKeepingLast());
  }

  // Clean up temporary files and directories in session and jobs.
  @VisibleForTesting
  void cleanup(SessionInfo sessionInfo) throws InterruptedException {
    try {
      ImmutableList<TestInfo> tests =
          sessionInfo.getAllJobs().stream()
              .map(jobInfo -> jobInfo.tests().getAll().values())
              .flatMap(Collection::stream)
              .collect(toImmutableList());
      for (TestInfo testInfo : tests) {
        sessionResultHandlerUtil.cleanUpLabGenFileDir(testInfo);
      }
      sessionResultHandlerUtil.cleanUpJobGenDirs(sessionInfo.getAllJobs());
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to clean up job gen dirs for session: %s", sessionInfo.getSessionId());
    }
    if (!mountedXtsRootDir.isEmpty()) {
      try {
        unmountOrRemoveZipDir(mountedXtsRootDir);
        mountedXtsRootDir = "";
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to unmount or unzip xts root directory: %s", mountedXtsRootDir);
      }
    }
  }

  /**
   * Creates a manifest file in the output directory, listing all the files (using their relative
   * paths) in the output directory. Also removes any existing manifest file with the same name if
   * it exists.
   */
  private void createOutputManifestFile(Path outputDirPath) {
    try {
      ImmutableList.Builder<String> outputManifestListBuilder = ImmutableList.builder();
      localFileUtil.listFiles(outputDirPath.toString(), /* recursively= */ true).stream()
          .map(File::getAbsolutePath)
          .filter(
              path -> {
                if (path.endsWith(OUTPUT_MANIFEST_FILE_NAME)) {
                  try {
                    localFileUtil.removeFileOrDir(path);
                  } catch (MobileHarnessException | InterruptedException e) {
                    logger.atWarning().withCause(e).log(
                        "Failed to remove existing output manifest file: %s", path);
                    if (e instanceof InterruptedException) {
                      Thread.currentThread().interrupt();
                    }
                  }
                  return false;
                }
                return true;
              })
          // Get the relative path of the file ("+ 1" to skip past the "/").
          .map(path -> path.substring(outputDirPath.toString().length() + 1))
          .forEach(outputManifestListBuilder::add);
      String outputManifest = String.join("\n", outputManifestListBuilder.build());
      localFileUtil.writeToFile(
          outputDirPath.resolve(OUTPUT_MANIFEST_FILE_NAME).toString(), outputManifest);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to create test output manifest file.");
    }
  }

  /**
   * Reads the Tradefed runtime info file and checks for the Tradefed invocation error message, and
   * set it in the command detail proto if present.
   */
  private void checkTradefedInvocationError(
      CommandDetail.Builder commandDetailBuilder, ImmutableList<JobInfo> jobs, Path logDir) {
    ImmutableList<TestInfo> tradefedTestInfos =
        jobs.stream()
            .filter(jobInfo -> jobInfo.properties().getBoolean(Job.IS_XTS_TF_JOB).orElse(false))
            .flatMap(jobInfo -> jobInfo.tests().getAll().values().stream())
            .collect(toImmutableList());

    tradefedTestInfos.forEach(
        tradefedTestInfo -> {
          Path testLogDir;
          try {
            testLogDir =
                sessionResultHandlerUtil.getTradefedInvocationLogDir(tradefedTestInfo, logDir);
          } catch (MobileHarnessException e) {
            logger.atWarning().withCause(e).log(
                "Failed to get Tradefed invocation log dir for test %s",
                tradefedTestInfo.locator().getId());
            return;
          }

          Path runtimeInfoFilePath =
              testLogDir.resolve(XtsConstants.TRADEFED_RUNTIME_INFO_FILE_NAME);
          if (!localFileUtil.isFileExist(runtimeInfoFilePath)) {
            return;
          }

          Optional<XtsTradefedRuntimeInfoFileDetail> fileDetailOptional;
          try {
            fileDetailOptional =
                xtsTradefedRuntimeInfoFileUtil.readInfo(
                    runtimeInfoFilePath, /* lastModifiedTime= */ null);
          } catch (IOException | RuntimeException | Error e) {
            logger.atWarning().withCause(e).log(
                "Failed to read Tradefed runtime info of test %s from file %s",
                tradefedTestInfo.locator().getId(), runtimeInfoFilePath);
            return;
          }

          if (fileDetailOptional.isEmpty()) {
            return;
          }

          ImmutableList<TradefedInvocation> invocationsWithError =
              fileDetailOptional.get().runtimeInfo().invocations().stream()
                  .filter(not(TradefedInvocation::isRunning))
                  .filter(invocation -> !isNullOrEmpty(invocation.errorMessage()))
                  .collect(toImmutableList());

          if (!invocationsWithError.isEmpty()) {
            String errorMessage = invocationsWithError.get(0).errorMessage();
            String newErrorMessage =
                commandDetailBuilder.getErrorMessage().isEmpty()
                    ? errorMessage
                    : commandDetailBuilder.getErrorMessage() + "\n" + errorMessage;
            commandDetailBuilder.setErrorMessage(newErrorMessage);
            if (commandDetailBuilder.getState() != CommandState.COMPLETED) {
              commandDetailBuilder
                  .setState(CommandState.ERROR)
                  .setErrorReason(ErrorReason.TRADEFED_INVOCATION_ERROR);
            }
          }
        });
  }

  private URL getTestResourceUrl(TestResource testResource) throws MobileHarnessException {
    try {
      return URI.create(testResource.getUrl()).toURL();
    } catch (IllegalArgumentException | MalformedURLException e) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.ATS_SERVER_INVALID_TEST_RESOURCE,
          String.format("Failed to parse url from : %s", testResource.getUrl()),
          e);
    }
  }

  private boolean hasCommandPassed(CommandDetail commandDetail) {
    return commandDetail.getTotalTestCount() > 0 && commandDetail.getFailedTestCount() == 0;
  }

  private boolean hasCommandFailed(CommandDetail commandDetail) {
    return commandDetail.getTotalTestCount() > 0 && commandDetail.getFailedTestCount() != 0;
  }

  @CanIgnoreReturnValue
  private String mountOrUnzipXtsZip(String zipFilePath, String targetDirPath)
      throws MobileHarnessException, InterruptedException {
    if (atsServerSessionUtil.isLocalMode()) {
      return localFileUtil.unzipFile(zipFilePath, targetDirPath, UNZIP_TIMEOUT);
    }
    Command command =
        Command.of("fuse-zip", "-r", zipFilePath, targetDirPath).timeout(SLOW_CMD_TIMEOUT);
    try {
      return commandExecutor.run(command);
    } catch (MobileHarnessException e) {
      if (e.getMessage().contains("mountpoint is not empty")) {
        logger.atInfo().log(
            "Mount point is not empty. It's usually caused by session is resumed and is"
                + " acceptable.");
        return "";
      } else {
        // This might happen when the host machine doesn't have the fuse-zip binary installed (e.g.
        // running on Forge during integration tests). We'll try to unzip the XTS zip file instead.
        logger.atWarning().withCause(e).log(
            "Failed to mount XTS zip file %s to %s. Trying to unzip it instead.",
            zipFilePath, targetDirPath);
        return localFileUtil.unzipFile(zipFilePath, targetDirPath, UNZIP_TIMEOUT);
      }
    }
  }

  private void unmountOrRemoveZipDir(String targetDirPath)
      throws MobileHarnessException, InterruptedException {
    if (atsServerSessionUtil.isLocalMode()) {
      localFileUtil.removeFileOrDir(targetDirPath);
      return;
    }
    Command command = Command.of("fusermount", "-u", targetDirPath).timeout(SLOW_CMD_TIMEOUT);
    try {
      // Add a 5 seconds delay before unmounting the zip file to avoid race condition and unmount
      // failure.
      sleeper.sleep(Duration.ofSeconds(5));
      commandExecutor.run(command);
    } catch (MobileHarnessException e) {
      // If unmounting fails, it might be because we unzipped the xTS zip file instead of mounting
      // it. In this case, we'll try to remove the directory instead.
      logger.atWarning().withCause(e).log(
          "Failed to unmount XTS zip file at %s. Trying to remove it instead.", targetDirPath);
      localFileUtil.removeFileOrDir(targetDirPath);
    }
  }

  /**
   * Set command error and log the stack trace.
   *
   * <p>Command error on ATS UI could show the stack trace properly. Ideally, setCommandError should
   * be called at most one time for each command.
   */
  private static void setCommandError(
      CommandDetail.Builder commandDetailBuilder, ErrorReason reason, Exception e) {
    setCommandError(commandDetailBuilder, reason, getStackTraceAsString(e));
  }

  private static void setCommandError(
      CommandDetail.Builder commandDetailBuilder, ErrorReason reason, String errorMessage) {
    commandDetailBuilder
        .setState(CommandState.ERROR)
        .setErrorReason(reason)
        .setErrorMessage(errorMessage);
  }

  private static int getRandom4Digits() {
    return ThreadLocalRandom.current().nextInt(1000, 10000);
  }

  @AutoValue
  public abstract static class CreateJobsResult {
    private static CreateJobsResult of(
        RequestState state,
        @Nullable ErrorReason errorReason,
        @Nullable String errorMessage,
        ImmutableMap<String, CommandDetail> commandDetails,
        ImmutableList<JobInfo> jobInfos) {
      return new AutoValue_NewMultiCommandRequestHandler_CreateJobsResult(
          state,
          Optional.ofNullable(errorReason),
          Optional.ofNullable(errorMessage),
          commandDetails,
          jobInfos);
    }

    public abstract RequestState state();

    public abstract Optional<ErrorReason> errorReason();

    public abstract Optional<String> errorMessage();

    public abstract ImmutableMap<String, CommandDetail> commandDetails();

    public abstract ImmutableList<JobInfo> jobInfos();
  }

  @AutoValue
  public abstract static class HandleResultProcessingResult {
    private static HandleResultProcessingResult of(
        RequestState state,
        @Nullable ErrorReason errorReason,
        @Nullable String errorMessage,
        ImmutableMap<String, CommandDetail> commandDetails,
        ImmutableMap<String, TestContext> testContexts) {
      return new AutoValue_NewMultiCommandRequestHandler_HandleResultProcessingResult(
          state,
          Optional.ofNullable(errorReason),
          Optional.ofNullable(errorMessage),
          commandDetails,
          testContexts);
    }

    public abstract RequestState state();

    public abstract Optional<ErrorReason> errorReason();

    public abstract Optional<String> errorMessage();

    public abstract ImmutableMap<String, CommandDetail> commandDetails();

    public abstract ImmutableMap<String, TestContext> testContexts();
  }
}
