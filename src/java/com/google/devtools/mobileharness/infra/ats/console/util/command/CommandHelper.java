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

package com.google.devtools.mobileharness.infra.ats.console.util.command;

import com.google.common.base.Suppliers;
import com.google.devtools.mobileharness.infra.ats.common.XtsTypeLoader;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import java.util.Optional;
import java.util.function.Supplier;
import javax.inject.Inject;

/** Helper class for commands. */
public class CommandHelper {

  private final ConsoleInfo consoleInfo;
  private final XtsTypeLoader xtsTypeLoader;

  private final Supplier<String> xtsTypeSupplier = Suppliers.memoize(this::calculateXtsType);

  @Inject
  CommandHelper(ConsoleInfo consoleInfo, XtsTypeLoader xtsTypeLoader) {
    this.consoleInfo = consoleInfo;
    this.xtsTypeLoader = xtsTypeLoader;
  }

  /**
   * Gets the xts type from system property flags or from the XTS root directory. System property
   * flags have higher priority. Once the xts type is calculated, it will be cached and returned
   * directly in the following calls.
   *
   * <p>Note: This method mainly serves ATS Console use case. For ATS Server, please use {@link
   * XtsTypeLoader} directly.
   */
  public String getXtsType() {
    return xtsTypeSupplier.get();
  }

  private String calculateXtsType() {
    Optional<String> xtsTypeFromSystemProperty = consoleInfo.getXtsTypeFromSystemProperty();
    if (xtsTypeFromSystemProperty.isPresent()) {
      return xtsTypeFromSystemProperty.get();
    }

    String xtsRootDir = consoleInfo.getXtsRootDirectoryNonEmpty();
    Supplier<String> multiDirHelpMessage =
        () -> String.format("Please specify XTS type by -D%s.", ConsoleInfo.XTS_TYPE_PROPERTY_KEY);
    return xtsTypeLoader.getXtsType(xtsRootDir, multiDirHelpMessage);
  }
}
