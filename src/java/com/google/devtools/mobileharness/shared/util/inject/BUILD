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

load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

package(default_applicable_licenses = ["//:license"])

kt_jvm_library(
    name = "inject-kotlin",
    srcs = [
        "ExtendedAnnotatedBindingBuilder.kt",
        "ExtendedLinkedBindingBuilder.kt",
        "GuiceKt.kt",
    ],
    visibility = [
        "//:deviceinfra_all_pkg",
        "//:omnilab_fusion",
    ],
    deps = [
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_inject_guice",
    ],
)
