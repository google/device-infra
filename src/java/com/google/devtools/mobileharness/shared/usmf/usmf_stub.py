#!/usr/bin/env python3

"""Stub utility for USMF (Universal Simulation & Mocking Framework) rules.

This script simulates client-side binary execution and intercepts system calls
based on rules defined in a JSON file.

Usage:
  This script can be executed as a replacement for real hardware/host binaries
  or tools. It is configured via environment variables:

  USMF_RULES_FILE: Path to the JSON file containing rules and mock behaviors.
  USMF_STATES_FILE: Path to the file where simulated states are saved.
  USMF_LOGS_DIR: Path to the directory where simulated execution logs are saved.
"""

from __future__ import annotations

from collections.abc import Iterator, Mapping, MutableMapping, MutableSequence, Sequence
import contextlib
import dataclasses
import datetime
import json
import logging
import operator
import os
import pathlib
import re
import shlex
import sys
import textwrap
import time
import traceback
from typing import Any
import uuid

try:
  # pylint: disable=g-import-not-at-top
  import fcntl
  # pylint: enable=g-import-not-at-top
except ImportError:
  fcntl = None

_INNERMOST_PATTERN = re.compile(
    textwrap.dedent(r"""
    \$                          # Matches a literal '$'.
    \{                          # Matches a literal '{'.
    (?P<prefix>@[cCsS])         # Captures the prefix: '@c', '@C', '@s', or '@S'.
    :                           # Matches a literal ':'.
    (?P<key>[^:'"{}]+)          # Captures the key.
    (?:                         # Start of an optional group for the format specifier.
        :                       # Matches a literal ':'.
        ['"]                    # Matches a single or double quote.
        (?P<fmt>[^'"{}]+)       # Captures the format specifier.
        ['"]                    # Matches a single or double quote.
    )?                          # End of the optional format group.
    }                           # Matches a literal '}'.
    """),
    re.VERBOSE,
)

_OPERATOR_BY_NAME = {
    "eq": operator.eq,
    "gt": operator.gt,
    "gte": operator.ge,
    "lt": operator.lt,
    "lte": operator.le,
}


@dataclasses.dataclass
class StateMutation:
  """A single state mutation.

  Attributes:
    key: The key of the state variable to mutate.
    op: The mutation operation (e.g., "set", "plus", "add_to_list").
    value: The value to use in the mutation.
  """

  key: Any
  op: str
  value: Any | None = None


@dataclasses.dataclass
class SideEffect:
  """A single side effect.

  Attributes:
    type: The type of side effect (e.g., "file").
    op: The operation of the side effect (e.g., "write_file", "create_dir").
    target_path: The target file or directory path.
    content: The content to write for "write_file" operations.
  """

  type: str
  op: str
  target_path: str | None = None
  content: str | None = None


@dataclasses.dataclass
class Behavior:
  """The behavior to execute when a rule matches.

  Attributes:
    state_mutations: A sequence of state mutations to apply.
    side_effects: A sequence of side effects to execute.
    stdout: The string or value to print to standard output.
    stderr: The string or value to print to standard error.
    exit_code: The exit code of the stub process.
    sleep_duration: The duration to sleep.
  """

  state_mutations: Sequence[StateMutation] = dataclasses.field(
      default_factory=list
  )
  side_effects: Sequence[SideEffect] = dataclasses.field(default_factory=list)
  stdout: Any = ""
  stderr: Any = ""
  exit_code: int = 0
  sleep_duration: datetime.timedelta = dataclasses.field(
      default_factory=datetime.timedelta
  )


@dataclasses.dataclass
class CommandCondition:
  """A command matching condition.

  Attributes:
    match_type: The type of command matching ("exact", "prefix", "regex").
    expected: A sequence of expected command arguments for "exact" or "prefix".
    regex: A regex pattern to match against command arguments.
  """

  match_type: str
  expected: Sequence[str] = dataclasses.field(default_factory=list)
  regex: str | None = None


