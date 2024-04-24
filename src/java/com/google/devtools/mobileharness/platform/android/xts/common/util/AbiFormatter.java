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

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.annotation.Nullable;

/** Utility class for abi. */
public class AbiFormatter {

  /**
   * Helper method to get the list of supported abis
   *
   * @param deviceAbiList value of device property "ro.product.cpu.abilist"
   * @param deviceAbi value of device property "ro.product.cpu.abi"
   */
  public static List<String> getSupportedAbis(
      @Nullable String deviceAbiList, @Nullable String deviceAbi) {
    if (!isNullOrEmpty(deviceAbiList)) {
      List<String> abis =
          Splitter.on(",").trimResults().omitEmptyStrings().splitToList(deviceAbiList);
      if (!abis.isEmpty()) {
        return abis;
      }
    }

    // fallback plan for before lmp
    if (!isNullOrEmpty(deviceAbi)) {
      return ImmutableList.of(deviceAbi);
    }
    return ImmutableList.of();
  }

  private AbiFormatter() {}
}
