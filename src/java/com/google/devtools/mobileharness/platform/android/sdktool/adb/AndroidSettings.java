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

package com.google.devtools.mobileharness.platform.android.sdktool.adb;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

/** Android Settings provider (settings) commands. */
public final class AndroidSettings {
  /** Run "adb shell settings help" to get available command name. */
  public enum Command {
    GET("get"),
    PUT("put"),
    DELETE("delete"),
    RESET("reset"),
    LIST("list");

    private final String commandName;

    private Command(String commandName) {
      this.commandName = commandName;
    }

    public String getCommandName() {
      return commandName;
    }
  }

  /** Run "adb shell settings help" to get available command name. */
  public enum NameSpace {
    SYSTEM("system"),
    SECURE("secure"),
    GLOBAL("global");

    private final String nameSpace;

    private NameSpace(String nameSpace) {
      this.nameSpace = nameSpace;
    }

    public String getNameSpace() {
      return nameSpace;
    }
  }

  /** To build command line arguments for "settings" command. */
  @AutoValue
  public abstract static class Spec {
    public static Spec create(Command command, NameSpace space, @Nullable String extraArgs) {
      String commandName = command.getCommandName();
      String nameSpace = space.getNameSpace();
      return new AutoValue_AndroidSettings_Spec(commandName, nameSpace, extraArgs);
    }

    public abstract String commandName();

    public abstract String nameSpace();

    @Nullable
    public abstract String extraArgs();
  }

  private AndroidSettings() {}
}
