// Package kotodama — Declaration-First App SDK.
//
// Command() declarations auto-generate identity, capabilities, and governance:
//
//	var app = kotodama.NewApp(kotodama.AppDef{
//	    ID: "cbn8gf7x", Name: "color-by-number",
//	})
//	app.Command("paint_cell", paintCell,
//	    kotodama.AsAgentTool("Paint a cell with a color number"),  // → ActorCard.tools + ActorCapability
//	    kotodama.WithCapabilityTags("game", "coloring"),           // → discovery tags
//	    kotodama.WithWLexicon("paint"),                            // → W Protocol Firehose routing
//	    kotodama.WithSignalEncrypt("canvas_id"),                   // → E2E encryption
//	    kotodama.Responsible(kotodama.AssigneeOrgRole, "player"),  // → RACI governance
//	    kotodama.WithOCELEvent("cell.painted"),                    // → OCEL process mining
//	)
//	app.HandleA2ATask(myA2AHandler)              // handle incoming A2A tasks
//	app.HandleConversationMessage(myConvHandler)  // handle N-agent conversation messages
//	func init() { app.Serve() }
//	func main() {}
//
// Serve() auto-generates from Command declarations:
//   - ActorCard (identity graph) — nanoid, tools, protocols, addresses
//   - ActorCapability (capability graph) — per-command, with tags from WithCapabilityTags + auto-tags from RACI/approval/BPMN/OCEL
//   - GovernanceManifest — RACI, approval, BPMN, OCEL from Responsible/Accountable/RequireApproval/WithBPMNTask/WithOCELEvent
//
// Other agents discover and call via:
//   - A2ADiscoverAndCall(tag, tool, args) — find by capability tag → send task
//   - A2ADiscoverByTool(toolName) — find by tool name
//   - A2ABroadcast(tag, tool, args) — fan-out to all matching agents
//   - StartConversation / Say / Reply — N-agent async discussions
//
// All boilerplate (W Protocol routing, Signal lifecycle, identity/capability/governance auto-publish,
// A2A dispatch, conversation dispatch) is handled by the SDK.
// Evolution is handled by kotodama-evolver (Rust runtime).

package kotodama

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"
)

// ── App Declaration ────────────────────────────────────────────────────────

// AppDef is the top-level declaration for a kotodama WASM application.
type AppDef struct {
	ID          string
	Name        string
	Description string
	Agent       *AgentConfig
}

// AgentConfig configures the AI agent mode for the app.
type AgentConfig struct {
	SystemPrompt string
	Model        string
}

// AppContext is the request-scoped context passed to every command/query handler.
type AppContext struct {
	OrgID     string
	UserID    string
	ActorID   string
	ChannelID string

	appID string
	now   string
}

// RLSMeta returns the standard RLS columns (org_id, user_id, actor_id, timestamps).
func (c *AppContext) RLSMeta() RLSMeta {
	now := c.now
	if now == "" {
		now = time.Now().UTC().Format(time.RFC3339)
	}
	return RLSMeta{
		OrgID: c.OrgID, UserID: c.UserID, ActorID: c.ActorID,
		CreatedAt: now, UpdatedAt: now,
	}
}

// RLSMeta is the standard Row-Level Security metadata for all cypher graph tables.
type RLSMeta struct {
	OrgID     string `json:"org_id"`
	UserID    string `json:"user_id"`
	ActorID   string `json:"actor_id"`
	CreatedAt string `json:"created_at"`
	UpdatedAt string `json:"updated_at"`
}

func (m RLSMeta) AsMap() map[string]any {
	return map[string]any{
		"org_id": m.OrgID, "user_id": m.UserID, "actor_id": m.ActorID,
		"created_at": m.CreatedAt, "updated_at": m.UpdatedAt,
	}
}

// ── Command/Query Options ──────────────────────────────────────────────────

type commandEntry struct {
	name             string
	handler          func(*AppContext, []byte) ([]byte, error)
	lexiconSuffix    string
	signalGroupField string
	agentToolDesc    string
	capabilityTags   []string
	capabilityPhase  string
	raci             []RACIAssignee
	approval         *ApprovalRequirement
	bpmnTaskID       string
	ocelEventType    string
}

type queryEntry struct {
	name    string
	handler func(*AppContext, []byte) ([]byte, error)
}

type CommandOption func(*commandEntry)
type QueryOption func(*queryEntry)

func WithATLexicon(suffix string) CommandOption {
	return func(e *commandEntry) { e.lexiconSuffix = suffix }
}

