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
 * VMinio
 * MinIO related models
 */
define(['require', 'models/VCommon', 'utils/UrlLinks'], function(require, VCommon, UrlLinks) {
    'use strict';

    var VMinioBucket = VCommon.extend({
        urlRoot: UrlLinks.minioBuckets(),
        defaults: {
            name: '',
            creationDate: '',
            location: '',
            owner: '',
            quota: 0,
            objectCount: 0,
            totalSize: 0,
            attributes: {}
        },
        getName: function() {
            return this.get('name');
        }
    });

    var VMinioObject = VCommon.extend({
        defaults: {
            bucketName: '',
            path: '',
            size: 0,
            contentType: '',
            etag: '',
            lastModified: '',
            storageClass: '',
            versionId: '',
            userMetadata: {},
            acl: {},
            attributes: {}
        },
        getName: function() {
            return this.get('path');
        }
    });

    var VMinioSyncEvent = VCommon.extend({
        urlRoot: UrlLinks.minioSyncHistory(),
        defaults: {
            eventType: '',
            timestamp: '',
            bucketName: '',
            objectPath: '',
            status: '',
            errorMessage: '',
            processedCount: 0,
            failedCount: 0,
            duration: 0
        }
    });

    var VMinioSyncStatus = VCommon.extend({
        urlRoot: UrlLinks.minioSyncStatus(),
        defaults: {
            status: '',
            mode: '',
            progress: 0,
            totalObjects: 0,
            processedObjects: 0,
            failedObjects: 0,
            startTime: '',
            endTime: '',
            lastSyncTime: ''
        }
    });

    return {
        VMinioBucket: VMinioBucket,
        VMinioObject: VMinioObject,
        VMinioSyncEvent: VMinioSyncEvent,
        VMinioSyncStatus: VMinioSyncStatus
    };
});
