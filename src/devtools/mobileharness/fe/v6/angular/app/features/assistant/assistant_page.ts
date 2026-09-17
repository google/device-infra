import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {ActivatedRoute, Router} from '@angular/router';
import {Subscription} from 'rxjs';

import {
  AssistantEvent,
  Conversation,
  ConversationMode,
  OutboundLink,
  ToolExecution,
  Turn,
  TurnStatus,
} from '../../core/models/assistant';
import {
  ASSISTANT_SERVICE,
  AssistantService,
} from '../../core/services/assistant/assistant_service';
import {LoadingService} from '../../shared/services/loading_service';
import {ConversationListComponent} from './components/conversation_list/conversation_list';
import {PromptInputComponent} from './components/prompt_input/prompt_input';
import {TurnViewComponent} from './components/turn_view/turn_view';

/** Pre-configured quick-start suggestion card for empty conversations. */
export interface SuggestionCard {
  readonly icon: string;
  readonly title: string;
  readonly description: string;
  readonly prompt: string;
}

const DEFAULT_SUGGESTIONS: readonly SuggestionCard[] = [
  {
    icon: 'devices_other',
    title: 'Lab Device Diagnostics',
    description: 'Check recovery status and quarantined devices in a lab',
    prompt: "Check recovery status of unhealthy devices in lab 'atc-core'",
  },
  {
    icon: 'dns',
    title: 'Host Health Triage',
    description: 'Summarize daemon logs and recent alerts for a host',
    prompt: "Summarize recent health failures for host 'mh-host-01.corp'",
  },
  {
    icon: 'bug_report',
    title: 'Test Failure Analysis',
    description: 'Inspect timeout traces and device logs for a test target',
    prompt: "Analyze why test 'BootHealthCheckTest' is timing out",
  },
];

