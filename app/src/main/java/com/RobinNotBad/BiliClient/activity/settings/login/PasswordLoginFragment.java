package com.RobinNotBad.BiliClient.activity.settings.login;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.SplashActivity;
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.api.LoginApi;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONObject;

/**
 * 密码登录 / 短信验证码登录（迁移自 PiliPlus，支持 APP 与网页两套接口）。
 * 若接口返回需要图形验证码（风控），会提示改用扫码登录。
 */
public class PasswordLoginFragment extends Fragment {

    private EditText inputUsername;
    private EditText inputPassword;
    private EditText inputTel;
    private EditText inputSmsCode;
    private View passwordSection;
    private View smsSection;
    private MaterialCardView sendCodeBtn;
    private TextView sendCodeText;
    private RadioGroup methodGroup;

    private boolean smsMode = false;
    private boolean useAppApi = true;
    private boolean requesting = false;
    private boolean pendingSmsMode = false;

    private String captchaKey = "";
    private CountDownTimer cooldownTimer;

    public PasswordLoginFragment() {
    }

    public static PasswordLoginFragment newInstance() {
        return new PasswordLoginFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_password_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        inputUsername = view.findViewById(R.id.inputUsername);
        inputPassword = view.findViewById(R.id.inputPassword);
        inputTel = view.findViewById(R.id.inputTel);
        inputSmsCode = view.findViewById(R.id.inputSmsCode);
        passwordSection = view.findViewById(R.id.passwordSection);
        smsSection = view.findViewById(R.id.smsSection);
        sendCodeBtn = view.findViewById(R.id.sendCodeBtn);
        sendCodeText = view.findViewById(R.id.sendCodeText);

        RadioGroup methodGroup = view.findViewById(R.id.methodGroup);
        methodGroup.setOnCheckedChangeListener((group, checkedId) -> {
            smsMode = checkedId == R.id.methodSms;
            passwordSection.setVisibility(smsMode ? View.GONE : View.VISIBLE);
            smsSection.setVisibility(smsMode ? View.VISIBLE : View.GONE);
        });
        this.methodGroup = methodGroup;

        RadioGroup apiGroup = view.findViewById(R.id.apiGroup);
        apiGroup.setOnCheckedChangeListener((group, checkedId) -> useAppApi = checkedId == R.id.apiApp);

        sendCodeBtn.setOnClickListener(v -> sendSmsCode());

        MaterialCardView loginBtn = view.findViewById(R.id.loginBtn);
        loginBtn.setOnClickListener(v -> doLogin());

        MaterialCardView backToQr = view.findViewById(R.id.backToQr);
        backToQr.setOnClickListener(v -> {
            if (isAdded() && requireActivity() instanceof LoginActivity)
                ((LoginActivity) requireActivity()).switchToQrLogin();
        });

        applyMode(pendingSmsMode);
    }

    /**
     * 由外部（扫码页按钮）切换登录方式
     *
     * @param smsMode true 短信登录，false 密码登录
     */
    public void setMode(boolean smsMode) {
        pendingSmsMode = smsMode;
        applyMode(smsMode);
    }

    private void applyMode(boolean smsMode) {
        if (methodGroup != null) methodGroup.check(smsMode ? R.id.methodSms : R.id.methodPassword);
    }

    private void sendSmsCode() {
        String tel = inputTel.getText().toString().trim();
        if (tel.isEmpty()) {
            MsgUtil.showMsg("请输入手机号");
            return;
        }
        if (requesting) return;
        requesting = true;

        CenterThreadPool.run(() -> {
            try {
                JSONObject result = useAppApi ? LoginApi.sendSmsCodeApp(tel) : LoginApi.sendSmsCodeWeb(tel);
                int code = result.optInt("code", -1);
                JSONObject data = result.optJSONObject("data");
                String key = data == null ? "" : data.optString("captcha_key", "");
                String recaptcha = data == null ? "" : data.optString("recaptcha_url", "");

                CenterThreadPool.runOnUiThread(() -> {
                    if (code == 0 && key != null && !key.isEmpty() && (recaptcha == null || recaptcha.isEmpty())) {
                        captchaKey = key;
                        MsgUtil.showMsg("验证码已发送");
                        startCooldown();
                    } else if (code == 0 && (key == null || key.isEmpty())) {
                        MsgUtil.showMsgLong("发送失败：接口未返回验证信息，可能需要图形验证，请改用扫码登录");
                    } else {
                        MsgUtil.showMsgLong("发送失败(" + code + ")：" + result.optString("message")
                                + "\n若提示需要验证，请改用扫码登录");
                    }
                });
            } catch (Exception e) {
                CenterThreadPool.runOnUiThread(() -> MsgUtil.showMsg("发送失败：" + e.getMessage()));
            } finally {
                requesting = false;
            }
        });
    }

