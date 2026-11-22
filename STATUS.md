# AirSafe Schools: Feature Implementation & Verification Status (STATUS.md)
**Date:** November 2026  
**Repository:** https://github.com/bdjewel27/airsafe-schools-clean (Public)  
**Target Environment:** AWS Sydney (`ap-southeast-2`) & Native Android (API 26–35)  
**Evaluation Standard:** Model School Outdoor Activity Policy v1.2/v1.3, Project Specification & Audit Gates 1–5  

---

## 1. Product Scope Feature Matrix (Section 03: F-01 to F-33)

| Feature Code | Requirement Level | Feature Name | Status | Technical Implementation & Verification Details |
| :--- | :---: | :--- | :---: | :--- |
| **F-01** | **MUST** | শুধু আমন্ত্রণে সাইন-ইন (Invite-Only Sign-In) | **done** | No self-registration. Server-enforced PBKDF2 (600,000 iterations) salted hashing (`POST /auth/login`). Caller role and `schoolId` are derived strictly from cryptographically signed server claims. |
| **F-02** | **MUST** | "এখন" হোম ডিসিশন কার্ড ("Now" Decision Card) | **done** | Implements Policy v1.2/v1.3 four-tier safety model (`PROCEED`, `PROCEED_WITH_PRECAUTIONS`, `MODIFY`, `MOVE_INDOORS`, fail-closed `UNKNOWN`). Displays localized reason, metrics, duration limits, and shade requirements. |
| **F-03** | **MUST** | আমার স্কুলের লাইভ অবস্থা (Live Campus Status) | **done (Simulated / Seeded)** | **Sensor Status:** কোনো physical hardware sensor online নেই (Zero physical sensors on campus). Playground, courtyard, and classroom readings are generated deterministically via sensor simulator (`simulator/src/index.ts`) and seeded via `scripts/seed-data.ts`. |
| **F-04** | **MUST** | ট্রেন্ড — ২৪ ঘণ্টা / ৭ দিন / ৩০ দিন (Trends & Historical Comparison) | **done** | 24-hour, 7-day, and 30-day environmental trends with Compose Canvas charts, PM2.5 column, 4 policy tiers, multi-site comparison, and accessible table view toggle. |
| **F-05** | **MUST** | প্রশ্ন করুন — AI উত্তর (Interactive Grounded AI Assistant) | **done (Live `gpt-6-luna` + Fallback)** | AWS Sydney (`ap-southeast-2`) রিজিয়নে Amazon Bedrock Nova অন-ডিমান্ড কোটা `0.0` (`NOT adjustable`, Account `570088430516` CloudWatch: `3,853` throttles / CloudTrail: `3,507` failed calls; [ADR-001](docs/adr/001-bedrock-model-fallback.md)) হওয়ায় `LlmClient` ইন্টারফেসের মাধ্যমে OpenAI **`gpt-6-luna`** (AWS Secrets Manager `airsafe/openai/api-key`) ও ডেডিকেটেড `airsafe-chat-stream-dev` (`RESPONSE_STREAM` SSE) Lambda যুক্ত করা হয়েছে। লাইভ মডেল ইভ্যালুয়েশনে (`node evals/run-evals.js --live`, `mode: live`, `model: gpt-6-luna`) **৪৪/৪৪ (১০০%) পাস** (`p50 = 5236 ms`, `p95 = 9011 ms`, `133,461` in / `10,214` out tokens, `$0.017430 USD`, [docs/eval-report.md](docs/eval-report.md)) এবং ফলব্যাক মোডেও (`--mock-only`) ৪৪/৪৪ পাস যাচাইকৃত। |
| **F-06** | **MUST** | সতর্কবার্তা — FCM পুশ ও quiet hours (Push Notifications) | **আংশিক / লোকাল ফলব্যাক** | `services/poller/src/fcm.ts`-এ Firebase Admin SDK এবং রাত ২১:০০–সকাল ০৬:০০ (BST) quiet hours যাচাইকৃত। তবে Android অ্যাপে `google-services.json` না থাকায় রিমোট FCM পুশ নিষ্ক্রিয় আছে; `AirSafeNotificationHelper`-এর মাধ্যমে ইন-অ্যাপ লোকাল নোটিফিকেশন ও হেডস-আপ সতর্কবার্তা কাজ করে (শিক্ষক-মুখী হোম স্ক্রিন থেকে ডেভেলপার FCM ওয়ার্নিং ব্যানার সরানো হয়েছে)। |
| **F-07** | **MUST** | অফলাইন ও পুরোনো ডেটা (Offline & Stale Data Resilience) | **done** | Telemetry older than 90 minutes automatically caps at `PROCEED_WITH_PRECAUTIONS`. Network disconnect transitions gracefully to cached SQLite/Room snapshot with warning banner. |
| **F-08** | **SHOULD** | অ্যাক্টিভিটি নোট (Teacher Context Notes) | **done** | Teachers log observational notes (`POST /schools/{schoolId}/notes`). Presence of respiratory symptoms steps up safety tier by 1. |
| **F-10** | **MUST** | রুটিন ও অ্যাক্টিভিটি (Routine & Intensity Matrix) | **done** | Structured intensity mapping (`LIGHT`, `MODERATE`, `VIGOROUS`), class, and duration configuration stored in DynamoDB `ROUTINE#current`. Includes PE downgrade warning dialog and mandatory justification. |
| **F-11** | **MUST** | বাধ্যতামূলক কারণসহ ওভাররাইড ও অডিট লগ (Coordinator Override) | **done** | Coordinator overrides require mandatory >=5-char justification note, duration, and expiration timestamp. Unified `OVERRIDE_AUDIT#` log (`GET /schools/{schoolId}/overrides/audit`). |
| **F-12** | **MUST** | স্টাফ আমন্ত্রণ ও রোল (Staff Invitation & RBAC) | **done** | Coordinators invite teachers (`POST /schools/{schoolId}/staff/invite`) via AWS Cognito (`AdminCreateUserCommand`). `schoolId` extracted strictly from token; role forced to `TEACHER`. |
| **F-13** | **MUST** | স্কুল অ্যানালিটিক্স (School Safety Analytics) | **done** | Aggregated telemetry summaries via `/schools/{schoolId}/analytics` with routine-based lost outdoor minutes, unified override counts, and conditional hiding when prior-week data is absent. |
| **F-14** | **SHOULD** | সাপ্তাহিক রিপোর্ট এক্সপোর্ট (Weekly Safety Report Share) | **done** | Native Android Intent Share Sheet and row-level structured CSV export for school governing bodies. |
| **F-15** | **SHOULD** | নীতি দেখা — বর্তমান ভার্সন ও কার্যকর তারিখ (Policy Viewer) | **done** | View active Model School Outdoor Activity Policy version, authority, and effective date (`/policies/current`). |
| **F-20** | **MUST** | বীজ ডেটা (Seed Dataset: 3 Demo Schools, 64 Districts, 30 Days) | **done** | Seeds 3 canonical demo schools (`school-dhaka-001`, `school-chattogram-001`, `school-rajshahi-001`), all 64 Bangladesh district baseline stations, staff, routines (`VIGOROUS` for PE), and diurnal telemetry. |
| **F-21** | **MUST** | ফল্ট ইনজেকশনসহ সেন্সর সিমুলেটর (Sensor Simulator) | **done** | `simulator/src/index.ts` generates telemetry with 7 fault modes, batches to SQS, enforces post-DB-write `event_id` idempotency, quarantine metrics, and `LATEST#` conditional guards. |
| **F-22** | **MUST** | শুধু-পড়ার MCP সার্ভার (Read-Only MCP Server) | **done** | JSON-RPC 2.0 MCP server (`POST /mcp`) with S-5 rate limiting (60 req/min) and HMAC-SHA256 scoped bearer token authentication (21/21 verified). |
| **F-23** | **SHOULD** | অস্বাভাবিক রিডিং যাচাই (Anomalous Reading Plausibility) | **done** | Validates physical plausibility via delta checks (temperature, humidity, PM2.5, regional station delta) and Open-Meteo fail-closed `null` handling. |
| **F-30** | **STRETCH** | মাঠের ছবি দেখে যাচাই (Ground Photo Verification) | **করা হয়নি** | প্রজেক্ট ব্রিফের স্ট্রেচ গোল; বর্তমান স্কোপে বাস্তবায়িত হয়নি। |
| **F-31** | **STRETCH** | বাংলা ভয়েস সহকারী (Bangla Voice Queries) | **করা হয়নি** | প্রজেক্ট ব্রিফের স্ট্রেচ গোল; বর্তমান স্কোপে বাস্তবায়িত হয়নি। |
| **F-32** | **STRETCH** | Glance উইজেট (Android Glance Home Screen Widget) | **করা হয়নি** | প্রজেক্ট ব্রিফের স্ট্রেচ গোল; বর্তমান স্কোপে বাস্তবায়িত হয়নি। |
| **F-33** | **STRETCH** | বাইরে যাওয়ার সেরা সময় (Best Outdoor Time Window) | **করা হয়নি** | প্রজেক্ট ব্রিফের স্ট্রেচ গোল; বর্তমান স্কোপে বাস্তবায়িত হয়নি। |

