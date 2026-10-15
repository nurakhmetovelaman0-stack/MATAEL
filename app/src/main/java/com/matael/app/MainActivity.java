
package com.matael.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private final int background = Color.rgb(18, 18, 20);
    private final int gold = Color.rgb(220, 184, 112);
    private final int muted = Color.rgb(170, 170, 175);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(32, 40, 32, 40);
        root.setBackgroundColor(background);

        TextView lock = new TextView(this);
        lock.setText("◆");
        lock.setTextSize(44);
        lock.setTextColor(gold);
        lock.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("M A T A E L");
        title.setTextSize(30);
        title.setTextColor(gold);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("YOUR PRIVATE SPACE");
        subtitle.setTextSize(12);
        subtitle.setTextColor(muted);
        subtitle.setGravity(Gravity.CENTER);

        TextView description = new TextView(this);
        description.setText(
            "Твои заметки.\nТвои мысли.\nТвоё личное пространство."
        );
        description.setTextSize(18);
        description.setTextColor(Color.WHITE);
        description.setGravity(Gravity.CENTER);
        description.setLineSpacing(12, 1f);

        Button button = new Button(this);
        button.setText("НАСТРОИТЬ ЗАЩИТУ");
        button.setTextColor(background);
        button.setTextSize(14);
        button.setAllCaps(false);

        GradientDrawable buttonShape = new GradientDrawable();
        buttonShape.setColor(gold);
        buttonShape.setCornerRadius(24);
        button.setBackground(buttonShape);

        button.setOnClickListener(v ->
            Toast.makeText(
                this,
                "Следующий этап — настройка PIN-кода",
                Toast.LENGTH_LONG
            ).show()
        );

        root.addView(lock, spaced(24, 24));
        root.addView(title, spaced(12, 8));
        root.addView(subtitle, spaced(0, 48));
        root.addView(description, spaced(12, 40));
        root.addView(button, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 60
        ));

        setContentView(root);
    }

    private LinearLayout.LayoutParams spaced(
        int top, int bottom
    ) {
        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
        params.topMargin = top;
        params.bottomMargin = bottom;
        return params;
    }
}
