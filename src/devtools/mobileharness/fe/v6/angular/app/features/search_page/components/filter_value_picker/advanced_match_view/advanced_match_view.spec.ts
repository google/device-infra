import {Component, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {AdvancedMatchMode, IntComparisonOp} from '../../../models';

import {AdvancedMatchView} from './advanced_match_view';

@Component({
  standalone: true,
  imports: [AdvancedMatchView],
  template: `
    <app-advanced-match-view
      [canCompareInt]="canCompareInt()"
      [(mode)]="mode"
      [(text)]="text"
      [(values)]="values"
      [(intOp)]="intOp"
      (backToSimple)="onBackToSimple()"
      (apply)="onApply()"
    />
  `,
})
class TestHostComponent {
  canCompareInt = signal<boolean>(false);
  mode = signal<AdvancedMatchMode>('prefix');
  text = signal<string>('test_prefix');
  values = signal<string[]>(['val1', 'val2']);
  intOp = signal<IntComparisonOp>('gt');

  backToSimpleCalled = false;
  applyCalled = false;

  onBackToSimple() {
    this.backToSimpleCalled = true;
  }

  onApply() {
    this.applyCalled = true;
  }
}

describe('AdvancedMatchView', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;
  let component: AdvancedMatchView;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    host = fixture.componentInstance;
    component = fixture.debugElement.query(
      By.directive(AdvancedMatchView),
    ).componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should render mode options and select new mode on click', () => {
    const modes = fixture.debugElement.queryAll(By.css('.vp-adv-mode'));
    expect(modes.length).toBeGreaterThan(0);

    // Find and click 'Matches regex' mode
    const regexMode = modes.find((el) =>
      el.nativeElement.textContent.includes('Matches regex'),
    );
    expect(regexMode).toBeDefined();
    regexMode?.nativeElement.click();
    fixture.detectChanges();

    expect(host.mode()).toBe('regex');
  });

  it('should emit backToSimple when back button clicked', () => {
    const backBtn = fixture.debugElement.query(By.css('.vp-adv-back'));
    backBtn.nativeElement.click();
    expect(host.backToSimpleCalled).toBeTrue();
  });

  it('should render text input for single value modes and update model', () => {
    host.mode.set('prefix');
    fixture.detectChanges();

    const input = fixture.debugElement.query(
      By.css('.vp-adv-field'),
    ).nativeElement;
    expect(input.value).toBe('test_prefix');

    input.value = 'new_prefix';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(host.text()).toBe('new_prefix');
  });

  it('should switch to multi-chip mode for exactly and at_least modes', () => {
    host.mode.set('exactly');
    fixture.detectChanges();

    expect(component.isMultiMode()).toBeTrue();

    const chips = fixture.debugElement.queryAll(By.css('.vp-adv-chip'));
    expect(chips.length).toBe(2);
    expect(chips[0].nativeElement.textContent).toContain('val1');
    expect(chips[1].nativeElement.textContent).toContain('val2');
  });

  it('should add and remove chips in multi-mode', () => {
    host.mode.set('exactly');
    fixture.detectChanges();

    component.inputVal.set('val3');
    component.addChip();
    fixture.detectChanges();

    expect(host.values()).toEqual(['val1', 'val2', 'val3']);
    expect(component.inputVal()).toBe('');

    // Removing first chip
    component.removeChip(0);
    fixture.detectChanges();

    expect(host.values()).toEqual(['val2', 'val3']);
  });

  it('should not add duplicate chips in multi-mode', () => {
    host.mode.set('exactly');
    fixture.detectChanges();

    component.inputVal.set('val1');
    component.addChip();
    fixture.detectChanges();

    expect(host.values()).toEqual(['val1', 'val2']);
  });

  it('should migrate text into values when switching from single to multi-mode', () => {
    host.mode.set('prefix');
    host.text.set('migrated_text');
    host.values.set([]);
    fixture.detectChanges();

    component.selectMode('exactly');
    fixture.detectChanges();

    expect(host.mode()).toBe('exactly');
    expect(host.values()).toEqual(['migrated_text']);
  });

  it('should migrate first value into text when switching from multi to single-mode', () => {
    host.mode.set('exactly');
    host.text.set('');
    host.values.set(['val_a', 'val_b']);
    fixture.detectChanges();

    component.selectMode('substring');
    fixture.detectChanges();

    expect(host.mode()).toBe('substring');
    expect(host.text()).toBe('val_a');
  });

  it('should no-op when selecting the same mode', () => {
    host.mode.set('prefix');
    host.text.set('existing');
    fixture.detectChanges();

    component.selectMode('prefix');
    fixture.detectChanges();

    expect(host.mode()).toBe('prefix');
    expect(host.text()).toBe('existing');
  });

  it('should not add empty value when switching from single to multi-mode with empty text', () => {
    host.mode.set('prefix');
    host.text.set('   ');
    host.values.set([]);
    fixture.detectChanges();

    component.selectMode('exactly');
    fixture.detectChanges();

    expect(host.mode()).toBe('exactly');
    expect(host.values()).toEqual([]);
  });

  it('should not overwrite existing values when switching from single to multi-mode', () => {
    host.mode.set('prefix');
    host.text.set('migrated_text');
    host.values.set(['already_present']);
    fixture.detectChanges();

    component.selectMode('at_least');
    fixture.detectChanges();

    expect(host.mode()).toBe('at_least');
    expect(host.values()).toEqual(['already_present']);
  });

  it('should preserve existing text when switching from multi to single-mode', () => {
    host.mode.set('exactly');
    host.text.set('already_has_text');
    host.values.set(['val_a', 'val_b']);
    fixture.detectChanges();

    component.selectMode('regex');
    fixture.detectChanges();

    expect(host.mode()).toBe('regex');
    expect(host.text()).toBe('already_has_text');
  });

  it('should not set text when switching from multi to single-mode with empty values', () => {
    host.mode.set('exactly');
    host.text.set('');
    host.values.set([]);
    fixture.detectChanges();

    component.selectMode('not_regex');
    fixture.detectChanges();

    expect(host.mode()).toBe('not_regex');
    expect(host.text()).toBe('');
  });

  it('should change mode without touching text or values when switching between single modes', () => {
    host.mode.set('prefix');
    host.text.set('test_text');
    host.values.set([]);
    fixture.detectChanges();

    component.selectMode('substring');
    fixture.detectChanges();

    expect(host.mode()).toBe('substring');
    expect(host.text()).toBe('test_text');
    expect(host.values()).toEqual([]);
  });

  it('should change mode without touching values or setting text when switching between multi modes', () => {
    host.mode.set('exactly');
    host.text.set('');
    host.values.set(['val1', 'val2']);
    fixture.detectChanges();

    component.selectMode('at_least');
    fixture.detectChanges();

    expect(host.mode()).toBe('at_least');
    expect(host.values()).toEqual(['val1', 'val2']);
    expect(host.text()).toBe('');
  });

  it('should not migrate text to values when switching between multi modes even if values is empty', () => {
    host.mode.set('exactly');
    host.text.set('some_text');
    host.values.set([]);
    fixture.detectChanges();

    component.selectMode('at_least');
    fixture.detectChanges();

    expect(host.mode()).toBe('at_least');
    expect(host.values()).toEqual([]);
    expect(host.text()).toBe('some_text');
  });

  it('hides Compare integer mode when canCompareInt is false and shows it first when true', () => {
    host.canCompareInt.set(false);
    fixture.detectChanges();

    let modes = fixture.debugElement.queryAll(By.css('.vp-adv-mode'));
    expect(
      modes.some((el) =>
        el.nativeElement.textContent.includes('Compare integer'),
      ),
    ).toBeFalse();

    host.canCompareInt.set(true);
    fixture.detectChanges();

    modes = fixture.debugElement.queryAll(By.css('.vp-adv-mode'));
    expect(modes[0].nativeElement.textContent).toContain('Compare integer');

    // Reopen fallback: when canCompareInt is false but mode is already 'compare_int',
    // Compare integer remains visible until the user switches to another mode.
    host.canCompareInt.set(false);
    host.mode.set('compare_int');
    fixture.detectChanges();

    modes = fixture.debugElement.queryAll(By.css('.vp-adv-mode'));
    expect(modes[0].nativeElement.textContent).toContain('Compare integer');

    component.selectMode('prefix');
    fixture.detectChanges();

    modes = fixture.debugElement.queryAll(By.css('.vp-adv-mode'));
    expect(
      modes.some((el) =>
        el.nativeElement.textContent.includes('Compare integer'),
      ),
    ).toBeFalse();
  });

  it('renders connected segmented operator and validates integer input in compare_int mode', () => {
    host.canCompareInt.set(true);
    host.mode.set('compare_int');
    host.text.set('20');
    fixture.detectChanges();

    const opButtons = fixture.debugElement.queryAll(By.css('.vp-adv-int-op'));
    expect(opButtons.length).toBe(4);
    expect(opButtons[0].nativeElement.classList.contains('active')).toBeTrue();
    expect(opButtons[0].nativeElement.getAttribute('aria-label')).toBe(
      'Greater than (>)',
    );

    // Select '≤' ('lte')
    opButtons[3].nativeElement.click();
    fixture.detectChanges();
    expect(host.intOp()).toBe('lte');
    expect(opButtons[3].nativeElement.classList.contains('active')).toBeTrue();

    const intInputDebug = fixture.debugElement.query(
      By.css('.vp-adv-int-field'),
    );
    const intInput = intInputDebug.nativeElement as HTMLInputElement;
    expect(intInput.classList.contains('invalid')).toBeFalse();
    expect(intInput.getAttribute('aria-invalid')).toBe('false');

    intInputDebug.triggerEventHandler('keydown.enter', new KeyboardEvent('keydown', {key: 'Enter'}));
    expect(host.applyCalled).toBeTrue();

    host.text.set('20.5');
    fixture.detectChanges();
    expect(component.isInvalidInt()).toBeTrue();
    expect(intInput.classList.contains('invalid')).toBeTrue();
    expect(intInput.getAttribute('aria-invalid')).toBe('true');
  });
});
