import { SecretsManagerClient, GetSecretValueCommand } from '@aws-sdk/client-secrets-manager';
import { BedrockRuntimeClient, ConverseCommand, ConverseStreamCommand } from '@aws-sdk/client-bedrock-runtime';
import { DynamoDBDocumentClient, GetCommand, UpdateCommand } from '@aws-sdk/lib-dynamodb';
import { PolicyDecision } from '@airsafe/policy';

export const ALLOWED_DECISIONS: readonly PolicyDecision[] = [
  'PROCEED',
  'PROCEED_WITH_PRECAUTIONS',
  'MODIFY',
  'MOVE_INDOORS',
] as const;

const STRICTNESS_RANK: Record<PolicyDecision, number> = {
  PROCEED: 0,
  PROCEED_WITH_PRECAUTIONS: 1,
  MODIFY: 2,
  MOVE_INDOORS: 3,
  UNKNOWN: 4,
};

/**
 * Tighten-Only Decision Combiner:
 * AI may select one of the 4 allowed decisions and can ONLY tighten (escalate)
 * the deterministic policy floor—never relax or downgrade it.
 */
export function combineDecisionTightenOnly(
  policyFloor: PolicyDecision,
  aiProposedDecision?: string | null
): PolicyDecision {
  if (policyFloor === 'UNKNOWN') return 'UNKNOWN';
  const normalized = String(aiProposedDecision || '').trim().toUpperCase() as PolicyDecision;
  if (!ALLOWED_DECISIONS.includes(normalized)) {
    return policyFloor;
  }
  return STRICTNESS_RANK[normalized] > STRICTNESS_RANK[policyFloor] ? normalized : policyFloor;
}

export interface ChatTurn {
  role: 'user' | 'assistant' | 'system';
  content: string;
}

export interface DecisionContext {
  schoolId: string;
  schoolNameEn: string;
  schoolNameBn: string;
  aqi: number | null;
  heatIndexC: number | null;
  intensity: string;
  policyFloor: PolicyDecision;
  reasons: string[];
  hasRespiratoryCondition: boolean;
  isStale: boolean;
  isModeled: boolean;
}

export interface DecisionSynthesisResult {
  decision: PolicyDecision;
  rationale: string;
  provider: 'openai' | 'bedrock' | 'rule-engine';
  model: string;
  isRuleBasedFallback: boolean;
  answerSourceLabel?: string;
  tokensUsed: { inputTokens: number; outputTokens: number; totalTokens: number };
  costUsd: number;
}

export interface RemoteMcpToolConfig {
  serverUrl: string;
  scopedToken: string;
  maxToolCalls?: number;
  localToolExecutor?: (name: string, args: Record<string, any>) => Promise<any>;
}

export interface StreamChatOptions {
  systemPrompt: string;
  telemetryContext: string;
  isBengali: boolean;
  schoolId: string;
  docClient?: DynamoDBDocumentClient;
  budgetTableName?: string;
  fallbackAnswer: string;
  remoteMcp?: RemoteMcpToolConfig;
  onTokenChunk?: (delta: string) => void;
  onToolCall?: (toolCall: { name: string; count: number; input: any; output?: any }) => void;
}

export interface StreamChatResult {
  answer: string;
  provider: 'openai' | 'bedrock' | 'rule-engine';
  model: string;
  isRuleBasedFallback: boolean;
  answerSourceLabel?: string;
  toolsCalledCount: number;
  toolCalls: Array<{ name: string; input: Record<string, any>; output?: any; durationMs: number }>;
  tokensUsed: { inputTokens: number; outputTokens: number; totalTokens: number };
  costUsd: number;
}

export interface LlmClient {
  readonly providerName: 'openai' | 'bedrock';
  readonly modelId: string;
  generateDecision(prompt: string, context: DecisionContext): Promise<DecisionSynthesisResult>;
  streamChat(query: string, history: ChatTurn[], options: StreamChatOptions): Promise<StreamChatResult>;
}

// Secrets Manager cache (in-memory only, never logged)
let cachedOpenAiKey: string | null = null;

export function resetOpenAiKeyCache(): void {
  cachedOpenAiKey = null;
}

