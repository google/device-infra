import {TextFieldModule} from '@angular/cdk/text-field';
import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
  signal,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';

import {ConversationMode} from '../../../../core/models/assistant';

/**
 * Input console component for OmniLab Assistant.
 * Supports auto-sizing multiline text entry, Enter-to-send / Shift+Enter-for-newline shortcuts,
 * Send vs Stop streaming state transitions, and ConversationMode switching.
 */
@Component({
  selector: 'app-prompt-input',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    TextFieldModule,
  ],
  templateUrl: './prompt_input.ng.html',
  styleUrl: './prompt_input.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PromptInputComponent {
  readonly ConversationMode = ConversationMode;

  /** Indicates whether an assistant turn is currently streaming. */
  readonly isStreaming = input<boolean>(false);

  /** Current identity mode for the conversation. */
  readonly mode = input<ConversationMode>(ConversationMode.GENERAL_USER);

  /** Whether the input box is disabled. */
  readonly disabled = input<boolean>(false);

  /** Placeholder text for the prompt textarea. */
  readonly placeholder = input<string>(
    'Ask OmniLab Assistant about devices, hosts, tests, or jobs...',
  );

  /** Emits the trimmed user prompt text on submit. */
  readonly submitPrompt = output<string>();

  /** Emits when the user clicks Stop during active streaming. */
  readonly stopRequested = output<void>();

  /** Emits when the user toggles the conversation mode. */
  readonly modeChanged = output<ConversationMode>();

  /** Reactive draft text currently typed in the prompt box. */
  readonly promptText = signal<string>('');

  /** Computed flag indicating whether the Send button should be disabled. */
  readonly isSendDisabled = computed(
    () =>
      this.disabled() ||
      this.isStreaming() ||
      this.promptText().trim().length === 0,
  );

  onInputChange(event: Event): void {
    const target = event.target as HTMLTextAreaElement;
    this.promptText.set(target.value);
  }

  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.onSubmit();
    }
  }

  onSubmit(): void {
    if (this.isSendDisabled()) {
      return;
    }
    const text = this.promptText().trim();
    this.promptText.set('');
    this.submitPrompt.emit(text);
  }

  onStop(): void {
    this.stopRequested.emit();
  }

  onToggleMode(): void {
    const nextMode =
      this.mode() === ConversationMode.TEAM_ASSISTANT
        ? ConversationMode.GENERAL_USER
        : ConversationMode.TEAM_ASSISTANT;
    this.modeChanged.emit(nextMode);
  }

  getModeLabel(): string {
    return this.mode() === ConversationMode.TEAM_ASSISTANT
      ? 'Team Assistant'
      : 'Personal User';
  }

  getModeIcon(): string {
    return this.mode() === ConversationMode.TEAM_ASSISTANT
      ? 'smart_toy'
      : 'person';
  }

  getModeTooltip(): string {
    return this.mode() === ConversationMode.TEAM_ASSISTANT
      ? 'Running under Team Service Identity. Click to switch to Personal User (EUC).'
      : 'Running under Personal User (EUC). Click to switch to Team Assistant.';
  }
}
