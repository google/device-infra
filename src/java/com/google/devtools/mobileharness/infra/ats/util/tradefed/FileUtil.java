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

import static com.google.common.base.StandardSystemProperty.JAVA_IO_TMPDIR;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** A helper class for file related operations */
public final class FileUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableSet<String> DISK_SPACE_ERRORS =
      ImmutableSet.of("No space left on device");

  /**
   * The minimum allowed disk space in megabytes. File creation methods will throw {@link
   * MobileHarnessException} with error ID {@link BasicErrorId#FILE_UTIL_LOW_DISK_SPACE} if the
   * usable disk space in desired partition is less than this amount.
   */
  private static final long MIN_DISK_SPACE_MB = 100;

  private static final char[] SIZE_SPECIFIERS = {' ', 'K', 'M', 'G', 'T'};

  /** A map of {@link PosixFilePermission} to its corresponding Unix file mode */
  private static final ImmutableMap<PosixFilePermission, Integer> PERM_MODE_MAP =
      ImmutableMap.of(
          PosixFilePermission.OWNER_READ,
          0b100000000,
          PosixFilePermission.OWNER_WRITE,
          0b010000000,
          PosixFilePermission.OWNER_EXECUTE,
          0b001000000,
          PosixFilePermission.GROUP_READ,
          0b000100000,
          PosixFilePermission.GROUP_WRITE,
          0b000010000,
          PosixFilePermission.GROUP_EXECUTE,
          0b000001000,
          PosixFilePermission.OTHERS_READ,
          0b000000100,
          PosixFilePermission.OTHERS_WRITE,
          0b000000010,
          PosixFilePermission.OTHERS_EXECUTE,
          0b000000001);

  public static final int FILESYSTEM_FILENAME_MAX_LENGTH = 255;

  private FileUtil() {}

  public static boolean chmodRwxRecursively(File file) {
    boolean success = true;
    if (!file.setExecutable(true, false)) {
      logger.atWarning().log("Failed to set %s executable.", file.getAbsolutePath());
      success = false;
    }
    if (!file.setWritable(true, false)) {
      logger.atWarning().log("Failed to set %s writable.", file.getAbsolutePath());
      success = false;
    }
    if (!file.setReadable(true, false)) {
      logger.atWarning().log("Failed to set %s readable.", file.getAbsolutePath());
      success = false;
    }

    if (file.isDirectory()) {
      File[] children = file.listFiles();
      for (File child : children) {
        if (!chmodRwxRecursively(child)) {
          success = false;
        }
      }
    }
    return success;
  }

  /** Recursively set read and exec (if folder) permissions for given file. */
  public static void setReadableRecursive(File file) {
    file.setReadable(true);
    if (file.isDirectory()) {
      file.setExecutable(true);
      File[] children = file.listFiles();
      if (children != null) {
        for (File childFile : file.listFiles()) {
          setReadableRecursive(childFile);
        }
      }
    }
  }

  /**
   * Helper function to create a temp directory in the system default temporary file directory.
   *
   * @param prefix The prefix string to be used in generating the file's name; must be at least
   *     three characters long
   * @return the created directory
   * @throws MobileHarnessException if file could not be created
   */
  public static File createTempDir(String prefix) throws MobileHarnessException {
    return createTempDir(prefix, null);
  }

  /**
   * Helper function to create a temp directory.
   *
   * @param prefix The prefix string to be used in generating the file's name; must be at least
   *     three characters long
   * @param parentDir The parent directory in which the directory is to be created. If <code>null
   *     </code> the system default temp directory will be used.
   * @return the created directory
   * @throws MobileHarnessException if file could not be created
   */
  public static File createTempDir(String prefix, File parentDir) throws MobileHarnessException {
    // create a temp file with unique name, then make it a directory
    if (parentDir != null) {
      logger.atFine().log(
          "Creating temp directory at %s with prefix \"%s\"", parentDir.getAbsolutePath(), prefix);
    }
    File tmpDir;
    try {
      tmpDir = File.createTempFile(prefix, "", parentDir);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_CREATE_TEMP_FILE_ERROR,
          String.format(
              "Failed to create temp file in directory %s with prefix \"%s\"", parentDir, prefix),
          e);
    }
    return deleteFileAndCreateDirWithSameName(tmpDir);
  }

  private static File deleteFileAndCreateDirWithSameName(File tmpDir)
      throws MobileHarnessException {
    tmpDir.delete();
    return createDir(tmpDir);
  }

  @CanIgnoreReturnValue
  private static File createDir(File tmpDir) throws MobileHarnessException {
    if (!tmpDir.mkdirs()) {
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_CREATE_DIR_ERROR, "unable to create directory");
    }
    return tmpDir;
  }

  /**
   * Helper function to create a named directory inside your temp folder.
   *
   * <p>This directory will not have its name randomized. If the directory already exists it will be
   * returned.
   *
   * @param name The name of the directory to create in your tmp folder.
   * @return the created directory
   */
  public static File createNamedTempDir(String name) throws MobileHarnessException {
    File namedTmpDir = new File(JAVA_IO_TMPDIR.value(), name);
    if (!namedTmpDir.exists()) {
      createDir(namedTmpDir);
    }
    return namedTmpDir;
  }

  /**
   * Helper function to create a named directory inside a folder.
   *
   * <p>This directory will not have its name randomized. If the directory already exists it will be
   * returned.
   *
   * @param parentDir the directory where to create the dir. If null, will be in /tmp
   * @param name The name of the directory to create in the parent folder
   * @return the created directory
   */
  public static File createNamedTempDir(File parentDir, String name) throws MobileHarnessException {
    String parentPath;
    if (parentDir == null) {
      parentPath = JAVA_IO_TMPDIR.value();
    } else {
      parentPath = parentDir.getAbsolutePath();
    }
    File namedTmpDir = new File(parentPath, name);
    if (!namedTmpDir.exists()) {
      createDir(namedTmpDir);
    }
    return namedTmpDir;
  }

  /**
   * Helper wrapper function around {@link File#createTempFile(String, String)} that audits for
   * potential out of disk space scenario.
   *
   * @see File#createTempFile(String, String)
   * @throws MobileHarnessException with error ID {@link BasicErrorId#FILE_UTIL_LOW_DISK_SPACE} if
   *     disk space on temporary partition is lower than minimum allowed
   */
  public static File createTempFile(String prefix, String suffix) throws MobileHarnessException {
    return internalCreateTempFile(prefix, suffix, null);
  }

  /**
   * Helper wrapper function around {@link File#createTempFile(String, String, File)} that audits
   * for potential out of disk space scenario.
   *
   * @see File#createTempFile(String, String, File)
   * @throws MobileHarnessException with error ID {@link BasicErrorId#FILE_UTIL_LOW_DISK_SPACE} if
   *     disk space on partition is lower than minimum allowed
   */
  public static File createTempFile(String prefix, String suffix, File parentDir)
      throws MobileHarnessException {
    return internalCreateTempFile(prefix, suffix, parentDir);
  }

  /** Internal helper to create a temporary file. */
  private static File internalCreateTempFile(String prefix, String suffix, File parentDir)
      throws MobileHarnessException {
    // File.createTempFile add an additional random long in the name so we remove the length.
    int overflowLength = prefix.length() + 19 - FILESYSTEM_FILENAME_MAX_LENGTH;
    if (suffix != null) {
      // suffix may be null
      overflowLength += suffix.length();
    }
    if (overflowLength > 0) {
      logger.atWarning().log(
          "Filename for prefix: %s and suffix: %s, would be too long for FileSystem,"
              + "truncating it.",
          prefix, suffix);
      // We truncate from suffix in priority because File.createTempFile wants prefix to be
      // at least 3 characters.
      if (suffix.length() >= overflowLength) {
        int temp = overflowLength;
        overflowLength -= suffix.length();
        suffix = suffix.substring(temp);
      } else {
        overflowLength -= suffix.length();
        suffix = "";
      }
      if (overflowLength > 0) {
        // Whatever remaining to remove after suffix has been truncating should be inside
        // prefix, otherwise there would not be overflow.
        prefix = prefix.substring(0, prefix.length() - overflowLength);
      }
    }
    File returnFile = null;
    if (parentDir != null) {
      logger.atFine().log(
          "Creating temp file at %s with prefix \"%s\" suffix \"%s\"",
          parentDir.getAbsolutePath(), prefix, suffix);
    }
    try {
      returnFile = File.createTempFile(prefix, suffix, parentDir);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_LAB_HOST_FILESYSTEM_ERROR,
          String.format("Failed to create temp file [%s/%s/%s]", prefix, suffix, parentDir),
          e);
    }
    verifyDiskSpace(returnFile);
    return returnFile;
  }

  /**
   * A helper method that hardlinks a file to another file. Fallback to copy in case of cross
   * partition linking.
   *
   * @param origFile the original file
   * @param destFile the destination file
   * @throws MobileHarnessException if failed to hardlink file
   */
  public static void hardlinkFile(File origFile, File destFile) throws MobileHarnessException {
    hardlinkFile(origFile, destFile, false);
  }

  /**
   * A helper method that hardlinks a file to another file. Fallback to copy in case of cross
   * partition linking.
   *
   * @param origFile the original file
   * @param destFile the destination file
   * @param ignoreExistingFile If True and the file being linked already exists, skip the exception.
   * @throws MobileHarnessException if failed to hardlink file
   */
  public static void hardlinkFile(File origFile, File destFile, boolean ignoreExistingFile)
      throws MobileHarnessException {
    try {
      Files.createLink(destFile.toPath(), origFile.toPath());
    } catch (FileAlreadyExistsException e) {
      if (!ignoreExistingFile) {
        throw new MobileHarnessException(
            BasicErrorId.FILE_UTIL_HARDLINK_FILE_ERROR,
            String.format(
                "Failed to hardlink file from %s to %s",
                origFile.getAbsolutePath(), destFile.getAbsolutePath()),
            e);
      }
    } catch (FileSystemException e) {
      if (e.getMessage().contains("Invalid cross-device link")) {
        logger.atInfo().log("Hardlink failed: '%s', falling back to copy.", e.getMessage());
        copyFile(origFile, destFile);
        return;
      }
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_HARDLINK_FILE_ERROR,
          String.format(
              "Failed to hardlink file from %s to %s",
              origFile.getAbsolutePath(), destFile.getAbsolutePath()),
          e);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_HARDLINK_FILE_ERROR,
          String.format(
              "Failed to hardlink file from %s to %s",
              origFile.getAbsolutePath(), destFile.getAbsolutePath()),
          e);
    }
  }

  /**
   * A helper method that symlinks a file to another file
   *
   * @param origFile the original file
   * @param destFile the destination file
   * @throws MobileHarnessException if failed to symlink file
   */
  public static void symlinkFile(File origFile, File destFile) throws MobileHarnessException {
    logger.atFine().log(
        "Attempting symlink from %s to %s", origFile.getAbsolutePath(), destFile.getAbsolutePath());
    try {
      Files.createSymbolicLink(destFile.toPath(), origFile.toPath());
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_SYMLINK_FILE_ERROR,
          String.format(
              "Failed to symlink file from %s to %s",
              origFile.getAbsolutePath(), destFile.getAbsolutePath()),
          e);
    }
  }

  /**
   * Recursively hardlink folder contents.
   *
   * <p>Only supports copying of files and directories - symlinks are not copied. If the destination
   * directory does not exist, it will be created.
   *
   * @param sourceDir the folder that contains the files to copy
   * @param destDir the destination folder
   * @throws MobileHarnessException if failed to hardlink folder contents
   */
  public static void recursiveHardlink(File sourceDir, File destDir) throws MobileHarnessException {
    recursiveHardlink(sourceDir, destDir, false);
  }

  /**
   * Recursively hardlink folder contents.
   *
   * <p>Only supports copying of files and directories - symlinks are not copied. If the destination
   * directory does not exist, it will be created.
   *
   * @param sourceDir the folder that contains the files to copy
   * @param destDir the destination folder
   * @param ignoreExistingFile If True and the file being linked already exists, skip the exception.
   * @throws MobileHarnessException if failed to hardlink folder contents
   */
  public static void recursiveHardlink(File sourceDir, File destDir, boolean ignoreExistingFile)
      throws MobileHarnessException {
    recursiveHardlink(sourceDir, destDir, ignoreExistingFile, new HashSet<>());
  }

  /**
   * Recursively hardlink folder contents.
   *
   * <p>Only supports copying of files and directories - symlinks are not copied. If the destination
   * directory does not exist, it will be created.
   *
   * @param sourceDir the folder that contains the files to copy
   * @param destDir the destination folder
   * @param ignoreExistingFile If True and the file being linked already exists, skip the exception.
   * @param copyInsteadofHardlink Set of files that needs to be copied instead of linked.
   * @throws MobileHarnessException if failed to hardlink folder contents
   */
  public static void recursiveHardlink(
      File sourceDir, File destDir, boolean ignoreExistingFile, Set<String> copyInsteadofHardlink)
      throws MobileHarnessException {
    if (!destDir.isDirectory() && !destDir.mkdir()) {
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_CREATE_DIR_ERROR,
          String.format("Could not create directory %s", destDir.getAbsolutePath()));
    }
    for (File childFile : sourceDir.listFiles()) {
      File destChild = new File(destDir, childFile.getName());
      if (childFile.isDirectory()) {
        recursiveHardlink(childFile, destChild, ignoreExistingFile);
      } else if (childFile.isFile()) {
        if (copyInsteadofHardlink.contains(childFile.getName())) {
          FileUtil.copyFile(childFile, destChild);
        } else {
          hardlinkFile(childFile, destChild, ignoreExistingFile);
        }
      }
    }
  }

  /**
   * Recursively symlink folder contents.
   *
   * <p>Only supports copying of files and directories - symlinks are not copied. If the destination
   * directory does not exist, it will be created.
   *
   * @param sourceDir the folder that contains the files to copy
   * @param destDir the destination folder
   * @throws MobileHarnessException if failed to symlink folder contents
   */
  public static void recursiveSymlink(File sourceDir, File destDir) throws MobileHarnessException {
    if (!destDir.isDirectory() && !destDir.mkdir()) {
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_CREATE_DIR_ERROR,
          String.format("Could not create directory %s", destDir.getAbsolutePath()));
    }
    for (File childFile : sourceDir.listFiles()) {
      File destChild = new File(destDir, childFile.getName());
      if (childFile.isDirectory()) {
        recursiveSymlink(childFile, destChild);
      } else if (childFile.isFile()) {
        symlinkFile(childFile, destChild);
      }
    }
  }

  /**
   * A helper method that copies a file's contents to a local file
   *
   * @param origFile the original file to be copied
   * @param destFile the destination file
   * @throws MobileHarnessException if failed to copy file
   */
  public static void copyFile(File origFile, File destFile) throws MobileHarnessException {
    try (InputStream inputStream = new FileInputStream(origFile)) {
      writeToFile(inputStream, destFile);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_COPY_FILE_ERROR,
          String.format(
              "Failed to copy file %s to %s",
              origFile.getAbsolutePath(), destFile.getAbsolutePath()),
          e);
    }
  }

  /**
   * Recursively copy folder contents.
   *
   * <p>Only supports copying of files and directories - symlinks are not copied. If the destination
   * directory does not exist, it will be created.
   *
   * @param sourceDir the folder that contains the files to copy
   * @param destDir the destination folder
   * @throws MobileHarnessException if failed to copy folder contents
   */
  public static void recursiveCopy(File sourceDir, File destDir) throws MobileHarnessException {
    File[] childFiles = sourceDir.listFiles();
    if (childFiles == null) {
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_LIST_FILES_ERROR,
          String.format(
              "Failed to recursively copy. Could not determine contents for directory '%s'",
              sourceDir.getAbsolutePath()));
    }
    if (!destDir.isDirectory() && !destDir.mkdir()) {
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_CREATE_DIR_ERROR,
          String.format("Could not create directory %s", destDir.getAbsolutePath()));
    }
    for (File childFile : childFiles) {
      File destChild = new File(destDir, childFile.getName());
      if (childFile.isDirectory()) {
        recursiveCopy(childFile, destChild);
      } else if (childFile.isFile()) {
        copyFile(childFile, destChild);
      }
    }
  }

  /**
   * A helper method for reading string data from a file
   *
   * @param sourceFile the file to read from
   * @throws MobileHarnessException if failed to read file
   */
  public static String readStringFromFile(File sourceFile) throws MobileHarnessException {
    return readStringFromFile(sourceFile, 0, 0);
  }

  /**
   * A helper method for reading partial string data from a file
   *
   * @param sourceFile the file to read from
   * @param startOffset the start offset to read from the file.
   * @param length the number of bytes to read of the file.
   * @throws MobileHarnessException if failed to read file
   */
  public static String readStringFromFile(File sourceFile, long startOffset, long length)
      throws MobileHarnessException {
    try (FileInputStream is = new FileInputStream(sourceFile)) {
      if (startOffset < 0) {
        startOffset = 0;
      }
      long fileLength = sourceFile.length();
      is.skip(startOffset);
      if (length <= 0 || fileLength <= startOffset + length) {
        return StreamUtil.getStringFromStream(is);
      }
      return StreamUtil.getStringFromStream(is, length);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_READ_STRING_FROM_FILE_ERROR,
          String.format("Failed to read string from file %s", sourceFile.getAbsolutePath()),
          e);
    }
  }

  /**
   * A helper method for writing string data to file
   *
   * @param inputString the input {@link String}
   * @param destFile the destination file to write to
   */
  public static void writeToFile(String inputString, File destFile) throws MobileHarnessException {
    writeToFile(inputString, destFile, false);
  }

  /**
   * A helper method for writing or appending string data to file
   *
   * @param inputString the input {@link String}
   * @param destFile the destination file to write or append to
   * @param append append to end of file if true, overwrite otherwise
   */
  public static void writeToFile(String inputString, File destFile, boolean append)
      throws MobileHarnessException {
    writeToFile(new ByteArrayInputStream(inputString.getBytes(UTF_8)), destFile, append);
  }

  /**
   * A helper method for writing stream data to file
   *
   * @param input the unbuffered input stream
   * @param destFile the destination file to write to
   */
  public static void writeToFile(InputStream input, File destFile) throws MobileHarnessException {
    writeToFile(input, destFile, false);
  }

  /**
   * A helper method for writing stream data to file
   *
   * @param input the unbuffered input stream
   * @param destFile the destination file to write or append to
   * @param append append to end of file if true, overwrite otherwise
   */
  public static void writeToFile(InputStream input, File destFile, boolean append)
      throws MobileHarnessException {
    // Set size to a negative value to write all content starting at the given offset.
    writeToFile(input, destFile, append, 0, -1);
  }

  /**
   * A helper method for writing stream data to file
   *
   * @param input the unbuffered input stream
   * @param destFile the destination file to write or append to
   * @param append append to end of file if true, overwrite otherwise
   * @param startOffset the start offset of the input stream to retrieve data
   * @param size number of bytes to retrieve from the input stream, set it to a negative value to
   *     retrieve all content starting at the given offset.
   */
  public static void writeToFile(
      InputStream input, File destFile, boolean append, long startOffset, long size)
      throws MobileHarnessException {
    InputStream origStream = null;
    OutputStream destStream = null;
    try {
      origStream = new BufferedInputStream(input);
      destStream = new BufferedOutputStream(new FileOutputStream(destFile, append));
      StreamUtil.copyStreams(origStream, destStream, startOffset, size);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_WRITE_TO_FILE_ERROR,
          String.format("Failed to write to file %s", destFile.getAbsolutePath()),
          e);
    } finally {
      StreamUtil.close(origStream);
      StreamUtil.flushAndCloseStream(destStream);
    }
  }

  /**
   * Note: We should never use CLog in here, since it also relies on that method, this would lead to
   * infinite recursion.
   */
  private static void verifyDiskSpace(File file) throws MobileHarnessException {
    // Based on empirical testing File.getUsableSpace is a low cost operation (~ 100 us for
    // local disk, ~ 100 ms for network disk). Therefore call it every time tmp file is
    // created
    File toCheck = file;
    if (!file.isDirectory() && file.getParentFile() != null) {
      // If the given file is not a directory it might not work properly so using the parent
      // in that case.
      toCheck = file.getParentFile();
    }
    long usableSpace = toCheck.getUsableSpace();

    long minDiskSpace = MIN_DISK_SPACE_MB * 1024 * 1024;
    if (usableSpace < minDiskSpace) {
      String message =
          String.format(
              "Available space on %s is %.2f MB. Min is %d MB.",
              toCheck.getAbsolutePath(), usableSpace / (1024.0 * 1024.0), MIN_DISK_SPACE_MB);
      throw new MobileHarnessException(BasicErrorId.FILE_UTIL_LOW_DISK_SPACE, message);
    }
  }

  /**
   * Recursively delete given file or directory and all its contents.
   *
   * @param rootDir the directory or file to be deleted; can be null
   */
  public static void recursiveDelete(File rootDir) {
    if (rootDir != null) {
      // We expand directories if they are not symlink
      if (rootDir.isDirectory() && !Files.isSymbolicLink(rootDir.toPath())) {
        File[] childFiles = rootDir.listFiles();
        if (childFiles != null) {
          for (File child : childFiles) {
            recursiveDelete(child);
          }
        }
      }
      rootDir.delete();
    }
  }

  /**
   * Gets the extension for given file name.
   *
   * @param fileName file name with extension
   * @return the extension or empty String if file has no extension
   */
  public static String getExtension(String fileName) {
    int index = fileName.lastIndexOf('.');
    if (index == -1) {
      return "";
    } else {
      return fileName.substring(index);
    }
  }

  /**
   * Gets the base name, without extension, of given file name.
   *
   * <p>e.g. getBaseName("file.txt") will return "file"
   *
   * @param fileName file name with extension
   * @return the base name
   */
  public static String getBaseName(String fileName) {
    int index = fileName.lastIndexOf('.');
    if (index == -1) {
      return fileName;
    } else {
      return fileName.substring(0, index);
    }
  }

  /**
   * Utility method to do byte-wise content comparison of two files.
   *
   * @return <code>true</code> if file contents are identical
   */
  public static boolean compareFileContents(File file1, File file2) throws MobileHarnessException {
    BufferedInputStream stream1 = null;
    BufferedInputStream stream2 = null;

    boolean result = true;
    try {
      stream1 = new BufferedInputStream(new FileInputStream(file1));
      stream2 = new BufferedInputStream(new FileInputStream(file2));
      boolean eof = false;
      while (!eof) {
        int byte1 = stream1.read();
        int byte2 = stream2.read();
        if (byte1 != byte2) {
          result = false;
          break;
        }
        eof = byte1 == -1;
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_COMPARE_FILE_CONTENTS_ERROR,
          String.format("Failed to compare file contents of %s and %s", file1, file2),
          e);
    } finally {
      StreamUtil.close(stream1);
      StreamUtil.close(stream2);
    }
    return result;
  }

  /**
   * Helper method which constructs a unique file on temporary disk, whose name corresponds as
   * closely as possible to the file name given by the remote file path
   *
   * @param remoteFilePath the '/' separated remote path to construct the name from
   * @param parentDir the parent directory to create the file in. <code>null</code> to use the
   *     default temporary directory
   */
  public static File createTempFileForRemote(String remoteFilePath, File parentDir)
      throws MobileHarnessException {
    List<String> segments = Splitter.on('/').splitToList(remoteFilePath);
    // take last segment as base name
    String remoteFileName = Iterables.getLast(segments);
    String prefix = getBaseName(remoteFileName);
    if (prefix.length() < 3) {
      // prefix must be at least 3 characters long
      prefix = prefix + "XXX";
    }
    String fileExt = getExtension(remoteFileName);

    // create a unique file name. Add a underscore to prefix so file name is more readable
    // e.g. myfile_57588758.img rather than myfile57588758.img
    return FileUtil.createTempFile(prefix + "_", fileExt, parentDir);
  }

  /**
   * Try to delete a file. Intended for use when cleaning up in {@code finally} stanzas.
   *
   * @param file may be null.
   */
  public static void deleteFile(File file) {
    if (file != null) {
      file.delete();
    }
  }

  /**
   * Helper method to build a system-dependent File
   *
   * @param parentDir the parent directory to use.
   * @param pathSegments the relative path segments to use
   * @return the {@link File} representing given path, with each <var>pathSegment</var> separated by
   *     {@link File#separatorChar}
   */
  public static File getFileForPath(File parentDir, String... pathSegments) {
    return new File(parentDir, getPath(pathSegments));
  }

  /**
   * Helper method to build a system-dependent relative path
   *
   * @param pathSegments the relative path segments to use
   * @return the {@link String} representing given path, with each <var>pathSegment</var> separated
   *     by {@link File#separatorChar}
   */
  public static String getPath(String... pathSegments) {
    StringBuilder pathBuilder = new StringBuilder();
    boolean isFirst = true;
    for (String path : pathSegments) {
      if (!isFirst) {
        pathBuilder.append(File.separatorChar);
      } else {
        isFirst = false;
      }
      pathBuilder.append(path);
    }
    return pathBuilder.toString();
  }

  /**
   * Recursively search given directory for first file with given name
   *
   * @param dir the directory to search
   * @param fileName the name of the file to search for
   * @return the {@link File} or <code>Optional.empty()</code> if it could not be found
   */
  public static Optional<File> findFile(File dir, String fileName) {
    if (dir.listFiles() != null) {
      for (File file : dir.listFiles()) {
        if (file.isDirectory()) {
          Optional<File> result = findFile(file, fileName);
          if (result.isPresent()) {
            return result;
          }
        }
        // after exploring the sub-dir, if the dir itself is the only match return it.
        if (file.getName().matches(fileName)) {
          return Optional.of(file);
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Recursively find all directories under the given {@code rootDir}
   *
   * @param rootDir the root directory to search in
   * @param relativeParent An optional parent for all {@link File}s returned. If not specified, all
   *     {@link File}s will be relative to {@code rootDir}.
   * @return An set of {@link File}s, representing all directories under {@code rootDir}, including
   *     {@code rootDir} itself. If {@code rootDir} is null, an empty set is returned.
   */
  public static Set<File> findDirsUnder(File rootDir, File relativeParent) {
    Set<File> dirs = new HashSet<>();
    if (rootDir != null) {
      if (!rootDir.isDirectory()) {
        throw new IllegalArgumentException(
            "Can't find dirs under '" + rootDir + "'. It's not a directory.");
      }
      File thisDir = new File(relativeParent, rootDir.getName());
      dirs.add(thisDir);
      for (File file : rootDir.listFiles()) {
        if (file.isDirectory()) {
          dirs.addAll(findDirsUnder(file, thisDir));
        }
      }
    }
    return dirs;
  }

  /**
   * Convert the given file size in bytes to a more readable format in X.Y[KMGT] format.
   *
   * @param sizeLong file size in bytes
   * @return descriptive string of file size
   */
  public static String convertToReadableSize(long sizeLong) {
    double size = sizeLong;
    for (int i = 0; i < SIZE_SPECIFIERS.length; i++) {
      if (size < 1024) {
        return String.format("%.1f%c", size, SIZE_SPECIFIERS[i]);
      }
      size /= 1024f;
    }
    throw new IllegalArgumentException(
        String.format("Passed a file size of %.2f, I cannot count that high", size));
  }

  /**
   * The inverse of {@link #convertToReadableSize(long)}. Converts the readable format described in
   * {@link #convertToReadableSize(long)} to a byte value.
   *
   * @param sizeString the string description of the size.
   * @return the size in bytes
   * @throws IllegalArgumentException if cannot recognize size
   */
  public static long convertSizeToBytes(String sizeString) {
    if (sizeString.isEmpty()) {
      throw new IllegalArgumentException("invalid empty string");
    }
    char sizeSpecifier = sizeString.charAt(sizeString.length() - 1);
    long multiplier = findMultiplier(sizeSpecifier);
    try {
      String numberString = sizeString;
      if (multiplier != 1) {
        // strip off last char
        numberString = sizeString.substring(0, sizeString.length() - 1);
      }
      return multiplier * Long.parseLong(numberString);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(String.format("Unrecognized size %s", sizeString), e);
    }
  }

  private static long findMultiplier(char sizeSpecifier) {
    long multiplier = 1;
    for (int i = 1; i < SIZE_SPECIFIERS.length; i++) {
      multiplier *= 1024;
      if (sizeSpecifier == SIZE_SPECIFIERS[i]) {
        return multiplier;
      }
    }
    // not found
    return 1;
  }

  /** Returns all jar files found in given directory */
  public static List<File> collectJars(File dir) {
    List<File> list = new ArrayList<>();
    File[] jarFiles = dir.listFiles(new JarFilter());
    if (jarFiles != null) {
      Collections.addAll(list, dir.listFiles(new JarFilter()));
    }
    return list;
  }

  private static class JarFilter implements FilenameFilter {
    /** {@inheritDoc} */
    @Override
    public boolean accept(File dir, String name) {
      return name.endsWith(".jar");
    }
  }

  /**
   * Helper method to calculate CRC-32 for a file.
   *
   * @return CRC-32 of the file
   * @throws MobileHarnessException if failed to calculate CRC-32 for the file
   */
  public static long calculateCrc32(File file) throws MobileHarnessException {
    try (BufferedInputStream inputSource = new BufferedInputStream(new FileInputStream(file))) {
      return StreamUtil.calculateCrc32(inputSource);
    } catch (MobileHarnessException | IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_CALCULATE_CRC32_ERROR,
          String.format("Failed to calculate CRC-32 for file %s", file.getAbsolutePath()),
          e);
    }
  }

  /**
   * Helper method to calculate md5 for a file.
   *
   * @return md5 of the file
   */
  public static String calculateMd5(File file) {
    try (FileInputStream inputSource = new FileInputStream(file)) {
      return StreamUtil.calculateMd5(inputSource);
    } catch (MobileHarnessException | IOException e) {
      logger.atSevere().log(
          "Failed to calculate md5 for file %s: %s",
          file.getPath(), MoreThrowables.shortDebugString(e));
    }
    return "-1";
  }

  /**
   * Helper method to calculate base64 md5 for a file.
   *
   * @return md5 of the file
   */
  public static String calculateBase64Md5(File file) {
    try (FileInputStream inputSource = new FileInputStream(file)) {
      return StreamUtil.calculateBase64Md5(inputSource);
    } catch (MobileHarnessException | IOException e) {
      logger.atSevere().log(
          "Failed to calculate base64 md5 for file %s: %s",
          file.getPath(), MoreThrowables.shortDebugString(e));
    }
    return "-1";
  }

  /** Converts an integer representing unix mode to a set of {@link PosixFilePermission}s */
  public static Set<PosixFilePermission> unixModeToPosix(int mode) {
    Set<PosixFilePermission> result = EnumSet.noneOf(PosixFilePermission.class);
    for (PosixFilePermission pfp : EnumSet.allOf(PosixFilePermission.class)) {
      int m = PERM_MODE_MAP.get(pfp);
      if ((m & mode) == m) {
        result.add(pfp);
      }
    }
    return result;
  }

  /**
   * Get all file paths of files in the given directory with name matching the given filter
   *
   * @param dir {@link File} object of the directory to search for files recursively
   * @param filter {@link String} of the regex to match file names
   * @return a set of {@link String} of the file paths
   */
  public static Set<String> findFiles(File dir, String filter) throws MobileHarnessException {
    Set<String> files = new HashSet<>();
    try (Stream<Path> stream =
        Files.walk(Path.of(dir.getAbsolutePath()), FileVisitOption.FOLLOW_LINKS)) {
      stream
          .filter(path -> path.getFileName().toString().matches(filter))
          .forEach(path -> files.add(path.toString()));
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_WALK_FILES_ERROR,
          String.format("Failed to find files with filter %s in directory %s", filter, dir),
          e);
    }
    return files;
  }

  /**
   * Search and return the first directory {@link File} among other directories.
   *
   * @param dirName The directory name we are looking for.
   * @param dirs The list of directories we are searching.
   * @return a {@link File} with the directory found or Null if not found.
   * @throws MobileHarnessException if failed to find the directory
   */
  public static Optional<File> findDirectory(String dirName, File... dirs)
      throws MobileHarnessException {
    for (File dir : dirs) {
      Set<File> testSrcs = findFilesObject(dir, dirName);
      if (testSrcs.isEmpty()) {
        continue;
      }
      for (File file : testSrcs) {
        if (file.isDirectory()) {
          return Optional.of(file);
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Get all file paths of files in the given directory with name matching the given filter
   *
   * @param dir {@link File} object of the directory to search for files recursively
   * @param filter {@link String} of the regex to match file names
   * @return a set of {@link File} of the file objects. @See {@link #findFiles(File, String)}
   */
  public static Set<File> findFilesObject(File dir, String filter) throws MobileHarnessException {
    return findFilesObject(dir, filter, true);
  }

  /**
   * Get all file paths of files in the given directory with name matching the given filter
   *
   * @param dir {@link File} object of the directory to search for files recursively
   * @param filter {@link String} of the regex to match file names
   * @param includeDirectory whether to include directories in the search result
   * @return a set of {@link File} of the file objects. @See {@link #findFiles(File, String)}
   */
  public static Set<File> findFilesObject(File dir, String filter, boolean includeDirectory)
      throws MobileHarnessException {
    Set<File> files = new LinkedHashSet<>();
    try (Stream<Path> stream =
        Files.walk(Path.of(dir.getAbsolutePath()), FileVisitOption.FOLLOW_LINKS)) {
      if (includeDirectory) {
        stream
            .filter(path -> path.getFileName().toString().matches(filter))
            .forEach(path -> files.add(path.toFile()));
      } else {
        stream
            .filter(path -> path.getFileName().toString().matches(filter) && path.toFile().isFile())
            .forEach(path -> files.add(path.toFile()));
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_WALK_FILES_ERROR,
          String.format("Failed to find files with filter %s in directory %s", filter, dir),
          e);
    }
    return files;
  }

  /**
   * Get file's content type based it's extension.
   *
   * @param filePath the file path
   * @return content type
   */
  public static String getContentType(String filePath) {
    int index = filePath.lastIndexOf('.');
    String ext = "";
    if (index >= 0) {
      ext = filePath.substring(index + 1);
    }
    LogDataType[] dataTypes = LogDataType.values();
    for (LogDataType dataType : dataTypes) {
      if (ext.equals(dataType.getFileExt())) {
        return dataType.getContentType();
      }
    }
    return LogDataType.UNKNOWN.getContentType();
  }

  /**
   * Save a resource file to a directory.
   *
   * @param resourceStream a {link InputStream} object to the resource to be saved.
   * @param destDir a {@link File} object of a directory to where the resource file will be saved.
   * @param targetFileName a {@link String} for the name of the file to be saved to.
   * @return a {@link File} object of the file saved.
   * @throws MobileHarnessException if the file failed to be saved.
   */
  public static File saveResourceFile(
      InputStream resourceStream, File destDir, String targetFileName)
      throws MobileHarnessException {
    // FileWriter writer = null;
    File file = Path.of(destDir.getAbsolutePath(), targetFileName).toFile();
    try (resourceStream;
        Writer writer = Files.newBufferedWriter(file.toPath(), UTF_8)) {
      StreamUtil.copyStreamToWriter(resourceStream, writer);
      return file;
    } catch (MobileHarnessException | IOException e) {
      logger.atSevere().log(
          "Error while saving resource %s/%s: %s",
          destDir, targetFileName, MoreThrowables.shortDebugString(e));
      deleteFile(file);
      throw new MobileHarnessException(
          BasicErrorId.FILE_UTIL_SAVE_RESOURCE_FILE_ERROR,
          String.format("Error while saving resource %s/%s", destDir, targetFileName),
          e);
    }
  }

  /** Returns the size reported by the directory. */
  public static Optional<Long> sizeOfDirectory(File directory) {
    if (directory == null || !directory.isDirectory()) {
      return Optional.empty();
    }
    Path folder = directory.getAbsoluteFile().toPath();
    try {
      long size = 0;
      try (Stream<Path> stream = Files.walk(folder, FileVisitOption.FOLLOW_LINKS)) {
        size = stream.filter(p -> p.toFile().isFile()).mapToLong(p -> p.toFile().length()).sum();
      }
      logger.atFine().log(
          "Directory '%s' has size: %s. Contains: %s",
          directory, size, Arrays.asList(directory.list()));
      return Optional.of(size);
    } catch (IOException | RuntimeException e) {
      logger.atSevere().log(
          "Failed to get size of directory %s: %s", directory, MoreThrowables.shortDebugString(e));
    }
    return Optional.empty();
  }

  /** Returns true if the message is an disk space error. */
  public static boolean isDiskSpaceError(String message) {
    return DISK_SPACE_ERRORS.contains(message);
  }
}
