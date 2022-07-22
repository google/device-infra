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

package com.google.devtools.deviceinfra.shared.util.command.java;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JavaCommandCreatorTest {

  @Test
  public void createJavaCommand() {
    assertThat(
            JavaCommandCreator.of(true /* useStandardInvocationForm */, "/fake/java")
                .createJavaCommand(
                    "/foo_deploy.jar",
                    ImmutableList.of("--arg1", "--arg2"),
                    ImmutableList.of("-native-arg1", "-native-arg2")))
        .containsExactly(
            "/fake/java",
            "-native-arg1",
            "-native-arg2",
            "-jar",
            "/foo_deploy.jar",
            "--arg1",
            "--arg2")
        .inOrder();

    assertThat(
            JavaCommandCreator.of(false /* useStandardInvocationForm */, "/fake/java")
                .createJavaCommand(
                    "/foo_deploy.jar",
                    ImmutableList.of("--arg1", "--arg2"),
                    ImmutableList.of("-native-arg1", "-native-arg2")))
        .containsExactly(
            "/foo_deploy.jar", "-native-arg1", "-native-arg2", "run", "--arg1", "--arg2")
        .inOrder();
  }
}
