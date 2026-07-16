package kotodama

// ── Shared types (no build constraint — compiled in both TinyGo and native) ──

// CypherResult holds the output of a Cypher graph query.
type CypherResult struct {
	Columns []string
	Rows    [][]string
}

// BatchStatement is a single Cypher statement in a batch.
type BatchStatement struct {
	Cypher string
	Params [][2]string // [name, json_encoded_value]
}

// ImageUploadOptions controls image processing before CDN upload.
type ImageUploadOptions struct {
	MaxWidth  uint32 // 0 = no limit
	MaxHeight uint32 // 0 = no limit
	Quality   uint32 // 1-100, 0 = default (80)
	Format    string // "webp" | "jpeg" | "png" | "" (auto=webp)
}

// AgentToolDef describes a tool registered with the LLM agent host.
type AgentToolDef struct {
	Name            string
	Description     string
	InputSchemaJSON string
}

// ── Workflow types ──────────────────────────────────────────────────────

// WorkflowStatus mirrors the WIT workflow-status enum.
type WorkflowStatus int32

const (
	WorkflowStatusPending    WorkflowStatus = 0
	WorkflowStatusRunning    WorkflowStatus = 1
	WorkflowStatusSuspended  WorkflowStatus = 2
	WorkflowStatusCompleted  WorkflowStatus = 3
	WorkflowStatusFailed     WorkflowStatus = 4
	WorkflowStatusTerminated WorkflowStatus = 5
)

// RetryPolicy mirrors the WIT retry-policy record.
type RetryPolicy struct {
	MaxRetries        uint32
	InitialIntervalMs uint64
	BackoffMultiplier float64
	MaxIntervalMs     uint64
}

// WorkflowOptions mirrors the WIT workflow-options record.
type WorkflowOptions struct {
	ID               string
	TaskQueue        string
	Memo             string
	ParentWorkflowID *string
}

// WorkflowInfo mirrors the WIT workflow-info record.
type WorkflowInfo struct {
	WorkflowID  string
	Status      WorkflowStatus
	CreatedAtMs uint64
	UpdatedAtMs uint64
	Output      []byte
	Error       *string
}

// ActivityOptions mirrors the WIT activity-options record.
type ActivityOptions struct {
	TaskQueue          string
	StartToCloseMs     uint64
	RetryPolicy        *RetryPolicy
	ScheduleToCloseMs  *uint64
}

// ── Reminder types ──────────────────────────────────────────────────────

// FailurePolicy mirrors the WIT failure-policy enum.
type FailurePolicy int32

const (
	FailurePolicyDrop  FailurePolicy = 0
	FailurePolicyRetry FailurePolicy = 1
)

// ReminderRetryConfig mirrors the WIT retry-config record.
type ReminderRetryConfig struct {
	MaxRetries uint32
	IntervalMs uint64
}

// ReminderConfig mirrors the WIT reminder-config record (input to register).
type ReminderConfig struct {
	Name            string
	DueUnixMs       uint64
	PeriodMs        uint64
	CallbackMethod  string
	Payload         []byte
	MaxInvocations  *uint32
	TTLUnixMs       *uint64
	OnFailure       FailurePolicy
	RetryConfig     *ReminderRetryConfig
}

// ReminderEntry mirrors the WIT reminder-entry record.
type ReminderEntry struct {
	Name           string
	DueUnixMs      uint64
	PeriodMs       uint64
	CallbackMethod string
	PayloadJSON    string
	MaxInvocations *uint32
	Remaining      *uint32
	TTLUnixMs      *uint64
	OnFailure      FailurePolicy
}

// ── Timer types ─────────────────────────────────────────────────────────

// TimerConfig mirrors the WIT timer-config record.
type TimerConfig struct {
	Name            string
	DueMs           uint64
	PeriodMs        uint64
	MaxInvocations  *uint32
	CallbackMethod  string
	Payload         []byte
}

// ── Agent converse types ────────────────────────────────────────────────

// Role mirrors the WIT role enum.
type Role int32

const (
	RoleSystem    Role = 0
	RoleUser      Role = 1
	RoleAssistant Role = 2
	RoleTool      Role = 3
)

