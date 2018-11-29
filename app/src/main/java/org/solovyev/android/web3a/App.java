package org.solovyev.android.web3a;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.multidex.MultiDex;
import android.support.multidex.MultiDexApplication;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

public class App extends MultiDexApplication {

    @NonNull
    static final String TAG = "web3a";
    @NonNull
    static final String PASSWORD = "";

    @NonNull
    public static App get(@NonNull Context context) {
        return (App) context.getApplicationContext();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @NonNull
    Web3j getWeb3j() {
        return Web3jHolder.INSTANCE;
    }

    @NonNull
    SharedPreferences getPrefs() {
        return getSharedPreferences("wallet", 0);
    }

    private static class Web3jHolder {
        @NonNull
        private static final Web3j INSTANCE = Web3j.build(
                new HttpService("https://rinkeby.infura.io/v3/817f42773d7d47f9bf35a7b9c353a00a"));
    }
}
