
package com.matael.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;

public class MainActivity extends Activity {

    private final int background = Color.rgb(18, 18, 20);
    private final int gold = Color.rgb(220, 184, 112);
    private final int muted = Color.rgb(170, 170, 175);
    private final int cardColor = Color.rgb(31, 31, 35);

    private SharedPreferences prefs;

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

        addText(root, "◆", 44, gold, 20, 20, true);
        addText(root, "M A T A E L", 30, gold, 8, 8, true);
        addText(root, "YOUR PRIVATE SPACE", 12, muted, 0, 40, false);

        TextView description = new TextView(this);
        description.setText(
            "Твои заметки.\nТвои мысли.\nТвоё личное пространство."
        );
        description.setTextSize(18);
        description.setTextColor(Color.WHITE);
        description.setGravity(Gravity.CENTER);
        description.setLineSpacing(12, 1f);
        root.addView(description, spaced(12, 40));

        Button button = createButton(
            prefs.contains("pin_hash")
                ? "РАЗБЛОКИРОВАТЬ MATAEL"
                : "НАСТРОИТЬ ЗАЩИТУ"
        );

        button.setOnClickListener(v -> {
            if (prefs.contains("pin_hash")) {
                askForPin();
            } else {
                setupPin();
            }
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
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String pin = input.getText().toString();

                    if (!pin.matches("[0-9]{4,8}")) {
                        input.setError("Нужно от 4 до 8 цифр");
                        return;
                    }

                    dialog.dismiss();
                    confirmPin(pin);
                })
        );

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
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String secondPin = input.getText().toString();

                    if (!firstPin.equals(secondPin)) {
                        input.setError("Коды не совпадают");
                        return;
                    }

                    if (savePin(firstPin)) {
                        dialog.dismiss();
                        showNotes();
                        toast("Защита настроена");
                    } else {
                        toast("Не удалось сохранить PIN");
                    }
                })
        );

        dialog.show();
    }

    private boolean savePin(String pin) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);

            String saltText = Base64.encodeToString(
                salt, Base64.NO_WRAP
            );

            String hash = hashPin(saltText, pin);

            return prefs.edit()
                .putString("pin_salt", saltText)
                .putString("pin_hash", hash)
                .commit();

        } catch (Exception e) {
            return false;
        }
    }

    private void askForPin() {
        EditText input = createPinInput();

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("MATAEL заблокирован")
            .setMessage("Введи PIN-код")
            .setView(input)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Открыть", null)
            .create();

        dialog.setOnShowListener(unused ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (!verifyPin(input.getText().toString())) {
                        input.setError("Неверный PIN-код");
                        return;
                    }

                    dialog.dismiss();
                    showNotes();
                })
        );

        dialog.show();
    }

    private boolean verifyPin(String pin) {
        try {
            String salt = prefs.getString("pin_salt", "");
            String savedHash = prefs.getString("pin_hash", "");
            String enteredHash = hashPin(salt, pin);

            return MessageDigest.isEqual(
                savedHash.getBytes(StandardCharsets.UTF_8),
                enteredHash.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    private String hashPin(String salt, String pin)
            throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        byte[] result = digest.digest(
            (salt + ":" + pin).getBytes(StandardCharsets.UTF_8)
        );

        return Base64.encodeToString(result, Base64.NO_WRAP);
    }

    private void showNotes() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 24, 22, 20);
        root.setBackgroundColor(background);

        addText(root, "M A T A E L", 26, gold, 4, 4, true);
        addText(root, "ТВОИ ЛИЧНЫЕ ЗАМЕТКИ", 12, muted, 0, 20, false);

        Button addButton = createButton("+ НОВАЯ ЗАМЕТКА");
        addButton.setOnClickListener(v -> editNote(null));
        root.addView(addButton, buttonParams());

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 12, 0, 12);

        JSONArray notes = readNotes();

        if (notes.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText(
                "Здесь пока пусто\n\n" +
                "Нажми «Новая заметка», чтобы сохранить первую мысль."
            );
            empty.setTextColor(muted);
            empty.setTextSize(15);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(12, 45, 12, 45);
            list.addView(empty);
        }

        for (int i = 0; i < notes.length(); i++) {
            JSONObject note = notes.optJSONObject(i);
            if (note == null) continue;

            final String id = note.optString("id", "");
            String title = note.optString("title", "");
            String body = note.optString("body", "");

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(16, 14, 16, 14);

            GradientDrawable shape = new GradientDrawable();
            shape.setColor(cardColor);
            shape.setCornerRadius(18);
            card.setBackground(shape);

            TextView heading = new TextView(this);
            heading.setText(title.isEmpty() ? "Без названия" : title);
            heading.setTextColor(gold);
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

            card.setOnClickListener(v -> editNote(id));

            LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
            cardParams.bottomMargin = 12;
            list.addView(card, cardParams);
        }

        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        Button lockButton = createButton("ЗАБЛОКИРОВАТЬ");
        lockButton.setOnClickListener(v -> showWelcome());
        root.addView(lockButton, buttonParams());

        setContentView(root);
    }

    private void editNote(String noteId) {
        JSONArray notes = readNotes();
        JSONObject existing = null;

        if (noteId != null) {
            for (int i = 0; i < notes.length(); i++) {
                JSONObject item = notes.optJSONObject(i);
                if (item != null && noteId.equals(item.optString("id"))) {
                    existing = item;
                    break;
                }
            }
        }

        final boolean isNew = existing == null;
        final String id = isNew ? UUID.randomUUID().toString() : noteId;

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(20, 8, 20, 4);

        EditText titleInput = new EditText(this);
        titleInput.setSingleLine(true);
        titleInput.setHint("Название заметки");
        titleInput.setTextColor(Color.BLACK);
        titleInput.setHintTextColor(Color.GRAY);
        titleInput.setTextSize(17);
        titleInput.setInputType(InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        titleInput.setBackgroundColor(Color.WHITE);
        titleInput.setPadding(12, 8, 12, 8);

        EditText bodyInput = new EditText(this);
        bodyInput.setHint("Напиши свою мысль...");
        bodyInput.setTextColor(Color.BLACK);
        bodyInput.setHintTextColor(Color.GRAY);
        bodyInput.setTextSize(16);
        bodyInput.setGravity(Gravity.TOP);
        bodyInput.setMinLines(6);
        bodyInput.setMaxLines(12);
        bodyInput.setInputType(InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_MULTI_LINE |
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        bodyInput.setBackgroundColor(Color.WHITE);
        bodyInput.setPadding(12, 10, 12, 10);

        if (!isNew) {
            titleInput.setText(existing.optString("title", ""));
            bodyInput.setText(existing.optString("body", ""));
        }

        LinearLayout.LayoutParams fieldParams =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
        fieldParams.bottomMargin = 12;

        form.addView(titleInput, fieldParams);
        form.addView(bodyInput, fieldParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(isNew ? "Новая заметка" : "Редактировать заметку")
            .setView(form)
            .setNegativeButton("Отмена", null)
            .setNeutralButton(isNew ? " " : "Удалить", null)
            .setPositiveButton("Сохранить", null)
            .create();

        dialog.setOnShowListener(unused -> {
            if (isNew) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setVisibility(android.view.View.GONE);
            } else {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setTextColor(Color.RED);

                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(v -> {
                        new AlertDialog.Builder(this)
                            .setTitle("Удалить заметку?")
                            .setMessage("Эту запись нельзя будет вернуть.")
                            .setNegativeButton("Отмена", null)
                            .setPositiveButton("Удалить", (d, w) -> {
                                deleteNote(id);
                                dialog.dismiss();
                                showNotes();
                            })
                            .show();
                    });
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String title = titleInput.getText().toString().trim();
                    String body = bodyInput.getText().toString().trim();

                    if (title.isEmpty() && body.isEmpty()) {
                        toast("Напиши заголовок или текст");
                        return;
                    }

                    saveNote(id, title, body);
                    dialog.dismiss();
                    showNotes();
                    toast("Заметка сохранена");
                });
        });

        dialog.show();
    }

    private JSONArray readNotes() {
        try {
            return new JSONArray(
                prefs.getString("notes_json", "[]")
            );
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveNote(String id, String title, String body) {
        JSONArray oldNotes = readNotes();
        JSONArray newNotes = new JSONArray();
        boolean replaced = false;

        for (int i = 0; i < oldNotes.length(); i++) {
            JSONObject item = oldNotes.optJSONObject(i);
            if (item == null) continue;

            if (id.equals(item.optString("id"))) {
                newNotes.put(makeNote(id, title, body));
                replaced = true;
            } else {
                newNotes.put(item);
            }
        }

        if (!replaced) {
            newNotes.put(makeNote(id, title, body));
        }

        prefs.edit().putString("notes_json", newNotes.toString()).apply();
    }

    private JSONObject makeNote(String id, String title, String body) {
        JSONObject note = new JSONObject();

        try {
            note.put("id", id);
            note.put("title", title);
            note.put("body", body);
            note.put("updated", System.currentTimeMillis());
        } catch (Exception ignored) {
        }

        return note;
    }

    private void deleteNote(String id) {
        JSONArray oldNotes = readNotes();
        JSONArray newNotes = new JSONArray();

        for (int i = 0; i < oldNotes.length(); i++) {
            JSONObject item = oldNotes.optJSONObject(i);
            if (item == null) continue;

            if (!id.equals(item.optString("id"))) {
                newNotes.put(item);
            }
        }

        prefs.edit().putString("notes_json", newNotes.toString()).apply();
    }

    private LinearLayout createRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(32, 40, 32, 40);
        root.setBackgroundColor(background);
        return root;
    }

    private void addText(
        LinearLayout root,
        String text,
        int size,
        int color,
        int top,
        int bottom,
        boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);

        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        root.addView(view, spaced(top, bottom));
    }

    private EditText createPinInput() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(22);
        input.setGravity(Gravity.CENTER);
        input.setHint("••••");
        input.setInputType(
            InputType.TYPE_CLASS_NUMBER |
            InputType.TYPE_NUMBER_VARIATION_PASSWORD
        );
        input.setPadding(20, 12, 20, 12);
        input.setBackgroundColor(Color.WHITE);
        input.setTextColor(Color.BLACK);
        input.setHintTextColor(Color.GRAY);
        return input;
    }

    private Button createButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(background);
        button.setTextSize(14);
        button.setAllCaps(false);

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(gold);
        shape.setCornerRadius(24);
        button.setBackground(shape);

        return button;
    }

    private LinearLayout.LayoutParams spaced(int top, int bottom) {
        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
        params.topMargin = top;
        params.bottomMargin = bottom;
        return params;
    }

    private LinearLayout.LayoutParams buttonParams() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 56
        );
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
