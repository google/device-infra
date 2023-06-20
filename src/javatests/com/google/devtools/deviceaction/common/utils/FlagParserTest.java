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

package com.google.devtools.deviceaction.common.utils;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.ActionOptions;
import com.google.devtools.deviceaction.common.schemas.ActionOptions.Options;
import com.google.devtools.deviceaction.common.schemas.Command;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FlagParserTest {

  @Test
  public void parseOptions_expectedResult() throws Exception {
    ArrayList<String> args =
        new ArrayList<>(
            Arrays.asList(
                "install_mainline",
                "--action=key=val1",
                "--action",
                "key=val2",
                "--other options",
                "string1,string2,string3",
                "--booloption",
                "--device1",
                "serial=id",
                "--action=repeatedKey=val1, repeatedKey= val2,  repeatedKey = val3",
                "--device1",
                "device_config=path/local",
                "--action",
                "nobool_flag, anotherbool, thirdbool=true",
                "--action",
                "file_tag=path/to/file"));

    ActionOptions options = FlagParser.parseOptions(args);

    assertThat(options)
        .isEqualTo(
            ActionOptions.builder()
                .setCommand(Command.INSTALL_MAINLINE)
                .setAction(
                    Options.builder()
                        .addKeyValues("key", "val1", "val2")
                        .addKeyValues("repeatedKey", "val1", "val2", "val3")
                        .addFalseBoolOptions("bool_flag")
                        .addTrueBoolOptions("anotherbool", "thirdbool")
                        .addFileOptions("tag", "path/to/file")
                        .build())
                .setFirstDevice(
                    Options.builder()
                        .addKeyValues("serial", "id")
                        .addKeyValues("device_config", "path/local")
                        .build())
                .setSecondDevice(Options.builder().build())
                .build());
    assertThat(args)
        .containsExactly(
            "--other options",
            "string1,string2,string3",
            "--booloption",
            "--adb_dont_kill_server",
            "--external_adb_initializer_template",
            "--adb_command_retry_attempts",
            "1");
  }

  @Test
  public void parseOptions_missingCmd_throwException() {
    ArrayList<String> args = new ArrayList<>(ImmutableList.of("--action=key=val1"));

    DeviceActionException thrown =
        assertThrows(DeviceActionException.class, () -> FlagParser.parseOptions(args));
    assertThat(thrown).hasMessageThat().contains("not supported");
    assertThat(thrown.getErrorId().name()).isEqualTo("INVALID_CMD");
  }
}
