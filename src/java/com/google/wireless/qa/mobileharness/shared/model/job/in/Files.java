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

package com.google.wireless.qa.mobileharness.shared.model.job.in;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Input files of a job/test. */
public class Files {
  /**
   * File tag name of the plugin jar with the handler for job/test events. The handler is executed
   * on client side.
   */
  public static final String TAG_CLIENT_PLUGIN = "client_plugin_jar";

  /**
   * File tag name of Plugin jar with the handler for test events. The handler is executed on lab
   * server side.
   */
  public static final String TAG_LAB_PLUGIN = "lab_plugin_jar";

  private final com.google.devtools.mobileharness.api.model.job.in.Files newFiles;

  /** Creates the input file segment of a job/test. */
  public Files() {
    newFiles = new com.google.devtools.mobileharness.api.model.job.in.Files();
  }

  /** Creates the input file segment of a job/test. */
  public Files(Timing timing, LocalFileUtil fileUtil) {
    newFiles =
        new com.google.devtools.mobileharness.api.model.job.in.Files(
            timing.toNewTiming(), fileUtil);
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
    newFiles.add(tag, fileOrDirPath);
    return this;
  }

  /**
   * Adds the given input files/dirs.
   *
   * @param fileOrDirs {tag, path} mappings of the input files/dirs
   */
  @CanIgnoreReturnValue
  public Files addAll(Map<String, String> fileOrDirs) throws MobileHarnessException {
    newFiles.addAll(fileOrDirs);
    return this;
  }

  /**
   * Adds the given input files/dirs.
   *
   * @param fileOrDirs {tag, path} mappings of the input files/dirs
   */
  @CanIgnoreReturnValue
  public Files addAll(Multimap<String, String> fileOrDirs) throws MobileHarnessException {
    newFiles.addAll(fileOrDirs);
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
    newFiles.replace(tag, originalFileOrDirPath, newFileOrDirPaths);
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
    newFiles.replaceAll(tag, newFileOrDirPaths);
    return this;
  }

  /** Returns whether the file map is empty. */
  public boolean isEmpty() {
    return newFiles.isEmpty();
  }

  /**
   * Finds a single input file/dir marked with the given tag.
   *
   * @param tag file/dir tag
   * @return the full path of the file/dir with the given tag
   * @throws MobileHarnessException when tag did not match exactly one input file/dir
   */
  public synchronized String getSingle(String tag) throws MobileHarnessException {
    return newFiles.getSingle(tag);
  }

  /**
   * Finds the input files/dirs marked with the given tag.
   *
   * @return a copy of the file/dir full path, or <b>an empty set</b> if there is no file/dir with
   *     the given tag
   */
  public synchronized ImmutableSet<String> get(String tag) {
    return newFiles.get(tag);
  }

  /** Gets the {tag, path} mapping of all the input files/dirs. */
  public synchronized ImmutableMultimap<String, String> getAll() {
    return newFiles.getAll();
  }

  /**
   * Checks whether the given tags are associated with at least one file/dir.
   *
   * @param tags file/folder tags
   * @throws MobileHarnessException if there is any tag doesn't map to any path
   */
  public void checkNotEmpty(String... tags) throws MobileHarnessException {
    List<com.google.devtools.mobileharness.api.model.error.MobileHarnessException> errors =
        newFiles.validateNotEmpty(tags);
    if (!errors.isEmpty()) {
      throw errors.get(0);
    }
  }

  /**
   * Checks whether the given tag is associated with at least one file/dir.
   *
   * @param tag file/folder tags
   * @return true if the tag is associated with at least one file/dir, otherwise return false.
   */
  public boolean isTagNotEmpty(String tag) {
    return newFiles.isTagNotEmpty(tag);
  }

  /**
   * Checks whether all tags are associated with an unique file/dir.
   *
   * @param tags file/folder tags
   * @throws MobileHarnessException if there is any tag doesn't map to exactly one single path
   */
  public synchronized void checkUnique(String... tags) throws MobileHarnessException {
    List<com.google.devtools.mobileharness.api.model.error.MobileHarnessException> errors =
        newFiles.validateUnique(tags);
    if (!errors.isEmpty()) {
      throw errors.get(0);
    }
  }

  /**
   * Removes a file/dir with the given tag.
   *
   * @param tag the tag of the file/dir
   * @param fileOrDirPath the path of the file/dir
   * @return whether the files change
   */
  public synchronized boolean remove(String tag, String fileOrDirPath) {
    return newFiles.remove(tag, fileOrDirPath);
  }

  @Override
  public String toString() {
    return newFiles.toString();
  }
}
