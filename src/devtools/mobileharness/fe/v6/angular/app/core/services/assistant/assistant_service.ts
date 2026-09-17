import {InjectionToken} from '@angular/core';
import {Observable} from 'rxjs';
import {
  CancelTurnRequest,
  CancelTurnResponse,
  CreateConversationRequest,
  CreateConversationResponse,
  GetConversationResponse,
  ListConversationsRequest,
  ListConversationsResponse,
  PollEventsRequest,
  PollEventsResponse,
  SendMessageRequest,
  SendMessageResponse,
  SubmitActionDecisionRequest,
  SubmitActionDecisionResponse,
} from '../../models/assistant';

/**
 * Injection token for the AssistantService.
 */
export const ASSISTANT_SERVICE = new InjectionToken<AssistantService>(
  'AssistantService',
);

/**
 * Abstract class defining the contract for OmniLab Assistant conversational operations.
 */
export abstract class AssistantService {
  /**
   * Lists recent conversations created by or visible to the user.
   */
  abstract listConversations(
    request?: ListConversationsRequest,
  ): Observable<ListConversationsResponse>;

  /**
   * Creates a new conversation container.
   */
  abstract createConversation(
    request: CreateConversationRequest,
  ): Observable<CreateConversationResponse>;

  /**
   * Retrieves conversation metadata and complete historical turn records.
   */
  abstract getConversation(
    conversationId: string,
  ): Observable<GetConversationResponse>;

  /**
   * Submits a user prompt to an existing conversation. Detaches turn execution on the server.
   */
  abstract sendMessage(
    request: SendMessageRequest,
  ): Observable<SendMessageResponse>;

  /**
   * Long-polls for incremental events from an active turn.
   */
  abstract pollEvents(
    request: PollEventsRequest,
  ): Observable<PollEventsResponse>;

  /**
   * Requests cancellation of an in-flight turn execution.
   */
  abstract cancelTurn(
    request: CancelTurnRequest,
  ): Observable<CancelTurnResponse>;

  /**
   * Submits an operator decision (approve/reject) for a human-in-the-loop action confirmation card.
   */
  abstract submitActionDecision(
    request: SubmitActionDecisionRequest,
  ): Observable<SubmitActionDecisionResponse>;
}
