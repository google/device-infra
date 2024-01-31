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

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.config.proto.ConfigurationDescription.Metadata;
import com.android.tradefed.invoker.proto.InvocationContext.Context;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth8;
import com.google.inject.Guice;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TestRecordHelperTest {

  @Inject private TestRecordHelper testRecordHelper;

  @Before
  public void setUp() {
    Guice.createInjector(new TestModule()).injectMembers(this);
  }

  @Test
  public void mergeTestRecords_success() throws Exception {
    Optional<TestRecord> res =
        testRecordHelper.mergeTestRecords(
            ImmutableList.of(
                TestRecordHelperTestData.TEST_RECORD_1, TestRecordHelperTestData.TEST_RECORD_2));

    Truth8.assertThat(res).isPresent();

    TestRecord testRecord = res.get();

    Context context = testRecord.getDescription().unpack(Context.class);
    assertThat(context.getMetadataList())
        .containsExactly(
            Metadata.newBuilder().setKey("invocation-id").addValue("1").build(),
            Metadata.newBuilder()
                .setKey("command_line_args")
                .addValue(
                    "cts --enable-token-sharding --max-testcase-run-count 2 --retry-strategy "
                        + "RETRY_ANY_FAILURE")
                .build());

    assertThat(testRecord.getChildrenCount()).isEqualTo(3);

    // Asserts 1st module child
    assertThat(testRecord.getChildren(0).getInlineTestRecord().getTestRecordId())
        .isEqualTo(TestRecordHelperTestData.MODULE_1_INLINE_TEST_RECORD.getTestRecordId());
    assertThat(testRecord.getChildren(0).getInlineTestRecord().getChildrenCount()).isEqualTo(1);
    assertThat(
            testRecord
                .getChildren(0)
                .getInlineTestRecord()
                .getChildren(0)
                .getInlineTestRecord()
                .getChildrenCount())
        .isEqualTo(4);
    assertThat(
            testRecord
                .getChildren(0)
                .getInlineTestRecord()
                .getChildren(0)
                .getInlineTestRecord()
                .getNumExpectedChildren())
        .isEqualTo(4);

    // Asserts 2nd module child
    assertThat(testRecord.getChildren(1).getInlineTestRecord().getTestRecordId())
        .isEqualTo(TestRecordHelperTestData.MODULE_2_INLINE_TEST_RECORD.getTestRecordId());
    assertThat(testRecord.getChildren(1).getInlineTestRecord().getChildrenCount()).isEqualTo(1);
    assertThat(
            testRecord
                .getChildren(1)
                .getInlineTestRecord()
                .getChildren(0)
                .getInlineTestRecord()
                .getChildrenCount())
        .isEqualTo(2);
    assertThat(
            testRecord
                .getChildren(1)
                .getInlineTestRecord()
                .getChildren(0)
                .getInlineTestRecord()
                .getNumExpectedChildren())
        .isEqualTo(2);

    // Asserts 3rd module child
    assertThat(testRecord.getChildren(2).getInlineTestRecord().getTestRecordId())
        .isEqualTo(TestRecordHelperTestData.MODULE_3_INLINE_TEST_RECORD.getTestRecordId());
    assertThat(testRecord.getChildren(2).getInlineTestRecord().getChildrenCount()).isEqualTo(1);
    assertThat(
            testRecord
                .getChildren(2)
                .getInlineTestRecord()
                .getChildren(0)
                .getInlineTestRecord()
                .getChildrenCount())
        .isEqualTo(3);
    assertThat(
            testRecord
                .getChildren(2)
                .getInlineTestRecord()
                .getChildren(0)
                .getInlineTestRecord()
                .getNumExpectedChildren())
        .isEqualTo(3);
  }
}
