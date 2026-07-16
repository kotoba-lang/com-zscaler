//go:build tinygo

package kotodama

// exports.go — WIT canonical ABI export implementations for kotodama:{core,auth,messaging,storage,agent,workflow,observability,forms}@1.0.0.
// Excluded from wasip2 builds: TinyGo 0.40+ generates cabi_realloc and WIT
// exports natively for the wasip2 target via Component Model support.
//
// The host (kotodama-engine) calls these functions directly via wasmtime bindgen!.
// No JSON, no base64, no HTTP wrapper — pure typed records via canonical ABI.
//
// Export names use the full WIT interface path syntax:
//   kotodama:core/http-handler#handle
//   kotodama:messaging/w-handler#handle-commit
//
// Canonical ABI parameter layout:
//   - Complex return values use an out-pointer as the first parameter.
//   - string → (ptr: i32, len: i32) flat params.
//   - list<T> → (ptr: i32, len: i32) flat params.
//   - record → each field flattened in order.
//
// Memory allocated in return areas is owned by the host after the call.
// We must NOT reference host-returned pointers after returning.

import (
	"bytes"
	"net/http"
	"strconv"
	"strings"
	"unsafe"
)

// ── kotodama:core/http-handler#handle ──────────────────────────────────
//
// WIT: handle(req: request) -> response
//
// Core WASM signature (canonical ABI lowered):
//   (method_ptr: i32, method_len: i32,
//    url_ptr: i32, url_len: i32,
//    headers_ptr: i32, headers_len: i32, -- list<tuple<string,string>>
//    body_ptr: i32, body_len: i32) -> (resp_ptr: i32)
//
// response has 5 flat values (> MAX_FLAT_RESULTS=1), so the callee allocates
// the record and returns a pointer. No caller-provided out-ptr.
//
// Response record layout (20 bytes, alignment 4):
//   [0:2]  status u16  [2:4] padding
//   [4:8]  headers_ptr
//   [8:12] headers_len
//   [12:16] body_ptr
//   [16:20] body_len

//export kotodama:core/http-handler@1.0.0#handle
func kotodamaRuntimeHttpHandlerHandle(
	methodPtr, methodLen int32,
	urlPtr, urlLen int32,
	headersPtr, headersLen int32,
	bodyPtr, bodyLen int32,
) int32 {
	method := liftString(methodPtr, methodLen)
	rawURL := liftString(urlPtr, urlLen)
	headers := liftHeaders(headersPtr, headersLen)
	body := liftBytes(bodyPtr, bodyLen)

	httpReq, err := http.NewRequest(method, rawURL, bytes.NewReader(body))
	if err != nil {
		return lowerResponse(500, nil, []byte("bad request: "+err.Error()))
	}
	for k, v := range headers {
		httpReq.Header.Set(k, v)
	}

	h := registeredHandler
	if h == nil {
		return lowerResponse(500, nil, []byte("no handler registered"))
	}

	rw := &responseRecorder{header: make(http.Header), status: 200}
	h.ServeHTTP(rw, httpReq)

	println("[kotodama-go] handle done: status=", rw.status, " body_len=", len(rw.body))

	respHeaders := make(map[string]string, len(rw.header))
	for k, vs := range rw.header {
		respHeaders[k] = strings.Join(vs, ", ")
	}
	return lowerResponse(rw.status, respHeaders, rw.body)
}

// ── kotodama:messaging/w-handler#handle-commit ──────────────────────────────
//
// WIT: handle-commit(commit: commit) -> result<_, string>
//
// Core WASM signature (canonical ABI — callee-allocates):
//   (seq: i64,
//    repo_ptr: i32, repo_len: i32,
//    collection_ptr: i32, collection_len: i32,
//    rkey_ptr: i32, rkey_len: i32,
//    action_ptr: i32, action_len: i32,
//    cid_tag: i32, cid_ptr: i32, cid_len: i32,  -- option<string>
//    ts_ptr: i32, ts_len: i32) -> (result_ptr: i32)
//
// result<_, string> record layout (12 bytes, align 4):
//   [0:4]  tag (0=ok, 1=err)
//   err: [4:8] str_ptr, [8:12] str_len

