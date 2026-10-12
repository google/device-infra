import {Component, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {Dialog} from './dialog';

@Component({
  standalone: true,
  imports: [Dialog],
  template: `
    <app-dialog
      [dialogTitle]="title()"
      [subtitle]="subtitle()"
      [showHeader]="showHeader()"
      [footerType]="'none'"
    >
      <div dialog-header class="projected-custom-header">Custom Header Content</div>
      <div class="dialog-body-content">Body Content</div>
    </app-dialog>
  `,
})
class TestHostComponent {
  readonly title = signal<string>('My Test Title');
  readonly subtitle = signal<string>('My Test Subtitle');
  readonly showHeader = signal<boolean>(true);
}

describe('Dialog Component', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        Dialog,
        TestHostComponent,
        NoopAnimationsModule,
        MatDialogModule,
        MatTestDialogOpenerModule,
      ],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created with MatTestDialogOpener', () => {
    const comp = TestBed.createComponent(
      MatTestDialogOpener.withComponent(Dialog, {
        data: {
          title: 'Test Title',
          content: 'Test Content',
        },
      }),
    );
    expect(comp).toBeTruthy();
  });

  it('renders default header with title, subtitle, and close button', () => {
    const titleEl = fixture.nativeElement.querySelector('.config-title');
    const subtitleEl = fixture.nativeElement.querySelector('.config-subtitle');
    const headerEl = fixture.nativeElement.querySelector('.config-header');
    const closeBtn = fixture.nativeElement.querySelector('.close-button');

    expect(titleEl?.textContent?.trim()).toBe('My Test Title');
    expect(subtitleEl?.textContent?.trim()).toBe('My Test Subtitle');
    expect(headerEl).toBeTruthy();
    expect(closeBtn).toBeTruthy();
  });

  it('hides header completely when showHeader is false', () => {
    host.showHeader.set(false);
    fixture.detectChanges();

    const headerEl = fixture.nativeElement.querySelector('.config-header');
    expect(headerEl).toBeFalsy();
  });

  it('projects custom content alongside dialog', () => {
    const customHeaderEl = fixture.nativeElement.querySelector(
      '.projected-custom-header',
    );
    const bodyEl = fixture.nativeElement.querySelector('.dialog-body-content');

    expect(customHeaderEl).toBeTruthy();
    expect(customHeaderEl?.textContent?.trim()).toBe('Custom Header Content');
    expect(bodyEl).toBeTruthy();
    expect(bodyEl?.textContent?.trim()).toBe('Body Content');
  });
});
