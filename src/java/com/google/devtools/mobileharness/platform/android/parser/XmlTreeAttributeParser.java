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

package com.google.devtools.mobileharness.platform.android.parser;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayDeque;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class for parsing attributes from output of dump xmltree. */
@AutoValue
public abstract class XmlTreeAttributeParser extends LineParser {
  /**
   * AutoValue class for an xml element, which is a pair of the indentation and the element line
   * content.
   */
  @AutoValue
  abstract static class Element {
    static Element create(int indent, String lineContent) {
      return new AutoValue_XmlTreeAttributeParser_Element(indent, lineContent);
    }

    abstract int indent();

    abstract String lineContent();
  }

  /**
   * A handler to take the action of {@code onMatch} if an element of {@code elementType} has an
   * attribute matching the pattern {@code attributePattern}.
   */
  @AutoValue
  public abstract static class AttributeHandler {
    public abstract String elementType();

    public abstract Pattern attributePattern();

    public abstract Consumer<Matcher> onMatch();

    public static AttributeHandler.Builder newBuilder() {
      return new AutoValue_XmlTreeAttributeParser_AttributeHandler.Builder();
    }

    /** Builder of AttributeHandler. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setElementType(String value);

      public abstract Builder setAttributePattern(Pattern value);

      public abstract Builder setOnMatch(Consumer<Matcher> value);

      public abstract AttributeHandler build();
    }
  }

  abstract ImmutableList<AttributeHandler> handlers();

  private static final Pattern NON_SPACE_PATTERN = Pattern.compile("\\S");
  // A stack to store parent elements.
  private final ArrayDeque<Element> stack = new ArrayDeque<>();

  /** Parses each line when traverse in dfs order. */
  @Override
  void parseLine(String line) {
    Matcher nonEmptyMatcher = NON_SPACE_PATTERN.matcher(line);
    if (!nonEmptyMatcher.find()) {
      // Ignore empty line.
      return;
    }

    int indent = nonEmptyMatcher.start();
    // Backtrack to parent node.
    while (!stack.isEmpty() && stack.peekFirst().indent() >= indent) {
      stack.removeFirst();
    }

    if (line.length() <= indent + 1) {
      throw new IllegalArgumentException("Line too short.");
    }
    if (line.startsWith("E:", indent)) {
      stack.addFirst(Element.create(indent, line));
      return;
    } else if (!line.startsWith("A:", indent)) {
      return;
    }

    if (stack.isEmpty()) {
      throw new IllegalArgumentException("Xml output misses element nodes.");
    }

    String elementLine = stack.peekFirst().lineContent();
    for (AttributeHandler handler : handlers()) {
      Matcher attrMatcher;
      if (Pattern.matches(String.format("^\\s*E:\\s*%s\\s.*", handler.elementType()), elementLine)
          && (attrMatcher = handler.attributePattern().matcher(line)).matches()) {
        handler.onMatch().accept(attrMatcher);
      }
    }
  }

  public static XmlTreeAttributeParser.Builder newBuilder() {
    return new AutoValue_XmlTreeAttributeParser.Builder();
  }

  /** Builder of XmlTreeAttributeParser. */
  @AutoValue.Builder
  public abstract static class Builder {

    abstract Builder setHandlers(ImmutableList<AttributeHandler> value);

    abstract ImmutableList.Builder<AttributeHandler> handlersBuilder();

    @CanIgnoreReturnValue
    public Builder addHandler(AttributeHandler handler) {
      handlersBuilder().add(handler);
      return this;
    }

    public abstract XmlTreeAttributeParser build();
  }
}
