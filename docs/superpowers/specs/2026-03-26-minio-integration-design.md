# Apache Atlas with MinIO Integration Design

**Date:** 2026-03-26
**Author:** Generated with Claude Code
**Status:** Approved

## 1. Project Overview

### 1.1 Purpose
Build an enhanced Apache Atlas with integrated MinIO metadata management capabilities for data governance and metadata management.

### 1.2 Key Requirements
- **Primary Goal:** Data governance and metadata management
- **Integration:** Native integration within Apache Atlas project (not separate application)
- **Deployment:** Docker Compose with full Atlas stack (Atlas, HBase, ZooKeeper, Solr)
- **Metadata:** Complete metadata extraction (basic + extended + custom tags, user metadata, ACL, version info)
- **Authentication:** Static credentials (Access Key + Secret Key)
- **User Interface:** Extended Atlas Web UI with MinIO management interface
- **Technology Stack:** Spring Boot + Vue.js (consistent with Atlas)
- **Classification:** Manual classification primary, with intelligent auto-classification
- **Synchronization:** Event-driven + daily early morning scheduled sync as fallback
- **Monitoring:** Complete monitoring panel via Web UI (sync status, success rate, failures)

## 2. System Architecture

### 2.1 Overall Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Docker Compose Environment                │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Enhanced Apache Atlas                   │   │
│  │  ┌──────────────────────────────────────────────┐   │   │
│  │  │            Atlas Web UI (Angular)            │   │   │
│  │  │  ┌────────────┐      ┌──────────────┐        │   │   │
│  │  │  │ Native UI  │  +   │ MinIO Panels  │        │   │   │
│  │  │  └────────────┘      └──────────────┘        │   │   │
│  │  └──────────────────────────────────────────────┘   │   │
│  │                       ↕ REST API                     │   │
│  │  ┌──────────────────────────────────────────────┐   │   │
│  │  │          Atlas REST API (Jersey)              │   │   │
│  │  │  ┌──────────────┐    ┌──────────────┐        │   │   │
│  │  │  │  Native API  │  + │ MinIO API    │        │   │   │
│  │  │  └──────────────┘    └──────────────┘        │   │   │
│  │  └──────────────────────────────────────────────┘   │   │
│  │                       ↕                               │   │
│  │  ┌──────────────────────────────────────────────┐   │   │
│  │  │              Atlas Core Layer                 │   │   │
│  │  │  ┌──────────────────┐  ┌─────────────────┐   │   │   │
│  │  │  │   Type System    │  │  Graph Engine   │   │   │   │
│  │  │  └──────────────────┘  └─────────────────┘   │   │   │
│  │  │  ┌──────────────────┐  ┌─────────────────┐   │   │   │
│  │  │  │   Metadata Store │  │  Search Engine  │   │   │   │
│  │  │  └──────────────────┘  └─────────────────┘   │   │   │
│  │  └──────────────────────────────────────────────┘   │   │
│  │                       ↕                               │   │
│  │  ┌──────────────────────────────────────────────┐   │   │
│  │  │          MinIO Integration Module             │   │   │
│  │  │  ┌──────────────┐  ┌──────────────────┐      │   │   │
│  │  │  │MinIO Bridge  │  │ Event Processor  │      │   │   │
│  │  │  └──────────────┘  └──────────────────┘      │   │   │
│  │  │  ┌──────────────┐  ┌──────────────────┐      │   │   │
│  │  │  │Hook System   │  │ Classification   │      │   │   │
│  │  │  └──────────────┘  └──────────────────┘      │   │   │
│  │  └──────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────┘   │
│                          ↕ S3 Protocol                       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Remote MinIO Instance                   │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Deployment Architecture

```
Docker Compose Stack:
├── atlas-app (Enhanced Apache Atlas)
│   ├── Web UI (Angular + MinIO Extensions)
│   ├── REST API (Jersey + MinIO Endpoints)
│   ├── Core Atlas (Graph, Metadata, Search)
│   └── MinIO Integration Module
├── hbase (Metadata storage)
├── zookeeper (Coordination)
└── solr (Search index)
```

