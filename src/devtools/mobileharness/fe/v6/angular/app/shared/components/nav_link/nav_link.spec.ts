import {DOCUMENT} from '@angular/common';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter, Router} from '@angular/router';
import {UrlService} from 'app/core/services/url_service';
import {NEVER, Observable, of} from 'rxjs';
import {NavLink} from './nav_link';

describe('NavLink', () => {
  let component: NavLink;
  let fixture: ComponentFixture<NavLink>;
  let router: Router;
  let mockUrlService: jasmine.SpyObj<UrlService>;

  beforeEach(async () => {
    mockUrlService = jasmine.createSpyObj('UrlService', [
      'getExternalUrl',
      'isInEmbeddedMode',
    ]);

    await TestBed.configureTestingModule({
      imports: [NavLink],
      providers: [
        provideRouter([]),
        {provide: UrlService, useValue: mockUrlService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NavLink);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
  });

  it('should create', () => {
    component.config = {type: 'host', hostName: 'host1', hostIp: '1.1.1.1'};
    mockUrlService.getExternalUrl.and.returnValue(of('http://parent/host1'));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should generate correct routerLink for host', () => {
    component.config = {type: 'host', hostName: 'host1', hostIp: '1.1.1.1'};
    mockUrlService.getExternalUrl.and.returnValue(of('http://parent/host1'));
    fixture.detectChanges();
    expect(component.routerLink).toBe('/hosts/host1');
  });

  it('should generate correct routerLink for device', () => {
    component.config = {
      type: 'device',
      hostName: 'host1',
      hostIp: '1.1.1.1',
      deviceId: 'dev1',
    };
    mockUrlService.getExternalUrl.and.returnValue(of('http://parent/dev1'));
    fixture.detectChanges();
    expect(component.routerLink).toBe('/devices/dev1');
  });

  it('should navigate with router in standalone mode', () => {
    component.config = {type: 'host', hostName: 'host1', hostIp: '1.1.1.1'};
    mockUrlService.getExternalUrl.and.returnValue(of('http://parent/host1'));
    mockUrlService.isInEmbeddedMode.and.returnValue(false);
    fixture.detectChanges();

    const event = new MouseEvent('click');
    spyOn(event, 'preventDefault');
    component.handleClick(event);

    expect(event.preventDefault).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/hosts/host1'], {
      queryParamsHandling: '',
    });
  });

  it('should navigate with router and merge query params if queryParamsHandling="merge" is set', () => {
    component.config = {type: 'host', hostName: 'host1', hostIp: '1.1.1.1'};
    mockUrlService.getExternalUrl.and.returnValue(of('http://parent/host1'));
    mockUrlService.isInEmbeddedMode.and.returnValue(false);
    fixture.detectChanges();

    component.queryParamsHandling = 'merge';
    const event = new MouseEvent('click');
    spyOn(event, 'preventDefault');
    component.handleClick(event);

    expect(event.preventDefault).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/hosts/host1'], {
      queryParamsHandling: 'merge',
    });
  });

  it('should navigate with router even in embedded mode for regular click', () => {
    component.config = {type: 'host', hostName: 'host1', hostIp: '1.1.1.1'};
    mockUrlService.getExternalUrl.and.returnValue(of('http://parent/host1'));
    mockUrlService.isInEmbeddedMode.and.returnValue(true);
    fixture.detectChanges();

    const event = new MouseEvent('click');
    spyOn(event, 'preventDefault');
    component.handleClick(event);

    expect(event.preventDefault).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/hosts/host1'], {
      queryParamsHandling: '',
    });
  });

  it('should let native behavior happen on special clicks (Ctrl/Meta/Middle)', () => {
    component.config = {type: 'host', hostName: 'host1', hostIp: '1.1.1.1'};

    // Ctrl+Click
    let event = new MouseEvent('click', {ctrlKey: true});
    spyOn(event, 'preventDefault');
    component.handleClick(event);
    expect(event.preventDefault).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();

    // Meta+Click (Cmd on Mac)
    event = new MouseEvent('click', {metaKey: true});
    spyOn(event, 'preventDefault');
    component.handleClick(event);
    expect(event.preventDefault).not.toHaveBeenCalled();

    // Middle-Click
    event = new MouseEvent('click', {button: 1});
    spyOn(event, 'preventDefault');
    component.handleClick(event);
    expect(event.preventDefault).not.toHaveBeenCalled();

    // target="_blank"
    component.target = '_blank';
    event = new MouseEvent('click');
    spyOn(event, 'preventDefault');
    component.handleClick(event);
    expect(event.preventDefault).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('should merge current query parameters into initial fullPageLink', () => {
    const mockDocument = TestBed.inject(DOCUMENT);
    spyOnProperty(mockDocument, 'defaultView', 'get').and.returnValue({
      location: {
        search: '?param1=value1',
        origin: 'http://localhost:4200',
      },
    } as unknown as Window & typeof globalThis);

    component.config = {type: 'host', hostName: 'host1', hostIp: '1.1.1.1'};
    mockUrlService.getExternalUrl.and.returnValue(NEVER);

    fixture.detectChanges();

    expect(component.fullPageLink).toBe('/hosts/host1?param1=value1');
  });

  it('should keep local URL on getExternalUrl failure in embedded mode', () => {
    component.config = {type: 'host', hostName: 'host1', hostIp: '1.1.1.1'};
    const error$ = new Observable<string>((sub) => {
      sub.error(new Error('fail'));
    });
    mockUrlService.getExternalUrl.and.returnValue(error$);
    mockUrlService.isInEmbeddedMode.and.returnValue(true);

    fixture.detectChanges();

    expect(component.fullPageLink).toContain('/hosts/host1');
  });
});
