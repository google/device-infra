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

package com.google.devtools.mobileharness.infra.ats.console.command.picocli.parameterpreprocessor;

import java.util.HashMap;
import java.util.Map;
import picocli.CommandLine.Model.ArgSpec;

/** Preprocessor to preprocess parameters with value type {@code Map<String, String>}. */
public class MapPreprocessor extends MapEntryPreprocessor {

  @Override
  void putEntry(ArgSpec argSpec, String key, String value) {
    Map<String, String> map = argSpec.getValue();
    map = map == null ? new HashMap<>() : map;
    argSpec.setValue(map);

    map.put(key, value);
  }
}
