import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter, Router} from '@angular/router';
import {of, throwError} from 'rxjs';

import {
  HOST_SERVICE,
  HostService,
} from '@deviceinfra/app/core/services/host/host_service';
import {SnackBarService} from '@deviceinfra/app/shared/services/snackbar_service';

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

  it('should render remove wording and host name', () => {
    const dialogContainer = document.querySelector('.confirm-dialog-container');
    expect(dialogContainer?.textContent).toContain('Remove Missing Host?');
    expect(dialogContainer?.textContent).toContain(
      'Removing it will permanently delete its record from OmniLab for host:',
    );
    expect(dialogContainer?.textContent).not.toContain('Decommission');

    const hostNameElement = document.querySelector('.target-host-value');
    expect(hostNameElement?.textContent).toContain('test-host');

    const operationBadge = document.querySelector('.summary-card-badge');
    expect(operationBadge?.textContent?.trim()).toBe('Remove');

    const primaryButton = document.querySelector('.primary-button');
    expect(primaryButton?.textContent).toContain('Remove');
  });

  it('should call decommissionHost and show success state when remove button is clicked', () => {
    hostServiceSpy.decommissionHost.and.returnValue(of({}));

    const removeButton = document.querySelector(
      '.primary-button',
    ) as HTMLButtonElement;
    removeButton.click();
    fixture.detectChanges();

    expect(hostServiceSpy.decommissionHost).toHaveBeenCalledWith('test-host');
    expect(component.isDecommissioned()).toBeTrue();
    expect(dialogRef.disableClose).toBeTrue();
    expect(router.navigate).not.toHaveBeenCalled();
    expect(dialogRef.close).not.toHaveBeenCalled();

    const dialogContainer = document.querySelector('.confirm-dialog-container');
    expect(dialogContainer?.textContent).toContain('Removed Successfully');
    expect(dialogContainer?.textContent).toContain(
      'has been removed successfully!',
    );
  });

  it('should show error snackbar when decommissionHost fails', () => {
    hostServiceSpy.decommissionHost.and.returnValue(
      throwError(() => new Error('RPC failed')),
    );

    const removeButton = document.querySelector(
      '.primary-button',
    ) as HTMLButtonElement;
    removeButton.click();

    expect(snackBarServiceSpy.showError).toHaveBeenCalledWith(
      'Failed to remove host test-host: RPC failed',
    );
    expect(component.isDecommissioning()).toBeFalse();
    expect(component.isDecommissioned()).toBeFalse();
  });

  it('should close with false when cancel button is clicked', () => {
    const cancelButton = document.querySelector(
      '.secondary-button',
    ) as HTMLButtonElement;
    cancelButton.click();
    expect(dialogRef.close).toHaveBeenCalledWith(false);
  });
});
