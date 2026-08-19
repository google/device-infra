import {InjectionToken} from '@angular/core';
import {Observable} from 'rxjs';
import {
  FleetChipResolverRequest,
  FleetChipResolverResponse,
  FleetColumnCatalogRequest,
  FleetColumnCatalogResponse,
  FleetPromotedKeysRequest,
  FleetPromotedKeysResponse,
  FleetSearchConfig,
  FleetSearchConfigRequest,
  FleetSearchRequest,
  FleetSearchResults,
  FleetSuggestionRequest,
  FleetSuggestionResponse,
  FleetValueListRequest,
  FleetValueListResponse,
  TjsResolveChipsRequest,
  TjsResolveChipsResponse,
  TjsSearchConfig,
  TjsSearchConfigRequest,
  TjsSearchRequest,
  TjsSearchResponse,
  TjsSuggestionRequest,
  TjsSuggestionResponse,
} from '../../models/search';

/** Injection token for referencing SearchService implementations. */
export const SEARCH_SERVICE = new InjectionToken<SearchService>(
  'SearchService',
);

/**
 * Abstract class defining the contract for FE v6 SearchService operations.
 */
export abstract class SearchService {
  // --- Fleet (device/host) Search RPCs ---
  abstract getFleetSearchConfig(
    request: FleetSearchConfigRequest,
  ): Observable<FleetSearchConfig>;

  abstract searchFleet(
    request: FleetSearchRequest,
  ): Observable<FleetSearchResults>;

  abstract getFleetSuggestions(
    request: FleetSuggestionRequest,
  ): Observable<FleetSuggestionResponse>;

  abstract resolveFleetChips(
    request: FleetChipResolverRequest,
  ): Observable<FleetChipResolverResponse>;

  abstract getFleetValueList(
    request: FleetValueListRequest,
  ): Observable<FleetValueListResponse>;

  abstract getFleetPromotedKeys(
    request: FleetPromotedKeysRequest,
  ): Observable<FleetPromotedKeysResponse>;

  abstract getFleetColumnCatalog(
    request: FleetColumnCatalogRequest,
  ): Observable<FleetColumnCatalogResponse>;

  // --- TJS (test/job/session) Search RPCs ---
  abstract getTjsSearchConfig(
    request: TjsSearchConfigRequest,
  ): Observable<TjsSearchConfig>;

  abstract searchTjs(request: TjsSearchRequest): Observable<TjsSearchResponse>;

  abstract getTjsSuggestions(
    request: TjsSuggestionRequest,
  ): Observable<TjsSuggestionResponse>;

  abstract resolveTjsChips(
    request: TjsResolveChipsRequest,
  ): Observable<TjsResolveChipsResponse>;
}
