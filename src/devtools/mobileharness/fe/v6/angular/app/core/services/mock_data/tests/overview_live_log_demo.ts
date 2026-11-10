import {
  JobStatus,
  TestResult,
  TestStatus,
} from '@deviceinfra/app/core/models/test_overview';
import {MockLogChunk, MockTestScenario} from '../models';

function formatTimestamp(index: number): string {
  const minutes = Math.floor(index / 60);
  const seconds = index % 60;
  const mm = String(minutes).padStart(2, '0');
  const ss = String(seconds).padStart(2, '0');
  return `[10:${mm}:${ss}]`;
}

function buildInitialWrappedTestLog(): string {
  const lines: string[] = [];
  for (let i = 0; i < 120; i++) {
    const ts = formatTimestamp(i);
    if (i === 17) {
      const payload =
        '{"device_prop":"PIXEL_9_PRO_XL","flags":"' + 'A'.repeat(10800) + '"}';
      lines.push(
        `${ts} [Line #017 WRAPPED 11KB JSON] DeviceDimensionSnapshot=${payload}`,
      );
    } else if (i === 60) {
      const cmd = '--es extra_config ' + 'B'.repeat(5800);
      lines.push(
        `${ts} [Line #060 WRAPPED 6KB CMD] adb shell am instrument -w ${cmd}`,
      );
    } else if (i >= 61 && i <= 90) {
      lines.push(
        `${ts} [Line #${String(i).padStart(3, '0')}]   at com.example.mobileharness.runner.StackTraceFrame_${i}.execute(Runner.java:${100 + i})`,
      );
    } else if (i >= 110) {
      lines.push(
        `${ts} [Line #${String(i).padStart(3, '0')} BOTTOM-CJK]\t\t\t\t正在同步测试设备状态与实时产物 (宽字符自动换行验证 #${i}) — tail marker ${'终端输出检查'.repeat(10)}`,
      );
    } else {
      lines.push(
        `${ts} [Line #${String(i).padStart(3, '0')}] Continuous log verification step ${i} completed normally.`,
      );
    }
  }
  return lines.join('\n') + '\n';
}

function buildLiveTestLogChunks(): MockLogChunk[] {
  const chunks: MockLogChunk[] = [
    {
      content: buildInitialWrappedTestLog(),
    },
    {
      // Partial trailing line without `\n` (tests re-measurement when {start, end} stays unchanged).
      content:
        '[10:02:05] [LIVE POLL #1 PARTIAL] Streaming long wrapped command output without newline: \t\t' +
        '实时追加分片内容_'.repeat(18),
    },
    {
      // Completes previous partial line and appends a wrapped CJK line.
      content:
        '实时追加分片完成_END_OF_PARTIAL_LINE\n' +
        '[10:02:10] [LIVE POLL #2] \t\t\t已完成分片合并，继续保持底部对齐与连续滚动验证 ' +
        '长行自动折行测试_'.repeat(16) +
        '\n',
    },
  ];

  for (let poll = 3; poll <= 36; poll++) {
    const sec = String((poll * 5) % 60).padStart(2, '0');
    const min = 2 + Math.floor((poll * 5) / 60);
    const isFinal = poll === 36;
    chunks.push({
      content:
        `[10:0${min}:${sec}] [LIVE POLL #${poll}] Executing live assertion batch #${poll}...\n` +
        `[10:0${min}:${sec}] [LIVE POLL #${poll} WRAPPED]\t\t\t设备实时采样数据=${'LIVE_PAYLOAD_SEGMENT_'.repeat(18)} (poll=${poll}/36)\n` +
        (isFinal
          ? `[10:0${min}:${sec}] [DONE] Live streaming test completed all 36 polling rounds.\n`
          : ''),
      isDone: isFinal,
    });
  }
  return chunks;
}

/**
 * Running test scenario with 120+ lines, multi-KB wrapped lines, bottom CJK/tab
 * lines, and 12 rounds of 5-second live streaming chunks.
 */
export const SCENARIO_TEST_LIVE_WRAP_DEMO: MockTestScenario = {
  id: 'test-live-wrap-demo',
  scenarioName: 'Live Log Demo 1: Running Wrapped + Bottom Stay',
  logChunks: buildLiveTestLogChunks(),
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=labels.test_id%3D%22test-live-wrap-demo%22',
  overview: {
    id: 'test-live-wrap-demo',
    name: 'com.example.mobileharness.LiveWrappedScrollTest#testContinuousWrappedAndStayAtBottom',
    status: TestStatus.TEST_STATUS_RUNNING,
    result: TestResult.TEST_RESULT_UNSPECIFIED,
    job: {
      id: 'job-live-log-demo',
      name: 'live_wrapped_log_verification_suite',
      status: JobStatus.JOB_STATUS_RUNNING,
    },
    devices: {
      device: [{id: '18261FDF6003KC'}],
    },
    host: {
      name: 'host-for-1-hit-1.example.com',
      ip: '172.16.42.18',
    },
    executionDetails: {
      createTime: '2026-11-09T10:00:00Z',
      startTime: '2026-11-09T10:00:02Z',
      updateTime: '2026-11-09T10:02:00Z',
      user: 'qiupingf',
      actualUser: 'qiupingf@example.com',
    },
    properties: {
      'demo_case': 'running_wrapped_scroll_and_bottom_pin',
    },
  },
};

