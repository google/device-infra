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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.shared.util.command.Timeout.fixed;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Arrays.stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.Timeout;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Utility class for operating with files on local disc.
 *
 * <p>Naming conversions for this class:
 *
 * <ul>
 *   <li>Public methods are sorted by alphabet order.
 *   <li>The method names should make it clear whether a method is used for a file, or dir, or both,
 *       like doFileXxx(), doDirXxx(), doFileOrDirXxx().
 *   <li>The params of the methods should make it clear whether it is for a file, or dir, or both,
 *       like filePath, dirPath, fileOrDirPath.
 *   <li>When using String param to represent the file/dir path, add the "Path" suffix. When using
 *       "Path" as param type, do NOT add the "Path" suffix.
 * </ul>
 *
 * <p>Error handling rules: DO NOT throw out the exception from the underlying CommandExecutor
 * directly.
 */
public class LocalFileUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final FileAttribute<Set<PosixFilePermission>> FULL_ACCESS =
      PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx"));

  /** Size of the buffer when reading input streams or writing output streams. */
  private static final int BUFFER_SIZE = 1024;

  /** Max number of attempts when creating a soft link. */
  private static final int LINK_ATTEMPTS = 100;

  /** Timeout setting for slow commands. */
  private static final Duration SLOW_CMD_TIMEOUT = Duration.ofMinutes(10);

  /** System command executor. */
  private final CommandExecutor cmdExecutor;

  public LocalFileUtil() {
    this(new CommandExecutor());
  }

  @VisibleForTesting
  public LocalFileUtil(CommandExecutor cmdExecutor) {
    this.cmdExecutor = cmdExecutor;
  }

  /**
   * Appends content of file {@code srcFile} to file {@code dstFile}. {@code srcFile} must exist
   * before using this method.
   */
  public void appendToFile(Path srcFile, Path dstFile) throws MobileHarnessException {
    checkFile(srcFile);
    try (SeekableByteChannel out =
        Files.newByteChannel(dstFile, EnumSet.of(WRITE, CREATE, APPEND))) {
      ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
      try (SeekableByteChannel in = Files.newByteChannel(srcFile)) {
        while (in.read(buffer) >= 0) {
          in.read(buffer);
          buffer.flip();

          out.write(buffer);
          buffer.clear();
        }
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_APPEND_ERROR,
          String.format("Failed to append file %s to file %s", srcFile, dstFile),
          e);
    }
  }

  /** Changes group of {@code path} to {@code group}. */
  public String changeFileOrDirGroup(String fileOrDirPath, String group)
      throws MobileHarnessException, InterruptedException {
    checkFileOrDir(fileOrDirPath);
    try {
      return cmdExecutor.run(Command.of("chgrp", "-R", group, fileOrDirPath));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_CHANGE_GROUP_ERROR,
          String.format("Failed to change the file/dir %s to group %s", fileOrDirPath, group),
          e);
    }
  }

  /** Changes owner of {@code path} to {@code user}. */
  public String changeFileOrDirOwner(String fileOrDirPath, String user)
      throws MobileHarnessException, InterruptedException {
    checkFileOrDir(fileOrDirPath);
    try {
      return cmdExecutor.run(Command.of("chown", "-R", user, fileOrDirPath));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_CHANGE_OWNER_ERROR,
          String.format("Failed to change the file/dir %s to owner %s", fileOrDirPath, user),
          e);
    }
  }

  /**
   * Makes sure the directory exists.
   *
   * @throws MobileHarnessException if the directory does not exist, or there is file exists with
   *     the same path name
   */
  public File checkDir(String dirPath) throws MobileHarnessException {
    File dir = checkFileOrDir(dirPath);
    if (dir.isFile()) {
      throw new MobileHarnessException(BasicErrorId.LOCAL_DIR_IS_FILE, dir + " is not a directory");
    }
    return dir;
  }

  /**
   * Makes sure the directory exists. The same as {@link #checkDir(String)}, but uses java.nio.
   *
   * @throws MobileHarnessException if the directory does not exist, or there is file exists with
   *     the same path name
   */
  public Path checkDir(Path dir) throws MobileHarnessException {
    dir = checkFileOrDir(dir);
    if (!Files.isDirectory(dir)) {
      throw new MobileHarnessException(BasicErrorId.LOCAL_DIR_IS_FILE, dir + " is not a directory");
    }
    return dir;
  }

  /**
   * Makes sure the file exists.
   *
   * @throws MobileHarnessException if the file does not exist
   */
  public File checkFile(String filePath) throws MobileHarnessException {
    File file = checkFileOrDir(filePath);
    if (file.isDirectory()) {
      throw new MobileHarnessException(BasicErrorId.LOCAL_FILE_IS_DIR, file + " is not a file");
    }
    return file;
  }

  /**
   * Makes sure the file exists. The same as {@link #checkFile(String)}, but uses java.nio.
   *
   * @throws MobileHarnessException if the file does not exist
   */
  public Path checkFile(Path file) throws MobileHarnessException {
    file = checkFileOrDir(file);
    if (Files.isDirectory(file)) {
      throw new MobileHarnessException(BasicErrorId.LOCAL_FILE_IS_DIR, file + " is not a file");
    }
    return file;
  }

  /**
   * Makes sure the directory or file exists.
   *
   * @throws MobileHarnessException if the file or directory does not exist
   */
  public File checkFileOrDir(String fileOrDirPath) throws MobileHarnessException {
    File fileOrDir = getFileOrDir(fileOrDirPath);
    if (!fileOrDir.exists()) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND,
          "Can not find file or directory: " + fileOrDirPath);
    }
    return fileOrDir;
  }

  /**
   * Makes sure the directory or file exists. The same as {@link #checkFileOrDir(String)}, but uses
   * java.nio.
   *
   * @throws MobileHarnessException if the file or directory does not exist
   */
  public Path checkFileOrDir(Path fileOrDir) throws MobileHarnessException {
    if (!Files.exists(fileOrDir)) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND, "Can not find file or directory: " + fileOrDir);
    }
    return fileOrDir;
  }

  /**
   * Clear all unopened files or directories directly under the given directory. It contains the
   * following steps to lists all opened files and formats the output with file names only before
   * removing unopened files.
   *
   * <p>1. Use command - "lsof -w -Fn +D <dir>" to list opened files. Normall there will be 3 lines
   * per file.
   *
   * <p>1.1 A line of process id with leading p like - p12345
   *
   * <p>1.2 A line of file descriptor with leading f like - fcwd
   *
   * <p>1.3 A line of file name with leading n like - n/foo/bar/filename
   *
   * <p>2. Use sed command to keep lines of filenames and remove the leading n.
   *
   * <p>3. Use sort command to sort and remove duplicate lines.
   *
   * <p>This method may consume GB memory if passes a high level directory path like root - /.
   */
  public void clearUnopenedFilesOrDirs(Path dirPath)
      throws MobileHarnessException, InterruptedException {
    checkDir(dirPath);

    String listOpenedFiles = String.format("lsof -w -Fn +D %s", dirPath);
    String removeUnrelatedLines = "sed '/^[^n]/d'";
    String removeLeadingN = "sed 's/^n//'";
    String sortAndRemoveDuplicateLines = "sort -u";
    // Command "lsof+D path" could easily return 1 if there are files under this directory
    // which aren't opened by any process.
    Command command =
        Command.of(
                "/bin/sh",
                "-c",
                String.join(
                    " | ",
                    listOpenedFiles,
                    removeUnrelatedLines,
                    removeLeadingN,
                    sortAndRemoveDuplicateLines))
            .successExitCodes(0, 1);

    String allOpenedFilesOrDirs;
    try {
      allOpenedFilesOrDirs = cmdExecutor.exec(command).stdout();
      logger.atInfo().log("Opened files or dirs: %s", allOpenedFilesOrDirs);
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_LIST_OPEN_FILES_ERROR,
          String.format("Failed to list open files or directories under %s", dirPath),
          e);
    }

    List<String> allFilesOrDirs = listFileOrDirPaths(dirPath.toString());
    for (String fileOrDir : allFilesOrDirs) {
      if (allOpenedFilesOrDirs.contains(fileOrDir)) {
        // Skip opened files or dirs.
        continue;
      }
      try {
        removeFileOrDir(fileOrDir);
      } catch (MobileHarnessException e) {
        // Catch exception here to avoid breaking the whole process.
        logger.atWarning().withCause(e).log("Failed to remove unopened file or dir %s.", fileOrDir);
      }
    }
  }

  /**
   * Copies a single file, or recursively copies a directory.
   *
   * <p>It uses Java NIO to iterate the files under the source directory and copy to the destination
   * directory. The symbolic link is not followed when copy the file.
   *
   * @throws MobileHarnessException if fails to copy
   */
  public void copyFileOrDir(String srcFileOrDirPath, String desFileOrDirPath)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Copy file or dir from %s to %s", srcFileOrDirPath, desFileOrDirPath);
    // If the dest is an existing directory, copy the dir/dir under it, just like "cp -rf" does.
    if (Files.isDirectory(Paths.get(desFileOrDirPath))) {
      desFileOrDirPath = PathUtil.join(desFileOrDirPath, PathUtil.basename(srcFileOrDirPath));
    }

    try {
      String finalDesFileOrDirPath = desFileOrDirPath;
      Files.walkFileTree(
          Paths.get(srcFileOrDirPath),
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
              Path targetFile =
                  Paths.get(
                      finalDesFileOrDirPath, dir.toString().substring(srcFileOrDirPath.length()));
              if (!Files.exists(targetFile)) {
                Files.createDirectory(targetFile);
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              Path targetFile =
                  Paths.get(
                      finalDesFileOrDirPath, file.toString().substring(srcFileOrDirPath.length()));
              Files.copy(
                  file, targetFile, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_COPY_ERROR, "Failed to copy file or dir", e);
    }
  }

  /** See {@link #copyFileOrDir(String, String)}. */
  public void copyFileOrDir(Path srcFileOrDir, Path desFileOrDir)
      throws MobileHarnessException, InterruptedException {
    copyFileOrDir(
        srcFileOrDir.toAbsolutePath().toString(), desFileOrDir.toAbsolutePath().toString());
  }

  /**
   * Copies a single file, or recursively copies a directory.
   *
   * <p>For example, calling this method with parameters ("/foo", "/goo", ["-r", "-fL", "--update"])
   * is equivalent to calling system command "cp -r -fL --update /foo /goo".
   *
   * <p>The difference between this method and {@link #copyFileOrDir(String, String)} is {@link
   * #copyFileOrDir(String, String)} is implemented with Java NIO and this method is implemented
   * with Linux command "copy". In most cases, you should use {@link #copyFileOrDir(String, String)}
   * because it's more efficient. Calling this method with {@code overridingCopyOptions} "-rf" has
   * same behavior with calling {@link #copyFileOrDir(String, String)}, but it's slower.
   */
  public void copyFileOrDirWithOverridingCopyOptions(
      Path srcFileOrDir, Path desFileOrDir, List<String> overridingCopyOptions)
      throws MobileHarnessException, InterruptedException {
    try {
      cmdExecutor.exec(
          Command.of(
              "cp",
              Stream.concat(
                      overridingCopyOptions.stream(),
                      Stream.of(
                          srcFileOrDir.toAbsolutePath().toString(),
                          desFileOrDir.toAbsolutePath().toString()))
                  .collect(toImmutableList())));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_COPY_ERROR, "Failed to copy file or dir", e);
    }
  }

  /** Creates a temp directory under the parent directory. */
  public String createTempDir(String parentDirPath) throws MobileHarnessException {
    String tempDirectoryPath = PathUtil.join(parentDirPath, UUID.randomUUID().toString());
    try {
      prepareDir(tempDirectoryPath, FULL_ACCESS);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_DIR_CREATE_TMP_ERROR,
          "Failed to create temp dir under " + parentDirPath,
          e);
    }
    return tempDirectoryPath;
  }

  /** Creates a temp file under the directory. */
  public Path createTempFile(Path parentDirPath, String fileNamePrefix, String fileNameSuffix)
      throws MobileHarnessException {
    try {
      return Files.createTempFile(parentDirPath, fileNamePrefix, fileNameSuffix);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_DIR_CREATE_TMP_ERROR,
          "Failed to create temp file under " + parentDirPath,
          e);
    }
  }

  /** Creates a temp file under the directory. */
  public String createTempFile(String parentDirPath, String fileNamePrefix, String fileNameSuffix)
      throws MobileHarnessException {
    return createTempFile(Paths.get(parentDirPath), fileNamePrefix, fileNameSuffix).toString();
  }

  public Instant getFileLastModifiedTime(String filePath) throws MobileHarnessException {
    return getFileLastModifiedTime(Paths.get(filePath));
  }

  public Instant getFileLastModifiedTime(Path file) throws MobileHarnessException {
    try {
      return Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis());
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_GET_MODIFIED_TIME_ERROR,
          String.format("Failed to get the last modified time of file %s", file),
          e);
    }
  }

  /**
   * Returns an instance of the AbstractFile implementation for the file or folder that corresponds
   * to the given filename. We don't guarantee that the file or folder exists.
   */
  public File getFileOrDir(String fileOrDirPath) {
    return new File(fileOrDirPath);
  }

  /**
   * Gets the human readable size of the given file/directory.
   *
   * @throws MobileHarnessException if fails to get the readable size of the file/directory
   */
  public String getFileOrDirHumanReadableSize(String fileOrDirPath)
      throws MobileHarnessException, InterruptedException {
    checkFileOrDir(fileOrDirPath);
    // This command could be very slow if the folder is too large.
    String output;
    try {
      output =
          cmdExecutor
              .run(Command.of("du", "-sh", fileOrDirPath).timeout(Duration.ofMinutes(10)))
              .trim();
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_GET_SIZE_ERROR,
          "Failed to check the size of file/dir " + fileOrDirPath,
          e);
    }
    // Example:
    // $ du -sh /var/www/
    // 286M /var/www/
    List<String> words = Splitter.onPattern("\\s+").splitToList(output);
    if (words.size() != 2) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_GET_SIZE_ERROR,
          String.format(
              "Failed to parse the size of file/dir %s from:%n%s", fileOrDirPath, output));
    }
    return words.get(0);
  }

  /** Returns the name of the given file or folder. */
  public String getFileOrDirName(String fileOrDirPath) {
    return getFileOrDir(fileOrDirPath).getName();
  }

  /** Returns the name of the given file or folder without extension. */
  public String getFileOrDirNameWithoutExtension(String fileOrDirPath) {
    String fileOrDirName = getFileOrDirName(fileOrDirPath);
    int dotIndex = fileOrDirName.lastIndexOf('.');
    return (dotIndex == -1) ? fileOrDirName : fileOrDirName.substring(0, dotIndex);
  }

  /**
   * Returns the *real* path of {@code path}. If {@code path} is a relative path, it will be
   * resolved to the absolute format. If {@code path} is a symbolic link, or any prefix of it is a
   * symbolic link, the link will be replaced by the *real* path.
   */
  public Path getFileOrDirRealPath(Path fileOrDir) throws MobileHarnessException {
    try {
      return fileOrDir.toRealPath();
    } catch (IOException | SecurityException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_GET_REAL_PATH_ERROR,
          "Failed to get real path of " + fileOrDir,
          e);
    }
  }

  /** Gets the size of the given file/directory. */
  public long getFileOrDirSize(String fileOrDirPath)
      throws MobileHarnessException, InterruptedException {
    checkFileOrDir(fileOrDirPath);
    // This command could be very slow if the folder is too large.
    String output;
    try {
      output =
          cmdExecutor.run(Command.of("du", "-s", fileOrDirPath).timeout(SLOW_CMD_TIMEOUT)).trim();
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_GET_SIZE_ERROR,
          "Failed to check the size of file/dir " + fileOrDirPath,
          e);
    }
    // Example: (default size unit is KB)
    // $ du -s /var/www/
    // 9539176 /var/www/
    List<String> words = Splitter.onPattern("\\s+").splitToList(output);
    if (words.size() < 2) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_PARSE_SIZE_ERROR,
          String.format(
              "Failed to parse the size of file/dir %s from:%n%s", fileOrDirPath, output));
    }
    try {
      return Long.parseLong(words.get(0)) << 10L;
    } catch (NumberFormatException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_PARSE_SIZE_ERROR,
          String.format("Failed to parse the size of file/dir %s from:%n%s", fileOrDirPath, output),
          e);
    }
  }

  /** Returns the owner of file {@code path}. */
  public String getFileOwner(Path file) throws MobileHarnessException {
    try {
      return Files.getOwner(file).getName();
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_GET_OWNER_ERROR, "Failed to get owner of file " + file, e);
    }
  }

  /** Gets file permissions of file {@code path}. */
  public Set<PosixFilePermission> getFilePermission(Path file) throws MobileHarnessException {
    try {
      return Files.getPosixFilePermissions(file);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_GET_PERMISSION_ERROR,
          "Failed to get permission of file " + file,
          e);
    }
  }

  /** Gets file permissions in the form of "rwxrwxrwx". */
  public String getFilePermissionString(Path file) throws MobileHarnessException {
    return PosixFilePermissions.toString(getFilePermission(file));
  }

  /** Gets the size of given {@code path}. */
  public long getFileSize(Path file) throws MobileHarnessException {
    try {
      return Files.size(file);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_GET_SIZE_ERROR, "Failed to get size of file: " + file, e);
    }
  }

  /** Gets the size of given {@code filePath}. */
  public long getFileSize(String filePath) throws MobileHarnessException {
    return getFileSize(Paths.get(filePath));
  }

  /** Returns the absolute path of the given file/dir's parent dir. */
  public String getParentDirPath(String fileOrDirPath) {
    File parentDir = getFileOrDir(fileOrDirPath).getParentFile();
    return parentDir == null ? "" : parentDir.getAbsolutePath();
  }

  /**
   * Grants full access to the file or directory.
   *
   * @param fileOrDirPath path and name of the file or directory
   * @throws MobileHarnessException file or directory does not exist, or can not grant full access
   *     to it
   */
  public void grantFileOrDirFullAccess(String fileOrDirPath) throws MobileHarnessException {
    if (isFullyAccessible(Paths.get(fileOrDirPath))) {
      return;
    }

    // Do NOT use AbstractFile.setPermissions(). It does not work on Mac.
    File fileOrDir = checkFileOrDir(fileOrDirPath);

    List<String> errors = new ArrayList<>();
    if (!fileOrDir.setReadable(true, false)) {
      errors.add("read");
    }

    if (!fileOrDir.setWritable(true, false)) {
      errors.add("write");
    }

    if (!fileOrDir.setExecutable(true, false)) {
      errors.add("execution");
    }

    if (errors.isEmpty()) {
      return;
    }

    String message = String.format("Fail to grant %s access to %s", errors, fileOrDir);
    throw new MobileHarnessException(BasicErrorId.LOCAL_FILE_GRANT_PERMISSION_ERROR, message);
  }

  /**
   * Grants full access to the file or directory.
   *
   * @param fileOrDir path and name of the file or directory
   * @throws MobileHarnessException file or directory does not exist, or can not grant full access
   *     to it
   */
  public void grantFileOrDirFullAccess(Path fileOrDir) throws MobileHarnessException {
    grantFileOrDirFullAccess(fileOrDir.toString());
  }

  /**
   * Grants full access to the file or directory recursively.
   *
   * @param fileOrDirPath path and name of the file or directory
   * @throws MobileHarnessException file or directory does not exist, or can not grant full access
   *     to it
   */
  public void grantFileOrDirFullAccessRecursively(String fileOrDirPath)
      throws MobileHarnessException, InterruptedException {
    if (areAllFilesFullAccessible(Paths.get(fileOrDirPath))) {
      return;
    }

    // Does NOT use AbstractFile.setPermissions(). It does not work on Mac.
    File fileOrDir = checkFileOrDir(fileOrDirPath);
    try {
      // Does not use fileOrDir.setReadable()..., since it cannot take effect recursively.
      cmdExecutor.exec(Command.of("chmod", "-R", "777", fileOrDirPath));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_GRANT_PERMISSION_RECURSIVELY_ERROR,
          "Fail to grant full access recursively to " + fileOrDir,
          e);
    }
  }

  /**
   * Grants full access to the file or directory recursively.
   *
   * @param fileOrDirPath path and name of the file or directory
   * @throws MobileHarnessException file or directory does not exist, or can not grant full access
   *     to it
   */
  public void grantFileOrDirFullAccessRecursively(Path fileOrDirPath)
      throws MobileHarnessException, InterruptedException {
    grantFileOrDirFullAccessRecursively(fileOrDirPath.toString());
  }

  /** Returns true only if it is a directory (not file) and exists. */
  public boolean isDirExist(String dirPath) {
    try {
      checkDir(dirPath);
      return true;
    } catch (MobileHarnessException e) {
      return false;
    }
  }

  /**
   * Returns true only if it is a directory (not file) and exists. The same as {@link
   * #isDirExist(String)}, but uses java.nio.
   */
  public boolean isDirExist(Path dir) {
    try {
      checkDir(dir);
      return true;
    } catch (MobileHarnessException e) {
      return false;
    }
  }

  /** Returns true only if it is a file (not directory) and exists. */
  public boolean isFileExist(String filePath) {
    try {
      checkFile(filePath);
      return true;
    } catch (MobileHarnessException e) {
      return false;
    }
  }

  /**
   * Returns true only if it is a file (not directory) and exists. The same as {@link
   * #isFileExist(String)}, but uses java.nio.
   */
  public boolean isFileExist(Path file) {
    try {
      checkFile(file);
      return true;
    } catch (MobileHarnessException e) {
      return false;
    }
  }

  /** Returns true only if this file or directory exists. */
  public boolean isFileOrDirExist(String fileOrDirPath) {
    try {
      checkFileOrDir(fileOrDirPath);
      return true;
    } catch (MobileHarnessException e) {
      return false;
    }
  }

  /**
   * Returns true only if this file or directory exists. The same as {@link
   * #isFileOrDirExist(String)}, but uses java.nio.
   */
  public boolean isFileOrDirExist(Path fileOrDir) {
    try {
      checkFileOrDir(fileOrDir);
      return true;
    } catch (MobileHarnessException e) {
      return false;
    }
  }

  /**
   * Returns true if this file exists, OR exists in at least one dir (not including sub dirs) of the
   * PATH environment variable.
   *
   * <p>In another word, if this method returns true, it means the file path can be used as the
   * executable of a command (of course, the file itself needs to be executable).
   *
   * <p>The file path can be an absolute path, a relative path, or a file name (without "/") in
   * PATH.
   */
  public boolean isFileExistInPath(String filePath) {
    if (isFileExist(filePath)) {
      return true;
    }
    if (filePath.contains(File.separator)) {
      return false;
    }
    return stream(System.getenv("PATH").split(Pattern.quote(File.pathSeparator)))
        .map(Path::of)
        .anyMatch(path -> isFileExist(path.resolve(filePath)));
  }

  /** Returns true only if the file/dir is local. */
  public boolean isLocalFileOrDir(String fileOrDirPath) {
    return true;
  }

  /** Returns whether {@code file} is a symbolic link. */
  public boolean isSymbolicLink(Path file) {
    try {
      return Files.isSymbolicLink(file);
    } catch (SecurityException e) {
      logger.atWarning().withCause(e).log(
          "Failed to check whether file %s is a symbolic link, return false.", file);
      return false;
    }
  }

  /**
   * Returns whether {@code file} is a hard link. This method can only be used in file systems which
   * support the "unix" view.
   */
  public boolean isHardLink(Path file) {
    try {
      // Hard linked files' hard link count is greater than 1.
      return (Integer) Files.getAttribute(file, "unix:nlink") >= 2;
    } catch (SecurityException | IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to check whether file %s is a hard link, return false.", file);
      return false;
    }
  }

  /**
   * Atomically creates a new symbolic link with using the passed arguments.
   *
   * <p>Adapted from guava's Files.createTempDir, see
   *
   * <p>TODO: deprecate linkFileOrDir method in favor of this.
   *
   * @param linkBaseDirPath the base dir for soft link to be created
   * @param linkPrefixName the base name for the soft link to be created
   * @param targetDirPath the target dir for which the generated soft link will point against
   * @return the soft link generated using the passed parameters
   * @throws MobileHarnessException if failed to create the soft link
   */
  public Path linkDir(String linkBaseDirPath, String linkPrefixName, String targetDirPath)
      throws MobileHarnessException {
    Exception lastException = null;
    for (int counter = 0; counter < LINK_ATTEMPTS; counter++) {
      File linkDir = new File(linkBaseDirPath, linkPrefixName + counter);
      if (!linkDir.exists()) {
        try {
          return Files.createSymbolicLink(linkDir.toPath(), Paths.get(targetDirPath));
        } catch (IOException e) {
          if (e instanceof FileAlreadyExistsException && counter < LINK_ATTEMPTS - 1) {
            lastException = e;
            continue;
          }
          throw new MobileHarnessException(
              BasicErrorId.LOCAL_DIR_LINK_ERROR,
              String.format(
                  "Failed to create symbolic link. link: %s, target: %s ", linkDir, targetDirPath),
              e);
        }
      }
    }
    throw new MobileHarnessException(
        BasicErrorId.LOCAL_DIR_LINK_ERROR_WITH_RETRY,
        "Failed to create directory within "
            + LINK_ATTEMPTS
            + " attempts (tried "
            + linkPrefixName
            + "0 to "
            + linkPrefixName
            + (LINK_ATTEMPTS - 1)
            + ')',
        lastException);
  }

  /**
   * Creates a symbolic link of to the target file or directory with the given link name. If
   * destination files already exists, will be removed.
   *
   * @throws MobileHarnessException if fails to create symbolic link
   */
  public void linkFileOrDir(String targetFileOrDirPath, String linkName)
      throws MobileHarnessException, InterruptedException {
    checkFileOrDir(targetFileOrDirPath);
    try {
      cmdExecutor.exec(Command.of("ln", "-sf", targetFileOrDirPath, linkName));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_LINK_ERROR,
          "Failed to create symbolic link for " + targetFileOrDirPath,
          e);
    }
  }

  /**
   * Creates a hard link of to the target file with the given link name. If the destination file
   * already exists, it will be removed. Hard link can only be applied to files while it does not
   * work for directory.
   *
   * @throws MobileHarnessException if fails to create symbolic link
   */
  public void hardLinkFile(String targetFilePath, String linkName)
      throws MobileHarnessException, InterruptedException {
    checkFile(targetFilePath);
    try {
      cmdExecutor.exec(Command.of("ln", "-f", targetFilePath, linkName));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_CREATE_HARD_LINK_ERROR,
          "Failed to create hard link for " + targetFilePath,
          e);
    }
  }

  /**
   * Reads links under dirPath and return paths to linked files. Tested on Mac, Ubuntu, and MTaaS
   * Docker container (Ubuntu
   *
   * @param dirPath a directory path
   * @return a set of string for all linked files
   */
  public Set<String> listAllFilesBeenLinked(String dirPath)
      throws MobileHarnessException, InterruptedException {
    try {
      String[] files =
          cmdExecutor
              .run(Command.of("find", dirPath, "-type", "l", "-exec", "readlink", "{}", ";"))
              .split("\n");
      return new HashSet<>(Arrays.asList(files));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_DIR_LIST_LINKS_ERROR,
          "Failed to list the linked files under " + dirPath,
          e);
    }
  }

  /**
   * Lists all the sub-directories directly under the given directory.
   *
   * @throws MobileHarnessException if the given path doesn't exist or is a file, not a directory
   */
  public File[] listDirs(String dirPath) throws MobileHarnessException {
    return listFilesOrDirs(dirPath, File::isDirectory);
  }

  /**
   * Lists all the sub-directories directly under the given directory.
   *
   * @param dirFilter filter for the sub-directories
   * @throws MobileHarnessException if the given path doesn't exist or is a file, not a directory
   */
  public File[] listDirs(String dirPath, @Nullable FileFilter dirFilter)
      throws MobileHarnessException {
    return listFilesOrDirs(
        dirPath,
        (pathname) -> pathname.isDirectory() && (dirFilter == null || dirFilter.accept(pathname)));
  }

  /**
   * Lists all the sub-directories directly under the given directory. The same as {@link
   * #listDirs(String)}, but uses java.nio.
   *
   * @throws MobileHarnessException if the given path doesn't exist or is a file, not a directory
   */
  public List<Path> listDirs(Path dir) throws MobileHarnessException {
    return listFilesOrDirs(dir, Files::isDirectory);
  }

  /**
   * Lists the sub-directories according to the depth under the given directory.
   *
   * @param dirPath the absolute directory path
   * @param depth the sub-directories depth need to scan
   *     <p>If depth is 2, for dirPath/level1/level2, only the directories with the same depth with
   *     level2 will be listed.
   * @param fileFilter filter for the sub-files
   */
  public List<String> listDirs(String dirPath, int depth, @Nullable FileFilter fileFilter)
      throws MobileHarnessException {
    return listDirs(dirPath, depth, false, fileFilter);
  }

  /**
   * Lists the sub-directories according to the depth under the given directory.
   *
   * @param dirPath the absolute directory path
   * @param depth the sub-directories depth need to scan
   * @param recursively whether to scan the sub-directories recursively
   *     <p>If depth is 2 and recursively is true, for dirPath/level1/level2, the directories with
   *     the same depth with level1&level2 will be listed.
   * @param fileFilter filter for the sub-files
   */
  public List<String> listDirs(
      String dirPath, int depth, boolean recursively, @Nullable FileFilter fileFilter)
      throws MobileHarnessException {
    if (depth < 0) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_DIR_LIST_DIR_DEPTH_PARAM_ERROR,
          "The depth should not be less than 0.");
    }
    File dir = checkDir(dirPath);
    List<String> dirs = new ArrayList<>();
    if (depth == 0) {
      if (recursively) {
        return dirs;
      }
      if (fileFilter == null || fileFilter.accept(dir)) {
        dirs.add(dirPath);
      }
    } else {
      for (File subFileOrDir : listFilesOrDirs(dir, /* filter= */ null)) {
        String subFileOrDirPath = subFileOrDir.getAbsolutePath();
        if (recursively && (fileFilter == null || fileFilter.accept(subFileOrDir))) {
          dirs.add(subFileOrDirPath);
        }
        if (subFileOrDir.isDirectory()) {
          dirs.addAll(listDirs(subFileOrDirPath, depth - 1, recursively, fileFilter));
        }
      }
    }
    return dirs;
  }

  /**
   * Gets the absolute paths of all files under the given directory.
   *
   * @param recursively whether to scan the sub-directories recursively
   * @return absolute paths of the files
   * @throws MobileHarnessException if the given path doesn't exist or is a file, not a directory
   */
  public List<String> listFilePaths(String dirPath, boolean recursively)
      throws MobileHarnessException {
    return listFilePaths(dirPath, recursively, /* fileFilter= */ null);
  }

  /**
   * Gets the absolute paths of all files under the given directory that satisfy the specified
   * filter.
   *
   * @param dirPath the absolute directory path
   * @param recursively whether to scan the sub-directories recursively
   * @param fileFilter filter for the sub-files, sub-directories don't need to satisfy this filter
   * @return absolute paths of the files
   * @throws MobileHarnessException if the given path doesn't exist or is a file, not a directory
   */
  public List<String> listFilePaths(
      String dirPath, boolean recursively, @Nullable FileFilter fileFilter)
      throws MobileHarnessException {
    List<String> filePaths = new ArrayList<>();
    for (File file : listFilesOrDirs(dirPath, fileFilter)) {
      if (file.isFile()) {
        filePaths.add(file.getAbsolutePath());
      }
    }
    if (recursively) {
      for (File subDir : listDirs(dirPath)) {
        filePaths.addAll(listFilePaths(subDir.getAbsolutePath(), true, fileFilter));
      }
    }
    return filePaths;
  }

  /**
   * Gets the absolute paths of all files under the given directories.
   *
   * @param recursively whether to scan the sub-directories recursively
   * @return absolute paths of the files
   * @throws MobileHarnessException if the given path doesn't exist or is a file, not a directory
   */
  public List<String> listFilePaths(Iterable<String> dirPaths, boolean recursively)
      throws MobileHarnessException {
    List<String> filePaths = new ArrayList<>();
    for (String dirPath : dirPaths) {
      filePaths.addAll(listFilePaths(dirPath, recursively));
    }
    return filePaths;
  }

  /**
   * Gets the absolute paths of all files under the given directory. The same as {@link
   * #listFilePaths(String, boolean)}, but uses java.nio.
   *
   * @param recursively whether to scan the sub-directories recursively
   * @return paths of the files
   * @throws MobileHarnessException if the given path doesn't exist or is a file, not a directory
   */
  public List<Path> listFilePaths(Path dir, boolean recursively) throws MobileHarnessException {
    return listFilePaths(dir, recursively, dummy -> true);
  }

  /**
   * Gets the absolute paths of all files under the given directory that satisfy the specified
   * filter. The same as {@link #listFilePaths(String, boolean, FileFilter)}, but uses java.nio.
   *
   * @param dir the directory path
   * @param recursively whether to scan the sub-directories recursively
   * @return absolute paths of the files
   * @throws MobileHarnessException if the given path doesn't exist or is a file, not a directory
   */
  public List<Path> listFilePaths(
      Path dir, boolean recursively, DirectoryStream.Filter<Path> filter)
      throws MobileHarnessException {
    checkDir(dir);
    try {
      List<Path> files = new ArrayList<>();
      Files.walkFileTree(
          dir,
          new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) {
              return recursively || directory.equals(dir)
                  ? FileVisitResult.CONTINUE
                  : FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              if (filter.accept(file)) {
                files.add(file);
              }
              return FileVisitResult.CONTINUE;
            }
          });
      return files;
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_DIR_LIST_FILE_PATHS_ERROR, "Failed to list files of " + dir, e);
    }
  }

  /**
   * Lists all the files under the given directory.
   *
   * @param recursively whether to scan the sub-directories recursively
   * @throws MobileHarnessException if the given path doesn't exist or is a file, not a directory
   */
  public List<File> listFiles(String dirPath, boolean recursively) throws MobileHarnessException {
    return listFiles(dirPath, recursively, /* fileFilter= */ null);
  }

  /**
   * Lists the files under the given directory that satisfy the specified filter.
   *
   * @param dirPath the absolute directory path
   * @param fileFilter filter for the sub-files, sub-directories don't need to satisfy this filter
   * @param recursively whether to scan the sub-directories recursively
   * @throws MobileHarnessException if the given path doesn't exist or is a file, not a directory
   */
  public List<File> listFiles(String dirPath, boolean recursively, @Nullable FileFilter fileFilter)
      throws MobileHarnessException {
    return listFiles(dirPath, recursively, fileFilter, null);
  }

  /**
   * Lists the files under the given directory that satisfy the specified filter.
   *
   * @param dirPath the absolute directory path
   * @param fileFilter filter for the sub-files
   * @param dirFilter filter for the sub-directories
   * @param recursively whether to scan the sub-directories recursively
   * @throws MobileHarnessException if the given path doesn't exist or is a file, not a directory
   */
  public List<File> listFiles(
      String dirPath,
      boolean recursively,
      @Nullable FileFilter fileFilter,
      @Nullable FileFilter dirFilter)
      throws MobileHarnessException {
    List<File> files = new ArrayList<>();
    for (File file : listFilesOrDirs(dirPath, fileFilter)) {
      if (file.isFile()) {
        files.add(file);
      }
    }
    if (recursively) {
      for (File subDir : listDirs(dirPath, dirFilter)) {
        files.addAll(listFiles(subDir.getAbsolutePath(), true, fileFilter));
      }
    }
    return files;
  }

  /**
   * Lists the files/directories directly under the given directory.
   *
   * @return array of the files/directories, null if the directory path is invalid
   * @throws MobileHarnessException if the given path doesn't exist or is a file, not a directory
   */
  public File[] listFilesOrDirs(String dirPath) throws MobileHarnessException {
    return listFilesOrDirs(dirPath, /* filter= */ null);
  }

  /**
   * Returns an array of files and directories directly under the given directory that satisfy the
   * specified filter.
   *
   * @param dirPath path of the directory where to find the files/folders
   * @param filter filter for the sub-files and sub-directories
   * @throws MobileHarnessException if the given path doesn't exist or is a file, not a directory
   */
  public File[] listFilesOrDirs(String dirPath, @Nullable FileFilter filter)
      throws MobileHarnessException {
    return listFilesOrDirs(checkDir(dirPath), filter);
  }

  /**
   * Returns an array of files and directories directly under the given directory that satisfy the
   * specified filter. The same as {@link #listFilesOrDirs(String, FileFilter)}, but uses java.nio.
   *
   * @param dir path of the directory where to find the files/folders
   * @param filter filter for the sub-files and sub-directories
   * @throws MobileHarnessException if the given path doesn't exist or is a file, not a directory
   */
  public List<Path> listFilesOrDirs(Path dir, DirectoryStream.Filter<Path> filter)
      throws MobileHarnessException {
    try (DirectoryStream<Path> subDirStream = Files.newDirectoryStream(dir, filter)) {
      List<Path> subDirs = new ArrayList<>();
      for (Path subDir : subDirStream) {
        subDirs.add(subDir);
      }
      return subDirs;
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_DIR_LIST_SUB_DIR_ERROR, "Failed to list sub directories of " + dir, e);
    }
  }

  /**
   * Returns an array of files and directories directly under the given {@link File} directory that
   * satisfy the specified filter.
   *
   * @param dir the directory of {@link File} model to list files and directories
   * @param filter filter to list the sub-files and sub-directories
   * @throws MobileHarnessException if the {@link File#listFiles(FileFilter)} returns null
   */
  private File[] listFilesOrDirs(File dir, @Nullable FileFilter filter)
      throws MobileHarnessException {
    File[] files = dir.listFiles(filter);
    if (files == null) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_DIR_LIST_FILE_OR_DIRS_ERROR,
          String.format("Failed to list files or directories under %s", dir.getAbsolutePath()));
    }
    return files;
  }

  /**
   * Lists the files/directories paths directly under the given directory.
   *
   * @return array of the files/directories, null if the directory path is invalid
   * @throws MobileHarnessException if the given path doesn't exist or is a file, not a directory
   */
  public List<String> listFileOrDirPaths(String dirPath) throws MobileHarnessException {
    return stream(listFilesOrDirs(dirPath)).map(File::getAbsolutePath).collect(toImmutableList());
  }

  /**
   * Moves and merges content of a directory to another. Same as {@link #mergeDir(String, String)},
   * but uses java.nio.
   */
  public void mergeDir(Path srcDir, Path targetDir)
      throws MobileHarnessException, InterruptedException {
    mergeDir(srcDir.toAbsolutePath().toString(), targetDir.toAbsolutePath().toString());
  }

  /** Moves and merges content of a directory to another. */
  public void mergeDir(String srcDirPath, String targetDirPath)
      throws MobileHarnessException, InterruptedException {
    checkDir(srcDirPath);
    prepareDir(targetDirPath);
    // The slash following srcDir is crucial for rsync to sync srcDir with targetDir
    // instead of creating a subdir under targetDir and sync srcDir with that.
    try {
      cmdExecutor.exec(
          Command.of(
              "rsync",
              // '-a' is archive mode. explain from 'rsync' manual:
              //     -a, --archive               archive mode; equals -rlptgoD (no -H,-A,-X)
              // Explanation of '-rlptgoD':
              //     -r, --recursive             recurse into directories
              //     -l, --links                 copy symlinks as symlinks
              //     -p, --perms                 preserve permissions
              //     -t, --times                 preserve modification times
              //     -o, --owner                 preserve owner (super-user only)
              //     -g, --group                 preserve group
              //     -D                          same as --devices --specials
              //         --devices               preserve device files (super-user only)
              //         --specials              preserve special files
              "-a",
              "--no-group",
              "--no-owner",
              srcDirPath + "/",
              targetDirPath + "/"));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_DIR_MERGE_ERROR,
          String.format("Failed to merge dir %s into dir %s", srcDirPath, targetDirPath),
          e);
    }
    removeFileOrDir(srcDirPath);
  }

  /**
   * Moves a single file, or a directory. It will invoke system command "mv ...". The same as {@link
   * #moveFileOrDir(String, String)}, but uses java.nio.
   */
  public void moveFileOrDir(Path srcFileOrDirPath, Path desFileOrDirPath)
      throws MobileHarnessException, InterruptedException {
    moveFileOrDir(srcFileOrDirPath.toString(), desFileOrDirPath.toString());
  }

  /**
   * Moves a single file, or a directory. It will invoke system command "mv ...".
   *
   * @throws MobileHarnessException if fails to move
   */
  public void moveFileOrDir(String srcFileOrDirPath, String desFileOrDirPath)
      throws MobileHarnessException, InterruptedException {
    try {
      cmdExecutor.exec(Command.of("mv", srcFileOrDirPath, desFileOrDirPath));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_MOVE_ERROR,
          String.format(
              "Failed to move file/dir from %s to %s", srcFileOrDirPath, desFileOrDirPath),
          e);
    }
  }

  /** See {@link Files#newBufferedWriter(Path, OpenOption...)}. */
  public BufferedWriter newBufferedWriter(Path filePath, OpenOption... options)
      throws MobileHarnessException {
    prepareParentDir(filePath);
    try {
      return Files.newBufferedWriter(filePath, options);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_NEW_BUFFERED_WRITER_ERROR,
          String.format("Failed to create new buffered writer on file %s", filePath),
          e);
    }
  }

  /**
   * Makes sure the directory exists. If not, will create the directory (and all nonexistent parent
   * directories).
   *
   * @param attrs file attributes to set when creating the new directories
   * @throws MobileHarnessException if unable to create or confirm directory
   */
  public void prepareDir(String dir, FileAttribute<?>... attrs) throws MobileHarnessException {
    prepareDir(Paths.get(dir), attrs);
  }

  /**
   * Makes sure the directory exists. If not, will create the directory (and all nonexistent parent
   * directories).
   *
   * @param attrs file attributes to set when creating the new directories
   * @throws MobileHarnessException if unable to create or confirm directory
   */
  public void prepareDir(Path dir, FileAttribute<?>... attrs) throws MobileHarnessException {
    try {
      Files.createDirectories(dir, attrs);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_DIR_CREATE_ERROR, "Failed to create directory " + dir, e);
    }
  }

  /**
   * Makes sure the parent directory exists. If not, will create the directory (and all nonexistent
   * parent directories).
   *
   * @param attrs file attributes to set when creating the new directories
   * @throws MobileHarnessException if unable to create or confirm directory
   */
  public void prepareParentDir(String fileOrDir, FileAttribute<?>... attrs)
      throws MobileHarnessException {
    String parentDir = new File(fileOrDir).getParent();
    if (parentDir != null) {
      prepareDir(parentDir, attrs);
    }
  }

  /**
   * Makes sure the parent directory exists. If not, will create the directory (and all nonexistent
   * parent directories).
   *
   * @param attrs file attributes to set when creating the new directories
   * @throws MobileHarnessException if unable to create or confirm directory
   */
  public void prepareParentDir(Path fileOrDir, FileAttribute<?>... attrs)
      throws MobileHarnessException {
    prepareParentDir(fileOrDir.toString(), attrs);
  }

  /**
   * Reads the binary file and returns the content. NOTE: only support to read file with size less
   * than 2GB.
   *
   * @throws MobileHarnessException if file not exists, or the given path is a directory, or failed
   *     to read the file
   */
  public byte[] readBinaryFile(String filePath) throws MobileHarnessException {
    File file = checkFile(filePath);
    // The {@link ByteArrayOutputStream} only accepts an integer as its buffer size. So the file
    // size can't be larger than the max value of integer.
    final long maxFileSize = Integer.MAX_VALUE;
    long fileLength = 0L;
    try {
      fileLength = file.length();
    } catch (SecurityException e) {
      logger.atWarning().withCause(e).log("Failed to get the length of file %s", filePath);
    }
    if (fileLength >= maxFileSize) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_TOO_LARGE_TO_READ,
          "Too large file " + filePath + ": " + fileLength + "Byte");
    }
    // Closing a ByteArrayOutputStream has no effect.
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream((int) fileLength);
    try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
      byte[] buffer = new byte[BUFFER_SIZE];
      for (int len = inputStream.read(buffer); len > 0; len = inputStream.read(buffer)) {
        outputStream.write(buffer, 0, len);
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_READ_BINARY_ERROR,
          "Failed to read the content of binary file " + filePath,
          e);
    }
    return outputStream.toByteArray();
  }

  /**
   * Reads the text file and returns the content.
   *
   * @throws MobileHarnessException if file not exists, or the given path is a directory, or failed
   *     to read the file
   */
  public String readFile(String filePath) throws MobileHarnessException {
    try {
      return readFile(Files.newBufferedReader(checkFile(filePath).toPath(), UTF_8));
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_READ_STRING_ERROR,
          "Failed to read content of file " + filePath,
          e);
    }
  }

  /** Reads the reader and returns the content. */
  private static String readFile(Reader reader) throws IOException {
    StringBuilder content = new StringBuilder();
    try (BufferedReader ignored = new BufferedReader(reader)) {
      char[] buffer = new char[BUFFER_SIZE];
      for (int len = reader.read(buffer); len > 0; len = reader.read(buffer)) {
        content.append(buffer, 0, len);
      }
    }
    return content.toString();
  }

  /**
   * Reads the text file and returns the content. The same as {@link #readFile(String)}, but uses
   * java.nio.
   *
   * @throws MobileHarnessException if file not exists, or the given path is a directory, or failed
   *     to read the file
   */
  public String readFile(Path file) throws MobileHarnessException {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_READ_STRING_ERROR, "Failed to read content of file " + file, e);
    }
  }

  /**
   * Reads the first ten lines of text file and returns the content.
   *
   * @throws MobileHarnessException if file not exists, or the given path is a directory, or failed
   *     to read the file
   */
  public String readFileHead(String filePath) throws MobileHarnessException, InterruptedException {
    checkFile(filePath);
    try {
      // Enlarge the timeout in case the command is slow.
      return cmdExecutor.run(Command.of("head", filePath).timeout(SLOW_CMD_TIMEOUT));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_READ_HEAD_ERROR,
          "Failed to read the head of file " + filePath,
          e);
    }
  }

  /**
   * Reads the last {@code line} of text file and returns the content.
   *
   * @throws MobileHarnessException if file not exists, or the given path is a directory, or failed
   *     to read the file
   */
  public String readFileTail(String filePath, int line)
      throws MobileHarnessException, InterruptedException {
    checkFile(filePath);
    try {
      // Enlarge the timeout in case the command is slow.
      return cmdExecutor.run(
          Command.of("tail", String.format("-%d", line), filePath).timeout(SLOW_CMD_TIMEOUT));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_READ_TAIL_ERROR,
          "Failed to read the tail of file " + filePath,
          e);
    }
  }

  /**
   * Saves the content of the files on the device in the set of strings.
   *
   * @throws MobileHarnessException if failed to read the file
   */
  public Set<String> readLineSetFromFiles(Collection<String> filePaths)
      throws MobileHarnessException {
    Set<String> lines = new HashSet<>();
    for (String filePath : filePaths) {
      try {
        BufferedReader in = com.google.common.io.Files.newReader(new File(filePath), UTF_8);
        String line;
        while ((line = in.readLine()) != null) {
          lines.add(line);
        }
      } catch (IOException e) {
        throw new MobileHarnessException(
            BasicErrorId.LOCAL_FILE_READ_LINES_FROM_FILE_SET,
            "Failed to read file(s) " + filePaths,
            e);
      }
    }
    return lines;
  }

  /**
   * Saves the content of the file on the device in the list of strings.
   *
   * @throws MobileHarnessException if failed to read the file
   */
  public List<String> readLineListFromFile(String filePath) throws MobileHarnessException {
    List<String> lines = new ArrayList<>();

    try {
      BufferedReader in = com.google.common.io.Files.newReader(new File(filePath), UTF_8);
      String line;
      while ((line = in.readLine()) != null) {
        lines.add(line);
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_READ_LINES_FROM_FILE, "Failed to read file " + filePath, e);
    }
    return lines;
  }

  /** Reads the real path of {@code symbolicLink}. */
  public Path readSymbolicLink(Path symbolicLink) throws MobileHarnessException {
    try {
      return Files.readSymbolicLink(symbolicLink);
    } catch (UnsupportedOperationException | IOException | SecurityException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_READ_SYMLINK_ERROR,
          "Failed to read the real path of symbolic link " + symbolicLink,
          e);
    }
  }

  /**
   * Deletes a single file, or recursively deletes a directory. It's the same as {@link
   * #removeFileOrDir(String)}, but uses java.nio.file.Path as the input.
   */
  public void removeFileOrDir(Path fileOrDirPath)
      throws MobileHarnessException, InterruptedException {
    removeFileOrDir(fileOrDirPath.toString());
  }

  /**
   * Deletes a single file, or recursively deletes a directory.
   *
   * @throws MobileHarnessException if fails to delete
   */
  public void removeFileOrDir(String fileOrDirPath)
      throws MobileHarnessException, InterruptedException {
    // Alternative solutions considered:
    // 1) Java APIs such as {@link com.google.io.file.AbstractFile#delete()}, {@link File#delete()}
    //    can not delete non-empty folder.
    // 2) {@code com.google.io.file.AbstractFile#deleteRecursively()} is not implemented.
    // 3) {@code com.google.common.io.Files#deleteRecursively(File)} is deprecated because it
    //    suffers from poor symbol link detection.
    try {
      cmdExecutor.exec(Command.of("rm", "-rf", fileOrDirPath).timeout(fixed(SLOW_CMD_TIMEOUT)));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_REMOVE_ERROR,
          "Failed to remove file/dir " + fileOrDirPath,
          e);
    }
  }

  /**
   * Cleans up the files or directories under the given directory, whose file or directory name
   * matches the given regular expression.
   *
   * @param dirPath the directory under which you find the files or directories to delete
   * @param fileOrDirNameRegExp the regular expression to match the files or directories' name to be
   *     deleted; use {@code null} to match all files
   * @throws MobileHarnessException if the directory does not exist, or fails to delete files or
   *     directories
   */
  public void removeFilesOrDirs(String dirPath, @Nullable String fileOrDirNameRegExp)
      throws MobileHarnessException, InterruptedException {
    File dir = checkDir(dirPath);
    @Nullable
    Pattern pattern = fileOrDirNameRegExp == null ? null : Pattern.compile(fileOrDirNameRegExp);
    for (File fileOrDir : listFilesOrDirs(dir, /* filter= */ null)) {
      if (pattern == null || pattern.matcher(fileOrDir.getName()).matches()) {
        removeFileOrDir(fileOrDir.getAbsolutePath());
      }
    }
  }

  /**
   * Cleans up the files or directories under the given directory.
   *
   * @param dirPath the directory under which you find the files or directories to delete
   * @throws MobileHarnessException if the directory does not exist, or fails to delete files or
   *     directories
   */
  public void removeFilesOrDirs(String dirPath)
      throws MobileHarnessException, InterruptedException {
    removeFilesOrDirs(dirPath, null);
  }

  /**
   * Cleans up the files or directories under the given directory.
   *
   * <p>It's the same a s{@link #removeFilesOrDirs(String)}}, but uses java.nio.files.Paths as the
   * input.
   */
  public void removeFilesOrDirs(Path dir) throws MobileHarnessException, InterruptedException {
    removeFilesOrDirs(dir.toString(), null);
  }

  /**
   * Makes the file empty. Deletes all bytes in the file but does not delete the file.
   *
   * @param filePath the path of the file
   * @throws MobileHarnessException if the named file exists but is a directory rather than a
   *     regular file, does not exist but cannot be created, or cannot be opened for any other
   *     reason, or an I/O error occurs while writing or closing
   */
  public void resetFile(String filePath) throws MobileHarnessException {
    try {
      writeToFile(filePath, "", false);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_RESET_ERROR,
          "Failed to clean up the content of file " + filePath,
          e);
    }
  }

  /**
   * Updates the last updated timestamp of the given file/dir. If the file/dir does not exist, it
   * can create an empty file if the {@code ifCreateNewFile} is set to {@code true}. When it creates
   * a new file, it will automatically prepare its parent dirs.
   *
   * @param ifCreateNewFile whether to create an empty file if the file does not exist
   * @return a boolean flag to indicate the file create/update result
   * @throws MobileHarnessException if fail to create the parent directory or the file
   */
  public boolean touchFileOrDir(String fileOrDirPath, boolean ifCreateNewFile)
      throws MobileHarnessException {
    File fileOrDir = getFileOrDir(fileOrDirPath);
    if (!fileOrDir.exists()) {
      if (ifCreateNewFile) {
        String parentDir = fileOrDir.getParent();
        if (parentDir != null) {
          prepareDir(parentDir);
        }
        try {
          return fileOrDir.createNewFile();
        } catch (IOException e) {
          throw new MobileHarnessException(
              BasicErrorId.LOCAL_FILE_CREATE_NEW_ERROR,
              "Can not create empty file " + fileOrDirPath,
              e);
        }
      }
      // Files doesn't exist, and doesn't create a new one.
      return false;
    } else {
      return fileOrDir.setLastModified(System.currentTimeMillis());
    }
  }

  /**
   * Updates the last updated timestamp of the given file/dir. If the file/dir does not exist, it
   * can create an empty file if the {@code ifCreateNewFile} is set to {@code true}.
   *
   * <p>It's the same a s{@link #touchFileOrDir(String, boolean)}, but uses java.nio.files.Paths as
   * the input.
   */
  public boolean touchFileOrDir(Path fileOrDir, boolean ifCreateNewFile)
      throws MobileHarnessException {
    return touchFileOrDir(fileOrDir.toString(), ifCreateNewFile);
  }

  /**
   * Sets {@code permission} to file {@code filePath}. Go to {@link PosixFilePermissions#fromString}
   * for the format of {@code permission}.
   */
  public void setFilePermission(String filePath, String permission) throws MobileHarnessException {
    setFilePermission(Paths.get(filePath), permission);
  }

  /**
   * Sets {@code permission} to file {@code path}. Go to {@link PosixFilePermissions#fromString} for
   * the format of {@code permission}.
   */
  public void setFilePermission(Path file, String permission) throws MobileHarnessException {
    try {
      Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(permission));
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_SET_PERMISSION_ERROR,
          String.format("Failed to set permission %s to file %s", permission, file),
          e);
    }
  }

  /**
   * Writes the given string into the given file. If the parent folder or the file does not exist,
   * will create them.
   *
   * @throws MobileHarnessException if the named file exists but is a directory rather than a
   *     regular file, does not exist but cannot be created, or cannot be opened for any other
   *     reason, or an I/O error occurs while writing or closing
   */
  public void writeToFile(String filePath, String content) throws MobileHarnessException {
    writeToFile(filePath, content, /* append= */ false);
  }

  /**
   * Writes the given string into the given file. If the parent folder or the file does not exist,
   * will create them.
   *
   * @param append boolean if <code>true</code>, then data will be written to the end of the file
   *     rather than the beginning
   * @throws MobileHarnessException if the named file exists but is a directory rather than a
   *     regular file, does not exist but cannot be created, or cannot be opened for any other
   *     reason, or an I/O error occurs while writing or closing
   */
  public void writeToFile(String filePath, String content, boolean append)
      throws MobileHarnessException {
    // Creates the parent folder if not exist.
    prepareParentDir(filePath);
    // Writes the content to the file.
    try (BufferedWriter out =
        Files.newBufferedWriter(
            Path.of(filePath),
            UTF_8,
            append
                ? new StandardOpenOption[] {CREATE, APPEND}
                : new StandardOpenOption[] {CREATE, WRITE, TRUNCATE_EXISTING})) {
      out.write(content);
      out.flush();
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_WRITE_STRING_ERROR,
          "Failed to write content to file " + filePath,
          e);
    }
  }

  /**
   * Writes the given bytes into the given file. If the parent folder or the file does not exist,
   * will create them.
   *
   * @param append if <code>true</code>, then data will be written to the end of the file rather
   *     than the beginning
   * @return the length of the given bytes
   * @throws MobileHarnessException if the file exists but is a directory rather than a regular
   *     file, or does not exist but cannot be created, or cannot be opened for any other reasons,
   *     or an I/O error occurs while writing or closing
   */
  public long writeToFile(Path filePath, byte[] input, boolean append)
      throws MobileHarnessException {
    prepareParentDir(filePath);
    try {
      Files.write(
          filePath,
          input,
          append
              ? new StandardOpenOption[] {CREATE, APPEND}
              : new StandardOpenOption[] {CREATE, WRITE, TRUNCATE_EXISTING});
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_WRITE_BYTE_ERROR,
          "Failed to write content to file " + filePath,
          e);
    }
    return input.length;
  }

  /**
   * Writes the given bytes into the given file. If the parent folder or the file does not exist,
   * will create them.
   *
   * @param append if <code>true</code>, then data will be written to the end of the file rather
   *     than the beginning
   * @return the length of the given bytes
   * @throws MobileHarnessException if the file exists but is a directory rather than a regular
   *     file, or does not exist but cannot be created, or cannot be opened for any other reasons,
   *     or an I/O error occurs while writing or closing
   */
  public long writeToFile(String filePath, byte[] input, boolean append)
      throws MobileHarnessException {
    return writeToFile(Path.of(filePath), input, append);
  }

  /**
   * Writes the given bytes into the given file. If the parent folder or the file does not exist,
   * will create them.
   *
   * @return the length of the given bytes
   * @throws MobileHarnessException if the file exists but is a directory rather than a regular
   *     file, or does not exist but cannot be created, or cannot be opened for any other reasons,
   *     or an I/O error occurs while writing or closing
   */
  @CanIgnoreReturnValue
  public long writeToFile(Path filePath, byte[] input) throws MobileHarnessException {
    return writeToFile(filePath, input, /* append= */ false);
  }

  /**
   * Writes the given bytes into the given file. If the parent folder or the file does not exist,
   * will create them.
   *
   * @return the length of the given bytes
   * @throws MobileHarnessException if the file exists but is a directory rather than a regular
   *     file, or does not exist but cannot be created, or cannot be opened for any other reasons,
   *     or an I/O error occurs while writing or closing
   */
  public long writeToFile(String filePath, byte[] input) throws MobileHarnessException {
    return writeToFile(Path.of(filePath), input);
  }

  /**
   * Reads the input stream and writes to the given file. If the file does not exist, will create
   * the file.
   *
   * @return the size of the file
   * @throws MobileHarnessException if the named file exists but is a directory rather than a
   *     regular file, does not exist but cannot be created, or cannot be opened for any other
   *     reason, or an I/O error occurs while writing or closing
   */
  public long writeToFile(String filePath, InputStream input) throws MobileHarnessException {
    Preconditions.checkNotNull(input);

    // Creates the parent folder if not exist.
    prepareParentDir(filePath);
    long fileSize = 0;
    try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(filePath))) {
      byte[] buf = new byte[BUFFER_SIZE];
      for (int len = input.read(buf); len > 0; len = input.read(buf)) {
        output.write(buf, 0, len);
        fileSize += len;
      }
      output.flush();
      return fileSize;
    } catch (SecurityException | IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_WRITE_STREAM_ERROR,
          "Failed to write stream to file " + filePath,
          e);
    }
  }

  /**
   * Unzips the file to the target dir. If the target dir does not exist, will create it.
   *
   * @return the log
   */
  public String unzipFile(String zipFilePath, String targetDirPath)
      throws MobileHarnessException, InterruptedException {
    return unzipFile(zipFilePath, targetDirPath, (Duration) null);
  }

  /**
   * Unzips the file to the target dir. If the target dir does not exist, will create it.
   *
   * @return the log
   */
  public String unzipFile(Path zipFile, Path targetDir)
      throws MobileHarnessException, InterruptedException {
    return unzipFile(zipFile.toString(), targetDir.toString());
  }

  /**
   * Unzips the file to the target dir. If the target dir does not exist, will create it.
   *
   * @return the log
   */
  public String unzipFile(String zipFilePath, String targetDirPath, @Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    try {
      prepareDir(targetDirPath);
      Command command =
          Command.of("unzip", "-o", zipFilePath).workDir(targetDirPath /*for b/28160125 */);
      if (timeout != null) {
        command = command.timeout(fixed(timeout));
      }
      return cmdExecutor.run(command);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_UNZIP_ERROR,
          String.format("Failed to unzip file %s to dir %s", zipFilePath, targetDirPath),
          e);
    }
  }

  /**
   * Unzips a particular file to the target dir. If the target dir does not exist, will create it.
   *
   * @return the log
   */
  public String unzipFile(String zipFilePath, String fileNameToUnzip, String targetDirPath)
      throws MobileHarnessException, InterruptedException {
    return unzipFiles(zipFilePath, ImmutableList.of(fileNameToUnzip), targetDirPath);
  }

  /**
   * Unzips a list of particular files to the target dir. If the target dir does not exist, will
   * create it.
   *
   * @return the log
   */
  public String unzipFiles(String zipFilePath, List<String> fileNamesToUnzip, String targetDirPath)
      throws MobileHarnessException, InterruptedException {
    try {
      prepareDir(targetDirPath);
      ImmutableList<String> cmd =
          new ImmutableList.Builder<String>()
              .add("unzip")
              .add("-o")
              .add(zipFilePath)
              .addAll(fileNamesToUnzip)
              .build();
      Command command = Command.of(cmd).workDir(targetDirPath /*for b/28160125 */);
      return cmdExecutor.run(command);
    } catch (MobileHarnessException e) {
      if (e.getErrorId() == BasicErrorId.COMMAND_EXEC_FAIL
          && e.getMessage().contains("filename not matched")) {
        throw new MobileHarnessException(
            BasicErrorId.LOCAL_FILE_UNZIP_FILENAME_NOT_MATCHED,
            String.format(
                "Failed to unzip file %s from %s because the filename %s is not matched",
                fileNamesToUnzip, zipFilePath, fileNamesToUnzip),
            e);
      }
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_UNZIP_PARTICULAR_FILES_ERROR,
          String.format(
              "Failed to unzip files %s from %s to dir %s",
              fileNamesToUnzip, zipFilePath, targetDirPath),
          e);
    }
  }

  /**
   * Write data with input BufferedWriter.
   *
   * @throws MobileHarnessException if an I/O error occurs when writing data to file.
   */
  public BufferedWriter writeToFileWithBufferedWriter(
      @Nullable BufferedWriter bufferedWriter, String filePath, String data, boolean append)
      throws MobileHarnessException {
    if (bufferedWriter == null) {
      try {
        bufferedWriter = createBufferedWriter(filePath, append);
      } catch (IOException e) {
        throw new MobileHarnessException(
            BasicErrorId.LOCAL_FILE_WRITE_STREAM_CREATE_BUFFER_ERROR,
            "Failed to write content to file " + filePath,
            e);
      }
    }
    try {
      bufferedWriter.write(data);
      bufferedWriter.flush();
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_WRITE_STREAM_WITH_BUFFER_ERROR,
          "Failed to write content to file " + filePath,
          e);
    }
    return bufferedWriter;
  }

  /**
   * Packs the given source directory into a zip file.
   *
   * @param sourceDirPath path of the source directory
   * @param zipFilePath path of the generated zip file
   * @return the log
   */
  public String zipDir(String sourceDirPath, String zipFilePath)
      throws MobileHarnessException, InterruptedException {
    return zipDir(
        sourceDirPath,
        zipFilePath,
        /* sortFile= */ false,
        /* storeOnly= */ false,
        /* compressionLevel= */ null,
        /* timeout= */ null);
  }

  /**
   * Packs the given source directory into a zip file.
   *
   * @param sourceDirPath path of the source directory
   * @param zipFilePath path of the generated zip file
   * @param sortFile whether to attach the files to the zip file in order.
   * @param storeOnly whether pack all file together without any compression
   * @param compressionLevel the level of compression (1-9), where 1 indicates the fastest
   *     compression speed (less compression) and 9 indicates the slowest compression speed (the
   *     best compression). If null, use the default configuration of "zip" command.
   * @param timeout the timeout of the zip operation; null means default timeout
   * @return the log
   */
  public String zipDir(
      String sourceDirPath,
      String zipFilePath,
      boolean sortFile,
      boolean storeOnly,
      @Nullable Integer compressionLevel,
      @Nullable Timeout timeout)
      throws MobileHarnessException, InterruptedException {
    Path absSourceDirPath = Paths.get(sourceDirPath).toAbsolutePath();
    ImmutableList.Builder<String> arguments =
        ImmutableList.<String>builder()
            .add(
                "zip",
                // Do not save extra file attributes(Extended Attributes on OS/2, uid/gid and file
                // timeson Unix). so the zip file MD5 keep the same if source file doesn't change.
                "-X");
    if (storeOnly) {
      arguments.add("-0");
    }
    if (compressionLevel != null) {
      if (compressionLevel >= 1 && compressionLevel <= 9) {
        arguments.add("-" + compressionLevel);
      } else {
        throw new MobileHarnessException(
            BasicErrorId.LOCAL_DIR_ZIP_ERROR,
            String.format(
                "Failed to zip dir %s into %s. The compressionLevel should between 1 to 9",
                sourceDirPath, zipFilePath));
      }
    }
    arguments.add("-r", zipFilePath);
    if (sortFile) {
      // 1) For b/119577531, we used "zip -X -r $zip .", but the order of the attached files may be
      // undefined in some file systems, that will result in checksum changes. To solve this
      // problem, we list files under the directory and append them to the zip file in order.
      // 2) Use `listFilePaths(String dirPath, boolean recursively)` instead of
      // `listFilePaths(Path dir, boolean recursively)` because the later one does not follow
      // symbolic links.
      List<String> files = listFilePaths(absSourceDirPath.toString(), true);
      arguments.addAll(
          files.stream()
              .sorted()
              .map(file -> absSourceDirPath.relativize(Paths.get(file).toAbsolutePath()).toString())
              .collect(toImmutableList()));
    } else {
      arguments.add(".");
    }
    Command command = Command.of(arguments.build()).workDir(absSourceDirPath); // for b/28160125
    if (timeout != null) {
      command = command.timeout(timeout);
    }
    try {
      return cmdExecutor.run(command);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_DIR_ZIP_ERROR,
          String.format("Failed to zip dir %s into %s", sourceDirPath, zipFilePath),
          e);
    }
  }

  /**
   * Create a BufferedWriter for output file.
   *
   * @return newly created BufferedWriter.
   * @throws MobileHarnessException if an I/O error occurs opening the file.
   */
  private BufferedWriter createBufferedWriter(String filePath, boolean append)
      throws MobileHarnessException, IOException {
    prepareParentDir(filePath);
    return Files.newBufferedWriter(
        Paths.get(filePath),
        UTF_8,
        append
            ? new StandardOpenOption[] {CREATE, APPEND}
            : new StandardOpenOption[] {CREATE, WRITE, TRUNCATE_EXISTING});
  }

  /**
   * Returns true if all sub files under {@code fileOrDirPath} (include itself) are fully
   * accessible.
   */
  private boolean areAllFilesFullAccessible(Path fileOrDir) {
    if (!isFullyAccessible(fileOrDir)) {
      return false;
    }
    if (Files.isDirectory(fileOrDir)) {
      try (DirectoryStream<Path> subFileStream = Files.newDirectoryStream(fileOrDir)) {
        for (Path sub : subFileStream) {
          if (!isFullyAccessible(sub)) {
            return false;
          }
        }
        return true;
      } catch (IOException e) {
        logger.atWarning().withCause(e).log(
            "Failed to check the accessibility for file or path %s", fileOrDir);
        return false;
      }
    }
    return true;
  }

  /** Returns true if file {@code path} is fully accessed. */
  private boolean isFullyAccessible(Path path) {
    try {
      String filePermissionString = getFilePermissionString(path);
      if (!filePermissionString.equals("rwxrwxrwx")) {
        logger.atInfo().log(
            "File %s is not fully accessible. Permissions: %s", path, filePermissionString);
        return false;
      }
      return true;
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to get file permission for path %s", path);
      return false;
    }
  }
}
