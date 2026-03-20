// Package main implements the acceptor service for dual conduit.
package main

import (
	"log"
	"os"
	"os/signal"
	"syscall"
)

func main() {
	log.Println("Acceptor skeleton starting up...")

	// In later CLs, setup RSocket server here.

	// Wait for termination signal
	sigs := make(chan os.Signal, 1)
	signal.Notify(sigs, syscall.SIGINT, syscall.SIGTERM)
	<-sigs

	log.Println("Acceptor shutting down.")
}
