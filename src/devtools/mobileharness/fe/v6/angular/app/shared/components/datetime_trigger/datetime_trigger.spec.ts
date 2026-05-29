import {Component, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatMenuModule} from '@angular/material/menu';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {ClipboardService} from '@deviceinfra/app/shared/services/clipboard_service';
import {DatetimeTrigger} from './datetime_trigger';

@Component({
  template: `<app-datetime-trigger [timestamp]="testTimestamp()"></app-datetime-trigger>`,
  imports: [DatetimeTrigger],
  standalone: true,
})
class TestHostComponent {
  readonly testTimestamp = signal(new Date().toISOString());
}

describe('DatetimeTrigger', () => {
  let component: TestHostComponent;
  let fixture: ComponentFixture<TestHostComponent>;
  let childComponent: DatetimeTrigger;
  let clipboardServiceSpy: jasmine.SpyObj<ClipboardService>;

  beforeEach(async () => {
    clipboardServiceSpy = jasmine.createSpyObj('ClipboardService', [
      'copyToClipboard',
    ]);

    await TestBed.configureTestingModule({
      imports: [
        TestHostComponent,
        DatetimeTrigger,
        MatMenuModule,
        NoopAnimationsModule,
      ],
      providers: [{provide: ClipboardService, useValue: clipboardServiceSpy}],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    const childEl = fixture.debugElement.query(By.directive(DatetimeTrigger));
    childComponent = childEl.componentInstance;
  });

  it('should create', () => {
    expect(childComponent).toBeTruthy();
  });

  it('should update relative time periodically when menu is opened', async () => {
    const now = new Date();
    // Set timestamp to 5 seconds ago on host component
    component.testTimestamp.set(new Date(now.getTime() - 5000).toISOString());
    fixture.detectChanges(); // Triggers ngOnChanges on child

    // Initial relative time should be "5 sec ago"
    expect(childComponent.relativeTime()).toBe('5 sec ago');
    const initialVal = childComponent.relativeTime();

    // Open menu (simulate event)
    childComponent.onMenuOpened();
    fixture.detectChanges();

    // Wait for 1.5 seconds (enough for at least one tick of 1s)
    // Wrap arrow function body in curly braces to satisfy linter
    await new Promise<void>((resolve) => {
      setTimeout(resolve, 1500);
    });
    fixture.detectChanges();

    // Relative time should have changed
    const newVal = childComponent.relativeTime();
    expect(newVal).not.toBe(initialVal);

    // Close menu
    childComponent.onMenuClosed();

    // Wait for another 1.5 seconds
    // Wrap arrow function body in curly braces to satisfy linter
    await new Promise<void>((resolve) => {
      setTimeout(resolve, 1500);
    });
    fixture.detectChanges();

    // Relative time should STILL be newVal because timer should be stopped
    expect(childComponent.relativeTime()).toBe(newVal);
  });
});
