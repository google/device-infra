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

package com.google.devtools.mobileharness.fe.v6.service.search.query.suggest;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TextSegment;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyVocabulary;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDescriptor;
import javax.annotation.Nullable;

/**
 * Wording of a suggestion row: its dim leading label, its main text, and the value spelling it
 * shows.
 *
 * <p>Every filter row carries a fixed action mode in its leading label ("Add filter" or "Modify
 * filter") and names its key once at the start of its main text: {@code <Key>} for a bare-key
 * picker opener, {@code <Key> <verb> <Value>} for a new condition (where the verb agrees with the
 * key's grammatical number), and {@code <Key>: add <Value>} or {@code <Key>: exclude <Value>} when
 * merging into an existing chip. The value is the emphasized segment and is shown in its original
 * spelling from the index, never in the lowercase form used for matching. Keys arrive as {@link
 * KeyDescriptor}s and their title comes from the entity's {@link KeyVocabulary}, so a raw key id is
 * never rendered and a shared host key reads naturally in whichever entity is searched.
 */
final class SuggestionText {

  /** The dim leading label: "Add filter" for a new key, "Modify filter" for an applied one. */
  static String label(boolean inChip) {
    return inChip ? "Modify filter" : "Add filter";
  }

  /** The verb agreeing with the key's grammatical number: "is", "are", "is not", "are not". */
  static String verb(KeyDescriptor key, boolean exclude) {
    String verb = key.display().isPlural() ? "are" : "is";
    return exclude ? verb + " not" : verb;
  }

  /** {@code <Key> <verb> } followed by the emphasized value, the standard add-filter main text. */
  static ImmutableList<TextSegment> conditionText(
      KeyVocabulary vocabulary, KeyDescriptor key, boolean exclude, String shownValue) {
    return segments(vocabulary.titleDisplayName(key) + " " + verb(key, exclude) + " ", shownValue);
  }

  /** Leading plain prefix ({@code <Key>: add } or {@code <Key>: exclude }) for a modify row. */
  static String modifyPrefix(KeyVocabulary vocabulary, KeyDescriptor key, boolean exclude) {
    return vocabulary.titleDisplayName(key) + (exclude ? ": exclude " : ": add ");
  }

  /** {@code <Key>: add/exclude } followed by the emphasized value, for modifying an active chip. */
  static ImmutableList<TextSegment> modifyConditionText(
      KeyVocabulary vocabulary, KeyDescriptor key, boolean exclude, String shownValue) {
    return segments(modifyPrefix(vocabulary, key, exclude), shownValue);
  }

  /** A plain segment followed by an optional emphasized one. */
  static ImmutableList<TextSegment> segments(String plain, @Nullable String emphasized) {
    ImmutableList.Builder<TextSegment> out = ImmutableList.builder();
    if (!plain.isEmpty()) {
      out.add(text(plain, false));
    }
    if (emphasized != null) {
      out.add(text(emphasized, true));
    }
    return out.build();
  }

  static TextSegment text(String value, boolean emphasized) {
    return TextSegment.newBuilder().setText(value).setEmphasized(emphasized).build();
  }

  /** The original spelling of an indexed value, falling back to its lowercase form. */
  static String displayValue(FleetIndex index, String keyId, String valueLower) {
    return index.valueDisplays(keyId).getOrDefault(valueLower, valueLower);
  }

  private SuggestionText() {}
}
