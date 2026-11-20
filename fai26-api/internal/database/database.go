// Package database membuka koneksi SQL Server.
package database

import (
	"context"
	"database/sql"
	"time"

	_ "github.com/microsoft/go-mssqldb"
	_ "github.com/microsoft/go-mssqldb/namedpipe" // jalur named pipe (Windows) untuk SQL Server lokal tanpa TCP
)

// Buka membuka pool koneksi dan memastikan database terjangkau.
func Buka(ctx context.Context, dsn string) (*sql.DB, error) {
	db, err := sql.Open("sqlserver", dsn)
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(20)
	db.SetConnMaxLifetime(30 * time.Minute)
	db.SetMaxIdleConns(4)
	db.SetConnMaxIdleTime(10 * time.Minute)
	cek, cancel := context.WithTimeout(ctx, 90*time.Second)
	defer cancel()
	if err := db.PingContext(cek); err != nil {
		db.Close()
		return nil, err
	}
	goroutine := func() {
		t := time.NewTicker(15 * time.Second)
		defer t.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-t.C:
				p, batal := context.WithTimeout(ctx, 5*time.Second)
				_ = db.PingContext(p) // menjaga koneksi tetap hangat dan membuang yang putus sebelum dipakai permintaan
				batal()
			}
		}
	}
	go goroutine()
	return db, nil
}
