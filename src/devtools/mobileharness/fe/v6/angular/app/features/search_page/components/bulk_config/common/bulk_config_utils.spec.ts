import {DeviceConfigChangeStatus} from '../../../../../core/models/device_config_models';
import {
  extractBatchApplyErrors,
  formatCurrentSelectionBadge,
  formatDeviceCount,
  formatNothingToUpdateMessage,
  formatSuccessSupportText,
  parseDimensionTokens,
  summarizeConfigEntries,
} from './bulk_config_utils';

describe('bulk_config_utils', () => {
  describe('formatDeviceCount', () => {
    it('formats singular and plural device counts', () => {
      expect(formatDeviceCount(1)).toBe('1 device');
      expect(formatDeviceCount(0)).toBe('0 devices');
      expect(formatDeviceCount(1200)).toBe('1,200 devices');
    });
  });

  describe('formatCurrentSelectionBadge', () => {
    it('returns null when count is 0 or negative', () => {
      expect(formatCurrentSelectionBadge(0, 3)).toBeNull();
      expect(formatCurrentSelectionBadge(-1, 3)).toBeNull();
    });

    it('formats badge when a subset of selected devices match', () => {
      expect(formatCurrentSelectionBadge(1, 3)).toBe(
        'Current on 1 device selected',
      );
      expect(formatCurrentSelectionBadge(2, 3)).toBe(
        'Current on 2 devices selected',
      );
    });

    it('formats badge when all selected devices match', () => {
      expect(formatCurrentSelectionBadge(3, 3)).toBe(
        'Current on all 3 selected',
      );
      expect(formatCurrentSelectionBadge(1, 1)).toBe(
        'Current on 1 device selected',
      );
    });
  });

  describe('summarizeConfigEntries', () => {
    it('aggregates willUpdate, alreadyMatch, and readOnly counts in a single pass', () => {
      const entries = [
        {
          deviceId: 'd1',
          status:
            DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
        },
        {
          deviceId: 'd2',
          status:
            DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
        },
        {
          deviceId: 'd3',
          status:
            DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        },
        {
          deviceId: 'd4',
          status:
            DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_UNSPECIFIED,
        },
      ];

      const summary = summarizeConfigEntries(entries);
      expect(summary.willUpdateCount).toBe(1);
      expect(summary.willUpdateRows.map((r) => r.deviceId)).toEqual(['d1']);
      expect(summary.alreadyMatchCount).toBe(1);
      expect(summary.readOnlyCount).toBe(1);
    });
  });

  describe('extractBatchApplyErrors', () => {
    it('maps backend error map to BulkApplyError list with message or code fallback', () => {
      expect(extractBatchApplyErrors(undefined)).toEqual([]);
      expect(
        extractBatchApplyErrors({
          'dev-1': {code: 'UNKNOWN', message: 'Device offline'},
          'dev-2': {code: 'PERMISSION_DENIED', message: ''},
        }),
      ).toEqual([
        {deviceId: 'dev-1', reason: 'Device offline'},
        {deviceId: 'dev-2', reason: 'PERMISSION_DENIED'},
      ]);
    });
  });

  describe('formatNothingToUpdateMessage', () => {
    it('formats all-match, mixed match+read-only, and all-read-only messages', () => {
      expect(
        formatNothingToUpdateMessage(
          {alreadyMatchCount: 3, readOnlyCount: 0},
          3,
          'already use this Wi-Fi configuration',
          'Wi-Fi configuration',
        ),
      ).toBe(
        'All 3 devices already use this Wi-Fi configuration. No update is needed.',
      );

      expect(
        formatNothingToUpdateMessage(
          {alreadyMatchCount: 1, readOnlyCount: 2},
          3,
          'already use this Wi-Fi configuration',
          'Wi-Fi configuration',
        ),
      ).toBe(
        '1 device already match this Wi-Fi configuration and 2 devices are read-only. No devices can be updated.',
      );

      expect(
        formatNothingToUpdateMessage(
          {alreadyMatchCount: 0, readOnlyCount: 3},
          3,
          'already use this Wi-Fi configuration',
          'Wi-Fi configuration',
        ),
      ).toBe('All 3 devices are read-only and cannot be updated.');
    });
  });

  describe('formatSuccessSupportText', () => {
    it('formats extra notes or falls back to default message', () => {
      expect(
        formatSuccessSupportText(
          {alreadyMatchCount: 1, readOnlyCount: 2},
          'Every updated device now uses this network.',
        ),
      ).toBe('1 device already matched; 2 devices read-only.');

      expect(
        formatSuccessSupportText(
          {alreadyMatchCount: 0, readOnlyCount: 0},
          'Every updated device now uses this network.',
        ),
      ).toBe('Every updated device now uses this network.');
    });
  });

  describe('parseDimensionTokens', () => {
    it('tokenizes comma- and semicolon-separated strings', () => {
      expect(parseDimensionTokens('foo, bar; baz')).toEqual([
        'foo',
        'bar',
        'baz',
      ]);
    });

    it('handles empty strings, whitespace, and trailing delimiters', () => {
      expect(parseDimensionTokens('')).toEqual([]);
      expect(parseDimensionTokens('   ')).toEqual([]);
      expect(parseDimensionTokens('foo,  , ; bar,')).toEqual(['foo', 'bar']);
    });

    it('handles newlines gracefully', () => {
      expect(parseDimensionTokens('line1\nline2\r\nline3')).toEqual([
        'line1',
        'line2',
        'line3',
      ]);
    });

    it('preserves commas and semicolons inside single or double quotes and strips enclosing quotes', () => {
      expect(
        parseDimensionTokens('"foo, bar", \'baz; qux\', plain_val'),
      ).toEqual(['foo, bar', 'baz; qux', 'plain_val']);
    });
  });
});
