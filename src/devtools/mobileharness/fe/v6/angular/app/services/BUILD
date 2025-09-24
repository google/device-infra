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

load("//src/devtools/mobileharness/fe/v6/angular:adapter.bzl", "ng_module", "third_party_js")
load("//third_party/bazel_rules/rules_sass/sass:sass.bzl", "sass_binary")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//src/devtools/mobileharness/fe/v6/angular:internal"],
)

licenses(["notice"])

sass_binary(
    name = "error_dialog_css",
    src = "error_dialog.scss",
)

sass_binary(
    name = "notifier_dialog_css",
    src = "notifier_dialog.scss",
)

filegroup(
    name = "styles",
    srcs = [
        "error_dialog.css",
        "notifier_dialog.css",
    ],
)

ng_module(
    name = "services",
    srcs = [
        "app_data.ts",
        "error_dialog.ts",
        "index.ts",
        "notifier.ts",
        "notifier_dialog.ts",
    ],
    assets = [
        ":styles",
        "error_dialog.ng.html",
        "notifier_dialog.ng.html",
    ],
    slow_development_build = True,
    deps = [
        third_party_js("angular2:common_http"),
        third_party_js("angular2:core"),
        third_party_js("angular2:forms"),
        third_party_js("angular_components:cdk_a11y"),
        third_party_js("angular_components:material_button"),
        third_party_js("angular_components:material_dialog"),
        third_party_js("angular_components:material_form_field"),
        third_party_js("angular_components:material_icon"),
        third_party_js("angular_components:material_input"),
        third_party_js("angular_components:material_snack_bar"),
        third_party_js("rxjs"),
        "//third_party/javascript/safevalues",
        "//third_party/javascript/safevalues/dom",
    ],
)

ng_module(
    name = "services_tests",
    testonly = True,
    srcs = ["notifier_test.ts"],
    deps = [
        ":services",
        "//third_party/javascript/angular2:core_testing",
        "//third_party/javascript/angular2:platform_browser_animations",
        "//third_party/javascript/angular_components:material_dialog",
        "//third_party/javascript/angular_components:material_snack_bar",
        "//third_party/javascript/typings/jasmine",
    ],
)
