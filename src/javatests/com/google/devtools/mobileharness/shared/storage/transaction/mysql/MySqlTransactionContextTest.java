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

package com.google.devtools.mobileharness.shared.storage.transaction.mysql;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import java.sql.Connection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MySqlTransactionContextTest {

  @Test
  public void getConnection_returnsConnection() {
    Connection mockConnection = mock(Connection.class);
    MySqlTransactionContext context = new MySqlTransactionContext(mockConnection);

    assertThat(context.getConnection()).isSameInstanceAs(mockConnection);
  }
}
