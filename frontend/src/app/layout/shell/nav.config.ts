export type NavPermission = 'allFarms' | 'manageAccess' | 'advise';
export type NavBadge = 'alerts' | 'tasks';
export type NavDynamic = 'species' | 'facilities' | 'alerts';

export interface NavTarget {
  commands: string[];
  query?: Record<string, string>;
  fragment?: string;
}

export interface NavMatch {
  path?: string;
  exact?: boolean;
  query?: Record<string, string>;
  fragment?: string;
  withoutQuery?: string[];
  any?: NavMatch[];
}

export interface NavChildConfig {
  id: string;
  label: string;
  target: NavTarget;
  match?: NavMatch;
  permission?: NavPermission;
  species?: string;
}

export interface NavItemConfig {
  id: string;
  label: string;
  icon: string;
  target: NavTarget;
  match?: NavMatch;
  permission?: NavPermission;
  badge?: NavBadge;
  children?: NavChildConfig[];
  dynamic?: NavDynamic;
}

export interface NavSectionConfig {
  id: string;
  label: string;
  items: NavItemConfig[];
}

const page = (path: string, exact = true): NavMatch => ({ path, exact });
const section = (path: string, fragment: string): NavMatch => ({ path, exact: true, fragment });
const query = (path: string, key: string, value: string): NavMatch => ({ path, exact: true, query: { [key]: value } });

const speciesChild = (name: string): NavChildConfig => ({
  id: `animals-${name.toLowerCase()}`,
  label: name,
  species: name,
  target: { commands: ['/livestock'], query: { species: name } },
  match: query('/livestock', 'species', name)
});

