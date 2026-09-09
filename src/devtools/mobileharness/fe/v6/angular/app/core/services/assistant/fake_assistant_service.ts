/**
 * @fileoverview High-fidelity mock implementation of AssistantService for development and testing.
 */

import {inject, Injectable, InjectionToken} from '@angular/core';
import {Observable, of, throwError} from 'rxjs';
import {
  ActionConfirmationRequest,
  ActionDecisionStatus,
  ActionRiskLevel,
  AssistantEvent,
  CancelTurnRequest,
  CancelTurnResponse,
  Conversation,
  ConversationMode,
  CreateConversationRequest,
  CreateConversationResponse,
  GetConversationResponse,
  ListConversationsRequest,
  ListConversationsResponse,
  OutboundLink,
  PollEventsRequest,
  PollEventsResponse,
  SendMessageRequest,
  SendMessageResponse,
  SubmitActionDecisionRequest,
  SubmitActionDecisionResponse,
  Turn,
  TurnStatus,
} from '../../models/assistant';
import {AssistantService} from './assistant_service';
import {
  createSimulatedTurnEvents,
  DEFAULT_MOCK_CONVERSATIONS,
} from './mock_assistant_data';

/**
 * Options for configuring FakeAssistantService simulation behavior.
 */
export interface FakeAssistantServiceOptions {
  /** Maximum number of events to release per poll request. Default is 3. */
  chunkSize?: number;
  /** Initial conversations to populate the store with. */
  initialConversations?: Conversation[];
}

/**
 * InjectionToken for providing FakeAssistantServiceOptions.
 */
export const FAKE_ASSISTANT_SERVICE_OPTIONS =
  new InjectionToken<FakeAssistantServiceOptions>(
    'FAKE_ASSISTANT_SERVICE_OPTIONS',
  );

/**
 * In-memory mock implementation of AssistantService providing realistic simulated streaming turns.
 */
@Injectable()
export class FakeAssistantService extends AssistantService {
  private readonly chunkSize: number;
  private readonly conversations = new Map<string, Conversation>();
  private readonly turnEvents = new Map<string, AssistantEvent[]>();
  private readonly emittedCountByTurn = new Map<string, number>();
  private readonly cancelledTurns = new Set<string>();

  constructor() {
    super();
    const options = inject(FAKE_ASSISTANT_SERVICE_OPTIONS, {optional: true});
    this.chunkSize = options?.chunkSize ?? 3;

    const seed = options?.initialConversations ?? DEFAULT_MOCK_CONVERSATIONS;
    for (const conv of seed) {
      this.conversations.set(conv.conversationId, {
        ...conv,
        turns: conv.turns ? [...conv.turns] : [],
      });
    }
  }

  /**
   * Lists conversations in descending order of updateTime.
   */
  override listConversations(
    request?: ListConversationsRequest,
  ): Observable<ListConversationsResponse> {
    const list = Array.from(this.conversations.values()).sort((a, b) => {
      const timeA = a.updateTime ? new Date(a.updateTime).getTime() : 0;
      const timeB = b.updateTime ? new Date(b.updateTime).getTime() : 0;
      return timeB - timeA;
    });

    const pageSize = request?.pageSize ?? 50;
    const conversations = list.slice(0, pageSize);

    return of({
      conversations,
      nextPageToken: list.length > pageSize ? `token_${pageSize}` : undefined,
    });
  }

  /**
   * Creates a new empty conversation.
   */
  override createConversation(
    request: CreateConversationRequest,
  ): Observable<CreateConversationResponse> {
    const now = new Date().toISOString();
    const conversationId = `conv_${Date.now()}`;
    const conversation: Conversation = {
      conversationId,
      title: request.title?.trim() || 'New Conversation',
      mode: request.mode ?? ConversationMode.GENERAL_USER,
      userLdap: 'mock_user',
      createTime: now,
      updateTime: now,
      turns: [],
    };

    this.conversations.set(conversationId, conversation);
    return of({conversation});
  }

  /**
   * Retrieves conversation by ID with all historical turns.
   */
  override getConversation(
    conversationId: string,
  ): Observable<GetConversationResponse> {
    const conversation = this.conversations.get(conversationId);
    if (!conversation) {
      return throwError(
        () => new Error(`Conversation not found: ${conversationId}`),
      );
    }
    return of({conversation});
  }

  /**
   * Submits a message to an existing conversation and schedules turn events.
   */
  override sendMessage(
    request: SendMessageRequest,
  ): Observable<SendMessageResponse> {
    const conversation = this.conversations.get(request.conversationId);
    if (!conversation) {
      return throwError(
        () => new Error(`Conversation not found: ${request.conversationId}`),
      );
    }

    const turnId = `turn_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`;
    const now = new Date().toISOString();

    const newTurn: Turn = {
      turnId,
      userPrompt: request.userPrompt,
      status: TurnStatus.RUNNING,
      startTime: now,
    };

    const updatedTurns = [...(conversation.turns ?? []), newTurn];
    this.conversations.set(request.conversationId, {
      ...conversation,
      updateTime: now,
      turns: updatedTurns,
    });

    // Prepare simulated events for this turn
    const events = createSimulatedTurnEvents(turnId, request.userPrompt);
    this.turnEvents.set(turnId, events);
    this.emittedCountByTurn.set(turnId, 0);

    return of({
      conversationId: request.conversationId,
      turnId,
      status: TurnStatus.RUNNING,
    });
  }

