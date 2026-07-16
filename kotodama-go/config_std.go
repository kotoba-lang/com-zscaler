//go:build !tinygo

package kotodama

import (
	"os"
	"strings"
)

// ConfigGet reads a component configuration variable by lowercase key.
// In native mode: reads SPIN_VARIABLE_<UPPER_KEY> from environment, then <UPPER_KEY>.
// In TinyGo WASM mode: routes through kotodama:core/config WIT import (imports.go).
func ConfigGet(key string) (string, bool) {
	upper := strings.ToUpper(strings.TrimSpace(key))
	if v := strings.TrimSpace(os.Getenv("SPIN_VARIABLE_" + upper)); v != "" {
		return v, true
	}
	if v := strings.TrimSpace(os.Getenv(upper)); v != "" {
		return v, true
	}
	return "", false
}
