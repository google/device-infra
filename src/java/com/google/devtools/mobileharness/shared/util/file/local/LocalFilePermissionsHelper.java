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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

/** Helper class for delegating permission-related file operations. */
final class LocalFilePermissionsHelper {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CommandExecutor cmdExecutor;

  @Inject
  LocalFilePermissionsHelper(CommandExecutor cmdExecutor) {
    this.cmdExecutor = cmdExecutor;
  }

  String changeFileOrDirGroup(String fileOrDirPath, String group)
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

  String changeFileOrDirOwner(String fileOrDirPath, String user)
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

  @CanIgnoreReturnValue
  private File checkFileOrDir(String fileOrDirPath) throws MobileHarnessException {
    File fileOrDir = new File(fileOrDirPath);
    if (!fileOrDir.exists()) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND,
          "Can not find file or directory: " + fileOrDirPath);
    }
    return fileOrDir;
  }

  /** Gets file permissions of file {@code path}. */
  private Set<PosixFilePermission> getFilePermission(Path file) throws MobileHarnessException {
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
  private String getFilePermissionString(Path file) throws MobileHarnessException {
    return PosixFilePermissions.toString(getFilePermission(file));
  }

  void grantFileOrDirFullAccess(String fileOrDirPath) throws MobileHarnessException {
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

  void grantFileOrDirFullAccessRecursively(String fileOrDirPath)
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
        logger.atFine().log(
            "Checked fully accessibility of file %s. Permissions: %s", path, filePermissionString);
        return false;
      }
      return true;
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to get file permission for path %s", path);
      return false;
    }
  }
}
