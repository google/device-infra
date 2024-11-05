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

package com.google.devtools.mobileharness.platform.android.parser;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link XmlTreeAttributeParser}. */
@RunWith(JUnit4.class)
public class XmlTreeAttributeParserTest {
  private static final String PACKAGE_NAME = "com.google.app";
  private static final Pattern XMLTREE_ACTIVITY_NAME_PATTERN =
      Pattern.compile(
          "^\\s*A:\\s*android:name\\(0x[0-9a-f]+\\)=\".+\"\\s*\\(Raw:\\s*\"(?<name>\\S+)\"\\)$");

  @Test
  public void parse_noHandler() {
    String xmlOutput =
        "N: android=http://schemas.android.com/apk/res/android\n"
            + "  E: manifest (line=2)\n"
            + "    A: android:versionCode(0x0101021b)=(type 0x10)0x6e9af\n"
            + "    A: android:versionName(0x0101021c)=\"2.19.308\" (Raw: \"2.19.308\")\n"
            + String.format("    A: package=\"%s\" (Raw: \"%s\")\n", PACKAGE_NAME, PACKAGE_NAME)
            + "    E: uses-sdk (line=7)\n"
            + "      A: android:minSdkVersion(0x0101020c)=(type 0x10)0xf\n"
            + "      A: android:targetSdkVersion(0x01010270)=(type 0x10)0x1c\n"
            + "    E: application (line=116)\n"
            + "      A: android:theme(0x01010000)=@0x7f1201e6\n"
            + "      E: meta-data (line=163)\n"
            + "        A: android:name(0x01010003)=\"android.max_aspect\" (Raw:"
            + " \"android.max_aspect\")\n"
            + "        A: android:value(0x01010024)=(type 0x4)0x40066666\n"
            + "      E: activity (line=167)\n"
            + "        A: android:theme(0x01010000)=@0x7f120004\n"
            + "        A: android:name(0x01010003)=\"activity1\" (Raw: \"activity1\")\n"
            + "      E: activity (line=172)\n"
            + "        A: android:theme(0x01010000)=@0x7f1201e6\n"
            + "        A: android:configChanges(0x0101001f)=(type 0x11)0xfb0\n"
            + "        E: intent-filter (line=295)\n"
            + "          E: action (line=296)\n"
            + "            A: android:name(0x01010003)=\"android.intent.action.VIEW\" (Raw:"
            + " \"android.intent.action.VIEW\")\n"
            + "          E: activity (line=306)\n"
            + "            A: android:name(0x01010003)=\"activity3\" (Raw: \"activity3\")\n"
            + "        A: android:name(0x01010003)=\"o.Yu\" (Raw: \"o.Yu\")\n"
            + "      E: activity (line=368)\n"
            + "        A: android:theme(0x01010000)=@0x7f120227\n"
            + "        A: android:label(0x01010001)=@0x7f110589\n"
            + "        A: android:name(0x01010003)=\"activity4\" (Raw: \"activity4\")\n"
            + "        E: intent-filter (line=295)\n"
            + "          E: action (line=296)\n"
            + "            A: android:name(0x01010003)=\"android.intent.action.VIEW\" (Raw:"
            + " \"android.intent.action.VIEW\")\n"
            + "          E: activity (line=306)\n"
            + "            A: android:name(0x01010003)=\"activity3\" (Raw: \"activity3\")\n"
            + "      E: activity-alias (line=455)\n"
            + "        A: android:name(0x01010003)=\"activity5\" (Raw: \"activity5\")\n"
            + "        A: android:targetActivity(0x01010202)=\"activity4\" (Raw: \"activity4\")\n"
            + "      E: service (line=1544)\n"
            + "        A: android:exported(0x01010010)=(type 0x12)0xffffffff\n"
            + "        E: intent-filter (line=1547)\n"
            + "          E: action (line=1548)\n"
            + "            A: android:name(0x01010003)=\"action.name\" (Raw: \"action.name\")\n"
            + "          E: activity (line=368)\n"
            + "            A: android:theme(0x01010000)=@0x7f120227\n"
            + "            A: android:label(0x01010001)=@0x7f110589\n"
            + "            A: android:name(0x01010003)=\"activity6\" (Raw: \"activity6\")\n"
            + "        A: android:name(0x01010003)=\"service1\" (Raw: \"service1\")\n"
            + "      E: provider (line=1710)\n"
            + "        A: android:name(0x01010003)=\"provider1\" (Raw: \"provider1\")\n"
            + "        A: android:exported(0x01010010)=(type 0x12)0x0\n"
            + "        A: android:multiprocess(0x01010013)=(type 0x12)0xffffffff\n"
            + "        A: android:authorities(0x01010018)=\".lifecycle-process\" (Raw:"
            + " \".lifecycle-process\")";
    LineParser parser = XmlTreeAttributeParser.newBuilder().build();

    assertTrue(parser.parse(xmlOutput));
  }