// ToolChoiceMode mirrors the WIT tool-choice-mode enum.
type ToolChoiceMode int32

const (
	ToolChoiceAuto     ToolChoiceMode = 0
	ToolChoiceRequired ToolChoiceMode = 1
	ToolChoiceNone     ToolChoiceMode = 2
)

// Message mirrors the WIT message record.
type Message struct {
	Role       Role
	Content    string
	ToolCalls  []byte
	ToolCallID *string
}

// ChatOptions mirrors the WIT chat-options record.
type ChatOptions struct {
	ContextID      *string
	Model          *string
	Temperature    *float64
	ToolChoice     *ToolChoiceMode
	ResponseSchema *string
	ScrubPII       bool
	MaxTokens      *uint32
}

// TokenUsage mirrors the WIT token-usage record.
type TokenUsage struct {
	PromptTokens     uint64
	CompletionTokens uint64
	TotalTokens      uint64
	CachedTokens     uint64
}

// ChatResponse mirrors the WIT chat-response record.
type ChatResponse struct {
	Content      string
	ToolCalls    []byte
	Usage        *TokenUsage
	Model        string
	FinishReason string
}

// ── Agent routing types ──────────────────────────────────────────────────

// RouteMatch is an LLM-classified intent→tool mapping (no execution).
type RouteMatch struct {
	ToolName   string
	Confidence float64
	Arguments  string
}

// ReactOptions configures a ReAct loop.
type ReactOptions struct {
	MaxIterations uint32
	Model         *string
	Temperature   *float64
	MaxTokens     *uint32
}

// ReactStep is a single step trace in a ReAct execution.
type ReactStep struct {
	Iteration  uint32
	Thought    string
	ToolName   *string
	ToolInput  *string
	ToolOutput *string
}

// ReactResult is the completed result of a ReAct loop.
type ReactResult struct {
	Answer         string
	Steps          []ReactStep
	IterationsUsed uint32
}

// ── Activity parallel types ─────────────────────────────────────────────

// ActivityParallelItem is a single activity to schedule in a parallel batch.
type ActivityParallelItem struct {
	Name    string
	Input   []byte
	Options ActivityOptions
}

// ParallelActivityResult is the result of a single activity in a parallel batch.
type ParallelActivityResult struct {
	ActivityID string
	Name       string
	OK         bool
	Output     []byte
	Error      *string
}

// ── SMTP types ───────────────────────────────────────────────────────────

// SmtpProvider identifies the SMTP bridge provider.
type SmtpProvider int32

const (
	SmtpProviderGmail   SmtpProvider = 0
	SmtpProviderOutlook SmtpProvider = 1
)

// SmtpConnectionInfo holds the result of smtp.connect.
type SmtpConnectionInfo struct {
	Provider    SmtpProvider
	Email       string
	DisplayName string
	Connected   bool
}

// ── Telemetry types ──────────────────────────────────────────────────────

// TelemetryAttribute is an OTEL attribute (key-value dimension/label).
type TelemetryAttribute struct {
	Key   string
	Value string
}

// ── Access Log types ─────────────────────────────────────────────────────

// AccessEntry is a single HTTP access log entry (auto-captured by host).
type AccessEntry struct {
	TsUnixMs  uint64
	Method    string
	Path      string
	Status    uint16
	LatencyUs uint64
	IP        string
	UserAgent string
	Referer   string
	UserID    string
	OrgID     string
	BytesIn   uint64
	BytesOut  uint64
}

// PageView is a per-path aggregated page view count.
type PageView struct {
	Path      string
	Count     uint64
	UniqueIPs uint64
}

// QueryStat is a query execution stat (auto-captured).
type QueryStat struct {
	TsUnixMs  uint64
	QueryType string
	QueryText string
	LatencyUs uint64
	Rows      uint64
	Error     string
}

// IPInfo is an observed IP address with first/last seen timestamps.
type IPInfo struct {
	IP           string
	FirstSeenMs  uint64
	LastSeenMs   uint64
	RequestCount uint64
	UserAgent    string
}

// ── Pub/Sub types ─────────────────────────────────────────────────────────

