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

package com.google.devtools.mobileharness.infra.ats.common;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.nio.file.Path;
import java.util.function.Supplier;
import javax.inject.Inject;

/** A loader to load XTS type from XTS root directory. */
public class XtsTypeLoader {
  public static final String XTS_TYPE_PROPERTY_KEY = "XTS_TYPE";
  private static final String ANDROID_XTS_DIR_NAME_PREFIX = "android-";
  private final LocalFileUtil localFileUtil;

  @Inject
  XtsTypeLoader(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  /**
   * Gets XTS type from XTS root directory.
   *
   * @param xtsRootDir the XTS root directory
   * @param helpMessageForMultipleXtsDirs the help message to set in the exception in case there are
   *     multiple XTS directories in root directory.
   * @return the XTS type
   * @throws IllegalStateException if XTS directory cannot be found in the root directory or the
   *     root directory contains multiple XTS directories.
   */
  public String getXtsType(String xtsRootDir, Supplier<String> helpMessageForMultipleXtsDirs) {

    if (xtsRootDir.isEmpty()) {
      throw new IllegalStateException("XTS root directory is empty.");
    }
    if (!localFileUtil.isDirExist(xtsRootDir)) {
      throw new IllegalStateException(
          String.format("XTS root directory [%s] doesn't exist.", xtsRootDir));
    }
    ImmutableList<Path> xtsDirs;
    try {
      xtsDirs =
          localFileUtil.listDirs(Path.of(xtsRootDir)).stream()
              .filter(p -> p.getFileName().toString().startsWith(ANDROID_XTS_DIR_NAME_PREFIX))
              .collect(toImmutableList());
    } catch (MobileHarnessException e) {
      throw new IllegalStateException(e);
    }
    Path xtsDir;
    if (xtsDirs.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "Android XTS directory whose name is in format [android-<xts>] doesn't"
                  + " exist under directory %s",
              xtsRootDir));
    } else if (xtsDirs.size() > 1) {
      throw new IllegalStateException(
          String.format(
              "Multiple Android XTS directories whose name are in format [android-<xts>] exist"
                  + " under directory %s. dirs=%s. %s",
              xtsRootDir, xtsDirs, helpMessageForMultipleXtsDirs.get()));
    } else {
      xtsDir = xtsDirs.get(0);
    }

    return xtsDir.getFileName().toString().substring(ANDROID_XTS_DIR_NAME_PREFIX.length());
  }
}
