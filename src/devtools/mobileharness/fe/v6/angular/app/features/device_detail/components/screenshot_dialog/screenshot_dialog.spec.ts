import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {ScreenshotDialogData} from '../../../../core/models/device_action';
import {dateUtils} from '../../../../shared/utils/date_utils';

import {ScreenshotDialog} from './screenshot_dialog';

describe('ScreenshotDialog', () => {
  let component: ScreenshotDialog;
  let fixture: ComponentFixture<MatTestDialogOpener<ScreenshotDialog>>;
  const dialogData: ScreenshotDialogData = {
    deviceId: 'test-device',
    screenshotUrl: 'http://mock.url/screenshot.png',
    capturedAt: new Date().toISOString(),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ScreenshotDialog,
        NoopAnimationsModule, // This makes test faster and more stable.
        MatTestDialogOpenerModule,
      ],
      providers: [{provide: MAT_DIALOG_DATA, useValue: dialogData}],
    }).compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(ScreenshotDialog, {data: dialogData}),
    );
    fixture.detectChanges();
    component = fixture.componentInstance.dialogRef.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should format timestamp for display', () => {
    const timestamp = new Date(dialogData.capturedAt);
    const formatted = component.getDisplayTimestamp();
    expect(formatted).toContain(timestamp.getFullYear().toString());
  });

  it('should format timestamp for filenames', () => {
    const timestamp = new Date();
    const formatted = dateUtils.formatFileTimestamp(timestamp);
    const YYYY = timestamp.getFullYear();
    const MM = String(timestamp.getMonth() + 1).padStart(2, '0');
    const DD = String(timestamp.getDate()).padStart(2, '0');
    expect(formatted).toContain(`${YYYY}${MM}${DD}`);
  });
});
