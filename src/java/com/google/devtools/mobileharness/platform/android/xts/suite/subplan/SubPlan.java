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

package com.google.devtools.mobileharness.platform.android.xts.suite.subplan;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.TreeMultimap;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteTestFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

/** Container, parser, and generator of SubPlan info. */
public class SubPlan extends AbstractXmlParser {

  private final TreeMultimap<String, String> includeFiltersMultimap;
  private final TreeMultimap<String, String> excludeFiltersMultimap;

  private final TreeMultimap<String, String> nonTfIncludeFiltersMultimap;
  private final TreeMultimap<String, String> nonTfExcludeFiltersMultimap;

  private static final String ENCODING = "UTF-8";
  private static final String NS = null; // namespace used for XML serializer
  private static final String VERSION_ATTR = "version";
  private static final String SUBPLAN_VERSION = "2.0";

  private static final String SUBPLAN_TAG = "SubPlan";
  private static final String ENTRY_TAG = "Entry";
  private static final String EXCLUDE_ATTR = "exclude";
  private static final String INCLUDE_ATTR = "include";
  private static final String ABI_ATTR = "abi";
  private static final String NAME_ATTR = "name";
  private static final String IS_NON_TF_ATTR = "isNonTf";

  public static final String ALL_TESTS_IN_MODULE = "ALL";

  private String prevSessionXtsTestPlan;
  private String prevSessionDeviceBuildFingerprint;
  private String prevSessionDeviceBuildFingerprintUnaltered;
  private String prevSessionDeviceVendorBuildFingerprint;

  public SubPlan() {
    includeFiltersMultimap = TreeMultimap.create();
    excludeFiltersMultimap = TreeMultimap.create();

    nonTfIncludeFiltersMultimap = TreeMultimap.create();
    nonTfExcludeFiltersMultimap = TreeMultimap.create();

    prevSessionXtsTestPlan = "";
  }

  /** Adds a filter of which TF tests to include. */
  public void addIncludeFilter(String filter) {
    addFilterHelper(includeFiltersMultimap, filter);
  }

  /** Adds the {@link Set} of filters of which TF tests to include. */
  public void addAllIncludeFilters(Set<String> filters) {
    for (String filter : filters) {
      addIncludeFilter(filter);
    }
  }

  /** Adds a filter of which non-TF tests to include. */
  public void addNonTfIncludeFilter(String filter) {
    addFilterHelper(nonTfIncludeFiltersMultimap, filter);
  }

  /** Adds a filter of which TF tests to exclude. */
  public void addExcludeFilter(String filter) {
    addFilterHelper(excludeFiltersMultimap, filter);
  }

  /** Adds the {@link Set} of filters of which TF tests to exclude. */
  public void addAllExcludeFilters(Set<String> filters) {
    for (String filter : filters) {
      addExcludeFilter(filter);
    }
  }

  /** Adds a filter of which non-TF tests to exclude. */
  public void addNonTfExcludeFilter(String filter) {
    addFilterHelper(nonTfExcludeFiltersMultimap, filter);
  }

  private static void addFilterHelper(TreeMultimap<String, String> multiMap, String filter) {
    SuiteTestFilter suiteTestFilter = SuiteTestFilter.create(filter);
    multiMap.put(
        (suiteTestFilter.abi().isPresent() ? suiteTestFilter.abi().get() + " " : "")
            + suiteTestFilter.moduleName(),
        suiteTestFilter.testName().orElse(ALL_TESTS_IN_MODULE));
  }

  /** Gets the current {@link SetMultimap} of include filters for TF tests. */
  public SetMultimap<String, String> getIncludeFiltersMultimap() {
    return TreeMultimap.create(includeFiltersMultimap);
  }

  /** Gets the current {@link SetMultimap} of include filters for non-TF tests. */
  public SetMultimap<String, String> getNonTfIncludeFiltersMultimap() {
    return TreeMultimap.create(nonTfIncludeFiltersMultimap);
  }

  /** Gets the current {@link SetMultimap} of exclude filters for TF tests. */
  public SetMultimap<String, String> getExcludeFiltersMultimap() {
    return TreeMultimap.create(excludeFiltersMultimap);
  }

  /** Gets the current {@link SetMultimap} of exclude filters for non-TF tests. */
  public SetMultimap<String, String> getNonTfExcludeFiltersMultimap() {
    return TreeMultimap.create(nonTfExcludeFiltersMultimap);
  }