@dataclasses.dataclass
class StateCondition:
  """A condition for a state variable.

  Attributes:
    key: The key of the state variable to check.
    op: The comparison operator (e.g., "eq", "gt", "contains").
    expected_value: The value to compare against the state variable.
  """

  key: Any
  op: str
  expected_value: Any


@dataclasses.dataclass
class Condition:
  """A condition within a USMF rule.

  Attributes:
    type: The type of condition ("command", "state").
    command: A CommandCondition object if type is "command".
    state: A StateCondition object if type is "state".
  """

  type: str
  command: CommandCondition | None = None
  state: StateCondition | None = None


@dataclasses.dataclass
class Rule:
  """A single USMF rule.

  Attributes:
    conditions: A sequence of Condition objects that must all be met.
    behavior: The Behavior to execute if conditions are met.
  """

  conditions: Sequence[Condition] = dataclasses.field(default_factory=list)
  behavior: Behavior = dataclasses.field(default_factory=Behavior)


def _parse_int_value(val: Any) -> int:
  """Parses raw value into an integer, defaulting to 0."""
  try:
    return int(float(val))
  except (ValueError, TypeError):
    return 0


def _parse_command_condition(cond: Mapping[str, Any]) -> CommandCondition:
  """Parses a dictionary into a CommandCondition dataclass."""
  return CommandCondition(
      match_type=cond.get("match_type", ""),
      expected=cond.get("expected", []),
      regex=cond.get("regex"),
  )


def _parse_state_condition(cond: Mapping[str, Any]) -> StateCondition:
  """Parses a dictionary into a StateCondition dataclass."""
  return StateCondition(
      key=cond.get("key"),
      op=cond.get("op", "eq"),
      expected_value=cond.get("expected_value"),
  )


def _parse_condition(cond_dict: Mapping[str, Any]) -> Condition:
  """Parses a condition dictionary into a Condition dataclass."""
  cond_type = cond_dict.get("type")
  if cond_type == "command":
    return Condition(
        type="command", command=_parse_command_condition(cond_dict)
    )
  if cond_type == "state":
    return Condition(type="state", state=_parse_state_condition(cond_dict))
  return Condition(type=cond_type if cond_type else "unknown")


def _parse_rule(rule_dict: Mapping[str, Any]) -> Rule:
  """Parses a dictionary into a Rule dataclass."""
  conditions = [
      _parse_condition(cond_dict)
      for cond_dict in rule_dict.get("conditions", [])
  ]

  behavior_dict = rule_dict.get("behavior", {})
  state_mutations = [
      StateMutation(key=m.get("key"), op=m.get("op"), value=m.get("value"))
      for m in behavior_dict.get("state_mutations", [])
  ]
  side_effects = [
      SideEffect(
          type=e.get("type", ""),
          op=e.get("op", ""),
          target_path=e.get("target_path"),
          content=e.get("content"),
      )
      for e in behavior_dict.get("side_effects", [])
  ]

  behavior = Behavior(
      state_mutations=state_mutations,
      side_effects=side_effects,
      stdout=behavior_dict.get("stdout", ""),
      stderr=behavior_dict.get("stderr", ""),
      exit_code=_parse_int_value(behavior_dict.get("exit_code", 0)),
      sleep_duration=datetime.timedelta(
          milliseconds=_parse_int_value(behavior_dict.get("sleep_ms", 0))
      ),
  )
  return Rule(conditions=conditions, behavior=behavior)


def _parse_bool(val: Any) -> bool:
  """Converts values to a boolean representation."""
  if isinstance(val, bool):
    return val
  return str(val).strip().lower() in ("true", "1", "yes")


def _try_parse_number(val: Any) -> int | float | None:
  """Parses numeric values, supporting floats and scientific notations."""
  if isinstance(val, (int, float)) and not isinstance(val, bool):
    return val
  try:
    f_val = float(val)
    return int(f_val) if f_val.is_integer() else f_val
  except (ValueError, TypeError):
    return None


