/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.platform.android.xts.common.util;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Utility class for handling device ABIs. */
public class AbiUtil {

  // List of supported abi
  public static final String ABI_ARM_V7A = "armeabi-v7a";
  public static final String ABI_ARM_64_V8A = "arm64-v8a";
  public static final String ABI_X86 = "x86";
  public static final String ABI_X86_64 = "x86_64";
  public static final String ABI_MIPS = "mips";
  public static final String ABI_MIPS64 = "mips64";
  public static final String ABI_RISCV64 = "riscv64";

  // List of supported architectures
  public static final String BASE_ARCH_ARM = "arm";
  public static final String ARCH_ARM64 = BASE_ARCH_ARM + "64";
  public static final String BASE_ARCH_X86 = "x86";
  public static final String ARCH_X86_64 = BASE_ARCH_X86 + "_64";
  public static final String BASE_ARCH_MIPS = "mips";
  public static final String ARCH_MIPS64 = BASE_ARCH_MIPS + "64";
  public static final String ARCH_RISCV64 = "riscv64";

  /** The set of 32Bit ABIs. */
  private static final Set<String> abis32bit = new LinkedHashSet<>();

  /** The set of 64Bit ABIs. */
  private static final Set<String> abis64bit = new LinkedHashSet<>();

  /** The set of ARM ABIs. */
  protected static final Set<String> armAbis = new LinkedHashSet<>();

  /** The set of Intel ABIs. */
  private static final Set<String> intelAbis = new LinkedHashSet<>();

  /** The set of Mips ABIs. */
  private static final Set<String> mipsAbis = new LinkedHashSet<>();

  /** The set of Risc-V ABIs. */
  private static final Set<String> riscvAbis = new LinkedHashSet<>();

  /** The set of ABI names which Compatibility supports. */
  protected static final Set<String> abisSupportedByCompatibility = new LinkedHashSet<>();

  /** The set of Architecture supported. */
  private static final Set<String> archSupported = new LinkedHashSet<>();

  /** The map of architecture to ABI. */
  private static final Map<String, Set<String>> archToAbis = new LinkedHashMap<>();

  private static final Map<String, String> abiToArch = new LinkedHashMap<>();

  private static final Map<String, String> abiToBaseArch = new LinkedHashMap<>();

  static {
    abis32bit.add(ABI_ARM_V7A);
    abis32bit.add(ABI_X86);
    abis32bit.add(ABI_MIPS);

    abis64bit.add(ABI_ARM_64_V8A);
    abis64bit.add(ABI_X86_64);
    abis64bit.add(ABI_MIPS64);
    abis64bit.add(ABI_RISCV64);

    armAbis.add(ABI_ARM_64_V8A);
    armAbis.add(ABI_ARM_V7A);

    intelAbis.add(ABI_X86_64);
    intelAbis.add(ABI_X86);

    mipsAbis.add(ABI_MIPS64);
    mipsAbis.add(ABI_MIPS);

    riscvAbis.add(ABI_RISCV64);

    archToAbis.put(BASE_ARCH_ARM, armAbis);
    archToAbis.put(ARCH_ARM64, armAbis);
    archToAbis.put(BASE_ARCH_X86, intelAbis);
    archToAbis.put(ARCH_X86_64, intelAbis);
    archToAbis.put(BASE_ARCH_MIPS, mipsAbis);
    archToAbis.put(ARCH_MIPS64, mipsAbis);
    archToAbis.put(ARCH_RISCV64, riscvAbis);

    abisSupportedByCompatibility.addAll(armAbis);
    abisSupportedByCompatibility.addAll(intelAbis);
    abisSupportedByCompatibility.addAll(mipsAbis);
    abisSupportedByCompatibility.addAll(riscvAbis);

    abiToArch.put(ABI_ARM_V7A, BASE_ARCH_ARM);
    abiToArch.put(ABI_ARM_64_V8A, ARCH_ARM64);
    abiToArch.put(ABI_X86, BASE_ARCH_X86);
    abiToArch.put(ABI_X86_64, ARCH_X86_64);
    abiToArch.put(ABI_MIPS, BASE_ARCH_MIPS);
    abiToArch.put(ABI_MIPS64, ARCH_MIPS64);
    abiToArch.put(ABI_RISCV64, ARCH_RISCV64);

    abiToBaseArch.put(ABI_ARM_V7A, BASE_ARCH_ARM);
    abiToBaseArch.put(ABI_ARM_64_V8A, BASE_ARCH_ARM);
    abiToBaseArch.put(ABI_X86, BASE_ARCH_X86);
    abiToBaseArch.put(ABI_X86_64, BASE_ARCH_X86);
    abiToBaseArch.put(ABI_MIPS, BASE_ARCH_MIPS);
    abiToBaseArch.put(ABI_MIPS64, BASE_ARCH_MIPS);
    abiToBaseArch.put(ABI_RISCV64, ABI_RISCV64);

    archSupported.add(BASE_ARCH_ARM);
    archSupported.add(ARCH_ARM64);
    archSupported.add(BASE_ARCH_X86);
    archSupported.add(ARCH_X86_64);
    archSupported.add(BASE_ARCH_MIPS);
    archSupported.add(ARCH_MIPS64);
    archSupported.add(ARCH_RISCV64);
  }

