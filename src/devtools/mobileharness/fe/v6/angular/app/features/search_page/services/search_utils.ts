import {
  Cell,
  Column,
  ComplexMatch,
  Filter,
  FleetFilterChipMetadata,
  Indicator,
  TjsFilter,
} from '../../../core/models/search';

/** The set of supported search scope types. */
export type EntityType = 'devices' | 'hosts' | 'tests' | 'jobs' | 'sessions';

/** Representation of a search filter chip. */
export interface FilterChip {
  key?: string;
  pillKey: string;
  pillCondition: string;
  label?: string;
  value?: string;
  isGroupBy?: boolean;
  metadata?: FleetFilterChipMetadata;
  rawValues?: string[];
  negated?: boolean;
  complex?: ComplexMatch;
}

/** Suggestion item structure for the smart search box popup dropdown. */
export interface SearchBoxSuggestion {
  label?: string;
  mainText?: Array<{text?: string; emphasized?: boolean}>;
  count?: number;
  countPrefix?: string;
  countUnit?: string;
  overMax?: boolean;
  rawItem?: unknown;
}

/** Promoted filter key mapping descriptor. */
export interface PromotedFilterKeyItem {
  key: string;
  metadata?: FleetFilterChipMetadata;
}

/** Promoted group by key mapping descriptor. */
export interface PromotedGroupByKeyItem {
  key: string;
  displayName: string;
  groupCount?: number;
}

/** Single value option inside the Popover selector list. */
export interface PickerValueItem {
  value: string;
  displayLabel: string;
  filtered?: number;
  total?: number;
  isNoValue?: boolean;
  disabled?: boolean;
}

/** Deserialized/parsed result of a URL query string filter param. */
export interface ParsedQueryFilter {
  key: string;
  filter: Filter;
  tjsFilter: TjsFilter;
  fallbackPillCondition: string;
  metadata?: FleetFilterChipMetadata;
  rawValues?: string[];
  negated?: boolean;
  complex?: ComplexMatch;
}

/** Serializes a FilterChip object into a URL search filter parameter string. */
export function serializeFilterChip(c: FilterChip): string {
  const key = c.key || c.pillKey.toLowerCase();
  let prefix = c.negated ? '!' : '';

  if (
    c.pillCondition === '(no value)' ||
    c.pillCondition === '<empty>' ||
    (c.rawValues && c.rawValues.includes('<empty>'))
  ) {
    return `${prefix}${key}~`;
  }

  if (c.complex && Object.keys(c.complex).length > 0) {
    const cm = c.complex;
    let mode = '';
    let valStr = '';
    let isNegated = c.negated || false;

    if (cm.startsWith) {
      mode = 'starts';
      valStr = cm.startsWith.value || '';
    } else if (cm.containsSubstring) {
      mode = 'contains';
      valStr = cm.containsSubstring.value || '';
      if (cm.containsSubstring.negated) isNegated = true;
    } else if (cm.matchesRegex) {
      mode = 'regex';
      valStr = cm.matchesRegex.value || '';
      if (cm.matchesRegex.negated) isNegated = true;
    } else if (cm.matchesExactly) {
      mode = 'exactly';
      const vals =
        c.rawValues && c.rawValues.length > 0
          ? c.rawValues
          : cm.matchesExactly.values || [];
      valStr = vals.map((v) => encodeURIComponent(v)).join(',');
    } else if (cm.matchesAtLeast) {
      mode = 'atleast';
      const vals =
        c.rawValues && c.rawValues.length > 0
          ? c.rawValues
          : cm.matchesAtLeast.values || [];
      valStr = vals.map((v) => encodeURIComponent(v)).join(',');
    }

    if (mode) {
      const p = isNegated ? '!' : '';
      const encodedVal =
        mode === 'exactly' || mode === 'atleast'
          ? valStr
          : encodeURIComponent(valStr);
      return `${p}${key}~${mode}~${encodedVal}`;
    }
  }

  let cond = c.pillCondition.trim();
  if (cond.startsWith('!')) {
    prefix = '!';
    cond = cond.slice(1).trim();
  }

  const match = cond.match(
    /^(starts with|starts|prefix|startswith|starts_with|does not contain|not contain|doesnt contain|not_substring|not substring|contains substring|contains_substring|contains|substring|does not match regex|doesnt match regex|not_regex|not regex|matches regex|matches_regex|regex|matches exactly|matches_exactly|is exactly|exactly|matches at least|matches_at_least|is at least|at least|atleast)\b[\s:]*"?([^"]*)"?$/i,
  );
  if (match) {
    const rawMode = match[1].toLowerCase().trim().replace(/_/g, ' ');
    const val = match[2].replace(/^"|"$/g, '').trim();
    let mode = '';
    let isNegated = prefix === '!';
    if (
      rawMode === 'starts with' ||
      rawMode === 'starts' ||
      rawMode === 'prefix' ||
      rawMode === 'startswith'
    ) {
      mode = 'starts';
    } else if (
      rawMode === 'does not contain' ||
      rawMode === 'not contain' ||
      rawMode === 'doesnt contain' ||
      rawMode === 'not substring'
    ) {
      mode = 'contains';
      isNegated = true;
    } else if (
      rawMode === 'contains' ||
      rawMode === 'substring' ||
      rawMode === 'contains substring'
    ) {
      mode = 'contains';
    } else if (
      rawMode === 'does not match regex' ||
      rawMode === 'doesnt match regex' ||
      rawMode === 'not regex'
    ) {
      mode = 'regex';
      isNegated = true;
    } else if (rawMode === 'regex' || rawMode === 'matches regex') {
      mode = 'regex';
    } else if (
      rawMode === 'exactly' ||
      rawMode === 'is exactly' ||
      rawMode === 'matches exactly'
    ) {
      mode = 'exactly';
    } else if (
      rawMode === 'at least' ||
      rawMode === 'is at least' ||
      rawMode === 'atleast' ||
      rawMode === 'matches at least'
    ) {
      mode = 'atleast';
    } else {
      mode = rawMode;
    }

    const p = isNegated ? '!' : '';
    return `${p}${key}~${mode}~${encodeURIComponent(val)}`;
  }

  const vals =
    c.rawValues && c.rawValues.length > 0 ? c.rawValues : [c.pillCondition];
  return `${prefix}${key}~${vals.map((v) => encodeURIComponent(v)).join(',')}`;
}

