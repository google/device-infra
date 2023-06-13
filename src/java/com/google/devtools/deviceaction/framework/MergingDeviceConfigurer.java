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

package com.google.devtools.deviceaction.framework;

import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.ActionConfig;
import com.google.devtools.deviceaction.common.schemas.ActionOptions;
import com.google.devtools.deviceaction.common.schemas.Command;
import com.google.devtools.deviceaction.common.schemas.DevicePosition;
import com.google.devtools.deviceaction.common.schemas.DeviceWrapper;
import com.google.devtools.deviceaction.common.utils.ProtoHelper;
import com.google.devtools.deviceaction.framework.deviceconfigs.DeviceConfigDao;
import com.google.devtools.deviceaction.framework.devices.Devices;
import com.google.devtools.deviceaction.framework.proto.ActionSpec;
import com.google.devtools.deviceaction.framework.proto.DeviceConfig;
import com.google.devtools.deviceaction.framework.proto.Operand;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.EnumMap;
import javax.inject.Inject;

/** An {@link ActionConfigurer} that merges configs from devices. */
public class MergingDeviceConfigurer implements ActionConfigurer {

  protected final DeviceConfigDao deviceConfigDao;
  protected final Devices devices;

  @Inject
  MergingDeviceConfigurer(DeviceConfigDao deviceConfigDao, Devices devices) {
    this.deviceConfigDao = deviceConfigDao;
    this.devices = devices;
  }

  @Override
  public ActionConfig createActionConfigure(ActionOptions options)
      throws DeviceActionException, InterruptedException {
    ActionSpec initialSpec = ProtoHelper.getActionSpec(options);
    return createActionConfigure(
        options.command(), initialSpec, ProtoHelper.getDeviceWrapperMap(initialSpec, options));
  }

  @Override
  public ActionConfig createActionConfigure(Command cmd, ActionSpec initialSpec)
      throws DeviceActionException, InterruptedException {
    return createActionConfigure(cmd, initialSpec, ProtoHelper.getDeviceWrapperMap(initialSpec));
  }

  private ActionConfig createActionConfigure(
      Command cmd, ActionSpec initialSpec, EnumMap<DevicePosition, DeviceWrapper> deviceWrapperMap)
      throws DeviceActionException, InterruptedException {
    ActionConfig.Builder actionConfigBuilder = ActionConfig.builder().setCmd(cmd);
    ActionSpec actionSpec = initialSpec;
    for (DevicePosition devicePosition : deviceWrapperMap.keySet()) {
      DeviceConfig deviceConfig = getDeviceConfig(cmd, deviceWrapperMap.get(devicePosition));
      actionConfigBuilder.setDeviceSpec(devicePosition, deviceConfig.getDeviceSpec());
      actionSpec = ProtoHelper.mergeActionSpec(cmd, actionSpec, deviceConfig);
    }
    return actionConfigBuilder.setActionSpec(actionSpec).build();
  }

  private DeviceConfig getDeviceConfig(Command cmd, DeviceWrapper deviceWrapper)
      throws DeviceActionException, InterruptedException {
    return deviceWrapper.deviceConfigFile().isPresent()
        ? getDeviceConfigFromUser(cmd, deviceWrapper.deviceConfigFile().get())
        : getDeviceConfigFromDao(cmd, deviceWrapper.operand());
  }

  private static DeviceConfig getDeviceConfigFromUser(Command cmd, String deviceConfigFile)
      throws DeviceActionException {
    String textProto;
    try {
      textProto = Files.readString(Paths.get(deviceConfigFile));
    } catch (IOException e) {
      throw new DeviceActionException(
          "FILE_NOT_FOUND", ErrorType.DEPENDENCY_ISSUE, "file not found", e);
    }
    return ProtoHelper.getDeviceConfigFromTextproto(textProto, cmd);
  }

  private DeviceConfig getDeviceConfigFromDao(Command cmd, Operand operand)
      throws DeviceActionException, InterruptedException {
    String deviceKey = devices.getDeviceKey(operand);
    return deviceConfigDao.getDeviceConfig(deviceKey, cmd);
  }
}
