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

package com.google.devtools.mobileharness.platform.android.xts.suite;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Represents an argument for a module. */
@AutoValue
public abstract class ModuleArg {
  private static final String KEY_VALUE_SEPARATOR = ":=";
  private static final String FILE_KEY = "file";
  private static final Pattern MODULE_ARG_PATTERN =
      Pattern.compile("(?<moduleName>[^:\\[\\]]+(\\[.+\\])?):(?<argName>[^:]+):(?<argKeyValue>.+)");

  public abstract String moduleName();

  public abstract String argName();

  public abstract String argKey();

  public abstract String argValue();

  /**
   * Creates a {@link ModuleArg} from a string representation.
   *
   * <p>The string representation should be a valid module argument in the format of:
   *
   * <p><module_name>:<arg_name>:[<arg_key>:=]<arg_value>
   */
  public static ModuleArg create(String moduleArgStr) throws MobileHarnessException {
    Matcher matcher = MODULE_ARG_PATTERN.matcher(moduleArgStr);
    if (!matcher.matches()) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.ATSC_INVALID_MODULE_ARG,
          String.format(
              "Invalid module arg: %s, expected format"
                  + " '<module_name>:<arg_name>:[<arg_key>:=]<arg_value>'",
              moduleArgStr),
          null);
    }

    String moduleIdentifier = matcher.group("moduleName");
    String argName = matcher.group("argName");
    // The first ":=" is used to separate the key and value.
    String[] keyValue = matcher.group("argKeyValue").split(KEY_VALUE_SEPARATOR, 2);
    if (keyValue.length == 1) {
      return new AutoValue_ModuleArg(moduleIdentifier, argName, "", keyValue[0]);
    }
    return new AutoValue_ModuleArg(moduleIdentifier, argName, keyValue[0], keyValue[1]);
  }

  /** Checks if the given string is a valid module argument. */
  public static boolean isValid(String moduleArgStr) {
    return MODULE_ARG_PATTERN.matcher(moduleArgStr).matches();
  }

  /** Checks if the argument is a file. */
  public boolean isFile() {
    return Objects.equals(argKey(), FILE_KEY);
  }
}
