import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {DeviceDiscovery} from './device_discovery';

describe('DeviceDiscovery Component', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        DeviceDiscovery,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(DeviceDiscovery);
    const comp = fixture.componentInstance;
    fixture.detectChanges();
    expect(comp).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('.discovery-title').innerText,
    ).toBe('Device Discovery');
  });

  describe('Signals Reactivity', () => {
    it('should compute chips and metadataList reactively from deviceDiscovery', () => {
      const fixture = TestBed.createComponent(DeviceDiscovery);
      const comp = fixture.componentInstance;

      fixture.componentRef.setInput('deviceDiscovery', {
        monitoredDeviceUuids: ['uuid1'],
        testbedUuids: ['tb1'],
        miscDeviceUuids: [],
        overTcpIps: [],
        overSshDevices: [{ipAddress: '1.1.1.1', username: 'user'}],
        manekiSpecs: [],
      });
      fixture.detectChanges();

      const chips = comp.chips();
      expect(chips[0].entries).toEqual(['uuid1']);
      expect(chips[1].entries).toEqual(['tb1']);

      const metadataList = comp.metadataList();
      expect(metadataList[0].list).toEqual([
        {ipAddress: '1.1.1.1', username: 'user'},
      ]);
    });
  });

  describe('Event Handlers', () => {
    it('should emit deviceDiscoveryChange on chip change', () => {
      const fixture = TestBed.createComponent(DeviceDiscovery);
      const comp = fixture.componentInstance;
      fixture.componentRef.setInput('deviceDiscovery', {
        monitoredDeviceUuids: ['uuid1'],
        testbedUuids: [],
        miscDeviceUuids: [],
        overTcpIps: [],
        overSshDevices: [],
        manekiSpecs: [],
      });
      fixture.detectChanges();

      spyOn(comp.deviceDiscoveryChange, 'emit');

      comp.onChipChange('monitoredDeviceUuids', ['uuid1', 'uuid2']);

      expect(comp.deviceDiscoveryChange.emit).toHaveBeenCalledWith({
        monitoredDeviceUuids: ['uuid1', 'uuid2'],
        testbedUuids: [],
        miscDeviceUuids: [],
        overTcpIps: [],
        overSshDevices: [],
        manekiSpecs: [],
      });
    });

    it('should emit deviceDiscoveryChange on metadata change', () => {
      const fixture = TestBed.createComponent(DeviceDiscovery);
      const comp = fixture.componentInstance;
      fixture.componentRef.setInput('deviceDiscovery', {
        monitoredDeviceUuids: [],
        testbedUuids: [],
        miscDeviceUuids: [],
        overTcpIps: [],
        overSshDevices: [
          {
            ipAddress: '1.1.1.1',
            username: 'user',
            password: 'pwd',
            sshDeviceType: 'type',
          },
        ],
        manekiSpecs: [],
      });
      fixture.detectChanges();

      spyOn(comp.deviceDiscoveryChange, 'emit');

      comp.onMetadataChange('overSshDevices', [
        {
          ipAddress: '1.1.1.1',
          username: 'user',
          password: 'pwd',
          sshDeviceType: 'type',
        },
        {
          ipAddress: '2.2.2.2',
          username: 'user2',
          password: 'pwd2',
          sshDeviceType: 'type2',
        },
      ]);

      expect(comp.deviceDiscoveryChange.emit).toHaveBeenCalledWith({
        monitoredDeviceUuids: [],
        testbedUuids: [],
        miscDeviceUuids: [],
        overTcpIps: [],
        overSshDevices: [
          {
            ipAddress: '1.1.1.1',
            username: 'user',
            password: 'pwd',
            sshDeviceType: 'type',
          },
          {
            ipAddress: '2.2.2.2',
            username: 'user2',
            password: 'pwd2',
            sshDeviceType: 'type2',
          },
        ],
        manekiSpecs: [],
      });
    });
  });
});
