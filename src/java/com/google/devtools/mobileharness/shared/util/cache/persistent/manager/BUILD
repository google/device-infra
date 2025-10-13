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

load("@rules_java//java:defs.bzl", "java_binary", "java_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//javatests/com/google/devtools/mobileharness/shared/util/cache/persistent/manager:__subpackages__"],
)

java_library(
    name = "cache_entry",
    srcs = ["CacheEntry.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:data_size",
        "//src/java/com/google/devtools/mobileharness/shared/util/cache/persistent:cache_paths",
    ],
)

java_library(
    name = "eviction_policy",
    srcs = [
        "EvictionPolicy.java",
        "LraEvictionPolicy.java",
    ],
    deps = [
        ":cache_entry",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:data_size",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "cache_scanner",
    srcs = ["CacheScanner.java"],
    deps = [
        ":cache_entry",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:data_size",
        "//src/java/com/google/devtools/mobileharness/shared/util/cache/persistent:cache_paths",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "cache_evictor",
    srcs = [
        "CacheEvictor.java",
    ],
    deps = [
        ":cache_entry",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "cache_manager",
    srcs = [
        "CacheManager.java",
        "CacheManagerModule.java",
    ],
    deps = [
        ":cache_entry",
        ":cache_evictor",
        ":cache_scanner",
        ":eviction_policy",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:data_size",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "cache_manager_server_lib",
    srcs = ["CacheManagerServer.java"],
    deps = [
        ":cache_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:data_size",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/wireless/qa/mobileharness/shared:log",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dir",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)

java_binary(
    name = "cache_manager_server",
    main_class = "com.google.devtools.mobileharness.shared.util.cache.persistent.manager.CacheManagerServer",
    runtime_deps = [":cache_manager_server_lib"],
)