---

## 2. UX Requirements Matrix (Section 04: U-1 to U-10)

| UX Code | Requirement | Status | Technical Implementation |
| :--- | :--- | :---: | :--- |
| **U-1** | ৩ ট্যাপে সিদ্ধান্ত (Morning Check in $\le 3$ Taps) | **done** | Cold start 790ms, glance decision verified in 2.8s on physical test device (Oct 2026 baseline). |
| **U-2** | রঙের পাশাপাশি সুস্পষ্ট চিহ্ন (Color + Icon + Text) | **done** | Green checkmark, Yellow caution, Orange alert, Red stop shield, Gray question with Bengali/English text labels. |
| **U-3** | আউটডোর ভিউয়েবিলিটি ও টাচ টার্গেট ($48\times 48\text{ dp}$) | **done** | All interactive controls enforce $\ge 48\times 48\text{ dp}$ touch targets with WCAG AAA high-contrast colors. |
| **U-4** | ডেটার বয়স ও আস্থার ব্যাজ (Freshness & Confidence Badges) | **done** | Measured (সবুজ), Modeled (হলুদ — 0% measured shows "মডেল করা ডেটা"), Stale (কমলা) badges. |
| **U-5** | অফলাইন নোটিস (Offline Warning Banner) | **done** | High-contrast banner appears on network loss, displaying cached snapshot without crashing. |
| **U-6** | পূর্ণ বাংলা ইন্টারফেস (Full Bengali Interface) | **done** | Bilingual runtime toggle with `res/values/strings.xml` and `res/values-bn/strings.xml`. |
| **U-7** | TalkBack অ্যাক্সেসিবিলিটি (Screen Reader Support) | **done** | Composables provide descriptive bilingual `contentDescription` and semantic grouping. |
| **U-8** | ৪টি অপরিহার্য অবস্থা (Loading, Empty, Error, Stale) | **done** | Fail-closed state transitions for all 4 states without blank screens or fake fallbacks. |
| **U-9** | চার্ট ও টেবিল টগল (Trends Chart & Table View) | **done** | Interactive 24h/7d/30d Canvas trends with accessible tabular alternative. |
| **U-10**| এআই সাইটেশন পিল ও ডিসক্লেইমার (AI Citations & Disclaimer) | **done** | Clickable policy citations with strict non-diagnostic medical disclaimer. |

