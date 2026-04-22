import {
  modifyDeviceHeaderInfo,
  modifyDeviceOverview,
  modifyHostDevices,
  modifyHostHeaderInfo,
  modifyHostOverview,
  updateActions,
} from './force_ready_utils';

describe('forceReadyUtils', () => {
  describe('updateActions', () => {
    it('should set isReady to true for specified buttons', () => {
      const actions = {
        debug: {isReady: false},
        decommission: {isReady: false},
      };
      const forcedButtons = ['debug'];
      const result = updateActions(actions, forcedButtons);
      expect(result.debug.isReady).toBeTrue();
      expect(result.decommission.isReady).toBeFalse();
    });

    it('should not modify actions if button not found', () => {
      const actions = {
        debug: {isReady: false},
      };
      const forcedButtons = ['nonexistent'];
      const result = updateActions(actions, forcedButtons);
      expect(result).toBe(actions);
    });

    it('should return new object if modified', () => {
      const actions = {
        debug: {isReady: false},
      };
      const forcedButtons = ['debug'];
      const result = updateActions(actions, forcedButtons);
      expect(result).not.toBe(actions);
    });
  });

  describe('modifyHostHeaderInfo', () => {
    it('should patch host header info', () => {
      const body = {
        actions: {
          debug: {isReady: false},
        },
      };
      const result = modifyHostHeaderInfo(body, ['debug']);
      expect(result.actions.debug.isReady).toBeTrue();
    });
  });

  describe('modifyHostOverview', () => {
    it('should patch host overview header info and lab server actions', () => {
      const body = {
        headerInfo: {
          actions: {
            debug: {isReady: false},
          },
        },
        overviewContent: {
          labServer: {
            actions: {
              start: {isReady: false},
            },
          },
        },
      };
      const result = modifyHostOverview(body, ['debug', 'start']);
      expect(result.headerInfo.actions.debug.isReady).toBeTrue();
      expect(
        result.overviewContent.labServer.actions!.start.isReady,
      ).toBeTrue();
    });
  });

  describe('modifyDeviceHeaderInfo', () => {
    it('should patch device header info', () => {
      const body = {
        actions: {
          screenshot: {isReady: false},
        },
      };
      const result = modifyDeviceHeaderInfo(body, ['screenshot']);
      expect(result.actions!.screenshot.isReady).toBeTrue();
    });
  });

  describe('modifyDeviceOverview', () => {
    it('should patch device overview header info', () => {
      const body = {
        headerInfo: {
          actions: {
            screenshot: {isReady: false},
          },
        },
      };
      const result = modifyDeviceOverview(body, ['screenshot']);
      expect(result.headerInfo.actions!.screenshot.isReady).toBeTrue();
    });
  });

  describe('modifyHostDevices', () => {
    it('should patch device summaries in host devices response', () => {
      const body = {
        deviceSummaries: [
          {
            actions: {
              screenshot: {isReady: false},
            },
          },
        ],
      };
      const result = modifyHostDevices(body, ['screenshot']);
      expect(result.deviceSummaries[0].actions!.screenshot.isReady).toBeTrue();
    });
  });
});
