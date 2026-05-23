package client.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.io.File;
import java.util.Map;

/**
 * Trình quản lý và đồng bộ tài nguyên đa phương tiện lên Cloud đám mây.
 */
public class CloudMediaManager {

  private final Cloudinary cloudinary;

  public CloudMediaManager() {
    this.cloudinary = new Cloudinary(ObjectUtils.asMap(
        "cloud_name", "dp0ynsodr",
        "api_key", "885748117355581",
        "api_secret", "gZT_olKB3e7-ygFufnSnZ7H2d4E",
        "secure", true
    ));
  }

  public String uploadAsset(File file) {
    if (file == null || !file.exists()) return null;
    try {
      Map<?, ?> result = cloudinary.uploader().upload(file, ObjectUtils.emptyMap());
      return (String) result.get("secure_url");
    } catch (Exception e) {
      System.err.println("❌ Cloud Storage Error: " + e.getMessage());
      return null;
    }
  }
}