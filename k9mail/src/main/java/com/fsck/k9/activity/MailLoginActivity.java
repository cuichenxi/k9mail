//package com.fsck.k9.activity;
//
//import android.content.Context;
//import android.content.Intent;
//import android.os.Bundle;
//
//import com.fsck.k9.Account;
//import com.fsck.k9.K9;
//import com.fsck.k9.Preferences;
//import com.fsck.k9.R;
//import com.fsck.k9.account.MailAccountUtil;
//import com.fsck.k9.activity.setup.AccountSetupCheckSettings;
//import com.fsck.k9.activity.setup.LoginActivity;
//import com.fsck.k9.activity.setup.AccountSetupNames;
//
///**
// * Created by chenxi.cui on 2018/5/10.
// */
//
//public class MailLoginActivity extends K9Activity {
//    private boolean mCheckedIncoming = false;
//    private Account mAccount;
//
//    public static void startActivity(Context context) {
//        Intent intent = new Intent(context, MailLoginActivity.class);
//        context.startActivity(intent);
//    }
//
//    @Override
//    public void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_login_layout);
//        mAccount = MailAccountUtil.getInstance(this).genAccount("chenxi.cui@ucarinc.com", "chenxi.1234");
//        LoginActivity.actionCheckSettings(this, mAccount, AccountSetupCheckSettings.CheckDirection.INCOMING);
//    }
//
//    @Override
//    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        if (resultCode == RESULT_OK) {
//            if (!mCheckedIncoming) {
//                //We've successfully checked incoming.  Now check outgoing.
//                mCheckedIncoming = true;
//                LoginActivity.actionCheckSettings(this, mAccount, AccountSetupCheckSettings.CheckDirection.OUTGOING);
//            } else {
//                //We've successfully checked outgoing as well.
//                mAccount.setDescription(mAccount.getEmail());
//                mAccount.save(Preferences.getPreferences(this));
//                K9.setServicesEnabled(this);
////                AccountSetupNames.actionSetNames(this, mAccount);
//                mAccount.save(Preferences.getPreferences(this));
//                Accounts.listAccounts(this);
//                finish();
//            }
//        }
//    }
//}
