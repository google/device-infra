import {
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
} from '@deviceinfra/app/core/models/common_models';
import {MockJobScenario} from '../models';

/** A mock job scenario representing a job that has been assigned devices. */
export const SCENARIO_JOB_ASSIGNED: MockJobScenario = {
  id: 'job-assigned-1',
  scenarioName: 'Assigned Job',
  actions: {
    kill: {
      enabled: false,
      visible: true,
      tooltip:
        'Permission denied. Only the owner (other_partner) or admins can kill this job.',
      isReady: true,
    },
  },
  overview: {
    name: 'pending_pixel_9_pro_smoke_run',
    executionDetails: {
      user: 'other_partner',
      actualUser: 'other_partner@google.com',
      createTime: '2025-07-15T09:00:00Z',
      startTime: '2025-07-15T09:00:12Z',
    },
    session: {
      id: 'b2c3d4e5-f6a7-8901-bcde-f0123456789a',
      name: 'Smoke Test Suite (Pixel 9)',
      status: SessionStatus.SESSION_STATUS_RUNNING,
      result: SessionResult.SESSION_RESULT_UNSPECIFIED,
    },
    config: {
      devices: {
        device: [
          {
            deviceType: 'AndroidRealDevice',
            driver: 'AndroidInstrumentation',
            dimensions: {
              'sdk_version': '35',
              'brand': 'Google',
              'model': 'Pixel 9 Pro',
            },
          },
        ],
      },
      settings: {
        totalTestCount: 2,
        priority: 'HIGH',
      },
      params: {
        'test_tag': 'smoke',
      },
    },
    tests: {
      test: [],
    },
    id: 'assigned-99e8-4d89-8260-8012626e2eef',
    status: JobStatus.JOB_STATUS_ASSIGNED,
    result: JobResult.JOB_RESULT_UNSPECIFIED,
    spongeLink: 'http://sponge2/assigned-99e8-4d89-8260-8012626e2eef',
    properties: {
      'trigger': 'presubmit',
      'allocated_device_id': '99061FFAZ008PP',
    },
    timingBreakdown: {
      createTime: '2025-07-15T09:00:00Z',
      startTime: '2025-07-15T09:00:12Z',
      stages: [
        {
          name: 'Queueing',
          startTime: '2025-07-15T09:00:00Z',
          endTime: '2025-07-15T09:00:10Z',
        },
        {
          name: 'Device Assigned',
          startTime: '2025-07-15T09:00:10Z',
          endTime: '2025-07-15T09:00:12Z',
        },
      ],
    },
  },
  log: '[09:00:00] Job created and submitted to queue.\n[09:00:10] Device allocation request satisfied: AndroidRealDevice (Pixel 9 Pro)\n[09:00:12] STATUS: ASSIGNED. Waiting for lab server and test runner to initialize binary execution...',
  cloudLogLink:
    'https://console.cloud.google.com/logs/query;query=resource.type%3D%22mobileharness_job%22%20AND%20labels.job_id%3D%22assigned-99e8-4d89-8260-8012626e2eef%22',
};
