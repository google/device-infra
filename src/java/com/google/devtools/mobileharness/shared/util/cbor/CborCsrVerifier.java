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

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Verifier for CBOR CSR. */
@Singleton
public class CborCsrVerifier {

  @Inject
  CborCsrVerifier() {}

  /**
   * Verifies that the CSR contains the expected challenge.
   *
   * @param csrBytes the CSR bytes to verify
   * @param expectedChallenge the expected challenge bytes
   * @throws MobileHarnessException if verification fails
   */
  public void verifyCsrChallenge(byte[] csrBytes, byte[] expectedChallenge)
      throws MobileHarnessException {
    List<DataItem> csrItems;
    try {
      csrItems = CborDecoder.decode(csrBytes);
    } catch (CborException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_CBOR_DECODE_ERROR,
          "Failed to decode CBOR for CSR",
          e);
    }
    if (csrItems.isEmpty() || !(csrItems.get(0) instanceof Array)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_CBOR_INVALID_STRUCTURE,
          "Invalid CSR CBOR structure, expected Array as first item");
    }
    Array csr = (Array) csrItems.get(0);
    if (csr.getDataItems().isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_CBOR_INVALID_STRUCTURE,
          "CSR CBOR Array is empty");
    }
    switch (csr.getDataItems().get(0).getMajorType()) {
      case ARRAY -> checkChallengeV1(csr, expectedChallenge);
      case UNSIGNED_INTEGER -> checkChallengeV2(csr, expectedChallenge);
      default ->
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_UNSUPPORTED_MAJOR_TYPE,
              "Unsupported CSR with first item major type: "
                  + csr.getDataItems().get(0).getMajorType());
    }
  }

  private void checkChallengeV1(Array csr, byte[] expectedChallenge) throws MobileHarnessException {
    if (csr.getDataItems().size() < 2 || !(csr.getDataItems().get(1) instanceof ByteString)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_CBOR_INVALID_STRUCTURE,
          "Invalid CSR CBOR structure for V1, expected ByteString at index 1");
    }
    ByteString challenge = (ByteString) csr.getDataItems().get(1);
    if (!Arrays.equals(challenge.getBytes(), expectedChallenge)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_CHALLENGE_MISMATCH,
          "Challenge mismatch in V1 CSR");
    }
  }

  private void checkChallengeV2(Array csr, byte[] expectedChallenge) throws MobileHarnessException {
    // Check the challenge embedded in the AuthenticatedRequest.
    if (csr.getDataItems().size() < 4
        || !(csr.getDataItems().get(0) instanceof UnsignedInteger)
        || !(csr.getDataItems().get(3) instanceof Array)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_CBOR_INVALID_STRUCTURE,
          "Invalid CSR CBOR structure for V2");
    }
    UnsignedInteger version = (UnsignedInteger) csr.getDataItems().get(0);
    if (version.getValue().intValue() != 1) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_UNSUPPORTED_VERSION,
          "Unsupported V2 CSR version: " + version.getValue().intValue());
    }
    Array signedData = (Array) csr.getDataItems().get(3);
    if (signedData.getDataItems().size() < 3
        || !(signedData.getDataItems().get(2) instanceof ByteString)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_CBOR_INVALID_STRUCTURE,
          "Invalid signedData structure in V2 CSR");
    }
    ByteString payloadBytes = (ByteString) signedData.getDataItems().get(2);
    List<DataItem> payloadItems;
    try {
      payloadItems = CborDecoder.decode(payloadBytes.getBytes());
    } catch (CborException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_CBOR_DECODE_ERROR,
          "Failed to decode payload in V2 CSR",
          e);
    }
    if (payloadItems.isEmpty() || !(payloadItems.get(0) instanceof Array)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_CBOR_INVALID_STRUCTURE,
          "Invalid payload structure in V2 CSR, expected Array");
    }
    Array payload = (Array) payloadItems.get(0);
    if (payload.getDataItems().isEmpty()
        || !(payload.getDataItems().get(0) instanceof ByteString)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_CBOR_INVALID_STRUCTURE,
          "Invalid payload content in V2 CSR, expected ByteString at index 0");
    }
    ByteString challenge = (ByteString) payload.getDataItems().get(0);
    if (!Arrays.equals(challenge.getBytes(), expectedChallenge)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_CHALLENGE_MISMATCH,
          "Challenge mismatch in V2 CSR");
    }
  }
}
