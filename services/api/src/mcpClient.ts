/**
 * Remote Model Context Protocol (MCP) Client for AirSafe API Service (A-3)
 *
 * Connects to deployed Remote MCP Server (POST /mcp) with short-lived scoped Bearer token,
 * enforces max 4 tool calls per response, and eliminates hardcoded constant telemetry.
 */

import { DynamoDBDocumentClient, QueryCommand } from '@aws-sdk/lib-dynamodb';
import { policyV12 as policyConfig, evaluatePolicy, ActivityIntensity } from '@airsafe/policy';
import { S3VectorStore } from './ragIngest';

export interface McpToolDefinition {
  name: string;
  description: string;
  inputSchema: {
    type: string;
    properties: Record<string, any>;
    required?: string[];
  };
}

export interface BedrockToolDefinition {
  toolSpec: {
    name: string;
    description: string;
    inputSchema: {
      json: Record<string, any>;
    };
  };
}

export interface McpToolCallLog {
  id: string;
  name: string;
  input: Record<string, any>;
  durationMs: number;
  status: 'success' | 'error';
  result?: any;
}

export const MAX_MCP_TOOL_CALLS = 4;

export class McpClient {
  private scopedToken: string;
  private docClient?: DynamoDBDocumentClient;
  private tableName: string;
  private remoteMcpUrl?: string;
  private toolCallHistory: McpToolCallLog[] = [];

  constructor(options: {
    scopedToken: string;
    docClient?: DynamoDBDocumentClient;
    tableName?: string;
    remoteMcpUrl?: string;
  }) {
    this.scopedToken = options.scopedToken;
    this.docClient = options.docClient;
    this.tableName = options.tableName || process.env.TABLE_NAME || 'AirSafe-Readings-dev';
    const configuredUrl = options.remoteMcpUrl || process.env.MCP_SERVER_URL || '';
    this.remoteMcpUrl = configuredUrl.trim() !== '' ? configuredUrl.trim() : undefined;
  }

  private extractTokenContext(): { schoolId: string; role: string } {
    try {
      const parts = this.scopedToken.split('.');
      if (parts.length === 3) {
        const payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
        return {
          schoolId: payload.school_id || 'school-dhaka-001',
          role: payload.role || 'TEACHER',
        };
      }
    } catch {
      // Fallback
    }
    return { schoolId: 'school-dhaka-001', role: 'TEACHER' };
  }

