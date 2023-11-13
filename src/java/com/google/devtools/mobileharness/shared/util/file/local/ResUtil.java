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

package com.google.devtools.mobileharness.shared.util.file.local;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.constant.DirCommon;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

/** Utility class for handling resource files which are packed into jar package. */
public class ResUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Default resource directory name. */
  public static final String DEFAULT_RES_DIR_NAME = "mh_res_files";

  /** {Path-in-jar@relative-class-name, path-in-file-system} of the resource files. */
  private static final Map<String, String> resFiles = Maps.newHashMap();

  private final LocalFileUtil fileUtil;
  private static final SystemUtil systemUtil = new SystemUtil();

  private final String resDir;

  /** Holder of {@link #RES_DIR} for lazy initialization. */
  private static class ResDirHolder {
    /** Target directory for placing the unpacked resource files. */
    private static final String RES_DIR;

    static {
      RES_DIR = PathUtil.join(DirCommon.getTempDirRoot(), Flags.instance().resDirName.getNonNull());
      logger.atInfo().log("Running on Lab server, use %s as RES_DIR.", RES_DIR);
      LocalFileUtil fileUtil = new LocalFileUtil();
      // Cleans up the res dir. Otherwise, the lab server may fail to extract the adb binary
      // because it failed to overwrite adb under use. See b/11909086.
      logger.atInfo().log("Clean res dir: %s", RES_DIR);
      try {
        fileUtil.removeFileOrDir(RES_DIR);
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log("Failed to clean up res dir: %s", RES_DIR);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.atWarning().log("Interrupted: %s", e.getMessage());
      }
    }
  }

  public ResUtil() {
    this(ResDirHolder.RES_DIR, new LocalFileUtil());
  }

  public ResUtil(String resDir) {
    this(resDir, new LocalFileUtil());
  }

  @VisibleForTesting
  ResUtil(String resDir, LocalFileUtil fileUtil) {
    this.resDir = resDir;
    this.fileUtil = fileUtil;
  }

  /**
   * Reads the resource file which is packed into the jar package, copies to local tmp directory if
   * it is never copied before, and return the file path.
   *
   * @param relativeClass whose class loader is used to search the resource
   * @param resPathInJar path of the resource file in the jar package, it can be the absolute path
   *     of the resource if it begins with '/', or it is the relative path from the relative class
   * @return path of the local copy of the resource file
   * @throws MobileHarnessException if failed to read or copy the resource file
   */
  public String getResourceFile(Class<?> relativeClass, String resPathInJar)
      throws MobileHarnessException {
    synchronized (resFiles) {
      String key = relativeClass.getSimpleName() + "_" + resPathInJar;
      String filePath = resFiles.get(key);
      if (filePath != null) {
        try {
          fileUtil.checkFile(filePath);
          logger.atInfo().log("Resource %s is already copied to %s", key, filePath);
          return filePath;
        } catch (MobileHarnessException e) {
          // File does not exist, needs to recreate the file.
          resFiles.put(key, null);
        }
      }

      // Use the resource in supplemental resource directory if exists.
      String supplementalResDir = Flags.instance().supplementalResDir.getNonNull();
      if (!Strings.isNullOrEmpty(supplementalResDir)) {
        filePath = PathUtil.join(supplementalResDir, resPathInJar);
        if (fileUtil.isFileExist(filePath)) {
          logger.atInfo().log("Resource %s is in the supplemental resource dir %s", key, filePath);
          resFiles.put(key, filePath);
          return filePath;
        }
      }

      // Workaround for https://bugs.openjdk.java.net/browse/JDK-8205976
      // getResourceAsStream() can throw "Stream closed" if there are multiple
      // via URLClassLoader.
      URL url = relativeClass.getResource(resPathInJar);
      if (url == null) {
        throw new MobileHarnessException(
            BasicErrorId.JAR_RES_COPY_ERROR,
            String.format("Failed to get resource URL from %s", resPathInJar));
      }
      URLConnection connection = null;
      try {
        connection = url.openConnection();
      } catch (IOException e) {
        throw new MobileHarnessException(
            BasicErrorId.JAR_RES_COPY_ERROR,
            String.format("An I/O error occurred opening the URLConnection to %s", resPathInJar),
            e);
      }
      // disable caching so the jars aren't locked. See details in b/197463895
      connection.setDefaultUseCaches(false);
      try (InputStream inputStream = connection.getInputStream()) {
        if (inputStream == null) {
          throw new MobileHarnessException(
              BasicErrorId.JAR_RES_NOT_FOUND, "Can not find resource " + resPathInJar);
        }
        filePath = PathUtil.join(resDir, resPathInJar);
        fileUtil.prepareDir(fileUtil.getParentDirPath(filePath), LocalFileUtil.FULL_ACCESS);
        fileUtil.writeToFile(filePath, inputStream);
        fileUtil.grantFileOrDirFullAccess(resDir);
        fileUtil.grantFileOrDirFullAccess(filePath);
        resFiles.put(key, filePath);
        logger.atInfo().log("Copy resource %s to %s", resPathInJar, filePath);
      } catch (IOException e) {
        throw new MobileHarnessException(
            BasicErrorId.JAR_RES_COPY_ERROR,
            String.format(
                "An I/O error occurred when copying resource from %s to %s",
                resPathInJar, filePath),
            e);
      }
      return filePath;
    }
  }

  /**
   * Returns if have the resource file in the JAR file.
   *
   * @param relativeClass whose class loader is used to search the resource
   * @param resPathInJar path of the resource file in the jar package, it can be the absolute path
   *     of the resource if it begins with '/', or it is the relative path from the relative class
   */
  public boolean hasResourceFile(Class<?> relativeClass, String resPathInJar) {
    try (InputStream inputStream = relativeClass.getResourceAsStream(resPathInJar)) {
      return inputStream != null;
    } catch (IOException e) {
      logger.atSevere().log("Cannot close unused resource stream, ignoring: %s", e);
      return true; // If there is a close() exception, it means the resource exists in principle.
    }
  }

  /** Gets path of default resource directory. */
  public static String getResDir() {
    return ResDirHolder.RES_DIR;
  }
}
