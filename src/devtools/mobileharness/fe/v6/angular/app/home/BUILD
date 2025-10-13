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
    name = "home_css",
    src = "home.scss",
    deps = ["//third_party/javascript/angular_components:material_sass"],
)

ng_module(
    name = "home",
    srcs = [
        "home.ts",
    ],
    assets = [
        "home_css",
        "home.ng.html",
    ],
    slow_development_build = True,
    deps = [
        third_party_js("angular2:core"),
        third_party_js("angular2:platform_browser"),
        third_party_js("angular2:router"),
        third_party_js("angular_components:cdk_a11y"),
        third_party_js("angular_components:cdk_collections"),
        third_party_js("angular_components:cdk_keycodes"),
        third_party_js("angular_components:material_autocomplete"),
        third_party_js("angular_components:material_button"),
        third_party_js("angular_components:material_chips"),
        third_party_js("angular_components:material_core"),
        third_party_js("angular_components:material_dialog"),
        third_party_js("angular_components:material_stepper"),
        third_party_js("angular_components:material_table"),
        third_party_js("angular_components:material_tabs"),
        third_party_js("rxjs"),
    ],
)

ng_module(
    name = "home_tests",
    testonly = True,
    srcs = ["home_test.ts"],
    deps = [
        ":home",
        "//third_party/javascript/angular2:core_testing",
        "//third_party/javascript/angular2:platform_browser_animations",
        "//third_party/javascript/typings/jasmine",
    ],
)
