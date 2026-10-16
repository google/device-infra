import {
  AllSiteIdType,
  AllSiteMatch,
  ResolveAllSiteQueryRequest,
  ResolveAllSiteQueryResponse,
} from '@deviceinfra/app/core/models/search';
import {MOCK_DEVICE_SCENARIOS} from '../devices';
import {MOCK_HOST_SCENARIOS} from '../hosts';
import {MOCK_JOB_SCENARIOS} from '../jobs';
import {MOCK_SESSION_SCENARIOS} from '../sessions';
import {MOCK_TEST_SCENARIOS} from '../tests';

/**
 * Multi-host placement map for devices that intentionally appear across multiple lab hosts
 * in the All-Site Search Ambiguous showcase (e.g., `device-id-for-ambiguous-1`).
 * Every device ID here exists in `MOCK_DEVICE_SCENARIOS` and every host exists in `MOCK_HOST_SCENARIOS`.
 */
const MULTI_HOST_DEVICE_PLACEMENTS: Readonly<
  Record<string, readonly string[]>
> = {
  'device-id-for-ambiguous-1': [
    'host-for-ambiguous-device-1.example.com',
    'host-for-ambiguous-device-2.example.com',
    'host-for-ambiguous-device-3.example.com',
  ],
};

/**
 * Filters a list of AllSiteMatch items by the optional `idType` scope in the request.
 *
 * Input:
 *   - matches: AllSiteMatch[] containing candidate matches across all entity types.
 *   - idType: Optional AllSiteIdType restricting results to a single entity type.
 * Output:
 *   - AllSiteMatch[] filtered to the requested scope (or unmodified when unspecified).
 * Explanation:
 *   When the user selects a specific ID scope in the Header Jump Bar (e.g., Device ID or Host name),
 *   only matches of that corresponding entity type are returned.
 */
function filterMatchesByScope(
  matches: AllSiteMatch[],
  idType?: AllSiteIdType,
): AllSiteMatch[] {
  if (!idType || idType === AllSiteIdType.ID_TYPE_UNSPECIFIED) {
    return matches;
  }
  return matches.filter((m) => {
    switch (idType) {
      case AllSiteIdType.ID_TYPE_DEVICE_ID:
        return !!m.device;
      case AllSiteIdType.ID_TYPE_HOST_NAME:
        return !!m.host;
      case AllSiteIdType.ID_TYPE_TEST_ID:
        return !!m.test;
      case AllSiteIdType.ID_TYPE_JOB_ID:
        return !!m.job;
      case AllSiteIdType.ID_TYPE_SESSION_ID:
        return !!m.session;
      default:
        return true;
    }
  });
}

/**
 * Resolves an All-Site Search query against the unified mock detail registries
 * (`MOCK_DEVICE_SCENARIOS`, `MOCK_HOST_SCENARIOS`, `MOCK_TEST_SCENARIOS`,
 * `MOCK_JOB_SCENARIOS`, `MOCK_SESSION_SCENARIOS`).
 *
 * Input:
 *   - request: ResolveAllSiteQueryRequest containing `query` and optional `idType` scope.
 * Output:
 *   - ResolveAllSiteQueryResponse with `matches` array where every match is guaranteed
 *     to resolve in its corresponding Fake Detail Service (`FakeDeviceService`,
 *     `FakeHostService`, `FakeTestService`, `FakeJobService`, `FakeSessionService`).
 * Explanation:
 *   1. Searches `MOCK_DEVICE_SCENARIOS` (expanding multi-host placements when applicable).
 *   2. Searches `MOCK_HOST_SCENARIOS` (only hosts with `overview` defined) by exact FQDN
 *      or short hostname prefix.
 *   3. Searches `MOCK_TEST_SCENARIOS`, `MOCK_JOB_SCENARIOS`, and `MOCK_SESSION_SCENARIOS`
 *      by exact case-insensitive ID.
 *   4. Applies scope filtering if `request.idType` is provided.
 */
