package client.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import client.model.User;
import client.service.SessionManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public abstract class BaseDashboardController {

    protected static class SectionContent {
        private final String title;
        private final String subtitle;
        private final String surfaceTitle;
        private final String surfaceDescription;
        private final String rightTitle;
        private final String rightDescription;
        private final String[] statValues;
        private final String[] statLabels;
        private final String[] featureTitles;
        private final String[] featureDescriptions;
        private final String[] activityLines;
        private final String[] quickTags;
        private final String[] insightTitles;
        private final String[] insightValues;

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

    protected abstract Map<String, SectionContent> createSections();

    protected abstract String getDefaultSectionKey();

    protected abstract String getRoleTitle();

    @FXML
    protected void initialize() {
        bindUserMeta();
        collectDisplayLabels();
        registerNavigationButtons();
        showSection(getDefaultSectionKey());
    }

    private void bindUserMeta() {
        User user = SessionManager.getCurrentUser();

        String username = user != null && user.getUsername() != null
                ? user.getUsername()
                : "Guest User";

        String email = user != null && user.getEmail() != null
                ? user.getEmail()
                : username.toLowerCase().replace(" ", ".") + "@auction.local";

        usernameLabel.setText(username);
        emailLabel.setText(email);
        roleLabel.setText(getRoleTitle());
        avatarInitialsLabel.setText(buildInitials(username));
    }

    private void collectDisplayLabels() {
        statValueLabels.add(statValue1);
        statValueLabels.add(statValue2);
        statValueLabels.add(statValue3);
        statValueLabels.add(statValue4);

        statTextLabels.add(statLabel1);
        statTextLabels.add(statLabel2);
        statTextLabels.add(statLabel3);
        statTextLabels.add(statLabel4);

        featureTitleLabels.add(featureTitle1);
        featureTitleLabels.add(featureTitle2);
        featureTitleLabels.add(featureTitle3);

        featureDescriptionLabels.add(featureDescription1);
        featureDescriptionLabels.add(featureDescription2);
        featureDescriptionLabels.add(featureDescription3);

        activityLabels.add(activityLine1);
        activityLabels.add(activityLine2);
        activityLabels.add(activityLine3);
        activityLabels.add(activityLine4);

        quickTagLabels.add(quickTag1);
        quickTagLabels.add(quickTag2);
        quickTagLabels.add(quickTag3);

        insightTitleLabels.add(insightTitle1);
        insightTitleLabels.add(insightTitle2);
        insightTitleLabels.add(insightTitle3);

        insightValueLabels.add(insightValue1);
        insightValueLabels.add(insightValue2);
        insightValueLabels.add(insightValue3);
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

        if (!(source instanceof Button clickedButton)) {
            return;
        }

        String sectionKey = navigationMap.get(clickedButton);

        if (sectionKey != null) {
            showSection(sectionKey);
        }
    }

    protected void showSection(String sectionKey) {
        SectionContent content = createSections().get(sectionKey);

        if (content == null) {
            return;
        }

        updateActiveButton(sectionKey);

        headerTitleLabel.setText(content.title);
        headerSubtitleLabel.setText(content.subtitle);
        sectionTitleLabel.setText(content.title);
        sectionDescriptionLabel.setText(content.subtitle);
        surfaceTitleLabel.setText(content.surfaceTitle);
        surfaceDescriptionLabel.setText(content.surfaceDescription);
        rightPanelTitleLabel.setText(content.rightTitle);
        rightPanelDescriptionLabel.setText(content.rightDescription);

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

    private String buildInitials(String username) {
        String[] parts = username.trim().split("\\s+");

        if (parts.length == 0 || parts[0].isBlank()) {
            return "AU";
        }

        if (parts.length == 1) {
            String word = parts[0].toUpperCase();
            return word.length() >= 2 ? word.substring(0, 2) : word;
        }

        String first = parts[0].substring(0, 1).toUpperCase();
        String second = parts[1].substring(0, 1).toUpperCase();

        return first + second;
    }

    @FXML
    protected void handleLogout() {
        SessionManager.clear();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/auth.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) usernameLabel.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Auction System - Sign In");
            stage.show();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}