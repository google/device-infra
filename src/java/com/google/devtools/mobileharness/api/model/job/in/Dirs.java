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

package com.google.devtools.mobileharness.api.model.job.in;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.util.Optional;
import javax.annotation.Nullable;

/** Directory settings of a job. */
public class Dirs {

  private final String genFileDir;
  private final String tmpFileDir;
  private final String runFileDir;
  @Nullable private final String remoteFileDir;
  private final boolean hasTestSubdirs;

  private final LocalFileUtil fileUtil;

  public Dirs(
      String genFileDir,
      String tmpFileDir,
      String runFileDir,
      @Nullable String remoteFileDir,
      boolean hasTestSubdirs,
      LocalFileUtil localFileUtil) {
    this.genFileDir = genFileDir;
    this.tmpFileDir = tmpFileDir;
    this.runFileDir = runFileDir;
    this.remoteFileDir = remoteFileDir;
    this.hasTestSubdirs = hasTestSubdirs;
    this.fileUtil = localFileUtil;
  }

  /**
   * The directory for job output files. Files in this directory will be sent from MH lab to MH
   * client and be uploaded to Sponge.
   *
   * <p>The directory will be created if it does not exist.
   */
  public String genFileDir() throws MobileHarnessException {
    try {
      fileUtil.prepareDir(genFileDir);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_OR_TEST_GEN_FILE_DIR_PREPARE_ERROR,
          "Failed to create job GEN_FILE dir",
          e);
    }
    return genFileDir;
  }

  /**
   * @see #genFileDir()
   */
  public boolean hasGenFileDir() {
    return fileUtil.isDirExist(genFileDir);
  }

  /**
   * The directory for job temp files.
   *
   * <p>The directory will be created if it does not exist.
   */
  public String tmpFileDir() throws MobileHarnessException {
    try {
      fileUtil.prepareDir(tmpFileDir);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_OR_TEST_TMP_FILE_DIR_PREPARE_ERROR,
          "Failed to create job TMP_FILE dir",
          e);
    }
    return tmpFileDir;
  }

  /**
   * @see #tmpFileDir()
   */
  public boolean hasTmpFileDir() {
    return fileUtil.isDirExist(tmpFileDir);
  }

  /**
   * The directory for job input files.
   *
   * <p>Note that plugin/driver/decorator should <b>not</b> write in this directory. They should use
   * {@link #genFileDir()} or {@link #tmpFileDir()} instead. This directory is managed by Mobile
   * Harness infrastructure.
   *
   * <p>The directory will be created if it does not exist.
   */
  public String runFileDir() throws MobileHarnessException {
    try {
      fileUtil.prepareDir(runFileDir);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_OR_TEST_RUN_FILE_DIR_PREPARE_ERROR,
          "Failed to create job RUN_FILE dir",
          e);
    }
    return runFileDir;
  }

  /**
   * @see #runFileDir()
   */
  public boolean hasRunFileDir() {
    return fileUtil.isDirExist(runFileDir);
  }

  /** The remote directory to which a MH job client plugin can upload files. */
  public Optional<String> remoteFileDir() {
    return Optional.ofNullable(remoteFileDir);
  }

  /**
   * @return whether tests of the job have their own sub directories (for gen/tmp files)
   */
  public boolean hasTestSubdirs() {
    return hasTestSubdirs;
  }
}
