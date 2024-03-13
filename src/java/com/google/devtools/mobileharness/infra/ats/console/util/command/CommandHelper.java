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

package com.google.devtools.mobileharness.infra.ats.console.util.command;

import com.google.common.base.Ascii;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.XtsType;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.nio.file.Path;
import javax.inject.Inject;

/** Helper class for commands. */
public class CommandHelper {

  private static final String ANDROID_XTS_DIR_NAME_PREFIX = "android-";

  private final LocalFileUtil localFileUtil;

  @Inject
  CommandHelper(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  /** Gets the xts type. */
  public XtsType getXtsType(String xtsRootDir) throws MobileHarnessException {
    if (xtsRootDir.isEmpty()) {
      throw new MobileHarnessException(
          ExtErrorId.COMMAND_HELPER_XTS_ROOT_DIR_NOT_EXIST, "XTS root directory is empty.");
    }
    if (!localFileUtil.isDirExist(xtsRootDir)) {
      throw new MobileHarnessException(
          ExtErrorId.COMMAND_HELPER_XTS_ROOT_DIR_NOT_EXIST, "XTS root directory doesn't exist.");
    }
    Path androidXtsDir =
        localFileUtil.listDirs(Path.of(xtsRootDir)).stream()
            .filter(p -> p.getFileName().toString().startsWith(ANDROID_XTS_DIR_NAME_PREFIX))
            .findFirst()
            .orElseThrow(
                () ->
                    new MobileHarnessException(
                        ExtErrorId.COMMAND_HELPER_ANDROID_XTS_DIR_NOT_EXIST,
                        String.format(
                            "Android XTS directory whose name in format [android-<xts>] doesn't"
                                + " exist under directory %s.",
                            xtsRootDir)));

    try {
      return XtsType.valueOf(
          Ascii.toUpperCase(
              androidXtsDir
                  .getFileName()
                  .toString()
                  .substring(ANDROID_XTS_DIR_NAME_PREFIX.length())));
    } catch (IllegalArgumentException e) {
      throw new MobileHarnessException(
          ExtErrorId.COMMAND_HELPER_UNEXPECTED_XTS_TYPE,
          String.format("The xts type in dir name %s is unexpected.", androidXtsDir.getFileName()),
          e);
    }
  }
}
