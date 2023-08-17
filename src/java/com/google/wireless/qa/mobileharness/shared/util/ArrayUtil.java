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

package com.google.wireless.qa.mobileharness.shared.util;

/** Operations on arrays. Prefer to use {@code org.apache.commons.lang3.ArrayUtils} instead. */
public final class ArrayUtil {

  /** Joins multiple string array as a single string array. */
  public static String[] join(String[]... arrays) {
    if (arrays == null) {
      return new String[0];
    }
    int totalLenth = 0;
    for (String[] array : arrays) {
      if (array != null) {
        totalLenth += array.length;
      }
    }
    String[] result = new String[totalLenth];
    int i = 0;
    for (String[] array : arrays) {
      if (array != null) {
        for (String element : array) {
          result[i++] = element;
        }
      }
    }
    return result;
  }

  /** Adds a string to the head of the array. */
  public static String[] join(String headingStr, String[] tailingArray) {
    if (tailingArray == null) {
      return new String[] {headingStr};
    }
    String[] result = new String[1 + tailingArray.length];
    result[0] = headingStr;
    for (int j = 0; j < tailingArray.length; j++) {
      result[1 + j] = tailingArray[j];
    }
    return result;
  }

  /** Adds two strings to the head of the array. */
  public static String[] join(String headingStr1, String headingStr2, String[] tailingArray) {
    return join(new String[] {headingStr1, headingStr2}, tailingArray);
  }

  /** Adds 3 strings to the head of the array. */
  public static String[] join(
      String headingStr1, String headingStr2, String headingStr3, String[] tailingArray) {
    return join(new String[] {headingStr1, headingStr2, headingStr3}, tailingArray);
  }

  /** Adds 4 strings to the head of the array. */
  public static String[] join(
      String headingStr1,
      String headingStr2,
      String headingStr3,
      String headingStr4,
      String[] tailingArray) {
    return join(new String[] {headingStr1, headingStr2, headingStr3, headingStr4}, tailingArray);
  }

  /** Adds a string to the end of the array. */
  public static String[] join(String[] headingArray, String tailingStr) {
    if (headingArray == null) {
      return new String[] {tailingStr};
    }
    String[] result = new String[headingArray.length + 1];
    for (int i = 0; i < headingArray.length; i++) {
      result[i] = headingArray[i];
    }
    result[headingArray.length] = tailingStr;
    return result;
  }

  /** Adds 2 string to the end of the array. */
  public static String[] join(String[] headingArray, String tailingStr1, String tailingStr2) {
    return join(headingArray, new String[] {tailingStr1, tailingStr2});
  }

  /** Adds 3 string to the end of the array. */
  public static String[] join(
      String[] headingArray, String tailingStr1, String tailingStr2, String tailingStr3) {
    return join(headingArray, new String[] {tailingStr1, tailingStr2, tailingStr3});
  }

  /** Adds 4 string to the end of the array. */
  public static String[] join(
      String[] headingArray,
      String tailingStr1,
      String tailingStr2,
      String tailingStr3,
      String tailingStr4) {
    return join(headingArray, new String[] {tailingStr1, tailingStr2, tailingStr3, tailingStr4});
  }

  private ArrayUtil() {}
}
