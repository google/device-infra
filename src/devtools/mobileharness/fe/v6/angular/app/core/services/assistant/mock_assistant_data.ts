/**
 * @fileoverview Mock data and fixtures for OmniLab Assistant testing and fake service.
 */

import {
  AssistantEvent,
  Conversation,
  ConversationMode,
  TurnStatus,
} from '../../models/assistant';

/** Mock conversation for fleet query. */
export const MOCK_CONVERSATION_FLEET: Conversation = {
  conversationId: 'conv_fleet_001',
  title: 'Idle Pixel 8 Devices in Atlanta',
  mode: ConversationMode.GENERAL_USER,
  userLdap: 'mock_user',
  createTime: '2026-09-09T08:30:00Z',
  updateTime: '2026-09-09T08:31:15Z',
  turns: [
    {
      turnId: 'turn_fleet_001_1',
      userPrompt: 'Find idle Pixel 8 devices in Atlanta lab',
      status: TurnStatus.COMPLETED,
      startTime: '2026-09-09T08:30:05Z',
      endTime: '2026-09-09T08:30:12Z',
      thoughts:
        'Analyzing fleet query for idle Pixel 8 devices in Atlanta (atl). Querying fleet inventory API with dimension filters.',
      responseMarkdown:
        'Found **12 idle Pixel 8 devices** in Atlanta (`atl`).\n\n' +
        '| Device ID | Host | Battery | Status |\n' +
        '|---|---|---|---|\n' +
        '| `pixel8-atl-01` | `lab-host-05` | 94% | IDLE |\n' +
        '| `pixel8-atl-02` | `lab-host-05` | 88% | IDLE |\n' +
        '| `pixel8-atl-03` | `lab-host-06` | 99% | IDLE |\n\n' +
        'All devices are online, healthy, and ready for scheduling.',
      toolExecutions: [
        {
          callId: 'call_search_001',
          toolName: 'SearchFleet',
          argumentsJson:
            '{"dimensions": {"model": "Pixel 8", "location": "atl"}, "status": "IDLE"}',
          success: true,
          resultJson:
            '{"count": 12, "devices": [{"id": "pixel8-atl-01", "host": "lab-host-05", "battery": 94}, {"id": "pixel8-atl-02", "host": "lab-host-05", "battery": 88}]}',
        },
      ],
      outboundLinks: [
        {
          title: 'View 12 Idle Pixel 8s in Atlanta',
          url: '/devices?dimensions=model:Pixel 8,location:atl&status=IDLE',
        },
      ],
    },
  ],
};

/** Mock conversation for host health triage. */
export const MOCK_CONVERSATION_HOST: Conversation = {
  conversationId: 'conv_host_002',
  title: 'Host lab-host-05 Health Triage',
  mode: ConversationMode.TEAM_ASSISTANT,
  userLdap: 'mock_user',
  createTime: '2026-09-08T16:20:00Z',
  updateTime: '2026-09-08T16:21:40Z',
  turns: [
    {
      turnId: 'turn_host_002_1',
      userPrompt: 'Check health status of host lab-host-05',
      status: TurnStatus.COMPLETED,
      startTime: '2026-09-08T16:20:10Z',
      endTime: '2026-09-08T16:20:18Z',
      thoughts:
        'Inspecting host daemon health, connected USB hubs, and memory usage on lab-host-05.',
      responseMarkdown:
        'Host `lab-host-05` is **healthy** and operational.\n\n' +
        '- **Daemon Version**: 6.4.2\n' +
        '- **Connected Devices**: 8/8 USB ports active\n' +
        '- **Memory Usage**: 38% (healthy)\n' +
        '- **Quarantine Status**: Normal',
      toolExecutions: [
        {
          callId: 'call_host_002',
          toolName: 'GetHostHealth',
          argumentsJson: '{"hostname": "lab-host-05"}',
          success: true,
          resultJson:
            '{"status": "HEALTHY", "daemon_version": "6.4.2", "devices_connected": 8}',
        },
      ],
      outboundLinks: [
        {
          title: 'Open Host Details for lab-host-05',
          url: '/hosts/lab-host-05',
        },
      ],
    },
  ],
};

