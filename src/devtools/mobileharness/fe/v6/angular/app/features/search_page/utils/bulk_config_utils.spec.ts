import {
  extractDeviceTarget,
  formatCurrentSelectionBadge,
  formatDeviceCount,
  formatNothingToUpdateMessage,
  formatSuccessSupportText,
  getDeviceTargetKey,
} from './bulk_config_utils';

describe('bulk_config_utils', () => {
  describe('extractDeviceTarget and getDeviceTargetKey', () => {
    it('formats composite key with hostName when present and plain deviceId when absent', () => {
      expect(
        getDeviceTargetKey({deviceId: '000000000000', hostName: 'host-1'}),
      ).toBe('000000000000@@host-1');
      expect(getDeviceTargetKey({deviceId: 'dev-1'})).toBe('dev-1');
    });

    it('extracts DeviceTarget and distinguishes duplicate deviceIds across different hosts', () => {
      const errorRowHost1 = {
        id: '000000000000',
        cells: [
          {
            link: {
              text: '000000000000',
              target: {device: {id: '000000000000', hostName: 'host-1.corp'}},
            },
          },
        ],
      };
      const errorRowHost2 = {
        id: '000000000000',
        cells: [
          {
            link: {
              text: '000000000000',
              target: {device: {id: '000000000000', hostName: 'host-2.corp'}},
            },
          },
        ],
      };

      expect(extractDeviceTarget(errorRowHost1)).toEqual({
        deviceId: '000000000000',
        hostName: 'host-1.corp',
      });
      expect(getDeviceTargetKey(errorRowHost1)).toBe(
        '000000000000@@host-1.corp',
      );
      expect(getDeviceTargetKey(errorRowHost2)).toBe(
        '000000000000@@host-2.corp',
      );
    });
  });

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
});
