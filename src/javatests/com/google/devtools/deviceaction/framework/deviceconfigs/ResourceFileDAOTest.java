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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ResourceFileDAOTest {

  private final ResourceFileDAO dao = new ResourceFileDAO();

  @Test
  public void readTextProto_verifyFileExistence() throws Exception {
    String deviceConfigStr = dao.readTextProto("oppo_cph2359_t_userdebug");

    assertThat(deviceConfigStr).contains("OPPO");
  }

  @Test
  public void readTextProto_fileNotExit_throwException() {
    assertThrows(DeviceActionException.class, () -> dao.readTextProto("not_exist"));
  }
}
