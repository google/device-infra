import {
  Fleet,
  GlobalSummary,
  SearchEntity,
} from '@deviceinfra/app/core/models/home';

/** Mock global summary for Home page OmniLab Summary card. */
export const MOCK_GLOBAL_SUMMARY: GlobalSummary = {
  firstParty: {
    hosts: {
      count: 5124,
      search: {
        entity: SearchEntity.SEARCH_ENTITY_HOST,
        fleet: Fleet.FLEET_SELF,
        filters: [],
      },
    },
    devices: {
      count: 151216,
      search: {
        entity: SearchEntity.SEARCH_ENTITY_DEVICE,
        fleet: Fleet.FLEET_SELF,
        filters: [],
      },
    },
    utilization: {
      busy: {
        count: 31834,
        search: {
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          fleet: Fleet.FLEET_SELF,
          filters: [
            {
              key: 'field::status',
              simple: {values: [{value: 'BUSY'}]},
            },
          ],
        },
      },
      idle: {
        count: 90757,
        search: {
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          fleet: Fleet.FLEET_SELF,
          filters: [
            {
              key: 'field::status',
              simple: {values: [{value: 'IDLE'}]},
            },
          ],
        },
      },
      others: {
        count: 28625,
        search: {
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          fleet: Fleet.FLEET_SELF,
          filters: [
            {
              key: 'field::status',
              simple: {
                values: [{value: 'BUSY'}, {value: 'IDLE'}],
                negated: true,
              },
            },
          ],
        },
      },
    },
    labTypes: [
      {
        id: 'core',
        displayName: 'Core Lab',
        description: 'Shared pool, operated by OmniLab team',
        hosts: {
          count: 1420,
          search: {
            entity: SearchEntity.SEARCH_ENTITY_HOST,
            fleet: Fleet.FLEET_SELF,
            filters: [
              {
                key: 'host_field::lab_type',
                simple: {values: [{value: 'Core Lab'}]},
              },
            ],
          },
        },
        devices: {
          count: 48390,
          search: {
            entity: SearchEntity.SEARCH_ENTITY_DEVICE,
            fleet: Fleet.FLEET_SELF,
            filters: [
              {
                key: 'host_field::lab_type',
                simple: {values: [{value: 'Core Lab'}]},
              },
            ],
          },
        },
        utilization: {
          busy: {
            count: 11614,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_SELF,
              filters: [
                {
                  key: 'host_field::lab_type',
                  simple: {values: [{value: 'Core Lab'}]},
                },
                {
                  key: 'field::status',
                  simple: {values: [{value: 'BUSY'}]},
                },
              ],
            },
          },
          idle: {
            count: 30002,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_SELF,
              filters: [
                {
                  key: 'host_field::lab_type',
                  simple: {values: [{value: 'Core Lab'}]},
                },
                {
                  key: 'field::status',
                  simple: {values: [{value: 'IDLE'}]},
                },
              ],
            },
          },
          others: {
            count: 6774,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_SELF,
              filters: [
                {
                  key: 'host_field::lab_type',
                  simple: {values: [{value: 'Core Lab'}]},
                },
                {
                  key: 'field::status',
                  simple: {
                    values: [{value: 'BUSY'}, {value: 'IDLE'}],
                    negated: true,
                  },
                },
              ],
            },
          },
        },
      },
      {
        id: 'satellite',
        displayName: 'Satellite Lab',
        description: 'Dedicated pools, operated by OmniLab customers',
        hosts: {
          count: 2894,
          search: {
            entity: SearchEntity.SEARCH_ENTITY_HOST,
            fleet: Fleet.FLEET_SELF,
            filters: [
              {
                key: 'host_field::lab_type',
                simple: {values: [{value: 'Satellite Lab'}]},
              },
            ],
          },
        },
        devices: {
          count: 78632,
          search: {
            entity: SearchEntity.SEARCH_ENTITY_DEVICE,
            fleet: Fleet.FLEET_SELF,
            filters: [
              {
                key: 'host_field::lab_type',
                simple: {values: [{value: 'Satellite Lab'}]},
              },
            ],
          },
        },
        utilization: {
          busy: {
            count: 14940,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_SELF,
              filters: [
                {
                  key: 'host_field::lab_type',
                  simple: {values: [{value: 'Satellite Lab'}]},
                },
                {
                  key: 'field::status',
                  simple: {values: [{value: 'BUSY'}]},
                },
              ],
            },
          },
          idle: {
            count: 45607,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_SELF,
              filters: [
                {
                  key: 'host_field::lab_type',
                  simple: {values: [{value: 'Satellite Lab'}]},
                },
                {
                  key: 'field::status',
                  simple: {values: [{value: 'IDLE'}]},
                },
              ],
            },
          },
          others: {
            count: 18085,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_SELF,
              filters: [
                {
                  key: 'host_field::lab_type',
                  simple: {values: [{value: 'Satellite Lab'}]},
                },
                {
                  key: 'field::status',
                  simple: {
                    values: [{value: 'BUSY'}, {value: 'IDLE'}],
                    negated: true,
                  },
                },
              ],
            },
          },
        },
      },
      {
        id: 'slaas',
        displayName: 'SLaaS (Satellite-Lab-as-a-Service) Lab',
        description:
          'Dedicated pools, funded by OmniLab customers, operated by OmniLab team',
        hosts: {
          count: 810,
          search: {
            entity: SearchEntity.SEARCH_ENTITY_HOST,
            fleet: Fleet.FLEET_SELF,
            filters: [
              {
                key: 'host_field::lab_type',
                simple: {values: [{value: 'SLaaS'}]},
              },
            ],
          },
        },
        devices: {
          count: 24194,
          search: {
            entity: SearchEntity.SEARCH_ENTITY_DEVICE,
            fleet: Fleet.FLEET_SELF,
            filters: [
              {
                key: 'host_field::lab_type',
                simple: {values: [{value: 'SLaaS'}]},
              },
            ],
          },
        },
        utilization: {
          busy: {
            count: 5280,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_SELF,
              filters: [
                {
                  key: 'host_field::lab_type',
                  simple: {values: [{value: 'SLaaS'}]},
                },
                {
                  key: 'field::status',
                  simple: {values: [{value: 'BUSY'}]},
                },
              ],
            },
          },
          idle: {
            count: 15148,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_SELF,
              filters: [
                {
                  key: 'host_field::lab_type',
                  simple: {values: [{value: 'SLaaS'}]},
                },
                {
                  key: 'field::status',
                  simple: {values: [{value: 'IDLE'}]},
                },
              ],
            },
          },
          others: {
            count: 3766,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_SELF,
              filters: [
                {
                  key: 'host_field::lab_type',
                  simple: {values: [{value: 'SLaaS'}]},
                },
                {
                  key: 'field::status',
                  simple: {
                    values: [{value: 'BUSY'}, {value: 'IDLE'}],
                    negated: true,
                  },
                },
              ],
            },
          },
        },
      },
    ],
  },
  partnerAtsLabs: {
    labCount: 12,
    hosts: {
      count: 24,
      search: {
        entity: SearchEntity.SEARCH_ENTITY_HOST,
        fleet: Fleet.FLEET_ATS,
        filters: [],
      },
    },
    devices: {
      count: 300,
      search: {
        entity: SearchEntity.SEARCH_ENTITY_DEVICE,
        fleet: Fleet.FLEET_ATS,
        filters: [],
      },
    },
    utilization: {
      busy: {
        count: 90,
        search: {
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          fleet: Fleet.FLEET_ATS,
          filters: [
            {
              key: 'field::status',
              simple: {values: [{value: 'BUSY'}]},
            },
          ],
        },
      },
      idle: {
        count: 180,
        search: {
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          fleet: Fleet.FLEET_ATS,
          filters: [
            {
              key: 'field::status',
              simple: {values: [{value: 'IDLE'}]},
            },
          ],
        },
      },
      others: {
        count: 30,
        search: {
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          fleet: Fleet.FLEET_ATS,
          filters: [
            {
              key: 'field::status',
              simple: {
                values: [{value: 'BUSY'}, {value: 'IDLE'}],
                negated: true,
              },
            },
          ],
        },
      },
    },
    labs: [
      {
        controllerId: 'xiaomi',
        displayName: 'Xiaomi',
        hosts: {
          count: 10,
          search: {
            entity: SearchEntity.SEARCH_ENTITY_HOST,
            fleet: Fleet.FLEET_ATS,
            filters: [
              {
                key: 'host::ats_controller',
                simple: {values: [{value: 'xiaomi'}]},
              },
            ],
          },
        },
        devices: {
          count: 150,
          search: {
            entity: SearchEntity.SEARCH_ENTITY_DEVICE,
            fleet: Fleet.FLEET_ATS,
            filters: [
              {
                key: 'host::ats_controller',
                simple: {values: [{value: 'xiaomi'}]},
              },
            ],
          },
        },
        utilization: {
          busy: {
            count: 50,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_ATS,
              filters: [
                {
                  key: 'host::ats_controller',
                  simple: {values: [{value: 'xiaomi'}]},
                },
                {
                  key: 'field::status',
                  simple: {values: [{value: 'BUSY'}]},
                },
              ],
            },
          },
          idle: {
            count: 90,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_ATS,
              filters: [
                {
                  key: 'host::ats_controller',
                  simple: {values: [{value: 'xiaomi'}]},
                },
                {
                  key: 'field::status',
                  simple: {values: [{value: 'IDLE'}]},
                },
              ],
            },
          },
          others: {
            count: 10,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_ATS,
              filters: [
                {
                  key: 'host::ats_controller',
                  simple: {values: [{value: 'xiaomi'}]},
                },
                {
                  key: 'field::status',
                  simple: {
                    values: [{value: 'BUSY'}, {value: 'IDLE'}],
                    negated: true,
                  },
                },
              ],
            },
          },
        },
      },
      {
        controllerId: 'samsung',
        displayName: 'Samsung',
        hosts: {
          count: 8,
          search: {
            entity: SearchEntity.SEARCH_ENTITY_HOST,
            fleet: Fleet.FLEET_ATS,
            filters: [
              {
                key: 'host::ats_controller',
                simple: {values: [{value: 'samsung'}]},
              },
            ],
          },
        },
        devices: {
          count: 100,
          search: {
            entity: SearchEntity.SEARCH_ENTITY_DEVICE,
            fleet: Fleet.FLEET_ATS,
            filters: [
              {
                key: 'host::ats_controller',
                simple: {values: [{value: 'samsung'}]},
              },
            ],
          },
        },
        utilization: {
          busy: {
            count: 20,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_ATS,
              filters: [
                {
                  key: 'host::ats_controller',
                  simple: {values: [{value: 'samsung'}]},
                },
                {
                  key: 'field::status',
                  simple: {values: [{value: 'BUSY'}]},
                },
              ],
            },
          },
          idle: {
            count: 70,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_ATS,
              filters: [
                {
                  key: 'host::ats_controller',
                  simple: {values: [{value: 'samsung'}]},
                },
                {
                  key: 'field::status',
                  simple: {values: [{value: 'IDLE'}]},
                },
              ],
            },
          },
          others: {
            count: 10,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_ATS,
              filters: [
                {
                  key: 'host::ats_controller',
                  simple: {values: [{value: 'samsung'}]},
                },
                {
                  key: 'field::status',
                  simple: {
                    values: [{value: 'BUSY'}, {value: 'IDLE'}],
                    negated: true,
                  },
                },
              ],
            },
          },
        },
      },
      {
        controllerId: 'oppo',
        displayName: 'Oppo',
        hosts: {
          count: 6,
          search: {
            entity: SearchEntity.SEARCH_ENTITY_HOST,
            fleet: Fleet.FLEET_ATS,
            filters: [
              {
                key: 'host::ats_controller',
                simple: {values: [{value: 'oppo'}]},
              },
            ],
          },
        },
        devices: {
          count: 50,
          search: {
            entity: SearchEntity.SEARCH_ENTITY_DEVICE,
            fleet: Fleet.FLEET_ATS,
            filters: [
              {
                key: 'host::ats_controller',
                simple: {values: [{value: 'oppo'}]},
              },
            ],
          },
        },
        utilization: {
          busy: {
            count: 40,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_ATS,
              filters: [
                {
                  key: 'host::ats_controller',
                  simple: {values: [{value: 'oppo'}]},
                },
                {
                  key: 'field::status',
                  simple: {values: [{value: 'BUSY'}]},
                },
              ],
            },
          },
          idle: {
            count: 10,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_ATS,
              filters: [
                {
                  key: 'host::ats_controller',
                  simple: {values: [{value: 'oppo'}]},
                },
                {
                  key: 'field::status',
                  simple: {values: [{value: 'IDLE'}]},
                },
              ],
            },
          },
          others: {
            count: 0,
            search: {
              entity: SearchEntity.SEARCH_ENTITY_DEVICE,
              fleet: Fleet.FLEET_ATS,
              filters: [
                {
                  key: 'host::ats_controller',
                  simple: {values: [{value: 'oppo'}]},
                },
                {
                  key: 'field::status',
                  simple: {
                    values: [{value: 'BUSY'}, {value: 'IDLE'}],
                    negated: true,
                  },
                },
              ],
            },
          },
        },
      },
    ],
  },
};