  /** Deletes all the include filters currently tracked. */
  public void clearIncludeFilters() {
    includeFiltersMultimap.clear();
    nonTfIncludeFiltersMultimap.clear();
  }

  /** Deletes all the exclude filters currently tracked. */
  public void clearExcludeFilters() {
    includeFiltersMultimap.clear();
    nonTfExcludeFiltersMultimap.clear();
  }

  /** Sets the previous session's test plan. */
  public void setPreviousSessionXtsTestPlan(String prevSessionXtsTestPlan) {
    this.prevSessionXtsTestPlan = prevSessionXtsTestPlan;
  }

  /** Gets the previous session's test plan. */
  public String getPreviousSessionXtsTestPlan() {
    return prevSessionXtsTestPlan;
  }

  /** Sets the previous session's device build fingerprint. */
  public void setPreviousSessionDeviceBuildFingerprint(String prevSessionDeviceBuildFingerprint) {
    this.prevSessionDeviceBuildFingerprint = prevSessionDeviceBuildFingerprint;
  }

  /** Gets the previous session's device build fingerprint. */
  public Optional<String> getPreviousSessionDeviceBuildFingerprint() {
    return Optional.ofNullable(prevSessionDeviceBuildFingerprint);
  }

  /** Sets the previous session's unaltered device build fingerprint. */
  public void setPreviousSessionDeviceBuildFingerprintUnaltered(
      String prevSessionDeviceBuildFingerprintUnaltered) {
    this.prevSessionDeviceBuildFingerprintUnaltered = prevSessionDeviceBuildFingerprintUnaltered;
  }

  /** Gets the previous session's unaltered device build fingerprint. */
  public Optional<String> getPreviousSessionDeviceBuildFingerprintUnaltered() {
    return Optional.ofNullable(prevSessionDeviceBuildFingerprintUnaltered);
  }

  /** Sets the previous session's device vendor build fingerprint. */
  public void setPreviousSessionDeviceVendorBuildFingerprint(
      String prevSessionDeviceVendorBuildFingerprint) {
    this.prevSessionDeviceVendorBuildFingerprint = prevSessionDeviceVendorBuildFingerprint;
  }

  /** Gets the previous session's device vendor build fingerprint. */
  public Optional<String> getPreviousSessionDeviceVendorBuildFingerprint() {
    return Optional.ofNullable(prevSessionDeviceVendorBuildFingerprint);
  }

  /** Gets all include filters. */
  public ImmutableList<String> getAllIncludeFilters() {
    ImmutableList.Builder<String> includeFilters = ImmutableList.builder();
    return includeFilters
        .addAll(getFiltersFromMap(includeFiltersMultimap))
        .addAll(getFiltersFromMap(nonTfIncludeFiltersMultimap))
        .build();
  }

  /** Gets all exclude filters. */
  public ImmutableList<String> getAllExcludeFilters() {
    ImmutableList.Builder<String> excludeFilters = ImmutableList.builder();
    return excludeFilters
        .addAll(getFiltersFromMap(excludeFiltersMultimap))
        .addAll(getFiltersFromMap(nonTfExcludeFiltersMultimap))
        .build();
  }

  /**
   * Serializes the existing filters into a stream of XML, and write to an output stream.
   *
   * @param xmlOutputStream the {@link OutputStream} to receive subplan XML
   */
  public void serialize(OutputStream xmlOutputStream, boolean tfFiltersOnly) throws IOException {
    XmlSerializer serializer = null;
    try {
      serializer = XmlPullParserFactory.newInstance().newSerializer();
    } catch (XmlPullParserException e) {
      try {
        xmlOutputStream.close();
      } catch (IOException e2) {
        // ignored
      }
      throw new IOException(e);
    }
    serializer.setOutput(xmlOutputStream, ENCODING);
    serializer.startDocument(ENCODING, /* standalone= */ false);
    serializer.setFeature(
        "http://xmlpull.org/v1/doc/features.html#indent-output", /* state= */ true);
    serializer.startTag(NS, SUBPLAN_TAG);
    serializer.attribute(NS, VERSION_ATTR, SUBPLAN_VERSION);

    serializeFiltersMultimap(
        serializer, includeFiltersMultimap, /* isIncludeFilter= */ true, /* isNonTf= */ false);
    serializeFiltersMultimap(
        serializer, excludeFiltersMultimap, /* isIncludeFilter= */ false, /* isNonTf= */ false);

    if (!tfFiltersOnly) {
      serializeFiltersMultimap(
          serializer,
          nonTfIncludeFiltersMultimap,
          /* isIncludeFilter= */ true,
          /* isNonTf= */ true);
      serializeFiltersMultimap(
          serializer,
          nonTfExcludeFiltersMultimap,
          /* isIncludeFilter= */ false,
          /* isNonTf= */ true);
    }

    serializer.endTag(NS, SUBPLAN_TAG);
    serializer.endDocument();
  }

