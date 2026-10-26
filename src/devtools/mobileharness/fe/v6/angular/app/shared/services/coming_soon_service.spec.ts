import {TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {
  ACTION_BAR_CONFIG_TOKEN,
  ActionBarAction,
  ActionMetadata,
} from '@deviceinfra/app/core/constants/action_bar_config';
import {ComingSoonDialog} from '../components/coming_soon_dialog/coming_soon_dialog';
import {ComingSoonService} from './coming_soon_service';

describe('ComingSoonService', () => {
  let service: ComingSoonService;
  let dialogSpy: jasmine.SpyObj<MatDialog>;

  const mockConfig = {
    [ActionBarAction.DEVICE_SCREENSHOT]: {
      displayName: 'Screenshot',
      legacyScreenshotLinks: {
        default: 'https://example.com/mock-screenshot',
      },
    },
  } as unknown as Record<ActionBarAction, ActionMetadata>;

  beforeEach(() => {
    dialogSpy = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    TestBed.configureTestingModule({
      providers: [
        ComingSoonService,
        {provide: MatDialog, useValue: dialogSpy},
        {provide: ACTION_BAR_CONFIG_TOKEN, useValue: mockConfig},
      ],
    });

    service = TestBed.inject(ComingSoonService);
  });

  it('passes legacyPageUrl through stayOnLegacyFe in show', () => {
    service.show(
      ActionBarAction.DEVICE_SCREENSHOT,
      'default',
      'https://mhfe/devicedetailview/h1/1.2.3.4/d1',
    );

    expect(dialogSpy.open).toHaveBeenCalledWith(
      ComingSoonDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          legacyPageUrl:
            'https://mhfe/devicedetailview/h1/1.2.3.4/d1?redirect_to_v6=0',
          legacyScreenshotLink: 'https://example.com/mock-screenshot',
        }),
      }),
    );
  });

  it('converts an empty legacyPageUrl to undefined in show', () => {
    service.show(ActionBarAction.DEVICE_SCREENSHOT, 'default', '');

    expect(dialogSpy.open).toHaveBeenCalledWith(
      ComingSoonDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          legacyPageUrl: undefined,
        }),
      }),
    );
  });

  it('builds device and host legacy URLs with redirect_to_v6=0', () => {
    service.showForDevice(
      ActionBarAction.DEVICE_SCREENSHOT,
      'https://mhfe',
      'h1',
      '1.2.3.4',
      'd1',
    );
    expect(dialogSpy.open).toHaveBeenCalledWith(
      ComingSoonDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          legacyPageUrl:
            'https://mhfe/devicedetailview/h1/1.2.3.4/d1?redirect_to_v6=0',
        }),
      }),
    );

    dialogSpy.open.calls.reset();
    service.showForHost(
      ActionBarAction.DEVICE_SCREENSHOT,
      'https://mhfe',
      'h1',
      '1.2.3.4',
    );
    expect(dialogSpy.open).toHaveBeenCalledWith(
      ComingSoonDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          legacyPageUrl:
            'https://mhfe/labdetailview/h1/1.2.3.4?redirect_to_v6=0',
        }),
      }),
    );
  });
});
