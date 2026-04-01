package org.primftpd.ui;


import android.view.Menu;

import androidx.annotation.NonNull;

public class LeanbackActivity extends MainTabsActivity {

    @NonNull
    @Override
    protected PftpdFragment createPftpdFragment() {
        return new LeanbackFragment();
    }
    //Original version: 未注解的方法重写注解为 @NotNull 的方法
    //我猜改了也没事（？


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // no menu for leanback
        return true;
    }

    @Override
    protected boolean isLeanback() {
        return true;
    }
}
