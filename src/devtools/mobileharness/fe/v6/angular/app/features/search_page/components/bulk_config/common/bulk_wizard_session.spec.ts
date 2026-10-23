import {TestBed} from '@angular/core/testing';
import {MatDialogRef} from '@angular/material/dialog';
import {of, Subject, throwError} from 'rxjs';

import {DeviceConfigChangeStatus} from '../../../../../core/models/device_config_models';
import {BulkConfigDialogResult} from '../../../models';
import {useBulkWizardSession} from './bulk_wizard_session';

describe('useBulkWizardSession', () => {
  let mockDialogRef: jasmine.SpyObj<
    MatDialogRef<unknown, BulkConfigDialogResult | undefined>
  >;

  beforeEach(() => {
    mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    mockDialogRef.disableClose = false;
    TestBed.configureTestingModule({});
  });

  it('loads review entries and computes reviewSummary', () => {
    const session = TestBed.runInInjectionContext(() =>
      useBulkWizardSession(mockDialogRef),
    );

    session.loadReview(
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
    expect(session.isStepLoading()).toBeFalse();
    expect(session.stepError()).toBeNull();
    expect(session.reviewSummary().willUpdateCount).toBe(1);
    expect(session.reviewSummary().alreadyMatchCount).toBe(1);
    expect(session.reviewSummary().readOnlyCount).toBe(1);
  });

  it('captures error when loadReview fails', () => {
    const session = TestBed.runInInjectionContext(() =>
      useBulkWizardSession(mockDialogRef),
    );

    session.loadReview(
      throwError(() => new Error('RPC timeout')),
      'Fallback review error',
    );

    expect(session.isStepLoading()).toBeFalse();
    expect(session.stepError()).toBe('RPC timeout');
  });

  it('applies updates to WILL_UPDATE devices and closes with {updated: true} on success', () => {
    const session = TestBed.runInInjectionContext(() =>
      useBulkWizardSession(mockDialogRef),
    );

    session.reviewEntries.set([
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
    ]);

    let requestedIds: string[] = [];
    session.apply((ids) => {
      requestedIds = ids;
      return of({errors: {}});
    }, 'Apply failed');

    expect(requestedIds).toEqual(['d1']);
    expect(session.isApplying()).toBeFalse();
    expect(mockDialogRef.disableClose).toBeFalse();
    expect(session.applyResult()).toBe('success');

    session.close();
    expect(mockDialogRef.close).toHaveBeenCalledWith({updated: true});
  });

  it('tracks partial errors and computes applySuccessCount accurately', () => {
    const session = TestBed.runInInjectionContext(() =>
      useBulkWizardSession(mockDialogRef),
    );

    session.reviewEntries.set([
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
    ]);

    session.apply(
      () =>
        of({
          errors: {
            'd2': {code: 'PERMISSION_DENIED', message: 'Locked'},
          },
        }),
      'Apply failed',
    );

    expect(session.applyResult()).toBe('errors');
    expect(session.applyErrors()).toEqual([{deviceId: 'd2', reason: 'Locked'}]);
    expect(session.applySuccessCount()).toBe(1);

    session.close();
    expect(mockDialogRef.close).toHaveBeenCalledWith({updated: true});
  });

  it('closes with undefined when all WILL_UPDATE devices fail', () => {
    const session = TestBed.runInInjectionContext(() =>
      useBulkWizardSession(mockDialogRef),
    );

    session.reviewEntries.set([
      {
        deviceId: 'd1',
        status:
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
      },
    ]);

    session.apply(
      () => throwError(() => new Error('Network error')),
      'Apply failed',
    );

    expect(session.applyResult()).toBe('errors');
    expect(session.applySuccessCount()).toBe(0);

    session.close();
    expect(mockDialogRef.close).toHaveBeenCalledWith(undefined);
  });

  it('cancels in-flight step requests via cancelStepTask, setPhase, or subsequent loadReview calls', () => {
    const session = TestBed.runInInjectionContext(() =>
      useBulkWizardSession<
        {deviceId: string; status?: DeviceConfigChangeStatus},
        'network' | 'review'
      >(mockDialogRef, 'network'),
    );

    const slowReq$ = new Subject<{
      entries: Array<{deviceId: string; status?: DeviceConfigChangeStatus}>;
    }>();
    const fastReq$ = new Subject<{
      entries: Array<{deviceId: string; status?: DeviceConfigChangeStatus}>;
    }>();

    session.loadReview(slowReq$.asObservable(), 'Slow error');
    expect(session.phase()).toBe('review');
    expect(session.isStepLoading()).toBeTrue();

    // Starting a new review cancels the slow request
    session.loadReview(fastReq$.asObservable(), 'Fast error');
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
    session.loadReview(pending$.asObservable(), 'Pending error');
    expect(session.isStepLoading()).toBeTrue();

    session.setPhase('network');
    expect(session.phase()).toBe('network');
    expect(session.isStepLoading()).toBeFalse();
    expect(session.stepError()).toBeNull();
  });

  it('computes viewMode across pre-review, review, and terminal apply states', () => {
    const session = TestBed.runInInjectionContext(() =>
      useBulkWizardSession<
        {deviceId: string; status?: DeviceConfigChangeStatus},
        'network' | 'review'
      >(mockDialogRef, 'network'),
    );

    session.isStepLoading.set(true);
    expect(session.viewMode()).toBe('network_loading');

    session.isStepLoading.set(false);
    session.stepError.set('Failed');
    expect(session.viewMode()).toBe('network_error');

    session.stepError.set(null);
    expect(session.viewMode()).toBe('network');

    session.setPhase('review');
    session.isStepLoading.set(true);
    expect(session.viewMode()).toBe('review_loading');

    session.isStepLoading.set(false);
    session.stepError.set('Review failed');
    expect(session.viewMode()).toBe('review_error');

    session.stepError.set(null);
    expect(session.viewMode()).toBe('review');

    session.isApplying.set(true);
    expect(session.viewMode()).toBe('applying');

    session.isApplying.set(false);
    session.applyResult.set('success');
    expect(session.viewMode()).toBe('success');

    session.applyResult.set('errors');
    expect(session.viewMode()).toBe('errors');
  });
});
