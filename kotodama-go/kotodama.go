// Package kotodama provides WIT Component Model bindings for kotodama:{core,auth,messaging,storage,agent,workflow,observability,forms}@1.0.0.
// Both TinyGo WASM (build tag: tinygo) and native Go (!tinygo) modes are supported.
package kotodama

import (
	"encoding/json"
	"net/http"
)

// ── authn/clerk types (JSON-encoded by host) ──────────────────────────────

// IdentityClaims holds normalized Clerk JWT claims.
// JSON fields match Rust IdentityClaims serialization in kotodama-engine.
type IdentityClaims struct {
	UserID            string   `json:"user_id"`
	SessionID         string   `json:"session_id"`
	OrgID             string   `json:"org_id,omitempty"`
	OrgRole           string   `json:"org_role,omitempty"`
	OrgPermissions    []string `json:"org_permissions"`
	IssuedAtMs        uint64   `json:"issued_at_ms"`
	ExpiresAtMs       uint64   `json:"expires_at_ms"`
	Issuer            string   `json:"issuer"`
	AuthorizedParties []string `json:"authorized_parties"`
	Email             string   `json:"email,omitempty"`
}

// AuthnContext is the request-level authentication context.
type AuthnContext struct {
	Claims      IdentityClaims `json:"claims"`
	TargetOrgID string         `json:"target_org_id,omitempty"`
	RequestID   string         `json:"request_id,omitempty"`
}


// WCommit mirrors the WIT w-handler.commit record.
type WCommit struct {
	Seq        int64
	Repo       string
	Collection string
	Rkey       string
	Action     string
	Cid        *string // option<string>
	Ts         string
}

// registeredHandler is the HTTP handler registered by Handle().
var registeredHandler http.Handler

// registeredWHandler is the W Protocol commit handler registered by HandleWCommit().
var registeredWHandler func(WCommit) error

// Handle registers fn as the inbound HTTP handler for this component.
// API mirrors kotodama:core/http-handler#handle (WIT Component Model).
//
// Call in init() exactly once:
//
//	func init() {
//	    kotodama.Handle(func(w http.ResponseWriter, r *http.Request) {
//	        // handler
//	    })
//	}
func Handle(fn http.HandlerFunc) {
	registeredHandler = fn
}

// HandleWCommit registers fn as the W Protocol commit handler.
// Called by the kotodama host for each commit matching the configured collections.
//
//	func init() {
//	    kotodama.HandleWCommit(func(c kotodama.WCommit) error {
//	        // process commit
//	        return nil
//	    })
//	}
func HandleWCommit(fn func(WCommit) error) {
	registeredWHandler = fn
}

// Handler returns the registered HTTP handler. Used by native-mode Serve().
func Handler() http.Handler {
	return registeredHandler
}

// Send performs an outbound HTTP request.
// In TinyGo WASM mode: routes through kotodama:core/outbound-http WIT import
// via wasmOutboundTransport installed as DefaultTransport.
// In native mode: uses net/http.DefaultClient directly.
func Send(req *http.Request) (*http.Response, error) {
	return http.DefaultClient.Do(req)
}

// installWASMTransport sets up the WIT outbound-http transport for TinyGo WASM.
// Called from imports.go init() in tinygo builds only.
func installWASMTransport(rt http.RoundTripper) {
	http.DefaultClient = &http.Client{Transport: rt}
}

// RespondJSON writes a JSON response with the given status code.
// Sets Content-Type: application/json. Falls back to 500 on marshal error.
func RespondJSON(w http.ResponseWriter, status int, payload any) {
	body, err := json.Marshal(payload)
	if err != nil {
		body = []byte(`{"code":"internal","message":"json marshal failed"}`)
		status = 500
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	w.Write(body)
}