  @Test
  public void parse_emptyOutput() {
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    LineParser parser =
        XmlTreeAttributeParser.newBuilder()
            .addHandler(
                XmlTreeAttributeParser.AttributeHandler.newBuilder()
                    .setElementType("activity")
                    .setAttributePattern(XMLTREE_ACTIVITY_NAME_PATTERN)
                    .setOnMatch(
                        m -> {
                          String activityName = m.group("name").trim();
                          if (!activityName.startsWith("o.")) {
                            builder.add(activityName);
                          }
                        })
                    .build())
            .build();

    assertTrue(parser.parse(""));
    assertThat(builder.build()).isEmpty();
  }

  @Test
  public void parse_getActivities() {
    String xmlOutput =
        "N: android=http://schemas.android.com/apk/res/android\n"
            + "  E: manifest (line=2)\n"
            + "    A: android:versionCode(0x0101021b)=(type 0x10)0x6e9af\n"
            + "    A: android:versionName(0x0101021c)=\"2.19.308\" (Raw: \"2.19.308\")\n"
            + String.format("    A: package=\"%s\" (Raw: \"%s\")\n", PACKAGE_NAME, PACKAGE_NAME)
            + "    E: uses-sdk (line=7)\n"
            + "      A: android:minSdkVersion(0x0101020c)=(type 0x10)0xf\n"
            + "      A: android:targetSdkVersion(0x01010270)=(type 0x10)0x1c\n"
            + "    E: application (line=116)\n"
            + "      A: android:theme(0x01010000)=@0x7f1201e6\n"
            + "      E: meta-data (line=163)\n"
            + "        A: android:name(0x01010003)=\"android.max_aspect\" (Raw:"
            + " \"android.max_aspect\")\n"
            + "        A: android:value(0x01010024)=(type 0x4)0x40066666\n"
            + "      E: activity (line=167)\n"
            + "        A: android:theme(0x01010000)=@0x7f120004\n"
            + "        A: android:name(0x01010003)=\"activity1\" (Raw: \"activity1\")\n"
            + "      E: activity (line=172)\n"
            + "        A: android:theme(0x01010000)=@0x7f1201e6\n"
            + "        A: android:configChanges(0x0101001f)=(type 0x11)0xfb0\n"
            + "        E: intent-filter (line=295)\n"
            + "          E: action (line=296)\n"
            + "            A: android:name(0x01010003)=\"android.intent.action.VIEW\" (Raw:"
            + " \"android.intent.action.VIEW\")\n"
            + "          E: activity (line=306)\n"
            + "            A: android:name(0x01010003)=\"activity3\" (Raw: \"activity3\")\n"
            + "        A: android:name(0x01010003)=\"o.Yu\" (Raw: \"o.Yu\")\n"
            + "      E: activity (line=368)\n"
            + "        A: android:theme(0x01010000)=@0x7f120227\n"
            + "        A: android:label(0x01010001)=@0x7f110589\n"
            + "        A: android:name(0x01010003)=\"activity4\" (Raw: \"activity4\")\n"
            + "        E: intent-filter (line=295)\n"
            + "          E: action (line=296)\n"
            + "            A: android:name(0x01010003)=\"android.intent.action.VIEW\" (Raw:"
            + " \"android.intent.action.VIEW\")\n"
            + "          E: activity (line=306)\n"
            + "            A: android:name(0x01010003)=\"activity3\" (Raw: \"activity3\")\n"
            + "      E: activity-alias (line=455)\n"
            + "        A: android:name(0x01010003)=\"activity5\" (Raw: \"activity5\")\n"
            + "        A: android:targetActivity(0x01010202)=\"activity4\" (Raw: \"activity4\")\n"
            + "      E: service (line=1544)\n"
            + "        A: android:exported(0x01010010)=(type 0x12)0xffffffff\n"
            + "        E: intent-filter (line=1547)\n"
            + "          E: action (line=1548)\n"
            + "            A: android:name(0x01010003)=\"action.name\" (Raw: \"action.name\")\n"
            + "          E: activity (line=368)\n"
            + "            A: android:theme(0x01010000)=@0x7f120227\n"
            + "            A: android:label(0x01010001)=@0x7f110589\n"
            + "            A: android:name(0x01010003)=\"activity6\" (Raw: \"activity6\")\n"
            + "        A: android:name(0x01010003)=\"service1\" (Raw: \"service1\")\n"
            + "      E: provider (line=1710)\n"
            + "        A: android:name(0x01010003)=\"provider1\" (Raw: \"provider1\")\n"
            + "        A: android:exported(0x01010010)=(type 0x12)0x0\n"
            + "        A: android:multiprocess(0x01010013)=(type 0x12)0xffffffff\n"
            + "        A: android:authorities(0x01010018)=\".lifecycle-process\" (Raw:"
            + " \".lifecycle-process\")";
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    LineParser parser =
        XmlTreeAttributeParser.newBuilder()
            .addHandler(
                XmlTreeAttributeParser.AttributeHandler.newBuilder()
                    .setElementType("activity")
                    .setAttributePattern(XMLTREE_ACTIVITY_NAME_PATTERN)
                    .setOnMatch(
                        m -> {
                          String activityName = m.group("name").trim();
                          if (!activityName.startsWith("o.")) {
                            builder.add(activityName);
                          }
                        })
                    .build())
            .build();

    parser.parse(xmlOutput);

    assertThat(builder.build()).containsExactly("activity1", "activity3", "activity4", "activity6");
  }