/** Parses a serialized query filter parameter string back into a ParsedQueryFilter. */
export function parseQueryFilterParam(s: string): ParsedQueryFilter | null {
  if (!s) return null;
  let negated = false;
  if (s.startsWith('!')) {
    negated = true;
    s = s.slice(1);
  }
  const parts = s.split('~');
  if (parts.length < 2) return null;
  const key = parts[0];
  if (!key) return null;

  if (parts.length >= 3) {
    const mode = parts[1];
    const val = parts.slice(2).join('~');
    const decodedVals = val
      .split(',')
      .map((v) => decodeURIComponent(v))
      .filter(Boolean);

    const filter: Filter = {key};
    const complex: ComplexMatch = {};
    let isNegated = negated;

    if (mode === 'starts') {
      complex.startsWith = {value: val};
    } else if (mode === 'contains') {
      complex.containsSubstring = {
        value: val,
        negated: isNegated ? true : undefined,
      };
    } else if (mode === 'not_substring') {
      isNegated = true;
      complex.containsSubstring = {value: val, negated: true};
    } else if (mode === 'regex') {
      complex.matchesRegex = {
        value: val,
        negated: isNegated ? true : undefined,
      };
    } else if (mode === 'not_regex') {
      isNegated = true;
      complex.matchesRegex = {value: val, negated: true};
    } else if (mode === 'exactly') {
      complex.matchesExactly = {values: decodedVals};
    } else if (mode === 'atleast') {
      complex.matchesAtLeast = {values: decodedVals};
    }
    filter.complex = complex;

    const tjsFilter: TjsFilter = {key};
    if (mode === 'exactly' || mode === 'atleast') {
      tjsFilter.enumValues = {values: decodedVals};
    } else {
      tjsFilter.stringValue = {value: val};
    }

    const modeLabel =
      mode === 'starts'
        ? 'starts with'
        : mode === 'atleast'
          ? 'is at least'
          : mode === 'exactly'
            ? 'is exactly'
            : mode === 'contains'
              ? isNegated
                ? 'does not contain'
                : 'contains'
              : mode === 'regex'
                ? isNegated
                  ? 'does not match regex'
                  : 'matches regex'
                : mode === 'not_substring'
                  ? 'does not contain'
                  : mode === 'not_regex'
                    ? 'does not match regex'
                    : mode;
    const isMulti = mode === 'exactly' || mode === 'atleast';
    const fallbackPillCondition = `${modeLabel} "${isMulti ? decodedVals.join(', ') : val}"`;

    return {
      key,
      filter,
      tjsFilter,
      rawValues: decodedVals.length > 0 ? decodedVals : [val],
      fallbackPillCondition,
      negated: isNegated,
      complex,
    };
  }

  const rawVals = parts[1];
  if (!rawVals) {
    return {
      key,
      filter: {
        key,
        simple: {
          values: [{noValue: {}}],
          negated: negated ? true : undefined,
        },
      },
      tjsFilter: {
        key,
        stringValue: {value: '<empty>'},
      },
      rawValues: ['<empty>'],
      fallbackPillCondition: '(no value)',
      negated,
    };
  }

  const values = rawVals
    .split(',')
    .map((v) => decodeURIComponent(v))
    .filter(Boolean);
  const formattedVals = values
    .map((v) => (v === '<empty>' ? '(no value)' : v))
    .join(', ');
  return {
    key,
    filter: {
      key,
      simple: {
        values: values.map((v) => ({value: v})),
        negated: negated ? true : undefined,
      },
    },
    tjsFilter: {
      key,
      enumValues: key === 'status' || key === 'result' ? {values} : undefined,
      stringValue:
        key !== 'status' && key !== 'result'
          ? {value: values.join(', ')}
          : undefined,
    },
    rawValues: values,
    fallbackPillCondition: negated ? `!${formattedVals}` : formattedVals,
    negated,
  };
}

