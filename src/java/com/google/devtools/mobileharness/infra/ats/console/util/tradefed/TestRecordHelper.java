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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.stream.Collectors.joining;

import com.android.tradefed.config.proto.ConfigurationDescription.Metadata;
import com.android.tradefed.invoker.proto.InvocationContext.Context;
import com.android.tradefed.result.proto.TestRecordProto.ChildReference;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.android.tradefed.result.proto.TestRecordProto.TestStatus;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

/** Helper class for {@link TestRecord}. */
public class TestRecordHelper {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final long PARSE_TIMEOUT_IN_HOUR = 1;

  private static final String CMD_LINE_ARGS_KEY = "command_line_args";

  private final ListeningExecutorService threadPool;
  private final LocalFileUtil localFileUtil;

  @Inject
  TestRecordHelper(ListeningExecutorService threadPool, LocalFileUtil localFileUtil) {
    this.threadPool = threadPool;
    this.localFileUtil = localFileUtil;
  }

  /**
   * Merges a list of {@code TestRecord} proto files into one.
   *
   * <p>All test cases belonging to the same module will be merged into one module, and currently it
   * assumes there are no duplicated results for the same test case.
   */
  public Optional<TestRecord> mergeTestRecordProtoFiles(List<Path> testRecordProtoFiles)
      throws MobileHarnessException, InterruptedException {
    return mergeTestRecords(parseTestRecordProtoFiles(testRecordProtoFiles));
  }

  /**
   * Merges a list of {@code TestRecord}s into one.
   *
   * <p>All test cases belonging to the same module will be merged into one module, and currently it
   * assumes there are no duplicated results for the same test case.
   */
  public Optional<TestRecord> mergeTestRecords(List<TestRecord> testRecords)
      throws MobileHarnessException {
    if (testRecords.isEmpty()) {
      return Optional.empty();
    }

    Context.Builder contextBuilder = null;
    try {
      contextBuilder = testRecords.get(0).getDescription().unpack(Context.class).toBuilder();
    } catch (InvalidProtocolBufferException e) {
      throw new MobileHarnessException(
          ExtErrorId.TEST_RECORD_INVALID_PROTO_CONTEXT, e.getMessage(), e);
    }

    List<Metadata> newMetadataList = new ArrayList<>();
    for (Metadata metadata : contextBuilder.getMetadataList()) {
      if (metadata.getKey().equals(CMD_LINE_ARGS_KEY)) {
        String commandLineArgs = metadata.getValueCount() > 0 ? metadata.getValue(0).trim() : "";
        if (!commandLineArgs.isEmpty()) {
          String configName =
              Splitter.on(' ').trimResults().omitEmptyStrings().splitToList(commandLineArgs).get(0);
          newMetadataList.add(
              Metadata.newBuilder()
                  .setKey(CMD_LINE_ARGS_KEY)
                  .addValue(
                      String.format(
                          "%s --enable-token-sharding --max-testcase-run-count 2"
                              + " --retry-strategy RETRY_ANY_FAILURE",
                          configName))
                  .build());
        } else {
          newMetadataList.add(metadata);
        }
      } else {
        newMetadataList.add(metadata);
      }
    }
    contextBuilder.clearMetadata().addAllMetadata(newMetadataList);

    TestRecord.Builder testRecordBuilder =
        TestRecord.newBuilder()
            .setTestRecordId(testRecords.get(0).getTestRecordId())
            .setStatus(TestStatus.PASS)
            .setDescription(Any.pack(contextBuilder.build()));

    Map<String, ChildReference> moduleChildReferences = new LinkedHashMap<>();
    for (TestRecord testRecord : testRecords) {
      ImmutableList<ChildReference> moduleChildReferenceList =
          testRecord.getChildrenList().stream()
              .filter(ChildReference::hasInlineTestRecord)
              .collect(toImmutableList());
      for (ChildReference moduleChild : moduleChildReferenceList) {
        // Test record id here is the unique module name, like "arm64-v8a CtsDeqpTestCases"
        String testRecordId = moduleChild.getInlineTestRecord().getTestRecordId();
        if (moduleChildReferences.containsKey(testRecordId)) {
          ChildReference existingModuleChild = moduleChildReferences.get(testRecordId);
          Optional<ChildReference> mergedModuleChild =
              mergeSameModuleChildReferences(existingModuleChild, moduleChild);
          if (mergedModuleChild.isPresent()) {
            moduleChildReferences.put(testRecordId, mergedModuleChild.get());
          }
        } else {
          moduleChildReferences.put(testRecordId, moduleChild);
        }
      }
    }

    moduleChildReferences.values().forEach(testRecordBuilder::addChildren);

    return Optional.of(testRecordBuilder.build());
  }

