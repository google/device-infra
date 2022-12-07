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

""" Rule for junit suite generation """

def _package_from_path(package_path):
    package = "com.google"
    if not package_path.startswith("src/javatests/com/google"):
        fail("Not in javatests", "package_path")
    return package + package_path[24:].replace("/", ".")

def junit_test_suites(
        name,
        sizes = None,
        deps = None):
    """ Generate junit suite from input

    Wrapper of native.test_suite for bazel tests.

    Args:
      name: The name used for the genrule.
      sizes: (optional; ignored in bazel tests).
      deps: The deps that are needed by each of the generated java_test targets.

    Returns:
      A rule definition that should be stored in a global whose name ends in
      `_test`.

    """

    sizes = sizes or []
    if len(deps) != 1:
        fail("Only one deps value supported", "deps")
    if deps[0][0] != ":":
        fail("Dep must be in same package", "deps")

    package = _package_from_path(native.package_name())

    tests = []
    src = native.existing_rule(deps[0][1:])
    for f in src["srcs"]:
        if f[0] != ":" or not f.endswith(".java"):
            fail("Invalid input %s" % f, "deps")
        f = f[1:-5]

        test_name = name + "_" + f
        tests.append(test_name)
        native.java_test(
            name = test_name,
            test_class = package + "." + f,
            runtime_deps = deps,
        )
    native.test_suite(
        name = name,
        tests = tests,
    )
