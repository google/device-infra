import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {GetHostDebugInfoResponse} from '@deviceinfra/app/core/models/host_action';
import {
  HOST_SERVICE,
  HostService,
} from '@deviceinfra/app/core/services/host/host_service';
import {ClipboardService} from '@deviceinfra/app/shared/services/clipboard_service';
import {SnackBarService} from '@deviceinfra/app/shared/services/snackbar_service';
import {of, Subject, throwError} from 'rxjs';

import {
  HostDebugDialog,
  normalizeHostDebugInfoResponse,
} from './host_debug_dialog';

describe('HostDebugDialog', () => {
  let component: HostDebugDialog;
  let fixture: ComponentFixture<MatTestDialogOpener<HostDebugDialog>>;
  let dialogRef: MatDialogRef<HostDebugDialog>;
  let hostServiceSpy: jasmine.SpyObj<HostService>;
  let snackBarServiceSpy: jasmine.SpyObj<SnackBarService>;
  let clipboardServiceSpy: jasmine.SpyObj<ClipboardService>;

  const mockDebugInfo: GetHostDebugInfoResponse = {
    results: [
      {
        command: 'lsusb',
        stdout:
          'Bus 001 Device 001: ID 1d6b:0002 Linux Foundation 2.0 root hub',
        stderr: '',
      },
      {
        command: 'ndm devices',
        stdout: '',
        stderr: 'Error running ndm',
      },
      {
        command: 'dmesg',
        stdout: 'dmesg output',
        stderr: 'dmesg error',
      },
    ],
    timestamp: '2026-04-03T08:00:00Z',
  };

  beforeEach(async () => {
    hostServiceSpy = jasmine.createSpyObj('HostService', ['getHostDebugInfo']);
    snackBarServiceSpy = jasmine.createSpyObj('SnackBarService', [
      'showInfo',
      'showError',
      'showSuccess',
    ]);
    clipboardServiceSpy = jasmine.createSpyObj('ClipboardService', [
      'copyToClipboard',
    ]);

    hostServiceSpy.getHostDebugInfo.and.returnValue(of(mockDebugInfo));

    await TestBed.configureTestingModule({
      imports: [
        HostDebugDialog,
        NoopAnimationsModule,
        MatTestDialogOpenerModule,
      ],
      providers: [
        {provide: MAT_DIALOG_DATA, useValue: {hostName: 'test-host'}},
        {provide: HOST_SERVICE, useValue: hostServiceSpy},
        {provide: SnackBarService, useValue: snackBarServiceSpy},
        {provide: ClipboardService, useValue: clipboardServiceSpy},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostDebugDialog, {
        data: {hostName: 'test-host'},
      }),
    );
    component = fixture.componentInstance.dialogRef.componentInstance;
    dialogRef = fixture.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');

    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should fetch debug info on init', () => {
    expect(hostServiceSpy.getHostDebugInfo).toHaveBeenCalledWith('test-host');
    expect(component.debugInfo()).toEqual(mockDebugInfo);
  });

  it('should show the host name and timestamp', () => {
    const subtitleElement = document.querySelector(
      '.host-debug-dialog__subtitle',
    );
    expect(subtitleElement?.textContent).toContain('test-host');
    expect(subtitleElement?.textContent).toContain('Apr 3, 2026');
  });

  it('should list commands in sidebar', () => {
    const commandItems = document.querySelectorAll(
      '.host-debug-dialog__sidebar-item',
    );
    expect(commandItems.length).toBe(3);
    expect(commandItems[0].textContent).toContain('lsusb');
    expect(commandItems[1].textContent).toContain('ndm devices');
    expect(commandItems[2].textContent).toContain('dmesg');
  });

  it('should show error icon for failed commands in sidebar', () => {
    const commandItems = document.querySelectorAll(
      '.host-debug-dialog__sidebar-item',
    );
    expect(
      commandItems[1].querySelector('.host-debug-dialog__sidebar-error-icon'),
    ).toBeTruthy();
    expect(
      commandItems[0].querySelector('.host-debug-dialog__sidebar-error-icon'),
    ).toBeFalsy();
  });

  it('should show command output in main content', () => {
    const commandSections = document.querySelectorAll(
      '.host-debug-dialog__panel',
    );
    expect(commandSections.length).toBe(3);

    const firstSection = commandSections[0];
    expect(
      firstSection.querySelector('.host-debug-dialog__accordion-command-text')
        ?.textContent,
    ).toContain('lsusb');
    expect(
      firstSection.querySelector('.host-debug-dialog__output-content')
        ?.textContent,
    ).toContain('Linux Foundation 2.0 root hub');
  });

  it('should show error in output box when command fails', () => {
    const commandSections = document.querySelectorAll(
      '.host-debug-dialog__panel',
    );
    const secondSection = commandSections[1];

    expect(
      secondSection.querySelector('.host-debug-dialog__accordion-command-text')
        ?.textContent,
    ).toContain('ndm devices');
    expect(
      secondSection.querySelector(
        '.host-debug-dialog__error-theme .host-debug-dialog__output-content',
      )?.textContent,
    ).toContain('Error running ndm');
  });

  it('should toggle expand state when command header is clicked', () => {
    const commandHeader = document.querySelector(
      '.host-debug-dialog__panel-header',
    ) as HTMLElement;
    expect(component.expandedCommands()['lsusb']).toBeTrue();

    commandHeader.click();
    expect(component.expandedCommands()['lsusb']).toBeFalse();

    commandHeader.click();
    expect(component.expandedCommands()['lsusb']).toBeTrue();
  });

  it('should toggle expand all commands', () => {
    // Start with all collapsed
    component.expandedCommands.set({
      'lsusb': false,
      'ndm devices': false,
      'dmesg': false,
    });
    fixture.detectChanges();

    const toggleButton = document.querySelector(
      '.host-debug-dialog__action-btn',
    ) as HTMLButtonElement;
    expect(toggleButton.textContent).toContain('Expand All');

    toggleButton.click();
    fixture.detectChanges();

    expect(component.expandedCommands()['lsusb']).toBeTrue();
    expect(component.expandedCommands()['ndm devices']).toBeTrue();
    expect(component.expandedCommands()['dmesg']).toBeTrue();
    expect(toggleButton.textContent).toContain('Collapse All');

    toggleButton.click();
    fixture.detectChanges();

    expect(component.expandedCommands()['lsusb']).toBeFalse();
    expect(component.expandedCommands()['ndm devices']).toBeFalse();
    expect(component.expandedCommands()['dmesg']).toBeFalse();
    expect(toggleButton.textContent).toContain('Expand All');
  });

  it('should show tabs when both stdout and stderr are present', () => {
    const commandSections = document.querySelectorAll(
      '.host-debug-dialog__panel',
    );
    const thirdSection = commandSections[2]; // dmesg

    const tabs = thirdSection.querySelectorAll('.host-debug-dialog__tab-btn');
    expect(tabs.length).toBe(2);
    expect(tabs[0].textContent).toContain('STDOUT');
    expect(tabs[1].textContent).toContain('STDERR');
  });

  it('should switch tabs when tab button is clicked', () => {
    const commandSections = document.querySelectorAll(
      '.host-debug-dialog__panel',
    );
    const thirdSection = commandSections[2]; // dmesg

    const tabs = thirdSection.querySelectorAll(
      '.host-debug-dialog__tab-btn',
    ) as NodeListOf<HTMLButtonElement>;

    // Default should be stdout
    expect(component.activeTabs()['dmesg']).toBe('stdout');

    tabs[1].click(); // Click STDERR
    fixture.detectChanges();
    expect(component.activeTabs()['dmesg']).toBe('stderr');

    tabs[0].click(); // Click STDOUT
    fixture.detectChanges();
    expect(component.activeTabs()['dmesg']).toBe('stdout');
  });

  it('should copy text to clipboard when copy button is clicked', async () => {
    clipboardServiceSpy.copyToClipboard.and.returnValue(true);

    const copyButton = document.querySelector(
      '.host-debug-dialog__copy-cmd-btn',
    ) as HTMLButtonElement;
    copyButton.click();

    await fixture.whenStable();

    expect(clipboardServiceSpy.copyToClipboard).toHaveBeenCalledWith('lsusb');
    expect(snackBarServiceSpy.showInfo).toHaveBeenCalledWith('Command copied');
  });

  it('should refresh debug info and show success snackbar', () => {
    expect(hostServiceSpy.getHostDebugInfo).toHaveBeenCalledTimes(1);

    const buttons = document.querySelectorAll(
      '.host-debug-dialog__footer-btn--outlined',
    );
    const refreshButton = buttons[0] as HTMLButtonElement;

    refreshButton.click();
    fixture.detectChanges();

    expect(hostServiceSpy.getHostDebugInfo).toHaveBeenCalledTimes(2);
    expect(snackBarServiceSpy.showSuccess).toHaveBeenCalledWith(
      'Debug info refreshed successfully',
    );
  });

  it('should keep dialog open and show error snackbar when refresh fails', () => {
    hostServiceSpy.getHostDebugInfo.and.returnValue(
      throwError(() => new Error('Refresh failed')),
    );

    const buttons = document.querySelectorAll(
      '.host-debug-dialog__footer-btn--outlined',
    );
    const refreshButton = buttons[0] as HTMLButtonElement;

    refreshButton.click();
    fixture.detectChanges();

    expect(snackBarServiceSpy.showError).toHaveBeenCalledWith(
      'Failed to refresh debug info for host test-host: Refresh failed',
    );
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('should close dialog if initial load fails', (done) => {
    const errorSubject = new Subject<GetHostDebugInfoResponse>();
    hostServiceSpy.getHostDebugInfo.and.returnValue(errorSubject);

    const failFixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostDebugDialog, {
        data: {hostName: 'test-host'},
      }),
    );
    const failDialogRef = failFixture.componentInstance.dialogRef;

    failDialogRef.afterClosed().subscribe(() => {
      expect(true).toBeTrue();
      done();
    });

    errorSubject.error(new Error('Initial load failed'));
    failFixture.detectChanges(); // triggers ngOnInit error callback
  });

  it('should scroll to the correct command panel when navigateToCommand is called', async () => {
    const mainContent = document.querySelector(
      '.host-debug-dialog__main',
    ) as HTMLElement;
    expect(mainContent).toBeTruthy();

    // Force overflow to enable scrolling in test environment
    mainContent.style.height = '100px';
    mainContent.style.overflowY = 'scroll';
    fixture.detectChanges();

    // Set scrollTop to something else initially
    mainContent.scrollTop = 100;

    // Navigate to 'ndm devices' (second command in mockDebugInfo)
    component.navigateToCommand('ndm devices');

    // Wait for the 100ms timeout in navigateToCommand
    await new Promise<void>((resolve) => {
      setTimeout(resolve, 150);
    });
    fixture.detectChanges();

    const targetPanel = document.getElementById(
      'cmd-ndm-devices',
    ) as HTMLElement;
    expect(targetPanel).toBeTruthy();

    // scrollTop should be set to offsetTop - 16
    expect(mainContent.scrollTop).toBe(targetPanel.offsetTop - 16);
  });
});

describe('normalizeHostDebugInfoResponse', () => {
  it('should not change valid commands', () => {
    const input: GetHostDebugInfoResponse = {
      results: [
        {command: 'lsusb', stdout: 'out1', stderr: 'err1'},
        {command: 'dmesg', stdout: 'out2', stderr: 'err2'},
      ],
      timestamp: '2026-04-03T08:00:00Z',
    };
    const output = normalizeHostDebugInfoResponse(input);
    expect(output).toEqual(input);
  });

  it('should normalize empty or whitespace commands', () => {
    const input: GetHostDebugInfoResponse = {
      results: [
        {command: '', stdout: 'out1', stderr: 'err1'},
        {command: '   ', stdout: 'out2', stderr: 'err2'},
        {command: 'lsusb', stdout: 'out3', stderr: 'err3'},
        {command: '', stdout: 'out4', stderr: 'err4'},
      ],
      timestamp: '2026-04-03T08:00:00Z',
    };
    const output = normalizeHostDebugInfoResponse(input);
    expect(output.results[0].command).toBe('Unknown Command 1');
    expect(output.results[1].command).toBe('Unknown Command 2');
    expect(output.results[2].command).toBe('lsusb');
    expect(output.results[3].command).toBe('Unknown Command 3');
  });

  it('should handle missing command field', () => {
    const input = {
      results: [{stdout: 'out1', stderr: 'err1'}],
      timestamp: '2026-04-03T08:00:00Z',
    };
    const output = normalizeHostDebugInfoResponse(
      input as unknown as GetHostDebugInfoResponse,
    );
    expect(output.results[0].command).toBe('Unknown Command 1');
  });
});