def _coerce_to_same_type(*, val1: Any, val2: Any) -> tuple[Any, Any]:
  """Coerces both variables to a comparable type (boolean, numeric, or string)."""
  if isinstance(val1, bool) or isinstance(val2, bool):
    return _parse_bool(val1), _parse_bool(val2)

  num1 = _try_parse_number(val1)
  num2 = _try_parse_number(val2)
  if num1 is not None and num2 is not None:
    return num1, num2

  return str(val1), str(val2)


def _compare_state(
    *, actual: Any, op: str, expected: Any, errors: MutableSequence[str]
) -> bool:
  """Compares the actual state value against expected using operator op.

  Special behavior for the "contains" operator:
  - If actual is a list, tuple, set, or dict, check membership of expected in
    actual (for dictionaries, this checks if expected is a key after type
    coercion).
  - If actual is a string, converts expected to string and checks if it is a
    substring of actual.

  Args:
    actual: The actual value from the state.
    op: The comparison operator (e.g., "eq", "gt", "contains").
    expected: The expected value.
    errors: A list to collect error messages.

  Returns:
    True if the comparison is successful, False otherwise.
  """
  actual_val = "" if actual is None and op != "contains" else actual

  if op == "contains":
    if actual_val is None:
      return False
    if not isinstance(actual_val, (list, tuple, set, dict)):
      return str(expected) in str(actual_val)

    # Handle list, tuple, set, dict for 'contains'.
    items_to_check = (
        actual_val.keys() if isinstance(actual_val, dict) else actual_val
    )
    for item in items_to_check:
      try:
        c_item, c_exp = _coerce_to_same_type(val1=item, val2=expected)
        if c_item == c_exp:
          return True
      except Exception:  # pylint: disable=broad-exception-caught
        pass
    return False

  try:
    c_actual, c_expected = _coerce_to_same_type(val1=actual_val, val2=expected)
  except Exception as e:  # pylint: disable=broad-exception-caught
    errors.append(
        f"Type coercion fail: actual={actual_val!r}, expected={expected!r},"
        f" err={e!r}"
    )
    return False

  if op not in _OPERATOR_BY_NAME:
    errors.append(f"USMF State compare error: unknown operator {op!r}")
    return False

  try:
    return _OPERATOR_BY_NAME[op](c_actual, c_expected)
  except Exception as e:  # pylint: disable=broad-exception-caught
    errors.append(
        f"Invalid comparison: cannot compare actual={actual!r} ("
        f"{type(actual).__name__}) with expected={expected!r} ("
        f"{type(expected).__name__}) using operator {op!r}. Details: {e!r}"
    )
  return False


def _format_single(val: Any, fmt: str | None) -> str:
  """Formats a single primitive using format patterns."""
  if val is None:
    return ""
  if fmt is None:
    return str(val)
  # Coerce string to numeric typings if expected by print specifiers.
  if isinstance(val, str):
    try:
      if any(p in fmt for p in ("%d", "%i", "%o", "%x", "%X")):
        val = int(float(val))
      elif any(p in fmt for p in ("%f", "%e", "%E", "%g", "%G")):
        val = float(val)
    except ValueError:
      pass
  try:
    return fmt % val
  except Exception:  # pylint: disable=broad-exception-caught
    return str(val)


def _format_value(val: Any, fmt: str | None) -> str:
  """Extracts and formats collections or primitives."""
  if isinstance(val, (list, tuple, set)):
    return (
        "".join(_format_single(item, fmt) for item in val)
        if fmt
        else ", ".join(str(item) for item in val)
    )
  return _format_single(val, fmt)


