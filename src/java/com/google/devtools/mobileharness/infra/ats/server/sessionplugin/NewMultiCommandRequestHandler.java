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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.Files;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.XtsType;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CancelReason;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandInfo;
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
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.inject.Inject;

/** Handler for ATS server's create test jobs request. */
final class NewMultiCommandRequestHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String GEN_FILE_DIR = DirUtil.getPublicGenDir();
  private static final String FILE_URL_PREFIX = "file://";

  /** Timeout setting for slow commands. */
  private static final Duration SLOW_CMD_TIMEOUT = Duration.ofMinutes(10);

  private static final Pattern ANDROID_XTS_ZIP_FILENAME_REGEX =
      Pattern.compile("android-[a-z]+\\.zip");
  @VisibleForTesting static final String XTS_TF_JOB_PROP = "xts-tradefed-job";

  private final SessionRequestHandlerUtil sessionRequestHandlerUtil;
  private final LocalFileUtil localFileUtil;
  private final CommandExecutor commandExecutor;

  @Inject
  NewMultiCommandRequestHandler(
      SessionRequestHandlerUtil sessionRequestHandlerUtil,
      LocalFileUtil localFileUtil,
      CommandExecutor commandExecutor) {
    this.sessionRequestHandlerUtil = sessionRequestHandlerUtil;
    this.localFileUtil = localFileUtil;
    this.commandExecutor = commandExecutor;
  }

  void handle(NewMultiCommandRequest request, SessionInfo sessionInfo)
      throws MobileHarnessException, InterruptedException {
    sessionInfo.setSessionPluginOutput(
        empty ->
            RequestDetail.newBuilder()
                .setId(sessionInfo.getSessionId())
                .setState(RequestState.RUNNING)
                .addAllCommandInfos(request.getCommandsList())
                .build(),
        RequestDetail.class);
    if (request.getCommandsList().isEmpty()) {
      sessionInfo.setSessionPluginOutput(
          oldOutput ->
              (oldOutput == null ? RequestDetail.newBuilder() : oldOutput.toBuilder())
                  .setId(sessionInfo.getSessionId())
                  .addAllCommandInfos(request.getCommandsList())
                  .setCancelReason(CancelReason.COMMAND_NOT_AVAILABLE)
                  .setState(RequestState.CANCELED)
                  .build(),
          RequestDetail.class);
      return;
    }
    for (CommandInfo commandInfo : request.getCommandsList()) {
      Optional<JobInfo> jobInfo = createXtsTradefedTestJob(request, commandInfo, sessionInfo);
      if (jobInfo.isEmpty()) {
        CommandDetail commandDetail =
            CommandDetail.newBuilder()
                .setCommandLine(commandInfo.getCommandLine())
                .setState(CommandState.CANCELED)
                .setCancelReason(CancelReason.INVALID_REQUEST)
                .build();
        sessionInfo.setSessionPluginOutput(
            oldOutput ->
                (oldOutput == null ? RequestDetail.newBuilder() : oldOutput.toBuilder())
                    .setId(sessionInfo.getSessionId())
                    .addAllCommandInfos(request.getCommandsList())
                    .setCancelReason(CancelReason.INVALID_REQUEST)
                    .setState(RequestState.CANCELED)
                    .putCommandDetails("UNKNOWN_" + commandInfo.getCommandLine(), commandDetail)
                    .build(),
            RequestDetail.class);
        return;
      }
      jobInfo.get().properties().add(XTS_TF_JOB_PROP, "true");
      sessionInfo.addJob(jobInfo.get());
      logger.atInfo().log(
          "Added job[%s] to the session %s",
          jobInfo.get().locator().getId(), sessionInfo.getSessionId());

      // Update session output to contain the added job.
      CommandDetail commandDetail =
          CommandDetail.newBuilder()
              .setCommandLine(commandInfo.getCommandLine())
              .setId(jobInfo.get().locator().getId())
              .build();
      sessionInfo.setSessionPluginOutput(
          oldOutput ->
              (oldOutput == null ? RequestDetail.newBuilder() : oldOutput.toBuilder())
                  .setId(sessionInfo.getSessionId())
                  .putCommandDetails(jobInfo.get().locator().getId(), commandDetail)
                  .build(),
          RequestDetail.class);
    }
  }

  private Optional<JobInfo> createXtsTradefedTestJob(
      NewMultiCommandRequest request, CommandInfo commandInfo, SessionInfo sessionInfo)
      throws MobileHarnessException, InterruptedException {
    // TODO: need to handle sharding.
    List<String> deviceSerials = new ArrayList<>();
    for (Entry<String, String> entry : commandInfo.getDeviceDimensionsMap().entrySet()) {
      if (entry.getKey().equals("device_serial")) {
        deviceSerials.add(entry.getValue());
      }
      // TODO: need to handle non device serial case.
    }
    String androidXtsZipPath = "";
    for (TestResource testResource : request.getTestResourcesList()) {
      if (ANDROID_XTS_ZIP_FILENAME_REGEX.matcher(testResource.getName()).matches()
          && testResource.getUrl().startsWith(FILE_URL_PREFIX)) {
        androidXtsZipPath = testResource.getUrl().substring(FILE_URL_PREFIX.length());
        break;
      }
    }
    if (androidXtsZipPath.isEmpty()) {
      logger.atInfo().log(
          "Didn't find android xts zip file in request resources: %s",
          request.getTestResourcesList());
      return Optional.empty();
    }
    String xtsRootDir =
        PathUtil.join(
            GEN_FILE_DIR,
            "session_" + sessionInfo.getSessionId(),
            Files.getNameWithoutExtension(androidXtsZipPath));
    localFileUtil.prepareDir(xtsRootDir);
    mountZip(androidXtsZipPath, xtsRootDir);
    int shardCount = commandInfo.getShardCount();
    String xtsType = Iterables.get(Splitter.on(' ').split(commandInfo.getCommandLine()), 0);
    String testPlan = xtsType;
    SessionRequestHandlerUtil.SessionRequestInfo.Builder sessionRequestInfoBuilder =
        SessionRequestHandlerUtil.SessionRequestInfo.builder();
    sessionRequestInfoBuilder.setTestPlan(testPlan);
    sessionRequestInfoBuilder.setXtsType(XtsType.valueOf(xtsType.toUpperCase(Locale.ROOT)));
    sessionRequestInfoBuilder.setXtsRootDir(xtsRootDir);
    sessionRequestInfoBuilder.setAndroidXtsZip(androidXtsZipPath);
    sessionRequestInfoBuilder.setDeviceSerials(deviceSerials);
    sessionRequestInfoBuilder.setShardCount(shardCount);
    // TODO:add modules and extra args
    sessionRequestInfoBuilder.setModuleNames(ImmutableList.of());
    sessionRequestInfoBuilder.setExtraArgs(ImmutableList.of());

    unmountZip(xtsRootDir);
    return sessionRequestHandlerUtil.createXtsTradefedTestJob(sessionRequestInfoBuilder.build());
  }

  void handleResultProcessing(
      NewMultiCommandRequest newMultiCommandRequest, SessionInfo sessionInfo) {
    // TODO: To be implemented.
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