export async function getOpenAiApiKey(): Promise<string> {
  if (cachedOpenAiKey) return cachedOpenAiKey;

  if (process.env.OPENAI_API_KEY && process.env.OPENAI_API_KEY.trim() !== '') {
    cachedOpenAiKey = process.env.OPENAI_API_KEY.trim();
    return cachedOpenAiKey;
  }

  if (process.env.JEST_WORKER_ID && !process.env.OPENAI_API_KEY) { throw new Error('Offline Jest test mode'); }
  const secretId = process.env.OPENAI_SECRET_NAME || 'airsafe/openai/api-key';
  const region = process.env.AWS_REGION || process.env.PROJECT_REGION || 'ap-southeast-2';
  const smClient = new SecretsManagerClient({ region });
  const res = await smClient.send(new GetSecretValueCommand({ SecretId: secretId }));
  const raw = res.SecretString?.trim();
  if (!raw) {
    throw new Error(`Secrets Manager secret ${secretId} is empty`);
  }

  try {
    const parsed = JSON.parse(raw);
    const key = String(parsed.OPENAI_API_KEY || parsed.apiKey || '').trim();
    if (!key) throw new Error('OPENAI_API_KEY field missing in secret JSON');
    cachedOpenAiKey = key;
  } catch {
    const skMatch = raw.match(/(sk-[A-Za-z0-9_-]{20,})/);
    cachedOpenAiKey = skMatch ? skMatch[1] : raw.replace(/^{?OPENAI_API_KEYs*:s*/i, '').replace(/}$/, '').trim();
  }

  return cachedOpenAiKey;
}

// Token pricing for gpt-6-luna ($0.10 / 1M input, $0.40 / 1M output)
const GPT6_LUNA_INPUT_PER_M = 0.10;
const GPT6_LUNA_OUTPUT_PER_M = 0.40;

export function estimateLunaCostUsd(inputTokens: number, outputTokens: number): number {
  const cost = (inputTokens * GPT6_LUNA_INPUT_PER_M + outputTokens * GPT6_LUNA_OUTPUT_PER_M) / 1_000_000;
  return Number(cost.toFixed(6));
}

// In-memory budget accumulator (supplemented by DynamoDB atomic tracking when available)
let inMemoryDailySpendUsd = 0;
let inMemorySpendDate = new Date().toISOString().slice(0, 10);

export function resetBudgetTrackerForTests(): void {
  inMemoryDailySpendUsd = 0;
  inMemorySpendDate = new Date().toISOString().slice(0, 10);
}

async function checkBudgetAvailable(
  docClient?: DynamoDBDocumentClient,
  tableName: string = process.env.DECISIONS_TABLE_NAME || 'AirSafe-Decisions-dev'
): Promise<{ allowed: boolean; dailySpent: number; dailyLimit: number }> {
  const dailyLimit = Number(process.env.OPENAI_DAILY_BUDGET_USD || '1.00');
  const monthlyLimit = Number(process.env.OPENAI_MONTHLY_BUDGET_USD || '10.00');
  const now = new Date();
  const dayKey = now.toISOString().slice(0, 10);
  const monthKey = now.toISOString().slice(0, 7);

  if (inMemorySpendDate !== dayKey) {
    inMemorySpendDate = dayKey;
    inMemoryDailySpendUsd = 0;
  }

  if (inMemoryDailySpendUsd >= dailyLimit) {
    return { allowed: false, dailySpent: inMemoryDailySpendUsd, dailyLimit };
  }

  if (docClient && typeof docClient.send === 'function') {
    try {
      const [dayRes, monthRes] = await Promise.all([
        docClient.send(new GetCommand({ TableName: tableName, Key: { school_id: 'LLM_BUDGET', activity_id: `DAILY#${dayKey}` } })),
        docClient.send(new GetCommand({ TableName: tableName, Key: { school_id: 'LLM_BUDGET', activity_id: `MONTHLY#${monthKey}` } })),
      ]);
      const dbDaily = Number(dayRes.Item?.spent_usd || 0);
      const dbMonthly = Number(monthRes.Item?.spent_usd || 0);
      if (dbDaily >= dailyLimit || dbMonthly >= monthlyLimit) {
        return { allowed: false, dailySpent: dbDaily, dailyLimit };
      }
    } catch {
      // Fail-open to in-memory budget guard if DynamoDB read unavailable
    }
  }

  return { allowed: true, dailySpent: inMemoryDailySpendUsd, dailyLimit };
}

