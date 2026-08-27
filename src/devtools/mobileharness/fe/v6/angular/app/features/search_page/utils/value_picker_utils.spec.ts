import {PickerValueItem} from '../models/value_picker_models';
import {
  buildDisplayValues,
  buildValuePickerApplyEvent,
  computeFilteredAndSortedValues,
  computePinnedValues,
  parseDateRange,
  toDateTimeLocalString,
} from './value_picker_utils';

describe('value_picker_utils', () => {
  const mockItems: PickerValueItem[] = [
    {value: 'apple', displayLabel: 'Apple', filtered: 10, total: 100},
    {value: 'banana', displayLabel: 'Banana', filtered: 50, total: 50},
    {value: 'cherry', displayLabel: 'Cherry', filtered: 5, total: 200},
    {value: 'date', displayLabel: 'Date', filtered: 0, total: 10, disabled: true},
  ];

  describe('buildDisplayValues', () => {
    it('should return base items if no staged inputs', () => {
      const result = buildDisplayValues(mockItems, new Set());
      expect(result.length).toBe(4);
    });

    it('should add missing staged inputs as disabled items', () => {
      const staged = new Set(['grape', 'apple']);
      const result = buildDisplayValues(mockItems, staged, false);
      expect(result.length).toBe(5);
      const grape = result.find((i) => i.value === 'grape');
      expect(grape).toBeDefined();
      expect(grape?.disabled).toBeTrue();
    });

    it('should return base items when loading even with staged inputs', () => {
      const staged = new Set(['grape']);
      const result = buildDisplayValues(mockItems, staged, true);
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
      expect(asc.map((i) => i.value)).toEqual(['apple', 'banana', 'cherry', 'date']);

      const desc = computeFilteredAndSortedValues({
        items: mockItems,
        query: '',
        sortBy: 'value',
        sortAsc: false,
      });
      expect(desc.map((i) => i.value)).toEqual(['cherry', 'banana', 'apple', 'date']);
    });

    it('should sort by filtered count desc and asc', () => {
      const desc = computeFilteredAndSortedValues({
        items: mockItems,
        query: '',
        sortBy: 'filtered',
        sortAsc: false,
      });
      expect(desc.map((i) => i.value)).toEqual(['banana', 'apple', 'cherry', 'date']);

      const asc = computeFilteredAndSortedValues({
        items: mockItems,
        query: '',
        sortBy: 'filtered',
        sortAsc: true,
      });
      expect(asc.map((i) => i.value)).toEqual(['cherry', 'apple', 'banana', 'date']);
    });

    it('should sort by total count desc', () => {
      const desc = computeFilteredAndSortedValues({
        items: mockItems,
        query: '',
        sortBy: 'total',
        sortAsc: false,
      });
      expect(desc.map((i) => i.value)).toEqual(['cherry', 'apple', 'banana', 'date']);
    });
  });

  describe('computePinnedValues', () => {
    it('should return empty array if total count is under threshold', () => {
      const selected = new Set(['apple']);
      const result = computePinnedValues(mockItems, selected, '', false, 20);
      expect(result).toEqual([]);
    });

    it('should return sorted pinned items if count exceeds threshold and has selections', () => {
      const largeList: PickerValueItem[] = Array.from({length: 25}, (_, idx) => ({
        value: `item_${idx}`,
        displayLabel: `Label ${idx.toString().padStart(2, '0')}`,
      }));
      const selected = new Set(['item_5', 'item_2']);
      const result = computePinnedValues(largeList, selected, '', false, 20);
      expect(result.map((i) => i.value)).toEqual(['item_2', 'item_5']);
    });

    it('should return empty if query is active or loading', () => {
      const largeList: PickerValueItem[] = Array.from({length: 25}, (_, idx) => ({
        value: `item_${idx}`,
        displayLabel: `Label ${idx}`,
      }));
      const selected = new Set(['item_5']);
      expect(computePinnedValues(largeList, selected, 'some query', false, 20)).toEqual([]);
      expect(computePinnedValues(largeList, selected, '', true, 20)).toEqual([]);
    });
  });

  describe('toDateTimeLocalString & parseDateRange', () => {
    it('should format ms to local datetime string format', () => {
      const date = new Date(2026, 0, 15, 14, 30);
      const str = toDateTimeLocalString(date.getTime());
      expect(str).toBe('2026-01-15T14:30');
    });

    it('should parse range string correctly', () => {
      const set = new Set(['From: 2026-01-01T00:00 To: 2026-01-02T00:00']);
      const parsed = parseDateRange(set);
      expect(parsed.from).toBe('2026-01-01T00:00');
      expect(parsed.to).toBe('2026-01-02T00:00');
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

    it('should include pending search query if not already in selected list', () => {
      const event = buildValuePickerApplyEvent({
        isAdvanced: false,
        negated: true,
        selectedSet: new Set(['item1']),
        searchQuery: 'item2',
      });
      expect(event.selected).toEqual(['item1', 'item2']);
      expect(event.negate).toBeTrue();
    });
  });
});
