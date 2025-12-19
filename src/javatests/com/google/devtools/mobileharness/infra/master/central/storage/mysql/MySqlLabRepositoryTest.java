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

package com.google.devtools.mobileharness.infra.master.central.storage.mysql;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.master.central.proto.Lab.LabServerCondition;
import com.google.devtools.mobileharness.shared.storage.transaction.TransactionContext;
import java.sql.Connection;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class MySqlLabRepositoryTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private MySqlLabTableClient mySqlLabTableClient;
  @Mock private Connection connection;
  @Mock private TransactionContext nonMySqlTransactionContext;

  private MySqlLabRepository mySqlLabRepository;
  private MySqlTransactionContext mySqlTransactionContext;
  private final LabLocator labLocator =
      LabLocator.of(/* ip= */ "lab_ip", /* hostName= */ "lab_host");

  @Before
  public void setUp() {
    mySqlTransactionContext = new MySqlTransactionContext(connection);
    mySqlLabRepository = new MySqlLabRepository(mySqlLabTableClient);
  }

  @Test
  public void getLabServerCondition_success() throws MobileHarnessException {
    LabServerCondition expectedCondition =
        LabServerCondition.newBuilder().setIsMissing(false).build();
    when(mySqlLabTableClient.getLabServerCondition(labLocator, connection))
        .thenReturn(Optional.of(expectedCondition));

    Optional<LabServerCondition> actualCondition =
        mySqlLabRepository.getLabServerCondition(labLocator, mySqlTransactionContext);

    assertThat(actualCondition).hasValue(expectedCondition);
    verify(mySqlLabTableClient).getLabServerCondition(labLocator, connection);
  }

  @Test
  public void getLabServerCondition_empty() throws MobileHarnessException {
    when(mySqlLabTableClient.getLabServerCondition(labLocator, connection))
        .thenReturn(Optional.empty());

    Optional<LabServerCondition> actualCondition =
        mySqlLabRepository.getLabServerCondition(labLocator, mySqlTransactionContext);

    assertThat(actualCondition).isEmpty();
    verify(mySqlLabTableClient).getLabServerCondition(labLocator, connection);
  }

  @Test
  public void getLabServerCondition_invalidContextType_throwsException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> mySqlLabRepository.getLabServerCondition(labLocator, nonMySqlTransactionContext));
    assertThat(exception).hasMessageThat().contains("Expected MySqlTransactionContext");
  }

  @Test
  public void updateLabServerCondition_success() throws MobileHarnessException {
    mySqlLabRepository.updateLabServerCondition(labLocator, true, mySqlTransactionContext);

    verify(mySqlLabTableClient).updateLabServerCondition(labLocator, true, connection);
  }

  @Test
  public void updateLabServerCondition_invalidContextType_throwsException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                mySqlLabRepository.updateLabServerCondition(
                    labLocator, false, nonMySqlTransactionContext));
    assertThat(exception).hasMessageThat().contains("Expected MySqlTransactionContext");
  }
}
