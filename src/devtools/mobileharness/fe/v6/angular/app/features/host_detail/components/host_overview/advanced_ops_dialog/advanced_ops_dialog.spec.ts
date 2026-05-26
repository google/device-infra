import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatTestDialogOpener} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of, throwError} from 'rxjs';

import {
  ListTroubleshootScriptsResponse,
  RunTroubleshootScriptResponse,
  TroubleshootScriptAction,
} from '../../../../../core/models/host_action';
import {
  HOST_SERVICE,
  HostService,
} from '../../../../../core/services/host/host_service';
import {AdvancedOpsDialog, AdvancedOpsDialogData} from './advanced_ops_dialog';

describe('AdvancedOpsDialog', () => {
  let component: AdvancedOpsDialog;
  let fixture: ComponentFixture<MatTestDialogOpener<AdvancedOpsDialog>>;
  let opener: MatTestDialogOpener<AdvancedOpsDialog>;
  let hostService: jasmine.SpyObj<HostService>;

  const dialogData: AdvancedOpsDialogData = {
    hostName: 'test-host',
    universe: 'google_1p',
  };

  const mockActions: ListTroubleshootScriptsResponse = {
    actions: [
      {
        script: 'RESET_USB_HUB',
        displayName: 'Reset USB Hub',
        description: 'Power cycle the USB hub',
        enabled: true,
        constraintTooltip: '',
      },
    ],
  };

  beforeEach(async () => {
    hostService = jasmine.createSpyObj('HOST_SERVICE', [
      'listTroubleshootScripts',
      'runTroubleshootScript',
    ]);
    hostService.listTroubleshootScripts.and.returnValue(of(mockActions));

    await TestBed.configureTestingModule({
      imports: [NoopAnimationsModule],
      providers: [{provide: HOST_SERVICE, useValue: hostService}],
    }).compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(AdvancedOpsDialog, {data: dialogData}),
    );
    opener = fixture.componentInstance;
    component = opener.dialogRef.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load actions', () => {
    expect(component).toBeTruthy();
    expect(hostService.listTroubleshootScripts).toHaveBeenCalledWith(
      'test-host',
      'google_1p',
    );
    expect(component.actions()).toEqual(mockActions.actions!);
    expect(component.selectedAction()).toEqual(mockActions.actions![0]);
    expect(component.isLoading()).toBeFalse();
  });

  it('should handle load actions failure', () => {
    hostService.listTroubleshootScripts.and.returnValue(
      throwError(() => new Error('Failed to load')),
    );

    // Create a new fixture to re-trigger ngOnInit
    const failFixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(AdvancedOpsDialog, {data: dialogData}),
    );
    const failComponent =
      failFixture.componentInstance.dialogRef.componentInstance;
    failFixture.detectChanges();

    expect(failComponent.actions()).toEqual([]);
    expect(failComponent.errorMessage()).toBe('Failed to load');
    expect(failComponent.isLoading()).toBeFalse();
  });

  it('should select action and reset states', () => {
    const newAction: TroubleshootScriptAction = {
      script: 'UNKNOWN',
      displayName: 'Other Action',
      description: 'Desc',
      enabled: true,
      constraintTooltip: '',
    };
    component.errorMessage.set('error');
    component.logs.set(['log']);
    component.executionSuccess.set(true);

    component.selectAction(newAction);

    expect(component.selectedAction()).toEqual(newAction);
    expect(component.errorMessage()).toBe('');
    expect(component.logs()).toEqual([]);
    expect(component.executionSuccess()).toBeFalse();
  });

  it('should not select action if executing', () => {
    const newAction: TroubleshootScriptAction = {
      script: 'UNKNOWN',
      displayName: 'Other Action',
      description: 'Desc',
      enabled: true,
      constraintTooltip: '',
    };
    component.isExecuting.set(true);
    component.selectedAction.set(mockActions.actions![0]);

    component.selectAction(newAction);

    expect(component.selectedAction()).toEqual(mockActions.actions![0]);
  });

  it('should execute script successfully', () => {
    const runResponse: RunTroubleshootScriptResponse = {
      exitCode: 0,
      stdout: 'success stdout',
      stderr: 'success stderr',
    };
    hostService.runTroubleshootScript.and.returnValue(of(runResponse));

    component.executeScript(mockActions.actions![0]);

    expect(hostService.runTroubleshootScript).toHaveBeenCalledWith(
      'test-host',
      'RESET_USB_HUB',
      {},
      'google_1p',
    );
    expect(component.isExecuting()).toBeFalse();
    expect(component.executionSuccess()).toBeTrue();
    expect(component.logs()).toContain('[Exit Code]: 0');
    expect(component.logs()).toContain('success stdout');
    expect(component.logs()).toContain('success stderr');
  });

  it('should handle execute script failure response (non-zero exit code)', () => {
    const runResponse: RunTroubleshootScriptResponse = {
      exitCode: 1,
      stdout: 'failed stdout',
      stderr: 'failed stderr',
    };
    hostService.runTroubleshootScript.and.returnValue(of(runResponse));

    component.executeScript(mockActions.actions![0]);

    expect(component.isExecuting()).toBeFalse();
    expect(component.executionSuccess()).toBeFalse();
    expect(component.errorMessage()).toBe(
      'Script returned a non-zero exit code.',
    );
    expect(component.logs()).toContain('[Exit Code]: 1');
    expect(component.logs()).toContain('failed stdout');
    expect(component.logs()).toContain('failed stderr');
  });

  it('should handle execute script RPC failure', () => {
    hostService.runTroubleshootScript.and.returnValue(
      throwError(() => new Error('RPC error')),
    );

    component.executeScript(mockActions.actions![0]);

    expect(component.isExecuting()).toBeFalse();
    expect(component.executionSuccess()).toBeFalse();
    expect(component.errorMessage()).toBe('RPC error');
    expect(component.logs()).toContain(
      '[Lab Console] ERROR: RPC invocation failed.',
    );
    expect(component.logs()).toContain('RPC error');
  });
});
