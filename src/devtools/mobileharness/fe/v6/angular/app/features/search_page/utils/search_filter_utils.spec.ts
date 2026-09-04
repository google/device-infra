import {ActivatedRoute, Params, Router} from '@angular/router';

import {
  ComplexMatch,
  Filter,
  Fleet,
  FleetSuggestion,
  SearchEntity,
  TjsEntity,
  TjsFilter,
  TjsSuggestion,
} from '../../../core/models/search';
import {EntityType, FilterChip} from '../models';
import {
  EMPTY_FILTER_VALUE,
  buildFleetFilterFromChip,
  buildFleetGroupSort,
  buildResolvedFilterChips,
  buildSimpleFleetFilter,
  buildUrlParamKey,
  createComplexMatch,
  createGroupByChip,
  deduplicateFilterChipsByKey,
  extractComplexMatchInfo,
  extractFilterChipFromFleetSuggestion,
  extractFilterChipFromTjsSuggestion,
  extractRawValuesFromTjsFilter,
  getChipKey,
  getInitialRouterUrl,
  getNormalizedChipKey,
  getQueryParamAsArray,
  getSerializedChipsKey,
  isChipNegated,
  isSameFilterChip,
  isSearchRouteActive,
  normalizeKey,
  parseComplexCondition,
  parseQueryFilterParam,
  parseUrlChips,
  resolveEntityFromPathOrUrl,
  resolveInitialChips,
  resolveInitialFleet,
  serializeComplexCondition,
  serializeFilterChip,
  toFleetProto,
  toSearchEntityProto,
  toTjsEntityProto,
} from './search_filter_utils';

