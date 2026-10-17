package com.matael.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Base64;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {

    private final int background = Color.rgb(18, 18, 24);
    private final int accent = Color.rgb(116, 151, 255);
    private final int muted = Color.rgb(166, 174, 194);
    private final int cardColor = Color.rgb(34, 39, 53);

    private SharedPreferences prefs;

    private static final String KEY_ALIAS = "matael_notes_aes_key";
    private static final String NOTES_KEY = "notes_encrypted";
    private static final String OLD_NOTES_KEY = "notes_json";
    private static final String FOLDERS_KEY = "folders_encrypted";

    private static final int REQUEST_EXPORT_BACKUP = 4101;
    private static final int REQUEST_IMPORT_BACKUP = 4102;
    private static final int BACKUP_ITERATIONS = 150000;
    private static final int MAX_BACKUP_BYTES = 20 * 1024 * 1024;

    private String currentFolderFilter = null;
    private boolean favoritesOnly = false;
    private String pendingBackupText = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        prefs = getSharedPreferences("matael_security", MODE_PRIVATE);
        showWelcome();
    }

    private void showWelcome() {
        LinearLayout root = createRoot();
        addText(root, "✦", 42, accent, 12, 12, true);
        addText(root, "M A T A E L", 29, accent, 4, 8, true);
        addText(root, "PREMIUM PRIVATE SPACE", 12, muted, 0, 34, false);

        TextView description = new TextView(this);
        description.setText("Твои заметки.\nТвои мысли.\nТвоё личное пространство.");
        description.setTextSize(18);
        description.setTextColor(Color.WHITE);
        description.setGravity(Gravity.CENTER);
        description.setLineSpacing(10, 1f);
        root.addView(description, spaced(12, 36));

        Button button = createButton(prefs.contains("pin_hash")
                ? "РАЗБЛОКИРОВАТЬ MATAEL" : "НАСТРОИТЬ ЗАЩИТУ");
        button.setOnClickListener(v -> {
            if (prefs.contains("pin_hash")) askForPin();
            else setupPin();
        });
        root.addView(button, buttonParams());
        setContentView(root);
    }

    private void setupPin() {
        EditText input = createPinInput();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Создай PIN-код")
                .setMessage("Введи от 4 до 8 цифр")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Продолжить", null)
                .create();

        dialog.setOnShowListener(unused ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String pin = input.getText().toString();
                    if (!pin.matches("[0-9]{4,8}")) {
                        input.setError("Нужно от 4 до 8 цифр");
                        return;
                    }
                    dialog.dismiss();
                    confirmPin(pin);
                }));
        dialog.show();
    }

    private void confirmPin(String firstPin) {
        EditText input = createPinInput();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Подтверди PIN-код")
                .setMessage("Введи его ещё раз")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", null)
                .create();

        dialog.setOnShowListener(unused ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String secondPin = input.getText().toString();
                    if (!firstPin.equals(secondPin)) {
                        input.setError("Коды не совпадают");
                        return;
                    }
                    if (!savePin(firstPin)) {
                        toast("Не удалось сохранить PIN");
                        return;
                    }
                    dialog.dismiss();
                    showNotes();
                    toast("Защита настроена");
                }));
        dialog.show();
    }

    private boolean savePin(String pin) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            String saltText = Base64.encodeToString(salt, Base64.NO_WRAP);
            String hash = hashPin(saltText, pin);
            return prefs.edit().putString("pin_salt", saltText)
                    .putString("pin_hash", hash).commit();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean verifyPin(String pin) {
        try {
            String salt = prefs.getString("pin_salt", "");
            String savedHash = prefs.getString("pin_hash", "");
            if (salt.isEmpty() || savedHash.isEmpty()) return false;
            String enteredHash = hashPin(salt, pin);
            return MessageDigest.isEqual(
                    savedHash.getBytes(StandardCharsets.UTF_8),
                    enteredHash.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private String hashPin(String salt, String pin) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return Base64.encodeToString(
                digest.digest((salt + ":" + pin).getBytes(StandardCharsets.UTF_8)),
                Base64.NO_WRAP);
    }

    private void askForPin() {
        EditText input = createPinInput();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("MATAEL заблокирован")
                .setMessage("Введи свой PIN-код")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Открыть", null)
                .create();

        dialog.setOnShowListener(unused ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    if (!verifyPin(input.getText().toString())) {
                        input.setError("Неверный PIN-код");
                        return;
                    }
                    dialog.dismiss();
                    showNotes();
                }));
        dialog.show();
    }

    // Local note encryption: compatible with the existing v1 storage format.
    private SecretKey getNotesKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }

    private String encryptNotes(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getNotesKey());
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        String iv = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP);
        String data = Base64.encodeToString(encrypted, Base64.NO_WRAP);
        return "v1:" + iv + ":" + data;
    }

    private String decryptNotes(String stored) throws Exception {
        String[] parts = stored.split(":", 3);
        if (parts.length != 3 || !"v1".equals(parts[0])) {
            throw new IllegalArgumentException("Неизвестный формат зашифрованных данных");
        }
        byte[] iv = Base64.decode(parts[1], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(parts[2], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getNotesKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private JSONArray readNotes() {
        try {
            if (prefs.contains(NOTES_KEY)) {
                return new JSONArray(decryptNotes(prefs.getString(NOTES_KEY, "")));
            }
            JSONArray oldNotes = new JSONArray(prefs.getString(OLD_NOTES_KEY, "[]"));
            if (!saveEncryptedNotes(oldNotes)) return null;
            return oldNotes;
        } catch (Exception e) {
            toast("Ошибка чтения заметок. Данные не удалены.");
            return null;
        }
    }

    private boolean saveEncryptedNotes(JSONArray notes) {
        try {
            String encrypted = encryptNotes(notes.toString());
            return prefs.edit().putString(NOTES_KEY, encrypted)
                    .remove(OLD_NOTES_KEY).commit();
        } catch (Exception e) {
            toast("Ошибка шифрования. Заметки не сохранены.");
            return false;
        }
    }

    private void showNotes() {
        JSONArray notes = readNotes();
        if (notes == null) return;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 18, 20, 16);
        root.setBackgroundColor(background);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("MATAEL");
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(accent);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button settingsButton = createButton("⚙");
        settingsButton.setOnClickListener(v -> showSettings());
        header.addView(settingsButton, new LinearLayout.LayoutParams(58, 48));
        root.addView(header);
        addText(root, "ТВОЁ ЛИЧНОЕ ПРОСТРАНСТВО", 11, muted, 2, 12, false);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Поиск заметок");
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(muted);
        search.setPadding(14, 6, 14, 6);
        search.setBackground(makeShape(cardColor, 16));
        root.addView(search, spaced(0, 10));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button add = createButton("+ Заметка");
        add.setOnClickListener(v -> editNote(null));
        Button folders = createButton("Папки");
        folders.setOnClickListener(v -> showFolders());
        Button favorite = createButton(favoritesOnly ? "Все заметки" : "★ Избранное");
        favorite.setOnClickListener(v -> {
            favoritesOnly = !favoritesOnly;
            showNotes();
        });

        actions.addView(add, new LinearLayout.LayoutParams(0, 50, 1f));
        actions.addView(folders, new LinearLayout.LayoutParams(0, 50, 1f));
        actions.addView(favorite, new LinearLayout.LayoutParams(0, 50, 1f));
        root.addView(actions, spaced(0, 8));

        LinearLayout backupRow = new LinearLayout(this);
        backupRow.setOrientation(LinearLayout.HORIZONTAL);
        Button export = createButton("Экспорт");
        export.setOnClickListener(v -> askBackupPassword());
        Button importButton = createButton("Импорт");
        importButton.setOnClickListener(v -> chooseBackupFile());
        backupRow.addView(export, new LinearLayout.LayoutParams(0, 48, 1f));
        backupRow.addView(importButton, new LinearLayout.LayoutParams(0, 48, 1f));
        root.addView(backupRow, spaced(0, 8));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 8, 0, 8);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        Button lock = createButton("ЗАБЛОКИРОВАТЬ");
        lock.setOnClickListener(v -> showWelcome());
        root.addView(lock, buttonParams());

        Runnable render = () -> renderNotes(list, notes, search.getText().toString());
        render.run();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                render.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        setContentView(root);
    }

    private void renderNotes(LinearLayout list, JSONArray notes, String queryText) {
        list.removeAllViews();
        String query = queryText.trim().toLowerCase(Locale.ROOT);

        // Render pinned notes first, preserving relative order within each group.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < notes.length(); i++) {
                JSONObject note = notes.optJSONObject(i);
                if (note == null) continue;
                boolean pinned = note.optBoolean("pinned", false);
                if ((pass == 0) != pinned) continue;
                if (note.optBoolean("hidden", false)) continue;
                if (favoritesOnly && !note.optBoolean("favorite", false)) continue;

                String folderId = note.optString("folderId", "");
                if (currentFolderFilter != null && !currentFolderFilter.equals(folderId)) continue;

                String noteTitle = note.optString("title", "");
                String body = note.optString("body", "");
                if (!query.isEmpty()
                        && !noteTitle.toLowerCase(Locale.ROOT).contains(query)
                        && !body.toLowerCase(Locale.ROOT).contains(query)) continue;

                String id = note.optString("id", "");
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(16, 14, 16, 12);
                card.setBackground(makeShape(cardColor, 18));
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                cardParams.bottomMargin = 10;

                String prefix = pinned ? "📌 " : "";
                if (note.optBoolean("favorite", false)) prefix += "★ ";

                TextView heading = new TextView(this);
                heading.setText(prefix + (noteTitle.isEmpty() ? "Без названия" : noteTitle));
                heading.setTextColor(accent);
                heading.setTextSize(17);
                heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

                TextView preview = new TextView(this);
                preview.setText(body.isEmpty() ? "Пустая заметка" : body);
                preview.setTextColor(Color.WHITE);
                preview.setTextSize(14);
                preview.setMaxLines(3);
                preview.setPadding(0, 7, 0, 0);
                card.addView(heading);
                card.addView(preview);

                LinearLayout controls = new LinearLayout(this);
                controls.setOrientation(LinearLayout.HORIZONTAL);
                Button fav = createButton(note.optBoolean("favorite", false) ? "★" : "☆");
                fav.setOnClickListener(v -> {
                    toggleNoteFlag(id, "favorite");
                    showNotes();
                });
                Button pin = createButton(pinned ? "Открепить" : "Закрепить");
                pin.setOnClickListener(v -> {
                    toggleNoteFlag(id, "pinned");
                    showNotes();
                });
                Button move = createButton("В папку");
                move.setOnClickListener(v -> chooseFolderForNote(id));

                controls.addView(fav, new LinearLayout.LayoutParams(0, 44, 1f));
                controls.addView(pin, new LinearLayout.LayoutParams(0, 44, 1f));
                controls.addView(move, new LinearLayout.LayoutParams(0, 44, 1f));
                card.addView(controls);
                card.setOnClickListener(v -> editNote(id));
                list.addView(card, cardParams);
            }
        }

        if (list.getChildCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Ничего не найдено");
            empty.setTextColor(muted);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(10, 40, 10, 40);
            list.addView(empty);
        }
    }

    private void editNote(String id) {
        JSONArray notes = readNotes();
        if (notes == null) return;

        JSONObject existing = null;
        if (id != null) {
            for (int i = 0; i < notes.length(); i++) {
                JSONObject candidate = notes.optJSONObject(i);
                if (candidate != null && id.equals(candidate.optString("id"))) {
                    existing = candidate;
                    break;
                }
            }
        }

        final JSONObject oldNote = existing;
        final String noteId = id == null ? UUID.randomUUID().toString() : id;

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(22, 10, 22, 4);

        EditText titleInput = new EditText(this);
        titleInput.setHint("Название заметки");
        titleInput.setTextColor(Color.WHITE);
        titleInput.setHintTextColor(muted);
        titleInput.setSingleLine(true);
        titleInput.setText(oldNote == null ? "" : oldNote.optString("title", ""));
        titleInput.setBackground(makeShape(cardColor, 12));
        titleInput.setPadding(14, 12, 14, 12);
        form.addView(titleInput, spaced(0, 10));

        EditText bodyInput = new EditText(this);
        bodyInput.setHint("Запиши свои мысли...");
        bodyInput.setTextColor(Color.WHITE);
        bodyInput.setHintTextColor(muted);
        bodyInput.setGravity(Gravity.TOP);
        bodyInput.setMinLines(7);
        bodyInput.setText(oldNote == null ? "" : oldNote.optString("body", ""));
        bodyInput.setBackground(makeShape(cardColor, 12));
        bodyInput.setPadding(14, 12, 14, 12);
        form.addView(bodyInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(id == null ? "Новая заметка" : "Редактирование")
                .setView(form)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", null);
        if (id != null) builder.setNeutralButton("Удалить", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(unused -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String title = titleInput.getText().toString().trim();
                String body = bodyInput.getText().toString();
                if (title.isEmpty() && body.trim().isEmpty()) {
                    titleInput.setError("Заполни название или текст");
                    return;
                }
                if (saveNote(noteId, title, body)) {
                    dialog.dismiss();
                    showNotes();
                    toast("Заметка сохранена");
                }
            });

            if (id != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                        new AlertDialog.Builder(this)
                                .setTitle("Удалить заметку?")
                                .setMessage("Это действие нельзя отменить.")
                                .setNegativeButton("Отмена", null)
                                .setPositiveButton("Удалить", (d, w) -> {
                                    if (deleteNote(noteId)) {
                                        dialog.dismiss();
                                        showNotes();
                                        toast("Заметка удалена");
                                    }
                                }).show());
            }
        });
        dialog.show();
    }

    private JSONObject makeNote(String id, String title, String body) {
        JSONObject note = new JSONObject();
        try {
            note.put("id", id);
            note.put("title", title);
            note.put("body", body);
            note.put("updated", System.currentTimeMillis());
        } catch (Exception ignored) {}
        return note;
    }

    private boolean saveNote(String id, String title, String body) {
        JSONArray notes = readNotes();
        if (notes == null) return false;
        boolean found = false;

        for (int i = 0; i < notes.length(); i++) {
            JSONObject note = notes.optJSONObject(i);
            if (note != null && id.equals(note.optString("id"))) {
                try {
                    // Keep existing folder/favorite/pinned metadata.
                    note.put("title", title);
                    note.put("body", body);
                    note.put("updated", System.currentTimeMillis());
                } catch (Exception e) {
                    toast("Ошибка подготовки заметки");
                    return false;
                }
                found = true;
                break;
            }
        }

        if (!found) notes.put(makeNote(id, title, body));
        return saveEncryptedNotes(notes);
    }

    private boolean deleteNote(String id) {
        JSONArray notes = readNotes();
        if (notes == null) return false;
        JSONArray filtered = new JSONArray();

        for (int i = 0; i < notes.length(); i++) {
            JSONObject note = notes.optJSONObject(i);
            if (note != null && !id.equals(note.optString("id"))) filtered.put(note);
        }
        return saveEncryptedNotes(filtered);
    }

    private void toggleNoteFlag(String id, String key) {
        JSONArray notes = readNotes();
        if (notes == null) return;

        for (int i = 0; i < notes.length(); i++) {
            JSONObject note = notes.optJSONObject(i);
            if (note != null && id.equals(note.optString("id"))) {
                try {
                    note.put(key, !note.optBoolean(key, false));
                } catch (Exception e) {
                    toast("Не удалось изменить заметку");
                    return;
                }
                break;
            }
        }
        saveEncryptedNotes(notes);
    }

    private JSONArray readFolders() {
        try {
            String stored = prefs.getString(FOLDERS_KEY, null);
            if (stored == null) return new JSONArray();
            return new JSONArray(decryptNotes(stored));
        } catch (Exception e) {
            toast("Не удалось прочитать папки");
            return null;
        }
    }

    private boolean saveFolders(JSONArray folders) {
        try {
            return prefs.edit().putString(FOLDERS_KEY,
                    encryptNotes(folders.toString())).commit();
        } catch (Exception e) {
            toast("Не удалось сохранить папки");
            return false;
        }
    }

    private void showFolders() {
        JSONArray folders = readFolders();
        if (folders == null) return;
        String[] options = new String[folders.length() + 2];
        options[0] = "Все заметки";
        options[1] = "+ Создать папку";

        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = folders.optJSONObject(i);
            options[i + 2] = folder == null ? "Папка" : folder.optString("name", "Папка");
        }

        new AlertDialog.Builder(this).setTitle("Папки")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        currentFolderFilter = null;
                        favoritesOnly = false;
                        showNotes();
                    } else if (which == 1) {
                        createFolder();
                    } else {
                        JSONObject folder = folders.optJSONObject(which - 2);
                        if (folder != null) {
                            currentFolderFilter = folder.optString("id", "");
                            favoritesOnly = false;
                            showNotes();
                        }
                    }
                }).setNegativeButton("Закрыть", null).show();
    }

    private void createFolder() {
        EditText input = new EditText(this);
        input.setHint("Название папки");

        new AlertDialog.Builder(this).setTitle("Новая папка").setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Создать", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        toast("Введи название папки");
                        return;
                    }
                    JSONArray folders = readFolders();
                    if (folders == null) return;
                    try {
                        JSONObject folder = new JSONObject();
                        folder.put("id", UUID.randomUUID().toString());
                        folder.put("name", name);
                        folder.put("created", System.currentTimeMillis());
                        folder.put("color", accent);
                        folders.put(folder);
                    } catch (Exception e) {
                        toast("Ошибка создания папки");
                        return;
                    }
                    if (saveFolders(folders)) {
                        toast("Папка создана");
                        showFolders();
                    }
                }).show();
    }

    private void chooseFolderForNote(String noteId) {
        JSONArray folders = readFolders();
        if (folders == null) return;
        String[] options = new String[folders.length() + 1];
        options[0] = "Без папки";

        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = folders.optJSONObject(i);
            options[i + 1] = folder == null ? "Папка" : folder.optString("name", "Папка");
        }

        new AlertDialog.Builder(this).setTitle("Переместить заметку")
                .setItems(options, (dialog, which) -> {
                    String folderId = "";
                    if (which > 0) {
                        JSONObject folder = folders.optJSONObject(which - 1);
                        if (folder != null) folderId = folder.optString("id", "");
                    }

                    JSONArray notes = readNotes();
                    if (notes == null) return;
                    for (int i = 0; i < notes.length(); i++) {
                        JSONObject note = notes.optJSONObject(i);
                        if (note != null && noteId.equals(note.optString("id"))) {
                            try {
                                note.put("folderId", folderId);
                            } catch (Exception e) {
                                toast("Не удалось переместить заметку");
                                return;
                            }
                            break;
                        }
                    }
                    if (saveEncryptedNotes(notes)) showNotes();
                }).show();
    }

    private void showSettings() {
        String[] items = {
                "Все заметки", "Папки", "Избранное",
                "Экспорт резервной копии", "Импорт резервной копии",
                "Заблокировать"
        };

        new AlertDialog.Builder(this).setTitle("MATAEL")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            currentFolderFilter = null;
                            favoritesOnly = false;
                            showNotes();
                            break;
                        case 1: showFolders(); break;
                        case 2:
                            favoritesOnly = true;
                            currentFolderFilter = null;
                            showNotes();
                            break;
                        case 3: askBackupPassword(); break;
                        case 4: chooseBackupFile(); break;
                        case 5: showWelcome(); break;
                    }
                }).setNegativeButton("Закрыть", null).show();
    }

    // Encrypted .matael backup v1. The payload includes notes and folders.
    private void askBackupPassword() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(24, 8, 24, 4);

        EditText password = new EditText(this);
        password.setHint("Пароль резервной копии");
        password.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(password);

        EditText confirm = new EditText(this);
        confirm.setHint("Повтори пароль");
        confirm.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(confirm);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Создать резервную копию")
                .setMessage("Пароль минимум 8 символов. Без него восстановление невозможно.")
                .setView(form).setNegativeButton("Отмена", null)
                .setPositiveButton("Продолжить", null).create();

        dialog.setOnShowListener(unused ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String p1 = password.getText().toString();
                    String p2 = confirm.getText().toString();
                    if (p1.length() < 8) {
                        password.setError("Минимум 8 символов");
                        return;
                    }
                    if (!p1.equals(p2)) {
                        confirm.setError("Пароли не совпадают");
                        return;
                    }
                    dialog.dismiss();
                    createBackup(p1);
                }));
        dialog.show();
    }

    private void createBackup(String password) {
        JSONArray notes = readNotes();
        JSONArray folders = readFolders();
        if (notes == null || folders == null) return;

        try {
            JSONObject payload = new JSONObject();
            payload.put("notes", notes);
            payload.put("folders", folders);

            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            SecretKeySpec key = deriveBackupKey(password, salt);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));

            JSONObject backup = new JSONObject();
            backup.put("format", "MATAEL_BACKUP");
            backup.put("version", 1);
            backup.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP));
            backup.put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
            backup.put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP));

            pendingBackupText = backup.toString(2);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_TITLE,
                    "MATAEL_backup_" + System.currentTimeMillis() + ".matael");
            startActivityForResult(intent, REQUEST_EXPORT_BACKUP);
        } catch (Exception e) {
            toast("Не удалось создать резервную копию");
        }
    }

    private SecretKeySpec deriveBackupKey(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, BACKUP_ITERATIONS, 256);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        } finally {
            spec.clearPassword();
        }
    }

    private void chooseBackupFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT_BACKUP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            pendingBackupText = null;
            return;
        }
        Uri uri = data.getData();
        if (uri == null) return;

        if (requestCode == REQUEST_EXPORT_BACKUP) writeBackup(uri);
        else if (requestCode == REQUEST_IMPORT_BACKUP) readBackupFile(uri);
    }

    private void writeBackup(Uri uri) {
        if (pendingBackupText == null) {
            toast("Нет данных для экспорта");
            return;
        }
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) {
                toast("Не удалось открыть файл");
                return;
            }
            out.write(pendingBackupText.getBytes(StandardCharsets.UTF_8));
            out.flush();
            pendingBackupText = null;
            toast("Резервная копия сохранена");
        } catch (Exception e) {
            pendingBackupText = null;
            toast("Ошибка записи резервной копии");
        }
    }

    private void readBackupFile(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                toast("Не удалось открыть файл");
                return;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > MAX_BACKUP_BYTES) {
                    toast("Файл слишком большой");
                    return;
                }
                out.write(buffer, 0, count);
            }

            JSONObject backup = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            if (!"MATAEL_BACKUP".equals(backup.optString("format")) ||
                    backup.optInt("version", -1) != 1) {
                toast("Неизвестный формат резервной копии");
                return;
            }
            askImportPassword(backup);
        } catch (Exception e) {
            toast("Не удалось прочитать файл резервной копии");
        }
    }

    private void askImportPassword(JSONObject backup) {
        EditText input = new EditText(this);
        input.setHint("Пароль резервной копии");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this).setTitle("Восстановить данные")
                .setMessage("Введи пароль, использованный при создании копии.")
                .setView(input).setNegativeButton("Отмена", null)
                .setPositiveButton("Восстановить", (d, w) ->
                        importBackup(backup, input.getText().toString())).show();
    }

    private void importBackup(JSONObject backup, String password) {
        try {
            byte[] salt = Base64.decode(backup.getString("salt"), Base64.NO_WRAP);
            byte[] iv = Base64.decode(backup.getString("iv"), Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(backup.getString("data"), Base64.NO_WRAP);
            SecretKeySpec key = deriveBackupKey(password, salt);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            String plain = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            JSONObject payload = new JSONObject(plain);

            JSONArray importedNotes = payload.getJSONArray("notes");
            JSONArray currentNotes = readNotes();
            if (currentNotes == null) return;
            JSONArray mergedNotes = mergeNotes(currentNotes, importedNotes);
            if (!saveEncryptedNotes(mergedNotes)) return;

            JSONArray importedFolders = payload.optJSONArray("folders");
            if (importedFolders != null) {
                JSONArray currentFolders = readFolders();
                if (currentFolders == null) return;
                if (!saveFolders(mergeFolders(currentFolders, importedFolders))) return;
            }

            toast("Импорт завершён. Существующие заметки сохранены.");
            showNotes();
        } catch (Exception e) {
            toast("Не удалось расшифровать копию. Проверь пароль и файл.");
        }
    }

    private JSONArray mergeNotes(JSONArray current, JSONArray imported) throws Exception {
        JSONArray result = new JSONArray();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < current.length(); i++) {
            JSONObject note = current.optJSONObject(i);
            if (note != null) {
                result.put(note);
                ids.add(note.optString("id", ""));
            }
        }
        for (int i = 0; i < imported.length(); i++) {
            JSONObject note = imported.optJSONObject(i);
            if (note == null) continue;
            String id = note.optString("id", "");
            if (id.isEmpty() || !ids.contains(id)) {
                result.put(note);
                if (!id.isEmpty()) ids.add(id);
            }
        }
        return result;
    }

    private JSONArray mergeFolders(JSONArray current, JSONArray imported) throws Exception {
        JSONArray result = new JSONArray();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < current.length(); i++) {
            JSONObject folder = current.optJSONObject(i);
            if (folder != null) {
                result.put(folder);
                ids.add(folder.optString("id", ""));
            }
        }
        for (int i = 0; i < imported.length(); i++) {
            JSONObject folder = imported.optJSONObject(i);
            if (folder == null) continue;
            String id = folder.optString("id", "");
            if (id.isEmpty() || !ids.contains(id)) {
                result.put(folder);
                if (!id.isEmpty()) ids.add(id);
            }
        }
        return result;
    }

    private LinearLayout createRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(28, 32, 28, 28);
        root.setBackgroundColor(background);
        return root;
    }

    private void addText(LinearLayout root, String text, int size, int color,
                         int top, int bottom, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(view, spaced(top, bottom));
    }

    private EditText createPinInput() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setSingleLine(true);
        input.setTextSize(22);
        input.setGravity(Gravity.CENTER);
        input.setHint("••••");
        return input;
    }

    private Button createButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setBackground(makeShape(accent, 14));
        button.setPadding(8, 4, 8, 4);
        return button;
    }

    private GradientDrawable makeShape(int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
        shape.setStroke(1, Color.argb(35, 255, 255, 255));
        return shape;
    }

    private LinearLayout.LayoutParams spaced(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = top;
        params.bottomMargin = bottom;
        return params;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 58);
        params.topMargin = 18;
        params.bottomMargin = 8;
        return params;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