export const NAV_SECTIONS: NavSectionConfig[] = [
  {
    id: 'workspace',
    label: 'Workspace',
    items: [
      {
        id: 'dashboard',
        label: 'Dashboard',
        icon: 'dashboard',
        target: { commands: ['/dashboard'] },
        match: page('/dashboard'),
        children: [
          {
            id: 'dash-overview',
            label: 'Overview',
            target: { commands: ['/dashboard'], fragment: 'overview' },
            match: { any: [page('/dashboard'), section('/dashboard', 'overview')] }
          },
          {
            id: 'dash-activity',
            label: 'Recent Activity',
            target: { commands: ['/dashboard'], fragment: 'activity' },
            match: section('/dashboard', 'activity')
          }
        ]
      },
      {
        id: 'farms',
        label: 'Farms',
        icon: 'farms',
        target: { commands: ['/farms'] },
        match: { path: '/farms', exact: true, withoutQuery: ['view'] },
        children: [
          {
            id: 'farms-all',
            label: 'All Farms',
            target: { commands: ['/farms'] },
            match: { path: '/farms', exact: true, withoutQuery: ['view'] }
          },
          {
            id: 'farms-map',
            label: 'Farm Map',
            target: { commands: ['/farms'], fragment: 'locations' },
            match: section('/farms', 'locations')
          },
          {
            id: 'farms-add',
            label: 'Add Farm',
            target: { commands: ['/farms', 'new'] },
            match: page('/farms/new'),
            permission: 'manageAccess'
          }
        ]
      },
      {
        id: 'facilities',
        label: 'Facilities',
        icon: 'facilities',
        target: { commands: ['/farms'], query: { view: 'facilities' } },
        match: {
          any: [
            query('/farms', 'view', 'facilities'),
            query('/farms', 'view', 'types'),
            query('/farms', 'view', 'maintenance'),
            { path: '/farms', query: { tab: 'facilities' } }
          ]
        },
        children: [
          {
            id: 'facilities-all',
            label: 'All Facilities',
            target: { commands: ['/farms'], query: { view: 'facilities' } },
            match: query('/farms', 'view', 'facilities')
          },
          {
            id: 'facilities-types',
            label: 'Facility Types',
            target: { commands: ['/farms'], query: { view: 'types' } },
            match: query('/farms', 'view', 'types')
          },
          {
            id: 'facilities-maintenance',
            label: 'Maintenance',
            target: { commands: ['/farms'], query: { view: 'maintenance' } },
            match: query('/farms', 'view', 'maintenance')
          }
        ]
      }
    ]
  },
  {
    id: 'livestock',
    label: 'Livestock',
    items: [
      {
        id: 'animals',
        label: 'Animals',
        icon: 'animals',
        target: { commands: ['/livestock'] },
        match: { path: '/livestock', exact: true, withoutQuery: ['species'] },
        children: [
          {
            id: 'animals-all',
            label: 'All Animals',
            target: { commands: ['/livestock'] },
            match: { path: '/livestock', exact: true, withoutQuery: ['species'] }
          },
          speciesChild('Cattle'),
          speciesChild('Goats'),
          speciesChild('Sheep'),
          speciesChild('Poultry'),
          speciesChild('Pigs'),
          speciesChild('Fish')
        ]
      },
      {
        id: 'movements',
        label: 'Movements',
        icon: 'movements',
        target: { commands: ['/livestock'], fragment: 'ledger' },
        match: {
          any: [
            section('/livestock', 'ledger'),
            section('/livestock', 'transfer'),
            section('/help', 'movements')
          ]
        },
        children: [
          {
            id: 'movements-ledger',
            label: 'Movement Ledger',
            target: { commands: ['/livestock'], fragment: 'ledger' },
            match: section('/livestock', 'ledger')
          },
          {
            id: 'movements-new',
            label: 'New Transfer',
            target: { commands: ['/livestock'], fragment: 'transfer' },
            match: section('/livestock', 'transfer')
          },
          {
            id: 'movements-history',
            label: 'Transfer History',
            target: { commands: ['/help'], fragment: 'movements' },
            match: section('/help', 'movements')
          }
        ]
      }
    ]
  },
  {
    id: 'operations',
    label: 'Operations',
    items: [
      {
        id: 'production',
        label: 'Production',
        icon: 'production',
        target: { commands: ['/production'] },
        match: page('/production'),
        children: [
          {
            id: 'production-overview',
            label: 'Overview',
            target: { commands: ['/production'], fragment: 'overview' },
            match: { any: [page('/production'), section('/production', 'overview')] }
          },
          {
            id: 'production-records',
            label: 'Production Records',
            target: { commands: ['/production'], fragment: 'records' },
            match: section('/production', 'records')
          },
          {
            id: 'production-cycles',
            label: 'Fish Cycles',
            target: { commands: ['/production'], fragment: 'cycles' },
            match: section('/production', 'cycles')
          }
        ]
      },
      {
        id: 'inventory',
        label: 'Inventory',
        icon: 'inventory',
        target: { commands: ['/inventory'] },
        match: page('/inventory'),
        children: [
          {
            id: 'inventory-stock',
            label: 'Stock Overview',
            target: { commands: ['/inventory'], fragment: 'stock' },
            match: {
              any: [
                { path: '/inventory', exact: true, withoutQuery: ['view'] },
                { path: '/inventory', exact: true, fragment: 'stock', withoutQuery: ['view'] }
              ]
            }
          },
          {
            id: 'inventory-adjust',
            label: 'Adjustments',
            target: { commands: ['/inventory'], fragment: 'adjustments' },
            match: section('/inventory', 'adjustments')
          },
          {
            id: 'inventory-low',
            label: 'Low-stock Alerts',
            target: { commands: ['/inventory'], query: { view: 'low' } },
            match: query('/inventory', 'view', 'low')
          }
        ]
      },
      {
        id: 'staff',
        label: 'Staff',
        icon: 'staff',
        target: { commands: ['/staff'] },
        match: page('/staff'),
        children: [
          {
            id: 'staff-directory',
            label: 'Staff Directory',
            target: { commands: ['/staff'], fragment: 'people' },
            match: { any: [page('/staff'), section('/staff', 'people')] }
          },
          {
            id: 'staff-assignments',
            label: 'Assignments',
            target: { commands: ['/staff'], fragment: 'assignments' },
            match: section('/staff', 'assignments')
          }
        ]
      },
      {
        id: 'tasks',
        label: 'Tasks',
        icon: 'tasks',
        badge: 'tasks',
        target: { commands: ['/staff'], fragment: 'tasks' },
        match: {
          any: [
            section('/staff', 'tasks'),
            section('/staff', 'my-tasks'),
            section('/staff', 'calendar')
          ]
        },
        children: [
          {
            id: 'tasks-mine',
            label: 'My Tasks',
            target: { commands: ['/staff'], fragment: 'my-tasks' },
            match: section('/staff', 'my-tasks')
          },
          {
            id: 'tasks-all',
            label: 'All Tasks',
            target: { commands: ['/staff'], fragment: 'tasks' },
            match: section('/staff', 'tasks')
          },
          {
            id: 'tasks-calendar',
            label: 'Task Calendar',
            target: { commands: ['/staff'], fragment: 'calendar' },
            match: section('/staff', 'calendar')
          }
        ]
      }
    ]
  },
  {
    id: 'insights',
    label: 'Insights',
    items: [
      {
        id: 'reports',
        label: 'Reports & Exports',
        icon: 'reports',
        target: { commands: ['/analytics'] },
        match: page('/analytics'),
        children: [
          {
            id: 'reports-operational',
            label: 'Operational Reports',
            target: { commands: ['/analytics'], fragment: 'reports' },
            match: { any: [page('/analytics'), section('/analytics', 'reports')] }
          },
          {
            id: 'reports-exports',
            label: 'Saved Exports',
            target: { commands: ['/analytics'], fragment: 'exports' },
            match: section('/analytics', 'exports')
          }
        ]
      },
      {
        id: 'alerts',
        label: 'Alerts',
        icon: 'alerts',
        badge: 'alerts',
        target: { commands: ['/alerts'] },
        match: page('/alerts'),
        children: [
          {
            id: 'alerts-active',
            label: 'Active Alerts',
            target: { commands: ['/alerts'], fragment: 'active' },
            match: { any: [page('/alerts'), section('/alerts', 'active')] }
          },
          {
            id: 'alerts-history',
            label: 'Alert History',
            target: { commands: ['/alerts'], fragment: 'history' },
            match: section('/alerts', 'history')
          },
          {
            id: 'alerts-rules',
            label: 'Alert Rules',
            target: { commands: ['/alerts'], fragment: 'rules' },
            match: section('/alerts', 'rules')
          },
          {
            id: 'alerts-report',
            label: 'Report health',
            target: { commands: ['/alerts'], fragment: 'report' },
            match: section('/alerts', 'report')
          }
        ]
      },
      {
        id: 'advisers',
        label: 'Farm Advisers',
        icon: 'advisers',
        target: { commands: ['/advisers'] },
        match: page('/advisers')
      },
      {
        id: 'advice',
        label: 'Expert Advice',
        icon: 'advice',
        target: { commands: ['/advice'] },
        match: page('/advice'),
        children: [
          {
            id: 'advice-board',
            label: 'All advice',
            target: { commands: ['/advice'], fragment: 'board' },
            match: { any: [page('/advice'), section('/advice', 'board')] }
          },
          {
            id: 'advice-post',
            label: 'Post advice',
            permission: 'advise',
            target: { commands: ['/advice'], fragment: 'post' },
            match: section('/advice', 'post')
          }
        ]
      }
    ]
  },
  {
    id: 'system',
    label: 'System',
    items: [
      {
        id: 'users',
        label: 'Users & Roles',
        icon: 'users',
        permission: 'manageAccess',
        target: { commands: ['/staff'], fragment: 'people' },
        match: {
          any: [
            section('/staff', 'people'),
            page('/access'),
            section('/access', 'permissions'),
            section('/access', 'assigned'),
            section('/access', 'activity')
          ]
        },
        children: [
          {
            id: 'users-list',
            label: 'Users',
            permission: 'allFarms',
            target: { commands: ['/staff'], fragment: 'people' },
            match: section('/staff', 'people')
          },
          {
            id: 'users-roles',
            label: 'Roles & Permissions',
            permission: 'allFarms',
            target: { commands: ['/access'] },
            match: {
              any: [
                page('/access'),
                section('/access', 'permissions'),
                section('/access', 'assigned')
              ]
            }
          },
          {
            id: 'users-audit',
            label: 'Access Audit',
            permission: 'allFarms',
            target: { commands: ['/access'], fragment: 'activity' },
            match: section('/access', 'activity')
          }
        ]
      },
      {
        id: 'settings',
        label: 'Settings',
        icon: 'settings',
        target: { commands: ['/settings'] },
        match: page('/settings'),
        children: [
          {
            id: 'settings-general',
            label: 'General Settings',
            target: { commands: ['/settings'], fragment: 'general' },
            match: { any: [page('/settings'), section('/settings', 'general')] }
          },
          {
            id: 'settings-master',
            label: 'Master Data',
            target: { commands: ['/settings'], fragment: 'master' },
            match: section('/settings', 'master')
          },
          {
            id: 'settings-integrations',
            label: 'Integrations',
            target: { commands: ['/settings'], fragment: 'integrations' },
            match: section('/settings', 'integrations')
          }
        ]
      },
      {
        id: 'help',
        label: 'Help & Support',
        icon: 'help',
        target: { commands: ['/help'] },
        match: page('/help'),
        children: [
          {
            id: 'help-centre',
            label: 'Help Centre',
            target: { commands: ['/help'], fragment: 'centre' },
            match: { any: [page('/help'), section('/help', 'centre')] }
          },
          {
            id: 'help-contact',
            label: 'Contact Support',
            target: { commands: ['/help'], fragment: 'contact' },
            match: section('/help', 'contact')
          },
          {
            id: 'help-status',
            label: 'System Status',
            target: { commands: ['/help'], fragment: 'status' },
            match: section('/help', 'status')
          }
        ]
      }
    ]
  }
];

