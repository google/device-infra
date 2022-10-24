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

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder.SetMultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.TouchableTiming;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/** Input files of a job/test. */
@Beta
public class Files {
  /** Logger of this job/test. */
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** {Tag, FilePathAndNames} mapping contains the files needed by this job/test. */
  @GuardedBy("this")
  private final SetMultimap<String, String> fileOrDirs =
      SetMultimapBuilder.linkedHashKeys().linkedHashSetValues().build();

  /** The time records of the job/test. */
  @Nullable private final TouchableTiming timing;

  /** File util for checking local files. */
  private final LocalFileUtil fileUtil;

  /** Creates the input file segment of a job/test. */
  public Files() {
    this(null, null);
  }

  /** Creates the input file segment of a job/test. */
  public Files(@Nullable TouchableTiming timing, @Nullable LocalFileUtil fileUtil) {
    this.timing = timing;
    this.fileUtil = fileUtil == null ? new LocalFileUtil() : fileUtil;
  }

  /**
   * Adds a input file/dir, marks it with the given tag. If you add the same file/dir with the same
   * tag for more than once, the call leaves it unchanged.
   *
   * @param tag tag of the file/dir
   * @param fileOrDirPath input file/dir path
   */
  @CanIgnoreReturnValue
  public Files add(String tag, String fileOrDirPath) throws MobileHarnessException {
    try {
      checkFileOrDirIfLocal(fileOrDirPath);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_OR_TEST_FILE_ADD_NONEXIST_FILE,
          String.format("Failed to add non-existing file [%s]%s", tag, fileOrDirPath),
          e);
    }
    synchronized (this) {
      if (fileOrDirs.containsEntry(tag, fileOrDirPath)) {
        logger.atInfo().log(
            "Input file/dir [%s]%s already exist", tag, fileUtil.getFileOrDirName(fileOrDirPath));
        return this;
      }
      fileOrDirs.put(tag, fileOrDirPath);
    }
    if (timing != null) {
      timing.touch();
    }
    logger.atInfo().log(
        "Added input file/dir [%s]%s", tag, fileUtil.getFileOrDirName(fileOrDirPath));
    return this;
  }

  /**
   * Adds the given input files/dirs.
   *
   * @param fileOrDirs {tag, path} mappings of the input files/dirs
   */
  @CanIgnoreReturnValue
  public Files addAll(Map<String, String> fileOrDirs) throws MobileHarnessException {
    for (Entry<String, String> inputFileOrDir : fileOrDirs.entrySet()) {
      add(inputFileOrDir.getKey(), inputFileOrDir.getValue());
    }
    return this;
  }

  /**
   * Adds the given input files/dirs.
   *
   * @param fileOrDirs {tag, path} mappings of the input files/dirs
   */
  @CanIgnoreReturnValue
  public Files addAll(Multimap<String, String> fileOrDirs) throws MobileHarnessException {
    for (Entry<String, String> inputFileOrDir : fileOrDirs.entries()) {
      add(inputFileOrDir.getKey(), inputFileOrDir.getValue());
    }
    return this;
  }

  /**
   * Replaces the input file/dir with a new set of file/dir.
   *
   * @throws MobileHarnessException if the original/new input files/dirs doesn't exists
   */
  @CanIgnoreReturnValue
  public Files replace(
      String tag, String originalFileOrDirPath, Collection<String> newFileOrDirPaths)
      throws MobileHarnessException {
    for (String newFileOrDirPath : newFileOrDirPaths) {
      checkFileOrDirIfLocal(newFileOrDirPath);
    }
    synchronized (this) {
      // Remove original files.
      Set<String> originalFileOrDirs = fileOrDirs.get(tag);
      if (!originalFileOrDirs.remove(originalFileOrDirPath)) {
        throw new MobileHarnessException(
            BasicErrorId.JOB_OR_TEST_REPLACE_NONEXIST_FILE,
            String.format("Original input file/dir [%s]%s not found", tag, originalFileOrDirPath));
      } else {
        logger.atInfo().log(
            "Removed original input file/dir [%s]%s",
            tag, fileUtil.getFileOrDirName(originalFileOrDirPath));
      }
      for (String newFileOrDirPath : newFileOrDirPaths) {
        if (fileOrDirs.containsEntry(tag, newFileOrDirPath)) {
          logger.atInfo().log(
              "New input file/dir [%s]%s already exist",
              tag, fileUtil.getFileOrDirName(newFileOrDirPath));
        } else {
          fileOrDirs.put(tag, newFileOrDirPath);
          logger.atInfo().log(
              "Added new input file/dir [%s]%s", tag, fileUtil.getFileOrDirName(newFileOrDirPath));
        }
      }
      if (timing != null) {
        timing.touch();
      }
    }
    return this;
  }

  /**
   * Replaces all file/dir of a tag with a new set of file/dir.
   *
   * @throws MobileHarnessException if the new files/dir does not exist
   */
  @CanIgnoreReturnValue
  public Files replaceAll(String tag, Collection<String> newFileOrDirPaths)
      throws MobileHarnessException {
    for (String newFileOrDirPath : newFileOrDirPaths) {
      checkFileOrDirIfLocal(newFileOrDirPath);
    }
    Set<String> originalFileOrDirs;
    synchronized (this) {
      originalFileOrDirs = fileOrDirs.replaceValues(tag, newFileOrDirPaths);
    }
    logger.atInfo().log(
        "Replaced file/dir [%s], original=%s, new=%s", tag, originalFileOrDirs, newFileOrDirPaths);
    if (timing != null) {
      timing.touch();
    }
    return this;
  }

  /** Returns whether the file map is empty. */
  public synchronized boolean isEmpty() {
    return fileOrDirs.isEmpty();
  }

  /**
   * Finds a single input file/dir marked with the given tag.
   *
   * @param tag file/dir tag
   * @return the full path of the file/dir with the given tag
   * @throws MobileHarnessException when tag did not match exactly one input file/dir
   */
  public synchronized String getSingle(String tag) throws MobileHarnessException {
    Set<String> taggedFileOrDirs = fileOrDirs.get(tag);
    if (taggedFileOrDirs.isEmpty()) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_OR_TEST_FILE_NOT_FOUND,
          String.format("No input file/dir tagged with [%s]", tag));
    } else if (taggedFileOrDirs.size() > 1) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_OR_TEST_FILE_MULTI_PATHS,
          String.format(
              "More than one input files/dirs %s are tagged with [%s]", taggedFileOrDirs, tag));
    } else {
      return Iterables.getOnlyElement(taggedFileOrDirs);
    }
  }

  /**
   * Finds the input files/dirs marked with the given tag.
   *
   * @return a copy of the file/dir full path, or <b>an empty set</b> if there is no file/dir with
   *     the given tag
   */
  public synchronized ImmutableSet<String> get(String tag) {
    Set<String> taggedInputFilesOrDirs = fileOrDirs.get(tag);
    // Returns a new copy of the set to avoid concurrency issue.
    return ImmutableSet.copyOf(taggedInputFilesOrDirs);
  }

  /** Gets the {tag, path} mapping of all the input files/dirs. */
  public synchronized ImmutableMultimap<String, String> getAll() {
    return ImmutableMultimap.copyOf(fileOrDirs);
  }

  /**
   * Checks whether the given tags are associated with at least one file/dir.
   *
   * @param tags file/folder tags
   * @return error list if there is any tag doesn't map to any path
   */
  public List<MobileHarnessException> validateNotEmpty(String... tags) {
    List<String> errorTags = new ArrayList<>(tags.length);
    for (String tag : tags) {
      if (!isTagNotEmpty(tag)) {
        errorTags.add(tag);
      }
    }
    List<MobileHarnessException> errors = new ArrayList<>();
    if (!errorTags.isEmpty()) {
      errors.add(
          new MobileHarnessException(
              BasicErrorId.JOB_OR_TEST_FILE_NOT_FOUND,
              "No file/dir marked with tag(s) " + errorTags));
    }
    return errors;
  }

  /**
   * Checks whether the given tag is associated with at least one file/dir.
   *
   * @param tag file/folder tags
   * @return true if the tag is associated with at least one file/dir, otherwise return false.
   */
  public synchronized boolean isTagNotEmpty(String tag) {
    Set<String> paths = fileOrDirs.get(tag);
    return !paths.isEmpty();
  }

  /**
   * Checks whether all tags are associated with an unique file/dir.
   *
   * @param tags file/folder tags
   * @return error list if the tag doesn't map to exactly one single path
   */
  public synchronized List<MobileHarnessException> validateUnique(String... tags) {
    List<String> emptyTags = new ArrayList<>(tags.length);
    List<String> duplicatedTags = new ArrayList<>(tags.length);
    for (String tag : tags) {
      Set<String> paths = fileOrDirs.get(tag);
      if (paths.isEmpty()) {
        emptyTags.add(tag);
      } else if (paths.size() != 1) {
        duplicatedTags.add(tag);
      }
    }
    List<MobileHarnessException> errors = new ArrayList<>();
    if (!emptyTags.isEmpty()) {
      errors.add(
          new MobileHarnessException(
              BasicErrorId.JOB_OR_TEST_FILE_NOT_FOUND,
              "No file/dir marked with tag(s) " + emptyTags));
    }
    if (!duplicatedTags.isEmpty()) {
      errors.add(
          new MobileHarnessException(
              BasicErrorId.JOB_OR_TEST_FILE_MULTI_PATHS,
              "More than one files/dirs marked for each tag(s)" + duplicatedTags));
    }
    return errors;
  }

  /**
   * Removes a file/dir with the given tag.
   *
   * @param tag the tag of the file/dir
   * @param fileOrDirPath the path of the file/dir
   * @return whether the files change
   */
  public synchronized boolean remove(String tag, String fileOrDirPath) {
    logger.atInfo().log("Remove file/dir [%s]%s", tag, fileOrDirPath);
    boolean changed = fileOrDirs.remove(tag, fileOrDirPath);
    if (changed && timing != null) {
      timing.touch();
    }
    return changed;
  }

  @Override
  public synchronized String toString() {
    StringBuilder stringBuilder = new StringBuilder("Files:\n");

    for (Entry<String, String> entry : fileOrDirs.entries()) {
      stringBuilder.append(
          String.format("\tTag: %s\n\tPath: %s\n", entry.getKey(), entry.getValue()));
    }

    return stringBuilder.toString();
  }

  /**
   * If it is a local file/dir, makes sure the file/dir exist or throws exception.
   *
   * @return whether it is a local file/dir
   * @throws MobileHarnessException if it is a local file/dir path but doesn't exist
   */
  private boolean checkFileOrDirIfLocal(String fileOrDirPath) throws MobileHarnessException {
    // Only check the existences of the local files. We don't want to invoke the dependencies of all
    // the libraries to handle different file types. So just gives up checking the non-local files.
    boolean isLocalFileOrDir = fileUtil.isLocalFileOrDir(fileOrDirPath);
    if (isLocalFileOrDir) {
      fileUtil.checkFileOrDir(fileOrDirPath);
    }
    return isLocalFileOrDir;
  }
}
