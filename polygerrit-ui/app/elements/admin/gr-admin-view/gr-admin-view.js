// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  // Note: noBaseUrl: true is set on entries where the URL is not yet supported
  // by router abstraction.
  const ADMIN_LINKS = [{
    name: 'Projects',
    noBaseUrl: true,
    url: '/admin/projects',
    view: 'gr-project-list',
    viewableToAll: true,
    children: [],
  }, {
    name: 'Groups',
    section: 'Groups',
    noBaseUrl: true,
    url: '/admin/groups',
    view: 'gr-admin-group-list',
    children: [],
  }, {
    name: 'Plugins',
    capability: 'viewPlugins',
    section: 'Plugins',
    noBaseUrl: true,
    url: '/admin/plugins',
    view: 'gr-plugin-list',
  }];

  const ACCOUNT_CAPABILITIES = ['createProject', 'createGroup', 'viewPlugins'];

  Polymer({
    is: 'gr-admin-view',

    properties: {
      /** @type {?} */
      params: Object,
      path: String,
      adminView: String,

      _projectName: String,
      _groupId: {
        type: Number,
        observer: '_computeGroupName',
      },
      _groupName: String,
      _groupOwner: {
        type: Boolean,
        value: false,
      },
      _filteredLinks: Array,
      _showDownload: {
        type: Boolean,
        value: false,
      },
      _isAdmin: {
        type: Boolean,
        value: false,
      },
      _showGroup: Boolean,
      _showGroupAuditLog: Boolean,
      _showGroupList: Boolean,
      _showGroupMembers: Boolean,
      _showProjectCommands: Boolean,
      _showProjectMain: Boolean,
      _showProjectList: Boolean,
      _showProjectDetailList: Boolean,
      _showPluginList: Boolean,
      _showProjectAccess: Boolean,
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    observers: [
      '_paramsChanged(params)',
    ],

    attached() {
      this.reload();
    },

    reload() {
      return this.$.restAPI.getAccount().then(account => {
        this._account = account;
        if (!account) {
          // Return so that  account capabilities don't load with no account.
          return this._filteredLinks = this._filterLinks(link => {
            return link.viewableToAll;
          });
        }
        this._loadAccountCapabilities();
      });
    },

    _filterLinks(filterFn) {
      const links = ADMIN_LINKS.filter(filterFn);
      const filteredLinks = [];
      for (const link of links) {
        const linkCopy = Object.assign({}, link);
        linkCopy.children = linkCopy.children ?
            linkCopy.children.filter(filterFn) : [];
        if (linkCopy.name === 'Projects' && this._projectName) {
          linkCopy.subsection = {
            name: this._projectName,
            view: 'gr-project',
            noBaseUrl: true,
            url: `/admin/projects/${this.encodeURL(this._projectName, true)}`,
            children: [{
              name: 'Access',
              detailType: 'access',
              view: 'gr-project-access',
              noBaseUrl: true,
              url: `/admin/projects/` +
                  `${this.encodeURL(this._projectName, true)},access`,
            },
            {
              name: 'Commands',
              detailType: 'commands',
              view: 'gr-project-commands',
              noBaseUrl: true,
              url: `/admin/projects/` +
                  `${this.encodeURL(this._projectName, true)},commands`,
            },
            {
              name: 'Branches',
              detailType: 'branches',
              view: 'gr-project-detail-list',
              noBaseUrl: true,
              url: `/admin/projects/` +
                  `${this.encodeURL(this._projectName, true)},branches`,
            },
            {
              name: 'Tags',
              detailType: 'tags',
              view: 'gr-project-detail-list',
              noBaseUrl: true,
              url: `/admin/projects/` +
                  `${this.encodeURL(this._projectName, true)},tags`,
            }],
          };
        }
        if (linkCopy.name === 'Groups' && this._groupId && this._groupName) {
          linkCopy.subsection = {
            name: this._groupName,
            view: Gerrit.Nav.View.GROUP,
            url: Gerrit.Nav.getUrlForGroup(this._groupId),
            children: [
              {
                name: 'Members',
                detailType: Gerrit.Nav.GroupDetailView.MEMBERS,
                view: Gerrit.Nav.View.GROUP,
                url: Gerrit.Nav.getUrlForGroupMembers(this._groupId),
              },
            ],
          };
          if (this._isAdmin || this._groupOwner) {
            linkCopy.subsection.children.push(
                {
                  name: 'Audit Log',
                  detailType: Gerrit.Nav.GroupDetailView.LOG,
                  view: Gerrit.Nav.View.GROUP,
                  url: Gerrit.Nav.getUrlForGroupLog(this._groupId),
                }
            );
          }
        }
        filteredLinks.push(linkCopy);
      }
      return filteredLinks;
    },

    _loadAccountCapabilities() {
      return this.$.restAPI.getAccountCapabilities(ACCOUNT_CAPABILITIES)
          .then(capabilities => {
            this._filteredLinks = this._filterLinks(link => {
              return !link.capability ||
                  capabilities.hasOwnProperty(link.capability);
            });
          });
    },

    _paramsChanged(params) {
      const isGroupView = params.view === Gerrit.Nav.View.GROUP;
      const isAdminView = params.view === Gerrit.Nav.View.ADMIN;

      this.set('_showGroup', isGroupView && !params.detail);
      this.set('_showGroupAuditLog', isGroupView &&
          params.detail === Gerrit.Nav.GroupDetailView.LOG);
      this.set('_showGroupMembers', isGroupView &&
          params.detail === Gerrit.Nav.GroupDetailView.MEMBERS);

      this.set('_showGroupList', isAdminView &&
          params.adminView === 'gr-admin-group-list');

      this.set('_showProjectCommands', isAdminView &&
          params.adminView === 'gr-project-commands');
      this.set('_showProjectMain', isAdminView &&
          params.adminView === 'gr-project');
      this.set('_showProjectList', isAdminView &&
          params.adminView === 'gr-project-list');
      this.set('_showProjectDetailList', isAdminView &&
          params.adminView === 'gr-project-detail-list');
      this.set('_showPluginList', isAdminView &&
          params.adminView === 'gr-plugin-list');
      this.set('_showProjectAccess', isAdminView &&
          params.adminView === 'gr-project-access');

      if (params.project !== this._projectName) {
        this._projectName = params.project || '';
        // Reloads the admin menu.
        this.reload();
      }
      if (params.groupId !== this._groupId) {
        this._groupId = params.groupId || '';
        // Reloads the admin menu.
        this.reload();
      }
    },

    // TODO (beckysiegel): Update these functions after router abstraction is
    // updated. They are currently copied from gr-dropdown (and should be
    // updated there as well once complete).
    _computeURLHelper(host, path) {
      return '//' + host + this.getBaseUrl() + path;
    },

    _computeRelativeURL(path) {
      const host = window.location.host;
      return this._computeURLHelper(host, path);
    },

    _computeLinkURL(link) {
      if (!link || typeof link.url === 'undefined') { return ''; }
      if (link.target || !link.noBaseUrl) {
        return link.url;
      }
      return this._computeRelativeURL(link.url);
    },

    /**
     * @param {string} itemView
     * @param {Object} params
     * @param {string=} opt_detailType
     */
    _computeSelectedClass(itemView, params, opt_detailType) {
      // Group params are structured differently from admin params. Compute
      // selected differently for groups.
      // TODO(wyatta): Simplify this when all routes work like group params.
      if (params.view === Gerrit.Nav.View.GROUP &&
          itemView === Gerrit.Nav.View.GROUP) {
        if (!params.detail && !opt_detailType) { return 'selected'; }
        if (params.detail === opt_detailType) { return 'selected'; }
        return '';
      }

      if (params.detailType && params.detailType !== opt_detailType) {
        return '';
      }
      return itemView === params.adminView ? 'selected' : '';
    },

    _computeGroupName(groupId) {
      if (!groupId) { return ''; }
      const promises = [];
      this.$.restAPI.getGroupConfig(groupId).then(group => {
        this._groupName = group.name;
        this.reload();
        promises.push(this.$.restAPI.getIsAdmin().then(isAdmin => {
          this._isAdmin = isAdmin;
        }));
        promises.push(this.$.restAPI.getIsGroupOwner(group.name).then(
            isOwner => {
              this._groupOwner = isOwner;
            }));
        return Promise.all(promises).then(() => {
          this.reload();
        });
      });
    },

    _updateGroupName(e) {
      this._groupName = e.detail.name;
      this.reload();
    },
  });
})();