/** Formulates screen display text for a filter chip's condition value. */
export function getChipDisplayCondition(chip: FilterChip): string {
  const cond = chip.pillCondition;
  if (!cond) return '';
  const isAdvanced =
    cond.includes('"') ||
    /^(exactly|at least|starts with|ends with|contains|regex)\b/i.test(cond);
  if (isAdvanced) {
    return cond;
  }
  const prefix = chip.negated && !cond.startsWith('!') ? '!' : '';
  return `${prefix}${cond}`;
}

/** Extracts structured advanced condition metadata from a filter chip object. */
export function extractAdvancedStateFromChip(chip: FilterChip): {
  isAdv: boolean;
  advMode: string;
  advText: string;
  advValues: string[];
} {
  let isAdv = false;
  let advMode = 'prefix';
  let advText = '';
  let advValues: string[] = [];

  if (chip.complex && Object.keys(chip.complex).length > 0) {
    const cm = chip.complex;
    isAdv = true;
    if (cm.startsWith) {
      advMode = 'prefix';
      advText = cm.startsWith.value || '';
    } else if (cm.containsSubstring) {
      advMode =
        cm.containsSubstring.negated || chip.negated
          ? 'not_substring'
          : 'substring';
      advText = cm.containsSubstring.value || '';
    } else if (cm.matchesRegex) {
      advMode = cm.matchesRegex.negated || chip.negated ? 'not_regex' : 'regex';
      advText = cm.matchesRegex.value || '';
    } else if (cm.matchesExactly) {
      advMode = 'exactly';
      advValues = chip.rawValues || cm.matchesExactly.values || [];
    } else if (cm.matchesAtLeast) {
      advMode = 'at_least';
      advValues = chip.rawValues || cm.matchesAtLeast.values || [];
    }
  } else {
    let cond = chip.pillCondition.trim();
    let isNegatedFromPill = false;
    if (cond.startsWith('!')) {
      isNegatedFromPill = true;
      cond = cond.slice(1).trim();
    }

    const match = cond.match(
      /^(starts with|starts|prefix|startswith|starts_with|does not contain|not contain|doesnt contain|not_substring|not substring|contains substring|contains_substring|contains|substring|does not match regex|doesnt match regex|not_regex|not regex|matches regex|matches_regex|regex|matches exactly|matches_exactly|is exactly|exactly|matches at least|matches_at_least|is at least|at least|atleast)\b[\s:]*"?([^"]*)"?$/i,
    );
    if (match) {
      isAdv = true;
      let rawMode = match[1].toLowerCase().trim().replace(/_/g, ' ');
      const val = match[2].replace(/^"|"$/g, '').trim();

      if (isNegatedFromPill) {
        if (rawMode === 'contains' || rawMode === 'substring') {
          rawMode = 'does not contain';
        } else if (rawMode === 'regex' || rawMode === 'matches regex') {
          rawMode = 'does not match regex';
        }
      }

      if (
        rawMode === 'starts with' ||
        rawMode === 'starts' ||
        rawMode === 'prefix' ||
        rawMode === 'startswith'
      ) {
        advMode = 'prefix';
      } else if (
        rawMode === 'does not contain' ||
        rawMode === 'not contain' ||
        rawMode === 'doesnt contain' ||
        rawMode === 'not substring'
      ) {
        advMode = 'not_substring';
      } else if (
        rawMode === 'contains' ||
        rawMode === 'substring' ||
        rawMode === 'contains substring'
      ) {
        advMode = 'substring';
      } else if (
        rawMode === 'does not match regex' ||
        rawMode === 'doesnt match regex' ||
        rawMode === 'not regex'
      ) {
        advMode = 'not_regex';
      } else if (rawMode === 'regex' || rawMode === 'matches regex') {
        advMode = 'regex';
      } else if (
        rawMode === 'exactly' ||
        rawMode === 'is exactly' ||
        rawMode === 'matches exactly'
      ) {
        advMode = 'exactly';
      } else if (
        rawMode === 'at least' ||
        rawMode === 'is at least' ||
        rawMode === 'atleast' ||
        rawMode === 'matches at least'
      ) {
        advMode = 'at_least';
      }

      if (advMode === 'exactly' || advMode === 'at_least') {
        advValues =
          chip.rawValues && chip.rawValues.length > 0
            ? chip.rawValues
            : val
                .split(',')
                .map((v) => v.trim())
                .filter(Boolean);
      } else {
        advText = val;
      }
    }
  }

  return {isAdv, advMode, advText, advValues};
}

