/**
 * @fileoverview Production HTTP implementation of AssistantService for FE v6.
 */

import {HttpClient, HttpParams} from '@angular/common/http';
import {Injectable, inject} from '@angular/core';
import {Observable} from 'rxjs';
import {APP_DATA, AppData} from '../../models/app_data';
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
import {AssistantService} from './assistant_service';

/** An implementation of AssistantService that communicates with the backend gateway via HTTP. */
@Injectable()
export class HttpAssistantService extends AssistantService {
  private readonly appData: AppData = inject(APP_DATA);
  private readonly apiUrl = `${this.appData.labConsoleServerUrl}/v6/assistant`;
  private readonly http = inject(HttpClient);

  constructor() {
    super();
  }

  /**
   * Lists recent conversations via GET /v6/assistant/conversations.
   */
  override listConversations(
    request?: ListConversationsRequest,
  ): Observable<ListConversationsResponse> {
    let params = new HttpParams();
    if (request?.pageSize) {
      params = params.set('page_size', request.pageSize.toString());
    }
    if (request?.pageToken) {
      params = params.set('page_token', request.pageToken);
    }
    return this.http.get<ListConversationsResponse>(
      `${this.apiUrl}/conversations`,
      {params},
    );
  }

  /**
   * Creates a new conversation container via POST /v6/assistant/conversations.
   */
  override createConversation(
    request: CreateConversationRequest,
  ): Observable<CreateConversationResponse> {
    const payload = {
      title: request.title,
      mode: request.mode,
    };
    return this.http.post<CreateConversationResponse>(
      `${this.apiUrl}/conversations`,
      payload,
    );
  }

  /**
   * Retrieves conversation by ID via GET /v6/assistant/conversations/{id}.
   */
  override getConversation(
    conversationId: string,
  ): Observable<GetConversationResponse> {
    return this.http.get<GetConversationResponse>(
      `${this.apiUrl}/conversations/${encodeURIComponent(conversationId)}`,
    );
  }

  /**
   * Submits a user prompt to a conversation via POST /v6/assistant/conversations/{id}/messages.
   */
  override sendMessage(
    request: SendMessageRequest,
  ): Observable<SendMessageResponse> {
    const payload = {
      'user_prompt': request.userPrompt,
    };
    return this.http.post<SendMessageResponse>(
      `${this.apiUrl}/conversations/${encodeURIComponent(request.conversationId)}/messages`,
      payload,
    );
  }

  /**
   * Long-polls for incremental events via GET /v6/assistant/conversations/{cid}/turns/{tid}/events.
   */
  override pollEvents(
    request: PollEventsRequest,
  ): Observable<PollEventsResponse> {
    let params = new HttpParams().set(
      'last_event_seq',
      request.lastEventSeq.toString(),
    );
    if (request.timeoutSeconds) {
      params = params.set('timeout_seconds', request.timeoutSeconds.toString());
    }
    return this.http.get<PollEventsResponse>(
      `${this.apiUrl}/conversations/${encodeURIComponent(
        request.conversationId,
      )}/turns/${encodeURIComponent(request.turnId)}/events`,
      {params},
    );
  }

  /**
   * Cancels an in-flight turn via POST /v6/assistant/conversations/{cid}/turns/{tid}:cancel.
   */
  override cancelTurn(
    request: CancelTurnRequest,
  ): Observable<CancelTurnResponse> {
    return this.http.post<CancelTurnResponse>(
      `${this.apiUrl}/conversations/${encodeURIComponent(
        request.conversationId,
      )}/turns/${encodeURIComponent(request.turnId)}:cancel`,
      {},
    );
  }

  /**
   * Submits an operator decision for a pending action via POST /v6/assistant/conversations/{cid}/turns/{tid}/actions/{aid}:decide.
   */
  override submitActionDecision(
    request: SubmitActionDecisionRequest,
  ): Observable<SubmitActionDecisionResponse> {
    const payload = {
      approved: request.approved,
      reason: request.reason,
    };
    return this.http.post<SubmitActionDecisionResponse>(
      `${this.apiUrl}/conversations/${encodeURIComponent(
        request.conversationId,
      )}/turns/${encodeURIComponent(request.turnId)}/actions/${encodeURIComponent(
        request.actionId,
      )}:decide`,
      payload,
    );
  }
}