## 3. Core Components

### 3.1 MinIO Integration Module

**Package:** `org.apache.atlas.minio`

#### 3.1.1 MinIO Bridge
```java
org.apache.atlas.minio.bridge
├── MinIOBridge.java              // Main bridge entry point
├── MinIOClient.java              // MinIO S3 client wrapper
├── MetadataExtractor.java        // Metadata extractor
└── SyncScheduler.java            // Sync scheduler
```

**Responsibilities:**
- Initialize and manage MinIO S3 client connection
- Execute full and incremental metadata scans
- Extract complete MinIO metadata (bucket, object, ACL, versions, etc.)
- Schedule scheduled sync tasks (daily early morning)

#### 3.1.2 Event Processor
```java
org.apache.atlas.minio.event
├── MinIOEventNotifier.java       // Event notification listener
├── MinIOEventHandler.java        // Event handling logic
└── EventQueue.java               // Event queue (async processing)
```

**Responsibilities:**
- Listen to MinIO event notifications
- Asynchronously process object creation, deletion, update events
- Real-time update of metadata in Atlas

#### 3.1.3 Classification Engine
```java
org.apache.atlas.minio.classification
├── ClassificationService.java    // Classification service
├── ManualClassification.java     // Manual classification
├── AutoClassifier.java           // Intelligent auto-classification
└── ClassificationRules.java      // Classification rules engine
```

**Responsibilities:**
- Provide manual classification functionality
- Auto-classification based on file path, type, naming rules
- Manage classification tags and rules

### 3.2 Atlas REST API Extensions

**Package:** `org.apache.atlas.web.resources`

Extend existing Atlas REST API with MinIO-specific endpoints:

```java
MinioResource.java                // New REST resource
```

**New API Endpoints:**
```
POST   /api/atlas/minio/sync              // Manual trigger sync
GET    /api/atlas/minio/buckets           // Get all buckets
GET    /api/atlas/minio/buckets/{id}      // Get bucket details
GET    /api/atlas/minio/objects           // Get object list
GET    /api/atlas/minio/objects/{id}      // Get object details
POST   /api/atlas/minio/classify          // Manual classification
GET    /api/atlas/minio/sync/status       // Get sync status
```

### 3.3 Atlas Web UI Extensions

Extend existing Angular UI with MinIO management interface:

```
web/app/modules/minio/
├── components/
│   ├── minio-dashboard/           // MinIO Dashboard
│   ├── bucket-list/               // Bucket List
│   ├── object-browser/            // Object Browser
│   ├── classification-manager/    // Classification Manager
│   └── sync-monitor/              // Sync Monitor Panel
├── services/
│   ├── minio.service.ts           // MinIO API service
│   └── classification.service.ts  // Classification service
└── models/
    └── minio.models.ts            // MinIO data models
```

**UI Components Features:**
- **Dashboard:** MinIO connection status, bucket statistics, sync status
- **Bucket Management:** Browse all buckets, view details and classifications
- **Object Browser:** Tree view of file structure, view complete metadata
- **Classification Manager:** Manual tagging, manage classification rules
- **Sync Monitor:** View sync history, success rate, failure reasons

### 3.4 Atlas Type System Extensions

Define MinIO-specific type system:

```java
// Atlas Type Definitions
MinioBucket {
    name: string
    creationDate: date
    location: string
    owner: string
    quota: long
    classification: []
}

MinioObject {
    bucketName: string
    path: string
    size: long
    contentType: string
    etag: string
    lastModified: date
    storageClass: string
    versionId: string
    userMetadata: map<string, string>
    acl: []
    classification: []
}

MinioSyncEvent {
    eventType: string
    timestamp: date
    bucketName: string
    objectPath: string
    status: string
    errorMessage: string
}
```