/** Root two-pane page component for OmniLab Assistant. */
@Component({
  selector: 'app-assistant-page',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    ConversationListComponent,
    TurnViewComponent,
    PromptInputComponent,
  ],
  templateUrl: './assistant_page.ng.html',
  styleUrl: './assistant_page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssistantPage implements OnInit {
  private readonly assistantService: AssistantService =
    inject(ASSISTANT_SERVICE);
  private readonly loadingService = inject(LoadingService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly ConversationMode = ConversationMode;
  readonly suggestions = DEFAULT_SUGGESTIONS;

  /** Controls whether the left conversation drawer is open. */
  readonly isDrawerOpen = signal<boolean>(true);

  /** All loaded conversations for the user. */
  readonly conversations = signal<readonly Conversation[]>([]);

  /** Whether the conversations list is currently loading. */
  readonly isLoadingConversations = signal<boolean>(false);

  /** Currently selected conversation ID. */
  readonly selectedConversationId = signal<string | null>(null);

  /** Full active conversation object. */
  readonly activeConversation = signal<Conversation | null>(null);

  /** Reactive list of turns in the active conversation. */
  readonly activeTurns = signal<readonly Turn[]>([]);

  /** Current conversation identity mode. */
  readonly currentMode = signal<ConversationMode>(
    ConversationMode.GENERAL_USER,
  );

  /** Turn ID of the currently streaming turn, or null if idle. */
  readonly activeTurnId = signal<string | null>(null);

  /** Whether an assistant turn is currently in flight. */
  readonly isStreaming = computed(() => this.activeTurnId() !== null);

  private pollingSubscription: Subscription | null = null;

  ngOnInit(): void {
    this.loadingService.hide();
    this.loadConversations();
  }

  toggleDrawer(): void {
    this.isDrawerOpen.update((open) => !open);
  }

  loadConversations(): void {
    this.isLoadingConversations.set(true);
    this.assistantService
      .listConversations()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.isLoadingConversations.set(false);
          const list = res.conversations ?? [];
          this.conversations.set(list);

          const cidParam =
            this.route.snapshot.queryParamMap.get('cid') ??
            (typeof window !== 'undefined'
              ? new URLSearchParams(window.location.search).get('cid')
              : null);
          if (cidParam) {
            this.selectConversation(cidParam);
          } else if (list.length > 0 && !this.selectedConversationId()) {
            this.selectConversation(list[0].conversationId);
          }
        },
        error: () => {
          this.isLoadingConversations.set(false);
        },
      });
  }

  selectConversation(conversationId: string): void {
    this.stopPolling();
    this.selectedConversationId.set(conversationId);
    this.syncUrlCid(conversationId);

    this.assistantService
      .getConversation(conversationId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          const conv = res.conversation;
          this.activeConversation.set(conv);
          this.currentMode.set(conv.mode);
          this.activeTurns.set(conv.turns ? [...conv.turns] : []);
        },
      });
  }

  onNewConversation(): void {
    this.stopPolling();
    this.assistantService
      .createConversation({
        title: 'New Conversation',
        mode: this.currentMode(),
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          const conv = res.conversation;
          this.conversations.update((prev) => [conv, ...prev]);
          this.selectedConversationId.set(conv.conversationId);
          this.activeConversation.set(conv);
          this.activeTurns.set([]);
          this.syncUrlCid(conv.conversationId);
        },
      });
  }

  onModeChanged(mode: ConversationMode): void {
    this.currentMode.set(mode);
  }

  onSendMessage(promptText: string): void {
    const trimmed = promptText.trim();
    if (!trimmed || this.isStreaming()) {
      return;
    }

    const currentConvId = this.selectedConversationId();
    if (!currentConvId) {
      this.assistantService
        .createConversation({
          title: trimmed.slice(0, 48),
          mode: this.currentMode(),
        })
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (res) => {
            const conv = res.conversation;
            this.conversations.update((prev) => [conv, ...prev]);
            this.selectedConversationId.set(conv.conversationId);
            this.activeConversation.set(conv);
            this.activeTurns.set([]);
            this.syncUrlCid(conv.conversationId);
            this.dispatchMessage(conv.conversationId, trimmed);
          },
        });
      return;
    }

    this.dispatchMessage(currentConvId, trimmed);
  }

  onCancelTurn(): void {
    const convId = this.selectedConversationId();
    const turnId = this.activeTurnId();
    if (!convId || !turnId) {
      return;
    }

    this.stopPolling();
    this.assistantService
      .cancelTurn({conversationId: convId, turnId})
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.updateTurnById(turnId, (turn) => ({
            ...turn,
            status: TurnStatus.CANCELLED,
            endTime: new Date().toISOString(),
          }));
        },
      });
  }

  private dispatchMessage(conversationId: string, userPrompt: string): void {
    const tempTurnId = `turn-pending-${Date.now()}`;
    const optimisticTurn: Turn = {
      turnId: tempTurnId,
      userPrompt,
      status: TurnStatus.QUEUED,
      startTime: new Date().toISOString(),
      thoughts: '',
      responseMarkdown: '',
      toolExecutions: [],
      outboundLinks: [],
    };

    this.activeTurns.update((turns) => [...turns, optimisticTurn]);
    this.activeTurnId.set(tempTurnId);

    this.assistantService
      .sendMessage({conversationId, userPrompt})
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          const realTurnId = res.turnId;
          this.updateTurnById(tempTurnId, (turn) => ({
            ...turn,
            turnId: realTurnId,
            status: res.status,
          }));
          this.startPolling(conversationId, realTurnId, 0);
        },
        error: (err) => {
          this.activeTurnId.set(null);
          this.updateTurnById(tempTurnId, (turn) => ({
            ...turn,
            status: TurnStatus.FAILED,
            errorMessage:
              err instanceof Error ? err.message : 'Failed to send message',
            endTime: new Date().toISOString(),
          }));
        },
      });
  }

  private startPolling(
    conversationId: string,
    turnId: string,
    lastEventSeq: number,
  ): void {
    this.clearPollingSubscription();
    this.activeTurnId.set(turnId);
    this.pollNext(conversationId, turnId, lastEventSeq);
  }

  private pollNext(
    conversationId: string,
    turnId: string,
    lastEventSeq: number,
  ): void {
    this.pollingSubscription = this.assistantService
      .pollEvents({
        conversationId,
        turnId,
        lastEventSeq,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.applyDeltaEvents(turnId, res.events, res.turnStatus);
          if (
            res.isTurnDone ||
            res.turnStatus === TurnStatus.COMPLETED ||
            res.turnStatus === TurnStatus.FAILED ||
            res.turnStatus === TurnStatus.CANCELLED
          ) {
            this.stopPolling();
          } else {
            this.pollNext(conversationId, turnId, res.nextEventSeq);
          }
        },
        error: () => {
          this.stopPolling();
        },
      });
  }

  private applyDeltaEvents(
    turnId: string,
    events: readonly AssistantEvent[],
    turnStatus: TurnStatus,
  ): void {
    this.updateTurnById(turnId, (turn) => {
      let thoughts = turn.thoughts ?? '';
      let responseMarkdown = turn.responseMarkdown ?? '';
      const toolExecutions: ToolExecution[] = [...(turn.toolExecutions ?? [])];
      const outboundLinks: OutboundLink[] = [...(turn.outboundLinks ?? [])];
      let status = turnStatus;
      let errorMessage = turn.errorMessage;

      for (const event of events) {
        if (event.thoughtDelta) {
          thoughts += event.thoughtDelta.text;
        }
        if (event.toolCall) {
          const call = event.toolCall;
          const existingIdx = toolExecutions.findIndex(
            (t) => t.callId === call.callId,
          );
          if (existingIdx === -1) {
            toolExecutions.push({
              callId: call.callId,
              toolName: call.toolName,
              argumentsJson: call.argumentsJson,
              success: true,
            });
          }
        }
        if (event.toolResult) {
          const result = event.toolResult;
          const idx = toolExecutions.findIndex(
            (t) => t.callId === result.callId,
          );
          if (idx >= 0) {
            toolExecutions[idx] = {
              ...toolExecutions[idx],
              success: result.success,
              resultJson: result.resultJson,
              errorMessage: result.errorMessage,
            };
          } else {
            toolExecutions.push({
              callId: result.callId,
              toolName: result.toolName,
              argumentsJson: '{}',
              success: result.success,
              resultJson: result.resultJson,
              errorMessage: result.errorMessage,
            });
          }
        }
        if (event.messageDelta) {
          responseMarkdown += event.messageDelta.text;
        }
        if (event.outboundLink) {
          const link = event.outboundLink;
          if (!outboundLinks.some((l) => l.url === link.url)) {
            outboundLinks.push(link);
          }
        }
        if (event.statusUpdate) {
          status = event.statusUpdate.status;
          if (event.statusUpdate.errorMessage) {
            errorMessage = event.statusUpdate.errorMessage;
          }
        }
      }

      const endTime =
        status === TurnStatus.COMPLETED ||
        status === TurnStatus.FAILED ||
        status === TurnStatus.CANCELLED
          ? (turn.endTime ?? new Date().toISOString())
          : turn.endTime;

      return {
        ...turn,
        status,
        thoughts,
        responseMarkdown,
        toolExecutions,
        outboundLinks,
        errorMessage,
        endTime,
      };
    });
  }

  private updateTurnById(turnId: string, updater: (turn: Turn) => Turn): void {
    this.activeTurns.update((turns) =>
      turns.map((turn) => (turn.turnId === turnId ? updater(turn) : turn)),
    );
  }

  private clearPollingSubscription(): void {
    this.pollingSubscription?.unsubscribe();
  }

  private stopPolling(): void {
    this.clearPollingSubscription();
    this.activeTurnId.set(null);
  }

  private syncUrlCid(conversationId: string): void {
    const currentCid =
      this.route.snapshot.queryParamMap.get('cid') ??
      (typeof window !== 'undefined'
        ? new URLSearchParams(window.location.search).get('cid')
        : null);
    if (currentCid === conversationId) {
      return;
    }
    this.router.navigate(['/assistant'], {
      queryParams: {'cid': conversationId},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}