func WithWLexicon(suffix string) CommandOption {
	return func(e *commandEntry) { e.lexiconSuffix = suffix }
}

func WithSignalEncrypt(groupIDField string) CommandOption {
	return func(e *commandEntry) { e.signalGroupField = groupIDField }
}

func AsAgentTool(description string) CommandOption {
	return func(e *commandEntry) { e.agentToolDesc = description }
}

// WithCapabilityTags sets discovery tags for this command's auto-declared capability.
// Tags enable other agents to discover this command via A2ADiscoverAndCall(tag, ...).
func WithCapabilityTags(tags ...string) CommandOption {
	return func(e *commandEntry) { e.capabilityTags = append(e.capabilityTags, tags...) }
}

// WithCapabilityPhase sets the capability timeline phase (default: "current").
// Values: "current", "near-term", "mid-term", "far-term".
func WithCapabilityPhase(phase string) CommandOption {
	return func(e *commandEntry) { e.capabilityPhase = phase }
}

// Responsible adds a RACI Responsible assignee to the command.
func Responsible(kind AssigneeKind, value string) CommandOption {
	return func(e *commandEntry) {
		e.raci = append(e.raci, RACIAssignee{Role: RACIRoleResponsible, Kind: kind, Value: value})
	}
}

// Accountable adds a RACI Accountable assignee to the command.
func Accountable(kind AssigneeKind, value string) CommandOption {
	return func(e *commandEntry) {
		e.raci = append(e.raci, RACIAssignee{Role: RACIRoleAccountable, Kind: kind, Value: value})
	}
}

// Consulted adds a RACI Consulted assignee to the command.
func Consulted(kind AssigneeKind, value string) CommandOption {
	return func(e *commandEntry) {
		e.raci = append(e.raci, RACIAssignee{Role: RACIRoleConsulted, Kind: kind, Value: value})
	}
}

// Informed adds a RACI Informed assignee to the command.
func Informed(kind AssigneeKind, value string) CommandOption {
	return func(e *commandEntry) {
		e.raci = append(e.raci, RACIAssignee{Role: RACIRoleInformed, Kind: kind, Value: value})
	}
}

// RequireApproval sets the approval requirement for the command.
func RequireApproval(class DecisionClass, minApprovers uint32, riskTier string, approvers ...AssigneeRef) CommandOption {
	return func(e *commandEntry) {
		e.approval = &ApprovalRequirement{
			DecisionClass: class,
			MinApprovers:  minApprovers,
			ApproverPool:  approvers,
			RiskTier:      riskTier,
		}
	}
}

// WithApprovalForm sets the form ID for the approval UI.
func WithApprovalForm(formID string) CommandOption {
	return func(e *commandEntry) {
		if e.approval != nil {
			e.approval.FormID = &formID
		}
	}
}

// WithBPMNTask links the command to a BPMN task ID for process tracking.
func WithBPMNTask(taskID string) CommandOption {
	return func(e *commandEntry) { e.bpmnTaskID = taskID }
}

// WithOCELEvent sets the OCEL event type emitted after command execution.
func WithOCELEvent(eventType string) CommandOption {
	return func(e *commandEntry) { e.ocelEventType = eventType }
}

func WithCypher() QueryOption { return func(*queryEntry) {} }

// ── App ───────────────────────────────────────────────────────────────────

type App struct {
	def         AppDef
	commands    []commandEntry
	queries     []queryEntry
	wRoutes     map[string]string // W Protocol collection → command name
	methodMap   map[string]func(*AppContext, []byte) ([]byte, error)
}

func NewApp(def AppDef) *App {
	return &App{
		def:       def,
		wRoutes:   make(map[string]string),
		methodMap: make(map[string]func(*AppContext, []byte) ([]byte, error)),
	}
}

func (a *App) Command(name string, handler func(*AppContext, []byte) ([]byte, error), opts ...CommandOption) *App {
	e := commandEntry{name: name, handler: handler}
	for _, o := range opts {
		o(&e)
	}
	a.commands = append(a.commands, e)
	a.methodMap[name] = handler
	if e.lexiconSuffix != "" {
		// W Protocol collection routing: com.etzhayyim.w.{suffix} → command name
		a.wRoutes["com.etzhayyim.w."+e.lexiconSuffix] = name
	}
	return a
}

func (a *App) Query(name string, handler func(*AppContext, []byte) ([]byte, error), opts ...QueryOption) *App {
	e := queryEntry{name: name, handler: handler}
	for _, o := range opts {
		o(&e)
	}
	a.queries = append(a.queries, e)
	a.methodMap[name] = handler
	return a
}

