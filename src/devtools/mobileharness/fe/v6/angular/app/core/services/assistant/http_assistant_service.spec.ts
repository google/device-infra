/**
 * @fileoverview Unit tests for HttpAssistantService.
 */

import {provideHttpClient} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {APP_DATA, AppData} from '../../models/app_data';
import {
  ActionDecisionStatus,
  ActionRiskLevel,
  ConversationMode,
  CreateConversationResponse,
  GetConversationResponse,
  ListConversationsResponse,
  PollEventsResponse,
  SendMessageResponse,
  TurnStatus,
} from '../../models/assistant';
import {ASSISTANT_SERVICE} from './assistant_service';
import {HttpAssistantService} from './http_assistant_service';

describe('HttpAssistantService', () => {
  let service: HttpAssistantService;
  let httpMock: HttpTestingController;
  const mockServerUrl = 'http://test-server.example.com';
  const mockAppData: AppData = {
    labConsoleServerUrl: mockServerUrl,
  } as AppData;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {provide: APP_DATA, useValue: mockAppData},
        {
          provide: ASSISTANT_SERVICE,
          useClass: HttpAssistantService,
        },
      ],
    });
    service = TestBed.inject(ASSISTANT_SERVICE) as HttpAssistantService;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('is created successfully', () => {
    expect(service).toBeTruthy();
  });

  describe('listConversations', () => {
    it('sends GET request with pagination query params', () => {
      const mockResponse: ListConversationsResponse = {
        conversations: [
          {
            conversationId: 'conv_1',
            title: 'Fleet Query',
            mode: ConversationMode.GENERAL_USER,
            userLdap: 'mock_user',
          },
        ],
        nextPageToken: 'token_next',
      };

      service
        .listConversations({pageSize: 10, pageToken: 'token_prev'})
        .subscribe((res) => {
          expect(res).toEqual(mockResponse);
        });

      const req = httpMock.expectOne(
        `${mockServerUrl}/v6/assistant/conversations?page_size=10&page_token=token_prev`,
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });
  });

  describe('createConversation', () => {
    it('sends POST request to create conversation', () => {
      const mockResponse: CreateConversationResponse = {
        conversation: {
          conversationId: 'conv_new',
          title: 'New Chat',
          mode: ConversationMode.TEAM_ASSISTANT,
          userLdap: 'mock_user',
        },
      };

      service
        .createConversation({
          title: 'New Chat',
          mode: ConversationMode.TEAM_ASSISTANT,
        })
        .subscribe((res) => {
          expect(res).toEqual(mockResponse);
        });

      const req = httpMock.expectOne(
        `${mockServerUrl}/v6/assistant/conversations`,
      );
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        title: 'New Chat',
        mode: ConversationMode.TEAM_ASSISTANT,
      });
      req.flush(mockResponse);
    });
  });

  describe('getConversation', () => {
    it('sends GET request to retrieve conversation by ID', () => {
      const mockResponse: GetConversationResponse = {
        conversation: {
          conversationId: 'conv_123',
          title: 'Existing Chat',
          mode: ConversationMode.GENERAL_USER,
          userLdap: 'mock_user',
        },
      };

      service.getConversation('conv_123').subscribe((res) => {
        expect(res).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(
        `${mockServerUrl}/v6/assistant/conversations/conv_123`,
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });
  });

  describe('sendMessage', () => {
    it('sends POST request to submit prompt', () => {
      const mockResponse: SendMessageResponse = {
        conversationId: 'conv_123',
        turnId: 'turn_456',
        status: TurnStatus.RUNNING,
      };

      service
        .sendMessage({
          conversationId: 'conv_123',
          userPrompt: 'Find pixel 8 devices',
        })
        .subscribe((res) => {
          expect(res).toEqual(mockResponse);
        });

      const req = httpMock.expectOne(
        `${mockServerUrl}/v6/assistant/conversations/conv_123/messages`,
      );
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        'user_prompt': 'Find pixel 8 devices',
      });
      req.flush(mockResponse);
    });
  });

  describe('pollEvents', () => {
    it('sends GET request with last_event_seq and timeout_seconds', () => {
      const mockResponse: PollEventsResponse = {
        conversationId: 'conv_123',
        turnId: 'turn_456',
        turnStatus: TurnStatus.RUNNING,
        events: [
          {
            seq: 1,
            thoughtDelta: {text: 'Analyzing fleet...'},
          },
        ],
        nextEventSeq: 1,
        isTurnDone: false,
      };

      service
        .pollEvents({
          conversationId: 'conv_123',
          turnId: 'turn_456',
          lastEventSeq: 0,
          timeoutSeconds: 20,
        })
        .subscribe((res) => {
          expect(res).toEqual(mockResponse);
        });

      const req = httpMock.expectOne(
        `${mockServerUrl}/v6/assistant/conversations/conv_123/turns/turn_456/events?last_event_seq=0&timeout_seconds=20`,
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });
  });

  describe('cancelTurn', () => {
    it('sends POST request to cancel turn', () => {
      const mockResponse = {
        success: true,
        message: 'Turn cancelled',
      };

      service
        .cancelTurn({
          conversationId: 'conv_123',
          turnId: 'turn_456',
        })
        .subscribe((res) => {
          expect(res.success).toBeTrue();
        });

      const req = httpMock.expectOne(
        `${mockServerUrl}/v6/assistant/conversations/conv_123/turns/turn_456:cancel`,
      );
      expect(req.request.method).toBe('POST');
      req.flush(mockResponse);
    });
  });

  describe('submitActionDecision', () => {
    it('sends POST request to decide pending action', () => {
      const mockResponse = {
        actionConfirmation: {
          actionId: 'act_789',
          toolName: 'RebootDevice',
          targetEntity: 'dev_01',
          summary: 'Reboot dev_01',
          riskLevel: ActionRiskLevel.HIGH,
          status: ActionDecisionStatus.APPROVED,
        },
      };

      service
        .submitActionDecision({
          conversationId: 'conv_123',
          turnId: 'turn_456',
          actionId: 'act_789',
          approved: true,
          reason: 'Operator approved',
        })
        .subscribe((res) => {
          expect(res.actionConfirmation.status).toBe(
            ActionDecisionStatus.APPROVED,
          );
        });

      const req = httpMock.expectOne(
        `${mockServerUrl}/v6/assistant/conversations/conv_123/turns/turn_456/actions/act_789:decide`,
      );
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        approved: true,
        reason: 'Operator approved',
      });
      req.flush(mockResponse);
    });
  });
});
