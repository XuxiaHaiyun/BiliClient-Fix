package com.RobinNotBad.BiliClient.activity.settings.login;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.adapter.viewpager.ViewPagerFragmentAdapter;

import java.util.ArrayList;
import java.util.List;

//登录页面，参考了腕上哔哩和WearBili的代码

public class LoginActivity extends BaseActivity {

    private ViewPager viewPager;
    private PasswordLoginFragment passwordLoginFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_viewpager);
        Log.e("debug", "进入登录页面");
        setPageName("登录");

        boolean from_setup = getIntent().getBooleanExtra("from_setup", false);

        /*
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            startActivity(new Intent(this, SpecialLoginActivity.class).putExtra("from_setup",from_setup));
            finish();
        }
         */

        viewPager = findViewById(R.id.viewPager);
        passwordLoginFragment = PasswordLoginFragment.newInstance();

        List<Fragment> fragmentList = new ArrayList<>();
        fragmentList.add(QRLoginFragment.newInstance(from_setup));
        fragmentList.add(passwordLoginFragment);

        viewPager.setOffscreenPageLimit(fragmentList.size());
        ViewPagerFragmentAdapter vpfAdapter = new ViewPagerFragmentAdapter(getSupportFragmentManager(), fragmentList);
        viewPager.setAdapter(vpfAdapter);

        findViewById(R.id.loading).setVisibility(View.GONE);
    }

    /**
     * 从扫码页切换到密码 / 短信登录页
     *
     * @param smsMode true 显示短信登录，false 显示密码登录
     */
    public void switchToPasswordLogin(boolean smsMode) {
        if (viewPager != null) viewPager.setCurrentItem(1, true);
        if (passwordLoginFragment != null) passwordLoginFragment.setMode(smsMode);
    }

    /**
     * 从密码页返回扫码登录页
     */
    public void switchToQrLogin() {
        if (viewPager != null) viewPager.setCurrentItem(0, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
