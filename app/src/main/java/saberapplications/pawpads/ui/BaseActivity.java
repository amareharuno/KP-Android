package saberapplications.pawpads.ui;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.location.LocationListener;
import com.quickblox.auth.QBAuth;
import com.quickblox.chat.QBChatService;
import com.quickblox.chat.QBGroupChat;
import com.quickblox.chat.QBPrivacyListsManager;
import com.quickblox.chat.QBPrivateChat;
import com.quickblox.chat.QBSystemMessagesManager;
import com.quickblox.chat.exception.QBChatException;
import com.quickblox.chat.listeners.QBGroupChatManagerListener;
import com.quickblox.chat.listeners.QBMessageListener;
import com.quickblox.chat.listeners.QBPrivateChatManagerListener;
import com.quickblox.chat.listeners.QBSystemMessageListener;
import com.quickblox.chat.model.QBChatMessage;
import com.quickblox.chat.model.QBPrivacyList;
import com.quickblox.core.QBEntityCallback;
import com.quickblox.core.exception.BaseServiceException;
import com.quickblox.core.exception.QBResponseException;
import com.quickblox.users.model.QBUser;

import org.greenrobot.eventbus.EventBus;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;

import java.util.Date;

import saberapplications.pawpads.C;
import saberapplications.pawpads.R;
import saberapplications.pawpads.UserStatusHelper;
import saberapplications.pawpads.Util;
import saberapplications.pawpads.events.UpdateChatEvent;
import saberapplications.pawpads.service.UserLocationService;
import saberapplications.pawpads.ui.chat.ChatActivity;
import saberapplications.pawpads.ui.chat.ChatGroupActivity;
import saberapplications.pawpads.ui.home.SplashActivity;

