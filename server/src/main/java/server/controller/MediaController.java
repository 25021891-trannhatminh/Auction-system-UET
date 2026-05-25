package server.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import server.service.CloudMediaManager;

@RestController
@RequestMapping("/api/media")
public class MediaController {

  private final CloudMediaManager cloudMediaManager;

  // Constructor injection (tốt nhất)
  public MediaController(CloudMediaManager cloudMediaManager) {
    this.cloudMediaManager = cloudMediaManager;
  }

  @PostMapping("/upload")
  public ResponseEntity<String> uploadImage(@RequestParam("file") MultipartFile file) {
    String url = cloudMediaManager.uploadFile(file, "auctionsystem_images");
    return ResponseEntity.ok(url);
  }
}