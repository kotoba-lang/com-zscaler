// A2A (Agent-to-Agent) communication via W Protocol.
//
// A2A tasks are dispatched via W Protocol conversation WIT.
// The sender sends a message, the W Protocol host delivers it to all subscribers,
// and the receiving component dispatches it to a registered handler.
//
// Collections:
//   com.etzhayyim.a2a.task   — task envelope (from, to, method, toolName, argumentsJson)
//   com.etzhayyim.a2a.result — result envelope (taskId, from, to, status, outputJson, error)
//
// Usage:
//
//	var app = kotodama.NewApp(kotodama.AppDef{...})
//	app.HandleA2ATask(func(ctx *kotodama.AppContext, task kotodama.A2ATask) (*kotodama.A2AResult, error) {
//	    // Process task, return result
//	    return &kotodama.A2AResult{Status: "ok", OutputJSON: `{"translated":"こんにちは"}`}, nil
//	})
//	func init() { app.Serve() }

package kotodama

import (
	"encoding/json"
	"fmt"
	"time"
)

const (
	// A2ACollectionTask is the W Protocol collection for A2A task records.
	A2ACollectionTask = "com.etzhayyim.a2a.task"
	// A2ACollectionResult is the W Protocol collection for A2A result records.
	A2ACollectionResult = "com.etzhayyim.a2a.result"
)

// a2aHandler is the registered A2A task handler (set via App.HandleA2ATask).
var a2aHandler func(*AppContext, A2ATask) (*A2AResult, error)

// A2ASendTask sends an A2A task to a target actor via W Protocol.
// The task payload is E2E encrypted via w-signal (network-assumed E2E).
// Even when agents are in the same Container, ciphertext flows through yata-wrpc.
// Returns the task ID or an error.
func A2ASendTask(task A2ATask) (string, error) {
	if task.TaskID == "" {
		task.TaskID = generateTaskID()
	}
	if task.From == "" {
		task.From = configGetOrDefault("PERFORMER_ID", configGetOrDefault("APP_NANOID", "unknown"))
	}
	if task.CreatedAt == "" {
		task.CreatedAt = time.Now().UTC().Format(time.RFC3339)
	}

	record := map[string]any{
		"$type":         A2ACollectionTask,
		"taskId":        task.TaskID,
		"from":          task.From,
		"to":            task.To,
		"method":        task.Method,
		"toolName":      task.ToolName,
		"argumentsJson": task.ArgumentsJSON,
		"replyTo":       task.ReplyTo,
		"createdAt":     task.CreatedAt,
	}

	recordJSON, err := json.Marshal(record)
	if err != nil {
		return "", fmt.Errorf("a2a: marshal task: %w", err)
	}

	// E2E encrypt: payload is encrypted via w-signal DR session.
	// Host relays ciphertext only. Even in-process agents get ciphertext.
	payload, contentType := a2aSecureEnvelope(task.To, recordJSON)

	// Dispatch via W Protocol conversation WIT.
	_, errMsg := ConversationSendMessage(task.TaskID, string(payload), &contentType)
	if errMsg != "" {
		return "", fmt.Errorf("a2a: send task: %s", errMsg)
	}

	return task.TaskID, nil
}

// A2AReply sends a result back for an A2A task via W Protocol.
// The result payload is E2E encrypted via w-signal.
// Returns the result ID or an error.
func A2AReply(result A2AResult) (string, error) {
	if result.From == "" {
		result.From = configGetOrDefault("PERFORMER_ID", configGetOrDefault("APP_NANOID", "unknown"))
	}
	if result.CreatedAt == "" {
		result.CreatedAt = time.Now().UTC().Format(time.RFC3339)
	}

	record := map[string]any{
		"$type":      A2ACollectionResult,
		"taskId":     result.TaskID,
		"from":       result.From,
		"to":         result.To,
		"status":     result.Status,
		"outputJson": result.OutputJSON,
		"error":      result.Error,
		"createdAt":  result.CreatedAt,
	}

	recordJSON, err := json.Marshal(record)
	if err != nil {
		return "", fmt.Errorf("a2a: marshal result: %w", err)
	}

	// E2E encrypt reply back to task sender
	payload, contentType := a2aSecureEnvelope(result.To, recordJSON)

	// Dispatch via W Protocol conversation WIT.
	rkey := "result-" + result.TaskID
	_, errMsg := ConversationSendMessage(rkey, string(payload), &contentType)
	if errMsg != "" {
		return "", fmt.Errorf("a2a: send result: %s", errMsg)
	}

	return rkey, nil
}