  private void serializeFiltersMultimap(
      XmlSerializer serializer,
      TreeMultimap<String, String> filtersMultimap,
      boolean isIncludeFilter,
      boolean isNonTf)
      throws IOException {
    for (Entry<String, SortedSet<String>> entry : Multimaps.asMap(filtersMultimap).entrySet()) {
      if (entry.getValue().contains(ALL_TESTS_IN_MODULE) && entry.getValue().size() == 1) {
        serializer.startTag(NS, ENTRY_TAG);
        serializer.attribute(NS, isIncludeFilter ? INCLUDE_ATTR : EXCLUDE_ATTR, entry.getKey());
        if (isNonTf) {
          serializer.attribute(NS, IS_NON_TF_ATTR, "true");
        }
        serializer.endTag(NS, ENTRY_TAG);
      } else {
        for (String testName : entry.getValue()) {
          if (testName.equals(ALL_TESTS_IN_MODULE)) {
            continue;
          }
          serializer.startTag(NS, ENTRY_TAG);
          serializer.attribute(
              NS, isIncludeFilter ? INCLUDE_ATTR : EXCLUDE_ATTR, entry.getKey() + " " + testName);
          if (isNonTf) {
            serializer.attribute(NS, IS_NON_TF_ATTR, "true");
          }
          serializer.endTag(NS, ENTRY_TAG);
        }
      }
    }
  }

  private static ImmutableList<String> getFiltersFromMap(TreeMultimap<String, String> map) {
    ImmutableList.Builder<String> filters = ImmutableList.builder();
    for (Entry<String, SortedSet<String>> entry : Multimaps.asMap(map).entrySet()) {
      if (entry.getValue().contains(ALL_TESTS_IN_MODULE) && entry.getValue().size() == 1) {
        filters.add(entry.getKey());
      } else {
        for (String testName : entry.getValue()) {
          if (testName.equals(ALL_TESTS_IN_MODULE)) {
            continue;
          }
          filters.add(entry.getKey() + " " + testName);
        }
      }
    }
    return filters.build();
  }

  /** Creates a {@link DefaultHandler} to process the xml. */
  @Override
  protected DefaultHandler createXmlHandler() {
    return new EntryHandler();
  }

  /** SAX callback object. Handles parsing data from the xml tags. */
  private class EntryHandler extends DefaultHandler {

    @Override
    public void startElement(String uri, String localName, String name, Attributes attributes)
        throws SAXException {
      if (Objects.equals(localName, ENTRY_TAG)) {
        String includeString = attributes.getValue(INCLUDE_ATTR);
        String excludeString = attributes.getValue(EXCLUDE_ATTR);
        if (includeString != null && excludeString != null) {
          throw new IllegalArgumentException(
              "Cannot specify include and exclude filter in the same element");
        }
        String abiString = attributes.getValue(ABI_ATTR);
        String nameString = attributes.getValue(NAME_ATTR);
        boolean isNonTf = Boolean.parseBoolean(attributes.getValue(IS_NON_TF_ATTR));

        if (excludeString == null) {
          parseFilter(
              abiString,
              nameString,
              includeString,
              isNonTf ? nonTfIncludeFiltersMultimap : includeFiltersMultimap);
        } else {
          parseFilter(
              abiString,
              nameString,
              excludeString,
              isNonTf ? nonTfExcludeFiltersMultimap : excludeFiltersMultimap);
        }
      }
    }

    private void parseFilter(
        String abi, String name, String filter, TreeMultimap<String, String> filtersMultimap) {
      if (name == null) {
        // ignore name and abi attributes, 'filter' should contain all necessary parts
        addFilterHelper(filtersMultimap, filter);
      } else {
        // 'filter' is name of test. Build filter string
        StringBuilder newFilter = new StringBuilder();
        if (abi != null) {
          newFilter.append(abi.trim());
          newFilter.append(' ');
        }
        if (name != null) {
          newFilter.append(name.trim());
        }
        if (filter != null) {
          newFilter.append(' ');
          newFilter.append(filter.trim());
        }

        addFilterHelper(filtersMultimap, newFilter.toString());
      }
    }
  }
}
