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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FilePermissionsTest {

  @Test
  public void filePermissions_success() throws Exception {
    FilePermissions unused =
        FilePermissions.builder()
            .setUserPermission("rw-")
            .setGroupPermission("r--")
            .setOthersPermission("---")
            .build();
    unused =
        FilePermissions.builder()
            .setUserPermission("r-x")
            .setGroupPermission("r-x")
            .setOthersPermission("r--")
            .build();
    unused =
        FilePermissions.builder()
            .setUserPermission("r--")
            .setGroupPermission("r-x")
            .setOthersPermission("---")
            .build();
    unused =
        FilePermissions.builder()
            .setUserPermission("---")
            .setGroupPermission("---")
            .setOthersPermission("---")
            .build();
    unused = FilePermissions.from("rw-r-----");
    unused = FilePermissions.from("---------");
    unused = FilePermissions.from("r--r--r--");
    // Special modes
    unused = FilePermissions.from("r-sr--r--");
    unused = FilePermissions.from("r-Sr--r--");
    unused = FilePermissions.from("r--r-sr--");
    unused = FilePermissions.from("r--r-Sr--");
    unused = FilePermissions.from("r--r--r-t");
    unused = FilePermissions.from("r--r--r-T");
  }

  @Test
  public void filePermissions_specialModeBit() throws Exception {
    assertThat(FilePermissions.from("r-sr--r--").specialModeBit()).isEqualTo(4);
    assertThat(FilePermissions.from("r-Sr--r--").specialModeBit()).isEqualTo(4);
    assertThat(FilePermissions.from("r--r-sr--").specialModeBit()).isEqualTo(2);
    assertThat(FilePermissions.from("r--r-Sr--").specialModeBit()).isEqualTo(2);
    assertThat(FilePermissions.from("r--r--r-t").specialModeBit()).isEqualTo(1);
    assertThat(FilePermissions.from("r--r--r-T").specialModeBit()).isEqualTo(1);
    assertThat(FilePermissions.from("r-sr-sr--").specialModeBit()).isEqualTo(6);
    assertThat(FilePermissions.from("r--r-sr-t").specialModeBit()).isEqualTo(3);
    assertThat(FilePermissions.from("r-sr--r-t").specialModeBit()).isEqualTo(5);
    assertThat(FilePermissions.from("r-sr-sr-t").specialModeBit()).isEqualTo(7);
  }

  @Test
  public void filePermissions_invalidPermissionFormat_throwException() throws Exception {
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        FilePermissions.builder()
                            .setUserPermission("rwx")
                            .setGroupPermission("r-x")
                            .setOthersPermission("abc")
                            .build())
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_PERMISSIONS_ILLEGAL_ARGUMENT);

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        FilePermissions.builder()
                            .setUserPermission("rrr")
                            .setGroupPermission("r-x")
                            .setOthersPermission("r--")
                            .build())
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_PERMISSIONS_ILLEGAL_ARGUMENT);

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        FilePermissions.builder()
                            .setUserPermission("rwx")
                            .setGroupPermission("x-x")
                            .setOthersPermission("r--")
                            .build())
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_PERMISSIONS_ILLEGAL_ARGUMENT);

    // Special modes
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        FilePermissions.builder()
                            .setUserPermission("rwt")
                            .setGroupPermission("r-x")
                            .setOthersPermission("r--")
                            .build())
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_PERMISSIONS_ILLEGAL_ARGUMENT);

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        FilePermissions.builder()
                            .setUserPermission("rwx")
                            .setGroupPermission("r-t")
                            .setOthersPermission("r--")
                            .build())
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_PERMISSIONS_ILLEGAL_ARGUMENT);

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        FilePermissions.builder()
                            .setUserPermission("rwx")
                            .setGroupPermission("r-x")
                            .setOthersPermission("r-s")
                            .build())
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_PERMISSIONS_ILLEGAL_ARGUMENT);
  }

  @Test
  public void filePermissions_from_throwException() throws Exception {
    assertThat(
            assertThrows(MobileHarnessException.class, () -> FilePermissions.from("rw-r--r--r"))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_PERMISSIONS_ILLEGAL_ARGUMENT);

    assertThat(
            assertThrows(MobileHarnessException.class, () -> FilePermissions.from(null))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_PERMISSIONS_ILLEGAL_ARGUMENT);

    // Special modes
    assertThat(
            assertThrows(MobileHarnessException.class, () -> FilePermissions.from("rwTr--r--"))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_PERMISSIONS_ILLEGAL_ARGUMENT);

    assertThat(
            assertThrows(MobileHarnessException.class, () -> FilePermissions.from("rw-r-Tr--"))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_PERMISSIONS_ILLEGAL_ARGUMENT);

    assertThat(
            assertThrows(MobileHarnessException.class, () -> FilePermissions.from("rw-r--r-S"))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_PERMISSIONS_ILLEGAL_ARGUMENT);
  }
}
