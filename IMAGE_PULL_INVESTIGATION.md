# Image Pull Secret Investigation

## Issue
Pod is failing with `FailedToRetrieveImagePullSecret` for `release-dash-preview-4-deployment-simple-externalsecret-imagepull`, even though the ExternalSecret shows `SecretSynced`.

## Key Observations
1. **ExternalSecret Status**: `SecretSynced` ✅
2. **Pod Error**: Can't retrieve `release-dash-preview-4-deployment-simple-externalsecret-imagepull`
3. **Expected Secret Name**: `jfrog-docker-imagepull` (from `values.preview.yaml`)

## Investigation Steps

### 1. Check what secret name the ExternalSecret creates:
```bash
kubectl get externalsecret release-dash-preview-4-deployment-simple-externalsecret-imagepull -n platform-preview -o yaml | grep -A 10 "spec:"
```
Look for `spec.target.name` - this is the actual secret name that will be created.

### 2. Check if the secret actually exists:
```bash
kubectl get secrets -n platform-preview | grep -E "(jfrog|imagepull)"
```

### 3. Check what secret name the deployment references:
```bash
kubectl get deployment release-dash-preview-4-deployment-simple -n platform-preview -o yaml | grep -A 5 "imagePullSecrets:"
```

### 4. Check the ServiceAccount (secrets can be referenced there too):
```bash
kubectl get serviceaccount release-dash-preview-4 -n platform-preview -o yaml | grep -A 5 "imagePullSecrets:"
```

### 5. Check ExternalSecret details:
```bash
kubectl describe externalsecret release-dash-preview-4-deployment-simple-externalsecret-imagepull -n platform-preview
```

## Possible Issues

### Issue 1: Secret Name Mismatch
- **Symptom**: ExternalSecret creates secret with name `release-dash-preview-4-deployment-simple-externalsecret-imagepull` but deployment expects `jfrog-docker-imagepull`
- **Fix**: The deployment-simple chart should use the name from `imagePullSecrets` array (`jfrog-docker-imagepull`) as the secret name

### Issue 2: Secret Not Created Yet
- **Symptom**: ExternalSecret shows `SecretSynced` but secret doesn't exist
- **Fix**: Wait a bit longer, or check External Secrets Operator logs for errors

### Issue 3: Secret Type Wrong
- **Symptom**: Secret exists but is wrong type (not `kubernetes.io/dockerconfigjson`)
- **Fix**: ExternalSecret should create secret of type `kubernetes.io/dockerconfigjson`

### Issue 4: ServiceAccount Reference Issue
- **Symptom**: Secret exists but ServiceAccount doesn't reference it
- **Fix**: ServiceAccount should have `imagePullSecrets` array with the secret name

## Quick Diagnostic Script
Run the provided `diagnose-imagepull.sh` script to gather all this information at once.

## Next Steps
1. Run the diagnostic commands above
2. Compare the secret name created vs. the name expected
3. If mismatch, we may need to check how deployment-simple chart handles `imagePullSecrets` configuration
