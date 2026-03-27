# MinIO Web UI - User Guide

## Overview

The MinIO Web UI provides a comprehensive interface for managing MinIO integration within Apache Atlas. This guide explains how to use the new MinIO UI components.

## Features

### 1. MinIO Dashboard
The main dashboard provides an at-a-glance view of your MinIO integration status.

**Access**: Click on your username → MinIO → Dashboard

**Features**:
- **Connection Status**: Shows whether Atlas is connected to MinIO
  - Green checkmark = Connected
  - Red X = Disconnected
  - Test Connection button to verify connectivity

- **Statistics Overview**:
  - Total Buckets count
  - Total Objects count
  - Total Storage used

- **Sync Status**:
  - Current sync status (Success, Failed, In Progress)
  - Last sync time
  - Sync Now button for manual sync

- **Quick Links**:
  - Browse Buckets
  - Browse Objects
  - Sync Monitor
  - Search MinIO

### 2. Bucket Browser
Browse and manage all MinIO buckets.

**Access**: Click on your username → MinIO → Buckets

**Features**:
- **Search**: Filter buckets by name or owner
- **Bucket List**: Table showing all buckets with:
  - Bucket Name
  - Owner
  - Creation Date
  - Number of Objects
  - Total Size
  - Actions (View Objects, View Details)

- **Actions**:
  - **View Objects**: Navigate to object browser for that bucket
  - **View Details**: Show detailed bucket information in a modal

### 3. Sync Monitor
Monitor and manage synchronization between MinIO and Atlas.

**Access**: Click on your username → MinIO → Sync Monitor

**Features**:
- **Current Sync Status**:
  - Status indicator (Success, Failed, In Progress)
  - Progress bar for active sync
  - Processed/Failed object counts
  - Sync duration
  - Last sync time

- **Sync Actions**:
  - **Sync Now**: Trigger incremental sync
  - **Full Sync**: Trigger full synchronization (with confirmation)

- **Sync History**:
  - Table showing recent sync events
  - Filter by time period (1, 7, or 30 days)
  - Columns: Time, Type, Status, Processed, Failed, Duration, Error

- **Schedule Information**:
  - Automatic sync status
  - Next scheduled sync time
  - Sync mode information

## Navigation

### Through the Menu
1. Click on your username in the top-right corner
2. Select "MinIO" from the dropdown
3. Choose the desired MinIO page

### Direct URLs
- Dashboard: `#!/minio/dashboard`
- Buckets: `#!/minio/buckets`
- Sync Monitor: `#!/minio/sync`

## Common Tasks

### Test MinIO Connection
1. Go to MinIO Dashboard
2. Click "Test Connection" button
3. Verify the status indicator turns green

### View Bucket Contents
1. Go to MinIO → Buckets
2. Find the bucket you want to explore
3. Click "View Objects" in the Actions column
4. The object browser will open for that bucket

### Check Sync Status
1. Go to MinIO → Sync Monitor
2. View the Current Sync Status section
3. Check the status indicator and progress

### Trigger Manual Sync
1. Go to MinIO → Sync Monitor
2. Click "Sync Now" for incremental sync
3. Or click "Full Sync" for complete synchronization
4. Monitor progress in the status section

### View Sync History
1. Go to MinIO → Sync Monitor
2. Scroll to Sync History section
3. Select time period (1, 7, or 30 days)
4. Review past sync events

## Search MinIO Entities

You can also search for MinIO entities using Atlas's global search:

1. Use the search box at the top
2. Type "minio" followed by your search term
3. Results will include MinIO buckets and objects indexed in Atlas

## Troubleshooting

### Connection Issues
If the connection status shows "Disconnected":
1. Click "Test Connection" to verify
2. Check MinIO server is running
3. Verify MinIO credentials in application.properties
4. Check network connectivity

### Sync Failures
If sync shows failed status:
1. Go to Sync Monitor
2. Check Sync History for error details
3. Review the error message in the Error column
4. Common issues:
   - Network timeout
   - Authentication failure
   - Insufficient permissions

### No Buckets Showing
If bucket list is empty:
1. Verify MinIO connection
2. Check user has permission to list buckets
3. Ensure buckets exist in MinIO

## File Locations

The MinIO UI components are located at:

```
atlas-source/dashboardv3/public/js/
├── views/minio/
│   ├── MinioDashboardView.js
│   ├── BucketBrowserView.js
│   └── SyncMonitorView.js
├── templates/minio/
│   ├── MinioDashboardView_tmpl.html
│   ├── BucketBrowserView_tmpl.html
│   └── SyncMonitorView_tmpl.html
├── models/VMinio.js
├── collection/VMinioList.js
├── utils/MinIOUtils.js
└── router/Router.js (updated with MinIO routes)
```

## API Endpoints Used

The UI communicates with the following REST API endpoints:

- `GET /api/atlas/minio/test` - Test connection
- `GET /api/atlas/minio/buckets` - List buckets
- `GET /api/atlas/minio/objects` - List objects
- `POST /api/atlas/minio/sync` - Trigger sync
- `GET /api/atlas/minio/sync/status` - Get sync status
- `GET /api/atlas/minio/sync/history` - Get sync history

## Development Notes

### Adding New Features

To add new MinIO UI features:

1. Create view in `views/minio/`
2. Create corresponding template in `templates/minio/`
3. Add route in `router/Router.js`
4. Add utility functions in `utils/MinIOUtils.js` if needed
5. Update URL links in `utils/UrlLinks.js` if needed

### Styling

The MinIO UI follows Atlas Dashboard V3 styling:
- Uses Bootstrap 3 classes
- Font Awesome icons
- AdminLTE-style boxes and panels
- Consistent with existing Atlas UI

## Future Enhancements

Planned features for future releases:

- Object Browser with full file tree view
- Classification management UI
- Error log viewer with filtering
- Real-time sync progress updates
- MinIO configuration editor
- Advanced sync scheduling

## Support

For issues or questions:
1. Check the main project README
2. Review API documentation
3. Check Atlas logs for error messages
4. Verify MinIO server status

---

**Version**: 1.0.0
**Last Updated**: 2026-03-27
**Author**: Generated with Claude Code
