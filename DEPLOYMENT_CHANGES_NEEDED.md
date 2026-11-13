# Changes Needed to Deploy release-dash to Staging

This document outlines all the changes needed in the platform-infra repository to deploy the release-dash application to the staging AWS cluster and make it browser accessible.

## Summary

The release-dash application is already configured with:
- ✅ Helm chart with staging values
- ✅ Ingress configuration for `release-dash.slingshotstaging.com`
- ✅ GitHub Actions workflow for deployment
- ✅ CI/CD IAM roles referenced in shared terraform config

**Missing**: The actual IAM role creation and terraform configuration in the platform team module.

## Required Changes

### 1. Create GitHub OIDC IAM Roles for CI/CD

**File**: `/Users/josephhall/Documents/repos/platform-infra/terraform/modules/teams/platform/oidc_github.tf`

Add these module calls at the end of the file:

```hcl
module "release_dash_github_staging" {
  source                   = "../../oidc-github"
  github_repository        = var.release_dash_github_repository
  iam_role_name            = var.release_dash_github_repository_iam_role_name_staging
  token_valid_sub_identity = var.release_dash_github_token_valid_sub
  iam_policy_json_file     = var.release_dash_github_iam_policy_json_file
}

module "release_dash_github_preview" {
  source                   = "../../oidc-github"
  github_repository        = var.release_dash_github_repository
  iam_role_name            = var.release_dash_github_repository_iam_role_name_preview
  token_valid_sub_identity = var.release_dash_github_token_valid_sub
  iam_policy_json_file     = var.release_dash_github_iam_policy_json_file
}
```

**Note**: Both roles are created in the staging environment because:
- Staging deployments use the `platform` namespace in the staging cluster
- Preview deployments use the `platform-preview` namespace in the staging cluster
- Both need access to the same staging EKS cluster

### 2. Add Variables to Platform Team Module

**File**: `/Users/josephhall/Documents/repos/platform-infra/terraform/modules/teams/platform/variables.tf`

Add these variables (following the pattern of other services like `developer_hub` or `platform_simulation`):

```hcl
#################
# release-dash #
#################

variable "release_dash_github_repository" {
  type        = string
  description = "name of the github repo holding release-dash code"
  default     = "release-dash"
}

variable "release_dash_github_repository_iam_role_name_staging" {
  type        = string
  description = "name of the iam role to be used from github workflows for staging"
  default     = "release-dash-cicd-staging"
}

variable "release_dash_github_repository_iam_role_name_preview" {
  type        = string
  description = "name of the iam role to be used from github workflows for preview"
  default     = "release-dash-cicd-preview"
}
```

**Note**: The role names must match exactly what's in the GitHub Actions workflow:
- `release-dash-cicd-staging` (used in `.github/workflows/deploy_staging.yaml`)
- `release-dash-cicd-preview` (used in `.github/workflows/deploy_preview.yaml`)

variable "release_dash_github_iam_policy_json_file" {
  type        = string
  description = "the path to the json file holding the iam policy for the github iam role"
  default     = ""
}

variable "release_dash_github_token_valid_sub" {
  type        = string
  description = "valid subjects to assume iam role for github"
  default     = "main"
}
```

### 3. Add Variables to Platform Team Environment

**File**: `/Users/josephhall/Documents/repos/platform-infra/terraform/environments/staging/teams/platform/variables.tf`

Add the same variables as above (or reference them with appropriate defaults). The variables should match what's expected by the platform module.

### 4. Pass Variables to Platform Module

**File**: `/Users/josephhall/Documents/repos/platform-infra/terraform/environments/staging/teams/platform/main.tf`

Add these variable assignments in the `module "platform"` block (around line 97):

```hcl
  # release-dash
  release_dash_github_repository                      = var.release_dash_github_repository
  release_dash_github_repository_iam_role_name_staging = var.release_dash_github_repository_iam_role_name_staging
  release_dash_github_repository_iam_role_name_preview = var.release_dash_github_repository_iam_role_name_preview
  release_dash_github_iam_policy_json_file             = var.release_dash_github_iam_policy_json_file
  release_dash_github_token_valid_sub                  = var.release_dash_github_token_valid_sub