  /** Private constructor to avoid instantiation. */
  private AbiUtil() {}

  /**
   * Gets the set of ABIs associated with the given architecture.
   *
   * @param arch The architecture to look up.
   * @return a new Set containing the ABIs.
   */
  public static Set<String> getAbisForArch(String arch) {
    if (isNullOrEmpty(arch) || !archToAbis.containsKey(arch)) {
      return getAbisSupportedByCompatibility();
    }
    return new LinkedHashSet<>(archToAbis.get(arch));
  }

  /** Gets the architecture matching the abi. */
  public static String getArchForAbi(String abi) {
    if (isNullOrEmpty(abi)) {
      throw new IllegalArgumentException("Abi cannot be null or empty");
    }
    return abiToArch.get(abi);
  }

  /** Gets the base architecture matching the abi. */
  public static String getBaseArchForAbi(String abi) {
    if (isNullOrEmpty(abi)) {
      throw new IllegalArgumentException("Abi cannot be null or empty");
    }
    return abiToBaseArch.get(abi);
  }

  /** Gets the set of ABIs supported by Compatibility. */
  public static Set<String> getAbisSupportedByCompatibility() {
    return new LinkedHashSet<>(abisSupportedByCompatibility);
  }

  /** Gets the set of supported architecture representations. */
  public static Set<String> getArchSupported() {
    return new LinkedHashSet<>(archSupported);
  }

  /**
   * Checks if the given ABI is supported by compatibility.
   *
   * @param abi The ABI name to test.
   */
  public static boolean isAbiSupportedByCompatibility(String abi) {
    return abisSupportedByCompatibility.contains(abi);
  }

  /**
   * Creates a flag for the given ABI.
   *
   * @param abi the ABI to create the flag for.
   * @return a string which can be add to a command sent to ADB.
   */
  public static String createAbiFlag(String abi) {
    if (isNullOrEmpty(abi) || !isAbiSupportedByCompatibility(abi)) {
      return "";
    }
    return String.format("--abi %s ", abi);
  }

  /**
   * Creates a unique id from the given ABI and name.
   *
   * @param abi The ABI to use.
   * @param name The name to use.
   * @return a string which uniquely identifies a run.
   */
  public static String createId(String abi, String name) {
    return String.format("%s %s", abi, name);
  }

  /**
   * Parses a unique id into the ABI and name.
   *
   * @param id The id to parse.
   * @return a string list containing the ABI and name.
   */
  public static ImmutableList<String> parseId(String id) {
    if (id == null || !id.contains(" ")) {
      return ImmutableList.of("", "");
    }
    return Splitter.on(' ').splitToStream(id).collect(toImmutableList());
  }

  /** Gets the test name portion of the test id. e.g. armeabi-v7a android.mytest = android.mytest */
  public static String parseTestName(String id) {
    return parseId(id).get(1);
  }

  /** Gets the abi portion of the test id. e.g. armeabi-v7a android.mytest = armeabi-v7a */
  public static String parseAbi(String id) {
    return parseId(id).get(0);
  }

  /**
   * Gets the bitness of the ABI with the given name.
   *
   * @param abi The name of the ABI.
   */
  public static String getBitness(String abi) {
    return abis32bit.contains(abi) ? "32" : "64";
  }

  /**
   * Gets a List of Strings containing valid ABIs.
   *
   * @param unsupportedAbiDescription A comma separated string containing abis.
   */
  public static Set<String> parseAbiList(String unsupportedAbiDescription) {
    Set<String> abiSet = new HashSet<>();
    List<String> descSegments = Splitter.on(":").splitToList(unsupportedAbiDescription);
    if (descSegments.size() == 2) {
      for (String abi : Splitter.on(",").splitToList(descSegments.get(1))) {
        String trimmedAbi = abi.trim();
        if (isAbiSupportedByCompatibility(trimmedAbi)) {
          abiSet.add(trimmedAbi);
        }
      }
    }
    return abiSet;
  }

  /**
   * Gets a List of Strings containing valid ABIs.
   *
   * @param abiListProp A comma separated list containing abis coming from the device property.
   */
  public static Set<String> parseAbiListFromProperty(String abiListProp) {
    Set<String> abiSet = new HashSet<>();
    if (abiListProp == null) {
      return abiSet;
    }
    List<String> abiList = Splitter.on(",").splitToList(abiListProp);
    for (String abi : abiList) {
      String trimmedAbi = abi.trim();
      if (isAbiSupportedByCompatibility(trimmedAbi)) {
        abiSet.add(trimmedAbi);
      }
    }
    return abiSet;
  }

  /** Gets the Set of ABIs supported by the host machine. */
  public static Set<String> getHostAbi() throws MobileHarnessException, InterruptedException {
    CommandExecutor cmdExecutor = new CommandExecutor();
    try {
      String mainAbi = cmdExecutor.run(Command.of("uname", "-m")).trim();
      return getAbisForArch(mainAbi);
    } catch (CommandException e) {
      throw new MobileHarnessException(
          ExtErrorId.ABI_UTIL_GET_HOST_ABI_ERROR, "Failed to get host ABI", e);
    }
  }
}
