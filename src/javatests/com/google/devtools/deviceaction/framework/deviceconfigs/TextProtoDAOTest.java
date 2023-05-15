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

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.Command;
import com.google.devtools.deviceaction.framework.proto.AndroidPhoneSpec;
import com.google.devtools.deviceaction.framework.proto.DeviceConfig;
import com.google.devtools.deviceaction.framework.proto.DeviceSpec;
import com.google.devtools.deviceaction.framework.proto.action.InstallMainlineSpec;
import com.google.devtools.deviceinfra.shared.util.runfiles.RunfilesUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TextProtoDAOTest {

  private static final String TEST_DATA_FOLDER =
      RunfilesUtil.getRunfilesLocation("javatests/com/google/devtools/deviceaction/testdata/");

  private static final String TEST_FILE_FORMAT = TEST_DATA_FOLDER + "%s.textproto";
  private static final DeviceConfig DEVICE_CONFIG =
      DeviceConfig.newBuilder()
          .setDeviceSpec(
              DeviceSpec.newBuilder()
                  .setAndroidPhoneSpec(
                      AndroidPhoneSpec.newBuilder()
                          .setBrand("Google")
                          .setNeedDisablePackageCache(false)
                          .build())
                  .build())
          .setExtension(
              InstallMainlineSpec.installMainlineSpec,
              InstallMainlineSpec.newBuilder().setCleanUpSessions(true).build())
          .build();

  private final TextProtoDAO dao =
      new TextProtoDAO() {
        @Override
        protected String readTextProto(String key) throws DeviceActionException {
          try {
            return Files.readString(Paths.get(String.format(TEST_FILE_FORMAT, key)));
          } catch (IOException e) {
            throw new DeviceActionException(
                "FILE_NOT_FOUND", ErrorType.DEPENDENCY_ISSUE, "file not found", e);
          }
        }
      };

  @Test
  public void getDeviceConfig_getExpectedResult() throws Exception {
    DeviceConfig deviceConfig =
        dao.getDeviceConfig("google_pixel-7_s_userdebug", Command.INSTALL_MAINLINE);

    assertThat(deviceConfig).isEqualTo(DEVICE_CONFIG);
  }

  @Test
  public void getDeviceConfig_fileNotFound_throwException() {
    assertThrows(
        DeviceActionException.class,
        () -> dao.getDeviceConfig("not-exist", Command.INSTALL_MAINLINE));
  }

  @Test
  public void getDeviceConfig_invalidCmd_throwException() {
    assertThrows(
        DeviceActionException.class,
        () -> dao.getDeviceConfig("google_pixel-7_s_userdebug", Command.DEFAULT));
  }
}
