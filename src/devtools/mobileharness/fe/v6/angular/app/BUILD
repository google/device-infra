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

# MTT application root
load("//third_party/bazel_rules/rules_sass/sass:sass.bzl", "sass_binary")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//src/devtools/mobileharness/fe/v6/angular:internal"],
)

licenses(["notice"])

sass_binary(
    name = "app_css",
    src = "app.scss",
    sourcemap = False,
    deps = ["//third_party/javascript/angular_components:material_sass"],
)

ng_module(
    name = "app",
    srcs = ["app.ts"],
    # allow_warnings = True,
    assets = [
        "app.ng.html",
        ":app_css",
    ],
    slow_development_build = True,
    deps = [
        third_party_js("angular2:common_http"),
        third_party_js("angular2:core"),
        third_party_js("angular2:platform_browser"),
        third_party_js("angular2:platform_browser_animations"),
        third_party_js("angular2:router"),
        third_party_js("angular_components:material_dialog"),
        third_party_js("angular_components:material_tooltip"),
        third_party_js("rxjs"),
        "//src/devtools/mobileharness/fe/v6/angular/app/devices",
        "//src/devtools/mobileharness/fe/v6/angular/app/home",
        "//src/devtools/mobileharness/fe/v6/angular/app/hosts",
        "//src/devtools/mobileharness/fe/v6/angular/app/services",
        "//third_party/javascript/angular_components:material_button",
        "//third_party/javascript/angular_components:material_divider",
        "//third_party/javascript/angular_components:material_icon",
        "//third_party/javascript/angular_components:material_list",
        "//third_party/javascript/angular_components:material_sidenav",
        "//third_party/javascript/angular_components:material_toolbar",
    ],
)

ng_module(
    name = "app_tests",
    testonly = True,
    srcs = ["app_test.ts"],
    deps = [
        ":app",
        "//src/devtools/mobileharness/fe/v6/angular/app/services",
        "//third_party/javascript/angular2:core_testing",
        "//third_party/javascript/angular2:platform_browser_animations",
        "//third_party/javascript/angular2:router_testing",
        "//third_party/javascript/typings/jasmine",
    ],
)