async function recordLlmSpendAndLog(params: {
  schoolId: string;
  operation: 'decision' | 'chat';
  model: string;
  inputTokens: number;
  outputTokens: number;
  costUsd: number;
  latencyMs: number;
  docClient?: DynamoDBDocumentClient;
  tableName?: string;
}): Promise<void> {
  const now = new Date();
  const dayKey = now.toISOString().slice(0, 10);
  const monthKey = now.toISOString().slice(0, 7);
  if (inMemorySpendDate !== dayKey) {
    inMemorySpendDate = dayKey;
    inMemoryDailySpendUsd = 0;
  }
  inMemoryDailySpendUsd = Number((inMemoryDailySpendUsd + params.costUsd).toFixed(6));

  // Structured CloudWatch metric log
  console.log(
    JSON.stringify({
      event: 'LLM_TOKEN_COST_AUDIT',
      school_id: params.schoolId,
      operation: params.operation,
      model: params.model,
      input_tokens: params.inputTokens,
      output_tokens: params.outputTokens,
      total_tokens: params.inputTokens + params.outputTokens,
      cost_usd: params.costUsd,
      daily_spend_usd: inMemoryDailySpendUsd,
      latency_ms: params.latencyMs,
      timestamp: now.toISOString(),
    })
  );

  const tableName = params.tableName || process.env.DECISIONS_TABLE_NAME || 'AirSafe-Decisions-dev';
  if (params.docClient && typeof params.docClient.send === 'function' && params.costUsd > 0) {
    try {
      await Promise.all([
        params.docClient.send(
          new UpdateCommand({
            TableName: tableName,
            Key: { school_id: 'LLM_BUDGET', activity_id: `DAILY#${dayKey}` },
            UpdateExpression: 'ADD spent_usd :c, total_calls :one SET updated_at = :ts',
            ExpressionAttributeValues: { ':c': params.costUsd, ':one': 1, ':ts': now.toISOString() },
          })
        ),
        params.docClient.send(
          new UpdateCommand({
            TableName: tableName,
            Key: { school_id: 'LLM_BUDGET', activity_id: `MONTHLY#${monthKey}` },
            UpdateExpression: 'ADD spent_usd :c, total_calls :one SET updated_at = :ts',
            ExpressionAttributeValues: { ':c': params.costUsd, ':one': 1, ':ts': now.toISOString() },
          })
        ),
      ]);
    } catch {
      // Best-effort DynamoDB spend persistence
    }
  }
}

/**
 * OpenAI Client Adapter using economical gpt-6-luna + Responses API + Remote MCP + True SSE
 */
export class OpenAiClient implements LlmClient {
  public readonly providerName = 'openai' as const;
  public readonly modelId: string;
  private baseUrl: string;
  private docClient?: DynamoDBDocumentClient;

  constructor(options?: { modelId?: string; baseUrl?: string; docClient?: DynamoDBDocumentClient }) {
    this.modelId = options?.modelId || process.env.OPENAI_MODEL_ID || 'gpt-6-luna';
    this.baseUrl = (options?.baseUrl || process.env.OPENAI_BASE_URL || 'https://api.openai.com/v1').replace(/\/$/, '');
    this.docClient = options?.docClient;
  }

