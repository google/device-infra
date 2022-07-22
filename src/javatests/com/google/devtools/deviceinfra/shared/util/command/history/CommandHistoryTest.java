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

package com.google.devtools.deviceinfra.shared.util.command.history;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CommandHistoryTest {

  private CommandHistory commandHistory;

  @Before
  public void setUp() {
    commandHistory = new CommandHistory(2);

    commandHistory.addCommand(ImmutableList.of("a", "b"));
    commandHistory.addCommand(ImmutableList.of("c", "d"));
    commandHistory.addCommand(ImmutableList.of("e", "f"));
  }

  @Test
  public void getAllCommands_removeEldestEntry() {
    assertThat(commandHistory.getAllCommands())
        .comparingElementsUsing(
            Correspondence.from(
                (@Nullable CommandRecord actual, @Nullable List<String> expected) ->
                    actual != null
                        && actual.command().equals(expected)
                        && actual
                            .stackTrace()
                            .get(0)
                            .getClassName()
                            .equals(CommandHistoryTest.class.getName()),
                "has command"))
        .containsExactly(ImmutableList.of("c", "d"), ImmutableList.of("e", "f"))
        .inOrder();
  }

  @Test
  public void searchCommands() {
    assertThat(
            commandHistory.searchCommands(
                commandRecord -> commandRecord.command().equals(ImmutableList.of("c", "d"))))
        .comparingElementsUsing(
            Correspondence.from(
                (@Nullable CommandRecord actual, @Nullable List<String> expected) ->
                    actual != null && actual.command().equals(expected),
                "has command"))
        .containsExactly(ImmutableList.of("c", "d"))
        .inOrder();
  }
}
