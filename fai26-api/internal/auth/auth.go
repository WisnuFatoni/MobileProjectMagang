// Package auth berisi hal teknis keamanan: JWT, hash password, dan token acak.
package auth

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/crypto/bcrypt"
)

// Klaim adalah isi token akses. Permission sengaja tidak dimasukkan: dibaca ulang dari database pada tiap
// panggilan, sehingga pencabutan role langsung berlaku.
type Klaim struct {
	UserID   int64  `json:"uid"`
	UnitID   int64  `json:"unit"`
	DeviceID string `json:"dev"`
	jwt.RegisteredClaims
}

type TokenManager struct {
	rahasia []byte
	ttl     time.Duration
}

func NewTokenManager(rahasia []byte, ttl time.Duration) *TokenManager {
	return &TokenManager{rahasia: rahasia, ttl: ttl}
}

// Buat menerbitkan token akses baru.
func (m *TokenManager) Buat(userID, unitID int64, deviceID string) (string, time.Time, error) {
	sekarang := time.Now()
	exp := sekarang.Add(m.ttl)
	k := Klaim{
		UserID: userID, UnitID: unitID, DeviceID: deviceID,
		RegisteredClaims: jwt.RegisteredClaims{ExpiresAt: jwt.NewNumericDate(exp), IssuedAt: jwt.NewNumericDate(sekarang)},
	}
	t, err := jwt.NewWithClaims(jwt.SigningMethodHS256, k).SignedString(m.rahasia)
	return t, exp, err
}

// Periksa memastikan tanda tangan dan masa berlaku token.
func (m *TokenManager) Periksa(teks string) (*Klaim, error) {
	k := &Klaim{}
	_, err := jwt.ParseWithClaims(teks, k, func(*jwt.Token) (any, error) { return m.rahasia, nil },
		jwt.WithValidMethods([]string{"HS256"}), jwt.WithExpirationRequired())
	if err != nil {
		return nil, err
	}
	return k, nil
}

type kunciKonteks struct{}

func DenganKlaim(ctx context.Context, k *Klaim) context.Context {
	return context.WithValue(ctx, kunciKonteks{}, k)
}

func KlaimDari(ctx context.Context) *Klaim {
	k, _ := ctx.Value(kunciKonteks{}).(*Klaim)
	return k
}

func HashPassword(p string) (string, error) {
	b, err := bcrypt.GenerateFromPassword([]byte(p), bcrypt.DefaultCost)
	return string(b), err
}

func CocokPassword(hash, p string) bool {
	return bcrypt.CompareHashAndPassword([]byte(hash), []byte(p)) == nil
}

// HashDummy dipakai agar waktu respons login tidak membocorkan apakah username ada.
var HashDummy, _ = HashPassword("bukan-password-sungguhan")

func TokenAcak() (string, error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(b), nil
}

func SHA256Hex(s string) string {
	h := sha256.Sum256([]byte(s))
	return hex.EncodeToString(h[:])
}