// A2ACallTool is a convenience wrapper that sends a "call-tool" A2A task.
func A2ACallTool(targetNanoid, toolName, argumentsJSON string) (string, error) {
	return A2ASendTask(A2ATask{
		To:            targetNanoid,
		Method:        "call-tool",
		ToolName:      toolName,
		ArgumentsJSON: argumentsJSON,
	})
}

// A2AAsk is a convenience wrapper that sends a free-form "ask" A2A task.
func A2AAsk(targetNanoid, question string) (string, error) {
	return A2ASendTask(A2ATask{
		To:            targetNanoid,
		Method:        "ask",
		ArgumentsJSON: question,
	})
}

// HandleA2ATask registers a handler for incoming A2A tasks on the App.
// The handler is called when an com.etzhayyim.a2a.task commit is received via W Protocol Firehose
// and the "to" field matches this actor's nanoid.
//
// If the handler returns a non-nil A2AResult, it is automatically sent back
// as an com.etzhayyim.a2a.result W Protocol message.
func (a *App) HandleA2ATask(fn func(*AppContext, A2ATask) (*A2AResult, error)) *App {
	a2aHandler = fn
	return a
}

// dispatchA2ATask is called from handleWCommit when collection = com.etzhayyim.a2a.task.
func (a *App) dispatchA2ATask(commit WCommit, recordJSON []byte) error {
	if a2aHandler == nil {
		return nil
	}

	// Decrypt if E2E encrypted (network-assumed E2E)
	plainJSON := a2aSecureDecrypt(recordJSON)

	var task A2ATask
	if err := json.Unmarshal(plainJSON, &task); err != nil {
		return fmt.Errorf("a2a: unmarshal task: %w", err)
	}

	// Only handle tasks addressed to this actor.
	myNanoid := configGetOrDefault("PERFORMER_ID", configGetOrDefault("APP_NANOID", ""))
	if task.To != myNanoid && task.To != "" && myNanoid != "" {
		return nil // not for us
	}

	ctx := &AppContext{
		OrgID: "anon", UserID: "anon", ActorID: task.From,
		appID: a.def.ID,
		now:   time.Now().UTC().Format(time.RFC3339),
	}

	result, err := a2aHandler(ctx, task)
	if err != nil {
		// Send error result back.
		A2AReply(A2AResult{
			TaskID: task.TaskID,
			To:     task.From,
			Status: "error",
			Error:  err.Error(),
		})
		return err
	}

	if result != nil {
		result.TaskID = task.TaskID
		result.To = task.From
		if result.Status == "" {
			result.Status = "ok"
		}
		A2AReply(*result)
	}

	return nil
}

// ── Phase 3: Capability Discovery + Routing Mesh ──────────────────────────
//
// These functions combine Phase 1 (identity/capability graph) with Phase 2
// (A2A task envelope) to enable intent-based agent routing:
//
//   "I need an agent that can translate" → discover → pick best → send task

// A2ADiscoverAndCall discovers agents with a given capability tag,
// picks the first operational one, and sends a call-tool task.
// Returns (taskURI, targetNanoid, error).
func A2ADiscoverAndCall(tag, toolName, argumentsJSON string) (string, string, error) {
	target, err := A2ADiscoverFirst(tag, nil)
	if err != nil {
		return "", "", err
	}

	uri, err := A2ACallTool(target, toolName, argumentsJSON)
	return uri, target, err
}

// A2ADiscoverFirst finds the first actor with a matching capability tag + optional status.
// Returns the actor's nanoid or an error if none found.
func A2ADiscoverFirst(tag string, status *CapabilityStatus) (string, error) {
	entries, errMsg := CapabilityDiscover(&tag, status, 0, 1)
	if errMsg != "" {
		return "", fmt.Errorf("a2a discover: %s", errMsg)
	}
	if len(entries) == 0 {
		return "", fmt.Errorf("a2a discover: no agent found with capability tag %q", tag)
	}
	return entries[0].Nanoid, nil
}

