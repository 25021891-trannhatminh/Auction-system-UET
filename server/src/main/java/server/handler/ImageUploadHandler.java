package server.handler;

import server.service.CloudinaryUploader;
import java.util.Base64;
import java.util.List;

/**
 * Xử lý upload ảnh từ client qua giao thức socket.
 * Command: UPLOAD_IMAGE <fileName> <base64Data>
 * Response: UPLOAD_IMAGE_SUCCESS <url> hoặc UPLOAD_IMAGE_FAIL <error>
 */
public class ImageUploadHandler {
  private final CloudinaryUploader uploader;

  public ImageUploadHandler() {
    this.uploader = new CloudinaryUploader();
  }

  /**
   * Xử lý message upload ảnh.
   * @param fullMessage toàn bộ message từ client (đã trim)
   * @return response string để gửi về client
   */
  public String handleUpload(String fullMessage) {
    // Format: UPLOAD_IMAGE fileName base64Data
    // Dùng split với giới hạn 3 phần (command, fileName, base64Data)
    String[] parts = fullMessage.split(" ", 3);
    if (parts.length < 3) {
      return "UPLOAD_IMAGE_FAIL MISSING_FILE_NAME_OR_DATA";
    }
    String fileName = parts[1];
    String base64Data = parts[2];

    // Kiểm tra sơ bộ
    if (fileName == null || fileName.isBlank()) {
      return "UPLOAD_IMAGE_FAIL INVALID_FILE_NAME";
    }
    if (base64Data == null || base64Data.isBlank()) {
      return "UPLOAD_IMAGE_FAIL EMPTY_DATA";
    }

    try {
      String url = uploader.uploadBase64(fileName, base64Data);
      // Escape URL để tránh lỗi protocol
      return "UPLOAD_IMAGE_SUCCESS " + encodeField(url);
    } catch (Exception e) {
      e.printStackTrace();
      return "UPLOAD_IMAGE_FAIL " + encodeField(e.getMessage());
    }
  }

  private String encodeField(String value) {
    if (value == null) return "";
    return value.replace("\\", "\\\\")
        .replace("|", "\\p")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }
}