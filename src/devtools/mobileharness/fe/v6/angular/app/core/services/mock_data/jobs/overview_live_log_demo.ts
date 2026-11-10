import {
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/common_models';
import {MockJobScenario, MockLogChunk} from '../models';

function buildInitialWrappedJobLog(): string {
  const lines: string[] = [];
  for (let i = 0; i < 90; i++) {
    const mm = String(Math.floor(i / 60)).padStart(2, '0');
    const ss = String(i % 60).padStart(2, '0');
    const ts = `[10:${mm}:${ss}]`;
    if (i === 15) {
      lines.push(
        `${ts} [Job Line #015 WRAPPED 9KB ALLOCATION JSON] {"allocated_devices":` +
          '"DEVICE_SPEC_ENTRY_'.repeat(450) +
          '"}',
      );
    } else if (i === 48) {
      lines.push(
        `${ts} [Job Line #048 WRAPPED 5KBRUNNER FLAGS] --jvm_flags=` +
          'Dmobileharness.flag.segment='.repeat(180),
      );
    } else if (i >= 80) {
      lines.push(
        `${ts} [Job Line #${String(i).padStart(3, '0')} BOTTOM-CJK]\t\t\t\t作业调度器实时日志：正在监控子测试运行进度 (#${i}) ${'调度心跳正常_'.repeat(10)}`,
      );
    } else {
      lines.push(
        `${ts} [Job Line #${String(i).padStart(3, '0')}] Job coordinator checkpoint ${i} recorded.`,
      );
    }
  }
  return lines.join('\n') + '\n';
}

function buildLiveJobLogChunks(): MockLogChunk[] {
  const chunks: MockLogChunk[] = [
    {
      content: buildInitialWrappedJobLog(),
    },
    {
      content:
        '[10:01:35] [JOB LIVE POLL #1 PARTIAL] Collecting multi-device heartbeat metrics: \t\t' +
        '作业分片增量数据_'.repeat(16),
    },
    {
      content:
        'HEARTBEAT_OK\n' +
        '[10:01:40] [JOB LIVE POLL #2] \t\t\t子测试仍在执行中，长行折行与底部跟随验证 ' +
        '持续输出作业状态_'.repeat(15) +
        '\n',
    },
  ];

  for (let poll = 3; poll <= 36; poll++) {
    const totalSec = 90 + poll * 5;
    const mm = String(Math.floor(totalSec / 60)).padStart(2, '0');
    const ss = String(totalSec % 60).padStart(2, '0');
    const isFinal = poll === 36;
    chunks.push({
      content:
        `[10:${mm}:${ss}] [JOB LIVE POLL #${poll}] Active sub-tests: 2 running, 1 passed.\n` +
        `[10:${mm}:${ss}] [JOB LIVE POLL #${poll} WRAPPED]\t\t\tShardSummary=${'JOB_SHARD_METRICS_'.repeat(18)} (round=${poll}/36)\n` +
        (isFinal
          ? `[10:${mm}:${ss}] [JOB DONE] All 36 live job log polling rounds completed.\n`
          : ''),
      isDone: isFinal,
    });
  }
  return chunks;
}

/**
 * Running job scenario with wrapped multi-KB lines, bottom CJK/tab lines,
 * 5-second live chunk streaming, and links to all 3 live log demo child tests.
 */
export const SCENARIO_JOB_LIVE_LOG_DEMO: MockJobScenario = {
  id: 'job-live-log-demo',
  scenarioName: 'Live Wrapped Log Demo Job (Running)',
  logChunks: buildLiveJobLogChunks(),
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=labels.job_id%3D%22job-live-log-demo%22',
  overview: {
    id: 'job-live-log-demo',
    name: 'live_wrapped_log_verification_suite',
    status: JobStatus.JOB_STATUS_RUNNING,
    result: JobResult.JOB_RESULT_UNSPECIFIED,
    spongeLink: 'http://sponge2/job-live-log-demo',
    executionDetails: {
      user: 'qiupingf',
      actualUser: 'qiupingf',
      createTime: '2026-11-09T10:00:00Z',
      startTime: '2026-11-09T10:00:01Z',
    },
    config: {
      devices: {
        device: [
          {
            deviceType: 'AndroidRealDevice',
            driver: 'AndroidInstrumentation',
          },
        ],
      },
      settings: {
        totalTestCount: 3,
      },
      params: {},
    },
    tests: {
      test: [
        {
          id: 'test-live-wrap-demo',
          name: 'com.example.mobileharness.LiveWrappedScrollTest#testContinuousWrappedAndStayAtBottom',
          status: TestStatus.TEST_STATUS_RUNNING,
          result: TestResult.TEST_RESULT_UNSPECIFIED,
          startTime: '2026-11-09T10:00:02Z',
          devices: {
            device: [{id: '18261FDF6003KC'}],
          },
        },
        {
          id: 'test-live-reset-demo',
          name: 'com.example.mobileharness.LiveWrappedScrollTest#testResetWithSameBanner',
          status: TestStatus.TEST_STATUS_RUNNING,
          result: TestResult.TEST_RESULT_UNSPECIFIED,
          startTime: '2026-11-09T10:00:01Z',
          devices: {
            device: [{id: '18261FDF6003KC'}],
          },
        },
        {
          id: 'test-single-line-resize-demo',
          name: 'com.example.mobileharness.LiveWrappedScrollTest#testSingleLineJsonResize',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2026-11-09T10:00:01Z',
          devices: {
            device: [{id: '18261FDF6003KC'}],
          },
        },
      ],
    },
    properties: {
      'demo_suite': 'virtual_scroll_live_and_wrapped_verification',
    },
  },
};
