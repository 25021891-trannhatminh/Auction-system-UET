package server.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;

public class CloudinaryUploader {
  private final Cloudinary cloudinary;

  public CloudinaryUploader() {
    Map<String, String> config = ObjectUtils.asMap(
        "cloud_name", "dp0ynsodr",
        "api_key", "885748117355581",
        "api_secret", "gZT_olKB3e7-ygFufndizFWo19k",
        "secure", true
    );
    this.cloudinary = new Cloudinary(config);
  }

  public String uploadBase64(String fileName, String base64Data) throws Exception {
    byte[] data = Base64.getDecoder().decode(base64Data);
    File tempFile = File.createTempFile("upload_", "_" + fileName);
    Files.write(tempFile.toPath(), data);
    Map<?, ?> result = cloudinary.uploader().upload(tempFile, ObjectUtils.asMap("folder", "auctionsystem_images"));
    tempFile.delete();
    return (String) result.get("secure_url");
  }
}