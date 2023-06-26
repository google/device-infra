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

package com.google.devtools.deviceaction.framework.deviceconfigs;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.Command;
import com.google.devtools.deviceaction.framework.proto.DeviceConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ResourceFileDaoTest {

  private final ResourceFileDao dao = new ResourceFileDao();

  @Test
  public void getDeviceConfig_verifyFileExistence() throws Exception {
    DeviceConfig deviceConfig =
        dao.getDeviceConfig("google_pixel-7_t_user", Command.INSTALL_MAINLINE);

    assertThat(deviceConfig.getDeviceSpec().getAndroidPhoneSpec().getBrand()).isEqualTo("GOOGLE");
  }

  @Test
  public void readTextProto_fileNotExit_throwException() {
    assertThrows(
        DeviceActionException.class,
        () -> dao.getDeviceConfig("not_exist", Command.INSTALL_MAINLINE));
  }
}
