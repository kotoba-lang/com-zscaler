//go:build !tinygo

package kotodama

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
)

// ErrNotAvailable is returned by host functions when running outside of kotodama (native mode).
var ErrNotAvailable = errors.New("kotodama: host function not available in native mode")

// ErrCypherNotAvailable is returned by CypherQuery when running outside of kotodama (native mode).
var ErrCypherNotAvailable = ErrNotAvailable

// ── Log stubs ─────────────────────────────────────────────────────────────

func LogAppend(_, _ string, _ []byte) (uint64, string) { return 0, "" }

// CypherQuery is a no-op stub in native mode.
func CypherQuery(_ string, _ [][2]string) (CypherResult, string) {
	return CypherResult{}, ErrCypherNotAvailable.Error()
}

// CypherBatchQuery is a no-op stub in native mode.
func CypherBatchQuery(_ []BatchStatement) ([]CypherResult, string) {
	return nil, ErrCypherNotAvailable.Error()
}

// Serve starts a net/http server using the registered HTTP handler.
// Address is read from SPIN_HTTP_LISTEN_ADDR or PORT env vars (default :80).
func Serve() {
	if err := ServeAt(listenAddr()); err != nil {
		log.Fatalf("kotodama: serve: %v", err)
	}
}

// ServeAt starts a net/http server on the given address.
func ServeAt(addr string) error {
	h := registeredHandler
	if h == nil {
		return fmt.Errorf("kotodama: no handler registered — call Handle() in init()")
	}
	fmt.Printf("kotodama: listening on %s\n", addr)
	return http.ListenAndServe(addr, h)
}

// ── Clerk stubs ───────────────────────────────────────────────────────────

func ClerkVerifyToken(_ string) ([]byte, string)                           { return nil, ErrNotAvailable.Error() }
func ClerkVerifyTokenWithAZP(_, _ string) ([]byte, string)                 { return nil, ErrNotAvailable.Error() }
func ClerkAuthorize(_, _, _ string) ([]byte, string)                       { return nil, ErrNotAvailable.Error() }
func ClerkGetUser(_ string) ([]byte, string)                               { return nil, ErrNotAvailable.Error() }
func ClerkGetOrganization(_ string) ([]byte, string)                       { return nil, ErrNotAvailable.Error() }
func ClerkGetSession(_ string) ([]byte, string)                            { return nil, ErrNotAvailable.Error() }
func ClerkCheckPermission(_, _, _ string) (bool, string)                   { return false, ErrNotAvailable.Error() }
func ClerkCheckRole(_, _, _ string) (bool, string)                         { return false, ErrNotAvailable.Error() }

// ── Authn stubs ───────────────────────────────────────────────────────────

func AuthnVerifyToken(_ string) (*AuthnContext, string)                        { return nil, ErrNotAvailable.Error() }
func AuthnResolveContext(_, _, _ string) (*AuthnContext, string)                { return nil, ErrNotAvailable.Error() }
func AuthnEnsureActiveSession(_ string) string                                 { return "" }

// ── Authz stubs ───────────────────────────────────────────────────────────

// AuthzEnforce always allows in native mode (facilitates local testing).
func AuthzEnforce(_, _ string, _, _, _ []string) string { return "" }

// ── CDN stubs ─────────────────────────────────────────────────────────────

func CdnUpload(_, _ string, _ []byte, _ string) (string, string) { return "", ErrNotAvailable.Error() }
func CdnFetchUpload(_, _, _, _ string) (string, string)          { return "", ErrNotAvailable.Error() }
func CdnDelete(_, _ string) string                               { return ErrNotAvailable.Error() }
func CdnPublicURL(subdomain, path string) string {
	return "https://" + subdomain + ".etzhayyim.com/" + path
}
func CdnUploadImage(_, _ string, _ []byte, _ ImageUploadOptions) (string, string) {
	return "", ErrNotAvailable.Error()
}

// ── Static Site stubs ─────────────────────────────────────────────────────

func StaticPut(_ string, _ []byte, _ string) (uint64, string) { return 0, ErrNotAvailable.Error() }
func StaticDelete(_ string) string                             { return ErrNotAvailable.Error() }

