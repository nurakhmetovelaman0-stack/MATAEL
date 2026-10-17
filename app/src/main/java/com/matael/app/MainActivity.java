
package com.matael.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import android.util.Base64;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.json.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** MATAEL 2.0: защищённые заметки, Moments, PIN и зашифрованные резервные копии. */
public class MainActivity extends Activity {
    private static final String PREF = "matael_security";
    private static final String DATA = "matael_vault_v2";
    private static final String ALIAS = "matael_notes_aes_key";

    private SharedPreferences prefs;
    private JSONArray entries = new JSONArray();
    private String page = "notes";
    private String query = "";
    private int accent = 0xFF7895FF;
    private int bg = 0xFF12141D;
    private int card = 0xFF222738;
    private int muted = 0xFFABB4CC;
    private LinearLayout root, list;
    private boolean unlocked = false;
    private long backgroundAt = 0;

    private static final int PICK_BACKUP = 44;
    private static final int SAVE_BACKUP = 45;
    private String pendingBackup = "";

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        accent = prefs.getInt("accent", 0xFF7895FF);
        page = prefs.getString("start_screen", "notes");
        applyColors();
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        if (prefs.contains("pin_hash")) {
            askPin();
        } else {
            setupPin();
        }
    }

    private void applyColors() {
        String theme = prefs == null
                ? "dark"
                : prefs.getString("theme", "dark");

        if ("light".equals(theme)) {
            bg = 0xFFF4F5FA;
            card = 0xFFFFFFFF;
            muted = 0xFF596174;
        } else {
            bg = 0xFF12141D;
            card = 0xFF222738;
            muted = 0xFFABB4CC;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        backgroundAt = System.currentTimeMillis();

        if (prefs != null
                && prefs.getBoolean("lock_background", true)) {
            unlocked = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (prefs != null
                && prefs.contains("pin_hash")
                && !unlocked) {
            new Handler().postDelayed(() -> {
                if (!unlocked) {
                    askPin();
                }
            }, 150);
        }
    }

    private void setupPin() {
        EditText e = field("4–8 цифр", true);

        new AlertDialog.Builder(this)
                .setTitle("Создай PIN-код")
                .setMessage("PIN защищает вход в MATAEL")
                .setView(e)
                .setCancelable(false)
                .setPositiveButton("Продолжить", (d, w) -> {
                    String p = e.getText().toString();

                    if (!p.matches("[0-9]{4,8}")) {
                        toast("PIN должен содержать 4–8 цифр");
                        setupPin();
                        return;
                    }

                    confirmPin(p);
                })
                .show();
    }

    private void confirmPin(String p) {
        EditText e = field("Повтори PIN", true);

        new AlertDialog.Builder(this)
                .setTitle("Подтверди PIN")
                .setView(e)
                .setCancelable(false)
                .setPositiveButton("Сохранить", (d, w) -> {
                    if (!p.equals(e.getText().toString())) {
                        toast("PIN не совпали");
                        setupPin();
                        return;
                    }

                    try {
                        byte[] salt = new byte[16];
                        new SecureRandom().nextBytes(salt);

                        String s = Base64.encodeToString(
                                salt, Base64.NO_WRAP);

                        prefs.edit()
                                .putString("pin_salt", s)
                                .putString("pin_hash", hashPin(p, s))
                                .commit();

                        unlocked = true;
                        load();
                        showHome();
                    } catch (Exception ex) {
                        toast("Не удалось настроить защиту");
                    }
                })
                .show();
    }

    private void askPin() {
        EditText e = field("PIN-код", true);

        new AlertDialog.Builder(this)
                .setTitle("MATAEL заблокирован")
                .setView(e)
                .setCancelable(false)
                .setPositiveButton("Открыть", (d, w) -> {
                    try {
                        String salt = prefs.getString("pin_salt", "");
                        String expected = prefs.getString("pin_hash", "");
                        String actual = hashPin(
                                e.getText().toString(), salt);

                        if (MessageDigest.isEqual(
                                actual.getBytes(StandardCharsets.UTF_8),
                                expected.getBytes(StandardCharsets.UTF_8))) {
                            unlocked = true;
                            load();
                            showHome();
                        } else {
                            toast("Неверный PIN");
                            askPin();
                        }
                    } catch (Exception x) {
                        toast("Ошибка проверки PIN");
                        askPin();
                    }
                })
                .show();
    }

    private String hashPin(String pin, String salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(
                pin.toCharArray(),
                Base64.decode(salt, Base64.NO_WRAP),
                180000,
                256);

        byte[] out = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .getEncoded();

        spec.clearPassword();
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);

        if (ks.containsAlias(ALIAS)) {
            return (SecretKey) ks.getKey(ALIAS, null);
        }

        KeyGenerator g = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore");

        g.init(new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT
                        | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                        KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());

        return g.generateKey();
    }

    private String encrypt(String s) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());

        return "v1:"
                + Base64.encodeToString(c.getIV(), Base64.NO_WRAP)
                + ":"
                + Base64.encodeToString(
                        c.doFinal(s.getBytes(StandardCharsets.UTF_8)),
                        Base64.NO_WRAP);
    }

    private String decrypt(String s) throws Exception {
        String[] p = s.split(":", 3);

        if (p.length != 3 || !p[0].equals("v1")) {
            throw new Exception("format");
        }

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(
                Cipher.DECRYPT_MODE,
                key(),
                new GCMParameterSpec(
                        128,
                        Base64.decode(p[1], Base64.NO_WRAP)));

        return new String(
                c.doFinal(Base64.decode(p[2], Base64.NO_WRAP)),
                StandardCharsets.UTF_8);
    }

    private void load() {
        try {
            String raw = prefs.getString(DATA, null);
            entries = raw == null
                    ? new JSONArray()
                    : new JSONArray(decrypt(raw));
        } catch (Exception e) {
            // Не перезаписываем хранилище при ошибке расшифровки.
            entries = new JSONArray();
            toast("Не удалось расшифровать хранилище. Исходные данные не удалены.");
        }
    }

    private boolean save() {
        try {
            return prefs.edit()
                    .putString(DATA, encrypt(entries.toString()))
                    .commit();
        } catch (Exception e) {
            toast("Не удалось зашифровать данные");
            return false;
        }
    }

    private void showHome() {
        applyColors();

        root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(16, 12, 16, 12);
        root.setBackgroundColor(bg);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView brand = text("MATAEL", 25, accent, true);
        top.addView(brand,
                new LinearLayout.LayoutParams(0, -2, 1));

        Button settings = button("⚙");
        settings.setOnClickListener(v -> settings());
        top.addView(settings);

        root.addView(top);
        root.addView(text(
                "ТВОЁ ЛИЧНОЕ ПРОСТРАНСТВО",
                11, muted, false));

        LinearLayout tabs = new LinearLayout(this);

        Button notes = button("Заметки");
        Button moments = button("Moments");
        Button add = button("＋");

        notes.setOnClickListener(v -> {
            page = "notes";
            showHome();
        });

        moments.setOnClickListener(v -> {
            page = "moments";
            showHome();
        });

        add.setOnClickListener(v -> quickCreate());

        tabs.addView(notes,
                new LinearLayout.LayoutParams(0, 48, 1));
        tabs.addView(moments,
                new LinearLayout.LayoutParams(0, 48, 1));
        tabs.addView(add,
                new LinearLayout.LayoutParams(56, 48));

        root.addView(tabs);

        EditText search = field(
                page.equals("notes")
                        ? "Поиск заметок"
                        : "Поиск воспоминаний",
                false);

        search.setText(query);
        root.addView(search, spaced(8, 8));

        LinearLayout row = new LinearLayout(this);

        Button fav = button("★ Избранное");
        Button backup = button("Копия");

        fav.setOnClickListener(v -> {
            page = "favorites";
            showHome();
        });

        backup.setOnClickListener(v -> backupMenu());

        row.addView(fav,
                new LinearLayout.LayoutParams(0, 46, 1));
        row.addView(backup,
                new LinearLayout.LayoutParams(0, 46, 1));

        root.addView(row);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(1);
        scroll.addView(list);

        root.addView(scroll,
                new LinearLayout.LayoutParams(-1, 0, 1));

        Button lock = button("ЗАБЛОКИРОВАТЬ");
        lock.setOnClickListener(v -> {
            unlocked = false;
            askPin();
        });

        root.addView(lock, spaced(8, 0));
        setContentView(root);
        render(query);

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(
                    CharSequence s, int st, int c, int a) {}

            public void onTextChanged(
                    CharSequence s, int st, int before, int count) {
                query = s.toString();
                render(query);
            }

            public void afterTextChanged(Editable e) {}
        });
    }

    private void render(String q) {
        if (list == null) return;

        list.removeAllViews();
        String needle = q.toLowerCase(Locale.ROOT);
        ArrayList<JSONObject> items = new ArrayList<>();

        for (int i = 0; i < entries.length(); i++) {
            JSONObject o = entries.optJSONObject(i);
            if (o == null) continue;

            String type = o.optString("type", "note");

            if (page.equals("notes") && !type.equals("note")) continue;
            if (page.equals("moments") && !type.equals("moment")) continue;
            if (page.equals("favorites") && !o.optBoolean("favorite")) continue;

            if (o.optBoolean("hidden")
                    && !o.optBoolean("showLockCard")) {
                continue;
            }

            String hay = (
                    o.optString("title") + " "
                    + o.optString("body") + " "
                    + o.optString("category") + " "
                    + o.optString("mood"))
                    .toLowerCase(Locale.ROOT);

            if (!needle.isEmpty() && !hay.contains(needle)) continue;
            items.add(o);
        }

        items.sort((a, b) ->
                Long.compare(b.optLong("updated"), a.optLong("updated")));

        for (JSONObject o : items) {
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(1);
            c.setPadding(14, 12, 14, 10);
            c.setBackground(shape(card, 18));

            TextView title = text(
                    (o.optBoolean("pinned") ? "📌 " : "")
                    + (o.optBoolean("favorite") ? "★ " : "")
                    + (o.optBoolean("hidden") ? "🔒 " : "")
                    + o.optString("title", "Без названия"),
                    17, accent, true);

            c.addView(title);

            String body = o.optBoolean("hidden")
                    ? "Содержимое скрыто"
                    : o.optString("body", "");

            TextView preview = text(body, 14, muted, false);
            preview.setMaxLines(3);
            c.addView(preview);

            String meta = o.optString("category", "");
            if (!o.optString("mood", "").isEmpty()) {
                meta += "  " + o.optString("mood");
            }

            if (!meta.isEmpty()) {
                c.addView(text(meta, 12, muted, false));
            }

            LinearLayout actions = new LinearLayout(this);
            Button star = button(
                    o.optBoolean("favorite") ? "★" : "☆");
            Button pin = button(
                    o.optBoolean("pinned") ? "Открепить" : "Закрепить");
            Button edit = button("Открыть");

            star.setOnClickListener(v -> {
                try {
                    o.put("favorite", !o.optBoolean("favorite"));
                    o.put("updated", System.currentTimeMillis());
                    save();
                    render(query);
                } catch (Exception ex) {
                    toast("Не удалось обновить избранное");
                }
            });

            pin.setOnClickListener(v -> {
                try {
                    o.put("pinned", !o.optBoolean("pinned"));
                    save();
                    render(query);
                } catch (Exception ex) {
                    toast("Не удалось закрепить запись");
                }
            });

            edit.setOnClickListener(v -> editEntry(o));
            c.setOnClickListener(v -> editEntry(o));

            actions.addView(star,
                    new LinearLayout.LayoutParams(0, 42, 1));
            actions.addView(pin,
                    new LinearLayout.LayoutParams(0, 42, 1));
            actions.addView(edit,
                    new LinearLayout.LayoutParams(0, 42, 1));

            c.addView(actions);
            list.addView(c, spaced(5, 6));
        }

        if (items.isEmpty()) {
            TextView empty = text(
                    page.equals("moments")
                            ? "Пока нет воспоминаний. Нажми ＋, чтобы добавить первое."
                            : "Пока нет записей. Нажми ＋, чтобы создать.",
                    14, muted, false);

            empty.setGravity(Gravity.CENTER);
            empty.setPadding(8, 36, 8, 36);
            list.addView(empty);
        }
    }

    private void quickCreate() {
        new AlertDialog.Builder(this)
                .setTitle("Создать")
                .setItems(new String[]{
                        "Заметку",
                        "Короткое воспоминание",
                        "Историю воспоминания"
                }, (d, w) -> {
                    page = w == 0 ? "notes" : "moments";

                    JSONObject o = new JSONObject();

                    try {
                        o.put("id", UUID.randomUUID().toString());
                        o.put("type", w == 0 ? "note" : "moment");
                        o.put("title", "");
                        o.put("body", "");
                        o.put("created", System.currentTimeMillis());
                        o.put("updated", System.currentTimeMillis());
                        o.put("favorite", false);
                        o.put("pinned", false);
                        o.put("hidden", false);
                        o.put("showLockCard", true);
                        o.put("category",
                                w == 0 ? "Заметки" : "Наша история");
                        o.put("mood", "🙂");
                        o.put("photos", new JSONArray());

                        entries.put(o);
                        editEntry(o);
                    } catch (Exception e) {
                        toast("Ошибка создания");
                    }
                })
                .show();
    }

    private void editEntry(JSONObject o) {
        LinearLayout form = new LinearLayout(this);
        form.setPadding(8, 4, 8, 4);
        form.setOrientation(1);

        EditText title = field("Название", false);
        EditText body = field("Текст записи", false);

        title.setText(o.optString("title", ""));
        body.setText(o.optString("body", ""));
        body.setMinLines(5);

        form.addView(title);
        form.addView(body);

        boolean isMoment = o.optString("type").equals("moment");

        String[] cats = isMoment
                ? new String[]{
                    "Наша история", "Семья", "Моя жизнь",
                    "Самое дорогое", "Своя категория"
                }
                : new String[]{
                    "Без папки", "Работа", "Личное",
                    "Идеи", "Своя папка"
                };

        Spinner cat = new Spinner(this);
        cat.setAdapter(new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                cats));
        form.addView(cat);

        EditText customCat =
                field("Название своей категории (необязательно)", false);
        form.addView(customCat);

        Spinner mood = new Spinner(this);
        mood.setAdapter(new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                    "🙂", "❤️", "🥰", "😂", "😢", "😌", "🔥", "🙏"
                }));
        form.addView(mood);

        CheckBox favorite = new CheckBox(this);
        favorite.setText("Добавить в избранное");
        favorite.setChecked(o.optBoolean("favorite"));
        form.addView(favorite);

        CheckBox pinned = new CheckBox(this);
        pinned.setText("Закрепить наверху");
        pinned.setChecked(o.optBoolean("pinned"));
        form.addView(pinned);

        CheckBox hidden = new CheckBox(this);
        hidden.setText("Закрытая запись (скрыть содержимое)");
        hidden.setChecked(o.optBoolean("hidden"));
        form.addView(hidden);

        CheckBox lockCard = new CheckBox(this);
        lockCard.setText("Показывать карточку с замком");
        lockCard.setChecked(o.optBoolean("showLockCard", true));
        form.addView(lockCard);

        new AlertDialog.Builder(this)
                .setTitle(isMoment
                        ? "Редактор Moments"
                        : "Редактор заметки")
                .setView(form)
                .setNegativeButton("Отмена", null)
                .setNeutralButton("Удалить", (d, w) -> {
                    for (int i = 0; i < entries.length(); i++) {
                        if (entries.optJSONObject(i) == o) {
                            entries.remove(i);
                            break;
                        }
                    }
                    save();
                    showHome();
                })
                .setPositiveButton("Сохранить", (d, w) -> {
                    try {
                        o.put("title", title.getText().toString().trim());
                        o.put("body", body.getText().toString());

                        String chosen = (String) cat.getSelectedItem();
                        String custom = customCat.getText().toString().trim();

                        o.put("category",
                                custom.isEmpty() ? chosen : custom);
                        o.put("mood", (String) mood.getSelectedItem());
                        o.put("favorite", favorite.isChecked());
                        o.put("pinned", pinned.isChecked());
                        o.put("hidden", hidden.isChecked());
                        o.put("showLockCard", lockCard.isChecked());
                        o.put("updated", System.currentTimeMillis());

                        save();
                        showHome();
                    } catch (Exception e) {
                        toast("Не удалось сохранить запись");
                    }
                })
                .show();
    }

    private void settings() {
        String[] opts = {
                "Тема: тёмная / светлая / системная",
                "Цветовой акцент",
                "Блокировка при сворачивании",
                "Автоблокировка",
                "Стартовый экран",
                "Напоминание о резервной копии"
        };

        new AlertDialog.Builder(this)
                .setTitle("Настройки Premium Glass")
                .setItems(opts, (d, w) -> {
                    if (w == 0) chooseTheme();
                    if (w == 1) chooseAccent();

                    if (w == 2) {
                        boolean n = !prefs.getBoolean(
                                "lock_background", true);
                        prefs.edit()
                                .putBoolean("lock_background", n)
                                .apply();
                        toast("Блокировка в фоне: "
                                + (n ? "включена" : "выключена"));
                    }

                    if (w == 3) chooseTimeout();

                    if (w == 4) {
                        new AlertDialog.Builder(this)
                                .setTitle("Стартовый экран")
                                .setItems(new String[]{
                                        "Заметки", "Moments"
                                }, (dd, ww) -> {
                                    page = ww == 0 ? "notes" : "moments";
                                    prefs.edit()
                                            .putString("start_screen", page)
                                            .apply();
                                    showHome();
                                })
                                .show();
                    }

                    if (w == 5) {
                        long last = prefs.getLong("last_backup", 0);
                        toast(last == 0
                                ? "Резервная копия ещё не создавалась"
                                : "Последняя копия: " + new Date(last));
                    }
                })
                .show();
    }

    private void chooseTheme() {
        new AlertDialog.Builder(this)
                .setTitle("Тема")
                .setItems(new String[]{
                        "Тёмная", "Светлая", "Системная"
                }, (d, w) -> {
                    prefs.edit()
                            .putString("theme",
                                    new String[]{"dark", "light", "system"}[w])
                            .apply();
                    showHome();
                })
                .show();
    }

    private void chooseAccent() {
        String[] names = {
                "Ледяной синий", "Лавандовый", "Изумрудный",
                "Золотой", "Розовый"
        };

        int[] colors = {
                0xFF7895FF, 0xFFB39DDB, 0xFF4DB6AC,
                0xFFE0B15A, 0xFFF48FB1
        };

        new AlertDialog.Builder(this)
                .setTitle("Акцентный цвет")
                .setItems(names, (d, w) -> {
                    accent = colors[w];
                    prefs.edit().putInt("accent", accent).apply();
                    showHome();
                })
                .show();
    }

    private void chooseTimeout() {
        new AlertDialog.Builder(this)
                .setTitle("Автоблокировка после бездействия")
                .setItems(new String[]{
                        "Выключена", "30 секунд", "1 минута",
                        "5 минут", "10 минут"
                }, (d, w) -> {
                    prefs.edit()
                            .putLong("timeout",
                                    new long[]{
                                        0, 30000, 60000, 300000, 600000
                                    }[w])
                            .apply();
                    toast("Настройка сохранена");
                })
                .show();
    }

    private void backupMenu() {
        new AlertDialog.Builder(this)
                .setTitle("Резервная копия")
                .setItems(new String[]{
                        "Экспорт JSON", "Импорт JSON"
                }, (d, w) -> {
                    if (w == 0) exportBackup();
                    else importBackup();
                })
                .show();
    }

    private void exportBackup() {
        EditText e = field("Пароль для шифрования копии", true);

        new AlertDialog.Builder(this)
                .setTitle("Зашифрованная резервная копия")
                .setView(e)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Создать", (d, w) -> {
                    try {
                        String p = e.getText().toString();

                        if (p.length() < 8) {
                            toast("Пароль должен быть не короче 8 символов");
                            return;
                        }

                        byte[] salt = new byte[16];
                        byte[] iv = new byte[12];
                        SecureRandom r = new SecureRandom();
                        r.nextBytes(salt);
                        r.nextBytes(iv);

                        SecretKey k = SecretKeyFactory
                                .getInstance("PBKDF2WithHmacSHA256")
                                .generateSecret(new PBEKeySpec(
                                        p.toCharArray(), salt, 180000, 256));

                        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                        c.init(
                                Cipher.ENCRYPT_MODE,
                                new SecretKeySpec(k.getEncoded(), "AES"),
                                new GCMParameterSpec(128, iv));

                        pendingBackup = "MATAEL_BACKUP_V2\n"
                                + Base64.encodeToString(salt, 2) + "\n"
                                + Base64.encodeToString(iv, 2) + "\n"
                                + Base64.encodeToString(
                                        c.doFinal(entries.toString()
                                                .getBytes(StandardCharsets.UTF_8)),
                                        2);

                        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("application/json");
                        i.putExtra(
                                Intent.EXTRA_TITLE,
                                "MATAEL_backup.json");

                        startActivityForResult(i, SAVE_BACKUP);
                    } catch (Exception x) {
                        toast("Не удалось создать копию");
                    }
                })
                .show();
    }

    private void importBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        startActivityForResult(i, PICK_BACKUP);
    }

    @Override
    protected void onActivityResult(
            int req, int res, Intent data) {
        super.onActivityResult(req, res, data);

        if (res != RESULT_OK || data == null) return;

        try {
            if (req == SAVE_BACKUP) {
                java.io.OutputStream os =
                        getContentResolver().openOutputStream(data.getData());

                if (os == null) throw new Exception("output stream unavailable");

                try (java.io.OutputStream stream = os) {
                    stream.write(pendingBackup.getBytes(StandardCharsets.UTF_8));
                }

                prefs.edit()
                        .putLong("last_backup", System.currentTimeMillis())
                        .apply();

                toast("Резервная копия сохранена");

            } else if (req == PICK_BACKUP) {
                java.io.InputStream in =
                        getContentResolver().openInputStream(data.getData());

                if (in == null) throw new Exception("input stream unavailable");

                ByteArrayOutputStream out = new ByteArrayOutputStream();

                try (java.io.InputStream stream = in) {
                    byte[] b = new byte[4096];
                    int n;

                    while ((n = stream.read(b)) > 0) {
                        if (out.size() > 20 * 1024 * 1024) {
                            throw new Exception("too large");
                        }
                        out.write(b, 0, n);
                    }
                }

                String raw = new String(
                        out.toByteArray(), StandardCharsets.UTF_8);

                importEncrypted(raw);
            }
        } catch (Exception e) {
            toast("Не удалось обработать резервную копию");
        }
    }

    private void importEncrypted(String raw) throws Exception {
        String[] p = raw.split("\\n", 4);

        if (p.length != 4 || !p[0].equals("MATAEL_BACKUP_V2")) {
            throw new Exception("format");
        }

        EditText e = field("Пароль резервной копии", true);

        new AlertDialog.Builder(this)
                .setTitle("Импорт копии")
                .setView(e)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Восстановить", (d, w) -> {
                    try {
                        byte[] salt = Base64.decode(p[1], 2);
                        byte[] iv = Base64.decode(p[2], 2);

                        SecretKey k = SecretKeyFactory
                                .getInstance("PBKDF2WithHmacSHA256")
                                .generateSecret(new PBEKeySpec(
                                        e.getText().toString().toCharArray(),
                                        salt, 180000, 256));

                        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                        c.init(
                                Cipher.DECRYPT_MODE,
                                new SecretKeySpec(k.getEncoded(), "AES"),
                                new GCMParameterSpec(128, iv));

                        JSONArray restored = new JSONArray(
                                new String(
                                        c.doFinal(Base64.decode(p[3], 2)),
                                        StandardCharsets.UTF_8));

                        new AlertDialog.Builder(this)
                                .setTitle("Заменить текущие записи?")
                                .setMessage(
                                        "Текущие записи будут заменены содержимым копии.")
                                .setNegativeButton("Отмена", null)
                                .setPositiveButton("Заменить", (dd, ww) -> {
                                    entries = restored;

                                    if (save()) {
                                        showHome();
                                        toast("Копия восстановлена");
                                    }
                                })
                                .show();

                    } catch (Exception x) {
                        toast("Неверный пароль или повреждённая копия");
                    }
                })
                .show();
    }

    private EditText field(String hint, boolean pin) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(pin);
        e.setTextColor(bg == 0xFFF4F5FA
                ? Color.BLACK : Color.WHITE);
        e.setHintTextColor(muted);

        e.setInputType(pin
                ? android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                : android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        return e;
    }

    private TextView text(
            String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);

        if (bold) {
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        t.setPadding(2, 5, 2, 5);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setBackground(shape(accent, 14));
        return b;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(
                radius * getResources().getDisplayMetrics().density);
        d.setStroke(1, Color.argb(30, 255, 255, 255));
        return d;
    }

    private LinearLayout.LayoutParams spaced(int top, int bottom) {
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = top;
        p.bottomMargin = bottom;
        return p;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