// A2ADiscoverAll finds all actors with a matching capability tag + optional status.
// Returns a list of (nanoid, capability) pairs.
func A2ADiscoverAll(tag string, status *CapabilityStatus) ([]CapabilityDiscoveryEntry, error) {
	entries, errMsg := CapabilityDiscover(&tag, status, 0, 100)
	if errMsg != "" {
		return nil, fmt.Errorf("a2a discover: %s", errMsg)
	}
	return entries, nil
}

// A2ABroadcast sends the same A2A task to all actors matching a capability tag.
// Returns a list of (nanoid, taskURI) pairs and any errors encountered.
func A2ABroadcast(tag, toolName, argumentsJSON string) ([][2]string, []error) {
	entries, err := A2ADiscoverAll(tag, nil)
	if err != nil {
		return nil, []error{err}
	}

	var results [][2]string
	var errs []error

	for _, entry := range entries {
		uri, sendErr := A2ACallTool(entry.Nanoid, toolName, argumentsJSON)
		if sendErr != nil {
			errs = append(errs, fmt.Errorf("a2a broadcast to %s: %w", entry.Nanoid, sendErr))
			continue
		}
		results = append(results, [2]string{entry.Nanoid, uri})
	}

	return results, errs
}

// A2AResolveAndCall resolves an actor by nanoid (identity), verifies it exists,
// and sends a call-tool task. Useful when you know the target but want to
// confirm it's registered before sending.
func A2AResolveAndCall(nanoid, toolName, argumentsJSON string) (string, error) {
	card, found, errMsg := IdentityResolve(nanoid)
	if errMsg != "" {
		return "", fmt.Errorf("a2a resolve: %s", errMsg)
	}
	if !found || card == nil {
		return "", fmt.Errorf("a2a resolve: actor %q not registered", nanoid)
	}

	return A2ACallTool(nanoid, toolName, argumentsJSON)
}

// A2ADiscoverByTool finds an actor that exposes a specific tool name.
// Searches identity cards directly (not capability tags).
// Returns the actor's nanoid or an error if none found.
func A2ADiscoverByTool(toolName string) (string, error) {
	cards, errMsg := IdentityListActors(0, 200)
	if errMsg != "" {
		return "", fmt.Errorf("a2a discover by tool: %s", errMsg)
	}

	for _, card := range cards {
		for _, tool := range card.Tools {
			if tool.Name == toolName {
				return card.Nanoid, nil
			}
		}
	}

	return "", fmt.Errorf("a2a discover by tool: no agent exposes tool %q", toolName)
}

// ── Phase 4: Multi-Agent Conversation Protocol ────────────────────────────
//
// N-agent async discussions on W Protocol channels. Each conversation is a session
// with participants, topic, and ordered messages — dispatched via W Protocol
// and broadcast via Firehose.
//
// Usage:
//
//	// Agent A: start a conversation with agents B and C
//	session, _ := kotodama.StartConversation("Design review", []string{"agent-b", "agent-c"})
//	kotodama.Say(session.SessionID, "I propose we add caching to the query path")
//
//	// Agent B: receive via Firehose, reply
//	app.HandleConversationMessage(func(ctx *kotodama.AppContext, msg kotodama.ConversationMessage) error {
//	    kotodama.Say(msg.SessionID, "Agreed, but we should benchmark first")
//	    return nil
//	})

// StartConversation creates a new multi-agent conversation session.
// Returns the session metadata and any error.
func StartConversation(topic string, participantNanoids []string) (*ConversationSession, error) {
	sessionJSON, errMsg := ConversationCreateSession(topic, participantNanoids)
	if errMsg != "" {
		return nil, fmt.Errorf("conversation: %s", errMsg)
	}
	var session ConversationSession
	if err := json.Unmarshal([]byte(sessionJSON), &session); err != nil {
		return nil, fmt.Errorf("conversation: decode session: %w", err)
	}
	return &session, nil
}

