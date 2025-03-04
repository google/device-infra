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

package com.google.devtools.mobileharness.platform.android.xts.common.util;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 * A loader to load test names from a mobly module by executing list command against its test
 * binary.
 */
public class MoblyTestLoader {
  private static final Pattern TEST_CLASS_PATTERN =
      Pattern.compile("==========> (\\w+) <==========");
  private static final Splitter NEWLINE_SPLITTER =
      Splitter.on('\n').trimResults().omitEmptyStrings();

  private final CommandExecutor commandExecutor;
  private final LocalFileUtil localFileUtil;

  @Inject
  MoblyTestLoader(CommandExecutor commandExecutor, LocalFileUtil localFileUtil) {
    this.commandExecutor = commandExecutor;
    this.localFileUtil = localFileUtil;
  }

  /**
   * Gets test names in a mobly module.
   *
   * @param moduleConfigPath the path of the module config file
   * @param moduleConfig the module config
   * @return a list of test names
   * @throws MobileHarnessException if fails to get test names
   */
  public ImmutableList<String> getTestNamesInModule(
      Path moduleConfigPath, Configuration moduleConfig)
      throws InterruptedException, MobileHarnessException {
    List<Path> filePaths =
        localFileUtil.listFilePaths(
            moduleConfigPath.getParent(),
            /* recursively= */ true,
            file ->
                file.getFileName().toString().equals(moduleConfig.getMetadata().getXtsModule()));

    if (filePaths.isEmpty()) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_LOAD_MOBLY_TEST_NAMES_ERROR,
          "The mobly binary does not exist in directory or sub-directory of "
              + moduleConfigPath.getParent());
    }
    Path moblyBinaryPath = filePaths.get(0);
    Command command =
        Command.of(moblyBinaryPath.toAbsolutePath().toString(), "--", "-l").redirectStderr(false);
    String output = commandExecutor.run(command);
    List<String> lines = NEWLINE_SPLITTER.splitToList(output);

    List<String> testNames = new ArrayList<>();
    String testClass = null;
    for (String line : lines) {
      Matcher matcher = TEST_CLASS_PATTERN.matcher(line);
      if (matcher.matches()) {
        testClass = matcher.group(1);
      } else if (testClass != null) {
        if (line.contains(".")) {
          // Test name is in the format of "TestClass.test_case", as output by Mobly suite_runner.
          testNames.add(line);
        } else {
          // Test name is in the format of "test_case", as output by Mobly test_runner.
          testNames.add(Joiner.on(".").join(testClass, line));
        }
      }
    }
    if (testNames.isEmpty()) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_LOAD_MOBLY_TEST_NAMES_ERROR,
          "Failed to get test cases from the mobly binary. Command: "
              + command
              + ". Output: "
              + output);
    }
    return ImmutableList.copyOf(testNames);
  }
}
