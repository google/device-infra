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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Ascii;
import com.google.common.io.Resources;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.Command;
import com.google.devtools.deviceaction.common.utils.ProtoHelper;
import com.google.devtools.deviceaction.framework.proto.DeviceConfig;
import java.io.IOException;
import java.nio.file.Paths;

/** A DAO to get device configs from jar resources. */
public class ResourceFileDao implements DeviceConfigDao {

  private static final String CONFIGS_DIR = "/devtools/deviceaction/deviceconfigs/configs/";

  public ResourceFileDao() {}

  /** See {@link DeviceConfigDao#getDeviceConfig(String, Command)}. */
  @Override
  public DeviceConfig getDeviceConfig(String deviceKey, Command cmd) throws DeviceActionException {
    return ProtoHelper.getDeviceConfigFromTextproto(readTextProto(deviceKey), cmd);
  }

  /** Reads device config text proto from jar resources. */
  private String readTextProto(String key) throws DeviceActionException {
    try {
      return Resources.toString(
          Resources.getResource(
              ResourceFileDao.class,
              Paths.get(CONFIGS_DIR, Ascii.toLowerCase(key) + ".textproto").toString()),
          UTF_8);
    } catch (IOException | IllegalArgumentException e) {
      throw new DeviceActionException(
          "RESOURCE_NOT_FOUND",
          ErrorType.DEPENDENCY_ISSUE,
          "Failed to load resource for " + key,
          e);
    }
  }
}
