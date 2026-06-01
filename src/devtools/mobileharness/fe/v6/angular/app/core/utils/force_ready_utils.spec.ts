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

    it('should set isReady to true for all buttons if forcedButtons contains "*"', () => {
      const actions = {
        debug: {isReady: false},
        decommission: {isReady: false},
      };
      const forcedButtons = ['*'];
      const result = updateActions(actions, forcedButtons);
      expect(result.debug.isReady).toBeTrue();
      expect(result.decommission.isReady).toBeTrue();
    });

    it('should handle flash action with nested state', () => {
      const actions = {
        screenshot: {isReady: false},
        flash: {
          state: {isReady: false},
          params: {deviceType: '', requiredDimensions: ''},
        },
      };
      const forcedButtons = ['flash'];
      const result = updateActions(actions, forcedButtons);
      expect(result.screenshot.isReady).toBeFalse();
      expect(result.flash.state.isReady).toBeTrue();
    });

    it('should handle flash action with nested state when "*" is forced', () => {
      const actions = {
        screenshot: {isReady: false},
        flash: {
          state: {isReady: false},
          params: {deviceType: '', requiredDimensions: ''},
        },
      };
      const forcedButtons = ['*'];
      const result = updateActions(actions, forcedButtons);
      expect(result.screenshot.isReady).toBeTrue();
      expect(result.flash.state.isReady).toBeTrue();
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
      expect(result.headerInfo.actions!.debug.isReady).toBeTrue();
      expect(
        result.overviewContent.labServer!.actions!.start.isReady,
      ).toBeTrue();
    });

    it('should patch canUpgrade in host overview', () => {
      const body = {
        overviewContent: {
          canUpgrade: false,
        },
      };
      const result = modifyHostOverview(body, ['canUpgrade']);
      expect(result.overviewContent.canUpgrade).toBeTrue();
    });

    it('should patch canUpgrade in host overview if forcedButtons contains "*"', () => {
      const body = {
        overviewContent: {
          canUpgrade: false,
        },
      };
      const result = modifyHostOverview(body, ['*']);
      expect(result.overviewContent.canUpgrade).toBeTrue();
    });

    it('should return original object if no changes are made', () => {
      const body = {
        overviewContent: {
          labServer: {
            actions: {
              start: {isReady: false},
            },
          },
        },
      };
      const result = modifyHostOverview(body, ['nonexistent']);
      expect(result as unknown).toBe(body);
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
