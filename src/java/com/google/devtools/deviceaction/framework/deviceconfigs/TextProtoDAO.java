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

import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.Command;
import com.google.devtools.deviceaction.framework.proto.DeviceConfig;
import com.google.devtools.deviceaction.framework.proto.action.InstallMainlineSpec;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;

/** An abstract DAO to get device configs from textproto files. */
public abstract class TextProtoDAO implements DeviceConfigDAO {

  /** See {@link DeviceConfigDAO#getDeviceConfig(String, Command)}. */
  @Override
  public DeviceConfig getDeviceConfig(String deviceKey, Command cmd) throws DeviceActionException {
    ExtensionRegistry registry = ExtensionRegistry.newInstance();
    switch (cmd) {
      case INSTALL_MAINLINE:
        registry.add(InstallMainlineSpec.installMainlineSpec);
        break;
      default:
        throw new DeviceActionException(
            "UNSUPPORTED_CMD",
            ErrorType.CUSTOMER_ISSUE,
            String.format("The cmd %s is not supported.", cmd));
    }
    String config = readTextProto(deviceKey);
    try {
      return TextFormat.parse(config, registry, DeviceConfig.class);
    } catch (ParseException e) {
      throw new DeviceActionException(
          "PROTO_ERROR", ErrorType.DEPENDENCY_ISSUE, "Failed to parse textproto file " + config, e);
    }
  }

  /** Read text proto file associated to the key. */
  protected abstract String readTextProto(String key) throws DeviceActionException;
}
