/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

define(['require',
    'backbone',
    'marionette',
    'hbs!tmpl/minio/MinioDashboardView_tmpl',
    'collection/VMinioList',
    'utils/MinIOUtils',
    'utils/Utils',
    'utils/Globals'
], function(require, Backbone, Marionette, MinioDashboardView_tmpl, VMinioList, MinIOUtils, Utils, Globals) {
    'use strict';

    var MinioDashboardView = Backbone.Marionette.LayoutView.extend(
        /** @lends MinioDashboardView */
        {
            _viewName: 'MinioDashboardView',

            template: MinioDashboardView_tmpl,

            /** Layout sub regions */
            regions: {},

            /** ui selector cache */
            ui: {
                connectionStatus: '.connection-status',
                connectionStatusIcon: '.connection-status-icon',
                connectionStatusText: '.connection-status-text',
                bucketCount: '.bucket-count',
                totalSize: '.total-size',
                totalObjects: '.total-objects',
                syncStatus: '.sync-status',
                syncStatusIcon: '.sync-status-icon',
                syncStatusText: '.sync-status-text',
                testConnectionBtn: '.test-connection-btn',
                syncNowBtn: '.sync-now-btn',
                refreshBtn: '.refresh-btn'
            },

            /** ui events hash */
            events: function() {
                var events = {};
                events['click ' + this.ui.testConnectionBtn] = 'onTestConnection';
                events['click ' + this.ui.syncNowBtn] = 'onSyncNow';
                events['click ' + this.ui.refreshBtn] = 'onRefresh';
                return events;
            },

            /**
             * Initialize a new MinioDashboardView Layout
             * @constructs
             */
            initialize: function(options) {
                _.extend(this, _.pick(options, 'collection'));
                this.bucketCollection = new VMinioList.VMinioBucketList();
                this.syncStatus = null;
                this.connectionStatus = null;
            },

            onRender: function() {
                this.loadConnectionStatus();
                this.loadDashboardStats();
                this.loadSyncStatus();

                // Auto-refresh every 30 seconds
                this.refreshInterval = setInterval(_.bind(this.onRefresh, this), 30000);
            },

            onDestroy: function() {
                if (this.refreshInterval) {
                    clearInterval(this.refreshInterval);
                }
            },

            loadConnectionStatus: function() {
                var that = this;
                MinIOUtils.testConnection()
                    .done(function(data) {
                        that.connectionStatus = data.connected ? 'ONLINE' : 'OFFLINE';
                        that.updateConnectionUI(data);
                    })
                    .fail(function(xhr, status, error) {
                        that.connectionStatus = 'OFFLINE';
                        that.updateConnectionUI({ connected: false, error: error });
                    });
            },

            updateConnectionUI: function(data) {
                var status = data.connected ? 'ONLINE' : 'OFFLINE';
                var statusClass = data.connected ? 'fa-check-circle text-success' : 'fa-times-circle text-danger';
                var statusText = data.connected ? 'Connected' : 'Disconnected';

                this.ui.connectionStatusIcon
                    .removeClass()
                    .addClass('fa ' + statusClass)
                    .attr('title', status);

                this.ui.connectionStatusText.text(statusText);

                if (!data.connected && data.error) {
                    this.ui.connectionStatusText.attr('title', data.error);
                }
            },

            loadDashboardStats: function() {
                var that = this;
                this.bucketCollection.fetch({
                    success: function(collection, response) {
                        that.updateStatsUI(collection);
                    },
                    error: function(model, response) {
                        Utils.showError(response);
                    }
                });
            },

            updateStatsUI: function(collection) {
                var bucketCount = collection.length;
                var totalSize = 0;
                var totalObjects = 0;

                collection.each(function(bucket) {
                    totalSize += bucket.get('totalSize') || 0;
                    totalObjects += bucket.get('objectCount') || 0;
                });

                this.ui.bucketCount.text(bucketCount);
                this.ui.totalSize.text(MinIOUtils.formatSize(totalSize));
                this.ui.totalObjects.text(totalObjects.toLocaleString());
            },

            loadSyncStatus: function() {
                var that = this;
                MinIOUtils.getSyncStatus()
                    .done(function(data) {
                        that.syncStatus = data;
                        that.updateSyncUI(data);
                    })
                    .fail(function(xhr, status, error) {
                        that.ui.syncStatusText.text('Unable to fetch sync status');
                    });
            },

            updateSyncUI: function(data) {
                var status = data.status || 'UNKNOWN';
                var statusIcon = MinIOUtils.getStatusIcon(status);
                var statusText = status.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, function(l) {
                    return l.toUpperCase();
                });

                this.ui.syncStatusIcon
                    .removeClass()
                    .addClass('fa ' + statusIcon);

                this.ui.syncStatusText.text(statusText);

                if (data.lastSyncTime) {
                    this.ui.syncStatusText.attr('title', 'Last sync: ' + MinIOUtils.formatDate(data.lastSyncTime));
                }
            },

            onTestConnection: function() {
                var that = this;
                this.ui.testConnectionBtn.prop('disabled', true).text('Testing...');

                MinIOUtils.testConnection()
                    .done(function(data) {
                        that.updateConnectionUI(data);
                        Utils.showSuccess('Connection test successful');
                    })
                    .fail(function(xhr, status, error) {
                        that.updateConnectionUI({ connected: false, error: error });
                        Utils.showError('Connection test failed: ' + error);
                    })
                    .always(function() {
                        that.ui.testConnectionBtn.prop('disabled', false).text('Test Connection');
                    });
            },

            onSyncNow: function() {
                var that = this;
                this.ui.syncNowBtn.prop('disabled', true).text('Syncing...');

                MinIOUtils.triggerSync('incremental')
                    .done(function(data) {
                        Utils.showSuccess('Sync started successfully');
                        that.loadSyncStatus();
                    })
                    .fail(function(xhr, status, error) {
                        var errorMsg = xhr.responseJSON && xhr.responseJSON.error || error || 'Sync failed';
                        Utils.showError(errorMsg);
                    })
                    .always(function() {
                        that.ui.syncNowBtn.prop('disabled', false).text('Sync Now');
                    });
            },

            onRefresh: function() {
                this.loadConnectionStatus();
                this.loadDashboardStats();
                this.loadSyncStatus();
            }
        });

    return MinioDashboardView;
});
