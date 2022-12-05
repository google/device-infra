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

package com.google.devtools.mobileharness.platform.android.file;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Splitter;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.util.List;

/** An abstract representation of permissions for file and directory on Android device. */
@AutoValue
public abstract class FilePermissions {

  /** Unix permission types */
  private enum PermissionType {
    USER,
    GROUP,
    OTHER,
  }

  /** User permission for file/dir, e.g., 'rwx', 'rw-'. */
  public abstract String userPermission();

  /** Group permission for file/dir, e.g., 'rwx', 'rw-'. */
  public abstract String groupPermission();

  /** Others permission for file/dir, e.g., 'rwx', 'rw-'. */
  public abstract String othersPermission();

  /** Gets user permission for file/dir in integer format. */
  @Memoized
  public int userPermissionInt() {
    return calculatePermission(userPermission());
  }

  /** Gets group permission for file/dir in integer format. */
  @Memoized
  public int groupPermissionInt() {
    return calculatePermission(groupPermission());
  }

  /** Gets others permission for file/dir in integer format. */
  @Memoized
  public int othersPermissionInt() {
    return calculatePermission(othersPermission());
  }

  /** Gets special mode bit in integer format. */
  @Memoized
  public int specialModeBit() {
    return calculateSpecialModeBit(allPermissions());
  }

  /** Gets all permissions for user, group, and others, for file/dir, e.g., 'rwxrw-rw-'. */
  @Memoized
  public String allPermissions() {
    return userPermission() + groupPermission() + othersPermission();
  }

  public static Builder builder() {
    return new AutoValue_FilePermissions.Builder();
  }

  /** Auto value builder for {@link FilePermissions}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract FilePermissions.Builder setUserPermission(String userPermission);

    public abstract FilePermissions.Builder setGroupPermission(String groupPermission);

    public abstract FilePermissions.Builder setOthersPermission(String othersPermission);

    abstract FilePermissions autoBuild();

    public FilePermissions build() throws MobileHarnessException {
      FilePermissions filePermissions = autoBuild();
      validatePermissionFormat(filePermissions.userPermission(), PermissionType.USER);
      validatePermissionFormat(filePermissions.groupPermission(), PermissionType.GROUP);
      validatePermissionFormat(filePermissions.othersPermission(), PermissionType.OTHER);
      return filePermissions;
    }
  }

  /** Constructs a {@code FilePermissions} object from {@code permissions}. */
  public static FilePermissions from(String permissions) throws MobileHarnessException {
    if (permissions == null || permissions.length() != 9) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_PERMISSIONS_ILLEGAL_ARGUMENT,
          String.format("Permission %s in invalid, it needs to be like 'rwxr-xr--'", permissions));
    }
    List<String> permissionsList = Splitter.fixedLength(3).splitToList(permissions);
    return FilePermissions.builder()
        .setUserPermission(permissionsList.get(0))
        .setGroupPermission(permissionsList.get(1))
        .setOthersPermission(permissionsList.get(2))
        .build();
  }

  private static int calculatePermission(String permission) {
    int permissionInt = 0;
    permissionInt += permission.charAt(0) == 'r' ? 4 : 0;
    permissionInt += permission.charAt(1) == 'w' ? 2 : 0;
    char bit = permission.charAt(2);
    permissionInt += (bit == 'x' || bit == 's' || bit == 't') ? 1 : 0;
    return permissionInt;
  }

  private static void validatePermissionFormat(String permission, PermissionType permissionType)
      throws MobileHarnessException {
    String executeBitOptions = "x";
    switch (permissionType) {
      case USER:
      case GROUP:
        executeBitOptions += "sS";
        break;
      case OTHER:
        executeBitOptions += "tT";
        break;
    }
    if (!permission.matches(String.format("[r\\-][w\\-][%s\\-]", executeBitOptions))) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_PERMISSIONS_ILLEGAL_ARGUMENT,
          String.format(
              "Permission %s is invalid for permission type %s.", permission, permissionType));
    }
  }

  private static int calculateSpecialModeBit(String permissions) {
    int specialModeBit = 0;
    // checks user permission part
    if (permissions.charAt(2) == 's' || permissions.charAt(2) == 'S') {
      specialModeBit += 4;
    }
    // checks group permission part
    if (permissions.charAt(5) == 's' || permissions.charAt(5) == 'S') {
      specialModeBit += 2;
    }
    // checks other permission part
    if (permissions.charAt(8) == 't' || permissions.charAt(8) == 'T') {
      specialModeBit += 1;
    }
    return specialModeBit;
  }
}