```

**Important**: You must also add these same variables to the platform module's `variables.tf` file:
- `/Users/josephhall/Documents/repos/platform-infra/terraform/modules/teams/platform/variables.tf`

The variables need to exist in both places:
1. The module's `variables.tf` (defines what the module accepts)
2. The environment's `variables.tf` (defines what values to pass)

### 5. Create IAM Policy JSON Files (Optional)

If the CI/CD roles need specific permissions beyond the default EKS access, create policy files:

**File**: `/Users/josephhall/Documents/repos/platform-infra/terraform/environments/staging/teams/platform/release_dash_github_iam.json`

Example (if default EKS policy is sufficient, you can leave `iam_policy_json_file` as empty string `""`):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "eks:DescribeCluster",
        "eks:ListClusters"
      ],
      "Resource": "*"
    }
  ]
}
```

**Note**: The default policy from the `oidc-github` module provides EKS access, so you may not need a custom policy unless release-dash needs additional AWS permissions.

### 6. IRSA Role (Optional - Probably Not Needed)

The default `values.yaml` references `aws.iam.role: release-dash`, but `values.staging.yaml` doesn't include this. Since the service uses:
- External Secrets Operator (handles AWS Secrets Manager access)
- No direct AWS service calls visible in the code

**The IRSA role is likely NOT needed** for staging. If you later need AWS access, you would need to:
1. Create an IRSA IAM policy JSON file
2. Add variables for IRSA role
3. Add module calls in the platform team module

### 7. Verify Secrets Exist

Ensure these secrets exist in AWS Secrets Manager:
- `/platform/jfrog/release-dash/staging/credentials` (JSON with `username` and `token`)
- `/platform/release-dash/webhook/staging/secret` (string)
- `/platform/release-dash/api/staging/secret` (string)

### 8. DNS Configuration

DNS should be handled automatically by the AWS Load Balancer Controller. When the ingress is created with the host `release-dash.slingshotstaging.com`, the controller will:
1. Create an ALB (Application Load Balancer)
2. Automatically create a Route53 record pointing to the ALB
3. Configure SSL/TLS using the certificate from the domain module

No manual DNS configuration is needed.

## Verification Steps

After making these changes:

1. **Apply Terraform**:
   ```bash
   cd /Users/josephhall/Documents/repos/platform-infra/terraform/environments/staging/teams/platform
   terraform plan
   terraform apply
   ```

2. **Verify IAM Roles Created**:
   ```bash
   aws iam get-role --role-name release-dash-cicd-staging
   aws iam get-role --role-name release-dash-cicd-preview
   ```

3. **Deploy the Application**:
   - Push to main branch or manually trigger the GitHub Actions workflow
   - The workflow should be able to assume the IAM role and deploy to EKS

4. **Verify Ingress**:
   ```bash
   kubectl get ingress -n platform release-dash
   ```

5. **Check DNS**:
   ```bash
   dig release-dash.slingshotstaging.com
   ```

6. **Access the Application**:
   - Navigate to `https://release-dash.slingshotstaging.com` in a browser
   - The healthcheck endpoint should be available at `https://release-dash.slingshotstaging.com/healthcheck`

## Notes

- The CI/CD roles are already referenced in `/Users/josephhall/Documents/repos/platform-infra/terraform/environments/staging/shared/terraform.auto.tfvars.json` (lines 339-346), so they just need to be created.
- The ingress configuration in `values.staging.yaml` is already correct and will work once the application is deployed.
- The service doesn't appear to need an IRSA role for AWS access since it uses External Secrets Operator.
- All secrets should be created manually in AWS Secrets Manager before deployment.