// PublishedMessage is a message returned by PubsubPull (JSON-decoded).
type PublishedMessage struct {
	Seq           uint64            `json:"seq"`
	Topic         string            `json:"topic"`
	PayloadB64    string            `json:"payload_b64"`
	Metadata      map[string]string `json:"metadata"`
	PublishedAtMs uint64            `json:"published_at_ms"`
}

// ── Lock types ────────────────────────────────────────────────────────────

// LockResponse is the result of LockTryLock.
type LockResponse struct {
	Success   bool
	LockToken string
}

// ── Virtual Actor types ───────────────────────────────────────────────────

// ActorID identifies a virtual actor instance.
type ActorID struct {
	ActorType string
	ID        string
}

// ── Identity types ────────────────────────────────────────────────────────

// AddressScheme identifies how an actor is reachable.
type AddressScheme string

const (
	AddressSchemeEmail   AddressScheme = "email"
	AddressSchemeActor   AddressScheme = "actor"
	AddressSchemeWebhook AddressScheme = "webhook"
)

// ActorAddress is a registered actor address.
type ActorAddress struct {
	Address     string        `json:"address"`
	Scheme      AddressScheme `json:"scheme"`
	Nanoid      string        `json:"nanoid"`
	DisplayName string        `json:"display_name,omitempty"`
}

// ToolDescriptor describes a tool/skill an actor exposes.
type ToolDescriptor struct {
	Name            string `json:"name"`
	Description     string `json:"description"`
	InputSchemaJSON string `json:"input_schema_json"`
}

// ActorCard is the unified A2A Agent Card / MCP server manifest.
type ActorCard struct {
	Nanoid           string           `json:"nanoid"`
	Name             string           `json:"name"`
	Description      string           `json:"description"`
	ServiceUserID    string           `json:"service_user_id,omitempty"`
	Addresses        []ActorAddress   `json:"addresses"`
	Tools            []ToolDescriptor `json:"tools"`
	Protocols        []string         `json:"protocols"`
	CapabilitiesJSON string           `json:"capabilities_json,omitempty"`
}

// ── Capability types ──────────────────────────────────────────────────────

// CapabilityStatus is the maturity level of a capability.
type CapabilityStatus string

const (
	CapabilityStatusPlanned     CapabilityStatus = "planned"
	CapabilityStatusDeveloping  CapabilityStatus = "developing"
	CapabilityStatusOperational CapabilityStatus = "operational"
	CapabilityStatusRetired     CapabilityStatus = "retired"
)

// ActorCapability is a business capability an actor provides (CV-1).
type ActorCapability struct {
	ID             string           `json:"id"`
	Name           string           `json:"name"`
	Description    string           `json:"description"`
	Status         CapabilityStatus `json:"status"`
	Phase          string           `json:"phase"`
	ParentID       string           `json:"parent_id,omitempty"`
	Tags           []string         `json:"tags"`
	ActivityIDs    []string         `json:"activity_ids"`
	MeasureNames   []string         `json:"measure_names"`
}

// CapabilityDiscoveryEntry is a (nanoid, capability) pair returned by discover.
type CapabilityDiscoveryEntry struct {
	Nanoid     string          `json:"nanoid"`
	Capability ActorCapability `json:"capability"`
}

// ── Conversation types ────────────────────────────────────────────────────

// ConversationSession is a multi-agent conversation session.
type ConversationSession struct {
	SessionID    string   `json:"sessionId"`
	Topic        string   `json:"topic"`
	Participants []string `json:"participants"`
	Status       string   `json:"status"` // "open" or "closed"
	CreatedAt    string   `json:"createdAt"`
	CreatedBy    string   `json:"createdBy,omitempty"`
}

// ConversationMessage is a message in a conversation session.
type ConversationMessage struct {
	MessageID string  `json:"messageId"`
	SessionID string  `json:"sessionId"`
	From      string  `json:"from"`
	Content   string  `json:"content"`
	ReplyTo   *string `json:"replyTo,omitempty"`
	CreatedAt string  `json:"createdAt"`
}

// ── A2A (Agent-to-Agent) types ────────────────────────────────────────────

