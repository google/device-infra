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
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;

/** Helper class for commands. */
public class CommandHelper {
  private static final String ANDROID_XTS_DIR_NAME_PREFIX = "android-";

  private final LocalFileUtil localFileUtil;
  private final ConsoleInfo consoleInfo;

  private final Map<String, String> xtsTypesByRootDir = new ConcurrentHashMap<>();

  @Inject
  CommandHelper(LocalFileUtil localFileUtil, ConsoleInfo consoleInfo) {
    this.localFileUtil = localFileUtil;
    this.consoleInfo = consoleInfo;
  }

  /** Gets the xts type. */
  public String getXtsType(String xtsRootDir) {
    return xtsTypesByRootDir.computeIfAbsent(xtsRootDir, this::calculateXtsType);
  }

  private String calculateXtsType(String xtsRootDir) {
    Optional<String> xtsTypeFromConsoleInfo = consoleInfo.getXtsType();
    if (xtsTypeFromConsoleInfo.isPresent()) {
      return xtsTypeFromConsoleInfo.get();
    }

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
                  + " under directory %s. Please specify XTS type by -D%s. dirs=%s",
              xtsRootDir, ConsoleInfo.XTS_TYPE_PROPERTY_KEY, xtsDirs));
    } else {
      xtsDir = xtsDirs.get(0);
    }

    return xtsDir.getFileName().toString().substring(ANDROID_XTS_DIR_NAME_PREFIX.length());
  }
}
