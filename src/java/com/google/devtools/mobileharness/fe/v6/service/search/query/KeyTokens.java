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

package com.google.devtools.mobileharness.fe.v6.service.search.query;

import com.google.common.base.CharMatcher;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lexical helpers for the text a user types as a search key.
 *
 * <p>This class answers one question: given the raw characters of a key token, what canonical form
 * should be compared against alias tables and key names? It knows nothing about which keys exist or
 * which entity is being searched; that is the job of a {@link KeyVocabulary}. Keeping the lexical
 * rules here means every vocabulary and the suggester normalize tokens identically, so an alias
 * declared as {@code "lab location"} matches {@code "Lab_Location"} and {@code "lab location"}
 * alike, and a discovered key named {@code "Rack_Slot"} is found by typing {@code "rack slot"}.
 *
 * <p>Two namespace spellings are recognized so a user can name a long-tail key explicitly instead
 * of relying on discovery: {@code dimension:<name>} (also {@code device dimension <name>}) and
 * {@code host property:<name>} (also {@code host_property <name>}). Whether an entity accepts a
 * given namespace is decided by its vocabulary, not here.
 */
public final class KeyTokens {

  /** A run of whitespace or underscores, the separators a user may type between words. */
  private static final Pattern SEPARATOR_RUN = Pattern.compile("[\\s_]+");

  private static final CharMatcher UNDERSCORE = CharMatcher.is('_');

  /** {@code dimension:<name>}, {@code dimension <name>}, {@code device__dimension: <name>}. */
  private static final Pattern DIMENSION_NAMESPACE =
      Pattern.compile("^(?:device[\\s_]+)?dimension[\\s_:]+(.+)$");

  /** {@code host property:<name>}, {@code host_property <name>}, {@code hostproperty:<name>}. */
  private static final Pattern HOST_PROPERTY_NAMESPACE =
      Pattern.compile("^host[\\s_]*property[\\s_:]+(.+)$");

  /**
   * Canonical form of a key token: lowercased, every run of whitespace or underscores collapsed to
   * a single underscore, and no underscore at either end. This is the form alias tables are keyed
   * by and the form a typed token is compared against discovered key names. A token made only of
   * separators ({@code "___"}) normalizes to the empty string, so it names nothing rather than a
   * key called {@code "_"}.
   */
  public static String normalize(String token) {
    String collapsed = SEPARATOR_RUN.matcher(lower(token)).replaceAll("_");
    return UNDERSCORE.trimFrom(collapsed);
  }

  /** The dimension name if the token uses the dimension namespace spelling, normalized. */
  static Optional<String> dimensionName(String token) {
    return namespaced(DIMENSION_NAMESPACE, token);
  }

  /** The property name if the token uses the host property namespace spelling, normalized. */
  static Optional<String> hostPropertyName(String token) {
    return namespaced(HOST_PROPERTY_NAMESPACE, token);
  }

  /** Whether the token uses either namespace spelling. */
  static boolean isNamespaced(String token) {
    return dimensionName(token).isPresent() || hostPropertyName(token).isPresent();
  }

  private static Optional<String> namespaced(Pattern namespace, String token) {
    Matcher matcher = namespace.matcher(lower(token));
    if (!matcher.matches()) {
      return Optional.empty();
    }
    String name = normalize(matcher.group(1));
    return name.isEmpty() ? Optional.empty() : Optional.of(name);
  }

  private static String lower(String token) {
    return token.strip().toLowerCase(Locale.ROOT);
  }

  private KeyTokens() {}
}
