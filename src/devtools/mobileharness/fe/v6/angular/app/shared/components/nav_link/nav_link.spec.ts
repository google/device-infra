import {DOCUMENT} from '@angular/common';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter, Router} from '@angular/router';
import {UrlService} from '@deviceinfra/app/core/services/url_service';
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
      'notifyNavigated',
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
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));
  });

  it('should create', () => {
    fixture.componentRef.setInput('config', {
      type: 'host',
      hostName: 'host1',
      hostIp: '1.1.1.1',
    });
    mockUrlService.getExternalUrl.and.returnValue(of('http://parent/host1'));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should generate correct routerLink for host', () => {
    fixture.componentRef.setInput('config', {
      type: 'host',
      hostName: 'host1',
      hostIp: '1.1.1.1',
    });
    mockUrlService.getExternalUrl.and.returnValue(of('http://parent/host1'));
    fixture.detectChanges();
    expect(component.routerLink).toBe('/hosts/host1');
  });

  it('should generate correct routerLink for device', () => {
    fixture.componentRef.setInput('config', {
      type: 'device',
      hostName: 'host1',
      hostIp: '1.1.1.1',
      deviceId: 'dev1',
    });
    mockUrlService.getExternalUrl.and.returnValue(of('http://parent/dev1'));
    fixture.detectChanges();
    expect(component.routerLink).toBe('/devices/dev1');
  });

  it('should navigate with router in standalone mode', () => {
    fixture.componentRef.setInput('config', {
      type: 'host',
      hostName: 'host1',
      hostIp: '1.1.1.1',
    });
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

  it('should include universe parameter in navigation when provided in config', () => {
    fixture.componentRef.setInput('config', {
      type: 'host',
      hostName: 'host1',
      hostIp: '1.1.1.1',
      universe: 'my_universe',
    });
    mockUrlService.getExternalUrl.and.returnValue(of('http://parent/host1'));
    mockUrlService.isInEmbeddedMode.and.returnValue(false);
    fixture.detectChanges();

    const event = new MouseEvent('click');
    spyOn(event, 'preventDefault');
    component.handleClick(event);

    expect(event.preventDefault).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/hosts/host1'], {
      queryParams: {universe: 'my_universe'},
      queryParamsHandling: '',
    });
  });

  it('should navigate with router and merge query params if queryParamsHandling="merge" is set', () => {
    fixture.componentRef.setInput('config', {
      type: 'host',
      hostName: 'host1',
      hostIp: '1.1.1.1',
    });
    mockUrlService.getExternalUrl.and.returnValue(of('http://parent/host1'));
    mockUrlService.isInEmbeddedMode.and.returnValue(false);

    fixture.componentRef.setInput('queryParamsHandling', 'merge');
    fixture.detectChanges();
    const event = new MouseEvent('click');
    spyOn(event, 'preventDefault');
    component.handleClick(event);

    expect(event.preventDefault).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/hosts/host1'], {
      queryParamsHandling: 'merge',
    });
  });

  it('should navigate with router even in embedded mode for regular click', () => {
    fixture.componentRef.setInput('config', {
      type: 'host',
      hostName: 'host1',
      hostIp: '1.1.1.1',
    });
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
    fixture.componentRef.setInput('config', {
      type: 'host',
      hostName: 'host1',
      hostIp: '1.1.1.1',
    });

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
    fixture.componentRef.setInput('target', '_blank');
    fixture.detectChanges();
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

    fixture.componentRef.setInput('config', {
      type: 'host',
      hostName: 'host1',
      hostIp: '1.1.1.1',
    });
    mockUrlService.getExternalUrl.and.returnValue(NEVER);

    fixture.detectChanges();

    expect(component.fullPageLink).toBe('/hosts/host1?param1=value1');
  });

  it('should keep local URL on getExternalUrl failure in embedded mode', () => {
    fixture.componentRef.setInput('config', {
      type: 'host',
      hostName: 'host1',
      hostIp: '1.1.1.1',
    });
    const error$ = new Observable<string>((sub) => {
      sub.error(new Error('fail'));
    });
    mockUrlService.getExternalUrl.and.returnValue(error$);
    mockUrlService.isInEmbeddedMode.and.returnValue(true);

    fixture.detectChanges();

    expect(component.fullPageLink).toContain('/hosts/host1');
  });

  it('should notify parent window immediately on client-side navigation', async () => {
    fixture.componentRef.setInput('config', {
      type: 'host',
      hostName: 'host1',
      hostIp: '1.1.1.1',
    });
    mockUrlService.getExternalUrl.and.returnValue(of('http://parent/host1'));
    mockUrlService.isInEmbeddedMode.and.returnValue(true);
    (router.navigate as jasmine.Spy).and.returnValue(Promise.resolve(true));
    fixture.detectChanges();

    const event = new MouseEvent('click');
    component.handleClick(event);

    await fixture.whenStable();

    expect(mockUrlService.notifyNavigated).toHaveBeenCalledWith(
      'host_details',
      {
        'host_name': 'host1',
        'host_ip': '1.1.1.1',
      },
    );
  });

  it('should include device_uuid when fetching external URL in embedded mode for device', () => {
    mockUrlService.isInEmbeddedMode.and.returnValue(true);
    mockUrlService.getExternalUrl.and.returnValue(of('http://parent/dev1'));

    fixture.componentRef.setInput('config', {
      type: 'device',
      hostName: 'host1',
      hostIp: '1.1.1.1',
      deviceId: 'dev1',
    });
    fixture.detectChanges();

    expect(mockUrlService.getExternalUrl).toHaveBeenCalledWith(
      'device_details',
      jasmine.objectContaining({
        'host_name': 'host1',
        'host_ip': '1.1.1.1',
        'device_uuid': 'dev1',
      }),
    );
    expect(component.fullPageLink).toBe('http://parent/dev1');
  });

  it('should include uuid in notification on client-side navigation for device', async () => {
    fixture.componentRef.setInput('config', {
      type: 'device',
      hostName: 'host1',
      hostIp: '1.1.1.1',
      deviceId: 'dev1',
    });
    mockUrlService.getExternalUrl.and.returnValue(of('http://parent/dev1'));
    mockUrlService.isInEmbeddedMode.and.returnValue(true);
    fixture.detectChanges();

    const event = new MouseEvent('click');
    component.handleClick(event);
    await fixture.whenStable();

    expect(mockUrlService.notifyNavigated).toHaveBeenCalledWith(
      'device_details',
      {
        'host_name': 'host1',
        'host_ip': '1.1.1.1',
        'uuid': 'dev1',
      },
    );
    expect(router.navigate).toHaveBeenCalledWith(['/devices/dev1'], {
      queryParamsHandling: '',
    });
  });

  it('should generate correct routerLink and navigation params for job', async () => {
    fixture.componentRef.setInput('config', {
      type: 'job',
      jobId: 'job_123',
    });
    mockUrlService.getExternalUrl.and.returnValue(of('http://parent/job_123'));
    mockUrlService.isInEmbeddedMode.and.returnValue(true);
    fixture.detectChanges();

    expect(component.routerLink).toBe('/jobs/job_123');
    expect(mockUrlService.getExternalUrl).toHaveBeenCalledWith('job_details', {
      'job_id': 'job_123',
    });

    const event = new MouseEvent('click');
    component.handleClick(event);
    await fixture.whenStable();

    expect(mockUrlService.notifyNavigated).toHaveBeenCalledWith('job_details', {
      'job_id': 'job_123',
    });
    expect(router.navigate).toHaveBeenCalledWith(['/jobs/job_123'], {
      queryParamsHandling: '',
    });
  });

  it('should generate correct routerLink and navigation params for test', async () => {
    fixture.componentRef.setInput('config', {
      type: 'test',
      jobId: 'job_123',
      testId: 'test_456',
    });
    mockUrlService.getExternalUrl.and.returnValue(of('http://parent/test_456'));
    mockUrlService.isInEmbeddedMode.and.returnValue(true);
    fixture.detectChanges();

    expect(component.routerLink).toBe('/jobs/job_123/tests/test_456');
    expect(mockUrlService.getExternalUrl).toHaveBeenCalledWith('test_details', {
      'job_id': 'job_123',
      'test_id': 'test_456',
    });

    const event = new MouseEvent('click');
    component.handleClick(event);
    await fixture.whenStable();

    expect(mockUrlService.notifyNavigated).toHaveBeenCalledWith(
      'test_details',
      {
        'job_id': 'job_123',
        'test_id': 'test_456',
      },
    );
    expect(router.navigate).toHaveBeenCalledWith(
      ['/jobs/job_123/tests/test_456'],
      {queryParamsHandling: ''},
    );
  });
});