  @Test
  public void parse_getServices() {
    String xmlOutput =
        "N: android=http://schemas.android.com/apk/res/android\n"
            + "  E: manifest (line=2)\n"
            + "    A: android:versionCode(0x0101021b)=(type 0x10)0x6e9af\n"
            + "    A: android:versionName(0x0101021c)=\"2.19.308\" (Raw: \"2.19.308\")\n"
            + String.format("    A: package=\"%s\" (Raw: \"%s\")\n", PACKAGE_NAME, PACKAGE_NAME)
            + "    E: uses-sdk (line=7)\n"
            + "      A: android:minSdkVersion(0x0101020c)=(type 0x10)0xf\n"
            + "      A: android:targetSdkVersion(0x01010270)=(type 0x10)0x1c\n"
            + "    E: application (line=116)\n"
            + "      A: android:theme(0x01010000)=@0x7f1201e6\n"
            + "      E: meta-data (line=163)\n"
            + "        A: android:name(0x01010003)=\"android.max_aspect\" (Raw:"
            + " \"android.max_aspect\")\n"
            + "        A: android:value(0x01010024)=(type 0x4)0x40066666\n"
            + "      E: activity (line=167)\n"
            + "        A: android:theme(0x01010000)=@0x7f120004\n"
            + "        A: android:name(0x01010003)=\"activity1\" (Raw: \"activity1\")\n"
            + "      E: activity (line=172)\n"
            + "        A: android:theme(0x01010000)=@0x7f1201e6\n"
            + "        A: android:configChanges(0x0101001f)=(type 0x11)0xfb0\n"
            + "        E: intent-filter (line=295)\n"
            + "          E: action (line=296)\n"
            + "            A: android:name(0x01010003)=\"android.intent.action.VIEW\" (Raw:"
            + " \"android.intent.action.VIEW\")\n"
            + "          E: activity (line=306)\n"
            + "            A: android:name(0x01010003)=\"activity3\" (Raw: \"activity3\")\n"
            + "        A: android:name(0x01010003)=\"o.Yu\" (Raw: \"o.Yu\")\n"
            + "      E: activity (line=368)\n"
            + "        A: android:theme(0x01010000)=@0x7f120227\n"
            + "        A: android:label(0x01010001)=@0x7f110589\n"
            + "        A: android:name(0x01010003)=\"activity4\" (Raw: \"activity4\")\n"
            + "        E: intent-filter (line=295)\n"
            + "          E: action (line=296)\n"
            + "            A: android:name(0x01010003)=\"android.intent.action.VIEW\" (Raw:"
            + " \"android.intent.action.VIEW\")\n"
            + "          E: activity (line=306)\n"
            + "            A: android:name(0x01010003)=\"activity3\" (Raw: \"activity3\")\n"
            + "      E: activity-alias (line=455)\n"
            + "        A: android:name(0x01010003)=\"activity5\" (Raw: \"activity5\")\n"
            + "        A: android:targetActivity(0x01010202)=\"activity4\" (Raw: \"activity4\")\n"
            + "      E: service (line=1544)\n"
            + "        A: android:exported(0x01010010)=(type 0x12)0xffffffff\n"
            + "        E: intent-filter (line=1547)\n"
            + "          E: action (line=1548)\n"
            + "            A: android:name(0x01010003)=\"action.name\" (Raw: \"action.name\")\n"
            + "          E: activity (line=368)\n"
            + "            A: android:theme(0x01010000)=@0x7f120227\n"
            + "            A: android:label(0x01010001)=@0x7f110589\n"
            + "            A: android:name(0x01010003)=\"activity6\" (Raw: \"activity6\")\n"
            + "        A: android:name(0x01010003)=\"service1\" (Raw: \"service1\")\n"
            + "      E: provider (line=1710)\n"
            + "        A: android:name(0x01010003)=\"provider1\" (Raw: \"provider1\")\n"
            + "        A: android:exported(0x01010010)=(type 0x12)0x0\n"
            + "        A: android:multiprocess(0x01010013)=(type 0x12)0xffffffff\n"
            + "        A: android:authorities(0x01010018)=\".lifecycle-process\" (Raw:"
            + " \".lifecycle-process\")";
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    LineParser parser =
        XmlTreeAttributeParser.newBuilder()
            .addHandler(
                XmlTreeAttributeParser.AttributeHandler.newBuilder()
                    .setElementType("service")
                    .setAttributePattern(XMLTREE_ACTIVITY_NAME_PATTERN)
                    .setOnMatch(
                        m -> {
                          String activityName = m.group("name").trim();
                          if (!activityName.startsWith("o.")) {
                            builder.add(activityName);
                          }
                        })
                    .build())
            .build();

    parser.parse(xmlOutput);

    assertThat(builder.build()).containsExactly("service1");
  }

