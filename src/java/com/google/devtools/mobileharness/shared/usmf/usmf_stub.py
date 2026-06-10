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
import fcntl
import json
import logging
import operator
import os
import pathlib
import re
import shlex
import sys
import time
import traceback
from typing import Any
import uuid


_INNERMOST_PATTERN = re.compile(r"\$\{([^{}]+?)}")
_FMT_PATTERN = re.compile(
    r"""
    :\s*
    (?P<quote>['"])  # capture group 'quote' matching either single or double quote
    (?P<fmt>.*?)     # capture group 'fmt' matching the format string non-greedily
    (?P=quote)       # backreference to match the same quote type
    \s*$
    """,
    re.VERBOSE | re.DOTALL,
)
_COAL_PATTERN = re.compile(
    r"""
    \?\s*
    (?P<quote>['"])  # capture group 'quote' matching either single or double quote
    (?P<coal>.*?)    # capture group 'coal' matching the coalescing string non-greedily
    (?P=quote)       # backreference to match the same quote type
    \s*$
    """,
    re.VERBOSE | re.DOTALL,
)
_ROOT_EXPR_PATTERN = re.compile(r"(#[CSV])(\s*\[.*)?")
_INT_FORMAT_PATTERN = re.compile("%[# 0-9+ .-]*[dioxX]")
_FLOAT_FORMAT_PATTERN = re.compile("%[# 0-9+ .-]*[fFegFEG]")


# Helper functions needed before AST classes
def _try_parse_number(val: Any) -> int | float | None:
  """Parses numeric values, supporting floats and scientific notations."""
  if isinstance(val, (int, float)) and not isinstance(val, bool):
    return val
  try:
    f_val = float(val)
    return int(f_val) if f_val.is_integer() else f_val
  except (ValueError, TypeError):
    return None


def _format_single(val: Any, fmt: str | None) -> str:
  """Formats a single primitive using format patterns."""
  if val is None:
    return ""
  if fmt is None:
    return str(val)
  # Coerce string to numeric typings if expected by print specifiers.
  if isinstance(val, str):
    try:
      if _INT_FORMAT_PATTERN.search(fmt):
        format_val = int(float(val))
      elif _FLOAT_FORMAT_PATTERN.search(fmt):
        format_val = float(val)
      else:
        format_val = val
    except ValueError:
      format_val = val
  else:
    format_val = val

  try:
    return fmt % format_val
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
    context_s: MutableMapping[str, Any],
    context_v: Mapping[str, Any],
) -> str:
  """Resolves placeholder substitutions in a template string.

  Args:
    text: The template string.
    context_c: The captured variables mapping.
    context_s: The state variables mapping.
    context_v: The static configuration variables mapping.

  Returns:
    The resolved string.
  """

  def replace(match: re.Match[str]) -> str:
    expr_str = match.group(1).strip()
    try:
      tree = parse_expr(expr_str)

      node_res = tree.evaluate(
          context_c=context_c, context_s=context_s, context_v=context_v
      )
      val = node_res.value if node_res is not None else None
    except Exception:  # pylint: disable=broad-exception-caught
      return match.group(0)

    if isinstance(val, (list, tuple, set)):
      return ", ".join(str(item) for item in val)
    return str(val) if val is not None else ""

  for _ in range(5):
    reconstructed, count = _INNERMOST_PATTERN.subn(replace, text)
    if count == 0:
      break
    text = reconstructed

  return text


class ContextNode:
  """An evaluated variable, value, or container."""

  @property
  def value(self) -> Any:
    """The underlying evaluated python object value."""
    del self
    raise NotImplementedError


@dataclasses.dataclass
class CaptureNode(ContextNode):
  """A read-only node containing command captures scope (#C) data."""

  _value: Any

  @property
  def value(self) -> Any:
    """The underlying command captures dictionary value."""
    return self._value


@dataclasses.dataclass
class VariableNode(ContextNode):
  """A read-only node containing configuration variables scope (#V) data."""

  _value: Any

  @property
  def value(self) -> Any:
    """The underlying configuration variables dictionary value."""
    return self._value


