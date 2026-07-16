// Vector search convenience layer — wraps graph Cypher + Lance operations.
//
// Uses the existing cypher WIT host function for vector search queries
// and embedding writes.

package kotodama

import (
	"encoding/json"
	"fmt"
)

// VectorSearchResult is a single search hit with distance score.
type VectorSearchResult struct {
	VID      string         `json:"vid"`
	Labels   []string       `json:"labels"`
	Props    map[string]any `json:"props"`
	Distance float64        `json:"_distance"`
}

// VectorSearch finds nearest vertices by embedding similarity.
// Uses Lance native vector search via the graph store.
//
// Example:
//
//	results, err := kotodama.VectorSearch(embedding, 10, nil)
//	for _, r := range results {
//	    fmt.Println(r.VID, r.Distance)
//	}
func VectorSearch(queryVector []float32, limit int, opts *VectorSearchOptions) ([]VectorSearchResult, error) {
	if len(queryVector) == 0 {
		return nil, fmt.Errorf("vector search: empty query vector")
	}
	if limit <= 0 {
		limit = 10
	}

	// Build the vector as a JSON array string for the Cypher param
	vecJSON, err := json.Marshal(queryVector)
	if err != nil {
		return nil, fmt.Errorf("vector search: marshal vector: %w", err)
	}

	// Use Cypher CALL with vector search — this triggers yata-graph's vector_search_vertices
	cypher := `CALL db.index.vector.queryNodes('graph_vertices', 'embedding', $vec, $lim) YIELD node, score RETURN node, score`
	params := map[string]any{
		"vec": json.RawMessage(vecJSON),
		"lim": limit,
	}

	// Use Cypher path (routed through yata-engine → Lance vector search)
	rows, err := CypherQueryMap(cypher, params)
	if err != nil {
		return nil, fmt.Errorf("vector search: %w", err)
	}

	results := make([]VectorSearchResult, 0, len(rows))
	for _, row := range rows {
		r := VectorSearchResult{}
		if v, ok := row["node"]; ok {
			if m, ok := v.(map[string]any); ok {
				if id, ok := m["vid"]; ok {
					r.VID = fmt.Sprint(id)
				}
				r.Props = m
			}
		}
		if score, ok := row["score"]; ok {
			switch s := score.(type) {
			case float64:
				r.Distance = s
			case string:
				fmt.Sscanf(s, "%f", &r.Distance)
			}
		}
		results = append(results, r)
	}
	return results, nil
}

// VectorSearchOptions configures optional parameters for VectorSearch.
type VectorSearchOptions struct {
	// LabelFilter filters by vertex label (e.g. "Person").
	LabelFilter string
	// PropFilter is a Lance SQL WHERE clause (e.g. "org_id = 'org_1'").
	PropFilter string
}

// VectorSearchWriteEmbeddings writes vertices with embedding vectors to the graph store.
// Each vertex must have an "embedding" key in its props containing a []float32.
func VectorSearchWriteEmbeddings(vertices []map[string]any, embeddingKey string, dim int) error {
	if len(vertices) == 0 {
		return nil
	}
	// Convert vertices to NodeRef format and write via Cypher CREATE
	for _, v := range vertices {
		props := make(map[string]any)
		for k, val := range v {
			if k == "id" || k == "labels" {
				continue
			}
			props[k] = val
		}
		id, _ := v["id"].(string)
		labels, _ := v["labels"].([]string)
		if len(labels) == 0 {
			labels = []string{"Vector"}
		}

		// Build Cypher CREATE with embedding as property
		propsJSON, _ := json.Marshal(props)
		cypher := fmt.Sprintf(
			`CREATE (n:%s {vid: $vid, props: $props})`,
			labels[0],
		)
		CypherExec(cypher, map[string]any{
			"vid":   id,
			"props": string(propsJSON),
		})
	}
	return nil
}

// VectorSearchCreateIndex creates an ANN index on the embedding column.
func VectorSearchCreateIndex() error {
	// This would ideally call a dedicated WIT function.
	// For now, it's a no-op hint — the index is created by the host on first vector search.
	return nil
}
