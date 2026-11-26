import {ApplicationRef} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of, Subject, throwError} from 'rxjs';
import {DeviceError} from '../../../core/models/common_models';
import {GetDeviceErrorsResponse} from '../../../core/models/device_overview';
import {
  DEVICE_SERVICE,
  DeviceService,
} from '../../../core/services/device/device_service';
import {ClipboardService} from '../../services/clipboard_service';
import {SnackBarService} from '../../services/snackbar_service';
import {
  DEVICE_ERROR_SIDEBAR_ICON_ONLY_STORAGE_KEY,
  DeviceErrorDialog,
  DeviceErrorDialogData,
  getLocalTimezoneSuffix,
  normalizeDeviceErrors,
} from './device_error_dialog';

/**
 * Runs change detection for `fixture` and for the dialog it opened. The dialog
 * is attached to the ApplicationRef, not to the fixture, so
 * `fixture.detectChanges()` alone doesn't render it when change detection is
 * driven by Zone.js, as in the OSS build.
 */
function detectDialogChanges(fixture: ComponentFixture<unknown>) {
  fixture.detectChanges();
  TestBed.inject(ApplicationRef).tick();
}

describe('DeviceErrorDialog', () => {
  let component: DeviceErrorDialog;
  let fixture: ComponentFixture<MatTestDialogOpener<DeviceErrorDialog>>;
  let dialogRef: MatDialogRef<DeviceErrorDialog>;
  let deviceServiceSpy: jasmine.SpyObj<DeviceService>;
  let clipboardServiceSpy: jasmine.SpyObj<ClipboardService>;
  let snackBarServiceSpy: jasmine.SpyObj<SnackBarService>;

  // Fusion raw-string shape with proto3 Envoy printed defaults (0 / '' / UNCLASSIFIED / UNKNOWN)
  // and Fusion dummy tail.
  const fusionRawError: DeviceError = {
    timestampMs: '1794799704129',
    error: {
      summary: {
        message:
          'DeviceException: Cannot restore device in state OFFLINE [MH|INFRA_ISSUE|DEVICE_INFRA_UNKNOWN_FAILURE|53901]\n\tat com.google.devtools.omnilab.Foo.bar(Foo.kt:12)',
        metadata: [],
      },
      cause: [{message: '', metadata: []}],
      criticalErrorId: {
        code: 0,
        name: '',
        type: 'UNCLASSIFIED',
        namespace: 'UNKNOWN',
      },
      completeStackTrace:
        ': DeviceException: Cannot restore device in state OFFLINE [MH|INFRA_ISSUE|DEVICE_INFRA_UNKNOWN_FAILURE|53901]\n\tat com.google.devtools.omnilab.Foo.bar(Foo.kt:12)\n []\n\tSuppressed: :  []\nCaused by: :  []\n',
    },
  };

  // Structured Non-Fusion shape with earlier timestamp (verifies chronological sorting)
  // and ESF-style omitted default fields.
  const structuredError: DeviceError = {
    timestampMs: '1794799701715',
    error: {
      summary: {
        errorId: {
          code: 40493,
          name: 'LAB_EXTERNAL_DEVICE_MANAGER_RESERVE_RPC_ERROR',
          type: 'DEPENDENCY_ISSUE',
          namespace: 'MH',
        },
        message: 'Failed to reserve device in TF.',
      },
      cause: [
        {
          errorId: {code: -1, name: 'UNKNOWN_ERROR_ID'},
          message: 'UNAVAILABLE: io exception',
        },
      ],
      userFacingCriticalErrorId: {
        code: 40493,
        name: 'LAB_EXTERNAL_DEVICE_MANAGER_RESERVE_RPC_ERROR',
        type: 'DEPENDENCY_ISSUE',
        namespace: 'MH',
      },
      errorNameStackTrace:
        'LAB_EXTERNAL_DEVICE_MANAGER_RESERVE_RPC_ERROR|UNKNOWN_ERROR_ID|UNKNOWN_ERROR_ID',
      errorCodeStackTrace: '40493|-1|-1',
      completeStackTrace:
        'MobileHarnessException: Failed to reserve device in TF.\n\tat com.google.devtools.mobileharness.Lab.reserve(Lab.java:42)\nCaused by: UNAVAILABLE: io exception',
    },
  };

  // Fusion ghost empty error payload.
  const ghostEmptyError: DeviceError = {
    timestampMs: '1794799709999',
    error: {
      summary: {},
      cause: [{}],
      completeStackTrace: ':  []\n\tSuppressed: :  []\nCaused by: :  []\n',
    },
  };

  const mockDialogData: DeviceErrorDialogData = {
    deviceId: '13061FDD40019B',
    hostName: 'mi05-dm02.atl14.mtaas.google.com',
  };

  beforeEach(async () => {
    window.localStorage.removeItem(DEVICE_ERROR_SIDEBAR_ICON_ONLY_STORAGE_KEY);
    deviceServiceSpy = jasmine.createSpyObj<DeviceService>('DeviceService', [
      'getDeviceErrors',
    ]);
    deviceServiceSpy.getDeviceErrors.and.returnValue(
      of({deviceErrors: [fusionRawError, structuredError, ghostEmptyError]}),
    );
    clipboardServiceSpy = jasmine.createSpyObj('ClipboardService', [
      'copyToClipboard',
    ]);
    snackBarServiceSpy = jasmine.createSpyObj('SnackBarService', [
      'showInfo',
      'showError',
      'showSuccess',
    ]);

    await TestBed.configureTestingModule({
      imports: [
        DeviceErrorDialog,
        NoopAnimationsModule,
        MatTestDialogOpenerModule,
      ],
      providers: [
        {provide: MAT_DIALOG_DATA, useValue: mockDialogData},
        {provide: DEVICE_SERVICE, useValue: deviceServiceSpy},
        {provide: ClipboardService, useValue: clipboardServiceSpy},
        {provide: SnackBarService, useValue: snackBarServiceSpy},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceErrorDialog, {
        data: mockDialogData,
      }),
    );
    component = fixture.componentInstance.dialogRef.componentInstance;
    dialogRef = fixture.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');

    fixture.detectChanges();
  });

  afterEach(() => {
    window.localStorage.removeItem(DEVICE_ERROR_SIDEBAR_ICON_ONLY_STORAGE_KEY);
  });

  it('should fetch device errors on init and display device, host, Snapshot from timestamp, and Refresh button', () => {
    expect(deviceServiceSpy.getDeviceErrors).toHaveBeenCalledWith(
      '13061FDD40019B',
      'mi05-dm02.atl14.mtaas.google.com',
    );
    const subtitle = document.querySelector('.device-error-dialog__subtitle');
    expect(subtitle?.textContent).toContain('13061FDD40019B');
    expect(subtitle?.textContent).toContain('mi05-dm02.atl14.mtaas.google.com');
    expect(subtitle?.textContent).toContain('Snapshot from');
    expect(subtitle?.querySelector('app-datetime-trigger')).not.toBeNull();
    const footerButtons = Array.from(
      document.querySelectorAll('.device-error-dialog__footer-btn'),
    ).map((b) => b.textContent?.trim() ?? '');
    expect(footerButtons.some((t) => t.includes('Refresh'))).toBeTrue();
    expect(footerButtons.some((t) => t.includes('Copy All'))).toBeTrue();
    expect(footerButtons.some((t) => t.includes('Done'))).toBeTrue();
  });

  it('should show loading spinner immediately while slow getDeviceErrors RPC is in flight', async () => {
    (dialogRef.close as jasmine.Spy).and.callThrough();
    dialogRef.close();
    fixture.detectChanges();
    await fixture.whenStable();

    const slowSubject = new Subject<GetDeviceErrorsResponse>();
    deviceServiceSpy.getDeviceErrors.and.returnValue(slowSubject);

    const slowFixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceErrorDialog, {
        data: mockDialogData,
      }),
    );
    detectDialogChanges(slowFixture);
    const dialogEls = document.querySelectorAll('app-device-error-dialog');
    const slowDialogEl = dialogEls[dialogEls.length - 1] as HTMLElement;

    const loadingOverlay = slowDialogEl.querySelector(
      '.device-error-dialog__loading-overlay',
    );
    expect(loadingOverlay).not.toBeNull();
    expect(loadingOverlay?.textContent).toContain('Loading device errors...');
    expect(
      slowDialogEl.querySelector('.device-error-dialog__subtitle')?.textContent,
    ).not.toContain('Snapshot from');

    const outlinedBtns = slowDialogEl.querySelectorAll(
      '.device-error-dialog__footer-btn--outlined',
    ) as NodeListOf<HTMLButtonElement>;
    expect(outlinedBtns[0].disabled).toBeTrue();
    expect(outlinedBtns[1].disabled).toBeTrue();

    slowSubject.next({deviceErrors: [structuredError]});
    slowSubject.complete();
    detectDialogChanges(slowFixture);

    expect(
      slowDialogEl.querySelector('.device-error-dialog__loading-overlay'),
    ).toBeNull();
    expect(
      slowDialogEl.querySelector('.device-error-dialog__subtitle')?.textContent,
    ).toContain('Snapshot from');
    expect(outlinedBtns[0].disabled).toBeFalse();
    expect(outlinedBtns[1].disabled).toBeFalse();

    slowFixture.componentInstance.dialogRef.close();
    detectDialogChanges(slowFixture);
    await slowFixture.whenStable();
  });

  it('should keep dialog open on initial load failure and recover when Refresh is clicked', async () => {
    (dialogRef.close as jasmine.Spy).and.callThrough();
    dialogRef.close();
    fixture.detectChanges();
    await fixture.whenStable();

    deviceServiceSpy.getDeviceErrors.and.returnValue(
      throwError(() => new Error('Deadline exceeded')),
    );

    const failFixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceErrorDialog, {
        data: mockDialogData,
      }),
    );
    const failDialogRef = failFixture.componentInstance.dialogRef;
    spyOn(failDialogRef, 'close');
    detectDialogChanges(failFixture);
    const dialogEls = document.querySelectorAll('app-device-error-dialog');
    const failDialogEl = dialogEls[dialogEls.length - 1] as HTMLElement;

    // Dialog MUST stay open so user can click Refresh
    expect(failDialogRef.close).not.toHaveBeenCalled();
    expect(snackBarServiceSpy.showError).toHaveBeenCalledWith(
      'Failed to get device errors for 13061FDD40019B: Deadline exceeded',
    );
    const errorEmptyState = failDialogEl.querySelector(
      '.device-error-dialog__empty-state--error',
    );
    expect(errorEmptyState?.textContent).toContain(
      'Failed to load device errors. Click Refresh to try again.',
    );

    const outlinedBtns = failDialogEl.querySelectorAll(
      '.device-error-dialog__footer-btn--outlined',
    ) as NodeListOf<HTMLButtonElement>;
    const refreshBtn = outlinedBtns[0];
    const copyAllBtn = outlinedBtns[1];
    expect(refreshBtn.disabled).toBeFalse();
    expect(copyAllBtn.disabled).toBeTrue();

    // Backend recovers -> click Refresh
    deviceServiceSpy.getDeviceErrors.and.returnValue(
      of({deviceErrors: [structuredError, fusionRawError]}),
    );
    refreshBtn.click();
    detectDialogChanges(failFixture);

    expect(snackBarServiceSpy.showSuccess).toHaveBeenCalledWith(
      'Device errors refreshed successfully',
    );
    expect(
      failDialogEl.querySelector('.device-error-dialog__empty-state--error'),
    ).toBeNull();
    expect(
      failDialogEl.querySelectorAll('.device-error-dialog__panel').length,
    ).toBe(2);
    expect(
      failDialogEl.querySelector('.device-error-dialog__subtitle')?.textContent,
    ).toContain('Snapshot from');

    (failDialogRef.close as jasmine.Spy).and.callThrough();
    failDialogRef.close();
    detectDialogChanges(failFixture);
    await failFixture.whenStable();
  });

  it('should keep existing errors visible and show error snackbar when Refresh fails', () => {
    deviceServiceSpy.getDeviceErrors.and.returnValue(
      throwError(() => new Error('Transient RPC failure')),
    );

    const refreshBtn = document.querySelectorAll(
      '.device-error-dialog__footer-btn--outlined',
    )[0] as HTMLButtonElement;
    refreshBtn.click();
    fixture.detectChanges();

    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(snackBarServiceSpy.showError).toHaveBeenCalledWith(
      'Failed to refresh device errors for 13061FDD40019B: Transient RPC failure',
    );
    expect(
      document.querySelectorAll('.device-error-dialog__panel').length,
    ).toBe(3);
  });

  it('should sort errors chronologically and show local timezone suffix in sidebar and headers', () => {
    const expectedTzSuffix = getLocalTimezoneSuffix(new Date(1794799701715));

    const sidebarTimestamps = Array.from(
      document.querySelectorAll('.device-error-dialog__sidebar-timestamp'),
    ).map((el) => el.textContent?.trim() ?? '');
    const headerTimestamps = Array.from(
      document.querySelectorAll('.device-error-dialog__accordion-ts-text'),
    ).map((el) => el.textContent?.trim() ?? '');

    expect(sidebarTimestamps.length).toBe(3);
    expect(headerTimestamps.length).toBe(3);
    expect(sidebarTimestamps[0]).toContain('#1 · ');
    expect(sidebarTimestamps[0]).toContain(expectedTzSuffix);
    expect(headerTimestamps[0]).toContain('#1 · ');
    expect(headerTimestamps[0]).toContain(expectedTzSuffix);

    // Structured error had earlier timestamp, so it should be sorted first.
    const panels = document.querySelectorAll('.device-error-dialog__panel');
    expect(
      panels[0].querySelector('.device-error-dialog__chip--name')?.textContent
        ?.trim(),
    ).toBe('LAB_EXTERNAL_DEVICE_MANAGER_RESERVE_RPC_ERROR');
    expect(
      panels[0].querySelector('.device-error-dialog__chip--code')?.textContent
        ?.trim(),
    ).toBe('40493');

    // Fusion error keeps colorful header chips (name, code, type, namespace) while rendering raw V5 console output.
    expect(
      panels[1].querySelector('.device-error-dialog__chip--name')?.textContent
        ?.trim(),
    ).toBe('DEVICE_INFRA_UNKNOWN_FAILURE');
    expect(
      panels[1].querySelector('.device-error-dialog__chip--code')?.textContent
        ?.trim(),
    ).toBe('53901');
    expect(
      panels[1].querySelector('.device-error-dialog__chip--type')?.textContent
        ?.trim(),
    ).toBe('INFRA_ISSUE');
    expect(
      panels[1]
        .querySelector('.device-error-dialog__chip--namespace')
        ?.textContent?.trim(),
    ).toBe('MH');
    const fusionPre = panels[1].querySelector(
      '.device-error-dialog__output-content',
    )?.textContent;
    expect(fusionPre).toContain(
      'User Facing Critical Error ID:  (0)  [UNKNOWN|UNCLASSIFIED]',
    );
    expect(fusionPre).toContain('DeviceException: Cannot restore device');
    expect(fusionPre).toContain('Suppressed: :  []');

    // Empty error renders terminal output directly with no subheader or chips.
    expect(
      panels[2].querySelector('.device-error-dialog__chip--name'),
    ).toBeNull();
    expect(
      panels[2].querySelector('.device-error-dialog__output-content')
        ?.textContent,
    ).toContain('StackTrace:');
  });

  it('should expand all sections by default and toggle expand/collapse all', () => {
    expect(component.allExpanded()).toBeTrue();
    const toggleBtn = document.querySelector(
      '.device-error-dialog__action-btn',
    ) as HTMLButtonElement;
    expect(toggleBtn.textContent?.trim()).toBe('Collapse All');

    toggleBtn.click();
    fixture.detectChanges();
    expect(component.allExpanded()).toBeFalse();
    expect(toggleBtn.textContent?.trim()).toBe('Expand All');

    toggleBtn.click();
    fixture.detectChanges();
    expect(component.allExpanded()).toBeTrue();
    expect(toggleBtn.textContent?.trim()).toBe('Collapse All');
  });

  it('should copy header summary, section content, and all errors', () => {
    clipboardServiceSpy.copyToClipboard.and.returnValue(true);

    const headerCopyBtn = document.querySelector(
      '.device-error-dialog__copy-header-btn',
    ) as HTMLButtonElement;
    headerCopyBtn.click();
    expect(clipboardServiceSpy.copyToClipboard).toHaveBeenCalledWith(
      jasmine.stringContaining('LAB_EXTERNAL_DEVICE_MANAGER_RESERVE_RPC_ERROR'),
    );
    expect(snackBarServiceSpy.showInfo).toHaveBeenCalledWith(
      'Error summary copied',
    );

    const contentCopyBtn = document.querySelector(
      '.device-error-dialog__copy-btn',
    ) as HTMLButtonElement;
    contentCopyBtn.click();
    expect(clipboardServiceSpy.copyToClipboard).toHaveBeenCalledWith(
      jasmine.stringContaining('MobileHarnessException: Failed to reserve'),
    );
    expect(snackBarServiceSpy.showInfo).toHaveBeenCalledWith(
      'Error content copied',
    );

    const copyAllBtn = document.querySelectorAll(
      '.device-error-dialog__footer-btn--outlined',
    )[1] as HTMLButtonElement;
    copyAllBtn.click();
    expect(clipboardServiceSpy.copyToClipboard).toHaveBeenCalledWith(
      jasmine.stringContaining('=== Error #1'),
    );
    expect(snackBarServiceSpy.showInfo).toHaveBeenCalledWith(
      'All device errors copied',
    );
  });

  it('should show green check icon for 3s after floating copy succeeds and revert', () => {
    jasmine.clock().install();
    try {
      clipboardServiceSpy.copyToClipboard.and.returnValue(true);

      const contentCopyBtn = document.querySelector(
        '.device-error-dialog__copy-btn',
      ) as HTMLButtonElement;
      const icon = contentCopyBtn.querySelector('mat-icon');
      expect(icon?.textContent?.trim()).toBe('content_copy');
      expect(
        contentCopyBtn.classList.contains(
          'device-error-dialog__copy-btn--copied',
        ),
      ).toBeFalse();

      contentCopyBtn.click();
      fixture.detectChanges();
      expect(icon?.textContent?.trim()).toBe('check');
      expect(
        contentCopyBtn.classList.contains(
          'device-error-dialog__copy-btn--copied',
        ),
      ).toBeTrue();

      jasmine.clock().tick(2999);
      fixture.detectChanges();
      expect(icon?.textContent?.trim()).toBe('check');

      jasmine.clock().tick(1);
      fixture.detectChanges();
      expect(icon?.textContent?.trim()).toBe('content_copy');
      expect(
        contentCopyBtn.classList.contains(
          'device-error-dialog__copy-btn--copied',
        ),
      ).toBeFalse();
    } finally {
      jasmine.clock().uninstall();
    }
  });

  it('should snap sidebar to 56px icon rail below 185px and restore via expand button', () => {
    const layoutEl = document.querySelector(
      '.device-error-dialog__layout',
    ) as HTMLElement;
    spyOnProperty(layoutEl, 'clientWidth', 'get').and.returnValue(1000);

    const resizer = document.querySelector(
      '.device-error-dialog__resizer',
    ) as HTMLElement;
    expect(component.sidebar.width()).toBe(300);
    expect(component.sidebar.isCompact()).toBeFalse();

    // Start drag at x=300
    resizer.dispatchEvent(
      new MouseEvent('mousedown', {clientX: 300, bubbles: true}),
    );
    expect(component.sidebar.isResizing()).toBeTrue();

    // Drag right past 50% (max should clamp to 500)
    window.dispatchEvent(new MouseEvent('mousemove', {clientX: 900}));
    expect(component.sidebar.width()).toBe(500);

    // Drag to a valid full width (380)
    window.dispatchEvent(new MouseEvent('mousemove', {clientX: 380}));
    expect(component.sidebar.width()).toBe(380);
    expect(component.sidebar.isCompact()).toBeFalse();

    // Drag below 185px threshold -> snaps cleanly to 56px icon rail
    window.dispatchEvent(new MouseEvent('mousemove', {clientX: 120}));
    expect(component.sidebar.width()).toBe(56);
    expect(component.sidebar.isCompact()).toBeTrue();

    window.dispatchEvent(new MouseEvent('mouseup'));
    expect(component.sidebar.isResizing()).toBeFalse();
    fixture.detectChanges();

    // Verify icon-only rail buttons render with #1..#3 badges and zero text items
    expect(
      document.querySelectorAll('.device-error-dialog__sidebar-item').length,
    ).toBe(0);
    const railItems = document.querySelectorAll(
      '.device-error-dialog__rail-item-btn',
    );
    expect(railItems.length).toBe(3);
    expect(
      railItems[2]
        .querySelector('.device-error-dialog__rail-item-badge')
        ?.textContent?.trim(),
    ).toBe('3');

    // Toggle collapse/expand all from the compact rail icon button
    const railToggleBtn = document.querySelectorAll(
      '.device-error-dialog__rail-icon-btn',
    )[1] as HTMLButtonElement;
    expect(railToggleBtn.querySelector('mat-icon')?.textContent?.trim()).toBe(
      'unfold_less',
    );
    railToggleBtn.click();
    fixture.detectChanges();
    expect(component.allExpanded()).toBeFalse();
    expect(railToggleBtn.querySelector('mat-icon')?.textContent?.trim()).toBe(
      'unfold_more',
    );

    // Clicking the expand-sidebar icon button restores initial default width (300)
    const expandRailBtn = document.querySelector(
      '.device-error-dialog__rail-icon-btn--secondary',
    ) as HTMLButtonElement;
    expandRailBtn.click();
    fixture.detectChanges();
    expect(component.sidebar.width()).toBe(300);
    expect(component.sidebar.isCompact()).toBeFalse();
  });

  it('should update activeErrorId to the last error when scrolled to bottom', () => {
    const mainEl = document.querySelector(
      '.device-error-dialog__main',
    ) as HTMLElement;
    spyOnProperty(mainEl, 'scrollHeight', 'get').and.returnValue(1200);
    spyOnProperty(mainEl, 'clientHeight', 'get').and.returnValue(400);

    let mockScrollTop = 0;
    spyOnProperty(mainEl, 'scrollTop', 'get').and.callFake(() => mockScrollTop);

    expect(component.activeErrorId()).toBe('device-err-0');

    // Scroll all the way to bottom (maxScroll = 800)
    mockScrollTop = 800;
    mainEl.dispatchEvent(new Event('scroll'));
    fixture.detectChanges();
    expect(component.activeErrorId()).toBe('device-err-2');

    // Scroll back to top
    mockScrollTop = 0;
    mainEl.dispatchEvent(new Event('scroll'));
    fixture.detectChanges();
    expect(component.activeErrorId()).toBe('device-err-0');
  });

  it('should navigate to an error when its sidebar item is activated with Enter or Space', () => {
    const sidebarItems = document.querySelectorAll<HTMLElement>(
      '.device-error-dialog__sidebar-item',
    );

    sidebarItems[1].dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter'}));
    expect(component.activeErrorId()).toBe('device-err-1');

    const spaceEvent = new KeyboardEvent('keydown', {key: ' ', cancelable: true});
    sidebarItems[2].dispatchEvent(spaceEvent);
    expect(component.activeErrorId()).toBe('device-err-2');
    // Space must not also scroll the sidebar.
    expect(spaceEvent.defaultPrevented).toBeTrue();
  });

  it('should keep sidebar header fixed while only the nav list scrolls', () => {
    const sidebarEl = document.querySelector(
      '.device-error-dialog__sidebar',
    ) as HTMLElement;
    const navEl = document.querySelector(
      '.device-error-dialog__sidebar-nav',
    ) as HTMLElement;

    expect(getComputedStyle(sidebarEl).overflowY).toBe('hidden');
    expect(getComputedStyle(navEl).overflowY).toBe('auto');
  });

  it('should hide left sidebar and resizer when there is only 1 error', () => {
    component.normalizedErrors.set(normalizeDeviceErrors([structuredError]));
    fixture.detectChanges();

    expect(
      component.dialogLayout!.nativeElement.querySelector(
        '.device-error-dialog__sidebar',
      ),
    ).toBeNull();
    expect(document.querySelector('.device-error-dialog__resizer')).toBeNull();
    expect(
      document.querySelectorAll('.device-error-dialog__panel').length,
    ).toBe(1);
  });

  it('should persist icon-only mode in localStorage for multi-error dialogs and never touch localStorage for single-error dialogs', () => {
    const resizer = document.querySelector(
      '.device-error-dialog__resizer',
    ) as HTMLElement;

    // Snap into icon-only mode -> writes 'true'
    resizer.dispatchEvent(
      new MouseEvent('mousedown', {clientX: 300, bubbles: true}),
    );
    window.dispatchEvent(new MouseEvent('mousemove', {clientX: 100}));
    window.dispatchEvent(new MouseEvent('mouseup'));
    expect(
      window.localStorage.getItem(DEVICE_ERROR_SIDEBAR_ICON_ONLY_STORAGE_KEY),
    ).toBe('true');

    // Opening a single-error dialog keeps sidebar hidden AND preserves 'true' untouched
    (dialogRef.close as jasmine.Spy).and.callThrough();
    dialogRef.close();
    fixture.detectChanges();

    deviceServiceSpy.getDeviceErrors.and.returnValue(
      of({deviceErrors: [structuredError]}),
    );
    const singleErrorFixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceErrorDialog, {
        data: {
          deviceId: 'single-dev',
        },
      }),
    );
    detectDialogChanges(singleErrorFixture);
    const singleComponent =
      singleErrorFixture.componentInstance.dialogRef.componentInstance;

    expect(
      singleComponent.dialogLayout!.nativeElement.querySelector(
        '.device-error-dialog__sidebar',
      ),
    ).toBeNull();
    singleComponent.sidebar.expand();
    expect(
      window.localStorage.getItem(DEVICE_ERROR_SIDEBAR_ICON_ONLY_STORAGE_KEY),
    ).toBe('true');

    singleErrorFixture.componentInstance.dialogRef.close();
    detectDialogChanges(singleErrorFixture);

    // Opening another multi-error dialog restores 56px icon-only mode from localStorage
    deviceServiceSpy.getDeviceErrors.and.returnValue(
      of({deviceErrors: [fusionRawError, structuredError, ghostEmptyError]}),
    );
    const multiErrorFixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceErrorDialog, {
        data: mockDialogData,
      }),
    );
    detectDialogChanges(multiErrorFixture);
    const multiComponent =
      multiErrorFixture.componentInstance.dialogRef.componentInstance;
    spyOnProperty(
      multiComponent.dialogLayout!.nativeElement,
      'clientWidth',
      'get',
    ).and.returnValue(1000);

    expect(multiComponent.sidebar.width()).toBe(56);
    expect(multiComponent.sidebar.isCompact()).toBeTrue();

    // Clicking Expand sidebar restores 300px and writes 'false' to localStorage
    multiComponent.sidebar.expand();
    detectDialogChanges(multiErrorFixture);
    expect(multiComponent.sidebar.width()).toBe(300);
    expect(
      window.localStorage.getItem(DEVICE_ERROR_SIDEBAR_ICON_ONLY_STORAGE_KEY),
    ).toBe('false');
    multiErrorFixture.componentInstance.dialogRef.close();
    detectDialogChanges(multiErrorFixture);
  });

  it('should render V5 diagnostic fields directly inside the dark terminal output with no subheader', () => {
    expect(
      document.querySelector('.device-error-dialog__meta-panel'),
    ).toBeNull();

    const firstPanel = document.querySelectorAll(
      '.device-error-dialog__panel',
    )[0] as HTMLElement;
    const preContent =
      firstPanel.querySelector('.device-error-dialog__output-content')
        ?.textContent ?? '';

    expect(preContent).toContain('Time: ');
    expect(preContent).toContain(
      'User Facing Critical Error ID:  (40493) LAB_EXTERNAL_DEVICE_MANAGER_RESERVE_RPC_ERROR [MH|DEPENDENCY_ISSUE]',
    );
    expect(preContent).toContain(
      'Error Name Stack Trace:  LAB_EXTERNAL_DEVICE_MANAGER_RESERVE_RPC_ERROR|UNKNOWN_ERROR_ID|UNKNOWN_ERROR_ID',
    );
    expect(preContent).toContain(
      'StackTrace:\nMobileHarnessException: Failed to reserve device in TF.',
    );

    clipboardServiceSpy.copyToClipboard.and.returnValue(true);
    const copyAllBtn = document.querySelectorAll(
      '.device-error-dialog__footer-btn--outlined',
    )[1] as HTMLButtonElement;
    copyAllBtn.click();

    const copiedPayload =
      clipboardServiceSpy.copyToClipboard.calls.mostRecent().args[0];
    expect(copiedPayload).toContain(
      'User Facing Critical Error ID:  (40493) LAB_EXTERNAL_DEVICE_MANAGER_RESERVE_RPC_ERROR [MH|DEPENDENCY_ISSUE]',
    );
    expect(copiedPayload).toContain(
      'Error Name Stack Trace:  LAB_EXTERNAL_DEVICE_MANAGER_RESERVE_RPC_ERROR|UNKNOWN_ERROR_ID|UNKNOWN_ERROR_ID',
    );
    expect(copiedPayload).toContain('StackTrace:');
  });
});

