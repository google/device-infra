import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {ActionNoPermissionContent} from './action_no_permission_content';

describe('ActionNoPermissionContent', () => {
  let component: ActionNoPermissionContent;
  let fixture: ComponentFixture<MatTestDialogOpener<ActionNoPermissionContent>>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ActionNoPermissionContent,
        NoopAnimationsModule,
        MatTestDialogOpenerModule,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(ActionNoPermissionContent),
    );
    fixture.detectChanges();
    component = fixture.componentInstance.dialogRef.componentInstance;
    component.hostName = 'test-host';
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display a no permission message', () => {
    const contentEl = document.querySelector(
      'app-action-no-permission-content',
    );
    expect(contentEl?.textContent).toContain("don't have the permission");
  });

  it('should close dialog when navigating to permissions page', () => {
    const matDialog = TestBed.inject(MatDialog);
    spyOn(matDialog, 'open');
    spyOn(fixture.componentInstance.dialogRef, 'close');
    component.navigateToPermissionsPage();
    expect(fixture.componentInstance.dialogRef.close).toHaveBeenCalled();
  });
});
