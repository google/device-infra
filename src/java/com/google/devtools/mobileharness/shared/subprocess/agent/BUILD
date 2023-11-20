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
        "//src/javatests/com/google/devtools/mobileharness/shared/subprocess/agent:__subpackages__",
    ],
)

# Add the following native arguments to a Java command to use the agent:
# -javaagent:<mobile_harness_agent_deploy_jar_path>=<mobile_harness_agent_config_file_path>
# -Xbootclasspath/a:<mobile_harness_agent_boot_class_deploy_jar_path>"
java_binary(
    name = "mobile_harness_agent",
    srcs = ["MobileHarnessAgent.java"],
    deploy_manifest_lines = [
        "Premain-Class: com.google.devtools.mobileharness.shared.subprocess.agent.MobileHarnessAgent",
    ],
    deps = [
        "//src/devtools/mobileharness/shared/commandhistory/proto:command_record_java_proto",
        "//src/devtools/mobileharness/shared/subprocess/proto:agent_config_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/commandhistory/controller:local_command_history_manager",
        "//src/java/com/google/devtools/mobileharness/shared/commandhistory/controller:local_command_history_recorder",
        "//src/java/com/google/devtools/mobileharness/shared/subprocess/listener:process_builder_listener",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:net_bytebuddy_byte_buddy",
    ],
)

# Binary which contains classes that needs to be loaded by Java bootstrap class
# loader so that the agent can instrument classes in the java.lang package.
java_binary(
    name = "mobile_harness_agent_boot_class",
    main_class = "none",
    runtime_deps = [
        # # Do NOT add deps to the following libs since they need to be loaded
        # by Java bootstrap class loader to instrument classes in the java.lang
        # package, and many classes like protobuf-generated classes can not be
        # loaded by Java bootstrap class loader.
        "//src/java/com/google/devtools/mobileharness/shared/subprocess/listener:process_builder_listener",
    ],
)
