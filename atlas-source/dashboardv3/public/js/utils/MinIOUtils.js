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

/**
 * MinIO Utils
 * Utility functions for MinIO integration
 */
define(['require', 'utils/UrlLinks'], function(require, UrlLinks) {
    'use strict';

    var MinIOUtils = {
        /**
         * Test MinIO connection
         */
        testConnection: function() {
            return $.ajax({
                url: UrlLinks.minioTest(),
                method: 'GET',
                dataType: 'json',
                contentType: 'application/json'
            });
        },

        /**
         * Get all buckets
         */
        getBuckets: function() {
            return $.ajax({
                url: UrlLinks.minioBuckets(),
                method: 'GET',
                dataType: 'json',
                contentType: 'application/json'
            });
        },

        /**
         * Get bucket details
         */
        getBucketDetails: function(bucketName) {
            return $.ajax({
                url: UrlLinks.minioBucket(bucketName),
                method: 'GET',
                dataType: 'json',
                contentType: 'application/json'
            });
        },

        /**
         * Get objects in a bucket
         */
        getObjects: function(bucketName, prefix, limit, offset) {
            var params = {};
            if (prefix) params.prefix = prefix;
            if (limit) params.limit = limit;
            if (offset) params.offset = offset;

            return $.ajax({
                url: UrlLinks.minioObjects(),
                method: 'GET',
                data: params,
                dataType: 'json',
                contentType: 'application/json'
            });
        },

        /**
         * Get object details
         */
        getObjectDetails: function(objectId) {
            return $.ajax({
                url: UrlLinks.minioObject(objectId),
                method: 'GET',
                dataType: 'json',
                contentType: 'application/json'
            });
        },

        /**
         * Trigger sync (full or incremental)
         */
        triggerSync: function(mode) {
            return $.ajax({
                url: UrlLinks.minioSync(),
                method: 'POST',
                data: { mode: mode || 'full' },
                dataType: 'json',
                contentType: 'application/json'
            });
        },

        /**
         * Get sync status
         */
        getSyncStatus: function() {
            return $.ajax({
                url: UrlLinks.minioSyncStatus(),
                method: 'GET',
                dataType: 'json',
                contentType: 'application/json'
            });
        },

        /**
         * Get sync history
         */
        getSyncHistory: function(fromDate, toDate, limit) {
            var params = {};
            if (fromDate) params.fromDate = fromDate;
            if (toDate) params.toDate = toDate;
            if (limit) params.limit = limit;

            return $.ajax({
                url: UrlLinks.minioSyncHistory(),
                method: 'GET',
                data: params,
                dataType: 'json',
                contentType: 'application/json'
            });
        },

        /**
         * Classify entity
         */
        classifyEntity: function(entityId, classifications) {
            return $.ajax({
                url: UrlLinks.minioClassify(),
                method: 'POST',
                data: JSON.stringify({
                    entityId: entityId,
                    classifications: classifications
                }),
                dataType: 'json',
                contentType: 'application/json'
            });
        },

        /**
         * Format file size
         */
        formatSize: function(bytes) {
            if (bytes === 0) return '0 B';
            var k = 1024;
            var sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
            var i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
        },

        /**
         * Format date
         */
        formatDate: function(dateString) {
            if (!dateString) return '-';
            var date = new Date(dateString);
            return date.toLocaleString();
        },

        /**
         * Get status icon class
         */
        getStatusIcon: function(status) {
            switch(status) {
                case 'SUCCESS':
                case 'ONLINE':
                    return 'fa-check-circle text-success';
                case 'FAILED':
                case 'OFFLINE':
                    return 'fa-times-circle text-danger';
                case 'IN_PROGRESS':
                case 'SYNCING':
                    return 'fa-spinner fa-spin text-warning';
                case 'WARNING':
                    return 'fa-exclamation-triangle text-warning';
                default:
                    return 'fa-question-circle text-muted';
            }
        },

        /**
         * Get status badge class
         */
        getStatusBadge: function(status) {
            switch(status) {
                case 'SUCCESS':
                case 'ONLINE':
                    return 'badge-success';
                case 'FAILED':
                case 'OFFLINE':
                    return 'badge-danger';
                case 'IN_PROGRESS':
                case 'SYNCING':
                    return 'badge-warning';
                case 'WARNING':
                    return 'badge-warning';
                default:
                    return 'badge-default';
            }
        }
    };

    return MinIOUtils;
});
