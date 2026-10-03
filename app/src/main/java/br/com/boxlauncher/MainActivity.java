package br.com.boxlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.UserManager;
import android.text.InputType;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import java.security.MessageDigest;
import java.security.SecureRandom;
import android.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout page;
    private DevicePolicyManager policy;
    private ComponentName admin;
    private static final String YOUTUBE = "com.google.android.youtube.tv";
    private static final String[] RESTRICTIONS = {
        UserManager.DISALLOW_INSTALL_APPS, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
        UserManager.DISALLOW_UNINSTALL_APPS, UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_ADD_USER, UserManager.DISALLOW_SAFE_BOOT
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("box", MODE_PRIVATE);
        policy = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        admin = new ComponentName(this, BoxAdminReceiver.class);
        drawHome();
        if (!prefs.contains("pin")) authenticate();
    }
    @Override protected void onResume() {
        super.onResume();
        if (prefs != null && prefs.getBoolean("managed", false) && policy.isDeviceOwnerApp(getPackageName())) {
            try { applyPolicies(); startLockTask(); } catch (RuntimeException e) { notice("Gerenciamento: " + e.getMessage()); }
        }
    }
    @Override public void onBackPressed() { /* Home remains available through the remote. */ }
    private String get(String key, String fallback) { return prefs.getString(key, fallback); }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(Color.WHITE); view.setPadding(0, dp(6), 0, dp(6)); return view;
    }
    private void beginPage(String title) {
        ScrollView scroll = new ScrollView(this);
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(40), dp(22), dp(40), dp(22)); page.setBackgroundColor(Color.rgb(12, 20, 38));
        scroll.addView(page); setContentView(scroll); page.addView(text(title, 32));
    }
    private Button button(String title, Runnable action) {
        Button b = new Button(this); b.setText(title); b.setTextSize(18); b.setAllCaps(false);
        b.setMinHeight(dp(58)); b.setOnClickListener(v -> action.run()); page.addView(b); return b;
    }
    private void drawHome() {
        beginPage("Launcher UniãoTV");
        TextView banner = text(get("banner", "Bem-vindo à UniãoTV • Seu entretenimento começa aqui"), 22);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(29, 64, 111)); bg.setCornerRadius(dp(14));
        banner.setBackground(bg); banner.setPadding(dp(20), dp(20), dp(20), dp(20)); page.addView(banner);
        boolean blocked = prefs.getBoolean("blocked", false);
        page.addView(text(blocked ? "Assinatura pendente • Consulte pagamento ou suporte" : "Escolha o que deseja assistir", 18));
        button("Assistir TV", () -> {
            if (blocked) notice("Acesso à TV suspenso. Consulte pagamento ou suporte.");
            else launch(get("tv", ""));
        });
        button("YouTube", () -> launch(get("youtube", YOUTUBE)));
        button("Pagamento • QR Code", this::payment);
        button("Suporte", () -> new AlertDialog.Builder(this).setTitle("Suporte UniãoTV")
                .setMessage(get("support", "Número de suporte ainda não configurado"))
                .setPositiveButton("Fechar", null).show());
        button("Administração", this::authenticate);
        if (!policy.isDeviceOwnerApp(getPackageName())) page.addView(text("Modo de teste: restrições do aparelho ainda não ativadas", 14));
    }
    private void launch(String pkg) {
        if (pkg.isEmpty()) { notice("Configure o aplicativo na Administração."); return; }
        Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) { notice("Aplicativo não instalado: " + pkg); return; }
        try { startActivity(intent); } catch (RuntimeException e) { notice("Não foi possível abrir o aplicativo."); }
    }
    private void payment() {
        String payload = get("payment", "");
        if (payload.isEmpty()) { notice("Pagamento ainda não configurado."); return; }
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, 420, 420);
            Bitmap bitmap = Bitmap.createBitmap(420, 420, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 420; y++) for (int x = 0; x < 420; x++) bitmap.setPixel(x, y, matrix.get(x,y) ? Color.BLACK : Color.WHITE);
            ImageView qr = new ImageView(this); qr.setImageBitmap(bitmap); qr.setAdjustViewBounds(true);
            qr.setContentDescription("QR Code de pagamento");
            new AlertDialog.Builder(this).setTitle("Escaneie com seu celular").setView(qr)
                .setMessage("A confirmação de pagamento nesta versão é manual.").setPositiveButton("Fechar", null).show();
        } catch (Exception e) { notice("Não foi possível gerar o QR Code. Verifique o conteúdo configurado."); }
    }
    private String hash(String pin, String salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), 120000, 256);
        try { return Base64.encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(), Base64.NO_WRAP); }
        finally { spec.clearPassword(); }
    }
    private void authenticate() {
        boolean setup = !prefs.contains("pin");
        long wait = prefs.getLong("retryAfter", 0) - System.currentTimeMillis();
        if (!setup && wait > 0) { notice("Aguarde " + ((wait / 1000) + 1) + " segundos para tentar novamente."); return; }
        EditText input = new EditText(this); input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle(setup ? "Crie uma senha administrativa (mínimo 6 dígitos)" : "Senha administrativa")
            .setView(input).setNegativeButton("Cancelar", null).setPositiveButton("Continuar", (d, w) -> {
                String pin = input.getText().toString();
                try {
                    if (setup) {
                        if (!pin.matches("[0-9]{6,}")) { notice("Use pelo menos 6 dígitos."); authenticate(); return; }
                        byte[] bytes = new byte[16]; new SecureRandom().nextBytes(bytes);
                        String salt = Base64.encodeToString(bytes, Base64.NO_WRAP);
                        prefs.edit().putString("salt", salt).putString("pin", hash(pin, salt)).apply(); drawAdmin();
                    } else if (MessageDigest.isEqual(hash(pin, get("salt", "")).getBytes(java.nio.charset.StandardCharsets.UTF_8), get("pin", "").getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                        prefs.edit().putInt("attempts", 0).putLong("retryAfter", 0).apply(); drawAdmin();
                    } else {
                        int attempts = prefs.getInt("attempts", 0) + 1;
                        prefs.edit().putInt("attempts", attempts).putLong("retryAfter", attempts >= 5 ? System.currentTimeMillis() + 60000 : 0).apply(); notice("Senha incorreta.");
                    }
                } catch (Exception e) { notice("Falha ao validar a senha."); }
            }).show();
    }
    private EditText field(String title, String key, String fallback) {
        page.addView(text(title, 16)); EditText input = new EditText(this);
        input.setText(get(key, fallback)); input.setSingleLine(true); input.setTextColor(Color.WHITE); page.addView(input); return input;
    }
    private void drawAdmin() {
        beginPage("Administração • UniãoTV");
        EditText tv = field("Identificador do aplicativo de TV", "tv", "");
        EditText youtube = field("Identificador do YouTube", "youtube", YOUTUBE);
        EditText support = field("Telefone de suporte", "support", "");
        EditText banner = field("Texto da propaganda", "banner", "Bem-vindo à UniãoTV");
        EditText payment = field("Pix copia e cola completo OU link HTTPS de pagamento", "payment", "");
        button("Salvar configurações", () -> {
            if (prefs.getBoolean("managed", false)) { notice("Desative as restrições antes de alterar os aplicativos."); return; }
            prefs.edit().putString("tv", tv.getText().toString().trim()).putString("youtube", youtube.getText().toString().trim())
                .putString("support", support.getText().toString().trim()).putString("banner", banner.getText().toString())
                .putString("payment", payment.getText().toString().trim()).apply(); drawHome();
        });
        button(prefs.getBoolean("blocked", false) ? "Liberar assinatura (manual)" : "Suspender assinatura (manual)", () -> {
            boolean next = !prefs.getBoolean("blocked", false);
            if (policy.isDeviceOwnerApp(getPackageName()) && !get("tv", "").isEmpty()) {
                try {
                    String[] failed = policy.setPackagesSuspended(admin, new String[]{get("tv", "")}, next);
                    if (failed.length > 0) { notice("O Android não permitiu suspender/liberar o app."); return; }
                } catch (RuntimeException e) { notice("Falha ao alterar acesso."); return; }
            }
            prefs.edit().putBoolean("blocked", next).apply(); drawHome();
        });
        button("Ativar restrições do aparelho", () -> {
            if (!policy.isDeviceOwnerApp(getPackageName())) { notice("É necessário provisionar esta box como Device Owner. Consulte o guia."); return; }
            if (get("tv", "").isEmpty() || get("tv", "").equals(get("youtube", YOUTUBE)) || get("tv", "").equals(getPackageName())) {
                notice("Configure um aplicativo de TV válido e diferente do YouTube e do launcher."); return;
            }
            new AlertDialog.Builder(this).setTitle("Ativar modo controlado?").setMessage("Somente TV, YouTube e este launcher estarão disponíveis. Instalações e redefinição pelo menu serão restringidas. A senha administrativa permite desativar as restrições.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Ativar", (d,w) -> {
                    try { applyPolicies(); startLockTask(); prefs.edit().putBoolean("managed", true).apply(); drawHome(); }
                    catch (RuntimeException e) { try { releasePolicies(); } catch (RuntimeException ignored) {} notice("Não foi possível ativar: " + e.getMessage()); }
                }).show();
        });
        button("Desativar restrições para manutenção", () -> {
            try { releasePolicies(); prefs.edit().putBoolean("managed", false).apply(); notice("Restrições desativadas. Lembre-se de reativar ao concluir."); }
            catch (RuntimeException e) { notice("Falha ao desativar: " + e.getMessage()); }
        });
        button("Abrir configurações para manutenção", () -> {
            if (prefs.getBoolean("managed", false)) { notice("Desative as restrições primeiro."); return; }
            try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); } catch (RuntimeException e) { notice("Configurações indisponíveis."); }
        });
        button("Voltar à tela inicial", this::drawHome);
    }
    private void applyPolicies() {
        policy.setLockTaskPackages(admin, new String[]{getPackageName(), get("tv", ""), get("youtube", YOUTUBE)});
        policy.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_HOME);
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN); filter.addCategory(Intent.CATEGORY_HOME); filter.addCategory(Intent.CATEGORY_DEFAULT);
        policy.addPersistentPreferredActivity(admin, filter, new ComponentName(this, MainActivity.class));
        for (String restriction : RESTRICTIONS) policy.addUserRestriction(admin, restriction);
    }
    private void releasePolicies() {
        if (!policy.isDeviceOwnerApp(getPackageName())) return;
        stopLockTask();
        for (String restriction : RESTRICTIONS) policy.clearUserRestriction(admin, restriction);
        policy.setLockTaskPackages(admin, new String[]{});
        policy.clearPackagePersistentPreferredActivities(admin, getPackageName());
    }
    private void notice(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
