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

package com.google.devtools.deviceaction.common.utils;

import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.framework.proto.FileSpec;
import java.io.File;

/** A trivial {@link SimpleResolver} for local files. */
final class LocalFileResolver extends SimpleResolver {
  private static final LocalFileResolver INSTANCE = new LocalFileResolver();

  private LocalFileResolver() {}

  /** Gets the singleton. */
  public static LocalFileResolver getInstance() {
    return INSTANCE;
  }

  /** Only applies to local files. */
  @Override
  boolean appliesTo(FileSpec fileSpec) {
    return fileSpec.hasLocalPath();
  }

  /** Resolves a local file by doing nothing. */
  @Override
  File resolveFile(FileSpec fileSpec) throws DeviceActionException {
    File result = new File(fileSpec.getLocalPath());
    if (!result.exists()) {
      throw new DeviceActionException(
          "FILE_NOT_FOUND",
          ErrorType.CUSTOMER_ISSUE,
          String.format("The file %s doesn't exist", fileSpec));
    }
    return result;
  }
}