    private void doLogin() {
        if (requesting) return;

        final String username = inputUsername.getText().toString().trim();
        final String password = inputPassword.getText().toString();
        final String tel = inputTel.getText().toString().trim();
        final String smsCode = inputSmsCode.getText().toString().trim();

        if (smsMode) {
            if (tel.isEmpty()) {
                MsgUtil.showMsg("请输入手机号");
                return;
            }
            if (smsCode.isEmpty()) {
                MsgUtil.showMsg("请输入验证码");
                return;
            }
            if (captchaKey.isEmpty()) {
                MsgUtil.showMsg("请先获取验证码");
                return;
            }
        } else {
            if (username.isEmpty() || password.isEmpty()) {
                MsgUtil.showMsg("账号或密码不能为空");
                return;
            }
        }

        requesting = true;

        CenterThreadPool.run(() -> {
            try {
                JSONObject result;
                if (smsMode) {
                    result = useAppApi
                            ? LoginApi.loginBySmsApp(tel, smsCode, captchaKey)
                            : LoginApi.loginBySmsWeb(tel, smsCode, captchaKey);
                } else {
                    result = useAppApi
                            ? LoginApi.loginByPasswordApp(username, password)
                            : LoginApi.loginByPasswordWeb(username, password);
                }

                int code = result.optInt("code", -1);
                if (code == 0) {
                    JSONObject data = result.optJSONObject("data");
                    if (data != null && data.optInt("status", 0) == 2) {
                        CenterThreadPool.runOnUiThread(() -> MsgUtil.showMsgLong(
                                "本次登录环境存在风险，需要手机号验证，请改用扫码登录\n"
                                        + data.optString("message")));
                        return;
                    }
                    CenterThreadPool.runOnUiThread(this::onLoginSuccess);
                } else if (code == -105) {
                    CenterThreadPool.runOnUiThread(() -> MsgUtil.showMsgLong(
                            "需要图形验证码，请改用扫码登录"));
                } else {
                    CenterThreadPool.runOnUiThread(() -> MsgUtil.showMsgLong(
                            "登录失败(" + code + ")：" + result.optString("message")));
                }
            } catch (Exception e) {
                CenterThreadPool.runOnUiThread(() -> MsgUtil.showMsg("登录失败：" + e.getMessage()));
            } finally {
                requesting = false;
            }
        });
    }

    private void onLoginSuccess() {
        if (!isAdded()) return;
        MsgUtil.showMsg("登录成功！");

        InstanceActivity instance = BiliTerminal.getInstanceActivityOnTop();
        if (instance != null && !instance.isDestroyed()) instance.finish();

        NetWorkUtil.refreshHeaders();

        startActivity(new Intent(requireContext(), SplashActivity.class));
        requireActivity().finish();
    }

    private void startCooldown() {
        if (cooldownTimer != null) cooldownTimer.cancel();
        sendCodeBtn.setEnabled(false);
        cooldownTimer = new CountDownTimer(60000, 1000) {
            @SuppressLint("SetTextI18n")
            @Override
            public void onTick(long millisUntilFinished) {
                sendCodeText.setText((millisUntilFinished / 1000) + "s");
            }

            @Override
            public void onFinish() {
                sendCodeBtn.setEnabled(true);
                sendCodeText.setText("获取验证码");
            }
        }.start();
    }

    @Override
    public void onDestroy() {
        if (cooldownTimer != null) cooldownTimer.cancel();
        super.onDestroy();
    }
}