describe('normalizeDeviceErrors', () => {
  it('handles ESF omitted fields and Envoy printed defaults identically', () => {
    const esfEmpty: DeviceError = {
      timestampMs: '1700000000000',
      error: {summary: {message: 'Plain java.lang.IllegalStateException'}},
    };
    const envoyPrinted: DeviceError = {
      timestampMs: '1700000000000',
      error: {
        summary: {
          errorId: {code: 0, name: '', type: 'UNCLASSIFIED', namespace: 'UNKNOWN'},
          message: 'Plain java.lang.IllegalStateException',
          metadata: [],
        },
        cause: [{message: '', metadata: []}],
        criticalErrorId: {
          code: 0,
          name: '',
          type: 'UNCLASSIFIED',
          namespace: 'UNKNOWN',
        },
        userFacingCriticalErrorId: {
          code: 0,
          name: '',
          type: 'UNCLASSIFIED',
          namespace: 'UNKNOWN',
        },
        errorNameStackTrace: '',
        errorCodeStackTrace: '',
        completeStackTrace: '',
      },
    };
    const resEsf = normalizeDeviceErrors([esfEmpty]);
    const resEnvoy = normalizeDeviceErrors([envoyPrinted]);

    expect(resEsf[0].errorId).toBeUndefined();
    expect(resEnvoy[0].errorId).toBeUndefined();
    expect(resEsf[0].displayContent).toBe(resEnvoy[0].displayContent);
    expect(resEsf[0].displayContent).toContain(
      'User Facing Critical Error ID:  (0)  [UNKNOWN|UNCLASSIFIED]\n',
    );
    expect(resEsf[0].displayContent).toContain('Error Name Stack Trace:  \n');
    expect(resEsf[0].displayContent).toContain('StackTrace:\n');
  });

  it('uses userFacingCriticalErrorId directly in V5 console output and normalizes omitted ESF enum defaults', () => {
    const esfUnknownErrorId: DeviceError = {
      timestampMs: '1700000000000',
      error: {
        summary: {
          errorId: {
            code: 40100,
            name: 'LAB_WRAPPER_PREPARE_ERROR',
            type: 'DEPENDENCY_ISSUE',
            namespace: 'MH',
          },
        },
        userFacingCriticalErrorId: {
          code: -1,
          name: 'UNKNOWN_ERROR_ID',
        },
        errorNameStackTrace: 'LAB_WRAPPER_PREPARE_ERROR|UNKNOWN_ERROR_ID',
        completeStackTrace: 'MobileHarnessException: Failed during prepare',
      },
    };

    const [normalized] = normalizeDeviceErrors([esfUnknownErrorId]);
    expect(normalized.errorId).toEqual({
      code: 40100,
      name: 'LAB_WRAPPER_PREPARE_ERROR',
      type: 'DEPENDENCY_ISSUE',
      namespace: 'MH',
    });
    expect(normalized.displayContent).toContain(
      'User Facing Critical Error ID:  (-1) UNKNOWN_ERROR_ID [UNKNOWN|UNCLASSIFIED]',
    );
    expect(normalized.displayContent).toContain(
      'Error Name Stack Trace:  LAB_WRAPPER_PREPARE_ERROR|UNKNOWN_ERROR_ID',
    );
    expect(normalized.displayContent).toContain(
      'StackTrace:\nMobileHarnessException: Failed during prepare',
    );
  });

  it('applies the structured error ID rules to Fusion error ID tags', () => {
    const placeholderTag: DeviceError = {
      timestampMs: '1700000000000',
      error: {
        summary: {
          message: 'Setup failed [UNKNOWN|UNCLASSIFIED|UNKNOWN_ERROR_ID|-1]',
          classType: {className: 'com.google.devtools.DeviceException'},
        },
      },
    };
    const namedTagWithoutCode: DeviceError = {
      timestampMs: '1700000001000',
      error: {
        summary: {message: 'Reserve failed [MH|INFRA_ISSUE|RESERVE_ERROR|-1]'},
      },
    };

    const [placeholder, named] = normalizeDeviceErrors([
      placeholderTag,
      namedTagWithoutCode,
    ]);

    // Like a placeholder structured error ID, a placeholder tag shows no chips.
    expect(placeholder.errorId).toBeUndefined();
    expect(placeholder.shortLabel).toBe('DeviceException');
    expect(placeholder.headerCopyText).not.toContain('UNKNOWN_ERROR_ID');
    // A real error name is kept, but a non-positive code is not shown.
    expect(named.errorId).toEqual({
      code: undefined,
      name: 'RESERVE_ERROR',
      type: 'INFRA_ISSUE',
      namespace: 'MH',
    });
  });
});
