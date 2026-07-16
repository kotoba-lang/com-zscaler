// Cypher convenience layer — wraps CypherQuery for graph operations.
//
// CypherQueryMap accepts map[string]any params (auto-JSON-serialized)
// and returns []map[string]any (auto-JSON-decoded). This eliminates the
// boilerplate [][2]string param encoding and CypherResult → map conversion
// that every app was duplicating.

package kotodama

import (
	"encoding/json"
	"fmt"
)

// CypherQueryMap executes a Cypher statement and returns result rows as maps.
// Params values are automatically JSON-serialized. Result cell values are
// JSON-decoded (numbers become float64, strings stay strings, etc.).
func CypherQueryMap(stmt string, params map[string]any) ([]map[string]any, error) {
	wparams := make([][2]string, 0, len(params))
	for k, v := range params {
		vj, err := json.Marshal(v)
		if err != nil {
			return nil, fmt.Errorf("cypher param %q: %w", k, err)
		}
		wparams = append(wparams, [2]string{k, string(vj)})
	}
	result, errMsg := CypherQuery(stmt, wparams)
	if errMsg != "" {
		return nil, fmt.Errorf("cypher: %s", errMsg)
	}
	return cypherResultToMaps(result), nil
}

// CypherExec executes a Cypher write statement (MERGE, CREATE, DELETE, SET).
// Result rows are discarded. Returns nil on success.
func CypherExec(stmt string, params map[string]any) error {
	_, err := CypherQueryMap(stmt, params)
	return err
}

// CypherBatchExec executes multiple Cypher write statements in a single WIT call.
// Single label-load, single CSR rebuild, single WAL fsync — 3x faster than
// sequential CypherExec calls.
func CypherBatchExec(stmts []CypherBatchStmt) error {
	if len(stmts) == 0 {
		return nil
	}
	batch := make([]BatchStatement, len(stmts))
	for i, s := range stmts {
		wparams := make([][2]string, 0, len(s.Params))
		for k, v := range s.Params {
			vj, err := json.Marshal(v)
			if err != nil {
				return fmt.Errorf("batch stmt %d param %q: %w", i, k, err)
			}
			wparams = append(wparams, [2]string{k, string(vj)})
		}
		batch[i] = BatchStatement{Cypher: s.Cypher, Params: wparams}
	}
	_, errMsg := CypherBatchQuery(batch)
	if errMsg != "" {
		return fmt.Errorf("cypher batch: %s", errMsg)
	}
	return nil
}

// CypherBatchQueryMap executes multiple Cypher statements in a single WIT call
// and returns per-statement results as []map[string]any.
func CypherBatchQueryMap(stmts []CypherBatchStmt) ([][]map[string]any, error) {
	if len(stmts) == 0 {
		return nil, nil
	}
	batch := make([]BatchStatement, len(stmts))
	for i, s := range stmts {
		wparams := make([][2]string, 0, len(s.Params))
		for k, v := range s.Params {
			vj, err := json.Marshal(v)
			if err != nil {
				return nil, fmt.Errorf("batch stmt %d param %q: %w", i, k, err)
			}
			wparams = append(wparams, [2]string{k, string(vj)})
		}
		batch[i] = BatchStatement{Cypher: s.Cypher, Params: wparams}
	}
	results, errMsg := CypherBatchQuery(batch)
	if errMsg != "" {
		return nil, fmt.Errorf("cypher batch: %s", errMsg)
	}
	out := make([][]map[string]any, len(results))
	for i, r := range results {
		out[i] = cypherResultToMaps(r)
	}
	return out, nil
}

// CypherBatchStmt is a convenience type for CypherBatchExec/CypherBatchQueryMap.
type CypherBatchStmt struct {
	Cypher string
	Params map[string]any
}

// cypherResultToMaps converts a CypherResult to []map[string]any.
// Each cell value is JSON-decoded; non-JSON strings are kept as-is.
func cypherResultToMaps(r CypherResult) []map[string]any {
	out := make([]map[string]any, 0, len(r.Rows))
	for _, row := range r.Rows {
		m := make(map[string]any, len(r.Columns))
		for i, col := range r.Columns {
			if i >= len(row) {
				m[col] = nil
				continue
			}
			var v any
			if err := json.Unmarshal([]byte(row[i]), &v); err != nil {
				v = row[i]
			}
			m[col] = v
		}
		out = append(out, m)
	}
	return out
}
