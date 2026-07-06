import {ChangeDetectionStrategy, Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {AccordionItem} from './accordion_item';

@Component({
  standalone: true,
  imports: [AccordionItem],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-accordion-item
      [title]="title"
      [defaultExpanded]="defaultExpanded"
      [customId]="customId"
    >
      <p class="projected-content">Detail content here.</p>
    </app-accordion-item>
  `,
})
class TestHostComponent {
  title = 'Test Accordion Item';
  defaultExpanded = false;
  customId = 'test-item-1';
}

describe('AccordionItem Component', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let component: AccordionItem;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccordionItem, TestHostComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();
    component = fixture.debugElement.query(
      By.directive(AccordionItem),
    ).componentInstance;
  });

  it('should create and render initial state', () => {
    expect(component).toBeTruthy();
    const trigger = fixture.debugElement.query(By.css('.accordion-trigger'));
    expect(trigger.nativeElement.textContent).toContain('Test Accordion Item');
    expect(component.expanded()).toBeFalse();
    expect(fixture.debugElement.query(By.css('.projected-content'))).toBeNull();
  });

  it('should toggle expansion when trigger is clicked', () => {
    const trigger = fixture.debugElement.query(By.css('.accordion-trigger'));
    trigger.nativeElement.click();
    fixture.detectChanges();

    expect(component.expanded()).toBeTrue();
    expect(
      fixture.debugElement.query(By.css('.projected-content')),
    ).not.toBeNull();

    trigger.nativeElement.click();
    fixture.detectChanges();
    expect(component.expanded()).toBeFalse();
  });

  it('should set ARIA attributes correctly', () => {
    const trigger = fixture.debugElement.query(By.css('.accordion-trigger'));
    expect(trigger.nativeElement.getAttribute('aria-expanded')).toBe('false');
    expect(trigger.nativeElement.getAttribute('aria-controls')).toBe(
      'test-item-1',
    );

    trigger.nativeElement.click();
    fixture.detectChanges();
    expect(trigger.nativeElement.getAttribute('aria-expanded')).toBe('true');

    const panel = fixture.debugElement.query(By.css('.accordion-panel'));
    expect(panel.nativeElement.getAttribute('id')).toBe('test-item-1');
    expect(panel.nativeElement.getAttribute('role')).toBe('region');
    expect(panel.nativeElement.getAttribute('aria-labelledby')).toBe(
      'test-item-1-trigger',
    );
  });
});
