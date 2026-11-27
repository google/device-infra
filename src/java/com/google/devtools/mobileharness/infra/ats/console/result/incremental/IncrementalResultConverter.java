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

package com.google.devtools.mobileharness.infra.ats.console.result.incremental;

import static com.google.common.primitives.Booleans.falseFirst;
import static java.util.Comparator.comparing;

import com.android.tradefed.build.proto.BuildInformation;
import com.android.tradefed.config.proto.ConfigurationDescription.Metadata;
import com.android.tradefed.invoker.proto.InvocationContext.Context;
import com.android.tradefed.result.proto.TestRecordProto.ChildReference;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Attribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.BuildInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Summary;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CertificationSuiteInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CertificationSuiteInfoFactory;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportUtil;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlConstants;
import com.google.devtools.mobileharness.infra.ats.console.util.tradefed.TestRecordProtoUtil;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommon;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Converts Tradefed incremental proto store files into console report models. */
public class IncrementalResultConverter {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String PB_FILE_SUFFIX = ".pb";
  private static final String CTS_BUILD_ATTR_PREFIX = "cts:";
  private static final String BUILD_SERIAL_ATTR = "build_serial";
  private static final String SERIAL_KEY = "serial";

  // Module metadata key prefix serialized by Tradefed's ModuleDefinition.
  private static final String MODULE_ATTR_PREFIX = "module-";

  // Uppercase suite build attribute keys serialized by Tradefed's CompatibilityBuildHelper.
  private static final String SUITE_NAME_BUILD_KEY = Ascii.toUpperCase(SuiteCommon.SUITE_NAME);
  private static final String SUITE_VARIANT_BUILD_KEY =
      Ascii.toUpperCase(SuiteCommon.SUITE_VARIANT);
  private static final String SUITE_VERSION_BUILD_KEY =
      Ascii.toUpperCase(SuiteCommon.SUITE_VERSION);
  private static final String SUITE_PLAN_BUILD_KEY = Ascii.toUpperCase(SuiteCommon.SUITE_PLAN);
  private static final String SUITE_BUILD_NUMBER_BUILD_KEY =
      Ascii.toUpperCase(XmlConstants.SUITE_BUILD_ATTR);
  private static final String SUITE_BUILD_SHORT_KEY = Ascii.toUpperCase(SuiteCommon.SUITE_BUILD);
  private static final String SUITE_REPORT_VERSION_BUILD_KEY =
      Ascii.toUpperCase(SuiteCommon.SUITE_REPORT_VERSION);

  private static final ZoneId UTC_DISPLAY_ZONE = ZoneId.of("Etc/UTC");
  private static final DateTimeFormatter DISPLAY_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz uuuu", Locale.US);

  private static final Comparator<Path> MODULE_FILE_COMPARATOR =
      comparing(FileSortKey::fromPath, FileSortKey.COMPARATOR);

  private final HostEnvironment hostEnv;

  @Inject
  IncrementalResultConverter() {
    this(HostEnvironment.system());
  }

  @VisibleForTesting
  IncrementalResultConverter(HostEnvironment hostEnv) {
    this.hostEnv = hostEnv;
  }

  /**
   * Converts an incremental result store directory (either a session store containing per-job
   * subdirectories or a single-job store directory) into a compatibility {@link Result}, requiring
   * non-empty {@code planned-modules} metadata on every non-empty job store and device build
   * fingerprint metadata in {@link BuildInfo}.
   */
  public Optional<Result> convert(Path storeDir) throws MobileHarnessException {
    return convert(storeDir, /* skipDeviceInfo= */ false);
  }

