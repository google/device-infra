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

package com.google.devtools.mobileharness.infra.ats.util.tradefed;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

/**
 * A helper class for zip extraction that takes POSIX file permissions into account. It's the
 * version 2.0 for {@link ZipUtil}.
 */
public class ZipUtil2 {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private ZipUtil2() {}

  /**
   * A util method to apply unix mode from {@link ZipArchiveEntry} to the created local file system
   * entry if necessary
   *
   * @param entry the entry inside zipfile (potentially contains mode info)
   * @param localFile the extracted local file entry
   * @return True if the Unix permissions are set, false otherwise.
   * @throws MobileHarnessException if failed to set the Unix permissions
   */
  private static boolean applyUnixModeIfNecessary(ZipArchiveEntry entry, File localFile)
      throws MobileHarnessException {
    if (entry.getPlatform() == ZipArchiveEntry.PLATFORM_UNIX) {
      try {
        Files.setPosixFilePermissions(
            localFile.toPath(), FileUtil.unixModeToPosix(entry.getUnixMode()));
      } catch (IOException e) {
        throw new MobileHarnessException(
            BasicErrorId.ZIP_UTIL_SET_POSIX_FILE_PERMISSION_ERROR,
            String.format(
                "Failed to set posix file permission to file %s", localFile.getAbsolutePath()),
            e);
      }
      return true;
    }
    return false;
  }

  /**
   * Utility method to extract a zip entry to a file.
   *
   * @param zipFile the {@link ZipFile} to extract
   * @param entry the {@link ZipArchiveEntry} to extract
   * @param destFile the {@link File} to extract to
   * @return whether the Unix permissions are set
   * @throws MobileHarnessException if failed to extract file
   */
  @CanIgnoreReturnValue
  private static boolean extractZipEntry(ZipFile zipFile, ZipArchiveEntry entry, File destFile)
      throws MobileHarnessException {
    try (InputStream inputStream = zipFile.getInputStream(entry)) {
      FileUtil.writeToFile(inputStream, destFile);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.ZIP_UTIL_GET_INPUT_STREAM_ERROR, e.getMessage(), e);
    }

    return applyUnixModeIfNecessary(entry, destFile);
  }

  /**
   * Utility method to extract entire contents of zip file into given directory
   *
   * @param zipFile the {@link ZipFile} to extract
   * @param destDir the local dir to extract file to
   * @throws MobileHarnessException if failed to extract file
   */
  public static void extractZip(ZipFile zipFile, File destDir) throws MobileHarnessException {
    Enumeration<? extends ZipArchiveEntry> entries = zipFile.getEntries();
    Set<String> noPermissions = new HashSet<>();
    while (entries.hasMoreElements()) {
      ZipArchiveEntry entry = entries.nextElement();
      File childFile = new File(destDir, entry.getName());
      ZipUtil.validateDestinationDir(destDir, entry.getName());
      childFile.getParentFile().mkdirs();
      if (entry.isDirectory()) {
        childFile.mkdirs();
        if (!applyUnixModeIfNecessary(entry, childFile)) {
          noPermissions.add(entry.getName());
        }
        continue;
      } else {
        if (!extractZipEntry(zipFile, entry, childFile)) {
          noPermissions.add(entry.getName());
        }
      }
    }
    if (!noPermissions.isEmpty()) {
      logger.atFine().log(
          "Entries '%s' exist but do not contain Unix mode permission info. Files will "
              + "have default permission.",
          noPermissions);
    }
  }

  /**
   * Utility method to extract a zip file into a given directory. The zip file being presented as a
   * {@link File}.
   *
   * @param toUnzip a {@link File} pointing to a zip file.
   * @param destDir the local dir to extract file to
   * @throws MobileHarnessException if failed to extract file
   */
  public static void extractZip(File toUnzip, File destDir) throws MobileHarnessException {
    // Extract fast
    try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(toUnzip)) {
      ZipUtil.extractZip(zipFile, destDir);
    } catch (IOException e) {
      if (e instanceof FileNotFoundException) {
        throw new MobileHarnessException(BasicErrorId.ZIP_UTIL_ARTIFACT_INVALID, e.getMessage(), e);
      }
      throw new MobileHarnessException(BasicErrorId.ZIP_UTIL_EXTRACT_ZIP_ERROR, e.getMessage(), e);
    }
    // Then restore permissions
    try (ZipFile zip = new ZipFile(toUnzip)) {
      restorePermissions(zip, destDir);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.ZIP_UTIL_OPEN_ZIP_FILE_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Utility method to extract one specific file from zip file
   *
   * @param zipFile the {@link ZipFile} to extract
   * @param filePath the file path in the zip
   * @param destFile the {@link File} to extract to
   * @return whether the file is found and extracted
   * @throws MobileHarnessException if failed to extract file
   */
  public static boolean extractFileFromZip(ZipFile zipFile, String filePath, File destFile)
      throws MobileHarnessException {
    ZipArchiveEntry entry = zipFile.getEntry(filePath);
    if (entry == null) {
      return false;
    }
    extractZipEntry(zipFile, entry, destFile);
    return true;
  }

  /**
   * Utility method to extract one specific file from zip file into a tmp file
   *
   * @param zipFile the {@link ZipFile} to extract
   * @param filePath the filePath of to extract
   * @throws MobileHarnessException if failed to extract file
   * @return the {@link File} or null if not found
   */
  public static Optional<File> extractFileFromZip(ZipFile zipFile, String filePath)
      throws MobileHarnessException {
    ZipArchiveEntry entry = zipFile.getEntry(filePath);
    if (entry == null) {
      return Optional.empty();
    }
    File createdFile = FileUtil.createTempFile("extracted", FileUtil.getExtension(filePath));
    extractZipEntry(zipFile, entry, createdFile);
    return Optional.of(createdFile);
  }

  /**
   * Extract a zip file to a temp directory prepended with a string
   *
   * @param zipFile the zip file to extract
   * @param nameHint a prefix for the temp directory
   * @return a {@link File} pointing to the temp directory
   */
  public static File extractZipToTemp(File zipFile, String nameHint) throws MobileHarnessException {
    File localRootDir = FileUtil.createTempDir(nameHint);
    try {
      extractZip(zipFile, localRootDir);
      return localRootDir;
    } catch (MobileHarnessException e) {
      // clean tmp file since we couldn't extract.
      FileUtil.recursiveDelete(localRootDir);
      throw e;
    }
  }

  /**
   * Close an open {@link ZipFile}, ignoring any exceptions.
   *
   * @param zipFile the file to close
   */
  public static void closeZip(ZipFile zipFile) {
    if (zipFile != null) {
      try {
        zipFile.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }

  /** Match permission on an already extracted destination directory. */
  private static void restorePermissions(ZipFile zipFile, File destDir)
      throws MobileHarnessException {
    Enumeration<? extends ZipArchiveEntry> entries = zipFile.getEntries();
    Set<String> noPermissions = new HashSet<>();
    while (entries.hasMoreElements()) {
      ZipArchiveEntry entry = entries.nextElement();
      File childFile = new File(destDir, entry.getName());
      if (!applyUnixModeIfNecessary(entry, childFile)) {
        noPermissions.add(entry.getName());
      }
    }
    if (!noPermissions.isEmpty()) {
      logger.atFine().log(
          "Entries '%s' exist but do not contain Unix mode permission info. Files will "
              + "have default permission.",
          noPermissions);
    }
  }
}
