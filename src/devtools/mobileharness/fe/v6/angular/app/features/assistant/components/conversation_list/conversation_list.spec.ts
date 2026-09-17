import {ChangeDetectionStrategy, Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {
  Conversation,
  ConversationMode,
} from '../../../../core/models/assistant';
import {
  ConversationListComponent,
  formatRelativeTime,
  groupConversations,
} from './conversation_list';

@Component({
  standalone: true,
  imports: [ConversationListComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-conversation-list
    [conversations]="conversations"
    [selectedConversationId]="selectedId"
    [isLoading]="isLoading"
    (conversationSelected)="onSelected($event)"
    (newConversationRequested)="onNewRequested()"
  />`,
})
class TestHostComponent {
  conversations: Conversation[] = [];
  selectedId: string | null = null;
  isLoading = false;
  lastSelectedId: string | null = null;
  newRequestedCount = 0;

  onSelected(id: string): void {
    this.lastSelectedId = id;
  }

  onNewRequested(): void {
    this.newRequestedCount++;
  }
}

describe('ConversationListComponent', () => {
  const fixedNow = new Date('2026-09-09T14:00:00Z');

  describe('groupConversations', () => {
    it('returns empty array for empty input', () => {
      expect(groupConversations([], fixedNow)).toEqual([]);
    });

    it('buckets conversations into Today, Yesterday, Previous 7 Days, and Older', () => {
      const convs: Conversation[] = [
        {
          conversationId: 'c1',
          title: 'Today Chat',
          mode: ConversationMode.GENERAL_USER,
          userLdap: 'user1',
          updateTime: '2026-09-09T10:00:00Z',
        },
        {
          conversationId: 'c2',
          title: 'Yesterday Chat',
          mode: ConversationMode.TEAM_ASSISTANT,
          userLdap: 'user1',
          updateTime: '2026-09-08T10:00:00Z',
        },
        {
          conversationId: 'c3',
          title: 'Last Week Chat',
          mode: ConversationMode.GENERAL_USER,
          userLdap: 'user1',
          updateTime: '2026-09-05T10:00:00Z',
        },
        {
          conversationId: 'c4',
          title: 'Older Chat',
          mode: ConversationMode.TEAM_ASSISTANT,
          userLdap: 'user1',
          updateTime: '2026-08-01T10:00:00Z',
        },
      ];

      const groups = groupConversations(convs, fixedNow);
      const summary = groups.map(
        (g) =>
          `${g.label}:${g.conversations.map((c) => c.conversationId).join(',')}`,
      );
      expect(summary).toEqual([
        'Today:c1',
        'Yesterday:c2',
        'Previous 7 Days:c3',
        'Older:c4',
      ]);
    });
  });

  describe('formatRelativeTime', () => {
    it('returns empty string for undefined or invalid dates', () => {
      expect(formatRelativeTime(undefined, fixedNow)).toBe('');
      expect(formatRelativeTime('invalid-date', fixedNow)).toBe('');
    });

    it('formats yesterday date as Yesterday', () => {
      expect(formatRelativeTime('2026-09-08T10:00:00Z', fixedNow)).toBe(
        'Yesterday',
      );
    });

    it('formats today date as time and older date as month/day', () => {
      expect(
        formatRelativeTime('2026-09-09T12:30:00Z', fixedNow).length,
      ).toBeGreaterThan(0);
      expect(formatRelativeTime('2026-01-01T10:00:00Z', fixedNow)).toContain(
        'Jan',
      );
    });
  });

  describe('Component rendering and interactions', () => {
    let fixture: ComponentFixture<TestHostComponent>;
    let host: TestHostComponent;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [
          ConversationListComponent,
          TestHostComponent,
          NoopAnimationsModule,
        ],
      }).compileComponents();

      fixture = TestBed.createComponent(TestHostComponent);
      host = fixture.componentInstance;
    });

    it('renders empty state when conversations list is empty', () => {
      fixture.detectChanges();

      const emptyContainer = fixture.debugElement.query(
        By.css('.empty-container'),
      );
      expect(emptyContainer).toBeTruthy();
      expect(emptyContainer.nativeElement.textContent).toContain(
        'No conversations yet',
      );
    });

    it('renders loading spinner when isLoading is true', () => {
      host.isLoading = true;
      fixture.detectChanges();

      const loadingContainer = fixture.debugElement.query(
        By.css('.loading-container'),
      );
      expect(loadingContainer).toBeTruthy();
      expect(loadingContainer.nativeElement.textContent).toContain(
        'Loading conversations...',
      );
    });

    it('renders conversations and highlights the selected conversation', () => {
      host.conversations = [
        {
          conversationId: 'c1',
          title: 'Check Pixel 8 status',
          mode: ConversationMode.GENERAL_USER,
          userLdap: 'user1',
          updateTime: new Date().toISOString(),
        },
        {
          conversationId: 'c2',
          title: 'Release lab host 04',
          mode: ConversationMode.TEAM_ASSISTANT,
          userLdap: 'user1',
          updateTime: new Date().toISOString(),
        },
      ];
      host.selectedId = 'c2';
      fixture.detectChanges();

      const items = fixture.debugElement.queryAll(By.css('.conversation-item'));
      expect(items.length).toBe(2);

      expect(items[0].nativeElement.classList.contains('selected')).toBeFalse();
      expect(items[1].nativeElement.classList.contains('selected')).toBeTrue();
    });

    it('emits conversationSelected when an item is clicked', () => {
      host.conversations = [
        {
          conversationId: 'conv-42',
          title: 'Debug test failure',
          mode: ConversationMode.GENERAL_USER,
          userLdap: 'user1',
        },
      ];
      fixture.detectChanges();

      const item = fixture.debugElement.query(By.css('.conversation-item'));
      item.nativeElement.click();

      expect(host.lastSelectedId).toBe('conv-42');
    });

    it('emits newConversationRequested when New Chat button is clicked', () => {
      fixture.detectChanges();

      const newBtn = fixture.debugElement.query(By.css('.new-chat-button'));
      newBtn.nativeElement.click();

      expect(host.newRequestedCount).toBe(1);
    });
  });
});
