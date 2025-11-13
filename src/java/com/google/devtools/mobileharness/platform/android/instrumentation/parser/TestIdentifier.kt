package com.google.devtools.mobileharness.platform.android.instrumentation.parser

/** Identifier for an individual test case. */
data class TestIdentifier(val testPackage: String, val testClass: String, val testMethod: String)