@dataclasses.dataclass
class StateNode(ContextNode):
  """A mutable node containing state scope (#S) data.

  Attributes:
    parent: The parent container (dict or list) holding this state value.
    key: The key or list index within the parent container.
    _value: The underlying mutable state value.
  """

  parent: Any
  key: Any
  _value: Any

  def __init__(self, *, parent: Any, key: Any, value: Any):
    """Initializes the instance."""
    self.parent = parent
    self.key = key
    self._value = value

  @property
  def value(self) -> Any:
    """The underlying mutable state value."""
    return self._value

  def mutate(self, op: str, value: Any, errors: MutableSequence[str]) -> None:
    """Mutates the state referenced by this node in the parent container.

    Args:
      op: The mutation operator, e.g., 'set', 'plus', 'add_to_list',
        'add_to_set'.
      value: The value representing the mutation argument.
      errors: A list to append any mutation errors. This parameter is mutated.
    """
    if self.parent is None or self.key is None:
      errors.append("USMF Mutation Error: root state reference is read-only")
      return

    key_str = str(self.key)

    if isinstance(self.parent, list):
      self._mutate_list(op, key_str, value, errors)
    else:
      self._mutate_dict(op, key_str, value, errors)

  def _mutate_list(
      self, op: str, key_str: str, value: Any, errors: MutableSequence[str]
  ) -> None:
    """Applies mutation to self.parent when it is a list.

    Args:
      op: The mutation operator.
      key_str: The key or list index.
      value: The value to apply.
      errors: A list to collect error messages. This list is mutated.
    """
    try:
      idx = int(float(key_str))
    except (ValueError, TypeError):
      errors.append(f"USMF Mutation error: Invalid list index: {key_str!r}")
      return

    if not (0 <= idx < len(self.parent)):
      errors.append(f"USMF Mutation error: Index out of bounds: {idx}")
      return

    if op == "set":
      self.parent[idx] = value
      return

    if op == "plus":
      curr_num = _try_parse_number(self.parent[idx]) or 0
      val_num = _try_parse_number(value)
      if val_num is None:
        errors.append(
            f"USMF Mutation error: cannot convert {value!r} to number"
        )
        return
      self.parent[idx] = curr_num + val_num
      return

    if op in ("add_to_list", "add_to_set"):
      lst = self.parent[idx]
      if not isinstance(lst, list):
        self.parent[idx] = [value]
      elif op == "add_to_list" or value not in lst:
        lst.append(value)

  def _mutate_dict(
      self, op: str, key_str: str, value: Any, errors: MutableSequence[str]
  ) -> None:
    """Applies mutation to self.parent when it is a dict.

    Args:
      op: The mutation operator.
      key_str: The dictionary key.
      value: The value to apply.
      errors: A list to collect error messages. This list is mutated.
    """
    if op == "set":
      self.parent[key_str] = value
      return

    if op == "plus":
      curr_val = self.parent.get(key_str)
      curr_num = _try_parse_number(curr_val if curr_val is not None else 0) or 0
      val_num = _try_parse_number(value)
      if val_num is None:
        errors.append(
            f"USMF Mutation error: cannot convert {value!r} to number"
        )
        return
      self.parent[key_str] = curr_num + val_num
      return

    if op in ("add_to_list", "add_to_set"):
      lst = self.parent.get(key_str)
      if not isinstance(lst, list):
        self.parent[key_str] = [value]
      elif op == "add_to_list" or value not in lst:
        lst.append(value)


class Expr:
  """An AST expression node."""

  def evaluate(
      self,
      *,
      context_c: Mapping[str, Any],
      context_s: MutableMapping[str, Any],
      context_v: Mapping[str, Any],
      is_pure_left_leaf: bool = False,
      is_mutation_target: bool = False,
  ) -> ContextNode | None:
    """Evaluates the expression under the given execution context.

    Args:
      context_c: The command capture variables mapping.
      context_s: The state model mapping.
      context_v: Static configuration variables mapping.
      is_pure_left_leaf: If True, indicates this is the leftmost target without
        sub-indexing.
      is_mutation_target: If True, indicates this evaluation is for a mutation
        assignment.

    Returns:
      The evaluated ContextNode containing the value, or None if evaluation
      fails.
    """
    del (
        self,
        context_c,
        context_s,
        context_v,
        is_pure_left_leaf,
        is_mutation_target,
    )
    raise NotImplementedError


