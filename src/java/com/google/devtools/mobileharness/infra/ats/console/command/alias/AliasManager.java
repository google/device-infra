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

package com.google.devtools.mobileharness.infra.ats.console.command.alias;

import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.util.command.CommandHelper;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Manages command aliases in memory.
 *
 * <p>Note: this class and the aliases reading/writing methods are not thread-safe. Currently only
 * the Alias command may write aliases, while the Alias and Run commands may read them.
 */
@Singleton
public class AliasManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String ALIASES_FILE_NAME = "aliases";
  private static final Pattern ALIAS_DEFINITION_PATTERN =
      Pattern.compile("alias (.+?)=['\"]?(.*?)['\"]?$");
  private final Map<String, String> aliases = new HashMap<>();
  private final ConsoleInfo consoleInfo;
  private final CommandHelper commandHelper;
  private final LocalFileUtil localFileUtil;
  private boolean predefinedAliasesLoaded = false;

  @Inject
  AliasManager(ConsoleInfo consoleInfo, CommandHelper commandHelper, LocalFileUtil localFileUtil) {
    this.consoleInfo = consoleInfo;
    this.commandHelper = commandHelper;
    this.localFileUtil = localFileUtil;
  }

  /** Defines or redefines an alias. */
  public void addAlias(String name, String value) {
    aliases.put(name, value);
  }

  /**
   * Gets the expansion of an alias.
   *
   * @param name the name of the alias
   * @return the expansion of the alias, or {@link Optional#empty()} if the alias is not found
   */
  public Optional<String> getAlias(String name) {
    ensurePredefinedAliasesLoaded();
    return Optional.ofNullable(aliases.get(name));
  }

  /**
   * Gets all aliases.
   *
   * @return all aliases
   */
  public ImmutableMap<String, String> getAll() {
    ensurePredefinedAliasesLoaded();
    return ImmutableMap.copyOf(aliases);
  }

  private void ensurePredefinedAliasesLoaded() {
    if (predefinedAliasesLoaded) {
      return;
    }
    predefinedAliasesLoaded = true;
    Path xtsToolsDir =
        XtsDirUtil.getXtsToolsDir(
            consoleInfo.getXtsRootDirectoryNonEmpty(), commandHelper.getXtsType());
    Path aliasesFilePath = xtsToolsDir.resolve(ALIASES_FILE_NAME);

    if (!localFileUtil.isFileExist(aliasesFilePath)) {
      return;
    }

    String aliases;
    try {
      aliases = localFileUtil.readFile(aliasesFilePath);
    } catch (MobileHarnessException e) {
      logger
          .atWarning()
          .with(IMPORTANCE, IMPORTANT)
          .withCause(e)
          .log("Failed to read aliases file: %s", aliasesFilePath);
      return;
    }

    Splitter.on('\n')
        .splitToStream(aliases)
        .forEach(
            alias -> {
              Matcher matcher = ALIAS_DEFINITION_PATTERN.matcher(alias);
              if (matcher.matches()) {
                String aliasName = matcher.group(1).trim();
                String aliasValue = matcher.group(2).trim();
                addAlias(aliasName, aliasValue);
                logger
                    .atInfo()
                    .with(IMPORTANCE, IMPORTANT)
                    .log("Alias '%s' loaded, value: [%s].", aliasName, aliasValue);
              } else {
                logger
                    .atWarning()
                    .with(IMPORTANCE, IMPORTANT)
                    .log("Invalid alias definition [%s] ignored.", alias);
              }
            });
  }
}