func (a *App) OnDailyEvolution(fn func(*AppContext) map[string]float64) *App {
	return a // no-op — evolution is handled by kotodama-evolver (Rust runtime)
}

// Serve wires up HTTP handler and W Protocol commit handler via kotodama WIT exports.
// Also auto-registers this actor's identity card and capabilities if configured.
func (a *App) Serve() {
	if a.def.Agent != nil {
		a.registerAgentTools()
	}
	a.autoRegisterIdentity()
	a.registerGovernanceManifest()
	Handle(http.HandlerFunc(a.handleHTTP))
	HandleWCommit(a.handleWCommit)
}

// autoRegisterIdentity publishes this actor's card and capabilities to the graph.
// Called automatically by Serve(). Uses AppDef + registered commands as tool descriptors.
func (a *App) autoRegisterIdentity() {
	nanoid := configGetOrDefault("PERFORMER_ID", configGetOrDefault("APP_NANOID", a.def.ID))
	if nanoid == "" {
		return
	}

	// Build tool descriptors from registered commands.
	tools := make([]ToolDescriptor, 0, len(a.commands))
	for _, cmd := range a.commands {
		desc := cmd.agentToolDesc
		if desc == "" {
			desc = cmd.name
		}
		tools = append(tools, ToolDescriptor{
			Name:            cmd.name,
			Description:     desc,
			InputSchemaJSON: `{"type":"object"}`,
		})
	}

	protocols := []string{"a2a", "connect-grpc", "w-protocol"}

	addresses := []ActorAddress{
		{Address: nanoid + ".etzhayyim.com", Scheme: AddressSchemeActor, Nanoid: nanoid},
		{Address: nanoid + "@etzhayyim.com", Scheme: AddressSchemeEmail, Nanoid: nanoid},
	}

	card := ActorCard{
		Nanoid:      nanoid,
		Name:        a.def.Name,
		Description: a.def.Description,
		Protocols:   protocols,
		Tools:       tools,
		Addresses:   addresses,
	}

	if errMsg := IdentityRegister(card); errMsg != "" {
		// Non-fatal — log and continue.
		fmt.Println("kotodama: auto-register identity failed:", errMsg)
	}

	// Auto-declare capabilities from commands that have AsAgentTool or WithCapabilityTags.
	a.autoRegisterCapabilities()
}

// autoRegisterCapabilities declares capabilities for each command that has
// agent tool description, capability tags, RACI, or approval requirements.
// Capability ID = "{appID}.{commandName}". Tags, RACI, and approval metadata
// are derived from the CommandOption declarations.
func (a *App) autoRegisterCapabilities() {
	for _, cmd := range a.commands {
		// Skip commands that have no discoverable metadata.
		if cmd.agentToolDesc == "" && len(cmd.capabilityTags) == 0 && len(cmd.raci) == 0 {
			continue
		}

		capID := a.def.ID + "." + cmd.name

		description := cmd.agentToolDesc
		if description == "" {
			description = cmd.name
		}

		phase := cmd.capabilityPhase
		if phase == "" {
			phase = "current"
		}

		// Merge tags: explicit tags + derive from RACI roles + BPMN/OCEL.
		tags := make([]string, 0, len(cmd.capabilityTags)+4)
		tags = append(tags, cmd.capabilityTags...)
		if len(cmd.raci) > 0 {
			tags = append(tags, "governed")
		}
		if cmd.approval != nil {
			tags = append(tags, "approval-required")
		}
		if cmd.bpmnTaskID != "" {
			tags = append(tags, "bpmn")
		}
		if cmd.ocelEventType != "" {
			tags = append(tags, "ocel")
		}

		// Build measure names from governance metadata.
		var measureNames []string
		if cmd.approval != nil {
			measureNames = append(measureNames, "approval_latency", "approval_rate")
		}
		if cmd.ocelEventType != "" {
			measureNames = append(measureNames, "event_throughput")
		}

		cap := ActorCapability{
			ID:           capID,
			Name:         cmd.name,
			Description:  description,
			Status:       CapabilityStatusOperational,
			Phase:        phase,
			Tags:         tags,
			ActivityIDs:  []string{},
			MeasureNames: measureNames,
		}

		if errMsg := CapabilityDeclare(cap); errMsg != "" {
			fmt.Println("kotodama: auto-declare capability failed:", cmd.name, errMsg)
		}
	}
}

