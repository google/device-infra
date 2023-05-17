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

package com.google.devtools.mobileharness.shared.util.base;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.List;

/** Helper class to display a matrix of string elements so each element column is lined up. */
public class TableFormatter {

  private static final int COLUMN_SPACING = 2;

  /**
   * Display given string elements as a table with aligned columns.
   *
   * @param table a matrix of String elements. Rows can be different length
   */
  public static String displayTable(List<? extends List<String>> table) {
    checkArgument(!table.isEmpty());
    StringBuilder result = new StringBuilder();
    List<Integer> columnSizes = calculateColumnSizes(table);
    for (int rowIndex = 0; rowIndex < table.size(); rowIndex++) {
      List<String> row = table.get(rowIndex);
      for (int columnIndex = 0; columnIndex < row.size(); columnIndex++) {
        String column = row.get(columnIndex);
        result.append(column);
        if (columnIndex < row.size() - 1) {
          result.append(" ".repeat(columnSizes.get(columnIndex) - column.length()));
        }
      }
      if (rowIndex < table.size() - 1) {
        result.append('\n');
      }
    }
    return result.toString();
  }

  private static List<Integer> calculateColumnSizes(List<? extends List<String>> table) {
    List<Integer> columnSizes = new ArrayList<>();
    for (List<String> row : table) {
      for (int columnIndex = 0; columnIndex < row.size(); columnIndex++) {
        if (columnIndex >= columnSizes.size()) {
          columnSizes.add(columnIndex, 0);
        }
        int columnSize = row.get(columnIndex).length() + COLUMN_SPACING;
        if (columnSizes.get(columnIndex) < columnSize) {
          columnSizes.set(columnIndex, columnSize);
        }
      }
    }
    return columnSizes;
  }

  private TableFormatter() {}
}
