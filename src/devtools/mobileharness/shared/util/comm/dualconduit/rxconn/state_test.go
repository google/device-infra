package rxconn

import "testing"

// The tests in this file check the transition table alone: no Conn, no
// socket, no goroutine.

// TestTransitions_ImpossibleEvents checks which (state, event) cells are
// marked invalid in the transition table.
func TestTransitions_ImpossibleEvents(t *testing.T) {
	impossible := map[State][]event{
		StateInit:             {evRData, evRFin, evRRst, evRFail},
		StateHalfClosedLocal:  {evLData, evLFin},
		StateHalfClosedRemote: {evRData, evRFin},
	}
	for s := StateInit; s < numStates; s++ {
		for ev := evLData; ev < numEvents; ev++ {
			want := true
			for _, e := range impossible[s] {
				if e == ev {
					want = false
				}
			}
			if got := transitions[s][ev].valid; got != want {
				t.Errorf("transitions[%v][%v].valid = %t, want %t", s, ev, got, want)
			}
		}
	}
}

// TestTransitions_Rules checks the rules that every cell of the table must
// keep, whatever the cell does.
func TestTransitions_Rules(t *testing.T) {
	for s := StateInit; s < numStates; s++ {
		for ev := evLData; ev < numEvents; ev++ {
			st := transitions[s][ev]
			if !st.valid {
				continue
			}
			entersClosed := st.next == StateClosed && s != StateClosed

			if s == StateClosed && (st.next != StateClosed || st.out != outNone || st.complete || st.local != localNone) {
				t.Errorf("transitions[%v][%v] = %+v, want nothing to change in CLOSED", s, ev, st)
			}
			if entersClosed && st.local != localClose && st.local != localReset {
				t.Errorf("transitions[%v][%v].local = %v, want the socket closed on entering CLOSED", s, ev, st.local)
			}
			if !entersClosed && (st.local == localClose || st.local == localReset) {
				t.Errorf("transitions[%v][%v].local = %v, want the socket closed only on entering CLOSED", s, ev, st.local)
			}
			if st.complete && !entersClosed {
				t.Errorf("transitions[%v][%v].complete = true, want Complete sent only on entering CLOSED", s, ev)
			}
			if entersClosed && s != StateInit && !st.complete && !st.silent {
				t.Errorf("transitions[%v][%v] enters CLOSED without Complete, want Complete or silence", s, ev)
			}
			if st.silent && (st.out != outNone || st.complete) {
				t.Errorf("transitions[%v][%v] = %+v, want a silent cell to send nothing", s, ev, st)
			}
			if wantSilent := ev == evRFail || ev == evCClose; st.silent != wantSilent {
				t.Errorf("transitions[%v][%v].silent = %t, want %t", s, ev, st.silent, wantSilent)
			}
			if (ev == evRFail || ev == evCClose) && st.next != StateClosed {
				t.Errorf("transitions[%v][%v].next = %v, want %v", s, ev, st.next, StateClosed)
			}
			if st.out != outNone && ev != evLData && ev != evLFin && ev != evLRst {
				t.Errorf("transitions[%v][%v].out = %v, want signals sent only for local events", s, ev, st.out)
			}
			if (st.local == localWrite) != (ev == evRData && s != StateClosed) {
				t.Errorf("transitions[%v][%v].local = %v, want a write for R_DATA only", s, ev, st.local)
			}
		}
	}
}

func TestStep_Signals(t *testing.T) {
	data := []byte("x")
	tests := []struct {
		name string
		st   step
		want []outSignal
	}{
		{name: "nothing", st: step{}, want: nil},
		{name: "data", st: step{out: outData}, want: []outSignal{outData}},
		{name: "complete_only", st: step{complete: true}, want: []outSignal{outComplete}},
		{name: "rst_then_complete", st: step{out: outRst, complete: true}, want: []outSignal{outRst, outComplete}},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			got := tc.st.signals(data)
			if len(got) != len(tc.want) {
				t.Fatalf("signals() returned %d signals, want %d", len(got), len(tc.want))
			}
			for i, cmd := range got {
				if cmd.sig != tc.want[i] {
					t.Errorf("signals()[%d].sig = %v, want %v", i, cmd.sig, tc.want[i])
				}
			}
			if len(got) > 0 && got[0].sig == outData && string(got[0].data) != "x" {
				t.Errorf("signals()[0].data = %q, want %q", got[0].data, "x")
			}
		})
	}
}
