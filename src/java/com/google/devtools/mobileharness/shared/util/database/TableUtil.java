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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.base.ProtoExtensionRegistry;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Utility for operating database tables. */
public class TableUtil {

  private TableUtil() {}

  /** Pattern of a legal table name / column name. */
  private static final Pattern TABLE_COLUMN_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

  /** Utility for operating a table whose key is a string and whose value is a binary proto. */
  public static class StringBinaryProtoTable<T extends Message> {

    private final DatabaseConnections databaseConnections;
    private final String tableName;
    private final String keyColumnName;
    private final String valueColumnName;
    private final Parser<T> parser;

    public StringBinaryProtoTable(
        DatabaseConnections databaseConnections,
        Parser<T> parser,
        String tableName,
        String keyColumnName,
        String valueColumnName) {
      this.databaseConnections = checkNotNull(databaseConnections);
      this.parser = checkNotNull(parser);
      this.tableName = validateName(tableName);
      this.keyColumnName = validateName(keyColumnName);
      this.valueColumnName = validateName(valueColumnName);
    }

    /** Inserts a value to the table or updates an existing one. */
    public void update(String key, T value) throws MobileHarnessException {
      checkArgument(!Strings.isNullOrEmpty(key));
      checkArgument(value != null);

      // Creates prepared statement.
      try (Connection connection = databaseConnections.getConnection();
          PreparedStatement preparedStatement =
              connection.prepareStatement(
                  String.format(
                      "INSERT INTO %s (%s, %s) VALUES (?, ?) "
                          + "ON DUPLICATE KEY UPDATE %s = VALUES(%s)",
                      tableName,
                      keyColumnName,
                      valueColumnName,
                      valueColumnName,
                      valueColumnName))) {

        // Sets parameters.
        preparedStatement.setString(1, key);
        preparedStatement.setBytes(2, value.toByteArray());

        // Executes update.
        preparedStatement.executeUpdate();
      } catch (MobileHarnessException | SQLException e) {
        throw new MobileHarnessException(
            BasicErrorId.DATABASE_TABLE_UPDATE_ERROR,
            String.format(
                "Failed to update proto to database table [%s], key=[%s], value=[%s]",
                tableName, key, shortDebugString(value)),
            e);
      }
    }

    /** Deletes a key from the table if exists. */
    public void delete(String key) throws MobileHarnessException {
      try (Connection connection = databaseConnections.getConnection();
          PreparedStatement preparedStatement =
              connection.prepareStatement(
                  String.format("DELETE FROM %s WHERE %s = ?", tableName, keyColumnName))) {
        preparedStatement.setString(1, key);
        preparedStatement.executeUpdate();
      } catch (MobileHarnessException | SQLException e) {
        throw new MobileHarnessException(
            BasicErrorId.DATABASE_TABLE_DELETE_ERROR,
            String.format(
                "Failed to delete proto from database table [%s], key=[%s]", tableName, key),
            e);
      }
    }

    /** Reads all keys and values from the table. */
    public ImmutableList<StringBinaryProtoOrError> read() throws MobileHarnessException {
      // Queries from table.
      try (Connection connection = databaseConnections.getConnection();
          ResultSet resultSet =
              connection
                  .prepareStatement(
                      String.format(
                          "SELECT %s, %s FROM %s", keyColumnName, valueColumnName, tableName))
                  .executeQuery()) {
        ImmutableList.Builder<StringBinaryProtoOrError> result = ImmutableList.builder();
        while (resultSet.next()) {
          // Reads a row.
          String key;
          InputStream valueStream;
          try {
            key = resultSet.getString(keyColumnName);
            valueStream = resultSet.getBinaryStream(valueColumnName);
          } catch (SQLException e) {
            result.add(StringBinaryProtoOrError.of(/* key= */ null, /* value= */ null, e));
            continue;
          }

          // Parses proto.
          T value;
          try {
            value = parser.parseFrom(valueStream, ProtoExtensionRegistry.getGeneratedRegistry());
          } catch (IOException e) {
            result.add(
                StringBinaryProtoOrError.of(
                    /* key= */ null,
                    /* value= */ null,
                    new MobileHarnessException(
                        BasicErrorId.DATABASE_TABLE_READ_PROTO_PARSE_ERROR,
                        String.format(
                            "Failed to parse proto from database table [%s], key=[%s]",
                            tableName, key),
                        e)));
            continue;
          }

          // Adds to result.
          result.add(StringBinaryProtoOrError.of(key, value, /* error= */ null));
        }
        return result.build();
      } catch (MobileHarnessException | SQLException e) {
        throw new MobileHarnessException(
            BasicErrorId.DATABASE_TABLE_READ_ERROR,
            String.format("Failed to read protos from database table [%s]", tableName),
            e);
      }
    }
  }

  @CanIgnoreReturnValue
  private static String validateName(String tableColumnName) {
    checkArgument(tableColumnName != null);
    checkArgument(TABLE_COLUMN_NAME_PATTERN.matcher(tableColumnName).matches());
    return tableColumnName;
  }

  /**
   * A key-value pair or an error. Either "key is present, value is present, error is empty", or
   * "error is present, key is empty, value is empty".
   */
  @AutoValue
  public abstract static class StringBinaryProtoOrError {

    public abstract Optional<String> key();

    public abstract Optional<Message> value();

    public abstract Optional<Throwable> error();

    public static StringBinaryProtoOrError of(
        @Nullable String key, @Nullable Message value, @Nullable Throwable error) {
      checkArgument(
          (key != null && value != null && error == null)
              || (key == null && value == null && error != null));
      return new AutoValue_TableUtil_StringBinaryProtoOrError(
          Optional.ofNullable(key), Optional.ofNullable(value), Optional.ofNullable(error));
    }
  }
}