/** Resolves the Cell element for a column from a Row. */
export function getCell(
  row: Record<string, Cell | string | string[]> | undefined,
  colKey: string,
  columns: Column[],
): Cell | null {
  if (!row || !row['cells']) return null;
  const cells = row['cells'] as Cell[];
  const idx = columns.findIndex((c) => c.key === colKey);
  return idx === -1 ? null : cells[idx];
}

/** Extracts the string value representation of a Cell. */
export function getTextValue(cell: Cell | undefined): string | null {
  if (!cell) {
    return null;
  }
  if (cell.text !== undefined && cell.text !== null) {
    if (typeof cell.text === 'string') {
      return cell.text || null;
    }
    if (typeof cell.text === 'object' && cell.text.value) {
      return cell.text.value;
    }
  }
  if (cell.value !== undefined && cell.value !== null) {
    return String(cell.value);
  }
  return null;
}

/** Determines the semantic type classification for a Cell. */
export function getCellType(cell: Cell | undefined, col?: Column): string {
  if (!cell) {
    return 'unknown';
  }
  const kind = cell.kind || col?.kind;
  if (kind === 'KIND_ID_LINK' || cell.navTarget || cell.link) {
    return 'link';
  }
  if (kind === 'KIND_TIME') {
    return 'time';
  }
  if (kind === 'KIND_DURATION') {
    return 'duration';
  }
  if (kind === 'KIND_LIST') {
    return 'list';
  }
  if (cell.chips) {
    return 'chips';
  }
  if (cell.status !== undefined && cell.status !== null) {
    return 'status';
  }
  if (cell.multiLink !== undefined && cell.multiLink !== null) {
    return 'multilink';
  }
  if (
    (cell.text !== undefined && cell.text !== null) ||
    kind === 'KIND_TEXT' ||
    cell.value !== undefined
  ) {
    return 'text';
  }
  return 'unknown';
}

