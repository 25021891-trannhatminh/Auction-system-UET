package client.service;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CloudMediaApiClient {
  private final NetworkManager networkManager;
  private CompletableFuture<String> pendingUpload;

  public CloudMediaApiClient(String ignored) {
    this.networkManager = NetworkManager.getInstance();
    // Đăng ký handler để nhận phản hồi từ server
    networkManager.addMessageHandler(this::handleUploadResponse);
  }

  private void handleUploadResponse(String msg) {
    if (msg.startsWith("UPLOAD_IMAGE_SUCCESS ")) {
      String url = msg.substring("UPLOAD_IMAGE_SUCCESS ".length());
      if (pendingUpload != null) pendingUpload.complete(url);
    } else if (msg.startsWith("UPLOAD_IMAGE_FAIL ")) {
      String error = msg.substring("UPLOAD_IMAGE_FAIL ".length());
      if (pendingUpload != null) pendingUpload.completeExceptionally(new RuntimeException(error));
    }
  }

  public String upload(File file) {
    try {
      byte[] bytes = Files.readAllBytes(file.toPath());
      String base64 = Base64.getEncoder().encodeToString(bytes);
      String fileName = file.getName();
      pendingUpload = new CompletableFuture<>();
      networkManager.send("UPLOAD_IMAGE " + fileName + " " + base64);
      return pendingUpload.get(30, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new RuntimeException("Upload failed: " + e.getMessage(), e);
    }
  }
}