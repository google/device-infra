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

load("//src/devtools/mobileharness/fe/v6/angular:adapter.bzl", "COMPILER_FLAGS", "js_binary", "karma_web_test_suite", "ng_module", "third_party_js", "ts_config", "ts_development_sources")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [":internal"],
)

package_group(
    name = "internal",
    includes = [
        # Other relevant google3 labels outside of your node.
    ],
    packages = [
        # Package of your boq node, which includes javatests for java
        # nodes.
        "//third_party/py/multitest_transport/...",
        "//src/devtools/mobileharness/fe/v6/angular/...",
        "//java/com/google/devtools/mobileharness/fe/v6/ui/...",
        "//javatests/com/google/devtools/mobileharness/fe/v6/ui/...",
    ],
)

licenses(["notice"])

filegroup(
    name = "images",
    srcs = glob(["images/*"]),
)

# Main Angular module
ng_module(
    name = "main",
    srcs = [
        "main.ts",
    ],
    assets = [
        # "index.html",
    ],
    deps = [
        third_party_js("angular2:core"),
        third_party_js("angular2:platform_browser"),
        "//javascript/modulesets:modulemanager",
        "//src/devtools/mobileharness/fe/v6/angular/app",
        "//src/devtools/mobileharness/fe/v6/angular/app/services",
        "//third_party/javascript/angular2:common_http",
        "//third_party/javascript/angular2:platform_browser_animations",
        "//third_party/javascript/angular2:router",
        "//third_party/javascript/angular_components:material_dialog",
    ],
)

################################################################################
# Static files                                                                 #
################################################################################

Fileset(
    name = "static_files",
    out = "static",
    entries = [
        FilesetEntry(
            files = glob(["images/*"]),
            destdir = "images",
        ),
    ],
)

################################################################################
# Development mode                                                             #
################################################################################

ts_development_sources(
    name = "dev_sources",
    runtime_deps = [
    ],
    deps = [
        ":main",
    ],
)

################################################################################
# Production mode                                                              #
################################################################################

THIRD_PARTY_JS = [
]

# Compiled production binary
js_binary(
    name = "compiled",
    defs = COMPILER_FLAGS,
    use_precompiled_libraries = False,
    deps = [
        ":main",
    ],
)

js_binary(
    name = "uncompiled",
    compile = False,
    use_precompiled_libraries = False,
    deps = THIRD_PARTY_JS,
)

genrule(
    name = "app",
    srcs = [
        ":uncompiled-bundle.js",
        ":compiled.js",
    ],
    outs = ["app.js"],
    cmd = "cat $(SRCS) > $@",
)

# Development mode flag
config_setting(
    name = "dev_mode",
    define_values = {
        "dev_mode": "true",
    },
)

ts_development_sources(
    name = "dev_sources_for_testing",
    testonly = 1,
    runtime_deps = THIRD_PARTY_JS + [":test_setup"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/angular/app:app_tests",
        "//src/devtools/mobileharness/fe/v6/angular/app/devices:devices_tests",
        "//src/devtools/mobileharness/fe/v6/angular/app/home:home_tests",
        "//src/devtools/mobileharness/fe/v6/angular/app/hosts:hosts_tests",
        "//src/devtools/mobileharness/fe/v6/angular/app/services:services_tests",
    ],
)

# TypeScript IDE configuration
ts_config(
    name = "tsconfig",
    deps = [
        ":main",
        ":test_setup",
        "//src/devtools/mobileharness/fe/v6/angular/app:app_tests",
        "//src/devtools/mobileharness/fe/v6/angular/app/devices:devices_tests",
        "//src/devtools/mobileharness/fe/v6/angular/app/home:home_tests",
        "//src/devtools/mobileharness/fe/v6/angular/app/hosts:hosts_tests",
        "//src/devtools/mobileharness/fe/v6/angular/app/services:services_tests",
    ],
)

karma_web_test_suite(
    name = "unit_tests",
    browsers = [
        "//testing/web/browsers:chrome-linux",
    ],
    data = [
        "tsconfig",
    ],
    manifest = ":dev_sources_for_testing",
)

ng_module(
    name = "test_setup",
    testonly = True,
    srcs = ["test_setup.ts"],
    deps = [
        "//third_party/javascript/angular2:core_testing",
        "//third_party/javascript/angular2:platform_browser_testing",
        "//third_party/javascript/typings/jasmine",
    ],
)
