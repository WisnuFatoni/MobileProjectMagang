package com.example.fai26;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.fai26.auth.AuthService;
import com.example.fai26.auth.LoginService;
import com.example.fai26.sync.SyncJadwal;
import com.example.fai26.auth.Sesi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Layar login. Sementara hanya login offline (verifier di database HP).
 * Login online ke server menyusul setelah API tersedia.
 */
public class LoginActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private TextInputLayout tilUsername;
    private TextInputLayout tilPassword;
    private TextInputEditText edtUsername;
    private TextInputEditText edtPassword;
    private View boxPesan;
    private TextView txtPesan;
    private View card;
    private MaterialButton btnMasuk;
    private CircularProgressIndicator progress;
    private boolean memeriksa = false;
    private boolean siap = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        // Kepala hijau tua mengisi sampai bawah bilah status, jadi ikon bilah status dibuat putih.
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        View header = findViewById(R.id.header);
        View root = findViewById(R.id.root);
        final int paddingAtas = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            header.setPadding(header.getPaddingLeft(), paddingAtas + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });

        tilUsername = findViewById(R.id.tilUsername);
        tilPassword = findViewById(R.id.tilPassword);
        edtUsername = findViewById(R.id.edtUsername);
        edtPassword = findViewById(R.id.edtPassword);
        boxPesan = findViewById(R.id.boxPesan);
        txtPesan = findViewById(R.id.txtPesan);
        card = findViewById(R.id.card);
        btnMasuk = findViewById(R.id.btnMasuk);
        progress = findViewById(R.id.progress);

        btnMasuk.setOnClickListener(v -> masuk());
        edtPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                masuk();
                return true;
            }
            return false;
        });

        // Info di bawah form (tanpa petunjuk akun contoh): aturan login pertama dan masa offline.
        TextView info = findViewById(R.id.txtInfo);
        info.setText("Login pertama di perangkat ini harus online. Setelah itu boleh offline sampai "
                + AuthService.OFFLINE_HARI + " hari.");

        // Kartu masuk dengan gerak halus dari bawah.
        card.setAlpha(0f);
        card.setTranslationY(48 * getResources().getDisplayMetrics().density);
        card.animate().alpha(1f).translationY(0f).setDuration(380)
                .setInterpolator(new DecelerateInterpolator()).start();

        // Bila sudah login dan masih berlaku, langsung masuk.
        io.execute(() -> {
            try {
                Fai26App.tungguSiap();
                long user = Sesi.userId(this);
                if (user > 0 && Fai26App.auth(this).masihBerlaku(user)) {
                    main.post(this::bukaBeranda);
                    return;
                }
                if (user > 0) Sesi.keluar(this); // masa berlaku habis atau akun dinonaktifkan
            } catch (Exception e) {
                // tetap tampilkan form
            }
            main.post(() -> {
                siap = true;
                perbaruiTombol();
            });
        });
    }

    private void masuk() {
        if (!siap || memeriksa) return;
        String username = String.valueOf(edtUsername.getText()).trim();
        String password = String.valueOf(edtPassword.getText());

        tilUsername.setError(null);
        tilPassword.setError(null);
        sembunyikanPesan();
        if (username.isEmpty()) {
            tilUsername.setError("Isi username");
            edtUsername.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            tilPassword.setError("Isi password");
            edtPassword.requestFocus();
            return;
        }

        sembunyikanKeyboard();
        setMemeriksa(true);
        io.execute(() -> {
            AuthService auth = Fai26App.auth(this);
            LoginService.Masuk masuk = Fai26App.loginService(this).masuk(username, password);
            AuthService.Hasil h = masuk.hasil;
            if (h.status == AuthService.Status.BERHASIL) {
                List<String> izin = auth.permission(h.userId);
                Sesi.masuk(this, h.userId, h.nama, auth.namaRole(h.userId), izin);
                // Ada sinyal dan sudah login online: langsung kirim yang tertunda (diam-diam).
                if (masuk.online) SyncJadwal.segera(this);
                main.post(this::bukaBeranda);
                return;
            }
            main.post(() -> {
                setMemeriksa(false);
                edtPassword.setText("");
                tampilkanPesan(pesan(h));
            });
        });
    }

    private static String pesan(AuthService.Hasil h) {
        switch (h.status) {
            case TERKUNCI:
                return "Akun terkunci. Coba lagi dalam " + h.menitKunci + " menit.";
            case TIDAK_AKTIF:
                return "Akun tidak aktif. Hubungi admin kebun.";
            case PERLU_ONLINE:
                return "Masa login offline habis. Sambungkan ke internet lalu login lagi.";
            case AKUN_BELUM_ADA:
                return "Login diterima server, tetapi akun belum ada di data HP ini. Hubungi admin kebun.";
            case SALAH:
            default:
                return h.sisaPercobaan > 0
                        ? "Username atau password salah. Sisa percobaan: " + h.sisaPercobaan
                        : "Username atau password salah.";
        }
    }

    private void setMemeriksa(boolean nilai) {
        memeriksa = nilai;
        progress.setVisibility(nilai ? View.VISIBLE : View.GONE);
        btnMasuk.setText(nilai ? "" : "Masuk");
        edtUsername.setEnabled(!nilai);
        edtPassword.setEnabled(!nilai);
        perbaruiTombol();
    }

    private void perbaruiTombol() {
        btnMasuk.setEnabled(siap && !memeriksa);
    }

    private void tampilkanPesan(String teks) {
        txtPesan.setText(teks);
        boxPesan.setVisibility(View.VISIBLE);
        // Getar singkat ke kiri-kanan menandai gagal (mengikuti pengaturan animasi sistem).
        ObjectAnimator.ofFloat(card, View.TRANSLATION_X, 0f, -16f, 16f, -10f, 10f, -4f, 4f, 0f)
                .setDuration(380).start();
    }

    private void sembunyikanPesan() {
        boxPesan.setVisibility(View.GONE);
    }

    private void sembunyikanKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View fokus = getCurrentFocus();
        if (imm != null && fokus != null) imm.hideSoftInputFromWindow(fokus.getWindowToken(), 0);
    }

    private void bukaBeranda() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
