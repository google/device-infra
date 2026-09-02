import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';
import {APP_DATA, AppData} from '../../models/app_data';
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
import {SearchService} from './search_service';

/** An implementation of the SearchService that uses HTTP to fetch data. */
@Injectable()
export class HttpSearchService extends SearchService {
  private readonly appData: AppData = inject(APP_DATA);
  private readonly fleetApiUrl = `${this.appData.labConsoleServerUrl}/v6/fleet-search`;
  private readonly tjsApiUrl = `${this.appData.labConsoleServerUrl}/v6/tjs-search`;
  private readonly http = inject(HttpClient);

  constructor() {
    super();
  }

  override getFleetSearchConfig(
    request: FleetSearchConfigRequest,
  ): Observable<FleetSearchConfig> {
    return this.http.get<FleetSearchConfig>(`${this.fleetApiUrl}/config`, {
      params: request as unknown as Record<string, string>,
    });
  }

  override searchFleet(
    request: FleetSearchRequest,
  ): Observable<FleetSearchResults> {
    return this.http.post<FleetSearchResults>(this.fleetApiUrl, request);
  }

  override getFleetSuggestions(
    request: FleetSuggestionRequest,
  ): Observable<FleetSuggestionResponse> {
    return this.http.post<FleetSuggestionResponse>(
      `${this.fleetApiUrl}/suggestions`,
      request,
    );
  }

  override resolveFleetChips(
    request: FleetChipResolverRequest,
  ): Observable<FleetChipResolverResponse> {
    return this.http.post<FleetChipResolverResponse>(
      `${this.fleetApiUrl}/resolve-chips`,
      request,
    );
  }

  override getFleetValueList(
    request: FleetValueListRequest,
  ): Observable<FleetValueListResponse> {
    return this.http
      .post<FleetValueListResponse>(`${this.fleetApiUrl}/value-list`, request)
      .pipe(map((res) => this.normalizeFleetValueListResponse(res)));
  }

  private normalizeFleetValueListResponse(
    res: FleetValueListResponse,
  ): FleetValueListResponse {
    if (!res) return res;
    if (res.counted) {
      return {
        counted: {
          values: (res.counted.values || []).map((v) => ({
            value: v.value,
            displayLabel: v.displayLabel || v.value,
            filtered: v.filtered ?? 0,
            total: v.total ?? 0,
          })),
          noValueEntry: res.counted.noValueEntry
            ? {
                filtered: res.counted.noValueEntry.filtered ?? 0,
                total: res.counted.noValueEntry.total ?? 0,
              }
            : undefined,
        },
      };
    }
    if (res.plain) {
      return {
        plain: {
          values: (res.plain.values || []).map((v) => ({
            value: v.value,
            displayLabel: v.displayLabel || v.value,
          })),
          noValueEntry: res.plain.noValueEntry ? {} : undefined,
        },
      };
    }
    return res;
  }

  override getFleetPromotedKeys(
    request: FleetPromotedKeysRequest,
  ): Observable<FleetPromotedKeysResponse> {
    return this.http.post<FleetPromotedKeysResponse>(
      `${this.fleetApiUrl}/promoted-keys`,
      request,
    );
  }

  override getFleetColumnCatalog(
    request: FleetColumnCatalogRequest,
  ): Observable<FleetColumnCatalogResponse> {
    return this.http.post<FleetColumnCatalogResponse>(
      `${this.fleetApiUrl}/column-catalog`,
      request,
    );
  }

  override getTjsSearchConfig(
    request: TjsSearchConfigRequest,
  ): Observable<TjsSearchConfig> {
    return this.http.get<TjsSearchConfig>(`${this.tjsApiUrl}/config`, {
      params: request as unknown as Record<string, string>,
    });
  }

  override searchTjs(request: TjsSearchRequest): Observable<TjsSearchResponse> {
    return this.http.post<TjsSearchResponse>(this.tjsApiUrl, request);
  }

  override getTjsSuggestions(
    request: TjsSuggestionRequest,
  ): Observable<TjsSuggestionResponse> {
    return this.http.post<TjsSuggestionResponse>(
      `${this.tjsApiUrl}/suggestions`,
      request,
    );
  }

  override resolveTjsChips(
    request: TjsResolveChipsRequest,
  ): Observable<TjsResolveChipsResponse> {
    return this.http.post<TjsResolveChipsResponse>(
      `${this.tjsApiUrl}/resolve-chips`,
      request,
    );
  }
}
