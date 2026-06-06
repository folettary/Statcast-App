package com.folettary.statcastcompare;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int STATCAST_START_YEAR = 2015;
    private static final int NAVY = Color.rgb(10, 23, 55);
    private static final int NAVY_2 = Color.rgb(21, 53, 97);
    private static final int TEAL = Color.rgb(13, 178, 163);
    private static final int TEAL_DARK = Color.rgb(0, 125, 115);
    private static final int SALMON = Color.rgb(255, 122, 107);
    private static final int AMBER = Color.rgb(255, 190, 89);
    private static final int BG = Color.rgb(242, 246, 251);
    private static final int INK = Color.rgb(22, 29, 43);
    private static final int MUTED = Color.rgb(94, 105, 124);
    private static final int LINE = Color.rgb(221, 229, 241);
    private static final int CARD = Color.WHITE;

    private final ExecutorService io = Executors.newFixedThreadPool(8);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, String> textCache = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Bitmap> imageCache = Collections.synchronizedMap(new HashMap<>());
    private final Map<Integer, ArrayList<LeaderboardEntry>> leaderboardCache = Collections.synchronizedMap(new HashMap<>());

    private LinearLayout root;
    private LinearLayout form;
    private Button playerModeButton;
    private Button teamModeButton;
    private EditText searchInput;
    private ListView suggestionsList;
    private Spinner teamSpinner;
    private Spinner seasonSpinner;
    private Spinner rankMetricSpinner;
    private Button compareButton;
    private Button standingsButton;
    private ProgressBar loading;
    private TextView statusView;
    private TextView errorView;
    private LinearLayout filterBox;
    private LinearLayout resultsBox;
    private LinearLayout headerBox;
    private LinearLayout metricBox;
    private LinearLayout standingsBox;
    private Button copyButton;

    private final ArrayList<Player> allPlayers = new ArrayList<>();
    private final ArrayList<Player> filteredPlayers = new ArrayList<>();
    private final ArrayList<Team> allTeams = new ArrayList<>();
    private ArrayAdapter<String> suggestionsAdapter;
    private ArrayAdapter<String> teamAdapter;
    private Player selectedPlayer;
    private Team selectedTeam;
    private boolean teamMode = false;
    private Comparison lastComparison;
    private String lastStandingsText = "";

    private final LinkedHashSet<String> selectedMetricKeys = new LinkedHashSet<>();
    private final Map<String, CheckBox> metricChecks = new LinkedHashMap<>();

    private final Metric[] metrics = new Metric[] {
            new Metric("xwOBA", "xwOBA", "", 3, true, "expected", "Expected"),
            new Metric("xBA", "xBA", "", 3, true, "expected", "Expected"),
            new Metric("xSLG", "xSLG", "", 3, true, "expected", "Expected"),
            new Metric("avgEV", "Avg EV", " mph", 1, true, "contact", "Contact"),
            new Metric("avgLA", "Launch Angle", "°", 1, null, "contact", "Contact"),
            new Metric("hardHitPct", "Hard-Hit %", "%", 1, true, "rate", "Contact"),
            new Metric("barrelPct", "Barrel %", "%", 1, true, "rate", "Contact"),
            new Metric("sweetSpotPct", "Sweet-Spot %", "%", 1, true, "rate", "Contact"),
            new Metric("kPct", "K %", "%", 1, false, "rate", "Discipline"),
            new Metric("bbPct", "BB %", "%", 1, true, "rate", "Discipline")
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        for (Metric m : metrics) selectedMetricKeys.add(m.key);
        buildUi();
        loadTeamsAndPlayers();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(16), dp(14), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        LinearLayout hero = verticalCard(26, new int[] { NAVY, NAVY_2, Color.rgb(12, 88, 98) });
        hero.setPadding(dp(18), dp(18), dp(18), dp(16));
        root.addView(hero, matchWrap());

        TextView eyebrow = text("LIVE MLB + STATCAST", 12, Color.rgb(181, 247, 239), true);
        eyebrow.setLetterSpacing(0.12f);
        hero.addView(eyebrow);

        TextView title = text("Statcast Compare", 31, Color.WHITE, true);
        title.setPadding(0, dp(6), 0, dp(4));
        hero.addView(title);

        TextView subtitle = text("Compact player and team comparisons, visual standings, and faster season-level loading.", 15, Color.rgb(220, 230, 247), false);
        subtitle.setLineSpacing(dp(2), 1.0f);
        hero.addView(subtitle);

        LinearLayout heroPills = new LinearLayout(this);
        heroPills.setOrientation(LinearLayout.HORIZONTAL);
        heroPills.setPadding(0, dp(14), 0, 0);
        heroPills.addView(heroPill("Players"), weightLp());
        heroPills.addView(heroPill("Teams"), weightLp());
        heroPills.addView(heroPill("Standings"), weightLp());
        hero.addView(heroPills);

        form = verticalCard(24, null);
        form.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams formLp = matchWrap();
        formLp.setMargins(0, dp(14), 0, 0);
        root.addView(form, formLp);

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        playerModeButton = pillButton("Player", true);
        teamModeButton = pillButton("Team", false);
        modeRow.addView(playerModeButton, weightLp());
        modeRow.addView(teamModeButton, weightLp());
        form.addView(modeRow, matchWrap());

        TextView searchLabel = text("Search", 13, MUTED, true);
        searchLabel.setPadding(0, dp(12), 0, 0);
        form.addView(searchLabel);

        searchInput = new EditText(this);
        searchInput.setHint("Type a player name, e.g. Soto");
        searchInput.setSingleLine(true);
        searchInput.setTextSize(17);
        searchInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        searchInput.setBackground(roundedStroke(Color.WHITE, Color.rgb(207, 217, 231), 15, 1));
        LinearLayout.LayoutParams inputLp = matchWrap();
        inputLp.setMargins(0, dp(6), 0, dp(8));
        form.addView(searchInput, inputLp);

        suggestionsList = new ListView(this);
        suggestionsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        suggestionsList.setAdapter(suggestionsAdapter);
        suggestionsList.setVisibility(View.GONE);
        suggestionsList.setDividerHeight(1);
        suggestionsList.setBackground(roundedStroke(Color.WHITE, Color.rgb(230, 235, 244), 14, 1));
        form.addView(suggestionsList, new LinearLayout.LayoutParams(-1, dp(180)));

        teamSpinner = new Spinner(this);
        teamAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>());
        teamSpinner.setAdapter(teamAdapter);
        teamSpinner.setVisibility(View.GONE);
        form.addView(teamSpinner, matchWrap());

        statusView = text("Loading MLB teams and active players…", 13, MUTED, false);
        statusView.setPadding(0, dp(8), 0, dp(10));
        form.addView(statusView);

        LinearLayout seasonRankRow = new LinearLayout(this);
        seasonRankRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout seasonCol = new LinearLayout(this);
        seasonCol.setOrientation(LinearLayout.VERTICAL);
        seasonCol.addView(text("Season", 13, MUTED, true));
        seasonSpinner = new Spinner(this);
        ArrayList<String> years = new ArrayList<>();
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int y = currentYear; y >= STATCAST_START_YEAR; y--) years.add(String.valueOf(y));
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, years);
        seasonSpinner.setAdapter(yearAdapter);
        seasonCol.addView(seasonSpinner, matchWrap());

        LinearLayout rankCol = new LinearLayout(this);
        rankCol.setOrientation(LinearLayout.VERTICAL);
        rankCol.addView(text("Rank by", 13, MUTED, true));
        rankMetricSpinner = new Spinner(this);
        ArrayList<String> metricLabels = new ArrayList<>();
        for (Metric m : metrics) metricLabels.add(m.label);
        ArrayAdapter<String> rankAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, metricLabels);
        rankMetricSpinner.setAdapter(rankAdapter);
        rankCol.addView(rankMetricSpinner, matchWrap());

        seasonRankRow.addView(seasonCol, weightLp());
        seasonRankRow.addView(rankCol, weightLp());
        form.addView(seasonRankRow, matchWrap());

        filterBox = new LinearLayout(this);
        filterBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams filterLp = matchWrap();
        filterLp.setMargins(0, dp(8), 0, 0);
        form.addView(filterBox, filterLp);
        buildMetricFilters();

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.VERTICAL);
        compareButton = new Button(this);
        compareButton.setText("Compare player");
        compareButton.setTextColor(Color.WHITE);
        compareButton.setTextSize(16);
        compareButton.setAllCaps(false);
        compareButton.setTypeface(Typeface.DEFAULT_BOLD);
        compareButton.setBackground(rounded(TEAL, 16));
        LinearLayout.LayoutParams btnLp = matchWrap();
        btnLp.setMargins(0, dp(12), 0, 0);
        actionRow.addView(compareButton, btnLp);

        standingsButton = new Button(this);
        standingsButton.setText("Show player standings");
        standingsButton.setTextColor(NAVY);
        standingsButton.setTextSize(15);
        standingsButton.setAllCaps(false);
        standingsButton.setTypeface(Typeface.DEFAULT_BOLD);
        standingsButton.setBackground(roundedStroke(Color.WHITE, Color.rgb(199, 213, 229), 16, 1));
        LinearLayout.LayoutParams stLp = matchWrap();
        stLp.setMargins(0, dp(8), 0, 0);
        actionRow.addView(standingsButton, stLp);
        form.addView(actionRow, matchWrap());

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        LinearLayout.LayoutParams loadLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        loadLp.gravity = Gravity.CENTER_HORIZONTAL;
        loadLp.setMargins(0, dp(14), 0, 0);
        form.addView(loading, loadLp);

        errorView = text("", 14, Color.rgb(180, 54, 70), false);
        errorView.setPadding(0, dp(12), 0, 0);
        errorView.setVisibility(View.GONE);
        form.addView(errorView);

        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        resultsBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams resultsLp = matchWrap();
        resultsLp.setMargins(0, dp(16), 0, 0);
        root.addView(resultsBox, resultsLp);

        headerBox = new LinearLayout(this);
        headerBox.setOrientation(LinearLayout.VERTICAL);
        resultsBox.addView(headerBox, matchWrap());

        copyButton = new Button(this);
        copyButton.setText("Copy current table");
        copyButton.setAllCaps(false);
        copyButton.setTextColor(NAVY);
        copyButton.setBackground(roundedStroke(Color.WHITE, Color.rgb(203, 214, 228), 14, 1));
        LinearLayout.LayoutParams copyLp = matchWrap();
        copyLp.setMargins(0, dp(8), 0, dp(8));
        resultsBox.addView(copyButton, copyLp);

        metricBox = new LinearLayout(this);
        metricBox.setOrientation(LinearLayout.VERTICAL);
        resultsBox.addView(metricBox, matchWrap());

        standingsBox = new LinearLayout(this);
        standingsBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams standingsLp = matchWrap();
        standingsLp.setMargins(0, dp(14), 0, 0);
        root.addView(standingsBox, standingsLp);

        TextView notes = text("Notes: leaderboard values come from season-level Baseball Savant CSV data. Career/team history is Statcast-era, 2015 through the selected season. Standings use a practical minimum PA/BBE filter so the list is not dominated by tiny samples.", 12, MUTED, false);
        notes.setLineSpacing(dp(2), 1.0f);
        notes.setPadding(0, dp(16), 0, 0);
        root.addView(notes);

        wireEvents();
    }

    private TextView heroPill(String label) {
        TextView tv = text(label, 12, Color.WHITE, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(8), dp(9), dp(8), dp(9));
        tv.setBackground(roundedStroke(Color.argb(38, 255, 255, 255), Color.argb(80, 255, 255, 255), 16, 1));
        return tv;
    }

    private Button pillButton(String label, boolean active) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(active ? Color.WHITE : NAVY);
        b.setBackground(active ? rounded(NAVY, 16) : roundedStroke(Color.WHITE, LINE, 16, 1));
        return b;
    }

    private void buildMetricFilters() {
        filterBox.removeAllViews();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("Stats shown", 13, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView all = text("All", 13, TEAL_DARK, true);
        all.setGravity(Gravity.CENTER);
        all.setPadding(dp(12), dp(6), dp(12), dp(6));
        all.setBackground(roundedStroke(Color.WHITE, LINE, 14, 1));
        all.setOnClickListener(v -> {
            selectedMetricKeys.clear();
            for (Metric m : metrics) selectedMetricKeys.add(m.key);
            for (CheckBox cb : metricChecks.values()) cb.setChecked(true);
            refreshCurrentResults();
        });
        TextView none = text("Clear", 13, MUTED, true);
        none.setGravity(Gravity.CENTER);
        none.setPadding(dp(12), dp(6), dp(12), dp(6));
        none.setBackground(roundedStroke(Color.WHITE, LINE, 14, 1));
        none.setOnClickListener(v -> {
            selectedMetricKeys.clear();
            for (CheckBox cb : metricChecks.values()) cb.setChecked(false);
            refreshCurrentResults();
        });
        top.addView(all);
        LinearLayout.LayoutParams noneLp = new LinearLayout.LayoutParams(-2, -2);
        noneLp.setMargins(dp(6), 0, 0, 0);
        top.addView(none, noneLp);
        filterBox.addView(top, matchWrap());

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, 0);
        metricChecks.clear();
        for (Metric m : metrics) {
            CheckBox cb = new CheckBox(this);
            cb.setText(m.label);
            cb.setTextColor(INK);
            cb.setTextSize(13);
            cb.setChecked(true);
            cb.setButtonTintList(ColorStateList.valueOf(TEAL));
            cb.setPadding(dp(2), 0, dp(10), 0);
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) selectedMetricKeys.add(m.key); else selectedMetricKeys.remove(m.key);
                refreshCurrentResults();
            });
            metricChecks.put(m.key, cb);
            row.addView(cb);
        }
        hsv.addView(row);
        filterBox.addView(hsv, matchWrap());
    }

    private void wireEvents() {
        playerModeButton.setOnClickListener(v -> setMode(false));
        teamModeButton.setOnClickListener(v -> setMode(true));

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterPlayers(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        suggestionsList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < filteredPlayers.size()) {
                selectedPlayer = filteredPlayers.get(position);
                searchInput.setText(selectedPlayer.fullName);
                searchInput.setSelection(searchInput.getText().length());
                suggestionsList.setVisibility(View.GONE);
                statusView.setText(selectedPlayer.fullName + " · " + selectedPlayer.teamAbbr + " · " + selectedPlayer.position + " · MLB ID " + selectedPlayer.id);
                hideKeyboard();
            }
        });

        teamSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < allTeams.size()) selectedTeam = allTeams.get(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        compareButton.setOnClickListener(v -> compareSelected());
        standingsButton.setOnClickListener(v -> showStandings());
        copyButton.setOnClickListener(v -> copyCurrentTable());
    }

    private void setMode(boolean useTeamMode) {
        teamMode = useTeamMode;
        playerModeButton.setTextColor(teamMode ? NAVY : Color.WHITE);
        playerModeButton.setBackground(teamMode ? roundedStroke(Color.WHITE, LINE, 16, 1) : rounded(NAVY, 16));
        teamModeButton.setTextColor(teamMode ? Color.WHITE : NAVY);
        teamModeButton.setBackground(teamMode ? rounded(NAVY, 16) : roundedStroke(Color.WHITE, LINE, 16, 1));
        searchInput.setVisibility(teamMode ? View.GONE : View.VISIBLE);
        suggestionsList.setVisibility(View.GONE);
        teamSpinner.setVisibility(teamMode ? View.VISIBLE : View.GONE);
        compareButton.setText(teamMode ? "Compare team" : "Compare player");
        standingsButton.setText(teamMode ? "Show team standings" : "Show player standings");
        statusView.setText(teamMode ? "Choose a team, season, and ranking stat." : (selectedPlayer == null ? "Start typing a player name." : selectedPlayer.fullName + " · " + selectedPlayer.teamAbbr));
        resultsBox.setVisibility(View.GONE);
        standingsBox.removeAllViews();
    }

    private void loadTeamsAndPlayers() {
        setBusy(true, "Loading MLB teams and active players…");
        io.execute(() -> {
            try {
                LoadedData loaded = fetchTeamsAndActivePlayers();
                main.post(() -> {
                    allTeams.clear(); allTeams.addAll(loaded.teams);
                    allPlayers.clear(); allPlayers.addAll(loaded.players);
                    teamAdapter.clear();
                    for (Team t : allTeams) teamAdapter.add(t.name);
                    teamAdapter.notifyDataSetChanged();
                    if (!allTeams.isEmpty()) selectedTeam = allTeams.get(0);
                    statusView.setText("Loaded " + allPlayers.size() + " active players and " + allTeams.size() + " teams.");
                    setBusy(false, null);
                });
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false, null);
                    showError("Could not load MLB team/player list. Check your connection and reopen the app. " + e.getMessage());
                    statusView.setText("Roster list unavailable.");
                });
            }
        });
    }

    private void filterPlayers(String raw) {
        if (teamMode) return;
        suggestionsAdapter.clear();
        filteredPlayers.clear();
        String q = raw.trim().toLowerCase(Locale.US);
        if (q.length() < 2 || allPlayers.isEmpty()) {
            suggestionsList.setVisibility(View.GONE);
            return;
        }
        for (Player p : allPlayers) {
            if (p.fullName.toLowerCase(Locale.US).contains(q)) {
                filteredPlayers.add(p);
                suggestionsAdapter.add(p.fullName + "  ·  " + p.teamAbbr + "  ·  " + p.position);
                if (filteredPlayers.size() >= 24) break;
            }
        }
        suggestionsAdapter.notifyDataSetChanged();
        suggestionsList.setVisibility(filteredPlayers.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void compareSelected() {
        hideKeyboard();
        showError(null);
        standingsBox.removeAllViews();
        int season = Integer.parseInt((String) seasonSpinner.getSelectedItem());
        if (teamMode) {
            if (selectedTeam == null) { showError("Pick a team first."); return; }
            compareTeam(selectedTeam, season);
        } else {
            if (selectedPlayer == null) { showError("Pick a player from the search results first."); return; }
            comparePlayer(selectedPlayer, season);
        }
    }

    private void comparePlayer(Player player, int season) {
        setBusy(true, "Loading season leaderboard and career summary…");
        io.execute(() -> {
            try {
                ArrayList<LeaderboardEntry> entries = fetchLeaderboard(season);
                LeaderboardEntry seasonEntry = findPlayerEntry(entries, player);
                Stats seasonStats = seasonEntry == null ? new Stats() : seasonEntry.stats;
                Stats leagueStats = computeLeagueAverage(entries);
                Stats careerStats = fetchPlayerCareerStats(player, season);
                Comparison comparison = new Comparison(false, player.fullName, player.teamAbbr, player.position, player.id, season, seasonStats, careerStats, leagueStats, new Date(), "Career", player);
                main.post(() -> {
                    lastComparison = comparison;
                    renderComparison(comparison);
                    setBusy(false, null);
                });
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false, null);
                    showError("Could not load player comparison. Baseball Savant may be slow/rate-limiting, or this player may have no leaderboard row for the selected season. " + e.getMessage());
                });
            }
        });
    }

    private void compareTeam(Team team, int season) {
        setBusy(true, "Loading team leaderboard summary…");
        io.execute(() -> {
            try {
                ArrayList<LeaderboardEntry> entries = fetchLeaderboard(season);
                Stats teamStats = aggregateTeamStats(entries).get(team.key());
                if (teamStats == null) teamStats = new Stats();
                Stats leagueStats = computeLeagueAverage(entries);
                Stats historyStats = fetchTeamHistoryStats(team, season);
                Comparison comparison = new Comparison(true, team.name, team.abbr, "Team", 0, season, teamStats, historyStats, leagueStats, new Date(), "2015–" + season + " team avg", null);
                main.post(() -> {
                    lastComparison = comparison;
                    renderComparison(comparison);
                    setBusy(false, null);
                });
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false, null);
                    showError("Could not load team comparison. " + e.getMessage());
                });
            }
        });
    }

    private void showStandings() {
        hideKeyboard();
        showError(null);
        int season = Integer.parseInt((String) seasonSpinner.getSelectedItem());
        Metric metric = metrics[rankMetricSpinner.getSelectedItemPosition()];
        setBusy(true, "Loading " + (teamMode ? "team" : "player") + " standings…");
        io.execute(() -> {
            try {
                ArrayList<LeaderboardEntry> entries = fetchLeaderboard(season);
                if (teamMode) renderTeamStandingsAsync(entries, season, metric);
                else renderPlayerStandingsAsync(entries, season, metric);
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false, null);
                    showError("Could not load standings. " + e.getMessage());
                });
            }
        });
    }

    private void renderPlayerStandingsAsync(ArrayList<LeaderboardEntry> entries, int season, Metric metric) {
        ArrayList<LeaderboardEntry> eligible = new ArrayList<>();
        for (LeaderboardEntry e : entries) {
            Double val = e.stats.get(metric.key);
            if (val == null) continue;
            int minPa = season == Calendar.getInstance().get(Calendar.YEAR) ? 50 : 100;
            int minBbe = season == Calendar.getInstance().get(Calendar.YEAR) ? 25 : 50;
            boolean contactMetric = metric.type.equals("contact") || metric.type.equals("rate");
            if (e.stats.pa >= minPa && (!contactMetric || e.stats.bbe >= minBbe)) eligible.add(e);
        }
        sortEntries(eligible, metric);
        main.post(() -> {
            renderPlayerStandings(eligible, season, metric);
            setBusy(false, null);
        });
    }

    private void renderTeamStandingsAsync(ArrayList<LeaderboardEntry> entries, int season, Metric metric) {
        Map<String, Stats> teamStats = aggregateTeamStats(entries);
        ArrayList<TeamStanding> teams = new ArrayList<>();
        for (Team t : allTeams) {
            Stats s = teamStats.get(t.key());
            if (s != null && s.get(metric.key) != null) teams.add(new TeamStanding(t, s));
        }
        teams.sort((a, b) -> compareMetricValues(a.stats.get(metric.key), b.stats.get(metric.key), metric));
        main.post(() -> {
            renderTeamStandings(teams, season, metric);
            setBusy(false, null);
        });
    }

    private void renderComparison(Comparison c) {
        resultsBox.setVisibility(View.VISIBLE);
        headerBox.removeAllViews();
        metricBox.removeAllViews();

        LinearLayout card = verticalCard(24, null);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        headerBox.addView(card, matchWrap());

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top, matchWrap());

        if (!c.isTeam) {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackground(roundedStroke(Color.rgb(235, 241, 248), LINE, 28, 1));
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(76), dp(76));
            imgLp.setMargins(0, 0, dp(12), 0);
            top.addView(img, imgLp);
            loadPlayerImage(c.mlbId, img);
        } else {
            TextView teamBadge = text(c.meta, 25, Color.WHITE, true);
            teamBadge.setGravity(Gravity.CENTER);
            teamBadge.setBackground(roundedGradient(new int[] { NAVY, TEAL_DARK }, 22));
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(76), dp(76));
            badgeLp.setMargins(0, 0, dp(12), 0);
            top.addView(teamBadge, badgeLp);
        }

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        top.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));
        titleCol.addView(text(c.name, 22, INK, true));
        TextView meta = text(c.meta + " · " + c.season + " · updated " + c.updated.toString(), 12, MUTED, false);
        meta.setPadding(0, dp(3), 0, 0);
        titleCol.addView(meta);

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setPadding(0, dp(12), 0, 0);
        summary.addView(summaryPill("Season", fmtCount(c.seasonStats.pa) + " PA\n" + fmtCount(c.seasonStats.bbe) + " BBE"), weightLp());
        summary.addView(summaryPill("League", c.leagueStats == null ? "Unavailable" : fmtCount(c.leagueStats.pa) + " PA\n" + fmtCount(c.leagueStats.bbe) + " BBE"), weightLp());
        summary.addView(summaryPill(c.thirdLabel, fmtCount(c.careerStats.pa) + " PA\n" + fmtCount(c.careerStats.bbe) + " BBE"), weightLp());
        card.addView(summary);

        TextView tableTitle = text("Compact comparison", 18, INK, true);
        tableTitle.setPadding(0, dp(12), 0, dp(4));
        metricBox.addView(tableTitle);

        int count = 0;
        for (Metric m : metrics) {
            if (!selectedMetricKeys.contains(m.key)) continue;
            renderMetricRow(c, m);
            count++;
        }
        if (count == 0) {
            TextView empty = text("No stats selected. Tap All in the Stats shown row.", 14, MUTED, false);
            empty.setPadding(0, dp(10), 0, dp(10));
            metricBox.addView(empty);
        }
    }

    private void refreshCurrentResults() {
        if (lastComparison != null && resultsBox.getVisibility() == View.VISIBLE) renderComparison(lastComparison);
    }

    private TextView summaryPill(String label, String value) {
        TextView tv = text(label + "\n" + value, 12, INK, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(6), dp(9), dp(6), dp(9));
        tv.setBackground(roundedStroke(Color.rgb(248, 250, 253), LINE, 16, 1));
        tv.setLineSpacing(dp(2), 1.0f);
        return tv;
    }

    private void renderMetricRow(Comparison c, Metric m) {
        Double seasonValue = c.seasonStats.get(m.key);
        Double leagueValue = c.leagueStats == null ? null : c.leagueStats.get(m.key);
        Double careerValue = c.careerStats.get(m.key);
        Double vsLeague = diff(seasonValue, leagueValue);
        Double vsCareer = diff(seasonValue, careerValue);

        LinearLayout row = verticalCard(17, null);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams rowLp = matchWrap();
        rowLp.setMargins(0, dp(7), 0, 0);
        metricBox.addView(row, rowLp);

        LinearLayout line1 = new LinearLayout(this);
        line1.setOrientation(LinearLayout.HORIZONTAL);
        line1.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(m.label, 15, INK, true);
        line1.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text(format(seasonValue, m), 19, NAVY, true);
        line1.addView(value);
        row.addView(line1);

        LinearLayout valueRow = new LinearLayout(this);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setPadding(0, dp(7), 0, 0);
        valueRow.addView(valueCell("Lg", format(leagueValue, m)), weightLp());
        valueRow.addView(valueCell(c.thirdLabelShort(), format(careerValue, m)), weightLp());
        valueRow.addView(valueCell("vs Lg", signedFormat(vsLeague, m)), weightLp());
        valueRow.addView(valueCell("vs Hist", signedFormat(vsCareer, m)), weightLp());
        row.addView(valueRow);

        double[] widths = scaleValues(new Double[] { seasonValue, leagueValue, careerValue }, m);
        LinearLayout bars = new LinearLayout(this);
        bars.setOrientation(LinearLayout.HORIZONTAL);
        bars.setPadding(0, dp(8), 0, 0);
        bars.addView(tinyBar(widths[0], TEAL), weightLp());
        bars.addView(tinyBar(widths[1], Color.rgb(90, 111, 147)), weightLp());
        bars.addView(tinyBar(widths[2], SALMON), weightLp());
        row.addView(bars);
    }

    private TextView valueCell(String label, String value) {
        TextView tv = text(label + "\n" + value, 11, MUTED, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(4), dp(4), dp(4), dp(4));
        tv.setBackground(rounded(Color.rgb(247, 250, 253), 12));
        tv.setLineSpacing(dp(1), 1.0f);
        return tv;
    }

    private ProgressBar tinyBar(double width, int color) {
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(1000);
        pb.setProgress((int) Math.round(width * 10));
        pb.setProgressTintList(ColorStateList.valueOf(color));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(235, 240, 247)));
        pb.setPadding(0, 0, 0, 0);
        return pb;
    }

    private void renderPlayerStandings(ArrayList<LeaderboardEntry> entries, int season, Metric metric) {
        standingsBox.removeAllViews();
        LinearLayout card = verticalCard(22, null);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        standingsBox.addView(card, matchWrap());
        card.addView(text(season + " player standings · " + metric.label, 20, INK, true));
        TextView sub = text("Top " + Math.min(30, entries.size()) + " hitters by selected stat", 12, MUTED, false);
        sub.setPadding(0, dp(3), 0, dp(8));
        card.addView(sub);

        lastStandingsText = season + " player standings by " + metric.label + "\nRank\tPlayer\tTeam\t" + metric.label + "\tPA\tBBE\n";
        int limit = Math.min(30, entries.size());
        for (int i = 0; i < limit; i++) {
            LeaderboardEntry e = entries.get(i);
            boolean highlight = selectedPlayer != null && e.playerId == selectedPlayer.id;
            card.addView(standingRow(i + 1, e.name, e.teamAbbrOrName(), format(e.stats.get(metric.key), metric), e.stats, highlight));
            lastStandingsText += (i + 1) + "\t" + e.name + "\t" + e.teamAbbrOrName() + "\t" + format(e.stats.get(metric.key), metric) + "\t" + e.stats.pa + "\t" + e.stats.bbe + "\n";
        }
        resultsBox.setVisibility(View.GONE);
    }

    private void renderTeamStandings(ArrayList<TeamStanding> teams, int season, Metric metric) {
        standingsBox.removeAllViews();
        LinearLayout card = verticalCard(22, null);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        standingsBox.addView(card, matchWrap());
        card.addView(text(season + " team standings · " + metric.label, 20, INK, true));
        TextView sub = text("All MLB teams ranked by selected stat", 12, MUTED, false);
        sub.setPadding(0, dp(3), 0, dp(8));
        card.addView(sub);

        lastStandingsText = season + " team standings by " + metric.label + "\nRank\tTeam\t" + metric.label + "\tPA\tBBE\n";
        for (int i = 0; i < teams.size(); i++) {
            TeamStanding t = teams.get(i);
            boolean highlight = selectedTeam != null && t.team.key().equals(selectedTeam.key());
            card.addView(standingRow(i + 1, t.team.name, t.team.abbr, format(t.stats.get(metric.key), metric), t.stats, highlight));
            lastStandingsText += (i + 1) + "\t" + t.team.name + "\t" + format(t.stats.get(metric.key), metric) + "\t" + t.stats.pa + "\t" + t.stats.bbe + "\n";
        }
        resultsBox.setVisibility(View.GONE);
    }

    private View standingRow(int rank, String name, String meta, String value, Stats stats, boolean highlight) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.setBackground(rounded(highlight ? Color.rgb(231, 251, 248) : Color.WHITE, 13));

        TextView rankTv = text(String.valueOf(rank), 14, highlight ? TEAL_DARK : MUTED, true);
        rankTv.setGravity(Gravity.CENTER);
        row.addView(rankTv, new LinearLayout.LayoutParams(dp(34), -2));

        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        nameCol.addView(text(name, 14, INK, true));
        nameCol.addView(text(meta + " · " + fmtCount(stats.pa) + " PA · " + fmtCount(stats.bbe) + " BBE", 11, MUTED, false));
        row.addView(nameCol, new LinearLayout.LayoutParams(0, -2, 1));

        TextView val = text(value, 15, NAVY, true);
        row.addView(val);

        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(3), 0, dp(3));
        row.setLayoutParams(lp);
        return row;
    }

    private void copyCurrentTable() {
        String text;
        if (resultsBox.getVisibility() == View.VISIBLE && lastComparison != null) text = comparisonText(lastComparison);
        else text = lastStandingsText;
        if (text == null || text.trim().isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Statcast table", text));
        Toast.makeText(this, "Copied table", Toast.LENGTH_SHORT).show();
    }

    private String comparisonText(Comparison c) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.name).append(" · ").append(c.season).append(" Statcast comparison\n");
        sb.append("Metric\tSeason\tLeague Avg\t").append(c.thirdLabel).append("\tVs League\tVs ").append(c.thirdLabel).append("\n");
        for (Metric m : metrics) {
            if (!selectedMetricKeys.contains(m.key)) continue;
            Double season = c.seasonStats.get(m.key);
            Double league = c.leagueStats == null ? null : c.leagueStats.get(m.key);
            Double career = c.careerStats.get(m.key);
            sb.append(m.label).append('\t')
                    .append(format(season, m)).append('\t')
                    .append(format(league, m)).append('\t')
                    .append(format(career, m)).append('\t')
                    .append(signedFormat(diff(season, league), m)).append('\t')
                    .append(signedFormat(diff(season, career), m)).append('\n');
        }
        return sb.toString();
    }

    // Data loading -----------------------------------------------------------------------------

    private LoadedData fetchTeamsAndActivePlayers() throws Exception {
        String teamsText = httpGet("https://statsapi.mlb.com/api/v1/teams?sportId=1&activeStatus=Y");
        JSONArray teams = new JSONObject(teamsText).optJSONArray("teams");
        ArrayList<Team> teamList = new ArrayList<>();
        LinkedHashMap<Integer, Player> playersById = new LinkedHashMap<>();
        if (teams == null) return new LoadedData(teamList, new ArrayList<>());

        for (int i = 0; i < teams.length(); i++) {
            JSONObject teamJson = teams.getJSONObject(i);
            int teamId = teamJson.optInt("id");
            String name = teamJson.optString("name", "MLB");
            String abbr = teamJson.optString("abbreviation", teamJson.optString("teamCode", name));
            Team t = new Team(teamId, name, abbr);
            teamList.add(t);
            try {
                String rosterText = httpGet("https://statsapi.mlb.com/api/v1/teams/" + teamId + "/roster/active?hydrate=person");
                JSONArray roster = new JSONObject(rosterText).optJSONArray("roster");
                if (roster == null) continue;
                for (int j = 0; j < roster.length(); j++) {
                    JSONObject item = roster.getJSONObject(j);
                    JSONObject person = item.optJSONObject("person");
                    JSONObject pos = item.optJSONObject("position");
                    if (person == null) continue;
                    int id = person.optInt("id");
                    String fullName = person.optString("fullName", "");
                    String position = pos == null ? "" : pos.optString("abbreviation", pos.optString("name", ""));
                    if (id > 0 && !fullName.isEmpty()) playersById.put(id, new Player(id, fullName, name, abbr, position));
                }
            } catch (Exception ignored) {}
        }
        teamList.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        ArrayList<Player> players = new ArrayList<>(playersById.values());
        players.sort((a, b) -> a.fullName.compareToIgnoreCase(b.fullName));
        return new LoadedData(teamList, players);
    }

    private ArrayList<LeaderboardEntry> fetchLeaderboard(int season) throws Exception {
        ArrayList<LeaderboardEntry> cached = leaderboardCache.get(season);
        if (cached != null) return cached;
        String csv = httpGet(customLeaderboardUrl(season));
        List<Map<String, String>> rows = parseCsv(csv);
        if (rows.isEmpty()) throw new Exception("No Baseball Savant leaderboard rows returned for " + season + ".");
        ArrayList<LeaderboardEntry> entries = new ArrayList<>();
        for (Map<String, String> row : rows) {
            int id = intVal(pick(row, "player_id", "playerid", "player id", "batter", "entity_id"));
            String name = pickString(row, "player_name", "player name", "last_name, first_name", "name", "last_name first_name");
            if (name.isEmpty()) name = buildNameFromColumns(row);
            name = normalizePlayerName(name);
            String teamName = pickString(row, "team_name", "team name", "team", "team_name_alt");
            String teamAbbr = pickString(row, "team_abbrev", "team abbr", "team_abbreviation", "team_short", "team");
            if (id > 0) {
                Player p = playerById(id);
                if (p != null) {
                    if (teamName.isEmpty()) teamName = p.teamName;
                    if (teamAbbr.isEmpty()) teamAbbr = p.teamAbbr;
                    if (name.isEmpty()) name = p.fullName;
                }
            }
            Stats stats = statsFromLeaderboardRow(row);
            if ((!name.isEmpty() || id > 0) && (stats.pa > 0 || stats.bbe > 0 || stats.anyValue())) {
                entries.add(new LeaderboardEntry(id, name, teamName, teamAbbr, stats));
            }
        }
        leaderboardCache.put(season, entries);
        return entries;
    }

    private String customLeaderboardUrl(int season) throws Exception {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("year", String.valueOf(season));
        params.put("type", "batter");
        params.put("filter", "");
        params.put("min", "1");
        params.put("selections", "pa,xba,xslg,woba,xwoba,exit_velocity_avg,launch_angle_avg,sweet_spot_percent,barrel_batted_rate,hard_hit_percent,k_percent,bb_percent");
        params.put("sort", "xwoba");
        params.put("sortDir", "desc");
        params.put("csv", "true");
        return "https://baseballsavant.mlb.com/leaderboard/custom" + toQuery(params);
    }

    private Stats statsFromLeaderboardRow(Map<String, String> row) {
        Stats s = new Stats();
        s.pa = intVal(pick(row, "pa", "PA"));
        s.bbe = intVal(pick(row, "batted_ball", "batted_balls", "batted balls", "bbe", "Batted Balls", "batted_ball_events"));
        if (s.bbe <= 0) s.bbe = intVal(pick(row, "bip", "balls in play"));
        if (s.bbe <= 0 && s.pa > 0) s.bbe = Math.max(1, (int) Math.round(s.pa * 0.68));
        s.put("xBA", pick(row, "xba", "xBA"));
        s.put("xSLG", pick(row, "xslg", "xSLG"));
        s.put("xwOBA", pick(row, "xwoba", "xwOBA"));
        s.put("avgEV", pick(row, "exit_velocity_avg", "Avg EV (MPH)", "avg_ev", "avg exit velocity"));
        s.put("avgLA", pick(row, "launch_angle_avg", "Avg LA (°)", "avg_la", "avg launch angle"));
        s.put("hardHitPct", pick(row, "hard_hit_percent", "Hard Hit %", "hardhit_percent"));
        s.put("barrelPct", pick(row, "barrel_batted_rate", "Barrel%", "barrel_percent", "barrel_batted_rate"));
        s.put("sweetSpotPct", pick(row, "sweet_spot_percent", "LA Sweet-Spot %", "sweet spot %"));
        s.put("kPct", pick(row, "k_percent", "K%", "strikeout_percent", "k %"));
        s.put("bbPct", pick(row, "bb_percent", "BB%", "walk_percent", "bb %"));
        return s;
    }

    private Stats computeLeagueAverage(ArrayList<LeaderboardEntry> entries) {
        WeightedStatsBuilder b = new WeightedStatsBuilder(metrics);
        for (LeaderboardEntry e : entries) b.add(e.stats);
        return b.build();
    }

    private Stats fetchPlayerCareerStats(Player player, int throughSeason) {
        WeightedStatsBuilder b = new WeightedStatsBuilder(metrics);
        for (int y = STATCAST_START_YEAR; y <= throughSeason; y++) {
            try {
                LeaderboardEntry e = findPlayerEntry(fetchLeaderboard(y), player);
                if (e != null) b.add(e.stats);
            } catch (Exception ignored) {}
        }
        return b.build();
    }

    private Stats fetchTeamHistoryStats(Team team, int throughSeason) {
        WeightedStatsBuilder b = new WeightedStatsBuilder(metrics);
        for (int y = STATCAST_START_YEAR; y <= throughSeason; y++) {
            try {
                Stats s = aggregateTeamStats(fetchLeaderboard(y)).get(team.key());
                if (s != null) b.add(s);
            } catch (Exception ignored) {}
        }
        return b.build();
    }

    private Map<String, Stats> aggregateTeamStats(ArrayList<LeaderboardEntry> entries) {
        LinkedHashMap<String, WeightedStatsBuilder> builders = new LinkedHashMap<>();
        for (LeaderboardEntry e : entries) {
            String key = teamKeyFromEntry(e);
            if (key.isEmpty()) continue;
            WeightedStatsBuilder b = builders.get(key);
            if (b == null) {
                b = new WeightedStatsBuilder(metrics);
                builders.put(key, b);
            }
            b.add(e.stats);
        }
        LinkedHashMap<String, Stats> out = new LinkedHashMap<>();
        for (Map.Entry<String, WeightedStatsBuilder> e : builders.entrySet()) out.put(e.getKey(), e.getValue().build());
        return out;
    }

    private LeaderboardEntry findPlayerEntry(ArrayList<LeaderboardEntry> entries, Player player) {
        for (LeaderboardEntry e : entries) if (e.playerId > 0 && e.playerId == player.id) return e;
        String target = normalizeNameKey(player.fullName);
        for (LeaderboardEntry e : entries) if (normalizeNameKey(e.name).equals(target)) return e;
        return null;
    }

    private Player playerById(int id) {
        for (Player p : allPlayers) if (p.id == id) return p;
        return null;
    }

    private String teamKeyFromEntry(LeaderboardEntry e) {
        String abbr = safe(e.teamAbbr).toUpperCase(Locale.US).trim();
        if (!abbr.isEmpty()) {
            for (Team t : allTeams) if (abbr.equals(t.abbr.toUpperCase(Locale.US))) return t.key();
        }
        String name = normalizeNameKey(e.teamName);
        if (!name.isEmpty()) {
            for (Team t : allTeams) if (normalizeNameKey(t.name).equals(name)) return t.key();
        }
        return abbr;
    }

    private void sortEntries(ArrayList<LeaderboardEntry> entries, Metric metric) {
        entries.sort((a, b) -> compareMetricValues(a.stats.get(metric.key), b.stats.get(metric.key), metric));
    }

    private int compareMetricValues(Double av, Double bv, Metric metric) {
        if (av == null && bv == null) return 0;
        if (av == null) return 1;
        if (bv == null) return -1;
        int cmp = Double.compare(bv, av); // default: high to low
        if (metric.higherGood != null && !metric.higherGood) cmp = Double.compare(av, bv);
        return cmp;
    }

    private void loadPlayerImage(int playerId, ImageView imageView) {
        if (playerId <= 0) return;
        String url = "https://img.mlbstatic.com/mlb-photos/image/upload/w_300,q_100/v1/people/" + playerId + "/headshot/current";
        Bitmap cached = imageCache.get(url);
        if (cached != null) { imageView.setImageBitmap(cached); return; }
        imageView.setImageDrawable(rounded(Color.rgb(235, 241, 248), 24));
        io.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 Statcast Compare Android");
                Bitmap bitmap = BitmapFactory.decodeStream(conn.getInputStream());
                if (bitmap != null) {
                    imageCache.put(url, bitmap);
                    main.post(() -> imageView.setImageBitmap(bitmap));
                }
            } catch (Exception ignored) {}
        });
    }

    private String httpGet(String urlString) throws Exception {
        String cached = textCache.get(urlString);
        if (cached != null) return cached;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(25000);
        conn.setReadTimeout(50000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 Statcast Compare Android");
        conn.setRequestProperty("Accept", "text/csv,text/plain,application/json,text/html,*/*");
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        String text = sb.toString();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        textCache.put(urlString, text);
        return text;
    }

    // CSV/format helpers -----------------------------------------------------------------------

    private List<Map<String, String>> parseCsv(String text) {
        ArrayList<ArrayList<String>> rows = new ArrayList<>();
        ArrayList<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
            if (c == '"') {
                if (inQuotes && next == '"') { field.append('"'); i++; }
                else inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                row.add(field.toString()); field.setLength(0);
            } else if ((c == '\n' || c == '\r') && !inQuotes) {
                if (c == '\r' && next == '\n') i++;
                row.add(field.toString()); field.setLength(0);
                boolean nonEmpty = false; for (String v : row) if (!v.isEmpty()) { nonEmpty = true; break; }
                if (nonEmpty) rows.add(row);
                row = new ArrayList<>();
            } else field.append(c);
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        ArrayList<Map<String, String>> out = new ArrayList<>();
        if (rows.size() < 2) return out;
        ArrayList<String> headers = rows.get(0);
        for (int r = 1; r < rows.size(); r++) {
            HashMap<String, String> map = new HashMap<>();
            ArrayList<String> vals = rows.get(r);
            for (int h = 0; h < headers.size(); h++) map.put(headers.get(h).trim(), h < vals.size() ? vals.get(h) : "");
            out.add(map);
        }
        return out;
    }

    private String toQuery(LinkedHashMap<String, String> params) throws Exception {
        StringBuilder sb = new StringBuilder("?");
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
            first = false;
        }
        return sb.toString();
    }

    private String enc(String s) throws Exception { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
    private String safe(String s) { return s == null ? "" : s; }
    private Double pick(Map<String, String> row, String... names) {
        for (String n : names) if (row.containsKey(n)) return num(row.get(n));
        HashMap<String, String> lowerMap = new HashMap<>();
        for (String k : row.keySet()) lowerMap.put(k.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", ""), k);
        for (String n : names) {
            String k = lowerMap.get(n.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", ""));
            if (k != null) return num(row.get(k));
        }
        return null;
    }
    private String pickString(Map<String, String> row, String... names) {
        for (String n : names) if (row.containsKey(n) && row.get(n) != null && !row.get(n).trim().isEmpty()) return row.get(n).trim();
        HashMap<String, String> lowerMap = new HashMap<>();
        for (String k : row.keySet()) lowerMap.put(k.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", ""), k);
        for (String n : names) {
            String k = lowerMap.get(n.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", ""));
            if (k != null && row.get(k) != null && !row.get(k).trim().isEmpty()) return row.get(k).trim();
        }
        return "";
    }
    private String buildNameFromColumns(Map<String, String> row) {
        String first = pickString(row, "first_name", "first name");
        String last = pickString(row, "last_name", "last name");
        return (first + " " + last).trim();
    }
    private String normalizePlayerName(String name) {
        String n = safe(name).trim();
        if (n.contains(",")) {
            String[] parts = n.split(",");
            if (parts.length >= 2) n = parts[1].trim() + " " + parts[0].trim();
        }
        return n.replaceAll("\\s+", " ").trim();
    }
    private String normalizeNameKey(String value) {
        return safe(value).toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
    }
    private Double num(String value) {
        if (value == null) return null;
        String cleaned = value.replace("%", "").replace(",", "").trim();
        if (cleaned.isEmpty() || cleaned.equals("-")) return null;
        try { return Double.parseDouble(cleaned); } catch (Exception e) { return null; }
    }
    private int intVal(Double d) { return d == null || Double.isNaN(d) ? 0 : (int) Math.round(d); }
    private Double diff(Double a, Double b) { return a == null || b == null ? null : a - b; }
    private String fmtCount(int v) { return String.format(Locale.US, "%,d", v); }

    private String format(Double v, Metric m) {
        if (v == null || Double.isNaN(v)) return "—";
        String pattern = m.decimals == 3 ? "0.000" : "0.0";
        DecimalFormat df = new DecimalFormat(pattern);
        return df.format(v) + m.unit;
    }
    private String signedFormat(Double v, Metric m) {
        if (v == null || Double.isNaN(v)) return "—";
        return (v > 0 ? "+" : "") + format(v, m);
    }
    private double[] scaleValues(Double[] values, Metric m) {
        ArrayList<Double> valid = new ArrayList<>();
        for (Double v : values) if (v != null && !Double.isNaN(v)) valid.add(v);
        double[] out = new double[values.length];
        if (valid.isEmpty()) return out;
        double min = Collections.min(valid), max = Collections.max(valid);
        if (max == min) { for (int i = 0; i < out.length; i++) out[i] = values[i] == null ? 0 : 70; return out; }
        double pad = m.type.equals("expected") ? 0.020 : m.type.equals("rate") ? 2.0 : 0.5;
        double lo = min - pad, hi = max + pad;
        for (int i = 0; i < values.length; i++) {
            Double v = values[i];
            if (v == null || Double.isNaN(v)) out[i] = 0;
            else out[i] = Math.max(7, Math.min(100, ((v - lo) / (hi - lo)) * 100));
        }
        return out;
    }

    // UI helpers -------------------------------------------------------------------------------

    private void setBusy(boolean busy, String message) {
        loading.setVisibility(busy ? View.VISIBLE : View.GONE);
        compareButton.setEnabled(!busy);
        standingsButton.setEnabled(!busy);
        if (message != null) statusView.setText(message);
    }
    private void showError(String message) {
        errorView.setText(message == null ? "" : message);
        errorView.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }
    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }
    private TextView text(String value, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }
    private LinearLayout verticalCard(int radius, int[] gradientColors) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(gradientColors == null ? rounded(CARD, radius) : roundedGradient(gradientColors, radius));
        v.setElevation(dp(2));
        return v;
    }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams weightLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(radius));
        return gd;
    }
    private GradientDrawable roundedStroke(int color, int stroke, int radius, int strokeDp) {
        GradientDrawable gd = rounded(color, radius);
        gd.setStroke(dp(strokeDp), stroke);
        return gd;
    }
    private GradientDrawable roundedGradient(int[] colors, int radius) {
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        gd.setCornerRadius(dp(radius));
        return gd;
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    static class LoadedData {
        final ArrayList<Team> teams; final ArrayList<Player> players;
        LoadedData(ArrayList<Team> teams, ArrayList<Player> players) { this.teams = teams; this.players = players; }
    }
    static class Team {
        final int id; final String name; final String abbr;
        Team(int id, String name, String abbr) { this.id = id; this.name = name; this.abbr = abbr == null || abbr.isEmpty() ? name : abbr; }
        String key() { return abbr.toUpperCase(Locale.US); }
    }
    static class Player {
        final int id; final String fullName; final String teamName; final String teamAbbr; final String position;
        Player(int id, String fullName, String teamName, String teamAbbr, String position) { this.id = id; this.fullName = fullName; this.teamName = teamName; this.teamAbbr = teamAbbr; this.position = position; }
    }
    static class LeaderboardEntry {
        final int playerId; final String name; final String teamName; final String teamAbbr; final Stats stats;
        LeaderboardEntry(int playerId, String name, String teamName, String teamAbbr, Stats stats) { this.playerId = playerId; this.name = name; this.teamName = teamName; this.teamAbbr = teamAbbr; this.stats = stats; }
        String teamAbbrOrName() { return teamAbbr == null || teamAbbr.isEmpty() ? teamName : teamAbbr; }
    }
    static class TeamStanding {
        final Team team; final Stats stats;
        TeamStanding(Team team, Stats stats) { this.team = team; this.stats = stats; }
    }
    static class Stats {
        int pa = 0; int bbe = 0; final Map<String, Double> vals = new HashMap<>();
        void put(String key, Double value) { vals.put(key, value); }
        Double get(String key) { return vals.get(key); }
        boolean anyValue() { for (Double d : vals.values()) if (d != null && !Double.isNaN(d)) return true; return false; }
    }
    static class Metric {
        final String key, label, unit, type, group; final int decimals; final Boolean higherGood;
        Metric(String key, String label, String unit, int decimals, Boolean higherGood, String type, String group) {
            this.key = key; this.label = label; this.unit = unit; this.decimals = decimals; this.higherGood = higherGood; this.type = type; this.group = group;
        }
    }
    static class Comparison {
        final boolean isTeam; final String name, meta; final int mlbId, season; final Stats seasonStats, careerStats, leagueStats; final Date updated; final String thirdLabel; final Player player;
        Comparison(boolean isTeam, String name, String meta, String role, int mlbId, int season, Stats seasonStats, Stats careerStats, Stats leagueStats, Date updated, String thirdLabel, Player player) {
            this.isTeam = isTeam; this.name = name; this.meta = meta + (role == null || role.isEmpty() ? "" : " · " + role); this.mlbId = mlbId; this.season = season; this.seasonStats = seasonStats; this.careerStats = careerStats; this.leagueStats = leagueStats; this.updated = updated; this.thirdLabel = thirdLabel; this.player = player;
        }
        String thirdLabelShort() { return isTeam ? "Hist" : "Career"; }
    }
    static class WeightedStatsBuilder {
        final Metric[] metrics; int pa = 0; int bbe = 0; final Map<String, Double> sums = new HashMap<>(); final Map<String, Double> weights = new HashMap<>();
        WeightedStatsBuilder(Metric[] metrics) { this.metrics = metrics; }
        void add(Stats s) {
            if (s == null) return;
            pa += s.pa; bbe += s.bbe;
            for (Metric m : metrics) {
                Double val = s.get(m.key);
                if (val == null || Double.isNaN(val)) continue;
                double w = (m.type.equals("expected") || m.key.equals("kPct") || m.key.equals("bbPct")) ? s.pa : s.bbe;
                if (w <= 0) w = s.pa > 0 ? s.pa : s.bbe;
                if (w <= 0) continue;
                sums.put(m.key, sums.getOrDefault(m.key, 0.0) + val * w);
                weights.put(m.key, weights.getOrDefault(m.key, 0.0) + w);
            }
        }
        Stats build() {
            Stats s = new Stats(); s.pa = pa; s.bbe = bbe;
            for (Metric m : metrics) {
                Double w = weights.get(m.key);
                s.put(m.key, w == null || w <= 0 ? null : sums.get(m.key) / w);
            }
            return s;
        }
    }
}
