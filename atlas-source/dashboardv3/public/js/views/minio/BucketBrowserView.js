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
    'hbs!tmpl/minio/BucketBrowserView_tmpl',
    'collection/VMinioList',
    'utils/MinIOUtils',
    'utils/Utils',
    'utils/Globals'
], function(require, Backbone, Marionette, BucketBrowserView_tmpl, VMinioList, MinIOUtils, Utils, Globals) {
    'use strict';

    var BucketBrowserView = Backbone.Marionette.LayoutView.extend(
        /** @lends BucketBrowserView */
        {
            _viewName: 'BucketBrowserView',

            template: BucketBrowserView_tmpl,

            /** Layout sub regions */
            regions: {
                rBucketsTable: "#r_bucketsTable"
            },

            /** ui selector cache */
            ui: {
                refreshBtn: '.refresh-btn',
                searchInput: '.search-input'
            },

            /** ui events hash */
            events: function() {
                var events = {};
                events['click ' + this.ui.refreshBtn] = 'onRefresh';
                events['keyup ' + this.ui.searchInput] = 'onSearch';
                return events;
            },

            /**
             * Initialize a new BucketBrowserView Layout
             * @constructs
             */
            initialize: function(options) {
                _.extend(this, _.pick(options, 'collection'));
                this.bucketCollection = new VMinioList.VMinioBucketList();
                this.filteredCollection = this.bucketCollection;
            },

            onRender: function() {
                this.loadBuckets();
            },

            loadBuckets: function() {
                var that = this;
                this.bucketCollection.fetch({
                    success: function(collection, response) {
                        that.filteredCollection = collection;
                        that.renderBucketsTable();
                    },
                    error: function(model, response) {
                        Utils.showError(response);
                        that.renderBucketsTable();
                    }
                });
            },

            renderBucketsTable: function() {
                var columns = [
                    {
                        name: 'name',
                        label: 'Bucket Name',
                        cell: 'string',
                        editable: false
                    },
                    {
                        name: 'owner',
                        label: 'Owner',
                        cell: 'string',
                        editable: false
                    },
                    {
                        name: 'creationDate',
                        label: 'Created',
                        cell: 'string',
                        editable: false,
                        formatter: _.extend({}, Backgrid.CellFormatter.prototype, {
                            fromRaw: function(rawValue, model) {
                                return MinIOUtils.formatDate(rawValue);
                            }
                        })
                    },
                    {
                        name: 'objectCount',
                        label: 'Objects',
                        cell: 'integer',
                        editable: false
                    },
                    {
                        name: 'totalSize',
                        label: 'Size',
                        cell: 'string',
                        editable: false,
                        formatter: _.extend({}, Backgrid.CellFormatter.prototype, {
                            fromRaw: function(rawValue, model) {
                                return MinIOUtils.formatSize(rawValue);
                            }
                        })
                    },
                    {
                        name: 'actions',
                        label: 'Actions',
                        cell: Backgrid.ActionCell.extend({
                            className: 'action-cell',
                            actions: [
                                {
                                    name: 'viewObjects',
                                    icon: 'fa-files-o',
                                    title: 'View Objects',
                                    callback: function(e, model) {
                                        var bucketName = model.get('name');
                                        Utils.setUrl({
                                            url: '#!/minio/objects?bucket=' + encodeURIComponent(bucketName),
                                            mergeBrowserUrl: false
                                        });
                                    }
                                },
                                {
                                    name: 'viewDetails',
                                    icon: 'fa-info-circle',
                                    title: 'View Details',
                                    callback: function(e, model) {
                                        var bucketName = model.get('name');
                                        that.showBucketDetails(model);
                                    }
                                }
                            ]
                        })
                    }
                ];

                this.commonTableOptions = {
                    collection: this.filteredCollection,
                    includeFilter: false,
                    includePagination: true,
                    includePageSize: true,
                    includeTableLoader: true,
                    includeFooterRecords: true,
                    gridOpts: {
                        className: 'table table-hover backgrid table-quickMenu',
                        emptyText: 'No buckets found!'
                    },
                    columns: columns
                };

                this.rBucketsTable.show(new Backgrid.Table(this.commonTableOptions));
            },

            onSearch: function(e) {
                var searchTerm = $(e.currentTarget).val().toLowerCase();
                if (searchTerm === '') {
                    this.filteredCollection = this.bucketCollection;
                } else {
                    this.filteredCollection = new VMinioList.VMinioBucketList(
                        this.bucketCollection.filter(function(bucket) {
                            var name = bucket.get('name').toLowerCase();
                            var owner = (bucket.get('owner') || '').toLowerCase();
                            return name.indexOf(searchTerm) !== -1 || owner.indexOf(searchTerm) !== -1;
                        })
                    );
                }
                this.renderBucketsTable();
            },

            onRefresh: function() {
                this.loadBuckets();
            },

            showBucketDetails: function(bucketModel) {
                var that = this;
                MinIOUtils.getBucketDetails(bucketModel.get('name'))
                    .done(function(data) {
                        that.renderBucketDetailsModal(data);
                    })
                    .fail(function(xhr, status, error) {
                        Utils.showError('Failed to load bucket details: ' + error);
                    });
            },

            renderBucketDetailsModal: function(bucketData) {
                var modalHtml = '<div class="modal fade" id="bucketDetailsModal" tabindex="-1" role="dialog">' +
                    '<div class="modal-dialog modal-lg" role="document">' +
                    '<div class="modal-content">' +
                    '<div class="modal-header">' +
                    '<button type="button" class="close" data-dismiss="modal" aria-label="Close">' +
                    '<span aria-hidden="true">&times;</span></button>' +
                    '<h4 class="modal-title">Bucket Details: ' + bucketData.name + '</h4>' +
                    '</div>' +
                    '<div class="modal-body">' +
                    '<dl class="dl-horizontal">' +
                    '<dt>Name:</dt><dd>' + bucketData.name + '</dd>' +
                    '<dt>Owner:</dt><dd>' + (bucketData.owner || 'N/A') + '</dd>' +
                    '<dt>Location:</dt><dd>' + (bucketData.location || 'default') + '</dd>' +
                    '<dt>Created:</dt><dd>' + MinIOUtils.formatDate(bucketData.creationDate) + '</dd>' +
                    '<dt>Object Count:</dt><dd>' + (bucketData.objectCount || 0).toLocaleString() + '</dd>' +
                    '<dt>Total Size:</dt><dd>' + MinIOUtils.formatSize(bucketData.totalSize || 0) + '</dd>' +
                    '</dl>';

                if (bucketData.attributes && Object.keys(bucketData.attributes).length > 0) {
                    modalHtml += '<h5>Attributes</h5><dl class="dl-horizontal">';
                    _.each(bucketData.attributes, function(value, key) {
                        modalHtml += '<dt>' + key + ':</dt><dd>' + value + '</dd>';
                    });
                    modalHtml += '</dl>';
                }

                modalHtml += '</div>' +
                    '<div class="modal-footer">' +
                    '<button type="button" class="btn btn-default" data-dismiss="modal">Close</button>' +
                    '<button type="button" class="btn btn-primary view-objects-btn">View Objects</button>' +
                    '</div>' +
                    '</div>' +
                    '</div>' +
                    '</div>';

                var $modal = $(modalHtml);
                $('body').append($modal);

                $modal.find('.view-objects-btn').on('click', function() {
                    $modal.modal('hide');
                    Utils.setUrl({
                        url: '#!/minio/objects?bucket=' + encodeURIComponent(bucketData.name),
                        mergeBrowserUrl: false
                    });
                });

                $modal.on('hidden.bs.modal', function() {
                    $(this).remove();
                });

                $modal.modal('show');
            }
        });

    return BucketBrowserView;
});
