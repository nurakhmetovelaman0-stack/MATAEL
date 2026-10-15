
package com.matael.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import android.util.Base64;

public class MainActivity extends Activity {

    private final int background = Color.rgb(18, 18, 20);
    private final int gold = Color.rgb(220, 184, 112);
    private final int muted = Color.rgb(170, 170, 175);

    private android.content.SharedPreferences prefs;

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

        addText(root, "◆", 44, gold, 24, 24, true);
        addText(root, "M A T A E L", 30, gold, 12, 8, true);
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

        boolean hasPin = prefs.contains("pin_hash");

        Button button = createButton(
            hasPin ? "РАЗБЛОКИРОВАТЬ MATAEL" : "НАСТРОИТЬ ЗАЩИТУ"
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
            .setNegativeButton("Отмена", (d, w) -> showWelcome())
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
            .setMessage("Введи тот же код ещё раз")
            .setView(input)
            .setNegativeButton("Отмена", (d, w) -> showWelcome())
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

                    savePin(firstPin);
                    dialog.dismiss();

                    Toast.makeText(
                        this,
                        "PIN-код установлен",
                        Toast.LENGTH_SHORT
                    ).show();

                    showProtectedHome();
                })
        );

        dialog.show();
    }

    private void savePin(String pin) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);

            String saltText = Base64.encodeToString(
                salt, Base64.NO_WRAP
            );

            String hash = hashPin(saltText, pin);

            prefs.edit()
                .putString("pin_salt", saltText)
                .putString("pin_hash", hash)
                .apply();

        } catch (Exception e) {
            Toast.makeText(
                this,
                "Не удалось сохранить защиту",
                Toast.LENGTH_LONG
            ).show();
        }
    }

    private void askForPin() {
        EditText input = createPinInput();

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("MATAEL заблокирован")
            .setMessage("Введи свой PIN-код")
            .setView(input)
            .setNegativeButton("Отмена", (d, w) -> showWelcome())
            .setPositiveButton("Открыть", null)
            .create();

        dialog.setOnShowListener(unused ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String pin = input.getText().toString();

                    if (!verifyPin(pin)) {
                        input.setError("Неверный PIN-код");
                        return;
                    }

                    dialog.dismiss();
                    showProtectedHome();
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

    private void showProtectedHome() {
        LinearLayout root = createRoot();

        addText(root, "◆", 44, gold, 24, 16, true);
        addText(root, "M A T A E L", 28, gold, 12, 8, true);
        addText(root, "ЗАЩИЩЁННОЕ ПРОСТРАНСТВО",
            12, muted, 0, 36, false);

        TextView message = new TextView(this);
        message.setText(
            "Доступ открыт\n\n" +
            "Твой PIN-код установлен.\n\n" +
            "Следующий этап — личные заметки."
        );
        message.setTextSize(17);
        message.setTextColor(Color.WHITE);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(8, 1f);

        root.addView(message, spaced(12, 40));

        Button lockButton = createButton("ЗАБЛОКИРОВАТЬ");
        lockButton.setOnClickListener(v -> showWelcome());

        root.addView(lockButton, buttonParams());

        setContentView(root);
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
        input.setSelectAllOnFocus(true);
        input.setPadding(20, 12, 20, 12);
        input.setBackgroundColor(Color.rgb(240, 240, 240));
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
            LinearLayout.LayoutParams.MATCH_PARENT,
            60
        );
    }
}
