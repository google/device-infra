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

package com.google.devtools.atsconsole;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;
import org.apache.commons.io.FilenameUtils;

/** Util class for the console. */
public class ConsoleUtil {

  ConsoleUtil() {}

  /** Expands path prefixed with "~/" with user home expanded path. */
  public String completeHomeDirectory(String path) {
    if (path.startsWith("~" + File.separator)) {
      return System.getProperty("user.home") + path.substring(1);
    }
    if ("~".equals(path)) {
      return System.getProperty("user.home");
    }
    return path;
  }

  /**
   * Displays a line of text on console.
   *
   * @param output which is displayed on the console
   */
  public void printLine(String output) {
    System.out.print(output);
    System.out.println();
  }

  /** Checks if the given file is a zip file. */
  public boolean isZipFile(File file) {
    String fileExt = FilenameUtils.getExtension(file.getPath());
    if (!fileExt.isEmpty() && !"zip".equals(fileExt)) {
      return false;
    }
    ZipFile zipFile = null;
    try {
      zipFile = new ZipFile(file);
    } catch (IOException e) {
      return false;
    } finally {
      if (zipFile != null) {
        try {
          zipFile.close();
        } catch (IOException e) {
          // Ignored
        }
      }
    }
    return true;
  }
}
