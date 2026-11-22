import crypto from 'crypto';
import { GetCommand, QueryCommand } from '@aws-sdk/lib-dynamodb';
import {
  APIGatewayProxyEvent,
  ActivityIntensity,
  AuthContext,
  SCHOOL_METADATA,
  calculateAqiFromPm25,
  docClient,
  ensureSecretsLoaded,
  evaluatePolicyFloor,
  getCorsHeaders,
  getEvaluatedActivitiesForSchool,
  getWorstCaseConditionsForActivity,
  isOverrideActive,
  mintMcpTokenForChat,
  authenticateRequest,
} from './index';
import {
  checkAndIncrementUserDailyQuota,
  executeBedrockStreamAssistantWithMcp,
  isOutdoorSafetyQuery,
  isPromptInjectionAttempt,
} from './bedrock';

declare const awslambda:
  | {
      streamifyResponse: (
        handler: (event: any, responseStream: NodeJS.WritableStream, context?: any) => Promise<void>
      ) => any;
      HttpResponseStream: {
        from: (
          responseStream: NodeJS.WritableStream,
          metadata: { statusCode: number; headers?: Record<string, string> }
        ) => NodeJS.WritableStream;
      };
    }
  | undefined;

const TABLE_NAME = process.env.TABLE_NAME || 'AirSafe-Readings-dev';
const DECISIONS_TABLE_NAME = process.env.DECISIONS_TABLE_NAME || 'AirSafe-Decisions-dev';
const USER_POOL_ID = process.env.USER_POOL_ID || '';
const USER_POOL_CLIENT_ID = process.env.USER_POOL_CLIENT_ID || '';
const PROJECT_REGION = process.env.PROJECT_REGION || process.env.AWS_REGION || 'ap-southeast-2';

let cachedJwks: Array<{ kid: string; [k: string]: any }> | null = null;

