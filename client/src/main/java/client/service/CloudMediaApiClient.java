package client.service;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudMediaApiClient {
  private static final Logger logger = LoggerFactory.getLogger(CloudMediaApiClient.class);

  // Cấu hình thẳng thông tin Cloudinary tại Client cho nhanh
  private final String CLOUD_NAME = "dp0ynsodr";
  private final String UPLOAD_PRESET = "my_preset"; // Tên upload preset (Chế độ Unsigned trên web Cloudinary)

  public CloudMediaApiClient() {}

  public String upload(File file) {
    if (file == null || !file.exists()) {
      logger.warn("upload() called with null or missing file");
      return null;
    }
    try {
      String url = "https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/image/upload";
      String boundary = "Boundary-" + UUID.randomUUID();

      // 1. Đọc byte của ảnh trực tiếp từ ổ đĩa
      byte[] fileBytes = Files.readAllBytes(file.toPath());

      // 2. Tạo body dạng Form-Data theo đúng chuẩn REST API của Cloudinary
      StringBuilder sb = new StringBuilder();
      sb.append("--").append(boundary).append("\r\n");
      sb.append("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n").append(UPLOAD_PRESET).append("\r\n");
      sb.append("--").append(boundary).append("\r\n");
      sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(file.getName()).append("\"\r\n");
      sb.append("Content-Type: ").append(detectMimeType(file)).append("\r\n\r\n");

      byte[] headerBytes = sb.toString().getBytes();
      byte[] footerBytes = ("\r\n--" + boundary + "--\r\n").getBytes();

      byte[] requestBody = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
      System.arraycopy(headerBytes, 0, requestBody, 0, headerBytes.length);
      System.arraycopy(fileBytes, 0, requestBody, headerBytes.length, fileBytes.length);
      System.arraycopy(footerBytes, 0, requestBody, headerBytes.length + fileBytes.length, footerBytes.length);

      // 3. Bắn HTTP POST thẳng lên mây (Không qua Server Socket)
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("Content-Type", "multipart/form-data; boundary=" + boundary)
          .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
          .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      String json = response.body();

      // 4. Trích xuất nhanh lấy chuỗi "secure_url" từ JSON trả về
      int index = json.indexOf("\"secure_url\":\"");
      if (index != -1) {
        int start = index + "\"secure_url\":\"".length();
        int end = json.indexOf("\"", start);
        String secureUrl = json.substring(start, end);

        logger.info("Uploaded to Cloudinary: {}", secureUrl);
        return secureUrl;
      }
      throw new RuntimeException("Lỗi upload: " + json);
    } catch (Exception e) {
      logger.error("Cloudinary upload failed for file={}", file.getName(), e);
      return null;
    }
  }
  private String detectMimeType(File file) {
    String name = file.getName().toLowerCase();
    if (name.endsWith(".png"))  return "image/png";
    if (name.endsWith(".webp")) return "image/webp";
    if (name.endsWith(".gif"))  return "image/gif";
    return "image/jpeg";
  }
}