  public async generateDecision(prompt: string, context: DecisionContext): Promise<DecisionSynthesisResult> {
    const startTime = Date.now();
    const budget = await checkBudgetAvailable(this.docClient);
    if (!budget.allowed) {
      return {
        decision: context.policyFloor,
        rationale: context.reasons[0] || 'Rule-based policy floor applied (daily AI budget reached).',
        provider: 'rule-engine',
        model: 'policy-v1.2-rule-engine',
        isRuleBasedFallback: true,
        answerSourceLabel: 'নিয়মভিত্তিক উত্তর',
        tokensUsed: { inputTokens: 0, outputTokens: 0, totalTokens: 0 },
        costUsd: 0,
      };
    }

    try {
      const apiKey = await getOpenAiApiKey();
      const systemInstruction = `You are the AirSafe Schools safety decision verifier.
Select ONE decision from: ["PROCEED", "PROCEED_WITH_PRECAUTIONS", "MODIFY", "MOVE_INDOORS"].
CRITICAL INVARIANT: The deterministic policy floor is "${context.policyFloor}". You may ONLY confirm "${context.policyFloor}" or escalate to a STRICTER decision. You must NEVER relax it.
Return ONLY valid JSON: {"decision":"...","rationale":"..."}.`;

      const res = await fetch(`${this.baseUrl}/responses`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${apiKey}`,
        },
        body: JSON.stringify({
          model: this.modelId,
          instructions: systemInstruction,
          input: `${prompt}\nContext: ${JSON.stringify(context)}`,
          max_output_tokens: 1024,
        }),
      });

      if (!res.ok) {
        throw new Error(`OpenAI Responses API HTTP ${res.status}`);
      }

      const data: any = await res.json();
      const outputText =
        data.output_text ||
        data.output?.[0]?.content?.[0]?.text ||
        '{}';
      const cleaned = String(outputText).replace(/```json/gi, '').replace(/```/g, '').trim();
      const parsed = JSON.parse(cleaned);

      // Tighten-only combiner guarantees AI can only tighten, never relax
      const finalDecision = combineDecisionTightenOnly(context.policyFloor, parsed.decision);

      const inputTokens = Number(data.usage?.input_tokens ?? 140);
      const outputTokens = Number(data.usage?.output_tokens ?? 45);
      const costUsd = estimateLunaCostUsd(inputTokens, outputTokens);

      await recordLlmSpendAndLog({
        schoolId: context.schoolId,
        operation: 'decision',
        model: this.modelId,
        inputTokens,
        outputTokens,
        costUsd,
        latencyMs: Date.now() - startTime,
        docClient: this.docClient,
      });

      return {
        decision: finalDecision,
        rationale: String(parsed.rationale || context.reasons[0] || ''),
        provider: 'openai',
        model: this.modelId,
        isRuleBasedFallback: false,
        tokensUsed: { inputTokens, outputTokens, totalTokens: inputTokens + outputTokens },
        costUsd,
      };
    } catch (err: any) {
      console.warn(`[OpenAiClient Decision Fallback]: ${err?.message || err}`);
      return {
        decision: context.policyFloor,
        rationale: context.reasons[0] || 'Deterministic policy floor applied.',
        provider: 'rule-engine',
        model: 'policy-v1.2-rule-engine',
        isRuleBasedFallback: true,
        answerSourceLabel: 'নিয়মভিত্তিক উত্তর',
        tokensUsed: { inputTokens: 0, outputTokens: 0, totalTokens: 0 },
        costUsd: 0,
      };
    }
  }

  public async streamChat(query: string, history: ChatTurn[], options: StreamChatOptions): Promise<StreamChatResult> {
    const startTime = Date.now();
    const docClient = options.docClient || this.docClient;
    const budget = await checkBudgetAvailable(docClient, options.budgetTableName);

    if (!budget.allowed) {
      if (options.onTokenChunk) {
        options.onTokenChunk(options.fallbackAnswer);
      }
      return {
        answer: options.fallbackAnswer,
        provider: 'rule-engine',
        model: 'policy-v1.2-rule-engine',
        isRuleBasedFallback: true,
        answerSourceLabel: 'নিয়মভিত্তিক উত্তর',
        toolsCalledCount: 0,
        toolCalls: [],
        tokensUsed: { inputTokens: 0, outputTokens: 0, totalTokens: 0 },
        costUsd: 0,
      };
    }

    const toolCallsLog: Array<{ name: string; input: Record<string, any>; output?: any; durationMs: number }> = [];
    const maxToolCalls = Math.min(4, options.remoteMcp?.maxToolCalls ?? 4);

    const mcpFunctionTools = [
      {
        type: 'function',
        name: 'get_school_decision',
        description: '[READ-ONLY] Fetch real-time environmental safety decision and metrics for the authenticated school.',
        parameters: { type: 'object', properties: {}, required: [] },
      },
      {
        type: 'function',
        name: 'get_sensor_readings',
        description: '[READ-ONLY] Retrieve real-time microclimate sensor telemetry (temperature, humidity, heat index, PM2.5, AQI).',
        parameters: {
          type: 'object',
          properties: {
            location_type: { type: 'string', enum: ['FIELD', 'COURTYARD', 'CLASSROOM', 'ROOFTOP'] },
          },
          required: [],
        },
      },
      {
        type: 'function',
        name: 'get_outdoor_policy',
        description: '[READ-ONLY] Fetch official safety thresholds and versioned citations from Model School Outdoor Activity Policy.',
        parameters: {
          type: 'object',
          properties: {
            section: {
              type: 'string',
              enum: ['ALL', 'AQI_THRESHOLDS', 'HEAT_THRESHOLDS', 'DATA_QUALITY_CAPS', 'SENSITIVE_GROUPS'],
            },
          },
          required: [],
        },
      },
    ];

    try {
      const apiKey = await getOpenAiApiKey();
      let totalIn = 0;
      let totalOut = 0;
      let previousResponseId: string | undefined;
      let nextInput: any = [
        ...history.map((h) => ({ role: h.role, content: h.content })),
        { role: 'user', content: `<untrusted_user_query>\n${query}\n</untrusted_user_query>` },
      ];

      let finalAnswer = '';

      // Tool-calling loop: max 4 tool calls per response, feeding Remote MCP results back to model
      for (let round = 0; round <= maxToolCalls; round++) {
        const isFinalAllowedRound = round === maxToolCalls;
        const payload: Record<string, any> = {
          model: this.modelId,
          instructions: `${options.systemPrompt}\n\n${options.telemetryContext}`,
          input: nextInput,
          stream: true,
          max_output_tokens: 2048,
          ...(previousResponseId ? { previous_response_id: previousResponseId } : {}),
          ...(!isFinalAllowedRound ? { tools: mcpFunctionTools } : {}),
        };

        const res = await fetch(`${this.baseUrl}/responses`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Accept: 'text/event-stream',
            Authorization: `Bearer ${apiKey}`,
          },
          body: JSON.stringify(payload),
        });

        if (!res.ok || !res.body) {
          throw new Error(`OpenAI Responses stream HTTP ${res.status}`);
        }

        const reader = res.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';
        let roundText = '';
        const pendingFunctionCalls: Array<{ call_id: string; name: string; arguments: string }> = [];

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });

          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          for (const line of lines) {
            const trimmed = line.trim();
            if (!trimmed.startsWith('data:')) continue;
            const dataStr = trimmed.slice(5).trim();
            if (!dataStr || dataStr === '[DONE]') continue;

            try {
              const evt = JSON.parse(dataStr);
              if (evt.type === 'response.output_text.delta' && typeof evt.delta === 'string') {
                roundText += evt.delta;
                if (options.onTokenChunk) {
                  options.onTokenChunk(evt.delta);
                }
              } else if (
                (evt.type === 'response.completed' ||
                  evt.type === 'response.incomplete' ||
                  evt.type === 'response.done') &&
                evt.response
              ) {
                previousResponseId = evt.response.id || previousResponseId;
                totalIn += Number(evt.response.usage?.input_tokens ?? 0);
                totalOut += Number(evt.response.usage?.output_tokens ?? 0);
                const outputs = Array.isArray(evt.response.output) ? evt.response.output : [];
                for (const item of outputs) {
                  if (item.type === 'function_call') {
                    pendingFunctionCalls.push({
                      call_id: item.call_id || item.id,
                      name: item.name,
                      arguments: item.arguments || '{}',
                    });
                  } else if (item.type === 'message' && Array.isArray(item.content) && !roundText) {
                    for (const part of item.content) {
                      if (typeof part?.text === 'string' && part.text.trim()) {
                        roundText += part.text;
                      }
                    }
                  }
                }
              }
            } catch {
              // Ignore partial non-JSON SSE heartbeat frames
            }
          }
        }

        if (pendingFunctionCalls.length === 0) {
          finalAnswer = roundText;
          break;
        }

        // Execute up to remaining tool call budget (max 4 total) via Remote MCP POST /mcp
        const toolOutputs: any[] = [];
        for (const call of pendingFunctionCalls) {
          if (toolCallsLog.length >= maxToolCalls) break;
          const tStart = Date.now();
          let parsedArgs: Record<string, any> = {};
          try {
            parsedArgs = JSON.parse(call.arguments || '{}');
          } catch {
            parsedArgs = {};
          }

          let toolResult: any;
          if (options.remoteMcp?.localToolExecutor) {
            toolResult = await options.remoteMcp.localToolExecutor(call.name, parsedArgs);
          } else if (options.remoteMcp?.serverUrl) {
            const mcpRes = await fetch(options.remoteMcp.serverUrl, {
              method: 'POST',
              headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${options.remoteMcp.scopedToken}`,
              },
              body: JSON.stringify({
                jsonrpc: '2.0',
                id: toolCallsLog.length + 1,
                method: 'tools/call',
                params: { name: call.name, arguments: parsedArgs },
              }),
            });
            const mcpJson: any = await mcpRes.json();
            const rawText = mcpJson?.result?.content?.[0]?.text;
            toolResult = rawText ? JSON.parse(rawText) : mcpJson?.result || mcpJson;
          } else {
            toolResult = { status: 'unavailable' };
          }

          const durationMs = Date.now() - tStart;
          toolCallsLog.push({ name: call.name, input: parsedArgs, output: toolResult, durationMs });

          if (options.onToolCall) {
            options.onToolCall({
              name: call.name,
              count: toolCallsLog.length,
              input: parsedArgs,
              output: toolResult,
            });
          }

          toolOutputs.push({
            type: 'function_call_output',
            call_id: call.call_id,
            output: JSON.stringify(toolResult),
          });
        }

        if (toolOutputs.length === 0) {
          finalAnswer = roundText;
          break;
        }

        nextInput = toolOutputs;
      }

      if (!finalAnswer || finalAnswer.trim().length === 0) {
        throw new Error('OpenAI Responses stream produced empty answer');
      }

      const finalIn = totalIn || 180 + Math.ceil(query.length / 4);
      const finalOut = totalOut || Math.max(40, Math.ceil(finalAnswer.length / 4));
      const costUsd = estimateLunaCostUsd(finalIn, finalOut);

      await recordLlmSpendAndLog({
        schoolId: options.schoolId,
        operation: 'chat',
        model: this.modelId,
        inputTokens: finalIn,
        outputTokens: finalOut,
        costUsd,
        latencyMs: Date.now() - startTime,
        docClient,
        tableName: options.budgetTableName,
      });

      return {
        answer: finalAnswer,
        provider: 'openai',
        model: this.modelId,
        isRuleBasedFallback: false,
        toolsCalledCount: toolCallsLog.length,
        toolCalls: toolCallsLog,
        tokensUsed: { inputTokens: finalIn, outputTokens: finalOut, totalTokens: finalIn + finalOut },
        costUsd,
      };
    } catch (err: any) {
      console.warn(`[OpenAiClient Stream Fallback]: ${err?.message || err}. Serving rule-based answer.`);
      if (options.onTokenChunk) {
        options.onTokenChunk(options.fallbackAnswer);
      }
      return {
        answer: options.fallbackAnswer,
        provider: 'rule-engine',
        model: 'policy-v1.2-rule-engine',
        isRuleBasedFallback: true,
        answerSourceLabel: 'নিয়মভিত্তিক উত্তর',
        toolsCalledCount: toolCallsLog.length,
        toolCalls: toolCallsLog,
        tokensUsed: { inputTokens: 0, outputTokens: 0, totalTokens: 0 },
        costUsd: 0,
      };
    }
  }
}