---

## 3. Evaluation Rubric: 5 Hard Gates Audit (Section 15)

- **Gate 1 (Secret Hygiene & Clean Repository):** **PASSED**. Published to a clean public repository (`https://github.com/bdjewel27/airsafe-schools-clean`) as a single verified initial release commit (`0` legacy history leaks, `0` Gitleaks findings); all credentials and API keys remain externalized in AWS Secrets Manager & SSM Parameter Store; `.github/workflows/security.yml` enforces `gitleaks-action@v2` on CI.
- **Gate 2 (Crash-Free & End-to-End Operational):** **PASSED (Simulated/Seeded Telemetry)**. Clean execution across backend API (`117/117`), Poller/Policy (`105/105` incl. `52/52` Appendix A), MCP (`21/21`), Simulator (`8/8`), Android JVM (`31/31` + clean `ktlintCheck`), and 15-minute CloudShell load test (`116,540` events, `0` enqueue failures).
- **Gate 3 (Tenant & Role Isolation):** **PASSED**. Strictly enforced server-side. Teachers cannot execute coordinator overrides or routines (`403 Forbidden`). Cross-tenant requests rejected. Cognito attributes client read-only.
- **Gate 4 (Policy Safety Floor & Monotonic Strictness):** **PASSED**. `shared/policy/src/policy.ts` and `v1.2.json`/`v1.3.json` serve as the single source of truth. Coordinator overrides and AI assistant can only tighten safety decisions, never relax below the calculated floor.
- **Gate 5 (Cost Discipline, Scope Integrity & Evaluation Honesty):** **PASSED (Transparent Disclosures)**. Requirement **F-05** is verified on live OpenAI **`gpt-6-luna`** (`node evals/run-evals.js --live`, **`44/44` (`100%`)**, `p50 = 5236 ms`, `p95 = 9011 ms`, `$0.017430 USD` in `docs/eval-report.md` and `evals/eval-results.json`) with automatic labelled fallback to the deterministic rule engine when Bedrock Nova quota is `0.0` (`NOT adjustable` per Account `570088430516` CloudWatch/CloudTrail evidence in `docs/day1-checks.md`). **F-06** is honestly recorded as local notification fallback pending `google-services.json`. Estimated spend `$3.42` tracked alongside `docs/aws-cost-explorer-billing.csv` in `docs/COST.md`.

---

## 4. Engineering Standards & Deliverables (Section 11, 14, 16)

- **AI Usage & Error Log:** Documented in [`docs/ai-usage-log.md`](docs/ai-usage-log.md).
- **Weekly Development Status Reports:** Documented in `docs/status/week-1.md` through `docs/status/week-4.md`.
- **Architectural Decision Records (ADRs):** Documented in `docs/adr/001-bedrock-model-fallback.md` through `docs/adr/014-coordinator-override-governance-and-audit.md`.
- **Physical Device Test Evidence:** Documented in `docs/device-test.md`.
- **Cloud Load Test Evidence:** Documented in `docs/ingestion-load-test-report.md` and `loadtest/results/cloud-loadtest-15min-570088430516.json`.
