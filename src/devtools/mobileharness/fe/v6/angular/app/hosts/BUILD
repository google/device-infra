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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//src/devtools/mobileharness/fe/v6/angular:internal"],
)

licenses(["notice"])

filegroup(
    name = "styles",
    srcs = [
    ],
)

ng_module(
    name = "hosts",
    srcs = [
        "host_details_page.ts",
        "host_list_page.ts",
    ],
    assets = [
        "host_details_page.ng.html",
        "host_list_page.ng.html",
    ],
    slow_development_build = True,
    deps = [
        third_party_js("angular2:core"),
        third_party_js("angular2:forms"),
        third_party_js("angular2:platform_browser"),
        third_party_js("angular2:router"),
        third_party_js("angular_components:cdk_a11y"),
        third_party_js("angular_components:material_autocomplete"),
        third_party_js("angular_components:material_dialog"),
        third_party_js("angular_components:material_paginator"),
        third_party_js("angular_components:material_radio"),
        third_party_js("angular_components:material_sort"),
        third_party_js("angular_components:material_table"),
        third_party_js("angular_components:material_tabs"),
        third_party_js("rxjs"),
        "//src/devtools/mobileharness/fe/v6/angular/app/services",
    ],
)

ng_module(
    name = "hosts_tests",
    testonly = True,
    srcs = ["host_list_page_test.ts"],
    deps = [
        ":hosts",
        "//src/devtools/mobileharness/fe/v6/angular/app/services",
        "//third_party/javascript/angular2:core_testing",
        "//third_party/javascript/angular2:platform_browser_animations",
        "//third_party/javascript/typings/jasmine",
    ],
)