/**
 * Bedrock Client Adapter (Instant 1-setting switch back to Amazon Nova when Sydney quota opens)
 */
export class BedrockClient implements LlmClient {
  public readonly providerName = 'bedrock' as const;
  public readonly modelId: string;
  private client: BedrockRuntimeClient;

  constructor(options?: { modelId?: string; region?: string; client?: BedrockRuntimeClient }) {
    this.modelId = options?.modelId || process.env.BEDROCK_MODEL_ID || 'amazon.nova-lite-v1:0';
    const region = options?.region || process.env.BEDROCK_REGION || process.env.AWS_REGION || 'ap-southeast-2';
    this.client = options?.client || new BedrockRuntimeClient({ region });
  }

  public async generateDecision(prompt: string, context: DecisionContext): Promise<DecisionSynthesisResult> {
    try {
      const cmd = new ConverseCommand({
        modelId: this.modelId,
        system: [{ text: `Return JSON {"decision":"...","rationale":"..."} without relaxing ${context.policyFloor}.` }],
        messages: [{ role: 'user', content: [{ text: `${prompt}\n${JSON.stringify(context)}` }] }],
        inferenceConfig: { maxTokens: 150, temperature: 0.0 },
      });
      const res: any = await this.client.send(cmd);
      const txt = res.output?.message?.content?.[0]?.text || '{}';
      const parsed = JSON.parse(txt.replace(/```json/gi, '').replace(/```/g, '').trim());
      const finalDecision = combineDecisionTightenOnly(context.policyFloor, parsed.decision);
      const inTok = res.usage?.inputTokens ?? 120;
      const outTok = res.usage?.outputTokens ?? 35;
      return {
        decision: finalDecision,
        rationale: parsed.rationale || context.reasons[0] || '',
        provider: 'bedrock',
        model: this.modelId,
        isRuleBasedFallback: false,
        tokensUsed: { inputTokens: inTok, outputTokens: outTok, totalTokens: inTok + outTok },
        costUsd: Number(((inTok * 0.06 + outTok * 0.24) / 1_000_000).toFixed(6)),
      };
    } catch {
      return {
        decision: context.policyFloor,
        rationale: context.reasons[0] || 'Rule-based fallback.',
        provider: 'rule-engine',
        model: 'policy-v1.2-rule-engine',
        isRuleBasedFallback: true,
        answerSourceLabel: 'নিয়মভিত্তিক উত্তর',
        tokensUsed: { inputTokens: 0, outputTokens: 0, totalTokens: 0 },
        costUsd: 0,
      };
    }
  }