## 4. Data Flow and Interactions

### 4.1 Initialization and First Full Sync

```
User Actions:
1. Configure MinIO connection via Web UI
   └── Endpoint, Access Key, Secret Key

2. Start Enhanced Atlas (Docker Compose)
   └→ Atlas startup → MinIO Module initialization

3. Click "First Full Scan" in Web UI
   └→ REST API: POST /api/atlas/minio/sync?mode=full

System Processing:
4. MinIOBridge.connect()
   └→ Verify connection, get bucket list

5. Iterate through each bucket:
   ├─ Extract bucket metadata
   ├─ Create Atlas MinioBucket entity
   └─ Iterate through all objects in bucket:
       ├─ Extract complete object metadata
       ├─ Apply auto-classification rules
       └─ Create Atlas MinioObject entity

6. Update sync status:
   └→ Record to MinioSyncEvent entity

7. Web UI displays progress and results:
   └→ Real-time progress bar, statistics after completion
```

### 4.2 Event-Driven Real-time Sync

```
MinIO Event Trigger:
1. User operates on MinIO (upload/delete/update object)
   └→ MinIO generates event notification

2. MinIO Event Notifier (configured on MinIO side):
   └→ Send event to Atlas Webhook endpoint
   └→ REST API: POST /api/atlas/minio/events

Atlas Processing:
3. MinioEventHandler receives event:
   └→ Parse event type and metadata

4. Put into async event queue:
   └→ Avoid blocking, handle burst traffic

5. Event processor handles asynchronously:
   ├─ Create/update/delete corresponding Atlas entity
   ├─ Re-apply classification rules
   └→ Record sync event

6. Web UI real-time update:
   └→ Polling or WebSocket to show changes
```

### 4.3 Scheduled Sync Fallback

```
Scheduler Trigger:
1. SyncScheduler (daily 2:00 AM):
   └→ Quartz Scheduler scheduled task

2. Execute incremental sync:
   ├─ Get last sync timestamp
   ├─ Query changed objects in MinIO
   └─ Only process changed objects

3. Or execute full validation:
   ├─ Compare metadata between Atlas and MinIO
   ├─ Fix inconsistencies
   └─ Clean up deleted objects in Atlas

4. Generate sync report:
   ├─ Success/failure count
   ├─ Processing time
   └→ Error details

5. Web UI displays report:
   └→ Viewable in "Sync Monitor" panel
```

### 4.4 Manual Classification Flow

```
User Actions (Web UI):
1. Browse to specific object or bucket
   └→ Object Browser component

2. Click "Classify" button
   └→ Open classification dialog

3. Select or create classification tags:
   ├─ Select from existing tags
   ├─ Create new tags
   └─ Add custom attributes

4. Save classification:
   └→ REST API: POST /api/atlas/minio/classify

Backend Processing:
5. ClassificationService:
   ├─ Validate tag validity
   ├─ Update classification property of Atlas entity
   └→ Trigger classification rule re-evaluation

6. Return success response:
   └→ Web UI updates display
```

### 4.5 Intelligent Auto-Classification Flow

```
Trigger Times:
1. When object is first imported
2. When object metadata is updated
3. When classification rules are updated

Classification Engine:
2. AutoClassifier executes:
   ├─ Path matching rules:
   │   └→ /data/production/* → "production_data"
   │   └→ /tmp/* → "temporary_files"
   │   └→ *.backup → "backup"
   ├─ File type rules:
   │   └→ application/pdf → "document"
   │   └→ image/* → "image"
   ├─ User metadata rules:
   │   └→ x-amz-meta-sensitive=true → "sensitive_data"
   └─ Custom rule engine (user configurable)

3. Apply classification results:
   └→ Auto-add tags to Atlas entity

4. User can override:
   └→ Manual classification priority higher than auto-classification
```

## 5. Error Handling, Monitoring, and Configuration

