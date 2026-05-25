package server.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

@Service
public class CloudMediaManager {

  private final Cloudinary cloudinary;

  public CloudMediaManager(
      @Value("${cloudinary.cloud-name}") String cloudName,
      @Value("${cloudinary.api-key}") String apiKey,
      @Value("${cloudinary.api-secret}") String apiSecret) {
    this.cloudinary = new Cloudinary(ObjectUtils.asMap(
        "cloud_name", cloudName,
        "api_key", apiKey,
        "api_secret", apiSecret,
        "secure", true
    ));
  }

  /**
   * Upload MultipartFile lên Cloudinary, trả về secure_url.
   */
  public String uploadFile(MultipartFile file, String folder) {
    if (file.isEmpty()) {
      throw new IllegalArgumentException("File is empty");
    }
    try {
      File tempFile = convertMultiPartToFile(file);
      Map<?, ?> result = cloudinary.uploader().upload(tempFile,
          ObjectUtils.asMap("folder", folder));
      tempFile.delete(); // dọn dẹp file tạm
      return (String) result.get("secure_url");
    } catch (Exception e) {
      throw new RuntimeException("Cloudinary upload failed: " + e.getMessage(), e);
    }
  }

  private File convertMultiPartToFile(MultipartFile file) throws IOException {
    File convFile = new File(System.getProperty("java.io.tmpdir") + "/" + file.getOriginalFilename());
    try (FileOutputStream fos = new FileOutputStream(convFile)) {
      fos.write(file.getBytes());
    }
    return convFile;
  }
}