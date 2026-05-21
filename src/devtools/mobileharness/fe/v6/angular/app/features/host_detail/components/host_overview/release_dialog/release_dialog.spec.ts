import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA, MatDialog} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {DeployableVersion} from '../../../../../core/models/host_action';
import {SnackBarService} from '../../../../../shared/services/snackbar_service';
import {ReleaseDialog, ReleaseDialogData} from './release_dialog';

describe('ReleaseDialog', () => {
  let component: ReleaseDialog;
  let fixture: ComponentFixture<ReleaseDialog>;

  const dialogData: ReleaseDialogData = {
    hostName: 'test-host',
    passThroughFlags: signal(''),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReleaseDialog, NoopAnimationsModule],
      providers: [
        {provide: MAT_DIALOG_DATA, useValue: dialogData},
        {
          provide: SnackBarService,
          useValue: jasmine.createSpyObj('SnackBarService', [
            'showSuccess',
            'showError',
          ]),
        },
        {
          provide: MatDialog,
          useValue: jasmine.createSpyObj('MatDialog', ['open']),
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ReleaseDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should format available versions with v prefix', () => {
    const versions: DeployableVersion[] = [
      {version: '1.0.0', name: 'v1', status: 'LATEST', buildTime: '2026-05-14'},
    ];
    component.availableVersions.set(versions);

    const formatted = component.formattedAvailableVersions();
    expect(formatted.length).toEqual(1);
    expect(formatted[0].version).toEqual('v1.0.0');
  });

  it('should handle version segments of different lengths', () => {
    component.selectedVersion.set({
      version: 'v1.1',
      name: 'v1',
      status: 'LATEST',
      buildTime: '2026-05-14',
    });
    component.availableVersions.set([
      {
        version: '1.1.0',
        name: 'v2',
        status: 'CURRENT',
        buildTime: '2026-05-14',
      },
    ]);

    const delta = component.versionDeltaInfo();
    expect(delta.type).toEqual('redeploy');
  });

  it('should handle version difference for line 126 coverage', () => {
    component.selectedVersion.set({
      version: 'v1.2.0',
      name: 'v1',
      status: 'LATEST',
      buildTime: '2026-05-14',
    });
    component.availableVersions.set([
      {
        version: '1.1.0',
        name: 'v2',
        status: 'CURRENT',
        buildTime: '2026-05-14',
      },
    ]);

    const delta = component.versionDeltaInfo();
    expect(delta.type).toEqual('upgrade');
  });
});