def _resolve_string(
    text: str,
    *,
    context_c: Mapping[str, Any],
    context_s: Mapping[str, Any],
) -> str:
  """Processes innermost placeholder substitutions (max 5 layers) iteratively.

  Args:
    text: The template string.
    context_c: The captured variables mapping.
    context_s: The state variables mapping.

  Returns:
    The resolved string.
  """
  context_by_prefix = {"@c": context_c, "@s": context_s}

  def replace(match: re.Match[str]) -> str:
    prefix = match.group("prefix").lower()
    key = match.group("key")
    fmt = match.group("fmt")

    val = context_by_prefix[prefix].get(key)
    return _format_value(val, fmt) if val is not None else ""

  for _ in range(5):
    reconstructed, count = _INNERMOST_PATTERN.subn(replace, text)
    if count == 0:
      break
    text = reconstructed
  return text


def _interpolate_value(
    val: Any,
    *,
    captures: Mapping[str, Any],
    state: Mapping[str, Any],
) -> Any:
  """Recursively interpolates regex captures and state placeholders.

  Args:
    val: The value to interpolate.
    captures: Captured variables mapping.
    state: State variables mapping.

  Returns:
    The interpolated value.
  """
  if isinstance(val, str):
    return _resolve_string(val, context_c=captures, context_s=state)
  if isinstance(val, list):
    return [
        _interpolate_value(item, captures=captures, state=state) for item in val
    ]
  if isinstance(val, dict):
    return {
        _interpolate_value(
            k, captures=captures, state=state
        ): _interpolate_value(v, captures=captures, state=state)
        for k, v in val.items()
    }
  if dataclasses.is_dataclass(val):
    kwargs = {
        f.name: _interpolate_value(
            getattr(val, f.name), captures=captures, state=state
        )
        for f in dataclasses.fields(val)
    }
    return type(val)(**kwargs)
  return val


def _eval_command_condition(
    cond: CommandCondition,
    actual_args: Sequence[str],
    context_c: MutableMapping[str, Any],
) -> bool:
  """Evaluates command condition matching, populating regex checks captured."""
  match_type = cond.match_type
  expected = cond.expected
  if match_type == "exact":
    return [str(e) for e in expected] == actual_args
  if match_type == "prefix":
    return (
        len(actual_args) >= len(expected)
        and [str(e) for e in expected] == actual_args[: len(expected)]
    )
  if match_type == "regex":
    regex_pattern = cond.regex
    if not regex_pattern:
      return False
    match = re.match(regex_pattern, shlex.join(actual_args))
    if not match:
      return False
    context_c.update(match.groupdict())
    return True
  return False


def _evaluate_rule(
    rule: Rule,
    actual_args: Sequence[str],
    context_s: Mapping[str, Any],
    errors: MutableSequence[str],
) -> tuple[bool, Mapping[str, Any]]:
  """Evaluates command and variable state conditions for a rule.

  Args:
    rule: The rule to evaluate.
    actual_args: The command-line arguments.
    context_s: The current state dictionary.
    errors: List to collect errors.

  Returns:
    A tuple (matched, captures), where:
      matched: True if all conditions in the rule are met, False otherwise.
      captures: A dictionary containing named capture groups from regex command
        conditions, if any matched.
  """
  context_c = {}
  conditions = rule.conditions

  # Populate context_c from command conditions.
  for cond in conditions:
    if not (cond.type == "command" and cond.command):
      continue
    if not _eval_command_condition(cond.command, actual_args, context_c):
      return False, {}

  # Validate State Conditions.
  for cond in conditions:
    if cond.type == "command":
      continue  # Already handled.
    if not (cond.type == "state" and cond.state):
      return False, {}

    raw_key = cond.state.key
    raw_expected_value = cond.state.expected_value

    interpolated_key = _interpolate_value(
        raw_key, captures=context_c, state=context_s
    )
    expected_val = _interpolate_value(
        raw_expected_value, captures=context_c, state=context_s
    )

    state_val = context_s.get(interpolated_key)
    op = cond.state.op

    if not _compare_state(
        actual=state_val, op=op, expected=expected_val, errors=errors
    ):
      return False, {}

  return True, context_c