  @Test
  public void parse_getActivitiesAndServices() {
    String xmlOutput =
        "N: android=http://schemas.android.com/apk/res/android\n"
            + "  E: manifest (line=2)\n"
            + "    A: android:versionCode(0x0101021b)=(type 0x10)0x6e9af\n"
            + "    A: android:versionName(0x0101021c)=\"2.19.308\" (Raw: \"2.19.308\")\n"
            + String.format("    A: package=\"%s\" (Raw: \"%s\")\n", PACKAGE_NAME, PACKAGE_NAME)
            + "    E: uses-sdk (line=7)\n"
            + "      A: android:minSdkVersion(0x0101020c)=(type 0x10)0xf\n"
            + "      A: android:targetSdkVersion(0x01010270)=(type 0x10)0x1c\n"
            + "    E: application (line=116)\n"
            + "      A: android:theme(0x01010000)=@0x7f1201e6\n"
            + "      E: meta-data (line=163)\n"
            + "        A: android:name(0x01010003)=\"android.max_aspect\" (Raw:"
            + " \"android.max_aspect\")\n"
            + "        A: android:value(0x01010024)=(type 0x4)0x40066666\n"
            + "      E: activity (line=167)\n"
            + "        A: android:theme(0x01010000)=@0x7f120004\n"
            + "        A: android:name(0x01010003)=\"activity1\" (Raw: \"activity1\")\n"
            + "      E: activity (line=172)\n"
            + "        A: android:theme(0x01010000)=@0x7f1201e6\n"
            + "        A: android:configChanges(0x0101001f)=(type 0x11)0xfb0\n"
            + "        E: intent-filter (line=295)\n"
            + "          E: action (line=296)\n"
            + "            A: android:name(0x01010003)=\"android.intent.action.VIEW\" (Raw:"
            + " \"android.intent.action.VIEW\")\n"
            + "          E: activity (line=306)\n"
            + "            A: android:name(0x01010003)=\"activity3\" (Raw: \"activity3\")\n"
            + "        A: android:name(0x01010003)=\"o.Yu\" (Raw: \"o.Yu\")\n"
            + "      E: activity (line=368)\n"
            + "        A: android:theme(0x01010000)=@0x7f120227\n"
            + "        A: android:label(0x01010001)=@0x7f110589\n"
            + "        A: android:name(0x01010003)=\"activity4\" (Raw: \"activity4\")\n"
            + "        E: intent-filter (line=295)\n"
            + "          E: action (line=296)\n"
            + "            A: android:name(0x01010003)=\"android.intent.action.VIEW\" (Raw:"
            + " \"android.intent.action.VIEW\")\n"
            + "          E: activity (line=306)\n"
            + "            A: android:name(0x01010003)=\"activity3\" (Raw: \"activity3\")\n"
            + "      E: activity-alias (line=455)\n"
            + "        A: android:name(0x01010003)=\"activity5\" (Raw: \"activity5\")\n"
            + "        A: android:targetActivity(0x01010202)=\"activity4\" (Raw: \"activity4\")\n"
            + "      E: service (line=1544)\n"
            + "        A: android:exported(0x01010010)=(type 0x12)0xffffffff\n"
            + "        E: intent-filter (line=1547)\n"
            + "          E: action (line=1548)\n"
            + "            A: android:name(0x01010003)=\"action.name\" (Raw: \"action.name\")\n"
            + "          E: activity (line=368)\n"
            + "            A: android:theme(0x01010000)=@0x7f120227\n"
            + "            A: android:label(0x01010001)=@0x7f110589\n"
            + "            A: android:name(0x01010003)=\"activity6\" (Raw: \"activity6\")\n"
            + "        A: android:name(0x01010003)=\"service1\" (Raw: \"service1\")\n"
            + "      E: provider (line=1710)\n"
            + "        A: android:name(0x01010003)=\"provider1\" (Raw: \"provider1\")\n"
            + "        A: android:exported(0x01010010)=(type 0x12)0x0\n"
            + "        A: android:multiprocess(0x01010013)=(type 0x12)0xffffffff\n"
            + "        A: android:authorities(0x01010018)=\".lifecycle-process\" (Raw:"
            + " \".lifecycle-process\")";
    ImmutableSet.Builder<String> activitiesBuilder = ImmutableSet.builder();
    ImmutableSet.Builder<String> servicesBuilder = ImmutableSet.builder();
    LineParser parser =
        XmlTreeAttributeParser.newBuilder()
            .addHandler(
                XmlTreeAttributeParser.AttributeHandler.newBuilder()
                    .setElementType("service")
                    .setAttributePattern(XMLTREE_ACTIVITY_NAME_PATTERN)
                    .setOnMatch(
                        m -> {
                          String activityName = m.group("name").trim();
                          if (!activityName.startsWith("o.")) {
                            servicesBuilder.add(activityName);
                          }
                        })
                    .build())
            .addHandler(
                XmlTreeAttributeParser.AttributeHandler.newBuilder()
                    .setElementType("activity")
                    .setAttributePattern(XMLTREE_ACTIVITY_NAME_PATTERN)
                    .setOnMatch(
                        m -> {
                          String activityName = m.group("name").trim();
                          if (!activityName.startsWith("o.")) {
                            activitiesBuilder.add(activityName);
                          }
                        })
                    .build())
            .build();

    parser.parse(xmlOutput);

    assertThat(activitiesBuilder.build())
        .containsExactly("activity1", "activity3", "activity4", "activity6");
    assertThat(servicesBuilder.build()).containsExactly("service1");
  }

