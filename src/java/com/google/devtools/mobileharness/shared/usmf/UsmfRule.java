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

package com.google.devtools.mobileharness.shared.usmf;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.nullToEmpty;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gson.annotations.SerializedName;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Master structured mock rule representing a CLI subprocess interception. When all conditions in
 * the rule are met, the rule is matched, and the binary then executes the behavior specified by
 * this rule.
 *
 * <p>Each rule consists of two main components:
 *
 * <ul>
 *   <li><b>Conditions</b>: Constraints that must be met for the rule to match. These can check
 *       command arguments (via exact, prefix, or regex match) or evaluate keys in shared state
 *       memory.
 *   <li><b>Behavior</b>: The simulated execution outcome to trigger when matched, including mock
 *       outputs (stdout, stderr), exit codes, state mutations (e.g., set, increment), or host side
 *       effects (e.g., file writing, directory creation).
 * </ul>
 *
 * <h3>Variable &amp; Template Interpolation</h3>
 *
 * <p>USMF supports dynamic evaluation of expressions ({@code u-exp}) and template string
 * substitutions ({@code u-string}) inside rules.
 *
 * <p>Expression examples:
 *
 * <ul>
 *   <li><b>Basic State Lookup</b>: {@code #S['rules_enabled']} (extracts boolean value).
 *   <li><b>Nested Lookup</b>: {@code #S['installed_packages'][#C['device_id']]} (dynamic
 *       device-specific lookup).
 *   <li><b>Global Translation Variable Lookup</b>: {@code #V['pkg_map'][#C['apk_name']]} (maps APK
 *       to package name).
 *   <li><b>Safety Fallback ({@code ?} Operator)</b>: {@code #C['device_id']?'unknown'} (defaults to
 *       'unknown' if missing).
 *   <li><b>Output Formatting ({@code :} Operator)</b>: {@code #C['device_id']:'device-%s'} (formats
 *       non-null value).
 *   <li><b>Combined Coalescing and Formatting</b>: {@code #C['device_id']?'':'device-%s'} (resolves
 *       priority order).
 *   <li><b>String Interpolation</b>: stdout/stderr outputs can wrap expressions in {@code ${u-exp}}
 *       blocks (e.g., {@code ${#S['installed_packages'][#C['device_id']]:'package:%s\\n'}}).
 * </ul>
 *
 * <p>For the complete expression grammar and resolution rules, see {@code README.md}.
 *
 * <p>If a rule is configured without any conditions, it acts as a wildcard that always matches
 * unconditionally. This is useful for declaring default or fallback behaviors (which should
 * typically be added as the final rule under {@link UsmfBinary}).
 *
 * @see UsmfBinary
 */
public final class UsmfRule {

  @SerializedName("conditions")
  private final ImmutableList<RuleCondition> conditions;

  @SerializedName("behavior")
  private final CommandBehavior behavior;

  public static Builder builder() {
    return new Builder();
  }

  private UsmfRule(ImmutableList<RuleCondition> conditions, CommandBehavior behavior) {
    this.conditions = conditions;
    this.behavior = checkNotNull(behavior);
  }

  public ImmutableList<RuleCondition> getConditions() {
    return conditions;
  }

  public CommandBehavior getBehavior() {
    return behavior;
  }

  /** Builder class to configure {@link UsmfRule} instances. */
  public static final class Builder {

    private final List<RuleCondition> conditions = new ArrayList<>();
    private CommandBehavior behavior;

    private Builder() {}

    @CanIgnoreReturnValue
    public Builder addCondition(RuleCondition condition) {
      this.conditions.add(condition);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setBehavior(CommandBehavior behavior) {
      this.behavior = behavior;
      return this;
    }

    /**
     * Builds the {@link UsmfRule} instance.
     *
     * <p>If no conditions were configured, the built rule behaves as a default wildcard that
     * matches all incoming commands unconditionally.
     */
    public UsmfRule build() {
      return new UsmfRule(ImmutableList.copyOf(conditions), behavior);
    }
  }

  /**
   * A single condition in a rule. Available condition types include:
   *
   * <ul>
   *   <li>{@link CommandCondition} - filters CLI actions by parameters.
   *   <li>{@link BinaryStateCondition} - filters CLI actions by binary states.
   * </ul>
   */
  public abstract static class RuleCondition {

    @SerializedName("type")
    private final String type;

    protected RuleCondition(String type) {
      this.type = type;
    }

    public String getType() {
      return type;
    }
  }

  /** Constraint condition filtering targets by CLI parameters. */
  public static final class CommandCondition extends RuleCondition {

    @SerializedName("match_type")
    private final String matchType;

    @SerializedName("expected")
    @Nullable
    private final ImmutableList<String> expected;

    @SerializedName("regex")
    @Nullable
    private final String regex;

    /**
     * Creates a {@link CommandCondition} that matches the CLI command arguments sequence exactly.
     *
     * @param args the literally expected command arguments sequence
     */
    public static CommandCondition exactMatch(List<String> args) {
      return new CommandCondition("exact", ImmutableList.copyOf(args), null);
    }

    /**
     * Creates a {@link CommandCondition} that matches the CLI command arguments sequence exactly.
     *
     * @param args the literally expected command arguments sequence
     */
    public static CommandCondition exactMatch(String... args) {
      return new CommandCondition("exact", ImmutableList.copyOf(args), null);
    }

    /**
     * Creates a {@link CommandCondition} that matches if the literals in {@code prefix} are a
     * prefix of the actual CLI command arguments.
     *
     * @param prefix the literally expected command prefix arguments sequence
     */
    public static CommandCondition prefixMatch(List<String> prefix) {
      return new CommandCondition("prefix", ImmutableList.copyOf(prefix), null);
    }

    /**
     * Creates a {@link CommandCondition} that matches if the literals in {@code prefix} are a
     * prefix of the actual CLI command arguments.
     *
     * @param prefix the literally expected command prefix arguments sequence
     */
    public static CommandCondition prefixMatch(String... prefix) {
      return new CommandCondition("prefix", ImmutableList.copyOf(prefix), null);
    }

    /**
     * Creates a {@link CommandCondition} that matches the CLI command arguments using a Python
     * regular expression pattern (evaluated by Python inside the USMF sandbox).
     *
     * <p>The regular expression is matched against the command arguments (excluding the binary name
     * itself) joined by spaces, with shell-quoting applied (e.g., "devices -l").
     *
     * <p>Matches containing Python regex named capture groups (e.g., {@code (?P<pkg>\\S+)}, note
     * the Python 'P' prefix syntax which differs from Java's native regex naming format) can be
     * used for dynamic parameter interpolation. The captured values will be dynamically substituted
     * into placeholders like {@code ${#C['group_name']}} in {@link CommandBehavior} properties such
     * as stdout/stderr, state mutations, and filesystem side effects.
     *
     * @param regex the Python regular expression pattern to match the CLI command arguments
     */
    public static CommandCondition regexMatch(String regex) {
      return new CommandCondition("regex", null, checkNotNull(regex));
    }

    private CommandCondition(
        String matchType, @Nullable ImmutableList<String> expected, @Nullable String regex) {
      super("command");
      this.matchType = matchType;
      this.expected = expected;
      this.regex = regex;
    }

    public String getMatchType() {
      return matchType;
    }

    @Nullable
    public ImmutableList<String> getExpected() {
      return expected;
    }

    @Nullable
    public String getRegex() {
      return regex;
    }
  }

  /**
   * Constraint condition filtering targets by binary states. A binary's binary states are modified
   * by {@link BinaryStateMutation} inside {@link CommandBehavior}.
   *
   * <p>The binary states are stored as a key-value map where the key is a string and the value can
   * be of types such as {@link Boolean}, {@link Number} (e.g., integer or double), {@link String},
   * or {@link List} of these types.
   *
   * <p>Example condition operations on different value types:
   *
   * <pre>{@code
   * // 1. Boolean state equals check
   * RuleCondition cond1 = BinaryStateCondition.stateNode("#S['installed']").equalTo(true);
   *
   * // 2. Integer state comparison check
   * RuleCondition cond2 = BinaryStateCondition.stateNode("#S['boot_count']").greaterThan(2);
   *
   * // 3. List state item containment check
   * RuleCondition cond3 = BinaryStateCondition.stateNode("#S['installed_packages']").contains("com.example.app");
   * }</pre>
   */
  public static final class BinaryStateCondition extends RuleCondition {

    @SerializedName("state_node")
    private final String stateNode;

    @SerializedName("op")
    private final String op;

    @SerializedName("expected_value")
    @Nullable
    private final Object expectedValue;

    public static Builder stateNode(String stateNode) {
      return new Builder(stateNode);
    }

    private BinaryStateCondition(String stateNode, String op, @Nullable Object expectedValue) {
      super("state");
      this.stateNode = checkNotNull(stateNode);
      this.op = op;
      this.expectedValue = expectedValue;
    }

    public String getStateNode() {
      return stateNode;
    }

    public String getOp() {
      return op;
    }

    @Nullable
    public Object getExpectedValue() {
      return expectedValue;
    }

    /** Builder class to configure {@link BinaryStateCondition} rule conditions. */
    public static final class Builder {

      private final String stateNode;

      private Builder(String stateNode) {
        this.stateNode = stateNode;
      }

      public BinaryStateCondition equalTo(@Nullable Object value) {
        return new BinaryStateCondition(stateNode, "eq", value);
      }

      public BinaryStateCondition greaterThan(Object value) {
        return new BinaryStateCondition(stateNode, "gt", checkNotNull(value));
      }

      public BinaryStateCondition greaterThanOrEqualTo(Object value) {
        return new BinaryStateCondition(stateNode, "gte", checkNotNull(value));
      }

      public BinaryStateCondition lessThan(Object value) {
        return new BinaryStateCondition(stateNode, "lt", checkNotNull(value));
      }

      public BinaryStateCondition lessThanOrEqualTo(Object value) {
        return new BinaryStateCondition(stateNode, "lte", checkNotNull(value));
      }

      public BinaryStateCondition contains(Object value) {
        return new BinaryStateCondition(stateNode, "contains", checkNotNull(value));
      }
    }
  }

  /**
   * Action response and binary state mutation configurations.
   *
   * <p>Properties such as {@code stdout}, {@code stderr}, {@code stateMutations}, and {@code
   * sideEffects} support dynamic parameter interpolation. If a rule match is triggered by a regex
   * {@link CommandCondition} with Python named capture groups (e.g., {@code (?P<pkg>\\S+)}, note
   * the Python 'P' prefix syntax which differs from Java's native regex naming format), the
   * captured values will recursively substitute all placeholder occurrences of {@code
   * ${#C['group_name']}} in behavior blocks.
   *
   * <p>Note: When a rule is matched, the state mutations are executed first (atomically) before the
   * mock execution sleep latency (configured via {@link Builder#sleep(Duration)}) and host side
   * effects, ensuring that concurrent commands can immediately observe the updated state.
   */
  public static final class CommandBehavior {

    @SerializedName("stdout")
    private final String stdout;

    @SerializedName("stderr")
    private final String stderr;

    @SerializedName("exit_code")
    private final int exitCode;

    @SerializedName("sleep_ms")
    private final long sleepMs;

    @SerializedName("state_mutations")
    private final ImmutableList<BinaryStateMutation> stateMutations;

    @SerializedName("side_effects")
    private final ImmutableList<CommandSideEffect> sideEffects;

    public static Builder builder(String stdout, String stderr, int exitCode) {
      return new Builder(stdout, stderr, exitCode);
    }

    public static Builder stdout(String stdout) {
      return new Builder(stdout, "", 0);
    }

    public static Builder stderr(String stderr) {
      return new Builder("", stderr, 0);
    }

    public static Builder exitCode(int exitCode) {
      return new Builder("", "", exitCode);
    }

    private CommandBehavior(
        String stdout,
        String stderr,
        int exitCode,
        long sleepMs,
        ImmutableList<BinaryStateMutation> stateMutations,
        ImmutableList<CommandSideEffect> sideEffects) {
      this.stdout = nullToEmpty(stdout);
      this.stderr = nullToEmpty(stderr);
      this.exitCode = exitCode;
      this.sleepMs = sleepMs;
      this.stateMutations = stateMutations;
      this.sideEffects = sideEffects;
    }

    public String getStdout() {
      return stdout;
    }

    public String getStderr() {
      return stderr;
    }

    public int getExitCode() {
      return exitCode;
    }

    public long getSleepMs() {
      return sleepMs;
    }

    public ImmutableList<BinaryStateMutation> getStateMutations() {
      return stateMutations;
    }

    public ImmutableList<CommandSideEffect> getSideEffects() {
      return sideEffects;
    }

    /** Builder class to configure {@link CommandBehavior} response configurations. */
    public static final class Builder {

      private String stdout;
      private String stderr;
      private int exitCode;
      private long sleepMs;
      private final List<BinaryStateMutation> stateMutations = new ArrayList<>();
      private final List<CommandSideEffect> sideEffects = new ArrayList<>();

      private Builder(String stdout, String stderr, int exitCode) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitCode = exitCode;
      }

      @CanIgnoreReturnValue
      public Builder stdout(String stdout) {
        this.stdout = stdout;
        return this;
      }

      @CanIgnoreReturnValue
      public Builder stderr(String stderr) {
        this.stderr = stderr;
        return this;
      }

      @CanIgnoreReturnValue
      public Builder exitCode(int exitCode) {
        this.exitCode = exitCode;
        return this;
      }

      /**
       * Sets the latency delay duration of the mocked command subprocess execution to simulate
       * hardware latency.
       *
       * <p>Note: Any configured state mutations will be executed atomically *before* this execution
       * latency delay begins, ensuring that concurrent commands do not read a stale state during
       * the sleep.
       *
       * @param duration the latency delay duration
       */
      @CanIgnoreReturnValue
      public Builder sleep(Duration duration) {
        this.sleepMs = duration.toMillis();
        return this;
      }

      @CanIgnoreReturnValue
      public Builder addStateMutation(BinaryStateMutation mutation) {
        this.stateMutations.add(mutation);
        return this;
      }

      @CanIgnoreReturnValue
      public Builder addSideEffect(CommandSideEffect sideEffect) {
        this.sideEffects.add(sideEffect);
        return this;
      }

      public CommandBehavior build() {
        return new CommandBehavior(
            stdout,
            stderr,
            exitCode,
            sleepMs,
            ImmutableList.copyOf(stateMutations),
            ImmutableList.copyOf(sideEffects));
      }
    }
  }

  /**
   * Single atomic operation performed on states of a binary. These binary states can in turn be
   * specified in {@link BinaryStateCondition} to filter rules.
   *
   * <p>The binary states are stored as a key-value map where the key is a string and the value can
   * be of types such as {@link Boolean}, {@link Number} (e.g., integer or double), {@link String},
   * or {@link List} of these types.
   *
   * <p>String values and keys support dynamic parameter interpolation using Python named regex
   * capture groups (e.g., {@code (?P<pkg>\\S+)}, note the Python 'P' prefix syntax) in matched rule
   * conditions. For example, {@code
   * BinaryStateMutation.stateNode("#S['installed_packages']").addToSet("${pkg}")} will add the
   * captured package name dynamically.
   *
   * <p>Example mutation operations on different value types:
   *
   * <pre>{@code
   * // 1. Set Boolean state value
   * BinaryStateMutation mut1 = BinaryStateMutation.stateNode("#S['installed']").set(true);
   *
   * // 2. Add numeric state value
   * BinaryStateMutation mut2 = BinaryStateMutation.stateNode("#S['boot_count']").plus(1);
   *
   * // 3. Add String element to Set state value
   * BinaryStateMutation mut3 = BinaryStateMutation.stateNode("#S['installed_packages']").addToSet("com.example.app");
   * }</pre>
   */
  public static final class BinaryStateMutation {

    @SerializedName("state_node")
    private final String stateNode;

    @SerializedName("op")
    private final String op;

    @SerializedName("value")
    @Nullable
    private final Object value;

    public static Builder stateNode(String stateNode) {
      return new Builder(stateNode);
    }

    private BinaryStateMutation(String stateNode, String op, @Nullable Object value) {
      this.stateNode = checkNotNull(stateNode);
      this.op = op;
      this.value = value;
    }

    public String getStateNode() {
      return stateNode;
    }

    public String getOp() {
      return op;
    }

    @Nullable
    public Object getValue() {
      return value;
    }

    /** Builder class to configure {@link BinaryStateMutation} actions. */
    public static final class Builder {

      private final String stateNode;

      private Builder(String stateNode) {
        this.stateNode = stateNode;
      }

      public BinaryStateMutation set(@Nullable Object value) {
        return new BinaryStateMutation(stateNode, "set", value);
      }

      public BinaryStateMutation plus(Object value) {
        return new BinaryStateMutation(stateNode, "plus", checkNotNull(value));
      }

      public BinaryStateMutation addToList(Object value) {
        return new BinaryStateMutation(stateNode, "add_to_list", checkNotNull(value));
      }

      public BinaryStateMutation addToSet(Object value) {
        return new BinaryStateMutation(stateNode, "add_to_set", checkNotNull(value));
      }
    }
  }

  /**
   * Single dynamic side-effect action executed on the host environment as part of a command
   * behavior, such as writing files, creating directories, or performing other environment
   * mutations. Available side-effect types include:
   *
   * <ul>
   *   <li>{@link LocalFileSideEffect} - file or directory mutations on the local host.
   * </ul>
   */
  public abstract static class CommandSideEffect {

    @SerializedName("type")
    private final String type;

    protected CommandSideEffect(String type) {
      this.type = type;
    }

    public String getType() {
      return type;
    }
  }

  /** Single filesystem side-effect executing file or directory mutations on the local host. */
  public static final class LocalFileSideEffect extends CommandSideEffect {

    @SerializedName("op")
    private final String op;

    @SerializedName("target_path")
    private final String targetPath;

    @SerializedName("content")
    @Nullable
    private final String content;

    public static LocalFileSideEffect createFile(String targetPath, String content) {
      return new LocalFileSideEffect("write_file", targetPath, content);
    }

    public static LocalFileSideEffect createDirectory(String targetPath) {
      return new LocalFileSideEffect("create_dir", targetPath, null);
    }

    private LocalFileSideEffect(String op, String targetPath, @Nullable String content) {
      super("file");
      this.op = op;
      this.targetPath = checkNotNull(targetPath);
      this.content = content;
    }

    public String getOp() {
      return op;
    }

    public String getTargetPath() {
      return targetPath;
    }

    @Nullable
    public String getContent() {
      return content;
    }
  }
}