export const ADVISER_SECTIONS: NavSectionConfig[] = [
  {
    id: 'workspace',
    label: 'Workspace',
    items: [
      {
        id: 'adviser-dashboard',
        label: 'Dashboard',
        icon: 'dashboard',
        target: { commands: ['/dashboard'] },
        match: page('/dashboard')
      },
      {
        id: 'adviser-farms',
        label: 'My Farms',
        icon: 'farms',
        target: { commands: ['/farms'] },
        match: { path: '/farms' }
      },
      {
        id: 'adviser-requests',
        label: 'Farmer Requests',
        icon: 'requests',
        target: { commands: ['/requests'] },
        match: page('/requests')
      },
      {
        id: 'adviser-cases',
        label: 'Advisory Cases',
        icon: 'cases',
        target: { commands: ['/alerts'], fragment: 'active' },
        match: { any: [page('/alerts'), section('/alerts', 'active')] }
      },
      {
        id: 'adviser-visits',
        label: 'Farm Visits',
        icon: 'visits',
        target: { commands: ['/staff'], fragment: 'calendar' },
        match: section('/staff', 'calendar')
      },
      {
        id: 'adviser-recommendations',
        label: 'Recommendations',
        icon: 'advice',
        target: { commands: ['/advice'] },
        match: { any: [page('/advice'), section('/advice', 'post')] }
      },
      {
        id: 'adviser-directory',
        label: 'Farm Advisers',
        icon: 'advisers',
        target: { commands: ['/advisers'] },
        match: page('/advisers')
      }
    ]
  },
  {
    id: 'operations',
    label: 'Operations',
    items: [
      {
        id: 'adviser-health',
        label: 'Animal Health',
        icon: 'health',
        target: { commands: ['/livestock'] },
        match: { path: '/livestock' }
      },
      {
        id: 'adviser-production',
        label: 'Production Insights',
        icon: 'production',
        target: { commands: ['/production'] },
        match: page('/production')
      },
      {
        id: 'adviser-tasks',
        label: 'Tasks',
        icon: 'tasks',
        badge: 'tasks',
        target: { commands: ['/staff'], fragment: 'tasks' },
        match: { any: [page('/staff'), section('/staff', 'tasks')] }
      },
      {
        id: 'adviser-knowledge',
        label: 'Knowledge Centre',
        icon: 'knowledge',
        target: { commands: ['/advice'], fragment: 'board' },
        match: section('/advice', 'board')
      }
    ]
  },
  {
    id: 'insights',
    label: 'Insights',
    items: [
      {
        id: 'adviser-reports',
        label: 'Reports',
        icon: 'reports',
        target: { commands: ['/analytics'], fragment: 'reports' },
        match: { any: [page('/analytics'), section('/analytics', 'reports')] }
      },
      {
        id: 'adviser-notifications',
        label: 'Notifications',
        icon: 'alerts',
        badge: 'alerts',
        target: { commands: ['/alerts'], fragment: 'history' },
        match: section('/alerts', 'history')
      }
    ]
  },
  {
    id: 'support',
    label: 'Support',
    items: [
      {
        id: 'adviser-help',
        label: 'Help & Support',
        icon: 'help',
        target: { commands: ['/help'] },
        match: { path: '/help' }
      }
    ]
  }
];
