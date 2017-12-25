/*
 * Copyright 2018  Pranav Raut
 *
 *         Permission is hereby granted, free of charge, to any person obtaining a copy of this
 *         software and associated documentation files (the "Software"), to deal in the Software
 *         without restriction, including without limitation the rights to use, copy, modify, merge,
 *         publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons
 *         to whom the Software is furnished to do so, subject to the following conditions:
 *
 *         The above copyright notice and this permission notice shall be included in all copies
 *         or substantial portions of the Software.
 *
 *         THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 *         INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 *         PURPOSE AND NONINFINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 *         FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 *         OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 *         DEALINGS IN THE SOFTWARE.
 */

package pranav.preons.burglaralert;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import pranav.utilities.Utilities;

public class PasswordActivity extends Activity {
    SharedPreferences prefs;
    String s = "";
    private TextView password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_dialog);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        password = findViewById(R.id.password);
        setFinishOnTouchOutside(false);
    }

    public synchronized void addKey(View view) {
        switch (view.getId()) {
            case R.id.ok:
                if (prefs.getString("password", "1234").equals(s)) {
                    stopService(new Intent(this, PlayerService.class));
                    finish();
                } else {
                    ObjectAnimator translationX = ObjectAnimator.ofFloat(password, "translationX", 0, 10f, 0f, -10f, 0f);
                    translationX.setRepeatCount(3);
                    translationX.setDuration(150);
                    translationX.start();
                    translationX.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animator) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {
                            password.setText(s = "");
                        }

                        @Override
                        public void onAnimationCancel(Animator animator) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animator) {

                        }
                    });
                }
                break;
            case R.id.delete:
                if (!s.isEmpty()) {
                    CharSequence a = password.getText().subSequence(0, s.length() - 1);
                    password.setText(a);
                    s = s.substring(0, s.length() - 1);
                }
                break;
            default:
                if (view instanceof Button) {
                    if (s.length() >= 6) return;
                    s += ((Button) view).getText().toString();
                    password.append("⚫");
                }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (Utilities.isServiceRunning(this, PlayerService.class))
            recreate();
    }
}
