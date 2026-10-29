/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.platform.android.xts.common.util;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.lab.common.dir.DirUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsPackageUnzipCache.CacheEntry;
import com.google.devtools.mobileharness.shared.util.concurrent.MobileHarnessRunnable;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class XtsPackageUnzipCacheTest {

  private static final String ZIP_PATH_1 = "/data/xts/android-cts-1.zip";
  private static final String ZIP_PATH_2 = "/data/xts/android-cts-2.zip";
  private static final String ZIP_PATH_3 = "/data/xts/android-cts-3.zip";

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  @Rule public final SetFlags flags = new SetFlags();

  @Spy private LocalFileUtil localFileUtil = new LocalFileUtil();
  @Mock private XtsChunkedTestCasesRestorer xtsChunkedTestCasesRestorer;
  @Mock private Clock clock;

  private XtsPackageUnzipCache cache;

  @Before
  public void setUp() throws Exception {
    String publicDir = tmpFolder.newFolder("public_dir").getAbsolutePath();
    String tmpDirRoot = tmpFolder.newFolder("tmp_dir_root").getAbsolutePath();
    flags.set("public_dir", publicDir);
    flags.set("tmp_dir_root", tmpDirRoot);
    flags.set("ats_xts_unzip_cache_enabled", "true");
    flags.set("ats_xts_unzip_cache_max_entries", "3");
    flags.set("ats_xts_unzip_cache_max_size_gb", "100");
    flags.set("ats_xts_unzip_cache_ttl", "24h");
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(1_000L));
    doReturn("unzipped")
        .when(localFileUtil)
        .unzipFile(anyString(), anyString(), any(Duration.class), anyString());
    cache = new XtsPackageUnzipCache(localFileUtil, xtsChunkedTestCasesRestorer, clock);
  }

  @Test
  public void acquire_multipleSessionsSameZip_linksBothSessionsAndPinsActiveLeases()
      throws Exception {
    String session1Dir = DirUtil.getPublicGenDir() + "/session_1/android-cts";
    String session2Dir = DirUtil.getPublicGenDir() + "/session_2/android-cts";
    cache.acquire("session_1", ZIP_PATH_1, session1Dir, "", dir -> "cts");

    cache.acquire("session_2", ZIP_PATH_1, session2Dir, "", dir -> "cts");

    Optional<CacheEntry> snapshot = cache.getEntrySnapshot(ZIP_PATH_1, "");
    assertThat(snapshot).isPresent();
    assertThat(snapshot.get().activeRefCount()).isEqualTo(2);
    assertThat(snapshot.get().activeSessionIds()).containsExactly("session_1", "session_2");
    assertThat(Files.isSymbolicLink(Path.of(session1Dir))).isTrue();
    assertThat(Files.isSymbolicLink(Path.of(session2Dir))).isTrue();
    assertThat(Files.readSymbolicLink(Path.of(session1Dir)))
        .isEqualTo(Files.readSymbolicLink(Path.of(session2Dir)));
  }

  @Test
  public void acquire_multipleSessionsSameZip_unzipsAndRestoresOnlyOnce() throws Exception {
    String session1Dir = DirUtil.getPublicGenDir() + "/session_1/android-cts";
    String session2Dir = DirUtil.getPublicGenDir() + "/session_2/android-cts";
    cache.acquire("session_1", ZIP_PATH_1, session1Dir, "", dir -> "cts");

    cache.acquire("session_2", ZIP_PATH_1, session2Dir, "", dir -> "cts");

    String cachedDirPath = cache.getEntrySnapshot(ZIP_PATH_1, "").orElseThrow().cachedDirPath();
    verify(localFileUtil)
        .unzipFile(eq(ZIP_PATH_1), eq(cachedDirPath), eq(Duration.ofHours(1)), eq(""));
    verify(xtsChunkedTestCasesRestorer).restoreTestCases(eq(cachedDirPath), eq("cts"));
  }

  @Test
  public void acquire_warmHit_returnsCachedXtsTypeWithoutCallingProviderAgain() throws Exception {
    String session1Dir = DirUtil.getPublicGenDir() + "/session_1/android-cts";
    String session2Dir = DirUtil.getPublicGenDir() + "/session_2/android-cts";
    cache.acquire("session_1", ZIP_PATH_1, session1Dir, "", dir -> "cts");

    String warmXtsType =
        cache.acquire(
            "session_2",
            ZIP_PATH_1,
            session2Dir,
            "",
            dir -> {
              throw new AssertionError("XtsTypeProvider should not be invoked on warm hit");
            });

    assertThat(warmXtsType).isEqualTo("cts");
  }

  @Test
  public void acquire_concurrentSessionsSameZip_singleFlightUnzip() throws Exception {
    CountDownLatch unzipStarted = new CountDownLatch(1);
    CountDownLatch allowUnzipFinish = new CountDownLatch(1);
    AtomicInteger unzipCallCount = new AtomicInteger(0);
    stubBlockingUnzip(ZIP_PATH_1, unzipStarted, allowUnzipFinish, unzipCallCount);
    String session1Dir = DirUtil.getPublicGenDir() + "/session_1/android-cts";
    String session2Dir = DirUtil.getPublicGenDir() + "/session_2/android-cts";

    runTwoConcurrentAcquires(session1Dir, session2Dir, unzipStarted, allowUnzipFinish);

    assertThat(unzipCallCount.get()).isEqualTo(1);
    Optional<CacheEntry> snapshot = cache.getEntrySnapshot(ZIP_PATH_1, "");
    assertThat(snapshot).isPresent();
    assertThat(snapshot.get().activeRefCount()).isEqualTo(2);
  }

  @Test
  public void computeCacheKey_differentPasswords_producesDistinctKeys() {
    String keySecret1 = cache.computeCacheKey(ZIP_PATH_1, "secret_1");

    String keySecret2 = cache.computeCacheKey(ZIP_PATH_1, "secret_2");

    assertThat(keySecret2).isNotEqualTo(keySecret1);
  }

  @Test
  public void acquire_passwordProtectedZip_passesPasswordToUnzip() throws Exception {
    String session1Dir = DirUtil.getPublicGenDir() + "/session_1/cts";

    cache.acquire("session_1", ZIP_PATH_1, session1Dir, "secret_1", dir -> "cts");

    String cachedDirPath =
        cache.getEntrySnapshot(ZIP_PATH_1, "secret_1").orElseThrow().cachedDirPath();
    verify(localFileUtil)
        .unzipFile(eq(ZIP_PATH_1), eq(cachedDirPath), eq(Duration.ofHours(1)), eq("secret_1"));
  }

  @Test
  public void isCached_passwordProtectedZip_matchesOnlyCorrectPassword() throws Exception {
    String session1Dir = DirUtil.getPublicGenDir() + "/session_1/cts";

    cache.acquire("session_1", ZIP_PATH_1, session1Dir, "secret_1", dir -> "cts");

    assertThat(cache.isCached(ZIP_PATH_1, "secret_1")).isTrue();
    assertThat(cache.isCached(ZIP_PATH_1, "secret_2")).isFalse();
  }

  @Test
  public void acquire_startupOrphanedCacheDirsOnDisk_purgesOrphansOnFirstAcquire()
      throws Exception {
    Path orphanDir1 = cache.getCacheRootDir().resolve("orphan_cts_dir_1");
    Path orphanDir2 = cache.getCacheRootDir().resolve("orphan_cts_dir_2");
    Files.createDirectories(orphanDir1);
    Files.writeString(orphanDir1.resolve("stale.jar"), "stale");
    Files.createDirectories(orphanDir2);
    String session1Dir = DirUtil.getPublicGenDir() + "/session_1/cts";

    cache.acquire("session_1", ZIP_PATH_1, session1Dir, "", dir -> "cts");

    assertThat(Files.exists(orphanDir1)).isFalse();
    assertThat(Files.exists(orphanDir2)).isFalse();
    assertThat(cache.isCached(ZIP_PATH_1, "")).isTrue();
  }

  @Test
  public void acquire_initInterruptedDuringOrphanCleanup_retriesRemainingOrphansOnNextAcquire()
      throws Exception {
    Path orphanDir = cache.getCacheRootDir().resolve("orphan_cts_dir");
    Files.createDirectories(orphanDir);
    String session1Dir = DirUtil.getPublicGenDir() + "/session_1/cts";
    String session2Dir = DirUtil.getPublicGenDir() + "/session_2/cts";
    doThrow(new InterruptedException("interrupted during startup orphan sweep"))
        .doCallRealMethod()
        .when(localFileUtil)
        .removeFileOrDir(eq(orphanDir));
    runExpectingInterrupt(
        () -> cache.acquire("session_1", ZIP_PATH_1, session1Dir, "", dir -> "cts"));

    cache.acquire("session_2", ZIP_PATH_1, session2Dir, "", dir -> "cts");

    assertThat(Files.exists(orphanDir)).isFalse();
    assertThat(cache.isCached(ZIP_PATH_1, "")).isTrue();
  }

  @Test
  public void acquire_cachedDirDeletedExternallyFromDisk_detectsMissAndReUnzips() throws Exception {
    String session1Dir = DirUtil.getPublicGenDir() + "/session_1/cts";
    String session2Dir = DirUtil.getPublicGenDir() + "/session_2/cts";
    cache.acquire("session_1", ZIP_PATH_1, session1Dir, "", dir -> "cts");
    String cachedDir = cache.getEntrySnapshot(ZIP_PATH_1, "").orElseThrow().cachedDirPath();
    cache.release("session_1", session1Dir);
    localFileUtil.removeFileOrDir(cachedDir);

    cache.acquire("session_2", ZIP_PATH_1, session2Dir, "", dir -> "cts");

    verify(localFileUtil, times(2))
        .unzipFile(eq(ZIP_PATH_1), anyString(), eq(Duration.ofHours(1)), eq(""));
  }

  @Test
  public void acquire_cachedDirDeletedExternallyAtMaxEntries_doesNotEvictOtherIdleEntry()
      throws Exception {
    flags.set("ats_xts_unzip_cache_max_entries", "2");
    String dir1 = DirUtil.getPublicGenDir() + "/session_1/cts";
    String dir2 = DirUtil.getPublicGenDir() + "/session_2/cts";
    cache.acquire("session_1", ZIP_PATH_1, dir1, "", dir -> "cts");
    cache.release("session_1", dir1);
    cache.acquire("session_2", ZIP_PATH_2, dir2, "", dir -> "cts");
    cache.release("session_2", dir2);
    localFileUtil.removeFileOrDir(
        cache.getEntrySnapshot(ZIP_PATH_1, "").orElseThrow().cachedDirPath());

    cache.acquire("session_1b", ZIP_PATH_1, dir1, "", dir -> "cts");

    assertThat(cache.isCached(ZIP_PATH_1, "")).isTrue();
    assertThat(cache.isCached(ZIP_PATH_2, "")).isTrue();
  }

  @Test
  public void eviction_otherEntryDirDeletedExternally_dropsStaleEntryBeforeEvictingValidOne()
      throws Exception {
    flags.set("ats_xts_unzip_cache_max_entries", "2");
    String dir1 = DirUtil.getPublicGenDir() + "/session_1/cts";
    String dir2 = DirUtil.getPublicGenDir() + "/session_2/cts";
    String dir3 = DirUtil.getPublicGenDir() + "/session_3/cts";
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(1_000L));
    cache.acquire("session_1", ZIP_PATH_1, dir1, "", dir -> "cts");
    cache.release("session_1", dir1);
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(2_000L));
    cache.acquire("session_2", ZIP_PATH_2, dir2, "", dir -> "cts");
    cache.release("session_2", dir2);
    localFileUtil.removeFileOrDir(
        cache.getEntrySnapshot(ZIP_PATH_2, "").orElseThrow().cachedDirPath());
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(3_000L));

    cache.acquire("session_3", ZIP_PATH_3, dir3, "", dir -> "cts");

    assertThat(cache.isCached(ZIP_PATH_1, "")).isTrue();
    assertThat(cache.getEntrySnapshot(ZIP_PATH_2, "")).isEmpty();
    assertThat(cache.isCached(ZIP_PATH_3, "")).isTrue();
  }

  @Test
  public void isCached_cachedDirDeletedExternallyFromDisk_returnsFalse() throws Exception {
    String session1Dir = DirUtil.getPublicGenDir() + "/session_1/cts";
    cache.acquire("session_1", ZIP_PATH_1, session1Dir, "", dir -> "cts");
    String cachedDir = cache.getEntrySnapshot(ZIP_PATH_1, "").orElseThrow().cachedDirPath();
    cache.release("session_1", session1Dir);

    localFileUtil.removeFileOrDir(cachedDir);

    assertThat(cache.isCached(ZIP_PATH_1, "")).isFalse();
  }

  @Test
  public void acquire_sameSessionAcquiresSecondZipWithoutRelease_releasesFirstLeasePreventingLeak()
      throws Exception {
    String dir1 = DirUtil.getPublicGenDir() + "/session_1/cts1";
    String dir2 = DirUtil.getPublicGenDir() + "/session_1/cts2";
    cache.acquire("session_1", ZIP_PATH_1, dir1, "", dir -> "cts");

    cache.acquire("session_1", ZIP_PATH_2, dir2, "", dir -> "cts");

    assertThat(cache.getEntrySnapshot(ZIP_PATH_1, "").orElseThrow().activeRefCount()).isEqualTo(0);
    assertThat(cache.getEntrySnapshot(ZIP_PATH_2, "").orElseThrow().activeRefCount()).isEqualTo(1);
  }

  @Test
  public void acquire_unzipFileThrowsException_cleansUpPartialCacheDirAndTarget() throws Exception {
    String dir1 = DirUtil.getPublicGenDir() + "/session_1/cts";
    doThrow(new MobileHarnessException(BasicErrorId.LOCAL_FILE_UNZIP_ERROR, "corrupt zip"))
        .when(localFileUtil)
        .unzipFile(eq(ZIP_PATH_1), anyString(), eq(Duration.ofHours(1)), eq(""));

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class,
            () -> cache.acquire("session_1", ZIP_PATH_1, dir1, "", dir -> "cts"));

    String expectedCachedDir =
        cache.getCacheRootDir().resolve(cache.computeCacheKey(ZIP_PATH_1, "")).toString();
    assertThat(thrown.getErrorId()).isEqualTo(BasicErrorId.LOCAL_FILE_UNZIP_ERROR);
    assertThat(cache.isCached(ZIP_PATH_1, "")).isFalse();
    assertThat(localFileUtil.isFileOrDirExist(expectedCachedDir)).isFalse();
    assertThat(Files.exists(Path.of(dir1), LinkOption.NOFOLLOW_LINKS)).isFalse();
  }

  @Test
  public void acquire_afterPreviousUnzipFailure_recoversOnRetry() throws Exception {
    String dir1 = DirUtil.getPublicGenDir() + "/session_1/cts";
    doThrow(new MobileHarnessException(BasicErrorId.LOCAL_FILE_UNZIP_ERROR, "corrupt zip"))
        .doReturn("unzipped")
        .when(localFileUtil)
        .unzipFile(eq(ZIP_PATH_1), anyString(), eq(Duration.ofHours(1)), eq(""));
    runIgnoringMobileHarnessException(
        () -> cache.acquire("session_1", ZIP_PATH_1, dir1, "", dir -> "cts"));

    cache.acquire("session_2", ZIP_PATH_1, dir1, "", dir -> "cts");

    assertThat(cache.isCached(ZIP_PATH_1, "")).isTrue();
    assertThat(Files.isSymbolicLink(Path.of(dir1))).isTrue();
  }

  @Test
  public void acquire_restoreTestCasesFails_cleansUpPartialCacheDirAndSymlink() throws Exception {
    String dir1 = DirUtil.getPublicGenDir() + "/session_1/cts";
    doThrow(new MobileHarnessException(BasicErrorId.COMMAND_EXEC_FAIL, "cts_restorer failed"))
        .when(xtsChunkedTestCasesRestorer)
        .restoreTestCases(anyString(), eq("cts"));

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class,
            () -> cache.acquire("session_1", ZIP_PATH_1, dir1, "", dir -> "cts"));

    assertThat(thrown.getErrorId()).isEqualTo(BasicErrorId.COMMAND_EXEC_FAIL);
    assertThat(cache.isCached(ZIP_PATH_1, "")).isFalse();
    assertThat(cache.getAllEntrySnapshots()).isEmpty();
    assertThat(Files.exists(Path.of(dir1), LinkOption.NOFOLLOW_LINKS)).isFalse();
  }

  @Test
  public void acquire_preExistingTargetDirectory_replacedCleanlyWithSymlink() throws Exception {
    Path targetDir = Path.of(DirUtil.getPublicGenDir(), "session_1", "cts");
    Files.createDirectories(targetDir);

    cache.acquire("session_1", ZIP_PATH_1, targetDir.toString(), "", dir -> "cts");

    assertThat(Files.isSymbolicLink(targetDir)).isTrue();
  }

  @Test
  public void release_removesOnlySessionSymlink_preservesSharedCachedFilesOnDisk()
      throws Exception {
    doAnswer(
            invocation -> {
              Path cachedDir = Path.of(invocation.getArgument(1, String.class));
              Path toolsDir = cachedDir.resolve("android-cts/tools");
              Files.createDirectories(toolsDir);
              Files.writeString(toolsDir.resolve("cts-tradefed"), "binary-content");
              return "unzipped";
            })
        .when(localFileUtil)
        .unzipFile(eq(ZIP_PATH_1), anyString(), eq(Duration.ofHours(1)), eq(""));
    String session1Dir = DirUtil.getPublicGenDir() + "/session_1/cts";
    cache.acquire("session_1", ZIP_PATH_1, session1Dir, "", dir -> "cts");
    Path cachedDir = Path.of(cache.getEntrySnapshot(ZIP_PATH_1, "").orElseThrow().cachedDirPath());

    cache.release("session_1", session1Dir);

    assertThat(Files.exists(Path.of(session1Dir), LinkOption.NOFOLLOW_LINKS)).isFalse();
    assertThat(Files.readString(cachedDir.resolve("android-cts/tools/cts-tradefed")))
        .isEqualTo("binary-content");
  }

  @Test
  public void release_calledMultipleTimesOrForUnknownSession_idempotentAndSafe() throws Exception {
    String session1Dir = DirUtil.getPublicGenDir() + "/session_1/cts";
    String session2Dir = DirUtil.getPublicGenDir() + "/session_2/cts";
    cache.acquire("session_1", ZIP_PATH_1, session1Dir, "", dir -> "cts");
    cache.acquire("session_2", ZIP_PATH_1, session2Dir, "", dir -> "cts");
    cache.release("unknown_session", DirUtil.getPublicGenDir() + "/unknown/cts");
    cache.release("session_1", session1Dir);

    cache.release("session_1", session1Dir);

    assertThat(cache.getEntrySnapshot(ZIP_PATH_1, "").orElseThrow().activeRefCount()).isEqualTo(1);
  }

  @Test
  public void
      release_whenCallerThreadAlreadyInterrupted_removesSymlinkReleasesLeaseAndRestoresFlag()
          throws Exception {
    String session1Dir = DirUtil.getPublicGenDir() + "/session_1/cts";
    cache.acquire("session_1", ZIP_PATH_1, session1Dir, "", dir -> "cts");

    try {
      Thread.currentThread().interrupt();
      cache.release("session_1", session1Dir);

      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(Files.exists(Path.of(session1Dir), LinkOption.NOFOLLOW_LINKS)).isFalse();
      assertThat(cache.getEntrySnapshot(ZIP_PATH_1, "").orElseThrow().activeRefCount())
          .isEqualTo(0);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  public void eviction_exceedsMaxEntries_evictsLeastRecentlyAccessedIdleEntry() throws Exception {
    flags.set("ats_xts_unzip_cache_max_entries", "2");
    String dir1 = DirUtil.getPublicGenDir() + "/session_1/cts";
    String dir2 = DirUtil.getPublicGenDir() + "/session_2/cts";
    String dir3 = DirUtil.getPublicGenDir() + "/session_3/cts";
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(1_000L));
    cache.acquire("session_1", ZIP_PATH_1, dir1, "", dir -> "cts");
    cache.release("session_1", dir1);
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(2_000L));
    cache.acquire("session_2", ZIP_PATH_2, dir2, "", dir -> "cts");
    cache.release("session_2", dir2);
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(3_000L));
    cache.acquire("session_1b", ZIP_PATH_1, dir1, "", dir -> "cts");
    cache.release("session_1b", dir1);
    String cachedDir2 = cache.getEntrySnapshot(ZIP_PATH_2, "").orElseThrow().cachedDirPath();
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(4_000L));

    cache.acquire("session_3", ZIP_PATH_3, dir3, "", dir -> "cts");

    assertThat(cache.isCached(ZIP_PATH_1, "")).isTrue();
    assertThat(cache.isCached(ZIP_PATH_2, "")).isFalse();
    assertThat(cache.isCached(ZIP_PATH_3, "")).isTrue();
    assertThat(localFileUtil.isDirExist(cachedDir2)).isFalse();
  }

  @Test
  public void eviction_activeEntriesPinnedDuringOverCapacityAcquire_retainsPinnedEntry()
      throws Exception {
    flags.set("ats_xts_unzip_cache_max_entries", "1");
    String dir1 = DirUtil.getPublicGenDir() + "/session_1/cts";
    String dir2 = DirUtil.getPublicGenDir() + "/session_2/cts";
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(1_000L));
    cache.acquire("session_1", ZIP_PATH_1, dir1, "", dir -> "cts");
    String cachedDir1 = cache.getEntrySnapshot(ZIP_PATH_1, "").orElseThrow().cachedDirPath();
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(2_000L));

    cache.acquire("session_2", ZIP_PATH_2, dir2, "", dir -> "cts");

    String cachedDir2 = cache.getEntrySnapshot(ZIP_PATH_2, "").orElseThrow().cachedDirPath();
    assertThat(cache.isCached(ZIP_PATH_1, "")).isTrue();
    assertThat(cache.isCached(ZIP_PATH_2, "")).isTrue();
    assertThat(localFileUtil.isDirExist(cachedDir1)).isTrue();
    assertThat(localFileUtil.isDirExist(cachedDir2)).isTrue();
  }

  @Test
  public void eviction_pinnedEntryOverCapacity_evictedImmediatelyUponRelease() throws Exception {
    flags.set("ats_xts_unzip_cache_max_entries", "1");
    String dir1 = DirUtil.getPublicGenDir() + "/session_1/cts";
    String dir2 = DirUtil.getPublicGenDir() + "/session_2/cts";
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(1_000L));
    cache.acquire("session_1", ZIP_PATH_1, dir1, "", dir -> "cts");
    String cachedDir1 = cache.getEntrySnapshot(ZIP_PATH_1, "").orElseThrow().cachedDirPath();
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(2_000L));
    cache.acquire("session_2", ZIP_PATH_2, dir2, "", dir -> "cts");
    String cachedDir2 = cache.getEntrySnapshot(ZIP_PATH_2, "").orElseThrow().cachedDirPath();
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(2_500L));

    cache.release("session_1", dir1);

    assertThat(cache.isCached(ZIP_PATH_1, "")).isFalse();
    assertThat(localFileUtil.isDirExist(cachedDir1)).isFalse();
    assertThat(cache.isCached(ZIP_PATH_2, "")).isTrue();
    assertThat(localFileUtil.isDirExist(cachedDir2)).isTrue();
  }

  @Test
  public void eviction_interruptedWhileDeletingEvictedDir_retriesDeletionOnNextSweep()
      throws Exception {
    flags.set("ats_xts_unzip_cache_max_entries", "1");
    String dir1 = DirUtil.getPublicGenDir() + "/session_1/cts";
    String dir2 = DirUtil.getPublicGenDir() + "/session_2/cts";
    cache.acquire("session_1", ZIP_PATH_1, dir1, "", dir -> "cts");
    Path cachedDir1 = Path.of(cache.getEntrySnapshot(ZIP_PATH_1, "").orElseThrow().cachedDirPath());
    cache.release("session_1", dir1);
    doThrow(new InterruptedException("interrupted during evicted dir deletion"))
        .doCallRealMethod()
        .when(localFileUtil)
        .removeFileOrDir(eq(cachedDir1));
    runExpectingInterrupt(() -> cache.acquire("session_2", ZIP_PATH_2, dir2, "", dir -> "cts"));

    cache.evictIfNeeded();

    assertThat(localFileUtil.isDirExist(cachedDir1)).isFalse();
  }

  @Test
  public void eviction_exceedsMaxSizeGb_evictsLruIdleEntriesUntilWithinBudget() throws Exception {
    flags.set("ats_xts_unzip_cache_max_entries", "10");
    flags.set("ats_xts_unzip_cache_max_size_gb", "100");
    long sixtyGb = 60L * 1024L * 1024L * 1024L;
    doReturn(true).when(localFileUtil).isFileExist(ZIP_PATH_1);
    doReturn(sixtyGb).when(localFileUtil).getFileSize(ZIP_PATH_1);
    doReturn(true).when(localFileUtil).isFileExist(ZIP_PATH_2);
    doReturn(sixtyGb).when(localFileUtil).getFileSize(ZIP_PATH_2);
    String dir1 = DirUtil.getPublicGenDir() + "/session_1/cts";
    String dir2 = DirUtil.getPublicGenDir() + "/session_2/cts";
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(1_000L));
    cache.acquire("session_1", ZIP_PATH_1, dir1, "", dir -> "cts");
    cache.release("session_1", dir1);
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(2_000L));

    cache.acquire("session_2", ZIP_PATH_2, dir2, "", dir -> "cts");

    assertThat(cache.isCached(ZIP_PATH_1, "")).isFalse();
    assertThat(cache.isCached(ZIP_PATH_2, "")).isTrue();
    assertThat(cache.getTotalCachedSizeBytes()).isEqualTo(sixtyGb);
  }

  @Test
  public void eviction_ttlExpired_reUnzipsOnSubsequentAcquire() throws Exception {
    flags.set("ats_xts_unzip_cache_ttl", "1h");
    String dir1 = DirUtil.getPublicGenDir() + "/session_1/cts";
    String dir2 = DirUtil.getPublicGenDir() + "/session_2/cts";
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(1_000L));
    cache.acquire("session_1", ZIP_PATH_1, dir1, "", dir -> "cts");
    cache.release("session_1", dir1);
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(1_000L + 7_200L));

    cache.acquire("session_2", ZIP_PATH_1, dir2, "", dir -> "cts");

    verify(localFileUtil, times(2))
        .unzipFile(eq(ZIP_PATH_1), anyString(), eq(Duration.ofHours(1)), eq(""));
  }

  @Test
  public void eviction_ttlExpiredWhileEntryInUse_retainsPinnedEntryAndAllowsConcurrentJoin()
      throws Exception {
    flags.set("ats_xts_unzip_cache_ttl", "1h");
    String dir1 = DirUtil.getPublicGenDir() + "/session_1/cts";
    String dir2 = DirUtil.getPublicGenDir() + "/session_2/cts";
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(1_000L));
    cache.acquire("session_1", ZIP_PATH_1, dir1, "", dir -> "cts");
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(1_000L + 10_800L));
    cache.evictIfNeeded();

    cache.acquire("session_2", ZIP_PATH_1, dir2, "", dir -> "cts");

    assertThat(cache.isCached(ZIP_PATH_1, "")).isTrue();
    assertThat(cache.getEntrySnapshot(ZIP_PATH_1, "").orElseThrow().activeRefCount()).isEqualTo(2);
  }

  @Test
  public void eviction_disabledLimitsZero_retainsAllEntries() throws Exception {
    flags.set("ats_xts_unzip_cache_ttl", "0s");
    flags.set("ats_xts_unzip_cache_max_entries", "0");
    flags.set("ats_xts_unzip_cache_max_size_gb", "0");
    String dir1 = DirUtil.getPublicGenDir() + "/session_1/cts";
    String dir2 = DirUtil.getPublicGenDir() + "/session_2/cts";
    String dir3 = DirUtil.getPublicGenDir() + "/session_3/cts";
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(1_000L));
    cache.acquire("session_1", ZIP_PATH_1, dir1, "", dir -> "cts");
    cache.release("session_1", dir1);
    cache.acquire("session_2", ZIP_PATH_2, dir2, "", dir -> "cts");
    cache.release("session_2", dir2);
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(1_000L + 8_640_000L));

    cache.acquire("session_3", ZIP_PATH_3, dir3, "", dir -> "cts");
    cache.release("session_3", dir3);

    assertThat(cache.getAllEntrySnapshots()).hasSize(3);
  }

  @Test
  public void acquire_cachedDirWithInternalSymlinks_sumsOnlyRegularFileBytes() throws Exception {
    doAnswer(
            invocation -> {
              Path cachedDir = Path.of(invocation.getArgument(1, String.class));
              Path toolsDir = cachedDir.resolve("android-cts/tools");
              Files.createDirectories(toolsDir);
              Files.write(toolsDir.resolve("tradefed.jar"), new byte[1024]);
              Files.write(toolsDir.resolve("cts-tradefed.jar"), new byte[2048]);
              Files.createSymbolicLink(
                  toolsDir.resolve("symlink.jar"), toolsDir.resolve("tradefed.jar"));
              return "unzipped";
            })
        .when(localFileUtil)
        .unzipFile(eq(ZIP_PATH_1), anyString(), eq(Duration.ofHours(1)), eq(""));
    String dir1 = DirUtil.getPublicGenDir() + "/session_1/cts";

    cache.acquire("session_1", ZIP_PATH_1, dir1, "", dir -> "cts");

    assertThat(cache.getTotalCachedSizeBytes()).isEqualTo(1024L + 2048L);
  }

  @Test
  public void computeCacheKey_zipFileModifiedOnDisk_invalidatesOldCacheKey() throws Exception {
    Path realZip = tmpFolder.newFile("android-cts.zip").toPath();
    Files.writeString(realZip, "v1");
    Files.setLastModifiedTime(realZip, FileTime.fromMillis(10_000L));
    String keyV1 = cache.computeCacheKey(realZip.toString(), "");
    Files.writeString(realZip, "v2-updated-content");
    Files.setLastModifiedTime(realZip, FileTime.fromMillis(20_000L));

    String keyV2 = cache.computeCacheKey(realZip.toString(), "");

    assertThat(keyV2).isNotEqualTo(keyV1);
  }

  @Test
  public void computeCacheKey_symlinkZipPath_resolvesRealPathAndDetectsRetargetedSymlink()
      throws Exception {
    Path realZip1 = tmpFolder.newFile("cts-r1.zip").toPath();
    Path realZip2 = tmpFolder.newFile("cts-r2.zip").toPath();
    Files.writeString(realZip1, "zip-r1-content");
    Files.writeString(realZip2, "zip-r2-different-content");
    Path symlinkZip = tmpFolder.getRoot().toPath().resolve("android-cts-latest.zip");
    Files.createSymbolicLink(symlinkZip, realZip1);
    String keyTarget1 = cache.computeCacheKey(symlinkZip.toString(), "");
    Files.delete(symlinkZip);
    Files.createSymbolicLink(symlinkZip, realZip2);

    String keyRetargeted = cache.computeCacheKey(symlinkZip.toString(), "");

    assertThat(keyTarget1).isEqualTo(cache.computeCacheKey(realZip1.toString(), ""));
    assertThat(keyRetargeted).isEqualTo(cache.computeCacheKey(realZip2.toString(), ""));
    assertThat(keyRetargeted).isNotEqualTo(keyTarget1);
  }

  @Test
  public void computeCacheKey_largeRemoteWorkerRunDirZipModifiedOnlyInTail_invalidatesCacheKey()
      throws Exception {
    Path job1Zip = Path.of(DirUtil.getRunDir(), "job_1", "ats-file-server", "android-cts.zip");
    Path job2Zip = Path.of(DirUtil.getRunDir(), "job_2", "ats-file-server", "android-cts.zip");
    Files.createDirectories(job1Zip.getParent());
    Files.createDirectories(job2Zip.getParent());
    byte[] contentV1 = new byte[200 * 1024];
    byte[] contentV2 = new byte[200 * 1024];
    byte[] tailV1 = "central-directory-crc-v1".getBytes(UTF_8);
    byte[] tailV2 = "central-directory-crc-v2".getBytes(UTF_8);
    System.arraycopy(tailV1, 0, contentV1, contentV1.length - tailV1.length, tailV1.length);
    System.arraycopy(tailV2, 0, contentV2, contentV2.length - tailV2.length, tailV2.length);
    Files.write(job1Zip, contentV1);
    Files.write(job2Zip, contentV2);

    String keyJob2 = cache.computeCacheKey(job2Zip.toString(), "");

    assertThat(keyJob2).isNotEqualTo(cache.computeCacheKey(job1Zip.toString(), ""));
  }

  @Test
  public void acquire_remoteWorkerPerJobRunFileDirs_sharesCacheForSameZipAcrossJobs()
      throws Exception {
    Path job1Zip =
        Path.of(DirUtil.getRunDir(), "job_1", "ats-file-server", "cts", "android-cts.zip");
    Path job2Zip =
        Path.of(DirUtil.getRunDir(), "job_2", "ats-file-server", "cts", "android-cts.zip");
    Files.createDirectories(job1Zip.getParent());
    Files.createDirectories(job2Zip.getParent());
    Files.writeString(job1Zip, "identical-zip-binary-content-v1");
    Files.setLastModifiedTime(job1Zip, FileTime.fromMillis(10_000L));
    Files.writeString(job2Zip, "identical-zip-binary-content-v1");
    Files.setLastModifiedTime(job2Zip, FileTime.fromMillis(20_000L));
    String test1TargetDir = DirUtil.getTempDir() + "/test_1/unzipped";
    String test2TargetDir = DirUtil.getTempDir() + "/test_2/unzipped";
    cache.acquire("test_1", job1Zip.toString(), test1TargetDir, "", dir -> "cts");

    cache.acquire("test_2", job2Zip.toString(), test2TargetDir, "", dir -> "cts");

    verify(localFileUtil)
        .unzipFile(eq(job1Zip.toString()), anyString(), eq(Duration.ofHours(1)), eq(""));
    verify(xtsChunkedTestCasesRestorer).restoreTestCases(anyString(), eq("cts"));
  }

  @Test
  public void computeCacheKey_remoteWorkerReplacedRunFileZip_invalidatesCacheKey()
      throws Exception {
    Path job1Zip =
        Path.of(DirUtil.getRunDir(), "job_1", "ats-file-server", "cts", "android-cts.zip");
    Path job2Zip =
        Path.of(DirUtil.getRunDir(), "job_2", "ats-file-server", "cts", "android-cts.zip");
    Files.createDirectories(job1Zip.getParent());
    Files.createDirectories(job2Zip.getParent());
    Files.writeString(job1Zip, "identical-zip-binary-content-v1");
    Files.writeString(job2Zip, "replaced-zip-binary-content-v2!");

    String keyJob2 = cache.computeCacheKey(job2Zip.toString(), "");

    assertThat(keyJob2).isNotEqualTo(cache.computeCacheKey(job1Zip.toString(), ""));
  }

  @Test
  public void forWorker_controllerAndWorkerCachesOnSameHost_isolatedCacheRoots() throws Exception {
    XtsPackageUnzipCache workerCache =
        XtsPackageUnzipCache.forWorker(
            localFileUtil, xtsChunkedTestCasesRestorer, clock, Duration.ofHours(2));
    String controllerTargetDir = DirUtil.getPublicGenDir() + "/session_1/cts";
    String workerTargetDir = DirUtil.getTempDir() + "/test_1/cts";
    cache.acquire("session_1", ZIP_PATH_1, controllerTargetDir, "", dir -> "cts");

    workerCache.acquire("test_1", ZIP_PATH_1, workerTargetDir, "", dir -> "cts");

    assertThat(workerCache.getCacheRootDir()).isNotEqualTo(cache.getCacheRootDir());
    assertThat(cache.isCached(ZIP_PATH_1, "")).isTrue();
    assertThat(workerCache.isCached(ZIP_PATH_1, "")).isTrue();
  }

  @Test
  public void forWorker_acquire_passesCustomWorkerUnzipTimeout() throws Exception {
    XtsPackageUnzipCache workerCache =
        XtsPackageUnzipCache.forWorker(
            localFileUtil, xtsChunkedTestCasesRestorer, clock, Duration.ofHours(2));
    String workerTargetDir = DirUtil.getTempDir() + "/test_1/cts";

    workerCache.acquire("test_1", ZIP_PATH_1, workerTargetDir, "", dir -> "cts");

    verify(localFileUtil).unzipFile(eq(ZIP_PATH_1), anyString(), eq(Duration.ofHours(2)), eq(""));
  }

  @Test
  public void acquireAndRelease_concurrentMultiZipAndEvictionStress_noDeadlockOrRefCountLeak()
      throws Exception {
    flags.set("ats_xts_unzip_cache_max_entries", "2");
    String[] zips = {ZIP_PATH_1, ZIP_PATH_2, ZIP_PATH_3};

    runConcurrentStressWorkload(zips, /* numThreads= */ 8, /* iterationsPerThread= */ 5);

    assertThat(
            cache.getAllEntrySnapshots().stream()
                .filter(CacheEntry::isInUse)
                .collect(toImmutableList()))
        .isEmpty();
    assertThat(cache.getAllEntrySnapshots().size()).isAtMost(2);
  }

  @Test
  public void acquire_warmHitLinkFailure_doesNotLeakActiveSessionLease() throws Exception {
    String session1TargetDir = DirUtil.getPublicGenDir() + "/session_1/android-cts";
    String session2TargetDir = DirUtil.getPublicGenDir() + "/session_2/android-cts";
    cache.acquire("session_1", ZIP_PATH_1, session1TargetDir, "", dir -> "cts");
    cache.release("session_1", session1TargetDir);
    doThrow(new MobileHarnessException(BasicErrorId.LOCAL_FILE_OR_DIR_LINK_ERROR, "link failed"))
        .when(localFileUtil)
        .linkFileOrDir(anyString(), eq(session2TargetDir));

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class,
            () -> cache.acquire("session_2", ZIP_PATH_1, session2TargetDir, "", dir -> "cts"));

    assertThat(thrown.getErrorId()).isEqualTo(BasicErrorId.LOCAL_FILE_OR_DIR_LINK_ERROR);
    assertThat(cache.getEntrySnapshot(ZIP_PATH_1, "").orElseThrow().activeRefCount()).isEqualTo(0);
  }

  @Test
  public void acquire_updatesCacheRootDirLastModifiedTimeFromClock() throws Exception {
    String session1TargetDir = DirUtil.getPublicGenDir() + "/session_1/android-cts";
    String session2TargetDir = DirUtil.getPublicGenDir() + "/session_2/android-cts";
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(1_000L));
    cache.acquire("session_1", ZIP_PATH_1, session1TargetDir, "", dir -> "cts");
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(5_000L));

    cache.acquire("session_2", ZIP_PATH_1, session2TargetDir, "", dir -> "cts");

    assertThat(Files.getLastModifiedTime(cache.getCacheRootDir()).toInstant())
        .isEqualTo(Instant.ofEpochSecond(5_000L));
  }

  @Test
  public void release_updatesCacheRootDirLastModifiedTimeFromClock() throws Exception {
    String session1TargetDir = DirUtil.getPublicGenDir() + "/session_1/android-cts";
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(1_000L));
    cache.acquire("session_1", ZIP_PATH_1, session1TargetDir, "", dir -> "cts");
    when(clock.instant()).thenReturn(Instant.ofEpochSecond(7_000L));

    cache.release("session_1", session1TargetDir);

    assertThat(Files.getLastModifiedTime(cache.getCacheRootDir()).toInstant())
        .isEqualTo(Instant.ofEpochSecond(7_000L));
  }

  @Test
  public void acquire_interruptedDuringCleanup_cleansUpBothTargetAndCachedDirAndPreservesInterrupt()
      throws Exception {
    String session1TargetDir = DirUtil.getPublicGenDir() + "/session_1/android-cts";
    String expectedCachedDir =
        cache.getCacheRootDir().resolve(cache.computeCacheKey(ZIP_PATH_1, "")).toString();
    doThrow(new MobileHarnessException(BasicErrorId.COMMAND_EXEC_FAIL, "cts_restorer failed"))
        .when(xtsChunkedTestCasesRestorer)
        .restoreTestCases(anyString(), eq("cts"));
    doCallRealMethod()
        .doThrow(new InterruptedException("interrupted during target symlink cleanup"))
        .when(localFileUtil)
        .removeFileOrDir(eq(Path.of(session1TargetDir)));

    try {
      MobileHarnessException thrown =
          assertThrows(
              MobileHarnessException.class,
              () -> cache.acquire("session_1", ZIP_PATH_1, session1TargetDir, "", dir -> "cts"));

      assertThat(thrown.getErrorId()).isEqualTo(BasicErrorId.COMMAND_EXEC_FAIL);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(localFileUtil.isFileOrDirExist(expectedCachedDir)).isFalse();
      assertThat(cache.isCached(ZIP_PATH_1, "")).isFalse();
    } finally {
      Thread.interrupted();
    }
  }

  private void stubBlockingUnzip(
      String zipPath,
      CountDownLatch unzipStarted,
      CountDownLatch allowUnzipFinish,
      AtomicInteger unzipCallCount)
      throws MobileHarnessException, InterruptedException {
    doAnswer(
            invocation -> {
              unzipCallCount.incrementAndGet();
              unzipStarted.countDown();
              if (!allowUnzipFinish.await(5, SECONDS)) {
                throw new AssertionError("Timed out waiting for allowUnzipFinish");
              }
              return "unzipped";
            })
        .when(localFileUtil)
        .unzipFile(eq(zipPath), anyString(), any(Duration.class), anyString());
  }

  private void runTwoConcurrentAcquires(
      String session1Dir,
      String session2Dir,
      CountDownLatch unzipStarted,
      CountDownLatch allowUnzipFinish)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> future1 =
          executor.submit(
              () -> {
                cache.acquire("session_1", ZIP_PATH_1, session1Dir, "", dir -> "cts");
                return null;
              });
      if (!unzipStarted.await(5, SECONDS)) {
        throw new AssertionError("Timed out waiting for unzipStarted");
      }
      Future<?> future2 =
          executor.submit(
              () -> {
                cache.acquire("session_2", ZIP_PATH_1, session2Dir, "", dir -> "cts");
                return null;
              });
      allowUnzipFinish.countDown();
      future1.get(5, SECONDS);
      future2.get(5, SECONDS);
    } finally {
      executor.shutdownNow();
    }
  }

  private void runConcurrentStressWorkload(String[] zips, int numThreads, int iterationsPerThread)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    try {
      CountDownLatch startGate = new CountDownLatch(1);
      List<Future<?>> futures = new ArrayList<>();
      for (int t = 0; t < numThreads; t++) {
        final int threadIdx = t;
        futures.add(
            executor.submit(
                () -> {
                  if (!startGate.await(5, SECONDS)) {
                    throw new AssertionError("Timed out waiting for startGate");
                  }
                  for (int i = 0; i < iterationsPerThread; i++) {
                    String sessionId = "stress_session_" + threadIdx + "_" + i;
                    String targetDir = DirUtil.getPublicGenDir() + "/" + sessionId + "/cts";
                    String zip = zips[(threadIdx + i) % zips.length];
                    cache.acquire(sessionId, zip, targetDir, "", dir -> "cts");
                    if (!Files.isSymbolicLink(Path.of(targetDir))) {
                      throw new AssertionError("Expected targetDir symlink: " + targetDir);
                    }
                    cache.release(sessionId, targetDir);
                    if (Files.exists(Path.of(targetDir), LinkOption.NOFOLLOW_LINKS)) {
                      throw new AssertionError("Expected targetDir symlink removed: " + targetDir);
                    }
                  }
                  return null;
                }));
      }
      startGate.countDown();
      for (Future<?> future : futures) {
        future.get(10, SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private static void runExpectingInterrupt(MobileHarnessRunnable action)
      throws MobileHarnessException {
    try {
      action.run();
      throw new AssertionError("Expected InterruptedException");
    } catch (InterruptedException expected) {
      Thread.interrupted();
    }
  }

  private static void runIgnoringMobileHarnessException(MobileHarnessRunnable action)
      throws InterruptedException {
    try {
      action.run();
    } catch (MobileHarnessException expected) {
      // Expected in setup before retry.
    }
  }
}
