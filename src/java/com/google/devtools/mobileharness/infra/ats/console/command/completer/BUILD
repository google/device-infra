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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:__pkg__",
    ],
)

java_library(
    name = "command_completer",
    srcs = ["CommandCompleter.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/plan:plan_config_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/plan:plan_lister",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
        "@maven//:org_jline_jline",
    ],
)

java_library(
    name = "command_completer_holder",
    srcs = ["CommandCompleterHolder.java"],
    deps = [
        ":command_completer",
        "@maven//:com_google_guava_guava",
        "@maven//:org_jline_jline",
    ],
)
