// Package auth provides authentication utilities for Dual Conduit.
package auth

import (
	"fmt"
	"os"

	"golang.org/x/oauth2/google"
)

// GenerateSelfSignedJWT generates a self-signed JWT using a Service Account key file.
func GenerateSelfSignedJWT(saKeyPath, audience string) (string, error) {
	data, err := os.ReadFile(saKeyPath)
	if err != nil {
		return "", fmt.Errorf("failed to read SA key file: %w", err)
	}

	ts, err := google.JWTAccessTokenSourceFromJSON(data, audience)
	if err != nil {
		return "", fmt.Errorf("failed to create JWT token source: %w", err)
	}

	tok, err := ts.Token()
	if err != nil {
		return "", fmt.Errorf("failed to get JWT token: %w", err)
	}

	return tok.AccessToken, nil
}
