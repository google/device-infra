import {OverlayContainer} from '@angular/cdk/overlay';
import {ApplicationRef} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  ListTroubleshootScriptsResponse,
  RunTroubleshootScriptResponse,
  TroubleshootScriptAction,
} from '@deviceinfra/app/core/models/host_action';
import {EnvUniverseService} from '@deviceinfra/app/core/services/env_universe_service';
import {
  HOST_SERVICE,
  HostService,
} from '@deviceinfra/app/core/services/host/host_service';
import {SnackBarService} from '@deviceinfra/app/shared/services/snackbar_service';
import {of, throwError} from 'rxjs';

import {HostAdvancedOpsDialog} from './host_advanced_ops_dialog';

describe('HostAdvancedOpsDialog', () => {
  let component: HostAdvancedOpsDialog;
  let fixture: ComponentFixture<MatTestDialogOpener<HostAdvancedOpsDialog>>;
  let dialogRef: MatDialogRef<HostAdvancedOpsDialog>;
  let hostServiceSpy: jasmine.SpyObj<HostService>;
  let envUniverseServiceSpy: jasmine.SpyObj<EnvUniverseService>;
  let snackBarServiceSpy: jasmine.SpyObj<SnackBarService>;
  let overlayContainer: OverlayContainer;
  let overlayElement: HTMLElement;
  let appRef: ApplicationRef;

  function detectChanges() {
    fixture.detectChanges();
    appRef.tick();
  }

  const mockScripts: TroubleshootScriptAction[] = [
    {
      script: 'RESET_USB_HUB' as const,
      displayName: 'Reset USB Hub',
      description:
        'Power cycle smart USB hub ports to recover missing devices.',
      enabled: true,
      constraintTooltip:
        'Power cycle smart USB hub ports to recover missing devices.',
    },
    {
      script: 'OTHER_SCRIPT' as unknown as TroubleshootScriptAction['script'],
      displayName: 'Other Script utility',
      description: 'Another sample troubleshooting tool.',
      enabled: false,
      constraintTooltip: 'Currently disabled on this host.',
    },
  ];

  const mockListResponse: ListTroubleshootScriptsResponse = {
    actions: mockScripts,
  };

  const mockRunResponse: RunTroubleshootScriptResponse = {
    exitCode: 0,
    stdout: 'Reset completed successfully.',
    stderr: '',
  };

  beforeEach(async () => {
    hostServiceSpy = jasmine.createSpyObj('HostService', [
      'listTroubleshootScripts',
      'runTroubleshootScript',
    ]);
    envUniverseServiceSpy = jasmine.createSpyObj('EnvUniverseService', [
      'getUniverseString',
    ]);
    snackBarServiceSpy = jasmine.createSpyObj('SnackBarService', [
      'showInfo',
      'showError',
      'showSuccess',
    ]);

    hostServiceSpy.listTroubleshootScripts.and.returnValue(
      of(mockListResponse),
    );
    envUniverseServiceSpy.getUniverseString.and.returnValue('prod');

    await TestBed.configureTestingModule({
      imports: [
        HostAdvancedOpsDialog,
        NoopAnimationsModule,
        MatTestDialogOpenerModule,
      ],
      providers: [
        {
          provide: MAT_DIALOG_DATA,
          useValue: {hostName: 'test-host.example.com'},
        },
        {provide: HOST_SERVICE, useValue: hostServiceSpy},
        {provide: EnvUniverseService, useValue: envUniverseServiceSpy},
        {provide: SnackBarService, useValue: snackBarServiceSpy},
      ],
    }).compileComponents();

    overlayContainer = TestBed.inject(OverlayContainer);
    overlayElement = overlayContainer.getContainerElement();
    overlayElement.innerHTML = '';

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostAdvancedOpsDialog, {
        data: {hostName: 'test-host.example.com'},
      }),
    );
    component = fixture.componentInstance.dialogRef.componentInstance;
    dialogRef = fixture.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');

    appRef = TestBed.inject(ApplicationRef);

    detectChanges();
  });

  afterEach(() => {
    overlayElement.innerHTML = '';
  });

  it('should create and fetch scripts on initialization', () => {
    expect(component).toBeTruthy();
    expect(hostServiceSpy.listTroubleshootScripts).toHaveBeenCalledWith(
      'test-host.example.com',
      'prod',
    );
    expect(component.scripts()).toEqual(mockScripts);
    expect(component.currentStep()).toBe(1);
    expect(component.dialogTitle()).toBe('Advanced Operations');
  });

  it('should render script cards in step 1 catalog', () => {
    const cards = overlayElement.querySelectorAll('.script-card');
    expect(cards.length).toBe(2);
    expect(cards[0].textContent).toContain('Reset USB Hub');
    expect(cards[0].textContent).toContain(
      'Power cycle smart USB hub ports to recover missing devices.',
    );
    expect(cards[1].textContent).toContain('Other Script utility');
    expect(cards[1].classList.contains('disabled')).toBeTrue();
  });

  it('should return correct script icons', () => {
    expect(component.getScriptIcon('RESET_USB_HUB')).toBe('usb');
    expect(component.getScriptIcon('ANY_OTHER_SCRIPT')).toBe('build');
  });

  it('should show error snackbar when fetchScripts fails', () => {
    hostServiceSpy.listTroubleshootScripts.and.returnValue(
      throwError(() => new Error('Network error')),
    );
    component.fetchScripts();
    expect(snackBarServiceSpy.showError).toHaveBeenCalledWith(
      'Failed to load scripts: Network error',
    );
    expect(component.isLoadingScripts()).toBeFalse();
  });

  it('should not select a disabled script', () => {
    const disabledScript = mockScripts[1];
    component.selectScript(disabledScript);
    expect(component.selectedScript()).toBeNull();
    expect(component.currentStep()).toBe(1);
  });

  it('should select an enabled script and advance to step 2 confirm', () => {
    const enabledScript = mockScripts[0];
    const cards = overlayElement.querySelectorAll('.script-card');
    (cards[0] as HTMLElement).click();
    detectChanges();

    expect(component.selectedScript()).toEqual(enabledScript);
    expect(component.currentStep()).toBe(2);
    expect(component.dialogTitle()).toBe('Reset USB Hub');

    const warningCard = overlayElement.querySelector('.warning-card');
    expect(warningCard).toBeTruthy();
    expect(
      overlayElement.querySelector('.host-name-text')?.textContent,
    ).toContain('test-host.example.com');
  });

  it('should go back from step 2 to step 1 catalog', () => {
    const cards = overlayElement.querySelectorAll('.script-card');
    (cards[0] as HTMLElement).click();
    detectChanges();
    expect(component.currentStep()).toBe(2);

    const backBtn = overlayElement.querySelector(
      '.dialog-button-secondary',
    ) as HTMLElement;
    backBtn.click();
    detectChanges();
    expect(component.currentStep()).toBe(1);
    expect(component.selectedScript()).toBeNull();
  });

  it('should run operation and advance to step 3 execution terminal on success', () => {
    hostServiceSpy.runTroubleshootScript.and.returnValue(of(mockRunResponse));
    const cards = overlayElement.querySelectorAll('.script-card');
    (cards[0] as HTMLElement).click();
    detectChanges();

    const runBtn = overlayElement.querySelector(
      '.dialog-button-primary',
    ) as HTMLElement;
    runBtn.click();
    detectChanges();

    expect(hostServiceSpy.runTroubleshootScript).toHaveBeenCalledWith(
      'test-host.example.com',
      'RESET_USB_HUB',
      'prod',
    );
    expect(component.currentStep()).toBe(3);
    expect(component.dialogTitle()).toBe('Running: Reset USB Hub');
    expect(component.isExecuting()).toBeFalse();
    expect(component.executionResult()).toEqual(mockRunResponse);

    expect(
      overlayElement.querySelector('.terminal-content')?.textContent,
    ).toContain('Reset completed successfully.');
    expect(overlayElement.querySelector('.status-text.success')).toBeTruthy();
  });

  it('should return empty string for dialogTitle if currentStep or selectedScript is invalid', () => {
    component.currentStep.set(99);
    expect(component.dialogTitle()).toBe('');

    component.currentStep.set(2);
    component.selectedScript.set(null);
    expect(component.dialogTitle()).toBe('');
  });

  it('should display stderr and failure state when operation returns non-zero exit code', () => {
    const errorResult: RunTroubleshootScriptResponse = {
      exitCode: 1,
      stdout: '',
      stderr: 'Failed to reset USB hub ports.',
    };
    hostServiceSpy.runTroubleshootScript.and.returnValue(of(errorResult));
    const cards = overlayElement.querySelectorAll('.script-card');
    (cards[0] as HTMLElement).click();
    detectChanges();

    const runBtn = overlayElement.querySelector(
      '.dialog-button-primary',
    ) as HTMLElement;
    runBtn.click();
    detectChanges();

    expect(component.executionResult()?.exitCode).toBe(1);
    expect(
      overlayElement.querySelector('.terminal-section.error')?.textContent,
    ).toContain('Failed to reset USB hub ports.');
    expect(
      overlayElement.querySelector('.status-text.error')?.textContent,
    ).toContain('Operation failed');
  });

  it('should handle observable error during runOperation', () => {
    hostServiceSpy.runTroubleshootScript.and.returnValue(
      throwError(() => new Error('RPC failure')),
    );
    const cards = overlayElement.querySelectorAll('.script-card');
    (cards[0] as HTMLElement).click();
    detectChanges();

    const runBtn = overlayElement.querySelector(
      '.dialog-button-primary',
    ) as HTMLElement;
    runBtn.click();
    detectChanges();

    expect(snackBarServiceSpy.showError).toHaveBeenCalledWith(
      'Execution failed: RPC failure',
    );
    expect(component.isExecuting()).toBeFalse();
    expect(component.executionResult()).toEqual({
      exitCode: -1,
      stdout: '',
      stderr: 'RPC failure',
    });
  });

  it('should go back from step 3 execution terminal to step 1 catalog', () => {
    hostServiceSpy.runTroubleshootScript.and.returnValue(of(mockRunResponse));
    const cards = overlayElement.querySelectorAll('.script-card');
    (cards[0] as HTMLElement).click();
    detectChanges();

    const runBtn = overlayElement.querySelector(
      '.dialog-button-primary',
    ) as HTMLElement;
    runBtn.click();
    detectChanges();
    expect(component.currentStep()).toBe(3);

    const backToOpsBtn = overlayElement.querySelector(
      '.dialog-button-secondary',
    ) as HTMLElement;
    backToOpsBtn.click();
    detectChanges();
    expect(component.currentStep()).toBe(1);
    expect(component.executionResult()).toBeNull();
  });

  it('should close dialog when not executing', () => {
    component.close();
    expect(dialogRef.close).toHaveBeenCalledTimes(1);
  });

  it('should prevent close when operation is currently executing', () => {
    component.isExecuting.set(true);
    component.close();
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
