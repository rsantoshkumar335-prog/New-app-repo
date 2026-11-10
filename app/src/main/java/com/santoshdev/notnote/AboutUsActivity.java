package com.santoshdev.notnote;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class AboutUsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_us);

        ImageView btnBack = findViewById(R.id.btn_back);
        TextView tvVersion = findViewById(R.id.tv_version);
        LinearLayout btnSendFeedback = findViewById(R.id.btn_send_feedback);
        LinearLayout btnPrivacyPolicy = findViewById(R.id.btn_privacy_policy);

        // 1. Back Button Logic
        btnBack.setOnClickListener(v -> finish());

        // 2. Dynamic Version Logic (Dots removed)
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            tvVersion.setText("V" + version);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            tvVersion.setText("V1.0"); // Fallback text
        }

        // 3. Send Feedback Logic
        btnSendFeedback.setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:skrana05789@gmail.com"));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "NotNote App Feedback");
            
            try {
                startActivity(Intent.createChooser(emailIntent, "Send Feedback via..."));
            } catch (Exception e) {
                Toast.makeText(this, "No email client found!", Toast.LENGTH_SHORT).show();
            }
        });

        // 4. Privacy Policy Logic
        btnPrivacyPolicy.setOnClickListener(v -> {
            String privacyUrl = "https://rsantoshkumar335-prog.github.io/NotNote_privacy/"; 
            
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl));
                startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Browser not found to open link!", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
