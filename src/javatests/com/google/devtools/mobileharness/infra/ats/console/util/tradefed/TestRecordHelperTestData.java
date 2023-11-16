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

package com.google.devtools.mobileharness.infra.ats.console.util.tradefed;

import com.android.tradefed.config.proto.ConfigurationDescription.Metadata;
import com.android.tradefed.invoker.proto.InvocationContext.Context;
import com.android.tradefed.result.proto.TestRecordProto.ChildReference;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.android.tradefed.result.proto.TestRecordProto.TestStatus;
import com.google.protobuf.Any;

/** Test data for TestRecordHelperTest */
final class TestRecordHelperTestData {

  private TestRecordHelperTestData() {}

  static final TestRecord MODULE_1_INLINE_TEST_RECORD =
      TestRecord.newBuilder()
          .setTestRecordId("arm64-v8a CtsAccelerationTestCases[instant]")
          .setParentTestRecordId("821172e7-9e85-4651-8d94-c41921f0a7d9")
          .addChildren(
              ChildReference.newBuilder()
                  .setInlineTestRecord(
                      TestRecord.newBuilder()
                          .setTestRecordId("arm64-v8a CtsAccelerationTestCases[instant]")
                          .setParentTestRecordId("arm64-v8a CtsAccelerationTestCases[instant]")
                          .addChildren(
                              ChildReference.newBuilder()
                                  .setInlineTestRecord(
                                      TestRecord.newBuilder()
                                          .setTestRecordId(
                                              "android.acceleration.cts.HardwareAccelerationTest#"
                                                  + "testIsHardwareAccelerated")
                                          .setParentTestRecordId(
                                              "arm64-v8a CtsAccelerationTestCases[instant]")
                                          .setStatus(TestStatus.PASS)))
                          .addChildren(
                              ChildReference.newBuilder()
                                  .setInlineTestRecord(
                                      TestRecord.newBuilder()
                                          .setTestRecordId(
                                              "android.acceleration.cts."
                                                  + "WindowFlagHardwareAccelerationTest"
                                                  + "#testNotAttachedView")
                                          .setParentTestRecordId(
                                              "arm64-v8a CtsAccelerationTestCases[instant]")
                                          .setStatus(TestStatus.PASS)))
                          .setNumExpectedChildren(2)
                          .setStatus(TestStatus.PASS)))
          .setStatus(TestStatus.PASS)
          .build();

  static final TestRecord MODULE_2_INLINE_TEST_RECORD =
      TestRecord.newBuilder()
          .setTestRecordId("arm64-v8a CtsAccelerationTestCases")
          .setParentTestRecordId("821172e7-9e85-4651-8d94-c41921f0a7d9")
          .addChildren(
              ChildReference.newBuilder()
                  .setInlineTestRecord(
                      TestRecord.newBuilder()
                          .setTestRecordId("arm64-v8a CtsAccelerationTestCases")
                          .setParentTestRecordId("arm64-v8a CtsAccelerationTestCases")
                          .addChildren(
                              ChildReference.newBuilder()
                                  .setInlineTestRecord(
                                      TestRecord.newBuilder()
                                          .setTestRecordId(
                                              "android.acceleration.cts.HardwareAccelerationTest#"
                                                  + "testNotAttachedView")
                                          .setParentTestRecordId(
                                              "arm64-v8a CtsAccelerationTestCases")
                                          .setStatus(TestStatus.PASS)))
                          .addChildren(
                              ChildReference.newBuilder()
                                  .setInlineTestRecord(
                                      TestRecord.newBuilder()
                                          .setTestRecordId(
                                              "android.acceleration.cts.SoftwareAccelerationTest#"
                                                  + "testIsHardwareAccelerated")
                                          .setParentTestRecordId(
                                              "arm64-v8a CtsAccelerationTestCases")
                                          .setStatus(TestStatus.PASS)))
                          .setNumExpectedChildren(2)
                          .setStatus(TestStatus.PASS)))
          .setStatus(TestStatus.PASS)
          .build();