// ── Signal stubs ──────────────────────────────────────────────────────────

func SignalGenerateIdentity() ([]byte, string)                         { return nil, ErrNotAvailable.Error() }
func SignalGenerateSignedPrekey(_ []byte, _ uint32) ([]byte, string)   { return nil, ErrNotAvailable.Error() }
func SignalGenerateOneTimePrekey(_ uint32) ([]byte, string)            { return nil, ErrNotAvailable.Error() }
func SignalBuildPreKeyBundle(_, _ []byte, _ []byte) ([]byte, string)   { return nil, ErrNotAvailable.Error() }
func SignalX3DHInitiate(_, _ []byte) ([]byte, string)                  { return nil, ErrNotAvailable.Error() }
func SignalX3DHRespond(_, _, _ []byte, _ []byte) ([]byte, string)      { return nil, ErrNotAvailable.Error() }
func SignalRatchetInitSender(_, _ []byte) ([]byte, string)             { return nil, ErrNotAvailable.Error() }
func SignalRatchetInitReceiver(_, _ []byte) ([]byte, string)           { return nil, ErrNotAvailable.Error() }
func SignalRatchetEncrypt(_, _ []byte) ([]byte, string)                { return nil, ErrNotAvailable.Error() }
func SignalRatchetDecrypt(_, _ []byte) ([]byte, string)                { return nil, ErrNotAvailable.Error() }
func SignalGroupInitSender(_, _ string) ([]byte, string)               { return nil, ErrNotAvailable.Error() }
func SignalGroupProcessDistribution(_, _ []byte) ([]byte, string)      { return nil, ErrNotAvailable.Error() }
func SignalGroupEncrypt(_, _ []byte) ([]byte, string)                  { return nil, ErrNotAvailable.Error() }
func SignalGroupDecrypt(_, _ []byte) ([]byte, string)                  { return nil, ErrNotAvailable.Error() }

// ── IPFS stubs ────────────────────────────────────────────────────────────

func IpfsPublish(_ []byte, _ string) (string, string)    { return "", ErrNotAvailable.Error() }
func IpfsPublishURL(_ []byte, _ string) (string, string) { return "", ErrNotAvailable.Error() }
func IpfsGatewayURL(_ string) string                     { return "" }

// ── Storage stubs ─────────────────────────────────────────────────────────

func StoragePutObject(_, _ string, _ []byte, _ string) (string, string) {
	return "", ErrNotAvailable.Error()
}
func StorageGetObject(_, _ string) ([]byte, string) { return nil, ErrNotAvailable.Error() }
func StorageDeleteObject(_, _ string) string        { return ErrNotAvailable.Error() }

// ── signal-session stubs ──────────────────────────────────────────────────

func SignalSessionGroupGetOrCreate(_ string, _ []string) ([]byte, []byte, string) {
	return nil, nil, ""
}
func SignalSessionGroupEncrypt(_ string, _ []byte) ([]byte, string) { return nil, "" }
func SignalSessionGroupDecrypt(_ string, _ []byte, _ string) ([]byte, string) {
	return nil, ""
}
func SignalSessionGroupAddMember(_, _ string) ([]byte, string) { return nil, "" }

// ── agent stubs ───────────────────────────────────────────────────────────

func AgentRegisterTools(_ []AgentToolDef) {}
func AgentChat(_, _ string) (string, string) {
	return "", ErrNotAvailable.Error()
}
func AgentInvokeTool(_, _ string) (string, string) {
	return "", ErrNotAvailable.Error()
}
func AgentConverse(_ []Message, _ ChatOptions) (ChatResponse, string) {
	return ChatResponse{}, ErrNotAvailable.Error()
}
func AgentRoute(_ string) ([]RouteMatch, string) {
	return nil, ErrNotAvailable.Error()
}
func AgentReact(_ string, _ ReactOptions) (ReactResult, string) {
	return ReactResult{}, ErrNotAvailable.Error()
}

// ── activity-parallel stubs ──────────────────────────────────────────────