describe('search_filter_utils', () => {
  describe('createComplexMatch', () => {
    it('creates StartsWith for prefix mode (NOT negatable per search_fleet.proto)', () => {
      expect(createComplexMatch('prefix', 'Pixel', false)).toEqual({
        startsWith: {value: 'Pixel'},
      });
      expect(createComplexMatch('prefix', 'Pixel', true)).toEqual({
        startsWith: {value: 'Pixel'},
      });
      expect(createComplexMatch('prefix', '')).toBeUndefined();
    });

    it('creates ContainsSubstring for substring and not_substring/not_contains', () => {
      expect(createComplexMatch('substring', 'Google', false)).toEqual({
        containsSubstring: {value: 'Google', negated: false},
      });
      expect(createComplexMatch('substring', 'Google', true)).toEqual({
        containsSubstring: {value: 'Google', negated: true},
      });
      expect(createComplexMatch('not_substring', 'Google')).toEqual({
        containsSubstring: {value: 'Google', negated: true},
      });
      expect(createComplexMatch('not_contains', 'Google')).toEqual({
        containsSubstring: {value: 'Google', negated: true},
      });
    });

    it('creates MatchesRegex for regex and not_regex', () => {
      expect(createComplexMatch('regex', '^lab.*', false)).toEqual({
        matchesRegex: {value: '^lab.*', negated: false},
      });
      expect(createComplexMatch('regex', '^lab.*', true)).toEqual({
        matchesRegex: {value: '^lab.*', negated: true},
      });
      expect(createComplexMatch('not_regex', '^lab.*')).toEqual({
        matchesRegex: {value: '^lab.*', negated: true},
      });
    });

    it('creates MatchesExactly and MatchesAtLeast for exact and at_least', () => {
      expect(createComplexMatch('exact', ['IDLE', 'BUSY'])).toEqual({
        matchesExactly: {values: ['IDLE', 'BUSY']},
      });
      expect(createComplexMatch('exactly', 'IDLE')).toEqual({
        matchesExactly: {values: ['IDLE']},
      });
      expect(createComplexMatch('at_least', ['tagA', 'tagB'])).toEqual({
        matchesAtLeast: {values: ['tagA', 'tagB']},
      });
      expect(createComplexMatch('exact', [])).toBeUndefined();
    });

    it('returns undefined for unsupported mode', () => {
      expect(createComplexMatch('unknown', 'value')).toBeUndefined();
    });
  });
  describe('parseComplexCondition', () => {
    it('returns undefined for empty, null, or non-tilde strings', () => {
      expect(parseComplexCondition(undefined)).toBeUndefined();
      expect(parseComplexCondition('')).toBeUndefined();
      expect(parseComplexCondition('plain_text')).toBeUndefined();
      expect(parseComplexCondition('starts~')).toBeUndefined();
      expect(parseComplexCondition('unknown~value')).toBeUndefined();
    });

    it('parses starts, contains, not_contains, regex, not_regex', () => {
      expect(parseComplexCondition('starts~Pixel')).toEqual({
        startsWith: {value: 'Pixel'},
      });
      expect(parseComplexCondition('contains~Google', false)).toEqual({
        containsSubstring: {value: 'Google', negated: false},
      });
      expect(parseComplexCondition('contains~Google', true)).toEqual({
        containsSubstring: {value: 'Google', negated: true},
      });
      expect(parseComplexCondition('not_contains~foo')).toEqual({
        containsSubstring: {value: 'foo', negated: true},
      });
      expect(parseComplexCondition('regex~^lab.*', false)).toEqual({
        matchesRegex: {value: '^lab.*', negated: false},
      });
      expect(parseComplexCondition('not_regex~^test')).toEqual({
        matchesRegex: {value: '^test', negated: true},
      });
    });

    it('parses exactly and atleast comma-separated lists', () => {
      expect(parseComplexCondition('exactly~IDLE,BUSY')).toEqual({
        matchesExactly: {values: ['IDLE', 'BUSY']},
      });
      expect(parseComplexCondition('atleast~tagA, tagB')).toEqual({
        matchesAtLeast: {values: ['tagA', 'tagB']},
      });
      expect(parseComplexCondition('exactly~ , ')).toBeUndefined();
    });
  });

  describe('extractComplexMatchInfo', () => {
    it('returns undefined for undefined complex match', () => {
      expect(extractComplexMatchInfo(undefined)).toBeUndefined();
    });

    it('extracts prefix match info', () => {
      expect(extractComplexMatchInfo({startsWith: {value: 'Pixel'}})).toEqual({
        mode: 'prefix',
        values: ['Pixel'],
        isNegated: false,
      });
    });

    it('extracts substring match info with negated flags', () => {
      expect(
        extractComplexMatchInfo({
          containsSubstring: {value: 'Google', negated: false},
        }),
      ).toEqual({
        mode: 'substring',
        values: ['Google'],
        isNegated: false,
      });
      expect(
        extractComplexMatchInfo({
          containsSubstring: {value: 'Google', negated: true},
        }),
      ).toEqual({
        mode: 'not_substring',
        values: ['Google'],
        isNegated: true,
      });
    });

    it('extracts regex match info', () => {
      expect(
        extractComplexMatchInfo({
          matchesRegex: {value: '^lab.*', negated: true},
        }),
      ).toEqual({
        mode: 'not_regex',
        values: ['^lab.*'],
        isNegated: true,
      });
    });

    it('extracts exact and at_least match info', () => {
      expect(
        extractComplexMatchInfo({matchesExactly: {values: ['A', 'B']}}),
      ).toEqual({
        mode: 'exactly',
        values: ['A', 'B'],
        isNegated: false,
      });
      expect(
        extractComplexMatchInfo({matchesAtLeast: {values: ['X']}}),
      ).toEqual({
        mode: 'at_least',
        values: ['X'],
        isNegated: false,
      });
    });
  });
  describe('isChipNegated', () => {
    it('returns false for undefined/null chip', () => {
      expect(isChipNegated(undefined)).toBeFalse();
      expect(isChipNegated(null)).toBeFalse();
    });

    it('returns true when chip.negated is explicitly true', () => {
      const chip: FilterChip = {
        pillKey: 'status',
        pillCondition: 'IDLE',
        negated: true,
      };
      expect(isChipNegated(chip)).toBeTrue();
    });

    it('returns true when complex match has negated flag', () => {
      const chip: FilterChip = {
        pillKey: 'model',
        pillCondition: 'pixel',
        complex: {containsSubstring: {value: 'pixel', negated: true}},
      };
      expect(isChipNegated(chip)).toBeTrue();
    });

    it('returns true when complex regex match has negated flag', () => {
      const chip: FilterChip = {
        pillKey: 'model',
        pillCondition: '^lab.*',
        complex: {matchesRegex: {value: '^lab.*', negated: true}},
      };
      expect(isChipNegated(chip)).toBeTrue();
    });

    it('returns false for positive complex conditions (startsWith, substring without negation)', () => {
      expect(
        isChipNegated({
          pillKey: 'model',
          complex: {startsWith: {value: 'Pixel'}},
        }),
      ).toBeFalse();
      expect(
        isChipNegated({
          pillKey: 'model',
          complex: {containsSubstring: {value: 'Pixel', negated: false}},
        }),
      ).toBeFalse();
    });

    it('returns true when pillCondition starts with exclamation mark', () => {
      expect(
        isChipNegated({pillKey: 'status', pillCondition: '!READY'}),
      ).toBeTrue();
      expect(
        isChipNegated({pillKey: 'status', pillCondition: '!BUSY,IDLE'}),
      ).toBeTrue();
    });

    it('returns false for positive conditions and respects explicit negated: false overriding string prefix', () => {
      expect(
        isChipNegated({
          pillKey: 'status',
          pillCondition: 'IDLE',
          negated: false,
        }),
      ).toBeFalse();
      expect(
        isChipNegated({pillKey: 'model', pillCondition: 'Pixel 8'}),
      ).toBeFalse();
      expect(
        isChipNegated({
          pillKey: 'status',
          pillCondition: '!IDLE',
          negated: false,
        }),
      ).toBeFalse();
    });
  });

  describe('normalizeKey', () => {
    it('lowercases and trims key without altering valid namespace prefixes', () => {
      expect(normalizeKey('dim::model')).toBe('dim::model');
      expect(normalizeKey('field::status')).toBe('field::status');
      expect(normalizeKey('config::wifi')).toBe('config::wifi');
      expect(normalizeKey('  HOST_NAME  ')).toBe('host_name');
      expect(normalizeKey('')).toBe('');
      expect(normalizeKey(undefined)).toBe('');
    });
  });

  describe('getChipKey & getNormalizedChipKey', () => {
    it('extracts and normalizes raw chip keys correctly respecting groupBy precedence', () => {
      const normalChip: FilterChip = {
        key: 'dim::model',
        pillKey: 'model',
        pillCondition: 'Pixel',
      };
      expect(getChipKey(normalChip)).toBe('dim::model');
      expect(getNormalizedChipKey(normalChip)).toBe('dim::model');

      const fallbackChip: FilterChip = {
        pillKey: 'driver',
        pillCondition: 'AndroidDriver',
      };
      expect(getChipKey(fallbackChip)).toBe('driver');
      expect(getNormalizedChipKey(fallbackChip)).toBe('driver');

      const groupByChip: FilterChip = {
        key: 'host_name',
        pillKey: 'Host Name',
        pillCondition: 'Host Name',
        isGroupBy: true,
      };
      expect(getChipKey(groupByChip)).toBe('host_name');
      expect(getNormalizedChipKey(groupByChip)).toBe('host_name');

      const namespacedGroupByChip: FilterChip = {
        key: 'host::lab_type',
        pillKey: 'Host Lab Type',
        pillCondition: 'Host Lab Type',
        isGroupBy: true,
      };
      expect(getChipKey(namespacedGroupByChip)).toBe('host::lab_type');
      expect(getNormalizedChipKey(namespacedGroupByChip)).toBe(
        'host::lab_type',
      );
    });
  });

  describe('isSameFilterChip', () => {
    it('correctly compares chips by key and type with null-safety', () => {
      const a: FilterChip = {
        key: 'status',
        pillKey: 'Status',
        pillCondition: 'IDLE',
        isGroupBy: false,
      };
      const b: FilterChip = {
        key: 'status',
        pillKey: 'Status',
        pillCondition: 'BUSY',
        isGroupBy: false,
      };
      const c: FilterChip = {
        key: 'status',
        pillKey: 'Status',
        pillCondition: 'IDLE',
        isGroupBy: true,
      };

      expect(isSameFilterChip(a, b)).toBeTrue();
      expect(isSameFilterChip(a, c)).toBeFalse();
      expect(isSameFilterChip(a, null)).toBeFalse();
      expect(isSameFilterChip(undefined, b)).toBeFalse();
      expect(isSameFilterChip(a, a)).toBeTrue();
    });

    it('matches canonical key case-insensitively', () => {
      const chipWithBoth: FilterChip = {
        key: 'status',
        pillKey: 'Device Status',
        pillCondition: 'IDLE',
        isGroupBy: false,
      };

      // Matches by canonical key
      expect(
        isSameFilterChip(chipWithBoth, {key: 'STATUS', isGroupBy: false}),
      ).toBeTrue();
      expect(
        isSameFilterChip(chipWithBoth, {key: 'status', isGroupBy: false}),
      ).toBeTrue();
      // Fails on different field
      expect(
        isSameFilterChip(chipWithBoth, {key: 'model', isGroupBy: false}),
      ).toBeFalse();
      // Fails when group-by dimension differs
      expect(
        isSameFilterChip(chipWithBoth, {key: 'status', isGroupBy: true}),
      ).toBeFalse();
    });
  });

  describe('getQueryParamAsArray', () => {
    it('normalizes string and array params into clean string arrays', () => {
      expect(getQueryParamAsArray(['a', 'b', ''])).toEqual(['a', 'b']);
      expect(getQueryParamAsArray('model~starts~Pixel')).toEqual([
        'model~starts~Pixel',
      ]);
      expect(getQueryParamAsArray('   ')).toEqual([]);
      expect(getQueryParamAsArray(undefined)).toEqual([]);
      expect(getQueryParamAsArray(null)).toEqual([]);
    });
  });

  describe('buildUrlParamKey', () => {
    it('constructs a deterministic canonical hash fingerprint regardless of parameter order or case', () => {
      const key1 = buildUrlParamKey(
        ['status~IDLE', 'model~Pixel'],
        ['host'],
        'ats',
      );
      const key2 = buildUrlParamKey(
        ['model~pixel', 'STATUS~idle'],
        ['HOST'],
        'ATS',
      );
      expect(key1).toBe(key2);
      expect(key1).toBe('f=model~pixel&status~idle|gb=host|fleet=ats');

      const defaultFleetKey = buildUrlParamKey(['status~idle'], [], 'internal');
      expect(defaultFleetKey).toBe('f=status~idle|gb=|fleet=');
    });
  });

  describe('buildSimpleFleetFilter', () => {
    it('builds positive and negated filters correctly', () => {
      const pos = buildSimpleFleetFilter('status', ['IDLE', 'BUSY'], false);
      expect(pos.key).toBe('status');
      expect(pos.simple?.values?.length).toBe(2);
      expect(pos.simple?.negated).toBeFalse();

      const neg = buildSimpleFleetFilter('status', ['ERROR'], true);
      expect(neg.simple?.negated).toBeTrue();
    });

    it('handles <empty> and blank values properly', () => {
      const res = buildSimpleFleetFilter('driver', [EMPTY_FILTER_VALUE, '']);
      expect(res.simple?.values?.length).toBe(1);
      expect(res.simple?.values?.[0]?.noValue).toBeTrue();
    });
  });

  describe('serializeComplexCondition', () => {
    it('serializes starts, contains, regex, exactly, atleast conditions to Scheme B DSL', () => {
      expect(serializeComplexCondition({startsWith: {value: 'Pixel'}})).toBe(
        'starts~Pixel',
      );
      expect(
        serializeComplexCondition({
          containsSubstring: {value: 'Pixel', negated: true},
        }),
      ).toBe('contains~Pixel');
      expect(
        serializeComplexCondition({
          containsSubstring: {value: 'Pixel', negated: false},
        }),
      ).toBe('contains~Pixel');
      expect(
        serializeComplexCondition({
          matchesRegex: {value: '^lab.*', negated: true},
        }),
      ).toBe('regex~^lab.*');
      expect(
        serializeComplexCondition({matchesExactly: {values: ['A', 'B']}}),
      ).toBe('exactly~A,B');
      expect(
        serializeComplexCondition({matchesAtLeast: {values: ['X', 'Y']}}),
      ).toBe('atleast~X,Y');
    });

    it('returns empty string when complex condition is empty or unhandled', () => {
      expect(serializeComplexCondition(undefined)).toBe('');
      expect(serializeComplexCondition({})).toBe('');
      expect(
        serializeComplexCondition(
          {unsupported: {value: 'foo'}} as unknown as ComplexMatch,
        ),
      ).toBe('');
    });
  });

  describe('serializeFilterChip', () => {
    it('serializes chip with prefix and raw values', () => {
      const chip: FilterChip = {
        key: 'status',
        pillKey: 'Status',
        pillCondition: 'IDLE,BUSY',
        rawValues: ['IDLE', 'BUSY'],
        negated: true,
      };
      expect(serializeFilterChip(chip)).toBe('!status~IDLE,BUSY');
    });

    it('serializes empty value chip into trailing tilde format', () => {
      const chip: FilterChip = {
        key: 'driver',
        pillKey: 'Driver',
        pillCondition: '(no value)',
        rawValues: [EMPTY_FILTER_VALUE],
        negated: false,
      };
      expect(serializeFilterChip(chip)).toBe('driver~');

      const negChip: FilterChip = {
        key: 'driver',
        pillKey: 'Driver',
        pillCondition: '(no value)',
        rawValues: [EMPTY_FILTER_VALUE],
        negated: true,
      };
      expect(serializeFilterChip(negChip)).toBe('!driver~');
    });

    it('serializes mixed empty and valid values preserving <empty> token', () => {
      const chip: FilterChip = {
        key: 'model',
        pillKey: 'Model',
        pillCondition: '<empty>, Pixel 8',
        rawValues: [EMPTY_FILTER_VALUE, 'Pixel 8'],
        negated: false,
      };
      expect(serializeFilterChip(chip)).toBe('model~<empty>,Pixel%208');

      const negChip: FilterChip = {
        key: 'model',
        pillKey: 'Model',
        pillCondition: '<empty>, Pixel 8',
        rawValues: [EMPTY_FILTER_VALUE, 'Pixel 8'],
        negated: true,
      };
      expect(serializeFilterChip(negChip)).toBe('!model~<empty>,Pixel%208');
    });

    it('serializes fallback pillCondition with (no value) or <empty> into trailing tilde format', () => {
      const chipNoVal: FilterChip = {
        key: 'driver',
        pillKey: 'Driver',
        pillCondition: '(no value)',
      };
      expect(serializeFilterChip(chipNoVal)).toBe('driver~');

      const chipEmptyVal: FilterChip = {
        key: 'driver',
        pillKey: 'Driver',
        pillCondition: EMPTY_FILTER_VALUE,
      };
      expect(serializeFilterChip(chipEmptyVal)).toBe('driver~');
    });

    it('serializes complex match chip into Scheme B 3-part DSL', () => {
      const prefixChip: FilterChip = {
        key: 'model',
        pillKey: 'Model',
        pillCondition: 'Starts with "Pixel"',
        complex: {startsWith: {value: 'Pixel'}},
      };
      expect(serializeFilterChip(prefixChip)).toBe('model~starts~Pixel');

      const prefixNegatedChip: FilterChip = {
        key: 'model',
        pillKey: 'Model',
        pillCondition: 'Starts with "Pixel"',
        complex: {startsWith: {value: 'Pixel'}},
        negated: true,
      };
      expect(serializeFilterChip(prefixNegatedChip)).toBe(
        '!model~starts~Pixel',
      );

      const containsChip: FilterChip = {
        key: 'model',
        pillKey: 'Model',
        pillCondition: 'Does not contain "Pixel"',
        complex: {containsSubstring: {value: 'Pixel', negated: true}},
      };
      expect(serializeFilterChip(containsChip)).toBe('!model~contains~Pixel');

      const regexChip: FilterChip = {
        key: 'host',
        pillKey: 'Host',
        pillCondition: 'Matches regex "^lab.*"',
        complex: {matchesRegex: {value: '^lab.*', negated: false}},
      };
      expect(serializeFilterChip(regexChip)).toBe('host~regex~^lab.*');

      const exactChip: FilterChip = {
        key: 'tags',
        pillKey: 'Tags',
        pillCondition: 'Exactly A, B',
        complex: {matchesExactly: {values: ['A', 'B']}},
      };
      expect(serializeFilterChip(exactChip)).toBe('tags~exactly~A,B');
    });

    it('returns empty string for group-by chip', () => {
      const chip: FilterChip = {
        pillKey: 'model',
        pillCondition: 'model',
        isGroupBy: true,
      };
      expect(serializeFilterChip(chip)).toBe('');
    });
  });

  describe('parseQueryFilterParam', () => {
    it('parses negated simple filter param', () => {
      const res = parseQueryFilterParam('!status~IDLE,BUSY');
      expect(res).not.toBeNull();
      expect(res?.key).toBe('status');
      expect(res?.negated).toBeTrue();
      expect(res?.rawValues).toEqual(['IDLE', 'BUSY']);
    });

    it('parses empty (no value) trailing tilde filter param', () => {
      const res = parseQueryFilterParam('driver~');
      expect(res).not.toBeNull();
      expect(res?.key).toBe('driver');
      expect(res?.negated).toBeFalse();
      expect(res?.fleetFilter?.simple?.values?.[0]?.noValue).toBeTrue();

      const negRes = parseQueryFilterParam('!driver~');
      expect(negRes).not.toBeNull();
      expect(negRes?.key).toBe('driver');
      expect(negRes?.negated).toBeTrue();
      expect(negRes?.fleetFilter?.simple?.values?.[0]?.noValue).toBeTrue();
    });

    it('parses mixed <empty> and valid values into rawValues and Protobuf filterValues', () => {
      const res = parseQueryFilterParam('model~<empty>,Pixel 8');
      expect(res).not.toBeNull();
      expect(res?.key).toBe('model');
      expect(res?.rawValues).toEqual([EMPTY_FILTER_VALUE, 'Pixel 8']);
      expect(res?.fleetFilter?.simple?.values).toEqual([
        {noValue: true},
        {value: 'Pixel 8'},
      ]);

      const resWithNoValue = parseQueryFilterParam('model~(no value),Pixel 8');
      expect(resWithNoValue).not.toBeNull();
      expect(resWithNoValue?.rawValues).toEqual([
        EMPTY_FILTER_VALUE,
        'Pixel 8',
      ]);
      expect(resWithNoValue?.fleetFilter?.simple?.values).toEqual([
        {noValue: true},
        {value: 'Pixel 8'},
      ]);
    });

    it('parses multi-part non-complex parameters without truncating extra segments', () => {
      const res = parseQueryFilterParam('property~tag~foo');
      expect(res).not.toBeNull();
      expect(res?.key).toBe('property');
      expect(res?.rawValues).toEqual(['tag', 'foo']);
    });

    it('parses Scheme B 3-part complex match DSL params', () => {
      const p1 = parseQueryFilterParam('model~starts~Pixel');
      expect(p1?.key).toBe('model');
      expect(p1?.complex?.startsWith?.value).toBe('Pixel');

      const p2 = parseQueryFilterParam('!model~contains~Pixel');
      expect(p2?.key).toBe('model');
      expect(p2?.negated).toBeTrue();
      expect(p2?.complex?.containsSubstring?.value).toBe('Pixel');

      const p3 = parseQueryFilterParam('host~regex~^lab.*');
      expect(p3?.key).toBe('host');
      expect(p3?.complex?.matchesRegex?.value).toBe('^lab.*');

      const p4 = parseQueryFilterParam('tags~exactly~A,B');
      expect(p4?.key).toBe('tags');
      expect(p4?.complex?.matchesExactly?.values).toEqual(['A', 'B']);

      const p5 = parseQueryFilterParam('tags~atleast~X,Y');
      expect(p5?.key).toBe('tags');
      expect(p5?.complex?.matchesAtLeast?.values).toEqual(['X', 'Y']);
    });

    it('parses dash-prefix negated filter param', () => {
      const res = parseQueryFilterParam('-model~Pixel 8');
      expect(res).not.toBeNull();
      expect(res?.key).toBe('model');
      expect(res?.negated).toBeTrue();
    });

    it('safely handles malformed percent-encoded sequences without throwing', () => {
      const res = parseQueryFilterParam('status~%E0%A4%A');
      expect(res).not.toBeNull();
      expect(res?.key).toBe('status');
      expect(res?.rawValues).toEqual(['%E0%A4%A']);

      const complexRes = parseQueryFilterParam('model~starts~%E0%A4%A');
      expect(complexRes).not.toBeNull();
      expect(complexRes?.complex?.startsWith?.value).toBe('%E0%A4%A');
    });
  });

  describe('extractFilterChipFromFleetSuggestion & TjsSuggestion', () => {
    it('extracts FilterChip from FleetSuggestion with normalized negated flag', () => {
      const suggestion: FleetSuggestion = {
        applyFilter: {
          pillKey: 'Status',
          pillCondition: 'IDLE',
          resultingFilter: {
            key: 'status',
            simple: {
              values: [{value: 'IDLE'}],
              negated: false,
            },
          },
        },
      };

      const chip = extractFilterChipFromFleetSuggestion(suggestion);
      expect(chip).not.toBeNull();
      expect(chip?.key).toBe('status');
      expect(chip?.negated).toBeFalse();
    });

    it('extracts FilterChip from negated TjsSuggestion', () => {
      const suggestion: TjsSuggestion = {
        applyFilter: {
          pillKey: 'Status',
          pillCondition: '!BUSY',
          filter: {
            key: 'status',
            stringValue: {value: 'BUSY'},
          },
        },
      };

      const chip = extractFilterChipFromTjsSuggestion(suggestion);
      expect(chip).not.toBeNull();
      expect(chip?.key).toBe('status');
      expect(chip?.negated).toBeTrue();
    });
  });

  describe('extractRawValuesFromTjsFilter', () => {
    it('extracts raw values from timeRange filter with from and to', () => {
      const filter: TjsFilter = {
        key: 'create_time',
        timeRange: {
          from: '2026-01-01T00:00:00Z',
          to: '2026-01-02T00:00:00Z',
        },
      };
      expect(extractRawValuesFromTjsFilter(filter)).toEqual([
        '2026-01-01T00:00:00Z',
        '2026-01-02T00:00:00Z',
      ]);
    });

    it('extracts raw values from timeRange filter with only from or to', () => {
      const filterFromOnly: TjsFilter = {
        key: 'create_time',
        timeRange: {
          from: '2026-01-01T00:00:00Z',
        },
      };
      expect(extractRawValuesFromTjsFilter(filterFromOnly)).toEqual([
        '2026-01-01T00:00:00Z',
      ]);

      const filterToOnly: TjsFilter = {
        key: 'create_time',
        timeRange: {
          to: '2026-01-02T00:00:00Z',
        },
      };
      expect(extractRawValuesFromTjsFilter(filterToOnly)).toEqual([
        '2026-01-02T00:00:00Z',
      ]);
    });

    it('returns undefined for timeRange filter with empty strings or empty filter', () => {
      const filterEmpty: TjsFilter = {
        key: 'create_time',
        timeRange: {
          from: '',
          to: '',
        },
      };
      expect(extractRawValuesFromTjsFilter(filterEmpty)).toBeUndefined();
      expect(extractRawValuesFromTjsFilter(undefined)).toBeUndefined();
      expect(extractRawValuesFromTjsFilter(null)).toBeUndefined();
    });

    it('extracts FilterChip from TjsSuggestion containing timeRange filter', () => {
      const suggestion: TjsSuggestion = {
        applyFilter: {
          pillKey: 'Created Time',
          pillCondition: '2026-01-01 ~ 2026-01-02',
          filter: {
            key: 'create_time',
            timeRange: {
              from: '2026-01-01T00:00:00Z',
              to: '2026-01-02T00:00:00Z',
            },
          },
        },
      };
      const chip = extractFilterChipFromTjsSuggestion(suggestion);
      expect(chip).not.toBeNull();
      expect(chip?.key).toBe('create_time');
      expect(chip?.rawValues).toEqual([
        '2026-01-01T00:00:00Z',
        '2026-01-02T00:00:00Z',
      ]);
    });
  });

  describe('buildFleetFilterFromChip', () => {
    it('returns fleetFilter directly when chip.fleetFilter is present', () => {
      const originalFilter: Filter = {
        key: 'status',
        simple: {values: [{value: 'BUSY'}], negated: false},
      };
      const chip: FilterChip = {
        key: 'status',
        pillKey: 'status',
        pillCondition: 'BUSY',
        fleetFilter: originalFilter,
      };
      expect(buildFleetFilterFromChip(chip)).toBe(originalFilter);
    });

    it('returns complex filter AST directly when chip.complex is present', () => {
      const chip: FilterChip = {
        key: 'model',
        pillKey: 'model',
        pillCondition: 'starts~Pixel',
        complex: {startsWith: {value: 'Pixel'}},
      };
      const filter = buildFleetFilterFromChip(chip);
      expect(filter.key).toBe('model');
      expect(filter.complex).toEqual({startsWith: {value: 'Pixel'}});
      expect(filter.simple).toBeUndefined();
    });

    it('builds SimpleMatch with rawValues correctly', () => {
      const chip: FilterChip = {
        key: 'status',
        pillKey: 'status',
        pillCondition: 'IDLE, BUSY',
        rawValues: ['IDLE', 'BUSY'],
      };
      const filter = buildFleetFilterFromChip(chip);
      expect(filter.key).toBe('status');
      expect(filter.simple?.values).toEqual([{value: 'IDLE'}, {value: 'BUSY'}]);
      expect(filter.simple?.negated).toBeFalse();
    });

    it('builds negated SimpleMatch when isChipNegated evaluates to true', () => {
      const chip: FilterChip = {
        key: 'status',
        pillKey: 'status',
        pillCondition: '!ERROR',
        rawValues: ['ERROR'],
        negated: true,
      };
      const filter = buildFleetFilterFromChip(chip);
      expect(filter.key).toBe('status');
      expect(filter.simple?.values).toEqual([{value: 'ERROR'}]);
      expect(filter.simple?.negated).toBeTrue();
    });

    it('builds default empty SimpleMatch when rawValues and fleetFilter are absent', () => {
      const chip: FilterChip = {
        key: 'driver',
        pillKey: 'driver',
        pillCondition: 'driver',
      };
      const filter = buildFleetFilterFromChip(chip);
      expect(filter.key).toBe('driver');
      expect(filter.simple?.values).toEqual([{noValue: true}]);
      expect(filter.simple?.negated).toBeFalse();
    });
  });

  describe('toSearchEntityProto', () => {
    it('maps hosts to SEARCH_ENTITY_HOST and devices to SEARCH_ENTITY_DEVICE', () => {
      expect(toSearchEntityProto('hosts')).toBe(
        SearchEntity.SEARCH_ENTITY_HOST,
      );
      expect(toSearchEntityProto('devices')).toBe(
        SearchEntity.SEARCH_ENTITY_DEVICE,
      );
    });
  });

  describe('toFleetProto', () => {
    it('maps ats to FLEET_ATS and internal/undefined to FLEET_SELF', () => {
      expect(toFleetProto('ats')).toBe(Fleet.FLEET_ATS);
      expect(toFleetProto('internal')).toBe(Fleet.FLEET_SELF);
      expect(toFleetProto(undefined)).toBe(Fleet.FLEET_SELF);
      expect(toFleetProto('')).toBe(Fleet.FLEET_SELF);
    });
  });

  describe('toTjsEntityProto', () => {
    it('maps tests, jobs, sessions to corresponding TjsEntity enums', () => {
      expect(toTjsEntityProto('tests')).toBe(TjsEntity.TJS_ENTITY_TEST);
      expect(toTjsEntityProto('jobs')).toBe(TjsEntity.TJS_ENTITY_JOB);
      expect(toTjsEntityProto('sessions')).toBe(TjsEntity.TJS_ENTITY_SESSION);
    });
  });

  describe('parseQueryFilterParam & createGroupByChip', () => {
    it('directly parses a query param into a standard FilterChip with pillKey and pillCondition', () => {
      const chip = parseQueryFilterParam('!status~IDLE,BUSY')!;
      expect(chip).not.toBeNull();
      expect(chip.key).toBe('status');
      expect(chip.pillKey).toBe('status');
      expect(chip.pillCondition).toBe('IDLE, BUSY');
      expect(chip.rawValues).toEqual(['IDLE', 'BUSY']);
      expect(chip.negated).toBeTrue();
      expect(chip.fleetFilter).toBeDefined();
    });

    it('constructs a specialized group-by FilterChip', () => {
      const chip = createGroupByChip('host', 'Host Machine');
      expect(chip.key).toBe('host');
      expect(chip.pillKey).toBe('Host Machine');
      expect(chip.pillCondition).toBe('Host Machine');
      expect(chip.isGroupBy).toBeTrue();

      const chipFallbackDisplay = createGroupByChip('model');
      expect(chipFallbackDisplay.pillKey).toBe('model');
    });
  });

  describe('buildFleetGroupSort', () => {
    it('returns undefined for empty, unrecognized, or invalid sort strings', () => {
      expect(buildFleetGroupSort('')).toBeUndefined();
      expect(buildFleetGroupSort('unknown:asc')).toBeUndefined();
      expect(buildFleetGroupSort('name:asc')).toBeUndefined();
      expect(buildFleetGroupSort('gb_asc:')).toBeUndefined();
      expect(buildFleetGroupSort('gb_desc:')).toBeUndefined();
    });

    it('builds itemCount sort for count:desc and count:asc', () => {
      expect(buildFleetGroupSort('count:desc')).toEqual({
        field: {itemCount: {}},
        ascending: false,
      });
      expect(buildFleetGroupSort('count:asc')).toEqual({
        field: {itemCount: {}},
        ascending: true,
      });
    });

    it('builds groupKey sort for gb_asc and gb_desc preserving keys with colons', () => {
      expect(buildFleetGroupSort('gb_asc:model')).toEqual({
        field: {groupKey: 'model'},
        ascending: true,
      });
      expect(buildFleetGroupSort('gb_desc:model')).toEqual({
        field: {groupKey: 'model'},
        ascending: false,
      });
      expect(buildFleetGroupSort('gb_asc:host::lab_type')).toEqual({
        field: {groupKey: 'host::lab_type'},
        ascending: true,
      });
      expect(buildFleetGroupSort('gb_desc:host::lab_type')).toEqual({
        field: {groupKey: 'host::lab_type'},
        ascending: false,
      });
    });
  });

  describe('deduplicateFilterChipsByKey', () => {
    it('returns empty array when input is empty', () => {
      expect(deduplicateFilterChipsByKey([])).toEqual([]);
    });

    it('preserves distinct filter keys', () => {
      const chips: FilterChip[] = [
        {key: 'status', pillKey: 'Status', pillCondition: 'IDLE'},
        {key: 'model', pillKey: 'Model', pillCondition: 'Pixel 8'},
      ];
      expect(deduplicateFilterChipsByKey(chips)).toEqual(chips);
    });

    it('overwrites earlier conditions with later conditions for the same key (Scenario 4)', () => {
      const chips: FilterChip[] = [
        {key: 'status', pillKey: 'Status', pillCondition: 'IDLE'},
        {key: 'model', pillKey: 'Model', pillCondition: 'Pixel 7'},
        {key: 'status', pillKey: 'Status', pillCondition: 'BUSY'},
      ];
      const result = deduplicateFilterChipsByKey(chips);
      expect(result.length).toBe(2);
      expect(result[0]).toEqual({
        key: 'status',
        pillKey: 'Status',
        pillCondition: 'BUSY',
      });
      expect(result[1]).toEqual({
        key: 'model',
        pillKey: 'Model',
        pillCondition: 'Pixel 7',
      });
    });

    it('preserves both filter and group-by chips when they share the same key', () => {
      const filterChip: FilterChip = {
        key: 'model',
        pillKey: 'Model',
        pillCondition: 'Pixel 8',
        isGroupBy: false,
      };
      const groupByChip: FilterChip = {
        key: 'model',
        pillKey: 'Model',
        pillCondition: 'Model',
        isGroupBy: true,
      };
      const result = deduplicateFilterChipsByKey([filterChip, groupByChip]);
      expect(result.length).toBe(2);
      expect(result).toEqual([filterChip, groupByChip]);
    });

    it('overwrites earlier condition for the same key case-insensitively within the same dimension', () => {
      const chip1: FilterChip = {
        key: 'Model',
        pillKey: 'Model',
        pillCondition: 'Pixel 7',
        isGroupBy: false,
      };
      const chip2: FilterChip = {
        key: 'model',
        pillKey: 'Model',
        pillCondition: 'Pixel 8',
        isGroupBy: false,
      };
      const result = deduplicateFilterChipsByKey([chip1, chip2]);
      expect(result.length).toBe(1);
      expect(result[0]).toEqual(chip2);
    });
  });

  describe('buildResolvedFilterChips', () => {
    it('returns empty array when resolvedList is null, undefined, or empty (Scenario 5)', () => {
      const pair = {
        chip: {key: 'status', pillKey: 'Status', pillCondition: 'IDLE'},
        filter: {key: 'status'},
      };
      expect(buildResolvedFilterChips([pair], null, (c) => c)).toEqual([]);
      expect(buildResolvedFilterChips([pair], undefined, (c) => c)).toEqual([]);
      expect(buildResolvedFilterChips([pair], [], (c) => c)).toEqual([]);
    });

    it('maps 1:1 when backend returns complete resolved list', () => {
      const pairs = [
        {
          chip: {key: 'status', pillKey: 'status', pillCondition: 'IDLE'},
          filter: {key: 'status'},
        },
        {
          chip: {key: 'model', pillKey: 'model', pillCondition: 'Pixel'},
          filter: {key: 'model'},
        },
      ];
      const resolved = [
        {pillKey: 'Status', pillCondition: 'IDLE'},
        {pillKey: 'Model', pillCondition: 'Pixel 8'},
      ];
      const result = buildResolvedFilterChips(
        pairs,
        resolved,
        (chip, res, filter) => ({
          ...chip,
          pillKey: res.pillKey,
          pillCondition: res.pillCondition,
        }),
      );
      expect(result.length).toBe(2);
      expect(result[0].pillKey).toBe('Status');
      expect(result[1].pillKey).toBe('Model');
      expect(result[1].pillCondition).toBe('Pixel 8');
    });

    it('ignores un-returned chips when backend returns fewer items (Scenarios 1 & 2)', () => {
      const pairs = [
        {
          chip: {key: 'status', pillKey: 'status', pillCondition: 'IDLE'},
          filter: {key: 'status'},
        },
        {
          chip: {key: 'unknown', pillKey: 'unknown', pillCondition: 'foo'},
          filter: {key: 'unknown'},
        },
      ];
      // Backend only returned the first chip:
      const resolved = [{pillKey: 'Status', pillCondition: 'IDLE'}];
      const result = buildResolvedFilterChips(pairs, resolved, (chip, res) => ({
        ...chip,
        pillKey: res.pillKey,
        pillCondition: res.pillCondition,
      }));
      expect(result.length).toBe(1);
      expect(result[0].pillKey).toBe('Status');
    });

    it('excludes chips when enrichFn returns null or undefined for invalid items', () => {
      const pairs = [
        {
          chip: {key: 'status', pillKey: 'status', pillCondition: 'IDLE'},
          filter: {key: 'status'},
        },
        {
          chip: {
            key: 'invalid_dim',
            pillKey: 'invalid_dim',
            pillCondition: 'bad',
          },
          filter: {key: 'invalid_dim'},
        },
      ];
      const resolved = [
        {valid: {pillKey: 'Status', pillCondition: 'IDLE'}},
        {invalid: {reason: 'Unknown dimension'}},
      ];
      const result = buildResolvedFilterChips(pairs, resolved, (chip, res) => {
        if (res.valid) {
          return {
            ...chip,
            pillKey: res.valid.pillKey,
            pillCondition: res.valid.pillCondition,
          };
        }
        return null;
      });
      expect(result.length).toBe(1);
      expect(result[0].pillKey).toBe('Status');
    });
  });

  describe('resolveEntityFromPathOrUrl', () => {
    const fleetEntities = new Set<EntityType>(['devices', 'hosts']);
    const tjsEntities = new Set<EntityType>(['tests', 'jobs', 'sessions']);

    it('resolves primary entity from URL path segments', () => {
      expect(
        resolveEntityFromPathOrUrl('/hosts', fleetEntities, 'devices'),
      ).toBe('hosts');
      expect(
        resolveEntityFromPathOrUrl(
          '/hosts?f=status~IDLE',
          fleetEntities,
          'devices',
        ),
      ).toBe('hosts');
      expect(
        resolveEntityFromPathOrUrl('/devices', fleetEntities, 'devices'),
      ).toBe('devices');
      expect(
        resolveEntityFromPathOrUrl('/jobs?tab=overview', tjsEntities, 'tests'),
      ).toBe('jobs');
      expect(
        resolveEntityFromPathOrUrl('/sessions', tjsEntities, 'tests'),
      ).toBe('sessions');
      expect(resolveEntityFromPathOrUrl('/tests', tjsEntities, 'tests')).toBe(
        'tests',
      );
    });

    it('falls back to defaultEntity if segment is not in supportedEntities or invalid', () => {
      expect(
        resolveEntityFromPathOrUrl('/tests', fleetEntities, 'devices'),
      ).toBe('devices');
      expect(resolveEntityFromPathOrUrl('/hosts', tjsEntities, 'tests')).toBe(
        'tests',
      );
      expect(resolveEntityFromPathOrUrl('', fleetEntities, 'devices')).toBe(
        'devices',
      );
      expect(
        resolveEntityFromPathOrUrl('/unknown', fleetEntities, 'devices'),
      ).toBe('devices');
    });
  });

  describe('isSearchRouteActive', () => {
    it('returns true when path exactly matches the bound entity', () => {
      expect(isSearchRouteActive('/devices', 'devices')).toBeTrue();
      expect(isSearchRouteActive('/hosts', 'hosts')).toBeTrue();
      expect(isSearchRouteActive('/tests', 'tests')).toBeTrue();
      expect(isSearchRouteActive('/jobs', 'jobs')).toBeTrue();
      expect(isSearchRouteActive('/sessions', 'sessions')).toBeTrue();
    });

    it('returns true when path has query params or hash fragments', () => {
      expect(
        isSearchRouteActive('/devices?f=status~IDLE&fleet=ats', 'devices'),
      ).toBeTrue();
      expect(isSearchRouteActive('/hosts#host-scenarios', 'hosts')).toBeTrue();
      expect(isSearchRouteActive('/devices/', 'devices')).toBeTrue();
      expect(isSearchRouteActive('/tests?gb=status', 'tests')).toBeTrue();
    });

    it('returns false when path belongs to a different entity', () => {
      expect(isSearchRouteActive('/hosts', 'devices')).toBeFalse();
      expect(isSearchRouteActive('/devices', 'hosts')).toBeFalse();
      expect(isSearchRouteActive('/jobs', 'tests')).toBeFalse();
      expect(isSearchRouteActive('/tests', 'jobs')).toBeFalse();
      expect(isSearchRouteActive('/sessions', 'tests')).toBeFalse();
    });

    it('returns false for sub-routes and detail pages', () => {
      expect(isSearchRouteActive('/devices/device-123', 'devices')).toBeFalse();
      expect(isSearchRouteActive('/hosts/my-host', 'hosts')).toBeFalse();
      expect(isSearchRouteActive('/jobs/123/tests/456', 'jobs')).toBeFalse();
      expect(isSearchRouteActive('/jobs/123/tests/456', 'tests')).toBeFalse();
      expect(isSearchRouteActive('/dev/device-harness', 'devices')).toBeFalse();
    });

    it('returns true for empty or root path to permit safe initialization', () => {
      expect(isSearchRouteActive('', 'devices')).toBeTrue();
      expect(isSearchRouteActive('/', 'devices')).toBeTrue();
    });
  });

  describe('getInitialRouterUrl', () => {
    it('returns finalUrl if currentNavigation has finalUrl', () => {
      const mockRouter = {
        currentNavigation: () => ({
          finalUrl: {toString: () => '/devices?fleet=ats'},
        }),
        url: '/fallback',
      } as unknown as Router;
      expect(getInitialRouterUrl(mockRouter)).toBe('/devices?fleet=ats');
    });

    it('returns extractedUrl if currentNavigation has extractedUrl but no finalUrl', () => {
      const mockRouter = {
        currentNavigation: () => ({
          extractedUrl: {toString: () => '/hosts?f=status~IDLE'},
        }),
        url: '/fallback',
      } as unknown as Router;
      expect(getInitialRouterUrl(mockRouter)).toBe('/hosts?f=status~IDLE');
    });

    it('returns router.url when currentNavigation is null and router.url is not "/"', () => {
      const mockRouter = {
        currentNavigation: () => null,
        url: '/tests?gb=status',
      } as unknown as Router;
      expect(getInitialRouterUrl(mockRouter)).toBe('/tests?gb=status');
    });

    it('falls back to location or router.url when router.url is "/" and navigation is null', () => {
      const mockRouter = {
        currentNavigation: () => null,
        url: '/',
      } as unknown as Router;
      const result = getInitialRouterUrl(mockRouter);
      expect(typeof result).toBe('string');
    });
  });

  describe('parseUrlChips', () => {
    it('returns empty structures when params is null or undefined', () => {
      expect(parseUrlChips(null)).toEqual({
        parsedFilters: [],
        groupByKeys: [],
        initialChips: [],
      });
      expect(parseUrlChips(undefined)).toEqual({
        parsedFilters: [],
        groupByKeys: [],
        initialChips: [],
      });
    });

    it('parses simple filters and group-bys correctly', () => {
      const params: Params = {
        'f': ['status~IDLE', 'host~lab1'],
        'gb': 'model,host',
      };
      const result = parseUrlChips(params);
      expect(result.parsedFilters.length).toBe(2);
      expect(result.parsedFilters[0].pillKey).toBe('status');
      expect(result.parsedFilters[0].pillCondition).toBe('IDLE');
      expect(result.groupByKeys).toEqual(['model', 'host']);
      expect(result.initialChips.length).toBe(4);
      expect(result.initialChips[2].isGroupBy).toBeTrue();
      expect(result.initialChips[2].pillKey).toBe('model');
    });

    it('overwrites earlier conditions with later conditions for the same key (Scenario 4)', () => {
      const params: Params = {
        'f': ['status~BUSY', 'status~IDLE'],
      };
      const result = parseUrlChips(params);
      expect(result.parsedFilters.length).toBe(1);
      expect(result.parsedFilters[0].pillKey).toBe('status');
      expect(result.parsedFilters[0].pillCondition).toBe('IDLE');
    });

    it('handles single string f parameter instead of array', () => {
      const params: Params = {
        'f': 'status~IDLE',
      };
      const result = parseUrlChips(params);
      expect(result.parsedFilters.length).toBe(1);
      expect(result.parsedFilters[0].pillKey).toBe('status');
    });
  });

  describe('resolveInitialChips', () => {
    it('extracts initial chips from route snapshot queryParams', () => {
      const mockRoute = {
        snapshot: {
          queryParams: {
            'f': ['status~IDLE'],
            'gb': 'host',
          },
        },
      } as unknown as ActivatedRoute;
      const chips = resolveInitialChips(mockRoute);
      expect(chips.length).toBe(2);
      expect(chips[0].pillKey).toBe('status');
      expect(chips[1].pillKey).toBe('host');
      expect(chips[1].isGroupBy).toBeTrue();
    });

    it('returns empty array when route snapshot has no queryParams', () => {
      const mockRoute = {
        snapshot: {},
      } as unknown as ActivatedRoute;
      expect(resolveInitialChips(mockRoute)).toEqual([]);
    });
  });

  describe('resolveInitialFleet', () => {
    it('returns "ats" when fleet query param is "ats"', () => {
      const mockRoute = {
        snapshot: {
          queryParams: {fleet: 'ats'},
        },
      } as unknown as ActivatedRoute;
      expect(resolveInitialFleet(mockRoute)).toBe('ats');
    });

    it('defaults to "internal" when fleet query param is missing or other value', () => {
      const mockRoute1 = {
        snapshot: {
          queryParams: {},
        },
      } as unknown as ActivatedRoute;
      expect(resolveInitialFleet(mockRoute1)).toBe('internal');

      const mockRoute2 = {
        snapshot: {
          queryParams: {fleet: 'other'},
        },
      } as unknown as ActivatedRoute;
      expect(resolveInitialFleet(mockRoute2)).toBe('internal');
    });
  });

  describe('getSerializedChipsKey', () => {
    it('returns empty filters and group-bys for empty chips array', () => {
      expect(getSerializedChipsKey([])).toBe('f=|gb=|fleet=');
    });

    it('serializes simple and group-by chips with canonical formatting', () => {
      const chips: FilterChip[] = [
        {
          key: 'status',
          pillKey: 'status',
          pillCondition: 'IDLE',
          rawValues: ['IDLE'],
        },
        {
          key: 'model',
          pillKey: 'model',
          pillCondition: '',
          isGroupBy: true,
        },
      ];
      expect(getSerializedChipsKey(chips, 'ats')).toBe(
        'f=status~idle|gb=model|fleet=ats',
      );
    });

    it('produces identical serialized keys regardless of chip order', () => {
      const chipsA: FilterChip[] = [
        {
          key: 'host',
          pillKey: 'host',
          pillCondition: 'lab1',
          rawValues: ['lab1'],
        },
        {
          key: 'status',
          pillKey: 'status',
          pillCondition: 'BUSY',
          rawValues: ['BUSY'],
        },
      ];
      const chipsB: FilterChip[] = [
        {
          key: 'status',
          pillKey: 'status',
          pillCondition: 'BUSY',
          rawValues: ['BUSY'],
        },
        {
          key: 'host',
          pillKey: 'host',
          pillCondition: 'lab1',
          rawValues: ['lab1'],
        },
      ];
      expect(getSerializedChipsKey(chipsA)).toBe(getSerializedChipsKey(chipsB));
    });
  });
});
