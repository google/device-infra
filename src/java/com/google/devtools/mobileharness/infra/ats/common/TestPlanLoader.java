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

package com.google.devtools.mobileharness.infra.ats.common;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** A loader to load test plan XMLs from the xts-tradefed.jar file only. */
public class TestPlanLoader {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CONFIGURATION_NODE_NAME = "configuration";
  private static final String OPTION_NODE_NAME = "option";
  private static final String INCLUDE_NODE_NAME = "include";

  private static final String ATTR_NAME_KEY = "name";
  private static final String ATTR_VALUE_KEY = "value";

  private static final String INCLUDE_FILTER_ATTR_NAME = "compatibility:include-filter";
  private static final String EXCLUDE_FILTER_ATTR_NAME = "compatibility:exclude-filter";

  public static TestPlanFilter parseFilters(Path xtsRootPath, String type, String rootTestPlan)
      throws MobileHarnessException {
    if (rootTestPlan.equals("retry")) {
      // Skip parsing the retry test plan since it is not a valid XML.
      return TestPlanFilter.create(ImmutableSet.of(), ImmutableSet.of());
    }

    JarFile xtsTradefedJarFile = createXtsTradefedJarFile(xtsRootPath, type);
    return parseFilters(xtsTradefedJarFile, rootTestPlan);
  }

  @VisibleForTesting
  static TestPlanFilter parseFilters(JarFile xtsTradefedJarFile, String rootTestPlan)
      throws MobileHarnessException {
    // Check existence of the root test plan.
    String testPlanPath = String.format("config/%s.xml", rootTestPlan);
    JarEntry jarEntry = xtsTradefedJarFile.getJarEntry(testPlanPath);
    if (jarEntry == null) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_XTS_TEST_PLAN_LOADER_TEST_PLAN_NOT_FOUND,
          String.format("Root test plan %s not found in xts-tradefed.jar file", rootTestPlan));
    }

    HashSet<String> includeFilters = new HashSet<>();
    HashSet<String> excludeFilters = new HashSet<>();
    HashSet<String> parsedTestPlans = new HashSet<>();

    Queue<Node> pendingNodes = new ArrayDeque<>();
    Queue<String> pendingTestPlans = new ArrayDeque<>();

    pendingTestPlans.offer(rootTestPlan);

    while (pendingTestPlans.peek() != null) {
      String testPlan = pendingTestPlans.poll();
      Optional<Document> testPlanXml = parseTestPlan(xtsTradefedJarFile, testPlan);
      parsedTestPlans.add(testPlan);

      if (testPlanXml.isEmpty()) {
        // Skip the test plan since it is not a valid XML.
        continue;
      }
      pendingNodes.offer(testPlanXml.get().getDocumentElement());
      while (pendingNodes.peek() != null) {
        Node node = pendingNodes.poll();

        switch (node.getNodeName()) {
          case CONFIGURATION_NODE_NAME:
            parseConfigurationNode(node, pendingNodes);
            break;
          case OPTION_NODE_NAME:
            parseOptionNode(node, includeFilters, excludeFilters);
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
        ImmutableSet.copyOf(includeFilters), ImmutableSet.copyOf(excludeFilters));
  }

  private static JarFile createXtsTradefedJarFile(Path xtsRootPath, String type)
      throws MobileHarnessException {
    Path xtsTradefedPath =
        xtsRootPath.resolve(String.format("android-%s/tools/%s-tradefed.jar", type, type));

    try {
      return new JarFile(xtsRootPath.resolve(xtsTradefedPath).toString());
    } catch (IOException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_XTS_TEST_PLAN_LOADER_JARFILE_CREATION_ERROR,
          String.format("JarFile creation failure, jar_path=%s", xtsTradefedPath),
          e);
    }
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
      Node item = attributes.item(0);
      if (item.getNodeName().equals(ATTR_NAME_KEY)) {
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
      Node node, Set<String> includeFilters, Set<String> excludeFilters) {
    NamedNodeMap attributes = node.getAttributes();
    if (attributes != null) {
      int index = 0;
      while (index < attributes.getLength() - 1) {
        if (checkAndInsertFilter(
            attributes.item(index), attributes.item(index + 1), includeFilters, excludeFilters)) {
          // Skip current node and next node as they are qualified to be a filter.
          index += 2;
        } else {
          index++;
        }
      }
    }
  }

  /**
   * Insert the filter and return true the given items pair is qualified to be a include/exclude
   * filter. Return false otherwise.
   */
  private static boolean checkAndInsertFilter(
      Node nameNode, Node valueNode, Set<String> includeFilters, Set<String> excludeFilters) {
    if (!nameNode.getNodeName().equals(ATTR_NAME_KEY)
        || !valueNode.getNodeName().equals(ATTR_VALUE_KEY)) {
      return false;
    }

    switch (nameNode.getNodeValue()) {
      case INCLUDE_FILTER_ATTR_NAME:
        includeFilters.add(valueNode.getNodeValue());
        return true;
      case EXCLUDE_FILTER_ATTR_NAME:
        excludeFilters.add(valueNode.getNodeValue());
        return true;
      default:
        return false;
    }
  }

  private static Optional<Document> parseTestPlan(JarFile xtsTradefedJarFile, String testPlan)
      throws MobileHarnessException {
    logger.atInfo().log("Start to parse the test plan: %s", testPlan);

    String testPlanPath = String.format("config/%s.xml", testPlan);
    JarEntry jarEntry = xtsTradefedJarFile.getJarEntry(testPlanPath);
    if (jarEntry == null) {
      logger.atWarning().log(
          "Skip parsing the test plan: %s since the JarEntry is null.", testPlan);
      return Optional.empty();
    }

    try {
      InputStream inputStream = xtsTradefedJarFile.getInputStream(jarEntry);
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      // Process XML securely, avoid attacks like XML External Entities (XXE)
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      return Optional.ofNullable(factory.newDocumentBuilder().parse(inputStream));
    } catch (ParserConfigurationException | SAXException | IOException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_XTS_TEST_PLAN_LOADER_XML_PARSE_ERROR,
          String.format("Failed to read test plan, test_plan=%s, path=%s", testPlan, testPlanPath),
          e);
    }
  }

  private TestPlanLoader() {}

  /** A data class for all filters collected from the test plan. */
  @AutoValue
  public abstract static class TestPlanFilter {
    public abstract ImmutableSet<String> includeFilters();

    public abstract ImmutableSet<String> excludeFilters();

    public static TestPlanFilter create(
        ImmutableSet<String> includeFilters, ImmutableSet<String> excludeFilters) {
      return new AutoValue_TestPlanLoader_TestPlanFilter(includeFilters, excludeFilters);
    }
  }
}
