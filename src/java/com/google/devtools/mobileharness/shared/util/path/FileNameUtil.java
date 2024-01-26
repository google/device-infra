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

package com.google.devtools.mobileharness.shared.util.path;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;
import org.apache.commons.io.FilenameUtils;

/** Utility for file names and extensions. */
public class FileNameUtil {

  /** See {@link FilenameUtils#getExtension(String)}. */
  public String getExtension(String fileName) {
    return FilenameUtils.getExtension(fileName);
  }

  /** See {@link FilenameUtils#removeExtension(String)}. */
  public String removeExtension(String fileName) {
    return FilenameUtils.removeExtension(fileName);
  }

  /** Checks if the given file is a zip file. */
  public boolean isZipFile(File file) {
    String fileExt = FilenameUtils.getExtension(file.getPath());
    if (!fileExt.isEmpty() && !fileExt.equals("zip")) {
      return false;
    }
    try (ZipFile ignored = new ZipFile(file)) {
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