// registerGovernanceManifest builds a GovernanceManifest from commands that
// have governance options (RACI, approval, BPMN, OCEL) and registers it with
// the host. Commands without governance options are skipped (backward compatible).
func (a *App) registerGovernanceManifest() {
	var policies []CommandPolicy
	for _, cmd := range a.commands {
		if len(cmd.raci) == 0 && cmd.approval == nil && cmd.bpmnTaskID == "" && cmd.ocelEventType == "" {
			continue
		}
		p := CommandPolicy{
			Command: cmd.name,
			RACI:    cmd.raci,
		}
		if cmd.approval != nil {
			p.Approval = cmd.approval
		}
		if cmd.bpmnTaskID != "" {
			p.BPMNTaskID = &cmd.bpmnTaskID
		}
		if cmd.ocelEventType != "" {
			p.OCELEventType = &cmd.ocelEventType
		}
		policies = append(policies, p)
	}
	if len(policies) == 0 {
		return
	}

	manifest := GovernanceManifest{
		AppID:    a.def.ID,
		Policies: policies,
	}
	manifestJSON, err := json.Marshal(manifest)
	if err != nil {
		fmt.Println("kotodama: governance manifest marshal:", err)
		return
	}
	if errMsg := GovernanceRegisterManifest(string(manifestJSON)); errMsg != "" {
		fmt.Println("kotodama: governance register manifest:", errMsg)
	}
}

func (a *App) handleHTTP(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path == "/health" || r.URL.Path == "/healthz" {
		RespondJSON(w, http.StatusOK, map[string]any{"status": "ok", "app": a.def.ID})
		return
	}

	// Connect gRPC dispatch: POST /api/grpc/{service}/{method}
	if r.Method == http.MethodPost && strings.HasPrefix(r.URL.Path, "/api/grpc/") {
		a.handleGRPC(w, r)
		return
	}

	// Timer callback dispatch: POST /{MethodName} (bare path from kotodama runtime)
	if r.Method == http.MethodPost {
		methodName := strings.TrimPrefix(r.URL.Path, "/")
		if handler, ok := a.methodMap[methodName]; ok {
			ctx := a.resolveContext(r)
			var payload []byte
			if r.Body != nil {
				payload, _ = readBody(r)
			}
			result, err := handler(ctx, payload)
			if err != nil {
				RespondJSON(w, http.StatusInternalServerError, map[string]any{"error": err.Error()})
				return
			}
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			w.Write(result)
			return
		}
	}

	RespondJSON(w, http.StatusNotFound, map[string]any{"error": "not found"})
}

