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
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:__subpackages__",
    ],
)

java_library(
    name = "module_parameter_handler_interface",
    srcs = ["IModuleParameterHandler.java"],
)

java_library(
    name = "module_parameters",
    srcs = ["ModuleParameters.java"],
)

java_library(
    name = "module_parameters_helper",
    srcs = ["ModuleParametersHelper.java"],
    deps = [
        ":foldable_expanding_handler",
        ":instant_app_handler",
        ":module_parameter_handler_interface",
        ":module_parameters",
        ":negative_handler",
        ":not_multi_abi_handler",
        ":run_on_sdk_sandbox_handler",
        ":secondary_user_handler",
        ":secondary_user_on_default_display_handler",
        ":secondary_user_on_secondary_display_handler",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/params/multiuser:run_on_clone_profile_parameter_handler",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/params/multiuser:run_on_secondary_user_parameter_handler",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/params/multiuser:run_on_work_profile_parameter_handler",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "foldable_handler",
    srcs = ["FoldableHandler.java"],
    deps = [":module_parameter_handler_interface"],
)

java_library(
    name = "foldable_expanding_handler",
    srcs = ["FoldableExpandingHandler.java"],
    deps = [
        ":foldable_handler",
        ":module_parameter_handler_interface",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:device_foldable_state",
    ],
)

java_library(
    name = "instant_app_handler",
    srcs = ["InstantAppHandler.java"],
    deps = [":module_parameter_handler_interface"],
)

java_library(
    name = "negative_handler",
    srcs = ["NegativeHandler.java"],
    deps = [":module_parameter_handler_interface"],
)

java_library(
    name = "not_multi_abi_handler",
    srcs = ["NotMultiAbiHandler.java"],
    deps = [
        ":negative_handler",
    ],
)

java_library(
    name = "run_on_sdk_sandbox_handler",
    srcs = ["RunOnSdkSandboxHandler.java"],
    deps = [":module_parameter_handler_interface"],
)

java_library(
    name = "secondary_user_handler",
    srcs = ["SecondaryUserHandler.java"],
    deps = [":module_parameter_handler_interface"],
)

java_library(
    name = "secondary_user_on_default_display_handler",
    srcs = ["SecondaryUserOnDefaultDisplayHandler.java"],
    deps = [":secondary_user_handler"],
)

java_library(
    name = "secondary_user_on_secondary_display_handler",
    srcs = ["SecondaryUserOnSecondaryDisplayHandler.java"],
    deps = [":secondary_user_handler"],
)
