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

load("@rules_kotlin//kotlin:core.bzl", "define_kt_toolchain", "kt_javac_options", "kt_kotlinc_options")
load("@rules_license//rules:license.bzl", "license")

package(default_applicable_licenses = [":license"])

license(
    name = "license",
    package_name = "deviceinfra",
)

licenses(["notice"])

exports_files(["LICENSE"])

package_group(
    name = "deviceinfra_api_users",
    includes = [
        ":deviceinfra_all_pkg",
    ],
)

package_group(
    name = "deviceinfra_all_pkg",
    includes = [
        ":deviceinfra_pkg",
    ],
)

package_group(
    name = "deviceinfra_pkg",
    packages = [
        "//...",
    ],
)

package_group(
    name = "deviceinfra_devicemanagement_pkg",
    packages = [
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/...",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local/...",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/...",
        "//src/java/com/google/devtools/mobileharness/infra/lab/...",
        "//src/javatests/com/google/devtools/mobileharness/api/devicemanager/...",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/api/mode/local/...",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/device/...",
    ],
)

package_group(
    name = "omnilab_fusion",
    packages = [
        "//src/java/com/google/devtools/omnilab/device/lab/server/...",
        "//src/javatests/com/google/devtools/omnilab/device/lab/server/...",
    ],
)

package_group(
    name = "omnilab_console",
    packages = [
    ],
)

kt_kotlinc_options(
    name = "kt_kotlinc_options",
    jvm_target = "17",
    x_jdk_release = "17",
)

kt_javac_options(
    name = "kt_javac_options",
    release = "17",
)

define_kt_toolchain(
    name = "kotlin_toolchain",
    api_version = "1.9",
    javac_options = "//:kt_javac_options",
    jvm_target = "17",
    kotlinc_options = "//:kt_kotlinc_options",
    language_version = "1.9",
)
