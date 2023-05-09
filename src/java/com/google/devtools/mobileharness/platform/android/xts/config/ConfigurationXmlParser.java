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

package com.google.devtools.mobileharness.platform.android.xts.config;

import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/** A parser for xts configuration files. */
public class ConfigurationXmlParser {
  private static final String DESCRIPTION = "description";

  public ConfigurationXmlParser() {}

  /**
   * Parses the {@code xmlFile} and returns to the {@code Configuration} proto.
   *
   * @throws MobileHarnessException if fail to parse
   */
  public static Configuration parse(File xmlFile) throws MobileHarnessException {
    try {
      FileInputStream fileInputStream = new FileInputStream(xmlFile);
      return parse(fileInputStream);
    } catch (IOException e) {
      throw new MobileHarnessException(
          InfraErrorId.XTS_CONFIG_XML_PARSE_ERROR, "Failed to open configuration xml file", e);
    }
  }

  /**
   * Parses the {@code xml} stream and saves the result to the {@code Configuration} proto.
   *
   * @throws MobileHarnessException if fail to parse
   */
  public static Configuration parse(InputStream xml) throws MobileHarnessException {
    DocumentBuilder documentBuilder;
    Document document;
    Configuration.Builder configuration = Configuration.newBuilder();
    try {
      documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      document = documentBuilder.parse(xml);
    } catch (ParserConfigurationException | SAXException | IOException e) {
      throw new MobileHarnessException(
          InfraErrorId.XTS_CONFIG_XML_PARSE_ERROR, "Failed to parse configuration xml file", e);
    }
    Element root = document.getDocumentElement();
    configuration.setDescription(root.getAttribute(DESCRIPTION));
    return configuration.build();
  }
}
