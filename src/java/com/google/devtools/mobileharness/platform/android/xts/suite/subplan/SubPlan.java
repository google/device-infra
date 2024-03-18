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

import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteTestFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

/** Container, parser, and generator of SubPlan info. */
public class SubPlan extends AbstractXmlParser {

  private final Set<String> includeFilters;
  private final Set<String> excludeFilters;

  private final Set<String> nonTfIncludeFilters;
  private final Set<String> nonTfExcludeFilters;

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

  public SubPlan() {
    includeFilters = new HashSet<>();
    excludeFilters = new HashSet<>();
    nonTfIncludeFilters = new HashSet<>();
    nonTfExcludeFilters = new HashSet<>();
  }

  /** Adds a filter of which TF tests to include. */
  public void addIncludeFilter(String filter) {
    includeFilters.add(filter);
  }

  /** Adds the {@link Set} of filters of which TF tests to include. */
  public void addAllIncludeFilters(Set<String> filters) {
    includeFilters.addAll(filters);
  }

  /** Adds a filter of which non-TF tests to include. */
  public void addNonTfIncludeFilter(String filter) {
    nonTfIncludeFilters.add(filter);
  }

  /** Adds a filter of which TF tests to exclude. */
  public void addExcludeFilter(String filter) {
    excludeFilters.add(filter);
  }

  /** Adds the {@link Set} of filters of which TF tests to exclude. */
  public void addAllExcludeFilters(Set<String> filters) {
    excludeFilters.addAll(filters);
  }

  /** Adds a filter of which non-TF tests to exclude. */
  public void addNonTfExcludeFilter(String filter) {
    nonTfExcludeFilters.add(filter);
  }

  /** Gets the current {@link Set} of include filters for TF tests. */
  public Set<String> getIncludeFilters() {
    return new HashSet<>(includeFilters);
  }

  /** Gets the current {@link Set} of include filters for non-TF tests. */
  public Set<String> getNonTfIncludeFilters() {
    return new HashSet<>(nonTfIncludeFilters);
  }

  /** Gets the current {@link Set} of exclude filters for TF tests. */
  public Set<String> getExcludeFilters() {
    return new HashSet<>(excludeFilters);
  }

  /** Gets the current {@link Set} of exclude filters for non-TF tests. */
  public Set<String> getNonTfExcludeFilters() {
    return new HashSet<>(nonTfExcludeFilters);
  }

  /** Deletes all the include filters currently tracked. */
  public void clearIncludeFilters() {
    includeFilters.clear();
    nonTfIncludeFilters.clear();
  }

  /** Deletes all the exclude filters currently tracked. */
  public void clearExcludeFilters() {
    excludeFilters.clear();
    nonTfExcludeFilters.clear();
  }

  /**
   * Serializes the existing filters into a stream of XML, and write to an output stream.
   *
   * @param xmlOutputStream the {@link OutputStream} to receive subplan XML
   */
  public void serialize(OutputStream xmlOutputStream) throws IOException {
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

    ArrayList<String> sortedIncludes = new ArrayList<>(includeFilters);
    ArrayList<String> sortedExcludes = new ArrayList<>(excludeFilters);
    Collections.sort(sortedIncludes);
    Collections.sort(sortedExcludes);
    for (String include : sortedIncludes) {
      serializer.startTag(NS, ENTRY_TAG);
      serializer.attribute(NS, INCLUDE_ATTR, include);
      serializer.endTag(NS, ENTRY_TAG);
    }
    for (String exclude : sortedExcludes) {
      serializer.startTag(NS, ENTRY_TAG);
      serializer.attribute(NS, EXCLUDE_ATTR, exclude);
      serializer.endTag(NS, ENTRY_TAG);
    }

    ArrayList<String> sortedNonTfIncludes = new ArrayList<>(nonTfIncludeFilters);
    ArrayList<String> sortedNonTfExcludes = new ArrayList<>(nonTfExcludeFilters);
    Collections.sort(sortedNonTfIncludes);
    Collections.sort(sortedNonTfExcludes);
    for (String include : sortedNonTfIncludes) {
      serializer.startTag(NS, ENTRY_TAG);
      serializer.attribute(NS, INCLUDE_ATTR, include);
      serializer.attribute(NS, IS_NON_TF_ATTR, "true");
      serializer.endTag(NS, ENTRY_TAG);
    }
    for (String exclude : sortedNonTfExcludes) {
      serializer.startTag(NS, ENTRY_TAG);
      serializer.attribute(NS, EXCLUDE_ATTR, exclude);
      serializer.attribute(NS, IS_NON_TF_ATTR, "true");
      serializer.endTag(NS, ENTRY_TAG);
    }

    serializer.endTag(NS, SUBPLAN_TAG);
    serializer.endDocument();
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
              abiString, nameString, includeString, isNonTf ? nonTfIncludeFilters : includeFilters);
        } else {
          parseFilter(
              abiString, nameString, excludeString, isNonTf ? nonTfExcludeFilters : excludeFilters);
        }
      }
    }

    private void parseFilter(String abi, String name, String filter, Set<String> filterSet) {
      if (name == null) {
        // ignore name and abi attributes, 'filter' should contain all necessary parts
        filterSet.add(filter);
      } else {
        // 'filter' is name of test. Build TestFilter and convert back to string
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

        filterSet.add(SuiteTestFilter.create(newFilter.toString()).toString());
      }
    }
  }
}
