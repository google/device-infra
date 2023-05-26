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

import static com.google.devtools.deviceaction.common.utils.Constants.DEVICE_CONFIG_KEY;

import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.ActionConfig;
import com.google.devtools.deviceaction.common.schemas.ActionOptions;
import com.google.devtools.deviceaction.common.schemas.ActionOptions.Options;
import com.google.devtools.deviceaction.common.schemas.Command;
import com.google.devtools.deviceaction.common.utils.ProtoHelper;
import com.google.devtools.deviceaction.framework.deviceconfigs.DeviceConfigDao;
import com.google.devtools.deviceaction.framework.devices.Devices;
import com.google.devtools.deviceaction.framework.proto.ActionSpec;
import com.google.devtools.deviceaction.framework.proto.DeviceConfig;
import com.google.devtools.deviceaction.framework.proto.Operand;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
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
  public ActionConfig getConfigure(ActionOptions options)
      throws DeviceActionException, InterruptedException {
    Command cmd = options.command();
    ActionConfig.Builder actionConfigBuilder = ActionConfig.builder().setCmd(cmd);

    ActionSpec initialSpec = ProtoHelper.getActionSpec(options);
    ActionSpec actionSpec = initialSpec;
    Optional<Operand> firstOp = ProtoHelper.getFirst(initialSpec);
    if (firstOp.isPresent()) {
      DeviceConfig firstDeviceConfig = getDeviceConfig(firstOp.get(), options.firstDevice(), cmd);
      actionConfigBuilder.setFirstSpec(firstDeviceConfig.getDeviceSpec());
      actionSpec = ProtoHelper.mergeActionSpec(cmd, actionSpec, firstDeviceConfig);
    }
    Optional<Operand> secondOp = ProtoHelper.getSecond(initialSpec);
    if (secondOp.isPresent()) {
      DeviceConfig secondDeviceConfig =
          getDeviceConfig(secondOp.get(), options.secondDevice(), cmd);
      actionConfigBuilder.setSecondSpec(secondDeviceConfig.getDeviceSpec());
      actionSpec = ProtoHelper.mergeActionSpec(cmd, actionSpec, secondDeviceConfig);
    }
    return actionConfigBuilder.setActionSpec(actionSpec).build();
  }

  private DeviceConfig getDeviceConfig(Operand operand, Options options, Command cmd)
      throws DeviceActionException, InterruptedException {
    Optional<DeviceConfig> deviceConfigOp = getDeviceConfigFromUser(options, cmd);
    if (deviceConfigOp.isPresent()) {
      return deviceConfigOp.get();
    }
    return getDeviceConfigFromDao(operand, cmd);
  }

  private static Optional<DeviceConfig> getDeviceConfigFromUser(Options options, Command cmd)
      throws DeviceActionException {
    Optional<String> deviceConfigOp = options.getOnlyValue(DEVICE_CONFIG_KEY);
    if (deviceConfigOp.isPresent()) {
      String textProto;
      try {
        textProto = Files.readString(Paths.get(deviceConfigOp.get()));
      } catch (IOException e) {
        throw new DeviceActionException(
            "FILE_NOT_FOUND", ErrorType.DEPENDENCY_ISSUE, "file not found", e);
      }
      return Optional.of(ProtoHelper.getDeviceConfigFromTextproto(textProto, cmd));
    }
    return Optional.empty();
  }

  private DeviceConfig getDeviceConfigFromDao(Operand operand, Command cmd)
      throws DeviceActionException, InterruptedException {
    String deviceKey = devices.getDeviceKey(operand);
    return deviceConfigDao.getDeviceConfig(deviceKey, cmd);
  }
}
