# Webhook Integration Guide

This guide explains how to integrate the Release Dashboard webhook with your GitHub Actions workflows.

## Overview

After uploading artifacts to JFrog Artifactory, workflows can notify the Release Dashboard service via a webhook. This allows the dashboard to track and display releases across all repositories.

## Setup

### 1. Deploy the Release Dashboard Service

Deploy the dashboard service and note its URL (e.g., `https://release-dashboard.example.com`).

### 2. Configure GitHub Secrets

Add the following secrets to your GitHub repository:

- `RELEASE_DASHBOARD_URL`: The base URL of your dashboard service (e.g., `https://release-dashboard.example.com`)
- `RELEASE_DASHBOARD_WEBHOOK_SECRET`: (Optional) Secret token for webhook authentication

### 3. Add Webhook Step to Workflow

Add the "Notify Release Dashboard" step to your workflow after uploading artifacts to JFrog. The step is already included in `deploy_preview.yaml` as an example.

## Webhook Payload Format

The webhook expects a JSON payload with the following structure:

```json
{
  "repository": "platform-apis",
  "pr_number": 123,
  "environment": "preview",
  "commit_sha": "abc123def456",
  "branch": "feature/new-feature",
  "workflow_run_id": "1234567890",
  "artifacts": {
    "sbom_cyclonedx": "generic-release-artifacts/platform-apis/preview/pr-123/sbom-cyclonedx.json",
    "sbom_spdx": "generic-release-artifacts/platform-apis/preview/pr-123/sbom-spdx.json",
    "sbom_summary": "generic-release-artifacts/platform-apis/preview/pr-123/sbom-summary.txt",
    "release_notes": "generic-release-artifacts/platform-apis/preview/pr-123/release-notes.md"
  }
}
```

## Manual Webhook Call Example

If you need to manually trigger a webhook notification:

```bash
curl -X POST "https://release-dashboard.example.com/webhook/artifact" \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Secret: your-secret-token" \
  -d '{
    "repository": "platform-apis",
    "pr_number": 123,
    "environment": "preview",
    "commit_sha": "abc123def456",
    "branch": "feature/new-feature",
    "artifacts": {
      "sbom_cyclonedx": "generic-release-artifacts/platform-apis/preview/pr-123/sbom-cyclonedx.json",
      "sbom_spdx": "generic-release-artifacts/platform-apis/preview/pr-123/sbom-spdx.json",
      "sbom_summary": "generic-release-artifacts/platform-apis/preview/pr-123/sbom-summary.txt",
      "release_notes": "generic-release-artifacts/platform-apis/preview/pr-123/release-notes.md"
    }
  }'
```

## Security

- The webhook endpoint supports optional secret-based authentication via the `X-Webhook-Secret` header
- Configure `WEBHOOK_SECRET` in the dashboard service environment variables
- If `WEBHOOK_SECRET` is not set, authentication is disabled (not recommended for production)

## Error Handling

The workflow step is designed to fail gracefully:
- If the dashboard service is unavailable, the workflow will continue
- Errors are logged but don't block the workflow
- The step only runs if `RELEASE_DASHBOARD_URL` is configured