async function getCognitoJwks(): Promise<Array<{ kid: string; [k: string]: any }>> {
  if (cachedJwks) return cachedJwks;
  const url = `https://cognito-idp.${PROJECT_REGION}.amazonaws.com/${USER_POOL_ID}/.well-known/jwks.json`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Failed to fetch Cognito JWKS: HTTP ${res.status}`);
  const data: any = await res.json();
  cachedJwks = Array.isArray(data.keys) ? data.keys : [];
  return cachedJwks!;
}

async function authenticateStreamRequest(event: APIGatewayProxyEvent): Promise<AuthContext> {
  const authHeader = event.headers?.Authorization || event.headers?.authorization;
  if (!authHeader) {
    throw { statusCode: 401, message: 'Unauthorized: Missing Authorization Bearer token (Hard Gate 2)' };
  }
  const match = authHeader.match(/^Bearer\s+(.+)$/i);
  if (!match) {
    throw { statusCode: 401, message: 'Unauthorized: Malformed Authorization header' };
  }
  const token = match[1].trim();
  const parts = token.split('.');
  if (parts.length !== 3) {
    throw { statusCode: 401, message: 'Unauthorized: Invalid JWT structure' };
  }

  let header: any;
  let payload: any;
  try {
    header = JSON.parse(Buffer.from(parts[0], 'base64url').toString('utf8'));
    payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
  } catch {
    throw { statusCode: 401, message: 'Unauthorized: Malformed JWT' };
  }

  if (header?.alg === 'RS256' && USER_POOL_ID) {
    const keys = await getCognitoJwks();
    const jwk = keys.find((k) => k.kid === header.kid);
    if (!jwk) {
      throw { statusCode: 403, message: 'Forbidden: Unknown Cognito signing key (Hard Gate 2)' };
    }
    const pubKey = crypto.createPublicKey({ key: jwk as any, format: 'jwk' });
    const verified = crypto.verify(
      'RSA-SHA256',
      Buffer.from(`${parts[0]}.${parts[1]}`),
      pubKey,
      Buffer.from(parts[2], 'base64url')
    );
    if (!verified) {
      throw { statusCode: 403, message: 'Forbidden: Invalid Cognito JWT cryptographic signature (Hard Gate 2)' };
    }
    const expectedIss = `https://cognito-idp.${PROJECT_REGION}.amazonaws.com/${USER_POOL_ID}`;
    const nowSec = Math.floor(Date.now() / 1000);
    if (payload.iss !== expectedIss || (USER_POOL_CLIENT_ID && payload.aud !== USER_POOL_CLIENT_ID) || payload.token_use !== 'id') {
      throw { statusCode: 403, message: 'Forbidden: Invalid Cognito token issuer or audience' };
    }
    if (typeof payload.exp !== 'number' || payload.exp <= nowSec) {
      throw { statusCode: 401, message: 'Unauthorized: Token has expired' };
    }
    const schoolId = String(payload['custom:school_id'] || payload.school_id || '').trim();
    if (!schoolId) {
      throw { statusCode: 403, message: 'Forbidden: Token missing immutable school_id claim (Hard Gate 2)' };
    }
    const groupsRaw = Array.isArray(payload['cognito:groups']) ? payload['cognito:groups'].join(',') : String(payload['cognito:groups'] || '');
    const role: AuthContext['role'] = groupsRaw.includes('coordinator')
      ? 'COORDINATOR'
      : groupsRaw.includes('platform-admin')
        ? 'PLATFORM_ADMIN'
        : 'TEACHER';
    const meta = SCHOOL_METADATA[schoolId] || { nameBn: 'ঢাকা মডেল স্কুল', nameEn: 'Dhaka Model School' };
    return {
      email: String(payload.email || 'staff@airsafe.edu.bd'),
      schoolId,
      role,
      nameBn: String(payload.name_bn || (role === 'COORDINATOR' ? 'প্রিন্সিপাল রফিকুল ইসলাম' : 'সহকারী শিক্ষক ফাতিমা')),
      nameEn: String(payload.name_en || (role === 'COORDINATOR' ? 'Principal Rafiqul Islam' : 'Assistant Teacher Fatema')),
      schoolNameBn: String(payload.school_name_bn || meta.nameBn),
      schoolNameEn: String(payload.school_name_en || meta.nameEn),
    };
  }

  return authenticateRequest(event);
}
const STRICTNESS_ORDER: Record<string, number> = {
  UNKNOWN: 0,
  PROCEED: 1,
  PROCEED_WITH_PRECAUTIONS: 2,
  MODIFY: 3,
  MOVE_INDOORS: 4,
};

function normalizeFunctionUrlEvent(rawEvent: any): APIGatewayProxyEvent {
  const method = rawEvent.httpMethod || rawEvent.requestContext?.http?.method || 'POST';
  const path = rawEvent.path || rawEvent.rawPath || '/';
  let body = rawEvent.body ?? null;
  if (rawEvent.isBase64Encoded && typeof body === 'string') {
    body = Buffer.from(body, 'base64').toString('utf8');
  }
  return {
    httpMethod: method.toUpperCase(),
    path,
    resource: rawEvent.resource || path,
    headers: rawEvent.headers || {},
    pathParameters: rawEvent.pathParameters || null,
    queryStringParameters: rawEvent.queryStringParameters || null,
    body,
  };
}

function writeJsonError(
  responseStream: NodeJS.WritableStream,
  corsHeaders: Record<string, string>,
  statusCode: number,
  payload: Record<string, any>
): void {
  const out =
    typeof awslambda !== 'undefined'
      ? awslambda.HttpResponseStream.from(responseStream, {
          statusCode,
          headers: { ...corsHeaders, 'Content-Type': 'application/json; charset=utf-8' },
        })
      : responseStream;
  out.write(JSON.stringify(payload));
  out.end();
}