  /**
   * Polls incremental events for an active turn.
   */
  override pollEvents(
    request: PollEventsRequest,
  ): Observable<PollEventsResponse> {
    const allEvents = this.turnEvents.get(request.turnId) ?? [];
    const isCancelled = this.cancelledTurns.has(request.turnId);

    if (isCancelled) {
      return of({
        conversationId: request.conversationId,
        turnId: request.turnId,
        turnStatus: TurnStatus.CANCELLED,
        events: [],
        nextEventSeq: request.lastEventSeq,
        isTurnDone: true,
      });
    }

    // Advance the emitted count by chunkSize
    const currentEmitted = this.emittedCountByTurn.get(request.turnId) ?? 0;
    const newEmitted = Math.min(
      allEvents.length,
      currentEmitted + this.chunkSize,
    );
    this.emittedCountByTurn.set(request.turnId, newEmitted);

    // Filter events: seq > lastEventSeq AND seq <= newEmitted
    const visibleEvents = allEvents.slice(0, newEmitted);
    const newEvents = visibleEvents.filter(
      (e) => Number(e.seq) > request.lastEventSeq,
    );

    const isTurnDone = newEmitted >= allEvents.length;
    const nextEventSeq =
      newEvents.length > 0
        ? Number(newEvents[newEvents.length - 1].seq)
        : request.lastEventSeq;

    const turnStatus = isTurnDone ? TurnStatus.COMPLETED : TurnStatus.RUNNING;

    // When turn completes, sync the updated state back to the conversation record
    if (isTurnDone) {
      this.syncCompletedTurnToConversation(
        request.conversationId,
        request.turnId,
        allEvents,
      );
    }

    return of({
      conversationId: request.conversationId,
      turnId: request.turnId,
      turnStatus,
      events: newEvents,
      nextEventSeq,
      isTurnDone,
    });
  }

  /**
   * Cancels an active turn execution.
   */
  override cancelTurn(
    request: CancelTurnRequest,
  ): Observable<CancelTurnResponse> {
    this.cancelledTurns.add(request.turnId);

    const conversation = this.conversations.get(request.conversationId);
    if (conversation && conversation.turns) {
      const updatedTurns = conversation.turns.map((t) =>
        t.turnId === request.turnId
          ? {
              ...t,
              status: TurnStatus.CANCELLED,
              endTime: new Date().toISOString(),
            }
          : t,
      );
      this.conversations.set(request.conversationId, {
        ...conversation,
        turns: updatedTurns,
      });
    }

    return of({
      success: true,
      message: `Turn ${request.turnId} cancelled.`,
    });
  }

  /**
   * Submits an operator decision for a pending action confirmation card.
   */
  override submitActionDecision(
    request: SubmitActionDecisionRequest,
  ): Observable<SubmitActionDecisionResponse> {
    const conversation = this.conversations.get(request.conversationId);
    const decisionStatus = request.approved
      ? ActionDecisionStatus.APPROVED
      : ActionDecisionStatus.REJECTED;
    const now = new Date().toISOString();

    let updatedAction: ActionConfirmationRequest = {
      actionId: request.actionId,
      toolName: 'UnknownAction',
      targetEntity: 'unknown',
      summary: 'Action decision recorded.',
      riskLevel: ActionRiskLevel.MEDIUM,
      status: decisionStatus,
      resolvedBy: 'operator@google.com',
      resolvedTime: now,
    };

    if (conversation && conversation.turns) {
      const updatedTurns = conversation.turns.map((turn) => {
        if (turn.turnId === request.turnId && turn.actionConfirmations) {
          const updatedConfirmations = turn.actionConfirmations.map(
            (action) => {
              if (action.actionId === request.actionId) {
                updatedAction = {
                  ...action,
                  status: decisionStatus,
                  resolvedBy: 'operator@google.com',
                  resolvedTime: now,
                };
                return updatedAction;
              }
              return action;
            },
          );
          return {
            ...turn,
            actionConfirmations: updatedConfirmations,
          };
        }
        return turn;
      });

      this.conversations.set(request.conversationId, {
        ...conversation,
        updateTime: now,
        turns: updatedTurns,
      });
    }

    return of({
      actionConfirmation: updatedAction,
    });
  }

  /**
   * Aggregates event deltas into completed Turn record properties.
   */
  private syncCompletedTurnToConversation(
    conversationId: string,
    turnId: string,
    events: AssistantEvent[],
  ) {
    const conversation = this.conversations.get(conversationId);
    if (!conversation || !conversation.turns) {
      return;
    }

    let thoughts = '';
    let responseMarkdown = '';
    const outboundLinks: OutboundLink[] = [];
    const actionConfirmations: ActionConfirmationRequest[] = [];

    for (const event of events) {
      if (event.thoughtDelta) {
        thoughts += event.thoughtDelta.text;
      }
      if (event.messageDelta) {
        responseMarkdown += event.messageDelta.text;
      }
      if (event.outboundLink) {
        outboundLinks.push(event.outboundLink);
      }
      if (event.actionConfirmation) {
        actionConfirmations.push(event.actionConfirmation);
      }
    }

    const updatedTurns = conversation.turns.map((t) => {
      if (t.turnId === turnId) {
        return {
          ...t,
          status: TurnStatus.COMPLETED,
          endTime: new Date().toISOString(),
          thoughts: thoughts || t.thoughts,
          responseMarkdown: responseMarkdown || t.responseMarkdown,
          outboundLinks:
            outboundLinks.length > 0 ? outboundLinks : t.outboundLinks,
          actionConfirmations:
            actionConfirmations.length > 0
              ? actionConfirmations
              : t.actionConfirmations,
        };
      }
      return t;
    });

    this.conversations.set(conversationId, {
      ...conversation,
      updateTime: new Date().toISOString(),
      turns: updatedTurns,
    });
  }
}