  /**
   * Converts an incremental result store directory (either a session store containing per-job
   * subdirectories or a single-job store directory) into a compatibility {@link Result}, requiring
   * non-empty {@code planned-modules} metadata on every non-empty job store.
   *
   * @param skipDeviceInfo if {@code false}, returns {@link Optional#empty()} when no device build
   *     fingerprint could be recovered into {@link BuildInfo}
   */
  public Optional<Result> convert(Path storeDir, boolean skipDeviceInfo)
      throws MobileHarnessException {
    if (!Files.isDirectory(storeDir)) {
      return Optional.empty();
    }

    List<Path> subdirs = new ArrayList<>();
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(storeDir)) {
      for (Path entry : entries) {
        if (Files.isDirectory(entry)) {
          subdirs.add(entry);
        }
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          ExtErrorId.TEST_RECORD_READ_PROTO_FILE_ERROR,
          String.format("Failed to list incremental store directory [%s]", storeDir),
          e);
    }
    subdirs.sort(comparing(path -> path.getFileName().toString()));

    SessionAccumulator session = new SessionAccumulator();
    JobStoreStatus rootStatus = foldJobStore(storeDir, session);
    if (rootStatus == JobStoreStatus.INVALID) {
      return Optional.empty();
    }
    boolean hasValidJobStore = rootStatus == JobStoreStatus.VALID;
    for (Path subdir : subdirs) {
      JobStoreStatus subdirStatus = foldJobStore(subdir, session);
      if (subdirStatus == JobStoreStatus.INVALID) {
        return Optional.empty();
      }
      hasValidJobStore |= subdirStatus == JobStoreStatus.VALID;
    }
    return hasValidJobStore
        ? session.buildResult(hostEnv, skipDeviceInfo, storeDir)
        : Optional.empty();
  }

  /**
   * Parses {@code invocation.pb} from a single Tradefed job's incremental store directory, if
   * present and readable.
   */
  public Optional<InvocationPlan> parseInvocation(Path jobStoreDir) {
    Path invocationFile = jobStoreDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME);
    if (!Files.isRegularFile(invocationFile)) {
      return Optional.empty();
    }

    Optional<TestRecord> recordOpt = tryReadTestRecord(invocationFile);
    if (recordOpt.isEmpty()) {
      return Optional.empty();
    }
    Context context = unpackContext(recordOpt.get(), invocationFile);
    ImmutableMap<String, Integer> plannedCountsByModule =
        parsePlannedModules(context, invocationFile);
    return Optional.of(new InvocationPlan(context, plannedCountsByModule));
  }

  /**
   * Parsed invocation context and planned unit counts per module ID.
   *
   * @param context unpacked Tradefed invocation {@link Context}
   * @param plannedCountsByModule planned unit count keyed by module ID in execution plan order
   */
  public record InvocationPlan(
      Context context, ImmutableMap<String, Integer> plannedCountsByModule) {
    public InvocationPlan {
      Objects.requireNonNull(context);
      Objects.requireNonNull(plannedCountsByModule);
    }
  }

  private enum JobStoreStatus {
    EMPTY,
    VALID,
    INVALID
  }

  private static JobStoreStatus foldJobStore(Path jobStoreDir, SessionAccumulator session)
      throws MobileHarnessException {
    JobStoreFiles files = scanJobStoreFiles(jobStoreDir);
    if (files.isEmpty()) {
      return JobStoreStatus.EMPTY;
    }

    if (files.invocationFile() == null) {
      logger.atWarning().log(
          "Rejecting incremental job store [%s]: missing %s",
          jobStoreDir, XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME);
      return JobStoreStatus.INVALID;
    }
    Optional<TestRecord> invocationRecordOpt = tryReadTestRecord(files.invocationFile());
    if (invocationRecordOpt.isEmpty()) {
      return JobStoreStatus.INVALID;
    }
    TestRecord invocationRecord = invocationRecordOpt.get();
    Context invocationContext = unpackContext(invocationRecord, files.invocationFile());
    ImmutableMap<String, Integer> plannedCountsByModule =
        parsePlannedModules(invocationContext, files.invocationFile());
    if (plannedCountsByModule.isEmpty()) {
      logger.atWarning().log(
          "Rejecting incremental job store [%s]: missing or empty %s in [%s]",
          jobStoreDir, XtsConstants.PLANNED_MODULES_ATTR, files.invocationFile());
      return JobStoreStatus.INVALID;
    }
    session.observeInvocation(invocationRecord, invocationContext);

    Map<String, ModuleAccumulator> jobModules = new LinkedHashMap<>();
    for (Path moduleFile : files.moduleFiles()) {
      Optional<TestRecord> moduleRecordOpt = tryReadTestRecord(moduleFile);
      if (moduleRecordOpt.isEmpty()) {
        continue;
      }
      TestRecord moduleRecord = moduleRecordOpt.get();
      Context moduleContext = unpackContext(moduleRecord, moduleFile);
      String moduleId = ModuleAccumulator.resolveModuleId(moduleRecord, moduleContext);
      if (moduleId.isEmpty()) {
        logger.atWarning().log("Skipping module record with empty ID in [%s]", moduleFile);
        continue;
      }
      session.observeModule(moduleRecord, moduleContext);
      jobModules
          .computeIfAbsent(moduleId, ModuleAccumulator::new)
          .foldModuleRecord(moduleRecord, moduleContext);
    }

    if (files.completeFile() != null) {
      Optional<TestRecord> completeRecordOpt = tryReadTestRecord(files.completeFile());
      if (completeRecordOpt.isPresent()) {
        TestRecord completeRecord = completeRecordOpt.get();
        Context completeContext = unpackContext(completeRecord, files.completeFile());
        session.observeComplete(completeRecord, completeContext);
      }
    }

    session.foldJobModules(plannedCountsByModule, jobModules);
    return JobStoreStatus.VALID;
  }

  private static JobStoreFiles scanJobStoreFiles(Path jobStoreDir) throws MobileHarnessException {
    Path invocationFile = null;
    Path completeFile = null;
    List<Path> moduleFiles = new ArrayList<>();
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(jobStoreDir)) {
      for (Path entry : entries) {
        if (!Files.isRegularFile(entry)) {
          continue;
        }
        String fileName = entry.getFileName().toString();
        if (!fileName.endsWith(PB_FILE_SUFFIX)
            || fileName.equals(XtsConstants.INCREMENTAL_BASELINE_FILE_NAME)) {
          continue;
        }
        if (fileName.equals(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME)) {
          invocationFile = entry;
        } else if (fileName.equals(XtsConstants.INCREMENTAL_COMPLETE_FILE_NAME)) {
          completeFile = entry;
        } else {
          moduleFiles.add(entry);
        }
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          ExtErrorId.TEST_RECORD_READ_PROTO_FILE_ERROR,
          String.format("Failed to list job incremental store directory [%s]", jobStoreDir),
          e);
    }
    moduleFiles.sort(MODULE_FILE_COMPARATOR);
    return new JobStoreFiles(invocationFile, completeFile, ImmutableList.copyOf(moduleFiles));
  }

  private record JobStoreFiles(
      @Nullable Path invocationFile, @Nullable Path completeFile, ImmutableList<Path> moduleFiles) {
    boolean isEmpty() {
      return invocationFile == null && completeFile == null && moduleFiles.isEmpty();
    }
  }

  private static BuildInfo buildBuildInfo(
      Map<String, String> mergedBuildInfoAttrs,
      Map<String, ImmutableList<String>> mergedInvocationMetadata) {
    Map<String, String> buildAttrMap = new LinkedHashMap<>();

    for (Map.Entry<String, String> entry : mergedBuildInfoAttrs.entrySet()) {
      String normalizedKey = normalizeBuildAttributeKey(entry.getKey());
      if (CompatibilityReportUtil.isAllowedBuildAttribute(normalizedKey)) {
        putIfAbsentOrBlank(buildAttrMap, normalizedKey, entry.getValue());
      }
    }

    for (Map.Entry<String, ImmutableList<String>> entry : mergedInvocationMetadata.entrySet()) {
      String normalizedKey = normalizeBuildAttributeKey(entry.getKey());
      if (CompatibilityReportUtil.isAllowedBuildAttribute(normalizedKey)) {
        putIfAbsentOrBlank(buildAttrMap, normalizedKey, String.join(",", entry.getValue()));
      }
    }

    return CompatibilityReportUtil.buildBuildInfo(buildAttrMap);
  }

  private static String normalizeBuildAttributeKey(String key) {
    String stripped =
        key.startsWith(CTS_BUILD_ATTR_PREFIX) ? key.substring(CTS_BUILD_ATTR_PREFIX.length()) : key;
    return stripped.replace(':', '_');
  }

  private static ImmutableList<Attribute> buildResultAttributes(
      TimestampTracker timestamps,
      Map<String, String> mergedBuildInfoAttrs,
      Map<String, ImmutableList<String>> mergedInvocationMetadata,
      Set<String> deviceSerials,
      HostEnvironment hostEnv) {
    long startTimeMillis = timestamps.resolveStartMillis();
    long endTimeMillis = timestamps.resolveEndMillis(startTimeMillis);

    Map<String, String> attrMap = new LinkedHashMap<>();
    attrMap.put(XmlConstants.START_TIME_ATTR, Long.toString(startTimeMillis));
    attrMap.put(XmlConstants.END_TIME_ATTR, Long.toString(endTimeMillis));
    attrMap.put(
        XmlConstants.START_DISPLAY_TIME_ATTR,
        formatDisplayTime(startTimeMillis, hostEnv.displayZone()));
    attrMap.put(
        XmlConstants.END_DISPLAY_TIME_ATTR,
        formatDisplayTime(endTimeMillis, hostEnv.displayZone()));

    ImmutableList<String> cmdArgsValues =
        mergedInvocationMetadata.get(XmlConstants.COMMAND_LINE_ARGS);
    if (cmdArgsValues != null && !cmdArgsValues.isEmpty()) {
      attrMap.put(XmlConstants.COMMAND_LINE_ARGS, cmdArgsValues.get(0).trim());
    }

    Set<String> resolvedSerials = new LinkedHashSet<>(deviceSerials);
    if (resolvedSerials.isEmpty()) {
      addSplitValues(resolvedSerials, mergedInvocationMetadata.get(XmlConstants.DEVICES_ATTR));
      addSplitValues(resolvedSerials, mergedInvocationMetadata.get(SERIAL_KEY));
    }
    attrMap.put(XmlConstants.DEVICES_ATTR, String.join(",", resolvedSerials));

    populateSuiteAttributes(attrMap, mergedBuildInfoAttrs, mergedInvocationMetadata);
    attrMap.putAll(
        CompatibilityReportUtil.buildHostAttributes(
            hostEnv.hostNameSupplier().get(), hostEnv.systemPropertyLookup()));

    return CompatibilityReportUtil.buildOrderedResultAttributes(attrMap);
  }

  private static void populateSuiteAttributes(
      Map<String, String> targetAttrs,
      Map<String, String> mergedBuildInfoAttrs,
      Map<String, ImmutableList<String>> mergedInvocationMetadata) {
    SuiteAttributeLookup lookup =
        new SuiteAttributeLookup(mergedBuildInfoAttrs, mergedInvocationMetadata);
    String suiteName = lookup.firstNonEmpty(SUITE_NAME_BUILD_KEY, XmlConstants.SUITE_NAME_ATTR);
    String suiteVariant =
        lookup.firstNonEmpty(SUITE_VARIANT_BUILD_KEY, XmlConstants.SUITE_VARIANT_ATTR);
    String suiteVersion =
        lookup.firstNonEmpty(SUITE_VERSION_BUILD_KEY, XmlConstants.SUITE_VERSION_ATTR);
    String suitePlan = lookup.firstNonEmpty(SUITE_PLAN_BUILD_KEY, XmlConstants.SUITE_PLAN_ATTR);
    String suiteBuild =
        lookup.firstNonEmpty(
            SUITE_BUILD_NUMBER_BUILD_KEY,
            SUITE_BUILD_SHORT_KEY,
            SuiteCommon.SUITE_BUILD,
            XmlConstants.SUITE_BUILD_ATTR);
    String reportVersion =
        lookup.firstNonEmpty(
            SUITE_REPORT_VERSION_BUILD_KEY,
            SuiteCommon.SUITE_REPORT_VERSION,
            XmlConstants.SUITE_REPORT_VERSION_ATTR);

    if (suiteName.isEmpty()
        && suiteVariant.isEmpty()
        && suiteVersion.isEmpty()
        && suitePlan.isEmpty()
        && suiteBuild.isEmpty()
        && reportVersion.isEmpty()) {
      return;
    }

    CertificationSuiteInfo suiteInfo =
        CertificationSuiteInfo.builder()
            .setSuiteName(suiteName)
            .setSuiteVariant(
                !suiteVariant.isEmpty()
                    ? suiteVariant
                    : CertificationSuiteInfoFactory.resolveSuiteVariant(suitePlan, suiteName))
            .setSuiteVersion(suiteVersion)
            .setSuitePlan(suitePlan)
            .setSuiteBuild(suiteBuild)
            .setSuiteReportVersion(
                !reportVersion.isEmpty()
                    ? reportVersion
                    : CertificationSuiteInfoFactory.SUITE_REPORT_VERSION)
            .build();
    targetAttrs.putAll(CompatibilityReportUtil.toSuiteAttributesMap(suiteInfo));
  }

  private static void mergeBuildInfoFromContext(
      Context context, Map<String, String> mergedBuildInfoAttrs, Set<String> deviceSerials) {
    for (BuildInformation.BuildInfo buildInfo : context.getNameBuildInfoMap().values()) {
      Map<String, String> attrs = buildInfo.getAttributesMap();
      for (Map.Entry<String, String> entry : attrs.entrySet()) {
        putIfAbsentOrBlank(mergedBuildInfoAttrs, entry.getKey(), entry.getValue());
      }
      String ctsSerial = attrs.get(CTS_BUILD_ATTR_PREFIX + BUILD_SERIAL_ATTR);
      if (!Strings.isNullOrEmpty(ctsSerial)) {
        addSplitSerials(deviceSerials, ctsSerial);
      }
      String plainSerial = attrs.get(BUILD_SERIAL_ATTR);
      if (!Strings.isNullOrEmpty(plainSerial)) {
        addSplitSerials(deviceSerials, plainSerial);
      }
    }
  }

  private static void mergeMetadataFromContext(
      Context context,
      Map<String, ImmutableList<String>> mergedInvocationMetadata,
      boolean skipModuleAttrs) {
    for (Metadata metadata : context.getMetadataList()) {
      String key = metadata.getKey();
      if (key.equals(XtsConstants.PLANNED_MODULES_ATTR)) {
        continue;
      }
      if (skipModuleAttrs && key.startsWith(MODULE_ATTR_PREFIX)) {
        continue;
      }
      if (!metadata.getValueList().stream().allMatch(String::isBlank)) {
        mergedInvocationMetadata.putIfAbsent(key, ImmutableList.copyOf(metadata.getValueList()));
      }
    }
  }

  private static void putIfAbsentOrBlank(Map<String, String> target, String key, String value) {
    String existing = target.get(key);
    if (existing == null || (existing.isBlank() && !value.isBlank())) {
      target.put(key, value);
    }
  }

  private static void addSplitValues(Set<String> target, @Nullable ImmutableList<String> values) {
    if (values == null) {
      return;
    }
    for (String value : values) {
      addSplitSerials(target, value);
    }
  }

  private static void addSplitSerials(Set<String> target, String raw) {
    for (String part : Splitter.on(',').trimResults().omitEmptyStrings().split(raw)) {
      target.add(part);
    }
  }

  private static Optional<TestRecord> tryReadTestRecord(Path protoFile) {
    try {
      TestRecord record = TestRecordProtoUtil.readFromFile(protoFile.toFile());
      if (record.equals(TestRecord.getDefaultInstance())) {
        logger.atWarning().log("Skipping empty test record proto file [%s]", protoFile);
        return Optional.empty();
      }
      return Optional.of(record);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Skipping unreadable test record proto file [%s]", protoFile);
      return Optional.empty();
    }
  }

  private static Context unpackContext(TestRecord record, Path protoFile) {
    if (!record.hasDescription()) {
      return Context.getDefaultInstance();
    }
    Any description = record.getDescription();
    if (!description.is(Context.class)) {
      logger.atWarning().log(
          "Ignoring unexpected description type URL [%s] in proto file [%s]; expected [%s]",
          description.getTypeUrl(), protoFile, Context.getDescriptor().getFullName());
      return Context.getDefaultInstance();
    }
    try {
      return description.unpack(Context.class);
    } catch (InvalidProtocolBufferException e) {
      logger.atWarning().withCause(e).log(
          "Ignoring unreadable Context description in proto file [%s]", protoFile);
      return Context.getDefaultInstance();
    }
  }

  private static boolean hasBuildFingerprint(BuildInfo buildInfo) {
    return !buildInfo.getBuildFingerprint().isEmpty()
        || !buildInfo.getBuildFingerprintUnaltered().isEmpty();
  }

  private static ImmutableMap<String, Integer> parsePlannedModules(
      Context context, Path invocationFile) {
    Map<String, Integer> plannedCountsByModule = new LinkedHashMap<>();
    for (Metadata metadata : context.getMetadataList()) {
      if (!metadata.getKey().equals(XtsConstants.PLANNED_MODULES_ATTR)) {
        continue;
      }
      for (String entry : metadata.getValueList()) {
        if (entry.isEmpty()) {
          continue;
        }
        int idx = entry.lastIndexOf(':');
        if (idx <= 0) {
          logger.atWarning().log(
              "Skipping malformed %s entry [%s] in [%s]",
              XtsConstants.PLANNED_MODULES_ATTR, entry, invocationFile);
          continue;
        }
        String moduleId = entry.substring(0, idx);
        if (moduleId.isBlank()) {
          logger.atWarning().log(
              "Skipping %s entry with blank module ID [%s] in [%s]",
              XtsConstants.PLANNED_MODULES_ATTR, entry, invocationFile);
          continue;
        }
        int count;
        try {
          count = Integer.parseInt(entry.substring(idx + 1));
        } catch (NumberFormatException e) {
          logger.atWarning().log(
              "Skipping %s entry with invalid count [%s] in [%s]",
              XtsConstants.PLANNED_MODULES_ATTR, entry, invocationFile);
          continue;
        }
        if (count <= 0) {
          logger.atWarning().log(
              "Skipping %s entry with non-positive count [%s] in [%s]",
              XtsConstants.PLANNED_MODULES_ATTR, entry, invocationFile);
          continue;
        }
        plannedCountsByModule.merge(moduleId, count, Integer::sum);
      }
    }
    return ImmutableMap.copyOf(plannedCountsByModule);
  }

  private static String formatDisplayTime(long epochMillis, ZoneId zoneId) {
    // DateTimeFormatter 'zzz' renders ZoneOffset.UTC as "Z"; map to Etc/UTC to emit "UTC".
    ZoneId effectiveZone = zoneId.equals(ZoneOffset.UTC) ? UTC_DISPLAY_ZONE : zoneId;
    return DISPLAY_TIME_FORMATTER.withZone(effectiveZone).format(Instant.ofEpochMilli(epochMillis));
  }

  private static String getHostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return "";
    }
  }

  private static String getSystemProperty(String key) {
    return Strings.nullToEmpty(System.getProperty(key));
  }

  private record SuiteAttributeLookup(
      Map<String, String> buildAttrs, Map<String, ImmutableList<String>> metadataMap) {
    String firstNonEmpty(String... candidateKeys) {
      for (String key : candidateKeys) {
        String buildVal = buildAttrs.get(key);
        if (!Strings.isNullOrEmpty(buildVal)) {
          return buildVal.trim();
        }
        ImmutableList<String> metaVals = metadataMap.get(key);
        if (metaVals != null) {
          for (String val : metaVals) {
            if (!Strings.isNullOrEmpty(val)) {
              return val.trim();
            }
          }
        }
      }
      return "";
    }
  }

  private static final class SessionAccumulator {
    private final Set<String> plannedModuleOrder = new LinkedHashSet<>();
    private final Map<String, ModuleAccumulator> moduleAccumulatorsById = new LinkedHashMap<>();
    private final Map<String, String> buildInfoAttrs = new LinkedHashMap<>();
    private final Map<String, ImmutableList<String>> invocationMetadata = new LinkedHashMap<>();
    private final Map<String, ImmutableList<String>> fallbackModuleMetadata = new LinkedHashMap<>();
    private final Set<String> deviceSerials = new LinkedHashSet<>();
    private final TimestampTracker timestamps = new TimestampTracker();

    void observeInvocation(TestRecord invocationRecord, Context invocationContext) {
      timestamps.observe(invocationRecord);
      mergeBuildInfoFromContext(invocationContext, buildInfoAttrs, deviceSerials);
      mergeMetadataFromContext(invocationContext, invocationMetadata, /* skipModuleAttrs= */ false);
    }

    void observeModule(TestRecord moduleRecord, Context moduleContext) {
      timestamps.observe(moduleRecord);
      for (ChildReference childRef : moduleRecord.getChildrenList()) {
        if (childRef.hasInlineTestRecord()) {
          timestamps.observe(childRef.getInlineTestRecord());
        }
      }
      mergeBuildInfoFromContext(moduleContext, buildInfoAttrs, deviceSerials);
      mergeMetadataFromContext(moduleContext, fallbackModuleMetadata, /* skipModuleAttrs= */ true);
    }

    void observeComplete(TestRecord completeRecord, Context completeContext) {
      timestamps.observe(completeRecord);
      mergeBuildInfoFromContext(completeContext, buildInfoAttrs, deviceSerials);
      mergeMetadataFromContext(completeContext, invocationMetadata, /* skipModuleAttrs= */ false);
    }

    void foldJobModules(
        ImmutableMap<String, Integer> plannedCountsByModule,
        Map<String, ModuleAccumulator> jobModules) {
      plannedModuleOrder.addAll(plannedCountsByModule.keySet());
      for (Map.Entry<String, ModuleAccumulator> entry : jobModules.entrySet()) {
        moduleAccumulatorsById
            .computeIfAbsent(entry.getKey(), ModuleAccumulator::new)
            .mergeFrom(entry.getValue());
      }
    }

    Optional<Result> buildResult(HostEnvironment hostEnv, boolean skipDeviceInfo, Path storeDir) {
      Map<String, ImmutableList<String>> effectiveMetadata =
          new LinkedHashMap<>(invocationMetadata);
      fallbackModuleMetadata.forEach(effectiveMetadata::putIfAbsent);
      BuildInfo buildInfo = buildBuildInfo(buildInfoAttrs, effectiveMetadata);
      if (!skipDeviceInfo && !hasBuildFingerprint(buildInfo)) {
        logger.atWarning().log(
            "Skipping incremental store [%s]: missing device build fingerprint in BuildInfo",
            storeDir);
        return Optional.empty();
      }

      List<Module> modules = new ArrayList<>();
      for (String plannedModuleId : plannedModuleOrder) {
        ModuleAccumulator accumulator = moduleAccumulatorsById.get(plannedModuleId);
        if (accumulator != null) {
          modules.add(accumulator.toModule());
        }
      }
      for (Map.Entry<String, ModuleAccumulator> entry : moduleAccumulatorsById.entrySet()) {
        if (!plannedModuleOrder.contains(entry.getKey())) {
          modules.add(entry.getValue().toModule());
        }
      }

      Summary summary = CompatibilityReportUtil.buildSummaryFromModules(modules);
      ImmutableList<Attribute> resultAttributes =
          buildResultAttributes(
              timestamps, buildInfoAttrs, effectiveMetadata, deviceSerials, hostEnv);

      return Optional.of(
          Result.newBuilder()
              .setBuild(buildInfo)
              .setSummary(summary)
              .addAllModuleInfo(modules)
              .addAllAttribute(resultAttributes)
              .build());
    }
  }

  private static final class TimestampTracker {
    private boolean hasStart;
    private long minStartMs = Long.MAX_VALUE;
    private boolean hasEnd;
    private long maxEndMs = Long.MIN_VALUE;

    void observe(TestRecord record) {
      if (record.hasStartTime()) {
        long startMs = ModuleAccumulator.toEpochMillis(record.getStartTime());
        minStartMs = hasStart ? Math.min(minStartMs, startMs) : startMs;
        hasStart = true;
      }
      if (record.hasEndTime()) {
        long endMs = ModuleAccumulator.toEpochMillis(record.getEndTime());
        maxEndMs = hasEnd ? Math.max(maxEndMs, endMs) : endMs;
        hasEnd = true;
      }
    }

    long resolveStartMillis() {
      return hasStart ? minStartMs : 0L;
    }

    long resolveEndMillis(long resolvedStartMillis) {
      return hasEnd ? Math.max(resolvedStartMillis, maxEndMs) : resolvedStartMillis;
    }
  }

  @VisibleForTesting
  record HostEnvironment(
      ZoneId displayZone,
      Supplier<String> hostNameSupplier,
      UnaryOperator<String> systemPropertyLookup) {
    static HostEnvironment system() {
      return new HostEnvironment(
          ZoneId.systemDefault(),
          IncrementalResultConverter::getHostName,
          IncrementalResultConverter::getSystemProperty);
    }
  }

  private record FileSortKey(String prefix, boolean hasNumericSuffix, long index, String fileName) {
    private static final CharMatcher ASCII_DIGIT = CharMatcher.inRange('0', '9');

    static final Comparator<FileSortKey> COMPARATOR =
        comparing(FileSortKey::prefix)
            .thenComparing(FileSortKey::hasNumericSuffix, falseFirst())
            .thenComparingLong(FileSortKey::index)
            .thenComparing(FileSortKey::fileName);

    static FileSortKey fromPath(Path path) {
      String name = path.getFileName().toString();
      String stem =
          name.endsWith(PB_FILE_SUFFIX)
              ? name.substring(0, name.length() - PB_FILE_SUFFIX.length())
              : name;
      int underscoreIdx = stem.lastIndexOf('_');
      if (underscoreIdx > 0 && underscoreIdx < stem.length() - 1) {
        OptionalLong parsed = tryParseAsciiDigits(stem.substring(underscoreIdx + 1));
        if (parsed.isPresent()) {
          return new FileSortKey(stem.substring(0, underscoreIdx), true, parsed.getAsLong(), name);
        }
      }
      return new FileSortKey(stem, false, -1L, name);
    }

    private static OptionalLong tryParseAsciiDigits(String s) {
      if (!ASCII_DIGIT.matchesAllOf(s)) {
        return OptionalLong.empty();
      }
      try {
        return OptionalLong.of(Long.parseLong(s));
      } catch (NumberFormatException e) {
        return OptionalLong.empty();
      }
    }
  }
}
