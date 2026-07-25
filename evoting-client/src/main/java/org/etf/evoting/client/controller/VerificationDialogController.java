package org.etf.evoting.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class VerificationDialogController {

    @FXML private TextField receiptCodeField;
    @FXML private Label statusLabel;

    @FXML
    private void handleVerify() {
        String receiptCode = receiptCodeField.getText().trim();

        if (receiptCode.isEmpty()) {
            statusLabel.setTextFill(Color.RED);
            statusLabel.setText("Molimo unesite Receipt Code.");
            return;
        }

        try {
            URL url = new URL("http://localhost:8080/api/voting/verify/" + receiptCode);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            if (code == 200) {
                try (InputStream is = conn.getInputStream()) {
                    String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                    statusLabel.setTextFill(Color.GREEN);
                    statusLabel.setText("POTVRĐENO!\n" + extractJsonValue(response, "message"));
                }
            } else {
                try (InputStream es = conn.getErrorStream()) {
                    String errorMsg = new String(es.readAllBytes(), StandardCharsets.UTF_8);
                    statusLabel.setTextFill(Color.RED);
                    statusLabel.setText("GREŠKA: " + extractJsonValue(errorMsg, "message"));
                }
            }
        } catch (Exception e) {
            statusLabel.setTextFill(Color.RED);
            statusLabel.setText("Greška pri komunikaciji sa serverom: " + e.getMessage());
        }
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int start = json.indexOf(searchKey);
        if (start == -1) return json;
        start += searchKey.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}