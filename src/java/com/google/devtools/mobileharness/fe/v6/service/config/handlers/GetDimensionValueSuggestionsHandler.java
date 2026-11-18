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

package com.google.devtools.mobileharness.fe.v6.service.config.handlers;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.Comparator.comparingInt;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition.DimensionMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.IntegerMatch;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.IntegerMatch.GreaterThanOrEqual;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringListMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringMultimapMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask.DimensionsMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.Page;
import com.google.devtools.mobileharness.fe.v6.service.config.util.BatchDeviceConfigPolicy;
import com.google.devtools.mobileharness.fe.v6.service.config.util.BatchDeviceConfigUtil;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.ConfigurableDimension;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DimensionList;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDimensionValueSuggestionsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDimensionValueSuggestionsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDimensionValueSuggestionsResponse.CandidateDimensionValue;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.protobuf.FieldMask;
import io.grpc.Context;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Handler for the GetDimensionValueSuggestions RPC.
 *
 * <p>When the policy reads fleet values from {@code LabInfoService} (the Google-internal fleet),
 * every suggestion list comes from a {@code GetLabInfo} call that scans the whole fleet on the
 * master and often takes several seconds, so the lists are cached in memory per dimension and list.
 * A cached list is served at once; once it is five minutes old, the next request for it also
 * reloads it in the background. The master's lab info already trails Device Config writes by
 * minutes, so the cache adds little staleness. {@link #refreshCuratedDimensionSuggestions} keeps
 * the curated dimensions loaded so their dialogs open from memory on every task. Deployments that
 * read fleet values from Device Config are not cached, so their suggestions keep reflecting just
 * applied changes.
 */
@Singleton
public final class GetDimensionValueSuggestionsHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Age after which the next request for a cached suggestion list reloads it in the background.
   * That request, and every request until the reload finishes, still gets the cached list at once.
   */
  private static final Duration SUGGESTIONS_REFRESH_AFTER = Duration.ofMinutes(5);

  /**
   * Age after which a cached suggestion list that was not reloaded is dropped, so a dimension
   * nobody asked about for a while is fetched afresh. Three times the interval at which the curated
   * dimensions are reloaded, so their lists survive two failed reloads in a row.
   */
  private static final Duration SUGGESTIONS_EXPIRE_AFTER = Duration.ofMinutes(45);

  /**
   * Time after which a load that the master has not answered fails, so a hung call cannot keep
   * every request for that list waiting on it.
   */
  private static final Duration SUGGESTIONS_LOAD_TIMEOUT = Duration.ofMinutes(1);

  /**
   * Bound on the total weight of the cached suggestion lists. A list weighs its serialized size
   * plus {@link #SUGGESTIONS_ENTRY_OVERHEAD_BYTES}. A list is small for the usual dimensions, but a
   * custom dimension with a distinct value on every device yields one suggestion per device.
   */
  private static final long SUGGESTIONS_CACHE_MAX_BYTES = 16L * 1024 * 1024;

  /**
   * Weight added to every cached list for its key and bookkeeping, so empty lists (for example of
   * dimensions no device has) count against the bound too.
   */
  private static final int SUGGESTIONS_ENTRY_OVERHEAD_BYTES = 1024;

  private static final FieldMask SUPPORTED_DIM_FIELD_MASK =
      FieldMask.newBuilder()
          .addPaths("device_locator.id")
          .addPaths("device_feature.composite_dimension.supported_dimension")
          .build();

  private static final FieldMask REQUIRED_DIM_FIELD_MASK =
      FieldMask.newBuilder()
          .addPaths("device_locator.id")
          .addPaths("device_feature.composite_dimension.required_dimension")
          .build();

  /** Key of one cached suggestion list. */
  private record SuggestionsKey(String dimensionName, DimensionList dimensionList) {}

  private final LabInfoProvider labInfoProvider;
  private final ConfigurationProvider configurationProvider;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final BatchDeviceConfigPolicy policy;
  private final ListeningExecutorService executor;
  private final AsyncLoadingCache<SuggestionsKey, GetDimensionValueSuggestionsResponse>
      labInfoSuggestionsCache;

  @Inject
  GetDimensionValueSuggestionsHandler(
      LabInfoProvider labInfoProvider,
      ConfigurationProvider configurationProvider,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      BatchDeviceConfigPolicy policy,
      ListeningExecutorService executor) {
    this.labInfoProvider = labInfoProvider;
    this.configurationProvider = configurationProvider;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.policy = policy;
    this.executor = executor;
    this.labInfoSuggestionsCache =
        Caffeine.newBuilder()
            .refreshAfterWrite(SUGGESTIONS_REFRESH_AFTER)
            .expireAfterWrite(SUGGESTIONS_EXPIRE_AFTER)
            .maximumWeight(SUGGESTIONS_CACHE_MAX_BYTES)
            .weigher((SuggestionsKey unusedKey, GetDimensionValueSuggestionsResponse response) ->
                SUGGESTIONS_ENTRY_OVERHEAD_BYTES + response.getSerializedSize())
            .executor(executor)
            .buildAsync((key, unusedExecutor) -> loadLabInfoSuggestions(key));
  }

  public ListenableFuture<GetDimensionValueSuggestionsResponse> getDimensionValueSuggestions(
      GetDimensionValueSuggestionsRequest request) {
    logger.atInfo().log("GetDimensionValueSuggestionsRequest: %s", request);

    try {
      configServiceCapabilityFactory.create(UniverseScope.SELF).checkConfigServiceAvailability();
    } catch (UnsupportedOperationException e) {
      return immediateFailedFuture(FeServiceException.failedPrecondition(e.getMessage()));
    }

    String dimensionName = BatchDeviceConfigUtil.cleanDimensionName(request.getDimensionName());
    DimensionList dimensionList = request.getDimensionList();
    Optional<String> invalidReason =
        BatchDeviceConfigUtil.checkDimensionTarget(dimensionName, dimensionList, policy);
    if (invalidReason.isPresent()) {
      return immediateFailedFuture(FeServiceException.invalidArgument(invalidReason.get()));
    }

    if (policy.discoverFleetDimensionsFromDeviceConfigs()) {
      return getSuggestionsFromDeviceConfigs(dimensionName, dimensionList);
    }
    return toListenableFuture(
        labInfoSuggestionsCache.get(new SuggestionsKey(dimensionName, dimensionList)));
  }

  /**
   * Loads the suggestion list of every curated dimension, or reloads it in the background when it
   * is already cached, so the dialogs of the curated dimensions open from memory. A cached list
   * keeps being served while it reloads, and a failed reload keeps the previous list. Does nothing
   * when suggestions are not read from {@code LabInfoService} or the config service is unavailable.
   */
  public void refreshCuratedDimensionSuggestions() {
    if (policy.discoverFleetDimensionsFromDeviceConfigs()
        || !configServiceCapabilityFactory.create(UniverseScope.SELF).isConfigServiceAvailable()) {
      return;
    }
    for (ConfigurableDimension dimension : policy.getCuratedDimensions()) {
      SuggestionsKey key =
          new SuggestionsKey(
              BatchDeviceConfigUtil.cleanDimensionName(dimension.getDimensionName()),
              dimension.getDimensionList());
      Futures.addCallback(
          toListenableFuture(labInfoSuggestionsCache.synchronous().refresh(key)),
          new FutureCallback<GetDimensionValueSuggestionsResponse>() {
            @Override
            public void onSuccess(GetDimensionValueSuggestionsResponse response) {
              logger.atInfo().log(
                  "Refreshed %d value suggestions for %s.",
                  response.getCandidateValuesCount(), key);
            }

            @Override
            public void onFailure(Throwable t) {
              logger.atWarning().withCause(t).log(
                  "Failed to refresh value suggestions for %s.", key);
            }
          },
          directExecutor());
    }
  }

  /**
   * Starts loading the suggestion list of {@code key} for the cache; never throws. The master call
   * runs outside the calling request's RPC context, because the result is shared with other
   * requests and a background reload outlives the request that triggered it. The call is cancelled
   * if it has not finished after {@link #SUGGESTIONS_LOAD_TIMEOUT}, which fails the load.
   */
  private CompletableFuture<GetDimensionValueSuggestionsResponse> loadLabInfoSuggestions(
      SuggestionsKey key) {
    ListenableFuture<GetDimensionValueSuggestionsResponse> load;
    try {
      load =
          Context.ROOT.call(
              () -> getSuggestionsFromLabInfo(key.dimensionName(), key.dimensionList()));
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
    // Cancelling a load that already finished does nothing.
    CompletableFuture.delayedExecutor(SUGGESTIONS_LOAD_TIMEOUT.toMillis(), MILLISECONDS)
        .execute(() -> load.cancel(/* mayInterruptIfRunning= */ false));
    return toCompletableFuture(load);
  }

  private ListenableFuture<GetDimensionValueSuggestionsResponse> getSuggestionsFromDeviceConfigs(
      String dimensionName, DimensionList dimensionList) {
    return Futures.transformAsync(
        labInfoProvider.getLabInfoAsync(GetLabInfoRequest.getDefaultInstance(), UniverseScope.SELF),
        labInfoResponse -> {
          ImmutableList<String> deviceIds =
              BatchDeviceConfigUtil.extractFleetDeviceIds(labInfoResponse);
          if (deviceIds.isEmpty()) {
            return immediateFuture(GetDimensionValueSuggestionsResponse.getDefaultInstance());
          }
          return Futures.transform(
              configurationProvider.getDeviceConfigs(deviceIds, UniverseScope.SELF),
              configs -> {
                ImmutableMap<String, DeviceConfig> configByDeviceId =
                    configs.stream()
                        .filter(cfg -> !cfg.getUuid().isEmpty())
                        .collect(
                            toImmutableMap(
                                DeviceConfig::getUuid, cfg -> cfg, (first, second) -> second));
                ImmutableList<ImmutableList<String>> perDeviceValueSets =
                    deviceIds.stream()
                        .map(id ->
                            configByDeviceId.containsKey(id)
                                ? BatchDeviceConfigUtil.getDimensionValues(
                                    configByDeviceId.get(id).getBasicConfig(),
                                    dimensionName,
                                    dimensionList)
                                : ImmutableList.<String>of())
                        .collect(toImmutableList());
                return aggregateValueSets(perDeviceValueSets);
              },
              executor);
        },
        executor);
  }

  private ListenableFuture<GetDimensionValueSuggestionsResponse> getSuggestionsFromLabInfo(
      String dimensionName, DimensionList dimensionList) {
    boolean isRequired = BatchDeviceConfigUtil.isRequiredDimensionList(dimensionList);
    DeviceInfoMask.Builder deviceInfoMaskBuilder =
        DeviceInfoMask.newBuilder()
            .setFieldMask(isRequired ? REQUIRED_DIM_FIELD_MASK : SUPPORTED_DIM_FIELD_MASK);
    DimensionsMask dimensionsMask =
        DimensionsMask.newBuilder().addDimensionNames(dimensionName).build();
    if (isRequired) {
      deviceInfoMaskBuilder.setRequiredDimensionsMask(dimensionsMask);
    } else {
      deviceInfoMaskBuilder.setSupportedDimensionsMask(dimensionsMask);
    }

    GetLabInfoRequest labInfoRequest =
        GetLabInfoRequest.newBuilder()
            .setLabQuery(
                LabQuery.newBuilder()
                    .setFilter(singleDimensionFilter(dimensionName))
                    .setLabViewRequest(LabQuery.LabViewRequest.getDefaultInstance())
                    .setMask(
                        LabQuery.Mask.newBuilder()
                            .setLabInfoMask(
                                LabInfoMask.newBuilder()
                                    .setFieldMask(FieldMask.getDefaultInstance()))
                            .setDeviceInfoMask(deviceInfoMaskBuilder)))
            .setPage(Page.newBuilder().setLimit(0))
            .setUseRealtimeData(false)
            .build();

    return Futures.transform(
        labInfoProvider.getLabInfoAsync(labInfoRequest, UniverseScope.SELF),
        response -> {
          if (!response.getLabQueryResult().hasLabView()) {
            return GetDimensionValueSuggestionsResponse.getDefaultInstance();
          }
          ImmutableList<ImmutableList<String>> perDeviceValueSets =
              response.getLabQueryResult().getLabView().getLabDataList().stream()
                  .flatMap(labData -> labData.getDeviceList().getDeviceInfoList().stream())
                  .map(deviceInfo ->
                      BatchDeviceConfigUtil.getDimensionValues(
                          deviceInfo.getDeviceFeature().getCompositeDimension(),
                          dimensionName,
                          dimensionList))
                  .collect(toImmutableList());
          return aggregateValueSets(perDeviceValueSets);
        },
        executor);
  }

  private static LabQuery.Filter singleDimensionFilter(String dimensionName) {
    return LabQuery.Filter.newBuilder()
        .setDeviceFilter(
            DeviceFilter.newBuilder()
                .addDeviceMatchCondition(
                    DeviceMatchCondition.newBuilder()
                        .setDimensionMatchCondition(
                            DimensionMatchCondition.newBuilder()
                                .setCondition(
                                    StringMultimapMatchCondition.newBuilder()
                                        .setKey(dimensionName)
                                        .setValueCondition(
                                            StringListMatchCondition.newBuilder()
                                                .setLengthMatch(
                                                    IntegerMatch.newBuilder()
                                                        .setGreaterThanOrEqual(
                                                            GreaterThanOrEqual.newBuilder()
                                                                .setMinValue(1))))))))
        .build();
  }

  private static GetDimensionValueSuggestionsResponse aggregateValueSets(
      List<ImmutableList<String>> perDeviceValueSets) {
    Map<ImmutableList<String>, ImmutableList<String>> displayByCanon = new LinkedHashMap<>();
    Map<ImmutableList<String>, Integer> countsByCanon = new LinkedHashMap<>();

    for (ImmutableList<String> values : perDeviceValueSets) {
      ImmutableList<String> canon = BatchDeviceConfigUtil.canonicalizeValues(values);
      if (canon.isEmpty()) {
        // Devices without the dimension are not a suggestion; clearing is ClearDimensionValues.
        continue;
      }
      displayByCanon.putIfAbsent(canon, BatchDeviceConfigUtil.normalizeValues(values));
      countsByCanon.merge(canon, 1, Integer::sum);
    }

    ImmutableList<CandidateDimensionValue> candidates =
        countsByCanon.entrySet().stream()
            .map(entry ->
                CandidateDimensionValue.newBuilder()
                    .addAllValues(displayByCanon.get(entry.getKey()))
                    .setDeviceCount(entry.getValue())
                    .build())
            .sorted(
                comparingInt(CandidateDimensionValue::getDeviceCount)
                    .reversed()
                    .thenComparing(candidate -> String.join(",", candidate.getValuesList())))
            .collect(toImmutableList());

    return GetDimensionValueSuggestionsResponse.newBuilder()
        .addAllCandidateValues(candidates)
        .build();
  }

  private static <T> CompletableFuture<T> toCompletableFuture(ListenableFuture<T> future) {
    CompletableFuture<T> result = new CompletableFuture<>();
    Futures.addCallback(
        future,
        new FutureCallback<T>() {
          @Override
          public void onSuccess(T value) {
            result.complete(value);
          }

          @Override
          public void onFailure(Throwable t) {
            result.completeExceptionally(t);
          }
        },
        directExecutor());
    return result;
  }

  /** Adapts a cache future, failing with the cause of a wrapping {@link CompletionException}. */
  @SuppressWarnings("FutureReturnValueIgnored") // The callback relays the outcome to `result`.
  private static <T> ListenableFuture<T> toListenableFuture(CompletableFuture<T> future) {
    SettableFuture<T> result = SettableFuture.create();
    future.whenComplete((value, error) -> {
      if (error == null) {
        result.set(value);
      } else if (error instanceof CompletionException && error.getCause() != null) {
        result.setException(error.getCause());
      } else {
        result.setException(error);
      }
    });
    return result;
  }
}