### 5.1 Error Handling Strategies

#### 5.1.1 Connection Error Handling
```
MinIO Connection Failure:
├─ Retry Mechanism:
│   ├─ 1st retry: Immediate
│   ├─ 2nd retry: After 5 seconds
│   └─ 3rd retry: After 30 seconds
├─ Timeout Settings:
│   ├─ Connection timeout: 10 seconds
│   └─ Read timeout: 60 seconds
└─ Failure Handling:
    ├─ Log error
    ├─ Update connection status to "offline"
    └─ Web UI displays error alert
```

#### 5.1.2 Sync Error Handling
```
Failure During Sync:
├─ Single Object Failure:
│   ├─ Skip the object, continue processing others
│   ├─ Record failed object in error list
│   └→ Generate error report at the end
├─ Batch Failure:
│   ├─ Pause sync task
│   ├─ Save progress (processed objects)
│   └─ Allow resume from breakpoint
└─ Error Classification:
    ├─ Network errors → Retryable
    ├─ Permission errors → User intervention required
    ├─ Data format errors → Skip and log
    └→ Atlas internal errors → Log and alert
```

#### 5.1.3 Event Processing Errors
```
Event Processing Failure:
├─ Event Queue Full:
│   ├─ Bounded queue design, limit size
│   ├─ Drop oldest events when queue is full
│   └─ Log dropped events
├─ Processor Exception:
│   ├─ Catch exception, avoid thread crash
│   ├─ Record to dead letter queue
│   └→ Scheduled retry of dead letter queue events
└─ Idempotency Guarantee:
    └→ Same event processed multiple times with same result
```

### 5.2 Monitoring Panel Design (Web UI)

#### 5.2.1 MinIO Dashboard
```
┌─────────────────────────────────────────────────────┐
│  MinIO Connection Status                             │
│  ├─ Status: 🟢 Online / 🔴 Offline / 🟡 Unstable     │
│  ├─ Endpoint: https://minio.example.com             │
│  ├─ Last Check: 2026-03-26 10:30:00                 │
│  └─ Response Time: 45ms                              │
├─────────────────────────────────────────────────────┤
│  Bucket Statistics                                   │
│  ├─ Total: 15                                       │
│  ├─ Total Size: 2.3 TB                              │
│  ├─ Total Objects: 1,234,567                        │
│  └─ Storage Trend (Last 7 Days)                     │
├─────────────────────────────────────────────────────┤
│  Classification Statistics                          │
│  ├─ Production Data: 45%                            │
│  ├─ Test Data: 30%                                  │
│  ├─ Backup Data: 15%                                │
│  └─ Unclassified: 10%                               │
└─────────────────────────────────────────────────────┘
```

#### 5.2.2 Sync Monitor Panel
```
┌─────────────────────────────────────────────────────┐
│  Current Sync Status                                │
│  ├─ Status: 🔄 Syncing / ✅ Complete / ⚠️ Failed    │
│  ├─ Mode: Full Scan / Incremental Sync              │
│  ├─ Progress: 1,234 / 5,000 Objects (24.7%)        │
│  ├─ Elapsed: 00:15:32                               │
│  ├─ Estimated Remaining: 00:47:18                   │
│  └─ Progress Bar: [████░░░░░░░░░░░░░░░]            │
├─────────────────────────────────────────────────────┤
│  Recent Sync Records                                │
│  ├─ 2026-03-26 02:00  ✅ Success  5,234 objs  15min │
│  ├─ 2026-03-25 02:00  ✅ Success  5,189 objs  14min │
│  ├─ 2026-03-24 02:00  ⚠️ Partial  5,100/5,200     │
│  └─ View Details →                                  │
├─────────────────────────────────────────────────────┤
│  Event Processing Stats (Real-time)                 │
│  ├─ Today Received: 1,234 Events                   │
│  ├─ Today Processed: 1,230 Events                  │
│  ├─ Success Rate: 99.7%                             │
│  ├─ Avg Latency: 230ms                              │
│  └─ Queue Backlog: 4 Events                         │
└─────────────────────────────────────────────────────┘
```

