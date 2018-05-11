package com.fsck.k9.account;

import android.content.Context;

import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.helper.Utility;
import com.fsck.k9.mail.AuthType;
import com.fsck.k9.mail.ConnectionSecurity;
import com.fsck.k9.mail.ServerSettings;
import com.fsck.k9.mail.TransportUris;
import com.fsck.k9.mail.store.RemoteStore;

import timber.log.Timber;

/**
 * Created by chenxi.cui on 2018/5/10.
 */

public class MailAccountUtil {
    private static MailAccountUtil instance = null;
    private final Context mContext;

    private MailAccountUtil(Context context) {
        this.mContext = context;
    }

    public static MailAccountUtil getInstance(Context context) {
        if (instance == null) {
            synchronized (MailAccountUtil.class) {
                if (instance == null) {
                    instance = new MailAccountUtil(context);
                }
            }
        }
        return instance;
    }


    public Account genAccount(String email, String password) {
        String[] emailParts = splitEmail(email);
        String domain = emailParts[1];

        Account mAccount = null;
        if (mAccount == null) {
            mAccount = Preferences.getPreferences(mContext).newAccount();
            mAccount.setChipColor(AccountCreator.pickColor(mContext));
        }
        mAccount.setName(getOwnerName());
        mAccount.setEmail(email);
        AuthType authenticationType = AuthType.PLAIN;
        // set default uris
        // NOTE: they will be changed again in AccountSetupAccountType!
        ServerSettings storeServer = new ServerSettings(ServerSettings.Type.IMAP, "mail." + domain, 993,
                ConnectionSecurity.SSL_TLS_REQUIRED, authenticationType, email, password, null);
        ServerSettings transportServer = new ServerSettings(ServerSettings.Type.SMTP, "smtp." + domain, 587,
                ConnectionSecurity.STARTTLS_REQUIRED, authenticationType, email, password, null);
        String storeUri = RemoteStore.createStoreUri(storeServer);
        String transportUri = TransportUris.createTransportUri(transportServer);
        mAccount.setStoreUri(storeUri);
        mAccount.setTransportUri(transportUri);
        return mAccount;
    }

    private String getOwnerName() {
        String name = null;
        try {
            name = getDefaultAccountName();
        } catch (Exception e) {
            Timber.e(e, "Could not get default account name");
        }

        if (name == null) {
            name = "";
        }
        return name;
    }

    private String getDefaultAccountName() {
        String name = null;
        Account account = Preferences.getPreferences(mContext).getDefaultAccount();
        if (account != null) {
            name = account.getName();
        }
        return name;
    }

    private String[] splitEmail(String email) {
        String[] retParts = new String[2];
        String[] emailParts = email.split("@");
        retParts[0] = (emailParts.length > 0) ? emailParts[0] : "";
        retParts[1] = (emailParts.length > 1) ? emailParts[1] : "";
        return retParts;
    }

}
