<?xml version="1.0" encoding="utf-8"?>
<!-- Copyright (C) 2016 The Android Open Source Project

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->

<!DOCTYPE xsl:stylesheet [ <!ENTITY nbsp "&#160;"> ]>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="html" version="1.0" encoding="UTF-8" indent="yes"/>

    <xsl:template match="/">

        <html>
            <head>
                <title>Test Report</title>
                <style type="text/css">
                    @import "compatibility_result.css";
                </style>
            </head>
            <body>
                <div>
                    <table class="title">
                        <tr>
                            <td align="left"><img src="logo.png"/></td>
                        </tr>
                    </table>
                </div>

                <div>
                    <table class="summary">
                        <tr>
                            <th colspan="2">Summary</th>
                        </tr>
                        <tr>
                            <td class="rowtitle">Suite / Plan</td>
                            <td>
                                <xsl:value-of select="Result/@suite_name"/> / <xsl:value-of select="Result/@suite_plan"/>
                            </td>
                        </tr>
                        <tr>
                            <td class="rowtitle">Suite / Build</td>
                            <td>
                                <xsl:value-of select="Result/@suite_version"/> / <xsl:value-of select="Result/@suite_build_number"/>
                            </td>
                        </tr>
                        <tr>
                            <td class="rowtitle">Host Info</td>
                            <td>
                                Result/@start
                                <xsl:value-of select="Result/@host_name"/>
                                (<xsl:value-of select="Result/@os_name"/> - <xsl:value-of select="Result/@os_version"/>)
                            </td>
                        </tr>
                        <tr>
                            <td class="rowtitle">Start time / End Time</td>
                            <td>
                                <xsl:value-of select="Result/@start_display"/> /
                                <xsl:value-of select="Result/@end_display"/>
                            </td>
                        </tr>
                        <tr>
                            <td class="rowtitle">Tests Passed</td>
                            <td>
                                <xsl:value-of select="Result/Summary/@pass"/>
                            </td>
                        </tr>
                        <tr>
                            <td class="rowtitle">Tests Failed</td>
                            <td>
                                <xsl:value-of select="Result/Summary/@failed"/>
                            </td>
                        </tr>
                        <tr>
                            <td class="rowtitle">Modules Done</td>
                            <td>
                                <xsl:value-of select="Result/Summary/@modules_done"/>
                            </td>
                        </tr>
                        <tr>
                            <td class="rowtitle">Modules Total</td>
                            <td>
                                <xsl:value-of select="Result/Summary/@modules_total"/>
                            </td>
                        </tr>
                        <tr>
                            <td class="rowtitle">Fingerprint</td>
                            <td>
                                <xsl:value-of select="Result/Build/@build_fingerprint"/>
                            </td>
                        </tr>
                        <tr>
                            <td class="rowtitle">Security Patch</td>
                            <td>
                                <xsl:value-of select="Result/Build/@build_version_security_patch"/>
                            </td>
                        </tr>
                        <tr>
                            <td class="rowtitle">Release (SDK)</td>
                            <td>
                                <xsl:value-of select="Result/Build/@build_version_release"/> (<xsl:value-of select="Result/Build/@build_version_sdk"/>)
                            </td>
                        </tr>
                        <tr>
                            <td class="rowtitle">ABIs</td>
                            <td>
                                <xsl:value-of select="Result/Build/@build_abis"/>
                            </td>
                        </tr>
                    </table>
                </div>

                <!-- High level summary of test execution -->
                <br/>
                <div>
                    <table class="testsummary">
                        <tr>
                            <th>Module</th>
                            <th>Passed</th>
                            <th>Failed</th>
                            <th>Assumption Failure</th>
                            <th>Ignored</th>
                            <th>Total Tests</th>
                            <th>Done</th>
                        </tr>
                        <xsl:for-each select="Result/Module">
                            <tr>
                                <td>
                                    <xsl:if test="count(TestCase/Test[@result = 'fail']) &gt; 0">
                                        <xsl:variable name="href"><xsl:value-of select="@abi"/>&#xA0;<xsl:value-of select="@name"/></xsl:variable>
                                        <a href="#{$href}"><xsl:value-of select="@abi"/>&#xA0;<xsl:value-of select="@name"/></a>
                                    </xsl:if>
                                    <xsl:if test="count(TestCase/Test[@result = 'fail']) &lt; 1">
                                        <xsl:value-of select="@abi"/>&#xA0;<xsl:value-of select="@name"/>
                                    </xsl:if>
                                </td>
                                <td>
                                    <xsl:value-of select="@pass"/>
                                </td>
                                <td>
                                    <xsl:value-of select="count(TestCase/Test[@result = 'fail'])"/>
                                </td>
                                <td>
                                    <xsl:value-of select="count(TestCase/Test[@result = 'ASSUMPTION_FAILURE'])"/>
                                </td>
                                <td>
                                    <xsl:value-of select="count(TestCase/Test[@result = 'IGNORED'])"/>
                                </td>
                                <td>
                                    <xsl:value-of select="@total_tests"/>
                                </td>
                                <td>
                                    <xsl:value-of select="@done"/>
                                </td>
                            </tr>
                        </xsl:for-each> <!-- end Module -->
                    </table>
                </div>

                <br/>
                <xsl:call-template name="detailedTestReport">
                    <xsl:with-param name="resultFilter" select="'fail'" />
                </xsl:call-template>

                <br/>
                <xsl:call-template name="incompleteModules" />

            </body>
        </html>
    </xsl:template>

    <xsl:template name="detailedTestReport">
        <xsl:param name="resultFilter" />
        <div>
            <xsl:for-each select="Result/Module">
                <xsl:if test="$resultFilter=''
                        or count(TestCase/Test[@result=$resultFilter]) &gt; 0">

                    <table class="testdetails">
                        <tr>
                            <td class="module" colspan="3">
                                <xsl:variable name="href"><xsl:value-of select="@abi"/>&#xA0;<xsl:value-of select="@name"/></xsl:variable>
                                <a name="{$href}"><xsl:value-of select="@abi"/>&#xA0;<xsl:value-of select="@name"/></a>
                            </td>
                        </tr>

                        <tr>
                            <th width="30%">Test</th>
                            <th width="5%">Result</th>
                            <th>Details</th>
                        </tr>

                        <xsl:for-each select="TestCase">
                            <xsl:variable name="TestCase" select="."/>
                            <!-- test -->
                            <xsl:for-each select="Test">
                                <xsl:if test="$resultFilter='' or @result=$resultFilter">
                                    <tr>
                                        <td class="testname"> <xsl:value-of select="$TestCase/@name"/>#<xsl:value-of select="@name"/></td>

                                        <!-- test results -->
                                        <xsl:if test="@result='pass'">
                                            <td class="pass">
                                                <div style="text-align: center; margin-left:auto; margin-right:auto;">
                                                    <xsl:value-of select="@result"/>
                                                </div>
                                            </td>
                                            <td class="failuredetails"/>
                                        </xsl:if>

                                        <xsl:if test="@result='IGNORED'">
                                            <td class="pass">
                                                <div style="text-align: center; margin-left:auto; margin-right:auto;">
                                                    <xsl:value-of select="@result"/>
                                                </div>
                                            </td>
                                            <td class="failuredetails"/>
                                        </xsl:if>

                                        <xsl:if test="@result='fail'">
                                            <td class="failed">
                                                <div style="text-align: center; margin-left:auto; margin-right:auto;">
                                                    <xsl:value-of select="@result"/>
                                                </div>
                                            </td>
                                            <td class="failuredetails">
                                                <div class="details">
                                                    <xsl:value-of select="Failure/@message"/>
                                                </div>
                                            </td>
                                        </xsl:if>

                                        <xsl:if test="@result='ASSUMPTION_FAILURE'">
                                            <td class="pass">
                                                <div style="text-align: center; margin-left:auto; margin-right:auto;">
                                                    <xsl:value-of select="@result"/>
                                                </div>
                                            </td>
                                            <td class="failuredetails">
                                                <div class="details">
                                                    <xsl:value-of select="Failure/@message"/>
                                                </div>
                                            </td>
                                        </xsl:if>

                                        <xsl:if test="@result='not_executed'">
                                            <td class="not_executed">
                                                <div style="text-align: center; margin-left:auto; margin-right:auto;">
                                                    <xsl:value-of select="@result"/>
                                                </div>
                                            </td>
                                            <td class="failuredetails"></td>
                                        </xsl:if>
                                    </tr> <!-- finished with a row -->
                                </xsl:if>
                            </xsl:for-each> <!-- end test -->
                        </xsl:for-each>
                    </table>
                </xsl:if>
            </xsl:for-each> <!-- end test Module -->
        </div>
    </xsl:template>

    <xsl:template name="incompleteModules">
        <xsl:if test="not(Result/Summary/@modules_done = Result/Summary/@modules_total)">
            <div>
                <table class="incompletemodules">
                    <tr>
                        <th>Incomplete Modules</th>
                    </tr>
                    <xsl:for-each select="Result/Module">
                        <xsl:if test="@done='false'">
                            <tr>
                                <td>
                                    <xsl:variable name="href"><xsl:value-of select="@abi"/>&#xA0;<xsl:value-of select="@name"/></xsl:variable>
                                    <a name="{$href}"><xsl:value-of select="@abi"/>&#xA0;<xsl:value-of select="@name"/></a>
                                </td>
                            </tr>
                        </xsl:if>
                    </xsl:for-each> <!-- end test Module -->
                </table>
            </div>
        </xsl:if>
    </xsl:template>

    <!-- Take a delimited string and insert line breaks after a some number of elements. -->
    <xsl:template name="formatDelimitedString">
        <xsl:param name="string" />
        <xsl:param name="numTokensPerRow" select="10" />
        <xsl:param name="tokenIndex" select="1" />
        <xsl:if test="$string">
            <!-- Requires the last element to also have a delimiter after it. -->
            <xsl:variable name="token" select="substring-before($string, ';')" />
            <xsl:value-of select="$token" />
            <xsl:text>&#160;</xsl:text>

            <xsl:if test="$tokenIndex mod $numTokensPerRow = 0">
                <br />
            </xsl:if>

            <xsl:call-template name="formatDelimitedString">
                <xsl:with-param name="string" select="substring-after($string, ';')" />
                <xsl:with-param name="numTokensPerRow" select="$numTokensPerRow" />
                <xsl:with-param name="tokenIndex" select="$tokenIndex + 1" />
            </xsl:call-template>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>