#### 5.2.3 Error Log Panel
```
┌─────────────────────────────────────────────────────┐
│  Error Overview                                     │
│  ├─ Today's Errors: 23                             │
│  ├─ Error Rate: 0.02%                              │
│  └─ Critical Errors: 2                             │
├─────────────────────────────────────────────────────┤
│  Error List                                         │
│  ├─ [10:30] 🔴 Connection Timeout: bucket-123      │
│  │   └→ Retrying... (2/3)                          │
│  ├─ [10:15] 🟡 Permission Denied: bucket-secret/obj │
│  │   └→ Skipped, check access control             │
│  └─ [09:45] 🟢 Resolved: Event queue full         │
│       └→ Auto-recovered                            │
├─────────────────────────────────────────────────────┤
│  Export Logs / Clean History                        │
└─────────────────────────────────────────────────────┘
```

### 5.3 Configuration Management

#### 5.3.1 Atlas Configuration (application.properties)
```properties
# MinIO Connection Configuration
atlas.minio.enabled=true
atlas.minio.endpoint=https://minio.example.com
atlas.minio.access.key=${MINIO_ACCESS_KEY}
atlas.minio.secret.key=${MINIO_SECRET_KEY}
atlas.minio.region=us-east-1
atlas.minio.connection.timeout=10000
atlas.minio.socket.timeout=60000

# Sync Configuration
atlas.minio.sync.enabled=true
atlas.minio.sync.full.initial=true
atlas.minio.sync.schedule.cron=0 0 2 * * ?
atlas.minio.sync.incremental.enabled=true
atlas.minio.sync.batch.size=100
atlas.minio.sync.thread.pool.size=5

# Event Processing Configuration
atlas.minio.event.enabled=true
atlas.minio.event.queue.capacity=10000
atlas.minio.event.thread.pool.size=3
atlas.minio.event.retry.max=3
atlas.minio.event.retry.delay=5000

# Classification Configuration
atlas.minio.classification.auto.enabled=true
atlas.minio.classification.rules.path=/etc/atlas/minio-classification-rules.json

# Monitoring Configuration
atlas.minio.monitor.metrics.retention.days=30
atlas.minio.monitor.error.retention.days=90
```

#### 5.3.2 Environment Variables (Docker Compose)
```yaml
environment:
  - MINIO_ENDPOINT=https://minio.example.com
  - MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY}
  - MINIO_SECRET_KEY=${MINIO_SECRET_KEY}
  - ATLAS_MINIO_SYNC_SCHEDULE=0 0 2 * * ?
```

#### 5.3.3 Classification Rules Configuration (JSON)
```json
{
  "rules": [
    {
      "name": "Production Data Identification",
      "type": "path",
      "pattern": "/data/production/.*",
      "classification": "production_data",
      "attributes": {
        "environment": "production",
        "criticality": "high"
      }
    },
    {
      "name": "Sensitive Data Identification",
      "type": "metadata",
      "key": "x-amz-meta-sensitive",
      "value": "true",
      "classification": "sensitive_data",
      "attributes": {
        "data_sensitivity": "high",
        "access_control": "restricted"
      }
    },
    {
      "name": "Backup Data Identification",
      "type": "path",
      "pattern": ".*\\.backup$",
      "classification": "backup",
      "attributes": {
        "retention_days": 90
      }
    }
  ]
}
```

## 6. Project Structure

