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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for manipulation of UNIX-like paths. See PathUtilTest for examples.
 *
 * <p>Mind that this class does not work on Windows due to / to \ and different root (C: etc)
 * issues.
 */
public final class PathUtil {
  private static final Pattern SLASH_PREFIX_PATTERN = Pattern.compile("^/+");
  private static final Pattern TWO_OR_MORE_SLASH_PATTERN = Pattern.compile("/{2,}");
  private static final Pattern NON_SLASH_SUFFIX_PATTERN = Pattern.compile("(.*[^/])/+$");

  /**
   * Gets the final component from a path. Trailing slashes are removed.
   *
   * <p>Examples:
   *
   * <pre>{@code
   * basename("/foo/bar/") = "bar"
   * basename("/foo/bar") = "bar"
   * basename("/foo") = "foo"
   * basename("/") = ""
   * }</pre>
   *
   * <p>The last example is inconsistent with Unix basename(1) which returns "/" as the basename of
   * "/".
   *
   * @param path The path to apply the basename operation to.
   * @return path, with any leading directory elements removed.
   */
  public static String basename(String path) {
    path = removeLeadingSlashes(removeExtraneousSlashes(path));

    if (path.length() == 0) {
      return path;
    }

    int pos = path.lastIndexOf("/");
    if (pos == -1) {
      return path;
    } else {
      return path.substring(pos + 1);
    }
  }

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

  /**
   * Determines the parent directory of the given path. This is similar to dirname(1).
   *
   * @param path The path to strip
   * @return path, with trailing component removed.
   */
  public static String dirname(String path) {
    path = removeExtraneousSlashes(path);

    int lastSlash = path.lastIndexOf("/");

    if ("/".equals(path) || lastSlash == 0) {
      return "/";
    } else if (lastSlash == -1) {
      return ".";
    } else {
      return path.substring(0, lastSlash);
    }
  }

  /**
   * Returns a path that is relative to 'dir' for the given 'fullPath'. Never returns a string with
   * a trailing slash.
   *
   * <p>Occurrences of ".." are silently ignored. It is the responsibility of the caller to ensure
   * that the paths given to this method do not contain a "..".
   *
   * @param dir The path that you wish to make fullPath relative to
   * @return a path relative to dir. The returned value will never start with a slash.
   */
  public static String makeRelative(String dir, String fullPath) {
    dir = removeExtraneousSlashes(dir);
    fullPath = removeExtraneousSlashes(fullPath);

    /**
     * If fullpath is indeed underneath dir, then we strip dir from fullPath to get the relative
     * relativePath. If not, we just use fullPath and assume that it is already a relative
     * relativePath.
     */
    String relativePath;
    if (fullPath.startsWith(dir)) {
      relativePath = fullPath.substring(dir.length());
    } else {
      relativePath = fullPath;
    }

    // Intentional denial: all components with ".." are removed.
    String clean = relativePath.replace("/../", "/").replace("/..", "/").replace("../", "/");
    relativePath = join(clean.split("/"));
    relativePath = removeLeadingSlashes(relativePath);

    return relativePath;
  }

  /**
   * Removes extra slashes from a path. Leading slash is preserved, trailing slash is stripped, and
   * any runs of more than one slash in the middle is replaced by a single slash.
   */
  public static String removeExtraneousSlashes(String s) {
    Matcher m = NON_SLASH_SUFFIX_PATTERN.matcher(s);

    if (m.matches()) {
      s = s.substring(0, m.end(1));
    }

    return TWO_OR_MORE_SLASH_PATTERN.matcher(s).replaceAll("/");
  }

  /** Removes leading slashes from a string. */
  public static String removeLeadingSlashes(String path) {
    return SLASH_PREFIX_PATTERN.matcher(path).replaceFirst("");
  }

  private PathUtil() {}
}
