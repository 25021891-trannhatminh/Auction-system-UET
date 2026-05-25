package client.controller;

/**
 * Lightweight data holder used by {@link UserDashboardController}.
 */
final class CreateItemUpload {
  final String publicUri;
  final String previewUri;
  final String fileName;
  final long sizeBytes;

  CreateItemUpload(String publicUri, String previewUri, String fileName, long sizeBytes) {
      this.publicUri = publicUri;
      this.previewUri = previewUri;
      this.fileName = fileName;
      this.sizeBytes = sizeBytes;
    }
}
