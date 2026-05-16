package client.controller;

import client.SceneNavigator;
import client.model.User;
import client.service.SessionManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Shared base controller for role-specific dashboards.
 */
public abstract class BaseDashboardController {

  protected static class SectionContent {
    protected final String title;
    protected final String subtitle;
    protected final String surfaceTitle;
    protected final String surfaceDescription;
    protected final String rightTitle;
    protected final String rightDescription;
    protected final String[] statValues;
    protected final String[] statLabels;
    protected final String[] featureTitles;
    protected final String[] featureDescriptions;
    protected final String[] activityLines;
    protected final String[] quickTags;
    protected final String[] insightTitles;
    protected final String[] insightValues;

    protected SectionContent(
        String title,
        String subtitle,
        String surfaceTitle,
        String surfaceDescription,
        String rightTitle,
        String rightDescription,
        String[] statValues,
        String[] statLabels,
        String[] featureTitles,
        String[] featureDescriptions,
        String[] activityLines,
        String[] quickTags,
        String[] insightTitles,
        String[] insightValues) {
      this.title = title;
      this.subtitle = subtitle;
      this.surfaceTitle = surfaceTitle;
      this.surfaceDescription = surfaceDescription;
      this.rightTitle = rightTitle;
      this.rightDescription = rightDescription;
      this.statValues = statValues;
      this.statLabels = statLabels;
      this.featureTitles = featureTitles;
      this.featureDescriptions = featureDescriptions;
      this.activityLines = activityLines;
      this.quickTags = quickTags;
      this.insightTitles = insightTitles;
      this.insightValues = insightValues;
    }
  }

  @FXML protected Label usernameLabel;
  @FXML protected Label emailLabel;
  @FXML protected Label roleLabel;
  @FXML protected Label avatarInitialsLabel;

  @FXML protected Label headerTitleLabel;
  @FXML protected Label headerSubtitleLabel;
  @FXML protected Label sectionTitleLabel;
  @FXML protected Label sectionDescriptionLabel;
  @FXML protected Label surfaceTitleLabel;
  @FXML protected Label surfaceDescriptionLabel;
  @FXML protected Label rightPanelTitleLabel;
  @FXML protected Label rightPanelDescriptionLabel;

  @FXML protected Label statValue1;
  @FXML protected Label statValue2;
  @FXML protected Label statValue3;
  @FXML protected Label statValue4;
  @FXML protected Label statLabel1;
  @FXML protected Label statLabel2;
  @FXML protected Label statLabel3;
  @FXML protected Label statLabel4;

  @FXML protected Label featureTitle1;
  @FXML protected Label featureTitle2;
  @FXML protected Label featureTitle3;
  @FXML protected Label featureDescription1;
  @FXML protected Label featureDescription2;
  @FXML protected Label featureDescription3;

  @FXML protected Label activityLine1;
  @FXML protected Label activityLine2;
  @FXML protected Label activityLine3;
  @FXML protected Label activityLine4;

  @FXML protected Label quickTag1;
  @FXML protected Label quickTag2;
  @FXML protected Label quickTag3;

  @FXML protected Label insightTitle1;
  @FXML protected Label insightTitle2;
  @FXML protected Label insightTitle3;
  @FXML protected Label insightValue1;
  @FXML protected Label insightValue2;
  @FXML protected Label insightValue3;

  @FXML protected TextField searchField;

  @FXML protected Button dashboardBtn;
  @FXML protected Button auctionsBtn;
  @FXML protected Button myBidsBtn;
  @FXML protected Button autoBidsBtn;
  @FXML protected Button myItemsBtn;
  @FXML protected Button winnersBtn;
  @FXML protected Button usersBtn;
  @FXML protected Button itemsBtn;
  @FXML protected Button reportsBtn;
  @FXML protected Button settingsBtn;

  private final Map<Button, String> navigationMap = new HashMap<>();
  private final List<Button> navButtons = new ArrayList<>();
  private final List<Label> statValueLabels = new ArrayList<>();
  private final List<Label> statTextLabels = new ArrayList<>();
  private final List<Label> featureTitleLabels = new ArrayList<>();
  private final List<Label> featureDescriptionLabels = new ArrayList<>();
  private final List<Label> activityLabels = new ArrayList<>();
  private final List<Label> quickTagLabels = new ArrayList<>();
  private final List<Label> insightTitleLabels = new ArrayList<>();
  private final List<Label> insightValueLabels = new ArrayList<>();

  private User dashboardUser;

  protected abstract Map<String, SectionContent> createSections();

  protected abstract String getDefaultSectionKey();

  protected abstract String getRoleTitle();

  @FXML
  protected void initialize() {
    refreshUserMeta();
    collectDisplayLabels();
    registerNavigationButtons();
    showSection(getDefaultSectionKey());
  }

  /**
   * Applies the authenticated user to the dashboard and persists the session.
   *
   * @param user user returned by the authentication flow
   */
  public void applyLoggedInUser(User user) {
    if (user != null) {
      dashboardUser = user;
      SessionManager.setCurrentUser(user);
    }
    refreshUserMeta();
  }

