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

package com.google.devtools.deviceinfra.shared.util.path;

/**
 * Utility methods for manipulation of UNIX-like paths. See PathUtilTest for examples.
 *
 * <p>Mind that this class does not work on Windows due to / to \ and different root (C: etc)
 * issues.
 */
public final class PathUtil {

  /**
   * Joins a set of path components into a single path. Empty path components are ignored.
   *
   * @param components the components of the path
   * @return the components joined into a string, delimited by slashes. Runs of slashes are reduced
   *     to a single slash. If present, leading slashes on the first non-empty component and
   *     trailing slash on the last non-empty component are preserved.
   */
  public static String join(String... components) {
    int len = components.length - 1;
    if (len == -1) {
      return "";
    }
    for (String component : components) {
      len += component.length();
    }
    char[] path = new char[len];
    int i = 0;
    for (String component : components) {
      if (!component.isEmpty()) {
        if (i > 0 && path[i - 1] != '/') {
          path[i++] = '/';
        }
        for (int j = 0, end = component.length(); j < end; j++) {
          char c = component.charAt(j);
          if (!(c == '/' && i > 0 && path[i - 1] == '/')) {
            path[i++] = c;
          }
        }
      }
    }
    return new String(path, 0, i);
  }

  private PathUtil() {}
}
