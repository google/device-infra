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

package com.google.devtools.deviceinfra.shared.util.command;

import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;

/** Command bug checker for checking potential bugs before starting a command. */
class CommandBugChecker {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  void checkCommand(Command command) {
    if (command.getExecutable().equals("sudo") && !isRunAsRoot()) {
      logger.atWarning().log(
          "Command [%s] uses sudo however the current process does not run as root, which"
              + " may cause the command to keep hanging until timeout and then throw out"
              + " InterruptedException",
          command);
    }
  }

  // LINT.IfChange
  // Duplicate codes from jcg/devtools/deviceinfra/shared/util/system/SystemUtil.java to avoid
  // circular dependency as SystemUtil.java has depended on
  // "//third_party/deviceinfra/src/java/com/google/devtools/deviceinfra/shared/util/command".
  private static boolean isRunAsRoot() {
    // Try system env "USER" first unless it is not set, fall back to "user.name" instead.

    // Some experimental results that show "user.name" can not always correctly reflect the truth:
    // 1. Everything is right:
    //         exec("logname") = [udcluster]
    //    getprop("user.name") = [udcluster]
    //          getenv("USER") = [udcluster]
    // 2. user.name returns "?"
    //         exec("logname") = [root]
    //    getprop("user.name") = [?]
    //          getenv("USER") = [root]
    // 3. no logname output:
    //         exec("logname") = [ERROR: logname: no login name]
    //    getprop("user.name") = [root]
    //          getenv("USER") = [root]
    // 4. no logname output and user.name returns "?":
    //         exec("logname") = [ERROR: logname: no login name]
    //    getprop("user.name") = [?]
    //          getenv("USER") = [udcluster]
    // 5. no logname output and env "USER" returns "null" :
    //         exec("logname") = [ERROR: logname: no login name]
    //    getprop("user.name") = [root]
    //          getenv("USER") = [null]
    String userName = System.getenv("USER");
    if (!Strings.isNullOrEmpty(userName)) {
      return "root".equals(userName);
    }

    return "root".equals(StandardSystemProperty.USER_NAME.value());
  }
  // LINT.ThenChange(//depot/google3/java/com/google/devtools/deviceinfra/shared/util/system/\
  //                 SystemUtil.java:getUser)
}
