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

package com.google.devtools.mobileharness.shared.util.reflection;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@SuppressWarnings({"rawtypes", "unused"}) // Expected warnings.
@RunWith(JUnit4.class)
public class TypeUtilTest {

  private static class ValidClass {}

  private static class GenericClass<T> {}

  private static class RawSubclass extends ArrayList {
    private RawSubclass() {}
  }

  @Test
  public void checkCompleteness_validClass_success() {
    TypeUtil.checkCompleteness(ValidClass.class);
  }

  @Test
  public void checkCompleteness_validParameterizedType_success() {
    Type type = new TypeToken<List<String>>() {}.getType();
    TypeUtil.checkCompleteness(type);
  }

  @Test
  public void checkCompleteness_stringArray_success() {
    TypeUtil.checkCompleteness(String[].class);
  }

  @Test
  public void checkCompleteness_rawClass_throwsException() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> TypeUtil.checkCompleteness(List.class));
    assertThat(e).hasMessageThat().contains("must not be raw");
  }

  @Test
  public void checkCompleteness_listOfRawArray_throwsException() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> TypeUtil.checkCompleteness(List[].class));
    assertThat(e).hasMessageThat().contains("must not be raw");
  }

  @Test
  public void checkCompleteness_rawSubclass_throwsException() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> TypeUtil.checkCompleteness(RawSubclass.class));
    assertThat(e).hasMessageThat().contains("must not be raw");
  }

  @Test
  public void checkCompleteness_wildcard_throwsException() {
    Type type = new TypeToken<List<?>>() {}.getType();
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> TypeUtil.checkCompleteness(type));
    assertThat(e).hasMessageThat().contains("must not contain");
  }

  @Test
  public void checkCompleteness_nestedWildcard_throwsException() {
    Type type = new TypeToken<List<GenericClass<?>>>() {}.getType();
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> TypeUtil.checkCompleteness(type));
    assertThat(e).hasMessageThat().contains("must not contain");
  }
}
