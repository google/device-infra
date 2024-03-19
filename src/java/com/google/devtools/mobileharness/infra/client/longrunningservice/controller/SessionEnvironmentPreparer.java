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

package com.google.devtools.mobileharness.infra.client.longrunningservice.controller;

import com.google.auto.value.AutoValue;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionDetailHolder;
import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.constant.DirCommon;
import java.nio.file.Path;
import javax.inject.Inject;

/** Preparer for preparing environment for a session. */
class SessionEnvironmentPreparer {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Environment settings of a session. */
  @AutoValue
  abstract static class SessionEnvironment implements NonThrowingAutoCloseable {

    abstract Path sessionGenDir();

    abstract Path sessionTempDir();

    abstract LocalFileUtil localFileUtil();

    static SessionEnvironment of(
        Path sessionGenDir, Path sessionTempDir, LocalFileUtil localFileUtil) {
      return new AutoValue_SessionEnvironmentPreparer_SessionEnvironment(
          sessionGenDir, sessionTempDir, localFileUtil);
    }

    @Override
    public void close() {
      boolean interrupted = Thread.interrupted();
      removeDir(sessionGenDir());
      interrupted |= Thread.interrupted();
      removeDir(sessionTempDir());
      interrupted |= Thread.interrupted();
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }

    private void removeDir(Path dir) {
      logger.atInfo().log("Removing %s", dir);
      try {
        localFileUtil().removeFileOrDir(dir);
      } catch (MobileHarnessException | InterruptedException e) {
        logger.atWarning().withCause(e).log("Failed to remove %s", dir);
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  private final LocalFileUtil localFileUtil;

  @Inject
  SessionEnvironmentPreparer(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  SessionEnvironment prepareEnvironment(SessionDetailHolder sessionDetailHolder)
      throws MobileHarnessException {
    String sessionId = sessionDetailHolder.getSessionId();
    Path sessionGenDir =
        Path.of(DirCommon.getPublicDirRoot(), "olc_server_gen_files", "session_" + sessionId);
    Path sessionTempDir =
        Path.of(DirCommon.getPublicDirRoot(), "olc_server_temp_files", "session_" + sessionId);
    localFileUtil.prepareDir(sessionGenDir.toString());
    localFileUtil.prepareDir(sessionTempDir.toString());
    localFileUtil.grantFileOrDirFullAccess(sessionGenDir);
    localFileUtil.grantFileOrDirFullAccess(sessionTempDir);
    return SessionEnvironment.of(sessionGenDir, sessionTempDir, localFileUtil);
  }
}
