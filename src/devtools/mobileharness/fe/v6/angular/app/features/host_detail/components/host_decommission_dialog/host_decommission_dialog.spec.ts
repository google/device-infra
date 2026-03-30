import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter, Router} from '@angular/router';
import {of} from 'rxjs';

import {
  HOST_SERVICE,
  HostService,
} from 'app/core/services/host/host_service';
import {SnackBarService} from 'app/shared/services/snackbar_service';

import {HostDecommissionDialog} from './host_decommission_dialog';

describe('HostDecommissionDialog', () => {
  let component: HostDecommissionDialog;
  let fixture: ComponentFixture<MatTestDialogOpener<HostDecommissionDialog>>;
  let dialogRef: MatDialogRef<HostDecommissionDialog>;
  let hostServiceSpy: jasmine.SpyObj<HostService>;
  let snackBarServiceSpy: jasmine.SpyObj<SnackBarService>;
  let router: Router;

  beforeEach(async () => {
    // No manual dialogRefSpy needed when using MatTestDialogOpener
    hostServiceSpy = jasmine.createSpyObj('HostService', ['decommissionHost']);
    snackBarServiceSpy = jasmine.createSpyObj('SnackBarService', [
      'showInfo',
      'showError',
    ]);
    // No manual routerSpy needed when using provideRouter

    await TestBed.configureTestingModule({
      imports: [
        HostDecommissionDialog,
        NoopAnimationsModule,
        MatTestDialogOpenerModule,
      ],
      providers: [
        {provide: MAT_DIALOG_DATA, useValue: {hostName: 'test-host'}},
        {provide: HOST_SERVICE, useValue: hostServiceSpy},
        {provide: SnackBarService, useValue: snackBarServiceSpy},
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostDecommissionDialog, {
        data: {hostName: 'test-host'},
      }),
    );
    component = fixture.componentInstance.dialogRef.componentInstance;
    dialogRef = fixture.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show the host name', () => {
    const hostNameElement = document.querySelector('.target-host-value');
    expect(hostNameElement?.textContent).toContain('test-host');
  });

  it('should call decommissionHost and close with true when decommission button is clicked', () => {
    hostServiceSpy.decommissionHost.and.returnValue(of({}));
    const decommissionButton = document.querySelector(
      '.primary-button',
    ) as HTMLButtonElement;
    decommissionButton.click();
    expect(hostServiceSpy.decommissionHost).toHaveBeenCalledWith('test-host');
    expect(snackBarServiceSpy.showInfo).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/'], {
      queryParamsHandling: 'preserve',
    });
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('should close with false when cancel button is clicked', () => {
    const cancelButton = document.querySelector(
      '.secondary-button',
    ) as HTMLButtonElement;
    cancelButton.click();
    expect(dialogRef.close).toHaveBeenCalledWith(false);
  });
});
