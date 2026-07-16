// Compatibility shims for legacy performer API.
// These map old performer types/functions to the kotodama-go equivalents.

package kotodama

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"
)

// ── Arg helpers (legacy performer.StrArg etc.) ──────────────────────────

func StrArg(args map[string]any, key string, def ...string) string {
	if v, ok := args[key]; ok {
		switch vv := v.(type) {
		case string:
			return vv
		case json.Number:
			return vv.String()
		case float64:
			return strconv.FormatFloat(vv, 'f', -1, 64)
		case bool:
			if vv {
				return "true"
			}
			return "false"
		default:
			return fmt.Sprint(v)
		}
	}
	if len(def) > 0 {
		return def[0]
	}
	return ""
}

func IntArg(args map[string]any, key string, def ...int) int {
	if v, ok := args[key]; ok {
		switch vv := v.(type) {
		case float64:
			return int(vv)
		case json.Number:
			n, _ := vv.Int64()
			return int(n)
		case int:
			return vv
		case string:
			n, _ := strconv.Atoi(vv)
			return n
		}
	}
	if len(def) > 0 {
		return def[0]
	}
	return 0
}

func FloatArg(args map[string]any, key string, def ...float64) float64 {
	if v, ok := args[key]; ok {
		switch vv := v.(type) {
		case float64:
			return vv
		case json.Number:
			f, _ := vv.Float64()
			return f
		case string:
			f, _ := strconv.ParseFloat(vv, 64)
			return f
		}
	}
	if len(def) > 0 {
		return def[0]
	}
	return 0
}

func BoolArg(args map[string]any, key string, def ...bool) bool {
	if v, ok := args[key]; ok {
		switch vv := v.(type) {
		case bool:
			return vv
		case string:
			return vv == "true" || vv == "1"
		case float64:
			return vv != 0
		}
	}
	if len(def) > 0 {
		return def[0]
	}
	return false
}

// ── Claims (legacy performer.ClaimsFromArgs) ────────────────────────────

type Claims struct {
	OrgID  string
	UserID string
}

func (c Claims) OrgOrAnon() string {
	if c.OrgID != "" {
		return c.OrgID
	}
	return "anon"
}

func (c Claims) UserOrAnon() string {
	if c.UserID != "" {
		return c.UserID
	}
	return "anon"
}

func ClaimsFromArgs(args map[string]any) Claims {
	return Claims{
		OrgID:  StrArg(args, "org_id"),
		UserID: StrArg(args, "user_id"),
	}
}

// ── Method / Adapter / Runtime (legacy performer routing) ────────────────

type Method struct {
	Name        string
	Description string
	InputSchema map[string]any
	Handler     func(map[string]any) (any, error)
}

type Adapter struct {
	prefix      string
	methods     map[string]func(map[string]any) (any, error)
	MaxBodySize int64
}

func NewAdapter(prefix string) *Adapter {
	return &Adapter{prefix: prefix, methods: make(map[string]func(map[string]any) (any, error))}
}

func (a *Adapter) Register(m Method) {
	a.methods[m.Name] = m.Handler
}

