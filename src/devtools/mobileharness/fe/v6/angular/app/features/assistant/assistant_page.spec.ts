import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  ParamMap,
  Router,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import {NEVER, Observable, Subject, of} from 'rxjs';

import {
  ActionConfirmationRequest,
  ActionDecisionStatus,
  ActionRiskLevel,
  Conversation,
  ConversationMode,
  PollEventsResponse,
  TurnStatus,
} from '../../core/models/assistant';
import {
  ASSISTANT_SERVICE,
  AssistantService,
} from '../../core/services/assistant/assistant_service';
import {AssistantPage} from './assistant_page';

describe('AssistantPage', () => {
  let component: AssistantPage;
  let fixture: ComponentFixture<AssistantPage>;
  let assistantServiceSpy: jasmine.SpyObj<AssistantService>;

  const mockConversation1: Conversation = {
    conversationId: 'conv-1',
    title: 'Lab Device Diagnostics',
    mode: ConversationMode.GENERAL_USER,
    userLdap: 'yowang',
    createTime: '2026-09-09T10:00:00Z',
    updateTime: '2026-09-09T10:05:00Z',
    turns: [
      {
        turnId: 'turn-1',
        userPrompt: 'Check device status in lab atc-core',
        status: TurnStatus.COMPLETED,
        thoughts: 'Checking lab atc-core...',
        responseMarkdown: 'Found 3 healthy devices.',
        actionConfirmations: [
          {
            actionId: 'action-1',
            toolName: 'reboot_device',
            targetEntity: 'device-001',
            summary: 'Reboot device-001 in lab atc-core',
            riskLevel: ActionRiskLevel.HIGH,
            status: ActionDecisionStatus.PENDING,
          },
          {
            actionId: 'action-2',
            toolName: 'flash_device',
            targetEntity: 'device-002',
            summary: 'Flash device-002 in lab atc-core',
            riskLevel: ActionRiskLevel.HIGH,
            status: ActionDecisionStatus.PENDING,
          },
        ],
      },
    ],
  };

  const mockConversation2: Conversation = {
    conversationId: 'conv-2',
    title: 'Empty Conversation',
    mode: ConversationMode.TEAM_ASSISTANT,
    userLdap: 'yowang',
    createTime: '2026-09-09T11:00:00Z',
    updateTime: '2026-09-09T11:00:00Z',
    turns: [],
  };

  beforeEach(async () => {
    assistantServiceSpy = jasmine.createSpyObj<AssistantService>(
      'AssistantService',
      [
        'listConversations',
        'createConversation',
        'getConversation',
        'sendMessage',
        'pollEvents',
        'cancelTurn',
        'submitActionDecision',
      ],
    );

    assistantServiceSpy.listConversations.and.returnValue(
      of({conversations: [mockConversation1, mockConversation2]}),
    );
    assistantServiceSpy.getConversation.and.callFake((id: string) =>
      of({
        conversation: id === 'conv-2' ? mockConversation2 : mockConversation1,
      }),
    );

    await TestBed.configureTestingModule({
      imports: [AssistantPage, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        {provide: ASSISTANT_SERVICE, useValue: assistantServiceSpy},
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap({}),
              queryParams: {},
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AssistantPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load conversations on init', () => {
    expect(component).toBeTruthy();
    expect(assistantServiceSpy.listConversations).toHaveBeenCalled();
    expect(component.conversations().length).toBe(2);
    expect(component.selectedConversationId()).toBe('conv-1');
    expect(component.activeTurns().length).toBe(1);
  });

  it('should toggle sidebar drawer', () => {
    expect(component.isDrawerOpen()).toBeTrue();
    component.toggleDrawer();
    expect(component.isDrawerOpen()).toBeFalse();
    component.toggleDrawer();
    expect(component.isDrawerOpen()).toBeTrue();
  });

  it('should switch conversation and display empty hero state when 0 turns', () => {
    component.selectConversation('conv-2');
    fixture.detectChanges();

    expect(component.selectedConversationId()).toBe('conv-2');
    expect(component.activeConversation()?.title).toBe('Empty Conversation');
    expect(component.currentMode()).toBe(ConversationMode.TEAM_ASSISTANT);
    expect(component.activeTurns().length).toBe(0);

    const welcomeHero = fixture.nativeElement.querySelector('.welcome-hero');
    expect(welcomeHero).toBeTruthy();
  });

  it('should create a new conversation and select it', () => {
    const newConv: Conversation = {
      conversationId: 'conv-new',
      title: 'New Conversation',
      mode: ConversationMode.GENERAL_USER,
      userLdap: 'yowang',
      turns: [],
    };
    assistantServiceSpy.createConversation.and.returnValue(
      of({conversation: newConv}),
    );

    component.onNewConversation();
    fixture.detectChanges();

    expect(assistantServiceSpy.createConversation).toHaveBeenCalled();
    expect(component.selectedConversationId()).toBe('conv-new');
    expect(component.conversations()[0].conversationId).toBe('conv-new');
    expect(component.activeTurns().length).toBe(0);
  });

  it('should send message, apply delta events, and complete streaming', () => {
    assistantServiceSpy.sendMessage.and.returnValue(
      of({
        conversationId: 'conv-1',
        turnId: 'turn-stream-1',
        status: TurnStatus.RUNNING,
      }),
    );

    assistantServiceSpy.pollEvents.and.returnValue(
      of({
        conversationId: 'conv-1',
        turnId: 'turn-stream-1',
        turnStatus: TurnStatus.COMPLETED,
        nextEventSeq: 3,
        isTurnDone: true,
        events: [
          {
            seq: 1,
            thoughtDelta: {text: 'Reasoning step 1...'},
          },
          {
            seq: 2,
            toolCall: {
              callId: 'call-1',
              toolName: 'get_device_status',
              argumentsJson: '{"device_id":"dev-1"}',
            },
          },
          {
            seq: 3,
            messageDelta: {text: 'Device dev-1 is healthy.'},
          },
        ],
      }),
    );

    component.onSendMessage('Diagnose dev-1');

    expect(assistantServiceSpy.sendMessage).toHaveBeenCalledWith({
      conversationId: 'conv-1',
      userPrompt: 'Diagnose dev-1',
    });

    const turns = component.activeTurns();
    const latestTurn = turns[turns.length - 1];
    expect(latestTurn.turnId).toBe('turn-stream-1');
    expect(latestTurn.status).toBe(TurnStatus.COMPLETED);
    expect(latestTurn.thoughts).toBe('Reasoning step 1...');
    expect(latestTurn.responseMarkdown).toBe('Device dev-1 is healthy.');
    expect(latestTurn.toolExecutions?.length).toBe(1);
    expect(component.isStreaming()).toBeFalse();
  });

  it('should cancel in-flight turn when stopRequested is emitted', () => {
    assistantServiceSpy.sendMessage.and.returnValue(
      of({
        conversationId: 'conv-1',
        turnId: 'turn-cancel-1',
        status: TurnStatus.RUNNING,
      }),
    );
    assistantServiceSpy.pollEvents.and.returnValue(NEVER);
    assistantServiceSpy.cancelTurn.and.returnValue(
      of({success: true, message: 'Cancelled'}),
    );

    component.onSendMessage('Long running query');
    expect(component.isStreaming()).toBeTrue();

    component.onCancelTurn();

    expect(assistantServiceSpy.cancelTurn).toHaveBeenCalledWith({
      conversationId: 'conv-1',
      turnId: 'turn-cancel-1',
    });
    expect(component.isStreaming()).toBeFalse();
    const turns = component.activeTurns();
    expect(turns[turns.length - 1].status).toBe(TurnStatus.CANCELLED);
  });

  it('should create a conversation automatically when sending a message with no selected conversation', () => {
    component.selectedConversationId.set(null);
    const autoConv: Conversation = {
      conversationId: 'conv-auto',
      title: 'Auto Created',
      mode: ConversationMode.GENERAL_USER,
      userLdap: 'yowang',
      turns: [],
    };
    assistantServiceSpy.createConversation.and.returnValue(
      of({conversation: autoConv}),
    );
    assistantServiceSpy.sendMessage.and.returnValue(
      of({
        conversationId: 'conv-auto',
        turnId: 'turn-auto-1',
        status: TurnStatus.RUNNING,
      }),
    );
    assistantServiceSpy.pollEvents.and.returnValue(
      of({
        conversationId: 'conv-auto',
        turnId: 'turn-auto-1',
        turnStatus: TurnStatus.COMPLETED,
        nextEventSeq: 1,
        isTurnDone: true,
        events: [],
      }),
    );

    component.onSendMessage('Auto create conversation prompt');

    expect(assistantServiceSpy.createConversation).toHaveBeenCalled();
    expect(component.selectedConversationId()).toBe('conv-auto');
    expect(assistantServiceSpy.sendMessage).toHaveBeenCalledWith({
      conversationId: 'conv-auto',
      userPrompt: 'Auto create conversation prompt',
    });
  });

  it('should handle sendMessage error and set turn status to FAILED', () => {
    assistantServiceSpy.sendMessage.and.returnValue(
      new Observable((subscriber) => {
        subscriber.error(new Error('Network timeout'));
      }),
    );

    component.onSendMessage('Trigger network error');

    const turns = component.activeTurns();
    const failedTurn = turns[turns.length - 1];
    expect(failedTurn.status).toBe(TurnStatus.FAILED);
    expect(failedTurn.errorMessage).toBe('Network timeout');
    expect(component.isStreaming()).toBeFalse();
  });

  it('should apply toolResult, outboundLink, and statusUpdate deltas', () => {
    assistantServiceSpy.sendMessage.and.returnValue(
      of({
        conversationId: 'conv-1',
        turnId: 'turn-delta-1',
        status: TurnStatus.RUNNING,
      }),
    );

    assistantServiceSpy.pollEvents.and.returnValue(
      of({
        conversationId: 'conv-1',
        turnId: 'turn-delta-1',
        turnStatus: TurnStatus.FAILED,
        nextEventSeq: 5,
        isTurnDone: true,
        events: [
          {
            seq: 1,
            toolCall: {
              callId: 'call-10',
              toolName: 'CheckDevice',
              argumentsJson: '{"id":"d1"}',
            },
          },
          {
            seq: 2,
            toolResult: {
              callId: 'call-10',
              toolName: 'CheckDevice',
              success: true,
              resultJson: '{"healthy":true}',
            },
          },
          {
            seq: 3,
            toolResult: {
              callId: 'call-orphan',
              toolName: 'OrphanTool',
              success: false,
              errorMessage: 'Orphan error',
            },
          },
          {
            seq: 4,
            outboundLink: {
              title: 'Sponge Link',
              url: 'https://sponge2/999',
            },
          },
          {
            seq: 5,
            statusUpdate: {
              status: TurnStatus.FAILED,
              errorMessage: 'Agent execution aborted',
            },
          },
        ],
      }),
    );

    component.onSendMessage('Test all deltas');

    const turns = component.activeTurns();
    const latestTurn = turns[turns.length - 1];
    expect(latestTurn.toolExecutions?.length).toBe(2);
    expect(latestTurn.toolExecutions?.[0].resultJson).toBe('{"healthy":true}');
    expect(latestTurn.toolExecutions?.[1].callId).toBe('call-orphan');
    expect(latestTurn.outboundLinks?.length).toBe(1);
    expect(latestTurn.status).toBe(TurnStatus.FAILED);
    expect(latestTurn.errorMessage).toBe('Agent execution aborted');
  });

  it('should handle listConversations error gracefully and update mode on onModeChanged', () => {
    assistantServiceSpy.listConversations.and.returnValue(
      new Observable((subscriber) => {
        subscriber.error(new Error('List failed'));
      }),
    );

    component.loadConversations();
    expect(component.isLoadingConversations()).toBeFalse();

    component.onModeChanged(ConversationMode.TEAM_ASSISTANT);
    expect(component.currentMode()).toBe(ConversationMode.TEAM_ASSISTANT);
  });

  it('should set isLoadingConversations to true while listConversations is in flight and stop polling on selectConversation', () => {
    const listSubject = new Subject<{conversations: Conversation[]}>();
    assistantServiceSpy.listConversations.and.returnValue(
      listSubject.asObservable(),
    );

    component.loadConversations();
    expect(component.isLoadingConversations()).toBeTrue();

    listSubject.next({conversations: [mockConversation1, mockConversation2]});
    expect(component.isLoadingConversations()).toBeFalse();

    // Start streaming turn and verify selectConversation stops polling and unsubscribes pollSubject
    assistantServiceSpy.sendMessage.and.returnValue(
      of({
        conversationId: 'conv-1',
        turnId: 'turn-stream-stop',
        status: TurnStatus.RUNNING,
      }),
    );
    const pollSubject = new Subject<PollEventsResponse>();
    assistantServiceSpy.pollEvents.and.returnValue(pollSubject.asObservable());

    component.onSendMessage('Start streaming before switching');
    expect(component.isStreaming()).toBeTrue();
    expect(pollSubject.observed).toBeTrue();

    const getConvSubject = new Subject<{conversation: Conversation}>();
    assistantServiceSpy.getConversation.and.returnValue(
      getConvSubject.asObservable(),
    );

    component.selectConversation('conv-2');
    expect(pollSubject.observed).toBeFalse();
    expect(component.isStreaming()).toBeFalse();
    expect(component.selectedConversationId()).toBe('conv-2');
    getConvSubject.next({conversation: mockConversation2});
  });

  it('should append optimistic QUEUED turn immediately before sendMessage resolves and stop polling on onNewConversation', () => {
    const sendSubject = new Subject<{
      conversationId: string;
      turnId: string;
      status: TurnStatus;
    }>();
    assistantServiceSpy.sendMessage.and.returnValue(sendSubject.asObservable());
    const pollSubject = new Subject<PollEventsResponse>();
    assistantServiceSpy.pollEvents.and.returnValue(pollSubject.asObservable());

    component.onSendMessage('Optimistic prompt');
    expect(component.isStreaming()).toBeTrue();
    expect(component.activeTurns().length).toBe(2);
    expect(component.activeTurns()[1].status).toBe(TurnStatus.QUEUED);
    expect(component.activeTurns()[1].userPrompt).toBe('Optimistic prompt');

    sendSubject.next({
      conversationId: 'conv-1',
      turnId: 'turn-opt-1',
      status: TurnStatus.RUNNING,
    });
    expect(pollSubject.observed).toBeTrue();
    expect(component.activeTurnId()).toBe('turn-opt-1');

    const pollSubject2 = new Subject<PollEventsResponse>();
    assistantServiceSpy.pollEvents.and.returnValue(pollSubject2.asObservable());
    component.onSendMessage('Second prompt');
    sendSubject.next({
      conversationId: 'conv-1',
      turnId: 'turn-opt-2',
      status: TurnStatus.RUNNING,
    });
    expect(pollSubject.observed).toBeFalse();
    expect(pollSubject2.observed).toBeTrue();
    expect(component.activeTurnId()).toBe('turn-opt-2');

    assistantServiceSpy.createConversation.and.returnValue(
      of({conversation: mockConversation2}),
    );
    component.onNewConversation();
    expect(pollSubject2.observed).toBeFalse();
    expect(component.isStreaming()).toBeFalse();
  });

  it('should navigate when cid changes and skip navigation when syncUrlCid matches current queryParamMap cid', () => {
    const route = TestBed.inject(ActivatedRoute);
    const router = TestBed.inject(Router);
    const navSpy = spyOn(router, 'navigate');

    (route.snapshot as unknown as {queryParamMap: ParamMap}).queryParamMap =
      convertToParamMap({
        'cid': 'conv-1',
      });
    component.selectConversation('conv-2');
    expect(component.selectedConversationId()).toBe('conv-2');
    expect(navSpy).toHaveBeenCalledWith(['/assistant'], {
      queryParams: {'cid': 'conv-2'},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });

    navSpy.calls.reset();
    (route.snapshot as unknown as {queryParamMap: ParamMap}).queryParamMap =
      convertToParamMap({
        'cid': 'conv-2',
      });
    component.selectConversation('conv-2');
    expect(navSpy).not.toHaveBeenCalled();
  });

  it('should submit action decision and update turn action confirmation status', () => {
    const updatedAction: ActionConfirmationRequest = {
      actionId: 'action-1',
      toolName: 'reboot_device',
      targetEntity: 'device-001',
      summary: 'Reboot device-001 in lab atc-core',
      riskLevel: ActionRiskLevel.HIGH,
      status: ActionDecisionStatus.APPROVED,
    };
    assistantServiceSpy.submitActionDecision.and.returnValue(
      of({actionConfirmation: updatedAction}),
    );

    const turn = component.activeTurns()[0];
    const action = turn.actionConfirmations![0];
    component.onActionDecision(turn, action, true);

    expect(assistantServiceSpy.submitActionDecision).toHaveBeenCalledWith({
      conversationId: 'conv-1',
      turnId: 'turn-1',
      actionId: 'action-1',
      approved: true,
    });
    expect(component.activeTurns()[0].actionConfirmations?.[0].status).toBe(
      ActionDecisionStatus.APPROVED,
    );
    expect(component.activeTurns()[0].actionConfirmations?.[1].status).toBe(
      ActionDecisionStatus.PENDING,
    );

    assistantServiceSpy.submitActionDecision.calls.reset();
    component.selectedConversationId.set(null);
    component.onActionDecision(turn, action, false);
    expect(assistantServiceSpy.submitActionDecision).not.toHaveBeenCalled();
  });

  it('should merge streamed actionConfirmation events during polling', () => {
    assistantServiceSpy.sendMessage.and.returnValue(
      of({
        conversationId: 'conv-1',
        turnId: 'turn-stream-act',
        status: TurnStatus.RUNNING,
      }),
    );
    const pollSubject = new Subject<PollEventsResponse>();
    assistantServiceSpy.pollEvents.and.returnValue(pollSubject.asObservable());

    component.onSendMessage('Run action');

    const newAction: ActionConfirmationRequest = {
      actionId: 'action-stream-1',
      toolName: 'drain_device',
      targetEntity: 'device-003',
      summary: 'Drain device-003',
      riskLevel: ActionRiskLevel.MEDIUM,
      status: ActionDecisionStatus.PENDING,
    };
    pollSubject.next({
      conversationId: 'conv-1',
      turnId: 'turn-stream-act',
      events: [{seq: 1, actionConfirmation: newAction}],
      nextEventSeq: 2,
      isTurnDone: false,
      turnStatus: TurnStatus.RUNNING,
    });
    expect(
      component.activeTurns()[1].actionConfirmations?.length,
    ).toBe(1);
    expect(
      component.activeTurns()[1].actionConfirmations?.[0].status,
    ).toBe(ActionDecisionStatus.PENDING);

    const resolvedAction: ActionConfirmationRequest = {
      ...newAction,
      status: ActionDecisionStatus.APPROVED,
    };
    pollSubject.next({
      conversationId: 'conv-1',
      turnId: 'turn-stream-act',
      events: [{seq: 2, actionConfirmation: resolvedAction}],
      nextEventSeq: 3,
      isTurnDone: true,
      turnStatus: TurnStatus.COMPLETED,
    });
    expect(
      component.activeTurns()[1].actionConfirmations?.length,
    ).toBe(1);
    expect(
      component.activeTurns()[1].actionConfirmations?.[0].status,
    ).toBe(ActionDecisionStatus.APPROVED);
  });
});