/**
 * Running test scenario exercising `reset: true` where Attempt 2 has `>=` lines
 * and the exact same `Line #0` header as Attempt 1's 6KB wrapped crash dump.
 */
export const SCENARIO_TEST_LIVE_RESET_DEMO: MockTestScenario = {
  id: 'test-live-reset-demo',
  scenarioName: 'Live Log Demo 2: Log Reset (reset=true Same Header)',
  logChunks: [
    {
      content:
        '=== OmniLab Test Runner Banner ===\n' +
        '[10:00:01] [ATTEMPT 1 CRASH DUMP] ' +
        'CRASH_STACK_PAYLOAD_'.repeat(300) +
        '\n[10:00:02] [ATTEMPT 1] Failed — stream will reset in 5 seconds with the same Line #0 banner...\n',
    },
    {
      logReset: true,
      content:
        '=== OmniLab Test Runner Banner ===\n' +
        '[10:00:07] [ATTEMPT 2 AFTER RESET] Stream reset succeeded — stale 6KB crash height cleared!\n' +
        '[10:00:08] [ATTEMPT 2] Device 18261FDF6003KC re-initialized cleanly.\n' +
        '[10:00:09] [ATTEMPT 2] APK installed.\n',
    },
    {
      content:
        '[10:00:12] [ATTEMPT 2] Running smoke verification...\n' +
        '[10:00:14] [ATTEMPT 2 PASS] All checks green after reset.\n',
      isDone: true,
    },
  ],
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=labels.test_id%3D%22test-live-reset-demo%22',
  overview: {
    id: 'test-live-reset-demo',
    name: 'com.example.mobileharness.LiveWrappedScrollTest#testResetWithSameBanner',
    status: TestStatus.TEST_STATUS_RUNNING,
    result: TestResult.TEST_RESULT_UNSPECIFIED,
    job: {
      id: 'job-live-log-demo',
      name: 'live_wrapped_log_verification_suite',
      status: JobStatus.JOB_STATUS_RUNNING,
    },
    devices: {
      device: [{id: '18261FDF6003KC'}],
    },
    host: {
      name: 'host-for-1-hit-1.example.com',
      ip: '172.16.42.18',
    },
    executionDetails: {
      createTime: '2026-11-09T10:00:00Z',
      startTime: '2026-11-09T10:00:01Z',
      updateTime: '2026-11-09T10:00:05Z',
      user: 'qiupingf',
      actualUser: 'qiupingf@example.com',
    },
    properties: {
      'demo_case': 'log_reset_same_first_line',
    },
  },
};

/**
 * Completed 1-line JSON + CJK/tab log replacing `'Loading logs...'` (`{0, 1}` ->
 * `{0, 1}`) for testing initial single-line measurement and browser width resize.
 */
export const SCENARIO_TEST_SINGLE_LINE_RESIZE_DEMO: MockTestScenario = {
  id: 'test-single-line-resize-demo',
  scenarioName: 'Live Log Demo 3: Single-Line Wrapped JSON & Resize',
  log:
    '{"single_line_json":true,"instruction":"Resize browser width narrower and wider to verify zero clipped lines",\t"cjk_summary":"\t\t单行超长宽字符JSON日志在窗口缩放时自动重新测量高度验证_' +
    '宽字符单行负载数据_'.repeat(45) +
    '","tail_marker":"END_OF_SINGLE_LINE_JSON_VISIBLE"}',
  cloudLogLink:
    'https://console.cloud.example.com/logs/query;query=labels.test_id%3D%22test-single-line-resize-demo%22',
  overview: {
    id: 'test-single-line-resize-demo',
    name: 'com.example.mobileharness.LiveWrappedScrollTest#testSingleLineJsonResize',
    status: TestStatus.TEST_STATUS_DONE,
    result: TestResult.TEST_RESULT_PASS,
    job: {
      id: 'job-live-log-demo',
      name: 'live_wrapped_log_verification_suite',
      status: JobStatus.JOB_STATUS_RUNNING,
    },
    devices: {
      device: [{id: '18261FDF6003KC'}],
    },
    host: {
      name: 'host-for-1-hit-1.example.com',
      ip: '172.16.42.18',
    },
    executionDetails: {
      createTime: '2026-11-09T10:00:00Z',
      startTime: '2026-11-09T10:00:01Z',
      endTime: '2026-11-09T10:00:10Z',
      updateTime: '2026-11-09T10:00:10Z',
      user: 'qiupingf',
      actualUser: 'qiupingf@example.com',
    },
    properties: {
      'demo_case': 'single_line_json_and_width_resize',
    },
  },
};