@dataclasses.dataclass
class LiteralExpr(Expr):
  """An AST expression node containing a raw string literal.

  Attributes:
    value: The raw string value.
  """

  value: str

  def evaluate(
      self,
      *,
      context_c: Mapping[str, Any],
      context_s: MutableMapping[str, Any],
      context_v: Mapping[str, Any],
      is_pure_left_leaf: bool = False,
      is_mutation_target: bool = False,
  ) -> ContextNode | None:
    """Evaluates the literal expression by resolving placeholders in the string value.

    Args:
      context_c: The command capture variables mapping.
      context_s: The state model mapping.
      context_v: Static configuration variables mapping.
      is_pure_left_leaf: Unused.
      is_mutation_target: Unused.

    Returns:
      A VariableNode containing the resolved string value.
    """
    del is_pure_left_leaf, is_mutation_target
    return VariableNode(
        _resolve_string(
            self.value,
            context_c=context_c,
            context_s=context_s,
            context_v=context_v,
        )
    )


@dataclasses.dataclass
class ContextExpr(Expr):
  """An AST expression node containing a context variable root.

  Attributes:
    name: The name of the context root, e.g., '#C', '#S', '#V'.
  """

  name: str

  def evaluate(
      self,
      *,
      context_c: Mapping[str, Any],
      context_s: MutableMapping[str, Any],
      context_v: Mapping[str, Any],
      is_pure_left_leaf: bool = False,
      is_mutation_target: bool = False,
  ) -> ContextNode | None:
    """Evaluates the context expression to access root scopes.

    Args:
      context_c: The command capture variables mapping.
      context_s: The state model mapping.
      context_v: Static configuration variables mapping.
      is_pure_left_leaf: Unused.
      is_mutation_target: Unused.

    Returns:
      The ContextNode representing the matched context root scope, or None.
    """
    del is_pure_left_leaf, is_mutation_target
    if self.name == "#C":
      return CaptureNode(context_c)
    if self.name == "#S":
      return StateNode(parent=None, key=None, value=context_s)
    if self.name == "#V":
      return VariableNode(context_v)
    return None


