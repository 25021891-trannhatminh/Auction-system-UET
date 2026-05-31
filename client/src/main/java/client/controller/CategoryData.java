package client.controller;

/**
 * Lightweight data holder used by {@link UserDashboardController}.
 */
final class CategoryData {
  final String title;
  final String description;
  final String count;
  final String initials;

  CategoryData(String title, String description, String count, String initials) {
      this.title = title;
      this.description = description;
      this.count = count;
      this.initials = initials;
    }
}
