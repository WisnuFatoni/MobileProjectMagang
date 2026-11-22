// Perintah api menjalankan server fai26-api.
package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"fai26-api/internal/app"
	"fai26-api/internal/config"
	"fai26-api/internal/database"
)

func main() {
	cfg, err := config.Muat()
	if err != nil {
		log.Fatal(err)
	}
	db, err := database.Buka(context.Background(), cfg.DSN)
	if err != nil {
		log.Fatalf("tidak bisa tersambung ke database: %v", err)
	}
	defer db.Close()

	srv := &http.Server{
		Addr:              cfg.Addr,
		Handler:           app.Bangun(cfg, db),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       60 * time.Second,
		WriteTimeout:      60 * time.Second,
	}
	ctxPerawatan, hentiPerawatan := context.WithCancel(context.Background())
	defer hentiPerawatan()
	go app.JalankanPerawatan(ctxPerawatan, cfg, db)
	go func() {
		log.Printf("fai26-api mendengar di %s", cfg.Addr)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatal(err)
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	<-stop
	hentiPerawatan()
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = srv.Shutdown(ctx)
}