@dataclasses.dataclass
class IndexAccessExpr(Expr):
  """An AST expression node containing target[index] lookup.

  Attributes:
    target: The target expression.
    index: The index expression.
  """

  target: Expr
  index: Expr

  def evaluate(
      self,
      *,
      context_c: Mapping[str, Any],
      context_s: MutableMapping[str, Any],
      context_v: Mapping[str, Any],
      is_pure_left_leaf: bool = False,
      is_mutation_target: bool = False,
  ) -> ContextNode | None:
    """Evaluates target[index] expressions.

    Args:
      context_c: The command capture variables mapping.
      context_s: The state model mapping.
      context_v: Static configuration variables mapping.
      is_pure_left_leaf: If True, indicates this is the leftmost target without
        sub-indexing.
      is_mutation_target: If True, indicates this evaluation is for a mutation
        assignment.

    Returns:
      The evaluated ContextNode containing the value from the lookup, or None.
    """
    next_left_leaf = is_pure_left_leaf or is_mutation_target

    vl = self.target.evaluate(
        context_c=context_c,
        context_s=context_s,
        context_v=context_v,
        is_pure_left_leaf=next_left_leaf,
        is_mutation_target=is_mutation_target,
    )
    if vl is None:
      return None

    vr_node = self.index.evaluate(
        context_c=context_c,
        context_s=context_s,
        context_v=context_v,
        is_pure_left_leaf=False,
        is_mutation_target=False,
    )
    if vr_node is None:
      return None

    vr = vr_node.value
    if vr is None:
      return None

    vr_str = str(vr)
    vl_val = vl.value

    if isinstance(vl_val, dict):
      if (
          isinstance(vl, StateNode)
          and is_mutation_target
          and is_pure_left_leaf
          and (vr_str not in vl_val)
      ):
        vl_val[vr_str] = {}
        return StateNode(parent=vl_val, key=vr_str, value=vl_val[vr_str])

      if vr_str in vl_val:
        next_val = vl_val[vr_str]
        if isinstance(vl, StateNode):
          return StateNode(parent=vl_val, key=vr_str, value=next_val)
        elif isinstance(vl, CaptureNode):
          return CaptureNode(next_val)
        elif isinstance(vl, VariableNode):
          return VariableNode(next_val)

      if isinstance(vl, StateNode) and is_mutation_target:
        return StateNode(parent=vl_val, key=vr_str, value=None)

      return None

    elif isinstance(vl_val, (list, tuple)):
      try:
        idx = int(float(vr_str))
        if 0 <= idx < len(vl_val):
          next_val = vl_val[idx]
          if isinstance(vl, StateNode):
            return StateNode(parent=vl_val, key=idx, value=next_val)
          elif isinstance(vl, CaptureNode):
            return CaptureNode(next_val)
          elif isinstance(vl, VariableNode):
            return VariableNode(next_val)
      except (ValueError, TypeError):
        pass
      return None

    return None


@dataclasses.dataclass
class GeneralExpr(Expr):
  """An AST expression node containing suffix configurations.

  Attributes:
    node_expr: The base expression.
    coalescing_val: Optional default fallback value.
    formatting_val: Optional formatting suffix directive string.
  """

  node_expr: Expr
  coalescing_val: str | None = None
  formatting_val: str | None = None

  def evaluate(
      self,
      *,
      context_c: Mapping[str, Any],
      context_s: MutableMapping[str, Any],
      context_v: Mapping[str, Any],
      is_pure_left_leaf: bool = False,
      is_mutation_target: bool = False,
  ) -> ContextNode | None:
    """Evaluates expression node and applies coalescing and formatting suffixes.

    Args:
      context_c: The command capture variables mapping.
      context_s: The state model mapping.
      context_v: Static configuration variables mapping.
      is_pure_left_leaf: If True, indicates this is the leftmost target without
        sub-indexing.
      is_mutation_target: If True, indicates this evaluation is for a mutation
        assignment.

    Returns:
      The evaluated ContextNode, or None.
    """
    node_res = self.node_expr.evaluate(
        context_c=context_c,
        context_s=context_s,
        context_v=context_v,
        is_pure_left_leaf=is_pure_left_leaf,
        is_mutation_target=is_mutation_target,
    )
    if self.coalescing_val is None and self.formatting_val is None:
      return node_res

    raw_val = node_res.value if node_res is not None else None

    # Safety Defaulting (? Suffix)
    defaulted_val = self.coalescing_val if raw_val is None else raw_val

    # Value Formatting (: Suffix)
    if defaulted_val is not None and self.formatting_val is not None:
      final_val = _format_value(defaulted_val, self.formatting_val)
    else:
      final_val = defaulted_val

    return VariableNode(final_val)


def _find_matching_bracket(s: str, start_idx: int) -> int:
  """Finds the index of the matching closing bracket ']' for s[start_idx] == '['."""
  depth = 0
  in_quote = None  # None, "'" or '"'
  escaped = False
  for offset, c in enumerate(s[start_idx:]):
    i = start_idx + offset
    if escaped:
      escaped = False
      continue
    if c == "\\":
      escaped = True
      continue
    if in_quote:
      if c == in_quote:
        in_quote = None
    elif c in ("'", '"'):
      in_quote = c
    elif c == "[":
      depth += 1
    elif c == "]":
      depth -= 1
      if depth == 0:
        return i
  raise ValueError(f"USMF Syntax Error: Unmatched bracket in {s!r}")