func (a *App) handleGRPC(w http.ResponseWriter, r *http.Request) {
	// Extract method name from path: /api/grpc/service.v1.Service/MethodName
	path := strings.TrimPrefix(r.URL.Path, "/api/grpc/")
	parts := strings.SplitN(path, "/", 2)
	methodName := ""
	if len(parts) == 2 {
		methodName = parts[1]
	} else if len(parts) == 1 {
		methodName = parts[0]
	}

	// Resolve authn context via WIT host.
	ctx := a.resolveContext(r)

	handler, ok := a.methodMap[methodName]
	if !ok {
		// Try snake_case conversion (PascalCase → snake_case)
		handler, ok = a.methodMap[toSnake(methodName)]
	}
	if !ok {
		// Try kebab-case conversion (PascalCase → kebab-case)
		handler, ok = a.methodMap[toKebab(methodName)]
	}
	if !ok {
		RespondJSON(w, http.StatusNotFound, map[string]any{"error": "unknown method: " + methodName})
		return
	}

	var payload []byte
	if r.Body != nil {
		payload, _ = readBody(r)
	}

	result, err := handler(ctx, payload)
	if err != nil {
		RespondJSON(w, http.StatusInternalServerError, map[string]any{"error": err.Error()})
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	w.Write(result)
}

func (a *App) handleWCommit(commit WCommit) error {
	// A2A task dispatch — intercept com.etzhayyim.a2a.task before collection filtering.
	if commit.Collection == A2ACollectionTask && commit.Action == "create" {
		return a.dispatchA2ACommit(commit)
	}

	// Conversation message dispatch — intercept com.etzhayyim.a2a.message.
	if commit.Collection == "com.etzhayyim.a2a.message" && commit.Action == "create" {
		return a.dispatchConversationCommit(commit)
	}

	// W Protocol collection routing.
	methodName, ok := a.wRoutes[commit.Collection]
	if !ok {
		return nil
	}

	handler, ok := a.methodMap[methodName]
	if !ok {
		return nil
	}

	// W Protocol host delivers record data via conversation WIT — dispatch directly.
	// Retrieve the record via the conversation history for this commit's rkey.
	histJSON, errMsg := ConversationGetHistory(commit.Rkey, 0, 1)
	if errMsg != "" {
		return fmt.Errorf("w-handler getHistory %s/%s: %s", commit.Collection, commit.Rkey, errMsg)
	}

	ctx := &AppContext{
		OrgID: "anon", UserID: "anon", ActorID: commit.Repo,
		appID: a.def.ID,
		now:   time.Now().UTC().Format(time.RFC3339),
	}

	_, err := handler(ctx, []byte(histJSON))
	return err
}

func (a *App) dispatchConversationCommit(commit WCommit) error {
	if conversationMessageHandler == nil {
		return nil
	}

	// W Protocol host delivers conversation messages via the conversation WIT.
	// Retrieve the message from conversation history using the commit's rkey as session ID.
	histJSON, errMsg := ConversationGetHistory(commit.Rkey, 0, 1)
	if errMsg != "" {
		return fmt.Errorf("conversation: get history %s/%s: %s", commit.Collection, commit.Rkey, errMsg)
	}

	return a.dispatchConversationMessage(commit, []byte(histJSON))
}

func (a *App) dispatchA2ACommit(commit WCommit) error {
	if a2aHandler == nil {
		return nil
	}

	// W Protocol host delivers A2A tasks via the conversation WIT.
	// Retrieve the task record from conversation history using the commit's rkey as session ID.
	histJSON, errMsg := ConversationGetHistory(commit.Rkey, 0, 1)
	if errMsg != "" {
		return fmt.Errorf("a2a: get history %s/%s: %s", commit.Collection, commit.Rkey, errMsg)
	}

	return a.dispatchA2ATask(commit, []byte(histJSON))
}

func (a *App) resolveContext(r *http.Request) *AppContext {
	authHeader := r.Header.Get("Authorization")
	orgHeader := r.Header.Get("X-etzhayyim-ORG-ID")
	reqIDHeader := r.Header.Get("X-Request-ID")

	orgID := "anon"
	userID := "anon"

	if authHeader != "" {
		authnCtx, errMsg := AuthnResolveContext(authHeader, orgHeader, reqIDHeader)
		if errMsg == "" && authnCtx != nil {
			if authnCtx.TargetOrgID != "" {
				orgID = authnCtx.TargetOrgID
			}
			if authnCtx.Claims.UserID != "" {
				userID = authnCtx.Claims.UserID
			}
			if orgID == "anon" && authnCtx.Claims.OrgID != "" {
				orgID = authnCtx.Claims.OrgID
			}
		}
	}

	actorID := userID
	if actorID == "anon" {
		actorID = a.def.ID
	}

	return &AppContext{
		OrgID: orgID, UserID: userID, ActorID: actorID,
		appID: a.def.ID,
		now:   time.Now().UTC().Format(time.RFC3339),
	}
}

func (a *App) registerAgentTools() {
	tools := make([]AgentToolDef, 0, len(a.commands))
	for _, cmd := range a.commands {
		if cmd.agentToolDesc == "" {
			continue
		}
		tools = append(tools, AgentToolDef{
			Name:            cmd.name,
			Description:     cmd.agentToolDesc,
			InputSchemaJSON: `{"type":"object","properties":{}}`,
		})
	}
	if len(tools) > 0 {
		AgentRegisterTools(tools)
	}
}

// ── helpers ────────────────────────────────────────────────────────────────

func configGetOrDefault(key, defaultVal string) string {
	if v, ok := ConfigGet(key); ok && v != "" {
		return v
	}
	return defaultVal
}

func toSnake(s string) string {
	var buf strings.Builder
	for i, r := range s {
		if r >= 'A' && r <= 'Z' {
			if i > 0 {
				buf.WriteByte('_')
			}
			buf.WriteRune(r + 32)
		} else {
			buf.WriteRune(r)
		}
	}
	return buf.String()
}

func toKebab(s string) string {
	var buf strings.Builder
	for i, r := range s {
		if r >= 'A' && r <= 'Z' {
			if i > 0 {
				buf.WriteByte('-')
			}
			buf.WriteRune(r + 32)
		} else {
			buf.WriteRune(r)
		}
	}
	return buf.String()
}

func readBody(r *http.Request) ([]byte, error) {
	defer r.Body.Close()
	var buf [64 * 1024]byte
	n := 0
	for {
		nn, err := r.Body.Read(buf[n:])
		n += nn
		if err != nil {
			break
		}
		if n >= len(buf) {
			break
		}
	}
	out := make([]byte, n)
	copy(out, buf[:n])
	return out, nil
}
