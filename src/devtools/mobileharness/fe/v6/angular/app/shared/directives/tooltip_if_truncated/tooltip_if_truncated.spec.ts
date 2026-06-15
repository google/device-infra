import {Component, Input} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatTooltip, MatTooltipModule} from '@angular/material/tooltip';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TooltipIfTruncatedDirective} from './tooltip_if_truncated';

@Component({
  template: `
    <div
      id="test-target"
      style="width: 50px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; display: block;"
      [matTooltip]="tooltipText"
      tooltipIfTruncated>
      {{elementText}}
    </div>
  `,
  standalone: true,
  imports: [TooltipIfTruncatedDirective, MatTooltipModule],
})
class TestComponent {
  tooltipText = 'Full text that is long';
  @Input() elementText = 'Short'; // Start short, no truncation
}

describe('TooltipIfTruncatedDirective', () => {
  let fixture: ComponentFixture<TestComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(TestComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.nativeElement.remove();
  });

  it('should disable tooltip when text is NOT truncated (mouseenter)', () => {
    const div = fixture.debugElement.query(By.css('#test-target'));

    // Trigger mouseenter to run the directive logic
    div.triggerEventHandler('mouseenter', null);
    fixture.detectChanges();

    const tooltip = div.injector.get(MatTooltip);
    expect(tooltip.disabled).toBeTrue();
  });

  it('should disable tooltip when text is NOT truncated (focusin)', () => {
    const div = fixture.debugElement.query(By.css('#test-target'));

    div.triggerEventHandler('focusin', null);
    fixture.detectChanges();

    const tooltip = div.injector.get(MatTooltip);
    expect(tooltip.disabled).toBeTrue();
  });

  it('should enable tooltip when text IS truncated (mouseenter)', async () => {
    // Make element text long so it truncates
    fixture.componentRef.setInput(
      'elementText',
      'Very long text that will definitely truncate in 50px',
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const div = fixture.debugElement.query(By.css('#test-target'));

    div.triggerEventHandler('mouseenter', null);
    fixture.detectChanges();

    const tooltip = div.injector.get(MatTooltip);
    expect(tooltip.disabled).toBeFalse();
  });

  it('should enable tooltip when text IS truncated (focusin)', async () => {
    fixture.componentRef.setInput(
      'elementText',
      'Very long text that will definitely truncate in 50px',
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const div = fixture.debugElement.query(By.css('#test-target'));

    div.triggerEventHandler('focusin', null);
    fixture.detectChanges();

    const tooltip = div.injector.get(MatTooltip);
    expect(tooltip.disabled).toBeFalse();
  });
});