/** Initial default conversations returned by the mock service. */
export const DEFAULT_MOCK_CONVERSATIONS: Conversation[] = [
  MOCK_CONVERSATION_FLEET,
  MOCK_CONVERSATION_HOST,
];

/**
 * Generates an ordered sequence of simulated events for a given turn.
 */
export function createSimulatedTurnEvents(
  turnId: string,
  userPrompt: string,
): AssistantEvent[] {
  const isFleetQuery =
    userPrompt.toLowerCase().includes('device') ||
    userPrompt.toLowerCase().includes('pixel') ||
    userPrompt.toLowerCase().includes('fleet');

  if (isFleetQuery) {
    return [
      {
        seq: 1,
        timestamp: new Date().toISOString(),
        thoughtDelta: {
          text: 'Analyzing prompt and extracting fleet dimension constraints...',
        },
      },
      {
        seq: 2,
        timestamp: new Date().toISOString(),
        thoughtDelta: {
          text: ' Querying MobileHarness Lab inventory for matching devices...',
        },
      },
      {
        seq: 3,
        timestamp: new Date().toISOString(),
        toolCall: {
          callId: `call_${turnId}_1`,
          toolName: 'SearchFleet',
          argumentsJson: '{"dimensions": {"status": "IDLE"}, "page_size": 10}',
        },
      },
      {
        seq: 4,
        timestamp: new Date().toISOString(),
        toolResult: {
          callId: `call_${turnId}_1`,
          toolName: 'SearchFleet',
          success: true,
          resultJson:
            '{"total": 12, "devices": [{"id": "pixel8-atl-01", "model": "Pixel 8", "status": "IDLE"}, {"id": "pixel8-atl-02", "model": "Pixel 8", "status": "IDLE"}]}',
        },
      },
      {
        seq: 5,
        timestamp: new Date().toISOString(),
        thoughtDelta: {
          text: ' Formatting retrieved device records into structured markdown table...',
        },
      },
      {
        seq: 6,
        timestamp: new Date().toISOString(),
        messageDelta: {
          text:
            'Found **12 matching devices** in the fleet matching your query:\n\n' +
            '| Device ID | Model | Status |\n' +
            '|---|---|---|\n' +
            '| `pixel8-atl-01` | Pixel 8 | IDLE |\n' +
            '| `pixel8-atl-02` | Pixel 8 | IDLE |\n' +
            '| `pixel8-atl-03` | Pixel 8 | IDLE |\n\n' +
            'All devices are powered on and ready for test execution.',
        },
      },
      {
        seq: 7,
        timestamp: new Date().toISOString(),
        outboundLink: {
          title: 'View Filtered Devices Table',
          url: '/devices?status=IDLE',
        },
      },
      {
        seq: 8,
        timestamp: new Date().toISOString(),
        statusUpdate: {
          status: TurnStatus.COMPLETED,
        },
      },
    ];
  }

  // Generic fallback simulated turn
  return [
    {
      seq: 1,
      timestamp: new Date().toISOString(),
      thoughtDelta: {
        text: 'Processing your request and consulting OmniLab documentation...',
      },
    },
    {
      seq: 2,
      timestamp: new Date().toISOString(),
      thoughtDelta: {
        text: ' Formulating answer and checking system configuration...',
      },
    },
    {
      seq: 3,
      timestamp: new Date().toISOString(),
      messageDelta: {
        text:
          `I have processed your query: **"${userPrompt}"**.\n\n` +
          'All lab subsystems are reporting normal operational telemetry. ' +
          'Let me know if you would like me to inspect specific hosts, sessions, or devices.',
      },
    },
    {
      seq: 4,
      timestamp: new Date().toISOString(),
      outboundLink: {
        title: 'Open OmniLab Home',
        url: '/home',
      },
    },
    {
      seq: 5,
      timestamp: new Date().toISOString(),
      statusUpdate: {
        status: TurnStatus.COMPLETED,
      },
    },
  ];
}
