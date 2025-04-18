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

package com.google.devtools.mobileharness.shared.util.file.local;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;

/**
 * A class to represent a file name argument to the unzip command.
 *
 * <p>There are 3 different formats used in mobileharness:
 *
 * <p>1. Arguments to the unzip command. This is the input to the constructor and is returned by the
 * {@link #arg()} method. See `$ man unzip` for more details about the syntax itself.
 *
 * <p>2. File paths as in the file system. This is returned by the {@link #path()} method.
 *
 * <p>3. Arguments to the CAS downloader command as regular expressions. This is returned by the
 * {@link #regex()} method.
 */
public class UnzipFileName {
  private final String arg;
  private final ImmutableList<Token> tokens;

  /**
   * Constructs an UnzipFileName from the given argument.
   *
   * @param arg the raw command line argument to the unzip command.
   */
  public UnzipFileName(String arg) {
    this.arg = arg;
    this.tokens = this.parse();
  }

  /** Constructs a list of UnzipFileName from the given arguments. */
  public static ImmutableList<UnzipFileName> listOf(String... args) {
    return stream(args).map(UnzipFileName::new).collect(toImmutableList());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof UnzipFileName) {
      UnzipFileName that = (UnzipFileName) o;
      return arg.equals(that.arg);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return arg.hashCode();
  }

  private enum Kind {
    CHAR,
    WILDCARD,
    IN_BRACKET
  }

  private static class Token {
    final Kind kind;
    final char ch;
    final String regex;

    private Token(Kind kind, char ch, String regex) {
      this.kind = kind;
      this.ch = ch;
      this.regex = regex;
    }

    static Token literal(char ch) {
      return new Token(Kind.CHAR, ch, "");
    }

    static Token wildcard(char ch, String regex) {
      return new Token(Kind.WILDCARD, ch, regex);
    }

    static Token bracketNegation(char ch) {
      return new Token(Kind.IN_BRACKET, ch, "^");
    }

    static Token bracketChar(char ch) {
      if (ch == '\\' || ch == ']' || ch == '^') { // they have special meaning in brackets
        return new Token(Kind.IN_BRACKET, ch, "\\" + ch);
      }
      return new Token(Kind.IN_BRACKET, ch, Character.toString(ch));
    }
  }

  private ImmutableList<Token> parse() {
    ImmutableList.Builder<Token> builder = ImmutableList.builder();
    for (int i = 0; i < arg.length(); ++i) {
      char ch = arg.charAt(i);
      switch (ch) {
        case '\\':
          if (++i < arg.length()) {
            builder.add(Token.literal(arg.charAt(i)));
          } // else invalid escape, discarded
          break;
        case '*':
          builder.add(Token.wildcard(ch, ".*"));
          break;
        case '?':
          builder.add(Token.wildcard(ch, "."));
          break;
        case '[':
          i = parseBracket(i, builder);
          break;
        default:
          builder.add(Token.literal(ch));
      }
    }
    return builder.build();
  }

  private int parseBracket(int start, ImmutableList.Builder<Token> builder) {
    builder.add(Token.wildcard('[', "["));
    int i = start + 1;
    for (; i < arg.length(); ++i) {
      char ch = arg.charAt(i);
      switch (ch) {
        case '\\':
          if (++i < arg.length()) {
            builder.add(Token.bracketChar(arg.charAt(i)));
          } // else invalid escape, discarded
          break;
        case ']':
          builder.add(Token.wildcard(ch, "]"));
          return i;
        case '!':
        case '^':
          if (i == start + 1) {
            builder.add(Token.bracketNegation(ch));
          } else {
            builder.add(Token.bracketChar(ch));
          }
          break;
        default:
          builder.add(Token.bracketChar(ch));
      }
    }
    return i;
  }

  /** Returns the raw command line argument to the unzip command. */
  public String arg() {
    return arg;
  }

  /** Returns true if the name contains any unescaped '*', '?' or '['. */
  public boolean hasWildcard() {
    return tokens.stream().anyMatch(token -> token.kind == Kind.WILDCARD);
  }

  /**
   * Returns the unescaped literal path.
   *
   * <p>Any wildcards will be kept as is. So this is inappropriate for use if hasWildcard() is true.
   */
  public String path() {
    return tokens.stream().map(t -> Character.toString(t.ch)).collect(joining());
  }

  /** A builder used by the regex() method to manage \Q...\E quoting. */
  private static class RegexBuilder {
    private final StringBuilder builder = new StringBuilder();
    private boolean inQuote = false;

    void beginQuote() {
      if (!inQuote) {
        builder.append("\\Q");
        inQuote = true;
      }
    }

    void endQuote() {
      if (inQuote) {
        builder.append("\\E");
        inQuote = false;
      }
    }

    void appendQuote(char c) {
      beginQuote();
      builder.append(c);
      if (c == '\\') {
        endQuote(); // in case there is a literal \E to match
      }
    }

    void appendRegex(String w) {
      endQuote();
      builder.append(w);
    }

    String regex() {
      return builder.toString();
    }
  }

  /**
   * Returns the equivalent regular expression.
   *
   * <p>The return value will be passed to a Go binary, so the Go regex syntax is used. See
   * http://godoc/pkg/regexp/syntax.
   *
   * <p>Invalid '[...]' wildcards will convert to invalid regular expressions.
   */
  public String regex() {
    UnzipFileName.RegexBuilder builder = new UnzipFileName.RegexBuilder();
    builder.appendRegex("^");
    for (Token token : tokens) {
      if (token.kind == Kind.CHAR) {
        builder.appendQuote(token.ch);
      } else {
        builder.appendRegex(token.regex);
      }
    }
    builder.appendRegex("$");
    return builder.regex();
  }
}