export function resolveMockAllSiteQuery(
  request: ResolveAllSiteQueryRequest,
): ResolveAllSiteQueryResponse {
  const rawQuery = (request.query || '').trim();
  if (!rawQuery) {
    return {matches: []};
  }

  const qLower = rawQuery.toLowerCase();
  const matches: AllSiteMatch[] = [];
  const seenKeys = new Set<string>();

  // 1. Check Mock Device Scenarios
  for (const wrapper of MOCK_DEVICE_SCENARIOS) {
    let scenario;
    try {
      scenario = wrapper.factory(0);
    } catch {
      continue;
    }
    const deviceId = scenario.id || wrapper.id;
    if (deviceId && deviceId.toLowerCase() === qLower) {
      const model = scenario.overview?.basicInfo?.model || 'Pixel';
      const hostPlacements = MULTI_HOST_DEVICE_PLACEMENTS[qLower] ?? [
        scenario.overview?.host?.name || 'mh-lab-01.prod.example.com',
      ];
      for (const hostName of hostPlacements) {
        const dedupKey = `device:${deviceId.toLowerCase()}:${hostName.toLowerCase()}`;
        if (!seenKeys.has(dedupKey)) {
          seenKeys.add(dedupKey);
          matches.push({
            device: {
              deviceId,
              hostName,
              model,
              universe: 'google_1p',
            },
          });
        }
      }
    }
  }

  // 2. Check Mock Host Scenarios (only hosts with overview so HostDetail page succeeds)
  for (const wrapper of MOCK_HOST_SCENARIOS) {
    let scenario;
    try {
      scenario = wrapper.factory(0);
    } catch {
      continue;
    }
    if (!scenario.overview) continue;
    const hostName = scenario.hostName || wrapper.hostName;
    if (!hostName) continue;
    const hostLower = hostName.toLowerCase();
    const shortHostLower = hostLower.split('.')[0];
    if (hostLower === qLower || shortHostLower === qLower) {
      const dedupKey = `host:${hostLower}`;
      if (!seenKeys.has(dedupKey)) {
        seenKeys.add(dedupKey);
        const attachedDeviceCount = scenario.deviceSummaries?.length ?? 0;
        const hostIp = scenario.overview.ip || '172.16.10.10';
        matches.push({
          host: {
            hostName,
            attachedDeviceCount,
            hostIp,
            universe: 'google_1p',
          },
        });
      }
    }
  }

  // 3. Check Mock Test Scenarios
  for (const scenario of MOCK_TEST_SCENARIOS) {
    const testId = scenario.id || scenario.overview?.id;
    if (testId && testId.toLowerCase() === qLower) {
      const jobId =
        scenario.overview?.job?.id || 'b65cadd7-6ad6-440e-a3b7-bfe1948557e6';
      const dedupKey = `test:${testId.toLowerCase()}`;
      if (!seenKeys.has(dedupKey)) {
        seenKeys.add(dedupKey);
        matches.push({
          test: {
            testId,
            jobId,
          },
        });
      }
    }
  }

  // 4. Check Mock Job Scenarios
  for (const scenario of MOCK_JOB_SCENARIOS) {
    const scenariosIds = [scenario.id, scenario.overview?.id].filter(
      (id): id is string => !!id,
    );
    const matchedId = scenariosIds.find((id) => id.toLowerCase() === qLower);
    if (matchedId) {
      const dedupKey = `job:${matchedId.toLowerCase()}`;
      if (!seenKeys.has(dedupKey)) {
        seenKeys.add(dedupKey);
        matches.push({
          job: {
            jobId: matchedId,
          },
        });
      }
    }
  }

  // 5. Check Mock Session Scenarios
  for (const scenario of MOCK_SESSION_SCENARIOS) {
    const scenarioIds = [scenario.id, scenario.overview?.id].filter(
      (id): id is string => !!id,
    );
    const matchedId = scenarioIds.find((id) => id.toLowerCase() === qLower);
    if (matchedId) {
      const dedupKey = `session:${matchedId.toLowerCase()}`;
      if (!seenKeys.has(dedupKey)) {
        seenKeys.add(dedupKey);
        matches.push({
          session: {
            sessionId: matchedId,
          },
        });
      }
    }
  }

  return {
    matches: filterMatchesByScope(matches, request.idType),
  };
}
