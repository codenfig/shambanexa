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
  icon?: string;
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
  icon: string;
  items: NavItemConfig[];
}

const page = (path: string, exact = true): NavMatch => ({ path, exact });
const section = (path: string, fragment: string): NavMatch => ({ path, exact: true, fragment });
const query = (path: string, key: string, value: string): NavMatch => ({ path, exact: true, query: { [key]: value } });

const speciesChild = (name: string): NavChildConfig => ({
  id: `animals-${name.toLowerCase()}`,
  label: name,
  species: name,
  target: { commands: ['/livestock', 'species', name] },
  match: page(`/livestock/species/${name}`)
});

export const NAV_SECTIONS: NavSectionConfig[] = [
  {
    id: 'workspace',
    label: 'Workspace',
    icon: 'workspace',
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
    icon: 'livestock',
    items: [
      {
        id: 'animals',
        label: 'Animals',
        icon: 'animals',
        target: { commands: ['/livestock'] },
        match: { path: '/livestock' },
        children: [
          {
            id: 'animals-overview',
            label: 'Animal Overview',
            target: { commands: ['/livestock'] },
            match: { path: '/livestock', exact: true }
          },
          {
            id: 'animals-registry',
            label: 'Animal Registry',
            target: { commands: ['/livestock', 'registry'] },
            match: page('/livestock/registry')
          },
          speciesChild('Cattle'),
          speciesChild('Goats'),
          speciesChild('Sheep'),
          speciesChild('Poultry'),
          speciesChild('Pigs'),
          speciesChild('Fish'),
          {
            id: 'animals-herds',
            label: 'Herd Batches',
            target: { commands: ['/livestock', 'herds'] },
            match: page('/livestock/herds')
          },
          {
            id: 'animals-health-center',
            label: 'Health & Veterinary',
            target: { commands: ['/health'] },
            match: page('/health')
          },
          {
            id: 'animals-health',
            label: 'Health Records',
            target: { commands: ['/livestock', 'health'] },
            match: page('/livestock/health')
          }
        ]
      },
      {
        id: 'movements',
        label: 'Movements',
        icon: 'movements',
        target: { commands: ['/livestock', 'registry'] },
        match: {
          any: [
            page('/livestock/registry'),
            page('/livestock/transfers'),
            section('/help', 'movements')
          ]
        },
        children: [
          {
            id: 'movements-ledger',
            label: 'Movement Ledger',
            target: { commands: ['/livestock', 'registry'] },
            match: page('/livestock/registry')
          },
          {
            id: 'movements-new',
            label: 'New Transfer',
            target: { commands: ['/livestock', 'registry'] }
          },
          {
            id: 'movements-history',
            label: 'Transfer History',
            target: { commands: ['/livestock', 'transfers'] },
            match: page('/livestock/transfers')
          }
        ]
      }
    ]
  },
  {
    id: 'operations',
    label: 'Operations',
    icon: 'operations',
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
        match: { path: '/inventory' },
        children: [
          {
            id: 'inventory-stock',
            label: 'Stock Overview',
            icon: 'chart',
            target: { commands: ['/inventory'] },
            match: { path: '/inventory', exact: true }
          },
          {
            id: 'inventory-receipts',
            label: 'Stock Receipts',
            icon: 'truck',
            target: { commands: ['/inventory', 'receipts'] },
            match: page('/inventory/receipts')
          },
          {
            id: 'inventory-issues',
            label: 'Stock Issues',
            icon: 'issue',
            target: { commands: ['/inventory', 'issues'] },
            match: page('/inventory/issues')
          },
          {
            id: 'inventory-adjust',
            label: 'Adjustments',
            icon: 'doc',
            target: { commands: ['/inventory', 'adjustments'] },
            match: page('/inventory/adjustments')
          },
          {
            id: 'inventory-transfers',
            label: 'Transfers',
            icon: 'transfer',
            target: { commands: ['/inventory', 'transfers'] },
            match: page('/inventory/transfers')
          },
          {
            id: 'inventory-suppliers',
            label: 'Suppliers',
            icon: 'people',
            target: { commands: ['/inventory', 'suppliers'] },
            match: page('/inventory/suppliers')
          },
          {
            id: 'inventory-low',
            label: 'Low-stock Alerts',
            icon: 'bell',
            target: { commands: ['/inventory', 'low'] },
            match: page('/inventory/low')
          },
          {
            id: 'inventory-reports',
            label: 'Reports',
            icon: 'chart',
            target: { commands: ['/inventory', 'reports'] },
            match: page('/inventory/reports')
          }
        ]
      },
      {
        id: 'staff',
        label: 'Staff',
        icon: 'staff',
        target: { commands: ['/staff'] },
        match: { path: '/staff' },
        children: [
          {
            id: 'staff-overview',
            label: 'Staff Overview',
            icon: 'chart',
            target: { commands: ['/staff'] },
            match: { path: '/staff', exact: true }
          },
          {
            id: 'staff-directory',
            label: 'Staff Directory',
            icon: 'people',
            target: { commands: ['/staff', 'directory'] },
            match: { path: '/staff/directory' }
          },
          {
            id: 'staff-assignments',
            label: 'Assignments',
            icon: 'doc',
            target: { commands: ['/staff', 'assignments'] },
            match: page('/staff/assignments')
          },
          {
            id: 'staff-attendance',
            label: 'Attendance & Shifts',
            icon: 'clock',
            target: { commands: ['/staff', 'attendance'] },
            match: page('/staff/attendance')
          },
          {
            id: 'staff-performance',
            label: 'Performance & Training',
            icon: 'award',
            target: { commands: ['/staff', 'performance'] },
            match: page('/staff/performance')
          }
        ]
      },
      {
        id: 'tasks',
        label: 'Tasks',
        icon: 'tasks',
        badge: 'tasks',
        target: { commands: ['/staff', 'assignments'], fragment: 'tasks' },
        match: {
          any: [
            section('/staff/assignments', 'tasks'),
            section('/staff/assignments', 'my-tasks'),
            section('/staff/assignments', 'calendar')
          ]
        },
        children: [
          {
            id: 'tasks-mine',
            label: 'My Tasks',
            target: { commands: ['/staff', 'assignments'], fragment: 'my-tasks' },
            match: section('/staff/assignments', 'my-tasks')
          },
          {
            id: 'tasks-all',
            label: 'All Tasks',
            target: { commands: ['/staff', 'assignments'], fragment: 'tasks' },
            match: section('/staff/assignments', 'tasks')
          },
          {
            id: 'tasks-calendar',
            label: 'Task Calendar',
            target: { commands: ['/staff', 'assignments'], fragment: 'calendar' },
            match: section('/staff/assignments', 'calendar')
          }
        ]
      }
    ]
  },
  {
    id: 'medical',
    label: 'Medical Suppliers',
    icon: 'medical',
    items: [
      {
        id: 'medical-suppliers',
        label: 'Medical Suppliers',
        icon: 'medical',
        target: { commands: ['/suppliers'] },
        match: page('/suppliers')
      }
    ]
  },
  {
    id: 'market',
    label: 'Agricultural Marketplace',
    icon: 'market',
    items: [
      {
        id: 'market-home',
        label: 'Overview',
        icon: 'dashboard',
        target: { commands: ['/market'] },
        match: page('/market')
      },
      {
        id: 'market-buy',
        label: 'Buy Products',
        icon: 'market',
        target: { commands: ['/market'], fragment: 'buy' },
        match: section('/market', 'buy')
      },
      {
        id: 'market-sell',
        label: 'Sell Products',
        icon: 'inventory',
        target: { commands: ['/market'], fragment: 'sell' },
        match: section('/market', 'sell')
      },
      {
        id: 'market-requests',
        label: 'Buying Requests',
        icon: 'requests',
        target: { commands: ['/market'], fragment: 'requests' },
        match: section('/market', 'requests')
      },
      {
        id: 'market-orders',
        label: 'My Orders',
        icon: 'tasks',
        target: { commands: ['/market'], fragment: 'orders' },
        match: section('/market', 'orders')
      },
      {
        id: 'market-messages',
        label: 'Messages',
        icon: 'advice',
        target: { commands: ['/market'], fragment: 'messages' },
        match: section('/market', 'messages')
      }
    ]
  },
  {
    id: 'insights',
    label: 'Insights',
    icon: 'insights',
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
        match: { path: '/alerts' },
        children: [
          {
            id: 'alerts-active',
            label: 'Active Alerts',
            target: { commands: ['/alerts'] },
            match: page('/alerts')
          },
          {
            id: 'alerts-history',
            label: 'Alert History',
            target: { commands: ['/alerts', 'history'] },
            match: page('/alerts/history')
          },
          {
            id: 'alerts-rules',
            label: 'Alert Rules',
            target: { commands: ['/alerts', 'rules'] },
            match: page('/alerts/rules')
          },
          {
            id: 'alerts-report',
            label: 'Report health',
            target: { commands: ['/alerts', 'report'] },
            match: page('/alerts/report')
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
    icon: 'system',
    items: [
      {
        id: 'users',
        label: 'Users & Roles',
        icon: 'users',
        permission: 'manageAccess',
        target: { commands: ['/staff', 'directory'] },
        match: {
          any: [
            { path: '/staff/directory' },
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
            target: { commands: ['/staff', 'directory'] },
            match: page('/staff/directory')
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
            label: 'General',
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
          },
          {
            id: 'settings-security',
            label: 'Security',
            target: { commands: ['/settings'], fragment: 'security' },
            match: section('/settings', 'security')
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
    icon: 'workspace',
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
        target: { commands: ['/alerts'] },
        match: page('/alerts')
      },
      {
        id: 'adviser-visits',
        label: 'Farm Visits',
        icon: 'visits',
        target: { commands: ['/staff', 'assignments'], fragment: 'calendar' },
        match: section('/staff/assignments', 'calendar')
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
      },
      {
        id: 'adviser-medical',
        label: 'Medical Suppliers',
        icon: 'medical',
        target: { commands: ['/suppliers'] },
        match: page('/suppliers')
      },
      {
        id: 'adviser-market',
        label: 'Agricultural Marketplace',
        icon: 'market',
        target: { commands: ['/market'] },
        match: { any: [page('/market'), section('/market', 'buy'), section('/market', 'sell'), section('/market', 'requests'), section('/market', 'orders'), section('/market', 'messages')] }
      }
    ]
  },
  {
    id: 'operations',
    label: 'Operations',
    icon: 'operations',
    items: [
      {
        id: 'adviser-health',
        label: 'Animal Health',
        icon: 'health',
        target: { commands: ['/health'] },
        match: page('/health')
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
        target: { commands: ['/staff', 'assignments'], fragment: 'tasks' },
        match: section('/staff/assignments', 'tasks')
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
    icon: 'insights',
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
        target: { commands: ['/alerts', 'history'] },
        match: page('/alerts/history')
      }
    ]
  },
  {
    id: 'support',
    label: 'Support',
    icon: 'support',
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
