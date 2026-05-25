// client/service/MediaUploadService.java (sửa lại)
package client.service;

import java.io.File;

public class MediaUploadService {

  private final CloudMediaApiClient apiClient;

  // Có thể nhận baseUrl từ config (ví dụ "http://localhost:8080")
  public MediaUploadService(String serverBaseUrl) {
    this.apiClient = new CloudMediaApiClient(serverBaseUrl);
  }

  public String uploadImage(File file) {
    return apiClient.upload(file);
  }
}