func ActivitySpawnParallel(_ []ActivityParallelItem) (string, string) {
	return "", ErrNotAvailable.Error()
}
func ActivityAwaitAll(_ string, _ uint64) ([]ParallelActivityResult, string) {
	return nil, ErrNotAvailable.Error()
}

// ── workflow stubs (v0.2.0) ──────────────────────────────────────────────

func WorkflowGet(_ string) (WorkflowInfo, string)     { return WorkflowInfo{}, ErrNotAvailable.Error() }
func WorkflowPause(_ string) string                    { return ErrNotAvailable.Error() }
func WorkflowResume(_ string) string                   { return ErrNotAvailable.Error() }
func WorkflowTerminate(_ string) string                { return ErrNotAvailable.Error() }
func WorkflowPurge(_ string) string                    { return ErrNotAvailable.Error() }
func WorkflowRaiseEvent(_, _ string, _ []byte) string  { return ErrNotAvailable.Error() }
func WorkflowCreateTimer(_, _ string, _ uint64) string { return ErrNotAvailable.Error() }

// ── crypto stubs (v0.2.0) ────────────────────────────────────────────────

func CryptoSHA256(data []byte) ([]byte, string) {
	h := sha256.Sum256(data)
	return h[:], ""
}
func CryptoSHA256Hex(data []byte) string {
	h := sha256.Sum256(data)
	return hex.EncodeToString(h[:])
}

// ── reminder stubs (v0.2.0) ──────────────────────────────────────────────

func ReminderGet(_ string) (*ReminderEntry, bool, string) { return nil, false, ErrNotAvailable.Error() }

// ── timer stubs ──────────────────────────────────────────────────────────

func TimerRegister(_ TimerConfig) string  { return ErrNotAvailable.Error() }
func TimerUnregister(_ string) string     { return ErrNotAvailable.Error() }

// ── SMTP stubs ───────────────────────────────────────────────────────────

func SmtpConnect(_ SmtpProvider, _, _, _, _ string) (SmtpConnectionInfo, string) {
	return SmtpConnectionInfo{}, ErrNotAvailable.Error()
}
func SmtpDisconnect(_ SmtpProvider, _, _ string) string { return ErrNotAvailable.Error() }
func SmtpStatus(_ SmtpProvider, _, _ string) ([]byte, string) {
	return nil, ErrNotAvailable.Error()
}
func SmtpSendTransactional(_, _ string, _ []string, _, _, _ string) (string, string) {
	return "", ErrNotAvailable.Error()
}

// ── Telemetry stubs ──────────────────────────────────────────────────────

func TelemetryCounterAdd(_ string, _ float64, _ []TelemetryAttribute) {}
func TelemetryGaugeSet(_ string, _ float64, _ []TelemetryAttribute)  {}
func TelemetryHistogramRecord(_ string, _ float64, _ []TelemetryAttribute) {}

// ── Access Log stubs ─────────────────────────────────────────────────────

func AccessLogListEntries(_, _ uint32) []AccessEntry       { return nil }
func AccessLogPageViews(_, _ uint64) []PageView            { return nil }
func AccessLogListQueryStats(_, _ uint32) []QueryStat      { return nil }
func AccessLogListIPs(_, _ uint32) []IPInfo                { return nil }
func AccessLogTotalRequests() uint64                       { return 0 }

// ── OCEL v2 stubs ───────────────────────────────────────────────────────

func OcelEmitEvent(_ string, _ string, _ []OcelObjectRef) (string, string) {
	return "", ErrNotAvailable.Error()
}
func OcelUpsertObject(_, _, _ string) string    { return ErrNotAvailable.Error() }
func OcelAddObjectEdge(_, _, _, _ string) string { return ErrNotAvailable.Error() }
func OcelExportJSON() (string, string)          { return "", ErrNotAvailable.Error() }

// ── Pub/Sub stubs ──────────────────────────────────────────────────────

