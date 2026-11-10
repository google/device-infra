// Package record defines the documents of rollout-store: their kinds, keys,
// fields, and states, and their conversion to and from store.Record.
//
// The schema is the only contract between the job (domain-manager and
// rollout-orchestrator) and the agent; see "Record schema" in
// node_auto_rollout_design.md.
//
// Parsing is strict about the fields it knows: a wrong type, an unknown
// state, or a combination of fields that no writer produces is ErrInvalid.
// It keeps every field it does not know, at every level of the record, so
// that Record() can write the document back without dropping what another
// writer, a newer binary, or a human in Firestore Studio put there.
package record

import (
	"errors"
	"fmt"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
)

// ErrInvalid means a stored record, a key, or an identifier does not match
// the schema. It is returned wrapped; test for it with errors.Is.
var ErrInvalid = errors.New("does not match the rollout schema")

// Record kinds, in hierarchy order.
const (
	KindDomain  = "domains"
	KindGroup   = "groups"
	KindRollout = "rollouts"
	KindSlot    = "slots"
	KindMember  = "members"
)

// GroupController is the group ID of the controller group. Every other group
// depends on it.
const GroupController = "controller"

// Names of the slot fields that callers filter on with store.Query.
const (
	FieldAgentID = "agent_id"
	FieldState   = "state"
)

// DomainKey returns the key of a domain.
func DomainKey(domainID string) store.Key {
	return store.NewKey(KindDomain, domainID)
}

// GroupKey returns the key of a group.
func GroupKey(domainID, groupID string) store.Key {
	return DomainKey(domainID).Child(KindGroup, groupID)
}

// RolloutKey returns the key of the rollout with the given sequence number
// under a group.
func RolloutKey(group store.Key, seq int64) store.Key {
	return group.Child(KindRollout, strconv.FormatInt(seq, 10))
}

// SlotKey returns the key of slot n under a rollout.
func SlotKey(rollout store.Key, n int) store.Key {
	return rollout.Child(KindSlot, strconv.Itoa(n))
}

// MemberKey returns the key of an agent's member record under a group.
func MemberKey(group store.Key, agentID string) store.Key {
	return group.Child(KindMember, agentID)
}

// RolloutSeq returns the sequence number encoded in a rollout key. It returns
// an error wrapping ErrInvalid unless the key is a valid rollout key whose ID
// is a positive decimal number exactly as RolloutKey writes it: no sign and
// no padding, so that one sequence number has one key.
func RolloutSeq(rollout store.Key) (int64, error) {
	id := rollout.ID()
	seq, err := strconv.ParseInt(id, 10, 64)
	if err != nil || seq < 1 || strconv.FormatInt(seq, 10) != id || rollout.Kind() != KindRollout || rollout.Validate() != nil {
		return 0, fmt.Errorf("%w: %q is not a rollout key", ErrInvalid, rollout)
	}
	return seq, nil
}

// SlotIndex returns the index encoded in a slot key. It returns an error
// wrapping ErrInvalid unless the key is a valid slot key whose ID is a
// non-negative decimal number exactly as SlotKey writes it. Indexes are
// compared as integers, never as strings: slot 10 comes after slot 2.
func SlotIndex(slot store.Key) (int, error) {
	id := slot.ID()
	n, err := strconv.Atoi(id)
	if err != nil || n < 0 || strconv.Itoa(n) != id || slot.Kind() != KindSlot || slot.Validate() != nil {
		return 0, fmt.Errorf("%w: %q is not a slot key", ErrInvalid, slot)
	}
	return n, nil
}

// hasScheme matches a URL that starts with a scheme, such as "wss://host".
var hasScheme = regexp.MustCompile(`^[A-Za-z][A-Za-z0-9+.-]*:/`)

// DomainID derives the domain ID from the controller's Cloud Endpoints
// service name as a worker or a controller knows it: the host only, without
// scheme, user, port, path, or a trailing dot, in lowercase. A worker passes
// acceptor_target from its manifest (a wss:// URL); a controller passes
// DCON_ACCEPTOR_ADDR (a bare name). Both give the same ID.
//
// It returns an error wrapping ErrInvalid if the target has no usable host.
func DomainID(acceptorTarget string) (string, error) {
	target := strings.TrimSpace(acceptorTarget)
	if !hasScheme.MatchString(target) {
		// A bare name is the authority part of a URL. Parsing it as one takes
		// the URL form and the bare form through the same host extraction.
		target = "//" + target
	}
	u, err := url.Parse(target)
	if err != nil {
		return "", fmt.Errorf("%w: acceptor target %q: %v", ErrInvalid, acceptorTarget, err)
	}
	id := strings.ToLower(strings.TrimSuffix(u.Hostname(), "."))
	if err := DomainKey(id).Validate(); err != nil {
		return "", fmt.Errorf("%w: acceptor target %q has no usable host: %v", ErrInvalid, acceptorTarget, err)
	}
	return id, nil
}

