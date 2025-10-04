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

package com.google.devtools.mobileharness.shared.util.comparator;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/**
 * A comparator of <a href="https://en.wikipedia.org/wiki/Natural_sort_order">natural sort
 * order</a>.
 *
 * <p>{@link com.ibm.icu.text.Collator} with "<a
 * href="https://www.unicode.org/reports/tr35/tr35-collation.html#Collation_Settings">kn</a>"
 * Unicode locale keyword cannot be used because the library size of icu4j is too large.
 */
public class NaturalSortOrderComparator implements Comparator<String> {

  /** A non-empty substring of a string. */
  @AutoValue
  abstract static class Section {

    private static Section of(String substring, @Nullable Integer number) {
      return new AutoValue_NaturalSortOrderComparator_Section(
          substring, Optional.ofNullable(number));
    }

    abstract String substring();

    abstract Optional<Integer> number();
  }

  private final Map<String, ImmutableList<Section>> sectionsCache = new ConcurrentHashMap<>();

  @Override
  public int compare(String string1, String string2) {
    ImmutableList<Section> sections1 =
        sectionsCache.computeIfAbsent(string1, NaturalSortOrderComparator::preprocess);
    ImmutableList<Section> sections2 =
        sectionsCache.computeIfAbsent(string2, NaturalSortOrderComparator::preprocess);

    int sectionNum1 = sections1.size();
    int sectionNum2 = sections2.size();

    int secondaryComparingResult = 0;

    // Compares each section which is a non-empty string of consecutive digits or non-digits.
    int minSectionNum = Math.min(sectionNum1, sectionNum2);
    for (int i = 0; i < minSectionNum; i++) {
      Section section1 = sections1.get(i);
      Section section2 = sections2.get(i);

      Optional<Integer> number1 = section1.number();
      Optional<Integer> number2 = section2.number();

      int comparingResult;
      if (number1.isPresent() && number2.isEmpty()) {
        // "123" < "abc".
        comparingResult = -1;
      } else if (number1.isEmpty() && number2.isPresent()) {
        comparingResult = 1;
      } else if (number1.isEmpty()) {
        // "abc" < "def".
        comparingResult = section1.substring().compareTo(section2.substring());
      } else {
        // "123" < "456"
        comparingResult = Integer.compare(number1.get(), number2.get());
        if (comparingResult == 0 && secondaryComparingResult == 0) {
          secondaryComparingResult = section2.substring().compareTo(section1.substring());
        }
      }

      if (comparingResult != 0) {
        return comparingResult;
      }
    }

    // "123" < "123a"
    int sectionNumComparingResult = Integer.compare(sectionNum1, sectionNum2);

    // "1" < "001" (only if all other comparing results are the same)
    return sectionNumComparingResult == 0 ? secondaryComparingResult : sectionNumComparingResult;
  }

  private static ImmutableList<Section> preprocess(String string) {
    ImmutableList.Builder<Section> sections = ImmutableList.builder();

    boolean inSection = false;
    int sectionStart = 0;
    int sectionEnd = 0;
    boolean sectionIsNumber = false;

    int stringLength = string.length();
    for (int i = 0; i < stringLength; i++) {
      char c = string.charAt(i);
      boolean isDigit = Character.isDigit(c);

      if (!inSection || (isDigit != sectionIsNumber)) { // Needs to create a new section.
        if (inSection) {
          sections.add(createSection(string, sectionStart, sectionEnd, sectionIsNumber));
        }

        // Creates a new section.
        inSection = true;
        sectionStart = i;
        sectionEnd = i + 1;
        sectionIsNumber = isDigit;
      } else {
        sectionEnd++;
      }
    }

    if (inSection) {
      sections.add(createSection(string, sectionStart, sectionEnd, sectionIsNumber));
    }

    return sections.build();
  }

  private static Section createSection(String string, int start, int end, boolean isNumber) {
    String substring = string.substring(start, end);
    return Section.of(substring, isNumber ? Integer.parseInt(substring) : null);
  }
}
