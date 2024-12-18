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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.TouchableTiming;
import com.google.devtools.mobileharness.service.moss.proto.Slg.FileProto;
import com.google.devtools.mobileharness.service.moss.proto.Slg.FilesProto;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/** Input files of a job/test. */
@Beta
public class Files {
  /** Logger of this job/test. */
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** File Info. */
  @AutoValue
  public abstract static class FileInfo {
    public static FileInfo create(String path, @Nullable String checksum) {
      return new AutoValue_Files_FileInfo(path, Optional.ofNullable(checksum));
    }

    public abstract String path();

    /**
     * The checksum of the file.
     *
     * <p>Different file/dir types can have different checksum formats.
     */
    public abstract Optional<String> checksum();
  }

  /**
   * The input files/dirs of the job/test. It's a two-level map:
   *
   * <p>The outer map is keyed by the tag of the file/dir. The inner map is keyed by the path of the
   * file/dir.
   */
  @GuardedBy("this")
  private final Map<String, Map<String, FileInfo>> fileOrDirs = new HashMap<>();

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

  public Files(
      @Nullable TouchableTiming timing, @Nullable LocalFileUtil fileUtil, FilesProto filesProto) {
    this(timing, fileUtil);
    for (FileProto file : filesProto.getFileList()) {
      try {
        add(file.getTag(), file.getLocation());
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to add file %s: %s", file.getTag(), file.getLocation());
      }
    }
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
      Map<String, FileInfo> tagFileOrDirs = getTagFileOrDirs(tag);
      if (tagFileOrDirs.containsKey(fileOrDirPath)) {
        logger.atInfo().log(
            "Input file/dir [%s]%s already exist", tag, fileUtil.getFileOrDirName(fileOrDirPath));
        return this;
      }
      tagFileOrDirs.put(fileOrDirPath, FileInfo.create(fileOrDirPath, null));
    }
    if (timing != null) {
      timing.touch();
    }
    logger.atInfo().log(
        "Added input file/dir [%s]%s", tag, fileUtil.getFileOrDirName(fileOrDirPath));
    return this;
  }

  /**
   * Gets the files with the given tag.
   *
   * <p>If the tag is not found, a new map will be created and returned.
   */
  private Map<String, FileInfo> getTagFileOrDirs(String tag) {
    synchronized (this) {
      return fileOrDirs.computeIfAbsent(tag, (String k) -> new LinkedHashMap<>());
    }
  }

  /**
   * Adds the given input files/dirs.
   *
   * @param inputFileOrDirs {tag, path} mappings of the input files/dirs
   */
  @CanIgnoreReturnValue
  public Files addAll(Map<String, String> inputFileOrDirs) throws MobileHarnessException {
    for (Entry<String, String> inputFileOrDir : inputFileOrDirs.entrySet()) {
      add(inputFileOrDir.getKey(), inputFileOrDir.getValue());
    }
    return this;
  }

  /**
   * Adds the given input files/dirs.
   *
   * @param inputFileOrDirs {tag, path} mappings of the input files/dirs
   */
  @CanIgnoreReturnValue
  public Files addAll(Multimap<String, String> inputFileOrDirs) throws MobileHarnessException {
    for (Entry<String, String> inputFileOrDir : inputFileOrDirs.entries()) {
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
  public Files replaceFileInfos(
      String tag, String originalFileOrDirPath, Collection<FileInfo> newFileOrDirInfos)
      throws MobileHarnessException {
    for (FileInfo newFileOrDirInfo : newFileOrDirInfos) {
      checkFileOrDirIfLocal(newFileOrDirInfo.path());
    }
    synchronized (this) {
      // Remove original files.
      Map<String, FileInfo> tagFileOrDirs = getTagFileOrDirs(tag);
      if (tagFileOrDirs.remove(originalFileOrDirPath) == null) {
        throw new MobileHarnessException(
            BasicErrorId.JOB_OR_TEST_REPLACE_NONEXIST_FILE,
            String.format("Original input file/dir [%s]%s not found", tag, originalFileOrDirPath));
      } else {
        logger.atInfo().log(
            "Removed original input file/dir [%s]%s",
            tag, fileUtil.getFileOrDirName(originalFileOrDirPath));
      }
      for (FileInfo newFileOrDirInfo : newFileOrDirInfos) {
        if (tagFileOrDirs.containsKey(newFileOrDirInfo.path())) {
          logger.atInfo().log(
              "New input file/dir [%s]%s already exist",
              tag, fileUtil.getFileOrDirName(newFileOrDirInfo.path()));
        } else {
          tagFileOrDirs.put(newFileOrDirInfo.path(), newFileOrDirInfo);
          logger.atInfo().log(
              "Added new input file/dir [%s]%s",
              tag, fileUtil.getFileOrDirName(newFileOrDirInfo.path()));
        }
      }
      if (timing != null) {
        timing.touch();
      }
    }
    return this;
  }

  /**
   * Replaces the input file/dir with a new set of file/dir.
   *
   * @throws MobileHarnessException if the original/new input files/dirs doesn't exist
   */
  @CanIgnoreReturnValue
  public Files replace(
      String tag, String originalFileOrDirPath, Collection<String> newFileOrDirPaths)
      throws MobileHarnessException {
    return replaceFileInfos(
        tag,
        originalFileOrDirPath,
        newFileOrDirPaths.stream()
            .map(path -> FileInfo.create(path, null))
            .collect(toImmutableList()));
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

    Map<String, FileInfo> tagFileOrDirs;
    synchronized (this) {
      tagFileOrDirs = getTagFileOrDirs(tag);
      tagFileOrDirs.clear();
      for (String newFileOrDirPath : newFileOrDirPaths) {
        tagFileOrDirs.put(newFileOrDirPath, FileInfo.create(newFileOrDirPath, null));
      }
    }
    logger.atInfo().log(
        "Replaced file/dir [%s], original=%s, new=%s", tag, tagFileOrDirs, newFileOrDirPaths);
    if (timing != null) {
      timing.touch();
    }
    return this;
  }

  /** Returns whether the file map is empty. */
  public synchronized boolean isEmpty() {
    for (Map<String, FileInfo> tagFiles : fileOrDirs.values()) {
      if (!tagFiles.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Finds a single input file/dir marked with the given tag.
   *
   * @param tag file/dir tag
   * @return the full path of the file/dir with the given tag
   * @throws MobileHarnessException when tag did not match exactly one input file/dir
   */
  public synchronized String getSingle(String tag) throws MobileHarnessException {
    Map<String, FileInfo> tagFileOrDirs = getTagFileOrDirs(tag);
    if (tagFileOrDirs.isEmpty()) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_OR_TEST_FILE_NOT_FOUND,
          String.format("No input file/dir tagged with [%s]", tag));
    } else if (tagFileOrDirs.size() > 1) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_OR_TEST_FILE_MULTI_PATHS,
          String.format(
              "More than one input files/dirs %s are tagged with [%s]", tagFileOrDirs, tag));
    } else {
      return Iterables.getOnlyElement(tagFileOrDirs.keySet());
    }
  }

  /**
   * Finds the input files/dirs marked with the given tag.
   *
   * @return a copy of the file/dir full path, or <b>an empty set</b> if there is no file/dir with
   *     the given tag
   */
  public synchronized ImmutableSet<String> get(String tag) {
    Map<String, FileInfo> tagFilesOrDirs = getTagFileOrDirs(tag);
    // Returns a new copy of the set to avoid concurrency issue.
    return ImmutableSet.copyOf(tagFilesOrDirs.keySet());
  }

  /** Gets the {tag, path} mapping of all the input files/dirs. */
  public synchronized ImmutableMultimap<String, String> getAll() {
    ImmutableMultimap.Builder<String, String> builder = ImmutableMultimap.builder();
    for (Map.Entry<String, Map<String, FileInfo>> entry : fileOrDirs.entrySet()) {
      for (Map.Entry<String, FileInfo> fileEntry : entry.getValue().entrySet()) {
        builder.put(entry.getKey(), fileEntry.getKey());
      }
    }
    return builder.build();
  }

  /** Gets the {tag, FileInfo} mapping of all the input files/dirs. */
  public synchronized ImmutableMultimap<String, FileInfo> getAllFileInfos() {
    ImmutableMultimap.Builder<String, FileInfo> builder = ImmutableMultimap.builder();
    for (Map.Entry<String, Map<String, FileInfo>> entry : fileOrDirs.entrySet()) {
      builder.putAll(entry.getKey(), entry.getValue().values());
    }
    return builder.build();
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
    Map<String, FileInfo> paths = fileOrDirs.get(tag);
    return paths != null && !paths.isEmpty();
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
      Map<String, FileInfo> tagFileOrDirs = getTagFileOrDirs(tag);
      if (tagFileOrDirs.isEmpty()) {
        emptyTags.add(tag);
      } else if (tagFileOrDirs.size() != 1) {
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
    boolean changed = false;
    Map<String, FileInfo> tagFiles = fileOrDirs.get(tag);
    if (tagFiles != null) {
      changed = tagFiles.remove(fileOrDirPath) != null;
    }
    if (changed && timing != null) {
      timing.touch();
    }
    return changed;
  }

  @Override
  public synchronized String toString() {
    StringBuilder stringBuilder = new StringBuilder("Files:\n");

    for (Entry<String, Map<String, FileInfo>> entry : fileOrDirs.entrySet()) {
      stringBuilder.append(String.format("\tTag: %s\n", entry.getKey()));
      for (Entry<String, FileInfo> fileEntry : entry.getValue().entrySet()) {
        stringBuilder.append(String.format("\t\tPath: %s\n", fileEntry.getKey()));
      }
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
