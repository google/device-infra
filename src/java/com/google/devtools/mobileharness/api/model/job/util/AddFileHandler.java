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

package com.google.devtools.mobileharness.api.model.job.util;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;

/** The extra file operation when adding a file or directory. */
public abstract class AddFileHandler {

  /** An empty adding file handler. */
  public static final AddFileHandler EMPTY = new EmptyAddFileHandler();

  private static class EmptyAddFileHandler extends AddFileHandler {

    @Override
    public void handleFileOrDir(String tag, String originalFileOrDirPath) {
      // Does nothing.
    }
  }

  protected AddFileHandler() {}

  /**
   * @return the target file or directory after handling.
   * @param originalFileOrDirPath the original file or directory path
   */
  public String getTargetFileOrDirPath(String originalFileOrDirPath) {
    return originalFileOrDirPath;
  }

  /**
   * The extra operation when adding a file or directory.
   *
   * @param tag file tag
   * @param originalFileOrDirPath the original file or directory path
   */
  public abstract void handleFileOrDir(String tag, String originalFileOrDirPath)
      throws MobileHarnessException, InterruptedException;
}