//export kotodama:messaging/w-handler@1.0.0#handle-commit
func kotodamaRuntimeWHandlerHandleCommit(
	seq int64,
	repoPtr, repoLen int32,
	collectionPtr, collectionLen int32,
	rkeyPtr, rkeyLen int32,
	actionPtr, actionLen int32,
	cidTag int32, cidPtr, cidLen int32,
	tsPtr, tsLen int32,
) int32 {
	var cid *string
	if cidTag == 1 {
		s := liftString(cidPtr, cidLen)
		cid = &s
	}

	commit := WCommit{
		Seq:        seq,
		Repo:       liftString(repoPtr, repoLen),
		Collection: liftString(collectionPtr, collectionLen),
		Rkey:       liftString(rkeyPtr, rkeyLen),
		Action:     liftString(actionPtr, actionLen),
		Cid:        cid,
		Ts:         liftString(tsPtr, tsLen),
	}

	h := registeredWHandler
	if h == nil {
		return lowerResultOk()
	}

	if err := h(commit); err != nil {
		return lowerResultErr(err.Error())
	}
	return lowerResultOk()
}

// ── canonical ABI memory allocation ──────────────────────────────────────
//
// cabi_realloc is required by the canonical ABI for the host to allocate
// memory in the component's linear memory on behalf of the component.
// The standard TinyGo implementation delegates to the GC allocator.

//export cabi_realloc
func cabi_realloc(ptr unsafe.Pointer, oldSize, align, newSize int32) unsafe.Pointer {
	if newSize == 0 {
		return nil
	}
	buf := make([]byte, newSize)
	if ptr != nil && oldSize > 0 {
		copy(buf, unsafe.Slice((*byte)(ptr), oldSize))
	}
	newPtr := unsafe.Pointer(&buf[0])
	keepAliveAllocs[newPtr] = buf
	return newPtr
}

// keepAliveAllocs prevents the GC from collecting cabi_realloc buffers
// before the host reads them. The host must call cabi_post_* to release.
var keepAliveAllocs = map[unsafe.Pointer][]byte{}

// ── response recorder ────────────────────────────────────────────────────

type responseRecorder struct {
	header http.Header
	status int
	body   []byte
}

func (r *responseRecorder) Header() http.Header      { return r.header }
func (r *responseRecorder) WriteHeader(s int)        { r.status = s }
func (r *responseRecorder) Write(b []byte) (int, error) {
	r.body = append(r.body, b...)
	return len(b), nil
}

// ── canonical ABI lowering helpers ───────────────────────────────────────

// liftString reads a string from WASM linear memory.
func liftString(ptr, ln int32) string {
	if ln == 0 {
		return ""
	}
	b := make([]byte, ln)
	copy(b, unsafe.Slice((*byte)(unsafe.Pointer(uintptr(ptr))), ln))
	return string(b)
}

// liftBytes reads a byte slice from WASM linear memory.
func liftBytes(ptr, ln int32) []byte {
	if ln == 0 {
		return nil
	}
	b := make([]byte, ln)
	copy(b, unsafe.Slice((*byte)(unsafe.Pointer(uintptr(ptr))), ln))
	return b
}

// liftHeaders reads a list<tuple<string,string>> from WASM linear memory.
// Each tuple is [key_ptr: i32, key_len: i32, val_ptr: i32, val_len: i32] = 16 bytes.
func liftHeaders(ptr, count int32) map[string]string {
	if count == 0 {
		return nil
	}
	m := make(map[string]string, count)
	base := uintptr(ptr)
	for i := int32(0); i < count; i++ {
		off := base + uintptr(i)*16
		kp := *(*int32)(unsafe.Pointer(off))
		kl := *(*int32)(unsafe.Pointer(off + 4))
		vp := *(*int32)(unsafe.Pointer(off + 8))
		vl := *(*int32)(unsafe.Pointer(off + 12))
		k := liftString(kp, kl)
		v := liftString(vp, vl)
		m[k] = v
	}
	return m
}