public abstract class BaseActivity extends AppCompatActivity
        implements LocationListener {
    private static final int RECREATE_SESSION = 2000;
    private static int openActivitiesCount = 0;

    protected boolean isExternalDialogOpened;

    protected static Integer currentUserId;
    protected static QBUser currentQBUser;

    //    private Location lastLocation;
    private boolean isActive;
    //    protected static QBLocation qbLocation;
    protected boolean isReopened;

    protected SharedPreferences preferences;
    protected QBPrivateChatManagerListener chatListener = new QBPrivateChatManagerListener() {
        @Override
        public void chatCreated(QBPrivateChat qbPrivateChat, final boolean createdLocally) {
            if (!createdLocally) {
                qbPrivateChat.addMessageListener(new QBMessageListener<QBPrivateChat>() {
                    @Override
                    public void processMessage(final QBPrivateChat qbPrivateChat, final QBChatMessage qbChatMessage) {
                        EventBus.getDefault().post(qbChatMessage);
                        if (isFinishing()) return;
                        BaseActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onChatMessage(qbPrivateChat, qbChatMessage);
                                UserStatusHelper.setUserStatusByNewMessage(qbChatMessage.getSenderId());
                            }
                        });

                    }

                    @Override
                    public void processError(QBPrivateChat qbPrivateChat, QBChatException e, QBChatMessage qbChatMessage) {
                        if (isFinishing()) return;
                        Util.onError(e, BaseActivity.this);
                    }

                });
            }
        }
    };

    protected QBGroupChatManagerListener groupChatListener = new QBGroupChatManagerListener() {
        @Override
        public void chatCreated(QBGroupChat qbGroupChat) {
            qbGroupChat.addMessageListener(new QBMessageListener<QBGroupChat>() {
                @Override
                public void processMessage(final QBGroupChat qbGroupChat, final QBChatMessage qbChatMessage) {
                    if (isFinishing()) return;
                    EventBus.getDefault().post(qbChatMessage);
                    BaseActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onGroupChatMessage(qbGroupChat, qbChatMessage);
                            UserStatusHelper.setUserStatusByNewMessage(qbChatMessage.getSenderId());
                        }
                    });

                }

                @Override
                public void processError(QBGroupChat qbGroupChat, QBChatException e, QBChatMessage qbChatMessage) {
                    if (isFinishing()) return;
                    Util.onError(e, BaseActivity.this);
                }

            });
        }
    };

    private QBSystemMessagesManager systemMessagesManager;

    private QBSystemMessageListener systemMessageListener = new QBSystemMessageListener() {
        @Override
        public void processMessage(final QBChatMessage qbChatMessage) {
            if (isFinishing()) return;
            BaseActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onGroupChatMessage(null, qbChatMessage);
                    UserStatusHelper.setUserStatusByNewMessage(qbChatMessage.getSenderId());
                }
            });
        }

        @Override
        public void processError(QBChatException e, QBChatMessage qbChatMessage) {
            if (isFinishing()) return;
            Util.onError(e, BaseActivity.this);
        }
    };


    BroadcastReceiver locationChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Location location = intent.getParcelableExtra(UserLocationService.LOCATION);
            onLocationChanged(location);
        }
    };

    BroadcastReceiver closeActivity = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isReopened = false;
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        currentUserId = preferences.getInt(C.QB_USERID, 0);
        Crashlytics.setUserIdentifier(currentUserId.toString());
        LocalBroadcastManager.getInstance(this).registerReceiver(
                closeActivity, new IntentFilter(C.CLOSE_ALL_APP_ACTIVITIES)
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(closeActivity);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (isLoggedIn() && QBChatService.getInstance().isLoggedIn()) {
            try {
                onQBConnect(isReopened);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        isActive = true;
        if (!isLoggedIn()) {
            Intent intent = new Intent(this, SplashActivity.class);
            intent.putExtra(C.RETURN_RESULT, true);
            startActivityForResult(intent, RECREATE_SESSION);
            return;
        }
        if (!UserLocationService.isRunning()) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            UserLocationService.startService(preferences.getInt(C.QB_USERID, 0));
        }

        incrementActivityCount();

        LocalBroadcastManager.getInstance(this).registerReceiver(
                locationChanged, new IntentFilter(UserLocationService.LOCATION_CHANGED)
        );

        if (!QBChatService.getInstance().isLoggedIn()
                ) {
            loginToChat();
            return;
        } else if (QBChatService.getInstance().getPrivateChatManager() == null) {
            reconnectToChat();
        } else {
            QBChatService.getInstance().getPrivateChatManager().addPrivateChatManagerListener(chatListener);
            QBChatService.getInstance().getGroupChatManager().addGroupChatManagerListener(groupChatListener);
            systemMessagesManager = QBChatService.getInstance().getSystemMessagesManager();
            systemMessagesManager.addSystemMessageListener(systemMessageListener);
        }


    }

    public void reconnectToChat() {
        if (QBChatService.getInstance().isLoggedIn()) {
            QBChatService.getInstance().logout(new QBEntityCallback<Void>() {
                @Override
                public void onSuccess(Void aVoid, Bundle bundle) {
                    loginToChat();
                }

                @Override
                public void onError(final QBResponseException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Util.onError(e, BaseActivity.this);
                        }
                    });
                }
            });
        } else {
            loginToChat();
        }
    }


    @Override
    protected void onStop() {
        super.onStop();
        isActive = false;
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationChanged);
        decrementActivityCount();
        if (QBChatService.getInstance().getPrivateChatManager() != null) {
            QBChatService.getInstance().getPrivateChatManager().removePrivateChatManagerListener(chatListener);
        }
        if (QBChatService.getInstance().getGroupChatManager() != null) {
            QBChatService.getInstance().getGroupChatManager().addGroupChatManagerListener(groupChatListener);
        }
        isReopened = true;
    }

    public void logOutChat() {
        if (QBChatService.getInstance().isLoggedIn()) {
            QBChatService.getInstance().logout(new QBEntityCallback<Void>() {
                @Override
                public void onSuccess(Void aVoid, Bundle bundle) {

                }

                @Override
                public void onError(QBResponseException e) {

                }
            });


        }
    }


    protected void loginToChat() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final QBUser qbUser = new QBUser();
        qbUser.setId(prefs.getInt(C.QB_USERID, 0));
        try {
            qbUser.setPassword(QBAuth.getBaseService().getToken());
        } catch (BaseServiceException e) {
            e.printStackTrace();
        }
        if (QBChatService.getInstance().isLoggedIn()) return;
        QBChatService.getInstance().login(qbUser, new QBEntityCallback() {
            @Override
            public void onSuccess(Object o, Bundle bundle) {
                QBChatService.getInstance().startAutoSendPresence(60);
                QBPrivacyListsManager privacyListsManager = QBChatService.getInstance().getPrivacyListsManager();
                try {
                    if (privacyListsManager.getPrivacyLists().size() > 0) {
                        QBPrivacyList list = privacyListsManager.getPrivacyList("public");
                        if (list != null) {
                            list.setDefaultList(true);
                            list.setActiveList(true);
                        }
                    }
                    QBChatService.getInstance().getPrivateChatManager().addPrivateChatManagerListener(chatListener);
                    QBChatService.getInstance().getGroupChatManager().addGroupChatManagerListener(groupChatListener);
                } catch (SmackException.NotConnectedException e) {
                    e.printStackTrace();
                } catch (XMPPException.XMPPErrorException e) {
                    e.printStackTrace();
                } catch (SmackException.NoResponseException e) {
                    e.printStackTrace();
                }

                QBChatService.getInstance().getPrivateChatManager().addPrivateChatManagerListener(chatListener);
                QBChatService.getInstance().getGroupChatManager().addGroupChatManagerListener(groupChatListener);
                systemMessagesManager = QBChatService.getInstance().getSystemMessagesManager();
                systemMessagesManager.addSystemMessageListener(systemMessageListener);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            onQBConnect(isReopened);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

            @Override
            public void onError(final QBResponseException e) {
                if (e.getMessage().equals("You have already logged in chat")) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Util.onError(e, BaseActivity.this);
                    }
                });
            }
        });
    }


    public void onQBConnect(boolean isActivityReopened) throws Exception {

    }


    @Override
    public void onLocationChanged(Location location) {

    }


    public synchronized void incrementActivityCount() {

        openActivitiesCount++;
    }

    public synchronized void decrementActivityCount() {

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                openActivitiesCount--;
                if (openActivitiesCount == 0) {
                    if (!isExternalDialogOpened) {
                        logOutChat();
                        UserLocationService.stop();
                    }
                    //stoplocation updates

                }
            }
        }, getWaitBeforeLogoffTime());
    }

    protected long getWaitBeforeLogoffTime() {
        return 500;
    }

    public Integer getUserId() {
        return currentUserId;
    }

    protected boolean isActive() {
        return isActive;
    }

    public boolean isLoggedIn() {
        try {
            Date expDate = QBAuth.getBaseService().getTokenExpirationDate();
            String token = QBAuth.getBaseService().getToken();
            if (expDate == null) return false;
            return expDate.getTime() > System.currentTimeMillis() && token != null;
        } catch (BaseServiceException e) {
            e.printStackTrace();
            return false;
        }

    }

    public void onChatMessage(QBPrivateChat qbPrivateChat, final QBChatMessage qbChatMessage) {
        EventBus.getDefault().post(new UpdateChatEvent());
        if (Util.IM_ALERT) {
            new AlertDialog.Builder(BaseActivity.this)
                    .setTitle(R.string.new_chat_message)
                    .setMessage(qbChatMessage.getBody())
                    .setPositiveButton("Open chat", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(BaseActivity.this, ChatActivity.class);
                            intent.putExtra(ChatActivity.DIALOG_ID, qbChatMessage.getDialogId());
                            intent.putExtra(ChatActivity.RECIPIENT_ID, qbChatMessage.getSenderId());
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    public void onGroupChatMessage(QBGroupChat qbGroupChat, final QBChatMessage qbChatMessage) {
        EventBus.getDefault().post(new UpdateChatEvent());
        if (Util.IM_ALERT) {
            new AlertDialog.Builder(BaseActivity.this)
                    .setTitle(R.string.new_chat_message)
                    .setMessage(qbChatMessage.getBody())
                    .setPositiveButton("Open chat", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(BaseActivity.this, ChatGroupActivity.class);
                            String dialogId;
                            if (qbChatMessage.getDialogId() != null && !qbChatMessage.getDialogId().equals("null")) {
                                dialogId = qbChatMessage.getDialogId();
                            } else {
                                dialogId = qbChatMessage.getProperty("_id").toString();
                            }
                            intent.putExtra(ChatActivity.DIALOG_ID, dialogId);
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RECREATE_SESSION) {
            if (resultCode == RESULT_OK) {
                try {
                    isReopened = false;
                    loginToChat();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                finish();
            }
        }
    }

    public void hideSoftKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnectedOrConnecting()) {
            return true;
        }

        return false;
    }

}
