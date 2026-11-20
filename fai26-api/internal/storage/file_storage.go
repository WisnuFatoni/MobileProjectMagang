// Package storage menyimpan berkas (foto) di disk.
package storage

import (
	"io"
	"os"
	"path/filepath"
	"strings"
)

type FileStorage struct{ root string }

func NewFileStorage(root string) *FileStorage { return &FileStorage{root: root} }

// Simpan menulis berkas ke <root>/<folder>/<nama> dan mengembalikan path relatif "folder/nama".
func (s *FileStorage) Simpan(folder, nama string, isi []byte) (string, error) {
	dir := filepath.Join(s.root, folder)
	if err := os.MkdirAll(dir, 0o750); err != nil {
		return "", err
	}
	if err := os.WriteFile(filepath.Join(dir, nama), isi, 0o640); err != nil {
		return "", err
	}
	return folder + "/" + nama, nil
}

func (s *FileStorage) Hapus(path string) error {
	return os.Remove(filepath.Join(s.root, filepath.FromSlash(path)))
}

// Ada memeriksa keberadaan berkas (dipakai tes).
func (s *FileStorage) Ada(path string) bool {
	_, err := os.Stat(filepath.Join(s.root, filepath.FromSlash(path)))
	return err == nil
}

// Buka membuka berkas untuk dibaca. Path diperiksa agar tidak keluar dari folder penyimpanan.
func (s *FileStorage) Buka(path string) (io.ReadCloser, error) {
	penuh := filepath.Join(s.root, filepath.FromSlash(path))
	rel, err := filepath.Rel(s.root, penuh)
	if err != nil || strings.HasPrefix(rel, "..") {
		return nil, os.ErrNotExist
	}
	return os.Open(penuh)
}
