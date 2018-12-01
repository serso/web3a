package org.solovyev.android.web3a;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.multidex.MultiDex;
import android.support.multidex.MultiDexApplication;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.security.Provider;
import java.security.Security;

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

    @Override
    public void onCreate() {
        super.onCreate();

        setupBouncyCastle();
    }

    private void setupBouncyCastle() {
        final Provider provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (provider == null) {
            // Web3j will set up the provider lazily when it's first used.
            return;
        }
        if (provider.getClass().equals(BouncyCastleProvider.class)) {
            // BC with same package name, shouldn't happen in real life.
            return;
        }
        // Android registers its own BC provider. As it might be outdated and might not include
        // all needed ciphers, we substitute it with a known BC bundled in the app.
        // Android's BC has its package rewritten to "com.android.org.bouncycastle" and because
        // of that it's possible to have another BC implementation loaded in VM.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.addProvider(new BouncyCastleProvider());
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
