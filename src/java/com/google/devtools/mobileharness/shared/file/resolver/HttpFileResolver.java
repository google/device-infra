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
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveResult;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveSource;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandFailureException;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.annotation.Nullable;

/** The resolver for files in http server. */
public class HttpFileResolver extends AbstractFileResolver {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public HttpFileResolver(@Nullable ListeningExecutorService executorService) {
    super(executorService);
  }

  @Override
  protected boolean shouldActuallyResolve(ResolveSource resolveSource) {
    return resolveSource.path().startsWith("http://")
        || resolveSource.path().startsWith("https://");
  }

  @Override
  protected ResolveResult actuallyResolve(ResolveSource resolveSource)
      throws InterruptedException, MobileHarnessException {
    try {
      String destination =
          PathUtil.join(resolveSource.targetDir(), new URI(resolveSource.path()).toURL().getPath());
      String output =
          createCommandExecutor()
              .run(Command.of("curl", "-o", destination, "-fL", resolveSource.path()));
      logger.atInfo().log(
          "Output of CAS downloader for downloading file %s: %s", resolveSource.path(), output);
      return ResolveResult.create(ImmutableList.of(destination), ImmutableMap.of(), resolveSource);
    } catch (URISyntaxException | MalformedURLException e) {
      throw new MobileHarnessException(
          BasicErrorId.HTTP_INVALID_FILE_PATH_ERROR,
          String.format("Invalid file path %s in http", resolveSource.path()),
          e);
    } catch (CommandException e) {
      if (e instanceof CommandFailureException) {
        CommandResult result = ((CommandFailureException) e).result();
        logger.atWarning().log(
            "Logs of failed http downloader: STDOUT: %s\nSTDERR: %s",
            result.stdout(), result.stderr());
      }
      throw new MobileHarnessException(
          BasicErrorId.HTTP_RESOLVE_FILE_ERROR,
          String.format("Failed to download file %s from http server.", resolveSource.path()),
          e);
    }
  }

  @VisibleForTesting
  CommandExecutor createCommandExecutor() {
    return new CommandExecutor();
  }
}