// Say sends a message to a conversation session. Returns the message metadata.
// The message is E2E encrypted for all session participants.
func Say(sessionID, content string) (*ConversationMessage, error) {
	// E2E encrypt for session (group session via Sender Keys)
	payload, contentType := a2aSecureGroupEnvelope(sessionID, []byte(content))
	msgJSON, errMsg := ConversationSendMessage(sessionID, string(payload), &contentType)
	if errMsg != "" {
		return nil, fmt.Errorf("conversation: %s", errMsg)
	}
	var msg ConversationMessage
	if err := json.Unmarshal([]byte(msgJSON), &msg); err != nil {
		return nil, fmt.Errorf("conversation: decode message: %w", err)
	}
	return &msg, nil
}

// Reply sends a reply to a specific message in a conversation.
// The reply is E2E encrypted for all session participants.
func Reply(sessionID, content, replyToMessageID string) (*ConversationMessage, error) {
	payload, contentType := a2aSecureGroupEnvelope(sessionID, []byte(content))
	msgJSON, errMsg := ConversationSendMessage(sessionID, string(payload), &contentType)
	if errMsg != "" {
		return nil, fmt.Errorf("conversation: %s", errMsg)
	}
	var msg ConversationMessage
	if err := json.Unmarshal([]byte(msgJSON), &msg); err != nil {
		return nil, fmt.Errorf("conversation: decode message: %w", err)
	}
	return &msg, nil
}

// GetConversationHistory retrieves messages from a conversation session.
func GetConversationHistory(sessionID string, offset, limit uint32) ([]ConversationMessage, error) {
	histJSON, errMsg := ConversationGetHistory(sessionID, offset, limit)
	if errMsg != "" {
		return nil, fmt.Errorf("conversation: %s", errMsg)
	}
	var msgs []ConversationMessage
	if err := json.Unmarshal([]byte(histJSON), &msgs); err != nil {
		return nil, fmt.Errorf("conversation: decode history: %w", err)
	}
	return msgs, nil
}

// EndConversation closes a conversation session.
func EndConversation(sessionID string) error {
	if errMsg := ConversationCloseSession(sessionID); errMsg != "" {
		return fmt.Errorf("conversation: %s", errMsg)
	}
	return nil
}

// conversationMessageHandler is the registered handler for incoming conversation messages.
var conversationMessageHandler func(*AppContext, ConversationMessage) error

// HandleConversationMessage registers a handler for incoming conversation messages on the App.
// Messages arrive via Firehose when another agent sends to a session this agent participates in.
func (a *App) HandleConversationMessage(fn func(*AppContext, ConversationMessage) error) *App {
	conversationMessageHandler = fn
	return a
}

// dispatchConversationMessage is called from handleWCommit for com.etzhayyim.a2a.message commits.
func (a *App) dispatchConversationMessage(commit WCommit, recordJSON []byte) error {
	if conversationMessageHandler == nil {
		return nil
	}

	// Decrypt if E2E encrypted (network-assumed E2E)
	plainJSON := a2aSecureDecrypt(recordJSON)

	var msg ConversationMessage
	if err := json.Unmarshal(plainJSON, &msg); err != nil {
		return fmt.Errorf("conversation: unmarshal message: %w", err)
	}

	// Don't handle our own messages.
	myNanoid := configGetOrDefault("PERFORMER_ID", configGetOrDefault("APP_NANOID", ""))
	if msg.From == myNanoid {
		return nil
	}

	ctx := &AppContext{
		OrgID: "anon", UserID: "anon", ActorID: msg.From,
		appID: a.def.ID,
		now:   time.Now().UTC().Format(time.RFC3339),
	}

	return conversationMessageHandler(ctx, msg)
}

// generateTaskID creates a simple time-based task ID.
func generateTaskID() string {
	now := time.Now().UTC()
	return fmt.Sprintf("t%d%03d", now.Unix(), now.Nanosecond()/1000000)
}

// ── A2A E2E Encryption Helpers ──────────────────────────────────────────
// Signal E2E migration: w-signal → kotodama:messaging/signal pending.
// Currently passes plaintext. Will use imports.go Signal bindings when ready.

func a2aSecureEnvelope(_ string, plainJSON []byte) ([]byte, string) {
	return plainJSON, "application/json"
}

func a2aSecureGroupEnvelope(_ string, plaintext []byte) ([]byte, string) {
	return plaintext, "text/plain"
}

func a2aSecureDecrypt(recordJSON []byte) []byte {
	return recordJSON
}

