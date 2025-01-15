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

package com.google.devtools.mobileharness.infra.ats.common.plan;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import javax.inject.Inject;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** A parser to parse test plans from the xts-tradefed.jar file only. */
public class TestPlanParser {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CONFIGURATION_NODE_NAME = "configuration";
  private static final String OPTION_NODE_NAME = "option";
  private static final String INCLUDE_NODE_NAME = "include";

  private static final String ATTR_NAME_KEY = "name";
  private static final String ATTR_KEY_KEY = "key";
  private static final String ATTR_VALUE_KEY = "value";

  private static final String INCLUDE_FILTER_ATTR_NAME = "compatibility:include-filter";
  private static final String EXCLUDE_FILTER_ATTR_NAME = "compatibility:exclude-filter";
  private static final String MODULE_METADATA_INCLUDE_FILTER_ATTR_NAME =
      "compatibility:module-metadata-include-filter";
  private static final String MODULE_METADATA_EXCLUDE_FILTER_ATTR_NAME =
      "compatibility:module-metadata-exclude-filter";

  private final PlanConfigUtil planConfigUtil;

  @Inject
  TestPlanParser(PlanConfigUtil planConfigUtil) {
    this.planConfigUtil = planConfigUtil;
  }

  public TestPlanFilter parseFilters(Path xtsRootPath, String type, String rootTestPlan)
      throws MobileHarnessException {
    if (rootTestPlan.equals("retry")) {
      // Skip parsing the retry test plan since it is not a valid XML.
      return TestPlanFilter.create(
          ImmutableSet.of(), ImmutableSet.of(), ImmutableMultimap.of(), ImmutableMultimap.of());
    }

    Path xtsTradefedJarPath =
        XtsDirUtil.getXtsToolsDir(xtsRootPath, type)
            .resolve(String.format("%s-tradefed.jar", type));
    return parseFilters(xtsTradefedJarPath, rootTestPlan);
  }

  @VisibleForTesting
  TestPlanFilter parseFilters(Path xtsTradefedJarPath, String rootTestPlan)
      throws MobileHarnessException {
    Set<String> includeFilters = new HashSet<>();
    Set<String> excludeFilters = new HashSet<>();
    ListMultimap<String, String> metadataIncludeFilters = ArrayListMultimap.create();
    ListMultimap<String, String> metadataExcludeFilters = ArrayListMultimap.create();

    HashSet<String> parsedTestPlans = new HashSet<>();
    Queue<Node> pendingNodes = new ArrayDeque<>();
    Queue<String> pendingTestPlans = new ArrayDeque<>();

    pendingTestPlans.offer(rootTestPlan);

    while (pendingTestPlans.peek() != null) {
      String testPlanName = pendingTestPlans.poll();
      Optional<Document> testPlan = planConfigUtil.loadConfig(testPlanName, xtsTradefedJarPath);
      parsedTestPlans.add(testPlanName);

      if (testPlan.isEmpty()) {
        // Skip the test plan since it is not valid.
        logger.atWarning().log(
            "Skip parsing the test plan: %s since it is not a valid test plan.", testPlanName);
        continue;
      }
      pendingNodes.offer(testPlan.get().getDocumentElement());
      while (pendingNodes.peek() != null) {
        Node node = pendingNodes.poll();

        switch (node.getNodeName()) {
          case CONFIGURATION_NODE_NAME:
            parseConfigurationNode(node, pendingNodes);
            break;
          case OPTION_NODE_NAME:
            parseOptionNode(
                node,
                includeFilters,
                excludeFilters,
                metadataIncludeFilters,
                metadataExcludeFilters);
            break;
          case INCLUDE_NODE_NAME:
            parseIncludeNode(node, parsedTestPlans, pendingTestPlans);
            break;
          default:
            break;
        }
      }
    }
    return TestPlanFilter.create(
        ImmutableSet.copyOf(includeFilters),
        ImmutableSet.copyOf(excludeFilters),
        ImmutableMultimap.copyOf(metadataIncludeFilters),
        ImmutableMultimap.copyOf(metadataExcludeFilters));
  }

  /**
   * Parse a node named configuration starting with "<configuration" which is the root node and
   * contains children nodes to be parsed.
   */
  private static void parseConfigurationNode(Node node, Queue<Node> pendingNodes) {
    NodeList children = node.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      pendingNodes.offer(children.item(i));
    }
  }

  /**
   * Parse a node named include starting with "<include" which may indicate another test plan should
   * be parsed.
   */
  private static void parseIncludeNode(
      Node node, Set<String> parsedTestPlans, Queue<String> pendingTestPlans) {
    NamedNodeMap attributes = node.getAttributes();
    if (attributes != null && attributes.getLength() == 1) {
      Node item = attributes.getNamedItem(ATTR_NAME_KEY);
      if (item != null) {
        String newTestPlan = item.getNodeValue();
        if (!parsedTestPlans.contains(newTestPlan)) {
          pendingTestPlans.offer(newTestPlan);
        }
      }
    }
  }

  /**
   * Parse a node named option starting with "<option" which may contain filters. The following line
   * is an example.
   */
  private static void parseOptionNode(
      Node node,
      Set<String> includeFilters,
      Set<String> excludeFilters,
      ListMultimap<String, String> metadataIncludeFilters,
      ListMultimap<String, String> metadataExcludeFilters) {
    NamedNodeMap attributes = node.getAttributes();
    if (attributes == null) {
      return;
    }

    Node nameNode = attributes.getNamedItem(ATTR_NAME_KEY);
    if (nameNode == null) {
      return;
    }

    Node keyNode = attributes.getNamedItem(ATTR_KEY_KEY);
    Node valueNode = attributes.getNamedItem(ATTR_VALUE_KEY);

    switch (nameNode.getNodeValue()) {
      case INCLUDE_FILTER_ATTR_NAME:
        if (valueNode != null) {
          includeFilters.add(valueNode.getNodeValue());
        }
        return;
      case EXCLUDE_FILTER_ATTR_NAME:
        if (valueNode != null) {
          excludeFilters.add(valueNode.getNodeValue());
        }
        return;
      case MODULE_METADATA_INCLUDE_FILTER_ATTR_NAME:
        if (keyNode != null && valueNode != null) {
          metadataIncludeFilters.put(keyNode.getNodeValue(), valueNode.getNodeValue());
        }
        return;
      case MODULE_METADATA_EXCLUDE_FILTER_ATTR_NAME:
        if (keyNode != null && valueNode != null) {
          metadataExcludeFilters.put(keyNode.getNodeValue(), valueNode.getNodeValue());
        }
        return;
      default:
        return;
    }
  }

  /** A data class for all filters collected from the test plan. */
  @AutoValue
  public abstract static class TestPlanFilter {
    public abstract ImmutableSet<String> includeFilters();

    public abstract ImmutableSet<String> excludeFilters();

    public abstract ImmutableMultimap<String, String> moduleMetadataIncludeFilters();

    public abstract ImmutableMultimap<String, String> moduleMetadataExcludeFilters();

    public static TestPlanFilter create(
        ImmutableSet<String> includeFilters,
        ImmutableSet<String> excludeFilters,
        ImmutableMultimap<String, String> moduleMetadataIncludeFilters,
        ImmutableMultimap<String, String> moduleMetadataExcludeFilters) {
      return new AutoValue_TestPlanParser_TestPlanFilter(
          includeFilters,
          excludeFilters,
          moduleMetadataIncludeFilters,
          moduleMetadataExcludeFilters);
    }
  }
}
