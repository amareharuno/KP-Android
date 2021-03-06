package saberapplications.pawpads.ui.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.quickblox.chat.model.QBDialog;
import com.quickblox.core.QBEntityCallback;
import com.quickblox.core.exception.QBResponseException;
import com.quickblox.users.QBUsers;
import com.quickblox.users.model.QBUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

import saberapplications.pawpads.R;
import saberapplications.pawpads.util.AvatarLoaderHelper;

public class DialogsAdapter extends BaseAdapter {

    private final int size;
    private ArrayList<QBDialog> dialogs;
    private Context context;
    private int currentUserId;

    public DialogsAdapter(ArrayList<QBDialog> dialogs, int currentUserId, Context context) {
        this.dialogs = dialogs;
        this.context = context;
        this.currentUserId = currentUserId;
        float d = context.getResources().getDisplayMetrics().density;
        size = Math.round(60 * d);
    }

    @Override
    public int getCount() {
        return dialogs.size();
    }

    @Override
    public QBDialog getItem(int position) {
        return dialogs.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            view = inflater.inflate(R.layout.row_dialog, parent, false);
        } else {
            view = convertView;
        }
        QBDialog dialog = getItem(position);

        final ImageView avatar = (ImageView) view.findViewById(R.id.dialog_avatar);
        TextView username = (TextView) view.findViewById(R.id.dialogs_username);
        TextView lastMessage = (TextView) view.findViewById(R.id.dialog_last_message);
        TextView lastDate = (TextView) view.findViewById(R.id.dialog_date_last_message);
        String lastDateFormat = new SimpleDateFormat("dd MMM yyyy ", Locale.getDefault()).format(dialog.getUpdatedAt());
        lastDate.setText(lastDateFormat);
        lastMessage.setText(dialog.getLastMessage());
        username.setText(dialog.getName());
        int userId = 0;
        for (int uid : dialog.getOccupants()) {
            if (uid != currentUserId) {
                userId = uid;
            }
        }

        QBUsers.getUser(userId, new QBEntityCallback<QBUser>() {
            @Override
            public void onSuccess(QBUser qbUser, Bundle bundle) {
                if (qbUser.getFileId() != null) {
                    AvatarLoaderHelper.loadImage(qbUser.getFileId(), avatar, size, size);
                }
            }

            @Override
            public void onError(QBResponseException e) {
            }
        });

        if (dialog.getUnreadMessageCount() > 0) {
            view.setBackgroundColor(context.getResources().getColor(android.R.color.holo_blue_light));
        }
        return view;
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
    }
}
