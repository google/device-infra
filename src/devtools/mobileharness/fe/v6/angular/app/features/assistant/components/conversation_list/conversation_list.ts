import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTooltipModule} from '@angular/material/tooltip';

import {
  Conversation,
  ConversationMode,
} from '../../../../core/models/assistant';

/** Represents a section of conversations categorized by temporal bucket. */
export interface ConversationGroup {
  readonly label: string;
  readonly conversations: readonly Conversation[];
}

const MS_PER_DAY = 24 * 60 * 60 * 1000;

/** Determines the bucket category for a given date. */
function getBucket(date: Date, now: Date): string {
  const startOfToday = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate(),
  ).getTime();
  const startOfYesterday = startOfToday - MS_PER_DAY;
  const startOf7Days = startOfToday - 7 * MS_PER_DAY;
  const time = date.getTime();

  if (time >= startOfToday) {
    return 'Today';
  } else if (time >= startOfYesterday) {
    return 'Yesterday';
  } else if (time >= startOf7Days) {
    return 'Previous 7 Days';
  } else {
    return 'Older';
  }
}

/** Formats a timestamp into a compact human-readable relative string. */
export function formatRelativeTime(
  dateStr?: string,
  now: Date = new Date(),
): string {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  if (isNaN(date.getTime())) return '';

  const startOfToday = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate(),
  ).getTime();
  const time = date.getTime();

  if (time >= startOfToday) {
    return date.toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'});
  }
  const startOfYesterday = startOfToday - 24 * 60 * 60 * 1000;
  if (time >= startOfYesterday) {
    return 'Yesterday';
  }
  return date.toLocaleDateString([], {month: 'short', day: 'numeric'});
}

/** Groups a list of conversations chronologically by date buckets. */
export function groupConversations(
  conversations: readonly Conversation[],
  now: Date = new Date(),
): ConversationGroup[] {
  if (!conversations || conversations.length === 0) {
    return [];
  }

  const order = ['Today', 'Yesterday', 'Previous 7 Days', 'Older'];
  const buckets = new Map<string, Conversation[]>();
  for (const label of order) {
    buckets.set(label, []);
  }

  for (const conv of conversations) {
    const rawTime = conv.updateTime || conv.createTime;
    let bucket = 'Older';
    if (rawTime) {
      const parsed = new Date(rawTime);
      if (!isNaN(parsed.getTime())) {
        bucket = getBucket(parsed, now);
      }
    }
    const list = buckets.get(bucket) ?? buckets.get('Older')!;
    list.push(conv);
  }

  const groups: ConversationGroup[] = [];
  for (const label of order) {
    const items = buckets.get(label);
    if (items && items.length > 0) {
      groups.push({
        label,
        conversations: items,
      });
    }
  }

  return groups;
}

/**
 * Sidebar navigation component displaying the list of previous conversations,
 * grouped by date buckets with active selection and "New Chat" creation triggers.
 */
@Component({
  selector: 'app-conversation-list',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  templateUrl: './conversation_list.ng.html',
  styleUrl: './conversation_list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConversationListComponent {
  /** Array of conversations to display. */
  readonly conversations = input<readonly Conversation[]>([]);

  /** Currently active/selected conversation ID. */
  readonly selectedConversationId = input<string | null>(null);

  /** Indicates whether conversations are currently loading. */
  readonly isLoading = input<boolean>(false);

  /** Emitted when a conversation is selected by the user. */
  readonly conversationSelected = output<string>();

  /** Emitted when the "New Chat" button is clicked. */
  readonly newConversationRequested = output<void>();

  /** Chronologically grouped conversation items. */
  readonly groupedConversations = computed(() =>
    groupConversations(this.conversations()),
  );

  onSelectConversation(id: string): void {
    this.conversationSelected.emit(id);
  }

  onNewConversation(): void {
    this.newConversationRequested.emit();
  }

  getModeIcon(mode: ConversationMode): string {
    return mode === ConversationMode.TEAM_ASSISTANT ? 'smart_toy' : 'chat';
  }

  getModeTooltip(mode: ConversationMode): string {
    return mode === ConversationMode.TEAM_ASSISTANT
      ? 'Team Assistant (Service Identity)'
      : 'Personal User Assistant (EUC)';
  }

  formatTime(dateStr?: string): string {
    return formatRelativeTime(dateStr);
  }
}
