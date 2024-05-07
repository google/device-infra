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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toJavaDuration;
import static com.google.protobuf.TextFormat.shortDebugString;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.Files;
import com.google.devtools.deviceinfra.shared.util.file.remote.constant.RemoteFileType;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.CommandHelper;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.SessionResultHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CancelReason;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandInfo;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandInfo.DeviceDimension;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.NewMultiCommandRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail.RequestState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestResource;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.lab.common.dir.DirUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;
import org.xmlpull.v1.XmlPullParserException;

/** Handler for ATS server's create test jobs request. */
@NotThreadSafe
final class NewMultiCommandRequestHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Timeout setting for slow commands. */
  private static final Duration SLOW_CMD_TIMEOUT = Duration.ofMinutes(10);

  private static final Pattern ANDROID_XTS_ZIP_FILENAME_REGEX =
      Pattern.compile("android-[a-z]+\\.zip");
  @VisibleForTesting static final String XTS_TF_JOB_PROP = "xts-tradefed-job";

  private final SessionRequestHandlerUtil sessionRequestHandlerUtil;
  private final SessionResultHandlerUtil sessionResultHandlerUtil;
  private final LocalFileUtil localFileUtil;
  private final CommandExecutor commandExecutor;
  private final Clock clock;
  private final CommandHelper commandHelper;

  // Command id to its job ids
  private final SetMultimap<String, String> commandToJobsMap = HashMultimap.create();

  // Job id to command id mapping.
  private final Map<String, String> jobToCommandMap = new HashMap<>();

  boolean hasMountedAndroidXtsZip = false;

  // Cache for storing validated sessionRequestInfo for each command generated by addTradefedJobs(),
  // and mainly used by addNonTradefedJobs() to reduce duplicate sessionRequestInfo generation that
  // involves time-consuming file I/O.
  private final ConcurrentHashMap<CommandInfo, SessionRequestInfo> sessionRequestInfoCache =
      new ConcurrentHashMap<>();

  @Inject
  NewMultiCommandRequestHandler(
      SessionRequestHandlerUtil sessionRequestHandlerUtil,
      SessionResultHandlerUtil sessionResultHandlerUtil,
      LocalFileUtil localFileUtil,
      CommandExecutor commandExecutor,
      Clock clock,
      CommandHelper commandHelper) {
    this.sessionRequestHandlerUtil = sessionRequestHandlerUtil;
    this.sessionResultHandlerUtil = sessionResultHandlerUtil;
    this.localFileUtil = localFileUtil;
    this.commandExecutor = commandExecutor;
    this.clock = clock;
    this.commandHelper = commandHelper;
  }

  RequestDetail addTradefedJobs(NewMultiCommandRequest request, SessionInfo sessionInfo)
      throws MobileHarnessException, InterruptedException {
    RequestDetail.Builder requestDetailBuilder =
        RequestDetail.newBuilder()
            .setCreateTime(Timestamps.fromMillis(clock.millis()))
            .setStartTime(Timestamps.fromMillis(clock.millis()))
            .setId(sessionInfo.getSessionId())
            .setState(RequestState.RUNNING)
            .setOriginalRequest(request)
            .setMaxRetryOnTestFailures(request.getMaxRetryOnTestFailures())
            .addAllCommandInfos(request.getCommandsList());
    if (request.getCommandsList().isEmpty()) {
      return requestDetailBuilder
          .setCancelReason(CancelReason.COMMAND_NOT_AVAILABLE)
          .setState(RequestState.CANCELED)
          .build();
    }
    for (CommandInfo commandInfo : request.getCommandsList()) {
      CommandDetail commandDetail = createXtsTradefedTestJob(request, commandInfo, sessionInfo);
      if (commandDetail.getState() == CommandState.CANCELED) {
        if (sessionRequestInfoCache.containsKey(commandInfo)
            && sessionRequestHandlerUtil.canCreateNonTradefedJobs(
                sessionRequestInfoCache.get(commandInfo))) {
          logger.atInfo().log(
              "Skip creating tradefed jobs for this command as this is a non-tradefed only"
                  + " command. Command: %s",
              commandInfo.getCommandLine());
          continue;
        }
        requestDetailBuilder
            .setCancelReason(CancelReason.INVALID_REQUEST)
            .setState(RequestState.CANCELED)
            .putCommandDetails("UNKNOWN_" + commandInfo.getCommandLine(), commandDetail);
        return requestDetailBuilder.build();
      }
      requestDetailBuilder.putCommandDetails(commandDetail.getId(), commandDetail);
    }
    requestDetailBuilder.setUpdateTime(Timestamps.fromMillis(clock.millis()));
    return requestDetailBuilder.build();
  }

  // Creates non-tradefed jobs if needed. If the command detail of this command hasn't been created
  // before, create and return one.
  Optional<CommandDetail> addNonTradefedJobs(
      NewMultiCommandRequest request, CommandInfo commandInfo, SessionInfo sessionInfo)
      throws MobileHarnessException, InterruptedException {
    SessionRequestInfo sessionRequestInfo;
    try {
      if (sessionRequestInfoCache.containsKey(commandInfo)) {
        sessionRequestInfo = sessionRequestInfoCache.get(commandInfo);
      } else {
        sessionRequestInfo = generateSessionRequestInfo(request, commandInfo, sessionInfo);
        sessionRequestInfoCache.put(commandInfo, sessionRequestInfo);
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to generate sessionRequestInfo from commandInfo: %s. SessionID: %s",
          shortDebugString(commandInfo), sessionInfo.getSessionId());
      return Optional.empty();
    }

    if (!sessionRequestHandlerUtil.canCreateNonTradefedJobs(sessionRequestInfo)) {
      logger.atInfo().log(
          "No valid module(s) matched, no non-tradefed jobs will run. The command info -> %s",
          shortDebugString(commandInfo));
      return Optional.empty();
    }

    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo);

    if (jobInfos.isEmpty()) {
      logger.atInfo().log(
          "No valid module(s) matched, no non-tradefed jobs will run. The command info -> %s",
          shortDebugString(commandInfo));
      return Optional.empty();
    }
    Optional<CommandDetail> optionalCommandDetail = Optional.empty();

    // Hash commandId
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    if (!commandToJobsMap.containsKey(commandId)) {
      CommandDetail commandDetail =
          CommandDetail.newBuilder()
              .setCommandLine(commandInfo.getCommandLine())
              .setOriginalCommandInfo(commandInfo)
              .setCreateTime(Timestamps.fromMillis(clock.millis()))
              .setStartTime(Timestamps.fromMillis(clock.millis()))
              .setUpdateTime(Timestamps.fromMillis(clock.millis()))
              .setId(commandId)
              .setState(CommandState.RUNNING)
              .build();
      optionalCommandDetail = Optional.of(commandDetail);
    }
    for (JobInfo jobInfo : jobInfos) {
      sessionInfo.addJob(jobInfo);
      reformatResourcePathForNonTradefedJob(jobInfo);
      commandToJobsMap.put(commandId, jobInfo.locator().getId());
      jobToCommandMap.put(jobInfo.locator().getId(), commandId);
      logger.atInfo().log(
          "Added non-tradefed job[%s] to the session %s",
          jobInfo.locator().getId(), sessionInfo.getSessionId());
    }
    return optionalCommandDetail;
  }

  String getCommandIdOfJob(JobInfo jobInfo) {
    if (jobToCommandMap.containsKey(jobInfo.locator().getId())) {
      return jobToCommandMap.get(jobInfo.locator().getId());
    }
    return "";
  }

  int getJobCountOfCommand(String commandId) {
    if (commandToJobsMap.containsKey(commandId)) {
      return commandToJobsMap.get(commandId).size();
    }
    return 0;
  }

  private void reformatResourcePathForNonTradefedJob(JobInfo jobInfo)
      throws MobileHarnessException {
    ImmutableMultimap<String, String> resources = jobInfo.files().getAll();
    for (String tag : resources.keySet()) {
      ImmutableCollection<String> files = resources.get(tag);
      jobInfo
          .files()
          .replaceAll(
              tag, files.stream().map(this::replacePathForRemoteRunner).collect(toImmutableSet()));
    }
  }

  private CommandDetail createXtsTradefedTestJob(
      NewMultiCommandRequest request, CommandInfo commandInfo, SessionInfo sessionInfo)
      throws MobileHarnessException, InterruptedException {
    SessionRequestInfo sessionRequestInfo;
    CommandDetail.Builder commandDetailBuilder = CommandDetail.newBuilder();
    commandDetailBuilder.setCommandLine(commandInfo.getCommandLine());
    commandDetailBuilder.setOriginalCommandInfo(commandInfo);
    commandDetailBuilder.setCreateTime(Timestamps.fromMillis(clock.millis()));
    commandDetailBuilder.setStartTime(Timestamps.fromMillis(clock.millis()));
    commandDetailBuilder.setUpdateTime(Timestamps.fromMillis(clock.millis()));
    commandDetailBuilder.setRequestId(sessionInfo.getSessionId());

    // Validates request and generate a sessionRequestInfo that is needed to create a jobInfo.
    try {
      sessionRequestInfo = generateSessionRequestInfo(request, commandInfo, sessionInfo);
      sessionRequestInfoCache.put(commandInfo, sessionRequestInfo);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to generate sessionRequestInfo from commandInfo: %s. SessionID: %s",
          shortDebugString(commandInfo), sessionInfo.getSessionId());
      commandDetailBuilder.setState(CommandState.CANCELED);
      if (e.getErrorId() == BasicErrorId.LOCAL_MOUNT_ZIP_TO_DIR_ERROR) {
        commandDetailBuilder.setCancelReason(CancelReason.INVALID_RESOURCE);
      } else {
        commandDetailBuilder.setCancelReason(CancelReason.INVALID_REQUEST);
      }
      return commandDetailBuilder.build();
    }

    Optional<JobInfo> jobInfo =
        sessionRequestHandlerUtil.createXtsTradefedTestJob(sessionRequestInfo);
    if (jobInfo.isPresent()) {
      String commandId =
          UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
      commandDetailBuilder.setId(commandId).setState(CommandState.RUNNING);
      commandToJobsMap.put(commandId, jobInfo.get().locator().getId());
      jobToCommandMap.put(jobInfo.get().locator().getId(), commandId);
      jobInfo.get().properties().add(XTS_TF_JOB_PROP, "true");
      insertAdditionalTestResource(jobInfo.get(), request);
      sessionInfo.addJob(jobInfo.get());
      logger.atInfo().log(
          "Added job[%s] to the session %s",
          jobInfo.get().locator().getId(), sessionInfo.getSessionId());
    } else {
      commandDetailBuilder
          .setState(CommandState.CANCELED)
          .setCancelReason(CancelReason.INVALID_REQUEST);
    }
    return commandDetailBuilder.build();
  }

  private void insertAdditionalTestResource(JobInfo jobInfo, NewMultiCommandRequest request)
      throws MobileHarnessException {
    for (TestResource testResource : request.getTestResourcesList()) {
      URL testResourceUrl;
      try {
        testResourceUrl = URI.create(testResource.getUrl()).toURL();
      } catch (IllegalArgumentException | MalformedURLException e) {
        logger.atWarning().withCause(e).log(
            "Failed to parse url from url: %s", testResource.getUrl());
        continue;
      }

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

  private String replacePathForRemoteRunner(String path) {
    if (path.startsWith(RemoteFileType.ATS_FILE_SERVER.prefix())) {
      return path;
    }
    return PathUtil.join(
        RemoteFileType.ATS_FILE_SERVER.prefix(),
        PathUtil.makeRelative(Flags.instance().atsStoragePath.getNonNull(), path));
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
      URL testResourceUrl;
      try {
        testResourceUrl = URI.create(testResource.getUrl()).toURL();
      } catch (IllegalArgumentException | MalformedURLException e) {
        logger.atWarning().withCause(e).log(
            "Failed to parse url from url: %s", testResource.getUrl());
        continue;
      }

      if (testResourceUrl.getProtocol().equals("file")) {
        if (ANDROID_XTS_ZIP_FILENAME_REGEX.matcher(testResource.getName()).matches()) {
          androidXtsZipPath = testResourceUrl.getPath();
        } else {
          fileTestResources.add(testResource);
        }
      }
    }
    if (androidXtsZipPath.isEmpty()) {
      logger.atInfo().log(
          "Didn't find android xts zip file in request resources: %s, session ID: %s ",
          request.getTestResourcesList(), sessionInfo.getSessionId());
      throw new MobileHarnessException(
          InfraErrorId.ATS_SERVER_INVALID_REQUEST_ERROR,
          String.format(
              "Didn't find valid android xts zip file in request resources: %s, session ID: %s ",
              request.getTestResourcesList(), sessionInfo.getSessionId()));
    }
    String xtsRootDir =
        PathUtil.join(
            DirUtil.getPublicGenDir(),
            "session_" + sessionInfo.getSessionId(),
            Files.getNameWithoutExtension(androidXtsZipPath));
    if (!hasMountedAndroidXtsZip) {
      localFileUtil.prepareDir(xtsRootDir);
      mountZip(androidXtsZipPath, xtsRootDir);
      hasMountedAndroidXtsZip = true;
    }
    String xtsType = commandHelper.getXtsType(xtsRootDir);

    // Generate XML test config template for ClusterCommandLauncher.
    Path commandPath = Path.of(xtsRootDir).resolveSibling("command.xml");
    try (OutputStream outputStream = new FileOutputStream(commandPath.toFile())) {
      TradefedConfigGenerator.generateXml(
          outputStream,
          request.getTestEnvironment(),
          fileTestResources.build(),
          deviceSerials.size());
    } catch (IOException | XmlPullParserException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATS_SERVER_FAILED_TO_GENERATE_XML_TEST_CONFIG,
          String.format(
              "Failed to create XML test config for session %s ", sessionInfo.getSessionId()),
          e);
    }
    logger.atInfo().log(
        "Generate TF config for session %s:\n%s",
        sessionInfo.getSessionId(), localFileUtil.readFile(commandPath));

    SessionRequestInfo.Builder sessionRequestInfoBuilder =
        CommandLineParser.getInstance().parseCommandLine(commandInfo.getCommandLine());
    sessionRequestInfoBuilder.setCommandLineArgs(commandInfo.getCommandLine());
    sessionRequestInfoBuilder.setXtsType(xtsType);
    sessionRequestInfoBuilder.setXtsRootDir(xtsRootDir);
    sessionRequestInfoBuilder.setAndroidXtsZip(replacePathForRemoteRunner(androidXtsZipPath));
    sessionRequestInfoBuilder.setDeviceSerials(deviceSerials);
    sessionRequestInfoBuilder.setEnvVars(
        ImmutableMap.copyOf(request.getTestEnvironment().getEnvVarsMap()));
    sessionRequestInfoBuilder.setTestPlanFile(
        replacePathForRemoteRunner(commandPath.toAbsolutePath().toString()));

    if (!request.getRetryPreviousSessionId().isEmpty()) {
      sessionRequestInfoBuilder.setTestPlan("retry");
      // TODO: customize retry type based on UI input.
      sessionRequestInfoBuilder.setRetrySessionId(request.getRetryPreviousSessionId());
      try {
        URL outputUrl = URI.create(request.getTestEnvironment().getOutputFileUploadUrl()).toURL();
        if (outputUrl.getProtocol().equals("file")) {
          String previousCommandId =
              UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
          Path outputDirPath =
              Path.of(outputUrl.getPath())
                  .resolve(request.getRetryPreviousSessionId())
                  .resolve(previousCommandId);
          // Resolve another test run's output dir.
          if (!request.getRetryPreviousTestRunId().isEmpty()) {
            outputDirPath =
                Path.of(outputUrl.getPath())
                    .getParent()
                    .getParent()
                    .resolve(request.getRetryPreviousTestRunId())
                    .resolve("output")
                    .resolve(request.getRetryPreviousSessionId())
                    .resolve(previousCommandId);
          }
          sessionRequestInfoBuilder.setRetryResultDir(outputDirPath.toString());
        }
      } catch (MalformedURLException e) {
        throw new MobileHarnessException(
            InfraErrorId.ATS_SERVER_INVALID_REQUEST_ERROR,
            String.format(
                "Failed to parse output file upload url: %s, skip processing result for session:"
                    + " %s.",
                request.getTestEnvironment().getOutputFileUploadUrl(), sessionInfo.getSessionId()),
            e);
      }
    }

    // TODO: add extra args
    sessionRequestInfoBuilder.setExtraArgs(ImmutableList.of());

    // Insert timeout.
    sessionRequestInfoBuilder.setJobTimeout(
        toJavaDuration(request.getTestEnvironment().getInvocationTimeout()));
    sessionRequestInfoBuilder.setStartTimeout(toJavaDuration(request.getQueueTimeout()));

    return sessionRequestHandlerUtil.addNonTradefedModuleInfo(sessionRequestInfoBuilder.build());
  }

  /**
   * Copies xTS tradefed and non-tradefed generated logs/results into proper locations within the
   * given xts root dir. Also update the request's command states based on the results.
   */
  void handleResultProcessing(SessionInfo sessionInfo, RequestDetail.Builder requestDetail)
      throws MobileHarnessException, InterruptedException {
    URL outputUrl;
    String outputFileUploadUrl =
        requestDetail.getOriginalRequest().getTestEnvironment().getOutputFileUploadUrl();
    Map<String, JobInfo> jobIdToJobMap = new HashMap<>();
    for (JobInfo jobInfo : sessionInfo.getAllJobs()) {
      jobIdToJobMap.put(jobInfo.locator().getId(), jobInfo);
    }
    try {
      outputUrl =
          URI.create(
                  requestDetail.getOriginalRequest().getTestEnvironment().getOutputFileUploadUrl())
              .toURL();
      // Currently only supports local URL.
      if (outputUrl.getProtocol().equals("file")) {
        for (CommandDetail commandDetail : requestDetail.getCommandDetailsMap().values()) {
          SessionRequestInfo sessionRequestInfo =
              sessionRequestInfoCache.get(commandDetail.getOriginalCommandInfo());
          if (sessionRequestInfo == null) {
            continue;
          }
          String commandId = commandDetail.getId();
          Path outputDirPath =
              Path.of(outputUrl.getPath()).resolve(sessionInfo.getSessionId()).resolve(commandId);
          Path resultDir = outputDirPath;
          Path logDir = outputDirPath;
          Optional<Result> result =
              sessionResultHandlerUtil.processResult(
                  resultDir,
                  logDir,
                  /* latestResultLink= */ null,
                  /* latestLogLink= */ null,
                  commandToJobsMap.get(commandId).stream()
                      .map(jobIdToJobMap::get)
                      .collect(toImmutableList()),
                  sessionRequestInfo);
          CommandDetail.Builder commandDetailBuilder = commandDetail.toBuilder();
          if (result.isPresent() && result.get().hasSummary()) {
            commandDetailBuilder
                .setPassedTestCount(result.get().getSummary().getPassed())
                .setFailedTestCount(result.get().getSummary().getFailed());
            commandDetailBuilder.setTotalTestCount(
                commandDetailBuilder.getPassedTestCount()
                    + commandDetailBuilder.getFailedTestCount());
          }
          commandDetailBuilder
              .setState(
                  hasCommandPassed(commandDetailBuilder.build())
                      ? CommandState.COMPLETED
                      : CommandState.ERROR)
              .setEndTime(Timestamps.fromMillis(clock.millis()))
              .setUpdateTime(Timestamps.fromMillis(clock.millis()));
          requestDetail.putCommandDetails(commandId, commandDetailBuilder.build());
        }
      } else {
        logger.atWarning().log(
            "Skip processing result for unsupported file output upload url: %s",
            outputFileUploadUrl);
      }
    } catch (IllegalArgumentException | MalformedURLException e) {
      logger.atWarning().withCause(e).log(
          "Failed to parse output file upload url: %s, skip processing result.",
          outputFileUploadUrl);

    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to process result for session: %s", sessionInfo.getSessionId());
    }
    cleanup(requestDetail.getOriginalRequest(), sessionInfo);
  }

  // Clean up temporary files and directories in session and jobs.
  void cleanup(NewMultiCommandRequest newMultiCommandRequest, SessionInfo sessionInfo)
      throws InterruptedException, MobileHarnessException {
    sessionResultHandlerUtil.cleanUpJobGenDirs(sessionInfo.getAllJobs());
    if (hasMountedAndroidXtsZip) {
      String xtsRootDir =
          sessionRequestInfoCache.get(newMultiCommandRequest.getCommandsList().get(0)).xtsRootDir();
      try {
        unmountZip(xtsRootDir);
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log("Failed to unmount xts root directory: %s", xtsRootDir);
      }
    }
  }

  private boolean hasCommandPassed(CommandDetail commandDetail) {
    return commandDetail.getTotalTestCount() > 0 && commandDetail.getFailedTestCount() == 0;
  }

  @CanIgnoreReturnValue
  private String mountZip(String zipFilePath, String mountDirPath)
      throws MobileHarnessException, InterruptedException {
    Command command =
        Command.of("fuse-zip", "-r", zipFilePath, mountDirPath).timeout(SLOW_CMD_TIMEOUT);
    try {
      return commandExecutor.run(command);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_MOUNT_ZIP_TO_DIR_ERROR,
          String.format("Failed to mount zip %s into dir %s", zipFilePath, mountDirPath),
          e);
    }
  }

  @CanIgnoreReturnValue
  private String unmountZip(String mountDirPath)
      throws MobileHarnessException, InterruptedException {
    Command command = Command.of("fusermount", "-u", mountDirPath).timeout(SLOW_CMD_TIMEOUT);
    try {
      return commandExecutor.run(command);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_UNMOUNT_DIR_ERROR,
          String.format("Failed to unmount dir %s", mountDirPath),
          e);
    }
  }
}
