package com.santoshdev.notnote;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {

    private SharedPreferences aiPrefs;
    private static final String PREF_NAME_AI = "NotNoteAiPrefs";
    private static final String KEY_PROVIDER = "selected_provider";
    private static final String KEY_GEMINI_API = "encrypted_api_key_gemini";
    private static final String KEY_GEMINI_MODEL = "selected_model_gemini";
    private static final String KEY_CF_API = "encrypted_api_key_cf";
    private static final String KEY_CF_ACCOUNT_ID = "cf_account_id";
    private static final String KEY_CF_MODEL = "selected_model_cf";

    private List<String> currentModelList = new ArrayList<>();
    private String selectedModelForSave = "";
    private boolean isDestroyedActivity = false;

    private static final String[] GEMINI_SUGGESTED_MODELS = {
            "gemini-3.5-flash-lite",
            "gemini-3.8-flash"
    };

    private static final String[] CLOUDFLARE_SUGGESTED_MODELS = {
            "@cf/meta/llama-3.2-3b-instruct",
            "@cf/meta/llama-4-scout-17b-16e-instruct"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Status bar ko white aur icons ko dark karna
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.WHITE);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        aiPrefs = getSharedPreferences(PREF_NAME_AI, MODE_PRIVATE);

        // Views ko find karna
        ImageView btnBack = findViewById(R.id.btn_back_settings);
        CardView btnAboutApp = findViewById(R.id.btn_about_app);
        LinearLayout btnOpenConfig = findViewById(R.id.btn_open_config);
        LinearLayout btnPrivacyVault = findViewById(R.id.btn_privacy_vault); // Naya privacy vault button

        // Click Listeners
        btnBack.setOnClickListener(v -> finish());

        btnAboutApp.setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, AboutUsActivity.class));
        });

        // Add API Key Dialog open karega
        btnOpenConfig.setOnClickListener(v -> openApiConfigurationDialog());

        // Privacy Vault Activity open karega
        btnPrivacyVault.setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, PrivateLockActivity.class));
        });
    }

    private void openApiConfigurationDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_api);
        
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialogWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // Center thoda adjust karne ke liye agar zaroorat ho, par dialog me naturally centre hota hai.
        }

        Switch switchGemini = dialog.findViewById(R.id.switch_gemini);
        Switch switchCloudflare = dialog.findViewById(R.id.switch_cloudflare);
        
        EditText etApiKey = dialog.findViewById(R.id.et_api_key);
        CardView layoutCfAccountId = dialog.findViewById(R.id.layout_cf_account_id);
        EditText etCfAccountId = dialog.findViewById(R.id.et_cf_account_id);
        
        LinearLayout layoutModelSelection = dialog.findViewById(R.id.layout_model_selection);
        LinearLayout layoutModelContainer = dialog.findViewById(R.id.layout_model_container); // Naya parent jisme list expand hogi
        LinearLayout btnSelectModel = dialog.findViewById(R.id.btn_select_model);
        TextView tvSelectedModelName = dialog.findViewById(R.id.tv_selected_model_name);
        ImageView ivDropdownArrow = dialog.findViewById(R.id.iv_dropdown_arrow);
        
        TextView btnCancel = dialog.findViewById(R.id.btn_cancel_api);
        TextView btnVerify = dialog.findViewById(R.id.btn_verify_api);

        if (switchGemini == null || switchCloudflare == null || etApiKey == null
                || layoutCfAccountId == null || etCfAccountId == null || layoutModelSelection == null
                || btnSelectModel == null || tvSelectedModelName == null || ivDropdownArrow == null || btnCancel == null || btnVerify == null) {
            Toast.makeText(this, "AI configuration layout is incomplete", Toast.LENGTH_LONG).show();
            dialog.dismiss();
            return;
        }

        final boolean[] isVerified = {false};
        final String[] validApiKey = {""};
        final String[] validCfAccountId = {""};

        String savedProvider = aiPrefs.getString(KEY_PROVIDER, "gemini");
        if (savedProvider.equals("cloudflare")) {
            switchCloudflare.setChecked(true);
            switchGemini.setChecked(false);
            layoutCfAccountId.setVisibility(View.VISIBLE);
            etApiKey.setHint("Enter Cloudflare API Key");
            
            String cfKey = aiPrefs.getString(KEY_CF_API, null);
            if (cfKey != null) etApiKey.setText("********");
            etCfAccountId.setText(aiPrefs.getString(KEY_CF_ACCOUNT_ID, ""));
        } else {
            switchGemini.setChecked(true);
            switchCloudflare.setChecked(false);
            layoutCfAccountId.setVisibility(View.GONE);
            etApiKey.setHint("Enter Gemini API Key");
            
            String gemKey = aiPrefs.getString(KEY_GEMINI_API, null);
            if (gemKey != null) etApiKey.setText("********");
        }

        switchGemini.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                isVerified[0] = false; 
                switchCloudflare.setChecked(false);
                layoutCfAccountId.setVisibility(View.GONE);
                etApiKey.setHint("Enter Gemini API Key");
                etApiKey.setText("");
                String gemKey = aiPrefs.getString(KEY_GEMINI_API, null);
                if (gemKey != null) etApiKey.setText("********");
                layoutModelSelection.setVisibility(View.GONE);
                btnVerify.setText("Verify Key");
            } else if (!switchCloudflare.isChecked()) {
                switchGemini.setChecked(true); 
            }
        });

        switchCloudflare.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                isVerified[0] = false; 
                switchGemini.setChecked(false);
                layoutCfAccountId.setVisibility(View.VISIBLE);
                etApiKey.setHint("Enter Cloudflare API Key");
                etApiKey.setText(""); 
                String cfKey = aiPrefs.getString(KEY_CF_API, null);
                if (cfKey != null) etApiKey.setText("********");
                etCfAccountId.setText(aiPrefs.getString(KEY_CF_ACCOUNT_ID, ""));
                layoutModelSelection.setVisibility(View.GONE);
                btnVerify.setText("Verify Key");
            } else if (!switchGemini.isChecked()) {
                switchCloudflare.setChecked(true); 
            }
        });
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnVerify.setOnClickListener(v -> {
            boolean isGemini = switchGemini.isChecked();
            
            if (!isVerified[0]) {
                String inputKey = etApiKey.getText().toString().trim();
                
                if(inputKey.startsWith("Bearer ")) {
                    inputKey = inputKey.replace("Bearer ", "").trim();
                }
                
                if (inputKey.equals("********")) {
                    inputKey = isGemini ? SecurityHelper.decrypt(aiPrefs.getString(KEY_GEMINI_API, "")) 
                                        : SecurityHelper.decrypt(aiPrefs.getString(KEY_CF_API, ""));
                }
                
                if (inputKey == null || inputKey.isEmpty()) {
                    Toast.makeText(this, "Please enter an API key", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                if (!isGemini) {
                    String accId = etCfAccountId.getText().toString().trim();
                    if (accId.isEmpty()) {
                        Toast.makeText(this, "Please enter Account ID", Toast.LENGTH_LONG).show();
                        return;
                    }
                    validCfAccountId[0] = accId;
                    aiPrefs.edit().putString(KEY_CF_ACCOUNT_ID, accId).apply();
                }
                
                validApiKey[0] = inputKey;
                
                if (isGemini) {
                    verifyGeminiKey(inputKey, layoutModelSelection, tvSelectedModelName, layoutModelContainer, btnSelectModel, ivDropdownArrow, btnVerify, isVerified);
                } else {
                    verifyCloudflareKey(inputKey, validCfAccountId[0], layoutModelSelection, tvSelectedModelName, layoutModelContainer, btnSelectModel, ivDropdownArrow, btnVerify, isVerified);
                }
                
            } else {
                String provider = isGemini ? "gemini" : "cloudflare";
                aiPrefs.edit().putString(KEY_PROVIDER, provider).apply();
                
                String encryptedKey = SecurityHelper.encrypt(validApiKey[0]);
                if (isGemini) {
                    aiPrefs.edit().putString(KEY_GEMINI_API, encryptedKey).apply();
                    aiPrefs.edit().putString(KEY_GEMINI_MODEL, selectedModelForSave).apply();
                } else {
                    aiPrefs.edit().putString(KEY_CF_API, encryptedKey).apply();
                    String accountIdToSave = validCfAccountId[0] == null || validCfAccountId[0].trim().isEmpty()
                            ? aiPrefs.getString(KEY_CF_ACCOUNT_ID, "")
                            : validCfAccountId[0].trim();
                    aiPrefs.edit().putString(KEY_CF_ACCOUNT_ID, accountIdToSave).apply();
                    aiPrefs.edit().putString(KEY_CF_MODEL, selectedModelForSave).apply();
                }
                
                Toast.makeText(this, "AI Connected Successfully", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        
        dialog.show();
    }

    private void verifyGeminiKey(String apiKey, LinearLayout layoutModelSelection, TextView tvSelectedModel, LinearLayout layoutModelContainer, LinearLayout btnSelectModel, ImageView ivDropdownArrow, TextView btnVerify, boolean[] isVerified) {
        btnVerify.setText("Verifying...");
        btnVerify.setEnabled(false);

        new Thread(() -> {
            final int[] status = {1}; 
            currentModelList.clear();
            HttpURLConnection connection = null;

            try {
                String endpoint = "https://generativelanguage.googleapis.com/v1beta/models";
                URL url = new URL(endpoint);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("x-goog-api-key", apiKey.trim());
                
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) responseBuilder.append(line.trim());
                    
                    JSONObject jsonResponse = new JSONObject(responseBuilder.toString());
                    JSONArray modelsArray = jsonResponse.getJSONArray("models");
                    
                    for (int i = 0; i < modelsArray.length(); i++) {
                        JSONObject modelObj = modelsArray.getJSONObject(i);
                        if (modelObj.has("supportedGenerationMethods")) {
                            JSONArray methods = modelObj.getJSONArray("supportedGenerationMethods");
                            for (int j = 0; j < methods.length(); j++) {
                                if (methods.getString(j).equals("generateContent")) {
                                    String name = modelObj.getString("name").replace("models/", "");
                                    currentModelList.add(name);
                                    break;
                                }
                            }
                        }
                    }
                    if (!currentModelList.isEmpty() && hasPreferredModel("gemini")) status[0] = 0; 
                    else status[0] = 2; 
                } else {
                    status[0] = 1; 
                }
            } catch (Exception e) {
                Log.e("SettingsActivity", "Gemini Verification Error", e);
                status[0] = 1; 
            } finally {
                if (connection != null) connection.disconnect();
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                if (isDestroyedActivity) return;
                if (status[0] == 0) {
                    String savedGeminiModel = aiPrefs.getString(KEY_GEMINI_MODEL, "").trim();
                    selectedModelForSave = chooseInitialModel("gemini", savedGeminiModel);
                    tvSelectedModel.setText(getCleanModelName(selectedModelForSave));
                    btnSelectModel.setOnClickListener(v -> showModelSelectionOptions(tvSelectedModel, ivDropdownArrow, "gemini", layoutModelContainer));
                    layoutModelSelection.setVisibility(View.VISIBLE);
                    btnVerify.setText("Save & Connect");
                    btnVerify.setEnabled(true);
                    isVerified[0] = true;
                } else if (status[0] == 2) {
                    btnVerify.setText("Verify Key");
                    btnVerify.setEnabled(true);
                    Toast.makeText(SettingsActivity.this, "Key verified, but requested models not available.", Toast.LENGTH_LONG).show();
                } else {
                    btnVerify.setText("Verify Key");
                    btnVerify.setEnabled(true);
                    Toast.makeText(SettingsActivity.this, "Verification failed. Check API key.", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void verifyCloudflareKey(String apiKey, String accountId, LinearLayout layoutModelSelection, TextView tvSelectedModel, LinearLayout layoutModelContainer, LinearLayout btnSelectModel, ImageView ivDropdownArrow, TextView btnVerify, boolean[] isVerified) {
        btnVerify.setText("Verifying...");
        btnVerify.setEnabled(false);

        new Thread(() -> {
            final int[] status = {1}; 
            boolean hasOkResponse = false;
            currentModelList.clear();

            try {
                int page = 1;
                boolean hasMore = true;
                
                while (hasMore && page <= 5) {
                    HttpURLConnection connection = null;
                    try {
                        String endpoint = "https://api.cloudflare.com/client/v4/accounts/" + accountId.trim() + "/ai/models/search?per_page=100&page=" + page;
                        URL url = new URL(endpoint);
                        connection = (HttpURLConnection) url.openConnection();
                        connection.setConnectTimeout(15000);
                        connection.setReadTimeout(15000);
                        connection.setRequestMethod("GET");
                        connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
                        
                        int responseCode = connection.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            hasOkResponse = true;
                            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                            StringBuilder responseBuilder = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) responseBuilder.append(line.trim());
                            
                            JSONObject jsonResponse = new JSONObject(responseBuilder.toString());
                            if (jsonResponse.optBoolean("success", false)) {
                                JSONArray resultArray = jsonResponse.optJSONArray("result");
                                if (resultArray != null && resultArray.length() > 0) {
                                    for (int i = 0; i < resultArray.length(); i++) {
                                        JSONObject modelObj = resultArray.getJSONObject(i);
                                        String modelName = modelObj.optString("name", modelObj.optString("id", ""));
                                        if (!modelName.isEmpty()) currentModelList.add(modelName);
                                    }
                                    if (resultArray.length() < 100) hasMore = false;
                                } else {
                                    hasMore = false; 
                                }
                            } else {
                                hasMore = false; 
                            }
                        } else {
                            if (page == 1) status[0] = 1;
                            hasMore = false; 
                        }
                    } finally {
                        if (connection != null) connection.disconnect();
                    }
                    page++;
                }

                if (hasOkResponse) {
                    if (!currentModelList.isEmpty() && hasPreferredModel("cloudflare")) status[0] = 0;
                    else status[0] = 2;
                }

            } catch (Exception e) {
                Log.e("SettingsActivity", "Cloudflare Verification Error", e);
                status[0] = 1;
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                if (isDestroyedActivity) return;
                if (status[0] == 0) {
                    String savedCloudflareModel = aiPrefs.getString(KEY_CF_MODEL, "").trim();
                    selectedModelForSave = chooseInitialModel("cloudflare", savedCloudflareModel);
                    tvSelectedModel.setText(getCleanModelName(selectedModelForSave));
                    btnSelectModel.setOnClickListener(v -> showModelSelectionOptions(tvSelectedModel, ivDropdownArrow, "cloudflare", layoutModelContainer));
                    layoutModelSelection.setVisibility(View.VISIBLE);
                    btnVerify.setText("Save & Connect");
                    btnVerify.setEnabled(true);
                    isVerified[0] = true;
                } else if (status[0] == 2) {
                    btnVerify.setText("Verify Key");
                    btnVerify.setEnabled(true);
                    Toast.makeText(SettingsActivity.this, "Key verified, but requested models not found.", Toast.LENGTH_LONG).show();
                } else {
                    btnVerify.setText("Verify Key");
                    btnVerify.setEnabled(true);
                    Toast.makeText(SettingsActivity.this, "Verification failed. Check API key & Account ID.", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void showModelSelectionOptions(TextView tvSelectedModel, ImageView ivDropdownArrow, String provider, LinearLayout modelContainer) {
        List<String> availableModels = getPreferredAvailableModels(provider);

        if (availableModels.isEmpty()) {
            Toast.makeText(this, "No supported model is available.", Toast.LENGTH_SHORT).show();
            return;
        }

        View existingOptions = modelContainer.findViewWithTag("model_options");
        if (existingOptions != null) {
            modelContainer.removeView(existingOptions);
            ivDropdownArrow.animate().rotation(0).setDuration(200).start();
            return;
        }

        ivDropdownArrow.animate().rotation(180).setDuration(200).start();
        final String currentSelected = selectedModelForSave;
        final float density = getResources().getDisplayMetrics().density;

        LinearLayout options = new LinearLayout(this);
        options.setTag("model_options");
        options.setOrientation(LinearLayout.VERTICAL);
        options.setBackgroundColor(Color.TRANSPARENT);
        options.setPadding(0, 0, 0, dp(8));

        for (int i = 0; i < availableModels.size(); i++) {
            final String model = availableModels.get(i);
            
            if (i > 0) {
                View divider = new View(this);
                divider.setBackgroundColor(Color.parseColor("#D1D1D6"));
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                dividerParams.setMargins(dp(16), 0, dp(16), 0);
                options.addView(divider, dividerParams);
            }

            LinearLayout optionRow = new LinearLayout(this);
            optionRow.setOrientation(LinearLayout.HORIZONTAL);
            optionRow.setGravity(Gravity.CENTER_VERTICAL);
            optionRow.setPadding(dp(16), 0, dp(16), 0);
            optionRow.setMinimumHeight(dp(48));
            optionRow.setClickable(true);
            optionRow.setFocusable(true);

            TextView modelText = new TextView(this);
            modelText.setText(getCleanModelName(model));
            modelText.setTextColor(Color.BLACK);
            modelText.setTextSize(15);
            modelText.setTypeface(null, android.graphics.Typeface.BOLD);
            modelText.setSingleLine(true);
            modelText.setEllipsize(android.text.TextUtils.TruncateAt.END);

            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            optionRow.addView(modelText, textParams);

            if (modelsEquivalent(model, currentSelected)) {
                TextView selectedDot = new TextView(this);
                selectedDot.setText("●");
                selectedDot.setTextColor(Color.parseColor("#424242"));
                selectedDot.setTextSize(15);
                selectedDot.setGravity(Gravity.CENTER);
                optionRow.addView(selectedDot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            optionRow.setOnClickListener(v -> {
                selectedModelForSave = model;
                tvSelectedModel.setText(getCleanModelName(model));
                modelContainer.removeView(options);
                ivDropdownArrow.animate().rotation(0).setDuration(200).start();
            });

            options.addView(optionRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        }

        modelContainer.addView(options, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private List<String> getPreferredAvailableModels(String provider) {
        List<String> result = new ArrayList<>();
        String[] suggestedModels = "cloudflare".equals(provider) ? CLOUDFLARE_SUGGESTED_MODELS : GEMINI_SUGGESTED_MODELS;

        for (String wanted : suggestedModels) {
            boolean foundInApi = false;
            for (String available : currentModelList) {
                if (modelsEquivalent(wanted, available)) {
                    foundInApi = true;
                    if ("gemini".equals(provider)) {
                        if (!result.contains(available)) result.add(available);
                    } else {
                        if (!result.contains(wanted)) result.add(wanted);
                    }
                    break;
                }
            }
            if (!foundInApi && !result.contains(wanted)) result.add(wanted);
        }
        return result;
    }

    private boolean hasPreferredModel(String provider) {
        return !getPreferredAvailableModels(provider).isEmpty();
    }

    private String chooseInitialModel(String provider, String savedModel) {
        List<String> preferredAvailable = getPreferredAvailableModels(provider);
        if (preferredAvailable.isEmpty()) return "";
        if (savedModel != null && !savedModel.trim().isEmpty()) {
            for (String available : preferredAvailable) {
                if (modelsEquivalent(savedModel, available)) return available;
            }
        }
        return preferredAvailable.get(0);
    }

    private boolean modelsEquivalent(String first, String second) {
        if (first == null || second == null) return false;
        return normalizeModelId(first).equals(normalizeModelId(second));
    }

    private String normalizeModelId(String model) {
        if (model == null) return "";
        String value = model.trim().toLowerCase();
        int lastSlash = value.lastIndexOf('/');
        if (lastSlash != -1) value = value.substring(lastSlash + 1);
        return value;
    }

    private String getCleanModelName(String rawName) {
        if (rawName == null) return "";
        int lastSlashIndex = rawName.lastIndexOf('/');
        if (lastSlashIndex != -1 && lastSlashIndex < rawName.length() - 1) {
            return rawName.substring(lastSlashIndex + 1);
        }
        return rawName;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyedActivity = true;
    }
}
