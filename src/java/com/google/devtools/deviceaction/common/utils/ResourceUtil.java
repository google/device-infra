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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** A utility class to help preparing resources. */
public final class ResourceUtil {

  private static final String DIR_NOT_FOUND_ERROR = "DIR_NOT_FOUND";

  /** Creates a session dir inside the parent dir. */
  static Path createSessionDir(Path parent) {
    Path toCreate = parent.resolve(Constants.SESSION_NAME);
    try {
      Files.createDirectories(toCreate);
      return toCreate;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to create the dir " + toCreate, e);
    }
  }

  /** Gets the path if the file exists or else get empty. */
  static Optional<Path> filterExistingFile(Path path) {
    return Optional.ofNullable(path).filter(p -> p.toFile().exists());
  }

  /** Gets the path if the file exists or else throws a {@link DeviceActionException}. */
  static Path getExistingDir(Path dirPath) throws DeviceActionException {
    return filterExistingFile(dirPath).orElseThrow(() -> dirNotFoundException(dirPath));
  }

  private static DeviceActionException dirNotFoundException(Path dirPath) {
    return new DeviceActionException(
        DIR_NOT_FOUND_ERROR,
        ErrorType.DEPENDENCY_ISSUE,
        String.format("The dir %s not found. Check if it is deleted.", dirPath));
  }

  private ResourceUtil() {}
}
