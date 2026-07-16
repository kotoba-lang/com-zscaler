//go:build tinygo

package kotodama

// imports.go — WIT canonical ABI import declarations for kotodama:{core,auth,messaging,storage,agent,workflow,observability,forms}@1.0.0.
//
// The import names use the full WIT interface path (kotodama:{domain}/{iface}).
// wasm-tools component embed maps these to the WIT interfaces in kotodama.wit.
//
// Complex return types use an out-pointer passed as the last parameter.
// The host writes the result into WASM linear memory at that pointer.
// All pointers are into WASM linear memory (not Go GC heap — use unsafe.Pointer).

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"unsafe"
)

func init() {
	installWASMTransport(&wasmOutboundTransport{})
}

// ── kotodama:core/log ───────────────────────────────────────────────────

//go:wasmimport kotodama:core/log@1.0.0 append
//go:noescape
func wasm_log_append(streamPtr, streamLen, subjectPtr, subjectLen, payloadPtr, payloadLen, retPtr int32)

// LogAppend appends a payload to the named log stream.
// Returns (seq, errMsg). seq is the sequence number assigned by the host.
func LogAppend(stream, subject string, payload []byte) (uint64, string) {
	// Return area layout for result<u64, string>:
	//   [0:4]  tag (0=ok, 1=err)
	//   [4:8]  padding (u64 alignment = 8)
	//   ok:  [8:16] u64
	//   err: [8:12] str_ptr, [12:16] str_len
	var ret [16]byte
	wasm_log_append(
		strPtr(stream), int32(len(stream)),
		strPtr(subject), int32(len(subject)),
		bytesPtr(payload), int32(len(payload)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return 0, readString(ret[:], 8)
	}
	return readU64(ret[:], 8), ""
}

// ── kotodama:core/outbound-http ────────────────────────────────────────
//
// The host import takes the full request encoded as a flat record:
//   (method_ptr, method_len, url_ptr, url_len,
//    headers_ptr, headers_len, body_ptr, body_len, ret_ptr)
// where headers is list<tuple<string,string>>: each item = [key_ptr,key_len,val_ptr,val_len] (16 bytes).

//go:wasmimport kotodama:core/outbound-http@1.0.0 send
//go:noescape
func wasm_outbound_http_send(
	methodPtr, methodLen int32,
	urlPtr, urlLen int32,
	headersPtr, headersLen int32,
	bodyPtr, bodyLen int32,
	retPtr int32,
)

// httpSend is installed as http.DefaultTransport in init() (tinygo only).
type wasmOutboundTransport struct{}

func (t *wasmOutboundTransport) RoundTrip(req *http.Request) (*http.Response, error) {
	var bodyBytes []byte
	if req.Body != nil {
		var err error
		bodyBytes, err = io.ReadAll(req.Body)
		if err != nil {
			return nil, fmt.Errorf("kotodama: read body: %w", err)
		}
		req.Body.Close()
	}

	// Encode headers as flat array of (key_ptr, key_len, val_ptr, val_len) tuples.
	type headerTuple struct{ kp, kl, vp, vl int32 }
	tuples := make([]headerTuple, 0, len(req.Header))
	// Keep all key/value strings alive until wasm_outbound_http_send returns.
	var stringKeepAlive []string
	for k, vs := range req.Header {
		v := strings.Join(vs, ", ")
		stringKeepAlive = append(stringKeepAlive, k, v)
		tuples = append(tuples, headerTuple{
			kp: strPtr(k), kl: int32(len(k)),
			vp: strPtr(v), vl: int32(len(v)),
		})
	}
	_ = stringKeepAlive

	headersPtr, headersLen := int32(0), int32(0)
	if len(tuples) > 0 {
		headersPtr = int32(uintptr(unsafe.Pointer(&tuples[0])))
		headersLen = int32(len(tuples))
	}

	method := req.Method
	rawURL := req.URL.String()

	// Return area layout for result<response, string>:
	//   [0:4]  tag (0=ok, 1=err)
	//   ok: response record flattened:
	//     [4:8]  status (u16 → i32)
	//     [8:12] resp_headers_ptr
	//     [12:16] resp_headers_len
	//     [16:20] body_ptr
	//     [20:24] body_len
	//     [24:28] error_ptr  (error string)
	//     [28:32] error_len
	//   err: [4:8] str_ptr, [8:12] str_len
	var ret [32]byte
	wasm_outbound_http_send(
		strPtr(method), int32(len(method)),
		strPtr(rawURL), int32(len(rawURL)),
		headersPtr, headersLen,
		bytesPtr(bodyBytes), int32(len(bodyBytes)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)

	if readI32(ret[:], 0) != 0 { // err
		return nil, fmt.Errorf("kotodama: outbound-http: %s", readString(ret[:], 4))
	}

	status := int(readI32(ret[:], 4))
	// Read response headers from host-allocated memory.
	rhPtr := uintptr(readI32(ret[:], 8))
	rhLen := int(readI32(ret[:], 12))
	respHeaders := make(http.Header)
	for i := 0; i < rhLen; i++ {
		base := rhPtr + uintptr(i)*16
		kp := uintptr(*(*int32)(unsafe.Pointer(base)))
		kl := int(*(*int32)(unsafe.Pointer(base + 4)))
		vp := uintptr(*(*int32)(unsafe.Pointer(base + 8)))
		vl := int(*(*int32)(unsafe.Pointer(base + 12)))
		k := string(unsafe.Slice((*byte)(unsafe.Pointer(kp)), kl))
		v := string(unsafe.Slice((*byte)(unsafe.Pointer(vp)), vl))
		respHeaders.Add(k, v)
	}
	// Read response body.
	bPtr := uintptr(readI32(ret[:], 16))
	bLen := int(readI32(ret[:], 20))
	bodyOut := make([]byte, bLen)
	if bLen > 0 {
		copy(bodyOut, unsafe.Slice((*byte)(unsafe.Pointer(bPtr)), bLen))
	}
	// Read error string (empty on success).
	if errStr := readString(ret[:], 24); errStr != "" {
		return nil, fmt.Errorf("kotodama: outbound-http response: %s", errStr)
	}

	return &http.Response{
		StatusCode: status,
		Proto:      "HTTP/1.1",
		ProtoMajor: 1, ProtoMinor: 1,
		Header:  respHeaders,
		Body:    io.NopCloser(bytes.NewReader(bodyOut)),
		Request: req,
	}, nil
}

// ── kotodama:core/cypher ───────────────────────────────────────────────
//
// WIT: query(cypher: string, params: list<cypher-param>) -> result<cypher-result, string>
//
// cypher-param  = record { name: string, value: string }
// cypher-result = record { columns: list<string>, rows: list<list<string>> }
//
// Shannon-optimal: column names transmitted once in `columns`, row values by index.
// All values are JSON-encoded strings (same encoding as cypher-param.value).
//
// Canonical ABI encoding for list<cypher-param>:
//   Each element is two (ptr, len) pairs → 16 bytes per param.
// Return area layout for result<cypher-result, string>:
//   [0:4]   outer tag (0=ok, 1=err)
//   ok:  [4:8]  cols_ptr  — list<string> (each element: ptr i32 + len i32 = 8 bytes)
//        [8:12] cols_len
//        [12:16] rows_ptr — list<list<string>> (each element: ptr i32 + len i32 = 8 bytes)
//        [16:20] rows_len
//   err: [4:8]  str_ptr, [8:12] str_len

//go:wasmimport kotodama:core/cypher@1.0.0 query
//go:noescape
func wasm_cypher_query(cypherPtr, cypherLen, paramsPtr, paramsLen, retPtr int32)

// CypherResult: see types.go

// CypherQuery executes a Cypher query via yata Arrow Flight (tiered LanceGraph).
// params is a slice of [2]string{name, json_encoded_value}.
// Returns (result, errMsg). errMsg is non-empty on error.
func CypherQuery(cypher string, params [][2]string) (CypherResult, string) {
	// Encode params as flat array of (name_ptr, name_len, val_ptr, val_len).
	type paramTuple struct{ np, nl, vp, vl int32 }
	tuples := make([]paramTuple, len(params))
	for i, p := range params {
		tuples[i] = paramTuple{
			np: strPtr(p[0]), nl: int32(len(p[0])),
			vp: strPtr(p[1]), vl: int32(len(p[1])),
		}
	}
	paramsPtr, paramsLen := int32(0), int32(len(tuples))
	if len(tuples) > 0 {
		paramsPtr = int32(uintptr(unsafe.Pointer(&tuples[0])))
	}

	// Return area layout for result<cypher-result, string>:
	//   [0:4]   tag (0=ok, 1=err)
	//   ok:  [4:8]  cols_ptr, [8:12] cols_len, [12:16] rows_ptr, [16:20] rows_len
	//   err: [4:8]  str_ptr,  [8:12] str_len
	var ret [20]byte
	wasm_cypher_query(
		strPtr(cypher), int32(len(cypher)),
		paramsPtr, paramsLen,
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)

	if readI32(ret[:], 0) != 0 {
		return CypherResult{}, readString(ret[:], 4)
	}

	// Decode list<string> for column names.
	// Each string element: [ptr i32, len i32] → 8 bytes.
	colsPtr := uintptr(readI32(ret[:], 4))
	colsLen := int(readI32(ret[:], 8))
	columns := make([]string, colsLen)
	for i := 0; i < colsLen; i++ {
		base := colsPtr + uintptr(i)*8
		p := uintptr(*(*int32)(unsafe.Pointer(base)))
		l := int(*(*int32)(unsafe.Pointer(base + 4)))
		columns[i] = string(unsafe.Slice((*byte)(unsafe.Pointer(p)), l))
	}

	// Decode list<list<string>> for row values.
	// Each outer element: [inner_ptr i32, inner_len i32] → 8 bytes.
	// Each inner element (string): [ptr i32, len i32] → 8 bytes.
	rowsPtr := uintptr(readI32(ret[:], 12))
	rowsLen := int(readI32(ret[:], 16))
	rows := make([][]string, rowsLen)
	for i := 0; i < rowsLen; i++ {
		outerBase := rowsPtr + uintptr(i)*8
		innerPtr := uintptr(*(*int32)(unsafe.Pointer(outerBase)))
		innerLen := int(*(*int32)(unsafe.Pointer(outerBase + 4)))
		row := make([]string, innerLen)
		for j := 0; j < innerLen; j++ {
			elemBase := innerPtr + uintptr(j)*8
			p := uintptr(*(*int32)(unsafe.Pointer(elemBase)))
			l := int(*(*int32)(unsafe.Pointer(elemBase + 4)))
			row[j] = string(unsafe.Slice((*byte)(unsafe.Pointer(p)), l))
		}
		rows[i] = row
	}

	return CypherResult{Columns: columns, Rows: rows}, ""
}

// ── kotodama:core/cypher batch-exec ────────────────────────────────────

//go:wasmimport kotodama:core/cypher@1.0.0 batch-exec
//go:noescape
func wasm_cypher_batch_exec(statementsPtr, statementsLen, retPtr int32)

// CypherBatchQuery executes multiple Cypher statements in a single WIT call.
// Single label-load, single CSR rebuild, single WAL fsync.
// Returns per-statement results or first error.
func CypherBatchQuery(statements []BatchStatement) ([]CypherResult, string) {
	if len(statements) == 0 {
		return nil, ""
	}

	// Canonical ABI layout for list<batch-statement>:
	// Each batch-statement = { cypher: (ptr,len), params: (ptr,len) }
	// cypher: string → (i32 ptr, i32 len) = 8 bytes
	// params: list<cypher-param> → (i32 ptr, i32 len) = 8 bytes
	// Total per statement: 16 bytes
	type stmtRecord struct {
		cypherPtr, cypherLen int32
		paramsPtr, paramsLen int32
	}

	// Each cypher-param = { name: (ptr,len), value: (ptr,len) } = 16 bytes
	type paramTuple struct{ np, nl, vp, vl int32 }

	// Pre-encode all param tuples (keep slices alive for GC).
	paramSlices := make([][]paramTuple, len(statements))
	for i, s := range statements {
		tuples := make([]paramTuple, len(s.Params))
		for j, p := range s.Params {
			tuples[j] = paramTuple{
				np: strPtr(p[0]), nl: int32(len(p[0])),
				vp: strPtr(p[1]), vl: int32(len(p[1])),
			}
		}
		paramSlices[i] = tuples
	}

	records := make([]stmtRecord, len(statements))
	for i, s := range statements {
		pp := int32(0)
		if len(paramSlices[i]) > 0 {
			pp = int32(uintptr(unsafe.Pointer(&paramSlices[i][0])))
		}
		records[i] = stmtRecord{
			cypherPtr: strPtr(s.Cypher), cypherLen: int32(len(s.Cypher)),
			paramsPtr: pp, paramsLen: int32(len(s.Params)),
		}
	}

	// Return area layout for result<list<cypher-result>, string>:
	//   [0:4]   tag (0=ok, 1=err)
	//   ok:  [4:8]  list_ptr, [8:12] list_len
	//   err: [4:8]  str_ptr,  [8:12] str_len
	var ret [12]byte
	wasm_cypher_batch_exec(
		int32(uintptr(unsafe.Pointer(&records[0]))), int32(len(records)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)

	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}

	// Decode list<cypher-result>
	// Each cypher-result = { columns: (ptr,len), rows: (ptr,len) } = 16 bytes
	listPtr := uintptr(readI32(ret[:], 4))
	listLen := int(readI32(ret[:], 8))
	results := make([]CypherResult, listLen)

	for i := 0; i < listLen; i++ {
		base := listPtr + uintptr(i)*16

		colsPtr := uintptr(*(*int32)(unsafe.Pointer(base)))
		colsLen := int(*(*int32)(unsafe.Pointer(base + 4)))
		columns := make([]string, colsLen)
		for c := 0; c < colsLen; c++ {
			cb := colsPtr + uintptr(c)*8
			p := uintptr(*(*int32)(unsafe.Pointer(cb)))
			l := int(*(*int32)(unsafe.Pointer(cb + 4)))
			columns[c] = string(unsafe.Slice((*byte)(unsafe.Pointer(p)), l))
		}

		rowsPtr := uintptr(*(*int32)(unsafe.Pointer(base + 8)))
		rowsLen := int(*(*int32)(unsafe.Pointer(base + 12)))
		rows := make([][]string, rowsLen)
		for r := 0; r < rowsLen; r++ {
			outerBase := rowsPtr + uintptr(r)*8
			innerPtr := uintptr(*(*int32)(unsafe.Pointer(outerBase)))
			innerLen := int(*(*int32)(unsafe.Pointer(outerBase + 4)))
			row := make([]string, innerLen)
			for j := 0; j < innerLen; j++ {
				elemBase := innerPtr + uintptr(j)*8
				p := uintptr(*(*int32)(unsafe.Pointer(elemBase)))
				l := int(*(*int32)(unsafe.Pointer(elemBase + 4)))
				row[j] = string(unsafe.Slice((*byte)(unsafe.Pointer(p)), l))
			}
			rows[r] = row
		}

		results[i] = CypherResult{Columns: columns, Rows: rows}
	}

	return results, ""
}

// ── kotodama:core/config ───────────────────────────────────────────────

//go:wasmimport kotodama:core/config@1.0.0 get
//go:noescape
func wasm_config_get(keyPtr, keyLen, retPtr int32)

// ConfigGet returns the value of a runtime config variable, or ("", false) if not set.
func ConfigGet(key string) (string, bool) {
	// Return area layout for option<string>:
	//   [0:4]  tag (0=None, 1=Some)
	//   Some: [4:8] str_ptr, [8:12] str_len
	var ret [12]byte
	wasm_config_get(
		strPtr(key), int32(len(key)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) == 0 { // None
		return "", false
	}
	return readString(ret[:], 4), true
}

// ── init ──────────────────────────────────────────────────────────────────

func init() {
	// Install canonical ABI outbound HTTP transport globally.
	// cypher graph interface, at-client, signal-client all use net/http.DefaultClient.
	http.DefaultTransport = &wasmOutboundTransport{}
}

// ── canonical ABI helpers ─────────────────────────────────────────────────

// strPtr returns the pointer to s's underlying bytes.
// The string must remain live (on the Go heap or stack) during the WASM import call.
func strPtr(s string) int32 {
	if len(s) == 0 {
		return 0
	}
	return int32(uintptr(unsafe.Pointer(unsafe.StringData(s))))
}

// bytesPtr returns the pointer to b's underlying bytes.
func bytesPtr(b []byte) int32 {
	if len(b) == 0 {
		return 0
	}
	return int32(uintptr(unsafe.Pointer(&b[0])))
}

func readI32(buf []byte, offset int) int32 {
	_ = buf[offset+3] // bounds check
	return int32(buf[offset]) |
		int32(buf[offset+1])<<8 |
		int32(buf[offset+2])<<16 |
		int32(buf[offset+3])<<24
}

func readU64(buf []byte, offset int) uint64 {
	_ = buf[offset+7]
	return uint64(buf[offset]) |
		uint64(buf[offset+1])<<8 |
		uint64(buf[offset+2])<<16 |
		uint64(buf[offset+3])<<24 |
		uint64(buf[offset+4])<<32 |
		uint64(buf[offset+5])<<40 |
		uint64(buf[offset+6])<<48 |
		uint64(buf[offset+7])<<56
}

// readString reads a (ptr, len) string from a return area at the given offset.
// The memory was allocated by the host using cabi_realloc — we must copy it out.
func readString(buf []byte, offset int) string {
	ptr := uintptr(readI32(buf, offset))
	ln := int(readI32(buf, offset+4))
	if ln == 0 {
		return ""
	}
	b := make([]byte, ln)
	copy(b, unsafe.Slice((*byte)(unsafe.Pointer(ptr)), ln))
	return string(b)
}

// optionStringArgs encodes an option<string> for WIT canonical ABI.
// Returns (tag, ptr, len): tag=0 means None, tag=1 means Some.
func optionStringArgs(s string) (tag, ptr, length int32) {
	if s == "" {
		return 0, 0, 0
	}
	return 1, strPtr(s), int32(len(s))
}

// readBytes reads a (ptr, len) list<u8> from a return area at the given offset.
func readBytes(buf []byte, offset int) []byte {
	ptr := uintptr(readI32(buf, offset))
	ln := int(readI32(buf, offset+4))
	if ln == 0 {
		return nil
	}
	out := make([]byte, ln)
	copy(out, unsafe.Slice((*byte)(unsafe.Pointer(ptr)), ln))
	return out
}

// ── kotodama:messaging/signal ───────────────────────────────────────────────
//
// All functions follow the same result<list<u8>, string> return layout.

//go:wasmimport kotodama:messaging/signal@1.0.0 generate-identity
//go:noescape
func wasm_signal_generate_identity(retPtr int32)

//go:wasmimport kotodama:messaging/signal@1.0.0 generate-signed-prekey
//go:noescape
func wasm_signal_generate_signed_prekey(ikPtr, ikLen, keyID, retPtr int32)

//go:wasmimport kotodama:messaging/signal@1.0.0 generate-one-time-prekey
//go:noescape
func wasm_signal_generate_one_time_prekey(keyID, retPtr int32)

//go:wasmimport kotodama:messaging/signal@1.0.0 build-pre-key-bundle
//go:noescape
func wasm_signal_build_pre_key_bundle(ikPtr, ikLen, spkPtr, spkLen, opkTag, opkPtr, opkLen, retPtr int32)

//go:wasmimport kotodama:messaging/signal@1.0.0 x3dh-initiate
//go:noescape
func wasm_signal_x3dh_initiate(ikPtr, ikLen, bundlePtr, bundleLen, retPtr int32)

//go:wasmimport kotodama:messaging/signal@1.0.0 x3dh-respond
//go:noescape
func wasm_signal_x3dh_respond(ikPtr, ikLen, spkPtr, spkLen, opkTag, opkPtr, opkLen, initMsgPtr, initMsgLen, retPtr int32)

//go:wasmimport kotodama:messaging/signal@1.0.0 ratchet-init-sender
//go:noescape
func wasm_signal_ratchet_init_sender(x3dhPtr, x3dhLen, recipientKeyPtr, recipientKeyLen, retPtr int32)

//go:wasmimport kotodama:messaging/signal@1.0.0 ratchet-init-receiver
//go:noescape
func wasm_signal_ratchet_init_receiver(x3dhPtr, x3dhLen, secretPtr, secretLen, retPtr int32)

//go:wasmimport kotodama:messaging/signal@1.0.0 ratchet-encrypt
//go:noescape
func wasm_signal_ratchet_encrypt(sessionPtr, sessionLen, plaintextPtr, plaintextLen, retPtr int32)

//go:wasmimport kotodama:messaging/signal@1.0.0 ratchet-decrypt
//go:noescape
func wasm_signal_ratchet_decrypt(sessionPtr, sessionLen, msgPtr, msgLen, retPtr int32)

//go:wasmimport kotodama:messaging/signal@1.0.0 group-init-sender
//go:noescape
func wasm_signal_group_init_sender(groupIDPtr, groupIDLen, ourDIDPtr, ourDIDLen, retPtr int32)

//go:wasmimport kotodama:messaging/signal@1.0.0 group-process-distribution
//go:noescape
func wasm_signal_group_process_distribution(sessionPtr, sessionLen, distPtr, distLen, retPtr int32)

//go:wasmimport kotodama:messaging/signal@1.0.0 group-encrypt
//go:noescape
func wasm_signal_group_encrypt(sessionPtr, sessionLen, plaintextPtr, plaintextLen, retPtr int32)

//go:wasmimport kotodama:messaging/signal@1.0.0 group-decrypt
//go:noescape
func wasm_signal_group_decrypt(sessionPtr, sessionLen, msgPtr, msgLen, retPtr int32)

// signalCall is a helper for single-result Signal calls with result<list<u8>, string>.
func signalCall(retBuf *[12]byte) ([]byte, string) {
	if readI32(retBuf[:], 0) != 0 {
		return nil, readString(retBuf[:], 4)
	}
	return readBytes(retBuf[:], 4), ""
}

// SignalGenerateIdentity generates a new Signal identity key pair.
// Returns CBOR bytes of the IdentityKeyPair (opaque — pass back to host).
func SignalGenerateIdentity() ([]byte, string) {
	var ret [12]byte
	wasm_signal_generate_identity(int32(uintptr(unsafe.Pointer(&ret[0]))))
	return signalCall(&ret)
}

// SignalGenerateSignedPrekey generates a signed prekey.
// identityCbor is from SignalGenerateIdentity. Returns JSON bytes.
func SignalGenerateSignedPrekey(identityCbor []byte, keyID uint32) ([]byte, string) {
	var ret [12]byte
	wasm_signal_generate_signed_prekey(
		bytesPtr(identityCbor), int32(len(identityCbor)),
		int32(keyID),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	return signalCall(&ret)
}

// SignalGenerateOneTimePrekey generates a one-time prekey. Returns JSON bytes.
func SignalGenerateOneTimePrekey(keyID uint32) ([]byte, string) {
	var ret [12]byte
	wasm_signal_generate_one_time_prekey(int32(keyID), int32(uintptr(unsafe.Pointer(&ret[0]))))
	return signalCall(&ret)
}

// SignalBuildPreKeyBundle builds a PreKeyBundle JSON.
// opkJson is nil if no one-time prekey.
func SignalBuildPreKeyBundle(identityCbor, spkJson, opkJson []byte) ([]byte, string) {
	var ret [12]byte
	opkTag, opkPtr, opkLen := optionBytesArgs(opkJson)
	wasm_signal_build_pre_key_bundle(
		bytesPtr(identityCbor), int32(len(identityCbor)),
		bytesPtr(spkJson), int32(len(spkJson)),
		opkTag, opkPtr, opkLen,
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	return signalCall(&ret)
}

// SignalX3DHInitiate initiates an X3DH session as sender.
// bundleJson is the recipient's PreKeyBundle JSON.
// Returns JSON: {shared_secret, ad, init_msg}.
func SignalX3DHInitiate(senderIKCbor, bundleJson []byte) ([]byte, string) {
	var ret [12]byte
	wasm_signal_x3dh_initiate(
		bytesPtr(senderIKCbor), int32(len(senderIKCbor)),
		bytesPtr(bundleJson), int32(len(bundleJson)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	return signalCall(&ret)
}

// SignalX3DHRespond responds to an X3DH initiation as receiver.
// opkJson is nil if no one-time prekey was used.
// Returns JSON: {shared_secret, ad}.
func SignalX3DHRespond(recipientIKCbor, spkJson, opkJson, initMsgJson []byte) ([]byte, string) {
	var ret [12]byte
	opkTag, opkPtr, opkLen := optionBytesArgs(opkJson)
	wasm_signal_x3dh_respond(
		bytesPtr(recipientIKCbor), int32(len(recipientIKCbor)),
		bytesPtr(spkJson), int32(len(spkJson)),
		opkTag, opkPtr, opkLen,
		bytesPtr(initMsgJson), int32(len(initMsgJson)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	return signalCall(&ret)
}

// SignalRatchetInitSender initializes a Double Ratchet session as sender.
// x3dhResultJson is from SignalX3DHInitiate.
// recipientRatchetPublic is the recipient's SPK public key (32 bytes).
// Returns CBOR session bytes.
func SignalRatchetInitSender(x3dhResultJson, recipientRatchetPublic []byte) ([]byte, string) {
	var ret [12]byte
	wasm_signal_ratchet_init_sender(
		bytesPtr(x3dhResultJson), int32(len(x3dhResultJson)),
		bytesPtr(recipientRatchetPublic), int32(len(recipientRatchetPublic)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	return signalCall(&ret)
}

// SignalRatchetInitReceiver initializes a Double Ratchet session as receiver.
// ourRatchetSecret is the SPK private key (32 bytes).
// Returns CBOR session bytes.
func SignalRatchetInitReceiver(x3dhResultJson, ourRatchetSecret []byte) ([]byte, string) {
	var ret [12]byte
	wasm_signal_ratchet_init_receiver(
		bytesPtr(x3dhResultJson), int32(len(x3dhResultJson)),
		bytesPtr(ourRatchetSecret), int32(len(ourRatchetSecret)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	return signalCall(&ret)
}

// SignalRatchetEncrypt encrypts a message using the Double Ratchet.
// Returns JSON: {session: []byte, msg: EncryptedMessage}.
func SignalRatchetEncrypt(sessionCbor, plaintext []byte) ([]byte, string) {
	var ret [12]byte
	wasm_signal_ratchet_encrypt(
		bytesPtr(sessionCbor), int32(len(sessionCbor)),
		bytesPtr(plaintext), int32(len(plaintext)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	return signalCall(&ret)
}

// SignalRatchetDecrypt decrypts a message using the Double Ratchet.
// Returns JSON: {session: []byte, plaintext: []byte}.
func SignalRatchetDecrypt(sessionCbor, msgJson []byte) ([]byte, string) {
	var ret [12]byte
	wasm_signal_ratchet_decrypt(
		bytesPtr(sessionCbor), int32(len(sessionCbor)),
		bytesPtr(msgJson), int32(len(msgJson)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	return signalCall(&ret)
}

// SignalGroupInitSender initializes a group session as sender.
// Returns JSON: {session: []byte, distribution: SenderKeyDistribution}.
func SignalGroupInitSender(groupID, ourDID string) ([]byte, string) {
	var ret [12]byte
	wasm_signal_group_init_sender(
		strPtr(groupID), int32(len(groupID)),
		strPtr(ourDID), int32(len(ourDID)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	return signalCall(&ret)
}

// SignalGroupProcessDistribution processes a SenderKeyDistribution from a member.
// Returns updated session JSON bytes.
func SignalGroupProcessDistribution(sessionJson, distJson []byte) ([]byte, string) {
	var ret [12]byte
	wasm_signal_group_process_distribution(
		bytesPtr(sessionJson), int32(len(sessionJson)),
		bytesPtr(distJson), int32(len(distJson)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	return signalCall(&ret)
}

// SignalGroupEncrypt encrypts a group message using Sender Keys.
// Returns JSON: {session: []byte, msg: SenderKeyMessage}.
func SignalGroupEncrypt(sessionJson, plaintext []byte) ([]byte, string) {
	var ret [12]byte
	wasm_signal_group_encrypt(
		bytesPtr(sessionJson), int32(len(sessionJson)),
		bytesPtr(plaintext), int32(len(plaintext)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	return signalCall(&ret)
}

// SignalGroupDecrypt decrypts a group message.
// Returns JSON: {session: []byte, plaintext: []byte}.
func SignalGroupDecrypt(sessionJson, msgJson []byte) ([]byte, string) {
	var ret [12]byte
	wasm_signal_group_decrypt(
		bytesPtr(sessionJson), int32(len(sessionJson)),
		bytesPtr(msgJson), int32(len(msgJson)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	return signalCall(&ret)
}

// optionBytesArgs encodes an option<list<u8>> for WIT canonical ABI.
func optionBytesArgs(b []byte) (tag, ptr, length int32) {
	if len(b) == 0 {
		return 0, 0, 0
	}
	return 1, bytesPtr(b), int32(len(b))
}

// ── kotodama:auth/clerk ────────────────────────────────────────────────
//
// All clerk functions return result<list<u8>, string> (JSON bytes or error).

//go:wasmimport kotodama:auth/clerk@1.0.0 verify-token
//go:noescape
func wasm_clerk_verify_token(tokenPtr, tokenLen, retPtr int32)

//go:wasmimport kotodama:auth/clerk@1.0.0 verify-token-with-azp
//go:noescape
func wasm_clerk_verify_token_with_azp(tokenPtr, tokenLen, azpPtr, azpLen, retPtr int32)

//go:wasmimport kotodama:auth/clerk@1.0.0 authorize
//go:noescape
func wasm_clerk_authorize(authHeaderPtr, authHeaderLen, orgIDPtr, orgIDLen, permPtr, permLen, retPtr int32)

//go:wasmimport kotodama:auth/clerk@1.0.0 get-user
//go:noescape
func wasm_clerk_get_user(userIDPtr, userIDLen, retPtr int32)

//go:wasmimport kotodama:auth/clerk@1.0.0 get-organization
//go:noescape
func wasm_clerk_get_organization(orgIDPtr, orgIDLen, retPtr int32)

//go:wasmimport kotodama:auth/clerk@1.0.0 get-session
//go:noescape
func wasm_clerk_get_session(sessionIDPtr, sessionIDLen, retPtr int32)

//go:wasmimport kotodama:auth/clerk@1.0.0 check-permission
//go:noescape
func wasm_clerk_check_permission(userIDPtr, userIDLen, orgIDPtr, orgIDLen, permPtr, permLen, retPtr int32)

//go:wasmimport kotodama:auth/clerk@1.0.0 check-role
//go:noescape
func wasm_clerk_check_role(userIDPtr, userIDLen, orgIDPtr, orgIDLen, rolePtr, roleLen, retPtr int32)

// ClerkVerifyToken verifies a JWT. Returns (JSON IdentityClaims bytes, errMsg).
func ClerkVerifyToken(token string) ([]byte, string) {
	var ret [12]byte
	wasm_clerk_verify_token(strPtr(token), int32(len(token)), int32(uintptr(unsafe.Pointer(&ret[0]))))
	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}
	return readBytes(ret[:], 4), ""
}

// ClerkVerifyTokenWithAZP verifies a JWT and checks the azp claim.
func ClerkVerifyTokenWithAZP(token, authorizedParty string) ([]byte, string) {
	var ret [12]byte
	wasm_clerk_verify_token_with_azp(
		strPtr(token), int32(len(token)),
		strPtr(authorizedParty), int32(len(authorizedParty)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}
	return readBytes(ret[:], 4), ""
}

// ClerkAuthorize verifies header + optional org + optional permission check.
// Empty string args skip the respective check.
// Returns (JSON IdentityClaims bytes, errMsg).
func ClerkAuthorize(authorizationHeader, requiredOrgID, requiredPermission string) ([]byte, string) {
	var ret [12]byte
	wasm_clerk_authorize(
		strPtr(authorizationHeader), int32(len(authorizationHeader)),
		strPtr(requiredOrgID), int32(len(requiredOrgID)),
		strPtr(requiredPermission), int32(len(requiredPermission)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}
	return readBytes(ret[:], 4), ""
}

// ClerkGetUser fetches a user by ID. Returns (JSON bytes, errMsg).
func ClerkGetUser(userID string) ([]byte, string) {
	var ret [12]byte
	wasm_clerk_get_user(strPtr(userID), int32(len(userID)), int32(uintptr(unsafe.Pointer(&ret[0]))))
	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}
	return readBytes(ret[:], 4), ""
}

// ClerkGetOrganization fetches an org by ID. Returns (JSON bytes, errMsg).
func ClerkGetOrganization(orgID string) ([]byte, string) {
	var ret [12]byte
	wasm_clerk_get_organization(strPtr(orgID), int32(len(orgID)), int32(uintptr(unsafe.Pointer(&ret[0]))))
	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}
	return readBytes(ret[:], 4), ""
}

// ClerkGetSession fetches a session by ID. Returns (JSON bytes, errMsg).
func ClerkGetSession(sessionID string) ([]byte, string) {
	var ret [12]byte
	wasm_clerk_get_session(strPtr(sessionID), int32(len(sessionID)), int32(uintptr(unsafe.Pointer(&ret[0]))))
	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}
	return readBytes(ret[:], 4), ""
}

// ClerkCheckPermission checks if user has permission in org. Returns (bool, errMsg).
func ClerkCheckPermission(userID, orgID, permission string) (bool, string) {
	// result<bool, string>: [0:4] tag, ok: [4:8] bool as i32, err: [4:8] str_ptr [8:12] str_len
	var ret [12]byte
	wasm_clerk_check_permission(
		strPtr(userID), int32(len(userID)),
		strPtr(orgID), int32(len(orgID)),
		strPtr(permission), int32(len(permission)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return false, readString(ret[:], 4)
	}
	return readI32(ret[:], 4) != 0, ""
}

// ClerkCheckRole checks if user has role in org. Returns (bool, errMsg).
func ClerkCheckRole(userID, orgID, role string) (bool, string) {
	var ret [12]byte
	wasm_clerk_check_role(
		strPtr(userID), int32(len(userID)),
		strPtr(orgID), int32(len(orgID)),
		strPtr(role), int32(len(role)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return false, readString(ret[:], 4)
	}
	return readI32(ret[:], 4) != 0, ""
}

// ── kotodama:auth/authn ────────────────────────────────────────────────

//go:wasmimport kotodama:auth/authn@1.0.0 verify-token
//go:noescape
func wasm_authn_verify_token(tokenPtr, tokenLen, retPtr int32)

//go:wasmimport kotodama:auth/authn@1.0.0 resolve-context
//go:noescape
func wasm_authn_resolve_context(authHeaderPtr, authHeaderLen, orgIDHeaderPtr, orgIDHeaderLen, reqIDHeaderPtr, reqIDHeaderLen, retPtr int32)

//go:wasmimport kotodama:auth/authn@1.0.0 ensure-active-session
//go:noescape
func wasm_authn_ensure_active_session(sessionIDPtr, sessionIDLen, retPtr int32)

// AuthnVerifyToken verifies a bearer token. Returns (AuthnContext, errMsg).
func AuthnVerifyToken(token string) (*AuthnContext, string) {
	var ret [12]byte
	wasm_authn_verify_token(strPtr(token), int32(len(token)), int32(uintptr(unsafe.Pointer(&ret[0]))))
	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}
	b := readBytes(ret[:], 4)
	var ctx AuthnContext
	if err := json.Unmarshal(b, &ctx); err != nil {
		return nil, err.Error()
	}
	return &ctx, ""
}

// AuthnResolveContext builds an AuthnContext from HTTP headers.
// orgIDHeader and requestIDHeader may be empty.
func AuthnResolveContext(authorizationHeader, orgIDHeader, requestIDHeader string) (*AuthnContext, string) {
	var ret [12]byte
	wasm_authn_resolve_context(
		strPtr(authorizationHeader), int32(len(authorizationHeader)),
		strPtr(orgIDHeader), int32(len(orgIDHeader)),
		strPtr(requestIDHeader), int32(len(requestIDHeader)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}
	b := readBytes(ret[:], 4)
	var ctx AuthnContext
	if err := json.Unmarshal(b, &ctx); err != nil {
		return nil, err.Error()
	}
	return &ctx, ""
}

// AuthnEnsureActiveSession validates session liveness. Returns errMsg ("" on success).
func AuthnEnsureActiveSession(sessionID string) string {
	// result<_, string>: [0:4] tag (0=ok, 1=err), err: [4:8] str_ptr [8:12] str_len
	var ret [12]byte
	wasm_authn_ensure_active_session(strPtr(sessionID), int32(len(sessionID)), int32(uintptr(unsafe.Pointer(&ret[0]))))
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// ── kotodama:auth/authz ────────────────────────────────────────────────

//go:wasmimport kotodama:auth/authz@1.0.0 enforce
//go:noescape
func wasm_authz_enforce(orgIDPtr, orgIDLen, rolePtr, roleLen, permsPtr, permsLen, reqPermsPtr, reqPermsLen, reqRolesPtr, reqRolesLen, retPtr int32)

// AuthzEnforce enforces RBAC. Returns errMsg ("" = allowed, non-empty = denied with reason).
// permissions: subject's actual permissions from JWT.
// requiredPermissions: at least one must match (OR). Empty = skip check.
// requiredRoles: at least one must match (OR). Empty = skip check.
func AuthzEnforce(orgID, role string, permissions, requiredPermissions, requiredRoles []string) string {
	type strTuple struct{ p, l int32 }
	encodeList := func(ss []string) (ptr, length int32) {
		if len(ss) == 0 {
			return 0, 0
		}
		tuples := make([]strTuple, len(ss))
		for i, s := range ss {
			tuples[i] = strTuple{strPtr(s), int32(len(s))}
		}
		return int32(uintptr(unsafe.Pointer(&tuples[0]))), int32(len(tuples))
	}
	permsPtr, permsLen := encodeList(permissions)
	reqPermsPtr, reqPermsLen := encodeList(requiredPermissions)
	reqRolesPtr, reqRolesLen := encodeList(requiredRoles)

	var ret [12]byte
	wasm_authz_enforce(
		strPtr(orgID), int32(len(orgID)),
		strPtr(role), int32(len(role)),
		permsPtr, permsLen,
		reqPermsPtr, reqPermsLen,
		reqRolesPtr, reqRolesLen,
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// ── kotodama:storage/cdn ──────────────────────────────────────────────────

//go:wasmimport kotodama:storage/cdn@1.0.0 upload
//go:noescape
func wasm_cdn_upload(subdomainPtr, subdomainLen, pathPtr, pathLen, dataPtr, dataLen, ctPtr, ctLen, retPtr int32)

//go:wasmimport kotodama:storage/cdn@1.0.0 fetch-upload
//go:noescape
func wasm_cdn_fetch_upload(subdomainPtr, subdomainLen, srcPtr, srcLen, pathPtr, pathLen, ctPtr, ctLen, retPtr int32)

//go:wasmimport kotodama:storage/cdn@1.0.0 delete
//go:noescape
func wasm_cdn_delete(subdomainPtr, subdomainLen, pathPtr, pathLen, retPtr int32)

//go:wasmimport kotodama:storage/cdn@1.0.0 public-url
//go:noescape
func wasm_cdn_public_url(subdomainPtr, subdomainLen, pathPtr, pathLen, retPtr int32)

// CdnUpload uploads data to CDN. Returns (publicURL, errMsg).
// subdomain="" uses host default.
func CdnUpload(subdomain, path string, data []byte, contentType string) (string, string) {
	// result<string, string>: [0:4] tag, ok: [4:8] str_ptr [8:12] str_len, err same
	var ret [12]byte
	wasm_cdn_upload(
		strPtr(subdomain), int32(len(subdomain)),
		strPtr(path), int32(len(path)),
		bytesPtr(data), int32(len(data)),
		strPtr(contentType), int32(len(contentType)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return "", readString(ret[:], 4)
	}
	return readString(ret[:], 4), ""
}

// CdnFetchUpload fetches sourceURL and uploads the result to CDN. Returns (publicURL, errMsg).
func CdnFetchUpload(subdomain, sourceURL, path, contentType string) (string, string) {
	var ret [12]byte
	wasm_cdn_fetch_upload(
		strPtr(subdomain), int32(len(subdomain)),
		strPtr(sourceURL), int32(len(sourceURL)),
		strPtr(path), int32(len(path)),
		strPtr(contentType), int32(len(contentType)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return "", readString(ret[:], 4)
	}
	return readString(ret[:], 4), ""
}

// CdnDelete deletes an object from CDN storage. Returns errMsg ("" on success).
func CdnDelete(subdomain, path string) string {
	var ret [12]byte
	wasm_cdn_delete(
		strPtr(subdomain), int32(len(subdomain)),
		strPtr(path), int32(len(path)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// CdnPublicURL returns the public CDN URL for a path without uploading.
func CdnPublicURL(subdomain, path string) string {
	// option<string> / string return: treat as result<string, string> (host always returns ok)
	var ret [12]byte
	wasm_cdn_public_url(
		strPtr(subdomain), int32(len(subdomain)),
		strPtr(path), int32(len(path)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	return readString(ret[:], 0)
}

//go:wasmimport kotodama:storage/cdn@1.0.0 upload-image
//go:noescape
func wasm_cdn_upload_image(subdomainPtr, subdomainLen, pathPtr, pathLen, dataPtr, dataLen int32, maxWidth, maxHeight, quality int32, fmtPtr, fmtLen, retPtr int32)

// CdnUploadImage decodes, resizes, re-encodes an image on the Rust host,
// then uploads to CDN. Returns (publicURL, errMsg).
// Typical: 15MB JPEG → ~200KB WebP with MaxWidth=1920.
func CdnUploadImage(subdomain, path string, data []byte, opts ImageUploadOptions) (string, string) {
	var ret [12]byte
	wasm_cdn_upload_image(
		strPtr(subdomain), int32(len(subdomain)),
		strPtr(path), int32(len(path)),
		bytesPtr(data), int32(len(data)),
		int32(opts.MaxWidth), int32(opts.MaxHeight), int32(opts.Quality),
		strPtr(opts.Format), int32(len(opts.Format)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return "", readString(ret[:], 4)
	}
	return readString(ret[:], 4), ""
}

// ── kotodama:storage/static-site ───────────────────────────────────────────
//
// WIT:
//   put(path: string, data: list<u8>, content-type: string) -> result<u64, string>
//   delete(path: string) -> result<_, string>
//   list(prefix: string) -> result<list<string>, string>

//go:wasmimport kotodama:storage/static-site@1.0.0 put
//go:noescape
func wasm_static_site_put(pathPtr, pathLen, dataPtr, dataLen, ctPtr, ctLen, retPtr int32)

//go:wasmimport kotodama:storage/static-site@1.0.0 delete
//go:noescape
func wasm_static_site_delete(pathPtr, pathLen, retPtr int32)

//go:wasmimport kotodama:storage/static-site@1.0.0 list-files
//go:noescape
func wasm_static_site_list_files(prefixPtr, prefixLen, retPtr int32)

// StaticPut writes a pre-rendered page to R2 FUSE mount. Returns (bytesWritten, errMsg).
func StaticPut(path string, data []byte, contentType string) (uint64, string) {
	// result<u64, string>: [0:4] tag, ok: [4:12] u64, err: [4:8] str_ptr [8:12] str_len
	var ret [12]byte
	wasm_static_site_put(
		strPtr(path), int32(len(path)),
		bytesPtr(data), int32(len(data)),
		strPtr(contentType), int32(len(contentType)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return 0, readString(ret[:], 4)
	}
	return readU64(ret[:], 4), ""
}

// StaticDelete removes a static page from R2 FUSE mount. Returns errMsg ("" on success).
func StaticDelete(path string) string {
	var ret [12]byte
	wasm_static_site_delete(
		strPtr(path), int32(len(path)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// StaticList is exposed via WIT but requires list<string> canonical ABI decoding.
// Use StaticPut/StaticDelete for ISR. For listing, query the graph.

// ── kotodama:storage/ipfs ─────────────────────────────────────────────────
//
// WIT:
//   publish(data: list<u8>, content-type: string) -> result<string, string>
//   publish-url(data: list<u8>, content-type: string) -> result<string, string>
//   gateway-url(cid: string) -> string

//go:wasmimport kotodama:storage/ipfs@1.0.0 publish
//go:noescape
func wasm_ipfs_publish(dataPtr, dataLen, ctPtr, ctLen, retPtr int32)

//go:wasmimport kotodama:storage/ipfs@1.0.0 publish-url
//go:noescape
func wasm_ipfs_publish_url(dataPtr, dataLen, ctPtr, ctLen, retPtr int32)

//go:wasmimport kotodama:storage/ipfs@1.0.0 gateway-url
//go:noescape
func wasm_ipfs_gateway_url(cidPtr, cidLen, retPtr int32)

// IpfsPublish uploads data and returns the CIDv1 or errMsg.
func IpfsPublish(data []byte, contentType string) (string, string) {
	// result<string, string>: [0:4] tag, ok: [4:8] str_ptr [8:12] str_len, err same
	var ret [12]byte
	wasm_ipfs_publish(
		bytesPtr(data), int32(len(data)),
		strPtr(contentType), int32(len(contentType)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return "", readString(ret[:], 4)
	}
	return readString(ret[:], 4), ""
}

// IpfsPublishURL uploads data and returns the public gateway URL or errMsg.
func IpfsPublishURL(data []byte, contentType string) (string, string) {
	var ret [12]byte
	wasm_ipfs_publish_url(
		bytesPtr(data), int32(len(data)),
		strPtr(contentType), int32(len(contentType)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return "", readString(ret[:], 4)
	}
	return readString(ret[:], 4), ""
}

// IpfsGatewayURL returns the public gateway URL for a known CID (no upload).
func IpfsGatewayURL(cid string) string {
	// returns plain string (no result wrapper)
	var ret [12]byte
	wasm_ipfs_gateway_url(
		strPtr(cid), int32(len(cid)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	return readString(ret[:], 0)
}

// ── kotodama:storage/storage ──────────────────────────────────────────────
//
// WIT:
//   put-object(bucket, key: string, data: list<u8>, content-type: string) -> result<string, string>
//   get-object(bucket, key: string) -> result<list<u8>, string>
//   delete-object(bucket, key: string) -> result<_, string>

//go:wasmimport kotodama:storage/storage@1.0.0 put-object
//go:noescape
func wasm_storage_put_object(bucketPtr, bucketLen, keyPtr, keyLen, dataPtr, dataLen, ctPtr, ctLen, retPtr int32)

//go:wasmimport kotodama:storage/storage@1.0.0 get-object
//go:noescape
func wasm_storage_get_object(bucketPtr, bucketLen, keyPtr, keyLen, retPtr int32)

//go:wasmimport kotodama:storage/storage@1.0.0 delete-object
//go:noescape
func wasm_storage_delete_object(bucketPtr, bucketLen, keyPtr, keyLen, retPtr int32)

// StoragePutObject uploads to distributed storage. Returns (CID, errMsg).
func StoragePutObject(bucket, key string, data []byte, contentType string) (string, string) {
	// result<string, string>: [0:4] tag, ok: [4:8] str_ptr [8:12] str_len, err same
	var ret [12]byte
	wasm_storage_put_object(
		strPtr(bucket), int32(len(bucket)),
		strPtr(key), int32(len(key)),
		bytesPtr(data), int32(len(data)),
		strPtr(contentType), int32(len(contentType)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return "", readString(ret[:], 4)
	}
	return readString(ret[:], 4), ""
}

// StorageGetObject retrieves object data by bucket + key. Returns (data, errMsg).
func StorageGetObject(bucket, key string) ([]byte, string) {
	// result<list<u8>, string>: [0:4] tag, ok: [4:8] ptr [8:12] len, err: [4:8] str_ptr [8:12] str_len
	var ret [12]byte
	wasm_storage_get_object(
		strPtr(bucket), int32(len(bucket)),
		strPtr(key), int32(len(key)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}
	return readBytes(ret[:], 4), ""
}

// StorageDeleteObject deletes an object from distributed storage. Returns errMsg ("" on success).
func StorageDeleteObject(bucket, key string) string {
	// result<_, string>: [0:4] tag (0=ok, 1=err), err: [4:8] str_ptr [8:12] str_len
	var ret [12]byte
	wasm_storage_delete_object(
		strPtr(bucket), int32(len(bucket)),
		strPtr(key), int32(len(key)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// ── kotodama:messaging/signal-session ──────────────────────────────────────
//
// Managed Signal Protocol group sessions (host persists KV state automatically).
// WIT:
//   group-get-or-create(group-id: string, member-dids: list<string>)
//     -> result<tuple<list<u8>, list<u8>>, string>
//   group-encrypt(group-id: string, plaintext: list<u8>) -> result<list<u8>, string>
//   group-decrypt(group-id: string, ciphertext: list<u8>, sender-did: string) -> result<list<u8>, string>
//   group-add-member(group-id: string, member-did: string) -> result<list<u8>, string>

//go:wasmimport kotodama:messaging/signal-session@1.0.0 group-get-or-create
//go:noescape
func wasm_signal_session_group_get_or_create(
	groupIDPtr, groupIDLen int32,
	memberDIDsPtr, memberDIDsLen int32,
	retPtr int32,
)

//go:wasmimport kotodama:messaging/signal-session@1.0.0 group-encrypt
//go:noescape
func wasm_signal_session_group_encrypt(groupIDPtr, groupIDLen, plaintextPtr, plaintextLen, retPtr int32)

//go:wasmimport kotodama:messaging/signal-session@1.0.0 group-decrypt
//go:noescape
func wasm_signal_session_group_decrypt(
	groupIDPtr, groupIDLen int32,
	ciphertextPtr, ciphertextLen int32,
	senderDIDPtr, senderDIDLen int32,
	retPtr int32,
)

//go:wasmimport kotodama:messaging/signal-session@1.0.0 group-add-member
//go:noescape
func wasm_signal_session_group_add_member(groupIDPtr, groupIDLen, memberDIDPtr, memberDIDLen, retPtr int32)

// SignalSessionGroupGetOrCreate gets an existing group session or creates a new one.
// Returns (sessionBytes, distributionJSON, errMsg).
// distributionJSON is the SenderKeyDistribution to share with new members.
func SignalSessionGroupGetOrCreate(groupID string, memberDIDs []string) ([]byte, []byte, string) {
	// Encode member DIDs as list<string> canonical ABI: list of (ptr, len) tuples.
	type strTuple struct{ p, l int32 }
	tuples := make([]strTuple, len(memberDIDs))
	for i, did := range memberDIDs {
		tuples[i] = strTuple{strPtr(did), int32(len(did))}
	}
	membersPtr, membersLen := int32(0), int32(0)
	if len(tuples) > 0 {
		membersPtr = int32(uintptr(unsafe.Pointer(&tuples[0])))
		membersLen = int32(len(tuples))
	}

	// Return area: result<tuple<list<u8>,list<u8>>, string>
	// ok:  [4:8] list1_ptr, [8:12] list1_len, [12:16] list2_ptr, [16:20] list2_len
	// err: [4:8] str_ptr, [8:12] str_len
	var ret [20]byte
	wasm_signal_session_group_get_or_create(
		strPtr(groupID), int32(len(groupID)),
		membersPtr, membersLen,
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return nil, nil, readString(ret[:], 4)
	}
	session := readBytesAt(ret[:], 4)
	dist := readBytesAt(ret[:], 12)
	return session, dist, ""
}

// SignalSessionGroupEncrypt encrypts a group message.
// The host manages session KV state automatically.
// Returns (encryptedJSON, errMsg). encryptedJSON is {"msg": SenderKeyMessage}.
func SignalSessionGroupEncrypt(groupID string, plaintext []byte) ([]byte, string) {
	var ret [12]byte
	wasm_signal_session_group_encrypt(
		strPtr(groupID), int32(len(groupID)),
		bytesPtr(plaintext), int32(len(plaintext)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	return signalCall(&ret)
}

// SignalSessionGroupDecrypt decrypts a group message.
// The host manages session KV state automatically.
// Returns (plaintext, errMsg).
func SignalSessionGroupDecrypt(groupID string, ciphertext []byte, senderDID string) ([]byte, string) {
	var ret [12]byte
	wasm_signal_session_group_decrypt(
		strPtr(groupID), int32(len(groupID)),
		bytesPtr(ciphertext), int32(len(ciphertext)),
		strPtr(senderDID), int32(len(senderDID)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	return signalCall(&ret)
}

// SignalSessionGroupAddMember adds a member to the group session.
// Returns (distributionJSON, errMsg). distributionJSON should be delivered to the new member.
func SignalSessionGroupAddMember(groupID, memberDID string) ([]byte, string) {
	var ret [12]byte
	wasm_signal_session_group_add_member(
		strPtr(groupID), int32(len(groupID)),
		strPtr(memberDID), int32(len(memberDID)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	return signalCall(&ret)
}

// ── kotodama:agent/agent ────────────────────────────────────────────────
//
// WIT:
//   register-tools(tools: list<tool-def>) -> ()
//   chat(user-message: string, llm-context-json: option<string>) -> result<string, string>
//   invoke-tool(tool-name, input-json: string) -> result<string, string>

//go:wasmimport kotodama:agent/agent@1.0.0 register-tools
//go:noescape
func wasm_agent_register_tools(toolsPtr, toolsLen int32)

//go:wasmimport kotodama:agent/agent@1.0.0 chat
//go:noescape
func wasm_agent_chat(msgPtr, msgLen, ctxTag, ctxPtr, ctxLen, retPtr int32)

//go:wasmimport kotodama:agent/agent@1.0.0 invoke-tool
//go:noescape
func wasm_agent_invoke_tool(namePtr, nameLen, inputPtr, inputLen, retPtr int32)

// AgentToolDef: see types.go

// AgentRegisterTools registers LLM tools for the current component instance.
// Encoded as list<tool-def> where each tool-def = (name_ptr,name_len, desc_ptr,desc_len, schema_ptr,schema_len).
func AgentRegisterTools(tools []AgentToolDef) {
	if len(tools) == 0 {
		return
	}
	// Encode list<tool-def> canonical ABI: packed array of 6×int32 per entry.
	type toolRecord struct{ np, nl, dp, dl, sp, sl int32 }
	records := make([]toolRecord, len(tools))
	for i, t := range tools {
		records[i] = toolRecord{
			np: strPtr(t.Name), nl: int32(len(t.Name)),
			dp: strPtr(t.Description), dl: int32(len(t.Description)),
			sp: strPtr(t.InputSchemaJSON), sl: int32(len(t.InputSchemaJSON)),
		}
	}
	wasm_agent_register_tools(
		int32(uintptr(unsafe.Pointer(&records[0]))), int32(len(records)),
	)
}

// AgentChat sends a message to the LLM with the registered tools.
// llmContextJSON is optional JSON (pass "" for default).
// Returns (responseText, errMsg).
func AgentChat(userMessage, llmContextJSON string) (string, string) {
	var ret [12]byte
	ctxTag, ctxPtr, ctxLen := int32(0), int32(0), int32(0)
	if llmContextJSON != "" {
		ctxTag = 1
		ctxPtr = strPtr(llmContextJSON)
		ctxLen = int32(len(llmContextJSON))
	}
	wasm_agent_chat(
		strPtr(userMessage), int32(len(userMessage)),
		ctxTag, ctxPtr, ctxLen,
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return "", readString(ret[:], 4)
	}
	return readString(ret[:], 4), ""
}

// AgentInvokeTool calls a registered tool directly (bypasses LLM).
// Returns (resultJSON, errMsg).
func AgentInvokeTool(toolName, inputJSON string) (string, string) {
	var ret [12]byte
	wasm_agent_invoke_tool(
		strPtr(toolName), int32(len(toolName)),
		strPtr(inputJSON), int32(len(inputJSON)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return "", readString(ret[:], 4)
	}
	return readString(ret[:], 4), ""
}

// ── kotodama:workflow/workflow ────────────────────────────────────────────
//
// WIT (v1.0.0 funcs):
//   get(workflow-id) -> result<workflow-info, string>
//   pause(workflow-id) -> result<_, string>
//   resume(workflow-id) -> result<_, string>
//   terminate(workflow-id) -> result<_, string>
//   purge(workflow-id) -> result<_, string>
//   raise-event(workflow-id, event-name, payload) -> result<_, string>
//   create-timer(workflow-id, name, fire-at-ms) -> result<_, string>
//
// workflow-info v1.0.0 layout (result ok branch):
//   [4]  workflow_id_ptr   (4)
//   [8]  workflow_id_len   (4)
//   [12] status_disc       (4)  — workflow-status enum
//   [16] created_at_ms     (8)
//   [24] updated_at_ms     (8)
//   [32] output_opt_tag    (4)
//   [36] output_ptr        (4)
//   [40] output_len        (4)
//   [44] error_opt_tag     (4)
//   [48] error_ptr         (4)
//   [52] error_len         (4)
//   Total ok: 56 bytes

//go:wasmimport kotodama:workflow/workflow@1.0.0 get
//go:noescape
func wasm_workflow_get(wfIdPtr, wfIdLen, retPtr int32)

//go:wasmimport kotodama:workflow/workflow@1.0.0 pause
//go:noescape
func wasm_workflow_pause(wfIdPtr, wfIdLen, retPtr int32)

//go:wasmimport kotodama:workflow/workflow@1.0.0 resume
//go:noescape
func wasm_workflow_resume(wfIdPtr, wfIdLen, retPtr int32)

//go:wasmimport kotodama:workflow/workflow@1.0.0 terminate
//go:noescape
func wasm_workflow_terminate(wfIdPtr, wfIdLen, retPtr int32)

//go:wasmimport kotodama:workflow/workflow@1.0.0 purge
//go:noescape
func wasm_workflow_purge(wfIdPtr, wfIdLen, retPtr int32)

//go:wasmimport kotodama:workflow/workflow@1.0.0 raise-event
//go:noescape
func wasm_workflow_raise_event(wfIdPtr, wfIdLen, evtNamePtr, evtNameLen, payloadPtr, payloadLen, retPtr int32)

//go:wasmimport kotodama:workflow/workflow@1.0.0 create-timer
//go:noescape
func wasm_workflow_create_timer(wfIdPtr, wfIdLen, namePtr, nameLen int32, fireAtMs uint64, retPtr int32)

// WorkflowGet retrieves workflow execution info.
func WorkflowGet(workflowID string) (WorkflowInfo, string) {
	var ret [60]byte
	wasm_workflow_get(
		strPtr(workflowID), int32(len(workflowID)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return WorkflowInfo{}, readString(ret[:], 4)
	}
	info := WorkflowInfo{
		WorkflowID:  readString(ret[:], 4),
		Status:      WorkflowStatus(readI32(ret[:], 12)),
		CreatedAtMs: readU64At(ret[:], 16),
		UpdatedAtMs: readU64At(ret[:], 24),
	}
	if readI32(ret[:], 32) != 0 {
		info.Output = readBytesAt(ret[:], 36)
	}
	if readI32(ret[:], 44) != 0 {
		s := readString(ret[:], 48)
		info.Error = &s
	}
	return info, ""
}

func workflowSimpleCall(fn func(int32, int32, int32), workflowID string) string {
	var ret [12]byte
	fn(
		strPtr(workflowID), int32(len(workflowID)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// WorkflowPause pauses a running workflow. Returns errMsg.
func WorkflowPause(workflowID string) string {
	var ret [12]byte
	wasm_workflow_pause(strPtr(workflowID), int32(len(workflowID)), int32(uintptr(unsafe.Pointer(&ret[0]))))
	if readI32(ret[:], 0) != 0 { return readString(ret[:], 4) }
	return ""
}

// WorkflowResume resumes a suspended workflow. Returns errMsg.
func WorkflowResume(workflowID string) string {
	var ret [12]byte
	wasm_workflow_resume(strPtr(workflowID), int32(len(workflowID)), int32(uintptr(unsafe.Pointer(&ret[0]))))
	if readI32(ret[:], 0) != 0 { return readString(ret[:], 4) }
	return ""
}

// WorkflowTerminate terminates a workflow. Returns errMsg.
func WorkflowTerminate(workflowID string) string {
	var ret [12]byte
	wasm_workflow_terminate(strPtr(workflowID), int32(len(workflowID)), int32(uintptr(unsafe.Pointer(&ret[0]))))
	if readI32(ret[:], 0) != 0 { return readString(ret[:], 4) }
	return ""
}

// WorkflowPurge deletes a completed/failed/terminated workflow. Returns errMsg.
func WorkflowPurge(workflowID string) string {
	var ret [12]byte
	wasm_workflow_purge(strPtr(workflowID), int32(len(workflowID)), int32(uintptr(unsafe.Pointer(&ret[0]))))
	if readI32(ret[:], 0) != 0 { return readString(ret[:], 4) }
	return ""
}

// WorkflowRaiseEvent sends an event to a workflow. Returns errMsg.
func WorkflowRaiseEvent(workflowID, eventName string, payload []byte) string {
	var ret [12]byte
	wasm_workflow_raise_event(
		strPtr(workflowID), int32(len(workflowID)),
		strPtr(eventName), int32(len(eventName)),
		bytesPtr(payload), int32(len(payload)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// WorkflowCreateTimer creates a timer attached to a workflow. Returns errMsg.
func WorkflowCreateTimer(workflowID, name string, fireAtMs uint64) string {
	var ret [12]byte
	wasm_workflow_create_timer(
		strPtr(workflowID), int32(len(workflowID)),
		strPtr(name), int32(len(name)),
		fireAtMs,
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// ── kotodama:workflow/reminder (v1.0.0) ────────────────────────

//go:wasmimport kotodama:workflow/reminder@1.0.0 get
//go:noescape
func wasm_reminder_get(namePtr, nameLen, retPtr int32)

// ReminderGet retrieves a single reminder by name. Returns (entry, found, errMsg).
func ReminderGet(name string) (*ReminderEntry, bool, string) {
	// result<option<reminder-entry>, string>
	// Layout: [0] disc(4), [4] option-tag(4), then entry fields or err string
	var ret [80]byte
	wasm_reminder_get(
		strPtr(name), int32(len(name)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return nil, false, readString(ret[:], 4)
	}
	if readI32(ret[:], 4) == 0 {
		return nil, false, ""
	}
	// Parse reminder-entry from offset 8.
	entry := &ReminderEntry{
		Name:           readString(ret[:], 8),
		DueUnixMs:      readU64At(ret[:], 16),
		PeriodMs:       readU64At(ret[:], 24),
		CallbackMethod: readString(ret[:], 32),
		PayloadJSON:    readString(ret[:], 40),
		OnFailure:      FailurePolicy(readI32(ret[:], 68)),
	}
	if readI32(ret[:], 48) != 0 {
		v := uint32(readI32(ret[:], 52))
		entry.MaxInvocations = &v
	}
	if readI32(ret[:], 56) != 0 {
		v := uint32(readI32(ret[:], 60))
		entry.Remaining = &v
	}
	if readI32(ret[:], 64) != 0 {
		// TTL stored as option<u64> — 8 bytes after tag
		// This is a simplification; actual layout depends on ABI
	}
	return entry, true, ""
}

// ── kotodama:workflow/timer ──────────────────────────────────────────────

//go:wasmimport kotodama:workflow/timer@1.0.0 register
//go:noescape
func wasm_timer_register(namePtr, nameLen int32, dueMs, periodMs uint64, maxInvTag, maxInvVal, cbPtr, cbLen, payloadPtr, payloadLen, retPtr int32)

//go:wasmimport kotodama:workflow/timer@1.0.0 unregister
//go:noescape
func wasm_timer_unregister(namePtr, nameLen, retPtr int32)

// TimerRegister registers an ephemeral timer. Returns errMsg.
func TimerRegister(config TimerConfig) string {
	maxTag, maxVal := int32(0), int32(0)
	if config.MaxInvocations != nil {
		maxTag = 1
		maxVal = int32(*config.MaxInvocations)
	}
	var ret [12]byte
	wasm_timer_register(
		strPtr(config.Name), int32(len(config.Name)),
		config.DueMs, config.PeriodMs,
		maxTag, maxVal,
		strPtr(config.CallbackMethod), int32(len(config.CallbackMethod)),
		bytesPtr(config.Payload), int32(len(config.Payload)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// TimerUnregister removes an ephemeral timer. Returns errMsg.
func TimerUnregister(name string) string {
	var ret [12]byte
	wasm_timer_unregister(
		strPtr(name), int32(len(name)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// ── kotodama:agent/governance@1.0.0 ─────────────────────────────────────

//go:wasmimport kotodama:agent/governance@1.0.0 register-manifest
//go:noescape
func wasm_governance_register_manifest(jsonPtr, jsonLen, retPtr int32)

//go:wasmimport kotodama:agent/governance@1.0.0 check-policy
//go:noescape
func wasm_governance_check_policy(cmdPtr, cmdLen, userPtr, userLen, orgPtr, orgLen, retPtr int32)

// GovernanceRegisterManifest registers the app's governance manifest with the host.
// Returns errMsg ("" on success).
func GovernanceRegisterManifest(manifestJSON string) string {
	var ret [12]byte
	wasm_governance_register_manifest(
		strPtr(manifestJSON), int32(len(manifestJSON)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// GovernanceCheckPolicy checks whether a command is allowed for the given caller.
// Returns (verdict, errMsg).
func GovernanceCheckPolicy(command, callerUserID, callerOrgID string) (PolicyVerdict, string) {
	// Return area: result<policy-verdict, string>
	//   [0:4] tag (0=ok, 1=err)
	//   ok: [4:8] enum discriminant (i32)
	//   err: [4:8] str_ptr [8:12] str_len
	var ret [12]byte
	wasm_governance_check_policy(
		strPtr(command), int32(len(command)),
		strPtr(callerUserID), int32(len(callerUserID)),
		strPtr(callerOrgID), int32(len(callerOrgID)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return PolicyVerdictDenied, readString(ret[:], 4)
	}
	return PolicyVerdict(readI32(ret[:], 4)), ""
}

// ── kotodama:auth/crypto@1.0.0 ────────────────────────────────────────

//go:wasmimport kotodama:auth/crypto@1.0.0 sha256
//go:noescape
func wasm_crypto_sha256(dataPtr, dataLen, retPtr int32)

//go:wasmimport kotodama:auth/crypto@1.0.0 sha256-hex
//go:noescape
func wasm_crypto_sha256_hex(dataPtr, dataLen, retPtr int32)

// CryptoSHA256 returns the SHA-256 digest (32 bytes) of data.
func CryptoSHA256(data []byte) ([]byte, string) {
	var ret [12]byte
	wasm_crypto_sha256(
		bytesPtr(data), int32(len(data)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return nil, "crypto sha256 failed"
	}
	return readBytes(ret[:], 4), ""
}

// CryptoSHA256Hex returns the hex-encoded SHA-256 digest (64 chars) of data.
func CryptoSHA256Hex(data []byte) string {
	var ret [12]byte
	wasm_crypto_sha256_hex(
		bytesPtr(data), int32(len(data)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return ""
	}
	return readString(ret[:], 4)
}

// ── kotodama:agent/agent (v1.0.0 route) ────────────────────────────────

// AgentRoute classifies input intent to best-matching tools without executing them.
// Returns ([]RouteMatch, errMsg).
func AgentRoute(input string) ([]RouteMatch, string) {
	reqBody, _ := json.Marshal(map[string]string{"input": input})
	resp, err := http.Post(
		"http://kotodama.internal/agent/route",
		"application/json",
		bytes.NewReader(reqBody),
	)
	if err != nil {
		return nil, err.Error()
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 400 {
		return nil, fmt.Sprintf("agent route error %d: %s", resp.StatusCode, string(body))
	}
	var matches []RouteMatch
	if err := json.Unmarshal(body, &matches); err != nil {
		return nil, err.Error()
	}
	return matches, ""
}

// ── kotodama:agent/agent (v1.0.0 react) ────────────────────────────────

// AgentReact runs a ReAct (Reasoning+Acting) loop. Returns (ReactResult, errMsg).
func AgentReact(task string, options ReactOptions) (ReactResult, string) {
	reqBody, _ := json.Marshal(map[string]interface{}{
		"task":    task,
		"options": options,
	})
	resp, err := http.Post(
		"http://kotodama.internal/agent/react",
		"application/json",
		bytes.NewReader(reqBody),
	)
	if err != nil {
		return ReactResult{}, err.Error()
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 400 {
		return ReactResult{}, fmt.Sprintf("agent react error %d: %s", resp.StatusCode, string(body))
	}
	var result ReactResult
	if err := json.Unmarshal(body, &result); err != nil {
		return ReactResult{}, err.Error()
	}
	return result, ""
}

// ── kotodama:workflow/activity-parallel ───────────────────────────────────

// ActivitySpawnParallel schedules N activities in parallel. Returns (batchID, errMsg).
func ActivitySpawnParallel(items []ActivityParallelItem) (string, string) {
	reqBody, _ := json.Marshal(items)
	resp, err := http.Post(
		"http://kotodama.internal/activity/spawn-parallel",
		"application/json",
		bytes.NewReader(reqBody),
	)
	if err != nil {
		return "", err.Error()
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 400 {
		return "", fmt.Sprintf("spawn-parallel error %d: %s", resp.StatusCode, string(body))
	}
	var result struct{ BatchID string `json:"batch_id"` }
	if err := json.Unmarshal(body, &result); err != nil {
		return "", err.Error()
	}
	return result.BatchID, ""
}

// ActivityAwaitAll blocks until all activities in a batch complete. Returns (results, errMsg).
func ActivityAwaitAll(batchID string, timeoutMs uint64) ([]ParallelActivityResult, string) {
	reqBody, _ := json.Marshal(map[string]interface{}{
		"batch_id":   batchID,
		"timeout_ms": timeoutMs,
	})
	resp, err := http.Post(
		"http://kotodama.internal/activity/await-all",
		"application/json",
		bytes.NewReader(reqBody),
	)
	if err != nil {
		return nil, err.Error()
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 400 {
		return nil, fmt.Sprintf("await-all error %d: %s", resp.StatusCode, string(body))
	}
	var results []ParallelActivityResult
	if err := json.Unmarshal(body, &results); err != nil {
		return nil, err.Error()
	}
	return results, ""
}

// ── kotodama:agent/agent (v1.0.0 converse) ────────────────────────────

//go:wasmimport kotodama:agent/agent@1.0.0 converse
//go:noescape
func wasm_agent_converse(msgsPtr, msgsLen int32, optsPtr, retPtr int32)

// AgentConverse invokes multi-turn conversation. Returns (ChatResponse, errMsg).
// Routes through outbound-http to kotodama.internal/agent/converse (host intercepts).
func AgentConverse(messages []Message, options ChatOptions) (ChatResponse, string) {
	msgsJSON, _ := json.Marshal(messages)
	optsJSON, _ := json.Marshal(options)
	reqBody, _ := json.Marshal(map[string]json.RawMessage{
		"messages": msgsJSON,
		"options":  optsJSON,
	})

	resp, err := http.Post(
		"http://kotodama.internal/agent/converse",
		"application/json",
		bytes.NewReader(reqBody),
	)
	if err != nil {
		return ChatResponse{}, err.Error()
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)

	if resp.StatusCode >= 400 {
		return ChatResponse{}, fmt.Sprintf("agent converse error %d: %s", resp.StatusCode, string(body))
	}

	var chatResp ChatResponse
	if err := json.Unmarshal(body, &chatResp); err != nil {
		return ChatResponse{}, err.Error()
	}
	return chatResp, ""
}

// ── kotodama:messaging/smtp ────────────────────────────────────────────────
//
// WIT:
//   connect(provider, auth-code, redirect-uri, user-id, org-id) -> result<connection-info, string>
//   disconnect(provider, user-id, org-id) -> result<_, string>
//   status(provider, user-id, org-id) -> result<list<u8>, string>
//   send-transactional(from-email, from-name, to, subject, body-text, body-html) -> result<string, string>

//go:wasmimport kotodama:messaging/smtp@1.0.0 connect
//go:noescape
func wasm_smtp_connect(providerDisc, authCodePtr, authCodeLen, redirectURIPtr, redirectURILen, userIDPtr, userIDLen, orgIDPtr, orgIDLen, retPtr int32)

//go:wasmimport kotodama:messaging/smtp@1.0.0 disconnect
//go:noescape
func wasm_smtp_disconnect(providerDisc, userIDPtr, userIDLen, orgIDPtr, orgIDLen, retPtr int32)

//go:wasmimport kotodama:messaging/smtp@1.0.0 status
//go:noescape
func wasm_smtp_status(providerDisc, userIDPtr, userIDLen, orgIDPtr, orgIDLen, retPtr int32)

//go:wasmimport kotodama:messaging/smtp@1.0.0 send-transactional
//go:noescape
func wasm_smtp_send_transactional(fromEmailPtr, fromEmailLen, fromNamePtr, fromNameLen, toPtr, toLen, subjectPtr, subjectLen, bodyTextPtr, bodyTextLen, bodyHTMLPtr, bodyHTMLLen, retPtr int32)

// SmtpConnect exchanges an OAuth auth code for tokens. Returns (ConnectionInfo, errMsg).
func SmtpConnect(provider SmtpProvider, authCode, redirectURI, userID, orgID string) (SmtpConnectionInfo, string) {
	// connection-info: provider(4) + email(ptr+len:8) + display_name(ptr+len:8) + connected(4) = 24
	// result: disc(4) + 24 = 28, or disc(4) + err(8) = 12
	var ret [28]byte
	wasm_smtp_connect(
		int32(provider),
		strPtr(authCode), int32(len(authCode)),
		strPtr(redirectURI), int32(len(redirectURI)),
		strPtr(userID), int32(len(userID)),
		strPtr(orgID), int32(len(orgID)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return SmtpConnectionInfo{}, readString(ret[:], 4)
	}
	return SmtpConnectionInfo{
		Provider:    SmtpProvider(readI32(ret[:], 4)),
		Email:       readString(ret[:], 8),
		DisplayName: readString(ret[:], 16),
		Connected:   readI32(ret[:], 24) != 0,
	}, ""
}

// SmtpDisconnect disconnects a provider. Returns errMsg.
func SmtpDisconnect(provider SmtpProvider, userID, orgID string) string {
	var ret [12]byte
	wasm_smtp_disconnect(
		int32(provider),
		strPtr(userID), int32(len(userID)),
		strPtr(orgID), int32(len(orgID)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// SmtpStatus returns connection status as JSON bytes. Returns (jsonBytes, errMsg).
func SmtpStatus(provider SmtpProvider, userID, orgID string) ([]byte, string) {
	var ret [12]byte
	wasm_smtp_status(
		int32(provider),
		strPtr(userID), int32(len(userID)),
		strPtr(orgID), int32(len(orgID)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}
	return readBytesAt(ret[:], 4), ""
}

// lowerStringList converts []string to a WASM list pointer for list<string>.
func lowerStringList(items []string) (int32, int32) {
	n := len(items)
	if n == 0 {
		return 0, 0
	}
	type strEntry struct{ ptr, len int32 }
	buf := make([]strEntry, n)
	for i, s := range items {
		buf[i] = strEntry{ptr: strPtr(s), len: int32(len(s))}
	}
	return int32(uintptr(unsafe.Pointer(&buf[0]))), int32(n)
}

// SmtpSendTransactional sends a transactional email via Resend. Returns (messageID, errMsg).
func SmtpSendTransactional(fromEmail, fromName string, to []string, subject, bodyText, bodyHTML string) (string, string) {
	toPtr, toLen := lowerStringList(to)
	var ret [12]byte
	wasm_smtp_send_transactional(
		strPtr(fromEmail), int32(len(fromEmail)),
		strPtr(fromName), int32(len(fromName)),
		toPtr, toLen,
		strPtr(subject), int32(len(subject)),
		strPtr(bodyText), int32(len(bodyText)),
		strPtr(bodyHTML), int32(len(bodyHTML)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return "", readString(ret[:], 4)
	}
	return readString(ret[:], 4), ""
}

// ── kotodama:observability/telemetry ──────────────────────────────────────────

//go:wasmimport kotodama:observability/telemetry@1.0.0 counter-add
//go:noescape
func wasm_telemetry_counter_add(namePtr, nameLen int32, value uint64, attrsPtr, attrsLen int32)

//go:wasmimport kotodama:observability/telemetry@1.0.0 gauge-set
//go:noescape
func wasm_telemetry_gauge_set(namePtr, nameLen int32, value uint64, attrsPtr, attrsLen int32)

//go:wasmimport kotodama:observability/telemetry@1.0.0 histogram-record
//go:noescape
func wasm_telemetry_histogram_record(namePtr, nameLen int32, value uint64, attrsPtr, attrsLen int32)

// lowerTelemetryAttrs encodes []TelemetryAttribute as list<attribute> canonical ABI.
// Each attribute record = (key_ptr, key_len, value_ptr, value_len) = 4×int32 = 16 bytes.
func lowerTelemetryAttrs(attrs []TelemetryAttribute) (int32, int32) {
	if len(attrs) == 0 {
		return 0, 0
	}
	type attrRecord struct{ kp, kl, vp, vl int32 }
	records := make([]attrRecord, len(attrs))
	for i, a := range attrs {
		records[i] = attrRecord{
			kp: strPtr(a.Key), kl: int32(len(a.Key)),
			vp: strPtr(a.Value), vl: int32(len(a.Value)),
		}
	}
	return int32(uintptr(unsafe.Pointer(&records[0]))), int32(len(records))
}

// f64ToU64 reinterprets a float64 as uint64 for WASM canonical ABI (f64 passed as i64/u64).
func f64ToU64(f float64) uint64 {
	return *(*uint64)(unsafe.Pointer(&f))
}

// TelemetryCounterAdd increments a monotonic counter (OTEL Sum).
func TelemetryCounterAdd(name string, value float64, attrs []TelemetryAttribute) {
	ap, al := lowerTelemetryAttrs(attrs)
	wasm_telemetry_counter_add(strPtr(name), int32(len(name)), f64ToU64(value), ap, al)
}

// TelemetryGaugeSet sets a gauge value (OTEL Gauge).
func TelemetryGaugeSet(name string, value float64, attrs []TelemetryAttribute) {
	ap, al := lowerTelemetryAttrs(attrs)
	wasm_telemetry_gauge_set(strPtr(name), int32(len(name)), f64ToU64(value), ap, al)
}

// TelemetryHistogramRecord records a histogram observation (OTEL Histogram).
func TelemetryHistogramRecord(name string, value float64, attrs []TelemetryAttribute) {
	ap, al := lowerTelemetryAttrs(attrs)
	wasm_telemetry_histogram_record(strPtr(name), int32(len(name)), f64ToU64(value), ap, al)
}

// ── kotodama:observability/access-log ────────────────────────────────────────

//go:wasmimport kotodama:observability/access-log@1.0.0 list-entries
//go:noescape
func wasm_access_log_list_entries(offset, limit, retPtr int32)

//go:wasmimport kotodama:observability/access-log@1.0.0 page-views
//go:noescape
func wasm_access_log_page_views(sinceMs, untilMs uint64, retPtr int32)

//go:wasmimport kotodama:observability/access-log@1.0.0 list-query-stats
//go:noescape
func wasm_access_log_list_query_stats(offset, limit, retPtr int32)

//go:wasmimport kotodama:observability/access-log@1.0.0 list-ips
//go:noescape
func wasm_access_log_list_ips(offset, limit, retPtr int32)

//go:wasmimport kotodama:observability/access-log@1.0.0 total-requests
//go:noescape
func wasm_access_log_total_requests(retPtr int32)

// readU64At reads a u64 from buf at byte offset off.
func readU64At(buf []byte, off int) uint64 {
	return *(*uint64)(unsafe.Pointer(uintptr(unsafe.Pointer(&buf[0])) + uintptr(off)))
}

// readU16At reads a u16 from buf at byte offset off.
func readU16At(buf []byte, off int) uint16 {
	return *(*uint16)(unsafe.Pointer(uintptr(unsafe.Pointer(&buf[0])) + uintptr(off)))
}

// readStringAt reads a WIT string (ptr+len at byte offsets off, off+4) from WASM memory.
func readStringAt(base uintptr, off int) string {
	sp := uintptr(*(*int32)(unsafe.Pointer(base + uintptr(off))))
	sl := int(*(*int32)(unsafe.Pointer(base + uintptr(off+4))))
	if sl == 0 || sp == 0 {
		return ""
	}
	return string(unsafe.Slice((*byte)(unsafe.Pointer(sp)), sl))
}

// AccessLogListEntries returns recent access log entries (newest first).
func AccessLogListEntries(offset, limit uint32) []AccessEntry {
	// Return: list<access-entry> = (ptr, len) = 8 bytes.
	var ret [8]byte
	wasm_access_log_list_entries(int32(offset), int32(limit), int32(uintptr(unsafe.Pointer(&ret[0]))))
	listPtr := uintptr(readI32(ret[:], 0))
	listLen := int(readI32(ret[:], 4))
	if listLen == 0 || listPtr == 0 {
		return nil
	}
	// access-entry canonical ABI layout (32-bit, all align ≤ 4):
	//   0:  u64  ts_unix_ms      (8)
	//   8:  string method        (8)
	//   16: string path          (8)
	//   24: u16 status           (2 + 2 pad)
	//   28: u64 latency_us       (8)
	//   36: string ip            (8)
	//   44: string user_agent    (8)
	//   52: string referer       (8)
	//   60: string user_id       (8)
	//   68: string org_id        (8)
	//   76: u64 bytes_in         (8)
	//   84: u64 bytes_out        (8)
	//   Total: 92 bytes per record.
	const recordSize = 92
	entries := make([]AccessEntry, listLen)
	for i := 0; i < listLen; i++ {
		base := listPtr + uintptr(i)*recordSize
		entries[i] = AccessEntry{
			TsUnixMs:  *(*uint64)(unsafe.Pointer(base)),
			Method:    readStringAt(base, 8),
			Path:      readStringAt(base, 16),
			Status:    *(*uint16)(unsafe.Pointer(base + 24)),
			LatencyUs: *(*uint64)(unsafe.Pointer(base + 28)),
			IP:        readStringAt(base, 36),
			UserAgent: readStringAt(base, 44),
			Referer:   readStringAt(base, 52),
			UserID:    readStringAt(base, 60),
			OrgID:     readStringAt(base, 68),
			BytesIn:   *(*uint64)(unsafe.Pointer(base + 76)),
			BytesOut:  *(*uint64)(unsafe.Pointer(base + 84)),
		}
	}
	return entries
}

// AccessLogPageViews returns aggregated page views for a time range (0 = no bound).
func AccessLogPageViews(sinceUnixMs, untilUnixMs uint64) []PageView {
	var ret [8]byte
	wasm_access_log_page_views(sinceUnixMs, untilUnixMs, int32(uintptr(unsafe.Pointer(&ret[0]))))
	listPtr := uintptr(readI32(ret[:], 0))
	listLen := int(readI32(ret[:], 4))
	if listLen == 0 || listPtr == 0 {
		return nil
	}
	// page-view: string path (8) + u64 count (8) + u64 unique_ips (8) = 24 bytes.
	const recordSize = 24
	views := make([]PageView, listLen)
	for i := 0; i < listLen; i++ {
		base := listPtr + uintptr(i)*recordSize
		views[i] = PageView{
			Path:      readStringAt(base, 0),
			Count:     *(*uint64)(unsafe.Pointer(base + 8)),
			UniqueIPs: *(*uint64)(unsafe.Pointer(base + 16)),
		}
	}
	return views
}

// AccessLogListQueryStats returns recent query execution stats (newest first).
func AccessLogListQueryStats(offset, limit uint32) []QueryStat {
	var ret [8]byte
	wasm_access_log_list_query_stats(int32(offset), int32(limit), int32(uintptr(unsafe.Pointer(&ret[0]))))
	listPtr := uintptr(readI32(ret[:], 0))
	listLen := int(readI32(ret[:], 4))
	if listLen == 0 || listPtr == 0 {
		return nil
	}
	// query-stat: u64 ts (8) + string query_type (8) + string query_text (8) + u64 latency_us (8) + u64 rows (8) + string error (8) = 48 bytes.
	const recordSize = 48
	stats := make([]QueryStat, listLen)
	for i := 0; i < listLen; i++ {
		base := listPtr + uintptr(i)*recordSize
		stats[i] = QueryStat{
			TsUnixMs:  *(*uint64)(unsafe.Pointer(base)),
			QueryType: readStringAt(base, 8),
			QueryText: readStringAt(base, 16),
			LatencyUs: *(*uint64)(unsafe.Pointer(base + 24)),
			Rows:      *(*uint64)(unsafe.Pointer(base + 32)),
			Error:     readStringAt(base, 40),
		}
	}
	return stats
}

// AccessLogListIPs returns observed IP addresses (most recent first).
func AccessLogListIPs(offset, limit uint32) []IPInfo {
	var ret [8]byte
	wasm_access_log_list_ips(int32(offset), int32(limit), int32(uintptr(unsafe.Pointer(&ret[0]))))
	listPtr := uintptr(readI32(ret[:], 0))
	listLen := int(readI32(ret[:], 4))
	if listLen == 0 || listPtr == 0 {
		return nil
	}
	// ip-info: string ip (8) + u64 first_seen (8) + u64 last_seen (8) + u64 request_count (8) + string user_agent (8) = 40 bytes.
	const recordSize = 40
	ips := make([]IPInfo, listLen)
	for i := 0; i < listLen; i++ {
		base := listPtr + uintptr(i)*recordSize
		ips[i] = IPInfo{
			IP:           readStringAt(base, 0),
			FirstSeenMs:  *(*uint64)(unsafe.Pointer(base + 8)),
			LastSeenMs:   *(*uint64)(unsafe.Pointer(base + 16)),
			RequestCount: *(*uint64)(unsafe.Pointer(base + 24)),
			UserAgent:    readStringAt(base, 32),
		}
	}
	return ips
}

// AccessLogTotalRequests returns the total request count for this component.
func AccessLogTotalRequests() uint64 {
	var ret [8]byte
	wasm_access_log_total_requests(int32(uintptr(unsafe.Pointer(&ret[0]))))
	return *(*uint64)(unsafe.Pointer(&ret[0]))
}

// ── kotodama:observability/ocel ───────────────────────────────────────────────

//go:wasmimport kotodama:observability/ocel@1.0.0 emit-event
//go:noescape
func wasm_ocel_emit_event(etPtr, etLen, attrsPtr, attrsLen, objsPtr, objsLen, retPtr int32)

//go:wasmimport kotodama:observability/ocel@1.0.0 upsert-object
//go:noescape
func wasm_ocel_upsert_object(oidPtr, oidLen, otPtr, otLen, attrsPtr, attrsLen, retPtr int32)

//go:wasmimport kotodama:observability/ocel@1.0.0 add-object-edge
//go:noescape
func wasm_ocel_add_object_edge(srcPtr, srcLen, dstPtr, dstLen, relPtr, relLen, qualPtr, qualLen, retPtr int32)

//go:wasmimport kotodama:observability/ocel@1.0.0 export-json
//go:noescape
func wasm_ocel_export_json(retPtr int32)

// lowerOcelObjectRefs encodes []OcelObjectRef as list<object-ref> canonical ABI.
// Each object-ref = (oid_ptr, oid_len, otype_ptr, otype_len, qual_ptr, qual_len, role_ptr, role_len) = 8×int32 = 32 bytes.
func lowerOcelObjectRefs(refs []OcelObjectRef) (int32, int32) {
	if len(refs) == 0 {
		return 0, 0
	}
	type refRecord struct{ op, ol, tp, tl, qp, ql, rp, rl int32 }
	records := make([]refRecord, len(refs))
	for i, r := range refs {
		records[i] = refRecord{
			op: strPtr(r.ObjectID), ol: int32(len(r.ObjectID)),
			tp: strPtr(r.ObjectType), tl: int32(len(r.ObjectType)),
			qp: strPtr(r.Qualifier), ql: int32(len(r.Qualifier)),
			rp: strPtr(r.Role), rl: int32(len(r.Role)),
		}
	}
	return int32(uintptr(unsafe.Pointer(&records[0]))), int32(len(records))
}

// OcelEmitEvent emits an OCEL v2 event. Returns (eventID, errMsg).
func OcelEmitEvent(eventType string, attrsJSON string, objects []OcelObjectRef) (string, string) {
	op, ol := lowerOcelObjectRefs(objects)
	var ret [12]byte
	wasm_ocel_emit_event(
		strPtr(eventType), int32(len(eventType)),
		strPtr(attrsJSON), int32(len(attrsJSON)),
		op, ol,
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return "", readString(ret[:], 4)
	}
	return readString(ret[:], 4), ""
}

// OcelUpsertObject upserts an OCEL v2 object. Returns errMsg.
func OcelUpsertObject(objectID, objectType, attrsJSON string) string {
	var ret [12]byte
	wasm_ocel_upsert_object(
		strPtr(objectID), int32(len(objectID)),
		strPtr(objectType), int32(len(objectType)),
		strPtr(attrsJSON), int32(len(attrsJSON)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// OcelAddObjectEdge creates an O2O edge. Returns errMsg.
func OcelAddObjectEdge(srcObjectID, dstObjectID, relType, qualifier string) string {
	var ret [12]byte
	wasm_ocel_add_object_edge(
		strPtr(srcObjectID), int32(len(srcObjectID)),
		strPtr(dstObjectID), int32(len(dstObjectID)),
		strPtr(relType), int32(len(relType)),
		strPtr(qualifier), int32(len(qualifier)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// OcelExportJSON exports the OCEL log as OCEL 2.0 JSON. Returns (json, errMsg).
func OcelExportJSON() (string, string) {
	var ret [12]byte
	wasm_ocel_export_json(int32(uintptr(unsafe.Pointer(&ret[0]))))
	if readI32(ret[:], 0) != 0 {
		return "", readString(ret[:], 4)
	}
	return readString(ret[:], 4), ""
}

// ── kotodama:observability/pubsub ───────────────────────────────────────────────

//go:wasmimport kotodama:observability/pubsub@1.0.0 publish
//go:noescape
func wasm_pubsub_publish(topicPtr, topicLen, payloadPtr, payloadLen, metaPtr, metaLen, retPtr int32)

//go:wasmimport kotodama:observability/pubsub@1.0.0 pull
//go:noescape
func wasm_pubsub_pull(topicPtr, topicLen, subIdPtr, subIdLen, maxMsgs, retPtr int32)

//go:wasmimport kotodama:observability/pubsub@1.0.0 ack
//go:noescape
func wasm_pubsub_ack(topicPtr, topicLen, subIdPtr, subIdLen int32, seqLo, seqHi, retPtr int32)

//go:wasmimport kotodama:observability/pubsub@1.0.0 cursor
//go:noescape
func wasm_pubsub_cursor(topicPtr, topicLen, subIdPtr, subIdLen, retPtr int32)

// PubsubPublish publishes a message to a topic. Returns (seq, errMsg).
func PubsubPublish(topic string, payload []byte, metadata map[string]string) (uint64, string) {
	metaJSON, _ := json.Marshal(metadata)
	metaStr := string(metaJSON)
	// Return area: result<u64, string>
	//   [0:4] tag, [4:8] pad, [8:16] u64 | [8:12] str_ptr [12:16] str_len
	var ret [16]byte
	wasm_pubsub_publish(
		strPtr(topic), int32(len(topic)),
		bytesPtr(payload), int32(len(payload)),
		strPtr(metaStr), int32(len(metaStr)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return 0, readString(ret[:], 8)
	}
	return readU64(ret[:], 8), ""
}

// PubsubPull fetches unacknowledged messages. Returns (messages, errMsg).
func PubsubPull(topic, subscriberID string, maxMessages uint32) ([]PublishedMessage, string) {
	// Return area: result<list<u8>, string>
	//   [0:4] tag, ok: [4:8] ptr [8:12] len, err: [4:8] str_ptr [8:12] str_len
	var ret [12]byte
	wasm_pubsub_pull(
		strPtr(topic), int32(len(topic)),
		strPtr(subscriberID), int32(len(subscriberID)),
		int32(maxMessages),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}
	data := readBytesAt(ret[:], 4)
	if len(data) == 0 {
		return nil, ""
	}
	var msgs []PublishedMessage
	if err := json.Unmarshal(data, &msgs); err != nil {
		return nil, "pubsub: decode pull response: " + err.Error()
	}
	return msgs, ""
}

// PubsubAck acknowledges messages up to seq. Returns errMsg.
func PubsubAck(topic, subscriberID string, seq uint64) string {
	var ret [12]byte
	wasm_pubsub_ack(
		strPtr(topic), int32(len(topic)),
		strPtr(subscriberID), int32(len(subscriberID)),
		int32(seq), int32(seq>>32),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// PubsubCursor returns (lastAckedSeq, pendingCount, errMsg).
func PubsubCursor(topic, subscriberID string) (uint64, uint64, string) {
	// Return area: result<tuple<u64, u64>, string>
	//   [0:4] tag, [4:8] pad, ok: [8:16] u64_a [16:24] u64_b, err: [8:12] str_ptr [12:16] str_len
	var ret [24]byte
	wasm_pubsub_cursor(
		strPtr(topic), int32(len(topic)),
		strPtr(subscriberID), int32(len(subscriberID)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return 0, 0, readString(ret[:], 8)
	}
	return readU64(ret[:], 8), readU64(ret[:], 16), ""
}

// ── kotodama:observability/secrets ──────────────────────────────────────────────

//go:wasmimport kotodama:observability/secrets@1.0.0 get
//go:noescape
func wasm_secrets_get(storePtr, storeLen, namePtr, nameLen, retPtr int32)

//go:wasmimport kotodama:observability/secrets@1.0.0 set
//go:noescape
func wasm_secrets_set(storePtr, storeLen, namePtr, nameLen, valPtr, valLen, retPtr int32)

//go:wasmimport kotodama:observability/secrets@1.0.0 delete
//go:noescape
func wasm_secrets_delete(storePtr, storeLen, namePtr, nameLen, retPtr int32)

//go:wasmimport kotodama:observability/secrets@1.0.0 list-names
//go:noescape
func wasm_secrets_list_names(storePtr, storeLen, retPtr int32)

// SecretsGet retrieves a secret. Returns (value, found, errMsg).
func SecretsGet(store, name string) ([]byte, bool, string) {
	var ret [16]byte
	wasm_secrets_get(
		strPtr(store), int32(len(store)),
		strPtr(name), int32(len(name)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return nil, false, readString(ret[:], 4)
	}
	if readI32(ret[:], 4) == 0 { // None
		return nil, false, ""
	}
	ptr := uintptr(readI32(ret[:], 8))
	ln := int(readI32(ret[:], 12))
	val := make([]byte, ln)
	copy(val, unsafe.Slice((*byte)(unsafe.Pointer(ptr)), ln))
	return val, true, ""
}

// SecretsSet stores a secret. Returns errMsg.
func SecretsSet(store, name string, value []byte) string {
	var ret [12]byte
	wasm_secrets_set(
		strPtr(store), int32(len(store)),
		strPtr(name), int32(len(name)),
		bytesPtr(value), int32(len(value)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// SecretsDelete removes a secret. Returns errMsg.
func SecretsDelete(store, name string) string {
	var ret [12]byte
	wasm_secrets_delete(
		strPtr(store), int32(len(store)),
		strPtr(name), int32(len(name)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// SecretsListNames returns secret names in a store. Returns (names, errMsg).
func SecretsListNames(store string) ([]string, string) {
	var ret [12]byte
	wasm_secrets_list_names(
		strPtr(store), int32(len(store)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}
	data := readBytesAt(ret[:], 4)
	if len(data) == 0 {
		return nil, ""
	}
	var names []string
	if err := json.Unmarshal(data, &names); err != nil {
		return nil, "secrets: decode list: " + err.Error()
	}
	return names, ""
}

// ── kotodama:workflow/lock ─────────────────────────────────────────────────

//go:wasmimport kotodama:workflow/lock@1.0.0 try-lock
//go:noescape
func wasm_lock_try_lock(namePtr, nameLen, ownerPtr, ownerLen int32, ttlMsLo, ttlMsHi, retPtr int32)

//go:wasmimport kotodama:workflow/lock@1.0.0 unlock
//go:noescape
func wasm_lock_unlock(namePtr, nameLen, tokenPtr, tokenLen, retPtr int32)

//go:wasmimport kotodama:workflow/lock@1.0.0 renew
//go:noescape
func wasm_lock_renew(namePtr, nameLen, tokenPtr, tokenLen int32, ttlMsLo, ttlMsHi, retPtr int32)

// LockTryLock attempts to acquire a distributed lock. Returns (response, errMsg).
func LockTryLock(lockName, owner string, ttlMs uint64) (LockResponse, string) {
	// Return area: result<lock-response, string>
	// lock-response = { success: bool(i32), lock-token: (ptr, len) } = 12 bytes
	//   [0:4] tag, ok: [4:8] success(i32) [8:12] token_ptr [12:16] token_len
	//   err: [4:8] str_ptr [8:12] str_len
	var ret [16]byte
	wasm_lock_try_lock(
		strPtr(lockName), int32(len(lockName)),
		strPtr(owner), int32(len(owner)),
		int32(ttlMs), int32(ttlMs>>32),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return LockResponse{}, readString(ret[:], 4)
	}
	success := readI32(ret[:], 4) != 0
	token := readString(ret[:], 8)
	return LockResponse{Success: success, LockToken: token}, ""
}

// LockUnlock releases a distributed lock. Returns errMsg.
func LockUnlock(lockName, lockToken string) string {
	var ret [12]byte
	wasm_lock_unlock(
		strPtr(lockName), int32(len(lockName)),
		strPtr(lockToken), int32(len(lockToken)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// LockRenew extends the TTL of a held lock. Returns errMsg.
func LockRenew(lockName, lockToken string, ttlMs uint64) string {
	var ret [12]byte
	wasm_lock_renew(
		strPtr(lockName), int32(len(lockName)),
		strPtr(lockToken), int32(len(lockToken)),
		int32(ttlMs), int32(ttlMs>>32),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// ── kotodama:workflow/virtual-actor ────────────────────────────────────────

//go:wasmimport kotodama:workflow/virtual-actor@1.0.0 register-actor-type
//go:noescape
func wasm_va_register(typePtr, typeLen, methodsPtr, methodsLen int32, idleMsLo, idleMsHi, reentrancy, maxQueue, retPtr int32)

//go:wasmimport kotodama:workflow/virtual-actor@1.0.0 invoke
//go:noescape
func wasm_va_invoke(typePtr, typeLen, idPtr, idLen, methodPtr, methodLen, payloadPtr, payloadLen, retPtr int32)

//go:wasmimport kotodama:workflow/virtual-actor@1.0.0 list-active
//go:noescape
func wasm_va_list_active(typePtr, typeLen, retPtr int32)

//go:wasmimport kotodama:workflow/virtual-actor@1.0.0 deactivate
//go:noescape
func wasm_va_deactivate(typePtr, typeLen, idPtr, idLen, retPtr int32)

//go:wasmimport kotodama:workflow/virtual-actor@1.0.0 schedule-method
//go:noescape
func wasm_va_schedule(typePtr, typeLen, idPtr, idLen, methodPtr, methodLen, payloadPtr, payloadLen int32, dueMsLo, dueMsHi, retPtr int32)

//go:wasmimport kotodama:workflow/virtual-actor@1.0.0 cancel-schedule
//go:noescape
func wasm_va_cancel_schedule(schedIdPtr, schedIdLen, retPtr int32)

// VirtualActorRegister registers an actor type. methods is a list of method names. Returns errMsg.
func VirtualActorRegister(actorType string, methods []string, idleTimeoutMs uint64, reentrancy bool, maxQueueDepth uint32) string {
	methodsJSON, _ := json.Marshal(methods)
	methodsStr := string(methodsJSON)
	reentrant := int32(0)
	if reentrancy {
		reentrant = 1
	}
	var ret [12]byte
	wasm_va_register(
		strPtr(actorType), int32(len(actorType)),
		strPtr(methodsStr), int32(len(methodsStr)),
		int32(idleTimeoutMs), int32(idleTimeoutMs>>32),
		reentrant, int32(maxQueueDepth),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// VirtualActorInvoke invokes a method on a virtual actor. Returns (response, errMsg).
func VirtualActorInvoke(actorType, actorID, method string, payload []byte) ([]byte, string) {
	var ret [12]byte
	wasm_va_invoke(
		strPtr(actorType), int32(len(actorType)),
		strPtr(actorID), int32(len(actorID)),
		strPtr(method), int32(len(method)),
		bytesPtr(payload), int32(len(payload)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}
	return readBytesAt(ret[:], 4), ""
}

// VirtualActorListActive returns active actor IDs for a type. Returns (ids, errMsg).
func VirtualActorListActive(actorType string) ([]string, string) {
	var ret [12]byte
	wasm_va_list_active(
		strPtr(actorType), int32(len(actorType)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}
	data := readBytesAt(ret[:], 4)
	if len(data) == 0 {
		return nil, ""
	}
	var ids []string
	if err := json.Unmarshal(data, &ids); err != nil {
		return nil, "virtual-actor: decode list: " + err.Error()
	}
	return ids, ""
}

// VirtualActorDeactivate explicitly deactivates an actor. Returns errMsg.
func VirtualActorDeactivate(actorType, actorID string) string {
	var ret [12]byte
	wasm_va_deactivate(
		strPtr(actorType), int32(len(actorType)),
		strPtr(actorID), int32(len(actorID)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// VirtualActorScheduleMethod schedules a future method call. Returns (scheduleID, errMsg).
func VirtualActorScheduleMethod(actorType, actorID, method string, payload []byte, dueMs uint64) (string, string) {
	var ret [12]byte
	wasm_va_schedule(
		strPtr(actorType), int32(len(actorType)),
		strPtr(actorID), int32(len(actorID)),
		strPtr(method), int32(len(method)),
		bytesPtr(payload), int32(len(payload)),
		int32(dueMs), int32(dueMs>>32),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return "", readString(ret[:], 4)
	}
	return readString(ret[:], 4), ""
}

// VirtualActorCancelSchedule cancels a scheduled method call. Returns errMsg.
func VirtualActorCancelSchedule(scheduleID string) string {
	var ret [12]byte
	wasm_va_cancel_schedule(
		strPtr(scheduleID), int32(len(scheduleID)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// ── kotodama:messaging/conversation ──────────────────────────────────────────

//go:wasmimport kotodama:messaging/conversation@1.0.0 create-session
//go:noescape
func wasm_conversation_create_session(topicPtr, topicLen, participantsPtr, participantsLen, retPtr int32)

//go:wasmimport kotodama:messaging/conversation@1.0.0 send-message
//go:noescape
func wasm_conversation_send_message(sessionPtr, sessionLen, contentPtr, contentLen, replyToTag, replyToPtr, replyToLen, retPtr int32)

//go:wasmimport kotodama:messaging/conversation@1.0.0 get-history
//go:noescape
func wasm_conversation_get_history(sessionPtr, sessionLen, offset, limit, retPtr int32)

//go:wasmimport kotodama:messaging/conversation@1.0.0 get-session
//go:noescape
func wasm_conversation_get_session(sessionPtr, sessionLen, retPtr int32)

//go:wasmimport kotodama:messaging/conversation@1.0.0 list-sessions
//go:noescape
func wasm_conversation_list_sessions(offset, limit, retPtr int32)

//go:wasmimport kotodama:messaging/conversation@1.0.0 close-session
//go:noescape
func wasm_conversation_close_session(sessionPtr, sessionLen, retPtr int32)

// ConversationCreateSession creates a new multi-agent conversation.
// Returns (sessionJSON, errMsg).
func ConversationCreateSession(topic string, participantNanoids []string) (string, string) {
	// Encode list<string> as flat canonical ABI: concatenated (ptr, len) pairs.
	// For simplicity, we pass JSON and let the host decode.
	// WIT list<string> flattens to (ptr, len) where ptr points to an array of (ptr, len) pairs.
	// However, our WIT uses list<string> which is complex in canonical ABI.
	// Workaround: encode as JSON in a single string parameter... but WIT is typed.
	// Actually for list<string>, canonical ABI: ptr to array of (i32 ptr, i32 len), count.
	// Let's build the flat buffer.
	flatBuf, flatLen := flattenStringList(participantNanoids)
	var ret [12]byte
	wasm_conversation_create_session(
		strPtr(topic), int32(len(topic)),
		int32(uintptr(flatBuf)), flatLen,
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return "", readString(ret[:], 4)
	}
	return readString(ret[:], 4), ""
}

// ConversationSendMessage sends a message to a conversation session.
// Returns (messageJSON, errMsg).
func ConversationSendMessage(sessionID, content string, replyTo *string) (string, string) {
	replyTag, replyPtr, replyLen := optionStringArgs("")
	if replyTo != nil {
		replyTag, replyPtr, replyLen = optionStringArgs(*replyTo)
		if *replyTo != "" {
			replyTag = 1
		}
	}
	var ret [12]byte
	wasm_conversation_send_message(
		strPtr(sessionID), int32(len(sessionID)),
		strPtr(content), int32(len(content)),
		replyTag, replyPtr, replyLen,
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return "", readString(ret[:], 4)
	}
	return readString(ret[:], 4), ""
}

// ConversationGetHistory fetches messages from a conversation session.
// Returns (messagesJSON, errMsg).
func ConversationGetHistory(sessionID string, offset, limit uint32) (string, string) {
	var ret [12]byte
	wasm_conversation_get_history(
		strPtr(sessionID), int32(len(sessionID)),
		int32(offset), int32(limit),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return "", readString(ret[:], 4)
	}
	return readString(ret[:], 4), ""
}

// ConversationGetSession gets session metadata.
// Returns (sessionJSON, found, errMsg).
func ConversationGetSession(sessionID string) (string, bool, string) {
	var ret [16]byte
	wasm_conversation_get_session(
		strPtr(sessionID), int32(len(sessionID)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return "", false, readString(ret[:], 4)
	}
	if readI32(ret[:], 4) == 0 { // None
		return "", false, ""
	}
	return readString(ret[:], 8), true, ""
}

// ConversationListSessions lists sessions this actor participates in.
// Returns (sessionsJSON, errMsg).
func ConversationListSessions(offset, limit uint32) (string, string) {
	var ret [12]byte
	wasm_conversation_list_sessions(
		int32(offset), int32(limit),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return "", readString(ret[:], 4)
	}
	return readString(ret[:], 4), ""
}

// ConversationCloseSession closes a conversation session. Returns errMsg.
func ConversationCloseSession(sessionID string) string {
	var ret [12]byte
	wasm_conversation_close_session(
		strPtr(sessionID), int32(len(sessionID)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// flattenStringList encodes a Go string slice into canonical ABI list<string> layout.
// Returns (pointer to flat buffer, count as i32).
// Layout: array of (ptr: i32, len: i32) tuples, 8 bytes each.
func flattenStringList(ss []string) (unsafe.Pointer, int32) {
	if len(ss) == 0 {
		return unsafe.Pointer(uintptr(0)), 0
	}
	// Each entry is 8 bytes (ptr i32 + len i32).
	buf := make([]byte, len(ss)*8)
	for i, s := range ss {
		off := i * 8
		p := strPtr(s)
		l := int32(len(s))
		buf[off] = byte(p)
		buf[off+1] = byte(p >> 8)
		buf[off+2] = byte(p >> 16)
		buf[off+3] = byte(p >> 24)
		buf[off+4] = byte(l)
		buf[off+5] = byte(l >> 8)
		buf[off+6] = byte(l >> 16)
		buf[off+7] = byte(l >> 24)
	}
	return unsafe.Pointer(&buf[0]), int32(len(ss))
}

// ── kotodama:agent/identity ──────────────────────────────────────────────

//go:wasmimport kotodama:agent/identity@1.0.0 register
//go:noescape
func wasm_identity_register(cardJsonPtr, cardJsonLen, retPtr int32)

//go:wasmimport kotodama:agent/identity@1.0.0 resolve
//go:noescape
func wasm_identity_resolve(nanoidPtr, nanoidLen, retPtr int32)

//go:wasmimport kotodama:agent/identity@1.0.0 resolve-address
//go:noescape
func wasm_identity_resolve_address(addrPtr, addrLen, retPtr int32)

//go:wasmimport kotodama:agent/identity@1.0.0 list-actors
//go:noescape
func wasm_identity_list_actors(offset, limit, retPtr int32)

// IdentityRegister registers or updates this actor's card. Returns errMsg.
func IdentityRegister(card ActorCard) string {
	cardJSON, err := json.Marshal(card)
	if err != nil {
		return "identity: marshal card: " + err.Error()
	}
	cardStr := string(cardJSON)
	var ret [12]byte
	wasm_identity_register(
		strPtr(cardStr), int32(len(cardStr)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// IdentityResolve looks up an actor card by nanoid. Returns (card, found, errMsg).
func IdentityResolve(nanoid string) (*ActorCard, bool, string) {
	// Return area: result<option<string>, string>
	//   [0:4] tag, ok: [4:8] option_tag, Some: [8:12] str_ptr [12:16] str_len
	//   err: [4:8] str_ptr [8:12] str_len
	var ret [16]byte
	wasm_identity_resolve(
		strPtr(nanoid), int32(len(nanoid)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return nil, false, readString(ret[:], 4)
	}
	if readI32(ret[:], 4) == 0 { // None
		return nil, false, ""
	}
	jsonStr := readString(ret[:], 8)
	var card ActorCard
	if err := json.Unmarshal([]byte(jsonStr), &card); err != nil {
		return nil, false, "identity: decode card: " + err.Error()
	}
	return &card, true, ""
}

// IdentityResolveAddress resolves an address to an actor card. Returns (card, found, errMsg).
func IdentityResolveAddress(address string) (*ActorCard, bool, string) {
	var ret [16]byte
	wasm_identity_resolve_address(
		strPtr(address), int32(len(address)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return nil, false, readString(ret[:], 4)
	}
	if readI32(ret[:], 4) == 0 { // None
		return nil, false, ""
	}
	jsonStr := readString(ret[:], 8)
	var card ActorCard
	if err := json.Unmarshal([]byte(jsonStr), &card); err != nil {
		return nil, false, "identity: decode card: " + err.Error()
	}
	return &card, true, ""
}

// IdentityListActors lists all registered actors. Returns (cards, errMsg).
func IdentityListActors(offset, limit uint32) ([]ActorCard, string) {
	// Return area: result<string, string>
	//   [0:4] tag, ok: [4:8] str_ptr [8:12] str_len
	//   err: [4:8] str_ptr [8:12] str_len
	var ret [12]byte
	wasm_identity_list_actors(
		int32(offset), int32(limit),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}
	jsonStr := readString(ret[:], 4)
	var cards []ActorCard
	if err := json.Unmarshal([]byte(jsonStr), &cards); err != nil {
		return nil, "identity: decode list: " + err.Error()
	}
	return cards, ""
}

// ── kotodama:agent/capability ───────────────────────────────────────────

//go:wasmimport kotodama:agent/capability@1.0.0 declare
//go:noescape
func wasm_capability_declare(capJsonPtr, capJsonLen, retPtr int32)

//go:wasmimport kotodama:agent/capability@1.0.0 revoke
//go:noescape
func wasm_capability_revoke(idPtr, idLen, retPtr int32)

//go:wasmimport kotodama:agent/capability@1.0.0 list-own
//go:noescape
func wasm_capability_list_own(retPtr int32)

//go:wasmimport kotodama:agent/capability@1.0.0 discover
//go:noescape
func wasm_capability_discover(tagTag, tagPtr, tagLen, statusTag, statusVal, offset, limit, retPtr int32)

// CapabilityDeclare declares a capability this actor provides. Returns errMsg.
func CapabilityDeclare(cap ActorCapability) string {
	capJSON, err := json.Marshal(cap)
	if err != nil {
		return "capability: marshal: " + err.Error()
	}
	capStr := string(capJSON)
	var ret [12]byte
	wasm_capability_declare(
		strPtr(capStr), int32(len(capStr)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// CapabilityRevoke revokes a previously declared capability. Returns errMsg.
func CapabilityRevoke(id string) string {
	var ret [12]byte
	wasm_capability_revoke(
		strPtr(id), int32(len(id)),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return readString(ret[:], 4)
	}
	return ""
}

// CapabilityListOwn lists this actor's declared capabilities. Returns (caps, errMsg).
func CapabilityListOwn() ([]ActorCapability, string) {
	var ret [12]byte
	wasm_capability_list_own(int32(uintptr(unsafe.Pointer(&ret[0]))))
	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}
	jsonStr := readString(ret[:], 4)
	var caps []ActorCapability
	if err := json.Unmarshal([]byte(jsonStr), &caps); err != nil {
		return nil, "capability: decode list: " + err.Error()
	}
	return caps, ""
}

// CapabilityDiscover searches for capabilities across all actors. Returns (entries, errMsg).
func CapabilityDiscover(tag *string, status *CapabilityStatus, offset, limit uint32) ([]CapabilityDiscoveryEntry, string) {
	// Encode option<string> tag: tag=0 for None, tag=1 + ptr+len for Some
	tagTagVal := int32(0)
	tagPtrVal := int32(0)
	tagLenVal := int32(0)
	if tag != nil {
		tagTagVal = 1
		tagPtrVal = strPtr(*tag)
		tagLenVal = int32(len(*tag))
	}

	// Encode option<capability-status>: tag=0 for None, tag=1 + enum discriminant for Some
	statusTagVal := int32(0)
	statusValVal := int32(0)
	if status != nil {
		statusTagVal = 1
		switch *status {
		case CapabilityStatusPlanned:
			statusValVal = 0
		case CapabilityStatusDeveloping:
			statusValVal = 1
		case CapabilityStatusOperational:
			statusValVal = 2
		case CapabilityStatusRetired:
			statusValVal = 3
		}
	}

	var ret [12]byte
	wasm_capability_discover(
		tagTagVal, tagPtrVal, tagLenVal,
		statusTagVal, statusValVal,
		int32(offset), int32(limit),
		int32(uintptr(unsafe.Pointer(&ret[0]))),
	)
	if readI32(ret[:], 0) != 0 {
		return nil, readString(ret[:], 4)
	}
	jsonStr := readString(ret[:], 4)
	var entries []CapabilityDiscoveryEntry
	if err := json.Unmarshal([]byte(jsonStr), &entries); err != nil {
		return nil, "capability: decode discover: " + err.Error()
	}
	return entries, ""
}

// readBytesAt reads a list<u8> from a return buffer at offset (ptr at off, len at off+4).
func readBytesAt(buf []byte, off int) []byte {
	ptr := uintptr(readI32(buf, off))
	ln := int(readI32(buf, off+4))
	if ln == 0 || ptr == 0 {
		return nil
	}
	out := make([]byte, ln)
	copy(out, unsafe.Slice((*byte)(unsafe.Pointer(ptr)), ln))
	return out
}