/** Constructs RouterLink path segments for cell targets. */
export function getRouterLink(
  cell: Cell | undefined,
  entity: string,
): string[] | null {
  if (!cell) {
    return null;
  }
  if (cell.link?.target) {
    const target = cell.link.target;
    if (target.device?.id) {
      return ['/devices', target.device.id];
    }
    if (target.host?.hostName) {
      return ['/hosts', target.host.hostName];
    }
    if (target.test?.testId) {
      return ['/tests', target.test.testId];
    }
    if (target.job?.jobId) {
      return ['/jobs', target.job.jobId];
    }
    if (target.session?.sessionId) {
      return ['/sessions', target.session.sessionId];
    }
  }
  const target = cell.navTarget as unknown as {
    targetType?: string;
    targetId?: string;
  };
  if (target?.targetId) {
    if (
      target.targetType === 'NAV_TARGET_TEST_DETAIL' ||
      target.targetType === 'TEST_DETAIL'
    ) {
      return ['/tests', target.targetId];
    }
    if (
      target.targetType === 'NAV_TARGET_JOB_DETAIL' ||
      target.targetType === 'JOB_DETAIL'
    ) {
      return ['/jobs', target.targetId];
    }
    if (
      target.targetType === 'NAV_TARGET_SESSION_DETAIL' ||
      target.targetType === 'SESSION_DETAIL'
    ) {
      return ['/sessions', target.targetId];
    }
    return ['/' + entity, target.targetId];
  }
  if (cell.value) {
    return ['/' + entity, String(cell.value)];
  }
  return null;
}

/** Computes the CSS status class name for cell status indicators. */
export function getStatusClass(
  cell:
    | Cell
    | {status?: {text?: string}; indicator?: unknown; value?: string}
    | undefined,
): string {
  if (!cell) {
    return 'status-neutral';
  }
  const indicator = (cell as unknown as {indicator?: unknown}).indicator;
  if (
    indicator !== undefined &&
    indicator !== null &&
    indicator !== 'INDICATOR_UNSPECIFIED' &&
    indicator !== Indicator.INDICATOR_UNSPECIFIED &&
    indicator !== 0
  ) {
    if (
      indicator === 'INDICATOR_GOOD' ||
      indicator === Indicator.INDICATOR_OK ||
      indicator === 'INDICATOR_OK' ||
      indicator === 1
    ) {
      return 'status-ok';
    }
    if (
      indicator === Indicator.INDICATOR_ACTIVE ||
      indicator === 'INDICATOR_ACTIVE' ||
      indicator === 2
    ) {
      return 'status-active';
    }
    if (
      indicator === Indicator.INDICATOR_ERROR ||
      indicator === 'INDICATOR_ERROR' ||
      indicator === 3
    ) {
      return 'status-error';
    }
    return 'status-neutral';
  }
  const txt = (
    cell.status?.text ||
    (cell as unknown as {status?: unknown}).status ||
    cell.value ||
    ''
  )
    .toString()
    .toUpperCase();
  if (
    [
      'PASS',
      'PASSED',
      'DONE',
      'FINISHED',
      'SUCCEEDED',
      'COMPLETED',
      'HEALTHY',
      'IDLE',
      'READY',
      'ACTIVE',
      'RUNNING',
    ].includes(txt)
  ) {
    return txt === 'RUNNING' || txt === 'ACTIVE'
      ? 'status-active'
      : 'status-ok';
  }
  if (
    [
      'FAIL',
      'ERROR',
      'FAILED',
      'ABORT',
      'TIMEOUT',
      'CANCELLED',
      'EXPIRED',
      'BUSY',
      'OFFLINE',
      'DRAINING',
    ].includes(txt)
  ) {
    return 'status-error';
  }
  return 'status-neutral';
}

/** Formats a timestamp into a locale date-time string. */
export function formatTime(val: string | number | undefined): string | null {
  if (!val) {
    return null;
  }
  const num = Number(val);
  if (isNaN(num) || num <= 0) {
    return String(val) || null;
  }
  const d = new Date(num);
  return d.toLocaleString();
}

/** Formats duration milliseconds into a readable scale representation (h/m/s). */
export function formatDuration(
  val: string | number | undefined,
): string | null {
  if (!val) {
    return null;
  }
  const num = Number(val);
  if (isNaN(num)) {
    return String(val) || null;
  }
  const sec = Math.round(num / 1000);
  if (sec < 60) {
    return `${sec}s`;
  }
  const min = Math.floor(sec / 60);
  const remSec = sec % 60;
  if (min < 60) {
    return remSec > 0 ? `${min}m ${remSec}s` : `${min}m`;
  }
  const hrs = Math.floor(min / 60);
  const remMin = min % 60;
  return remMin > 0 ? `${hrs}h ${remMin}m` : `${hrs}h`;
}
