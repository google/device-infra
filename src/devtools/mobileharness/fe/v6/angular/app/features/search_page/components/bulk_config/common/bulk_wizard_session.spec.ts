import {TestBed} from '@angular/core/testing';
import {MatDialogRef} from '@angular/material/dialog';
import {of, Subject, throwError} from 'rxjs';

import {
  DeviceConfigChangeStatus,
  DeviceTarget,
} from '../../../../../core/models/device_config_models';
import {BulkConfigDialogResult} from '../../../models';
import {useBulkWizardSession, useInlineTask} from './bulk_wizard_session';

describe('useBulkWizardSession', () => {
  let mockDialogRef: jasmine.SpyObj<
    MatDialogRef<unknown, BulkConfigDialogResult | undefined>
  >;

  beforeEach(() => {
    mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    mockDialogRef.disableClose = false;
    TestBed.configureTestingModule({
      providers: [
        {provide: MatDialogRef, useValue: mockDialogRef}, // disable_use_mat_dialog_opener
      ],
    });
  });

  it('loads review entries and computes reviewSummary', () => {
    const session = TestBed.runInInjectionContext(() => useBulkWizardSession());

    session.loadReview(
      () =>
        of({
          entries: [
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
          ],
        }),
      'Fallback review error',
    );

    expect(session.phase()).toBe('review');
    expect(session.viewMode()).toBe('review');
    expect(session.stepError()).toBeNull();
    expect(session.reviewSummary().willUpdateCount).toBe(1);
    expect(session.reviewSummary().alreadyMatchCount).toBe(1);
    expect(session.reviewSummary().readOnlyCount).toBe(1);
  });

  it('captures error and stepErrorTitle when loadReview fails, including OnePlatform nested errors', () => {
    const session = TestBed.runInInjectionContext(() => useBulkWizardSession());

    session.loadReview(
      () =>
        throwError(() => ({
          error: {error: {message: 'Backend ESF deadline exceeded'}},
        })),
      'Failed to check device status',
    );

    expect(session.viewMode()).toBe('step_error');
    expect(session.stepErrorTitle()).toBe('Failed to check device status');
    expect(session.stepError()).toBe('Backend ESF deadline exceeded');
  });

  it('applies updates to WILL_UPDATE devices and closes with {updated: true} on success', () => {
    const session = TestBed.runInInjectionContext(() => useBulkWizardSession());

    session.loadReview(
      () =>
        of({
          entries: [
            {
              deviceId: 'd1',
              hostName: 'host-1',
              status:
                DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
            },
            {
              deviceId: 'd2',
              hostName: 'host-2',
              status:
                DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
            },
          ],
        }),
      '',
    );

    let requestedDevices: DeviceTarget[] = [];
    session.apply((devices) => {
      requestedDevices = devices;
      return of({errors: {}});
    }, 'Apply failed');

    expect(requestedDevices).toEqual([{deviceId: 'd1', hostName: 'host-1'}]);
    expect(mockDialogRef.disableClose).toBeFalse();
    expect(session.viewMode()).toBe('success');

    session.close();
    expect(mockDialogRef.close).toHaveBeenCalledWith({updated: true});
  });

  it('tracks partial errors and closes with {updated: true} when some devices succeed', () => {
    const session = TestBed.runInInjectionContext(() => useBulkWizardSession());

    session.loadReview(
      () =>
        of({
          entries: [
            {
              deviceId: 'd1',
              status:
                DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
            },
            {
              deviceId: 'd2',
              status:
                DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
            },
          ],
        }),
      '',
    );

    session.apply(
      () =>
        of({
          errors: {
            'd2': {code: 'PERMISSION_DENIED', message: 'Locked'},
          },
        }),
      'Apply failed',
    );

    expect(session.viewMode()).toBe('errors');
    expect(session.applyErrors()).toEqual([{deviceId: 'd2', reason: 'Locked'}]);

    session.close();
    expect(mockDialogRef.close).toHaveBeenCalledWith({updated: true});
  });

  it('keeps viewMode on review and sets applyRpcError when the whole apply RPC fails, allowing retry', () => {
    const session = TestBed.runInInjectionContext(() => useBulkWizardSession());

    session.loadReview(
      () =>
        of({
          entries: [
            {
              deviceId: 'd1',
              status:
                DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
            },
          ],
        }),
      '',
    );

    session.apply(
      () => throwError(() => new Error('Network error')),
      'Apply failed',
    );

    expect(session.viewMode()).toBe('review');
    expect(session.applyRpcError()).toBe('Network error');
    expect(session.applyErrors()).toEqual([]);

    session.close();
    expect(mockDialogRef.close).toHaveBeenCalledWith(undefined);

    session.setPhase('review');
    expect(session.applyRpcError()).toBeNull();
  });

  it('cancels in-flight step requests via setPhase or subsequent loadReview calls', () => {
    const session = TestBed.runInInjectionContext(() =>
      useBulkWizardSession<
        {deviceId: string; status?: DeviceConfigChangeStatus},
        'network' | 'review'
      >('network'),
    );

    const slowReq$ = new Subject<{
      entries: Array<{deviceId: string; status?: DeviceConfigChangeStatus}>;
    }>();
    const fastReq$ = new Subject<{
      entries: Array<{deviceId: string; status?: DeviceConfigChangeStatus}>;
    }>();

    session.loadReview(() => slowReq$.asObservable(), 'Slow error');
    expect(session.phase()).toBe('review');
    expect(session.viewMode()).toBe('loading');

    // Starting a new review cancels the slow request
    session.loadReview(() => fastReq$.asObservable(), 'Fast error');
    fastReq$.next({
      entries: [
        {
          deviceId: 'fast-dev',
          status:
            DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
        },
      ],
    });
    fastReq$.complete();

    // Late emission from cancelled slowReq$ is ignored
    slowReq$.next({
      entries: [
        {
          deviceId: 'stale-dev',
          status:
            DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        },
      ],
    });
    slowReq$.complete();

    expect(session.reviewEntries().map((e) => e.deviceId)).toEqual([
      'fast-dev',
    ]);

    // setPhase aborts in-flight request and clears error
    const pending$ = new Subject<{
      entries: Array<{deviceId: string; status?: DeviceConfigChangeStatus}>;
    }>();
    session.loadReview(() => pending$.asObservable(), 'Pending error');
    expect(session.viewMode()).toBe('loading');

    session.setPhase('network');
    expect(session.phase()).toBe('network');
    expect(session.viewMode()).toBe('network');
    expect(session.stepError()).toBeNull();
  });

  it('computes viewMode across pre-review, review, and terminal apply states', () => {
    const session = TestBed.runInInjectionContext(() =>
      useBulkWizardSession<
        {deviceId: string; status?: DeviceConfigChangeStatus},
        'network' | 'review'
      >('network'),
    );

    expect(session.viewMode()).toBe('network');

    const reviewReq$ = new Subject<{
      entries: Array<{deviceId: string; status?: DeviceConfigChangeStatus}>;
    }>();
    session.loadReview(() => reviewReq$, 'Review failed');
    expect(session.viewMode()).toBe('loading');

    reviewReq$.error(new Error('Review failed'));
    expect(session.viewMode()).toBe('step_error');
    expect(session.stepError()).toBe('Review failed');

    session.setPhase('network');
    expect(session.viewMode()).toBe('network');

    session.loadReview(
      () =>
        of({
          entries: [
            {
              deviceId: 'd1',
              status:
                DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
            },
          ],
        }),
      '',
    );
    expect(session.viewMode()).toBe('review');

    const applyReq$ = new Subject<{errors?: Record<string, never>}>();
    session.apply(() => applyReq$, 'Apply failed');
    expect(session.viewMode()).toBe('applying');

    applyReq$.next({errors: {}});
    applyReq$.complete();
    expect(session.viewMode()).toBe('success');
  });

  it('tracks inline tasks via useInlineTask without blocking wizard viewMode', () => {
    const {session, task} = TestBed.runInInjectionContext(() => ({
      session: useBulkWizardSession<
        {deviceId: string; status?: DeviceConfigChangeStatus},
        'key' | 'values' | 'review'
      >('key'),
      task: useInlineTask('Inline fallback error'),
    }));

    const req$ = new Subject<string>();
    let result = '';
    task.run(
      () => req$,
      (res) => {
        result = res;
      },
    );

    expect(task.isLoading()).toBeTrue();
    expect(task.hasLoaded()).toBeFalse();
    expect(task.error()).toBeNull();
    expect(session.viewMode()).toBe('key');

    req$.next('loaded-data');
    req$.complete();

    expect(task.isLoading()).toBeFalse();
    expect(task.hasLoaded()).toBeTrue();
    expect(result).toBe('loaded-data');

    let attempts = 0;
    task.run(
      () => {
        attempts++;
        return attempts === 1
          ? throwError(() => new Error('Timeout fetching suggestions'))
          : of('retried-data');
      },
      (res) => {
        result = res;
      },
    );
    expect(task.isLoading()).toBeFalse();
    expect(task.hasLoaded()).toBeFalse();
    expect(task.error()).toBe('Timeout fetching suggestions');
    expect(session.viewMode()).toBe('key');

    task.retry();
    expect(task.hasLoaded()).toBeTrue();
    expect(task.error()).toBeNull();
    expect(result).toBe('retried-data');
  });
});