// A2ATask is an agent-to-agent task envelope sent as an AT record.
// Collection: com.etzhayyim.a2a.task
type A2ATask struct {
	// Unique task ID (ULID).
	TaskID string `json:"taskId"`
	// Sender actor nanoid.
	From string `json:"from"`
	// Target actor nanoid.
	To string `json:"to"`
	// Task method: "call-tool", "ask", "notify".
	Method string `json:"method"`
	// Tool name (for method=call-tool).
	ToolName string `json:"toolName,omitempty"`
	// JSON-encoded arguments.
	ArgumentsJSON string `json:"argumentsJson,omitempty"`
	// Reply-to AT URI (channel or record URI for the response).
	ReplyTo string `json:"replyTo,omitempty"`
	// ISO 8601 timestamp.
	CreatedAt string `json:"createdAt"`
}

// A2AResult is the result of an A2A task, sent back as an AT record.
// Collection: com.etzhayyim.a2a.result
type A2AResult struct {
	// Task ID this result corresponds to.
	TaskID string `json:"taskId"`
	// Responder actor nanoid.
	From string `json:"from"`
	// Original requester actor nanoid.
	To string `json:"to"`
	// "ok" or "error".
	Status string `json:"status"`
	// JSON-encoded output (for status=ok).
	OutputJSON string `json:"outputJson,omitempty"`
	// Error message (for status=error).
	Error string `json:"error,omitempty"`
	// ISO 8601 timestamp.
	CreatedAt string `json:"createdAt"`
}

// ── Governance types ──────────────────────────────────────────────────────

// RACIRole identifies a RACI responsibility role.
type RACIRole int32

const (
	RACIRoleResponsible RACIRole = 0
	RACIRoleAccountable RACIRole = 1
	RACIRoleConsulted   RACIRole = 2
	RACIRoleInformed    RACIRole = 3
)

// AssigneeKind identifies how a RACI assignee is resolved.
type AssigneeKind int32

const (
	AssigneeOrgRole       AssigneeKind = 0
	AssigneeOrgPermission AssigneeKind = 1
	AssigneeUserID        AssigneeKind = 2
	AssigneeActorID       AssigneeKind = 3
)

// RACIAssignee is a single RACI assignment (role + target).
type RACIAssignee struct {
	Role  RACIRole     `json:"role"`
	Kind  AssigneeKind `json:"kind"`
	Value string       `json:"value"`
}

// DecisionClass classifies the approval weight of a command.
type DecisionClass int32

const (
	DecisionClassA DecisionClass = 0
	DecisionClassB DecisionClass = 1
	DecisionClassC DecisionClass = 2
)

// AssigneeRef is a (kind, value) pair identifying an approver.
type AssigneeRef struct {
	Kind  AssigneeKind `json:"kind"`
	Value string       `json:"value"`
}

// ApprovalRequirement defines the approval gate for a command.
type ApprovalRequirement struct {
	DecisionClass DecisionClass `json:"decision_class"`
	MinApprovers  uint32        `json:"min_approvers"`
	ApproverPool  []AssigneeRef `json:"approver_pool"`
	RiskTier      string        `json:"risk_tier"`
	FormID        *string       `json:"form_id,omitempty"`
}

// CommandPolicy is a governance policy for a single command.
type CommandPolicy struct {
	Command       string               `json:"command"`
	RACI          []RACIAssignee       `json:"raci"`
	Approval      *ApprovalRequirement `json:"approval,omitempty"`
	BPMNTaskID    *string              `json:"bpmn_task_id,omitempty"`
	OCELEventType *string              `json:"ocel_event_type,omitempty"`
}

// GovernanceManifest is the full governance declaration for an app.
type GovernanceManifest struct {
	AppID    string          `json:"app_id"`
	Policies []CommandPolicy `json:"policies"`
}

// PolicyVerdict is the result of a governance policy check.
type PolicyVerdict int32

const (
	PolicyVerdictAllow           PolicyVerdict = 0
	PolicyVerdictPendingApproval PolicyVerdict = 1
	PolicyVerdictDenied          PolicyVerdict = 2
)

// ── OCEL v2 types ────────────────────────────────────────────────────────

// OcelObjectRef is an event→object reference (E2O edge).
type OcelObjectRef struct {
	ObjectID   string
	ObjectType string
	Qualifier  string
	Role       string
}
