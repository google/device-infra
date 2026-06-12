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

package com.google.devtools.mobileharness.shared.util.cbor;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.io.ByteArrayOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CborCsrVerifierTest {

  private CborCsrVerifier verifier;

  @Before
  public void setUp() {
    verifier = new CborCsrVerifier();
  }

  @Test
  public void verifyCsrChallenge_v1_success() throws Exception {
    byte[] challenge = new byte[] {1, 2, 3, 4};
    byte[] csrBytes = createV1Csr(challenge);

    verifier.verifyCsrChallenge(csrBytes, challenge);
  }

  @Test
  public void verifyCsrChallenge_v2_success() throws Exception {
    byte[] challenge = new byte[] {1, 2, 3, 4};
    byte[] csrBytes = createV2Csr(challenge);

    verifier.verifyCsrChallenge(csrBytes, challenge);
  }

  @Test
  public void verifyCsrChallenge_v1_challengeMismatch_throws() throws Exception {
    byte[] challenge = new byte[] {1, 2, 3, 4};
    byte[] expectedChallenge = new byte[] {1, 2, 3, 5};
    byte[] csrBytes = createV1Csr(challenge);

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () -> verifier.verifyCsrChallenge(csrBytes, expectedChallenge));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_CHALLENGE_MISMATCH);
  }

  @Test
  public void verifyCsrChallenge_v2_challengeMismatch_throws() throws Exception {
    byte[] challenge = new byte[] {1, 2, 3, 4};
    byte[] expectedChallenge = new byte[] {1, 2, 3, 5};
    byte[] csrBytes = createV2Csr(challenge);

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () -> verifier.verifyCsrChallenge(csrBytes, expectedChallenge));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_CHALLENGE_MISMATCH);
  }

  @Test
  public void verifyCsrChallenge_invalidCbor_throws() {
    byte[] challenge = new byte[] {1, 2, 3, 4};
    byte[] csrBytes = new byte[] {(byte) 0x81}; // Array of length 1, but missing content

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class, () -> verifier.verifyCsrChallenge(csrBytes, challenge));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_CBOR_DECODE_ERROR);
  }

  @Test
  public void verifyCsrChallenge_emptyCbor_throws() throws Exception {
    byte[] challenge = new byte[] {1, 2, 3, 4};
    // Decode empty list.
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    new CborEncoder(baos).encode(new CborBuilder().build());
    byte[] csrBytes = baos.toByteArray();

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class, () -> verifier.verifyCsrChallenge(csrBytes, challenge));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_CBOR_INVALID_STRUCTURE);
  }

  @Test
  public void verifyCsrChallenge_unsupportedVersion_throws() throws Exception {
    byte[] challenge = new byte[] {1, 2, 3, 4};
    // V2 with unsupported version 2
    ByteArrayOutputStream payloadBaos = new ByteArrayOutputStream();
    new CborEncoder(payloadBaos).encode(new CborBuilder().addArray().add(challenge).end().build());
    byte[] payloadBytes = payloadBaos.toByteArray();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    new CborEncoder(baos)
        .encode(
            new CborBuilder()
                .addArray()
                .add(2) // version 2
                .add("unused")
                .add("unused")
                .addArray()
                .add("unused")
                .add("unused")
                .add(payloadBytes)
                .end()
                .end()
                .build());
    byte[] csrBytes = baos.toByteArray();

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class, () -> verifier.verifyCsrChallenge(csrBytes, challenge));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_UNSUPPORTED_VERSION);
  }

  @Test
  public void verifyCsrChallenge_unsupportedMajorType_throws() throws Exception {
    byte[] challenge = new byte[] {1, 2, 3, 4};
    // CBOR that is not an array at the root but e.g. a map. Or array whose first element is not
    // Array/UnsignedInteger
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    new CborEncoder(baos)
        .encode(
            new CborBuilder()
                .addArray()
                .addMap() // index 0: Map (unsupported)
                .end()
                .end()
                .build());
    byte[] csrBytes = baos.toByteArray();

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class, () -> verifier.verifyCsrChallenge(csrBytes, challenge));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_UNSUPPORTED_MAJOR_TYPE);
  }

  private byte[] createV1Csr(byte[] challenge) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    new CborEncoder(baos)
        .encode(
            new CborBuilder()
                .addArray()
                .addArray()
                .end() // index 0: Array
                .add(challenge) // index 1: challenge (ByteString)
                .end()
                .build());
    return baos.toByteArray();
  }

  private byte[] createV2Csr(byte[] challenge) throws Exception {
    ByteArrayOutputStream payloadBaos = new ByteArrayOutputStream();
    new CborEncoder(payloadBaos).encode(new CborBuilder().addArray().add(challenge).end().build());
    byte[] payloadBytes = payloadBaos.toByteArray();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    new CborEncoder(baos)
        .encode(
            new CborBuilder()
                .addArray()
                .add(1) // index 0: version (UnsignedInteger)
                .add("unused")
                .add("unused")
                .addArray()
                .add("unused")
                .add("unused")
                .add(payloadBytes) // index 2: payload (ByteString)
                .end()
                .end()
                .build());
    return baos.toByteArray();
  }
}
