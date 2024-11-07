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

package com.google.wireless.qa.mobileharness.shared.api.spec;

import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;

/** Shared param in the job info used to split entries. */
public interface EntryDelimiterSpec {

  @ParamAnnotation(
      required = false,
      help =
          "The delimiter string of input entries. For many iOS drivers this applies to "
              + "PARAM_ENV_VARS and PARAM_ARGS entries. For the AndroidInstrumentation driver this "
              + "applies to --test_args."
              + StrUtil.DEFAULT_ENTRY_DELIMITER
              + "is the default value.")
  public static final String PARAM_ENTRY_DELIMITER = "entry_delimiter";
}