def _parse_node_expr(s: str) -> Expr:
  """Parses a string statement into an AST Expr object."""
  s = s.strip()
  if not s:
    raise ValueError("USMF Syntax Error: Empty expression")

  match = _ROOT_EXPR_PATTERN.fullmatch(s)
  if not match:
    raise ValueError(
        f"USMF Syntax Error: Literal strings must be quoted: {s!r}"
    )

  root_name = match.group(1)
  rem = match.group(2).strip() if match.group(2) else ""

  if root_name == "#C":
    root = ContextExpr("#C")
  elif root_name == "#S":
    root = ContextExpr("#S")
  elif root_name == "#V":
    root = ContextExpr("#V")
  else:
    raise ValueError(
        f"USMF Syntax Error: Literal strings must be quoted: {s!r}"
    )

  target = root
  while rem:
    if not rem.startswith("["):
      raise ValueError(f"USMF Syntax Error: Expected '[' in {s!r}")
    match_idx = _find_matching_bracket(rem, 0)
    index_str = rem[1:match_idx]
    target = IndexAccessExpr(target, parse_expr(index_str))
    rem = rem[match_idx + 1 :].strip()

  return target


def parse_expr(s: str) -> Expr:
  """Recursively parses a string expression into an AST."""
  expr_str = s.strip()
  if not expr_str:
    return LiteralExpr("")

  if (
      len(expr_str) >= 2
      and expr_str.startswith(("'", '"'))
      and expr_str[0] == expr_str[-1]
  ):
    return LiteralExpr(expr_str[1:-1])

  coalescing_val = None
  formatting_val = None

  # Split formatting and coalescing suffixes from right to left
  fmt_match = _FMT_PATTERN.search(expr_str)
  if fmt_match:
    formatting_val = fmt_match.group("fmt")
    expr_str_without_fmt = expr_str[: fmt_match.start()].strip()
  else:
    expr_str_without_fmt = expr_str

  coal_match = _COAL_PATTERN.search(expr_str_without_fmt)
  if coal_match:
    coalescing_val = coal_match.group("coal")
    expr_str_final = expr_str_without_fmt[: coal_match.start()].strip()
  else:
    expr_str_final = expr_str_without_fmt

  return GeneralExpr(
      _parse_node_expr(expr_str_final), coalescing_val, formatting_val
  )


