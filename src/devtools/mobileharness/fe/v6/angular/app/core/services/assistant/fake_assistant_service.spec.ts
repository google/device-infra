/**
 * @fileoverview Unit tests for FakeAssistantService.
 */

import {TestBed} from '@angular/core/testing';
import {ConversationMode, TurnStatus} from '../../models/assistant';
import {
  FAKE_ASSISTANT_SERVICE_OPTIONS,
  FakeAssistantService,
} from './fake_assistant_service';
import {MOCK_CONVERSATION_FLEET} from './mock_assistant_data';

describe('FakeAssistantService', () => {
  let service: FakeAssistantService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        FakeAssistantService,
        {
          provide: FAKE_ASSISTANT_SERVICE_OPTIONS,
          useValue: {chunkSize: 3},
        },
      ],
    });
    service = TestBed.inject(FakeAssistantService);
  });

  describe('listConversations', () => {
    it('returns seeded conversations sorted by updateTime desc', (done) => {
      service.listConversations().subscribe((res) => {
        expect(res.conversations.length).toBeGreaterThanOrEqual(2);
        expect(res.conversations[0].conversationId).toBe('conv_fleet_001');
        done();
      });
    });

    it('respects pageSize option', (done) => {
      service.listConversations({pageSize: 1}).subscribe((res) => {
        expect(res.conversations.length).toBe(1);
        expect(res.nextPageToken).toBeDefined();
        done();
      });
    });
  });

  describe('createConversation', () => {
    it('creates a new conversation and persists it', (done) => {
      service
        .createConversation({
          title: 'Custom Test Chat',
          mode: ConversationMode.TEAM_ASSISTANT,
        })
        .subscribe((res) => {
          expect(res.conversation.title).toBe('Custom Test Chat');
          expect(res.conversation.mode).toBe(ConversationMode.TEAM_ASSISTANT);
          expect(res.conversation.conversationId).toContain('conv_');

          // Verify it can be retrieved
          service
            .getConversation(res.conversation.conversationId)
            .subscribe((getRes) => {
              expect(getRes.conversation.title).toBe('Custom Test Chat');
              done();
            });
        });
    });
  });

  describe('getConversation', () => {
    it('retrieves an existing conversation with its turns', (done) => {
      service
        .getConversation(MOCK_CONVERSATION_FLEET.conversationId)
        .subscribe((res) => {
          expect(res.conversation.conversationId).toBe(
            MOCK_CONVERSATION_FLEET.conversationId,
          );
          expect(res.conversation.turns?.length).toBe(1);
          expect(res.conversation.turns?.[0].status).toBe(TurnStatus.COMPLETED);
          done();
        });
    });

    it('returns an error when conversation is not found', (done) => {
      service.getConversation('non_existent_id').subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (err) => {
          expect(err.message).toContain('Conversation not found');
          done();
        },
      });
    });
  });

  describe('sendMessage and pollEvents', () => {
    it('initiates a turn and streams events until completion', (done) => {
      const convId = MOCK_CONVERSATION_FLEET.conversationId;

      service
        .sendMessage({
          conversationId: convId,
          userPrompt: 'Find idle Pixel 8 devices in lab',
        })
        .subscribe((sendRes) => {
          expect(sendRes.conversationId).toBe(convId);
          expect(sendRes.turnId).toContain('turn_');
          expect(sendRes.status).toBe(TurnStatus.RUNNING);

          const turnId = sendRes.turnId;

          // First poll: receives first batch of up to 3 events
          service
            .pollEvents({
              conversationId: convId,
              turnId,
              lastEventSeq: 0,
            })
            .subscribe((pollRes1) => {
              expect(pollRes1.events.length).toBe(3);
              expect(pollRes1.events[0].seq).toBe(1);
              expect(pollRes1.events[0].thoughtDelta).toBeDefined();
              expect(pollRes1.isTurnDone).toBeFalse();
              expect(pollRes1.turnStatus).toBe(TurnStatus.RUNNING);

              const lastSeq = pollRes1.nextEventSeq;

              // Second poll: receives next batch
              service
                .pollEvents({
                  conversationId: convId,
                  turnId,
                  lastEventSeq: lastSeq,
                })
                .subscribe((pollRes2) => {
                  expect(pollRes2.events.length).toBe(3);
                  expect(pollRes2.events[0].seq).toBe(lastSeq + 1);

                  const secondSeq = pollRes2.nextEventSeq;

                  // Third poll: receives remaining events and finishes
                  service
                    .pollEvents({
                      conversationId: convId,
                      turnId,
                      lastEventSeq: secondSeq,
                    })
                    .subscribe((pollRes3) => {
                      expect(pollRes3.isTurnDone).toBeTrue();
                      expect(pollRes3.turnStatus).toBe(TurnStatus.COMPLETED);

                      // Verify that the conversation record now reflects the completed turn
                      service.getConversation(convId).subscribe((getRes) => {
                        const turns = getRes.conversation.turns ?? [];
                        const completedTurn = turns.find(
                          (t) => t.turnId === turnId,
                        );
                        expect(completedTurn).toBeDefined();
                        expect(completedTurn?.status).toBe(
                          TurnStatus.COMPLETED,
                        );
                        expect(completedTurn?.responseMarkdown).toContain(
                          'matching devices',
                        );
                        done();
                      });
                    });
                });
            });
        });
    });

    it('filters out events with seq <= lastEventSeq', (done) => {
      const convId = MOCK_CONVERSATION_FLEET.conversationId;

      service
        .sendMessage({
          conversationId: convId,
          userPrompt: 'Help with general query',
        })
        .subscribe((sendRes) => {
          const turnId = sendRes.turnId;

          // Poll with lastEventSeq = 2 directly (simulating client reconnect)
          service
            .pollEvents({
              conversationId: convId,
              turnId,
              lastEventSeq: 2,
            })
            .subscribe((pollRes) => {
              // Emitted batch has 3 items (seq 1, 2, 3), but only seq 3 is strictly > 2
              expect(pollRes.events.length).toBe(1);
              expect(pollRes.events[0].seq).toBe(3);
              done();
            });
        });
    });
  });

  describe('cancelTurn', () => {
    it('cancels an active turn execution and stops polling', (done) => {
      const convId = MOCK_CONVERSATION_FLEET.conversationId;

      service
        .sendMessage({
          conversationId: convId,
          userPrompt: 'Long running query',
        })
        .subscribe((sendRes) => {
          const turnId = sendRes.turnId;

          service
            .cancelTurn({
              conversationId: convId,
              turnId,
            })
            .subscribe((cancelRes) => {
              expect(cancelRes.success).toBeTrue();

              // Polling cancelled turn should return CANCELLED status and isTurnDone
              service
                .pollEvents({
                  conversationId: convId,
                  turnId,
                  lastEventSeq: 0,
                })
                .subscribe((pollRes) => {
                  expect(pollRes.turnStatus).toBe(TurnStatus.CANCELLED);
                  expect(pollRes.isTurnDone).toBeTrue();
                  done();
                });
            });
        });
    });
  });
});
