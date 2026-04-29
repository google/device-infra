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

package com.google.devtools.mobileharness.shared.util.flags.core;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/** Invalid flags for testing. */
@SuppressWarnings({"rawtypes", "unused"})
public class InvalidFlagsForTesting {

  public static class RawListFlag {
    @FlagSpec(name = "raw_list", help = "help")
    public static final Flag<List> flag = Flag.value(ImmutableList.of());

    private RawListFlag() {}
  }

  public static class RawMyListFlag {
    public static class MyRawList<T> extends ArrayList<T> {
      private MyRawList() {}
    }

    @FlagSpec(name = "raw_my_list", help = "help")
    public static final Flag<MyRawList> flag = Flag.value(new MyRawList());

    private RawMyListFlag() {}
  }

  public static class WildcardListFlag {
    @FlagSpec(name = "wildcard_list", help = "help")
    public static final Flag<List<?>> flag = Flag.value(ImmutableList.of());

    private WildcardListFlag() {}
  }

  public static class WildcardInsideListFlag {
    public static class WildcardInsideClass<T> {
      private WildcardInsideClass() {}
    }

    @FlagSpec(name = "wildcard_inside", help = "help")
    public static final Flag<List<WildcardInsideClass<?>>> flag = Flag.value(ImmutableList.of());

    private WildcardInsideListFlag() {}
  }

  public static class NonGenericRawListFlag {
    public static class NonGenericRawList extends ArrayList {
      private NonGenericRawList() {}
    }

    @FlagSpec(name = "non_generic_raw_list", help = "help")
    public static final Flag<NonGenericRawList> flag = Flag.value(new NonGenericRawList());

    private NonGenericRawListFlag() {}
  }

  public static class DeeplyNestedWildcardFlag {
    public static class DeeplyNestedClass<T> {
      private DeeplyNestedClass() {}
    }

    @FlagSpec(name = "deeply_nested_wildcard", help = "help")
    public static final Flag<List<List<DeeplyNestedClass<?>>>> flag =
        Flag.value(ImmutableList.of());

    private DeeplyNestedWildcardFlag() {}
  }

  public static class ListWithRawElementFlag {
    @FlagSpec(name = "list_with_raw_element", help = "help")
    public static final Flag<List<RawMyListFlag.MyRawList>> flag = Flag.value(ImmutableList.of());

    private ListWithRawElementFlag() {}
  }

  public static class WildcardFlag {
    @FlagSpec(name = "wildcard_flag", help = "help")
    public static final Flag<?> flag = Flag.value(123);

    private WildcardFlag() {}
  }

  public static class RawClassFlag {
    public static class MyRawClass<T> {
      private MyRawClass() {}
    }

    @FlagSpec(name = "raw_class_flag", help = "help")
    public static final Flag<MyRawClass> flag = Flag.value(new MyRawClass());

    private RawClassFlag() {}
  }

  private InvalidFlagsForTesting() {}
}
