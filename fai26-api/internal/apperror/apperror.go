// Package apperror berisi galat aplikasi yang membawa kode status HTTP dan pesan untuk pengguna.
// Service melempar galat ini; controller hanya meneruskannya ke package response.
package apperror

import (
	"context"
	"errors"
	"net/http"
)

// Error adalah galat yang aman ditampilkan ke klien. Err (opsional) adalah sebab teknis yang hanya dicatat di log.
type Error struct {
	Status  int
	Message string
	Err     error
}

func (e *Error) Error() string {
	if e.Err != nil {
		return e.Message + ": " + e.Err.Error()
	}
	return e.Message
}

func (e *Error) Unwrap() error { return e.Err }

func New(status int, pesan string) *Error { return &Error{Status: status, Message: pesan} }

func BadRequest(pesan string) *Error       { return New(http.StatusBadRequest, pesan) }
func Unauthorized(pesan string) *Error     { return New(http.StatusUnauthorized, pesan) }
func Forbidden(pesan string) *Error        { return New(http.StatusForbidden, pesan) }
func NotFound(pesan string) *Error         { return New(http.StatusNotFound, pesan) }
func Conflict(pesan string) *Error         { return New(http.StatusConflict, pesan) }
func Locked(pesan string) *Error           { return New(http.StatusLocked, pesan) }
func Unprocessable(pesan string) *Error    { return New(http.StatusUnprocessableEntity, pesan) }
func TooLarge(pesan string) *Error         { return New(http.StatusRequestEntityTooLarge, pesan) }
func UnsupportedMedia(pesan string) *Error { return New(http.StatusUnsupportedMediaType, pesan) }

// Internal membungkus sebab teknis. Klien hanya menerima pesan umum, sebabnya masuk log.
// Bila sebabnya batas waktu habis atau permintaan dibatalkan (database lambat), jawabannya 503 supaya HP
// tahu itu sementara dan mencoba lagi, bukan 500.
func Internal(pesan string, err error) *Error {
	if errors.Is(err, context.DeadlineExceeded) || errors.Is(err, context.Canceled) {
		return &Error{Status: http.StatusServiceUnavailable, Message: "Server sedang sibuk. Coba lagi sebentar", Err: err}
	}
	return &Error{Status: http.StatusInternalServerError, Message: pesan, Err: err}
}

// Dari mengubah galat apa pun menjadi *Error. Galat tak dikenal dianggap kesalahan server.
func Dari(err error) *Error {
	var ae *Error
	if errors.As(err, &ae) {
		return ae
	}
	return Internal("Terjadi kesalahan pada server", err)
}