  protected void refreshUserMeta() {
    User user = SessionManager.getCurrentUser();
    if (user == null) {
      user = dashboardUser;
    } else {
      dashboardUser = user;
    }

    String username = hasText(user == null ? null : user.getUsername())
        ? user.getUsername().trim()
        : "";

    String email = hasText(user == null ? null : user.getEmail())
        ? user.getEmail().trim()
        : "";

    String role = user != null && user.getSystemRole() != null
        ? user.getSystemRole().name()
        : "";

    setText(usernameLabel, username);
    setText(emailLabel, email);
    setText(roleLabel, role);
    setText(avatarInitialsLabel, buildInitials(username));
  }

  private void collectDisplayLabels() {
    addIfPresent(statValueLabels, statValue1, statValue2, statValue3, statValue4);
    addIfPresent(statTextLabels, statLabel1, statLabel2, statLabel3, statLabel4);
    addIfPresent(featureTitleLabels, featureTitle1, featureTitle2, featureTitle3);
    addIfPresent(
        featureDescriptionLabels,
        featureDescription1,
        featureDescription2,
        featureDescription3
    );
    addIfPresent(activityLabels, activityLine1, activityLine2, activityLine3, activityLine4);
    addIfPresent(quickTagLabels, quickTag1, quickTag2, quickTag3);
    addIfPresent(insightTitleLabels, insightTitle1, insightTitle2, insightTitle3);
    addIfPresent(insightValueLabels, insightValue1, insightValue2, insightValue3);
  }

  private void addIfPresent(List<Label> target, Label... labels) {
    for (Label label : labels) {
      if (label != null) {
        target.add(label);
      }
    }
  }

  private void registerNavigationButtons() {
    putButton(dashboardBtn, "dashboard");
    putButton(auctionsBtn, "auctions");
    putButton(myBidsBtn, "myBids");
    putButton(autoBidsBtn, "autoBids");
    putButton(myItemsBtn, "myItems");
    putButton(winnersBtn, "winners");
    putButton(usersBtn, "users");
    putButton(itemsBtn, "items");
    putButton(reportsBtn, "reports");
    putButton(settingsBtn, "settings");
  }

  private void putButton(Button button, String sectionKey) {
    if (button != null) {
      navigationMap.put(button, sectionKey);
      navButtons.add(button);
    }
  }

  @FXML
  protected void handleSidebarNavigation(ActionEvent event) {
    Object source = event.getSource();

    if (source instanceof Button clickedButton) {
      String sectionKey = navigationMap.get(clickedButton);

      if (sectionKey != null) {
        showSection(sectionKey);
      }
    }
  }

  protected void showSection(String sectionKey) {
    SectionContent content = createSections().get(sectionKey);

    if (content == null) {
      return;
    }

    updateActiveButton(sectionKey);

    setText(headerTitleLabel, content.title);
    setText(headerSubtitleLabel, content.subtitle);
    setText(sectionTitleLabel, content.title);
    setText(sectionDescriptionLabel, content.subtitle);
    setText(surfaceTitleLabel, content.surfaceTitle);
    setText(surfaceDescriptionLabel, content.surfaceDescription);
    setText(rightPanelTitleLabel, content.rightTitle);
    setText(rightPanelDescriptionLabel, content.rightDescription);

    applyTextArray(statValueLabels, content.statValues);
    applyTextArray(statTextLabels, content.statLabels);
    applyTextArray(featureTitleLabels, content.featureTitles);
    applyTextArray(featureDescriptionLabels, content.featureDescriptions);
    applyTextArray(activityLabels, content.activityLines);
    applyTextArray(quickTagLabels, content.quickTags);
    applyTextArray(insightTitleLabels, content.insightTitles);
    applyTextArray(insightValueLabels, content.insightValues);

    if (searchField != null) {
      searchField.setPromptText("Search within " + content.title.toLowerCase() + "...");
    }
  }

  private void updateActiveButton(String sectionKey) {
    for (Button button : navButtons) {
      button.getStyleClass().remove("nav-btn-active");

      String key = navigationMap.get(button);

      if (sectionKey.equals(key)) {
        button.getStyleClass().add("nav-btn-active");
      }
    }
  }

  private void applyTextArray(List<Label> labels, String[] values) {
    for (int i = 0; i < labels.size(); i++) {
      labels.get(i).setText(values != null && i < values.length ? values[i] : "");
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private void setText(Label label, String value) {
    if (label != null) {
      String safeValue = value == null ? "" : value;
      label.setText(safeValue);
      boolean visible = !safeValue.isBlank();
      label.setVisible(visible);
      label.setManaged(visible);
    }
  }

  private String buildInitials(String username) {
    String[] parts = username == null ? new String[0] : username.trim().split("\\s+");

    if (parts.length == 0 || parts[0].isBlank()) {
      return "U";
    }

    if (parts.length == 1) {
      String word = parts[0].toUpperCase();
      return word.length() >= 2 ? word.substring(0, 2) : word;
    }

    return parts[0].substring(0, 1).toUpperCase()
        + parts[1].substring(0, 1).toUpperCase();
  }

  @FXML
  protected void handleLogout() {
    SessionManager.clear();

    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/auth.fxml"));
      Parent root = loader.load();

      Stage stage = (Stage) usernameLabel.getScene().getWindow();
      SceneNavigator.switchSceneKeepingWindow(stage, root, "Auction System - Sign In");
    } catch (IOException exception) {
      exception.printStackTrace();
    }
  }
}
