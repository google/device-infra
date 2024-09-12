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

package com.google.devtools.mobileharness.shared.util.database;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Lister for listing all table names in a database. */
public class TablesLister {

  /** Returns all table names in the given database. */
  public ImmutableList<String> listTables(DatabaseConnections connections)
      throws MobileHarnessException {
    try (Connection connection = connections.getConnection();
        ResultSet resultSet =
            connection
                .getMetaData()
                .getTables(
                    /* catalog= */ null,
                    /* schemaPattern= */ null,
                    /* tableNamePattern= */ null,
                    /* types= */ null)) {
      ImmutableList.Builder<String> result = ImmutableList.builder();
      while (resultSet.next()) {
        result.add(resultSet.getString("TABLE_NAME"));
      }
      return result.build();
    } catch (MobileHarnessException | SQLException e) {
      throw new MobileHarnessException(
          BasicErrorId.DATABASE_LIST_TABLES_ERROR, "Failed to list tables", e);
    }
  }
}
