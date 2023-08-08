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

package com.google.devtools.deviceaction.common.schemas;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.devtools.deviceaction.common.schemas.CommandHelp.OptionDescription;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CommandHelpTest {

  @Test
  public void printSummaryAndDetails_expectedResult() {
    OptionDescription flag1 =
        OptionDescription.builder()
            .setFlag("action")
            .setKey("enable_rollback")
            .setDescription("Optional flag to enable rollback if install fails.")
            .setIsOptional(true)
            .build();
    OptionDescription flag2 =
        OptionDescription.builder()
            .setFlag("action")
            .setKey("dev_key_signed")
            .setDescription("If the packages are signed by dev keys. It is false by default.")
            .setIsOptional(true)
            .build();
    OptionDescription flag3 =
        OptionDescription.builder()
            .setFlag("action")
            .setKey("file_train_folder")
            .setDescription("Path to the folder of mainline modules")
            .addExampleValues("folder")
            .setIsOptional(false)
            .build();
    OptionDescription flag4 =
        OptionDescription.builder()
            .setFlag("action")
            .setKey("file_mainline_modules")
            .setDescription("repeated flags of mainline modules")
            .addExampleValues("module1.apex", "module2.apk")
            .setIsOptional(false)
            .build();
    OptionDescription flag5 =
        OptionDescription.builder()
            .setFlag("action")
            .setKey("file_apks_zips")
            .setDescription("repeated flags of zip files containing apks.")
            .addExampleValues("train1.zip", "train2.zip")
            .setIsOptional(false)
            .build();
    OptionDescription flag6 =
        OptionDescription.builder()
            .setFlag("da_bundletool")
            .setDescription("Path to the bundletool jar file.")
            .addExampleValues("bundletool.jar")
            .setIsOptional(false)
            .build();
    OptionDescription flag7 =
        OptionDescription.builder()
            .setFlag("bool_flag")
            .setDescription("Unknown bool flag")
            .build();
    CommandHelp help =
        CommandHelp.builder()
            .setCommandName("install_mainline")
            .setCommandDescription("install mainline modules")
            .addFlag("enable_rollback", flag1)
            .addFlag("dev_key_signed", flag2)
            .addFlag("file", flag3)
            .addFlag("file", flag4)
            .addFlag("file", flag5)
            .addFlag("bundletool", flag6)
            .addFlag("unknown", flag7)
            .build();
    final ByteArrayOutputStream summary = new ByteArrayOutputStream();
    final ByteArrayOutputStream details = new ByteArrayOutputStream();

    help.printSummary(new PrintStream(summary));
    help.printDetails(new PrintStream(details));

    assertThat(summary.toString(UTF_8))
        .isEqualTo("install_mainline command:\n" + "    install mainline modules\n" + "\n");
    assertThat(details.toString(UTF_8))
        .isEqualTo(
            "Description:\n"
                + "    install mainline modules\n"
                + "\n"
                + "Synopsis:\n"
                + "    DeviceActionMain install_mainline\n"
                + "        \"--da_bundletool <bundletool.jar>\"\n"
                + "        [\"--action dev_key_signed\"]\n"
                + "        [\"--action enable_rollback\"]\n"
                + "        (\"--action"
                + " file_apks_zips=<train1.zip>,file_apks_zips=<train2.zip>\"|\"--\n"
                + "            action file_mainline_modules=<module1.apex>,\n"
                + "            file_mainline_modules=<module2.apk>\"|\"--action\n"
                + "            file_train_folder=<folder>\")\n"
                + "        [\"--bool_flag\"]\n"
                + "\n"
                + "Flags:\n"
                + "    --da_bundletool: Path to the bundletool jar file.\n"
                + "\n"
                + "    dev_key_signed: (Optional) If the packages are signed by dev keys. It is\n"
                + "        false by default.\n"
                + "\n"
                + "    enable_rollback: (Optional) Optional flag to enable rollback if install\n"
                + "        fails.\n"
                + "\n"
                + "    file_apks_zips: repeated flags of zip files containing apks.\n"
                + "\n"
                + "    file_mainline_modules: repeated flags of mainline modules\n"
                + "\n"
                + "    file_train_folder: Path to the folder of mainline modules\n"
                + "\n"
                + "    --bool_flag: (Optional) Unknown bool flag\n"
                + "\n");
  }
}
