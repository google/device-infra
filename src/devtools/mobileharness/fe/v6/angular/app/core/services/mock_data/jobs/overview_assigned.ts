import {
  JobResult,
  JobStatus,
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
      actualUser: 'other_partner@example.com',
      createTime: '2025-07-15T09:00:00Z',
      startTime: '2025-07-15T09:00:12Z',
    },
    session: {
      id: 'b2c3d4e5-f6a7-8901-bcde-f0123456789a',
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
      summaryLines: ['Assigned after 12.0s: 10.0s queued, 2.0s preparing.'],
      originTime: '2025-07-15T09:00:00Z',
      finishTime: '2025-07-15T09:00:12Z',
      live: true,
      axis: {
        tickStepPercent: 25,
        ticks: [
          {
            leftPercent: 0,
            label: '0s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_START',
          },
          {
            leftPercent: 50,
            label: '+6s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_CENTER',
          },
          {
            leftPercent: 100,
            label: '+12s',
            alignment: 'TIMELINE_TICK_ALIGNMENT_END',
          },
        ],
      },
      splitSegments: [
        {
          focusKey: 'queued',
          label: 'Queued',
          fill: 'TIMELINE_FILL_QUEUED',
          flexWeight: 10000,
          durationText: '10.0s',
          shareText: '83%',
        },
        {
          focusKey: 'preparing',
          label: 'Preparing',
          fill: 'TIMELINE_FILL_SETUP',
          flexWeight: 2000,
          durationText: '2.0s',
          shareText: '17%',
        },
      ],
      jobTimeline: {
        totalDurationText: '12.0s',
        activityBars: [
          {
            focusKey: 'queued',
            label: 'Queued',
            fill: 'TIMELINE_FILL_QUEUED',
            shareText: '83%',
            interval: {
              startTime: '2025-07-15T09:00:00Z',
              endTime: '2025-07-15T09:00:10Z',
              leftPercent: 0,
              widthPercent: 83.333,
              startOffsetText: '+0ms',
              endOffsetText: '+10.0s',
              durationText: '10.0s',
            },
          },
          {
            focusKey: 'preparing',
            label: 'Preparing',
            fill: 'TIMELINE_FILL_SETUP',
            shareText: '17%',
            interval: {
              startTime: '2025-07-15T09:00:10Z',
              endTime: '2025-07-15T09:00:12Z',
              leftPercent: 83.333,
              widthPercent: 16.667,
              startOffsetText: '+10.0s',
              endOffsetText: '+12.0s',
              durationText: '2.0s',
            },
          },
        ],
      },
    },
  },
  log: '[09:00:00] Job created and submitted to queue.\n[09:00:10] Device allocation request satisfied: AndroidRealDevice (Pixel 9 Pro)\n[09:00:12] STATUS: ASSIGNED. Waiting for lab server and test runner to initialize binary execution...',
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=resource.type%3D%22mobileharness_job%22%20AND%20labels.job_id%3D%22assigned-99e8-4d89-8260-8012626e2eef%22',
};
