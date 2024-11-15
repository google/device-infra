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

package com.google.devtools.mobileharness.shared.file.resolver;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.net.UrlEscapers;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.deviceinfra.shared.util.file.remote.constant.RemoteFileType;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandFailureException;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import javax.annotation.Nullable;

/** The resolver for files in ats file server. */
public class AtsFileServerFileResolver extends AbstractFileResolver {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @ParamAnnotation(
      required = false,
      help =
          "whether or not to resolve ATS file server files in lab server. By default, it's resolved"
              + " in client.")
  public static final String PARAM_RESOLVE_ATS_FILE_SERVER_FILES_IN_LAB =
      "resolve_ats_file_server_files_in_lab";

  private final LocalFileUtil localFileUtil;

  public AtsFileServerFileResolver(
      @Nullable ListeningExecutorService executorService, LocalFileUtil localFileUtil) {
    super(executorService);
    this.localFileUtil = localFileUtil;
  }

  @Override
  protected boolean shouldActuallyResolve(ResolveSource resolveSource) {
    return resolveSource.path().startsWith(RemoteFileType.ATS_FILE_SERVER.prefix());
  }

  @Override
  protected ResolveResult actuallyResolve(ResolveSource resolveSource)
      throws InterruptedException, MobileHarnessException {
    String path = resolveSource.path();
    if (Boolean.parseBoolean(
        resolveSource.parameters().get(PARAM_RESOLVE_ATS_FILE_SERVER_FILES_IN_LAB))) {
      return ResolveResult.create(ImmutableList.of(path), ImmutableMap.of(), resolveSource);
    } else {
      String sourcePath = path.replace(RemoteFileType.ATS_FILE_SERVER.prefix(), "");
      String httpSourcePath =
          String.join(
              "/",
              Flags.instance().atsFileServer.getNonNull(),
              PathUtil.join("file", UrlEscapers.urlFragmentEscaper().escape(sourcePath)));
      try {
        httpSourcePath = new URI(httpSourcePath).normalize().toString();
        String destination = PathUtil.join(resolveSource.targetDir(), sourcePath);
        localFileUtil.prepareParentDir(destination);
        String output =
            createCommandExecutor()
                .run(
                    Command.of("curl", "-o", destination, "-fL", httpSourcePath)
                        .timeout(Duration.ofHours(2)));
        logger.atInfo().log(
            "Output of ats file server downloader for downloading file %s: %s",
            httpSourcePath, output);
        return ResolveResult.create(
            ImmutableList.of(destination), ImmutableMap.of(), resolveSource);
      } catch (URISyntaxException e) {
        throw new MobileHarnessException(
            BasicErrorId.HTTP_INVALID_FILE_PATH_ERROR,
            String.format("Invalid file path %s in ats file server.", httpSourcePath),
            e);
      } catch (CommandException e) {
        if (e instanceof CommandFailureException) {
          CommandResult result = ((CommandFailureException) e).result();
          logger.atWarning().log(
              "Logs of failed ats file downloader: STDOUT: %s\nSTDERR: %s",
              result.stdout(), result.stderr());
        }
        throw new MobileHarnessException(
            BasicErrorId.ATS_FILE_SERVER_RESOLVE_FILE_ERROR,
            String.format("Failed to download file %s from ats file server.", httpSourcePath),
            e);
      }
    }
  }

  @VisibleForTesting
  CommandExecutor createCommandExecutor() {
    return new CommandExecutor();
  }
}