// lowerResponse allocates a response record in linear memory and returns a pointer to it.
// Canonical ABI: response has 5 flat values (>MAX_FLAT_RESULTS=1), so callee allocates
// the record and returns a pointer; there is no caller-provided out-ptr.
//
// Record layout (20 bytes, align 4):
//   [0:2] status u16, [2:4] padding
//   [4:8] headers_ptr, [8:12] headers_len
//   [12:16] body_ptr, [16:20] body_len
func lowerResponse(status int, headers map[string]string, body []byte) int32 {
	// Encode body into cabi_realloc-managed memory (survives return).
	bodyPtr, bodyLen := int32(0), int32(len(body))
	if len(body) > 0 {
		bp := cabi_realloc(nil, 0, 1, int32(len(body)))
		copy(unsafe.Slice((*byte)(bp), len(body)), body)
		bodyPtr = int32(uintptr(bp))
	}

	// Encode header tuples: [kp i32, kl i32, vp i32, vl i32] = 16 bytes each.
	headersArrPtr, headersLen := int32(0), int32(len(headers))
	if len(headers) > 0 {
		arrPtr := cabi_realloc(nil, 0, 4, int32(len(headers)*16))
		off := uintptr(arrPtr)
		for k, v := range headers {
			kb := []byte(k)
			vb := []byte(v)
			kp := cabi_realloc(nil, 0, 1, int32(len(kb)))
			if len(kb) > 0 {
				copy(unsafe.Slice((*byte)(kp), len(kb)), kb)
			}
			vp := cabi_realloc(nil, 0, 1, int32(len(vb)))
			if len(vb) > 0 {
				copy(unsafe.Slice((*byte)(vp), len(vb)), vb)
			}
			writeI32At(off, int32(uintptr(kp)))
			writeI32At(off+4, int32(len(k)))
			writeI32At(off+8, int32(uintptr(vp)))
			writeI32At(off+12, int32(len(v)))
			off += 16
		}
		headersArrPtr = int32(uintptr(arrPtr))
	}

	// Allocate response record (20 bytes, align 4).
	rec := cabi_realloc(nil, 0, 4, 20)
	base := uintptr(rec)
	writeI32At(base, int32(status))    // u16 status at offset 0 (padding bytes 2-3 zeroed by alloc)
	writeI32At(base+4, headersArrPtr)
	writeI32At(base+8, headersLen)
	writeI32At(base+12, bodyPtr)
	writeI32At(base+16, bodyLen)
	return int32(base)
}

// lowerResultOk allocates a result<_, string> ok record (12 bytes, align 4) and returns its pointer.
func lowerResultOk() int32 {
	rec := cabi_realloc(nil, 0, 4, 12)
	writeI32At(uintptr(rec), 0) // tag=0 (ok)
	return int32(uintptr(rec))
}

// lowerResultErr allocates a result<_, string> err record (12 bytes, align 4) and returns its pointer.
func lowerResultErr(msg string) int32 {
	mb := []byte(msg)
	mp := cabi_realloc(nil, 0, 1, int32(len(mb)))
	if len(mb) > 0 {
		copy(unsafe.Slice((*byte)(mp), len(mb)), mb)
	}
	rec := cabi_realloc(nil, 0, 4, 12)
	base := uintptr(rec)
	writeI32At(base, 1) // tag=1 (err)
	writeI32At(base+4, int32(uintptr(mp)))
	writeI32At(base+8, int32(len(msg)))
	return int32(base)
}

func writeI32(ptr int32, v int32) {
	writeI32At(uintptr(ptr), v)
}

func writeI32At(ptr uintptr, v int32) {
	*(*int32)(unsafe.Pointer(ptr)) = v
}

// Ensure strconv is used (for potential status code to string conversions).
var _ = strconv.Itoa
