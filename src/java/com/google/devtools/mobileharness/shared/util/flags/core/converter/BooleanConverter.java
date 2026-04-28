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

package com.google.devtools.mobileharness.shared.util.flags.core.converter;

import java.util.regex.Pattern;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Boolean converter that supports aliases. */
public class BooleanConverter implements ITypeConverter<Boolean> {

  private static final Pattern TRUE_PATTERN =
      Pattern.compile("^(true|t|yes|y|1)$", Pattern.CASE_INSENSITIVE);

  private static final Pattern FALSE_PATTERN =
      Pattern.compile("^(false|f|no|n|0)$", Pattern.CASE_INSENSITIVE);

  @Override
  public Boolean convert(String text) {
    if (TRUE_PATTERN.matcher(text).matches()) {
      return Boolean.TRUE;
    } else if (FALSE_PATTERN.matcher(text).matches()) {
      return Boolean.FALSE;
    } else {
      throw new TypeConversionException(
          String.format(
              "'%s' is not a boolean. Expected one of [true, false, yes, no, 1, 0, t, f, y, n]"
                  + " (case-insensitive)",
              text));
    }
  }
}
