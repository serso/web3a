package org.solovyev.android.web3a;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.web3j.crypto.Bip39Wallet;
import org.web3j.crypto.Bip44WalletUtils;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.math.BigInteger;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView mMnemonic;
    private TextView mAddress;
    private TextView mBalance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMnemonic = findViewById(R.id.mnemonic);
        mMnemonic.setOnClickListener(this);

        mAddress = findViewById(R.id.address);
        mAddress.setOnClickListener(this);

        mBalance = findViewById(R.id.balance);
        mBalance.setOnClickListener(this);

        new LoadWalletTask(this).execute();
    }

    private void onWalletLoaded(@Nullable Bip39Wallet wallet) {
        if (wallet == null) {
            mMnemonic.setError("No wallet");
            return;
        }
        mMnemonic.setText(wallet.getMnemonic());
        new LoadCredentialsTask(this, wallet).execute();
    }

    private void onCredentialsLoaded(@NonNull Credentials credentials) {
        mAddress.setText(credentials.getAddress());
        new GetBalanceTask(this, credentials).execute();
    }

    private void onBalanceReceived(@Nullable BigInteger balance) {
        mBalance.setText(balance == null ? "N/A" : balance.toString());
    }

    @Override
    public void onClick(View v) {
        if (v instanceof TextView) {
            final ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            final CharSequence text = ((TextView) v).getText();
            clipboard.setPrimaryClip(ClipData.newPlainText(text, text));
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private abstract static class BaseTask<R> extends AsyncTask<Void, Void, R> {
        @SuppressLint("StaticFieldLeak")
        @NonNull
        final Context mContext;
        @NonNull
        final WeakReference<MainActivity> mActivity;

        private BaseTask(@NonNull MainActivity activity) {
            mContext = activity.getApplicationContext();
            mActivity = new WeakReference<>(activity);
        }

        @Override
        protected final void onPostExecute(@Nullable R result) {
            super.onPostExecute(result);
            final MainActivity activity = mActivity.get();
            if (activity != null) {
                handleResult(activity, result);
            }
        }

        protected abstract void handleResult(@NonNull MainActivity activity, R result);

        final void handleError(@NonNull Exception e) {
            Log.e(App.TAG, e.getMessage(), e);
            final MainActivity activity = mActivity.get();
            if (activity == null) return;
            activity.runOnUiThread(
                    () -> Toast.makeText(mContext, e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private static class LoadCredentialsTask extends BaseTask<Credentials> {
        @NonNull
        private final Bip39Wallet mWallet;

        private LoadCredentialsTask(@NonNull MainActivity activity,
                                    @NonNull Bip39Wallet wallet) {
            super(activity);
            mWallet = wallet;
        }

        @NonNull
        @Override
        protected Credentials doInBackground(Void... voids) {
            return Bip44WalletUtils.loadBip44Credentials(App.PASSWORD, mWallet.getMnemonic());
        }

        @Override
        protected void handleResult(@NonNull MainActivity activity,
                                    @NonNull Credentials credentials) {
            activity.onCredentialsLoaded(credentials);
        }
    }

    private static class GetBalanceTask extends BaseTask<BigInteger> {
        @NonNull
        private final Credentials mCredentials;

        private GetBalanceTask(@NonNull MainActivity activity,
                               @NonNull Credentials credentials) {
            super(activity);
            mCredentials = credentials;
        }

        @Override
        protected void handleResult(@NonNull MainActivity activity, @Nullable BigInteger balance) {
            activity.onBalanceReceived(balance);
        }

        @Override
        protected BigInteger doInBackground(Void... voids) {
            final Web3j web3j = App.get(mContext).getWeb3j();
            try {
                return web3j
                        .ethGetBalance(mCredentials.getAddress(), DefaultBlockParameterName.LATEST)
                        .send().getBalance();
            } catch (IOException e) {
                handleError(e);
            }
            return null;
        }
    }

    private static class LoadWalletTask extends BaseTask<Bip39Wallet> {

        private LoadWalletTask(@NonNull MainActivity activity) {
            super(activity);
        }

        @Override
        protected void handleResult(@NonNull MainActivity activity, @Nullable Bip39Wallet wallet) {
            activity.onWalletLoaded(wallet);
        }

        @Nullable
        @Override
        protected Bip39Wallet doInBackground(Void... voids) {
            final File dir = mContext.getFilesDir();
            try {
                final SharedPreferences prefs = App.get(mContext).getPrefs();
                final String filename = prefs.getString("filename", "");
                final String mnemonic = prefs.getString("mnemonic", "");
                if (TextUtils.isEmpty(filename) || TextUtils.isEmpty(mnemonic)) {
                    return generate(dir);
                }
                final Credentials credentials =
                        Bip44WalletUtils.loadCredentials(App.PASSWORD, new File(dir, filename));
                if (credentials == null) return generate(dir);
                return new Bip39Wallet(filename, mnemonic);
            } catch (CipherException | IOException e) {
                handleError(e);
            }
            return null;
        }

        @NonNull
        private Bip39Wallet generate(@NonNull File dir) throws CipherException, IOException {
            final Bip39Wallet wallet = Bip44WalletUtils.generateBip44Wallet(App.PASSWORD, dir);
            final File file = new File(dir, wallet.getFilename());
            if (!file.exists()) throw new IOException("No file created");

            final SharedPreferences prefs = App.get(mContext).getPrefs();
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putString("filename", wallet.getFilename());
            editor.putString("mnemonic", wallet.getMnemonic());
            editor.apply();

            return wallet;
        }
    }
}