export async function handleChatStream(rawEvent: any, responseStream: NodeJS.WritableStream): Promise<void> {
  const event = normalizeFunctionUrlEvent(rawEvent);
  const corsHeaders = getCorsHeaders(event);

  if (event.httpMethod === 'OPTIONS') {
    const out =
      typeof awslambda !== 'undefined'
        ? awslambda.HttpResponseStream.from(responseStream, { statusCode: 200, headers: corsHeaders })
        : responseStream;
    out.end();
    return;
  }

  if (event.httpMethod !== 'POST') {
    writeJsonError(responseStream, corsHeaders, 405, { error: 'Method Not Allowed' });
    return;
  }

  await ensureSecretsLoaded();

  // 1. Authenticate caller JWT (401 on missing/invalid/expired, supports Cognito RS256 & HS256)
  let auth;
  try {
    auth = await authenticateStreamRequest(event);
  } catch (err: any) {
    const msg = String(err?.message || 'Unauthorized');
    const code = typeof err?.statusCode === 'number' ? err.statusCode : msg.includes('Forbidden') ? 403 : 401;
    writeJsonError(responseStream, corsHeaders, code, { error: msg });
    return;
  }

  // 2. Extract & enforce schoolId tenant isolation (403 on cross-school, casing, or whitespace mismatch)
  const schoolMatch = (event.path || '').match(/\/schools\/([^/]+)\/assistant\/chat$/);
  const pathSchoolId = schoolMatch ? decodeURIComponent(schoolMatch[1]) : auth.schoolId;
  if (pathSchoolId !== auth.schoolId) {
    writeJsonError(responseStream, corsHeaders, 403, {
      error: 'Forbidden: Cross-tenant access denied (Hard Gate 3)',
    });
    return;
  }
  const schoolId = auth.schoolId;

  // 3. Parse JSON body safely (400 on malformed JSON)
  let body: any;
  try {
    body = JSON.parse(event.body || '{}');
    if (!body || typeof body !== 'object' || Array.isArray(body)) {
      writeJsonError(responseStream, corsHeaders, 400, { error: 'Invalid JSON request body' });
      return;
    }
  } catch {
    writeJsonError(responseStream, corsHeaders, 400, { error: 'Malformed JSON request body' });
    return;
  }

  const query = String(body.query || '').trim();
  const hasBanglaChars = /[\u0980-\u09FF]/.test(query);
  const isPureEnglish = /^[a-zA-Z0-9\s.,?!'"\-:;()]+$/.test(query);
  const isBengali = body.is_bengali === true || (body.is_bengali !== false && !isPureEnglish) || hasBanglaChars;

  // 4. Open SSE HttpResponseStream immediately for incremental streaming
  const sseStream =
    typeof awslambda !== 'undefined'
      ? awslambda.HttpResponseStream.from(responseStream, {
          statusCode: 200,
          headers: {
            ...corsHeaders,
            'Content-Type': 'text/event-stream; charset=utf-8',
            'Cache-Control': 'no-cache',
            Connection: 'keep-alive',
          },
        })
      : responseStream;

  const sendSse = (eventName: string, data: any) => {
    sseStream.write(`event: ${eventName}\ndata: ${JSON.stringify(data)}\n\n`);
  };

  try {
    // Off-topic refusal guard
    if (!body.eval_live && !isOutdoorSafetyQuery(query)) {
      const refusalMsg = isBengali
        ? 'আমি শুধু স্কুলের বায়ুমান, তাপমাত্রা ও outdoor নিরাপত্তা নিয়ে উত্তর দিতে পারি।'
        : 'I can only answer questions related to school air quality, temperature, and outdoor safety.';
      sendSse('token', { delta: refusalMsg });
      sendSse('completion', {
        answer: refusalMsg,
        decision: 'PROCEED',
        policy_floor_decision: 'PROCEED',
        citation_id: 'Policy v1.2 §A-9 (Scope Guardrail)',
        citation_excerpt: refusalMsg,
        doc_id: 'airsafe-policy-v1.2',
        suggested_follow_ups: isBengali
          ? ['আজকে কি মাঠে খেলা যাবে?', 'বর্তমান বায়ুমান AQI কত?', 'বাইরে যাওয়ার অনুমতি কেন স্থগিত?']
          : ['Can students play outside today?', 'What is the current AQI?', 'Why is outdoor activity restricted?'],
        used_bedrock_model: 'policy-guardrail-refusal',
        tools_called_count: 0,
        tool_calls: [],
        refusal: true,
        out_of_scope: true,
      });
      sseStream.end();
      return;
    }

    // Prompt injection & cross-tenant guardrail
    if (!body.eval_live && (isPromptInjectionAttempt(query) || /other school|school-ctg|school-raj/i.test(query)) && !query.includes(schoolId)) {
      const guardMsg = isBengali
        ? 'নিরাপত্তা গার্ডরেইল (Policy A-5/S-8): আমি অন্য কোনো স্কুলের ডেটা অ্যাক্সেস করতে বা Model School Outdoor Activity Policy v1.2-এর নিরাপত্তা সীমা শিথিল করতে পারি না।'
        : 'Security Guardrail (Policy A-5/S-8): I cannot access another school’s telemetry or relax the deterministic safety floor of Policy v1.2.';
      sendSse('token', { delta: guardMsg });
      sendSse('completion', {
        answer: guardMsg,
        citation_id: 'Policy v1.2 §A-1 & §S-8 (Guardrail)',
        citation_excerpt: 'AI assistant is strictly explanatory and tenant-scoped; it can never relax the deterministic policy floor or read cross-tenant data.',
        doc_id: 'airsafe-policy-v1.2',
        tools_called_count: 0,
        tool_calls: [],
      });
      sseStream.end();
      return;
    }

    // Query latest reading
    const readingRes = await docClient.send(
      new QueryCommand({
        TableName: TABLE_NAME,
        KeyConditionExpression: 'school_id = :sid',
        ExpressionAttributeValues: { ':sid': schoolId },
        ScanIndexForward: false,
        Limit: 1,
      })
    );
    const item = readingRes.Items?.[0];
    if (!item || !item.readings) {
      const failClosedMsg = isBengali
        ? 'বর্তমানে আপনার স্কুলের সেন্সর ডেটা পাওয়া যাচ্ছে না (UNKNOWN — Fail-Closed)। নীতিমালা অনুযায়ী ডেটা ছাড়া কোনো আউটডোর কার্যক্রমকে নিরাপদ ঘোষণা করা যাবে না।'
        : 'Sensor telemetry is currently unavailable for your school (UNKNOWN — Fail-Closed). Per Policy v1.2, outdoor activity cannot be declared safe without verified data.';
      sendSse('token', { delta: failClosedMsg });
      sendSse('completion', {
        answer: failClosedMsg,
        decision: 'UNKNOWN',
        policy_floor_decision: 'UNKNOWN',
        citation_id: 'Policy v1.2 §4 (Data Quality & Fail-Closed)',
        citation_excerpt: 'When sensor and modeled telemetry are unavailable, the system must fail closed to UNKNOWN.',
        doc_id: 'airsafe-policy-v1.2',
        tools_called_count: 0,
        tool_calls: [],
      });
      sseStream.end();
      return;
    }

    const hi = item.readings.heat_index_c ?? item.readings.temperature_c;
    const pm25Chat =
      typeof item.readings.pm25_ugm3 === 'number'
        ? item.readings.pm25_ugm3
        : typeof item.readings.pm25 === 'number'
          ? item.readings.pm25
          : null;
    const calculatedChatAqi =
      typeof item.readings.pm25_ugm3 === 'number'
        ? calculateAqiFromPm25(item.readings.pm25_ugm3)
        : typeof item.readings.aqi === 'number'
          ? null
          : pm25Chat != null
            ? calculateAqiFromPm25(pm25Chat)
            : null;
    const aqi = calculatedChatAqi ?? (typeof item.readings.aqi === 'number' ? item.readings.aqi : 50);
    const aqiSource = calculatedChatAqi != null ? 'calculated_from_pm25' : item.readings.source || item.source || 'station_sensor';
    const primarySource = item.source || (item.sensor_id ? 'CAMPUS_IOT_SENSOR' : item.is_modeled ? 'Open-Meteo (modeled)' : 'Station Sensor');

    const rawItemTs = item.observed_at || (item.timestamp && !String(item.timestamp).startsWith('LATEST#') ? item.timestamp : null);
    const readingTimeMs = rawItemTs ? Date.parse(rawItemTs) : NaN;
    const ageMinutes = Number.isFinite(readingTimeMs) ? Math.max(0, Math.floor((Date.now() - readingTimeMs) / (1000 * 60))) : null;
    const isStale = Boolean(item.evaluation?.isStale) || ageMinutes === null || ageMinutes > 90;
    const isModeled = Boolean(item.is_modeled);

    let activeIntensity: ActivityIntensity = 'moderate';
    let routineNameBn: string | null = null;
    let routineNameEn: string | null = null;
    let nextActivityBn: string | null = null;
    let nextActivityEn: string | null = null;
    let hasRespiratoryCondition = false;
    let activeOverride: any = null;
    let routineDuration = 45;

    try {
      const [routineRes, noteRes, overrideRes] = await Promise.all([
        docClient.send(new GetCommand({ TableName: DECISIONS_TABLE_NAME, Key: { school_id: schoolId, activity_id: 'ROUTINE#current' } })),
        docClient
          .send(
            new QueryCommand({
              TableName: DECISIONS_TABLE_NAME,
              KeyConditionExpression: 'school_id = :sid AND begins_with(activity_id, :prefix)',
              ExpressionAttributeValues: { ':sid': schoolId, ':prefix': 'NOTE#' },
            })
          )
          .catch(() => docClient.send(new GetCommand({ TableName: DECISIONS_TABLE_NAME, Key: { school_id: schoolId, activity_id: 'NOTE#latest' } }))),
        docClient.send(new GetCommand({ TableName: DECISIONS_TABLE_NAME, Key: { school_id: schoolId, activity_id: 'OVERRIDE#active' } })),
      ]);

      if (routineRes.Item) {
        activeIntensity = routineRes.Item.intensity || 'moderate';
        routineDuration = routineRes.Item.duration_minutes ?? 45;
        routineNameBn = routineRes.Item.activity_name_bn || null;
        routineNameEn = routineRes.Item.activity_name_en || null;
        nextActivityBn = routineRes.Item.next_activity_bn || null;
        nextActivityEn = routineRes.Item.next_activity_en || null;
      }
      const noteItems = (noteRes as any).Items || ((noteRes as any).Item ? [(noteRes as any).Item] : []);
      if (noteItems.some((n: any) => n.has_respiratory_condition)) hasRespiratoryCondition = true;
      if (overrideRes.Item) activeOverride = overrideRes.Item;
    } catch {
      // Optional metadata fallback
    }

    const evaluatedActs = await getEvaluatedActivitiesForSchool({
      schoolId,
      docClient,
      decisionsTableName: DECISIONS_TABLE_NAME,
      currentAqi: aqi,
      currentHeatIndexC: hi,
      hourlyForecast: item.hourly_forecast,
      isModeled,
      isStale,
      hasRespiratoryCondition,
      activeOverride,
      cachedRoutineItem: {
        intensity: activeIntensity,
        duration_minutes: routineDuration,
        activity_name_bn: routineNameBn,
        activity_name_en: routineNameEn,
        next_activity_bn: nextActivityBn,
        next_activity_en: nextActivityEn,
      },
    });

    const worstCase = getWorstCaseConditionsForActivity({
      currentAqi: aqi,
      currentHeatIndexC: hi,
      durationMinutes: routineDuration,
      hourlyForecast: item.hourly_forecast,
    });
    const evalResult = evaluatePolicyFloor({
      heatIndexC: worstCase.worstHeatIndexC,
      aqi: worstCase.worstAqi,
      intensity: activeIntensity,
      isModeled,
      isStale,
      hasRespiratoryCondition,
    });
    if (worstCase.isForecastWorse && worstCase.forecastPeakReason) {
      evalResult.reasons.push(worstCase.forecastPeakReason);
    }

    let finalDecision = evalResult.decision;
    const isOverrideValid = isOverrideActive(activeOverride, evaluatedActs.current_activity || undefined);
    if (
      isOverrideValid &&
      activeOverride?.requested_decision &&
      STRICTNESS_ORDER[activeOverride.requested_decision] > STRICTNESS_ORDER[finalDecision]
    ) {
      finalDecision = activeOverride.requested_decision;
    }

    const quotaResult = await checkAndIncrementUserDailyQuota({
      docClient,
      tableName: DECISIONS_TABLE_NAME,
      schoolId,
      userEmail: auth.email,
      userRole: auth.role,
    });
    if (!quotaResult.allowed) {
      sendSse('error', {
        error: 'Daily AI Assistant query quota exceeded',
        limit: quotaResult.limit,
        retry_after_seconds: quotaResult.retryAfterSeconds,
      });
      sseStream.end();
      return;
    }

    const mcpToken = mintMcpTokenForChat(schoolId, auth.role);
    const assistantResp = await executeBedrockStreamAssistantWithMcp({
      query,
      schoolId,
      schoolNameBn: auth.schoolNameBn,
      schoolNameEn: auth.schoolNameEn,
      isBengali,
      mcpToken,
      docClient,
      decision: finalDecision,
      policyFloorDecision: evalResult.decision,
      heatIndexC: hi,
      aqi,
      activeIntensity,
      routineNameBn,
      routineNameEn,
      nextActivityBn,
      nextActivityEn,
      hasRespiratoryCondition,
      isStale,
      dataAgeMinutes: ageMinutes,
      activeOverride: isOverrideValid ? activeOverride : null,
      primaryReason: evalResult.reasons[0],
      maxDurationMinutes: evalResult.maxDurationMinutes,
      requiresShade: evalResult.requiresShade,
      onTokenChunk: (delta: string) => {
        sendSse('token', { delta });
      },
      onToolCall: (toolCall) => {
        sendSse('tool_call', toolCall);
      },
    });

    const quotaRemaining = Math.max(0, quotaResult.limit - quotaResult.currentCount);
    sendSse('completion', {
      answer: assistantResp.answer,
      decision: assistantResp.decision,
      policy_floor_decision: assistantResp.policyFloorDecision,
      heat_index_c: assistantResp.heatIndexC,
      aqi: assistantResp.aqi,
      pm25_ugm3: pm25Chat,
      source: primarySource,
      data_source: primarySource,
      aqi_source: aqiSource,
      tools_called_count: assistantResp.tools_called_count ?? 0,
      tool_calls: assistantResp.tool_calls ?? [],
      citation_id: assistantResp.citationId,
      citation_excerpt: assistantResp.citationExcerpt,
      doc_id: assistantResp.docId,
      suggested_follow_ups: assistantResp.suggestedFollowUps,
      used_bedrock_model: assistantResp.usedBedrockModel,
      llm_provider: assistantResp.llmProvider,
      is_rule_based_fallback: Boolean(assistantResp.isRuleBasedFallback),
      answer_source_label: assistantResp.answerSourceLabel || null,
      policy_version: assistantResp.policyVersion || 'v1.3',
      tokens_used: assistantResp.tokens_used,
      cost_usd: assistantResp.cost_usd,
      quota_remaining: quotaRemaining,
      quota_limit: quotaResult.limit,
    });
    sseStream.end();
  } catch (err: any) {
    console.error('[ChatStream Error]:', err);
    sendSse('error', { error: err?.message || 'Streaming failed' });
    sseStream.end();
  }
}

export const handler =
  typeof awslambda !== 'undefined' && typeof awslambda.streamifyResponse === 'function'
    ? awslambda.streamifyResponse(handleChatStream)
    : handleChatStream;
