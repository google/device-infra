import {createLabServerActions} from './ui_status_utils';

describe('ui_status_utils', () => {
  describe('createLabServerActions', () => {
    it('should disable mutation actions for CORE lab types', () => {
      const actions = createLabServerActions('RUNNING', 'LAB_SERVER_RELEASE_STATE_RUNNING', 'RUNNING', [
        'CORE',
      ]);
      expect(actions.release.enabled).toBeFalse();
      expect(actions.start.enabled).toBeFalse();
      expect(actions.restart.enabled).toBeFalse();
      expect(actions.stop.enabled).toBeFalse();
      expect(actions.release.visible).toBeFalse();
    });

    it('should disable mutation actions for FUSION lab types', () => {
      const actions = createLabServerActions('RUNNING', 'LAB_SERVER_RELEASE_STATE_RUNNING', 'RUNNING', [
        'FUSION',
      ]);
      expect(actions.release.enabled).toBeFalse();
      expect(actions.start.enabled).toBeFalse();
      expect(actions.restart.enabled).toBeFalse();
      expect(actions.stop.enabled).toBeFalse();
      expect(actions.release.visible).toBeFalse();
    });

    it('should enable mutation actions for SATELLITE lab types', () => {
      const actions = createLabServerActions('RUNNING', 'LAB_SERVER_RELEASE_STATE_RUNNING', 'RUNNING', [
        'SATELLITE',
      ]);
      expect(actions.release.enabled).toBeTrue();
      expect(actions.restart.enabled).toBeTrue();
      expect(actions.stop.enabled).toBeTrue();
      expect(actions.release.visible).toBeTrue();
    });

    it('should set start visible for start targets (DRAINED, STOPPED, UNKNOWN)', () => {
      let actions = createLabServerActions('RUNNING', 'LAB_SERVER_RELEASE_STATE_DRAINED', 'RUNNING', [
        'SATELLITE',
      ]);
      expect(actions.start.visible).toBeTrue();

      actions = createLabServerActions('RUNNING', 'LAB_SERVER_RELEASE_STATE_STOPPED', 'RUNNING', [
        'SATELLITE',
      ]);
      expect(actions.start.visible).toBeTrue();

      actions = createLabServerActions('RUNNING', 'LAB_SERVER_RELEASE_STATE_UNKNOWN', 'RUNNING', [
        'SATELLITE',
      ]);
      expect(actions.start.visible).toBeTrue();
    });

    it('should not set start visible for non-start targets', () => {
      const actions = createLabServerActions('RUNNING', 'LAB_SERVER_RELEASE_STATE_RUNNING', 'RUNNING', [
        'SATELLITE',
      ]);
      expect(actions.start.visible).toBeFalse();
    });

    it('should set restart/stop visible for restart/stop targets (RUNNING, ERROR)', () => {
      let actions = createLabServerActions('RUNNING', 'LAB_SERVER_RELEASE_STATE_RUNNING', 'RUNNING', [
        'SATELLITE',
      ]);
      expect(actions.restart.visible).toBeTrue();
      expect(actions.stop.visible).toBeTrue();

      actions = createLabServerActions('RUNNING', 'LAB_SERVER_RELEASE_STATE_ERROR', 'RUNNING', [
        'SATELLITE',
      ]);
      expect(actions.restart.visible).toBeTrue();
      expect(actions.stop.visible).toBeTrue();
    });

    it('should not set restart/stop visible for non-targets', () => {
      const actions = createLabServerActions('RUNNING', 'LAB_SERVER_RELEASE_STATE_DRAINED', 'RUNNING', [
        'SATELLITE',
      ]);
      expect(actions.restart.visible).toBeFalse();
      expect(actions.stop.visible).toBeFalse();
    });
  });
});
