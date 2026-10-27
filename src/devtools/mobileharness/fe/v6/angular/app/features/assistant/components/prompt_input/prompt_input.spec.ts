import {ChangeDetectionStrategy, Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {ConversationMode} from '../../../../core/models/assistant';
import {PromptInputComponent} from './prompt_input';

@Component({
  standalone: true,
  imports: [PromptInputComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-prompt-input
    [isStreaming]="isStreaming"
    [mode]="mode"
    [disabled]="disabled"
    (submitPrompt)="onSubmitPrompt($event)"
    (stopRequested)="onStopRequested()"
    (modeChanged)="onModeChanged($event)"
  />`,
})
class TestHostComponent {
  isStreaming = false;
  mode = ConversationMode.GENERAL_USER;
  disabled = false;
  lastSubmittedPrompt = '';
  stopCount = 0;
  lastMode = ConversationMode.GENERAL_USER;

  onSubmitPrompt(prompt: string): void {
    this.lastSubmittedPrompt = prompt;
  }

  onStopRequested(): void {
    this.stopCount++;
  }

  onModeChanged(mode: ConversationMode): void {
    this.lastMode = mode;
  }
}

describe('PromptInputComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PromptInputComponent, TestHostComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    host = fixture.componentInstance;
  });

  it('disables Send button when input is empty or whitespace only', () => {
    fixture.detectChanges();

    const sendBtn = fixture.debugElement.query(By.css('.send-button'));
    expect(sendBtn.nativeElement.disabled).toBeTrue();

    const textarea = fixture.debugElement.query(By.css('.prompt-textarea'));
    textarea.nativeElement.value = '   ';
    textarea.nativeElement.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(sendBtn.nativeElement.disabled).toBeTrue();
  });

  it('enables Send button, emits trimmed prompt on click, and clears draft', () => {
    fixture.detectChanges();

    const textarea = fixture.debugElement.query(By.css('.prompt-textarea'));
    textarea.nativeElement.value = '  check device pixel-8  ';
    textarea.nativeElement.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const sendBtn = fixture.debugElement.query(By.css('.send-button'));
    expect(sendBtn.nativeElement.disabled).toBeFalse();

    sendBtn.nativeElement.click();
    fixture.detectChanges();

    expect(host.lastSubmittedPrompt).toBe('check device pixel-8');
    expect(textarea.nativeElement.value).toBe('');
  });

  it('submits prompt on Enter key and allows newline on Shift+Enter', () => {
    fixture.detectChanges();

    const textarea = fixture.debugElement.query(By.css('.prompt-textarea'));
    textarea.nativeElement.value = 'hello assistant';
    textarea.nativeElement.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    // Shift + Enter should not submit
    const shiftEnter = new KeyboardEvent('keydown', {
      key: 'Enter',
      shiftKey: true,
    });
    textarea.nativeElement.dispatchEvent(shiftEnter);
    fixture.detectChanges();
    expect(host.lastSubmittedPrompt).toBe('');

    // Enter without Shift should submit and preventDefault
    const enterEvent = new KeyboardEvent('keydown', {
      key: 'Enter',
      shiftKey: false,
      cancelable: true,
    });
    spyOn(enterEvent, 'preventDefault').and.callThrough();
    textarea.nativeElement.dispatchEvent(enterEvent);
    fixture.detectChanges();
    expect(enterEvent.preventDefault).toHaveBeenCalled();
    expect(host.lastSubmittedPrompt).toBe('hello assistant');
  });

  it('does not emit submitPrompt when onSubmit is called while disabled or empty', () => {
    fixture.detectChanges();
    const promptInput = fixture.debugElement.query(
      By.directive(PromptInputComponent),
    ).componentInstance as PromptInputComponent;
    promptInput.onSubmit();
    expect(host.lastSubmittedPrompt).toBe('');
  });

  it('shows Stop button during streaming and emits stopRequested on click', () => {
    host.isStreaming = true;
    fixture.detectChanges();

    const sendBtn = fixture.debugElement.query(By.css('.send-button'));
    const stopBtn = fixture.debugElement.query(By.css('.stop-button'));

    expect(sendBtn).toBeNull();
    expect(stopBtn).toBeTruthy();

    stopBtn.nativeElement.click();
    expect(host.stopCount).toBe(1);
  });

  it('renders mode labels, icons, tooltips and emits modeChanged for both modes', () => {
    host.mode = ConversationMode.GENERAL_USER;
    fixture.detectChanges();

    const promptInput = fixture.debugElement.query(
      By.directive(PromptInputComponent),
    ).componentInstance as PromptInputComponent;

    expect(promptInput.getModeLabel()).toBe('Personal User');
    expect(promptInput.getModeIcon()).toBe('person');
    expect(promptInput.getModeTooltip()).toContain('Personal User');

    const modeBtn = fixture.debugElement.query(By.css('.mode-badge-button'));
    expect(modeBtn.nativeElement.textContent).toContain('Personal User');

    modeBtn.nativeElement.click();
    expect(host.lastMode).toBe(ConversationMode.TEAM_ASSISTANT);

    host.mode = ConversationMode.TEAM_ASSISTANT;
    fixture.detectChanges();
    expect(promptInput.getModeLabel()).toBe('Team Assistant');
    expect(promptInput.getModeIcon()).toBe('smart_toy');
    expect(promptInput.getModeTooltip()).toContain('Team Service Identity');

    modeBtn.nativeElement.click();
    expect(host.lastMode).toBe(ConversationMode.GENERAL_USER);
  });
});