def _load_state(
    state_file: pathlib.Path | None, errors: MutableSequence[str]
) -> MutableMapping[str, Any]:
  """Loads current central state memory from the state file.

  Args:
    state_file: Path to the state file.
    errors: List to collect errors.

  Returns:
    The loaded state.
  """
  if not state_file:
    return {}
  try:
    with state_file.open("r", encoding="utf-8") as f:
      return json.load(f)
  except FileNotFoundError:
    return {}
  except Exception as e:  # pylint: disable=broad-exception-caught
    errors.append(f"Load State Error for file {state_file!r}: {e!r}")
    return {}


@contextlib.contextmanager
def _state_lock(lock_path: pathlib.Path | None) -> Iterator[None]:
  """Coordinates exclusive locking on the state file."""
  if not (fcntl and lock_path):
    yield
    return
  lock_fd = None
  try:
    lock_fd = lock_path.open("w", encoding="utf-8")
    fcntl.lockf(lock_fd, fcntl.LOCK_EX)
  except Exception:  # pylint: disable=broad-exception-caught
    pass

  if lock_fd is not None:
    with lock_fd:
      yield
  else:
    yield


def _write_json_atomic(
    file_path: pathlib.Path | None,
    data: Any,
    errors: MutableSequence[str] | None = None,
) -> None:
  """Writes JSON data atomically using a temporary file rename.

  Args:
    file_path: The destination file path.
    data: The JSON-serializable data to write.
    errors: An optional list to append error messages to if an exception occurs
      during the write operation.
  """
  if not file_path:
    return
  temp_file = file_path.with_suffix(file_path.suffix + ".tmp")
  try:
    file_path.parent.mkdir(parents=True, exist_ok=True)
    with temp_file.open("w", encoding="utf-8") as tf:
      json.dump(data, tf)
    temp_file.replace(file_path)
  except Exception as e:  # pylint: disable=broad-exception-caught
    if errors is not None:
      errors.append(f"Write JSON atomic error for {file_path}: {e!r}")


def _apply_mutation(
    mutation: StateMutation,
    state: MutableMapping[str, Any],
    errors: MutableSequence[str],
) -> None:
  """Applies a single state mutation operation.

  Args:
    mutation: The state mutation config.
    state: The dictionary representing the current USMF state. This is mutated
      by the function.
    errors: A list to collect error messages. This is mutated by the function.
  """
  op = mutation.op
  k = mutation.key
  v = mutation.value
  if op == "set":
    state[k] = v
  elif op == "plus":
    curr_num = _try_parse_number(state.get(k, 0)) or 0
    if (val_num := _try_parse_number(v)) is None:
      errors.append(f"USMF Mutation error: cannot convert {v!r} to number")
    else:
      state[k] = curr_num + val_num
  elif op in ("add_to_list", "add_to_set"):
    lst = state.get(k)
    if not isinstance(lst, list):
      state[k] = [v]
    elif op == "add_to_list" or v not in lst:
      lst.append(v)


def _apply_state_mutations(
    *,
    state_mutations: Sequence[StateMutation],
    state: MutableMapping[str, Any],
    captures: Mapping[str, Any],
    errors: MutableSequence[str],
) -> None:
  """Mutates binary shared memory state with parameter interpolation.

  Args:
    state_mutations: A sequence of mutation configurations.
    state: The current state dictionary. This dictionary is mutated.
    captures: Captured variables mapping.
    errors: A list to collect error messages. This list is mutated.
  """
  for mutation in state_mutations or []:
    raw_key = mutation.key
    if not raw_key:
      continue
    op = mutation.op
    if op not in ("set", "plus", "add_to_list", "add_to_set"):
      errors.append(f"USMF Mutation error: unknown op {op!r}")
      continue

    try:
      interpolated_key = _interpolate_value(
          raw_key, captures=captures, state=state
      )
      interpolated_value = _interpolate_value(
          mutation.value, captures=captures, state=state
      )
      mutated_mutation = StateMutation(
          key=interpolated_key, op=op, value=interpolated_value
      )
      _apply_mutation(mutated_mutation, state, errors)
    except Exception as e:  # pylint: disable=broad-exception-caught
      logging.exception(
          "USMF Mutation error during processing key %r with mutation %r",
          raw_key,
          mutation,
      )
      errors.append(f"USMF Mutation error for key {raw_key!r}: {e!r}")


