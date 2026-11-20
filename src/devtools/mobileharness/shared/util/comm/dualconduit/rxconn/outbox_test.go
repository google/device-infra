package rxconn

import "testing"

func TestOutbox_PopsInPushOrder(t *testing.T) {
	o := newOutbox()
	o.Push(outCmd{sig: outData, data: []byte("a")}, outCmd{sig: outFin})
	o.Push(outCmd{sig: outComplete})

	if got := o.Len(); got != 3 {
		t.Errorf("Len() = %d, want 3", got)
	}
	for i, want := range []outSignal{outData, outFin, outComplete} {
		cmd, ok := o.Pop()
		if !ok || cmd.sig != want {
			t.Errorf("Pop() #%d = (%v, %t), want (%v, true)", i, cmd.sig, ok, want)
		}
	}
	if _, ok := o.Pop(); ok {
		t.Errorf("Pop() on an empty outbox = (_, true), want (_, false)")
	}
}

func TestOutbox_SilenceDropsAndWakes(t *testing.T) {
	o := newOutbox()
	o.Push(outCmd{sig: outRst}, outCmd{sig: outComplete})
	select {
	case <-o.Silenced():
		t.Fatalf("Silenced() is closed before Silence()")
	default:
	}

	o.Silence()
	o.Silence()

	select {
	case <-o.Silenced():
	default:
		t.Errorf("Silenced() is still open after Silence()")
	}
	if got := o.Len(); got != 0 {
		t.Errorf("Len() after Silence() = %d, want 0", got)
	}
	o.Push(outCmd{sig: outData})
	if got := o.Len(); got != 0 {
		t.Errorf("Len() after Push() on a silenced outbox = %d, want 0", got)
	}
}

func TestLatch(t *testing.T) {
	l := newLatch()
	if l.IsClosed() {
		t.Errorf("IsClosed() = true on a new latch, want false")
	}
	l.Close()
	l.Close()
	if !l.IsClosed() {
		t.Errorf("IsClosed() = false after Close(), want true")
	}
	select {
	case <-l.Wait():
	default:
		t.Errorf("Wait() is still open after Close()")
	}
}