  private Optional<ChildReference> mergeSameModuleChildReferences(
      ChildReference moduleChild1, ChildReference moduleChild2) {
    if (moduleChild1.hasInlineTestRecord() && moduleChild2.hasInlineTestRecord()) {
      TestRecord testRecord1 = moduleChild1.getInlineTestRecord();
      TestRecord testRecord2 = moduleChild2.getInlineTestRecord();
      TestRecord.Builder mergedTestRecord =
          TestRecord.newBuilder()
              .setTestRecordId(testRecord1.getTestRecordId())
              .setParentTestRecordId(testRecord1.getParentTestRecordId())
              .setDescription(testRecord1.getDescription());
      TestStatus testStatus1 = testRecord1.getStatus();
      TestStatus testStatus2 = testRecord2.getStatus();
      if (testStatus1.equals(TestStatus.PASS) && testStatus2.equals(TestStatus.PASS)) {
        mergedTestRecord.setStatus(TestStatus.PASS);
      } else if (!testStatus1.equals(TestStatus.PASS)) {
        mergedTestRecord.setStatus(testStatus1);
      } else {
        mergedTestRecord.setStatus(testStatus2);
      }

      List<ChildReference> testRecord1ChildrenList = testRecord1.getChildrenList();
      List<ChildReference> testRecord2ChildrenList = testRecord2.getChildrenList();
      if (testRecord1ChildrenList.size() != 1 || testRecord2ChildrenList.size() != 1) {
        logger.atInfo().log(
            "One of the inline test records doesn't have exactly one ChildReference. Skip merging"
                + " child references.");
        return Optional.empty();
      }
      ChildReference testRecord1Child = testRecord1ChildrenList.get(0);
      ChildReference testRecord2Child = testRecord2ChildrenList.get(0);
      mergedTestRecord.addChildren(
          mergeTestCaseLevelChildReferences(testRecord1Child, testRecord2Child));
      return Optional.of(ChildReference.newBuilder().setInlineTestRecord(mergedTestRecord).build());
    } else if (moduleChild1.hasInlineTestRecord()) {
      return Optional.of(moduleChild1);
    }
    return Optional.of(moduleChild2);
  }

  private ChildReference mergeTestCaseLevelChildReferences(
      ChildReference child1, ChildReference child2) {
    if (child1.hasInlineTestRecord() && child2.hasInlineTestRecord()) {
      TestRecord.Builder testCasesGroup = TestRecord.newBuilder();
      TestRecord testRecord1 = child1.getInlineTestRecord();
      TestRecord testRecord2 = child2.getInlineTestRecord();
      testCasesGroup.setTestRecordId(testRecord1.getTestRecordId());
      testCasesGroup.setParentTestRecordId(testRecord1.getParentTestRecordId());
      testCasesGroup
          .addAllChildren(testRecord1.getChildrenList())
          .addAllChildren(testRecord2.getChildrenList());
      testCasesGroup.setNumExpectedChildren(
          testRecord1.getChildrenCount() + testRecord2.getChildrenCount());
      TestStatus testStatus1 = testRecord1.getStatus();
      TestStatus testStatus2 = testRecord2.getStatus();
      if (testStatus1.equals(TestStatus.PASS) && testStatus2.equals(TestStatus.PASS)) {
        testCasesGroup.setStatus(TestStatus.PASS);
      } else if (!testStatus1.equals(TestStatus.PASS)) {
        testCasesGroup.setStatus(testStatus1);
      } else {
        testCasesGroup.setStatus(testStatus2);
      }
      return ChildReference.newBuilder().setInlineTestRecord(testCasesGroup).build();
    } else if (child1.hasInlineTestRecord()) {
      return child1;
    }
    return child2;
  }

  /** Parses multiple test record proto files syncly. */
  public List<TestRecord> parseTestRecordProtoFiles(List<Path> testRecordProtoFiles)
      throws MobileHarnessException, InterruptedException {
    if (testRecordProtoFiles.isEmpty()) {
      return ImmutableList.of();
    }
    List<Path> testRecordProtoFilesList = new ArrayList<>();
    for (Path testRecordProtoFile : testRecordProtoFiles) {
      if (localFileUtil.isFileExist(testRecordProtoFile)) {
        testRecordProtoFilesList.add(testRecordProtoFile);
      } else {
        logger.atInfo().log(
            "The test record proto file [%s] doesn't exist, skip it", testRecordProtoFile);
      }
    }

    try {
      return parseTestRecordProtoFilesAsync(testRecordProtoFilesList)
          .get(PARSE_TIMEOUT_IN_HOUR, HOURS);
    } catch (TimeoutException e) {
      throw new MobileHarnessException(
          ExtErrorId.TEST_RECORD_PARSE_PROTOS_TIMEOUT_ERROR,
          "Timeout while parsing test record proto files",
          e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof MobileHarnessException) {
        throw (MobileHarnessException) e.getCause();
      } else {
        throw new MobileHarnessException(
            ExtErrorId.TEST_RECORD_PARSE_PROTOS_GENERIC_ERROR,
            "Failed to parse test record proto files",
            e);
      }
    }
  }

  /** Parses multiple test record proto files asyncly. */
  private ListenableFuture<List<TestRecord>> parseTestRecordProtoFilesAsync(
      List<Path> testRecordProtoFiles) {
    List<ListenableFuture<TestRecord>> parseTestRecordFutures = new ArrayList<>();
    logger.atInfo().log(
        "Start to parse test record proto files:\n - %s",
        testRecordProtoFiles.stream().map(Path::toString).collect(joining(",\n - ")));
    for (Path testRecordProtoFile : testRecordProtoFiles) {
      parseTestRecordFutures.add(parseTestRecordProtoFileAsync(testRecordProtoFile));
    }
    return Futures.allAsList(parseTestRecordFutures);
  }

  private ListenableFuture<TestRecord> parseTestRecordProtoFileAsync(Path testRecordProtoFile) {
    return threadPool.submit(() -> parseTestRecordProtoFile(testRecordProtoFile));
  }

  private TestRecord parseTestRecordProtoFile(Path testRecordProtoFile)
      throws MobileHarnessException {
    try {
      return TestRecordProtoUtil.readFromFile(testRecordProtoFile.toFile());
    } catch (IOException e) {
      throw new MobileHarnessException(
          ExtErrorId.TEST_RECORD_READ_PROTO_FILE_ERROR,
          String.format("Failed to read test record proto file [%s]", testRecordProtoFile),
          e);
    }
  }
}