func (a *Adapter) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	path := r.URL.Path
	if path == "/health" || path == "/healthz" || path == "/readyz" {
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"status":"healthy"}`))
		return
	}
	methodName := ""
	if idx := strings.LastIndex(path, "/"); idx >= 0 {
		methodName = path[idx+1:]
	}
	handler, ok := a.methods[methodName]
	if !ok {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(501)
		w.Write([]byte(fmt.Sprintf(`{"code":"unimplemented","message":"unknown method: %s"}`, methodName)))
		return
	}
	var body []byte
	if r.Body != nil {
		body, _ = io.ReadAll(io.LimitReader(r.Body, 64<<20))
	}
	var args map[string]any
	if len(body) > 0 {
		json.Unmarshal(body, &args)
	}
	if args == nil {
		args = make(map[string]any)
	}
	result, err := handler(args)
	if err != nil {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(500)
		errMsg := strings.ReplaceAll(err.Error(), "\"", "\\\"")
		w.Write([]byte(fmt.Sprintf(`{"code":"internal","message":"%s"}`, errMsg)))
		return
	}
	w.Header().Set("Content-Type", "application/json")
	data, _ := json.Marshal(result)
	w.Write(data)
}

func (a *Adapter) AddServiceAlias(alias string) {}

func (a *Adapter) Methods() []Method {
	out := make([]Method, 0, len(a.methods))
	for name, handler := range a.methods {
		out = append(out, Method{Name: name, Handler: handler})
	}
	return out
}

func (a *Adapter) RegisterConversation(handler any) {}

func (app *App) BindToAdapter(a *Adapter) {
	for name, handler := range app.methodMap {
		n := name
		h := handler
		a.methods[n] = func(args map[string]any) (any, error) {
			raw, _ := json.Marshal(args)
			ctx := &AppContext{
				OrgID: StrArg(args, "org_id", "anon"),
				UserID: StrArg(args, "user_id", "anon"),
				appID: app.def.ID,
			}
			result, err := h(ctx, raw)
			if err != nil {
				return nil, err
			}
			var out any
			json.Unmarshal(result, &out)
			return out, nil
		}
	}
}

func (app *App) Register(v any) {
	switch vv := v.(type) {
	case Method:
		app.Command(vv.Name, func(ctx *AppContext, raw []byte) ([]byte, error) {
			var args map[string]any
			if err := json.Unmarshal(raw, &args); err != nil {
				args = map[string]any{}
			}
			result, err := vv.Handler(args)
			if err != nil {
				return nil, err
			}
			return json.Marshal(result)
		})
	case *PerformerConfig:
		if vv == nil {
			return
		}
		if vv.ID != "" {
			app.def.ID = vv.ID
		}
		if vv.Name != "" {
			app.def.Name = vv.Name
		}
		if vv.Description != "" {
			app.def.Description = vv.Description
		}
		for name, handler := range vv.Methods {
			h := handler // capture
			app.methodMap[name] = func(ctx *AppContext, raw []byte) ([]byte, error) {
				pctx := &PerformerContext{
					PerformerID: app.def.ID,
					Claims:      &Claims{OrgID: ctx.OrgID, UserID: ctx.UserID},
				}
				return h(pctx, raw)
			}
		}
		for lex, method := range vv.ATLexiconRoutes {
			app.wRoutes[lex] = method
		}
		if vv.OnActivate != nil {
			// Run OnActivate immediately (host will handle idempotency).
			pctx := &PerformerContext{
				PerformerID: app.def.ID,
				Claims:      &Claims{OrgID: "anon", UserID: "anon"},
			}
			_ = vv.OnActivate(pctx)
		}
	default:
		// ignore unknown types
	}
}

// PerformerMethod is a handler function for legacy performer methods.
type PerformerMethod = func(*PerformerContext, []byte) ([]byte, error)

// Runtime is a type alias for App (legacy performer.Runtime compat).
type Runtime = App

type RuntimeConfig struct {
	CommandPrefix string
	QueryPrefix   string
}

type PerformerConfig struct {
	ID              string
	Name            string
	Description     string
	Methods         map[string]PerformerMethod
	ATLexiconRoutes map[string]string
	OnActivate      func(*PerformerContext) error
}

type PerformerContext struct {
	Config      PerformerConfig
	PerformerID string
	Claims      *Claims
}

type ChatMsg struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

func NewRuntime(cfg RuntimeConfig) *App {
	app := NewApp(AppDef{ID: "compat"})
	return app
}

func EncodeJSONBytes(v any) []byte {
	b, _ := json.Marshal(v)
	return b
}

// ── Evolution types (stubs — evolution runs in kotodama-evolver Rust) ───

type EvolutionConfig struct {
	EvolutionChannelCID string
	ColonyChannelCID    string
}
type EvolutionMetric struct {
	EvolutionID       string
	ActorID           string
	Generation        int
	ObservationWindow int
	FitnessBefore     float64
	FitnessAfter      float64
	Axes              FitnessAxes
}
type EvolutionProposal struct{}
type AgentSnapshot struct {
	Generation int
	Fitness    float64
	Traits     map[string]string
	Learnings  []string
}
type MutationBehavior struct{}
type MutationTraits struct{}
type MutationType int
type MutationWeights struct{}
type FitnessAxes struct{}
type RiskTier int

type AppTeamConfig struct{}

const (
	RiskLow    RiskTier = 0
	RiskMedium RiskTier = 1
)

var (
	EvtEvolutionPropose              = "evolution.propose"
	EvtEvolutionAccept               = "evolution.accept"
	EvtEvolutionReject               = "evolution.reject"
	EvtFormsPending                  = "forms.pending"
	LexiconEvolutionTeamSessionStart = "com.etzhayyim.evolution.teamSessionStart"
)

func DefaultAppTeam(args ...any) AppTeamConfig                              { return AppTeamConfig{} }
func DefaultRollbackPolicy() EvolutionConfig                                { return EvolutionConfig{} }
func EnsureEvolutionSchema()                                                {}
func EnsureEvolutionWorkflows()                                             {}
func RunDailyEvolution(args ...any) error                                   { return nil }
func EvolveWithApproval(args ...any) (any, error)                           { return nil, nil }

func (c *PerformerContext) Context() any { return nil }
func (c *PerformerContext) BPMN() any    { return nil }
func (c *PerformerContext) DMN() any     { return nil }
func (c *PerformerContext) Forms() any   { return nil }
func GetEvolutionSnapshot() (*AgentSnapshot, error)                         { return nil, nil }
func AppendEvolutionMetric(args ...any) error                               { return nil }
func UpsertEvolutionSnapshot(args ...any) error                             { return nil }
func CompleteEvolutionApproval(_ string, _ bool) error                      { return nil }
func RegisterDailyEvolutionReminder(args ...any)                            {}

// ── HTTP compat helpers ──────────────────────────────────────────────

// WriteJSON writes a JSON response (legacy performer.WriteJSON).
func WriteJSON(w http.ResponseWriter, status int, payload any) {
	RespondJSON(w, status, payload)
}

// HandleCORS sets CORS headers and returns true if the request was an OPTIONS preflight.
func HandleCORS(w http.ResponseWriter, r *http.Request) bool {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
	w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, Connect-Protocol-Version, X-etzhayyim-ORG-ID, X-Request-ID")
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusNoContent)
		return true
	}
	return false
}

// NormalizePath trims trailing slashes and lowercases path (legacy performer helper).
func NormalizePath(p string) string {
	p = strings.TrimSpace(p)
	for len(p) > 1 && p[len(p)-1] == '/' {
		p = p[:len(p)-1]
	}
	return p
}

// NowTimestamp returns current time as RFC3339.
func NowTimestamp() string {
	return time.Now().UTC().Format(time.RFC3339)
}

// ── State compat (legacy performer.PerformerState → Cypher) ──────────────

// PerformerState provides scoped state storage backed by Cypher graph.
type PerformerState struct {
	performerID string
}

// ErrKeyNotFound is returned when a graph state key is not found.
var ErrKeyNotFound = fmt.Errorf("key not found")

// GlobalState returns a PerformerState scoped to this performer.
func (c *PerformerContext) GlobalState() *PerformerState {
	return &PerformerState{performerID: c.PerformerID}
}

// SetJSON stores a JSON-serialized value as an ActorState graph node.
func (s *PerformerState) SetJSON(key string, v any) error {
	data, err := json.Marshal(v)
	if err != nil {
		return err
	}
	return CypherExec(
		"MERGE (n:ActorState {performer_id: $pid, key: $key}) SET n.value_json = $val",
		map[string]any{"pid": s.performerID, "key": key, "val": string(data)},
	)
}

// Delete removes an ActorState graph node.
func (s *PerformerState) Delete(key string) error {
	return CypherExec(
		"MATCH (n:ActorState {performer_id: $pid, key: $key}) DELETE n",
		map[string]any{"pid": s.performerID, "key": key},
	)
}

// GetJSON retrieves a JSON value from an ActorState graph node.
func (s *PerformerState) GetJSON(key string, dst any) error {
	rows, err := CypherQueryMap(
		"MATCH (n:ActorState {performer_id: $pid, key: $key}) RETURN n.value_json AS val",
		map[string]any{"pid": s.performerID, "key": key},
	)
	if err != nil {
		return err
	}
	if len(rows) == 0 {
		return ErrKeyNotFound
	}
	val, ok := rows[0]["val"].(string)
	if !ok || val == "" {
		return ErrKeyNotFound
	}
	return json.Unmarshal([]byte(val), dst)
}

// ── OnActivate / Runtime.Invoke stubs ────────────────────────────────────

// OnActivateProvisionATTeam returns an OnActivate func (stub — provisioning runs in host).
func OnActivateProvisionATTeam(args ...any) func(*PerformerContext) error {
	return func(_ *PerformerContext) error { return nil }
}

// Tick is a no-op (legacy performer timer tick).
func (app *App) Tick(ms uint64) {}

// Invoke calls a registered method by name (compat stub).
func (app *App) Invoke(method string, payload []byte, claims *Claims) ([]byte, error) {
	handler, ok := app.methodMap[method]
	if !ok {
		return nil, fmt.Errorf("method not found: %s", method)
	}
	ctx := &AppContext{
		OrgID: "anon", UserID: "anon", ActorID: app.def.ID,
		appID: app.def.ID,
		now:   time.Now().UTC().Format(time.RFC3339),
	}
	if claims != nil {
		if claims.OrgID != "" {
			ctx.OrgID = claims.OrgID
		}
		if claims.UserID != "" {
			ctx.UserID = claims.UserID
		}
	}
	return handler(ctx, payload)
}

// ── Murakumo LLM compat ─────────────────────────────────────────────────

// MurakumoConfig holds configuration for calling the Murakumo LLM API.
type MurakumoConfig struct {
	Model        string
	Endpoint     string
	MaxTokens    int
	Temperature  float64
	SystemPrompt string
	HTTPSender   func(*http.Request) (*http.Response, error)
	Referer      string
	Title        string
}

// RegisterMurakumoConversation is a no-op (legacy compat).
func (a *Adapter) RegisterMurakumoConversation(cfg MurakumoConfig) {}

// DefaultMurakumoModel is the standard model alias.
const DefaultMurakumoModel = "qwen3-vl-8b"

// CallMurakumo calls the Murakumo LLM API (legacy performer helper).
func CallMurakumo(cfg MurakumoConfig, messages []ChatMsg) (string, error) {
	endpoint := cfg.Endpoint
	if endpoint == "" {
		endpoint = "https://murakumo.etzhayyim.com/api/openai/v1/chat/completions"
	}
	model := cfg.Model
	if model == "" {
		model = DefaultMurakumoModel
	}
	maxTokens := cfg.MaxTokens
	if maxTokens == 0 {
		maxTokens = 512
	}
	temp := cfg.Temperature
	if temp == 0 {
		temp = 0.7
	}
	body, _ := json.Marshal(map[string]any{
		"model": model, "messages": messages,
		"max_tokens": maxTokens, "temperature": temp,
	})
	req, err := http.NewRequest(http.MethodPost, endpoint, strings.NewReader(string(body)))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := Send(req)
	if err != nil {
		return "", fmt.Errorf("murakumo: %w", err)
	}
	defer resp.Body.Close()
	raw, _ := readAllBody(resp.Body)
	if resp.StatusCode >= 400 {
		return "", fmt.Errorf("murakumo %d: %s", resp.StatusCode, string(raw))
	}
	var result struct {
		Choices []struct {
			Message struct {
				Content string `json:"content"`
			} `json:"message"`
		} `json:"choices"`
	}
	if json.Unmarshal(raw, &result) != nil || len(result.Choices) == 0 {
		return "", fmt.Errorf("no response")
	}
	return strings.TrimSpace(result.Choices[0].Message.Content), nil
}

func readAllBody(r io.Reader) ([]byte, error) {
	var buf [128 * 1024]byte
	n := 0
	for {
		nn, err := r.Read(buf[n:])
		n += nn
		if err != nil || n >= len(buf) {
			break
		}
	}
	out := make([]byte, n)
	copy(out, buf[:n])
	return out, nil
}

// ── Actor compat (legacy actor.SetHTTPSender) ──

// SetHTTPSender is a no-op (legacy actor shim — outbound HTTP is WIT-provided).
func SetHTTPSender(_ func(*http.Request) (*http.Response, error)) {}

// ── Actor compat (legacy performeractor package) ──

// OutboundEmail represents an email to send via the actor mailbox system.
type OutboundEmail struct {
	FromNanoid string
	ToEmails   []string
	Subject    string
	TextBody   string
	HTMLBody   string
	Metadata   map[string]string
}

// SendEmailEnvelope sends an email envelope via Cypher graph + outbound HTTP.
// Returns the envelope ID. Legacy performeractor.SendEmailEnvelope compat.
func SendEmailEnvelope(email OutboundEmail) (string, error) {
	envelopeID := fmt.Sprintf("env-%d", time.Now().UnixNano())
	metadata, _ := json.Marshal(email.Metadata)
	toList, _ := json.Marshal(email.ToEmails)
	err := CypherExec(
		"CREATE (e:EmailEnvelope {envelope_id: $eid, from_nanoid: $from, to_emails: $to, subject: $subj, text_body: $text, html_body: $html, metadata_json: $meta, status: 'queued', created_at: $ts})",
		map[string]any{
			"eid": envelopeID, "from": email.FromNanoid,
			"to": string(toList), "subj": email.Subject,
			"text": email.TextBody, "html": email.HTMLBody,
			"meta": string(metadata),
			"ts":   time.Now().UTC().Format(time.RFC3339),
		},
	)
	return envelopeID, err
}

// ResolveAddress resolves an actor address to an ActorCard.
// Legacy performeractor.ResolveAddress compat.
func ResolveAddress(address string) (*ActorCard, error) {
	card, found, errMsg := IdentityResolveAddress(address)
	if errMsg != "" {
		return nil, fmt.Errorf("%s", errMsg)
	}
	if !found {
		return nil, fmt.Errorf("actor not found for address: %s", address)
	}
	return card, nil
}

// Register registers an actor identity card in the graph.
// Legacy performeractor.Register compat.
func Register(card ActorCard) error {
	errMsg := IdentityRegister(card)
	if errMsg != "" {
		return fmt.Errorf("%s", errMsg)
	}
	return nil
}

// AddAddress adds an address entry for an actor.
// Legacy performeractor.AddAddress compat.
func AddAddress(nanoid, addrType, displayName string) error {
	return CypherExec(
		"MATCH (a:ActorCard {nanoid: $nanoid}) MERGE (addr:ActorAddress {nanoid: $nanoid, address_type: $type}) SET addr.display_name = $name MERGE (a)-[:HAS_ADDRESS]->(addr)",
		map[string]any{"nanoid": nanoid, "type": addrType, "name": displayName},
	)
}

// ── Helpers ─────────────────────────────────────────────────────────────

func init() {
	_ = strings.TrimSpace // keep import
}