  public async streamChat(query: string, history: ChatTurn[], options: StreamChatOptions): Promise<StreamChatResult> {
    try {
      const cmd = new ConverseStreamCommand({
        modelId: this.modelId,
        system: [{ text: `${options.systemPrompt}\n\n${options.telemetryContext}` }],
        messages: [
          ...history.map((h) => ({ role: h.role === 'assistant' ? 'assistant' as const : 'user' as const, content: [{ text: h.content }] })),
          { role: 'user', content: [{ text: `<untrusted_user_query>\n${query}\n</untrusted_user_query>` }] },
        ],
        inferenceConfig: { maxTokens: 512, temperature: 0.2 },
      });
      const res: any = await this.client.send(cmd);
      let answer = '';
      if (res.stream) {
        for await (const chunk of res.stream) {
          if (chunk.contentBlockDelta?.delta?.text) {
            const delta = chunk.contentBlockDelta.delta.text;
            answer += delta;
            if (options.onTokenChunk) options.onTokenChunk(delta);
          }
        }
      }
      if (!answer.trim()) throw new Error('Empty Bedrock stream');
      const inTok = 150;
      const outTok = Math.max(30, Math.ceil(answer.length / 4));
      return {
        answer,
        provider: 'bedrock',
        model: this.modelId,
        isRuleBasedFallback: false,
        toolsCalledCount: 0,
        toolCalls: [],
        tokensUsed: { inputTokens: inTok, outputTokens: outTok, totalTokens: inTok + outTok },
        costUsd: Number(((inTok * 0.06 + outTok * 0.24) / 1_000_000).toFixed(6)),
      };
    } catch {
      if (options.onTokenChunk) options.onTokenChunk(options.fallbackAnswer);
      return {
        answer: options.fallbackAnswer,
        provider: 'rule-engine',
        model: 'policy-v1.2-rule-engine',
        isRuleBasedFallback: true,
        answerSourceLabel: 'নিয়মভিত্তিক উত্তর',
        toolsCalledCount: 0,
        toolCalls: [],
        tokensUsed: { inputTokens: 0, outputTokens: 0, totalTokens: 0 },
        costUsd: 0,
      };
    }
  }
}

/**
 * Single configuration switch: LLM_PROVIDER=openai (default, gpt-6-luna) | bedrock (Amazon Nova)
 */
export function createLlmClient(options?: { docClient?: DynamoDBDocumentClient }): LlmClient {
  const provider = (process.env.LLM_PROVIDER || 'openai').trim().toLowerCase();
  if (provider === 'bedrock') {
    return new BedrockClient();
  }
  return new OpenAiClient({ docClient: options?.docClient });
}