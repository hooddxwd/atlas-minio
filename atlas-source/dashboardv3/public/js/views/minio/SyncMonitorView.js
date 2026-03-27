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
    'hbs!tmpl/minio/SyncMonitorView_tmpl',
    'collection/VMinioList',
    'utils/MinIOUtils',
    'utils/Utils',
    'utils/Globals'
], function(require, Backbone, Marionette, SyncMonitorView_tmpl, VMinioList, MinIOUtils, Utils, Globals) {
    'use strict';

    var SyncMonitorView = Backbone.Marionette.LayoutView.extend(
        /** @lends SyncMonitorView */
        {
            _viewName: 'SyncMonitorView',

            template: SyncMonitorView_tmpl,

            /** Layout sub regions */
            regions: {
                rSyncHistoryTable: "#r_syncHistoryTable"
            },

            /** ui selector cache */
            ui: {
                syncStatusIcon: '.sync-status-icon',
                syncStatusText: '.sync-status-text',
                syncProgressBar: '.sync-progress-bar',
                syncProgressText: '.sync-progress-text',
                syncProcessedCount: '.sync-processed-count',
                syncFailedCount: '.sync-failed-count',
                syncDuration: '.sync-duration',
                syncLastSyncTime: '.sync-last-sync-time',
                syncNowBtn: '.sync-now-btn',
                fullSyncBtn: '.full-sync-btn',
                refreshBtn: '.refresh-btn',
                syncHistoryDays: '.sync-history-days'
            },

            /** ui events hash */
            events: function() {
                var events = {};
                events['click ' + this.ui.syncNowBtn] = 'onSyncNow';
                events['click ' + this.ui.fullSyncBtn] = 'onFullSync';
                events['click ' + this.ui.refreshBtn] = 'onRefresh';
                events['change ' + this.ui.syncHistoryDays] = 'onHistoryDaysChange';
                return events;
            },

            /**
             * Initialize a new SyncMonitorView Layout
             * @constructs
             */
            initialize: function(options) {
                _.extend(this, _.pick(options, 'collection'));
                this.syncEventCollection = new VMinioList.VMinioSyncEventList();
                this.syncStatus = null;
                this.historyDays = 7;
            },

            onRender: function() {
                this.loadSyncStatus();
                this.loadSyncHistory();

                // Auto-refresh every 10 seconds when syncing
                this.startAutoRefresh();
            },

            onDestroy: function() {
                this.stopAutoRefresh();
            },

            startAutoRefresh: function() {
                var that = this;
                this.autoRefreshInterval = setInterval(function() {
                    if (that.syncStatus && (that.syncStatus.status === 'IN_PROGRESS' || that.syncStatus.status === 'SYNCING')) {
                        that.loadSyncStatus();
                    }
                }, 10000);
            },

            stopAutoRefresh: function() {
                if (this.autoRefreshInterval) {
                    clearInterval(this.autoRefreshInterval);
                }
            },

            loadSyncStatus: function() {
                var that = this;
                MinIOUtils.getSyncStatus()
                    .done(function(data) {
                        that.syncStatus = data;
                        that.updateSyncStatusUI(data);
                    })
                    .fail(function(xhr, status, error) {
                        that.updateSyncStatusUI({ status: 'ERROR', errorMessage: error });
                    });
            },

            updateSyncStatusUI: function(data) {
                var status = data.status || 'UNKNOWN';
                var statusIcon = MinIOUtils.getStatusIcon(status);
                var statusText = status.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, function(l) {
                    return l.toUpperCase();
                });

                this.ui.syncStatusIcon
                    .removeClass()
                    .addClass('fa ' + statusIcon);

                this.ui.syncStatusText.text(statusText);

                // Progress
                var progress = data.progress || 0;
                this.ui.syncProgressBar.css('width', progress + '%');
                this.ui.syncProgressText.text(progress + '%');

                // Counts
                this.ui.syncProcessedCount.text((data.processedObjects || 0).toLocaleString());
                this.ui.syncFailedCount.text((data.failedObjects || 0).toLocaleString());

                // Duration
                if (data.duration) {
                    var duration = Math.floor(data.duration / 1000);
                    var minutes = Math.floor(duration / 60);
                    var seconds = duration % 60;
                    this.ui.syncDuration.text(minutes + 'm ' + seconds + 's');
                } else {
                    this.ui.syncDuration.text('-');
                }

                // Last sync time
                if (data.lastSyncTime) {
                    this.ui.syncLastSyncTime.text(MinIOUtils.formatDate(data.lastSyncTime));
                } else {
                    this.ui.syncLastSyncTime.text('Never');
                }

                // Enable/disable buttons
                var isSyncing = status === 'IN_PROGRESS' || status === 'SYNCING';
                this.ui.syncNowBtn.prop('disabled', isSyncing);
                this.ui.fullSyncBtn.prop('disabled', isSyncing);
            },

            loadSyncHistory: function() {
                var that = this;
                var fromDate = new Date();
                fromDate.setDate(fromDate.getDate() - this.historyDays);

                this.syncEventCollection.fetch({
                    data: {
                        fromDate: fromDate.toISOString(),
                        limit: 100
                    },
                    success: function(collection, response) {
                        that.renderSyncHistoryTable();
                    },
                    error: function(model, response) {
                        Utils.showError(response);
                    }
                });
            },

            renderSyncHistoryTable: function() {
                var columns = [
                    {
                        name: 'timestamp',
                        label: 'Time',
                        cell: 'string',
                        editable: false,
                        formatter: _.extend({}, Backgrid.CellFormatter.prototype, {
                            fromRaw: function(rawValue, model) {
                                return MinIOUtils.formatDate(rawValue);
                            }
                        })
                    },
                    {
                        name: 'eventType',
                        label: 'Type',
                        cell: 'string',
                        editable: false
                    },
                    {
                        name: 'status',
                        label: 'Status',
                        cell: 'string',
                        editable: false,
                        formatter: _.extend({}, Backgrid.CellFormatter.prototype, {
                            fromRaw: function(rawValue, model) {
                                var badgeClass = MinIOUtils.getStatusBadge(rawValue);
                                return '<span class="badge ' + badgeClass + '">' + rawValue + '</span>';
                            }
                        })
                    },
                    {
                        name: 'processedCount',
                        label: 'Processed',
                        cell: 'integer',
                        editable: false
                    },
                    {
                        name: 'failedCount',
                        label: 'Failed',
                        cell: 'integer',
                        editable: false
                    },
                    {
                        name: 'duration',
                        label: 'Duration',
                        cell: 'string',
                        editable: false,
                        formatter: _.extend({}, Backgrid.CellFormatter.prototype, {
                            fromRaw: function(rawValue, model) {
                                if (!rawValue) return '-';
                                var duration = Math.floor(rawValue / 1000);
                                var minutes = Math.floor(duration / 60);
                                var seconds = duration % 60;
                                return minutes + 'm ' + seconds + 's';
                            }
                        })
                    },
                    {
                        name: 'errorMessage',
                        label: 'Error',
                        cell: 'string',
                        editable: false,
                        formatter: _.extend({}, Backgrid.CellFormatter.prototype, {
                            fromRaw: function(rawValue, model) {
                                if (!rawValue) return '-';
                                return '<span title="' + _.escape(rawValue) + '" class="text-danger">' +
                                       (rawValue.length > 50 ? rawValue.substring(0, 50) + '...' : rawValue) +
                                       '</span>';
                            }
                        })
                    }
                ];

                this.commonTableOptions = {
                    collection: this.syncEventCollection,
                    includeFilter: false,
                    includePagination: true,
                    includePageSize: true,
                    includeTableLoader: true,
                    includeFooterRecords: true,
                    gridOpts: {
                        className: 'table table-hover backgrid table-quickMenu',
                        emptyText: 'No sync history found!'
                    },
                    columns: columns,
                    sortOpts: {
                        sortColumn: 'timestamp',
                        sortDirection: 'descending'
                    }
                };

                this.rSyncHistoryTable.show(new Backgrid.Table(this.commonTableOptions));
            },

            onSyncNow: function() {
                var that = this;
                if (this.syncStatus && (this.syncStatus.status === 'IN_PROGRESS' || this.syncStatus.status === 'SYNCING')) {
                    Utils.showError('A sync is already in progress');
                    return;
                }

                this.ui.syncNowBtn.prop('disabled', true).text('Syncing...');

                MinIOUtils.triggerSync('incremental')
                    .done(function(data) {
                        Utils.showSuccess('Incremental sync started successfully');
                        that.loadSyncStatus();
                        setTimeout(function() {
                            that.loadSyncHistory();
                        }, 2000);
                    })
                    .fail(function(xhr, status, error) {
                        var errorMsg = xhr.responseJSON && xhr.responseJSON.error || error || 'Sync failed';
                        Utils.showError(errorMsg);
                    })
                    .always(function() {
                        that.ui.syncNowBtn.prop('disabled', false).text('<i class="fa fa-refresh"></i> Sync Now');
                    });
            },

            onFullSync: function() {
                var that = this;
                if (this.syncStatus && (this.syncStatus.status === 'IN_PROGRESS' || this.syncStatus.status === 'SYNCING')) {
                    Utils.showError('A sync is already in progress');
                    return;
                }

                if (!confirm('Are you sure you want to start a full sync? This may take a long time.')) {
                    return;
                }

                this.ui.fullSyncBtn.prop('disabled', true).text('Syncing...');

                MinIOUtils.triggerSync('full')
                    .done(function(data) {
                        Utils.showSuccess('Full sync started successfully');
                        that.loadSyncStatus();
                        setTimeout(function() {
                            that.loadSyncHistory();
                        }, 2000);
                    })
                    .fail(function(xhr, status, error) {
                        var errorMsg = xhr.responseJSON && xhr.responseJSON.error || error || 'Sync failed';
                        Utils.showError(errorMsg);
                    })
                    .always(function() {
                        that.ui.fullSyncBtn.prop('disabled', false).text('<i class="fa fa-database"></i> Full Sync');
                    });
            },

            onRefresh: function() {
                this.loadSyncStatus();
                this.loadSyncHistory();
            },

            onHistoryDaysChange: function(e) {
                this.historyDays = parseInt($(e.currentTarget).val());
                this.loadSyncHistory();
            }
        });

    return SyncMonitorView;
});
