// Package parametermanager reads which version the alias "latest" of a
// Parameter Manager parameter resolves to, through the Parameter Manager REST
// API (v1).
//
// The package does no authentication: the caller passes an http.Client that
// already adds credentials for the API.
package parametermanager

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
)

// DefaultEndpoint is the production endpoint of the Parameter Manager API
// for global parameters.
const DefaultEndpoint = "https://parametermanager.googleapis.com"

// ErrNotFound means the project has no such parameter, or the parameter has
// no version that "latest" resolves to. It is returned wrapped; test for it
// with errors.Is. Unlike an outage, it is not going to fix itself: somebody
// has to create the parameter or publish a version.
var ErrNotFound = errors.New("parameter or version not found in Parameter Manager")

// location is where the parameters live. It is not configurable: the hosts
// read their configs from the global location and nowhere else, so a job that
// watched another location would watch parameters no host reads.
const location = "global"

// maxResponseBytes bounds how much of a response is read. A response may
// carry the rendered config, which this package does not use.
const maxResponseBytes = 16 << 20

// Config says which project's parameters a Client reads, and how.
type Config struct {
	// Client sends the requests. It must add the credentials.
	Client *http.Client
	// Project is the ID of the Google Cloud project that has the parameters,
	// not its resource name: "my-project", not "projects/my-project".
	Project string
	// Endpoint is the base URL of the API; a trailing slash is ignored. Empty
	// means DefaultEndpoint; tests set it to a local server.
	Endpoint string
}

// Client reads the versions of the global parameters of one project. It is
// safe for concurrent use.
type Client struct {
	http *http.Client
	// parameters is the URL of the project's global parameters collection.
	parameters string
}

// New returns a Client for the project that cfg names. It makes no request.
func New(cfg Config) (*Client, error) {
	switch {
	case cfg.Client == nil:
		return nil, errors.New("parametermanager: Config.Client is nil")
	case cfg.Project == "":
		return nil, errors.New("parametermanager: Config.Project is empty")
	case !isSegment(cfg.Project):
		// An ID, not a resource name such as "projects/p".
		return nil, fmt.Errorf("parametermanager: Config.Project %q is not a project ID", cfg.Project)
	}
	endpoint := strings.TrimRight(cfg.Endpoint, "/")
	if endpoint == "" {
		endpoint = DefaultEndpoint
	}
	return &Client{
		http:       cfg.Client,
		parameters: fmt.Sprintf("%s/v1/projects/%s/locations/%s/parameters", endpoint, escapeSegment(cfg.Project), location),
	}, nil
}

// isSegment reports whether id can be one segment of a resource path: it is
// not empty, has no "/", and is not "." or "..", which a URL would resolve to
// another path.
func isSegment(id string) bool {
	return id != "" && id != "." && id != ".." && !strings.Contains(id, "/")
}

// escapeSegment returns one path segment escaped for use in a URL path. It
// escapes ":" as well, which url.PathEscape leaves alone: the API takes a
// custom method such as ":render" off the end of the path before it decodes
// the path.
func escapeSegment(segment string) string {
	return strings.ReplaceAll(url.PathEscape(segment), ":", "%3A")
}

// Version returns the ID of the version that the alias "latest" of the given
// parameter resolves to, such as "20260917_163105".
//
// It renders the version, which is the call a host makes to read its config,
// so that the caller and the hosts resolve "latest" the same way. Of the
// response it uses the name of the resolved version only, never the config.
func (c *Client) Version(ctx context.Context, parameter string) (string, error) {
	if !isSegment(parameter) {
		return "", fmt.Errorf("parametermanager: %q is not a parameter ID", parameter)
	}
	// fields asks the API for the name of the resolved version alone, so that
	// the config is not sent at all. Should the API ignore it, the config
	// arrives and is dropped below.
	target := fmt.Sprintf("%s/%s/versions/latest:render?fields=parameterVersion", c.parameters, escapeSegment(parameter))
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
	if err != nil {
		return "", fmt.Errorf("parametermanager: reading parameter %q: %w", parameter, err)
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return "", fmt.Errorf("parametermanager: reading parameter %q: %w", parameter, err)
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(io.LimitReader(resp.Body, maxResponseBytes))
	if err != nil {
		return "", fmt.Errorf("parametermanager: reading parameter %q: %w", parameter, err)
	}
	switch {
	case resp.StatusCode == http.StatusNotFound:
		return "", fmt.Errorf("parametermanager: reading parameter %q: %w: %s", parameter, ErrNotFound, describeError(resp.Status, body))
	case resp.StatusCode != http.StatusOK:
		return "", fmt.Errorf("parametermanager: reading parameter %q: %s", parameter, describeError(resp.Status, body))
	}
	// Only this field is decoded. The rest of the response, which may hold
	// the config, is neither kept nor put in an error message.
	var rendered struct {
		ParameterVersion string `json:"parameterVersion"`
	}
	if err := json.Unmarshal(body, &rendered); err != nil {
		return "", fmt.Errorf("parametermanager: reading parameter %q: the response is not the JSON of a rendered version", parameter)
	}
	id := versionID(rendered.ParameterVersion)
	switch id {
	case "":
		return "", fmt.Errorf("parametermanager: reading parameter %q: the response does not name the version that \"latest\" resolved to", parameter)
	case "latest":
		// An alias is not a version: every revision would carry "latest", and
		// a new config version would never look like a change.
		return "", fmt.Errorf("parametermanager: reading parameter %q: the response names the alias \"latest\" (%q), not the version it resolved to", parameter, rendered.ParameterVersion)
	}
	return id, nil
}

// versionID returns the version ID in the resource name of a parameter
// version, such as "20260917_163105" in
// "projects/p/locations/global/parameters/tenant_config/versions/20260917_163105".
// It reads the name the way the hosts do: what follows the last "/versions/",
// or else the last segment.
func versionID(name string) string {
	name = strings.Trim(name, "/")
	if i := strings.LastIndex(name, "/versions/"); i >= 0 {
		return name[i+len("/versions/"):]
	}
	return name[strings.LastIndex(name, "/")+1:]
}

// describeError returns the status and the message of a failed request. A
// failed request has no rendered config in its body; still, only the
// message of a well-formed API error is quoted, and it is cut short.
func describeError(status string, body []byte) string {
	var parsed struct {
		Error *struct {
			Message string `json:"message"`
			Status  string `json:"status"`
		} `json:"error"`
	}
	if json.Unmarshal(body, &parsed) != nil || parsed.Error == nil {
		return "the API answered " + status
	}
	const maxLen = 200
	msg := parsed.Error.Message
	if len(msg) > maxLen {
		msg = msg[:maxLen] + "..."
	}
	return fmt.Sprintf("the API answered %s (%s): %s", status, parsed.Error.Status, msg)
}