def _validate_state_node_expr(tree: Expr, expr_str: str) -> None:
  """Validates that a parsed Expr is a valid target context for mutation."""
  if isinstance(tree, GeneralExpr):
    if tree.coalescing_val is not None or tree.formatting_val is not None:
      raise SyntaxError(
          "USMF Syntax Error: Coalescing or formatting operator is not"
          f" allowed in state_node target: {expr_str!r}"
      )
    _validate_state_node_expr(tree.node_expr, expr_str)
    return

  if isinstance(tree, IndexAccessExpr):
    _validate_state_node_expr(tree.target, expr_str)
    return

  if isinstance(tree, ContextExpr):
    if tree.name != "#S":
      raise SyntaxError(
          "USMF Syntax Error: State node reference must start with '#S':"
          f" {expr_str!r}"
      )
    return

  raise SyntaxError(
      f"USMF Syntax Error: Invalid state_node target expression: {expr_str!r}"
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
    state_node: The state node to mutate.
    op: The mutation operation (e.g., "set", "plus", "add_to_list").
    value: The value to use in the mutation.
  """

  state_node: str
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
    state_node: The state node expression to check.
    op: The comparison operator (e.g., "eq", "gt", "contains").
    expected_value: The value to compare against the state variable.
  """

  state_node: str
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
      state_node=cond.get("state_node"),
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
      StateMutation(
          state_node=m.get("state_node"), op=m.get("op"), value=m.get("value")
      )
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
  if actual is None or expected is None:
    if op == "eq":
      return actual is expected
    if op == "contains":
      if isinstance(actual, (list, tuple, set)):
        return any(item is None for item in actual)
      if isinstance(actual, dict):
        return any(key is None for key in actual)
      return False
    return False

  if op == "contains":
    if not isinstance(actual, (list, tuple, set, dict)):
      return str(expected) in str(actual)

    # Handle list, tuple, set, dict for 'contains'.
    items_to_check = actual.keys() if isinstance(actual, dict) else actual
    for item in items_to_check:
      try:
        c_item, c_exp = _coerce_to_same_type(val1=item, val2=expected)
        if c_item == c_exp:
          return True
      except Exception:  # pylint: disable=broad-exception-caught
        pass
    return False

  try:
    c_actual, c_expected = _coerce_to_same_type(val1=actual, val2=expected)
  except Exception as e:  # pylint: disable=broad-exception-caught
    errors.append(
        f"Type coercion fail: actual={actual!r}, expected={expected!r},"
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


def _interpolate_value(
    val: Any,
    *,
    captures: Mapping[str, Any],
    state: MutableMapping[str, Any],
    variables: Mapping[str, Any],
) -> Any:
  """Recursively interpolates regex captures and state placeholders.

  Args:
    val: The value to recursively interpolate.
    captures: Captured command parameters scope mapping (#C).
    state: Mutable state database scope mapping (#S).
    variables: Static configuration variables scope mapping (#V).

  Returns:
    The interpolated value with resolved placeholders.
  """
  if isinstance(val, str):
    return _resolve_string(
        val, context_c=captures, context_s=state, context_v=variables
    )
  if isinstance(val, list):
    return [
        _interpolate_value(
            item, captures=captures, state=state, variables=variables
        )
        for item in val
    ]
  if isinstance(val, dict):
    return {
        _interpolate_value(
            k, captures=captures, state=state, variables=variables
        ): _interpolate_value(
            v, captures=captures, state=state, variables=variables
        )
        for k, v in val.items()
    }
  if dataclasses.is_dataclass(val):
    kwargs = {
        f.name: _interpolate_value(
            getattr(val, f.name),
            captures=captures,
            state=state,
            variables=variables,
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
    *,
    rule: Rule,
    actual_args: Sequence[str],
    context_s: MutableMapping[str, Any],
    context_v: Mapping[str, Any],
    errors: MutableSequence[str],
) -> tuple[bool, Mapping[str, Any]]:
  """Evaluates command and variable state conditions for a rule.

  Args:
    rule: The rule to evaluate.
    actual_args: The command-line arguments.
    context_s: The current state dictionary.
    context_v: Static configuration variables mapping.
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

    raw_state_node = cond.state.state_node
    raw_expected_value = cond.state.expected_value

    try:
      tree_state_node = parse_expr(raw_state_node)
      _validate_state_node_expr(tree_state_node, raw_state_node)
      node_res = tree_state_node.evaluate(
          context_c=context_c, context_s=context_s, context_v=context_v
      )
      state_val = node_res.value if node_res is not None else None
    except Exception:  # pylint: disable=broad-exception-caught
      state_val = None

    expected_val = _interpolate_value(
        raw_expected_value,
        captures=context_c,
        state=context_s,
        variables=context_v,
    )

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
  if not lock_path:
    yield
    return
  lock_path.parent.mkdir(parents=True, exist_ok=True)
  with lock_path.open("w", encoding="utf-8") as lock_fd:
    fcntl.lockf(lock_fd, fcntl.LOCK_EX)
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


def _apply_state_mutations(
    *,
    state_mutations: Sequence[StateMutation],
    state: MutableMapping[str, Any],
    captures: Mapping[str, Any],
    variables: Mapping[str, Any],
    errors: MutableSequence[str],
) -> None:
  """Mutates state database with raw expression evaluation.

  Args:
    state_mutations: The state mutations to apply.
    state: The state repository.
    captures: Captured variables mapping.
    variables: Static configuration variables mapping.
    errors: List to collect errors.
  """
  for mutation in state_mutations or []:
    raw_state_node = mutation.state_node
    if not raw_state_node:
      continue
    op = mutation.op
    if op not in ("set", "plus", "add_to_list", "add_to_set"):
      errors.append(f"USMF Mutation error: unknown op {op!r}")
      continue

    try:
      tree = parse_expr(raw_state_node)
      _validate_state_node_expr(tree, raw_state_node)
      node_res = tree.evaluate(
          context_c=captures,
          context_s=state,
          context_v=variables,
          is_pure_left_leaf=False,
          is_mutation_target=True,
      )
      if not isinstance(node_res, StateNode):
        errors.append(
            "USMF Mutation error: target must be a mutable state node:"
            f" {raw_state_node!r}"
        )
        continue

      interpolated_value = _interpolate_value(
          mutation.value, captures=captures, state=state, variables=variables
      )
      node_res.mutate(op, interpolated_value, errors)
    except Exception as e:  # pylint: disable=broad-exception-caught
      logging.exception(
          "USMF Mutation error during processing state_node %r with"
          " mutation %r",
          raw_state_node,
          mutation,
      )
      errors.append(
          f"USMF Mutation error for state_node {raw_state_node!r}: {e!r}"
      )


def _execute_side_effects(
    *,
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

  # We use distinct variables for different contexts to avoid reassignment.
  default_status_code = 1
  default_stdout = ""
  default_stderr = ""

  # Calculate log file path if log directory is available.
  log_file = (
      log_dir.joinpath(f"history_{uuid.uuid4()}.json") if log_dir else None
  )

  try:
    # 1. Load Rules configuration before entering the state lock.
    if rules_file is not None:
      try:
        with rules_file.open("r", encoding="utf-8") as f:
          raw_config = json.load(f)
          raw_rules = raw_config.get("rules", [])
          rules = [_parse_rule(r) for r in raw_rules]
          variables = raw_config.get("variables", {})
      except FileNotFoundError:
        rules = []
        variables = {}
      except Exception as e:  # pylint: disable=broad-exception-caught
        logging.exception("Mock USMF JSON Error for rules file %r", rules_file)
        errors.append(
            f"Mock USMF JSON Error for rules file {rules_file!r}: {e!r}"
        )
        return 1
    else:
      rules = []
      variables = {}

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
              context_v=variables,
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
            variables=variables,
            errors=errors,
        )
        if state_file is not None and behavior_config.state_mutations:
          _write_json_atomic(state_file, state, errors)

    # 3. Resolve behavior outputs outside of the state lock.

    # Dynamically interpolate output fields with matched capture groups, state
    # and variables.
    active_stdout = _interpolate_value(
        behavior_config.stdout,
        captures=captures,
        state=state,
        variables=variables,
    )
    active_stderr = _interpolate_value(
        behavior_config.stderr,
        captures=captures,
        state=state,
        variables=variables,
    )
    active_status_code = behavior_config.exit_code
    side_effects = _interpolate_value(
        behavior_config.side_effects,
        captures=captures,
        state=state,
        variables=variables,
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
    _execute_side_effects(side_effects=side_effects, errors=errors)

    print(active_stdout, end="", flush=True)
    print(active_stderr, end="", file=sys.stderr, flush=True)
    return active_status_code

  except Exception as e:  # pylint: disable=broad-exception-caught
    logging.exception(
        "Unexpected error during USMF stub execution. args: %r",
        actual_args,
    )
    errors.append(f"Unexpected error: {e!r}\n{traceback.format_exc()}")
    sys.stdout.flush()
    sys.stderr.flush()
    active_stdout = ""
    active_stderr = f"Error: {e!r}"
    active_status_code = 1
    return 1

  finally:
    # 6. Atomically log this execution run.
    if log_file:
      stdout_for_log = (
          active_stdout if "active_stdout" in locals() else default_stdout
      )
      stderr_for_log = (
          active_stderr if "active_stderr" in locals() else default_stderr
      )
      exit_code_for_log = (
          active_status_code
          if "active_status_code" in locals()
          else default_status_code
      )
      result = {
          "exit_code": exit_code_for_log,
          "stdout": stdout_for_log,
          "stderr": stderr_for_log,
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