```
atlas-minio/
├── docker/                                # Docker Configuration
│   ├── docker-compose.yml                # Complete Atlas + MinIO environment
│   ├── Dockerfile.atlas                  # Enhanced Atlas image
│   └── config/                           # Configuration files
│       ├── application.properties        # Atlas configuration
│       ├── classification-rules.json     # Classification rules
│       └── log4j.xml                     # Logging configuration
│
├── atlas-source/                         # Apache Atlas source (cloned)
│   ├── addons/                           # Native Atlas plugins
│   ├── bridge/                           # Atlas Bridge
│   ├── common/                           # Common modules
│   ├── core/                             # Core modules
│   ├── server-api/                       # REST API
│   ├── webapp/                           # Web UI (Angular)
│   └── minio-integration/                # 🆕 New MinIO integration module
│       ├── src/main/java/
│       │   └── org/apache/atlas/minio/
│       │       ├── bridge/
│       │       │   ├── MinIOBridge.java
│       │       │   ├── MinIOClient.java
│       │       │   ├── MetadataExtractor.java
│       │       │   └── SyncScheduler.java
│       │       ├── event/
│       │       │   ├── MinIOEventNotifier.java
│       │       │   ├── MinIOEventHandler.java
│       │       │   └── EventQueue.java
│       │       ├── classification/
│       │       │   ├── ClassificationService.java
│       │       │   ├── ManualClassification.java
│       │       │   ├── AutoClassifier.java
│       │       │   └── ClassificationRules.java
│       │       ├── model/
│       │       │   ├── MinioBucket.java
│       │       │   ├── MinioObject.java
│       │       │   └── MinioSyncEvent.java
│       │       └── utils/
│       │           ├── MinIOConstants.java
│       │           └── MinIOUtils.java
│       └── src/main/resources/
│           ├── spring-minio.xml          # Spring configuration
│           └── minio-models.json         # Atlas type definitions
│
├── docs/                                 # Documentation
│   └── superpowers/
│       └── specs/
│           └── 2026-03-26-minio-integration-design.md
│
├── scripts/                              # Scripts
│   ├── setup.sh                          # Initialization script
│   ├── build.sh                          # Build script
│   └── deploy.sh                         # Deploy script
│
└── README.md                             # Project documentation
```

## 7. Technology Stack Summary

| Layer          | Technology              | Description                          |
|----------------|-------------------------|--------------------------------------|
| Storage        | Apache HBase            | Atlas metadata storage              |
| Coordination   | Apache ZooKeeper        | Distributed coordination             |
| Search         | Apache Solr             | Full-text search and indexing        |
| App Framework  | Spring Boot             | Java backend framework              |
| REST API       | Jersey (JAX-RS)         | RESTful API                          |
| Web UI         | Angular 1.x             | Frontend framework (Atlas native)    |
| S3 Client      | AWS SDK for Java        | MinIO S3 protocol communication      |
| Scheduler      | Quartz Scheduler        | Scheduled task scheduling           |
| Message Queue  | In-memory Queue (Java)  | Event queue (upgradeable to Kafka)   |
| Build Tool     | Maven                   | Java project build                  |
| Container      | Docker Compose          | Service orchestration                |
| Logging        | Log4j 2                 | Log management                      |

## 8. Key Technical Decisions

### 8.1 Why Develop Within Atlas Project?
- Direct access to Atlas internal APIs for optimal performance
- No additional inter-service communication needed
- Tighter integration with type system
- Easier maintenance and upgrades

### 8.2 Why Use Angular 1.x Instead of Modern Frameworks?
- Consistency with existing Atlas UI
- Reduced learning and maintenance costs
- Avoid multi-framework mixing issues

### 8.3 Why Use In-Memory Queue Instead of Kafka?
- Current phase is monolithic architecture, no need for distributed messaging
- Simplify deployment and operations
- Easy upgrade to Kafka if needed later

### 8.4 Why Choose Quartz Over Spring Scheduler?
- Quartz is more powerful (supports cron, persistence, clustering)
- Enterprise-grade scheduling standard
- Good integration with Spring

## 9. Implementation Phases

### Phase 1: Infrastructure Setup
- Docker Compose environment configuration
- Atlas source clone and build
- MinIO test environment deployment

