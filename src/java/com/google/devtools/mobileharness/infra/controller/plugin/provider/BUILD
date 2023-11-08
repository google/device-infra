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

java_library(
    name = "plugin_class_provider",
    srcs = [
        "AnnotatedPluginClassProvider.java",
        "AnnotatedPluginModuleClassProvider.java",
        "NamedPluginClassProvider.java",
        "NamedPluginModuleClassProvider.java",
        "PluginClassProvider.java",
        "PluginModuleClassProvider.java",
        "RetryPluginClassProvider.java",
        "RetryPluginModuleClassProvider.java",
    ],
    visibility = ["//src/java/com/google/devtools/mobileharness/infra/controller/plugin:__subpackages__"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/plugin",
        "//src/java/com/google/wireless/qa/mobileharness/shared/log",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:org_reflections_reflections",
    ],
)