  @Test
  public void parse_missingElementError() {
    String xmlOutput =
        "N: android=http://schemas.android.com/apk/res/android\n"
            + "    A: android:versionCode(0x0101021b)=(type 0x10)0x6e9af\n"
            + "    A: android:versionName(0x0101021c)=\"2.19.308\" (Raw: \"2.19.308\")\n"
            + String.format("    A: package=\"%s\" (Raw: \"%s\")\n", PACKAGE_NAME, PACKAGE_NAME)
            + "    E: uses-sdk (line=7)\n"
            + "      A: android:minSdkVersion(0x0101020c)=(type 0x10)0xf\n"
            + "      A: android:targetSdkVersion(0x01010270)=(type 0x10)0x1c\n"
            + "    E: application (line=116)\n"
            + "      A: android:theme(0x01010000)=@0x7f1201e6\n"
            + "      E: meta-data (line=163)\n"
            + "        A: android:name(0x01010003)=\"android.max_aspect\" (Raw:"
            + " \"android.max_aspect\")\n"
            + "      E: activity (line=167)\n"
            + "        A: android:theme(0x01010000)=@0x7f120004\n"
            + "        A: android:name(0x01010003)=\"activity1\" (Raw: \"activity1\")\n"
            + "      E: activity (line=172)\n"
            + "        A: android:theme(0x01010000)=@0x7f1201e6\n"
            + "        A: android:configChanges(0x0101001f)=(type 0x11)0xfb0\n"
            + "        E: intent-filter (line=295)\n"
            + "          E: action (line=296)\n"
            + "            A: android:name(0x01010003)=\"android.intent.action.VIEW\" (Raw:"
            + " \"android.intent.action.VIEW\")\n";
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    LineParser parser =
        XmlTreeAttributeParser.newBuilder()
            .addHandler(
                XmlTreeAttributeParser.AttributeHandler.newBuilder()
                    .setElementType("activity")
                    .setAttributePattern(XMLTREE_ACTIVITY_NAME_PATTERN)
                    .setOnMatch(
                        m -> {
                          String activityName = m.group("name").trim();
                          if (!activityName.startsWith("o.")) {
                            builder.add(activityName);
                          }
                        })
                    .build())
            .build();

    assertFalse(parser.parse(xmlOutput));
    assertThat(builder.build()).isEmpty();
  }

