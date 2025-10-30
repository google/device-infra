import {TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {MatTestDialogOpener} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {ConfirmDialog} from './confirm_dialog';

describe('ConfirmDialog Component', () => {
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            ConfirmDialog,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          teardown: {destroyAfterEach: true},
          providers:
              [provideRouter([]), {provide: MAT_DIALOG_DATA, useValue: {}}],
        })
        .compileComponents();
  });

  it('should render the dialog correctly for info type', () => {
    const fixture = TestBed.createComponent(
        MatTestDialogOpener.withComponent(ConfirmDialog, {
          data: {
            title: 'Test Title',
            content: 'Test Content',
            type: 'info',
            primaryButtonLabel: 'Test Primary Button',
            secondaryButtonLabel: 'Test Secondary Button',
          },
        }),
    );
    fixture.detectChanges();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the dialog correctly for success type', () => {
    const fixture = TestBed.createComponent(
        MatTestDialogOpener.withComponent(ConfirmDialog, {
          data: {
            title: 'Test Title',
            content: 'Test Content',
            type: 'success',
            primaryButtonLabel: 'Test Primary Button',
            secondaryButtonLabel: 'Test Secondary Button',
          },
        }),
    );
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the dialog correctly for warning type', () => {
    const fixture = TestBed.createComponent(
        MatTestDialogOpener.withComponent(ConfirmDialog, {
          data: {
            title: 'Test Title',
            content: 'Test Content',
            type: 'warning',
            primaryButtonLabel: 'Test Primary Button',
            secondaryButtonLabel: 'Test Secondary Button',
          },
        }),
    );
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the dialog correctly for error type', () => {
    const fixture = TestBed.createComponent(
        MatTestDialogOpener.withComponent(ConfirmDialog, {
          data: {
            title: 'Test Title',
            content: 'Test Content',
            type: 'error',
            primaryButtonLabel: 'Test Primary Button',
            secondaryButtonLabel: 'Test Secondary Button',
          },
        }),
    );
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });
});
