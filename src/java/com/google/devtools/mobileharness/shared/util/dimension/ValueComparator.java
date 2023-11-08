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

package com.google.devtools.mobileharness.shared.util.dimension;

/**
 * The comparator used to compare the size relationship between the actual dimension value and the
 * value specified by the user.
 */
public final class ValueComparator {
  /** Dimension value prefix which indicates it is a str value comparison. */
  public static final String PREFIX_STR_COMPARISON = "compare_str:";

  /** Dimension value prefix which indicates it is an int value comparison. */
  public static final String PREFIX_INT_COMPARISON = "compare_int:";

  /**
   * Compares the size relationship between the actual dimension value and the * value specified by
   * the user.
   *
   * @param requirementDimensionValueWithTypeAndOp the comparison prefix + opcode + dimension value
   *     of device requirement.
   * @param actualDimensionValue the actual device dimension value.
   */
  public static boolean match(
      String requirementDimensionValueWithTypeAndOp, String actualDimensionValue) {
    int compareResult;
    String op;
    if (requirementDimensionValueWithTypeAndOp.startsWith(PREFIX_STR_COMPARISON)) {
      op =
          getOpcode(
              requirementDimensionValueWithTypeAndOp.substring(PREFIX_STR_COMPARISON.length()));
      String requirementComparisonValue =
          requirementDimensionValueWithTypeAndOp.substring(
              PREFIX_STR_COMPARISON.length() + op.length());
      compareResult = actualDimensionValue.compareTo(requirementComparisonValue);
    } else if (requirementDimensionValueWithTypeAndOp.startsWith(PREFIX_INT_COMPARISON)) {
      try {
        op =
            getOpcode(
                requirementDimensionValueWithTypeAndOp.substring(PREFIX_INT_COMPARISON.length()));
        Integer requirementComparisonValue =
            Integer.valueOf(
                requirementDimensionValueWithTypeAndOp.substring(
                    PREFIX_INT_COMPARISON.length() + op.length()));
        compareResult = Integer.valueOf(actualDimensionValue).compareTo(requirementComparisonValue);
      } catch (NumberFormatException e) {
        return false;
      }
    } else {
      return false;
    }

    switch (op) {
      case "<":
        return compareResult < 0;
      case "<=":
        return compareResult <= 0;
      case ">":
        return compareResult > 0;
      case ">=":
        return compareResult >= 0;
      default: // fall out
    }
    return false;
  }

  private static String getOpcode(String requirementDimensionValueWithOp) {
    if (requirementDimensionValueWithOp.length() > 0) {
      if (requirementDimensionValueWithOp.charAt(0) == '<') {
        if (requirementDimensionValueWithOp.length() > 1
            && requirementDimensionValueWithOp.charAt(1) == '=') {
          return "<=";
        } else {
          return "<";
        }
      } else if (requirementDimensionValueWithOp.charAt(0) == '>') {
        if (requirementDimensionValueWithOp.length() > 1
            && requirementDimensionValueWithOp.charAt(1) == '=') {
          return ">=";
        } else {
          return ">";
        }
      }
    }
    return "";
  }

  private ValueComparator() {}
}