### Phase 2: MinIO Integration Module Development
- MinIO client wrapper
- Metadata extractor
- Atlas type definition and registration

### Phase 3: REST API Development
- MinIO-specific API endpoints
- Sync control interfaces
- Classification management interfaces

### Phase 4: Web UI Development
- MinIO dashboard
- Bucket and object browser
- Classification management interface
- Sync monitoring panel

### Phase 5: Event Processing and Sync
- MinIO event listening
- Async event processor
- Scheduled sync

### Phase 6: Classification and Intelligence Engine
- Manual classification functionality
- Auto-classification rules engine
- Classification rule management

### Phase 7: Error Handling and Monitoring
- Error handling and retry mechanisms
- Monitoring panel development
- Logging and reporting

### Phase 8: Testing and Optimization
- Unit tests and integration tests
- Performance optimization
- Documentation improvement

## 10. Success Criteria

- [ ] Successfully connect to remote MinIO instance via S3 protocol
- [ ] Extract complete metadata (buckets, objects, ACLs, versions, user metadata)
- [ ] Import metadata into Apache Atlas with custom type definitions
- [ ] Web UI displays MinIO data with browsing and search capabilities
- [ ] Manual classification works with tag management
- [ ] Auto-classification applies rules based on path, type, and metadata
- [ ] Event-driven real-time sync captures MinIO changes
- [ ] Scheduled daily sync ensures data consistency
- [ ] Monitoring panel shows sync status, success rate, and error details
- [ ] Error handling includes retries, logging, and user notifications
- [ ] All components run in Docker Compose environment

## Appendix A: API Endpoint Reference

### MinIO Management APIs
```
POST   /api/atlas/minio/sync
       - Trigger manual sync (full or incremental)
       - Query params: mode=full|incremental
       - Response: SyncJob status

GET    /api/atlas/minio/buckets
       - Get all buckets
       - Response: List<MinioBucket>

GET    /api/atlas/minio/buckets/{id}
       - Get bucket details with objects
       - Response: MinioBucket with objects

GET    /api/atlas/minio/objects
       - Get object list with pagination
       - Query params: bucket, path, page, size
       - Response: PagedResult<MinioObject>

GET    /api/atlas/minio/objects/{id}
       - Get object details with full metadata
       - Response: MinioObject

POST   /api/atlas/minio/classify
       - Apply classification to entity
       - Body: entityId, classifications[]
       - Response: Success/Failure

GET    /api/atlas/minio/sync/status
       - Get current sync status
       - Response: SyncStatus (current, history, stats)

GET    /api/atlas/minio/sync/history
       - Get sync history
       - Query params: fromDate, toDate, limit
       - Response: List<MinioSyncEvent>

POST   /api/atlas/minio/events
       - Webhook for MinIO events
       - Body: MinIO event notification
       - Response: 202 Accepted
```

## Appendix B: Classification Rule Examples

### Path-Based Rules
```json
{
  "name": "PII Data Detection",
  "type": "path",
  "pattern": "/data/users/.*",
  "classification": "pii_data",
  "attributes": {
    "data_sensitivity": "high",
    "retention_policy": "7_years"
  }
}
```

### Metadata-Based Rules
```json
{
  "name": "Confidential Document",
  "type": "metadata",
  "key": "x-amz-meta-confidential",
  "value": "true",
  "classification": "confidential",
  "attributes": {
    "access_level": "restricted",
    "audit_required": true
  }
}
```

### Content-Type Based Rules
```json
{
  "name": "Financial Documents",
  "type": "contentType",
  "pattern": "application/vnd.ms-excel.*",
  "classification": "financial_report",
  "attributes": {
    "department": "finance",
    "requires_approval": true
  }
}
```

---

**Document Version:** 1.0
**Last Updated:** 2026-03-26
**Status:** Ready for Implementation Planning