def _execute_side_effects(
    side_effects: Sequence[SideEffect],
    errors: MutableSequence[str],
) -> None:
  """Executes dynamic host side-effects.

  Args:
    side_effects: A sequence of side-effect configurations.
    errors: A list to collect error messages. This list is mutated.
  """
  for effect in side_effects:
    if effect.type != "file" or not (op := effect.op):
      continue
    target_path = effect.target_path
    if not target_path:
      continue

    try:
      target_path_obj = pathlib.Path(target_path)
      if op == "write_file":
        content = effect.content if effect.content is not None else ""
        target_path_obj.parent.mkdir(parents=True, exist_ok=True)
        with target_path_obj.open("w", encoding="utf-8") as f:
          f.write(content)
      elif op == "create_dir":
        target_path_obj.mkdir(parents=True, exist_ok=True)
    except Exception as e:  # pylint: disable=broad-exception-caught
      logging.exception("Fake USMF Side-Effect Exec Error. effect: %r", effect)
      errors.append(f"Fake USMF Side-Effect Exec Error: {e!r}")


def _write_history_status(
    *,
    log_file: pathlib.Path | None,
    args: Sequence[str],
    status: str,
    start_time: datetime.datetime,
    result: Mapping[str, Any] | None = None,
    errors: Sequence[str] | None = None,
) -> None:
  """Writes or updates the chronological execution history log.

  Args:
    log_file: Path to the history log file.
    args: The command-line arguments of the execution.
    status: The status of the execution (e.g., "RUNNING", "FINISHED").
    start_time: The start time of the execution.
    result: Optional dictionary containing execution results like exit code,
      stdout, and stderr.
    errors: Optional list of error messages to be included in the log.
  """
  if not log_file:
    return
  data = {
      "args": list(args),
      "status": status,
      "start_time_ms": int(start_time.timestamp() * 1000),
      "errors": list(errors) if errors is not None else [],
  }
  if result is not None:
    data["result"] = result

  _write_json_atomic(log_file, data)


