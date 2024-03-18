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

import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Helper base class for parsing xml files via {@link SAXParser}. */
public abstract class AbstractXmlParser {

  /**
   * Parses out xml data contained in given input.
   *
   * @param xmlInput the {@link InputStream} containing XML
   * @throws ParseException if input could not be parsed
   */
  public void parse(InputStream xmlInput) throws MobileHarnessException {
    try {
      SAXParserFactory parserFactory = SAXParserFactory.newInstance();
      parserFactory.setNamespaceAware(true);
      SAXParser parser = parserFactory.newSAXParser();

      DefaultHandler handler = createXmlHandler();
      parser.parse(new InputSource(xmlInput), handler);
    } catch (ParserConfigurationException | SAXException | IOException e) {
      throw new MobileHarnessException(
          ExtErrorId.ABSTRACT_XML_PARSER_PARSE_XML_ERROR, "Failed to parse xml", e);
    }
  }

  /** Creates a {@link DefaultHandler} to process the xml. */
  protected abstract DefaultHandler createXmlHandler();
}