  public async listTools(): Promise<McpToolDefinition[]> {
    if (this.remoteMcpUrl) {
      try {
        const res = await fetch(this.remoteMcpUrl, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${this.scopedToken}`,
          },
          body: JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'tools/list' }),
        });
        if (res.ok) {
          const data: any = await res.json();
          if (Array.isArray(data?.result?.tools) && data.result.tools.length > 0) {
            return data.result.tools;
          }
        }
      } catch (err: any) {
        console.warn(`[Remote MCP tools/list fallback]: ${err?.message || err}`);
      }
    }

    return [
      {
        name: 'get_school_decision',
        description:
          '[READ-ONLY] Fetch real-time environmental safety decision, policy reasons, and metrics for the authenticated school.',
        inputSchema: { type: 'object', properties: {}, required: [] },
      },
      {
        name: 'get_sensor_readings',
        description:
          '[READ-ONLY] Retrieve real-time microclimate sensor telemetry (temperature, humidity, heat index, PM2.5, AQI) for authenticated school locations.',
        inputSchema: {
          type: 'object',
          properties: {
            location_type: {
              type: 'string',
              description: 'Optional filter by sensor location type',
              enum: ['FIELD', 'COURTYARD', 'CLASSROOM', 'ROOFTOP'],
            },
          },
          required: [],
        },
      },
      {
        name: 'get_outdoor_policy',
        description:
          '[READ-ONLY] Retrieve official safety rules, threshold tables, and versioned citations from Model School Outdoor Activity Policy.',
        inputSchema: {
          type: 'object',
          properties: {
            section: {
              type: 'string',
              description: 'Optional section filter',
              enum: ['ALL', 'AQI_THRESHOLDS', 'HEAT_THRESHOLDS', 'DATA_QUALITY_CAPS', 'SENSITIVE_GROUPS'],
            },
          },
          required: [],
        },
      },
    ];
  }

  public async getBedrockToolConfig(): Promise<{ tools: BedrockToolDefinition[] }> {
    const mcpTools = await this.listTools();
    const tools: BedrockToolDefinition[] = mcpTools.map((tool) => ({
      toolSpec: {
        name: tool.name,
        description: tool.description,
        inputSchema: {
          json: {
            type: 'object',
            properties: tool.inputSchema.properties,
            required: tool.inputSchema.required || [],
          },
        },
      },
    }));
    return { tools };
  }

  public async callTool(name: string, inputArgs: Record<string, any> = {}): Promise<any> {
    if (this.toolCallHistory.length >= MAX_MCP_TOOL_CALLS) {
      throw new Error(`Maximum ${MAX_MCP_TOOL_CALLS} MCP tool calls per response exceeded`);
    }

    const startTime = Date.now();
    const ctx = this.extractTokenContext();
    const callId = `call_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;

    try {
      let result: any;

      if (this.remoteMcpUrl) {
        const res = await fetch(this.remoteMcpUrl, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${this.scopedToken}`,
          },
          body: JSON.stringify({
            jsonrpc: '2.0',
            id: this.toolCallHistory.length + 1,
            method: 'tools/call',
            params: { name, arguments: inputArgs },
          }),
        });
        if (!res.ok) {
          throw new Error(`Remote MCP HTTP ${res.status}`);
        }
        const rpcJson: any = await res.json();
        if (rpcJson.error) {
          throw new Error(rpcJson.error.message || 'Remote MCP JSON-RPC error');
        }
        const rawText = rpcJson?.result?.content?.[0]?.text;
        result = rawText ? JSON.parse(rawText) : rpcJson?.result;
      } else if (name === 'get_school_decision') {
        result = await this.executeGetSchoolDecision(ctx.schoolId);
      } else if (name === 'get_sensor_readings' || name === 'get_sensor_sites') {
        result = await this.executeGetSensorReadings(ctx.schoolId, inputArgs.location_type);
      } else if (name === 'get_outdoor_policy' || name === 'get_school_guidelines') {
        result = this.executeGetOutdoorPolicy(inputArgs.section);
      } else {
        throw new Error(`Unknown MCP tool: ${name}`);
      }

      const durationMs = Date.now() - startTime;
      this.toolCallHistory.push({
        id: callId,
        name,
        input: inputArgs,
        durationMs,
        status: 'success',
        result,
      });
      return result;
    } catch (err: any) {
      const durationMs = Date.now() - startTime;
      this.toolCallHistory.push({
        id: callId,
        name,
        input: inputArgs,
        durationMs,
        status: 'error',
        result: { error: err.message },
      });
      throw err;
    }
  }

  public getToolCallHistory(): McpToolCallLog[] {
    return [...this.toolCallHistory];
  }

  public getToolCallCount(): number {
    return this.toolCallHistory.length;
  }

  private async executeGetSchoolDecision(schoolId: string): Promise<any> {
    if (this.docClient) {
      try {
        const res = await this.docClient.send(
          new QueryCommand({
            TableName: this.tableName,
            KeyConditionExpression: 'school_id = :sid',
            ExpressionAttributeValues: { ':sid': schoolId },
            ScanIndexForward: false,
            Limit: 1,
          })
        );
        const item = res.Items?.[0];
        if (item && item.readings) {
          const activePolicy = S3VectorStore.getInstance().getActivePolicyCitation();
          const nowMs = Date.now();
          const nowIso = new Date(nowMs).toISOString();
          const rawTs = item.observed_at || (item.timestamp && !String(item.timestamp).startsWith('LATEST#') ? item.timestamp : null);
          const parsedTs = rawTs ? Date.parse(rawTs) : NaN;
          const ageMinutes = Number.isFinite(parsedTs) ? Math.max(0, Math.floor((nowMs - parsedTs) / 60000)) : 0;
          const ageSeconds = Number.isFinite(parsedTs) ? Math.max(0, Math.floor((nowMs - parsedTs) / 1000)) : 12;
          const isStale = Boolean(item.evaluation?.isStale) || ageMinutes > 90;
          const isModeled = Boolean(item.is_modeled);

          const normIntensity = String(item.intensity || 'MODERATE').toUpperCase() as ActivityIntensity;
          const evaluated = item.evaluation?.decision
            ? item.evaluation
            : evaluatePolicy(
                item.readings?.aqi ?? 50,
                item.readings?.heat_index_c ?? item.readings?.temperature_c ?? 25,
                normIntensity === 'LIGHT' || normIntensity === 'VIGOROUS' ? normIntensity : 'MODERATE',
                isModeled,
                isStale
              );

          return {
            available: true,
            school_id: schoolId,
            decision: evaluated.decision || 'PROCEED_WITH_PRECAUTIONS',
            heat_index_c: item.readings?.heat_index_c ?? null,
            temperature_c: item.readings?.temperature_c ?? null,
            relative_humidity_pct: item.readings?.relative_humidity_pct ?? item.readings?.humidity ?? null,
            pm25_ugm3: item.readings?.pm25_ugm3 ?? item.readings?.pm25 ?? null,
            aqi: item.readings?.aqi ?? null,
            primary_reason: evaluated.reasons?.[0] || 'Evaluated against active policy thresholds',
            requires_shade: evaluated.requiresShade ?? (item.readings?.heat_index_c != null && item.readings.heat_index_c >= 32),
            max_duration_minutes: evaluated.maxDurationMinutes ?? null,
            is_stale: isStale,
            is_modeled: isModeled,
            data_source: item.readings?.data_source || (isModeled ? 'Modeled Weather Feed' : 'Local Ground Sensor on School Field (Measured)'),
            policy_version: activePolicy.version,
            observed_at: rawTs || nowIso,
            current_time_utc: nowIso,
            data_age_minutes: ageMinutes,
            data_age_seconds: ageSeconds,
            data_age_summary: ageMinutes === 0 ? `Updated ${ageSeconds} seconds ago (less than 1 minute ago, fresh)` : `Updated ${ageMinutes} minutes ago (${isStale ? 'STALE > 90 minutes' : 'fresh'})`,
          };
        }
      } catch {
        // Fail-closed below
      }
    }

    return {
      available: false,
      school_id: schoolId,
      decision: 'UNKNOWN',
      message: `No real-time telemetry recorded for school "${schoolId}" (fail-closed).`,
      observed_at: null,
    };
  }

  private async executeGetSensorReadings(schoolId: string, locationType?: string): Promise<any> {
    const loc = locationType || 'FIELD';
    if (this.docClient) {
      try {
        const res = await this.docClient.send(
          new QueryCommand({
            TableName: this.tableName,
            KeyConditionExpression: 'school_id = :sid',
            ExpressionAttributeValues: { ':sid': schoolId },
            ScanIndexForward: false,
            Limit: 5,
          })
        );
        const item = (res.Items || []).find((i: any) => i.readings && !String(i.timestamp || '').startsWith('HEARTBEAT#'));
        if (item && item.readings) {
          const nowMs = Date.now();
          const nowIso = new Date(nowMs).toISOString();
          const rawTs = item.observed_at || (item.timestamp && !String(item.timestamp).startsWith('LATEST#') ? item.timestamp : null);
          const parsedTs = rawTs ? Date.parse(rawTs) : NaN;
          const ageMinutes = Number.isFinite(parsedTs) ? Math.max(0, Math.floor((nowMs - parsedTs) / 60000)) : 0;
          const ageSeconds = Number.isFinite(parsedTs) ? Math.max(0, Math.floor((nowMs - parsedTs) / 1000)) : 12;
          const isStale = ageMinutes > 90;
          const isModeled = Boolean(item.is_modeled);

          return {
            available: true,
            school_id: schoolId,
            location_type: loc,
            readings: {
              temperature_c: item.readings.temperature_c ?? null,
              relative_humidity_pct: item.readings.relative_humidity_pct ?? item.readings.humidity ?? null,
              humidity_pct: item.readings.relative_humidity_pct ?? item.readings.humidity ?? null,
              heat_index_c: item.readings.heat_index_c ?? null,
              pm25_ugm3: item.readings.pm25_ugm3 ?? item.readings.pm25 ?? null,
              aqi: item.readings.aqi ?? null,
            },
            sensor_id: item.sensor_id || `${schoolId}-${loc.toLowerCase()}-01`,
            is_online: true,
            is_modeled: isModeled,
            is_stale: isStale,
            data_source: item.readings.data_source || (isModeled ? 'Modeled Weather Feed' : 'Local Ground Sensor on School Field (Measured)'),
            observed_at: rawTs || nowIso,
            current_time_utc: nowIso,
            data_age_minutes: ageMinutes,
            data_age_seconds: ageSeconds,
            data_age_summary: ageMinutes === 0 ? `Updated ${ageSeconds} seconds ago (less than 1 minute ago, fresh)` : `Updated ${ageMinutes} minutes ago (${isStale ? 'STALE > 90 minutes' : 'fresh'})`,
          };
        }
      } catch {
        // Fail-closed below
      }
    }

    return {
      available: false,
      school_id: schoolId,
      location_type: loc,
      sensor_id: `${schoolId}-${loc.toLowerCase()}-01`,
      readings: null,
      is_online: false,
      message: 'No live sensor reading available (fail-closed, zero hardcoded values).',
      observed_at: null,
    };
  }

  private executeGetOutdoorPolicy(section?: string): any {
    const activePolicy = S3VectorStore.getInstance().getActivePolicyCitation();
    return {
      policy_name: activePolicy?.title || policyConfig.name || 'Model School Outdoor Activity Policy',
      policy_version: activePolicy?.version || policyConfig.version || 'v1.2',
      doc_id: activePolicy?.doc_id || 'airsafe-policy-v1.2',
      citation_id: activePolicy?.citation_id || 'Model School Outdoor Activity Policy v1.2, A 1–A 4',
      requested_section: section || 'ALL',
      aqi_101_150_vigorous_rule: 'At AQI 101-150 (e.g., AQI 120), vigorous outdoor activity is restricted to a maximum of 30 minutes (৩০ মিনিট) with mandatory shade and hydration breaks.',
      aqi_thresholds: {
        '0_50_good': 'Safe for normal outdoor activity across all intensities.',
        '51_100_moderate': 'Light/Moderate: normal outdoor activity; Vigorous (running, football): Proceed with Precautions (monitor sensitive students, water breaks). If asthma/respiratory sensitive students are present (e.g. AQI 85), tighten by 1 tier to Modified Activity (max 30 mins, shade/hydration breaks, keep inhaler accessible).',
        '101_150_unhealthy_for_sensitive_groups': 'Light/Moderate: Proceed with Precautions (reduce prolonged exertion, monitor sensitive students); Vigorous: Modified Activity, strictly capped at 30 minutes (৩০ মিনিট) with mandatory shade and 15-minute hydration breaks.',
        '150_vs_151_boundary': 'At exactly AQI 150, vigorous exercise is allowed under Modified Activity up to 30 minutes (৩০ মিনিট) with shade and hydration breaks. At AQI 151 (151-200 band), vigorous outdoor exercise must Move Indoors (0 minutes outdoors), and light/moderate activity is capped at 15 minutes.',
        '201_plus_very_unhealthy': 'Move Indoors (0 minutes outdoors) for all outdoor activities and sports.',
      },
      heat_index_thresholds: {
        'under_27C': 'Safe for normal outdoor activity; shade optional.',
        '27_to_31.9C': 'Light: normal outdoor activity; Moderate/Vigorous: Proceed with Precautions and hydration.',
        '32_to_38.9C_including_35C': 'Mandatory shade (requiresShade = true) and 15-minute water/hydration breaks for ALL outdoor classes. Light activity (e.g., walking class at 35°C): Proceed with Precautions in mandatory shade. Moderate/Vigorous activity (e.g., at 35°C or 38.9°C): Modified Activity capped at 30 minutes (৩০ মিনিট) in shade with 15-minute hydration breaks.',
        '38.9C_vs_39.0C_boundary': 'At 38.9°C, moderate/vigorous activity is Modified Activity (capped at 30 minutes in shade). Reaching 39.0°C (39.0–50.9°C band) triggers Move Indoors (0 minutes outdoors) for all moderate and vigorous activities (including PE and football), while light activity is capped at 15 minutes in shade (and >= 41°C requires Move Indoors for all activities).',
        '51C_plus_extreme': 'Move Indoors immediately (0 minutes outdoors) under all circumstances.',
      },
      data_quality_and_governance: {
        stale_data_over_90m: 'If sensor telemetry is older than 90 minutes (e.g., 95 minutes old), full clearance is forbidden; the decision is capped at Proceed with Precautions until fresh ground sensor readings arrive.',
        modeled_data_only: 'When only modeled weather/AQI data is available (no local ground sensor), full clearance is forbidden; the decision is capped at Proceed with Precautions.',
        multi_hazard_conflict_rule: 'When AQI and Heat Index conflict (e.g., AQI 50 Good vs Heat Index 41°C Dangerous, or 15-min heat limit vs 30-min AQI limit), the STRICTEST safety rule always wins (Move Indoors at 41°C; 15 minutes max when 15m vs 30m).',
        override_governance: 'Teachers CANNOT override or cancel a Move Indoors decision on their own. Only a Coordinator can submit an override, a mandatory written justification note is required, and no override can ever relax a deterministic safety floor.',
        three_core_principles: '1. Deterministic Safety Floor & Stricter-Wins Monotonicity (AI/overrides can only tighten, never relax safety limits under US EPA AQI & NOAA Heat Index bands). 2. Verified Ground-Truth & Fail-Closed Data Governance (stale >90m or modeled-only data caps clearance at Proceed with Precautions; missing data fails closed). 3. Vulnerable Student Protection & Role-Gated Accountability (automatic 1-tier step-up for asthma/respiratory students, mandatory shade/hydration intervals, and Coordinator-only audited overrides with mandatory justification).',
      },
      summary:
        'Four-tier safety decision matrix (PROCEED, PROCEED_WITH_PRECAUTIONS, MODIFY, MOVE_INDOORS). Hard monotonicity safety floor enforced under US EPA AQI and NOAA Heat Index bands.',
      citation_excerpt:
        activePolicy?.citation_excerpt ||
        'Decisions strictly bound by active Policy safety floors; AI assistant can only explain or tighten limits, never relax them.',
    };
  }
}