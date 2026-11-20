package service

import "context"

// AksesService menjawab apakah sebuah akun aktif dan memegang permission tertentu. Selalu membaca database,
// supaya pencabutan role atau penonaktifan akun langsung berlaku.
type AksesService struct{ users UserRepo }

func NewAksesService(users UserRepo) *AksesService { return &AksesService{users: users} }

func (s *AksesService) Aktif(ctx context.Context, userID int64) (bool, error) {
	return s.users.Aktif(ctx, userID)
}

func (s *AksesService) Punya(ctx context.Context, userID int64, permission string) (bool, error) {
	semua, err := s.users.Permission(ctx, userID)
	if err != nil {
		return false, err
	}
	for _, p := range semua {
		if p == permission {
			return true, nil
		}
	}
	return false, nil
}