def _execute_stub(
    *,
    rules_file: pathlib.Path | None,
    state_file: pathlib.Path | None,
    log_dir: pathlib.Path | None,
    actual_args: Sequence[str],
) -> int:
  """Executes USMF rules evaluation and command execution interceptions.

  Args:
    rules_file: Path to the rules JSON file.
    state_file: Path to the central state file.
    log_dir: Path to the execution history logs directory.
    actual_args: The command-line arguments.

  Returns:
    The exit code.
  """
  start_time = datetime.datetime.now(datetime.timezone.utc)
  errors = []
  exit_code = 1
  stdout = ""
  stderr = ""

  # Calculate log file path if log directory is available.
  log_file = (
      log_dir.joinpath(f"history_{uuid.uuid4()}.json") if log_dir else None
  )

  try:
    # 1. Load Rules configuration before entering the state lock.
    rules: list[Rule] = []
    if rules_file:
      try:
        with rules_file.open("r", encoding="utf-8") as f:
          raw_rules = json.load(f).get("rules", [])
          rules = [_parse_rule(r) for r in raw_rules]
      except FileNotFoundError:
        # Proceed with empty rules if the file doesn't exist.
        pass
      except Exception as e:  # pylint: disable=broad-exception-caught
        logging.exception("Mock USMF JSON Error for rules file %r", rules_file)
        errors.append(
            f"Mock USMF JSON Error for rules file {rules_file!r}: {e!r}"
        )
        return 1

    # 2. Acquire transactional state locks.
    state = {}
    matched_rule = None
    captures = {}

    lock_file = (
        state_file.with_suffix(state_file.suffix + ".lock")
        if state_file
        else None
    )
    with _state_lock(lock_file):
      # Load current central state memory inside lock.
      state = _load_state(state_file, errors)

      # Evaluate sequential configuration lists.
      for rule in rules:
        try:
          matched, captures = _evaluate_rule(
              rule=rule,
              actual_args=actual_args,
              context_s=state,
              errors=errors,
          )
          if matched:
            matched_rule = rule
            break
        except Exception as e:  # pylint: disable=broad-exception-caught
          errors.append(f"USMF Rule evaluation error: {e!r}")

      # Apply state mutations inside locks.
      behavior_config = matched_rule.behavior if matched_rule else Behavior()
      if matched_rule:
        _apply_state_mutations(
            state_mutations=behavior_config.state_mutations,
            state=state,
            captures=captures,
            errors=errors,
        )
        if state_file:
          _write_json_atomic(state_file, state, errors)

    # 3. Resolve behavior outputs outside of the state lock.

    # Dynamically interpolate output fields with matched capture groups and
    # state.
    stdout = _interpolate_value(
        behavior_config.stdout, captures=captures, state=state
    )
    stderr = _interpolate_value(
        behavior_config.stderr, captures=captures, state=state
    )
    exit_code = behavior_config.exit_code
    side_effects = _interpolate_value(
        behavior_config.side_effects, captures=captures, state=state
    )
    # 4. Execute hardware latency sleeps outside of lock.
    if behavior_config.sleep_duration.total_seconds() > 0:
      if log_file:
        _write_history_status(
            log_file=log_file,
            args=actual_args,
            status="RUNNING",
            start_time=start_time,
            errors=errors,
        )
      time.sleep(behavior_config.sleep_duration.total_seconds())

    # 5. Execute dynamic host side-effects outside of lock.
    _execute_side_effects(side_effects, errors)

    print(stdout, end="", flush=True)
    print(stderr, end="", file=sys.stderr, flush=True)
    return exit_code

  except Exception as e:  # pylint: disable=broad-exception-caught
    logging.exception(
        "Unexpected error during USMF stub execution. args: %r",
        actual_args,
    )
    errors.append(f"Unexpected error: {e!r}\n{traceback.format_exc()}")
    sys.stdout.flush()
    sys.stderr.flush()
    stdout, stderr = "", f"Error: {e!r}"
    return 1

  finally:
    # 6. Atomically log this execution run.
    if log_file:
      result = {
          "exit_code": exit_code,
          "stdout": stdout,
          "stderr": stderr,
          "end_time_ms": int(
              datetime.datetime.now(datetime.timezone.utc).timestamp() * 1000
          ),
      }
      try:
        _write_history_status(
            log_file=log_file,
            args=actual_args,
            status="FINISHED",
            start_time=start_time,
            result=result,
            errors=errors,
        )
      except Exception:  # pylint: disable=broad-exception-caught
        pass


def main() -> None:
  """Executes the USMF stub."""
  rules_file = os.environ.get("USMF_RULES_FILE", "")
  state_file = os.environ.get("USMF_STATES_FILE", "")
  log_dir = os.environ.get("USMF_LOGS_DIR", "")
  actual_args = sys.argv[1:]

  exit_code = _execute_stub(
      rules_file=pathlib.Path(rules_file) if rules_file else None,
      state_file=pathlib.Path(state_file) if state_file else None,
      log_dir=pathlib.Path(log_dir) if log_dir else None,
      actual_args=actual_args,
  )
  sys.exit(exit_code)


if __name__ == "__main__":
  main()
