import {FilterChip, PickerValueItem} from '../models';
import {
  buildComplexMatchFromEvent,
  buildDisplayValues,
  buildValuePickerApplyEvent,
  computeFilteredAndSortedValues,
  computePinnedValues,
  extractAdvancedStateFromChip,
  getPacificTimezoneName,
  isValuePickerSelectionEmpty,
  pacificToUtc,
  parseDateRange,
  utcToPacific,
} from './value_picker_utils';

describe('value_picker_utils', () => {
  const mockItems: PickerValueItem[] = [
    {value: 'apple', displayLabel: 'Apple', filtered: 10, total: 100},
    {value: 'banana', displayLabel: 'Banana', filtered: 50, total: 50},
    {value: 'cherry', displayLabel: 'Cherry', filtered: 5, total: 200},
    {
      value: 'date',
      displayLabel: 'Date',
      filtered: 0,
      total: 10,
      disabled: true,
    },
  ];

  describe('buildDisplayValues', () => {
    it('should return base items if no selected inputs', () => {
      const result = buildDisplayValues(mockItems, new Set());
      expect(result.length).toBe(4);
    });

    it('should add missing selected inputs as interactive items with count 0', () => {
      const selected = new Set(['grape', 'apple', 'orange']);
      const result = buildDisplayValues(mockItems, selected, false, false);
      expect(result.length).toBe(6);

      const grape = result.find((i) => i.value === 'grape');
      expect(grape).toBeDefined();
      expect(grape?.filtered).toBe(0);
      expect(grape?.total).toBe(0);
      expect(grape?.disabled).toBeFalsy();

      const orange = result.find((i) => i.value === 'orange');
      expect(orange).toBeDefined();
      expect(orange?.filtered).toBe(0);
      expect(orange?.total).toBe(0);
      expect(orange?.disabled).toBeFalsy();

      // 'apple' already exists in baseItems, should not be duplicated or overwritten with 0 count
      const apples = result.filter((i) => i.value === 'apple');
      expect(apples.length).toBe(1);
      expect(apples[0].filtered).toBe(10);
      expect(apples[0].total).toBe(100);
    });

    it('should set undefined counts for plain lists', () => {
      const selected = new Set(['custom_host']);
      const result = buildDisplayValues(mockItems, selected, true, false);
      const custom = result.find((i) => i.value === 'custom_host');
      expect(custom).toBeDefined();
      expect(custom?.filtered).toBeUndefined();
      expect(custom?.total).toBeUndefined();
      expect(custom?.disabled).toBeFalsy();
    });

    it('should return base items when loading even with custom inputs', () => {
      const selected = new Set(['grape', 'orange']);
      const result = buildDisplayValues(mockItems, selected, false, true);
      expect(result.length).toBe(4);
    });
  });

  describe('computeFilteredAndSortedValues', () => {
    it('should filter items by query matching label or value', () => {
      const result = computeFilteredAndSortedValues({
        items: mockItems,
        query: 'an',
        sortBy: 'value',
        sortAsc: true,
      });
      expect(result.map((i) => i.value)).toEqual(['banana']);
    });

    it('should sort by value asc and desc', () => {
      const asc = computeFilteredAndSortedValues({
        items: mockItems,
        query: '',
        sortBy: 'value',
        sortAsc: true,
      });
      // Normal items sorted by label asc, disabled item 'date' sinks to bottom
      expect(asc.map((i) => i.value)).toEqual([
        'apple',
        'banana',
        'cherry',
        'date',
      ]);

      const desc = computeFilteredAndSortedValues({
        items: mockItems,
        query: '',
        sortBy: 'value',
        sortAsc: false,
      });
      expect(desc.map((i) => i.value)).toEqual([
        'cherry',
        'banana',
        'apple',
        'date',
      ]);
    });

    it('should sort by filtered count desc and asc', () => {
      const desc = computeFilteredAndSortedValues({
        items: mockItems,
        query: '',
        sortBy: 'filtered',
        sortAsc: false,
      });
      expect(desc.map((i) => i.value)).toEqual([
        'banana',
        'apple',
        'cherry',
        'date',
      ]);

      const asc = computeFilteredAndSortedValues({
        items: mockItems,
        query: '',
        sortBy: 'filtered',
        sortAsc: true,
      });
      expect(asc.map((i) => i.value)).toEqual([
        'cherry',
        'apple',
        'banana',
        'date',
      ]);
    });

    it('should sort by total count desc', () => {
      const desc = computeFilteredAndSortedValues({
        items: mockItems,
        query: '',
        sortBy: 'total',
        sortAsc: false,
      });
      expect(desc.map((i) => i.value)).toEqual([
        'cherry',
        'apple',
        'banana',
        'date',
      ]);
    });

    it('should break ties using displayLabel alphabetically when counts are equal', () => {
      const tiedItems: PickerValueItem[] = [
        {value: 'pear', displayLabel: 'Pear', filtered: 20, total: 100},
        {value: 'apricot', displayLabel: 'Apricot', filtered: 20, total: 100},
        {value: 'mango', displayLabel: 'Mango', filtered: 20, total: 100},
      ];
      const result = computeFilteredAndSortedValues({
        items: tiedItems,
        query: '',
        sortBy: 'filtered',
        sortAsc: false,
      });
      expect(result.map((i) => i.value)).toEqual(['apricot', 'mango', 'pear']);
    });

    it('should return all items without slicing to 100', () => {
      const largeItems: PickerValueItem[] = Array.from(
        {length: 150},
        (_, i) => ({
          value: `val_${i}`,
          displayLabel: `Val ${i}`,
          filtered: i,
          total: i,
        }),
      );
      const result = computeFilteredAndSortedValues({
        items: largeItems,
        query: '',
        sortBy: 'value',
        sortAsc: true,
      });
      expect(result.length).toBe(150);
    });
  });

  describe('computePinnedValues', () => {
    it('should return empty array if total count is under threshold', () => {
      const selected = new Set(['apple']);
      const result = computePinnedValues(mockItems, selected, '', false, 20);
      expect(result).toEqual([]);
    });

    it('should return sorted pinned items if count exceeds threshold and has selections', () => {
      const largeList: PickerValueItem[] = Array.from(
        {length: 25},
        (_, idx) => ({
          value: `item_${idx}`,
          displayLabel: `Label ${idx.toString().padStart(2, '0')}`,
        }),
      );
      const selected = new Set(['item_5', 'item_2']);
      const result = computePinnedValues(largeList, selected, '', false, 20);
      expect(result.map((i) => i.value)).toEqual(['item_2', 'item_5']);
    });

    it('should return empty if query is active or loading', () => {
      const largeList: PickerValueItem[] = Array.from(
        {length: 25},
        (_, idx) => ({
          value: `item_${idx}`,
          displayLabel: `Label ${idx}`,
        }),
      );
      const selected = new Set(['item_5']);
      expect(
        computePinnedValues(largeList, selected, 'some query', false, 20),
      ).toEqual([]);
      expect(computePinnedValues(largeList, selected, '', true, 20)).toEqual(
        [],
      );
    });
  });

  describe('pacificToUtc, utcToPacific & parseDateRange', () => {
    it('should convert Pacific Time datetime string to UTC ISO string', () => {
      // PDT test (July, UTC-7)
      expect(pacificToUtc('2026-07-15T14:30')).toBe('2026-07-15T21:30:00.000Z');

      // PST test (January, UTC-8)
      expect(pacificToUtc('2026-01-15T14:30')).toBe('2026-01-15T22:30:00.000Z');
    });

    it('should return timezone abbreviation for given date', () => {
      expect(getPacificTimezoneName(Date.UTC(2026, 6, 15))).toBe('PDT');
      expect(getPacificTimezoneName(Date.UTC(2026, 0, 15))).toBe('PST');
    });

    it('should parse range string correctly', () => {
      const set = new Set(['From: 2026-01-01T00:00 To: 2026-01-02T00:00']);
      const parsed = parseDateRange(set);
      expect(parsed.from).toBe('2026-01-01T00:00');
      expect(parsed.to).toBe('2026-01-02T00:00');

      const tildeSet = new Set(['2026-01-01T00:00 ~ 2026-01-02T00:00 (PDT)']);
      const parsedTilde = parseDateRange(tildeSet);
      expect(parsedTilde.from).toBe('2026-01-01T00:00');
      expect(parsedTilde.to).toBe('2026-01-02T00:00');
    });

    it('should parse date range from Set containing 2 separate datetime strings', () => {
      const multiSet = new Set(['2026-01-01T00:00', '2026-01-02T12:00']);
      const parsed = parseDateRange(multiSet);
      expect(parsed.from).toBe('2026-01-01T00:00');
      expect(parsed.to).toBe('2026-01-02T12:00');

      const paddedSet = new Set([
        '  2026-05-10T08:00  ',
        '  2026-05-20T18:00  ',
        'extra',
      ]);
      const paddedParsed = parseDateRange(paddedSet);
      expect(paddedParsed.from).toBe('2026-05-10T08:00');
      expect(paddedParsed.to).toBe('2026-05-20T18:00');
    });

    it('should parse date range from UTC ISO strings and convert to Pacific Time', () => {
      // 2026-07-15T21:30:00.000Z is 2026-07-15T14:30 PDT
      // 2026-07-16T21:30:00.000Z is 2026-07-16T14:30 PDT
      const isoSet = new Set([
        '2026-07-15T21:30:00.000Z',
        '2026-07-16T21:30:00.000Z',
      ]);
      const parsed = parseDateRange(isoSet);
      expect(parsed.from).toBe('2026-07-15T14:30');
      expect(parsed.to).toBe('2026-07-16T14:30');
    });

    it('should parse date range with spaces in From/To format', () => {
      const spaceSet = new Set(['From: 2026-01-01 00:00 To: 2026-01-02 00:00']);
      const parsed = parseDateRange(spaceSet);
      expect(parsed.from).toBe('2026-01-01T00:00');
      expect(parsed.to).toBe('2026-01-02T00:00');
    });

    it('should parse date-only strings into datetime-local format', () => {
      const dateOnlySet = new Set(['2026-01-01', '2026-01-02']);
      const parsed = parseDateRange(dateOnlySet);
      expect(parsed.from).toBe('2026-01-01T00:00');
      expect(parsed.to).toBe('2026-01-02T00:00');
    });

    it('should normalize various datetime formats via utcToPacific', () => {
      expect(utcToPacific('')).toBe('');
      expect(utcToPacific('2026-01-01T12:00')).toBe('2026-01-01T12:00');
      expect(utcToPacific('2026-01-01 12:00')).toBe('2026-01-01T12:00');
      expect(utcToPacific('2026-01-01')).toBe('2026-01-01T00:00');
      expect(utcToPacific('2026-07-15T21:30:00.000Z')).toBe('2026-07-15T14:30');
      expect(utcToPacific('2026-07-15 21:30:00 UTC')).toBe('2026-07-15T14:30');
      // Epoch millisecond timestamp (1784151000000 ms is 2026-07-15T21:30:00 UTC = 2026-07-15T14:30 PDT)
      expect(utcToPacific('1784151000000')).toBe('2026-07-15T14:30');
    });

    it('should calculate 24h fallback range correctly when only single bound is provided', () => {
      const singleFrom = new Set(['From: 2026-01-02T12:00']);
      const parsedFrom = parseDateRange(singleFrom);
      expect(parsedFrom.from).toBe('2026-01-02T12:00');
      expect(parsedFrom.to).toBe('2026-01-03T12:00');

      const singleTo = new Set(['To: 2026-01-03T12:00']);
      const parsedTo = parseDateRange(singleTo);
      expect(parsedTo.from).toBe('2026-01-02T12:00');
      expect(parsedTo.to).toBe('2026-01-03T12:00');
    });
  });

  describe('buildValuePickerApplyEvent', () => {
    it('should construct range event', () => {
      const event = buildValuePickerApplyEvent({
        type: 'range',
        isAdvanced: false,
        negated: false,
        selectedSet: new Set(),
        searchQuery: '',
        rangeFrom: '2026-01-01T00:00',
        rangeTo: '2026-01-02T00:00',
      });
      expect(event).toEqual({
        selected: ['From: 2026-01-01T00:00 To: 2026-01-02T00:00'],
        negate: false,
        isAdvanced: false,
        rangeFrom: '2026-01-01T00:00',
        rangeTo: '2026-01-02T00:00',
      });
    });

    it('should construct namedPair event', () => {
      const event = buildValuePickerApplyEvent({
        type: 'namedPair',
        isAdvanced: false,
        negated: false,
        selectedSet: new Set(),
        searchQuery: '',
        propName: 'device_type',
        propVal: 'pixel',
      });
      expect(event).toEqual({
        selected: ['pixel'],
        negate: false,
        isAdvanced: false,
        propName: 'device_type',
        textVal: 'pixel',
      });
    });

    it('should construct advanced event', () => {
      const event = buildValuePickerApplyEvent({
        isAdvanced: true,
        negated: false,
        selectedSet: new Set(),
        searchQuery: '',
        advMode: 'prefix',
        advText: 'test_prefix',
      });
      expect(event).toEqual({
        selected: [],
        negate: false,
        isAdvanced: true,
        advMode: 'prefix',
        advText: 'test_prefix',
        advValues: undefined,
      });
    });

    it('should not include search query in selected list when applying', () => {
      const event = buildValuePickerApplyEvent({
        isAdvanced: false,
        negated: true,
        selectedSet: new Set(['item1']),
        searchQuery: 'item2',
      });
      expect(event.selected).toEqual(['item1']);
      expect(event.negate).toBeTrue();
    });
  });

  describe('buildComplexMatchFromEvent', () => {
    it('returns undefined when isAdvanced is false or advMode is missing', () => {
      expect(
        buildComplexMatchFromEvent({
          selected: [],
          negate: false,
          isAdvanced: false,
        }),
      ).toBeUndefined();
    });

    it('builds prefix, substring, regex, exact, and at_least complex matches', () => {
      expect(
        buildComplexMatchFromEvent({
          selected: [],
          negate: false,
          isAdvanced: true,
          advMode: 'prefix',
          advText: 'Pixel',
        }),
      ).toEqual({startsWith: {value: 'Pixel'}});

      expect(
        buildComplexMatchFromEvent({
          selected: [],
          negate: true, // Ignored in advanced mode: 'substring' explicitly means positive Contains
          isAdvanced: true,
          advMode: 'substring',
          advText: 'Google',
        }),
      ).toEqual({containsSubstring: {value: 'Google', negated: false}});

      expect(
        buildComplexMatchFromEvent({
          selected: [],
          negate: false,
          isAdvanced: true,
          advMode: 'not_substring',
          advText: 'Google',
        }),
      ).toEqual({containsSubstring: {value: 'Google', negated: true}});

      expect(
        buildComplexMatchFromEvent({
          selected: [],
          negate: false,
          isAdvanced: true,
          advMode: 'regex',
          advText: '^lab.*',
        }),
      ).toEqual({matchesRegex: {value: '^lab.*', negated: false}});

      expect(
        buildComplexMatchFromEvent({
          selected: [],
          negate: false,
          isAdvanced: true,
          advMode: 'exactly',
          advValues: ['A', 'B'],
        }),
      ).toEqual({matchesExactly: {values: ['A', 'B']}});

      expect(
        buildComplexMatchFromEvent({
          selected: [],
          negate: false,
          isAdvanced: true,
          advMode: 'at_least',
          advValues: ['X'],
        }),
      ).toEqual({matchesAtLeast: {values: ['X']}});
    });
  });

  describe('extractAdvancedStateFromChip', () => {
    it('extracts advanced state when chip contains complex match', () => {
      const chip: FilterChip = {
        pillKey: 'model',
        pillCondition: 'prefix:Pixel',
        complex: {startsWith: {value: 'Pixel'}},
      };
      expect(extractAdvancedStateFromChip(chip)).toEqual({
        isAdv: true,
        advMode: 'prefix',
        advText: 'Pixel',
        advValues: ['Pixel'],
      });
    });

    it('returns default inactive state when chip has no complex match', () => {
      const chip: FilterChip = {
        pillKey: 'status',
        pillCondition: 'IDLE',
      };
      expect(extractAdvancedStateFromChip(chip)).toEqual({
        isAdv: false,
        advMode: 'substring',
        advText: '',
        advValues: [],
      });
    });

    it('returns default inactive state when chip is a negated simple filter', () => {
      const chip: FilterChip = {
        pillKey: 'status',
        pillCondition: '!IDLE',
        negated: true,
      };
      expect(extractAdvancedStateFromChip(chip)).toEqual({
        isAdv: false,
        advMode: 'substring',
        advText: '',
        advValues: [],
      });
    });
  });

  describe('isValuePickerSelectionEmpty', () => {
    it('checks empty state for advanced multi-value modes (exactly, at_least) (line 340)', () => {
      expect(
        isValuePickerSelectionEmpty({
          selected: [],
          negate: false,
          isAdvanced: true,
          advMode: 'exactly',
          advValues: [],
        }),
      ).toBeTrue();

      expect(
        isValuePickerSelectionEmpty({
          selected: [],
          negate: false,
          isAdvanced: true,
          advMode: 'exactly',
          advValues: undefined,
        }),
      ).toBeTrue();

      expect(
        isValuePickerSelectionEmpty({
          selected: [],
          negate: false,
          isAdvanced: true,
          advMode: 'exactly',
          advValues: ['item1'],
        }),
      ).toBeFalse();

      expect(
        isValuePickerSelectionEmpty({
          selected: [],
          negate: false,
          isAdvanced: true,
          advMode: 'at_least',
          advValues: [],
        }),
      ).toBeTrue();

      expect(
        isValuePickerSelectionEmpty({
          selected: [],
          negate: false,
          isAdvanced: true,
          advMode: 'at_least',
          advValues: ['tagA', 'tagB'],
        }),
      ).toBeFalse();
    });

    it('checks empty state for advanced text modes (prefix, substring, regex)', () => {
      expect(
        isValuePickerSelectionEmpty({
          selected: [],
          negate: false,
          isAdvanced: true,
          advMode: 'prefix',
          advText: '',
        }),
      ).toBeTrue();

      expect(
        isValuePickerSelectionEmpty({
          selected: [],
          negate: false,
          isAdvanced: true,
          advMode: 'prefix',
          advText: '   ',
        }),
      ).toBeTrue();

      expect(
        isValuePickerSelectionEmpty({
          selected: [],
          negate: false,
          isAdvanced: true,
          advMode: 'prefix',
          advText: 'Pixel',
        }),
      ).toBeFalse();
    });

    it('checks empty state for standard non-advanced events (line 344)', () => {
      expect(
        isValuePickerSelectionEmpty({
          selected: [],
          negate: false,
          isAdvanced: false,
        }),
      ).toBeTrue();

      expect(
        isValuePickerSelectionEmpty({
          selected: ['IDLE'],
          negate: false,
          isAdvanced: false,
        }),
      ).toBeFalse();

      expect(
        isValuePickerSelectionEmpty({
          selected: [],
          negate: false,
          isAdvanced: false,
          rangeFrom: '2026-01-01T00:00',
        }),
      ).toBeFalse();

      expect(
        isValuePickerSelectionEmpty({
          selected: [],
          negate: false,
          isAdvanced: false,
          rangeTo: '2026-01-02T00:00',
        }),
      ).toBeFalse();

      expect(
        isValuePickerSelectionEmpty({
          selected: [],
          negate: false,
          isAdvanced: false,
          textVal: 'query',
        }),
      ).toBeFalse();

      expect(
        isValuePickerSelectionEmpty({
          selected: [],
          negate: false,
          isAdvanced: false,
          propName: 'custom_prop',
        }),
      ).toBeFalse();
    });
  });
});
