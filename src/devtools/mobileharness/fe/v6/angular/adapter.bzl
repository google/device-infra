# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""Blaze adapter for UI build rules.

This file maps build rules used in the project to Blaze build rules.
adapter.bazel.bzl is a Bazel version of this file. Copybara rules rename
adapter.bazel.bzl to adapter.bzl when exporting code.
"""

load("//communication/messages/shared_web/builddefs:sass.bzl", _per_file_sass_binaries = "per_file_sass_binaries")
load("//javascript/angular2:build_defs.bzl", _ng_module = "ng_module")
load("//javascript/closure/builddefs:builddefs.bzl", _CLOSURE_COMPILER_FLAGS_FULL = "CLOSURE_COMPILER_FLAGS_FULL")
load("//javascript/typescript:build_defs.bzl", _ts_config = "ts_config", _ts_development_sources = "ts_development_sources", _ts_library = "ts_library")
load("//testing/karma/builddefs:karma_web_test_suite.bzl", _karma_web_test_suite = "karma_web_test_suite")
load("//third_party/bazel_rules/rules_sass/sass:sass.bzl", _sass_binary = "sass_binary", _sass_library = "sass_library")
load("//tools/build_defs/js:rules.bzl", _js_binary = "js_binary")

# Closure compilation flags
COMPILER_FLAGS = _CLOSURE_COMPILER_FLAGS_FULL + [
    "--hide_warnings_for=javascript,third_party/javascript",
]

js_binary = _js_binary

karma_web_test_suite = _karma_web_test_suite

ng_module = _ng_module

per_file_sass_binaries = _per_file_sass_binaries

sass_binary = _sass_binary

sass_library = _sass_library

ts_config = _ts_config

ts_development_sources = _ts_development_sources

ts_library = _ts_library

def third_party_js(target_name):
    return "//third_party/javascript/%s" % target_name