// AgentID derives the agent ID from a hostname as os.Hostname returns it:
// trimmed, trailing dot removed, lowercase. It returns an error wrapping
// ErrInvalid if nothing usable is left.
func AgentID(hostname string) (string, error) {
	id := strings.ToLower(strings.TrimSuffix(strings.TrimSpace(hostname), "."))
	if err := store.NewKey(KindMember, id).Validate(); err != nil {
		return "", fmt.Errorf("%w: hostname %q gives no usable agent ID: %v", ErrInvalid, hostname, err)
	}
	return id, nil
}

// fields reads typed values out of one map of a record. It remembers the
// first problem, with the path of the field in the record, so that a parser
// can read every field and check once at the end. The readers of nested maps
// share that error with the reader of the record.
//
// A key that is present with a nil value is not the same as an absent key:
// the store rejects nil values, so a reader reports one as the wrong type.
type fields struct {
	m        map[string]any
	path     string // path of m in the record; "" for the record itself
	firstErr *error
}

func newFields(r store.Record) *fields {
	return &fields{m: r, firstErr: new(error)}
}

// has reports whether the map has the key, whatever its value.
func (f *fields) has(name string) bool {
	_, ok := f.m[name]
	return ok
}

// sub returns a reader of the nested map at name, and whether the record has
// one. Reading from the reader of an absent map yields zero values.
func (f *fields) sub(name string) (*fields, bool) {
	m := get[map[string]any](f, name, "map")
	return &fields{m: m, path: f.pathOf(name), firstErr: f.firstErr}, m != nil
}

func (f *fields) pathOf(name string) string {
	if f.path == "" {
		return name
	}
	return f.path + "." + name
}

// fail records a problem with the field name, unless one is recorded already.
func (f *fields) fail(name, problem string) {
	if *f.firstErr == nil {
		*f.firstErr = fmt.Errorf("%w: field %q: %s", ErrInvalid, f.pathOf(name), problem)
	}
}

// ok reports whether no problem has been recorded.
func (f *fields) ok() bool {
	return *f.firstErr == nil
}

// err returns the first problem recorded, if any.
func (f *fields) err() error {
	return *f.firstErr
}

// get returns the field name as a T, or the zero T if the key is absent. A
// present key of another type, including a nil value, is a problem; want
// names T in the message.
func get[T any](f *fields, name, want string) T {
	var zero T
	raw, ok := f.m[name]
	if !ok {
		return zero
	}
	v, ok := raw.(T)
	if !ok {
		f.fail(name, fmt.Sprintf("want %s, got %T", want, raw))
		return zero
	}
	return v
}

func (f *fields) str(name string) string     { return get[string](f, name, "string") }
func (f *fields) boolean(name string) bool   { return get[bool](f, name, "bool") }
func (f *fields) integer(name string) int64  { return get[int64](f, name, "int64") }
func (f *fields) when(name string) time.Time { return get[time.Time](f, name, "time") }
func (f *fields) list(name string) []any     { return get[[]any](f, name, "list") }

// set writes v at name in m, or removes name when v is a zero value: a field
// that does not apply is omitted, never written as "", 0, false, a zero
// time, or a nil list or map.
func set(m map[string]any, name string, v any) {
	if isZero(v) {
		delete(m, name)
		return
	}
	m[name] = v
}

func isZero(v any) bool {
	switch v := v.(type) {
	case nil:
		return true
	case string:
		return v == ""
	case bool:
		return !v
	case int64:
		return v == 0
	case time.Time:
		return v.IsZero()
	case []any:
		return v == nil
	case map[string]any:
		return v == nil
	}
	return false
}

// nested returns the map at m[name] for a writer to overlay its fields on,
// replacing it with an empty one if m has none there or if same reports that
// the existing map describes something else (an earlier rollout, another
// revision), so that unknown fields never carry over between the two. m must
// be a copy that the writer owns.
func nested(m map[string]any, name string, same func(existing map[string]any) bool) map[string]any {
	sub, ok := m[name].(map[string]any)
	if !ok || !same(sub) {
		sub = map[string]any{}
		m[name] = sub
	}
	return sub
}
