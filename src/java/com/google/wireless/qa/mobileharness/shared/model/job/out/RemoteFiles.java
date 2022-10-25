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

package com.google.wireless.qa.mobileharness.shared.model.job.out;


import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The files for a job/test which have been uploaded to a remote file system like CNS. */
public class RemoteFiles {
  /** Logger of the job/test. */
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The time records of the job/test. */
  private final Timing timing;

  /** The set of generated files of the job/test. */
  private final Set<String> generatedFiles = ConcurrentHashMap.newKeySet();

  /** The root path. */
  private final Optional<String> rootPath;

  public RemoteFiles(Timing timing, Optional<String> rootPath) {
    this.timing = timing;
    this.rootPath = rootPath;
  }

  /**
   * Adds a generated file of the job/test which has been copied to a remote file system to this
   * class.
   *
   * @param filePath the file path of the remote file
   */
  @CanIgnoreReturnValue
  public RemoteFiles add(String filePath) {
    if (generatedFiles.add(filePath)) {
      timing.touch();
      logger.atInfo().log("Successfully added remote generated file %s", filePath);
    } else {
      logger.atInfo().log("File %s has been added to the generated file set.", filePath);
    }
    return this;
  }

  /**
   * Adds a collection of remote files to the generated file set.
   *
   * @param filePaths the collection of paths of remote files
   */
  @CanIgnoreReturnValue
  public RemoteFiles addAll(Collection<String> filePaths) {
    filePaths.forEach(this::add);
    return this;
  }

  /** Returns if the stored remote generated file set is empty. */
  public boolean isEmpty() {
    return generatedFiles.isEmpty();
  }

  /** Checks if the given remote file has been added to the generated file set. */
  public boolean isExist(String filePath) {
    return generatedFiles.contains(filePath);
  }

  /** Gets the set of all the remote generated files. */
  public Collection<String> getAll() {
    return ImmutableSet.copyOf(generatedFiles);
  }

  /** Gets the root path for remote files. */
  public Optional<String> getRootPath() {
    return rootPath;
  }
}
