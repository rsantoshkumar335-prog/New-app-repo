package com.santoshdev.notnote;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AiApiManager {

    private static final int GEMINI_MAX_RETRIES = 8;
    private static final long GEMINI_INITIAL_BACKOFF_MS = 1000L;
    
    private static final int CF_PRIMARY_MAX_TOKENS = 2048;
    private static final int CF_FALLBACK_MAX_TOKENS = 1024;
    private static final int CF_MAX_CONTINUATIONS = 3;
    private static final int CF_MAX_HISTORY_MESSAGES = 12;

    public static class ChatMessage {
        public static final int TYPE_USER = 0;
        public static final int TYPE_AI = 1;
        public String message;
        public int type;
        public boolean isThinking;

        public ChatMessage(String message, int type) {
            this.message = message;
            this.type = type;
            this.isThinking = false;
        }
    }
            private static String getEditorSystemInstruction() {
        return "You are an intelligent, natural AI assistant integrated directly into a Note-taking app. Keep answers concise.\n" +
               "Your goal is to be helpful and write naturally. DO NOT act like a rigid robot. The user's current note content is provided in HTML format.\n\n" +
               "CRITICAL OUTPUT STRUCTURE:\n" +
               "You MUST ALWAYS structure your output exactly like this:\n" +
               "[TITLE] <Write a short title ONLY if requested or writing a completely new note. Otherwise output NO_CHANGE>\n" +
               "[CONTENT] <Your main response here>\n\n" +
               "RULES FOR [CONTENT]:\n" +
               "1. NO REPETITION: If the user asks you to summarize, translate, explain, or ADD a new task/line, ONLY provide the new content. NEVER rewrite their original text.\n" +
               "2. TEXT REPLACEMENT: ONLY if the user explicitly asks to EDIT, REWRITE, or FIX GRAMMAR, start your [CONTENT] exactly with [REPLACE_CURRENT].\n" +
               "3. CHECKBOXES FOR TASKS: Whenever the user asks for a Schedule, Routine, To-Do list, or Task list, YOU MUST USE '☐ ' (empty checkbox symbol) at the start of each line instead of standard bullets. Example:\n" +
               "☐ 8:45 AM - College Leave\n" +
               "4. LANGUAGE & SCRIPT MATCHING (CRUCIAL): Reply in the EXACT same language and script the user uses. If the user writes in Hinglish (Hindi in English letters), you MUST reply in Hinglish. ONLY use native regional scripts (like Odia ଆ,ବ or Hindi अ,ब) IF the user explicitly asks for it or types in it first.\n" +
               "5. FORMATTING: Use standard markdown (**bold**, *italic*, # Heading). DO NOT literally type the characters '\\n' in your response; just use normal empty lines (hit Enter key) to separate paragraphs.\n" +
               "6. HIGHLIGHTING: Use `**text**` to make highly important keywords bold (even when writing newly requested tasks or lines). DO NOT use ==text==.\n" +
               "7. MATH, CALCULATIONS & CHEMISTRY: For any bills, lists with prices, or math, YOU MUST carefully calculate step-by-step internally and verify the exact total before writing it to ensure 100% accuracy. NEVER use inline LaTeX (like $H_2$). Write plain text (e.g., H2O). ONLY use block equations `$$ \\ce{...} $$` on a NEW LINE if requested.";
    }


    public static String callGeminiForEditor(String apiKey, String selectedModel, String noteContext, String userPrompt) throws Exception {
        if (selectedModel == null || selectedModel.trim().isEmpty()) {
            throw new IllegalStateException("Gemini model is not configured.");
        }

        String cleanModel = selectedModel.replace("models/", "");
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + cleanModel + ":generateContent";

        URL url = new URL(endpoint);
        JSONObject requestBody = new JSONObject();

        // Setup System Instruction
        JSONObject systemInstruction = new JSONObject();
        JSONArray sysParts = new JSONArray();
        JSONObject sysPart = new JSONObject();
        sysPart.put("text", getEditorSystemInstruction());
        sysParts.put(sysPart);
        systemInstruction.put("parts", sysParts);
        requestBody.put("systemInstruction", systemInstruction);

        // Setup Content (Context + User Prompt)
        JSONArray contents = new JSONArray();
        JSONObject contentObj = new JSONObject();
        contentObj.put("role", "user");
        JSONArray parts = new JSONArray();
        JSONObject partObj = new JSONObject();
        
        String finalPrompt = "CURRENT NOTE CONTEXT:\n\"" + noteContext + "\"\n\nUSER REQUEST: " + userPrompt;
        partObj.put("text", finalPrompt);
        parts.put(partObj);
        contentObj.put("parts", parts);
        contents.put(contentObj);
        
        requestBody.put("contents", contents);

        byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
        int lastResponseCode = -1;
        String lastErrorText = "";

        for (int attempt = 0; attempt <= GEMINI_MAX_RETRIES; attempt++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Gemini request cancelled");

            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(90000);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("x-goog-api-key", apiKey.trim());
                connection.setDoOutput(true);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(input);
                    os.flush();
                }

                int responseCode = connection.getResponseCode();
                String responseText = readResponse(connection, responseCode);
                lastResponseCode = responseCode;
                lastErrorText = responseText;

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    JSONObject jsonResponse = new JSONObject(responseText);
                    JSONArray candidates = jsonResponse.optJSONArray("candidates");
                    if (candidates != null && candidates.length() > 0) {
                        JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
                        if (content != null) {
                            JSONArray responseParts = content.optJSONArray("parts");
                            if (responseParts != null && responseParts.length() > 0) {
                                return responseParts.getJSONObject(0).optString("text", "").trim();
                            }
                        }
                    }
                }

                if (isRetryableGeminiResponse(responseCode)) {
                    waitBeforeGeminiRetry(attempt);
                    continue;
                }
                return getGeminiPermanentErrorMessage(responseCode, responseText);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw new GeminiUnavailableException("Gemini request failed (HTTP " + lastResponseCode + ")");
    }

    public static String callCloudflareForEditor(String apiKey, String accountId, String selectedModel, String noteContext, String userPrompt) throws Exception {
        if (accountId == null || accountId.trim().isEmpty()) return "Cloudflare Account ID is missing.";
        
        String endpoint = "https://api.cloudflare.com/client/v4/accounts/" + accountId.trim() + "/ai/run/" + selectedModel.trim();
        HttpURLConnection connection = null;
        
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(90000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            connection.setDoOutput(true);

            JSONObject requestBody = new JSONObject();
            JSONArray messagesArr = new JSONArray();

            // System Instruction
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", getEditorSystemInstruction());
            messagesArr.put(sysMsg);

            // User Prompt with Context
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            String finalPrompt = "CURRENT NOTE CONTEXT:\n\"" + noteContext + "\"\n\nUSER REQUEST: " + userPrompt;
            userMsg.put("content", finalPrompt);
            messagesArr.put(userMsg);

            requestBody.put("messages", messagesArr);
            requestBody.put("max_tokens", CF_PRIMARY_MAX_TOKENS);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            String responseText = readResponse(connection, responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                JSONObject jsonResponse = new JSONObject(responseText);
                if (jsonResponse.optBoolean("success", false)) {
                    JSONObject result = jsonResponse.optJSONObject("result");
                    if (result != null) return result.optString("response", "").trim();
                }
                return "Cloudflare error: " + extractCloudflareError(jsonResponse);
            }
            
            return "Cloudflare error (HTTP " + responseCode + "): " + extractCloudflareErrorSafely(responseText);

        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean isRetryableGeminiResponse(int responseCode) {
        return responseCode == 429 || responseCode == 500 || responseCode == 502 || responseCode == 503 || responseCode == 504;
    }

    private static void waitBeforeGeminiRetry(int attempt) throws InterruptedException {
        long delay = GEMINI_INITIAL_BACKOFF_MS * (1L << Math.min(attempt, 3));
        Thread.sleep(delay);
    }

    private static String getGeminiPermanentErrorMessage(int responseCode, String responseText) {
        if (responseCode == 400) return "Gemini rejected the request. Please check the selected model.";
        if (responseCode == 401 || responseCode == 403) return "Gemini API key is invalid or lacks access.";
        if (responseCode == 404) return "The selected Gemini model is not available.";
        if (responseCode == 408) return "Gemini request timed out.";
        return "Gemini returned an error (HTTP " + responseCode + ").";
    }

    private static String readResponse(HttpURLConnection connection, int responseCode) {
        try {
            InputStream stream = responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) return "";
            try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) builder.append(line);
                return builder.toString();
            }
        } catch (Exception e) {
            return "";
        }
    }

    private static String extractCloudflareError(JSONObject json) {
        try {
            JSONArray errors = json.optJSONArray("errors");
            if (errors != null && errors.length() > 0) {
                JSONObject error = errors.optJSONObject(0);
                if (error != null) {
                    String message = error.optString("message", "");
                    if (!message.isEmpty()) return message;
                }
            }
            return json.optString("message", "Unknown error");
        } catch (Exception e) {
            return "Unknown Cloudflare error";
        }
    }

    private static String extractCloudflareErrorSafely(String responseText) {
        if (responseText == null || responseText.trim().isEmpty()) return "Unknown server error";
        try {
            return extractCloudflareError(new JSONObject(responseText));
        } catch (Exception ignored) {
            String cleaned = responseText.replace('\n', ' ').trim();
            return cleaned.length() > 300 ? cleaned.substring(0, 300) + "..." : cleaned;
        }
    }

    public static class GeminiUnavailableException extends Exception {
        public GeminiUnavailableException(String message) {
            super(message);
        }
    }
}