  static final ChildReference MODULE_1_CHILD_REFERENCE =
      ChildReference.newBuilder().setInlineTestRecord(MODULE_1_INLINE_TEST_RECORD).build();

  static final ChildReference MODULE_2_CHILD_REFERENCE =
      ChildReference.newBuilder().setInlineTestRecord(MODULE_2_INLINE_TEST_RECORD).build();

  static final TestRecord TEST_RECORD_1 =
      TestRecord.newBuilder()
          .setTestRecordId("821172e7-9e85-4651-8d94-c41921f0a7d9")
          .addChildren(MODULE_1_CHILD_REFERENCE)
          .addChildren(MODULE_2_CHILD_REFERENCE)
          .setStatus(TestStatus.PASS)
          .setDescription(
              Any.pack(
                  Context.newBuilder()
                      .addMetadata(Metadata.newBuilder().setKey("invocation-id").addValue("1"))
                      .addMetadata(
                          Metadata.newBuilder()
                              .setKey("command_line_args")
                              .addValue("cts -m CtsAccelerationTestCases"))
                      .build()))
          .build();

  static final TestRecord MODULE_3_INLINE_TEST_RECORD =
      TestRecord.newBuilder()
          .setTestRecordId("arm64-v8a CtsBatterySavingTestCases")
          .setParentTestRecordId("45a4d39f-03bd-472e-b83f-11f07c5d2afe")
          .addChildren(
              ChildReference.newBuilder()
                  .setInlineTestRecord(
                      TestRecord.newBuilder()
                          .setTestRecordId("arm64-v8a CtsBatterySavingTestCases")
                          .setParentTestRecordId("arm64-v8a CtsBatterySavingTestCases")
                          .addChildren(
                              ChildReference.newBuilder()
                                  .setInlineTestRecord(
                                      TestRecord.newBuilder()
                                          .setTestRecordId(
                                              "android.os.cts.batterysaving.ActionChargingTest#"
                                                  + "testActionChargingDeferred_withGlobalSetting")
                                          .setParentTestRecordId(
                                              "arm64-v8a CtsBatterySavingTestCases")
                                          .setStatus(TestStatus.PASS)))
                          .addChildren(
                              ChildReference.newBuilder()
                                  .setInlineTestRecord(
                                      TestRecord.newBuilder()
                                          .setTestRecordId(
                                              "android.os.cts.batterysaving.ActionChargingTest#"
                                                  + "testSetChargingStateUpdateDelayMillis_"
                                                  + "noPermission")
                                          .setParentTestRecordId(
                                              "arm64-v8a CtsBatterySavingTestCases")
                                          .setStatus(TestStatus.PASS)))
                          .addChildren(
                              ChildReference.newBuilder()
                                  .setInlineTestRecord(
                                      TestRecord.newBuilder()
                                          .setTestRecordId(
                                              "android.os.cts.batterysaving.ActionChargingTest#"
                                                  + "testActionChargingDeferred_withApi")
                                          .setParentTestRecordId(
                                              "arm64-v8a CtsBatterySavingTestCases")
                                          .setStatus(TestStatus.PASS)))
                          .setNumExpectedChildren(3)
                          .setStatus(TestStatus.PASS)))
          .setStatus(TestStatus.PASS)
          .build();

  static final ChildReference MODULE_3_CHILD_REFERENCE =
      ChildReference.newBuilder().setInlineTestRecord(MODULE_3_INLINE_TEST_RECORD).build();

  static final TestRecord TEST_RECORD_2 =
      TestRecord.newBuilder()
          .setTestRecordId("45a4d39f-03bd-472e-b83f-11f07c5d2afe")
          .addChildren(MODULE_1_CHILD_REFERENCE)
          .addChildren(MODULE_3_CHILD_REFERENCE)
          .setStatus(TestStatus.PASS)
          .setDescription(
              Any.pack(
                  Context.newBuilder()
                      .addMetadata(Metadata.newBuilder().setKey("invocation-id").addValue("1"))
                      .addMetadata(
                          Metadata.newBuilder()
                              .setKey("command_line_args")
                              .addValue("cts -m CtsBatterySavingTestCases"))
                      .build()))
          .build();
}
