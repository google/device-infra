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

package com.google.devtools.mobileharness.shared.util.system;

import static com.google.common.base.StandardSystemProperty.JAVA_HOME;
import static com.google.common.base.StandardSystemProperty.OS_VERSION;
import static com.google.common.base.StandardSystemProperty.USER_NAME;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.common.graph.Traverser;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandFailureException;
import com.google.devtools.mobileharness.shared.util.command.java.JavaCommandCreator;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.errorprone.annotations.DoNotCall;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Utility class for system operations. */
public class SystemUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The JVM environment variable which specifies the test output directory. */
  public static final String ENV_TEST_UNDECLARED_OUTPUTS_DIR = "TEST_UNDECLARED_OUTPUTS_DIR";

  /** The JVM environment variable which specifies the test output directory. */
  public static final String ENV_TEST_UNDECLARED_OUTPUTS_ANNOTATIONS_DIR =
      "TEST_UNDECLARED_OUTPUTS_ANNOTATIONS_DIR";

  /** The JVM environment variable which specifies the test diagnostics output directory. */
  public static final String ENV_TEST_DIAGNOSTICS_OUTPUT_DIR = "TEST_DIAGNOSTICS_OUTPUT_DIR";

  /**
   * The JVM environment variable which specifies where the Sponge infrastructure failure file is
   * written.
   */
  public static final String ENV_TEST_INFRASTRUCTURE_FAILURE_FILE =
      "TEST_INFRASTRUCTURE_FAILURE_FILE";

  /** The JVM environment variable which specifies the test components directory. */
  public static final String ENV_TEST_COMPONENTS_DIR = "TEST_COMPONENTS_DIR";

  /** Error message that no matching process when running "killall xxx" on Mac. */
  @VisibleForTesting static final String ERROR_MSG_NO_MATCHING_PROCESSES = "No matching processes";

  /** Error message that no matching service when running "launchctl kill SIGKILL xxx" on Mac. */
  private static final String ERROR_MSG_NO_MATCHING_SERVICE = "No process to signal.";

  /** Error message that no matching process when running "killall xxx" when run on Linux. */
  @VisibleForTesting static final String ERROR_MSG_NO_PROCESS_FOUND = "no process found";

  private static final Pattern NO_SUCH_PROCESS_PATTERN =
      Pattern.compile("kill: (.+) No such process");

  private static final Pattern HARDWARE_UUID_PATTERN = Pattern.compile("Hardware UUID: (.+)");

  // For output of "ps xao pid,ppid,pgid".
  private static final Pattern PS_PID_PPID_PGID_HEADER_PATTERN =
      Pattern.compile("^\\s*USER\\s+PID\\s+PPID\\s+PGID\\s+COMMAND\\s*$");
  private static final Pattern PS_PID_PPID_PGID_OUTPUT_PATTERN =
      Pattern.compile(
          "^\\s*"
              + "(?<user>[^\\s]+)\\s+(?<pid>\\d+)\\s+(?<ppid>\\d+)\\s+(?<pgid>\\d+)\\s+(?<command>.+)"
              + "$");

  @VisibleForTesting static final String GUITAR_CHANGELIST_ENV_VAR = "GUITAR_CHANGELIST";
  private static final Pattern CHANGELIST_PATTERN = Pattern.compile("^\\d+$");
  private static volatile boolean processIsShuttingDown;

  private String osName = System.getProperty("os.name");
  private final CommandExecutor executor;

  /** Kill signals. */
  public enum KillSignal {
    // Interrupts a process. (The default action is to terminate gracefully). This too, like,
    // SIGTERM can be handled, ignored or caught. The difference between SIGINT and SIGTERM is that
    // the former can be sent from a terminal as input characters. This is the signal generated when
    // a user presses Ctrl+C.
    SIGINT(2),
    // Terminates a process immediately. This signal cannot be handled (caught), ignored or blocked.
    SIGKILL(9),
    // Terminates a process immediately. However, this signal can be handled, ignored or caught in
    // code. If the signal is not caught by a process, the process is killed. Also, this is used for
    // graceful termination of a process.
    SIGTERM(15);

    private final int value;

    KillSignal(int value) {
      this.value = value;
    }

    public int value() {
      return value;
    }
  }

  /** The disk type of the host. */
  public enum DiskType {
    UNKNOWN(0),
    HDD(1),
    SSD(2);

    private final int value;

    DiskType(int value) {
      this.value = value;
    }

    public int value() {
      return value;
    }
  }

  public SystemUtil() {
    this(new CommandExecutor());
  }

  @VisibleForTesting
  SystemUtil(CommandExecutor executor) {
    this.executor = executor;
  }

  /** Checks whether the system supports display. */
  public boolean supportDisplay() {
    return !Strings.isNullOrEmpty(System.getenv("DISPLAY"));
  }

  /** Terminates the currently running Java Virtual Machine. */
  @SuppressWarnings("SystemExitOutsideMain")
  public void exit(ExitCode exitCode) {
    logger.atInfo().log("Exit code: %d %s", exitCode.code(), exitCode.name());
    System.exit(exitCode.code());
  }

  /** Logs the exception and terminates the currently running Java Virtual Machine. */
  public void exit(ExitCode exitCode, Throwable e) {
    logger.atSevere().withCause(e).log("FATAL ERROR");
    exit(exitCode);
  }

  /** Logs the message and terminates the currently running Java Virtual Machine. */
  public void exit(ExitCode exitCode, String severeMsg) {
    logger.atSevere().log("%s", severeMsg);
    exit(exitCode);
  }

  /** Logs the message and terminates the currently running Java Virtual Machine. */
  public void exit(ExitCode exitCode, Level level, String msg) {
    logger.at(level).log("%s", msg);
    exit(exitCode);
  }

  /** Logs the message and exception, terminates the currently running Java Virtual Machine. */
  public void exit(ExitCode exitCode, String severeMsg, Throwable e) {
    logger.atSevere().withCause(e).log("%s", severeMsg);
    exit(exitCode);
  }

  /** Get the user who run the current process. */
  public String getUser() {
    // Try system env "USER" first unless it is not set, fall back to "user.name" instead.

    // Some experimental results that show "user.name" can not always correctly reflect the truth:
    // 1. Everything is right:
    //         exec("logname") = [mobileharness]
    //    getprop("user.name") = [mobileharness]
    //          getenv("USER") = [mobileharness]
    // 2. user.name returns "?"
    //         exec("logname") = [root]
    //    getprop("user.name") = [?]
    //          getenv("USER") = [root]
    // 3. no logname output:
    //         exec("logname") = [ERROR: logname: no login name]
    //    getprop("user.name") = [root]
    //          getenv("USER") = [root]
    // 4. no logname output and user.name returns "?":
    //         exec("logname") = [ERROR: logname: no login name]
    //    getprop("user.name") = [?]
    //          getenv("USER") = [mobileharness]
    // 5. no logname output and env "USER" returns "null" :
    //         exec("logname") = [ERROR: logname: no login name]
    //    getprop("user.name") = [root]
    //          getenv("USER") = [null]

    String userName = System.getenv("USER");
    if (userName != null && !userName.isEmpty()) {
      return userName;
    }

    return USER_NAME.value();
  }

  /** Returns true if the current program is running as root. */
  public boolean isRunAsRoot() {
    return getUser().equals("root");
  }

  /** Returns true if the current program is running as root. */
  public boolean runAsRoot() {
    return isRunAsRoot();
  }

  /** Returns the OS name. */
  public String getOsName() {
    return osName;
  }

  /** Overrides the OS name for testing, e.g. to simulate Mac-specific behavior under test. */
  // TODO: This @VisibleForTesting annotation was being ignored by prod code.
  // Please check that removing it is correct, and remove this comment along with it.
  // @VisibleForTesting
  public void setOsNameForTest(String osName) {
    this.osName = osName;
  }

  /** Returns the OS version. */
  public String getOsVersion() {
    return OS_VERSION.value();
  }

  /** Returns whether the OS is Mac OS X. */
  public boolean isOnMac() {
    return getOsName().startsWith("Mac OS X");
  }

  /** Returns whether the OS is Linux. */
  public boolean isOnLinux() {
    return getOsName().startsWith("Linux");
  }

  /**
   * Checks whether the KVM is enabled.
   *
   * <p>KVM is enabled when the following 3 requirements are all met.
   *
   * <ol>
   *   <li>On linux.
   *   <li>File /proc/cpuinfo contains the flags indicating that the CPU has the VT capability
   *   <li>File /dev/kvm exists.
   * </ol>
   *
   * @see <a href="http://manpages.ubuntu.com/manpages/bionic/man1/kvm-ok.1.html">kvm-ok manual</a>
   */
  public boolean isKvmEnabled() throws MobileHarnessException, InterruptedException {
    if (!isOnLinux()) {
      return false;
    }

    try {
      return Integer.parseInt(
                  executor
                      .exec(Command.of("grep", "-E", "-c", "'svm|vmx|0xc0f'", "/proc/cpuinfo"))
                      .stdoutWithoutTrailingLineTerminator())
              > 0
          && Files.exists(Paths.get("/dev/kvm"));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_CHECK_KVM_ERROR, "Failed to check kvm.", e);
    }
  }

  /** Returns whether QEMU is installed. */
  public boolean isQemuInstalled() throws InterruptedException {
    try {
      executor.run(Command.of("qemu-system-x86_64", "--version"));
      return true;
    } catch (CommandException e) {
      return false;
    }
  }

  /** Returns the java binary path. */
  public String getJavaBin() {
    String javaBin = getEnv("JAVABIN");
    if (javaBin == null) {
      String javaHome = JAVA_HOME.value();
      if (!Strings.isNullOrEmpty(javaHome)) {
        // When run on m&m lab, we should get the java home directory and locate java from it.
        javaBin = PathUtil.join(System.getProperty("java.home"), "bin", "java");
      } else if (isOnMac()) {
        javaBin = "java";
      } else if (isOnLinux()) {
        javaBin = "/usr/local/buildtools/java/jdk/bin/java";
      }
    }
    return javaBin;
  }

  /**
   * Returns a {@link JavaCommandCreator} for creating Java commands, based on the current
   * environment and the Java launcher path.
   */
  public JavaCommandCreator getJavaCommandCreator() {
    return JavaCommandCreator.of(/* useStandardInvocationForm= */ true, getJavaBin());
  }

  /**
   * Runs lsof(list open files) command.
   *
   * @return output of the lsof command, showing the following info of each open file: COMMAND PID
   *     USER FD TYPE DEVICE SIZE NODE NAME
   */
  @DoNotCall("This method may consume GB memory.")
  public final String listOpenFiles() throws MobileHarnessException, InterruptedException {
    try {
      return executor.exec(Command.of("lsof")).stdoutWithoutTrailingLineTerminator();
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_LIST_OPEN_FILES_ERROR, "Failed to list open files.", e);
    }
  }

  /** Returns the process information which is the output of 'ps aux'. */
  public String getProcesses() throws MobileHarnessException, InterruptedException {
    String output = "";
    int maxAttempts = 3;
    // This command is flaky in FoM. b/67465627
    for (int i = 0; i < maxAttempts; ++i) {
      try {
        output = executor.exec(Command.of("ps", "aux")).stdoutWithoutTrailingLineTerminator();
        if (output.split("\n").length > 1) {
          break;
        }
      } catch (CommandException e) {
        if (i < maxAttempts - 1) {
          continue;
        }
        throw new MobileHarnessException(
            BasicErrorId.SYSTEM_LIST_PROCESSES_ERROR, "Failed to list processes.", e);
      }
    }
    return output;
  }

  /**
   * Shows full information of the given process.
   *
   * @return output of the "ps -fp processId" command
   */
  public String getProcessInfo(int processId) throws MobileHarnessException, InterruptedException {
    try {
      return executor
          .exec(Command.of("ps", "-fp", String.valueOf(processId)))
          .stdoutWithoutTrailingLineTerminator();
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_GET_GIVEN_PROCESS_INFO_ERROR,
          String.format("Failed to get process info for process id `%s`", processId),
          e);
    }
  }

  /**
   * Gets the IDs of the processes who have all the given keywords in its "ps aux" output.
   *
   * @param keywords the keywords to filter the processes
   * @return the IDs of the processes, or empty if no process has the keywords
   */
  public Set<Integer> getProcessIds(String... keywords)
      throws MobileHarnessException, InterruptedException {
    Set<Integer> processIds = new HashSet<>();
    String output = getProcesses();
    // Example output on Linux:
    // USER    PID %CPU %MEM    VSZ   RSS TTY      STAT START   TIME COMMAND
    // root   2470  0.0  0.0  19236   816 ?        Ss   Sep25   0:00 rpcbind
    // statd  2489  0.0  0.0  21544   772 ?        Ss   Sep25   0:00 rpc.statd -L
    // root   2553  0.0  0.0  15524   568 ?        S    Sep25   0:00 upstart-socket-bridge
    // root   2556  0.0  0.0  17132   588 ?        S    Sep25   0:00 upstart-file-bridge --daemon
    // root   2612  0.0  0.0  87640   692 ?        S<sl Sep25   0:01 /sbin/auditd
    // root   2614  0.0  0.0  80260   912 ?        S<sl Sep25   0:01 /sbin/audispd
    //
    // Another example output on Mac:
    // USER   PID  %CPU %MEM      VSZ    RSS   TT  STAT STARTED      TIME COMMAND
    // root   491   0.0  0.0  2460424    272   ??  S    Wed04PM   0:00.01 /usr/sbin/pboard
    // root   291   0.0  0.0  2471576   3892   ??  Ss   Wed04PM   0:23.05 /sbin/launchd
    // root   106   0.0  0.0  2470388   1192   ??  Ss   Wed04PM   0:00.44 autofsd
    @SuppressWarnings("StringSplitter")
    String[] lines = output.split("\n");
    if (lines.length < 1) {
      return processIds;
    }
    // Skips the first line. It should be the header.
    String[] words = lines[0].split("\\s+");
    if (words.length < 2 || !words[0].equals("USER") || !words[1].equals("PID")) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_PROCESS_HEADER_NOT_FOUND,
          "Unexpected command output: header not found\n" + output);
    }

    for (int i = 1; i < lines.length; i++) {
      String line = lines[i];
      boolean matches = true;
      for (String keyword : keywords) {
        if (!line.contains(keyword)) {
          matches = false;
          break;
        }
      }
      if (matches) {
        words = line.split("\\s+");
        if (words.length < 2) {
          throw new MobileHarnessException(
              BasicErrorId.SYSTEM_INVALID_PROCESS_INFO_LINE,
              "\"" + line + "\" is not a valid process info line:\n" + output);
        }
        try {
          processIds.add(Integer.valueOf(words[1]));
        } catch (NumberFormatException e) {
          throw new MobileHarnessException(
              BasicErrorId.SYSTEM_INVALID_PROCESS_ID,
              "\"" + words[1] + "\" is not a valid process ID:\n" + output,
              e);
        }
      }
    }
    return processIds;
  }

  /** Returns the process information which contains the keywords. */
  public String getProcessesByKeywords(String... keywords)
      throws MobileHarnessException, InterruptedException {
    String processes = "";
    String output = getProcesses();
    List<String> lines = Splitter.on('\n').splitToList(output);
    if (lines.size() < 1) {
      return processes;
    }
    // Skips the first line. It should be the header.
    List<String> words = Splitter.onPattern("\\s+").splitToList(lines.get(0));
    if (words.size() < 2 || !words.get(0).equals("USER") || !words.get(1).equals("PID")) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_PROCESS_HEADER_NOT_FOUND,
          "Unexpected command output: header not found\n" + output);
    }

    for (int i = 1; i < lines.size(); i++) {
      String line = lines.get(i);
      boolean matches = true;
      for (String keyword : keywords) {
        if (!line.contains(keyword)) {
          matches = false;
          break;
        }
      }
      if (matches) {
        processes += line + "\n";
      }
    }
    return processes;
  }

  /**
   * Gets the IDs of the processes which are listening the given port.
   *
   * @return the IDs of the processes, or empty if there is no process listening the given port
   */
  public Set<Integer> getProcessesByPort(int port)
      throws MobileHarnessException, InterruptedException {
    Set<Integer> processIds = new HashSet<>();
    String output;
    try {
      output =
          executor.exec(Command.of("lsof", "-i", ":" + port)).stdoutWithoutTrailingLineTerminator();
    } catch (CommandException e) {
      // If there is no process using the given port, the above command will throw out exception.
      // Catches it and return the empty set.
      return processIds;
    }
    /* Example output:
     * COMMAND   PID USER   FD   TYPE DEVICE SIZE/OFF NODE NAME
     * java    20844 root   15w  IPv6 108044      0t0  TCP mh1.bej...:9999->gntj... (ESTABLISHED)
     * java    20844 root   39r  IPv6 106603      0t0  TCP *:9999 (LISTEN)
     * java    20844 root   41r  IPv6 106604      0t0  UDP *:9999
     *
     * Another example output on mac:
     * lsof: WARNING: can't stat() fuse4x file system /private/var/folders/.../device_document_mount
     * Output information may be incomplete.
     * assuming "dev=2f000004" from mount table
     * COMMAND   PID USER   FD   TYPE             DEVICE SIZE/OFF NODE NAME
     * java    75490 root   34u  IPv6 0xe7d7ab1bdb900b69      0t0  TCP dhcp...:52699->...
     */
    @SuppressWarnings("StringSplitter")
    String[] lines = output.split("\n");
    if (lines.length < 1) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_NO_PROCESS_FOUND_BY_PORT,
          "Command output less than 1 line:\n" + output);
    }
    int i;
    // Finds the header.
    for (i = 0; i < lines.length; i++) {
      @SuppressWarnings("StringSplitter")
      String[] words = lines[i].split("\\s+");
      if (words.length >= 2 && words[0].equals("COMMAND") && words[1].equals("PID")) {
        break;
      }
    }
    if (i >= lines.length) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_PROCESS_HEADER_NOT_FOUND,
          "Unexpected command output: header not found:\n" + output);
    }
    // Parses the content under the header.
    for (i++; i < lines.length; i++) {
      String line = lines[i].trim();
      if (!line.endsWith("(LISTEN)")) {
        continue;
      }
      @SuppressWarnings("StringSplitter")
      String[] words = line.split("\\s+");
      try {
        processIds.add(Integer.valueOf(words[1]));
      } catch (NumberFormatException e) {
        throw new MobileHarnessException(
            BasicErrorId.SYSTEM_INVALID_PROCESS_ID,
            "\"" + words[1] + "\" is not a valid process ID:\n" + output,
            e);
      }
    }
    return processIds;
  }

  /**
   * Kills descendant processes with the given parent ID (parent process won't be killed) and
   * potentially zombie processes.
   *
   * <p>Note that "pkill -P $ppid" won't work since it only kills direct child processes.
   *
   * <p>This method tries killing the "process tree" whose root is {@code parentProcessId} first.
   * Then it also tries killing potentially "zombie" processes (and their descendants) whose ppid is
   * 1 (init process) and in the same process group as {@code parentProcessId}.
   */
  public void killDescendantAndZombieProcesses(int parentProcessId, KillSignal killSignal)
      throws MobileHarnessException, InterruptedException {
    killDescendantAndZombieProcesses(
        parentProcessId,
        killSignal,
        /* ancestorsOfParentProcess= */ new HashSet<>(),
        /* killZombie= */ true);
  }

  private void killDescendantAndZombieProcesses(
      int parentProcessId,
      KillSignal killSignal,
      Set<Integer> ancestorsOfParentProcess,
      boolean killZombie)
      throws MobileHarnessException, InterruptedException {
    // Let's say a process node is with "name(pid,pgid)" and the process tree looks like this:
    //
    //               A(1,1)
    //              /   |  \
    //        B(2,2) C(3,3) D(4,6)
    //       /   |  \          |
    // E(5,5) F(6,6) G(7,7) H(8,6)
    //       /      \
    // I(9,6)        J(10,6)
    //              /       \
    //       L(11,6)         M(12,6)
    //          |
    //       N(13,6)
    //
    // Call killDescendantAndZombieProcesses(10) to kill descendants of J, it kills N, L, M, then
    // potentially zombie process D and its descendant H because:
    //   1. D's pgid=6, same as J
    //   2. D's ppid=1
    //   3. D is not an ancestor of J
    //
    // Disclaimer: "Init process" pid might not be 1. If we're on a goobuntu's GUI (ssh should be
    // fine), the process tree would be like:
    // ---------------------------------------------------------------------------------------------
    // USER       PID  PPID  PGID COMMAND
    // root      4757     1  4757 lightdm
    // root      6806  4757  4757 lightdm --session-child 12 19
    // dxu       7150  6806  7150 init --user
    // ...
    // ---------------------------------------------------------------------------------------------
    // then e.g. if J is killed the ppid of L and M will be 7150 instead of 1. As a result L, M, N
    // will keep running and won't be considered "zombie". There's nothing we can do since the
    // "init process" pid changes every time.
    String output;
    try {
      output =
          executor
              .exec(Command.of("ps", "xao", "user,pid,ppid,pgid,command"))
              .stdoutWithoutTrailingLineTerminator();
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_LIST_PROCESSES_ERROR, "Failed to list processes.", e);
    }
    // Format output with only: process id (pid), parent process id (ppid), process group id (pgid).
    //
    // Example output:
    // USER   PID PPID  PGID COMMAND
    // root     1    0     1 foo
    // dxu  32453    1 32453 bar
    // ...
    @SuppressWarnings("StringSplitter")
    String[] lines = output.split("\n");
    if (lines.length < 2) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_INVALID_PROCESS_LIST_ERROR, "Error listing processes:\n" + output);
    } else {
      // First line should be the header.
      Matcher matcher = PS_PID_PPID_PGID_HEADER_PATTERN.matcher(lines[0]);
      if (!matcher.find()) {
        throw new MobileHarnessException(
            BasicErrorId.SYSTEM_UNEXPECTED_PROCESS_HEADER, "Unexpected header:\n" + output);
      }
    }

    ProcessInfo parentProcessInfo = null;
    // {ppid : [ProcessInfo]}
    ListMultimap<Integer, ProcessInfo> processTree = ArrayListMultimap.create();
    // {pid : ppid} which is only used to calculate ancestors of the given parent process, whose
    // ancestors won't be considered "zombie".
    Map<Integer, Integer> pidToPpid = new HashMap<>();
    for (String line : lines) {
      Matcher matcher = PS_PID_PPID_PGID_OUTPUT_PATTERN.matcher(line);
      // Constructs processTree.
      if (matcher.find()) {
        String user = matcher.group("user");
        int pid = Integer.parseInt(matcher.group("pid"));
        int ppid = Integer.parseInt(matcher.group("ppid"));
        int pgid = Integer.parseInt(matcher.group("pgid"));
        String command = matcher.group("command").trim();
        ProcessInfo processInfo = new ProcessInfo(user, pid, ppid, pgid, command);
        processTree.put(ppid, processInfo);
        pidToPpid.put(pid, ppid);
        if (pid == parentProcessId) {
          parentProcessInfo = processInfo;
        }
      }
    }
    if (parentProcessInfo == null) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_PARENT_PROCESS_NOT_FOUND,
          String.format("Parent process %d not found. Output:\n%s", parentProcessId, output));
    }
    logger.atInfo().log("Processes:\n%s", output);
    logger.atInfo().log("Parent Process for this iteration: %s", parentProcessInfo);

    // Calculates all ancestors of given parent process, until init process (pid = 1). They won't be
    // considered "zombie".
    int ancestorProcessId = pidToPpid.get(parentProcessId);
    while (ancestorProcessId != 1) {
      ancestorsOfParentProcess.add(ancestorProcessId);
      ancestorProcessId = pidToPpid.get(ancestorProcessId);
    }

    // Constructs a Traverser from processTree.
    Traverser<ProcessInfo> treeTraverser = Traverser.forTree(n -> processTree.get(n.pid));

    // Traverses sub-tree with given parent process as root and kills its descendant processes.
    for (ProcessInfo node : treeTraverser.depthFirstPreOrder(parentProcessInfo)) {
      if (node.pid != parentProcessId) {
        try {
          killProcess(node.pid, killSignal);
          logger.atInfo().log("Killed %s", node);
        } catch (MobileHarnessException e) {
          logger.atWarning().log("Failed to kill process %s (ignored):\n%s", node, e.getMessage());
        }
      }
    }

    // Skip killing zombie to avoid infinite loop.
    if (!killZombie) {
      return;
    }

    // Kills potentially "zombie" processes which meet following criteria:
    // (1) ppid = 1 (is the direct child of the init process)
    for (ProcessInfo node : processTree.get(1)) {
      // (2) In the same process group as the given parent process
      if (node.pgid == parentProcessInfo.pgid
          // (3) Not an ancestor of the given parent process
          && !ancestorsOfParentProcess.contains(node.pid)
          && node.pid != parentProcessId
          // (4) Not the root ancestor whose pid == pgid
          && node.pid != parentProcessInfo.pgid) {
        // Now pid is considred potentially "zombie". Kills its descendants first.
        try {
          killDescendantAndZombieProcesses(
              node.pid, killSignal, ancestorsOfParentProcess, /* killZombie= */ false);
        } catch (MobileHarnessException e) {
          logger.atWarning().log(
              "Failed to kill descendants of process %s (ignored):\n%s", node, e.getMessage());
        }
        // Then kill pid itself.
        try {
          killProcess(node.pid, killSignal);
          logger.atInfo().log("Killed %s", node);
        } catch (MobileHarnessException e) {
          logger.atWarning().log("Failed to kill process %s (ignored):\n%s", node, e.getMessage());
        }
      }
    }
  }

  /** Kills the process according to the given ID. */
  public void killProcess(int processId) throws MobileHarnessException, InterruptedException {
    killProcess(processId, KillSignal.SIGKILL);
  }

  /** Kills the process according to the given ID and signal. */
  public void killProcess(int processId, KillSignal killSignal)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Killing process, pid=%s, signal=%s", processId, killSignal.value());
    try {
      executor.run(Command.of("kill", "-" + killSignal.value(), String.valueOf(processId)));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_KILL_PROCESS_ERROR,
          String.format(
              "Failed to kill process [%s] with kill signal value [%s]",
              processId, killSignal.value()),
          e);
    }
  }

  /**
   * Invokes the "killall <processName>" command. Returns true if the killall runs successfully.
   * Returns false if the process is not existed.
   */
  public boolean killAllProcesses(String processName)
      throws InterruptedException, MobileHarnessException {
    return killAllProcesses(processName, null);
  }

  /**
   * Invokes the "killall <processName>" command with signal. Returns true if the killall runs
   * successfully. Returns false if the process is not existed.
   */
  public boolean killAllProcesses(String processName, @Nullable KillSignal signal)
      throws InterruptedException, MobileHarnessException {
    Command command = Command.of("killall");
    if (signal != null) {
      command = command.argsAppended("-" + signal.value());
    }
    command = command.argsAppended(processName);
    try {
      executor.run(command);
      return true;
    } catch (CommandException e) {
      String exceptionMsg = e.toString();
      if (!exceptionMsg.contains(ERROR_MSG_NO_MATCHING_PROCESSES)
          && !exceptionMsg.contains(ERROR_MSG_NO_PROCESS_FOUND)) {
        throw new MobileHarnessException(
            BasicErrorId.SYSTEM_KILLALL_PROCESS_ERROR,
            String.format("Failed to kill the process [%s].", processName),
            e);
      }
    }
    return false;
  }

  /**
   * Adds {@code user} to udev group 'plugdev', which is the default group for accessing all usb
   * devices (such as phones). It needs to run as root. Only works on Linux.
   */
  public void addUserToUdevGroup(String user) throws MobileHarnessException, InterruptedException {
    try {
      executor.exec(Command.of("adduser", user, "plugdev"));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_ADD_USER_TO_GROUP_ERROR,
          String.format("Failed to add user `%s` to group `plugdev`.", user),
          e);
    }
    logger.atInfo().log("Added user `%s` to group `plugdev`.", user);
  }

  /** Gets default(first) group of {@code user}. */
  public String getUserGroup(String user) throws MobileHarnessException, InterruptedException {
    String output;
    try {
      output = executor.exec(Command.of("groups", user)).stdoutWithoutTrailingLineTerminator();
    } catch (CommandException e) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_GET_USER_GROUP_ERROR,
          String.format("Failed to get groups for user `%s`.", user),
          e);
    }

    String groupList;
    if (isOnMac()) {
      // The output of `groups` on Mac is group names split with single space. such as:
      //     group1 group2 group3
      groupList = output;
    } else {
      // The output of `groups` on Linux is as:
      // user : group1 group2 group3
      int index = output.indexOf(":");
      if (index < 0) {
        throw new MobileHarnessException(
            BasicErrorId.SYSTEM_FAILED_TO_GET_USER_GROUPS,
            String.format("Failed to get group of user `%s`: %s", user, output));
      }
      groupList = output.substring(index + 1).trim();
    }
    @SuppressWarnings("StringSplitter")
    String[] tokens = groupList.trim().split(" ");
    if (tokens.length < 1) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_NO_GROUPS_FOR_USER,
          String.format("This is no group of user `%s`, output: %s", user, output));
    }
    return tokens[0].trim();
  }

  /**
   * Sigkills the services which contain the serviceTargetName by launchctl on Mac. Returns true if
   * the sigkill runs successfully. Returns false if the service is not existed or it is not on Mac.
   */
  @SuppressWarnings({"StringSplitter", "FloggerWithoutCause"})
  public boolean sigkillServiceOnMac(String serviceTargetName, String serviceType)
      throws InterruptedException, MobileHarnessException {
    if (isOnMac()) {
      String userUid;
      try {
        userUid =
            executor
                .exec(Command.of("launchctl", "manageruid"))
                .stdoutWithoutTrailingLineTerminator();
      } catch (CommandException e) {
        throw new MobileHarnessException(
            BasicErrorId.SYSTEM_GET_USER_GROUP_ERROR, "Failed to get user uid on Mac.", e);
      }
      /*
       * The example output of launchctl list is:
       *
       * INFO: PID    Status  Label
       *  -   0   com.apple.CoreAuthentication.daemon
       *  -   0   com.apple.quicklook
       *  -   0   com.apple.parentalcontrols.check
       *  2480    0   com.apple.Finder
       *  -   0   com.apple.PackageKit.InstallStatus
       */
      boolean allCmdSucceeded = true;
      String[] launchctlListResult;
      try {
        launchctlListResult =
            executor
                .exec(Command.of("launchctl", "list"))
                .stdoutWithoutTrailingLineTerminator()
                .split("\n");
      } catch (CommandException e) {
        throw new MobileHarnessException(
            BasicErrorId.SYSTEM_MAC_LAUNCHCTL_LIST_ERROR, "Failed to list services on Mac.", e);
      }

      for (String serviceTargetItem : launchctlListResult) {
        if (serviceTargetItem.contains(serviceTargetName)) {
          @SuppressWarnings("StringSplitter")
          String[] items = serviceTargetItem.trim().split("\\s+");
          if (items.length != 3) {
            continue;
          }
          @SuppressWarnings("StringSplitter")
          String serviceTarget = serviceTargetItem.trim().split("\\s+")[2];

          try {
            executor.run(
                Command.of(
                    "launchctl",
                    "kill",
                    "SIGKILL",
                    String.format("%s/%s/%s", serviceType, userUid, serviceTarget)));
            logger.atInfo().log("Killed %s", serviceTarget);
          } catch (CommandException e) {
            allCmdSucceeded = false;
            if (!e.toString().contains(ERROR_MSG_NO_MATCHING_SERVICE)) {
              logger.atWarning().log("%s", e);
            }
          }
        }
      }
      return allCmdSucceeded;
    } else {
      logger.atWarning().log("It is not on Mac. Failed to sigkill the service.");
    }
    return false;
  }

  /** Returns true if the process with the given ID is running, otherwise returns false. */
  public boolean hasProcess(int processId) throws MobileHarnessException, InterruptedException {
    try {
      executor.run(Command.of("kill", "-0", String.valueOf(processId)));
      return true;
    } catch (CommandException e) {
      if (NO_SUCH_PROCESS_PATTERN.matcher(e.getMessage()).find()) {
        return false;
      }
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_KILL_PROCESS_ERROR,
          String.format("Failed to check if has process id `%s`", processId),
          e);
    }
  }

  /** Gets the logins user. */
  public String getLoginUser() throws MobileHarnessException, InterruptedException {
    String loginUser;
    try {
      loginUser = executor.exec(Command.of("logname")).stdoutWithoutTrailingLineTerminator();
    } catch (CommandException e) {
      if (e instanceof CommandFailureException) {
        if (((CommandFailureException) e).result().stdout().contains("no login name")
            && runAsRoot()) {
          return "root";
        }
      }
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_GET_LOGNAME_ERROR, "Failed to get logname.", e);
    }
    if (loginUser.isEmpty()) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_EMPTY_LOGNAME, "Unexpected command output: empty logname.");
    }
    return loginUser;
  }

  /** Returns home directory of the run-as user. */
  public String getUserHome() throws MobileHarnessException, InterruptedException {
    if (runAsRoot()) {
      return getUserHome(getLoginUser());
    }

    String homeEnv = System.getenv("HOME");

    if (homeEnv == null || isOnMac()) {
      return System.getProperty("user.home");
    }
    return homeEnv;
  }

  /** Returns home directory of user {@code userName}. Must run as root. */
  public String getUserHome(String userName) throws MobileHarnessException, InterruptedException {
    if (!runAsRoot()) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_ROOT_ACCESS_REQUIRED, "Must run as root.");
    }
    if (isOnMac()) {
      String output;
      try {
        output =
            executor
                .exec(Command.of("dscl", ".", "-read", "/Users/" + userName, "NFSHomeDirectory"))
                .stdoutWithoutTrailingLineTerminator();
      } catch (CommandException e) {
        throw new MobileHarnessException(
            BasicErrorId.SYSTEM_MAC_DSCL_CMD_ERROR, "Command dscl failed on Mac.", e);
      }

      @SuppressWarnings("StringSplitter")
      String[] array = output.split(" ");
      if (array.length != 2) {
        throw new MobileHarnessException(
            BasicErrorId.SYSTEM_USER_HOME_NOT_FOUND, "Failed to find the user home.");
      }
      return array[1];
    } else if (isOnLinux()) {
      String output;
      try {
        output =
            executor
                .exec(Command.of("getent", "passwd", userName))
                .stdoutWithoutTrailingLineTerminator();
      } catch (CommandException e) {
        throw new MobileHarnessException(
            BasicErrorId.SYSTEM_GETENT_CMD_ERROR, "Command getent failed.", e);
      }
      // **DO NOT use String.split(String) here, because the method will ignore the trailing empty
      // strings. For example, ":::".split(":").length is 0. (b/35928162).
      String[] array = output.split(":", -1);
      if (array.length != 7) {
        throw new MobileHarnessException(
            BasicErrorId.SYSTEM_USER_HOME_NOT_FOUND, "Failed to find the user home: " + output);
      }
      return array[5];
    } else {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_NOT_RUN_ON_LINUX_OR_MAC, "Unknown Operation system.");
    }
  }

  /** Gets the test undeclared output directory from system environment. */
  @Nullable
  public String getTestUndeclaredOutputsAnnotationsDir() {
    return System.getenv(ENV_TEST_UNDECLARED_OUTPUTS_ANNOTATIONS_DIR);
  }

  /** Gets the test undeclared output directory from system environment. */
  @Nullable
  public String getTestUndeclaredOutputDir() {
    return System.getenv(ENV_TEST_UNDECLARED_OUTPUTS_DIR);
  }

  /** Gets the test diagnostics output directory from system environment. */
  @Nullable
  public String getTestDiagnosticsOutputDir() {
    return System.getenv(ENV_TEST_DIAGNOSTICS_OUTPUT_DIR);
  }

  /** Gets the test components output directory from system environment. */
  @Nullable
  public String getTestComponentsOutputDir() {
    return System.getenv(ENV_TEST_COMPONENTS_DIR);
  }

  /** Returns the env var to the given key. */
  @Nullable
  public String getEnv(String key) {
    return System.getenv(key);
  }

  /**
   * When the current process is running as root, convert the given command to force it run as the
   * login user instead of root.
   */
  public String[] toNonRootCmd(String[] command)
      throws MobileHarnessException, InterruptedException {
    return toNonRootCmd(command, true);
  }

  /**
   * When the current process is running as root, convert the given command to force it run as the
   * login user instead of root.
   */
  public String[] toNonRootCmd(String[] command, boolean withEnvVars)
      throws MobileHarnessException, InterruptedException {
    if (isRunAsRoot()) {
      return toNonRootCmd(command, getLoginUser(), withEnvVars);
    }
    return command;
  }

  /**
   * When the current process is running as root, convert the given command to force it run as the
   * login user instead of root.
   */
  public String[] toNonRootCmd(String[] command, String user, boolean withEnvVars)
      throws MobileHarnessException, InterruptedException {
    if (isRunAsRoot()) {
      List<String> commandList = new ArrayList<>();
      Collections.addAll(commandList, "sudo");
      // "-E" is to pass the environment variables which are set by root to login user.
      // What will be preserved to target user, depense on /etc/sudoers. Some host will preserve
      // HOME environment which could cause |getUserHome| fails.
      if (withEnvVars) {
        Collections.addAll(commandList, "-E");
      }
      // From "sudo --help":
      //   -H, --set-home  set HOME variable to target user's home dir
      //
      // If don't set it, method |getUserHome| fails to get the right home directory (b/64704813).
      // This flag won't impact lab server on Mac, which depense on Java property "user.home".
      Collections.addAll(commandList, "-H");
      Collections.addAll(commandList, "-u", user);
      // After sudo -u username you should append "--" (without the quotes) to the command line
      // arguments. This tells sudo that what follows is the command to run with its' arguments.
      // If you don't do this then any arguments that follow could be interpreted as sudo arguments.
      Collections.addAll(commandList, "--");
      Collections.addAll(commandList, command);
      logger.atInfo().log("%s", commandList);
      return commandList.toArray(new String[commandList.size()]);
    }
    return command;
  }

  /**
   * When the current process is running as root, convert the given command to force it run as the
   * login user instead of root. The command will run without any environment variable.
   */
  public String[] toNonRootCmdWithoutEnvVar(String[] command)
      throws MobileHarnessException, InterruptedException {
    return toNonRootCmd(command, false);
  }

  /** Gets the java command path on the running machine. */
  public String getJavaCommandPath() {
    return Flags.instance().javaCommandPath.getNonNull();
  }

  /** Gets the total amount of memory in bytes on the local system. */
  public long getTotalMemory() throws MobileHarnessException, InterruptedException {
    if (isOnLinux()) {
      return getProcMemInfoTag("MemTotal");
    } else if (isOnMac()) {
      String output;
      try {
        output = executor.run(Command.of("sysctl", "-n", "hw.memsize")).trim();
      } catch (CommandException e) {
        throw new MobileHarnessException(
            BasicErrorId.SYSTEM_MAC_GET_MEMORY_SIZE_ERROR, "Failed to get memory size on Mac.", e);
      }
      return Long.parseLong(output);
    } else {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_NOT_RUN_ON_LINUX_OR_MAC, "Unsupported Operation system.");
    }
  }

  /** Returns the disk type of the host. It only works on macOS. */
  public DiskType getDiskType() throws MobileHarnessException, InterruptedException {
    if (isOnMac()) {
      String output;
      try {
        output = executor.run(Command.of("system_profiler", "SPStorageDataType")).trim();
      } catch (CommandException e) {
        throw new MobileHarnessException(
            BasicErrorId.SYSTEM_GET_MAC_DISK_INFO_ERROR,
            "Failed to get storage information on Mac.",
            e);
      }
      /*
       * Example output:
       * SSD:
       * Device Name: APPLE SSD AP0256J
       * Media Name: AppleAPFSMedia
       * Medium Type: SSD
       * Protocol: PCI-Express
       * Internal: Yes
       * Partition Map Type: Unknown
       *
       * HDD:
       * Device Name: APPLE HDD HTS541010A9E662
       * Media Name: AppleAPFSMedia
       * Medium Type: Rotational
       * Protocol: SATA
       * Internal: Yes
       * Partition Map Type: Unknown
       * S.M.A.R.T. Status: Verified
       */
      if (output.contains("SSD")) {
        return DiskType.SSD;
      } else if (output.contains("HDD")) {
        return DiskType.HDD;
      }
      logger.atWarning().log(
          "Failed to determine the disk type according to the output of "
              + "`system_profiler SPStorageDataType`: %s.",
          output);
      return DiskType.UNKNOWN;
    }
    throw new MobileHarnessException(
        BasicErrorId.SYSTEM_GET_DISK_TYPE_NON_MAC_UNIMPLEMENTED,
        "Unsupported getting disk type on non-macOS machine.");
  }

  /** Gets the total amount of free memory in bytes on the local system. */
  public long getFreeMemory() throws MobileHarnessException {
    if (isOnLinux()) {
      return getProcMemInfoTag("MemFree");
    } else {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_NOT_RUN_ON_LINUX, "Unsupported Operation system.");
    }
  }

  /** Gets the total amount of used memory in bytes on the local system. */
  public long getUsedMemory() throws MobileHarnessException, InterruptedException {
    return getTotalMemory() - getFreeMemory();
  }

  /** Gets the memory size in bytes of a tag from the proc mem info file. */
  @VisibleForTesting
  long getProcMemInfoTag(String tag) throws MobileHarnessException {
    for (String line : getProcMemInfoLines()) {
      @SuppressWarnings("StringSplitter")
      String[] pieces = line.split("\\s+");
      if (pieces[0].startsWith(tag)) {
        try {
          long total = Long.parseLong(pieces[1]);
          if (pieces.length >= 3 && pieces[2].equals("kB")) {
            total = total * 1024;
          }
          return total;
        } catch (NumberFormatException expected) {
          // It is ok to ignore here since exception will be thrown later.
        }
      }
    }
    throw new MobileHarnessException(
        BasicErrorId.SYSTEM_TAG_NOT_FOUND_IN_PROC_MEMINFO, "Could not find tag " + tag);
  }

  /** Reads the lines of the proc mem info file. */
  @VisibleForTesting
  ImmutableList<String> getProcMemInfoLines() throws MobileHarnessException {
    try (BufferedReader reader = Files.newBufferedReader(Paths.get("/proc/meminfo"))) {
      return reader.lines().collect(toImmutableList());
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.SYSTEM_ACCESS_PROC_MEMINFO_ERROR, "Could not access /proc/meminfo", e);
    }
  }

  /** A simple data structure for tree traversal in {@link #killDescendantAndZombieProcesses}. */
  private static class ProcessInfo {
    public final String user;
    public final int pid;
    public final int ppid;
    public final int pgid;
    public final String command;

    public ProcessInfo(String user, int pid, int ppid, int pgid, String command) {
      this.user = user;
      this.pid = pid;
      this.ppid = ppid;
      this.pgid = pgid;
      this.command = command;
    }

    @Override
    public String toString() {
      return String.format(
          "Process: user=%s, pid=%d, ppid=%d, pgid=%d, command=%s", user, pid, ppid, pgid, command);
    }
  }

  /** Checks whether the JVM is shutting down. */
  public static boolean isProcessShuttingDown() {
    return processIsShuttingDown;
  }

  public static void setProcessIsShuttingDown() {
    processIsShuttingDown = true;
  }
}
