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

import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.DEBUG;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.util.command.CommandHelper;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsCommandUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.nio.file.Path;
import javax.inject.Inject;

/**
 * {@link ServerEnvironmentPreparer} implementation which copies JDK and binaries from xTS dir to
 * xTS resource dir.
 */
public class XtsServerEnvironmentPreparer implements ServerEnvironmentPreparer {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ConsoleInfo consoleInfo;
  private final CommandHelper commandHelper;
  private final LocalFileUtil localFileUtil;
  private final XtsCommandUtil xtsCommandUtil;

  @Inject
  XtsServerEnvironmentPreparer(
      ConsoleInfo consoleInfo,
      CommandHelper commandHelper,
      LocalFileUtil localFileUtil,
      XtsCommandUtil xtsCommandUtil) {
    this.consoleInfo = consoleInfo;
    this.commandHelper = commandHelper;
    this.localFileUtil = localFileUtil;
    this.xtsCommandUtil = xtsCommandUtil;
  }

  @Override
  public ServerEnvironment prepareServerEnvironment()
      throws MobileHarnessException, InterruptedException {
    Path serverBinary;
    Path javaBinary;

    String xtsType = commandHelper.getXtsType();
    Path xtsRootDir = consoleInfo.getXtsRootDirectoryNonEmpty();
    Path initialServerBinary = Path.of(Flags.instance().atsConsoleOlcServerPath.getNonNull());

    Path xtsServerResDirRoot = Path.of(Flags.instance().xtsServerResDirRoot.getNonNull());

    // Prepare olc server work dir.
    Path serverWorkDir = xtsServerResDirRoot.resolve("olc_server_work");
    localFileUtil.prepareDir(serverWorkDir);

    if (Flags.instance().atsConsoleOlcServerCopyServerResource.getNonNull()) {
      // Prepares server resource dir.
      Path serverResDir = xtsServerResDirRoot.resolve("olc_server_res");
      logger.atInfo().with(IMPORTANCE, DEBUG).log("Preparing xTS resource dir [%s]", serverResDir);
      localFileUtil.prepareDir(serverResDir);
      grantFileOrDirFullAccess(serverResDir);

      // Removes all files under server resource dir.
      logger
          .atInfo()
          .with(IMPORTANCE, DEBUG)
          .log("Cleaning up xTS resource dir [%s]", serverResDir);
      localFileUtil.removeFilesOrDirs(serverResDir);

      // Copies server binary into server resource dir and grants access.
      logger
          .atInfo()
          .with(IMPORTANCE, DEBUG)
          .log(
              "Copying OLC server binary [%s] into xTS resource dir [%s]",
              initialServerBinary, serverResDir);
      localFileUtil.checkFile(initialServerBinary);
      localFileUtil.copyFileOrDir(initialServerBinary, serverResDir);
      serverBinary = serverResDir.resolve(initialServerBinary.getFileName());
      grantFileOrDirFullAccess(serverBinary);
      javaBinary = getJavaBinaryPath(xtsType, xtsRootDir, serverResDir);
    } else {
      serverBinary = initialServerBinary;
      javaBinary = getJavaBinaryPath(xtsType, xtsRootDir, /* serverResDir= */ null);
    }

    // Checks files.
    localFileUtil.checkFile(serverBinary);
    localFileUtil.checkFile(javaBinary);
    return ServerEnvironment.of(serverBinary, javaBinary, serverWorkDir);
  }

  /** Grants full access to a file/dir and ignores errors if any. */
  private void grantFileOrDirFullAccess(Path path) {
    try {
      localFileUtil.grantFileOrDirFullAccess(path);
    } catch (MobileHarnessException e) {
      logger
          .atInfo()
          .with(IMPORTANCE, DEBUG)
          .withCause(e)
          .log("Failed to grant access to [%s]", path);
    }
  }

  /**
   * Gets the Java binary path. If the flag --xts_jdk_dir is set, use the JDK files from the flag
   * value. Otherwise, if the flag --ats_console_olc_server_copy_server_resource is unset, use the
   * JDK files from the xTS root dir. Otherwise, use the JDK files from the system.
   */
  private Path getJavaBinaryPath(String xtsType, Path xtsRootDir, Path serverResDir)
      throws MobileHarnessException, InterruptedException {
    if (!Flags.instance().xtsJdkDir.getNonNull().isEmpty()) {
      logger
          .atInfo()
          .with(IMPORTANCE, IMPORTANT)
          .log(
              "Use the JDK files from %s passed in via the flag --xts_jdk_dir for the server",
              Flags.instance().xtsJdkDir.getNonNull());
      Path jdkDir = Path.of(Flags.instance().xtsJdkDir.getNonNull());
      grantFileOrDirFullAccess(jdkDir);
      return jdkDir.resolve("bin/java");
    }
    if (!Flags.instance().atsConsoleOlcServerCopyServerResource.getNonNull()) {
      return xtsCommandUtil.getJavaBinary(xtsType, xtsRootDir);
    }
    boolean useXtsJavaBinary = xtsCommandUtil.useXtsJavaBinary(xtsType, xtsRootDir);
    if (useXtsJavaBinary && serverResDir != null) {
      // Copies JDK dir into server resource dir and grants access.
      Path xtsJdkDir = XtsDirUtil.getXtsJdkDir(xtsRootDir, xtsType);
      logger
          .atInfo()
          .with(IMPORTANCE, DEBUG)
          .log("Copying xTS JDK dir [%s] into xTS resource dir [%s]", xtsJdkDir, serverResDir);
      localFileUtil.checkDir(xtsJdkDir);
      localFileUtil.copyFileOrDir(xtsJdkDir, serverResDir);
      Path jdkDir = serverResDir.resolve(xtsJdkDir.getFileName());
      grantFileOrDirFullAccess(jdkDir);
      return jdkDir.resolve("bin/java");
    } else {
      return xtsCommandUtil.getJavaBinary(xtsType, xtsRootDir);
    }
  }
}
