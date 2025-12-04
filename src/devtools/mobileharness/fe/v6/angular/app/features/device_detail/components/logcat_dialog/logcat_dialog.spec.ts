import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {LogcatDialogData} from '../../../../core/models/device_action';

import {LogcatDialog} from './logcat_dialog';

describe('LogcatDialog', () => {
  let component: LogcatDialog;
  let fixture: ComponentFixture<MatTestDialogOpener<LogcatDialog>>;
  const dialogData: LogcatDialogData = {
    deviceId: 'test-device',
    logContent: 'line 1\nline 2-test\nline 3',
    capturedAt: new Date().toISOString(),
    logUrl: 'http://mock.url/log.txt',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        LogcatDialog,
        NoopAnimationsModule, // This makes test faster and more stable.
        MatTestDialogOpenerModule,
      ],
      providers: [{provide: MAT_DIALOG_DATA, useValue: dialogData}],
    }).compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(LogcatDialog, {data: dialogData}),
    );
    fixture.detectChanges();
    component = fixture.componentInstance.dialogRef.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize with all log lines', () => {
    expect(component.allLogLines).toEqual(['line 1', 'line 2-test', 'line 3']);
    expect(component.filteredLogLines()).toEqual([
      'line 1',
      'line 2-test',
      'line 3',
    ]);
  });

  it('should filter logs based on search term', () => {
    component.searchTerm = 'test';
    component.filterLogs();
    fixture.detectChanges();
    expect(component.filteredLogLines()).toEqual(['line 2-test']);
  });

  it('should show all logs when search term is cleared', () => {
    component.searchTerm = 'test';
    component.filterLogs();
    fixture.detectChanges();

    component.searchTerm = '';
    component.filterLogs();
    fixture.detectChanges();
    expect(component.filteredLogLines()).toEqual([
      'line 1',
      'line 2-test',
      'line 3',
    ]);
  });
});
