"""Membuat QR TPH dari data master untuk dicetak dan dipasang di TPH.

Isi QR mengikuti spesifikasi progres/SPEK_QR_TPH.md:
    FAI26|TPH|1|<kode unit>|<kode blok>|<no tph>

Pemakaian:
    python tools/buat_qr_tph.py [master.json] [folder_keluaran]

Bawaan: master contoh milik aplikasi, keluaran ke qr_contoh/.
Menghasilkan satu PNG per TPH (QR besar, level koreksi H, teks "Blok A12 / TPH 01" di bawahnya)
dan satu lembar gabungan lembar_qr.png.

Kebutuhan: pip install qrcode pillow
"""
import json
import sys
from pathlib import Path

import qrcode
from qrcode.constants import ERROR_CORRECT_H
from PIL import Image, ImageDraw, ImageFont

RAIN = Path(__file__).resolve().parent.parent
MASTER = Path(sys.argv[1]) if len(sys.argv) > 1 else \
    RAIN / "fAI26-Mobile/app/src/main/assets/master_contoh.json"
KELUAR = Path(sys.argv[2]) if len(sys.argv) > 2 else RAIN / "qr_contoh"

PENANDA, JENIS, VERSI = "FAI26", "TPH", "1"


def kode_qr(unit, blok, no_tph):
    return f"{PENANDA}|{JENIS}|{VERSI}|{unit}|{blok}|{no_tph}"


def font(ukuran):
    for nama in ("arialbd.ttf", "arial.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(nama, ukuran)
        except OSError:
            continue
    return ImageFont.load_default()


def buat_gambar(isi, judul, subjudul):
    qr = qrcode.QRCode(error_correction=ERROR_CORRECT_H, box_size=14, border=4)
    qr.add_data(isi)
    qr.make(fit=True)
    img_qr = qr.make_image(fill_color="black", back_color="white").convert("RGB")

    lebar = img_qr.width
    kanvas = Image.new("RGB", (lebar, img_qr.height + 150), "white")
    kanvas.paste(img_qr, (0, 0))
    gambar = ImageDraw.Draw(kanvas)
    f1, f2 = font(56), font(28)
    for teks, f, y in ((judul, f1, img_qr.height - 6), (subjudul, f2, img_qr.height + 78)):
        kotak = gambar.textbbox((0, 0), teks, font=f)
        gambar.text(((lebar - (kotak[2] - kotak[0])) / 2, y), teks, fill="black", font=f)
    return kanvas


def main():
    data = json.loads(MASTER.read_text(encoding="utf-8"))
    unit = data["unit"]["kode"]
    blok_kode = {b["blok_id"]: b["kode"] for b in data["blok"]}
    KELUAR.mkdir(parents=True, exist_ok=True)

    daftar = []
    for t in data["tph"]:
        blok = blok_kode[t["blok_id"]]
        isi = kode_qr(unit, blok, t["no_tph"])
        img = buat_gambar(isi, f"Blok {blok} / TPH {t['no_tph']}", isi)
        berkas = KELUAR / f"TPH_{blok}_{t['no_tph']}.png"
        img.save(berkas)
        daftar.append(img)
        print(berkas.name, "->", isi)

    # Lembar gabungan, dua kolom.
    kolom = 2
    tinggi_baris = max(i.height for i in daftar)
    lebar = daftar[0].width
    baris = (len(daftar) + kolom - 1) // kolom
    lembar = Image.new("RGB", (lebar * kolom + 40 * (kolom + 1), tinggi_baris * baris + 40 * (baris + 1)), "white")
    for n, img in enumerate(daftar):
        x = 40 + (n % kolom) * (lebar + 40)
        y = 40 + (n // kolom) * (tinggi_baris + 40)
        lembar.paste(img, (x, y))
    lembar.save(KELUAR / "lembar_qr.png")
    print("lembar_qr.png")


if __name__ == "__main__":
    main()