func PubsubPublish(_ string, _ []byte, _ map[string]string) (uint64, string) {
	return 0, ErrNotAvailable.Error()
}
func PubsubPull(_, _ string, _ uint32) ([]PublishedMessage, string) {
	return nil, ErrNotAvailable.Error()
}
func PubsubAck(_, _ string, _ uint64) string                { return ErrNotAvailable.Error() }
func PubsubCursor(_, _ string) (uint64, uint64, string)     { return 0, 0, ErrNotAvailable.Error() }

// ── Secrets stubs ──────────────────────────────────────────────────────

func SecretsGet(_, _ string) ([]byte, bool, string) { return nil, false, ErrNotAvailable.Error() }
func SecretsSet(_, _ string, _ []byte) string       { return ErrNotAvailable.Error() }
func SecretsDelete(_, _ string) string              { return ErrNotAvailable.Error() }
func SecretsListNames(_ string) ([]string, string)  { return nil, ErrNotAvailable.Error() }

// ── Lock stubs ─────────────────────────────────────────────────────────

func LockTryLock(_, _ string, _ uint64) (LockResponse, string) {
	return LockResponse{}, ErrNotAvailable.Error()
}
func LockUnlock(_, _ string) string              { return ErrNotAvailable.Error() }
func LockRenew(_, _ string, _ uint64) string     { return ErrNotAvailable.Error() }

// ── Virtual Actor stubs ────────────────────────────────────────────────

func VirtualActorRegister(_ string, _ []string, _ uint64, _ bool, _ uint32) string {
	return ErrNotAvailable.Error()
}
func VirtualActorInvoke(_, _, _ string, _ []byte) ([]byte, string) {
	return nil, ErrNotAvailable.Error()
}
func VirtualActorListActive(_ string) ([]string, string) { return nil, ErrNotAvailable.Error() }
func VirtualActorDeactivate(_, _ string) string          { return ErrNotAvailable.Error() }
func VirtualActorScheduleMethod(_, _, _ string, _ []byte, _ uint64) (string, string) {
	return "", ErrNotAvailable.Error()
}
func VirtualActorCancelSchedule(_ string) string { return ErrNotAvailable.Error() }

// ── Governance stubs ──────────────────────────────────────────────────

func GovernanceRegisterManifest(_ string) string                            { return "" }
func GovernanceCheckPolicy(_, _, _ string) (PolicyVerdict, string)          { return PolicyVerdictAllow, "" }

// ── Conversation stubs ──────────────────────────────────────────────

func ConversationCreateSession(_ string, _ []string) (string, string) { return "", ErrNotAvailable.Error() }
func ConversationSendMessage(_, _ string, _ *string) (string, string) { return "", ErrNotAvailable.Error() }
func ConversationGetHistory(_ string, _, _ uint32) (string, string)   { return "", ErrNotAvailable.Error() }
func ConversationGetSession(_ string) (string, string)                { return "", ErrNotAvailable.Error() }
func ConversationListSessions(_, _ uint32) (string, string)           { return "", ErrNotAvailable.Error() }
func ConversationCloseSession(_ string) string                        { return ErrNotAvailable.Error() }

// ── Identity stubs ──────────────────────────────────────────────────

func IdentityRegister(_ ActorCard) string              { return "" }
func IdentityResolve(_ string) (*ActorCard, bool, string) { return nil, false, ErrNotAvailable.Error() }
func IdentityResolveAddress(_ string) (*ActorCard, bool, string) { return nil, false, ErrNotAvailable.Error() }
func IdentityListActors(_, _ uint32) ([]ActorCard, string) { return nil, ErrNotAvailable.Error() }

// ── Capability stubs ────────────────────────────────────────────────

func CapabilityDeclare(_ ActorCapability) string { return "" }
func CapabilityDiscover(_ *string, _ *CapabilityStatus, _, _ uint32) ([]CapabilityDiscoveryEntry, string) {
	return nil, ErrNotAvailable.Error()
}
func CapabilityListOwn(_, _ uint32) ([]ActorCapability, string) { return nil, ErrNotAvailable.Error() }

func listenAddr() string {
	if v := os.Getenv("SPIN_HTTP_LISTEN_ADDR"); v != "" {
		return v
	}
	if v := os.Getenv("PORT"); v != "" {
		return ":" + v
	}
	return ":80"
}
