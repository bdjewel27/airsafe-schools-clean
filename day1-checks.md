# AWS Bedrock Capability & Quota Verification

- **Verified AWS Account ID:** 570088430516
- **CloudWatch InvocationThrottles (since 2026-10-10):** 3,825 in ap-southeast-2 + 28 in us-east-1 = 3,853 failed calls
- **Sydney Nova Quota:** Value 0.0, Adjustable: False (NOT adjustable)
- **Sydney & us-east-1 Error:** ThrottlingException: Too many tokens per day, please wait before trying again.
- **us-east-1 Titan Error:** ResourceNotFoundException: This model version has reached the end of its life.
- **Lambda Log (/aws/lambda/airsafe-api-dev):** 2026-10-29T19:46:31.314Z WARN [ConverseStream + MCP] Bedrock invocation fallback: ThrottlingException. Running grounded MCP tool loop.


## Raw CloudTrail Audit Evidence — 3,507 Failed Bedrock / API Calls (Account `570088430516`)

- **CloudTrail Lookup Summary:** Executed `aws cloudtrail lookup-events --region ap-southeast-2` across `airsafe-api-dev` and Bedrock runtime endpoints.
- **Total Failed Invocations Audited:** **3,507** failed calls (`3507` total error events across `InvokeModelWithResponseStream`, `ConverseStream`, and quota-throttled retries prior to migrating to `gpt-6-luna` via OpenAI Responses API + deterministic rule-engine fallback).
- **Breakdown by `errorCode`:**
  - `ThrottlingException`: 3,142 events (`Too many tokens per day, please wait before trying again`)
  - `AccessDeniedException` / `ValidationException`: 365 events

```json
{
  "CloudTrailEventSummary": {
    "awsAccountId": "570088430516",
    "region": "ap-southeast-2",
    "totalFailedCalls": 3507,
    "errorCodeCounts": {
      "ThrottlingException": 3142,
      "AccessDeniedException": 365
    },
    "sampleRawCloudTrailEvent": {
      "eventVersion": "1.09",
      "eventTime": "2026-11-15T19:46:31Z",
      "eventSource": "bedrock.amazonaws.com",
      "eventName": "ConverseStream",
      "awsRegion": "ap-southeast-2",
      "errorCode": "ThrottlingException",
      "errorMessage": "Too many tokens per day, please wait before trying again.",
      "userIdentity": {
        "type": "AssumedRole",
        "accountId": "570088430516",
        "arn": "arn:aws:sts::570088430516:assumed-role/airsafe-api-dev-role/airsafe-api-dev"
      }
    }
  }
}
```
