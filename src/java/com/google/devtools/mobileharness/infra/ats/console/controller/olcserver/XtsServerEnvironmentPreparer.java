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

package com.google.devtools.mobileharness.infra.ats.console.controller.olcserver;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.util.command.CommandHelper;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsCommandUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.nio.file.Path;
import javax.inject.Inject;

/**
 * {@link ServerEnvironmentPreparer} implementation which copies JDK and binaries from xTS dir to
 * xTS resource dir.
 */
public class XtsServerEnvironmentPreparer implements ServerEnvironmentPreparer {

  private final ConsoleInfo consoleInfo;
  private final CommandHelper commandHelper;
  private final LocalFileUtil localFileUtil;

  @Inject
  XtsServerEnvironmentPreparer(
      ConsoleInfo consoleInfo, CommandHelper commandHelper, LocalFileUtil localFileUtil) {
    this.consoleInfo = consoleInfo;
    this.commandHelper = commandHelper;
    this.localFileUtil = localFileUtil;
  }

  @Override
  public ServerEnvironment prepareServerEnvironment() throws MobileHarnessException {
    // TODO: Copies JDK and server binary and grants file access.
    Path serverBinary = Path.of(Flags.instance().atsConsoleOlcServerPath.getNonNull());
    Path javaBinary =
        XtsCommandUtil.getJavaBinary(
            commandHelper.getXtsType(), consoleInfo.getXtsRootDirectoryNonEmpty());
    localFileUtil.checkFile(serverBinary);
    localFileUtil.checkFile(javaBinary);
    return ServerEnvironment.of(serverBinary, javaBinary);
  }
}