  @Test
  public void parse_lineTooShortError() {
    String xmlOutput =
        "N: android=http://schemas.android.com/apk/res/android\n"
            + "  E: manifest (line=2)\n"
            + "    A: android:versionCode(0x0101021b)=(type 0x10)0x6e9af\n"
            + "    A: android:versionName(0x0101021c)=\"2.19.308\" (Raw: \"2.19.308\")\n"
            + String.format("    A: package=\"%s\" (Raw: \"%s\")\n", PACKAGE_NAME, PACKAGE_NAME)
            + "    E\n"
            + "      A: android:minSdkVersion(0x0101020c)=(type 0x10)0xf\n"
            + "      A: android:targetSdkVersion(0x01010270)=(type 0x10)0x1c\n"
            + "    E: application (line=116)\n"
            + "      A: android:theme(0x01010000)=@0x7f1201e6\n"
            + "      E: meta-data (line=163)\n"
            + "        A: android:name(0x01010003)=\"android.max_aspect\" (Raw:"
            + " \"android.max_aspect\")\n"
            + "      E: activity (line=167)\n"
            + "        A: android:theme(0x01010000)=@0x7f120004\n"
            + "        A: android:name(0x01010003)=\"activity1\" (Raw: \"activity1\")\n"
            + "      E: activity (line=172)\n"
            + "        A: android:theme(0x01010000)=@0x7f1201e6\n"
            + "        A: android:configChanges(0x0101001f)=(type 0x11)0xfb0\n"
            + "        E: intent-filter (line=295)\n"
            + "          E: action (line=296)\n"
            + "            A: android:name(0x01010003)=\"android.intent.action.VIEW\" (Raw:"
            + " \"android.intent.action.VIEW\")\n";
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    LineParser parser =
        XmlTreeAttributeParser.newBuilder()
            .addHandler(
                XmlTreeAttributeParser.AttributeHandler.newBuilder()
                    .setElementType("activity")
                    .setAttributePattern(XMLTREE_ACTIVITY_NAME_PATTERN)
                    .setOnMatch(
                        m -> {
                          String activityName = m.group("name").trim();
                          if (!activityName.startsWith("o.")) {
                            builder.add(activityName);
                          }
                        })
                    .build())
            .build();

    assertFalse(parser.parse(xmlOutput));
    assertThat(builder.build()).isEmpty();
  }
}
