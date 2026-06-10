package com.folettary.statcastcompare;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.SweepGradient;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends Activity {
    private enum StatScope { HIT_ONLY, PITCH_ONLY, BOTH }
    private static final int STATCAST_START_YEAR = 2015;
    private static boolean isDark = false;          // dark mode toggle – static survives recreate()

    // Mutable color fields – reassigned in initColors() on each dark/light switch
    private int NAVY, NAVY_2, TEAL, TEAL_DARK, SALMON, AMBER, BG, INK, MUTED, LINE, CARD;

    // v29: Font weight ladder – initialized in initColors()
    private Typeface tfRegular, tfMedium, tfBold;

    private final ExecutorService io = Executors.newFixedThreadPool(8);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, String> textCache = Collections.synchronizedMap(new HashMap<>());
    private LruCache<String, Bitmap> imageCache;
    private final Map<String, ArrayList<BitmapCallback>> imageWaiters = Collections.synchronizedMap(new HashMap<>());
    // v137: Cache prepared/tinted team logos separately. Padres/Dodgers all-stats pages
    // were lagging because every visible stat row re-tinted the same SD/LAD bitmap on
    // the UI thread. This cache + waiter map prepares one shared bitmap off-thread.
    private final Map<String, ArrayList<BitmapCallback>> preparedTeamLogoWaiters = Collections.synchronizedMap(new HashMap<>());
    private final Map<Integer, ArrayList<LeaderboardEntry>> leaderboardCache = Collections.synchronizedMap(new HashMap<>());
    private final Map<Integer, ArrayList<LeaderboardEntry>> pitchingLeaderboardCache = Collections.synchronizedMap(new HashMap<>());
    // v139: align stat browsing categories with comparison presets.
    // v138: cache full 30-team season stat maps so team ranks/quality percentiles are
    // computed against the league instead of the partial player-leaderboard pool.
    private final Map<String, Map<String, Stats>> leagueTeamStatsCache = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, ArrayList<GameLogEntry>> gameLogCache = Collections.synchronizedMap(new HashMap<>());

    private LinearLayout root;
    private LinearLayout form;
    private Button playerModeButton;
    private Button teamModeButton;
    private Button singleViewButton;
    private Button headToHeadButton;
    private Button expectedViewButton;
    private Button stickyCompareButton;
    private Button stickyProfileButton;
    private Button stickyRankingsButton;
    private Button teamPickerButton;
    private Button compareTeamPickerButton;
    private Button rankMetricPickerButton;
    private EditText searchInput;
    private ListView suggestionsList;
    private Spinner teamSpinner;
    private LinearLayout teamChipRow;
    private LinearLayout compareTeamChipRow;
    private TextView compareSelectorLabel;
    private EditText compareSearchInput;
    private ListView compareSuggestionsList;
    private Spinner compareTeamSpinner;
    private LinearLayout comparePreviewBox;
    private Spinner seasonSpinner;
    private Spinner rankMetricSpinner;
    private LinearLayout seasonChipRow;
    private LinearLayout rankMetricChipRow;
    private LinearLayout rankControlContainer;
    private final ArrayList<TextView> seasonChips = new ArrayList<>();
    private final ArrayList<TextView> rankMetricChips = new ArrayList<>();
    private int selectedSeasonValue = Calendar.getInstance().get(Calendar.YEAR);
    private int selectedRankMetricPosition = -1; // -1 = All selected stats
    private Button metricPickerButton;
    private LinearLayout selectedPreviewBox;
    private Button compareButton;
    private Button standingsButton;
    private ProgressBar loading;
    private View skeletonView = null;       // v29: shimmer skeleton during profile loads
    private TextView statusView;
    private TextView errorView;
    private Button retryButton;
    private Button shareButton;
    private LinearLayout filterBox;
    private LinearLayout resultsBox;
    private LinearLayout headerBox;
    private LinearLayout metricBox;
    private LinearLayout standingsBox;
    private LinearLayout recentsBox;
    private Button copyButton;
    private TextView liveBadge;
    private TextView primarySelectorLabel;
    private TextView typeModeLabel;
    private LinearLayout profileToolsShell;
    private LinearLayout controlsCard;
    private ScrollView mainScroll;
    private LinearLayout homeBox;
    private FrameLayout homeMatchupPreview;
    private FrameLayout homeCircleAFrame;
    private FrameLayout homeCircleBFrame;
    private TextView homeVsBadge;
    private TextView homeCreateButton;
    private TextView homeStatsButton;
    private ImageView homeCircleAImage;
    private ImageView homeCircleBImage;
    private View homeCircleAGlow;
    private View homeCircleBGlow;
    private TextView homeCircleAActionBadge;
    private TextView homeCircleBActionBadge;
    private TextView homeCircleAClearBadge;
    private TextView homeCircleBClearBadge;
    private HomeEnergyView homeEnergyView;
    private TextView homeCircleAText;
    private TextView homeCircleBText;
    private TextView homeActiveSideBadge;
    private TextView homeSelectAText;
    private TextView homeSelectBText;
    private TextView homeModePlayersBtn;
    private TextView homeModeTeamsBtn;
    private LinearLayout homeInlineSelectorCard;
    private TextView homeInlineSelectorTitle;
    private EditText homeInlineSearchInput;
    private ListView homeInlineSuggestionList;
    private TextView homeInlineHint;
    private AlertDialog homePickerDialog;
    private boolean homeInlineSecondary = false;
    private boolean homeInlineTeamMode = false;
    private final ArrayList<Player> homeInlinePlayers = new ArrayList<>();
    private final ArrayList<Team> homeInlineTeams = new ArrayList<>();
    private Player homePlayerA;
    private Player homePlayerB;
    private Team homeTeamA;
    private Team homeTeamB;
    private PlayerSuggestionAdapter homeInlinePlayerAdapter;
    private TeamPickerAdapter homeInlineTeamAdapterA;
    private TeamPickerAdapter homeInlineTeamAdapterB;
    private LinearLayout bottomHomeTab;
    private LinearLayout bottomMatchupTab;
    private LinearLayout bottomSearchTab;
    private LinearLayout bottomRankingsTab;
    private TextView bottomHomeIcon;
    private TextView bottomMatchupIcon;
    private TextView bottomSearchIcon;
    private TextView bottomRankingsIcon;
    private TextView bottomHomeLabel;
    private TextView bottomMatchupLabel;
    private TextView bottomSearchLabel;
    private TextView bottomRankingsLabel;
    private View bottomHomeLine;
    private View bottomMatchupLine;
    private View bottomSearchLine;
    private View bottomRankingsLine;
    private View bottomNavHost;
    private static final int TAB_HOME = 0;
    private static final int TAB_MATCHUP = 1;
    private static final int TAB_PROFILE = 2;
    private static final int TAB_RANKINGS = 3;
    private int activePrimaryTab = TAB_HOME;
    private final Map<String, String> metricTrendModes = new HashMap<>();
    private final ArrayList<Metric> rankMetrics = new ArrayList<>();

    private final ArrayList<Player> allPlayers = new ArrayList<>();
    private final ArrayList<Player> filteredPlayers = new ArrayList<>();
    private final ArrayList<Player> filteredComparePlayers = new ArrayList<>();
    private final ArrayList<Team> allTeams = new ArrayList<>();
    private PlayerSuggestionAdapter suggestionsAdapter;
    private PlayerSuggestionAdapter compareSuggestionsAdapter;
    private ArrayAdapter<String> teamAdapter;
    private ArrayAdapter<String> compareTeamAdapter;
    private Player selectedPlayer;
    private Player comparePlayer;
    private Team selectedTeam;
    private Team compareTeam;
    private boolean teamMode = false;
    private boolean headToHeadMode = true; // v69: Compare is the primary/default path
    private boolean expectedMode = false;
    private Comparison lastComparison;
    private HeadToHeadComparison lastHeadToHead;
    private String lastStandingsText = "";
    private boolean rankingsModeActive = false;
    private boolean generalRankingsMode = false;
    private boolean suppressNextAutoScroll = false;
    private String trendWindowMode = "season";

    private final LinkedHashSet<String> selectedMetricKeys = new LinkedHashSet<>();
    // v119: stats shown on screen and stats shown in the hero Key Stat Edge card are separate controls.
    private final LinkedHashSet<String> keyEdgeMetricKeys = new LinkedHashSet<>();
    // v132/v134: one sticky comparison preset is preserved across player/team changes until the
    // user explicitly changes presets or manually customizes the stat set.
    private String activeComparisonPreset = "recommended";
    private boolean metricsManuallyCustomized = false;
    // v134: stat picker browsing is separate from applying full-card comparison presets.
    private String activeStatCategory = "all";
    // v143: one Lens drives both the scored matchup card and the default rows shown below.
    // Advanced escape hatch: temporarily show all available rows while keeping the card scored by the Lens.
    private boolean showAllResultsStats = false;
    private String activeResultsStatCategory = "all";
    private final Map<String, CheckBox> metricChecks = new LinkedHashMap<>();
    private boolean updatingMetricChecks = false;

    private final Metric[] metrics = new Metric[] {
            // Hitter standard stats
            new Metric("avg", "AVG", "", 3, true, "standard", "Standard Hitting", "hit"),
            new Metric("obp", "OBP", "", 3, true, "standard", "Standard Hitting", "hit"),
            new Metric("slg", "SLG", "", 3, true, "standard", "Standard Hitting", "hit"),
            new Metric("ops", "OPS", "", 3, true, "standard", "Standard Hitting", "hit"),
            new Metric("iso", "ISO", "", 3, true, "standard", "Standard Hitting", "hit"),
            new Metric("babip", "BABIP", "", 3, null, "context", "Standard Hitting", "hit"),
            new Metric("h", "H", "", 0, true, "count", "Volume Hitting", "hit"),
            new Metric("doubles", "2B", "", 0, true, "count", "Volume Hitting", "hit"),
            new Metric("triples", "3B", "", 0, true, "count", "Volume Hitting", "hit"),
            new Metric("hr", "HR", "", 0, true, "count", "Volume Hitting", "hit"),
            new Metric("xbh", "XBH", "", 0, true, "count", "Volume Hitting", "hit"),
            new Metric("rbi", "RBI", "", 0, true, "count", "Volume Hitting", "hit"),
            new Metric("r", "Runs", "", 0, true, "count", "Volume Hitting", "hit"),
            new Metric("sb", "SB", "", 0, true, "count", "Volume Hitting", "hit"),
            new Metric("bb", "BB", "", 0, true, "count", "Volume Hitting", "hit"),
            new Metric("so", "SO", "", 0, false, "count", "Volume Hitting", "hit"),
            new Metric("tb", "TB", "", 0, true, "count", "Volume Hitting", "hit"),

            // Hitter Statcast / expected stats
            new Metric("wOBA", "wOBA", "", 3, true, "expected", "Expected Stats", "hit"),
            new Metric("xwOBA", "xwOBA", "", 3, true, "expected", "Expected Stats", "hit"),
            new Metric("luck", "Luck", "", 3, null, "luck", "Expected Stats", "hit"),
            new Metric("xBA", "xBA", "", 3, true, "expected", "Expected Stats", "hit"),
            new Metric("xOBP", "xOBP", "", 3, true, "expected", "Expected Stats", "hit"),
            new Metric("xSLG", "xSLG", "", 3, true, "expected", "Expected Stats", "hit"),
            new Metric("xISO", "xISO", "", 3, true, "expected", "Expected Stats", "hit"),
            new Metric("wOBAcon", "wOBAcon", "", 3, true, "expected", "Expected Stats", "hit"),
            new Metric("xwOBAcon", "xwOBAcon", "", 3, true, "expected", "Expected Stats", "hit"),

            // Hitter contact / approach
            new Metric("avgEV", "Avg EV", " mph", 1, true, "contact", "Contact Quality", "hit"),
            new Metric("avgLA", "Launch Angle", "°", 1, null, "target", "Contact Quality", "hit"),
            new Metric("hardHitPct", "Hard-Hit %", "%", 1, true, "rate", "Contact Quality", "hit"),
            new Metric("barrelPct", "Barrel %", "%", 1, true, "rate", "Contact Quality", "hit"),
            new Metric("sweetSpotPct", "Sweet-Spot %", "%", 1, true, "rate", "Contact Quality", "hit"),
            new Metric("gbPct", "GB %", "%", 1, null, "context", "Batted Ball Profile", "hit"),
            new Metric("fbPct", "FB %", "%", 1, null, "context", "Batted Ball Profile", "hit"),
            new Metric("ldPct", "LD %", "%", 1, true, "rate", "Batted Ball Profile", "hit"),
            new Metric("pullPct", "Pull %", "%", 1, null, "context", "Batted Ball Profile", "hit"),
            new Metric("oppoPct", "Oppo %", "%", 1, null, "context", "Batted Ball Profile", "hit"),
            new Metric("kPct", "K %", "%", 1, false, "rate", "Plate Discipline", "hit"),
            new Metric("bbPct", "BB %", "%", 1, true, "rate", "Plate Discipline", "hit"),
            new Metric("bbMinusKPct", "BB-K %", "%", 1, true, "rate", "Plate Discipline", "hit"),
            new Metric("whiffPct", "Whiff %", "%", 1, false, "rate", "Plate Discipline", "hit"),
            new Metric("swingPct", "Swing %", "%", 1, null, "context", "Plate Discipline", "hit"),
            new Metric("chasePct", "Chase %", "%", 1, false, "rate", "Plate Discipline", "hit"),
            new Metric("zoneContactPct", "Zone Contact %", "%", 1, true, "rate", "Plate Discipline", "hit"),
            new Metric("sprintSpeed", "Sprint Speed", " ft/s", 1, true, "rate", "Speed / Baserunning", "hit"),

            // Pitching standard stats
            new Metric("era", "ERA", "", 2, false, "pitching", "Standard Pitching", "pitch"),
            new Metric("whip", "WHIP", "", 2, false, "pitching", "Standard Pitching", "pitch"),
            new Metric("k9", "K/9", "", 1, true, "pitching", "Standard Pitching", "pitch"),
            new Metric("bb9", "BB/9", "", 1, false, "pitching", "Standard Pitching", "pitch"),
            new Metric("kbb", "K/BB", "", 2, true, "pitching", "Standard Pitching", "pitch"),
            new Metric("pitchKPct", "K %", "%", 1, true, "rate", "Command / Swing-Miss", "pitch"),
            new Metric("pitchBBPct", "BB %", "%", 1, false, "rate", "Command / Swing-Miss", "pitch"),
            new Metric("pitchKMinusBBPct", "K-BB %", "%", 1, true, "rate", "Command / Swing-Miss", "pitch"),
            new Metric("pitchK", "SO", "", 0, true, "count", "Volume Pitching", "pitch"),
            new Metric("pitchBB", "BB", "", 0, false, "count", "Volume Pitching", "pitch"),
            new Metric("saves", "SV", "", 0, true, "count", "Volume Pitching", "pitch"),
            new Metric("ip", "IP", "", 1, true, "pitching", "Volume Pitching", "pitch"),
            new Metric("pHitsAllowed", "H Allowed", "", 0, false, "count", "Standard Pitching", "pitch"),
            new Metric("pHrAllowed", "HR Allowed", "", 0, false, "count", "Standard Pitching", "pitch"),
            new Metric("pOppAvg", "Opp AVG", "", 3, false, "pitching", "Standard Pitching", "pitch"),
            new Metric("pOppOps", "Opp OPS", "", 3, false, "pitching", "Standard Pitching", "pitch"),

            // Pitching Statcast / allowed quality
            new Metric("pxBA", "xBA Allowed", "", 3, false, "expected", "Expected Allowed", "pitch"),
            new Metric("pxSLG", "xSLG Allowed", "", 3, false, "expected", "Expected Allowed", "pitch"),
            new Metric("pwOBA", "wOBA Allowed", "", 3, false, "expected", "Expected Allowed", "pitch"),
            new Metric("pxwOBA", "xwOBA Allowed", "", 3, false, "expected", "Expected Allowed", "pitch"),
            new Metric("pAvgEV", "Avg EV Allowed", " mph", 1, false, "contact", "Power/Contact Allowed", "pitch"),
            new Metric("pHardHitPct", "Hard-Hit % Allowed", "%", 1, false, "rate", "Power/Contact Allowed", "pitch"),
            new Metric("pBarrelPct", "Barrel % Allowed", "%", 1, false, "rate", "Power/Contact Allowed", "pitch"),
            new Metric("pWhiffPct", "Whiff %", "%", 1, true, "rate", "Command / Swing-Miss", "pitch"),
            new Metric("pChasePct", "Chase %", "%", 1, true, "rate", "Command / Swing-Miss", "pitch"),
            new Metric("pFirstStrikePct", "First Strike %", "%", 1, true, "rate", "Command / Swing-Miss", "pitch"),
            new Metric("pZonePct", "Zone %", "%", 1, true, "rate", "Command / Swing-Miss", "pitch"),
            new Metric("pGbPct", "GB % Allowed", "%", 1, true, "rate", "Power/Contact Allowed", "pitch"),
            new Metric("pFbPct", "FB % Allowed", "%", 1, false, "rate", "Power/Contact Allowed", "pitch"),
            new Metric("pLdPct", "LD % Allowed", "%", 1, false, "rate", "Power/Contact Allowed", "pitch"),

            // Team-only explicit stats (no duplicate player/pitcher labels in team mode)
            new Metric("teamWinPct", "Win %", "", 3, true, "team", "Team Results", "team"),
            new Metric("teamRunsScored", "Runs Scored", "", 0, true, "count", "Team Results", "team"),
            new Metric("teamRunsAllowed", "Runs Allowed", "", 0, false, "count", "Team Results", "team"),
            new Metric("teamRunDiff", "Run Diff", "", 0, true, "team", "Team Results", "team"),
            new Metric("teamRPG", "Runs/Game", "", 2, true, "team", "Team Results", "team"),
            new Metric("teamRAPG", "RA/Game", "", 2, false, "team", "Team Results", "team"),

            new Metric("teamAVG", "AVG", "", 3, true, "standard", "Team Offense", "team"),
            new Metric("teamOBP", "OBP", "", 3, true, "standard", "Team Offense", "team"),
            new Metric("teamSLG", "SLG", "", 3, true, "standard", "Team Offense", "team"),
            new Metric("teamOPS", "OPS", "", 3, true, "standard", "Team Offense", "team"),
            new Metric("teamISO", "ISO", "", 3, true, "standard", "Team Offense", "team"),
            new Metric("teamBABIP", "BABIP", "", 3, null, "context", "Team Offense", "team"),
            new Metric("teamHits", "Hits", "", 0, true, "count", "Team Offense", "team"),
            new Metric("teamDoubles", "2B", "", 0, true, "count", "Team Offense", "team"),
            new Metric("teamTriples", "3B", "", 0, true, "count", "Team Offense", "team"),
            new Metric("teamHR", "HR", "", 0, true, "count", "Team Offense", "team"),
            new Metric("teamXbh", "XBH", "", 0, true, "count", "Team Offense", "team"),
            new Metric("teamRBI", "RBI", "", 0, true, "count", "Team Offense", "team"),
            new Metric("teamSB", "SB", "", 0, true, "count", "Team Offense", "team"),
            new Metric("teamWalks", "Walks", "", 0, true, "count", "Team Offense", "team"),
            new Metric("teamStrikeouts", "Batting SO", "", 0, false, "count", "Team Offense", "team"),
            new Metric("teamTB", "TB", "", 0, true, "count", "Team Offense", "team"),
            new Metric("teamKPct", "Batting K %", "%", 1, false, "rate", "Team Discipline", "team"),
            new Metric("teamBBPct", "Batting BB %", "%", 1, true, "rate", "Team Discipline", "team"),
            new Metric("teamBBMinusKPct", "Batting BB-K %", "%", 1, true, "rate", "Team Discipline", "team"),
            new Metric("teamWhiffPct", "Batting Whiff %", "%", 1, false, "rate", "Team Discipline", "team"),
            new Metric("teamSwingPct", "Batting Swing %", "%", 1, null, "context", "Team Discipline", "team"),
            new Metric("teamChasePct", "Batting Chase %", "%", 1, false, "rate", "Team Discipline", "team"),
            new Metric("teamZoneContactPct", "Zone Contact %", "%", 1, true, "rate", "Team Discipline", "team"),
            new Metric("teamWOBA", "wOBA", "", 3, true, "expected", "Team Expected", "team"),
            new Metric("teamXWOBA", "xwOBA", "", 3, true, "expected", "Team Expected", "team"),
            new Metric("teamXBA", "xBA", "", 3, true, "expected", "Team Expected", "team"),
            new Metric("teamXSLG", "xSLG", "", 3, true, "expected", "Team Expected", "team"),
            new Metric("teamXOBP", "xOBP", "", 3, true, "expected", "Team Expected", "team"),
            new Metric("teamXISO", "xISO", "", 3, true, "expected", "Team Expected", "team"),
            new Metric("teamAvgEV", "Avg EV", " mph", 1, true, "contact", "Team Contact", "team"),
            new Metric("teamHardHitPct", "Hard-Hit %", "%", 1, true, "rate", "Team Contact", "team"),
            new Metric("teamBarrelPct", "Barrel %", "%", 1, true, "rate", "Team Contact", "team"),
            new Metric("teamSweetSpotPct", "Sweet-Spot %", "%", 1, true, "rate", "Team Contact", "team"),
            new Metric("teamGbPct", "GB %", "%", 1, null, "context", "Team Batted Ball", "team"),
            new Metric("teamFbPct", "FB %", "%", 1, null, "context", "Team Batted Ball", "team"),
            new Metric("teamLdPct", "LD %", "%", 1, true, "rate", "Team Batted Ball", "team"),
            new Metric("teamPullPct", "Pull %", "%", 1, null, "context", "Team Batted Ball", "team"),
            new Metric("teamOppoPct", "Oppo %", "%", 1, null, "context", "Team Batted Ball", "team"),

            new Metric("teamERA", "ERA", "", 2, false, "pitching", "Team Pitching", "team"),
            new Metric("teamWHIP", "WHIP", "", 2, false, "pitching", "Team Pitching", "team"),
            new Metric("teamK9", "K/9", "", 1, true, "pitching", "Team Pitching", "team"),
            new Metric("teamBB9", "BB/9", "", 1, false, "pitching", "Team Pitching", "team"),
            new Metric("teamKBB", "K/BB", "", 2, true, "pitching", "Team Pitching", "team"),
            new Metric("teamPitchKPct", "Pitching K %", "%", 1, true, "rate", "Team Pitching", "team"),
            new Metric("teamPitchBBPct", "Pitching BB %", "%", 1, false, "rate", "Team Pitching", "team"),
            new Metric("teamPitchKMinusBBPct", "Pitching K-BB %", "%", 1, true, "rate", "Team Pitching", "team"),
            new Metric("teamPitchStrikeouts", "Pitching K", "", 0, true, "count", "Team Pitching", "team"),
            new Metric("teamHitsAllowed", "Hits Allowed", "", 0, false, "count", "Team Pitching", "team"),
            new Metric("teamHrAllowed", "HR Allowed", "", 0, false, "count", "Team Pitching", "team"),
            new Metric("teamWalksAllowed", "Walks Allowed", "", 0, false, "count", "Team Pitching", "team"),
            new Metric("teamOppAvg", "Opponent AVG", "", 3, false, "team", "Team Pitching", "team"),
            new Metric("teamOppOps", "Opponent OPS", "", 3, false, "team", "Team Pitching", "team"),
            new Metric("teamPXBA", "xBA Allowed", "", 3, false, "expected", "Team Expected Allowed", "team"),
            new Metric("teamPXSLG", "xSLG Allowed", "", 3, false, "expected", "Team Expected Allowed", "team"),
            new Metric("teamPWOBA", "wOBA Allowed", "", 3, false, "expected", "Team Expected Allowed", "team"),
            new Metric("teamPXWOBA", "xwOBA Allowed", "", 3, false, "expected", "Team Expected Allowed", "team"),
            new Metric("teamPAvgEV", "Avg EV Allowed", " mph", 1, false, "contact", "Team Contact Allowed", "team"),
            new Metric("teamPHardHitPct", "Hard-Hit % Allowed", "%", 1, false, "rate", "Team Contact Allowed", "team"),
            new Metric("teamPBarrelPct", "Barrel % Allowed", "%", 1, false, "rate", "Team Contact Allowed", "team"),
            new Metric("teamPWhiffPct", "Pitching Whiff %", "%", 1, true, "rate", "Team Pitching", "team"),
            new Metric("teamPChasePct", "Pitching Chase %", "%", 1, true, "rate", "Team Pitching", "team"),
            new Metric("teamPFirstStrikePct", "First Strike %", "%", 1, true, "rate", "Team Pitching", "team"),
            new Metric("teamPZonePct", "Zone %", "%", 1, true, "rate", "Team Pitching", "team"),
            new Metric("teamPGbPct", "GB % Allowed", "%", 1, true, "rate", "Team Contact Allowed", "team"),
            new Metric("teamPFbPct", "FB % Allowed", "%", 1, false, "rate", "Team Contact Allowed", "team"),
            new Metric("teamPLdPct", "LD % Allowed", "%", 1, false, "rate", "Team Contact Allowed", "team")
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        isDark = false; // v28 uses one polished light theme.
        initColors();
        long maxKb = Runtime.getRuntime().maxMemory() / 1024;
        imageCache = new LruCache<String, Bitmap>((int) (maxKb / 8)) {
            @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount() / 1024; }
        };
        selectedMetricKeys.addAll(metricKeysForPreset("recommended"));
        keyEdgeMetricKeys.addAll(defaultKeyEdgeForPresetAndRole("recommended", allowedMetricRoleForCurrentContext(), selectedMetricKeys));
        buildUi();
        setPrimaryTab(TAB_HOME);
        loadTeamsAndPlayers();
        fetchTodayGames();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    private void initColors() {
        if (isDark) {
            NAVY     = Color.rgb(9, 15, 28);
            NAVY_2   = Color.rgb(18, 38, 70);
            TEAL     = Color.rgb(13, 178, 163);
            TEAL_DARK= Color.rgb(0, 145, 132);
            SALMON   = Color.rgb(255, 122, 107);
            AMBER    = Color.rgb(255, 190, 89);
            BG       = Color.rgb(13, 18, 30);
            INK      = Color.rgb(225, 233, 245);
            MUTED    = Color.rgb(130, 145, 168);
            LINE     = Color.rgb(38, 52, 74);
            CARD     = Color.rgb(20, 28, 44);
        } else {
            NAVY     = Color.rgb(10, 23, 55);
            NAVY_2   = Color.rgb(21, 53, 97);
            TEAL     = Color.rgb(13, 178, 163);
            TEAL_DARK= Color.rgb(0, 125, 115);
            SALMON   = Color.rgb(255, 122, 107);
            AMBER    = Color.rgb(255, 190, 89);
            BG       = Color.rgb(242, 246, 251);
            INK      = Color.rgb(22, 29, 43);
            MUTED    = Color.rgb(94, 105, 124);
            LINE     = Color.rgb(221, 229, 241);
            CARD     = Color.WHITE;
        }
        // v29: font weight ladder (color-independent; system-cached)
        tfRegular = Typeface.create("sans-serif",        Typeface.NORMAL);
        tfMedium  = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        tfBold    = Typeface.create("sans-serif",        Typeface.BOLD);
    }

    private void buildUi() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Color.rgb(4, 8, 15));

        ScrollView scroll = new ScrollView(this);
        mainScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(4, 8, 15));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(28), dp(10), dp(12));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(screen);

        LinearLayout appBar = new LinearLayout(this);
        appBar.setOrientation(LinearLayout.HORIZONTAL);
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        appBar.setPadding(dp(8), dp(4), dp(8), dp(4));
        appBar.setBackground(isDark
            ? roundedGradientStroke(new int[] { Color.rgb(3, 7, 14), Color.rgb(7, 15, 29), Color.rgb(7, 28, 38) }, 20, Color.argb(46, 96, 225, 255), 1)
            : roundedGradientStroke(new int[] { Color.rgb(4, 10, 22), Color.rgb(8, 22, 42), Color.rgb(7, 48, 56) }, 20, Color.argb(58, 96, 225, 255), 1));
        appBar.setElevation(dp(4));
        root.addView(appBar, matchWrap());

        TextView monogram = text("SC", 13, Color.WHITE, true);
        monogram.setGravity(Gravity.CENTER);
        monogram.setLetterSpacing(0.08f);
        monogram.setBackground(roundedStroke(Color.argb(34, 255, 255, 255), Color.argb(90, 255, 255, 255), 18, 1));
        LinearLayout.LayoutParams monoLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        monoLp.setMargins(0, 0, dp(9), 0);
        appBar.addView(monogram, monoLp);

        LinearLayout titleColApp = new LinearLayout(this);
        titleColApp.setOrientation(LinearLayout.VERTICAL);
        appBar.addView(titleColApp, new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = text("STATCAST", 20, Color.WHITE, true);
        title.setLetterSpacing(0.01f);
        titleColApp.addView(title);
        TextView subtitle = text("Matchup · Search · Rankings", 9, Color.rgb(217, 230, 245), false);
        subtitle.setPadding(0, dp(1), 0, 0);
        titleColApp.addView(subtitle);

        LinearLayout badgeStack = new LinearLayout(this);
        badgeStack.setOrientation(LinearLayout.VERTICAL);
        badgeStack.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        liveBadge = text("LIVE", 10, Color.WHITE, true);
        liveBadge.setGravity(Gravity.CENTER);
        liveBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
        liveBadge.setLetterSpacing(0.12f);
        liveBadge.setBackground(roundedStroke(Color.argb(40, 255, 255, 255), Color.argb(92, 255, 255, 255), 14, 1));
        badgeStack.addView(liveBadge);
        TextView versionBadge = text("v147", 10, Color.rgb(213, 238, 236), true);
        versionBadge.setGravity(Gravity.CENTER);
        versionBadge.setPadding(0, dp(3), 0, 0);
        badgeStack.addView(versionBadge);

        appBar.addView(badgeStack);

        homeBox = buildHomeDashboard();
        LinearLayout.LayoutParams homeLp = matchWrap();
        homeLp.setMargins(0, dp(8), 0, 0);
        root.addView(homeBox, homeLp);

        form = verticalCard(28, new int[] { Color.rgb(5, 10, 18), Color.rgb(8, 18, 34), Color.rgb(7, 24, 46) });
        form.setBackground(roundedGradientStroke(new int[] { Color.rgb(5, 10, 18), Color.rgb(8, 18, 34), Color.rgb(7, 24, 46) }, 28, Color.argb(92, 104, 195, 228), 1));
        form.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams formLp = matchWrap();
        formLp.setMargins(0, dp(6), 0, 0);
        root.addView(form, formLp);

        LinearLayout commandHeader = new LinearLayout(this);
        commandHeader.setOrientation(LinearLayout.HORIZONTAL);
        commandHeader.setGravity(Gravity.CENTER_VERTICAL);
        form.addView(commandHeader, matchWrap());
        LinearLayout commandText = new LinearLayout(this);
        commandText.setOrientation(LinearLayout.VERTICAL);
        commandHeader.addView(commandText, new LinearLayout.LayoutParams(0, -2, 1));
        commandText.addView(text("Workspace controls", 16, Color.WHITE, true));
        commandText.addView(text("Matchups, profiles, and rankings share one premium control surface.", 11, Color.rgb(201, 215, 233), false));

        metricPickerButton = compactControlButton("Stats", false);
        metricPickerButton.setForeground(ripple(false));
        metricPickerButton.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams metricPickerLp = new LinearLayout.LayoutParams(dp(104), dp(38));
        metricPickerLp.setMargins(dp(8), 0, 0, 0);
        commandHeader.addView(metricPickerButton, metricPickerLp);
        updateMetricPickerLabel();

        LinearLayout navShell = new LinearLayout(this);
        navShell.setOrientation(LinearLayout.HORIZONTAL);
        navShell.setPadding(dp(4), dp(4), dp(4), dp(4));
        navShell.setBackground(roundedStroke(Color.argb(26, 255, 255, 255), Color.argb(52, 186, 214, 236), 20, 1));
        LinearLayout.LayoutParams navShellLp = matchWrap();
        navShellLp.setMargins(0, dp(8), 0, dp(8));
        form.addView(navShell, navShellLp);
        headToHeadButton = pillButton("Matchup", true);
        singleViewButton = pillButton("Profile", false);
        standingsButton = pillButton("Rankings", false);
        headToHeadButton.setForeground(ripple(true));
        singleViewButton.setForeground(ripple(true));
        standingsButton.setForeground(ripple(true));
        navShell.addView(headToHeadButton, weightLp());
        navShell.addView(singleViewButton, weightLp());
        navShell.addView(standingsButton, weightLp());
        navShell.setVisibility(View.GONE);

        typeModeLabel = sectionEyebrow("ENTITY TYPE");
        form.addView(typeModeLabel);

        LinearLayout modeShell = new LinearLayout(this);
        modeShell.setOrientation(LinearLayout.HORIZONTAL);
        modeShell.setPadding(dp(4), dp(4), dp(4), dp(4));
        modeShell.setBackground(roundedStroke(Color.argb(26, 255, 255, 255), Color.argb(52, 186, 214, 236), 20, 1));
        LinearLayout.LayoutParams modeShellLp = matchWrap();
        modeShellLp.setMargins(0, dp(4), 0, dp(7));
        form.addView(modeShell, modeShellLp);
        playerModeButton = pillButton("Player", true);
        teamModeButton = pillButton("Team", false);
        playerModeButton.setForeground(ripple(true));
        teamModeButton.setForeground(ripple(true));
        modeShell.addView(playerModeButton, weightLp());
        modeShell.addView(teamModeButton, weightLp());

        profileToolsShell = new LinearLayout(this);
        profileToolsShell.setOrientation(LinearLayout.HORIZONTAL);
        profileToolsShell.setPadding(dp(4), dp(4), dp(4), dp(4));
        profileToolsShell.setBackground(roundedStroke(Color.argb(18, 255, 255, 255), Color.argb(48, 186, 214, 236), 20, 1));
        profileToolsShell.setVisibility(View.GONE);
        LinearLayout.LayoutParams profileToolsLp = matchWrap();
        profileToolsLp.setMargins(0, 0, 0, dp(8));
        form.addView(profileToolsShell, profileToolsLp);
        expectedViewButton = pillButton("Show xStats", false);
        expectedViewButton.setForeground(ripple(true));
        profileToolsShell.addView(expectedViewButton, weightLp());

        primarySelectorLabel = sectionEyebrow("1 · CHOOSE PLAYER A");
        form.addView(primarySelectorLabel);

        // v28: recent-player quick picks removed to reduce top-card clutter.
        recentsBox = new LinearLayout(this);
        recentsBox.setOrientation(LinearLayout.HORIZONTAL);
        recentsBox.setVisibility(View.GONE);

        searchInput = new EditText(this);
        searchInput.setHint("Search Player A — Tatis, Soto, Judge, Skenes…");
        searchInput.setSingleLine(true);
        searchInput.setTextSize(16);
        searchInput.setTextColor(Color.WHITE);
        searchInput.setHintTextColor(Color.rgb(160, 175, 196));
        searchInput.setPadding(dp(14), dp(11), dp(14), dp(11));
        searchInput.setBackground(roundedStroke(Color.argb(14, 255, 255, 255), Color.argb(82, 190, 214, 236), 18, 1));
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchInput.setContentDescription("Search for a player by name, team, or position");
        LinearLayout.LayoutParams inputLp = matchWrap();
        inputLp.setMargins(0, dp(6), 0, dp(8));
        form.addView(searchInput, inputLp);

        suggestionsList = new ListView(this);
        suggestionsAdapter = new PlayerSuggestionAdapter(filteredPlayers);
        suggestionsList.setAdapter(suggestionsAdapter);
        suggestionsList.setVisibility(View.GONE);
        suggestionsList.setDividerHeight(1);
        suggestionsList.setBackground(roundedStroke(Color.rgb(9, 15, 28), Color.argb(88, 118, 155, 198), 18, 1));
        form.addView(suggestionsList, new LinearLayout.LayoutParams(-1, dp(248)));

        teamSpinner = new Spinner(this);
        teamAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>());
        teamSpinner.setAdapter(teamAdapter);
        teamSpinner.setVisibility(View.GONE);
        form.addView(teamSpinner, new LinearLayout.LayoutParams(dp(1), dp(1)));

        teamPickerButton = secondaryActionButton("Choose Team A");
        teamPickerButton.setVisibility(View.GONE);
        teamPickerButton.setForeground(ripple(false));
        LinearLayout.LayoutParams teamPickerLp = matchWrap();
        teamPickerLp.setMargins(0, dp(6), 0, dp(8));
        form.addView(teamPickerButton, teamPickerLp);

        teamChipRow = new LinearLayout(this);
        teamChipRow.setOrientation(LinearLayout.HORIZONTAL);
        teamChipRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams teamChipLp = matchWrap();
        teamChipLp.setMargins(0, dp(6), 0, dp(8));
        form.addView(horizontalChipScroller(teamChipRow), teamChipLp);

        selectedPreviewBox = new LinearLayout(this);
        selectedPreviewBox.setOrientation(LinearLayout.HORIZONTAL);
        selectedPreviewBox.setGravity(Gravity.CENTER_VERTICAL);
        selectedPreviewBox.setPadding(dp(9), dp(9), dp(9), dp(9));
        selectedPreviewBox.setVisibility(View.GONE);
        selectedPreviewBox.setBackground(roundedStroke(Color.argb(22, 255, 255, 255), Color.argb(54, 186, 214, 236), 18, 1));
        LinearLayout.LayoutParams previewLp = matchWrap();
        previewLp.setMargins(0, dp(2), 0, dp(10));
        form.addView(selectedPreviewBox, previewLp);

        compareSelectorLabel = sectionEyebrow("2 · PICK PLAYER B");
        compareSelectorLabel.setVisibility(View.GONE);
        form.addView(compareSelectorLabel);

        compareSearchInput = new EditText(this);
        compareSearchInput.setHint("Search Player B — Ohtani, Judge, Soto…");
        compareSearchInput.setSingleLine(true);
        compareSearchInput.setTextSize(16);
        compareSearchInput.setTextColor(Color.WHITE);
        compareSearchInput.setHintTextColor(Color.rgb(160, 175, 196));
        compareSearchInput.setPadding(dp(14), dp(11), dp(14), dp(11));
        compareSearchInput.setBackground(roundedStroke(Color.argb(14, 255, 255, 255), Color.argb(82, 190, 214, 236), 18, 1));
        compareSearchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        compareSearchInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        compareSearchInput.setContentDescription("Search for a second player to compare");
        compareSearchInput.setVisibility(View.GONE);
        LinearLayout.LayoutParams compareInputLp = matchWrap();
        compareInputLp.setMargins(0, dp(6), 0, dp(8));
        form.addView(compareSearchInput, compareInputLp);

        compareSuggestionsList = new ListView(this);
        compareSuggestionsAdapter = new PlayerSuggestionAdapter(filteredComparePlayers);
        compareSuggestionsList.setAdapter(compareSuggestionsAdapter);
        compareSuggestionsList.setVisibility(View.GONE);
        compareSuggestionsList.setDividerHeight(1);
        compareSuggestionsList.setBackground(roundedStroke(Color.rgb(9, 15, 28), Color.argb(88, 118, 155, 198), 18, 1));
        form.addView(compareSuggestionsList, new LinearLayout.LayoutParams(-1, dp(258)));

        compareTeamSpinner = new Spinner(this);
        compareTeamAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>());
        compareTeamSpinner.setAdapter(compareTeamAdapter);
        compareTeamSpinner.setVisibility(View.GONE);
        form.addView(compareTeamSpinner, new LinearLayout.LayoutParams(dp(1), dp(1)));

        compareTeamPickerButton = secondaryActionButton("Choose Team B");
        compareTeamPickerButton.setVisibility(View.GONE);
        compareTeamPickerButton.setForeground(ripple(false));
        LinearLayout.LayoutParams compareTeamPickerLp = matchWrap();
        compareTeamPickerLp.setMargins(0, dp(6), 0, dp(8));
        form.addView(compareTeamPickerButton, compareTeamPickerLp);

        compareTeamChipRow = new LinearLayout(this);
        compareTeamChipRow.setOrientation(LinearLayout.HORIZONTAL);
        compareTeamChipRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams compareTeamLp = matchWrap();
        compareTeamLp.setMargins(0, dp(6), 0, dp(8));
        form.addView(horizontalChipScroller(compareTeamChipRow), compareTeamLp);

        comparePreviewBox = new LinearLayout(this);
        comparePreviewBox.setOrientation(LinearLayout.HORIZONTAL);
        comparePreviewBox.setGravity(Gravity.CENTER_VERTICAL);
        comparePreviewBox.setPadding(dp(9), dp(9), dp(9), dp(9));
        comparePreviewBox.setVisibility(View.GONE);
        comparePreviewBox.setBackground(roundedStroke(Color.argb(22, 255, 255, 255), Color.argb(54, 186, 214, 236), 18, 1));
        LinearLayout.LayoutParams comparePreviewLp = matchWrap();
        comparePreviewLp.setMargins(0, dp(2), 0, dp(10));
        form.addView(comparePreviewBox, comparePreviewLp);

        controlsCard = new LinearLayout(this);
        controlsCard.setOrientation(LinearLayout.VERTICAL);
        controlsCard.setPadding(dp(10), dp(10), dp(10), dp(10));
        controlsCard.setBackground(roundedStroke(Color.argb(18, 255, 255, 255), Color.argb(56, 186, 214, 236), 20, 1));
        controlsCard.setVisibility(View.GONE);
        LinearLayout.LayoutParams controlsLp = matchWrap();
        controlsLp.setMargins(0, dp(3), 0, 0);
        form.addView(controlsCard, controlsLp);

        LinearLayout seasonCol = new LinearLayout(this);
        seasonCol.setOrientation(LinearLayout.VERTICAL);
        seasonCol.addView(sectionEyebrow("SEASON"));
        seasonSpinner = new Spinner(this);
        seasonSpinner.setVisibility(View.GONE);
        ArrayList<String> years = new ArrayList<>();
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        selectedSeasonValue = currentYear;
        for (int y = currentYear; y >= STATCAST_START_YEAR; y--) years.add(String.valueOf(y));
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, years);
        seasonSpinner.setAdapter(yearAdapter);
        seasonCol.addView(seasonSpinner, new LinearLayout.LayoutParams(dp(1), dp(1)));
        seasonChipRow = new LinearLayout(this);
        seasonChipRow.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView seasonScroll = horizontalChipScroller(seasonChipRow);
        seasonCol.addView(seasonScroll, matchWrap());
        controlsCard.addView(seasonCol, matchWrap());
        buildSeasonChips();

        rankControlContainer = new LinearLayout(this);
        rankControlContainer.setOrientation(LinearLayout.VERTICAL);
        rankControlContainer.setVisibility(View.GONE);
        LinearLayout.LayoutParams rankControlsLp = matchWrap();
        rankControlsLp.setMargins(0, dp(8), 0, 0);
        controlsCard.addView(rankControlContainer, rankControlsLp);

        rankControlContainer.addView(sectionEyebrow("RANKING STAT"));
        rankMetricSpinner = new Spinner(this);
        rankMetricSpinner.setVisibility(View.GONE);
        rankControlContainer.addView(rankMetricSpinner, new LinearLayout.LayoutParams(dp(1), dp(1)));
        rankMetricPickerButton = secondaryActionButton("Ranking stat: OPS");
        rankMetricPickerButton.setForeground(ripple(false));
        LinearLayout.LayoutParams rankPickerLp = matchWrap();
        rankPickerLp.setMargins(0, dp(4), 0, 0);
        rankControlContainer.addView(rankMetricPickerButton, rankPickerLp);
        rankMetricChipRow = new LinearLayout(this);
        rankMetricChipRow.setOrientation(LinearLayout.HORIZONTAL);
        rankMetricChipRow.setVisibility(View.GONE);
        rebuildRankMetricSpinner();

        // v28: no bottom action row. Data loads immediately when mode/selection changes.
        compareButton = primaryActionButton("Compare");
        compareButton.setVisibility(View.GONE);
        compareButton.setEnabled(false);
        controlsCard.addView(compareButton, new LinearLayout.LayoutParams(dp(1), dp(1)));

        statusView = text("Loading MLB teams and active players…", 12, Color.rgb(187, 203, 224), false);
        statusView.setPadding(dp(2), dp(9), dp(2), 0);
        statusView.setVisibility(View.GONE);

        filterBox = new LinearLayout(this);
        filterBox.setOrientation(LinearLayout.VERTICAL);
        filterBox.setVisibility(View.GONE);

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        LinearLayout.LayoutParams loadLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        loadLp.gravity = Gravity.CENTER_HORIZONTAL;
        loadLp.setMargins(0, dp(10), 0, 0);
        form.addView(loading, loadLp);

        errorView = text("", 14, Color.rgb(255, 145, 158), false);
        errorView.setPadding(dp(2), dp(10), dp(2), 0);
        errorView.setVisibility(View.GONE);
        form.addView(errorView);

        retryButton = secondaryActionButton("Retry");
        retryButton.setForeground(ripple(false));
        retryButton.setVisibility(View.GONE);
        retryButton.setOnClickListener(v -> {
            showError(null);
            loadTeamsAndPlayers();
        });
        LinearLayout.LayoutParams retryLp = matchWrap();
        retryLp.setMargins(0, dp(6), 0, 0);
        form.addView(retryButton, retryLp);

        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        resultsBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams resultsLp = matchWrap();
        resultsLp.setMargins(0, dp(12), 0, 0);
        root.addView(resultsBox, resultsLp);

        headerBox = new LinearLayout(this);
        headerBox.setOrientation(LinearLayout.VERTICAL);
        resultsBox.addView(headerBox, matchWrap());

        copyButton = compactControlButton("Copy table", false);
        copyButton.setForeground(ripple(false));
        copyButton.setVisibility(View.GONE);

        shareButton = compactControlButton("Share image", false);
        shareButton.setForeground(ripple(false));
        shareButton.setVisibility(View.GONE);

        metricBox = new LinearLayout(this);
        metricBox.setOrientation(LinearLayout.VERTICAL);
        resultsBox.addView(metricBox, matchWrap());

        standingsBox = new LinearLayout(this);
        standingsBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams standingsLp = matchWrap();
        standingsLp.setMargins(0, dp(14), 0, 0);
        root.addView(standingsBox, standingsLp);

        bottomNavHost = buildBottomAppNav();
        screen.addView(bottomNavHost, new LinearLayout.LayoutParams(-1, -2));

        updateAnalysisModeButtons();
        updateViewModeButtons();
        updateControlsVisibility();
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
        b.setTextSize(13);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(4), dp(6), dp(4), dp(6));
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setSingleLine(false);
        b.setMaxLines(2);
        b.setEllipsize(null);
        b.setTextColor(active ? Color.WHITE : Color.rgb(71, 83, 105));
        b.setBackground(active ? roundedGradient(new int[] { NAVY, Color.rgb(24, 62, 109) }, 15) : rounded(Color.TRANSPARENT, 15));
        return b;
    }

    private TextView sectionEyebrow(String value) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(10);
        tv.setTextColor(Color.rgb(171, 188, 209));
        tv.setTypeface(tfMedium);               // v29: medium weight for section labels
        tv.setLetterSpacing(0.12f);
        tv.setPadding(dp(2), dp(2), dp(2), dp(2));
        return tv;
    }


    private HorizontalScrollView horizontalChipScroller(LinearLayout chipRow) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(false);
        scroll.addView(chipRow, new HorizontalScrollView.LayoutParams(-2, -2));
        return scroll;
    }

    private LinearLayout buildHomeDashboard() {
        LinearLayout home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);

        LinearLayout hero = verticalCard(30, new int[] {
                Color.rgb(5, 9, 17),
                Color.rgb(7, 17, 31),
                Color.rgb(6, 22, 42)
        });
        hero.setPadding(dp(6), dp(12), dp(6), dp(12));

        TextView eyebrow = text("STATCAST MATCHUP", 10, Color.rgb(240, 193, 76), true);
        eyebrow.setLetterSpacing(0.22f);
        hero.addView(eyebrow);

        TextView heroTitle = text("Build a Matchup", 23, Color.WHITE, true);
        heroTitle.setPadding(0, dp(6), 0, 0);
        hero.addView(heroTitle);

        TextView heroSub = text("Select any two players or teams and see who owns the edge.", 11, Color.rgb(204, 216, 234), false);
        heroSub.setPadding(0, dp(7), 0, 0);
        hero.addView(heroSub);

        homeMatchupPreview = buildHomeMatchupPreview();
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, dp(222));
        previewLp.setMargins(0, dp(10), 0, 0);
        hero.addView(homeMatchupPreview, previewLp);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionRowLp = matchWrap();
        actionRowLp.setMargins(0, dp(9), 0, 0);
        hero.addView(actionRow, actionRowLp);

        homeCreateButton = homePrimaryButton("Create Matchup", Color.rgb(247, 197, 77), Color.rgb(178, 124, 20));
        homeCreateButton.setOnClickListener(v -> performHomeCreateMatchup());
        attachPremiumPress(homeCreateButton, 0.975f);
        LinearLayout.LayoutParams createLp = new LinearLayout.LayoutParams(0, -2, 1);
        createLp.setMargins(0, 0, dp(8), 0);
        actionRow.addView(homeCreateButton, createLp);

        // v118: Stats belongs with the action row instead of over the matchup logos/name captions.
        homeStatsButton = homeStatsSelectorButton();
        homeStatsButton.setOnClickListener(v -> openHomeStatsPicker());
        attachPremiumPress(homeStatsButton, 0.96f);
        LinearLayout.LayoutParams statsActionLp = new LinearLayout.LayoutParams(dp(128), -2);
        actionRow.addView(homeStatsButton, statsActionLp);
        updateMetricPickerLabel();

        homeInlineSelectorCard = buildHomeInlineSelectorCard();
        LinearLayout.LayoutParams inlineLp = matchWrap();
        inlineLp.setMargins(0, dp(12), 0, 0);
        hero.addView(homeInlineSelectorCard, inlineLp);

        home.addView(hero, matchWrap());

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridLp = matchWrap();
        gridLp.setMargins(0, dp(10), 0, 0);
        home.addView(grid, gridLp);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        grid.addView(row1, matchWrap());
        LinearLayout.LayoutParams row2Lp = matchWrap();
        row2Lp.setMargins(0, dp(8), 0, 0);
        grid.addView(row2, row2Lp);

        LinearLayout card1 = dashboardCard("⚔", "Matchup", Color.rgb(7, 13, 25), Color.rgb(13, 31, 61), Color.rgb(77, 139, 255));
        LinearLayout card2 = dashboardCard("♙", "Players", Color.rgb(10, 12, 24), Color.rgb(31, 18, 63), Color.rgb(176, 119, 255));
        LinearLayout card3 = dashboardCard("♚", "Teams", Color.rgb(14, 12, 22), Color.rgb(46, 31, 6), Color.rgb(242, 194, 68));
        LinearLayout card4 = dashboardCard("▥", "Rankings", Color.rgb(8, 17, 24), Color.rgb(5, 43, 48), Color.rgb(93, 230, 215));
        card1.setOnClickListener(v -> setPrimaryTab(TAB_MATCHUP));
        card2.setOnClickListener(v -> { setMode(false); setPrimaryTab(TAB_PROFILE); });
        card3.setOnClickListener(v -> { setMode(true); setPrimaryTab(TAB_PROFILE); });
        card4.setOnClickListener(v -> setPrimaryTab(TAB_RANKINGS));
        attachPremiumPress(card1, 0.975f);
        attachPremiumPress(card2, 0.975f);
        attachPremiumPress(card3, 0.975f);
        attachPremiumPress(card4, 0.975f);

        LinearLayout.LayoutParams c1 = new LinearLayout.LayoutParams(0, dp(128), 1);
        LinearLayout.LayoutParams c2 = new LinearLayout.LayoutParams(0, dp(128), 1);
        c2.setMargins(dp(10), 0, 0, 0);
        LinearLayout.LayoutParams c3 = new LinearLayout.LayoutParams(0, dp(128), 1);
        LinearLayout.LayoutParams c4 = new LinearLayout.LayoutParams(0, dp(128), 1);
        c4.setMargins(dp(10), 0, 0, 0);
        row1.addView(card1, c1);
        row1.addView(card2, c2);
        row2.addView(card3, c3);
        row2.addView(card4, c4);

        homeInlinePlayerAdapter = new PlayerSuggestionAdapter(homeInlinePlayers);
        homeInlineTeamAdapterA = new TeamPickerAdapter(homeInlineTeams, true);
        homeInlineTeamAdapterB = new TeamPickerAdapter(homeInlineTeams, false);
        updateHomeSelectionMode(false);
        updateHomeMatchupPreview();
        return home;
    }

    private FrameLayout buildHomeMatchupPreview() {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(0), dp(6), dp(0), dp(8));
        wrap.setBackground(roundedGradientStroke(new int[] {
                Color.rgb(5, 10, 20),
                Color.rgb(8, 16, 30),
                Color.rgb(5, 12, 22)
        }, 26, Color.argb(96, 242, 194, 68), 1));

        homeEnergyView = new HomeEnergyView(this);
        wrap.addView(homeEnergyView, new FrameLayout.LayoutParams(-1, -1));

        homeCircleAFrame = new FrameLayout(this);
        homeCircleAFrame.setPadding(dp(5), dp(5), dp(5), dp(5));
        FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(dp(126), dp(126));
        leftLp.gravity = Gravity.LEFT | Gravity.TOP;
        leftLp.leftMargin = dp(12);
        leftLp.topMargin = dp(14);
        wrap.addView(homeCircleAFrame, leftLp);

        homeCircleAGlow = new View(this);
        homeCircleAFrame.addView(homeCircleAGlow, new FrameLayout.LayoutParams(-1, -1));

        homeCircleAImage = new ImageView(this);
        homeCircleAImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        homeCircleAImage.setPadding(dp(8), dp(8), dp(8), dp(8));
        homeCircleAImage.setBackground(rounded(Color.rgb(6, 10, 18), 58));
        applyRoundedClip(homeCircleAImage, 58);
        homeCircleAImage.setVisibility(View.GONE);
        homeCircleAFrame.addView(homeCircleAImage, new FrameLayout.LayoutParams(-1, -1));

        homeCircleAText = text("", 24, Color.WHITE, true);
        homeCircleAText.setGravity(Gravity.CENTER);
        homeCircleAFrame.addView(homeCircleAText, new FrameLayout.LayoutParams(-1, -1));

        homeCircleAActionBadge = circleActionBadge("+", Color.rgb(247, 197, 77));
        FrameLayout.LayoutParams aBadgeLp = new FrameLayout.LayoutParams(dp(44), dp(44));
        aBadgeLp.gravity = Gravity.CENTER;
        homeCircleAFrame.addView(homeCircleAActionBadge, aBadgeLp);

        homeCircleAClearBadge = homeClearBadge();
        FrameLayout.LayoutParams aClearLp = new FrameLayout.LayoutParams(dp(25), dp(25));
        aClearLp.gravity = Gravity.TOP | Gravity.RIGHT;
        aClearLp.topMargin = dp(8);
        aClearLp.rightMargin = dp(8);
        homeCircleAFrame.addView(homeCircleAClearBadge, aClearLp);
        homeCircleAClearBadge.setOnClickListener(v -> clearHomeMatchupSide(false));

        homeCircleAFrame.setForeground(ripple(true));
        homeCircleAFrame.setOnClickListener(v -> openHomeInlineSelector(false));
        attachPremiumPress(homeCircleAFrame, 0.965f);

        homeCircleBFrame = new FrameLayout(this);
        homeCircleBFrame.setPadding(dp(5), dp(5), dp(5), dp(5));
        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(dp(126), dp(126));
        rightLp.gravity = Gravity.RIGHT | Gravity.TOP;
        rightLp.rightMargin = dp(12);
        rightLp.topMargin = dp(14);
        wrap.addView(homeCircleBFrame, rightLp);

        homeCircleBGlow = new View(this);
        homeCircleBFrame.addView(homeCircleBGlow, new FrameLayout.LayoutParams(-1, -1));

        homeCircleBImage = new ImageView(this);
        homeCircleBImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        homeCircleBImage.setPadding(dp(8), dp(8), dp(8), dp(8));
        homeCircleBImage.setBackground(rounded(Color.rgb(6, 10, 18), 58));
        applyRoundedClip(homeCircleBImage, 58);
        homeCircleBImage.setVisibility(View.GONE);
        homeCircleBFrame.addView(homeCircleBImage, new FrameLayout.LayoutParams(-1, -1));

        homeCircleBText = text("", 24, Color.WHITE, true);
        homeCircleBText.setGravity(Gravity.CENTER);
        homeCircleBFrame.addView(homeCircleBText, new FrameLayout.LayoutParams(-1, -1));

        homeCircleBActionBadge = circleActionBadge("+", Color.rgb(247, 197, 77));
        FrameLayout.LayoutParams bBadgeLp = new FrameLayout.LayoutParams(dp(44), dp(44));
        bBadgeLp.gravity = Gravity.CENTER;
        homeCircleBFrame.addView(homeCircleBActionBadge, bBadgeLp);

        homeCircleBClearBadge = homeClearBadge();
        FrameLayout.LayoutParams bClearLp = new FrameLayout.LayoutParams(dp(25), dp(25));
        bClearLp.gravity = Gravity.TOP | Gravity.LEFT;
        bClearLp.topMargin = dp(8);
        bClearLp.leftMargin = dp(8);
        homeCircleBFrame.addView(homeCircleBClearBadge, bClearLp);
        homeCircleBClearBadge.setOnClickListener(v -> clearHomeMatchupSide(true));

        homeCircleBFrame.setForeground(ripple(true));
        homeCircleBFrame.setOnClickListener(v -> openHomeInlineSelector(true));
        attachPremiumPress(homeCircleBFrame, 0.965f);

        homeVsBadge = text("VS", 15, Color.WHITE, true);
        homeVsBadge.setGravity(Gravity.CENTER);
        homeVsBadge.setBackground(roundedGradientStroke(new int[] {
                Color.argb(252, 7, 10, 18),
                Color.argb(248, 19, 27, 41),
                Color.argb(240, 42, 30, 14)
        }, 24, Color.argb(88, 255, 215, 90), 1));
        homeVsBadge.setShadowLayer(dp(5), 0, 0, Color.argb(150, 255, 216, 86));
        FrameLayout.LayoutParams vsLp = new FrameLayout.LayoutParams(dp(42), dp(42));
        vsLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        vsLp.topMargin = dp(56);
        wrap.addView(homeVsBadge, vsLp);

        // v113: circles are the actual selectors now, so the old large "tap to select" pills
        // become compact identity captions and the freed space hosts the mode toggle.
        homeSelectAText = homeSelectorPill("LEFT", Color.rgb(135, 185, 255));
        homeSelectAText.setSingleLine(true);
        homeSelectAText.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams aLp = new FrameLayout.LayoutParams(dp(92), dp(18));
        aLp.gravity = Gravity.TOP | Gravity.LEFT;
        aLp.leftMargin = dp(28);
        aLp.topMargin = dp(143);
        wrap.addView(homeSelectAText, aLp);

        homeSelectBText = homeSelectorPill("RIGHT", Color.rgb(255, 226, 136));
        homeSelectBText.setSingleLine(true);
        homeSelectBText.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams bLp = new FrameLayout.LayoutParams(dp(92), dp(18));
        bLp.gravity = Gravity.TOP | Gravity.RIGHT;
        bLp.rightMargin = dp(28);
        bLp.topMargin = dp(143);
        wrap.addView(homeSelectBText, bLp);

        LinearLayout homeModeToggle = new LinearLayout(this);
        homeModeToggle.setOrientation(LinearLayout.HORIZONTAL);
        homeModeToggle.setPadding(dp(3), dp(3), dp(3), dp(3));
        homeModeToggle.setBackground(roundedGradientStroke(new int[] {
                Color.argb(246, 6, 11, 22),
                Color.argb(238, 9, 22, 36)
        }, 18, Color.argb(92, 255, 255, 255), 1));
        FrameLayout.LayoutParams toggleLp = new FrameLayout.LayoutParams(dp(178), dp(36));
        toggleLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        toggleLp.bottomMargin = dp(6);
        wrap.addView(homeModeToggle, toggleLp);

        homeModePlayersBtn = homeSegmentButton("Player", Color.rgb(84, 142, 247));
        homeModeTeamsBtn = homeSegmentButton("Team", Color.rgb(23, 187, 171));
        homeModePlayersBtn.setOnClickListener(v -> updateHomeSelectionMode(false));
        homeModeTeamsBtn.setOnClickListener(v -> updateHomeSelectionMode(true));
        attachPremiumPress(homeModePlayersBtn, 0.97f);
        attachPremiumPress(homeModeTeamsBtn, 0.97f);
        homeModeToggle.addView(homeModePlayersBtn, new LinearLayout.LayoutParams(0, -1, 1));
        homeModeToggle.addView(homeModeTeamsBtn, new LinearLayout.LayoutParams(0, -1, 1));

        styleHomeCircle(homeCircleAFrame, Color.rgb(84, 142, 247), false, true);
        styleHomeCircle(homeCircleBFrame, Color.rgb(247, 197, 77), false, false);
        return wrap;
    }

    private LinearLayout buildHomeInlineSelectorCard() {
        LinearLayout card = verticalCard(24, new int[] { Color.rgb(7, 13, 24), Color.rgb(8, 18, 33) });
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setVisibility(View.GONE);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(titleRow, matchWrap());

        homeInlineSelectorTitle = text("Select matchup side A", 13, Color.WHITE, true);
        titleRow.addView(homeInlineSelectorTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text("Hide", 11, Color.rgb(168, 186, 214), true);
        close.setPadding(dp(8), dp(4), dp(8), dp(4));
        close.setOnClickListener(v -> closeHomeInlineSelector());
        titleRow.addView(close);

        homeInlineHint = text("Type at least 2 letters. Pick a player or team and the home preview updates live.", 11, Color.rgb(160, 176, 198), false);
        homeInlineHint.setPadding(0, dp(6), 0, 0);
        card.addView(homeInlineHint);

        homeInlineSearchInput = new EditText(this);
        homeInlineSearchInput.setHint("Search players");
        homeInlineSearchInput.setHintTextColor(Color.rgb(113, 130, 154));
        homeInlineSearchInput.setTextColor(Color.WHITE);
        homeInlineSearchInput.setSingleLine(true);
        homeInlineSearchInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        homeInlineSearchInput.setBackground(roundedStroke(Color.argb(36, 255, 255, 255), Color.argb(72, 255, 255, 255), 16, 1));
        homeInlineSearchInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams inputLp = matchWrap();
        inputLp.setMargins(0, dp(10), 0, 0);
        card.addView(homeInlineSearchInput, inputLp);

        homeInlineSuggestionList = new ListView(this);
        homeInlineSuggestionList.setDividerHeight(0);
        homeInlineSuggestionList.setCacheColorHint(Color.TRANSPARENT);
        homeInlineSuggestionList.setSelector(rounded(Color.argb(52, 255, 255, 255), 18));
        homeInlineSuggestionList.setVerticalScrollBarEnabled(true);
        homeInlineSuggestionList.setBackground(roundedStroke(Color.argb(16, 255, 255, 255), Color.argb(28, 255, 255, 255), 18, 1));
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, dp(240));
        listLp.setMargins(0, dp(10), 0, 0);
        card.addView(homeInlineSuggestionList, listLp);

        homeInlineSearchInput.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) keepInputAboveKeyboard(homeInlineSearchInput, 58); });
        homeInlineSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterHomeInlineSuggestions(s == null ? "" : s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        homeInlineSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if ((actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH)) {
                if (homeInlineTeamMode) {
                    if (!homeInlineTeams.isEmpty()) applyHomeInlineSelection(homeInlineTeams.get(0));
                } else {
                    if (!homeInlinePlayers.isEmpty()) applyHomeInlineSelection(homeInlinePlayers.get(0));
                }
                return true;
            }
            return false;
        });

        homeInlineSuggestionList.setOnItemClickListener((parent, view, position, id) -> {
            if (homeInlineTeamMode) {
                if (position >= 0 && position < homeInlineTeams.size()) applyHomeInlineSelection(homeInlineTeams.get(position));
            } else {
                if (position >= 0 && position < homeInlinePlayers.size()) applyHomeInlineSelection(homeInlinePlayers.get(position));
            }
        });
        return card;
    }

    private void openHomeInlineSelector(boolean secondarySide) {
        homeInlineSecondary = secondarySide;
        showHomePickerDialog();
    }

    private void showHomePickerDialog() {
        if (homePickerDialog != null && homePickerDialog.isShowing()) {
            updateHomeInlineSelectorUi();
            if (homeInlineSearchInput != null) {
                homeInlineSearchInput.requestFocus();
                homeInlineSearchInput.setSelection(homeInlineSearchInput.getText() == null ? 0 : homeInlineSearchInput.getText().length());
                showKeyboard(homeInlineSearchInput);
            }
            return;
        }

        LinearLayout sheet = verticalCard(26, new int[] {
                Color.rgb(6, 10, 20),
                Color.rgb(8, 16, 30),
                Color.rgb(6, 24, 42)
        });
        sheet.setPadding(dp(16), dp(12), dp(16), dp(16));

        View handle = new View(this);
        handle.setBackground(rounded(Color.argb(92, 255, 255, 255), 99));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(42), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(handle, handleLp);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerLp = matchWrap();
        headerLp.setMargins(0, dp(10), 0, 0);
        sheet.addView(header, headerLp);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        header.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));

        homeInlineSelectorTitle = text("Select player for side A", 16, Color.WHITE, true);
        titleCol.addView(homeInlineSelectorTitle);
        homeActiveSideBadge = text("Now filling side A", 11, Color.rgb(255, 224, 120), true);
        homeActiveSideBadge.setPadding(dp(10), dp(5), dp(10), dp(5));
        LinearLayout.LayoutParams activeLp = new LinearLayout.LayoutParams(-2, -2);
        activeLp.setMargins(0, dp(6), 0, 0);
        titleCol.addView(homeActiveSideBadge, activeLp);
        homeInlineHint = text("Search, tap, and watch the matchup circles update live.", 11, Color.rgb(156, 173, 197), false);
        homeInlineHint.setPadding(0, dp(4), 0, 0);
        titleCol.addView(homeInlineHint);

        TextView close = text("Close", 11, Color.rgb(171, 188, 214), true);
        close.setPadding(dp(12), dp(8), dp(12), dp(8));
        close.setBackground(roundedStroke(Color.argb(26, 255, 255, 255), Color.argb(70, 255, 255, 255), 14, 1));
        close.setOnClickListener(v -> closeHomeInlineSelector());
        header.addView(close);

        homeInlineSearchInput = new EditText(this);
        homeInlineSearchInput.setHint(homeInlineTeamMode ? "Search teams" : "Search players");
        homeInlineSearchInput.setHintTextColor(Color.rgb(110, 126, 150));
        homeInlineSearchInput.setTextColor(Color.WHITE);
        homeInlineSearchInput.setSingleLine(true);
        homeInlineSearchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        homeInlineSearchInput.setBackground(roundedStroke(Color.argb(34, 255, 255, 255), Color.argb(72, 255, 255, 255), 18, 1));
        homeInlineSearchInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams inputLp = matchWrap();
        inputLp.setMargins(0, dp(12), 0, 0);
        sheet.addView(homeInlineSearchInput, inputLp);

        TextView miniHint = text(homeInlineTeamMode ? "Scroll for all teams · or search city, nickname, abbreviation" : "Try: Tatis, Ohtani, Judge", 10, Color.rgb(128, 145, 168), false);
        miniHint.setPadding(dp(2), dp(7), dp(2), 0);
        sheet.addView(miniHint, matchWrap());

        homeInlineSuggestionList = new ListView(this);
        homeInlineSuggestionList.setDividerHeight(0);
        homeInlineSuggestionList.setCacheColorHint(Color.TRANSPARENT);
        homeInlineSuggestionList.setVerticalScrollBarEnabled(true);
        homeInlineSuggestionList.setFastScrollEnabled(true);
        homeInlineSuggestionList.setSmoothScrollbarEnabled(true);
        homeInlineSuggestionList.setSelector(rounded(Color.argb(26, 255, 255, 255), 20));
        homeInlineSuggestionList.setBackground(roundedStroke(Color.argb(14, 255, 255, 255), Color.argb(36, 255, 255, 255), 20, 1));
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, dp(286));
        listLp.setMargins(0, dp(10), 0, 0);
        sheet.addView(homeInlineSuggestionList, listLp);

        homeInlineSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterHomeInlineSuggestions(s == null ? "" : s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        homeInlineSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if ((actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH)) {
                if (homeInlineTeamMode) {
                    if (!homeInlineTeams.isEmpty()) applyHomeInlineSelection(homeInlineTeams.get(0));
                } else {
                    if (!homeInlinePlayers.isEmpty()) applyHomeInlineSelection(homeInlinePlayers.get(0));
                }
                return true;
            }
            return false;
        });
        homeInlineSuggestionList.setOnItemClickListener((parent, view, position, id) -> {
            if (homeInlineTeamMode) {
                if (position >= 0 && position < homeInlineTeams.size()) applyHomeInlineSelection(homeInlineTeams.get(position));
            } else {
                if (position >= 0 && position < homeInlinePlayers.size()) applyHomeInlineSelection(homeInlinePlayers.get(position));
            }
        });

        homePickerDialog = new AlertDialog.Builder(this).create();
        homePickerDialog.setView(sheet);
        homePickerDialog.setOnDismissListener(d -> {
            setBottomDockVisible(true);
            hideKeyboard();
        });
        homePickerDialog.show();
        try {
            android.view.Window w = homePickerDialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                w.setGravity(Gravity.BOTTOM);
                w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.72f);
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maxH);
            }
        } catch (Exception ignored) {}

        setBottomDockVisible(false);
        updateHomeInlineSelectorUi();
        homeInlineSearchInput.postDelayed(() -> {
            try {
                homeInlineSearchInput.requestFocus();
                showKeyboard(homeInlineSearchInput);
            } catch (Exception ignored) {}
        }, 120);
    }

    private void closeHomeInlineSelector() {
        if (homePickerDialog != null) {
            try { homePickerDialog.dismiss(); } catch (Exception ignored) {}
            homePickerDialog = null;
        }
        if (homeInlineSelectorCard != null) homeInlineSelectorCard.setVisibility(View.GONE);
        if (homeInlineSearchInput != null) homeInlineSearchInput.clearFocus();
        setBottomDockVisible(true);
        hideKeyboard();
    }

    private void updateHomeSelectionMode(boolean useTeams) {
        homeInlineTeamMode = useTeams;
        if (homeModePlayersBtn != null) styleHomeModeButton(homeModePlayersBtn, !useTeams, Color.rgb(84, 142, 247));
        if (homeModeTeamsBtn != null) styleHomeModeButton(homeModeTeamsBtn, useTeams, Color.rgb(23, 187, 171));
        if (homeInlineSearchInput != null) {
            homeInlineSearchInput.setHint(useTeams ? "Search teams" : "Search players");
        }
        if (useTeams) applyDefaultMetricsForRole("both");
        else applyHomePlayerRoleMetricConstraint(true);
        updateHomeMatchupPreview();
        updateHomeInlineSelectorUi();
    }

    private void styleHomeModeButton(TextView tv, boolean active, int accent) {
        if (tv == null) return;
        tv.setSingleLine(true);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setIncludeFontPadding(false);
        tv.setTextColor(active ? Color.WHITE : Color.rgb(188, 198, 216));
        if (active) {
            tv.setBackground(roundedGradientStroke(new int[] {
                    Color.argb(235, Math.max(9, Color.red(accent) / 5), Math.max(14, Color.green(accent) / 5), Math.max(20, Color.blue(accent) / 5)),
                    Color.argb(244, Math.max(16, Color.red(accent) / 2), Math.max(24, Color.green(accent) / 2), Math.max(34, Color.blue(accent) / 2))
            }, 15, Color.argb(118, Color.red(accent), Color.green(accent), Color.blue(accent)), 1));
            tv.setShadowLayer(dp(3), 0, 0, Color.argb(100, Color.red(accent), Color.green(accent), Color.blue(accent)));
        } else {
            tv.setBackground(rounded(Color.TRANSPARENT, 15));
            tv.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        }
    }

    private void updateHomeInlineSelectorUi() {
        if (homeInlineSelectorTitle == null || homeInlineSuggestionList == null) return;
        String side = homeInlineSecondary ? "B" : "A";
        homeInlineSelectorTitle.setText("Select " + (homeInlineTeamMode ? "team" : "player") + " for side " + side);
        if (homeActiveSideBadge != null) {
            int sideColor = homeInlineTeamMode
                    ? displayAccentForTeam(homeInlineSecondary ? homeTeamB : homeTeamA, !homeInlineSecondary)
                    : displayAccentForPlayer(homeInlineSecondary ? homePlayerB : homePlayerA, !homeInlineSecondary);
            homeActiveSideBadge.setText("Now filling side " + side);
            homeActiveSideBadge.setBackground(roundedGradientStroke(new int[] {
                    Color.argb(130, Color.red(sideColor), Color.green(sideColor), Color.blue(sideColor)),
                    Color.argb(232, 8, 12, 22)
            }, 14, Color.argb(170, Color.red(sideColor), Color.green(sideColor), Color.blue(sideColor)), 1));
        }
        String requiredRole = homeRequiredPlayerRoleForCurrentSide();
        if (homeInlineHint != null) homeInlineHint.setText(homeInlineTeamMode
                ? "Search by city, nickname, or abbreviation. Teams fill the matchup circles live."
                : (requiredRole == null
                        ? "Search by player name. Two-way players can match either role."
                        : "Filtered to " + homeRoleLabel(requiredRole) + " so the matchup uses the right stat set."));
        if (homeInlineSearchInput != null) {
            homeInlineSearchInput.setHint(homeInlineTeamMode ? "Search teams" : (requiredRole == null ? "Search players" : "Search " + homeRoleLabel(requiredRole)));
        }
        ListAdapter adapter = homeInlineTeamMode ? (homeInlineSecondary ? homeInlineTeamAdapterB : homeInlineTeamAdapterA) : homeInlinePlayerAdapter;
        homeInlineSuggestionList.setAdapter(adapter);
        filterHomeInlineSuggestions(homeInlineSearchInput != null ? homeInlineSearchInput.getText().toString() : "");
    }

    private String homeRequiredPlayerRoleForCurrentSide() {
        if (homeInlineTeamMode) return null;
        Player anchor = homeInlineSecondary ? homePlayerA : homePlayerB;
        if (anchor == null) return null;
        String role = playerMatchRole(anchor);
        return "two".equals(role) ? null : role;
    }

    private String playerMatchRole(Player p) {
        if (p == null) return "unknown";
        if (isTwoWayPlayer(p)) return "two";
        return isPitcher(p) ? "pitch" : "hit";
    }

    private boolean isTwoWayPlayer(Player p) {
        if (p == null) return false;
        String pos = safe(p.position).toUpperCase(Locale.US);
        String name = safe(p.fullName).toLowerCase(Locale.US);
        if (pos.contains("TWP") || pos.contains("TWO-WAY") || pos.contains("TWO WAY")) return true;
        if (pos.contains("/") && pos.contains("P") && (pos.contains("DH") || pos.contains("OF") || pos.contains("1B") || pos.contains("2B") || pos.contains("3B") || pos.contains("SS"))) return true;
        return p.id == 660271 || name.contains("shohei ohtani");
    }

    private boolean playerCanMatchRole(Player p, String requiredRole) {
        if (p == null) return false;
        if (requiredRole == null || requiredRole.isEmpty() || "two".equals(requiredRole)) return true;
        if (isTwoWayPlayer(p)) return true;
        if ("pitch".equals(requiredRole)) return isPitcher(p);
        if ("hit".equals(requiredRole)) return !isPitcher(p);
        return true;
    }

    private boolean homePlayerAllowedForCurrentSide(Player p) {
        String requiredRole = homeRequiredPlayerRoleForCurrentSide();
        return playerCanMatchRole(p, requiredRole);
    }

    private String homeResolvedPlayerMatchupRole(Player a, Player b) {
        if (a == null || b == null) return null;
        String ra = playerMatchRole(a);
        String rb = playerMatchRole(b);
        if ("two".equals(ra) && "two".equals(rb)) return "both";
        if ("two".equals(ra)) return "pitch".equals(rb) ? "pitch" : "hit";
        if ("two".equals(rb)) return "pitch".equals(ra) ? "pitch" : "hit";
        return ra.equals(rb) ? ra : null;
    }

    private boolean homePlayersCompatible(Player a, Player b) {
        if (a == null || b == null) return true;
        return homeResolvedPlayerMatchupRole(a, b) != null;
    }

    private String homeRoleLabel(String role) {
        if ("pitch".equals(role)) return "pitchers";
        if ("hit".equals(role)) return "position players";
        if ("both".equals(role) || "two".equals(role)) return "all stats";
        return "players";
    }

    private LinkedHashSet<String> metricKeysForSideOnly(String role) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (Metric m : metrics) {
            if ("pitch".equals(role) && "pitch".equals(m.side)) keys.add(m.key);
            else if ("hit".equals(role) && "hit".equals(m.side)) keys.add(m.key);
        }
        return keys;
    }

    private void constrainSelectedMetricsToPlayerRole(String role, boolean fillIfEmpty) {
        if ("two".equals(role)) role = "both";
        if (!"pitch".equals(role) && !"hit".equals(role) && !"both".equals(role)) return;
        LinkedHashSet<String> allowed = metricKeysForPresetAndRole("all", role);
        if (fillIfEmpty && "both".equals(role) && !isTeamMetricContext() && (!selectedHasMetricSide("hit") || !selectedHasMetricSide("pitch"))) {
            applyMetricsForRoleAndPreset("both", metricsManuallyCustomized ? "recommended" : activeComparisonPreset, !metricsManuallyCustomized);
            return;
        }
        boolean hadDisallowed = false;
        for (String key : selectedMetricKeys) if (!allowed.contains(key)) { hadDisallowed = true; break; }
        if (fillIfEmpty && hadDisallowed && !metricsManuallyCustomized) {
            applyMetricsForRoleAndPreset(role, activeComparisonPreset, false);
            return;
        }
        LinkedHashSet<String> kept = new LinkedHashSet<>();
        for (String key : selectedMetricKeys) if (allowed.contains(key)) kept.add(key);
        selectedMetricKeys.clear();
        selectedMetricKeys.addAll(kept);
        if (fillIfEmpty && selectedMetricKeys.isEmpty()) selectedMetricKeys.addAll(allowed);

        LinkedHashSet<String> keptEdge = new LinkedHashSet<>();
        for (String key : keyEdgeMetricKeys) {
            if (selectedMetricKeys.contains(key) && allowed.contains(key)) keptEdge.add(key);
            if (keptEdge.size() >= 8) break;
        }
        keyEdgeMetricKeys.clear();
        keyEdgeMetricKeys.addAll(keptEdge);
        if (keyEdgeMetricKeys.isEmpty()) keyEdgeMetricKeys.addAll(defaultKeyEdgeForRole(role, selectedMetricKeys));

        selectedRankMetricPosition = -1;
        rebuildRankMetricSpinner();
        updateMetricPickerLabel();
        syncMetricChecks();
    }

    private void applyHomePlayerRoleMetricConstraint(boolean fillIfEmpty) {
        String role = allowedMetricRoleForHomePlayers();
        if (role == null) return;
        constrainSelectedMetricsToPlayerRole(role, fillIfEmpty);
    }

    private void filterHomeInlineSuggestions(String raw) {
        if (homeInlineSuggestionList == null) return;
        String q = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if (homeInlineTeamMode) {
            homeInlineTeams.clear();
            if (allTeams.isEmpty()) return;
            for (Team t : allTeams) {
                String name = (t.name + " " + t.abbr).toLowerCase(Locale.US);
                if (q.length() < 1 || name.contains(q)) homeInlineTeams.add(t);
            }
            homeInlineTeams.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
            ListAdapter adapter = homeInlineSuggestionList.getAdapter();
            if (adapter instanceof BaseAdapter) ((BaseAdapter) adapter).notifyDataSetChanged();
            warmHomeInlineImages();
        } else {
            homeInlinePlayers.clear();
            if (allPlayers.isEmpty()) return;
            if (q.length() < 1) {
                // Keep empty until they start typing, so the sheet feels focused and not cluttered.
                homeInlinePlayerAdapter.notifyDataSetChanged();
                return;
            }
            for (Player p : allPlayers) {
                if (!homePlayerAllowedForCurrentSide(p)) continue;
                if (p.searchKey.contains(q)) {
                    homeInlinePlayers.add(p);
                    if (homeInlinePlayers.size() >= 18) break;
                }
            }
            homeInlinePlayerAdapter.notifyDataSetChanged();
            warmHomeInlineImages();
        }
    }

    private void warmHomeInlineImages() {
        try {
            if (homeInlineTeamMode) {
                int n = Math.min(8, homeInlineTeams.size());
                for (int i = 0; i < n; i++) loadTeamLogoBitmap(homeInlineTeams.get(i), b -> {});
            } else {
                int n = Math.min(8, homeInlinePlayers.size());
                for (int i = 0; i < n; i++) loadPlayerImageBitmap(homeInlinePlayers.get(i).id, b -> {});
            }
        } catch (Exception ignored) {}
    }

    private void applyHomeInlineSelection(Player p) {
        if (p == null) return;
        if (homeInlineSecondary) {
            if (homePlayerA != null && homePlayerA.id == p.id) {
                Toast.makeText(this, "Pick a different player for side B.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (homePlayerA != null && !homePlayersCompatible(homePlayerA, p)) {
                String required = homeRequiredPlayerRoleForCurrentSide();
                Toast.makeText(this, "Pick one of the " + homeRoleLabel(required) + " for side B.", Toast.LENGTH_SHORT).show();
                return;
            }
            homePlayerB = p;
        } else {
            Player previousA = homePlayerA;
            homePlayerA = p;
            if (homePlayerB != null && homePlayerB.id == p.id) homePlayerB = null;
            if (homePlayerB != null && !homePlayersCompatible(homePlayerA, homePlayerB)) {
                homePlayerB = null;
                Toast.makeText(this, "Side B cleared — pick a matching " + homeRoleLabel(playerMatchRole(homePlayerA)) + ".", Toast.LENGTH_SHORT).show();
            } else if (previousA != null && homePlayerB != null) {
                String oldRole = homeResolvedPlayerMatchupRole(previousA, homePlayerB);
                String newRole = homeResolvedPlayerMatchupRole(homePlayerA, homePlayerB);
                if (oldRole != null && newRole != null && !oldRole.equals(newRole)) {
                    homePlayerB = null;
                    Toast.makeText(this, "Side B cleared — pick a new matching player.", Toast.LENGTH_SHORT).show();
                }
            }
        }
        applyHomePlayerRoleMetricConstraint(true);
        updateHomeMatchupPreview();
        afterHomeSelectionApplied();
    }

    private void applyHomeInlineSelection(Team t) {
        if (t == null) return;
        if (homeInlineSecondary) {
            if (homeTeamA != null && homeTeamA.id == t.id) {
                Toast.makeText(this, "Pick a different team for side B.", Toast.LENGTH_SHORT).show();
                return;
            }
            homeTeamB = t;
        } else {
            homeTeamA = t;
            if (homeTeamB != null && homeTeamB.id == t.id) homeTeamB = null;
        }
        updateHomeMatchupPreview();
        afterHomeSelectionApplied();
    }

    private void afterHomeSelectionApplied() {
        if (homeInlineSearchInput != null) homeInlineSearchInput.setText("");
        if (!homeInlineSecondary && ((homeInlineTeamMode && homeTeamB == null) || (!homeInlineTeamMode && homePlayerB == null))) {
            homeInlineSecondary = true;
            Toast.makeText(this, "Side A set — now pick side B", Toast.LENGTH_SHORT).show();
            updateHomeInlineSelectorUi();
            if (homeInlineSearchInput != null) {
                homeInlineSearchInput.requestFocus();
                showKeyboard(homeInlineSearchInput);
            }
        } else {
            closeHomeInlineSelector();
        }
    }

    private void updateHomeMatchupPreview() {
        boolean teamSel = homeInlineTeamMode;
        if (homeSelectAText != null) homeSelectAText.setText(teamSel ? shortTeamLabel(homeTeamA, "LEFT") : shortPlayerLabel(homePlayerA, "LEFT"));
        if (homeSelectBText != null) {
            if (teamSel || homePlayerB != null || homePlayerA == null) homeSelectBText.setText(teamSel ? shortTeamLabel(homeTeamB, "RIGHT") : shortPlayerLabel(homePlayerB, "RIGHT"));
            else {
                String role = playerMatchRole(homePlayerA);
                homeSelectBText.setText("two".equals(role) ? "RIGHT" : homeRoleLabel(role).toUpperCase(Locale.US));
            }
        }
        boolean hasA = teamSel ? homeTeamA != null : homePlayerA != null;
        boolean hasB = teamSel ? homeTeamB != null : homePlayerB != null;
        int accentA = teamSel ? displayAccentForTeam(homeTeamA, true) : displayAccentForPlayer(homePlayerA, true);
        int accentB = teamSel ? displayAccentForTeam(homeTeamB, false) : displayAccentForPlayer(homePlayerB, false);
        styleHomeCircle(homeCircleAFrame, accentA, hasA, true);
        styleHomeCircle(homeCircleBFrame, accentB, hasB, false);
        styleHomeSelectorPill(homeSelectAText, accentA, hasA, true);
        styleHomeSelectorPill(homeSelectBText, accentB, hasB, false);
        styleHomePreviewShell(hasA && hasB, accentA, accentB);
        if (homeEnergyView != null) homeEnergyView.setEnergy(hasA && hasB, accentA, accentB);
        styleHomeActionBadge(homeCircleAActionBadge, accentA, hasA);
        styleHomeActionBadge(homeCircleBActionBadge, accentB, hasB);
        if (homeCircleAClearBadge != null) homeCircleAClearBadge.setVisibility(hasA ? View.VISIBLE : View.GONE);
        if (homeCircleBClearBadge != null) homeCircleBClearBadge.setVisibility(hasB ? View.VISIBLE : View.GONE);
        styleHomeVsAndCreate(hasA && hasB, accentA, accentB);
        if (teamSel) {
            bindHomePreviewEntity(homeTeamA, null, homeCircleAImage, homeCircleAText, true);
            bindHomePreviewEntity(homeTeamB, null, homeCircleBImage, homeCircleBText, false);
        } else {
            bindHomePreviewEntity(null, homePlayerA, homeCircleAImage, homeCircleAText, true);
            bindHomePreviewEntity(null, homePlayerB, homeCircleBImage, homeCircleBText, false);
        }
    }

    private void styleHomeCircle(FrameLayout frame, int accent, boolean selected, boolean leftSide) {
        if (frame == null) return;
        int glow = selected ? accent : (leftSide ? Color.rgb(84, 142, 247) : Color.rgb(247, 197, 77));
        frame.setBackground(layeredCircleButton(glow, selected));
        frame.setElevation(selected ? dp(14) : dp(4));
        View glowView = leftSide ? homeCircleAGlow : homeCircleBGlow;
        if (glowView != null) {
            glowView.setBackground(circleGlow(glow, selected));
            glowView.setAlpha(selected ? 0.58f : 0.34f);
        }
    }

    private Drawable layeredCircleButton(int accent, boolean selected) {
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(new Drawable[] {
                circleGlow(accent, selected),
                roundedGradientStroke(new int[] {
                        Color.rgb(2, 5, 10),
                        mixColor(accent, Color.rgb(3, 7, 13), selected ? 0.82f : 0.90f),
                        Color.rgb(2, 5, 10)
                }, 65, Color.argb(selected ? 145 : 90, Color.red(accent), Color.green(accent), Color.blue(accent)), selected ? 1 : 1)
        });
        int inset = selected ? dp(8) : dp(8);
        layers.setLayerInset(1, inset, inset, inset, inset);
        return layers;
    }

    private Drawable circleGlow(int accent, boolean selected) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        gd.setGradientRadius(dp(selected ? 86 : 62));
        gd.setColors(new int[] {
                Color.argb(selected ? 120 : 54, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.argb(selected ? 78 : 30, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.argb(selected ? 22 : 8, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT
        });
        return gd;
    }

    private TextView homeClearBadge() {
        TextView tv = text("×", 13, Color.rgb(224, 233, 246), true);
        tv.setGravity(Gravity.CENTER);
        tv.setIncludeFontPadding(false);
        tv.setAlpha(0.88f);
        tv.setBackground(roundedStroke(Color.argb(122, 5, 9, 17), Color.argb(104, 255, 255, 255), 13, 1));
        tv.setForeground(ripple(false));
        attachPremiumPress(tv, 0.92f);
        tv.setVisibility(View.GONE);
        return tv;
    }

    private void clearHomeMatchupSide(boolean secondarySide) {
        if (homeInlineTeamMode) {
            if (secondarySide) homeTeamB = null;
            else homeTeamA = null;
        } else {
            if (secondarySide) homePlayerB = null;
            else homePlayerA = null;
            applyHomePlayerRoleMetricConstraint(true);
        }
        updateHomeMatchupPreview();
        if (homeInlineSearchInput != null) filterHomeInlineSuggestions(homeInlineSearchInput.getText() == null ? "" : homeInlineSearchInput.getText().toString());
    }

    private TextView circleActionBadge(String label, int accent) {
        TextView tv = text(label, 24, Color.WHITE, true);
        tv.setGravity(Gravity.CENTER);
        tv.setIncludeFontPadding(false);
        tv.setPadding(0, 0, 0, dp(1));
        tv.setBackground(roundedGradientStroke(new int[] {
                Color.argb(252, 10, 13, 20),
                Color.argb(242, Color.red(accent), Math.max(20, Color.green(accent) / 2), Math.max(20, Color.blue(accent) / 2))
        }, 22, Color.argb(235, 255, 255, 255), 2));
        tv.setShadowLayer(dp(5), 0, 0, Color.argb(210, Color.red(accent), Color.green(accent), Color.blue(accent)));
        return tv;
    }

    private void styleHomeActionBadge(TextView badge, int accent, boolean selected) {
        if (badge == null) return;
        // v97: the center + is only an empty-slot affordance. Once selected, the circle itself remains tappable.
        badge.setVisibility(selected ? View.GONE : View.VISIBLE);
        if (selected) return;
        badge.setText("+");
        badge.setTextSize(28);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setTextColor(Color.WHITE);
        badge.setBackground(roundedGradientStroke(new int[] {
                Color.argb(236, 10, 13, 20),
                mixColor(accent, Color.rgb(15, 11, 8), 0.34f),
                Color.argb(232, 8, 10, 16)
        }, 24, Color.argb(245, Color.red(accent), Color.green(accent), Color.blue(accent)), 2));
        badge.setShadowLayer(dp(11), 0, 0, Color.argb(240, Color.red(accent), Color.green(accent), Color.blue(accent)));
    }

    private void styleHomePreviewShell(boolean ready, int accentA, int accentB) {
        if (homeMatchupPreview == null) return;
        if (ready) {
            int neonA = boostNeonColor(accentA, 1.16f, 1.08f);
            int neonB = boostNeonColor(accentB, 1.16f, 1.08f);
            int mid = boostNeonColor(mixColor(neonA, neonB, 0.50f), 1.06f, 1.02f);
            int left = mixColor(neonA, Color.rgb(2, 5, 11), 0.42f);
            int leftMid = mixColor(mixColor(neonA, mid, 0.28f), Color.rgb(3, 7, 14), 0.34f);
            int center = mixColor(mid, Color.rgb(6, 10, 18), 0.34f);
            int rightMid = mixColor(mixColor(mid, neonB, 0.72f), Color.rgb(3, 7, 14), 0.34f);
            int right = mixColor(neonB, Color.rgb(2, 5, 11), 0.42f);
            int stroke = boostNeonColor(mixColor(neonA, neonB, 0.5f), 1.02f, 1.05f);
            homeMatchupPreview.setBackground(roundedGradientStroke(new int[] {
                    left,
                    leftMid,
                    center,
                    rightMid,
                    right
            }, 26, Color.argb(232, Color.red(stroke), Color.green(stroke), Color.blue(stroke)), 1));
        } else {
            homeMatchupPreview.setBackground(roundedGradientStroke(new int[] {
                    Color.rgb(5, 10, 20),
                    Color.rgb(8, 16, 30),
                    Color.rgb(5, 12, 22)
            }, 26, Color.argb(96, 242, 194, 68), 1));
        }
    }

    private void styleHomeSelectorPill(TextView tv, int accent, boolean selected, boolean leftSide) {
        if (tv == null) return;
        int c = selected ? accent : Color.rgb(162, 174, 194);
        tv.setTextColor(selected ? mixColor(Color.WHITE, c, 0.30f) : c);
        tv.setTextSize(selected ? 10 : 9);
        tv.setLetterSpacing(selected ? 0.10f : 0.12f);
        tv.setBackground(roundedGradientStroke(new int[] {
                Color.argb(selected ? 44 : 18, Color.red(c), Color.green(c), Color.blue(c)),
                Color.argb(118, 5, 8, 15),
                Color.argb(selected ? 34 : 14, Color.red(c), Color.green(c), Color.blue(c))
        }, 11, Color.argb(selected ? 142 : 60, Color.red(c), Color.green(c), Color.blue(c)), 1));
        tv.setShadowLayer(selected ? dp(2) : 0, 0, 0, selected ? Color.argb(96, Color.red(c), Color.green(c), Color.blue(c)) : Color.TRANSPARENT);
    }

    private void styleHomeVsAndCreate(boolean ready, int accentA, int accentB) {
        if (homeVsBadge != null) {
            homeVsBadge.setBackground(splitVsDrawable(accentA, accentB, ready));
            homeVsBadge.setShadowLayer(dp(ready ? 10 : 5), 0, 0,
                    ready ? Color.argb(220, 255, 220, 92) : Color.argb(120, 255, 216, 86));
            homeVsBadge.setTextSize(ready ? 16 : 15);
        }
        if (homeCreateButton != null) {
            homeCreateButton.setSingleLine(true);
            homeCreateButton.setEllipsize(null);
            if (ready) {
                int edge = mixColor(accentA, accentB, 0.5f);
                homeCreateButton.setText("⚡  CREATE MATCHUP  ❯");
                homeCreateButton.setTextSize(12);
                homeCreateButton.setLetterSpacing(0.055f);
                homeCreateButton.setTextColor(Color.rgb(14, 10, 2));
                homeCreateButton.setShadowLayer(dp(3), 0, dp(1), Color.argb(150, 255, 249, 190));
                homeCreateButton.setBackground(roundedGradientStroke(new int[] {
                        Color.rgb(255, 248, 138),
                        Color.rgb(255, 222, 66),
                        Color.rgb(236, 164, 24),
                        mixColor(edge, Color.rgb(118, 72, 6), 0.50f)
                }, 21, Color.argb(255, 255, 244, 138), 2));
            } else {
                homeCreateButton.setText("SELECT TWO FIRST  ❯");
                homeCreateButton.setTextSize(12);
                homeCreateButton.setLetterSpacing(0.052f);
                homeCreateButton.setTextColor(Color.rgb(130, 139, 154));
                homeCreateButton.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
                homeCreateButton.setBackground(roundedGradientStroke(new int[] {
                        Color.rgb(26, 31, 42),
                        Color.rgb(18, 23, 34),
                        Color.rgb(12, 17, 27)
                }, 21, Color.argb(92, 130, 145, 164), 1));
            }
        }
    }

    private Drawable splitVsDrawable(int accentA, int accentB, boolean ready) {
        int left = ready ? mixColor(boostNeonColor(accentA, 1.14f, 1.08f), Color.rgb(5, 9, 16), 0.12f) : Color.rgb(8, 12, 20);
        int seam = ready ? boostNeonColor(mixColor(accentA, accentB, 0.50f), 1.06f, 1.04f) : Color.rgb(16, 22, 34);
        int right = ready ? mixColor(boostNeonColor(accentB, 1.14f, 1.08f), Color.rgb(5, 9, 16), 0.12f) : Color.rgb(19, 27, 41);
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {
                left,
                mixColor(left, seam, 0.32f),
                seam,
                mixColor(seam, right, 0.68f),
                right
        });
        fill.setShape(GradientDrawable.RECTANGLE);
        fill.setCornerRadius(dp(24));
        fill.setStroke(dp(ready ? 2 : 1), ready ? Color.argb(225, Color.red(seam), Color.green(seam), Color.blue(seam)) : Color.argb(96, 255, 215, 90));
        return fill;
    }

    private void bindHomePreviewEntity(Team team, Player player, ImageView image, TextView fallback, boolean leftSide) {
        if (image == null || fallback == null) return;
        if (team != null) {
            image.setVisibility(View.VISIBLE);
            fallback.setVisibility(View.GONE);
            image.clearColorFilter();
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setPadding(dp(10), dp(10), dp(10), dp(10));
            int teamAccent = boostNeonColor(displayAccentForTeam(team, leftSide), 1.10f, 1.06f);
            String abbr = safe(team.abbr).toUpperCase(Locale.US);
            int hotCore = mixColor(teamAccent, Color.WHITE, 0.16f);
            int midTone = mixColor(teamAccent, Color.rgb(10, 16, 28), 0.28f);
            int deepTone = mixColor(teamAccent, Color.rgb(4, 7, 14), 0.46f);
            if (abbr.equals("NYY")) {
                hotCore = mixColor(Color.rgb(225, 233, 244), teamAccent, 0.38f);
                midTone = mixColor(Color.rgb(122, 142, 178), teamAccent, 0.56f);
                deepTone = mixColor(teamAccent, Color.rgb(8, 12, 20), 0.30f);
            } else if (abbr.equals("COL")) {
                hotCore = mixColor(Color.rgb(233, 212, 255), teamAccent, 0.42f);
                midTone = mixColor(Color.rgb(165, 118, 255), teamAccent, 0.56f);
                deepTone = mixColor(teamAccent, Color.rgb(10, 7, 20), 0.28f);
            } else if (abbr.equals("LAD")) {
                hotCore = mixColor(Color.rgb(214, 235, 255), teamAccent, 0.26f);
                midTone = mixColor(Color.rgb(56, 156, 255), teamAccent, 0.44f);
                deepTone = mixColor(Color.rgb(5, 24, 60), teamAccent, 0.20f);
            } else if (abbr.equals("STL")) {
                hotCore = Color.rgb(138, 14, 34);
                midTone = Color.rgb(92, 10, 26);
                deepTone = Color.rgb(28, 5, 12);
            } else if (abbr.equals("SD") || abbr.equals("SDP")) {
                hotCore = Color.rgb(82, 58, 39);
                midTone = Color.rgb(47, 36, 29);
                deepTone = Color.rgb(16, 10, 7);
            }
            image.setBackground(roundedGradient(new int[] { hotCore, midTone, deepTone }, 60));
            loadTeamLogo(team, image);
            return;
        }
        if (player != null) {
            image.setVisibility(View.VISIBLE);
            fallback.setVisibility(View.GONE);
            image.clearColorFilter();
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setPadding(dp(2), dp(2), dp(2), dp(2));
            int playerAccent = displayAccentForPlayer(player, leftSide);
            image.setBackground(roundedGradient(new int[] { Color.rgb(4, 7, 14), mixColor(playerAccent, Color.rgb(4, 7, 14), 0.78f) }, 60));
            loadPlayerImage(player.id, image);
            return;
        }
        image.clearColorFilter();
        image.setImageDrawable(null);
        image.setVisibility(View.GONE);
        fallback.setVisibility(View.VISIBLE);
        fallback.setText("");
    }

    private int displayAccentForTeam(Team team, boolean leftSide) {
        if (team == null) return leftSide ? Color.rgb(84, 142, 247) : Color.rgb(247, 197, 77);
        TeamPalette p = paletteForTeam(team);
        String a = safe(team.abbr).toUpperCase(Locale.US);
        if (a.equals("SD") || a.equals("SDP")) return Color.rgb(255, 205, 40);
        if (a.equals("COL")) return Color.rgb(133, 66, 255);
        if (a.equals("LAD")) return Color.rgb(48, 156, 255);
        if (a.equals("SF") || a.equals("SFG")) return Color.rgb(255, 108, 36);
        if (a.equals("PIT")) return Color.rgb(255, 205, 52);
        if (a.equals("MIL")) return Color.rgb(255, 205, 52);
        if (a.equals("ATH") || a.equals("OAK")) return Color.rgb(0, 122, 71);
        if (a.equals("AZ") || a.equals("ARI")) return Color.rgb(46, 226, 239);
        if (a.equals("SEA")) return Color.rgb(0, 201, 168);
        if (a.equals("TB") || a.equals("TBR")) return Color.rgb(143, 203, 255);
        if (a.equals("NYM")) return Color.rgb(255, 116, 26);
        if (a.equals("DET")) return Color.rgb(255, 106, 32);
        if (a.equals("HOU")) return Color.rgb(255, 122, 35);
        if (a.equals("PHI")) return Color.rgb(255, 41, 63);
        int primaryLum = colorLuminance(p.primary);
        int secondaryLum = colorLuminance(p.secondary);
        int base = p.primary;
        if (primaryLum < 86 && secondaryLum > primaryLum + 40) base = p.secondary;
        return ensureReadableColor(base, leftSide ? 192 : 186);
    }

    private int displayAccentForPlayer(Player player, boolean leftSide) {
        if (player == null) return leftSide ? Color.rgb(84, 142, 247) : Color.rgb(247, 197, 77);
        TeamPalette p = paletteForAbbr(player.teamAbbr);
        return ensureReadableColor(p.primary, leftSide ? 194 : 188);
    }

    private String shortPlayerLabel(Player p, String empty) {
        if (p == null || p.fullName == null || p.fullName.isEmpty()) return empty;
        String[] parts = p.fullName.trim().split("\\s+");
        if (parts.length == 0) return empty;
        String label = parts[parts.length - 1];
        if (parts.length >= 2) {
            String suffix = label.replace(".", "").toLowerCase(Locale.US);
            if (suffix.equals("jr") || suffix.equals("sr") || suffix.equals("ii") || suffix.equals("iii") || suffix.equals("iv")) {
                label = parts[parts.length - 2] + " " + parts[parts.length - 1];
            }
        }
        if (label.length() > 13) label = label.substring(0, 13);
        return label;
    }

    private String shortTeamLabel(Team t, String empty) {
        if (t == null) return empty;
        if (t.abbr != null && !t.abbr.isEmpty()) return t.abbr.toUpperCase(Locale.US);
        return t.name == null ? empty : t.name;
    }

    private void performHomeCreateMatchup() {
        closeHomeInlineSelector();
        if (homeInlineTeamMode) {
            if (homeTeamA == null || homeTeamB == null) {
                Toast.makeText(this, "Select two teams first.", Toast.LENGTH_SHORT).show();
                return;
            }
            selectedTeam = homeTeamA;
            compareTeam = homeTeamB;
            teamMode = true;
            constrainSelectedMetricsToPlayerRole("both", true);
            enterMatchupCardFromHome();
            compareTeamsSideBySide(selectedTeam, compareTeam, currentSeason());
            return;
        }
        if (homePlayerA == null || homePlayerB == null) {
            Toast.makeText(this, "Select two players first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String matchupRole = homeResolvedPlayerMatchupRole(homePlayerA, homePlayerB);
        if (matchupRole == null) {
            homePlayerB = null;
            updateHomeMatchupPreview();
            Toast.makeText(this, "Pick two hitters or two pitchers. Two-way players can match either.", Toast.LENGTH_LONG).show();
            return;
        }
        constrainSelectedMetricsToPlayerRole(matchupRole, true);
        selectedPlayer = homePlayerA;
        comparePlayer = homePlayerB;
        teamMode = false;
        enterMatchupCardFromHome();
        comparePlayersSideBySide(selectedPlayer, comparePlayer, currentSeason());
    }

    private void enterMatchupCardFromHome() {
        activePrimaryTab = TAB_MATCHUP;
        headToHeadMode = true;
        rankingsModeActive = false;
        expectedMode = false;
        hideKeyboard();
        if (homeBox != null) homeBox.setVisibility(View.GONE);
        // Home-created matchups should land directly on the matchup card, not reopen the picker workspace.
        if (form != null) form.setVisibility(View.GONE);
        if (standingsBox != null) standingsBox.setVisibility(View.GONE);
        if (resultsBox != null) resultsBox.setVisibility(View.VISIBLE);
        applyHeadToHeadVisibility();
        updateAnalysisModeButtons();
        updateViewModeButtons();
        updateBottomNavSelection();
    }

    private TextView homeStatsSelectorButton() {
        TextView tv = text("Stats / Edge\n0 shown · 0 card", 9, Color.rgb(226, 236, 248), true);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(false);
        tv.setMaxLines(2);
        tv.setIncludeFontPadding(false);
        tv.setLineSpacing(0, 0.96f);
        tv.setLetterSpacing(0.02f);
        tv.setPadding(dp(6), dp(7), dp(6), dp(7));
        tv.setBackground(roundedGradientStroke(new int[] {
                Color.argb(246, 7, 12, 23),
                Color.argb(236, 14, 31, 48),
                Color.argb(226, 9, 50, 54)
        }, 17, Color.argb(104, 118, 235, 225), 1));
        tv.setForeground(ripple(true));
        return tv;
    }

    private void openHomeStatsPicker() {
        teamMode = homeInlineTeamMode;
        showMetricPicker();
    }

    private TextView homeSegmentButton(String label, int accent) {
        TextView tv = text(label, 10, Color.WHITE, true);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setMaxLines(1);
        tv.setIncludeFontPadding(false);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setPadding(dp(6), dp(8), dp(6), dp(8));
        tv.setBackground(rounded(Color.TRANSPARENT, 15));
        tv.setForeground(ripple(true));
        return tv;
    }

    private TextView homePrimaryButton(String label, int start, int end) {
        TextView tv = text(label, 12, Color.rgb(17, 20, 28), true);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setMaxLines(1);
        tv.setEllipsize(null);
        tv.setIncludeFontPadding(false);
        tv.setLetterSpacing(0.035f);
        tv.setPadding(dp(10), dp(15), dp(10), dp(15));
        tv.setBackground(roundedGradientStroke(new int[] { Color.rgb(255, 240, 112), start, end, Color.rgb(116, 72, 10) }, 21, Color.argb(210, 255, 244, 138), 2));
        tv.setForeground(ripple(true));
        return tv;
    }

    private TextView homeSecondaryButton(String label, int accent) {
        TextView tv = text(label, 12, Color.WHITE, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(10), dp(12), dp(10), dp(12));
        tv.setBackground(roundedGradient(new int[] {
                Color.argb(244, 8, 14, 25),
                Color.argb(230, Math.max(9, Color.red(accent) / 6), Math.max(12, Color.green(accent) / 6), Math.max(18, Color.blue(accent) / 6))
        }, 18));
        tv.setForeground(ripple(true));
        return tv;
    }

    private TextView homeSelectorPill(String label, int accent) {
        TextView tv = text(label, 9, accent, true);
        tv.setGravity(Gravity.CENTER);
        tv.setIncludeFontPadding(false);
        tv.setPadding(dp(5), dp(3), dp(5), dp(3));
        tv.setLetterSpacing(0.12f);
        tv.setBackground(roundedStroke(Color.argb(20, 0, 0, 0), Color.argb(80, Color.red(accent), Color.green(accent), Color.blue(accent)), 11, 1));
        return tv;
    }

    private LinearLayout dashboardCard(String glyph, String title, int start, int end, int accent) {
        LinearLayout card = verticalCard(26, new int[] { start, end });
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setForeground(ripple(true));
        card.setElevation(dp(2));

        TextView icon = text(glyph, glyph.length() > 1 ? 17 : 24, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundedGradient(new int[] {
                Color.argb(82, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.argb(20, Color.red(accent), Color.green(accent), Color.blue(accent))
        }, 44));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(70), dp(70));
        iconLp.setMargins(0, 0, 0, dp(7));
        card.addView(icon, iconLp);

        TextView t = text(title, 15, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        card.addView(t);
        return card;
    }

    private void attachPremiumPress(View view, float pressedScale) {
        if (view == null) return;
        view.setClickable(true);
        view.setOnTouchListener((v, event) -> {
            if (event == null) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(pressedScale).scaleY(pressedScale).alpha(0.92f).setDuration(70).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
            }
            return false;
        });
    }

    private LinearLayout buildBottomAppNav() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(8), dp(1), dp(8), dp(2));
        outer.setBackgroundColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 20) {
            outer.setOnApplyWindowInsetsListener((v, insets) -> {
                int bottom = Math.max(dp(2), insets.getSystemWindowInsetBottom());
                v.setPadding(dp(8), dp(1), dp(8), bottom);
                return insets;
            });
        }

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setPadding(dp(5), dp(2), dp(5), dp(2));
        dock.setBackground(roundedGradient(new int[] {
                Color.argb(248, 5, 9, 17),
                Color.argb(242, 8, 18, 33),
                Color.argb(236, 5, 27, 37)
        }, 20));
        dock.setElevation(dp(5));
        outer.addView(dock, matchWrap());

        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(0, -2, 1);
        itemLp.setMargins(dp(1), 0, dp(1), 0);
        bottomHomeTab = createBottomTab("⌂", "Home");
        bottomMatchupTab = createBottomTab("⚔", "Matchup");
        bottomSearchTab = createBottomTab("♙", "Search");
        bottomRankingsTab = createBottomTab("▥", "Rankings");
        dock.addView(bottomHomeTab, itemLp);
        dock.addView(bottomMatchupTab, itemLp);
        dock.addView(bottomSearchTab, itemLp);
        dock.addView(bottomRankingsTab, itemLp);
        updateBottomNavSelection();
        return outer;
    }

    private LinearLayout createBottomTab(String iconText, String labelText) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(2), dp(2), dp(2), dp(1));
        TextView icon = text(iconText, 21, Color.rgb(206, 216, 232), true);
        icon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(38), dp(26));
        item.addView(icon, iconLp);
        TextView label = text(labelText, 7, Color.rgb(186, 198, 216), true);
        label.setGravity(Gravity.CENTER);
        item.addView(label);
        View line = new View(this);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(dp(20), dp(2));
        lineLp.setMargins(0, dp(1), 0, 0);
        item.addView(line, lineLp);
        if ("Home".equals(labelText)) { bottomHomeIcon = icon; bottomHomeLabel = label; bottomHomeLine = line; }
        else if ("Matchup".equals(labelText)) { bottomMatchupIcon = icon; bottomMatchupLabel = label; bottomMatchupLine = line; }
        else if ("Search".equals(labelText)) { bottomSearchIcon = icon; bottomSearchLabel = label; bottomSearchLine = line; }
        else if ("Rankings".equals(labelText)) { bottomRankingsIcon = icon; bottomRankingsLabel = label; bottomRankingsLine = line; }
        return item;
    }

    private void updateBottomNavSelection() {
        styleBottomTab(bottomHomeTab, bottomHomeIcon, bottomHomeLabel, bottomHomeLine, activePrimaryTab == TAB_HOME, Color.rgb(245, 198, 74));
        styleBottomTab(bottomMatchupTab, bottomMatchupIcon, bottomMatchupLabel, bottomMatchupLine, activePrimaryTab == TAB_MATCHUP, Color.rgb(99, 166, 255));
        styleBottomTab(bottomSearchTab, bottomSearchIcon, bottomSearchLabel, bottomSearchLine, activePrimaryTab == TAB_PROFILE, Color.rgb(245, 198, 74));
        styleBottomTab(bottomRankingsTab, bottomRankingsIcon, bottomRankingsLabel, bottomRankingsLine, activePrimaryTab == TAB_RANKINGS, Color.rgb(100, 226, 218));
    }

    private void styleBottomTab(LinearLayout item, TextView icon, TextView label, View line, boolean active, int accent) {
        if (item == null || icon == null || label == null || line == null) return;
        item.setBackground(rounded(Color.TRANSPARENT, 16));
        icon.setTextColor(active ? accent : Color.rgb(198, 208, 224));
        label.setTextColor(active ? Color.WHITE : Color.rgb(154, 166, 188));
        line.setBackground(rounded(active ? accent : Color.TRANSPARENT, 2));
        icon.setBackground(rounded(Color.TRANSPARENT, 14));
    }

    private void setPrimaryTab(int tab) {
        activePrimaryTab = tab;
        if (tab == TAB_HOME) {
            hideKeyboard();
            if (homeBox != null) homeBox.setVisibility(View.VISIBLE);
            if (form != null) form.setVisibility(View.GONE);
            if (resultsBox != null) resultsBox.setVisibility(View.GONE);
            if (standingsBox != null) standingsBox.setVisibility(View.GONE);
            updateBottomNavSelection();
            return;
        }
        if (homeBox != null) homeBox.setVisibility(View.GONE);
        if (form != null) form.setVisibility(View.VISIBLE);
        if (tab == TAB_MATCHUP) {
            setHeadToHeadMode(true);
        } else if (tab == TAB_PROFILE) {
            headToHeadMode = false;
            rankingsModeActive = false;
            expectedMode = false;
            applyHeadToHeadVisibility();
            updateAnalysisModeButtons();
            updateViewModeButtons();
            resultsBox.setVisibility(View.GONE);
            boolean hasSelection = (teamMode && selectedTeam != null) || (!teamMode && selectedPlayer != null);
            if (hasSelection) openProfileForCurrentSelection();
            else renderSearchLanding();
            statusView.setText(teamMode ? "Search · choose a team to open its profile." : "Search · choose a player to open their profile.");
        } else if (tab == TAB_RANKINGS) {
            showStandings();
        }
        updateBottomNavSelection();
    }

    private void setBottomDockVisible(boolean visible) {
        if (bottomNavHost != null) bottomNavHost.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void keepInputAboveKeyboard(View target, int topPaddingDp) {
        if (mainScroll == null || root == null || target == null) return;
        target.postDelayed(() -> {
            try {
                int[] targetLoc = new int[2];
                int[] rootLoc = new int[2];
                target.getLocationOnScreen(targetLoc);
                root.getLocationOnScreen(rootLoc);
                int y = mainScroll.getScrollY() + targetLoc[1] - rootLoc[1] - dp(topPaddingDp);
                mainScroll.smoothScrollTo(0, Math.max(0, y));
            } catch (Exception ignored) {}
        }, 170);
    }

    private LinearLayout buildStickyModeNav() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        // v77: screenshot-friendly dock. No teal block, no heavy module; it should read like premium app chrome.
        outer.setPadding(dp(12), dp(1), dp(12), dp(3));
        outer.setBackground(rounded(Color.TRANSPARENT, 0));
        outer.setElevation(dp(5));
        if (Build.VERSION.SDK_INT >= 20) {
            outer.setOnApplyWindowInsetsListener((v, insets) -> {
                int bottom = Math.max(dp(3), insets.getSystemWindowInsetBottom() + dp(1));
                v.setPadding(dp(12), dp(1), dp(12), bottom);
                return insets;
            });
        }

        LinearLayout chrome = new LinearLayout(this);
        chrome.setOrientation(LinearLayout.VERTICAL);
        chrome.setPadding(dp(3), dp(3), dp(3), dp(3));
        chrome.setBackground(roundedGradient(new int[] {
                Color.argb(244, 5, 10, 18),
                Color.argb(238, 8, 18, 32),
                Color.argb(232, 7, 28, 42)
        }, 18));
        chrome.setElevation(dp(6));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(1), dp(1), dp(1), dp(1));
        nav.setBackground(roundedStroke(Color.argb(10, 255, 255, 255), Color.argb(32, 255, 255, 255), 15, 1));
        stickyCompareButton = pillButton("Matchup", true);
        stickyProfileButton = pillButton("Profile", false);
        stickyRankingsButton = pillButton("Rankings", false);
        styleStickyNavButtonBase(stickyCompareButton);
        styleStickyNavButtonBase(stickyProfileButton);
        styleStickyNavButtonBase(stickyRankingsButton);
        stickyCompareButton.setForeground(ripple(true));
        stickyProfileButton.setForeground(ripple(true));
        stickyRankingsButton.setForeground(ripple(true));
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, dp(32), 1);
        leftLp.setMargins(dp(2), dp(1), dp(2), dp(1));
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(0, dp(32), 1);
        midLp.setMargins(dp(2), dp(1), dp(2), dp(1));
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, dp(32), 1);
        rightLp.setMargins(dp(2), dp(1), dp(2), dp(1));
        nav.addView(stickyCompareButton, leftLp);
        nav.addView(stickyProfileButton, midLp);
        nav.addView(stickyRankingsButton, rightLp);
        chrome.addView(nav, matchWrap());
        outer.addView(chrome, matchWrap());
        return outer;
    }

    private void styleStickyNavButtonBase(Button b) {
        if (b == null) return;
        b.setAllCaps(false);
        b.setTypeface(tfMedium);
        b.setTextSize(10);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(3), dp(3), dp(3), dp(3));
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setSingleLine(true);
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
    }

    private TextView choiceChip(String label, boolean active) {
        TextView tv = text(label, 13, active ? Color.WHITE : NAVY, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(13), dp(8), dp(13), dp(8));
        tv.setSingleLine(true);
        tv.setBackground(active
                ? roundedGradient(new int[] { NAVY, Color.rgb(24, 62, 109) }, 16)
                : roundedStroke(Color.WHITE, Color.rgb(205, 216, 230), 16, 1));
        tv.setForeground(ripple(active));
        return tv;
    }

    private TextView groupChip(String label) {
        TextView tv = text(label.toUpperCase(Locale.US), 10, MUTED, true);
        tv.setGravity(Gravity.CENTER);
        tv.setLetterSpacing(0.10f);
        tv.setPadding(dp(10), dp(8), dp(8), dp(8));
        tv.setSingleLine(true);
        return tv;
    }

    private void addChipWithMargin(LinearLayout row, View chip) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(3), dp(7), dp(3));
        row.addView(chip, lp);
    }


    private void buildTeamChips() {
        if (teamChipRow != null) {
            teamChipRow.removeAllViews();
            for (int i = 0; i < allTeams.size(); i++) {
                final int index = i;
                Team t = allTeams.get(i);
                boolean active = selectedTeam != null && selectedTeam.key().equals(t.key());
                TextView chip = choiceChip(t.abbr == null || t.abbr.isEmpty() ? initials(t.name) : t.abbr, active);
                chip.setOnClickListener(v -> {
                    selectedTeam = allTeams.get(index);
                    if (teamSpinner != null) teamSpinner.setSelection(index);
                    buildTeamChips();
                    renderSelectionPreview();
                    updateTeamPickerButtons();
                    if (teamMode) refreshAfterPrimarySelection();
                });
                addChipWithMargin(teamChipRow, chip);
            }
        }
        if (compareTeamChipRow != null) {
            compareTeamChipRow.removeAllViews();
            for (int i = 0; i < allTeams.size(); i++) {
                final int index = i;
                Team t = allTeams.get(i);
                boolean active = compareTeam != null && compareTeam.key().equals(t.key());
                TextView chip = choiceChip(t.abbr == null || t.abbr.isEmpty() ? initials(t.name) : t.abbr, active);
                chip.setOnClickListener(v -> {
                    compareTeam = allTeams.get(index);
                    if (compareTeamSpinner != null) compareTeamSpinner.setSelection(index);
                    buildTeamChips();
                    updateTeamPickerButtons();
                    if (teamMode && headToHeadMode) { renderComparePreview(); refreshHeadToHeadIfReady(); }
                });
                addChipWithMargin(compareTeamChipRow, chip);
            }
        }
    }

    private void showTeamPickerDialog(boolean primarySide) {
        if (allTeams.isEmpty()) {
            showError("Team list is still loading.");
            return;
        }
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(6), dp(6), dp(6), dp(2));

        EditText filter = new EditText(this);
        filter.setHint("Search teams");
        filter.setSingleLine(true);
        filter.setTextSize(15);
        filter.setTextColor(Color.WHITE);
        filter.setHintTextColor(Color.rgb(142, 156, 179));
        filter.setPadding(dp(12), dp(10), dp(12), dp(10));
        filter.setBackground(roundedStroke(Color.rgb(10, 15, 26), Color.rgb(50, 67, 94), 16, 1));
        shell.addView(filter, matchWrap());

        ListView list = new ListView(this);
        list.setDividerHeight(0);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setVerticalScrollBarEnabled(true);
        list.setScrollbarFadingEnabled(false);
        list.setFastScrollEnabled(true);
        list.setFastScrollAlwaysVisible(true);
        list.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        list.setBackground(roundedStroke(Color.rgb(7, 12, 22), Color.rgb(38, 54, 78), 18, 1));
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, dp(390));
        listLp.setMargins(0, dp(8), 0, 0);
        shell.addView(list, listLp);

        final ArrayList<Team> visible = new ArrayList<>(allTeams);
        final TeamPickerAdapter adapter = new TeamPickerAdapter(visible, primarySide);
        list.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(primarySide ? (headToHeadMode ? "Choose Team A" : "Choose team") : "Choose Team B")
                .setView(shell)
                .setNegativeButton("Cancel", null)
                .create();

        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s == null ? "" : s.toString().trim().toLowerCase(Locale.US);
                visible.clear();
                for (Team t : allTeams) {
                    if (q.isEmpty() || safe(t.name).toLowerCase(Locale.US).contains(q) || safe(t.abbr).toLowerCase(Locale.US).contains(q)) visible.add(t);
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= visible.size()) return;
            Team t = visible.get(position);
            if (primarySide) {
                selectedTeam = t;
                syncTeamSpinner(teamSpinner, t);
                buildTeamChips();
                renderSelectionPreview();
                statusView.setText(statusTextForMode());
                refreshAfterPrimarySelection();
            } else {
                compareTeam = t;
                syncTeamSpinner(compareTeamSpinner, t);
                buildTeamChips();
                renderComparePreview();
                refreshHeadToHeadIfReady();
            }
            updateTeamPickerButtons();
            dialog.dismiss();
        });

        dialog.setOnShowListener(d -> filter.requestFocus());
        dialog.show();
    }

    private ArrayList<String> teamPickerLabels(ArrayList<Team> teams, boolean primarySide) {
        ArrayList<String> labels = new ArrayList<>();
        for (Team t : teams) {
            boolean active = primarySide
                    ? (selectedTeam != null && selectedTeam.key().equals(t.key()))
                    : (compareTeam != null && compareTeam.key().equals(t.key()));
            labels.add((active ? "✓ " : "   ") + t.name + "  ·  " + t.abbr);
        }
        return labels;
    }

    private void syncTeamSpinner(Spinner spinner, Team team) {
        if (spinner == null || team == null) return;
        for (int i = 0; i < allTeams.size(); i++) {
            if (allTeams.get(i).key().equals(team.key())) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private void showRankMetricPickerDialog() {
        rebuildRankMetricSpinner();
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(6), dp(6), dp(6), dp(2));

        EditText filter = new EditText(this);
        filter.setHint("Search stats");
        filter.setSingleLine(true);
        filter.setTextSize(15);
        filter.setPadding(dp(12), dp(9), dp(12), dp(9));
        filter.setBackground(roundedStroke(Color.WHITE, Color.rgb(202, 215, 232), 16, 1));
        shell.addView(filter, matchWrap());

        ListView list = new ListView(this);
        list.setDividerHeight(1);
        list.setBackground(roundedStroke(Color.WHITE, Color.rgb(226, 233, 243), 16, 1));
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, dp(380));
        listLp.setMargins(0, dp(8), 0, 0);
        shell.addView(list, listLp);

        final ArrayList<Integer> visibleIndexes = new ArrayList<>();
        visibleIndexes.add(-1);
        for (int i = 0; i < rankMetrics.size(); i++) visibleIndexes.add(i);

        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rankMetricPickerLabels(visibleIndexes));
        list.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Choose ranking stat")
                .setView(shell)
                .setNegativeButton("Cancel", null)
                .create();

        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s == null ? "" : s.toString().trim().toLowerCase(Locale.US);
                visibleIndexes.clear();
                if (q.isEmpty() || "all".contains(q)) visibleIndexes.add(-1);
                for (int i = 0; i < rankMetrics.size(); i++) {
                    Metric m = rankMetrics.get(i);
                    String hay = (m.label + " " + m.key + " " + m.group + " " + m.side).toLowerCase(Locale.US);
                    if (q.isEmpty() || hay.contains(q)) visibleIndexes.add(i);
                }
                adapter.clear();
                adapter.addAll(rankMetricPickerLabels(visibleIndexes));
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= visibleIndexes.size()) return;
            selectedRankMetricPosition = visibleIndexes.get(position);
            if (rankMetricSpinner != null) rankMetricSpinner.setSelection(selectedRankMetricPosition < 0 ? 0 : selectedRankMetricPosition + 1);
            refreshRankMetricChips();
            updateRankMetricPickerButton();
            refreshRankingsIfActive();
            dialog.dismiss();
        });

        dialog.setOnShowListener(d -> filter.requestFocus());
        dialog.show();
    }

    private ArrayList<String> rankMetricPickerLabels(ArrayList<Integer> indexes) {
        ArrayList<String> labels = new ArrayList<>();
        for (Integer idx : indexes) {
            if (idx == null || idx < 0) {
                labels.add((selectedRankMetricPosition < 0 ? "✓ " : "   ") + "All selected stats");
            } else if (idx < rankMetrics.size()) {
                Metric m = rankMetrics.get(idx);
                labels.add((selectedRankMetricPosition == idx ? "✓ " : "   ") + m.label + "  ·  " + m.group);
            }
        }
        return labels;
    }

    private void buildSeasonChips() {
        if (seasonChipRow == null) return;
        seasonChipRow.removeAllViews();
        seasonChips.clear();
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int y = currentYear; y >= STATCAST_START_YEAR; y--) {
            final int year = y;
            TextView chip = choiceChip(String.valueOf(year), year == selectedSeasonValue);
            chip.setOnClickListener(v -> setSelectedSeason(year));
            seasonChips.add(chip);
            addChipWithMargin(seasonChipRow, chip);
        }
    }

    private void refreshSeasonChips() {
        for (int i = 0; i < seasonChips.size(); i++) {
            TextView chip = seasonChips.get(i);
            try {
                int year = Integer.parseInt(chip.getText().toString());
                chip.setTextColor(year == selectedSeasonValue ? Color.WHITE : NAVY);
                chip.setBackground(year == selectedSeasonValue
                        ? roundedGradient(new int[] { NAVY, Color.rgb(24, 62, 109) }, 16)
                        : roundedStroke(Color.WHITE, Color.rgb(205, 216, 230), 16, 1));
            } catch (Exception ignored) {}
        }
    }

    private void setSelectedSeason(int year) {
        selectedSeasonValue = year;
        if (seasonSpinner != null && seasonSpinner.getAdapter() != null) {
            for (int i = 0; i < seasonSpinner.getAdapter().getCount(); i++) {
                Object item = seasonSpinner.getAdapter().getItem(i);
                if (item != null && item.toString().equals(String.valueOf(year))) {
                    seasonSpinner.setSelection(i);
                    break;
                }
            }
        }
        refreshSeasonChips();
        if (rankingsModeActive) showStandings();
        else if (headToHeadMode) refreshHeadToHeadIfReady();
        else if (expectedMode) refreshExpectedIfReady();
        else if ((teamMode && selectedTeam != null) || (!teamMode && selectedPlayer != null)) openProfileForCurrentSelection();
    }

    private void refreshRankMetricChips() {
        if (rankMetricChipRow == null) return;
        rankMetricChipRow.removeAllViews();
        rankMetricChips.clear();

        // Keep the old "all stats" view available, but default to OPS/ERA for one-stat rankings.
        TextView allChip = choiceChip("All", selectedRankMetricPosition < 0);
        allChip.setOnClickListener(v -> {
            selectedRankMetricPosition = -1;
            if (rankMetricSpinner != null) rankMetricSpinner.setSelection(0);
            refreshRankMetricChips();
            refreshRankingsIfActive();
        });
        rankMetricChips.add(allChip);
        addChipWithMargin(rankMetricChipRow, allChip);

        boolean hasHit = false, hasPitch = false;
        for (Metric m : rankMetrics) {
            if ("pitch".equals(m.side)) hasPitch = true;
            else hasHit = true;
        }

        if (hasHit) addChipWithMargin(rankMetricChipRow, groupChip("Batting"));
        for (int i = 0; i < rankMetrics.size(); i++) {
            Metric m = rankMetrics.get(i);
            if ("pitch".equals(m.side)) continue;
            addRankMetricChip(i, m);
        }

        if (hasPitch) addChipWithMargin(rankMetricChipRow, groupChip("Pitching"));
        for (int i = 0; i < rankMetrics.size(); i++) {
            Metric m = rankMetrics.get(i);
            if (!"pitch".equals(m.side)) continue;
            addRankMetricChip(i, m);
        }
    }

    private void addRankMetricChip(int index, Metric m) {
        TextView chip = choiceChip(m.label, selectedRankMetricPosition == index);
        chip.setOnClickListener(v -> {
            selectedRankMetricPosition = index;
            if (rankMetricSpinner != null) rankMetricSpinner.setSelection(index + 1);
            refreshRankMetricChips();
            refreshRankingsIfActive();
        });
        rankMetricChips.add(chip);
        addChipWithMargin(rankMetricChipRow, chip);
    }

    private int defaultRankingMetricIndex() {
        String preferredKey = teamMode || homeInlineTeamMode ? "teamRunDiff" : "ops";
        if ((!teamMode && selectedPlayer != null && isPitcher(selectedPlayer)) || ("pitch".equals(preferredMetricSide()) && !teamMode)) {
            preferredKey = "era";
        }
        for (int i = 0; i < rankMetrics.size(); i++) if (rankMetrics.get(i).key.equals(preferredKey)) return i;
        for (int i = 0; i < rankMetrics.size(); i++) if (rankMetrics.get(i).key.equals("ops")) return i;
        for (int i = 0; i < rankMetrics.size(); i++) if (rankMetrics.get(i).key.equals("era")) return i;
        return rankMetrics.isEmpty() ? -1 : 0;
    }

    private Button compactControlButton(String label, boolean active) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextSize(11);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(6), dp(5), dp(6), dp(5));
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setSingleLine(false);
        b.setMaxLines(2);
        b.setEllipsize(null);
        b.setTextColor(active ? Color.WHITE : NAVY);
        b.setBackground(active ? rounded(TEAL_DARK, 14) : roundedStroke(Color.WHITE, Color.rgb(213, 223, 236), 14, 1));
        return b;
    }

    private Button primaryActionButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(8), dp(6), dp(8), dp(6));
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setSingleLine(false);
        b.setMaxLines(2);
        b.setEllipsize(null);
        b.setBackground(roundedGradient(new int[] { TEAL, TEAL_DARK }, 16));
        return b;
    }

    private Button secondaryActionButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(8), dp(6), dp(8), dp(6));
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setSingleLine(false);
        b.setMaxLines(2);
        b.setEllipsize(null);
        b.setBackground(roundedStroke(Color.argb(10, 255, 255, 255), Color.argb(84, 190, 214, 236), 16, 1));
        return b;
    }

    private void updateViewModeButtons() {
        if (standingsButton == null || headToHeadButton == null) return;
        if (compareButton != null) compareButton.setVisibility(View.GONE);
        boolean compareActive = headToHeadMode;
        boolean profileActive = !headToHeadMode && !rankingsModeActive;
        boolean rankingsActive = rankingsModeActive;
        styleIntentButton(headToHeadButton, compareActive, "Matchup");
        if (singleViewButton != null) styleIntentButton(singleViewButton, profileActive, "Profile");
        styleIntentButton(standingsButton, rankingsActive, "Rankings");
        styleIntentButton(stickyCompareButton, compareActive, "Matchup");
        styleIntentButton(stickyProfileButton, profileActive, "Profile");
        styleIntentButton(stickyRankingsButton, rankingsActive, "Rankings");
        styleEntityButton(playerModeButton, !teamMode, "Player");
        styleEntityButton(teamModeButton, teamMode, "Team");
        updateTeamPickerButtons();
        updateRankMetricPickerButton();
        if (profileToolsShell != null) profileToolsShell.setVisibility(profileActive ? View.VISIBLE : View.GONE);
        if (expectedViewButton != null) {
            expectedViewButton.setVisibility(profileActive ? View.VISIBLE : View.GONE);
            styleSubModeButton(expectedViewButton, expectedMode, expectedMode ? "Viewing xStats" : "Show xStats");
        }
        if (rankControlContainer != null) rankControlContainer.setVisibility(rankingsModeActive ? View.VISIBLE : View.GONE);
        if (metricPickerButton != null) metricPickerButton.setVisibility((activePrimaryTab != TAB_HOME && !rankingsModeActive) ? View.VISIBLE : View.GONE);
        updatePrimarySelectorLabel();
        updateControlsVisibility();
        if (primarySelectorLabel != null) primarySelectorLabel.setVisibility(rankingsModeActive ? View.GONE : View.VISIBLE);
        if (rankingsModeActive) {
            if (searchInput != null) searchInput.setVisibility(View.GONE);
            if (suggestionsList != null) suggestionsList.setVisibility(View.GONE);
            if (teamPickerButton != null) teamPickerButton.setVisibility(View.GONE);
            if (teamChipRow != null) teamChipRow.setVisibility(View.GONE);
            if (selectedPreviewBox != null) selectedPreviewBox.setVisibility(View.GONE);
        }
    }

    private void styleIntentButton(Button b, boolean active, String label) {
        if (b == null) return;
        boolean sticky = b == stickyCompareButton || b == stickyProfileButton || b == stickyRankingsButton;
        b.setText(label);
        if (sticky) {
            styleStickyNavButtonBase(b);
            b.setText(active ? "• " + label : label);
            b.setTextColor(active ? Color.rgb(252, 255, 255) : Color.rgb(154, 174, 192));
            b.setBackground(active
                    ? roundedStroke(Color.argb(54, 255, 255, 255), Color.argb(92, 255, 255, 255), 14, 1)
                    : roundedStroke(Color.argb(4, 255, 255, 255), Color.argb(18, 255, 255, 255), 14, 1));
            return;
        }
        b.setTextColor(active ? Color.WHITE : NAVY);
        b.setBackground(active
                ? roundedGradient(new int[] { NAVY, Color.rgb(24, 62, 109) }, 16)
                : roundedStroke(Color.WHITE, LINE, 16, 1));
    }

    private void styleEntityButton(Button b, boolean active, String label) {
        if (b == null) return;
        b.setText(label);
        b.setTextColor(active ? Color.WHITE : Color.rgb(225, 235, 244));
        b.setBackground(active
                ? roundedGradient(new int[] { TEAL, TEAL_DARK }, 16)
                : roundedStroke(Color.argb(8, 255, 255, 255), Color.argb(70, 190, 214, 236), 16, 1));
    }

    private void styleSubModeButton(Button b, boolean active, String label) {
        if (b == null) return;
        b.setText(label);
        b.setTextColor(active ? Color.WHITE : Color.rgb(225, 235, 244));
        b.setBackground(active
                ? roundedGradient(new int[] { Color.rgb(44, 82, 133), NAVY }, 16)
                : roundedStroke(Color.argb(8, 255, 255, 255), Color.argb(70, 190, 214, 236), 16, 1));
    }

    private void updatePrimarySelectorLabel() {
        if (primarySelectorLabel == null) return;
        if (typeModeLabel != null) {
            typeModeLabel.setText(headToHeadMode ? "COMPARE TYPE" : (rankingsModeActive ? "LEADERBOARD TYPE" : "PROFILE TYPE"));
        }
        if (headToHeadMode) {
            primarySelectorLabel.setText(teamMode ? "1 · CHOOSE TEAM A" : "1 · CHOOSE PLAYER A");
            if (compareSelectorLabel != null) compareSelectorLabel.setText(teamMode ? "2 · CHOOSE TEAM B" : "2 · CHOOSE PLAYER B");
            if (searchInput != null) searchInput.setHint(teamMode ? "Choose Team A below" : "Search Player A — Tatis, Soto, Judge, Skenes…");
            if (compareSearchInput != null) compareSearchInput.setHint(teamMode ? "Choose Team B below" : "Search Player B — Ohtani, Judge, Soto…");
        } else if (rankingsModeActive) {
            primarySelectorLabel.setText(teamMode ? "TEAM RANKINGS" : "PLAYER RANKINGS");
            if (searchInput != null) searchInput.setHint(teamMode ? "Optional: choose a team for context" : "Optional: search a player for context");
        } else {
            primarySelectorLabel.setText(teamMode ? "CHOOSE TEAM PROFILE" : "CHOOSE PLAYER PROFILE");
            if (searchInput != null) searchInput.setHint(teamMode ? "Choose a team below" : "Search player — Soto, Judge, Ohtani, Skenes…");
        }
    }

    private void updateControlsVisibility() {
        if (controlsCard == null) return;
        boolean hasSelection = (teamMode && selectedTeam != null) || (!teamMode && selectedPlayer != null);
        controlsCard.setVisibility((hasSelection || rankingsModeActive) ? View.VISIBLE : View.GONE);
    }

    private void updateTeamPickerButtons() {
        if (teamPickerButton != null) {
            String prefix = headToHeadMode ? "Choose Team A" : (rankingsModeActive ? "Team context" : "Choose team");
            String name = selectedTeam == null ? prefix : prefix + ": " + selectedTeam.abbr;
            teamPickerButton.setText(name);
        }
        if (compareTeamPickerButton != null) {
            compareTeamPickerButton.setText(compareTeam == null ? "Choose Team B" : "Team B: " + compareTeam.abbr);
        }
    }

    private void updateRankMetricPickerButton() {
        if (rankMetricPickerButton == null) return;
        Metric m = selectedRankMetric();
        rankMetricPickerButton.setText(m == null ? "Ranking stat: All selected stats" : "Ranking stat: " + m.label);
    }

    private void refreshRankingsIfActive() {
        if (rankingsModeActive && !isBusy()) showStandings();
    }

    private void openProfileForCurrentSelection() {
        activePrimaryTab = TAB_PROFILE;
        if (homeBox != null) homeBox.setVisibility(View.GONE);
        if (form != null) form.setVisibility(View.VISIBLE);
        if (isBusy()) return;
        boolean hasSelection = (teamMode && selectedTeam != null) || (!teamMode && selectedPlayer != null);
        if (!hasSelection) {
            rankingsModeActive = false;
            headToHeadMode = false;
            expectedMode = false;
            updateAnalysisModeButtons();
            applyHeadToHeadVisibility();
            updateViewModeButtons();
            statusView.setText(statusTextForMode());
            return;
        }
        rankingsModeActive = false;
        headToHeadMode = false;
        expectedMode = false;
        lastHeadToHead = null;
        updateAnalysisModeButtons();
        applyHeadToHeadVisibility();
        updateViewModeButtons();
        updateControlsVisibility();
        standingsBox.removeAllViews();
        standingsBox.setVisibility(View.GONE);
        showError(null);
        try {
            int season = currentSeason();
            if (teamMode) {
                if (selectedTeam != null) compareTeam(selectedTeam, season);
            } else {
                if (selectedPlayer != null) comparePlayer(selectedPlayer, season);
            }
        } catch (Exception ignored) {}
    }

    private void scrollToResultsTop() {
        if (mainScroll == null || resultsBox == null) return;
        mainScroll.postDelayed(() -> mainScroll.smoothScrollTo(0, Math.max(0, resultsBox.getTop() - dp(8))), 250);
    }

    private void refreshAfterPrimarySelection() {
        generalRankingsMode = false;
        applyHeadToHeadVisibility();
        updateViewModeButtons();
        if (rankingsModeActive) {
            showStandings();
        } else if (headToHeadMode) {
            renderComparePreview();
            if (!refreshHeadToHeadIfReady()) resultsBox.setVisibility(View.GONE);
        } else if (expectedMode) {
            if (!refreshExpectedIfReady()) resultsBox.setVisibility(View.GONE);
        } else {
            openProfileForCurrentSelection();
        }
    }


    private void updateMetricPickerLabel() {
        String preset = metricPresetNameForRole(selectedMetricKeys, allowedMetricRoleForCurrentContext());
        if (metricPickerButton != null) metricPickerButton.setText("Stats");
        if (homeStatsButton != null) {
            homeStatsButton.setText("Stats / Edge\n" + selectedMetricKeys.size() + " shown · " + keyEdgeMetricKeys.size() + " card");
            homeStatsButton.setTextSize(9);
            homeStatsButton.setMaxLines(2);
            homeStatsButton.setSingleLine(false);
            homeStatsButton.setLineSpacing(0, 0.96f);
        }
    }

    private String preferredMetricSide() {
        if (!teamMode && selectedPlayer != null && isPitcher(selectedPlayer)) return "pitch";
        if (teamMode && selectedMetricKeys.size() > 0) {
            int pitch = 0, hit = 0;
            for (Metric m : metrics) if (selectedMetricKeys.contains(m.key)) { if ("pitch".equals(m.side)) pitch++; else if ("hit".equals(m.side)) hit++; }
            if (pitch > hit) return "pitch";
        }
        return "hit";
    }

    private boolean isPitcher(Player p) {
        if (p == null || p.position == null) return false;
        String pos = p.position.toUpperCase(Locale.US);
        return pos.equals("P") || pos.equals("SP") || pos.equals("RP") || pos.contains("PITCH");
    }

    private ArrayList<Metric> metricsForCurrentContext() {
        StatScope scope = currentStatScope();
        String role = roleForScope(scope);
        ArrayList<Metric> list = new ArrayList<>();
        for (Metric m : metrics) {
            if (roleAllowsMetric(role, m)) list.add(m);
        }
        return list;
    }

    private void rebuildRankMetricSpinner() {
        rankMetrics.clear();
        rankMetrics.addAll(metricsForCurrentContext());
        ArrayList<String> metricLabels = new ArrayList<>();
        metricLabels.add("All");
        for (Metric m : rankMetrics) metricLabels.add(m.label);
        if (rankMetricSpinner != null) {
            ArrayAdapter<String> rankAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, metricLabels);
            rankMetricSpinner.setAdapter(rankAdapter);
        }
        int def = defaultRankingMetricIndex();
        if (selectedRankMetricPosition >= rankMetrics.size()) selectedRankMetricPosition = def;
        if (selectedRankMetricPosition < -1) selectedRankMetricPosition = def;
        if (selectedRankMetricPosition == -1 && def >= 0) selectedRankMetricPosition = def;
        if (rankMetricSpinner != null) rankMetricSpinner.setSelection(selectedRankMetricPosition < 0 ? 0 : selectedRankMetricPosition + 1);
        refreshRankMetricChips();
        updateRankMetricPickerButton();
    }

    private Metric selectedRankMetric() {
        if (rankMetrics.isEmpty()) return metrics[0];
        if (selectedRankMetricPosition < 0) return null;
        return rankMetrics.get(Math.min(selectedRankMetricPosition, rankMetrics.size() - 1));
    }

    private ArrayList<Metric> selectedRankingMetrics() {
        ArrayList<Metric> out = new ArrayList<>();
        String role = roleForScope(currentStatScope());
        for (Metric m : metrics) {
            if (selectedMetricKeys.contains(m.key) && roleAllowsMetric(role, m)) out.add(m);
        }
        if (out.isEmpty()) out.addAll(metricsForCurrentContext());
        return out;
    }

    private void applySmartDefaultForSelection(Player p) {
        String role = playerMatchRole(p);
        role = "two".equals(role) ? "both" : role;
        if (metricsManuallyCustomized || "custom".equals(activeComparisonPreset)) {
            constrainSelectedMetricsToPlayerRole(role, true);
            return;
        }
        applyMetricsForRoleAndPreset(role, activeComparisonPreset, false);
    }


    private StatScope scopeForRole(String role) {
        if ("pitch".equals(role)) return StatScope.PITCH_ONLY;
        if ("hit".equals(role)) return StatScope.HIT_ONLY;
        return StatScope.BOTH;
    }

    private String roleForScope(StatScope scope) {
        if (scope == StatScope.PITCH_ONLY) return "pitch";
        if (scope == StatScope.HIT_ONLY) return "hit";
        return "both";
    }

    private StatScope scopeForPlayer(Player p) {
        String role = playerMatchRole(p);
        if ("pitch".equals(role)) return StatScope.PITCH_ONLY;
        if ("hit".equals(role)) return StatScope.HIT_ONLY;
        return StatScope.BOTH;
    }

    private StatScope scopeForPlayers(Player a, Player b) {
        return scopeForRole(homeResolvedPlayerMatchupRole(a, b));
    }

    private StatScope scopeForHomeContext() {
        if (homeInlineTeamMode) return StatScope.BOTH;
        String resolved = allowedMetricRoleForHomePlayers();
        return scopeForRole(resolved);
    }

    private StatScope currentStatScope() {
        // v145: Scope must come from the content currently being rendered first.
        // Otherwise a stale global Player/Team toggle can leak team metrics into player profiles.
        if (lastHeadToHead != null && resultsBox != null && resultsBox.getVisibility() == View.VISIBLE) {
            return lastHeadToHead.scope == null ? StatScope.BOTH : lastHeadToHead.scope;
        }
        if (lastComparison != null && resultsBox != null && resultsBox.getVisibility() == View.VISIBLE) {
            if (lastComparison.isTeam) return StatScope.BOTH;
            if (lastComparison.player != null) return scopeForPlayer(lastComparison.player);
        }
        if (homeBox != null && homeBox.getVisibility() == View.VISIBLE) return scopeForHomeContext();
        if (teamMode || homeInlineTeamMode) return StatScope.BOTH;
        if (selectedPlayer != null && comparePlayer != null && headToHeadMode) return scopeForPlayers(selectedPlayer, comparePlayer);
        if (selectedPlayer != null) return scopeForPlayer(selectedPlayer);
        return StatScope.BOTH;
    }

    private String allowedMetricRoleForCurrentContext() {
        return roleForScope(currentStatScope());
    }

    private String allowedMetricRoleForHomePlayers() {
        String resolved = homeResolvedPlayerMatchupRole(homePlayerA, homePlayerB);
        if (resolved != null) return resolved;
        Player anchor = homePlayerA != null ? homePlayerA : homePlayerB;
        if (anchor == null) return null;
        String role = playerMatchRole(anchor);
        return "two".equals(role) ? "both" : role;
    }

    private boolean isTeamMetricContext() {
        // v145: Rendered content wins over global toggle state.
        if (lastHeadToHead != null && resultsBox != null && resultsBox.getVisibility() == View.VISIBLE) return lastHeadToHead.isTeam;
        if (lastComparison != null && resultsBox != null && resultsBox.getVisibility() == View.VISIBLE) return lastComparison.isTeam;
        if (teamMode || homeInlineTeamMode) return true;
        return false;
    }

    private boolean roleAllowsMetric(String role, Metric m) {
        if (m == null) return false;
        // v134: team mode uses explicit team metrics only. This avoids duplicate-looking labels
        // like H / Hits, BB / Walks, SO / Batting SO, HR / HR Allowed in All Stats.
        if (isTeamMetricContext()) return "team".equals(m.side);
        if ("team".equals(m.side)) return false;
        if (role == null || role.isEmpty() || "both".equals(role) || "two".equals(role)) return true;
        return role.equals(m.side);
    }

    private String normalizePresetKey(String preset) {
        if (preset == null || preset.trim().isEmpty()) return "recommended";
        String p = preset.trim();
        if ("core".equals(p) || "hitterCore".equals(p) || "pitcherCore".equals(p)) return "recommended";
        if ("standard".equals(p)) return "traditional";
        if ("statcast".equals(p) || "expected".equals(p) || "advanced".equals(p)) return "statcastAdvanced";
        if ("discipline".equals(p) || "command".equals(p)) return "plateDiscipline";
        if ("contact".equals(p) || "power".equals(p) || "powerContactAllowed".equals(p)) return "powerContact";
        if ("pitching".equals(p) || "pitchingDefense".equals(p) || "pitching_defense".equals(p) || "allPitching".equals(p)) return "teamPitchingDefense";
        if ("offense".equals(p) || "allHitting".equals(p) || "teamHitting".equals(p)) return "teamOffense";
        if ("results".equals(p) || "overall".equals(p)) return "teamOverall";
        if ("volume".equals(p) || "context".equals(p) || "more".equals(p) || "moreStats".equals(p)) return "moreStats";
        if ("speed".equals(p) || "baserunning".equals(p)) return "speedBaserunning";
        return p;
    }

    private boolean hasKey(String[] keys, String key) {
        if (keys == null || key == null) return false;
        for (String k : keys) if (key.equals(k)) return true;
        return false;
    }

    private String[] recommendedKeysForRole(String role) {
        boolean team = isTeamMetricContext();
        if (team) return new String[] { "teamWinPct", "teamRunDiff", "teamRPG", "teamRAPG", "teamOPS", "teamOppOps", "teamXWOBA", "teamPXWOBA", "teamBBMinusKPct", "teamPitchKMinusBBPct" };
        if ("pitch".equals(role)) return new String[] { "era", "whip", "pxwOBA", "pitchKPct", "pitchBBPct", "pitchKMinusBBPct", "pWhiffPct", "pBarrelPct", "pHardHitPct", "pAvgEV" };
        if ("hit".equals(role)) return new String[] { "ops", "wOBA", "xwOBA", "obp", "slg", "bbPct", "kPct", "barrelPct", "hardHitPct", "whiffPct" };
        return new String[] { "ops", "xwOBA", "barrelPct", "hardHitPct", "era", "whip", "pitchKMinusBBPct", "pxwOBA" };
    }

    private String[] presetKeysForRole(String preset, String role) {
        preset = normalizePresetKey(preset);
        boolean team = isTeamMetricContext();
        if ("recommended".equals(preset)) return recommendedKeysForRole(role);
        if ("traditional".equals(preset)) {
            if (team) return new String[] { "teamWinPct", "teamRunsScored", "teamRunsAllowed", "teamRunDiff", "teamAVG", "teamOPS", "teamERA", "teamWHIP", "teamHR", "teamHrAllowed", "teamHits", "teamHitsAllowed", "teamWalks", "teamWalksAllowed", "teamStrikeouts", "teamPitchStrikeouts" };
            if ("pitch".equals(role)) return new String[] { "era", "whip", "ip", "pitchK", "pitchBB", "saves", "k9", "bb9", "kbb", "pHitsAllowed", "pHrAllowed", "pOppAvg", "pOppOps" };
            return new String[] { "avg", "obp", "slg", "ops", "h", "doubles", "triples", "hr", "xbh", "rbi", "r", "sb", "bb", "so", "tb", "bbPct", "kPct" };
        }
        if ("teamOffense".equals(preset) && team) return new String[] { "teamAVG", "teamOBP", "teamSLG", "teamOPS", "teamISO", "teamBABIP", "teamHits", "teamDoubles", "teamTriples", "teamHR", "teamXbh", "teamRBI", "teamRunsScored", "teamRPG", "teamSB", "teamWalks", "teamStrikeouts", "teamTB", "teamKPct", "teamBBPct", "teamBBMinusKPct", "teamWhiffPct", "teamChasePct", "teamZoneContactPct", "teamWOBA", "teamXWOBA", "teamXBA", "teamXOBP", "teamXSLG", "teamXISO", "teamAvgEV", "teamHardHitPct", "teamBarrelPct", "teamSweetSpotPct", "teamGbPct", "teamFbPct", "teamLdPct", "teamPullPct", "teamOppoPct" };
        if ("teamPitchingDefense".equals(preset) && team) return new String[] { "teamRunsAllowed", "teamRAPG", "teamERA", "teamWHIP", "teamK9", "teamBB9", "teamKBB", "teamPitchKPct", "teamPitchBBPct", "teamPitchKMinusBBPct", "teamPitchStrikeouts", "teamHitsAllowed", "teamHrAllowed", "teamWalksAllowed", "teamOppAvg", "teamOppOps", "teamPXBA", "teamPXSLG", "teamPWOBA", "teamPXWOBA", "teamPAvgEV", "teamPHardHitPct", "teamPBarrelPct", "teamPWhiffPct", "teamPChasePct", "teamPFirstStrikePct", "teamPZonePct", "teamPGbPct", "teamPFbPct", "teamPLdPct" };
        if ("statcastAdvanced".equals(preset)) {
            if (team) return new String[] { "teamXWOBA", "teamPXWOBA", "teamXBA", "teamPXBA", "teamXSLG", "teamPXSLG", "teamXOBP", "teamXISO", "teamAvgEV", "teamPAvgEV", "teamBarrelPct", "teamPBarrelPct", "teamHardHitPct", "teamPHardHitPct", "teamSweetSpotPct", "teamWhiffPct", "teamPWhiffPct", "teamChasePct", "teamPChasePct", "teamPFirstStrikePct", "teamPZonePct" };
            if ("pitch".equals(role)) return new String[] { "pxBA", "pxSLG", "pwOBA", "pxwOBA", "pAvgEV", "pHardHitPct", "pBarrelPct", "pWhiffPct", "pChasePct", "pFirstStrikePct", "pZonePct" };
            return new String[] { "wOBA", "xwOBA", "xBA", "xOBP", "xSLG", "xISO", "wOBAcon", "xwOBAcon", "avgEV", "avgLA", "hardHitPct", "barrelPct", "sweetSpotPct", "whiffPct", "chasePct" };
        }
        if ("plateDiscipline".equals(preset)) {
            if (team) return new String[] { "teamKPct", "teamBBPct", "teamBBMinusKPct", "teamWhiffPct", "teamChasePct", "teamSwingPct", "teamZoneContactPct", "teamPitchKPct", "teamPitchBBPct", "teamPitchKMinusBBPct", "teamPWhiffPct", "teamPChasePct", "teamPFirstStrikePct", "teamPZonePct" };
            if ("pitch".equals(role)) return new String[] { "pitchKPct", "pitchBBPct", "pitchKMinusBBPct", "pWhiffPct", "pChasePct", "pFirstStrikePct", "pZonePct" };
            return new String[] { "kPct", "bbPct", "bbMinusKPct", "whiffPct", "chasePct", "swingPct", "zoneContactPct" };
        }
        if ("powerContact".equals(preset)) {
            if (team) return new String[] { "teamSLG", "teamISO", "teamHR", "teamXbh", "teamXSLG", "teamAvgEV", "teamBarrelPct", "teamHardHitPct", "teamSweetSpotPct", "teamGbPct", "teamFbPct", "teamLdPct", "teamPullPct", "teamOppoPct", "teamOppOps", "teamHrAllowed", "teamPXSLG", "teamPAvgEV", "teamPBarrelPct", "teamPHardHitPct", "teamPGbPct", "teamPFbPct", "teamPLdPct" };
            if ("pitch".equals(role)) return new String[] { "pHrAllowed", "pOppOps", "pxSLG", "pAvgEV", "pHardHitPct", "pBarrelPct", "pGbPct", "pFbPct", "pLdPct" };
            return new String[] { "slg", "iso", "hr", "xbh", "xSLG", "avgEV", "hardHitPct", "barrelPct", "sweetSpotPct", "gbPct", "fbPct", "ldPct", "pullPct", "oppoPct" };
        }
        if ("runPrevention".equals(preset)) {
            if (team) return new String[] { "teamRunsAllowed", "teamRAPG", "teamERA", "teamWHIP", "teamOppAvg", "teamOppOps", "teamPXWOBA", "teamPXBA", "teamHitsAllowed", "teamHrAllowed", "teamWalksAllowed", "teamPitchKMinusBBPct", "teamPBarrelPct", "teamPHardHitPct", "teamPGbPct" };
            if ("pitch".equals(role)) return new String[] { "era", "whip", "pxwOBA", "pxBA", "pOppAvg", "pOppOps", "pHitsAllowed", "pHrAllowed", "bb9", "pitchKMinusBBPct", "pBarrelPct", "pHardHitPct", "pGbPct" };
            return recommendedKeysForRole(role);
        }
        if ("teamOverall".equals(preset) && team) return new String[] { "teamWinPct", "teamRunDiff", "teamRunsScored", "teamRunsAllowed", "teamRPG", "teamRAPG", "teamOPS", "teamOppOps", "teamERA", "teamWHIP", "teamBBMinusKPct", "teamPitchKMinusBBPct" };
        if ("speedBaserunning".equals(preset)) return new String[] { "sb", "sprintSpeed" };
        if ("moreStats".equals(preset)) {
            if (team) return new String[] { "teamRunsScored", "teamRunsAllowed", "teamHits", "teamDoubles", "teamTriples", "teamHR", "teamXbh", "teamRBI", "teamSB", "teamWalks", "teamStrikeouts", "teamTB", "teamPitchStrikeouts", "teamHitsAllowed", "teamHrAllowed", "teamWalksAllowed", "teamBABIP", "teamGbPct", "teamFbPct", "teamPullPct", "teamOppoPct", "teamSwingPct" };
            if ("pitch".equals(role)) return new String[] { "ip", "pitchK", "pitchBB", "saves", "pHitsAllowed", "pHrAllowed", "babip", "luck" };
            return new String[] { "h", "doubles", "triples", "hr", "xbh", "rbi", "r", "sb", "bb", "so", "tb", "babip", "luck", "avgLA", "gbPct", "fbPct", "pullPct", "oppoPct", "swingPct" };
        }
        return null;
    }

    private LinkedHashSet<String> metricKeysForPresetAndRole(String preset, String role) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        preset = normalizePresetKey(preset);
        String[] explicit = presetKeysForRole(preset, role);
        if (explicit != null) {
            for (String key : explicit) {
                Metric m = findMetricByKey(key);
                if (roleAllowsMetric(role, m)) keys.add(key);
            }
            return keys;
        }
        for (Metric m : metrics) {
            if (!roleAllowsMetric(role, m)) continue;
            boolean include;
            if ("all".equals(preset)) include = true;
            else if ("luck".equals(preset)) include = m.key.equals("wOBA") || m.key.equals("xwOBA") || m.key.equals("luck");
            else if ("moreStats".equals(preset)) include = m.isCount() || isContextOnlyMetric(m) || m.group.toLowerCase(Locale.US).contains("volume") || m.key.equals("ip");
            else include = false;
            if (include) keys.add(m.key);
        }
        return keys;
    }

    private String metricPresetNameForRole(Set<String> keys, String role) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (keys != null) for (String key : keys) {
            Metric m = findMetricByKey(key);
            if (roleAllowsMetric(role, m)) normalized.add(key);
        }
        String[] order = new String[] { "recommended", "traditional", "statcastAdvanced", "plateDiscipline", "powerContact", "runPrevention", "teamOverall", "teamOffense", "teamPitchingDefense", "speedBaserunning", "moreStats", "all", "luck" };
        for (String preset : order) if (normalized.equals(metricKeysForPresetAndRole(preset, role))) return presetDisplayName(preset, role);
        return "Custom";
    }

    private String presetDisplayName(String preset, String role) {
        preset = normalizePresetKey(preset);
        boolean team = isTeamMetricContext();
        if ("recommended".equals(preset)) return "Recommended";
        if ("traditional".equals(preset)) return "Traditional";
        if ("statcastAdvanced".equals(preset)) return "Statcast";
        if ("plateDiscipline".equals(preset)) return "Plate Discipline";
        if ("powerContact".equals(preset)) return "pitch".equals(role) && !team ? "Power/Contact Allowed" : "Power/Contact";
        if ("runPrevention".equals(preset)) return "Run Prevention";
        if ("teamOverall".equals(preset)) return "Team Overall";
        if ("teamOffense".equals(preset)) return "Offense";
        if ("teamPitchingDefense".equals(preset)) return "Pitching/Defense";
        if ("speedBaserunning".equals(preset)) return "Speed/Baserunning";
        if ("moreStats".equals(preset)) return "More Stats";
        if ("all".equals(preset)) return "pitch".equals(role) ? "All Pitching" : ("hit".equals(role) ? "All Hitting" : "All Stats");
        if ("luck".equals(preset)) return "Luck";
        return "Custom";
    }

    private LinkedHashSet<String> defaultKeyEdgeForRole(String role, Set<String> sourceKeys) {
        return defaultKeyEdgeForPresetAndRole("recommended", role, sourceKeys);
    }

    private LinkedHashSet<String> defaultKeyEdgeForPresetAndRole(String preset, String role, Set<String> sourceKeys) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Set<String> keys = sourceKeys == null ? selectedMetricKeys : sourceKeys;
        preset = normalizePresetKey(preset);
        boolean team = isTeamMetricContext();
        String[] order;
        if ("traditional".equals(preset)) {
            if (team) order = new String[] { "teamWinPct", "teamRunDiff", "teamRunsScored", "teamRunsAllowed", "teamOPS", "teamOppOps", "teamERA", "teamWHIP" };
            else if ("pitch".equals(role)) order = new String[] { "era", "whip", "ip", "pitchK", "pitchBB", "saves", "k9", "bb9" };
            else order = new String[] { "avg", "obp", "slg", "ops", "hr", "rbi", "r", "sb" };
        } else if ("teamOffense".equals(preset)) {
            order = new String[] { "teamRPG", "teamOPS", "teamWOBA", "teamXWOBA", "teamBBMinusKPct", "teamSLG", "teamBarrelPct", "teamHardHitPct" };
        } else if ("teamPitchingDefense".equals(preset)) {
            order = new String[] { "teamRAPG", "teamERA", "teamWHIP", "teamOppOps", "teamPXWOBA", "teamPitchKMinusBBPct", "teamPBarrelPct", "teamPHardHitPct" };
        } else if ("statcastAdvanced".equals(preset)) {
            if (team) order = new String[] { "teamXWOBA", "teamPXWOBA", "teamBarrelPct", "teamPBarrelPct", "teamHardHitPct", "teamPHardHitPct", "teamWhiffPct", "teamPWhiffPct" };
            else if ("pitch".equals(role)) order = new String[] { "pxwOBA", "pxBA", "pxSLG", "pWhiffPct", "pChasePct", "pBarrelPct", "pHardHitPct", "pAvgEV" };
            else order = new String[] { "xwOBA", "xBA", "xSLG", "wOBA", "barrelPct", "hardHitPct", "avgEV", "whiffPct" };
        } else if ("plateDiscipline".equals(preset)) {
            if (team) order = new String[] { "teamBBPct", "teamKPct", "teamBBMinusKPct", "teamPitchKPct", "teamPitchBBPct", "teamPitchKMinusBBPct", "teamPFirstStrikePct", "teamPZonePct" };
            else if ("pitch".equals(role)) order = new String[] { "pitchKPct", "pitchBBPct", "pitchKMinusBBPct", "pWhiffPct", "pChasePct", "pFirstStrikePct", "pZonePct" };
            else order = new String[] { "bbPct", "kPct", "bbMinusKPct", "chasePct", "whiffPct", "zoneContactPct" };
        } else if ("powerContact".equals(preset)) {
            if (team) order = new String[] { "teamSLG", "teamISO", "teamBarrelPct", "teamHardHitPct", "teamAvgEV", "teamXSLG", "teamPBarrelPct", "teamPHardHitPct" };
            else if ("pitch".equals(role)) order = new String[] { "pxSLG", "pOppOps", "pBarrelPct", "pHardHitPct", "pAvgEV", "pHrAllowed", "pGbPct", "pFbPct" };
            else order = new String[] { "slg", "iso", "barrelPct", "hardHitPct", "avgEV", "xSLG", "sweetSpotPct", "hr" };
        } else if ("runPrevention".equals(preset)) {
            if (team) order = new String[] { "teamRAPG", "teamERA", "teamWHIP", "teamOppOps", "teamPXWOBA", "teamPitchKMinusBBPct", "teamPBarrelPct", "teamPHardHitPct" };
            else order = new String[] { "teamRAPG", "era", "whip", "teamOppOps", "pxwOBA", "pitchKMinusBBPct", "pBarrelPct", "pHardHitPct" };
        } else if ("teamOverall".equals(preset)) {
            order = new String[] { "teamWinPct", "teamRunDiff", "teamRPG", "teamRAPG", "teamOPS", "teamOppOps", "teamERA", "teamWHIP" };
        } else {
            order = recommendedKeysForRole(role);
        }
        for (String key : order) {
            Metric m = findMetricByKey(key);
            if (keys.contains(key) && roleAllowsMetric(role, m) && ("traditional".equals(preset) || !isContextOnlyMetric(m))) out.add(key);
            if (out.size() >= 8) return out;
        }
        for (String key : defaultKeyEdgeOrder()) {
            Metric m = findMetricByKey(key);
            if (keys.contains(key) && roleAllowsMetric(role, m) && isDefaultKeyEdgeMetric(m)) out.add(key);
            if (out.size() >= 8) return out;
        }
        for (Metric m : metrics) {
            if (keys.contains(m.key) && roleAllowsMetric(role, m) && ("traditional".equals(preset) || isDefaultKeyEdgeMetric(m))) out.add(m.key);
            if (out.size() >= 8) return out;
        }
        return out;
    }

    private boolean isDefaultKeyEdgeMetric(Metric m) {
        if (m == null) return false;
        if (m.isCount()) return false;
        if (isContextOnlyMetric(m) || isTargetRangeMetric(m) || "ip".equals(m.key)) return false;
        return true;
    }

    private void applyDefaultMetricsForRole(String role) {
        applyMetricsForRoleAndPreset(role, "recommended", true);
    }

    private void applyMetricsForRoleAndPreset(String role, String preset, boolean updatePresetState) {
        preset = normalizePresetKey(preset);
        if ("custom".equals(preset)) return;
        selectedMetricKeys.clear();
        selectedMetricKeys.addAll(metricKeysForPresetAndRole(preset, role));
        if (selectedMetricKeys.isEmpty()) selectedMetricKeys.addAll(metricKeysForPresetAndRole("recommended", role));
        keyEdgeMetricKeys.clear();
        keyEdgeMetricKeys.addAll(defaultKeyEdgeForPresetAndRole(preset, role, selectedMetricKeys));
        if (updatePresetState) {
            activeComparisonPreset = preset;
            metricsManuallyCustomized = false;
            showAllResultsStats = false;
            activeResultsStatCategory = "all";
        }
        selectedRankMetricPosition = -1;
        rebuildRankMetricSpinner();
        updateMetricPickerLabel();
        syncMetricChecks();
    }

    private LinkedHashSet<String> checkedMetricKeys(Map<String, CheckBox> boxes) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (boxes == null) return keys;
        for (Map.Entry<String, CheckBox> e : boxes.entrySet()) {
            CheckBox cb = e.getValue();
            if (cb != null && cb.isChecked()) keys.add(e.getKey());
        }
        return keys;
    }

    private void updateStatsDialogPresetPill(TextView pill, Map<String, CheckBox> boxes, String role) {
        if (pill == null) return;
        String preset = metricPresetNameForRole(checkedMetricKeys(boxes), role);
        boolean custom = "Custom".equals(preset);
        pill.setText(custom ? "Lens · Custom" : "Lens · " + preset);
        int accent = custom ? Color.rgb(150, 166, 190) : Color.rgb(246, 198, 68);
        pill.setTextColor(custom ? Color.rgb(190, 204, 224) : Color.rgb(255, 235, 152));
        pill.setBackground(roundedGradientStroke(new int[] {
                Color.argb(210, 6, 10, 18),
                Color.argb(160, Color.red(accent), Color.green(accent), Color.blue(accent))
        }, 14, Color.argb(custom ? 66 : 124, Color.red(accent), Color.green(accent), Color.blue(accent)), 1));
    }

    private void resetKeyEdgeMetricsForSelectedStats() {
        keyEdgeMetricKeys.clear();
        keyEdgeMetricKeys.addAll(defaultKeyEdgeForPresetAndRole(activeComparisonPreset, allowedMetricRoleForCurrentContext(), selectedMetricKeys));
    }

    private String[] defaultKeyEdgeOrder() {
        return new String[] { "teamRunDiff", "teamRPG", "teamRAPG", "teamOPS", "teamOppOps", "teamXWOBA", "teamPXWOBA", "teamBBMinusKPct", "teamPitchKMinusBBPct", "ops", "wOBA", "xwOBA", "obp", "slg", "barrelPct", "hardHitPct", "whiffPct", "bbPct", "kPct", "bbMinusKPct", "era", "whip", "pxwOBA", "pitchKMinusBBPct", "pitchKPct", "pitchBBPct", "pWhiffPct", "pBarrelPct", "pHardHitPct" };
    }

    private LinkedHashSet<String> firstKeyEdgeFrom(Set<String> keys) {
        return defaultKeyEdgeForRole(allowedMetricRoleForCurrentContext(), keys);
    }


    private String metricPresetName(Set<String> keys) {
        return metricPresetNameForRole(keys, allowedMetricRoleForCurrentContext());
    }

    private LinkedHashSet<String> metricKeysForPreset(String preset) {
        return metricKeysForPresetAndRole(preset, allowedMetricRoleForCurrentContext());
    }

    private void showMetricPicker() {
        final LinkedHashMap<String, CheckBox> boxes = new LinkedHashMap<>();
        final LinkedHashMap<String, CheckBox> keyBoxes = new LinkedHashMap<>();
        final LinkedHashMap<String, TextView> presetChips = new LinkedHashMap<>();
        final String allowedMetricRole = allowedMetricRoleForCurrentContext();
        final ArrayList<Metric> pickerMetrics = new ArrayList<>();
        for (Metric m : metrics) {
            if (!roleAllowsMetric(allowedMetricRole, m)) continue;
            pickerMetrics.add(m);
        }

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(16), dp(14), dp(16), dp(14));
        sheet.setBackground(roundedGradientStroke(new int[] {
                Color.rgb(4, 8, 16),
                Color.rgb(7, 15, 28),
                Color.rgb(5, 24, 40)
        }, 28, Color.argb(96, 96, 228, 222), 1));

        View handle = new View(this);
        handle.setBackground(rounded(Color.argb(92, 255, 255, 255), 99));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(44), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(handle, handleLp);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, dp(10), 0, dp(8));
        TextView title = text("Comparison Lens", 18, Color.rgb(238, 245, 252), true);
        title.setLetterSpacing(0.02f);
        header.addView(title);
        TextView hint = text(("both".equals(allowedMetricRole)
                ? "Pick a Lens or create a Custom card. Show = scored stats + default rows. Key Edge = top card highlights (max 8)."
                : "This matchup is locked to " + homeRoleLabel(allowedMetricRole) + " stats. Show = scored stats + default rows. Key Edge = top card highlights (max 8)."),
                11, Color.rgb(150, 166, 190), false);
        hint.setPadding(0, dp(5), 0, 0);
        header.addView(hint);
        TextView presetPill = text("Preset", 11, Color.rgb(255, 235, 152), true);
        presetPill.setGravity(Gravity.CENTER);
        presetPill.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams presetPillLp = new LinearLayout.LayoutParams(-2, -2);
        presetPillLp.setMargins(0, dp(8), 0, 0);
        header.addView(presetPill, presetPillLp);
        sheet.addView(header, matchWrap());

        LinearLayout presetRow1 = new LinearLayout(this); presetRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout presetRow2 = new LinearLayout(this); presetRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout presetRow3 = new LinearLayout(this); presetRow3.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout presetRow4 = new LinearLayout(this); presetRow4.setOrientation(LinearLayout.HORIZONTAL);
        sheet.addView(presetRow1, matchWrap());
        LinearLayout.LayoutParams preset2Lp = matchWrap(); preset2Lp.setMargins(0, dp(6), 0, 0); sheet.addView(presetRow2, preset2Lp);
        LinearLayout.LayoutParams preset3Lp = matchWrap(); preset3Lp.setMargins(0, dp(6), 0, 0); sheet.addView(presetRow3, preset3Lp);
        LinearLayout.LayoutParams preset4Lp = matchWrap(); preset4Lp.setMargins(0, dp(6), 0, 0); sheet.addView(presetRow4, preset4Lp);

        addDialogPresetChip(presetRow1, presetChips, "recommended", "Recommended", boxes, keyBoxes, allowedMetricRole, presetPill);
        addDialogPresetChip(presetRow1, presetChips, "traditional", "Traditional", boxes, keyBoxes, allowedMetricRole, presetPill);
        addDialogPresetChip(presetRow1, presetChips, "statcastAdvanced", "Statcast", boxes, keyBoxes, allowedMetricRole, presetPill);
        addDialogPresetChip(presetRow2, presetChips, "plateDiscipline", "Plate Discipline", boxes, keyBoxes, allowedMetricRole, presetPill);
        addDialogPresetChip(presetRow2, presetChips, "powerContact", isTeamMetricContext() || "hit".equals(allowedMetricRole) ? "Power/Contact" : "Power/Contact Allowed", boxes, keyBoxes, allowedMetricRole, presetPill);
        if (isTeamMetricContext() || "pitch".equals(allowedMetricRole)) addDialogPresetChip(presetRow2, presetChips, "runPrevention", "Run Prevention", boxes, keyBoxes, allowedMetricRole, presetPill);
        else addDialogPresetChip(presetRow2, presetChips, "speedBaserunning", "Speed/Baserunning", boxes, keyBoxes, allowedMetricRole, presetPill);
        if (isTeamMetricContext()) {
            addDialogPresetChip(presetRow3, presetChips, "teamOverall", "Team Overall", boxes, keyBoxes, allowedMetricRole, presetPill);
            addDialogPresetChip(presetRow3, presetChips, "teamOffense", "Offense", boxes, keyBoxes, allowedMetricRole, presetPill);
            addDialogPresetChip(presetRow3, presetChips, "teamPitchingDefense", "Pitching/Defense", boxes, keyBoxes, allowedMetricRole, presetPill);
            addDialogPresetChip(presetRow4, presetChips, "moreStats", "More Stats", boxes, keyBoxes, allowedMetricRole, presetPill);
            addDialogPresetChip(presetRow4, presetChips, "all", "All Stats", boxes, keyBoxes, allowedMetricRole, presetPill);
        } else {
            addDialogPresetChip(presetRow3, presetChips, "moreStats", "More Stats", boxes, keyBoxes, allowedMetricRole, presetPill);
            addDialogPresetChip(presetRow3, presetChips, "all", "All Stats", boxes, keyBoxes, allowedMetricRole, presetPill);
        }
        TextView clearChip = dialogPresetChip("Clear", () -> {
            for (CheckBox cb : boxes.values()) cb.setChecked(false);
            for (CheckBox cb : keyBoxes.values()) cb.setChecked(false);
            updateStatsDialogPresetState(presetPill, boxes, allowedMetricRole, presetChips);
        });
        (isTeamMetricContext() ? presetRow4 : presetRow3).addView(clearChip, weightLp());

        TextView applyTop = statsDialogAction("Apply Lens / Save Custom", true);
        LinearLayout.LayoutParams applyTopLp = matchWrap();
        applyTopLp.setMargins(0, dp(10), 0, dp(2));
        sheet.addView(applyTop, applyTopLp);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(10), 0, 0);

        LinearLayout columnHeader = new LinearLayout(this);
        columnHeader.setOrientation(LinearLayout.HORIZONTAL);
        columnHeader.setGravity(Gravity.CENTER_VERTICAL);
        columnHeader.setPadding(dp(10), dp(4), dp(10), dp(4));
        columnHeader.addView(text("STAT", 9, Color.rgb(132, 148, 172), true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView showHdr = text("SCORE", 9, Color.rgb(132, 148, 172), true); showHdr.setGravity(Gravity.CENTER);
        TextView keyHdr = text("CARD", 9, Color.rgb(132, 148, 172), true); keyHdr.setGravity(Gravity.CENTER);
        columnHeader.addView(showHdr, new LinearLayout.LayoutParams(dp(66), -2));
        columnHeader.addView(keyHdr, new LinearLayout.LayoutParams(dp(78), -2));
        list.addView(columnHeader, matchWrap());

        String lastGroup = "";
        for (Metric m : pickerMetrics) {
            if (!m.group.equals(lastGroup)) {
                TextView sub = text(m.group, 11, Color.rgb(194, 208, 228), true);
                sub.setLetterSpacing(0.08f);
                sub.setPadding(dp(2), dp(12), 0, dp(5));
                list.addView(sub);
                lastGroup = m.group;
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(8), dp(8), dp(8));
            row.setBackground(roundedGradientStroke(new int[] {
                    Color.argb(208, 8, 13, 24),
                    Color.argb(188, 5, 10, 18)
            }, 17, Color.argb(44, 255, 255, 255), 1));
            LinearLayout.LayoutParams rowLp = matchWrap(); rowLp.setMargins(0, 0, 0, dp(6));
            list.addView(row, rowLp);

            LinearLayout labelCol = new LinearLayout(this);
            labelCol.setOrientation(LinearLayout.VERTICAL);
            TextView label = text(m.label, 13, Color.rgb(234, 241, 250), true);
            labelCol.addView(label);
            TextView meta = text(("team".equals(m.side) ? "Team" : ("pitch".equals(m.side) ? "Pitching" : "Hitting")) + " · " + m.group.replace("Standard ", "").replace("Statcast ", ""), 9, Color.rgb(126, 141, 164), false);
            meta.setPadding(0, dp(2), 0, 0);
            labelCol.addView(meta);
            row.addView(labelCol, new LinearLayout.LayoutParams(0, -2, 1));

            CheckBox showCb = new CheckBox(this);
            showCb.setButtonTintList(ColorStateList.valueOf(Color.rgb(86, 226, 218)));
            showCb.setGravity(Gravity.CENTER);
            showCb.setChecked(selectedMetricKeys.contains(m.key));
            row.addView(showCb, new LinearLayout.LayoutParams(dp(66), -2));
            boxes.put(m.key, showCb);

            CheckBox keyCb = new CheckBox(this);
            keyCb.setButtonTintList(ColorStateList.valueOf(Color.rgb(246, 198, 68)));
            keyCb.setGravity(Gravity.CENTER);
            keyCb.setChecked(keyEdgeMetricKeys.contains(m.key));
            keyCb.setEnabled(showCb.isChecked());
            keyCb.setAlpha(showCb.isChecked() ? 1f : 0.38f);
            row.addView(keyCb, new LinearLayout.LayoutParams(dp(78), -2));
            keyBoxes.put(m.key, keyCb);

            showCb.setOnCheckedChangeListener((buttonView, checked) -> {
                keyCb.setEnabled(checked);
                keyCb.setAlpha(checked ? 1f : 0.38f);
                if (!checked) keyCb.setChecked(false);
                updateStatsDialogPresetState(presetPill, boxes, allowedMetricRole, presetChips);
            });
            keyCb.setOnCheckedChangeListener((buttonView, checked) -> {
                if (checked && !showCb.isChecked()) showCb.setChecked(true);
                if (checked && checkedCount(keyBoxes) > 8) {
                    keyCb.setChecked(false);
                    Toast.makeText(this, "Key Edge card can show up to 8 stats. Uncheck one first.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        updateStatsDialogPresetState(presetPill, boxes, allowedMetricRole, presetChips);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        scroller.addView(list);
        LinearLayout.LayoutParams scrollerLp = new LinearLayout.LayoutParams(-1, dp(260));
        scrollerLp.setMargins(0, dp(10), 0, dp(10));
        sheet.addView(scroller, scrollerLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView cancel = statsDialogAction("Cancel", false);
        TextView apply = statsDialogAction("Apply Stats", true);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        applyLp.setMargins(dp(10), 0, 0, 0);
        actions.addView(cancel, cancelLp);
        actions.addView(apply, applyLp);
        sheet.addView(actions, matchWrap());

        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        cancel.setOnClickListener(v -> dialog.dismiss());
        applyTop.setOnClickListener(v -> apply.performClick());
        apply.setOnClickListener(v -> {
            selectedMetricKeys.clear();
            keyEdgeMetricKeys.clear();
            for (Metric m : pickerMetrics) {
                CheckBox cb = boxes.get(m.key);
                if (cb != null && cb.isChecked()) selectedMetricKeys.add(m.key);
            }
            int keyCount = 0;
            for (Metric m : pickerMetrics) {
                CheckBox cb = keyBoxes.get(m.key);
                if (cb != null && cb.isChecked() && selectedMetricKeys.contains(m.key)) {
                    if (keyCount < 8) {
                        keyEdgeMetricKeys.add(m.key);
                        keyCount++;
                    }
                }
            }
            String active = activePresetKeyForRole(selectedMetricKeys, allowedMetricRole);
            if (selectedMetricKeys.isEmpty()) {
                active = "all";
                selectedMetricKeys.addAll(metricKeysForPresetAndRole("all", allowedMetricRole));
                Toast.makeText(this, "No stats selected — restored All Stats.", Toast.LENGTH_SHORT).show();
            }
            metricsManuallyCustomized = "custom".equals(active);
            activeComparisonPreset = metricsManuallyCustomized ? "custom" : normalizePresetKey(active);
            showAllResultsStats = false;
            activeResultsStatCategory = "all";
            if (keyEdgeMetricKeys.isEmpty()) keyEdgeMetricKeys.addAll(defaultKeyEdgeForPresetAndRole(activeComparisonPreset, allowedMetricRole, selectedMetricKeys));
            rebuildRankMetricSpinner();
            updateMetricPickerLabel();
            syncMetricChecks();
            refreshCurrentResults();
            refreshRankingsIfActive();
            dialog.dismiss();
        });
        dialog.setView(sheet);
        dialog.show();
        try {
            android.view.Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        } catch (Exception ignored) {}
    }

    private void addDialogPresetChip(LinearLayout row, LinkedHashMap<String, TextView> presetChips, String presetKey, String label, Map<String, CheckBox> boxes, Map<String, CheckBox> keyBoxes, String role, TextView presetPill) {
        TextView chip = dialogPresetChip(label, () -> {
            setDialogPreset(boxes, keyBoxes, presetKey, role);
            updateStatsDialogPresetState(presetPill, boxes, role, presetChips);
        });
        chip.setTag(presetKey);
        presetChips.put(presetKey, chip);
        row.addView(chip, weightLp());
    }

    private void updateStatsDialogPresetState(TextView pill, Map<String, CheckBox> boxes, String role, LinkedHashMap<String, TextView> presetChips) {
        updateStatsDialogPresetPill(pill, boxes, role);
        refreshDialogPresetHighlights(presetChips, activePresetKeyForRole(checkedMetricKeys(boxes), role));
    }

    private String activePresetKeyForRole(Set<String> keys, String role) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (keys != null) for (String key : keys) {
            Metric m = findMetricByKey(key);
            if (roleAllowsMetric(role, m)) normalized.add(key);
        }
        String[] order = new String[] { "recommended", "traditional", "statcastAdvanced", "plateDiscipline", "powerContact", "runPrevention", "teamOverall", "teamOffense", "teamPitchingDefense", "speedBaserunning", "moreStats", "all", "luck" };
        for (String preset : order) if (normalized.equals(metricKeysForPresetAndRole(preset, role))) return preset;
        return "custom";
    }

    private void refreshDialogPresetHighlights(LinkedHashMap<String, TextView> chips, String activePreset) {
        if (chips == null) return;
        for (Map.Entry<String, TextView> e : chips.entrySet()) styleDialogPresetChip(e.getValue(), e.getKey().equals(activePreset));
    }

    private void styleDialogPresetChip(TextView tv, boolean active) {
        if (tv == null) return;
        tv.setTextColor(active ? Color.rgb(12, 16, 24) : Color.rgb(226, 236, 248));
        tv.setShadowLayer(active ? dp(3) : 0, 0, active ? dp(1) : 0, active ? Color.argb(110, 255, 245, 164) : Color.TRANSPARENT);
        tv.setBackground(active
                ? roundedGradientStroke(new int[] { Color.rgb(255, 245, 132), Color.rgb(244, 192, 54), Color.rgb(217, 132, 24) }, 15, Color.argb(230, 255, 245, 142), 2)
                : roundedGradientStroke(new int[] { Color.argb(230, 7, 12, 23), Color.argb(212, 12, 31, 46) }, 15, Color.argb(82, 95, 230, 220), 1));
    }

    private TextView dialogPresetChip(String label, Runnable action) {
        TextView tv = text(label, 11, Color.rgb(226, 236, 248), true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(7), dp(8), dp(7), dp(8));
        tv.setSingleLine(true);
        tv.setBackground(roundedGradientStroke(new int[] {
                Color.argb(230, 7, 12, 23),
                Color.argb(212, 12, 31, 46)
        }, 15, Color.argb(82, 95, 230, 220), 1));
        tv.setForeground(ripple(true));
        tv.setOnClickListener(v -> action.run());
        attachPremiumPress(tv, 0.97f);
        return tv;
    }

    private TextView statsDialogAction(String label, boolean primary) {
        TextView tv = text(label, 12, primary ? Color.rgb(11, 13, 20) : Color.rgb(210, 224, 244), true);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setBackground(primary
                ? roundedGradientStroke(new int[] { Color.rgb(255, 245, 132), Color.rgb(244, 192, 54), Color.rgb(221, 138, 26) }, 18, Color.argb(220, 255, 245, 142), 2)
                : roundedGradientStroke(new int[] { Color.rgb(8, 13, 22), Color.rgb(10, 18, 31) }, 18, Color.argb(72, 255, 255, 255), 1));
        tv.setForeground(ripple(true));
        attachPremiumPress(tv, 0.97f);
        return tv;
    }

    private int checkedCount(Map<String, CheckBox> boxes) {
        int n = 0;
        if (boxes == null) return 0;
        for (CheckBox cb : boxes.values()) if (cb != null && cb.isChecked()) n++;
        return n;
    }

    private void setDialogPreset(Map<String, CheckBox> boxes, Map<String, CheckBox> keyBoxes, String preset, String role) {
        Set<String> keys = metricKeysForPresetAndRole(preset, role);
        LinkedHashSet<String> keyDefaults = defaultKeyEdgeForPresetAndRole(preset, role, keys);
        for (Map.Entry<String, CheckBox> e : boxes.entrySet()) {
            CheckBox cb = e.getValue();
            if (cb != null) cb.setChecked(keys.contains(e.getKey()));
        }
        int keyCount = 0;
        for (Map.Entry<String, CheckBox> e : keyBoxes.entrySet()) {
            CheckBox cb = e.getValue();
            boolean checked = keyDefaults.contains(e.getKey()) && keyCount < 8;
            if (cb != null) cb.setChecked(checked);
            if (checked) keyCount++;
        }
    }

    private void renderSelectionPreview() {
        if (selectedPreviewBox == null) return;
        selectedPreviewBox.removeAllViews();
        boolean hasSelection = teamMode ? selectedTeam != null : selectedPlayer != null;
        selectedPreviewBox.setVisibility(hasSelection ? View.VISIBLE : View.GONE);
        if (!hasSelection) return;

        TeamPalette palette = teamMode ? paletteForTeam(selectedTeam) : paletteForAbbr(selectedPlayer.teamAbbr);
        selectedPreviewBox.setBackground(roundedGradient(new int[] { palette.primary, palette.secondary }, 20));
        selectedPreviewBox.setOnClickListener(v -> openProfileForCurrentSelection());
        selectedPreviewBox.setClickable(true);
        selectedPreviewBox.setForeground(ripple(true));

        if (teamMode) {
            View logo = teamLogoView(selectedTeam, 46);
            LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(46), dp(46));
            logoLp.setMargins(0, 0, dp(11), 0);
            selectedPreviewBox.addView(logo, logoLp);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(text(selectedTeam.name, 16, Color.WHITE, true));
            col.addView(text(headToHeadMode ? "Team A · " + selectedTeam.abbr : "Team profile · " + selectedTeam.abbr, 11, Color.rgb(228, 238, 248), false));
            selectedPreviewBox.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            TextView hint = text(headToHeadMode ? "A" : (expectedMode ? "xStats" : (rankingsModeActive ? "Ranks" : "Profile")), 11, Color.WHITE, true);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(dp(9), dp(5), dp(9), dp(5));
            hint.setBackground(roundedStroke(Color.argb(34, 255, 255, 255), Color.argb(80, 255, 255, 255), 14, 1));
            selectedPreviewBox.addView(hint);
        } else {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setAdjustViewBounds(true);
            img.setBackground(roundedStroke(Color.WHITE, Color.argb(90, 255, 255, 255), 18, 1));
            img.setPadding(dp(2), dp(2), dp(2), dp(2));
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(46), dp(46));
            imgLp.setMargins(0, 0, dp(11), 0);
            selectedPreviewBox.addView(img, imgLp);
            loadPlayerImage(selectedPlayer.id, img);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(text(selectedPlayer.fullName, 16, Color.WHITE, true));
            col.addView(text(headToHeadMode ? "Player A · " + selectedPlayer.teamAbbr + " · " + selectedPlayer.position : selectedPlayer.teamAbbr + " · " + selectedPlayer.position, 11, Color.rgb(228, 238, 248), false));
            selectedPreviewBox.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            TextView hint = text(headToHeadMode ? "A" : (expectedMode ? "xStats" : (rankingsModeActive ? "Ranks" : "Profile")), 11, Color.WHITE, true);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(dp(9), dp(5), dp(9), dp(5));
            hint.setBackground(roundedStroke(Color.argb(34, 255, 255, 255), Color.argb(80, 255, 255, 255), 14, 1));
            selectedPreviewBox.addView(hint);
        }
    }

    private String[] categoryKeysForRole(String role) {
        if (isTeamMetricContext()) return new String[] { "all", "recommended", "traditional", "teamOverall", "teamOffense", "teamPitchingDefense", "statcastAdvanced", "plateDiscipline", "powerContact", "runPrevention", "moreStats" };
        if ("pitch".equals(role)) return new String[] { "all", "recommended", "traditional", "statcastAdvanced", "plateDiscipline", "powerContact", "runPrevention", "moreStats" };
        return new String[] { "all", "recommended", "traditional", "statcastAdvanced", "plateDiscipline", "powerContact", "speedBaserunning", "moreStats" };
    }

    private String categoryLabel(String key) {
        String k = normalizePresetKey(key);
        if ("all".equals(k)) return "All";
        if ("recommended".equals(k)) return "Recommended";
        if ("traditional".equals(k)) return "Traditional";
        if ("teamOverall".equals(k)) return "Team Overall";
        if ("teamOffense".equals(k)) return "Offense";
        if ("teamPitchingDefense".equals(k)) return "Pitching/Defense";
        if ("statcastAdvanced".equals(k)) return "Statcast";
        if ("plateDiscipline".equals(k)) return "Plate Discipline";
        if ("powerContact".equals(k)) return "pitch".equals(allowedMetricRoleForCurrentContext()) && !isTeamMetricContext() ? "Power/Contact Allowed" : "Power/Contact";
        if ("runPrevention".equals(k)) return "Run Prevention";
        if ("speedBaserunning".equals(k)) return "Speed/Baserunning";
        if ("moreStats".equals(k)) return "More Stats";
        if ("luck".equals(k)) return "Luck";
        return key;
    }

    private boolean metricMatchesCategory(Metric m, String category) {
        if (m == null) return false;
        String c = normalizePresetKey(category == null ? "all" : category);
        if ("all".equals(c)) return true;
        String role = allowedMetricRoleForCurrentContext();
        if (!roleAllowsMetric(role, m)) return false;
        LinkedHashSet<String> keys = metricKeysForPresetAndRole(c, role);
        if (!keys.isEmpty()) return keys.contains(m.key);
        if ("luck".equals(c)) return m.key.equals("wOBA") || m.key.equals("xwOBA") || m.key.equals("luck");
        return false;
    }

    private TextView categoryFilterChip(String key) {
        boolean active = key.equals(activeStatCategory);
        TextView tv = filterChip(categoryLabel(key), () -> { activeStatCategory = key; buildMetricFilters(); });
        tv.setSingleLine(true);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        if (active) {
            tv.setTextColor(Color.rgb(8, 16, 26));
            tv.setBackground(roundedGradientStroke(new int[] { Color.rgb(255, 243, 140), Color.rgb(88, 210, 232) }, 999, Color.argb(210, 255, 245, 150), 1));
        } else {
            tv.setTextColor(Color.rgb(220, 229, 242));
            tv.setBackground(roundedStroke(Color.argb(16, 255, 255, 255), Color.argb(70, 190, 214, 236), 999, 1));
        }
        return tv;
    }

    private void buildMetricFilters() {
        filterBox.removeAllViews();
        activeStatCategory = "all";

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.addView(text("Comparison Lens", 14, Color.WHITE, true));
        String current = metricPresetNameForRole(selectedMetricKeys, allowedMetricRoleForCurrentContext());
        TextView hint = text("One Lens controls the matchup card, key edge stats, and default rows below. Current: " + current, 12, Color.rgb(190, 205, 223), false);
        hint.setPadding(0, dp(3), 0, dp(8));
        top.addView(hint);
        filterBox.addView(top, matchWrap());

        LinearLayout presetRow1 = new LinearLayout(this);
        presetRow1.setOrientation(LinearLayout.HORIZONTAL);
        presetRow1.addView(filterChip("Recommended", () -> applyMetricPreset("recommended")), weightLp());
        presetRow1.addView(filterChip("Traditional", () -> applyMetricPreset("traditional")), weightLp());
        presetRow1.addView(filterChip("Statcast", () -> applyMetricPreset("statcastAdvanced")), weightLp());
        filterBox.addView(presetRow1, matchWrap());

        LinearLayout presetRow2 = new LinearLayout(this);
        presetRow2.setOrientation(LinearLayout.HORIZONTAL);
        presetRow2.addView(filterChip("Plate Discipline", () -> applyMetricPreset("plateDiscipline")), weightLp());
        presetRow2.addView(filterChip(isTeamMetricContext() || "hit".equals(allowedMetricRoleForCurrentContext()) ? "Power/Contact" : "Power/Contact Allowed", () -> applyMetricPreset("powerContact")), weightLp());
        presetRow2.addView(filterChip(isTeamMetricContext() || "pitch".equals(allowedMetricRoleForCurrentContext()) ? "Run Prevention" : "Speed/Baserunning", () -> applyMetricPreset(isTeamMetricContext() || "pitch".equals(allowedMetricRoleForCurrentContext()) ? "runPrevention" : "speedBaserunning")), weightLp());
        filterBox.addView(presetRow2, matchWrap());

        if (isTeamMetricContext()) {
            LinearLayout presetRow3 = new LinearLayout(this);
            presetRow3.setOrientation(LinearLayout.HORIZONTAL);
            presetRow3.addView(filterChip("Team Overall", () -> applyMetricPreset("teamOverall")), weightLp());
            presetRow3.addView(filterChip("Offense", () -> applyMetricPreset("teamOffense")), weightLp());
            presetRow3.addView(filterChip("Pitching/Defense", () -> applyMetricPreset("teamPitchingDefense")), weightLp());
            filterBox.addView(presetRow3, matchWrap());
        }

        LinearLayout presetRow4 = new LinearLayout(this);
        presetRow4.setOrientation(LinearLayout.HORIZONTAL);
        presetRow4.addView(filterChip("More Stats", () -> applyMetricPreset("moreStats")), weightLp());
        presetRow4.addView(filterChip("All Stats", () -> applyMetricPreset("all")), weightLp());
        filterBox.addView(presetRow4, matchWrap());

        TextView custom = text("Custom Card", 13, Color.WHITE, true);
        custom.setPadding(0, dp(12), 0, dp(2));
        filterBox.addView(custom);
        TextView customHint = text("Check stats below to create a Custom Lens. Selected stats become the scored card stats and the default rows below.", 11, Color.rgb(190, 205, 223), false);
        customHint.setPadding(0, 0, 0, dp(6));
        filterBox.addView(customHint);

        metricChecks.clear();
        ArrayList<Metric> contextMetrics = metricsForCurrentContext();
        for (int i = 0; i < contextMetrics.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(metricCheckChip(contextMetrics.get(i)), weightLp());
            if (i + 1 < contextMetrics.size()) row.addView(metricCheckChip(contextMetrics.get(i + 1)), weightLp());
            else row.addView(new Space(this), weightLp());
            filterBox.addView(row, matchWrap());
        }
    }

    private TextView filterChip(String label, Runnable onClick) {
        TextView tv = text(label, 13, Color.WHITE, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(8), dp(9), dp(8), dp(9));
        tv.setBackground(roundedStroke(Color.argb(10, 255, 255, 255), Color.argb(80, 190, 214, 236), 16, 1));
        tv.setOnClickListener(v -> onClick.run());
        return tv;
    }

    private CheckBox metricCheckChip(Metric m) {
        CheckBox cb = new CheckBox(this);
        cb.setText(m.label);
        cb.setTextColor(INK);
        cb.setTextSize(13);
        cb.setTypeface(Typeface.DEFAULT_BOLD);
        cb.setChecked(selectedMetricKeys.contains(m.key));
        cb.setButtonTintList(ColorStateList.valueOf(TEAL));
        cb.setPadding(dp(8), dp(6), dp(8), dp(6));
        cb.setBackground(roundedStroke(Color.rgb(248, 250, 253), LINE, 14, 1));
        cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updatingMetricChecks) return;
            metricsManuallyCustomized = true;
            activeComparisonPreset = "custom";
            showAllResultsStats = false;
            activeResultsStatCategory = "all";
            if (isChecked) selectedMetricKeys.add(m.key); else selectedMetricKeys.remove(m.key);
            if (!isChecked) keyEdgeMetricKeys.remove(m.key);
            if (keyEdgeMetricKeys.isEmpty()) resetKeyEdgeMetricsForSelectedStats();
            updateMetricPickerLabel();
            refreshCurrentResults();
        });
        metricChecks.put(m.key, cb);
        return cb;
    }

    private void applyMetricPreset(String preset) {
        String normalized = normalizePresetKey(preset);
        showAllResultsStats = false;
        activeResultsStatCategory = "all";
        applyMetricsForRoleAndPreset(allowedMetricRoleForCurrentContext(), normalized, true);
        refreshCurrentResults();
        refreshRankingsIfActive();
    }

    private void syncMetricChecks() {
        updatingMetricChecks = true;
        for (Map.Entry<String, CheckBox> e : metricChecks.entrySet()) e.getValue().setChecked(selectedMetricKeys.contains(e.getKey()));
        updatingMetricChecks = false;
    }

    private void wireEvents() {
        playerModeButton.setOnClickListener(v -> setMode(false));
        teamModeButton.setOnClickListener(v -> setMode(true));
        headToHeadButton.setOnClickListener(v -> setHeadToHeadMode(true));
        if (stickyCompareButton != null) stickyCompareButton.setOnClickListener(v -> setHeadToHeadMode(true));
        if (singleViewButton != null) singleViewButton.setOnClickListener(v -> openProfileForCurrentSelection());
        if (stickyProfileButton != null) stickyProfileButton.setOnClickListener(v -> openProfileForCurrentSelection());
        if (stickyRankingsButton != null) stickyRankingsButton.setOnClickListener(v -> showStandings());
        if (bottomHomeTab != null) bottomHomeTab.setOnClickListener(v -> setPrimaryTab(TAB_HOME));
        if (bottomMatchupTab != null) bottomMatchupTab.setOnClickListener(v -> setPrimaryTab(TAB_MATCHUP));
        if (bottomSearchTab != null) bottomSearchTab.setOnClickListener(v -> setPrimaryTab(TAB_PROFILE));
        if (bottomRankingsTab != null) bottomRankingsTab.setOnClickListener(v -> setPrimaryTab(TAB_RANKINGS));
        if (teamPickerButton != null) teamPickerButton.setOnClickListener(v -> showTeamPickerDialog(true));
        if (compareTeamPickerButton != null) compareTeamPickerButton.setOnClickListener(v -> showTeamPickerDialog(false));
        expectedViewButton.setOnClickListener(v -> setExpectedMode());

        searchInput.setOnFocusChangeListener((v, hasFocus) -> {
            setBottomDockVisible(!hasFocus);
            if (hasFocus) keepInputAboveKeyboard(searchInput, 70);
        });
        compareSearchInput.setOnFocusChangeListener((v, hasFocus) -> {
            setBottomDockVisible(!hasFocus);
            if (hasFocus) keepInputAboveKeyboard(compareSelectorLabel != null ? compareSelectorLabel : compareSearchInput, 56);
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterPlayers(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if ((actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) && !filteredPlayers.isEmpty()) {
                selectedPlayer = filteredPlayers.get(0);
                searchInput.setText("");
                searchInput.clearFocus();
                suggestionsList.setVisibility(View.GONE);
                applySmartDefaultForSelection(selectedPlayer);
                renderSelectionPreview();
                hideKeyboard();
                refreshAfterPrimarySelection();
                return true;
            }
            return false;
        });

        compareSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterComparePlayers(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        compareSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if ((actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) && !filteredComparePlayers.isEmpty()) {
                comparePlayer = filteredComparePlayers.get(0);
                compareSearchInput.setText("");
                compareSearchInput.clearFocus();
                compareSuggestionsList.setVisibility(View.GONE);
                renderComparePreview();
                hideKeyboard();
                refreshHeadToHeadIfReady();
                return true;
            }
            return false;
        });

        suggestionsList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < filteredPlayers.size()) {
                selectedPlayer = filteredPlayers.get(position);
                searchInput.setText("");
                searchInput.clearFocus();
                suggestionsList.setVisibility(View.GONE);
                applySmartDefaultForSelection(selectedPlayer);
                statusView.setText(statusTextForMode());
                renderSelectionPreview();
                hideKeyboard();
                refreshAfterPrimarySelection();
            }
        });

        compareSuggestionsList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < filteredComparePlayers.size()) {
                comparePlayer = filteredComparePlayers.get(position);
                compareSearchInput.setText("");
                compareSearchInput.clearFocus();
                compareSuggestionsList.setVisibility(View.GONE);
                statusView.setText("Second player selected · opening comparison.");
                renderComparePreview();
                hideKeyboard();
                refreshHeadToHeadIfReady();
            }
        });

        teamSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < allTeams.size()) {
                    selectedTeam = allTeams.get(position);
                    if (teamMode) {
                        buildTeamChips();
                        renderSelectionPreview();
                        statusView.setText(statusTextForMode());
                        refreshAfterPrimarySelection();
                    }
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        compareTeamSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < allTeams.size()) {
                    compareTeam = allTeams.get(position);
                    buildTeamChips();
                    updateTeamPickerButtons();
                    if (teamMode && headToHeadMode) { renderComparePreview(); refreshHeadToHeadIfReady(); }
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        seasonSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try { selectedSeasonValue = Integer.parseInt(String.valueOf(parent.getItemAtPosition(position))); refreshSeasonChips(); } catch (Exception ignored) {}
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        rankMetricSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedRankMetricPosition = position <= 0 ? -1 : position - 1;
                refreshRankMetricChips();
                refreshRankingsIfActive();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        metricPickerButton.setOnClickListener(v -> showMetricPicker());
        if (rankMetricPickerButton != null) rankMetricPickerButton.setOnClickListener(v -> showRankMetricPickerDialog());
        if (compareButton != null) compareButton.setOnClickListener(v -> compareSelected());
        standingsButton.setOnClickListener(v -> showStandings());
        copyButton.setOnClickListener(v -> copyCurrentTable());
        shareButton.setOnClickListener(v -> shareComparisonAsImage());
    }

    private void setMode(boolean useTeamMode) {
        boolean wasRankings = rankingsModeActive;
        boolean wasHeadToHead = headToHeadMode;
        boolean wasExpected = expectedMode;
        teamMode = useTeamMode;
        searchInput.setVisibility(teamMode ? View.GONE : View.VISIBLE);
        suggestionsList.setVisibility(View.GONE);
        if (teamSpinner != null) teamSpinner.setVisibility(View.GONE);
        if (teamPickerButton != null) teamPickerButton.setVisibility(teamMode ? View.VISIBLE : View.GONE);
        if (teamChipRow != null) teamChipRow.setVisibility(View.GONE);
        buildTeamChips();
        if (metricsManuallyCustomized || "custom".equals(activeComparisonPreset)) constrainSelectedMetricsToPlayerRole("both", true);
        else applyMetricsForRoleAndPreset("both", activeComparisonPreset, false);
        selectedRankMetricPosition = -1;
        rebuildRankMetricSpinner();
        updateMetricPickerLabel();
        rankingsModeActive = wasRankings;
        headToHeadMode = wasHeadToHead;
        expectedMode = wasExpected && !wasHeadToHead && !wasRankings;
        applyHeadToHeadVisibility();
        updateAnalysisModeButtons();
        updateViewModeButtons();
        statusView.setText(statusTextForMode());
        renderSelectionPreview();
        renderComparePreview();
        resultsBox.setVisibility(View.GONE);
        standingsBox.removeAllViews();
        if (rankingsModeActive) {
            showStandings();
        } else if (headToHeadMode) {
            if (!refreshHeadToHeadIfReady()) resultsBox.setVisibility(View.GONE);
        } else if (expectedMode) {
            if (!refreshExpectedIfReady()) resultsBox.setVisibility(View.GONE);
        } else {
            // v79: switching Player/Team should not drag the user into a profile.
            // Profiles open only after an explicit search selection or player/headshot tap.
            resultsBox.setVisibility(View.GONE);
        }
    }

    private int currentSeason() {
        return selectedSeasonValue > 0 ? selectedSeasonValue : Calendar.getInstance().get(Calendar.YEAR);
    }

    private void setHeadToHeadMode(boolean useHeadToHead) {
        activePrimaryTab = TAB_MATCHUP;
        if (homeBox != null) homeBox.setVisibility(View.GONE);
        if (form != null) form.setVisibility(View.VISIBLE);
        if (standingsBox != null) standingsBox.setVisibility(View.VISIBLE);
        updateBottomNavSelection();
        headToHeadMode = useHeadToHead;
        rankingsModeActive = false;
        expectedMode = false;
        updateAnalysisModeButtons();
        applyHeadToHeadVisibility();
        updateViewModeButtons();
        statusView.setText(statusTextForMode());
        renderComparePreview();
        standingsBox.removeAllViews();
        if (!refreshHeadToHeadIfReady()) resultsBox.setVisibility(View.GONE);
    }

    private void setExpectedMode() {
        activePrimaryTab = TAB_PROFILE;
        if (homeBox != null) homeBox.setVisibility(View.GONE);
        if (form != null) form.setVisibility(View.VISIBLE);
        updateBottomNavSelection();
        headToHeadMode = false;
        rankingsModeActive = false;
        expectedMode = true;
        updateAnalysisModeButtons();
        applyHeadToHeadVisibility();
        updateViewModeButtons();
        statusView.setText(statusTextForMode());
        renderComparePreview();
        standingsBox.removeAllViews();
        if (!refreshExpectedIfReady()) resultsBox.setVisibility(View.GONE);
    }

    private boolean refreshHeadToHeadIfReady() {
        if (!headToHeadMode || isBusy()) return false;
        if (teamMode) {
            if (selectedTeam == null || compareTeam == null || selectedTeam.key().equals(compareTeam.key())) return false;
            compareTeamsSideBySide(selectedTeam, compareTeam, currentSeason());
            return true;
        }
        if (selectedPlayer == null || comparePlayer == null || selectedPlayer.id == comparePlayer.id) return false;
        comparePlayersSideBySide(selectedPlayer, comparePlayer, currentSeason());
        return true;
    }

    private boolean refreshExpectedIfReady() {
        if (!expectedMode || isBusy()) return false;
        if (teamMode) {
            if (selectedTeam == null) return false;
            compareTeam(selectedTeam, currentSeason());
            return true;
        }
        if (selectedPlayer == null) return false;
        comparePlayer(selectedPlayer, currentSeason());
        return true;
    }

    private void updateAnalysisModeButtons() {
        updateViewModeButtons();
    }

    private void applyHeadToHeadVisibility() {
        boolean hasPrimary = teamMode ? selectedTeam != null : selectedPlayer != null;
        int showSecond = headToHeadMode && hasPrimary ? View.VISIBLE : View.GONE;
        if (compareSelectorLabel != null) compareSelectorLabel.setVisibility(showSecond);
        if (compareSearchInput != null) compareSearchInput.setVisibility(showSecond == View.VISIBLE && !teamMode ? View.VISIBLE : View.GONE);
        if (compareTeamSpinner != null) compareTeamSpinner.setVisibility(View.GONE);
        if (compareTeamPickerButton != null) compareTeamPickerButton.setVisibility(showSecond == View.VISIBLE && teamMode ? View.VISIBLE : View.GONE);
        if (compareTeamChipRow != null) compareTeamChipRow.setVisibility(View.GONE);
        if (compareSuggestionsList != null) compareSuggestionsList.setVisibility(View.GONE);
        if (comparePreviewBox != null) comparePreviewBox.setVisibility(showSecond);
        updateTeamPickerButtons();
    }

    private String statusTextForMode() {
        if (headToHeadMode) {
            boolean hasPrimary = teamMode ? selectedTeam != null : selectedPlayer != null;
            if (!hasPrimary) return teamMode ? "Matchup · choose Team A first." : "Matchup · choose Player A first.";
            return teamMode ? "Matchup · now choose Team B. The matchup opens automatically." : "Matchup · now choose Player B. The matchup opens automatically.";
        }
        if (rankingsModeActive) return teamMode ? "Rankings · browsing team leaderboards." : "Rankings · browsing player leaderboards.";
        if (expectedMode) {
            return teamMode ? "Profile · xStats view. Choose a team to open actual vs expected." : (selectedPlayer == null ? "Profile · xStats view. Search and select a player first." : "Profile · xStats view loaded for the selected player.");
        }
        return teamMode ? "Profile · choose a team." : (selectedPlayer == null ? "Profile · search and select a player." : "Profile · selected player loaded.");
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
                    compareTeamAdapter.clear();
                    for (Team t : allTeams) {
                        teamAdapter.add(t.name);
                        compareTeamAdapter.add(t.name);
                    }
                    teamAdapter.notifyDataSetChanged();
                    compareTeamAdapter.notifyDataSetChanged();
                    selectedTeam = null;
                    compareTeam = null;
                    buildTeamChips();
                    updateTeamPickerButtons();
                    statusView.setText(statusTextForMode());
                    renderSelectionPreview();
                    setBusy(false, null);
                });
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false, null);
                    showError("Could not load MLB team/player list. Check connection and tap Retry.\n" + e.getMessage());
                    statusView.setText("Roster list unavailable.");
                });
            }
        });
    }

    private void filterPlayers(String raw) {
        if (teamMode) return;
        filteredPlayers.clear();
        String q = raw.trim().toLowerCase(Locale.US);
        if (q.length() < 2 || allPlayers.isEmpty()) {
            suggestionsList.setVisibility(View.GONE);
            suggestionsAdapter.notifyDataSetChanged();
            return;
        }
        for (Player p : allPlayers) {
            if (p.searchKey.contains(q)) {
                filteredPlayers.add(p);
                if (filteredPlayers.size() >= 24) break;
            }
        }
        suggestionsAdapter.notifyDataSetChanged();
        suggestionsList.setVisibility(filteredPlayers.isEmpty() ? View.GONE : View.VISIBLE);
        if (!filteredPlayers.isEmpty()) keepInputAboveKeyboard(searchInput, 70);
    }

    private void filterComparePlayers(String raw) {
        if (teamMode || !headToHeadMode) return;
        filteredComparePlayers.clear();
        String q = raw.trim().toLowerCase(Locale.US);
        if (q.length() < 2 || allPlayers.isEmpty()) {
            compareSuggestionsList.setVisibility(View.GONE);
            compareSuggestionsAdapter.notifyDataSetChanged();
            return;
        }
        for (Player p : allPlayers) {
            if (selectedPlayer != null && p.id == selectedPlayer.id) continue;
            if (p.searchKey.contains(q)) {
                filteredComparePlayers.add(p);
                if (filteredComparePlayers.size() >= 24) break;
            }
        }
        compareSuggestionsAdapter.notifyDataSetChanged();
        compareSuggestionsList.setVisibility(filteredComparePlayers.isEmpty() ? View.GONE : View.VISIBLE);
        if (!filteredComparePlayers.isEmpty()) keepInputAboveKeyboard(compareSelectorLabel != null ? compareSelectorLabel : compareSearchInput, 56);
    }

    private void renderComparePreview() {
        if (comparePreviewBox == null) return;
        comparePreviewBox.removeAllViews();
        boolean hasSelection = headToHeadMode && (teamMode ? compareTeam != null : comparePlayer != null);
        comparePreviewBox.setVisibility(hasSelection ? View.VISIBLE : View.GONE);
        if (!hasSelection) return;

        TeamPalette palette = teamMode ? paletteForTeam(compareTeam) : paletteForAbbr(comparePlayer.teamAbbr);
        comparePreviewBox.setBackground(roundedGradient(new int[] { softColor(palette.primary, 0.20f), palette.primary, palette.secondary }, 20));

        if (teamMode) {
            View logo = teamLogoView(compareTeam, 46);
            LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(46), dp(46));
            logoLp.setMargins(0, 0, dp(11), 0);
            comparePreviewBox.addView(logo, logoLp);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(text(compareTeam.name, 16, Color.WHITE, true));
            col.addView(text("Head-to-head opponent · " + compareTeam.abbr, 11, Color.rgb(228, 238, 248), false));
            comparePreviewBox.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        } else {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setAdjustViewBounds(true);
            img.setBackground(roundedStroke(Color.WHITE, Color.argb(90, 255, 255, 255), 18, 1));
            img.setPadding(dp(2), dp(2), dp(2), dp(2));
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(46), dp(46));
            imgLp.setMargins(0, 0, dp(11), 0);
            comparePreviewBox.addView(img, imgLp);
            loadPlayerImage(comparePlayer.id, img);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(text(comparePlayer.fullName, 16, Color.WHITE, true));
            col.addView(text("Opponent · " + comparePlayer.teamAbbr + " · " + comparePlayer.position, 11, Color.rgb(228, 238, 248), false));
            comparePreviewBox.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        }
        TextView hint = text("VS", 11, Color.WHITE, true);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(10), dp(5), dp(10), dp(5));
        hint.setBackground(roundedStroke(Color.argb(34, 255, 255, 255), Color.argb(80, 255, 255, 255), 14, 1));
        comparePreviewBox.addView(hint);
    }

    private void compareSelected() {
        hideKeyboard();
        showError(null);
        rankingsModeActive = false;
        updateViewModeButtons();
        standingsBox.removeAllViews();
        int season = currentSeason();
        if (headToHeadMode) {
            compareSideBySideSelected(season);
            return;
        }
        lastHeadToHead = null;
        if (teamMode) {
            if (selectedTeam == null) { showError("Pick a team first."); return; }
            compareTeam(selectedTeam, season);
        } else {
            if (selectedPlayer == null) { showError("Pick a player from the search results first."); return; }
            comparePlayer(selectedPlayer, season);
        }
    }

    private void compareSideBySideSelected(int season) {
        if (teamMode) {
            if (selectedTeam == null || compareTeam == null) { showError("Pick two teams first."); return; }
            if (selectedTeam.key().equals(compareTeam.key())) { showError("Pick two different teams."); return; }
            compareTeamsSideBySide(selectedTeam, compareTeam, season);
        } else {
            if (selectedPlayer == null || comparePlayer == null) { showError("Pick two players first."); return; }
            if (selectedPlayer.id == comparePlayer.id) { showError("Pick two different players."); return; }
            comparePlayersSideBySide(selectedPlayer, comparePlayer, season);
        }
    }

    private void comparePlayer(Player player, int season) {
        lastComparison = null;
        lastHeadToHead = null;
        StatScope scope = scopeForPlayer(player);
        constrainSelectedMetricsToPlayerRole(roleForScope(scope), true);
        showProfileSkeleton();  // v29: skeleton while season data loads
        setBusy(true, "Loading selected season…");
        io.execute(() -> {
            try {
                ArrayList<LeaderboardEntry> entries = fetchLeaderboardForScope(season, scope);
                LeaderboardEntry seasonEntry = findPlayerEntry(entries, player);
                Stats seasonStats = seasonEntry == null ? new Stats() : copyStats(seasonEntry.stats);
                ensureDirectPlayerStatsForScope(player, season, seasonStats, scope);
                Stats leagueStats = computeLeagueAverage(entries);
                ArrayList<Metric> displayed = selectedMetricsForScope(scope);
                ArrayList<Metric> rankMetrics = metricsForRankScope(scope, false);
                HashMap<String, Integer> ranks = computePlayerRankMap(entries, season, player.id, rankMetrics);
                HashMap<String, Integer> totals = computePlayerRankTotalMap(entries, season, rankMetrics);
                HashMap<String, Double> percentiles = percentileMap(ranks, totals);
                Stats pendingCareer = new Stats();
                Comparison quick = new Comparison(false, player.fullName, player.teamAbbr, player.position, player.id, season, seasonStats, pendingCareer, leagueStats, new Date(), "Career loading…", player, null, ranks, totals, percentiles, new HashMap<>());
                main.post(() -> {
                    lastComparison = quick;
                    if (expectedMode) renderExpectedComparison(quick); else renderComparison(quick);
                    setBusy(false, expectedMode ? "Actual + expected loaded. Career numbers are filling in…" : "Season + league loaded. Career numbers are filling in…");
                });

                Stats careerStats = fetchPlayerCareerStats(player, season);
                LinkedHashMap<Integer, Stats> recentSeasons = fetchPlayerRecentSeasonStats(player, season);
                LinkedHashMap<String, Stats> recentWindows = fetchPlayerRecentWindows(player, season);
                Map<String, ArrayList<TrendPoint>> seasonTrends = fetchPlayerSeasonTrendMap(player, season);
                HashMap<String, Double> careerPercentiles = valuePercentileMapForPlayerEntries(entries, season, careerStats, rankMetrics);
                Comparison complete = new Comparison(false, player.fullName, player.teamAbbr, player.position, player.id, season, seasonStats, careerStats, leagueStats, new Date(), "Career", player, null, ranks, totals, percentiles, careerPercentiles, recentSeasons, seasonTrends, recentWindows);
                main.post(() -> {
                    if (!teamMode && selectedPlayer != null && selectedPlayer.id == player.id && currentSeason() == season) {
                        lastComparison = complete;
                        if (expectedMode) renderExpectedComparison(complete); else renderComparison(complete);
                        statusView.setText(expectedMode ? "Complete · actual vs expected loaded" : "Complete · season, league, and career loaded");
                    }
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
        lastComparison = null;
        lastHeadToHead = null;
        StatScope scope = StatScope.BOTH;
        constrainSelectedMetricsToPlayerRole("both", true);
        showProfileSkeleton();  // v29: skeleton while season data loads
        setBusy(true, "Loading selected season…");
        io.execute(() -> {
            try {
                ArrayList<LeaderboardEntry> entries = fetchLeaderboardForScope(season, scope);
                Map<String, Stats> aggregateSeeds = aggregateTeamStats(entries);
                Map<String, Stats> teamStatsMap = fetchLeagueTeamStatsForScope(season, scope, aggregateSeeds);
                Stats teamStats = teamStatsMap.get(team.key());
                if (teamStats == null) {
                    teamStats = fetchTeamStatsForScope(team, season, scope, aggregateSeeds.get(team.key()));
                    if (teamStats == null) teamStats = new Stats();
                    teamStatsMap.put(team.key(), teamStats);
                }
                Stats leagueStats = computeLeagueAverage(entries);
                ArrayList<Metric> displayed = selectedMetricsForScope(scope);
                ArrayList<Metric> rankMetrics = metricsForRankScope(scope, true);
                HashMap<String, Integer> ranks = computeTeamRankMap(teamStatsMap, team.key(), rankMetrics);
                HashMap<String, Integer> totals = computeTeamRankTotalMap(teamStatsMap, rankMetrics);
                HashMap<String, Double> percentiles = percentileMap(ranks, totals);
                Stats pendingHistory = new Stats();
                Comparison quick = new Comparison(true, team.name, team.abbr, "Team", 0, season, teamStats, pendingHistory, leagueStats, new Date(), "History loading…", null, team, ranks, totals, percentiles, new HashMap<>());
                main.post(() -> {
                    lastComparison = quick;
                    if (expectedMode) renderExpectedComparison(quick); else renderComparison(quick);
                    setBusy(false, expectedMode ? "Actual + expected loaded. Team history is filling in…" : "Season + league loaded. Team history is filling in…");
                });

                Stats historyStats = fetchTeamHistoryStats(team, season);
                LinkedHashMap<Integer, Stats> recentSeasons = fetchTeamRecentSeasonStats(team, season);
                Map<String, ArrayList<TrendPoint>> seasonTrends = fetchTeamSeasonTrendMap(team, season);
                HashMap<String, Double> historyPercentiles = valuePercentileMapForTeams(teamStatsMap, historyStats, rankMetrics);
                Comparison complete = new Comparison(true, team.name, team.abbr, "Team", 0, season, teamStats, historyStats, leagueStats, new Date(), "2015–" + season + " team avg", null, team, ranks, totals, percentiles, historyPercentiles, recentSeasons, seasonTrends, new LinkedHashMap<>());
                main.post(() -> {
                    if (teamMode && selectedTeam != null && selectedTeam.key().equals(team.key()) && currentSeason() == season) {
                        lastComparison = complete;
                        if (expectedMode) renderExpectedComparison(complete); else renderComparison(complete);
                        statusView.setText(expectedMode ? "Complete · actual vs expected loaded" : "Complete · season, league, and team history loaded");
                    }
                });
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false, null);
                    showError("Could not load team comparison. " + e.getMessage());
                });
            }
        });
    }

    private void comparePlayersSideBySide(Player a, Player b, int season) {
        lastComparison = null;
        lastHeadToHead = null;
        StatScope scope = scopeForPlayers(a, b);
        constrainSelectedMetricsToPlayerRole(roleForScope(scope), true);
        showProfileSkeleton();  // v29: skeleton while H2H loads
        setBusy(true, scope == StatScope.BOTH ? "Loading player side-by-side · hitting + pitching…" : "Loading player side-by-side…");
        io.execute(() -> {
            try {
                ArrayList<LeaderboardEntry> entries = fetchLeaderboardForScope(season, scope);
                LeaderboardEntry entryA = findPlayerEntry(entries, a);
                LeaderboardEntry entryB = findPlayerEntry(entries, b);
                Stats statsA = entryA == null ? new Stats() : copyStats(entryA.stats);
                Stats statsB = entryB == null ? new Stats() : copyStats(entryB.stats);
                ensureDirectPlayerStatsForScope(a, season, statsA, scope);
                ensureDirectPlayerStatsForScope(b, season, statsB, scope);
                Stats leagueStats = computeLeagueAverage(entries);
                ArrayList<Metric> displayed = selectedMetricsForScope(scope);
                ArrayList<Metric> rankMetrics = metricsForRankScope(scope, false);
                HashMap<String, Integer> ranksA = computePlayerRankMap(entries, season, a.id, rankMetrics);
                HashMap<String, Integer> ranksB = computePlayerRankMap(entries, season, b.id, rankMetrics);
                HashMap<String, Integer> totals = computePlayerRankTotalMap(entries, season, rankMetrics);
                HeadToHeadComparison h = new HeadToHeadComparison(false, a.fullName, b.fullName, a.teamAbbr + " · " + a.position, b.teamAbbr + " · " + b.position, a.id, b.id, season, statsA, statsB, leagueStats, a, b, null, null, ranksA, ranksB, totals, totals, percentileMap(ranksA, totals), percentileMap(ranksB, totals), scope, new ArrayList<>(displayed), keyEdgeMetricsForScope(scope));
                main.post(() -> {
                    lastComparison = null;
                    lastHeadToHead = h;
                    renderHeadToHead(h);
                    setBusy(false, "Loaded side-by-side for " + season);
                });
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false, null);
                    showError("Could not load side-by-side player comparison. " + e.getMessage());
                });
            }
        });
    }

    private void compareTeamsSideBySide(Team a, Team b, int season) {
        lastComparison = null;
        lastHeadToHead = null;
        StatScope scope = StatScope.BOTH;
        constrainSelectedMetricsToPlayerRole("both", true);
        showProfileSkeleton();  // v29: skeleton while H2H loads
        setBusy(true, "Loading team side-by-side · hitting + pitching…");
        io.execute(() -> {
            try {
                ArrayList<LeaderboardEntry> entries = fetchLeaderboardForScope(season, scope);
                Map<String, Stats> aggregateSeeds = aggregateTeamStats(entries);
                Map<String, Stats> teams = fetchLeagueTeamStatsForScope(season, scope, aggregateSeeds);
                Stats statsA = teams.get(a.key());
                Stats statsB = teams.get(b.key());
                if (statsA == null) {
                    statsA = fetchTeamStatsForScope(a, season, scope, aggregateSeeds.get(a.key()));
                    if (statsA == null) statsA = new Stats();
                    teams.put(a.key(), statsA);
                }
                if (statsB == null) {
                    statsB = fetchTeamStatsForScope(b, season, scope, aggregateSeeds.get(b.key()));
                    if (statsB == null) statsB = new Stats();
                    teams.put(b.key(), statsB);
                }
                Stats leagueStats = computeLeagueAverage(entries);
                ArrayList<Metric> displayed = selectedMetricsForScope(scope);
                ArrayList<Metric> rankMetrics = metricsForRankScope(scope, true);
                HashMap<String, Integer> ranksA = computeTeamRankMap(teams, a.key(), rankMetrics);
                HashMap<String, Integer> ranksB = computeTeamRankMap(teams, b.key(), rankMetrics);
                HashMap<String, Integer> totals = computeTeamRankTotalMap(teams, rankMetrics);
                HeadToHeadComparison h = new HeadToHeadComparison(true, a.name, b.name, a.abbr, b.abbr, 0, 0, season, statsA, statsB, leagueStats, null, null, a, b, ranksA, ranksB, totals, totals, percentileMap(ranksA, totals), percentileMap(ranksB, totals), scope, new ArrayList<>(displayed), keyEdgeMetricsForScope(scope));
                main.post(() -> {
                    lastComparison = null;
                    lastHeadToHead = h;
                    renderHeadToHead(h);
                    setBusy(false, "Loaded side-by-side for " + season);
                });
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false, null);
                    showError("Could not load side-by-side team comparison. " + e.getMessage());
                });
            }
        });
    }


    private LinearLayout premiumPanelCard(int radius) {
        LinearLayout card = verticalCard(radius, new int[] {
                Color.rgb(5, 10, 18),
                Color.rgb(8, 18, 34),
                Color.rgb(7, 24, 46)
        });
        card.setBackground(roundedGradientStroke(new int[] {
                Color.rgb(5, 10, 18),
                Color.rgb(8, 18, 34),
                Color.rgb(7, 24, 46)
        }, radius, Color.argb(92, 104, 195, 228), 1));
        return card;
    }

    private TextView premiumInfoPill(String value, int accent) {
        TextView tv = text(value, 11, Color.WHITE, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(10), dp(6), dp(10), dp(6));
        tv.setSingleLine(false);
        tv.setBackground(roundedStroke(Color.argb(18, 255, 255, 255), softColor(accent, 0.75f), 999, 1));
        return tv;
    }

    private void renderSearchLanding() {
        if (standingsBox == null) return;
        standingsBox.setVisibility(View.VISIBLE);
        standingsBox.removeAllViews();
        LinearLayout card = premiumPanelCard(28);
        card.setPadding(dp(15), dp(15), dp(15), dp(15));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(10), 0, 0);
        standingsBox.addView(card, lp);

        TextView eyebrow = text(teamMode ? "TEAM SEARCH" : "PLAYER SEARCH", 10, Color.rgb(126, 235, 226), true);
        eyebrow.setLetterSpacing(0.18f);
        card.addView(eyebrow);
        card.addView(text(teamMode ? "Find a team profile" : "Find a player profile", 22, Color.WHITE, true));
        TextView sub = text(teamMode
                ? "Use the Team button above, then open a premium team profile with season, ranking, and side-by-side-ready stats."
                : "Type a name above, tap a result, and the profile opens with season, ranking, expected-stat, and comparison-ready views.",
                12, Color.rgb(190, 205, 223), false);
        sub.setPadding(0, dp(6), 0, dp(10));
        card.addView(sub);

        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.addView(premiumInfoPill(teamMode ? "30 teams" : "Active players", Color.rgb(88, 210, 232)), weightLp());
        pills.addView(premiumInfoPill("Season chips", Color.rgb(245, 198, 74)), weightLp());
        pills.addView(premiumInfoPill("Tap to open", Color.rgb(99, 166, 255)), weightLp());
        card.addView(pills, matchWrap());

        TextView tip = text(teamMode ? "Tip: switch to Player any time to search player profiles." : "Tip: switch to Team to browse team profiles.", 11, Color.rgb(166, 183, 205), false);
        tip.setPadding(0, dp(10), 0, 0);
        card.addView(tip);
    }

    private void renderRankingsLoadingCard(int season, Metric metric) {
        if (standingsBox == null) return;
        standingsBox.setVisibility(View.VISIBLE);
        standingsBox.removeAllViews();
        LinearLayout card = premiumPanelCard(28);
        card.setPadding(dp(15), dp(15), dp(15), dp(15));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(10), 0, 0);
        standingsBox.addView(card, lp);

        TextView eyebrow = text(teamMode ? "TEAM RANKINGS" : "PLAYER RANKINGS", 10, Color.rgb(126, 235, 226), true);
        eyebrow.setLetterSpacing(0.18f);
        card.addView(eyebrow);
        String statName = metric == null ? "selected stats" : metric.label;
        card.addView(text("Loading " + statName, 22, Color.WHITE, true));
        TextView sub = text(season + " season · " + (teamMode ? "all MLB teams" : "qualified MLB players"), 12, Color.rgb(190, 205, 223), false);
        sub.setPadding(0, dp(6), 0, dp(10));
        card.addView(sub);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(premiumInfoPill(teamMode ? "Team leaders" : "Player leaders", Color.rgb(88, 210, 232)), weightLp());
        row.addView(premiumInfoPill(metric == null ? "Multi-stat" : "Single stat", Color.rgb(245, 198, 74)), weightLp());
        row.addView(premiumInfoPill("Tap rows", Color.rgb(99, 166, 255)), weightLp());
        card.addView(row, matchWrap());
    }

    private void showStandings() {
        activePrimaryTab = TAB_RANKINGS;
        if (homeBox != null) homeBox.setVisibility(View.GONE);
        if (form != null) form.setVisibility(View.VISIBLE);
        updateBottomNavSelection();
        hideKeyboard();
        showError(null);
        headToHeadMode = false;
        expectedMode = false;
        rankingsModeActive = true;
        generalRankingsMode = true;
        applyHeadToHeadVisibility();
        updateAnalysisModeButtons();
        rebuildRankMetricSpinner();
        updateViewModeButtons();
        resultsBox.setVisibility(View.GONE);
        if (standingsBox != null) standingsBox.setVisibility(View.VISIBLE);
        int season = currentSeason();
        Metric metric = selectedRankMetric();
        if (metric == null) {
            renderRankingsLoadingCard(season, null);
            showAllSelectedRankings(season);
            return;
        }
        renderRankingsLoadingCard(season, metric);
        setBusy(true, "Loading " + (teamMode ? "team" : "player") + " rankings…");
        io.execute(() -> {
            try {
                ArrayList<LeaderboardEntry> entries = fetchLeaderboardForMetric(season, metric);
                if (teamMode) renderTeamStandingsAsync(entries, season, metric);
                else renderPlayerStandingsAsync(entries, season, metric);
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false, null);
                    showError("Could not load rankings. " + e.getMessage());
                });
            }
        });
    }

    private void showAllSelectedRankings(int season) {
        setBusy(true, "Loading rankings across selected stats…");
        io.execute(() -> {
            try {
                ArrayList<Metric> selected = selectedRankingMetrics();
                if (teamMode) renderTeamAllStatsRankingsAsync(season, selected);
                else renderPlayerAllStatsRankingsAsync(season, selected);
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false, null);
                    showError("Could not load all-stat rankings. " + e.getMessage());
                });
            }
        });
    }

    private boolean hasRankingContextSelection(boolean teamRows) {
        return teamRows ? selectedTeam != null : selectedPlayer != null;
    }

    private void addRankingScopeToggle(LinearLayout card, boolean teamRows) {
        // v144: Rankings is now a global leaderboard surface.
        // Player/team-specific rank context belongs in profiles, so no secondary scope toggle here.
    }

    private void renderPlayerStandingsAsync(ArrayList<LeaderboardEntry> entries, int season, Metric metric) {
        ArrayList<LeaderboardEntry> eligible = eligiblePlayerEntries(entries, season, metric);
        main.post(() -> {
            renderPlayerStandings(eligible, season, metric);
            setBusy(false, null);
        });
    }

    private void renderTeamStandingsAsync(ArrayList<LeaderboardEntry> entries, int season, Metric metric) {
        Map<String, Stats> aggregateSeeds = aggregateTeamStats(entries);
        Map<String, Stats> teamStats = fetchLeagueTeamStatsForScope(season, StatScope.BOTH, aggregateSeeds);
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

    private ArrayList<LeaderboardEntry> eligiblePlayerEntries(ArrayList<LeaderboardEntry> entries, int season, Metric metric) {
        ArrayList<LeaderboardEntry> eligible = new ArrayList<>();
        for (LeaderboardEntry e : entries) {
            Double val = e.stats.get(metric.key);
            if (val == null) continue;
            int minPa = season == Calendar.getInstance().get(Calendar.YEAR) ? 50 : 100;
            int minBbe = season == Calendar.getInstance().get(Calendar.YEAR) ? 25 : 50;
            boolean contactMetric = metric.type.equals("contact") || metric.type.equals("rate");
            boolean pitchingMetric = "pitch".equals(metric.side);
            boolean eligibleSample = pitchingMetric ? (e.stats.ip >= (season == Calendar.getInstance().get(Calendar.YEAR) ? 10 : 25) || e.stats.pa >= minPa) : (e.stats.pa >= minPa && (!contactMetric || e.stats.bbe >= minBbe));
            if (eligibleSample) eligible.add(e);
        }
        sortEntries(eligible, metric);
        return eligible;
    }

    private void renderPlayerAllStatsRankingsAsync(int season, ArrayList<Metric> selected) throws Exception {
        ArrayList<AllRankRow> rows = new ArrayList<>();
        for (Metric m : selected) {
            ArrayList<LeaderboardEntry> entries = eligiblePlayerEntries(fetchLeaderboardForMetric(season, m), season, m);
            if (entries.isEmpty()) continue;
            LeaderboardEntry leader = entries.get(0);
            if (generalRankingsMode || selectedPlayer == null) {
                rows.add(new AllRankRow(m, 1, entries.size(), leader.name, leader.stats.get(m.key), leader.name, leader.stats.get(m.key), leader.playerId, null));
            } else {
                int rank = selectedPlayerRank(entries);
                LeaderboardEntry mine = rank > 0 ? entries.get(rank - 1) : null;
                rows.add(new AllRankRow(m, rank, entries.size(), mine == null ? selectedPlayer.fullName : mine.name, mine == null ? null : mine.stats.get(m.key), leader.name, leader.stats.get(m.key), selectedPlayer.id, null));
            }
        }
        main.post(() -> {
            renderAllStatsRankings(rows, season, false);
            setBusy(false, null);
        });
    }

    private void renderTeamAllStatsRankingsAsync(int season, ArrayList<Metric> selected) throws Exception {
        ArrayList<AllRankRow> rows = new ArrayList<>();
        Map<String, Stats> aggregateSeeds = aggregateTeamStats(fetchLeaderboardForScope(season, StatScope.BOTH));
        Map<String, Stats> teamStats = fetchLeagueTeamStatsForScope(season, StatScope.BOTH, aggregateSeeds);
        for (Metric m : selected) {
            ArrayList<TeamStanding> teams = new ArrayList<>();
            for (Team t : allTeams) {
                Stats s = teamStats.get(t.key());
                if (s != null && s.get(m.key) != null) teams.add(new TeamStanding(t, s));
            }
            teams.sort((a, b) -> compareMetricValues(a.stats.get(m.key), b.stats.get(m.key), m));
            if (teams.isEmpty()) continue;
            TeamStanding leader = teams.get(0);
            if (generalRankingsMode || selectedTeam == null) {
                rows.add(new AllRankRow(m, 1, teams.size(), leader.team.name, leader.stats.get(m.key), leader.team.name, leader.stats.get(m.key), 0, leader.team));
            } else {
                int rank = selectedTeamRank(teams);
                TeamStanding mine = rank > 0 ? teams.get(rank - 1) : null;
                rows.add(new AllRankRow(m, rank, teams.size(), mine == null ? selectedTeam.name : mine.team.name, mine == null ? null : mine.stats.get(m.key), leader.team.name, leader.stats.get(m.key), 0, selectedTeam));
            }
        }
        main.post(() -> {
            renderAllStatsRankings(rows, season, true);
            setBusy(false, null);
        });
    }

    private void renderAllStatsRankings(ArrayList<AllRankRow> rows, int season, boolean teamRows) {
        standingsBox.setVisibility(View.VISIBLE);
        standingsBox.removeAllViews();
        LinearLayout card = premiumPanelCard(24);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        standingsBox.addView(card, matchWrap());
        String subject = teamRows ? ((generalRankingsMode || selectedTeam == null) ? "Team leaders" : selectedTeam.name) : ((generalRankingsMode || selectedPlayer == null) ? "Player leaders" : selectedPlayer.fullName);
        card.addView(text(subject, 20, Color.WHITE, true));
        addRankingScopeToggle(card, teamRows);
        TextView sub = text(season + " season · rank, value, and current leader", 12, Color.rgb(178, 195, 216), false);
        sub.setPadding(0, dp(3), 0, dp(8));
        card.addView(sub);
        lastStandingsText = subject + " " + season + " rankings across selected stats\nMetric\tRank\tValue\tLeader\tLeader Value\n";
        if (rows.isEmpty()) {
            TextView empty = text("No ranking rows found for the selected stats. Try a different stat preset.", 14, Color.rgb(178, 195, 216), false);
            empty.setPadding(0, dp(10), 0, dp(10));
            card.addView(empty);
        }
        for (AllRankRow r : rows) {
            card.addView(allRankRow(r, teamRows));
            lastStandingsText += r.metric.label + "\t" + (r.rank > 0 ? "#" + r.rank + " of " + r.total : "Not ranked") + "\t" + format(r.value, r.metric) + "\t" + r.leaderName + "\t" + format(r.leaderValue, r.metric) + "\n";
        }
        resultsBox.setVisibility(View.GONE);
    }

    private View allRankRow(AllRankRow r, boolean teamRows) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.setBackground(roundedStroke(Color.argb(22, 255, 255, 255), Color.argb(60, 190, 214, 236), 15, 1));
        row.setForeground(ripple(false));
        row.setClickable(true);
        row.setOnClickListener(v -> openProfileFromRanking(r, teamRows));

        if (teamRows && r.team != null) {
            View logo = teamLogoView(r.team, 34);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
            lp.setMargins(0, 0, dp(8), 0);
            row.addView(logo, lp);
        } else if (!teamRows && r.playerId > 0) {
            ImageView avatar = new ImageView(this);
            avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
            avatar.setBackground(roundedStroke(Color.rgb(235, 241, 248), LINE, 17, 1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
            lp.setMargins(0, 0, dp(8), 0);
            row.addView(avatar, lp);
            loadPlayerImage(r.playerId, avatar);
        }

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        String rankText = displayRankLabel(r.rank, r.total, teamRows);
        col.addView(text(r.metric.label + " · " + rankText, 13, Color.WHITE, true));
        col.addView(text("Leader: " + r.leaderName + " " + format(r.leaderValue, r.metric), 10, Color.rgb(178, 195, 216), false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        TextView val = text(format(r.value, r.metric), 14, Color.rgb(88, 210, 232), true);
        val.setGravity(Gravity.RIGHT);
        row.addView(val);

        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(3), 0, dp(3));
        row.setLayoutParams(lp);
        return row;
    }

    private void openProfileFromRanking(AllRankRow r, boolean teamRows) {
        if (r == null) return;
        rankingsModeActive = false;
        generalRankingsMode = false;
        if (teamRows) {
            Team t = r.team;
            if (t == null) t = findTeamByName(r.subjectName);
            if (t == null) t = findTeamByName(r.leaderName);
            if (t != null) {
                teamMode = true;
                selectedTeam = t;
                if (teamSpinner != null) teamSpinner.setSelection(Math.max(0, allTeams.indexOf(t)));
                setMode(true);
            }
        } else if (r.playerId > 0) {
            Player p = findPlayerById(r.playerId);
            if (p != null) {
                teamMode = false;
                selectedPlayer = p;
                applySmartDefaultForSelection(p);
                setMode(false);
            }
        }
        updateViewModeButtons();
        renderSelectionPreview();
        openProfileForCurrentSelection();
    }

    private ArrayList<Metric> metricsForRankScope(StatScope scope, boolean teamContext) {
        ArrayList<Metric> out = new ArrayList<>();
        String role = roleForScope(scope);
        for (Metric m : metrics) {
            if (m == null) continue;
            if (teamContext) {
                if ("team".equals(m.side)) out.add(m);
            } else {
                if ("team".equals(m.side)) continue;
                if (role == null || role.isEmpty() || "both".equals(role) || "two".equals(role) || role.equals(m.side)) out.add(m);
            }
        }
        return out;
    }

    private ArrayList<Metric> selectedMetricsForRows() {
        return selectedMetricsForScope(currentStatScope());
    }

    private ArrayList<Metric> selectedMetricsForScope(StatScope scope) {
        String role = roleForScope(scope);
        ArrayList<Metric> out = new ArrayList<>();
        for (Metric m : metrics) if (selectedMetricKeys.contains(m.key) && roleAllowsMetric(role, m)) out.add(m);
        if (out.isEmpty()) {
            LinkedHashSet<String> fallback = metricKeysForPresetAndRole("all", role);
            for (Metric m : metrics) if (fallback.contains(m.key) && roleAllowsMetric(role, m)) out.add(m);
        }
        return out;
    }

    private ArrayList<Metric> availableMetricsForScope(StatScope scope) {
        String role = roleForScope(scope);
        ArrayList<Metric> out = new ArrayList<>();
        LinkedHashSet<String> allKeys = metricKeysForPresetAndRole("all", role);
        for (Metric m : metrics) {
            if (allKeys.contains(m.key) && roleAllowsMetric(role, m)) out.add(m);
        }
        return out;
    }

    private ArrayList<Metric> metricsWithHeadToHeadValues(HeadToHeadComparison h, ArrayList<Metric> source) {
        ArrayList<Metric> out = new ArrayList<>();
        if (source == null) return out;
        for (Metric m : source) if (hasHeadToHeadMetricValue(h, m)) out.add(m);
        return out;
    }

    private ArrayList<Metric> metricsWithProfileValues(Comparison c, ArrayList<Metric> source) {
        ArrayList<Metric> out = new ArrayList<>();
        if (source == null) return out;
        for (Metric m : source) if (hasComparisonMetricValue(c, m)) out.add(m);
        return out;
    }

    private ArrayList<Metric> keyEdgeMetricsForScope(StatScope scope) {
        String role = roleForScope(scope);
        ArrayList<Metric> out = new ArrayList<>();
        for (String key : keyEdgeMetricKeys) {
            Metric m = findMetricByKey(key);
            if (m != null && roleAllowsMetric(role, m) && selectedMetricKeys.contains(m.key)) out.add(m);
            if (out.size() >= 8) return out;
        }
        if (out.isEmpty()) {
            LinkedHashSet<String> fallback = defaultKeyEdgeForRole(role, selectedMetricKeys);
            for (String key : fallback) {
                Metric m = findMetricByKey(key);
                if (m != null && roleAllowsMetric(role, m)) out.add(m);
                if (out.size() >= 8) return out;
            }
        }
        return out;
    }

    private HashMap<String, Integer> computePlayerRankMap(ArrayList<LeaderboardEntry> entries, int season, int playerId, ArrayList<Metric> metricList) {
        HashMap<String, Integer> ranks = new HashMap<>();
        if (playerId <= 0) return ranks;
        for (Metric m : metricList) {
            ArrayList<LeaderboardEntry> eligible = eligiblePlayerEntries(entries, season, m);
            for (int i = 0; i < eligible.size(); i++) {
                if (eligible.get(i).playerId == playerId) {
                    ranks.put(m.key, i + 1);
                    break;
                }
            }
        }
        return ranks;
    }

    private HashMap<String, Integer> computeTeamRankMap(Map<String, Stats> teamStats, String teamKey, ArrayList<Metric> metricList) {
        HashMap<String, Integer> ranks = new HashMap<>();
        for (Metric m : metricList) {
            ArrayList<TeamStanding> teams = new ArrayList<>();
            for (Team t : allTeams) {
                Stats s = teamStats.get(t.key());
                if (s != null && s.get(m.key) != null) teams.add(new TeamStanding(t, s));
            }
            teams.sort((a, b) -> compareMetricValues(a.stats.get(m.key), b.stats.get(m.key), m));
            for (int i = 0; i < teams.size(); i++) {
                if (teams.get(i).team.key().equals(teamKey)) {
                    ranks.put(m.key, i + 1);
                    break;
                }
            }
        }
        return ranks;
    }

    private HashMap<String, Integer> computePlayerRankTotalMap(ArrayList<LeaderboardEntry> entries, int season, ArrayList<Metric> metricList) {
        HashMap<String, Integer> totals = new HashMap<>();
        for (Metric m : metricList) totals.put(m.key, eligiblePlayerEntries(entries, season, m).size());
        return totals;
    }

    private HashMap<String, Integer> computeTeamRankTotalMap(Map<String, Stats> teamStats, ArrayList<Metric> metricList) {
        HashMap<String, Integer> totals = new HashMap<>();
        for (Metric m : metricList) {
            int total = 0;
            for (Team t : allTeams) {
                Stats s = teamStats.get(t.key());
                if (s != null && s.get(m.key) != null) total++;
            }
            totals.put(m.key, total);
        }
        return totals;
    }

    private HashMap<String, Double> percentileMap(Map<String, Integer> ranks, Map<String, Integer> totals) {
        HashMap<String, Double> out = new HashMap<>();
        if (ranks == null || totals == null) return out;
        for (String key : ranks.keySet()) {
            Double pct = percentileFromRank(ranks.get(key), totals.get(key));
            if (pct != null) out.put(key, pct);
        }
        return out;
    }

    private Double percentileFromRank(Integer rank, Integer total) {
        if (rank == null || total == null || rank <= 0 || total <= 0) return null;
        if (total == 1) return 100.0;
        return Math.max(0, Math.min(100, 100.0 * (total - rank) / (double) (total - 1)));
    }

    private String percentileLabel(Double pct) {
        if (pct == null || Double.isNaN(pct)) return "—";
        return Math.round(Math.max(0, Math.min(100, pct))) + "% percentile";
    }

    private String rankPercentileLabel(Integer rank, Integer total, Double pct) {
        String rankText = rank == null || rank <= 0 ? "Not ranked" : "#" + rank + (total == null || total <= 0 ? "" : " of " + total);
        String pctText = pct == null ? "" : " · " + percentileLabel(pct);
        return rankText + pctText;
    }

    private String rankOnlyLabel(Integer rank, Integer total) {
        if (rank == null || rank <= 0) return "Not ranked";
        return "#" + rank + (total == null || total <= 0 ? "" : " of " + total);
    }

    private String displayRankLabel(Integer rank, Integer total, boolean teamContext) {
        if (rank == null || rank <= 0) return "Not ranked";
        String value = rankOnlyLabel(rank, total);
        if (teamContext && total != null && total > 0 && total < 24) return "Partial " + value;
        return value;
    }

    private String rankTypeLabel(boolean teamContext) {
        return teamContext ? "Team Rank" : "Player Rank";
    }

    private String percentileBigLabel(Double pct) {
        if (pct == null || Double.isNaN(pct)) return "—";
        return Math.round(Math.max(0, Math.min(100, pct))) + "%";
    }

    /** v30: Returns a tier label from a percentile value. */
    private String percentileTierLabel(Double pct) {
        if (pct == null || Double.isNaN(pct)) return "—";
        int p = (int) Math.round(Math.max(0, Math.min(100, pct)));
        if (p >= 95) return "Elite";
        if (p >= 80) return "Great";
        if (p >= 60) return "Above avg";
        if (p >= 40) return "Average";
        if (p >= 20) return "Below avg";
        return "Poor";
    }

    private String leagueContextLabel(Double pct, Metric m) {
        if (pct == null || Double.isNaN(pct)) return isContextOnlyMetric(m) ? "Context" : "League —";
        int rounded = (int)Math.round(Math.max(0, Math.min(100, pct)));
        if (isContextOnlyMetric(m)) return "Profile · " + rounded + "%";
        if (isTargetRangeMetric(m)) return "Target · " + rounded + "%";
        // Percentile is already polarity-adjusted. Lower-is-better stats like ERA/WHIP/HR Allowed
        // should therefore still read as strong league standing when the raw value is low.
        return "League " + percentileTierLabel(pct) + " · " + rounded + "%";
    }

    private boolean isLowerGoodMetric(Metric m) {
        return m != null && m.higherGood != null && !m.higherGood;
    }

    private boolean isHigherGoodMetric(Metric m) {
        return m != null && m.higherGood != null && m.higherGood;
    }

    private HashMap<String, Double> valuePercentileMapForPlayerEntries(ArrayList<LeaderboardEntry> entries, int season, Stats stats, ArrayList<Metric> metricList) {
        HashMap<String, Double> out = new HashMap<>();
        if (stats == null) return out;
        for (Metric m : metricList) {
            Double value = stats.get(m.key);
            if (value == null || Double.isNaN(value)) continue;
            ArrayList<Double> vals = new ArrayList<>();
            for (LeaderboardEntry e : eligiblePlayerEntries(entries, season, m)) {
                Double v = e.stats.get(m.key);
                if (v != null && !Double.isNaN(v)) vals.add(v);
            }
            Double pct = percentileForValue(vals, value, m);
            if (pct != null) out.put(m.key, pct);
        }
        return out;
    }

    private HashMap<String, Double> valuePercentileMapForTeams(Map<String, Stats> teamStats, Stats stats, ArrayList<Metric> metricList) {
        HashMap<String, Double> out = new HashMap<>();
        if (stats == null) return out;
        for (Metric m : metricList) {
            Double value = stats.get(m.key);
            if (value == null || Double.isNaN(value)) continue;
            ArrayList<Double> vals = new ArrayList<>();
            for (Stats s : teamStats.values()) {
                Double v = s.get(m.key);
                if (v != null && !Double.isNaN(v)) vals.add(v);
            }
            Double pct = percentileForValue(vals, value, m);
            if (pct != null) out.put(m.key, pct);
        }
        return out;
    }

    private Double percentileForValue(ArrayList<Double> values, Double value, Metric m) {
        if (values == null || values.isEmpty() || value == null || Double.isNaN(value)) return null;
        int better = 0;
        for (Double v : values) {
            if (v == null || Double.isNaN(v)) continue;
            int cmp = compareMetricValues(v, value, m);
            if (cmp < 0) better++;
        }
        return percentileFromRank(better + 1, values.size());
    }

    private void renderComparison(Comparison c) {
        hideProfileSkeleton();  // v29: remove skeleton before rendering content
        resultsBox.setVisibility(View.VISIBLE);
        headerBox.removeAllViews();
        metricBox.removeAllViews();
        if (copyButton != null) copyButton.setVisibility(View.VISIBLE);
        if (shareButton != null) shareButton.setVisibility(View.VISIBLE);

        TeamPalette palette = paletteForComparison(c);

        LinearLayout card = verticalCard(24, null);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(roundedGradient(new int[] {
                Color.rgb(8, 12, 20),
                darkColor(palette.primary, 0.46f),
                darkColor(palette.secondary, 0.62f),
                Color.rgb(7, 11, 18)
        }, 24));
        card.setElevation(dp(5));
        headerBox.addView(card, matchWrap());

        LinearLayout topMeta = new LinearLayout(this);
        topMeta.setOrientation(LinearLayout.HORIZONTAL);
        topMeta.setGravity(Gravity.CENTER_VERTICAL);
        TextView profileLabel = text(c.isTeam ? "TEAM PROFILE" : "PLAYER PROFILE", 10, softColor(palette.primary, 0.12f), true);
        profileLabel.setLetterSpacing(0.10f);
        topMeta.addView(profileLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView seasonBadge = text(c.season + " SEASON", 10, Color.rgb(235, 242, 251), true);
        seasonBadge.setGravity(Gravity.CENTER);
        seasonBadge.setPadding(dp(10), dp(5), dp(10), dp(5));
        seasonBadge.setBackground(roundedStroke(Color.argb(28, 255, 255, 255), Color.argb(78, 255, 255, 255), 15, 1));
        topMeta.addView(seasonBadge);
        card.addView(topMeta, matchWrap());

        FrameLayout heroShell = new FrameLayout(this);
        heroShell.setPadding(dp(14), dp(14), dp(14), dp(14));
        heroShell.setBackground(roundedGradient(new int[] {
                Color.rgb(7, 10, 16),
                darkColor(palette.primary, 0.70f),
                Color.rgb(6, 10, 18)
        }, 26));
        LinearLayout.LayoutParams topLp = matchWrap();
        topLp.setMargins(0, dp(10), 0, dp(8));
        card.addView(heroShell, topLp);

        View heroGlow = new View(this);
        heroGlow.setBackground(roundedGradient(new int[] {
                Color.argb(120, Color.red(palette.primary), Color.green(palette.primary), Color.blue(palette.primary)),
                Color.argb(44, Color.red(palette.secondary), Color.green(palette.secondary), Color.blue(palette.secondary)),
                Color.TRANSPARENT
        }, 26));
        FrameLayout.LayoutParams glowLp = new FrameLayout.LayoutParams(dp(280), dp(210));
        glowLp.gravity = Gravity.START | Gravity.TOP;
        glowLp.leftMargin = dp(4);
        heroShell.addView(heroGlow, glowLp);

        View heroSheen = new View(this);
        heroSheen.setBackground(roundedGradient(new int[] {
                Color.argb(24, 255, 255, 255),
                Color.argb(8, 255, 255, 255),
                Color.TRANSPARENT
        }, 26));
        FrameLayout.LayoutParams sheenLp = new FrameLayout.LayoutParams(-1, -1);
        heroShell.addView(heroSheen, sheenLp);

        if (c.team != null) {
            ImageView watermark = new ImageView(this);
            watermark.setScaleType(ImageView.ScaleType.FIT_CENTER);
            watermark.setAlpha(0.10f);
            loadTeamLogo(c.team, watermark);
            FrameLayout.LayoutParams wmLp = new FrameLayout.LayoutParams(dp(150), dp(150));
            wmLp.gravity = Gravity.END | Gravity.TOP;
            wmLp.topMargin = dp(0);
            wmLp.rightMargin = dp(0);
            heroShell.addView(watermark, wmLp);
        }

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams topInnerLp = new FrameLayout.LayoutParams(-1, -2);
        topInnerLp.gravity = Gravity.BOTTOM;
        heroShell.addView(top, topInnerLp);

        FrameLayout avatarFrame = new FrameLayout(this);
        avatarFrame.setPadding(dp(6), dp(6), dp(6), dp(6));
        avatarFrame.setBackground(roundedGradient(new int[] {
                softColor(palette.primary, 0.04f),
                softColor(palette.primary, 0.14f),
                softColor(palette.secondary, 0.28f)
        }, 64));
        applyRoundedClip(avatarFrame, 64);
        avatarFrame.setElevation(dp(4));

        View ringGlow = new View(this);
        ringGlow.setBackground(roundedGradient(new int[] {
                softColor(palette.primary, 0.10f),
                softColor(palette.primary, 0.16f),
                softColor(palette.secondary, 0.18f)
        }, 60));
        avatarFrame.addView(ringGlow, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout matte = new FrameLayout(this);
        matte.setPadding(dp(3), dp(3), dp(3), dp(3));
        matte.setBackground(rounded(Color.argb(248, 255, 255, 255), 56));
        applyRoundedClip(matte, 56);
        FrameLayout.LayoutParams matteLp = new FrameLayout.LayoutParams(-1, -1);
        matteLp.setMargins(dp(2), dp(2), dp(2), dp(2));
        avatarFrame.addView(matte, matteLp);
        if (!c.isTeam) {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setAdjustViewBounds(false);
            img.setBackground(rounded(Color.WHITE, 52));
            applyRoundedClip(img, 52);
            matte.addView(img, new FrameLayout.LayoutParams(-1, -1));
            loadPlayerImage(c.mlbId, img);
        } else {
            View teamLogo = teamLogoView(c.team, 78);
            FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(-2, -2);
            logoLp.gravity = Gravity.CENTER;
            matte.addView(teamLogo, logoLp);
        }
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(124), dp(124));
        imgLp.setMargins(0, 0, dp(16), 0);
        top.addView(avatarFrame, imgLp);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        top.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));
        if (c.team != null) {
            TextView club = text(safe(c.team.name).toUpperCase(Locale.US), 11, softColor(palette.primary, 0.20f), true);
            club.setLetterSpacing(0.10f);
            titleCol.addView(club);
        }
        TextView name = text(c.name, 27, Color.WHITE, true);
        name.setLetterSpacing(0.02f);
        titleCol.addView(name);
        TextView meta = text(c.meta, 13, Color.rgb(214, 223, 236), true);
        meta.setPadding(0, dp(4), 0, dp(10));
        titleCol.addView(meta);
        LinearLayout miniPills = new LinearLayout(this);
        miniPills.setOrientation(LinearLayout.HORIZONTAL);
        if (c.seasonStats.ip > 0 && (c.seasonStats.get("era") != null || c.seasonStats.get("whip") != null)) {
            miniPills.addView(profileDataPill(new DecimalFormat("0.0").format(c.seasonStats.ip), "IP", palette), weightLp());
            miniPills.addView(profileDataPill(fmtCount(c.seasonStats.pa), "BF", palette), weightLp());
        } else {
            miniPills.addView(profileDataPill(fmtCount(c.seasonStats.pa), c.isTeam ? "PA" : "PA", palette), weightLp());
            miniPills.addView(profileDataPill(fmtCount(c.seasonStats.bbe), "BBE", palette), weightLp());
        }
        titleCol.addView(miniPills, matchWrap());

        addBaseballCardSummary(card, c, palette);
        if (!c.isTeam) addPlayerLensSummaryCard(card, c, palette);

        LinearLayout sectionRow = new LinearLayout(this);
        sectionRow.setOrientation(LinearLayout.HORIZONTAL);
        sectionRow.setGravity(Gravity.CENTER_VERTICAL);
        sectionRow.setPadding(0, dp(9), 0, dp(3));
        TextView tableTitle = text(c.isTeam ? "Profile comparison" : "Lens vs league average", 18, Color.rgb(214, 225, 242), true);
        sectionRow.addView(tableTitle, new LinearLayout.LayoutParams(0, -2, 1));
        sectionRow.addView(c.isTeam ? comparisonLegend(c.thirdLabelShort(), palette) : profileSparkLegend());
        metricBox.addView(sectionRow, matchWrap());
        TextView scaleHint = text(c.isTeam ? "Bar position = league percentile: 0% left · 50% midpoint · 100% right." : "Spark center = league average. Right is better, left is worse. Color shows good / average / below avg.", 10, Color.rgb(132, 146, 166), false);
        scaleHint.setPadding(dp(1), 0, dp(1), dp(8));
        metricBox.addView(scaleHint, matchWrap());

        ArrayList<Metric> profileMetrics = new ArrayList<>();
        for (Metric m : selectedMetricsForRows()) if (hasComparisonMetricValue(c, m)) profileMetrics.add(m);
        ArrayList<Metric> availableProfileMetrics = metricsWithProfileValues(c, availableMetricsForScope(c.isTeam ? StatScope.BOTH : currentStatScope()));
        addLensStatsControl(metricBox, "Lens stats", profileMetrics, availableProfileMetrics, () -> {
            if (lastComparison != null) renderComparison(lastComparison);
        });
        ArrayList<Metric> visibleProfileMetrics = showAllResultsStats ? availableProfileMetrics : profileMetrics;
        if (visibleProfileMetrics.isEmpty() && showAllResultsStats) visibleProfileMetrics = profileMetrics;

        int count = 0;
        String lastSection = "";
        int sepAccent = boostNeonColor(readableTeamColor(palette.primary, palette.secondary, true), 1.08f, 1.03f);
        for (Metric m : visibleProfileMetrics) {
            String section = metricSectionLabel(m);
            if (count > 0 && !section.equals(lastSection)) metricBox.addView(statGroupSeparator(section, sepAccent), matchWrap());
            renderMetricRow(c, m, palette);
            lastSection = section;
            count++;
        }
        if (count == 0) {
            TextView empty = text("No stats available for this selection.", 14, MUTED, false);
            empty.setPadding(0, dp(10), 0, dp(10));
            metricBox.addView(empty);
        }
        if (suppressNextAutoScroll) suppressNextAutoScroll = false;
        else scrollToResultsTop();
    }


    private void addPlayerLensSummaryCard(LinearLayout card, Comparison c, TeamPalette palette) {
        if (card == null || c == null || c.isTeam) return;
        ArrayList<Metric> lensMetrics = scorableLensMetricsForProfile(c);
        PlayerLeagueMatchupCardView lensCard = new PlayerLeagueMatchupCardView(this, c, lensMetrics, palette);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(760));
        lp.setMargins(0, dp(10), 0, 0);
        card.addView(lensCard, lp);
    }

    private View profileSparkLegend() {
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.VERTICAL);
        legend.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        legend.addView(legendMini("Better", profilePositiveColor()));
        legend.addView(legendMini("League avg", Color.rgb(190, 202, 222)));
        legend.addView(legendMini("Below avg", profileNegativeColor()));
        return legend;
    }

    private ArrayList<Metric> scorableLensMetricsForProfile(Comparison c) {
        ArrayList<Metric> out = new ArrayList<>();
        if (c == null || c.seasonStats == null || c.leagueStats == null) return out;
        for (Metric m : selectedMetricsForRows()) {
            if (m == null || m.higherGood == null) continue;
            if (!hasComparisonMetricValue(c, m)) continue;
            Double value = c.seasonStats.get(m.key);
            Double league = c.leagueStats.get(m.key);
            if (value == null || league == null || Double.isNaN(value) || Double.isNaN(league)) continue;
            out.add(m);
        }
        return out;
    }

    private Double averageLensPercentile(Comparison c, ArrayList<Metric> lensMetrics) {
        if (c == null || c.percentile == null || lensMetrics == null || lensMetrics.isEmpty()) return null;
        double total = 0d;
        int count = 0;
        for (Metric m : lensMetrics) {
            Double pct = c.percentile.get(m.key);
            if (pct == null || Double.isNaN(pct)) continue;
            total += Math.max(0d, Math.min(100d, pct));
            count++;
        }
        return count == 0 ? null : total / count;
    }

    private double averageLensSignedEdge(Comparison c, ArrayList<Metric> lensMetrics) {
        if (c == null || c.seasonStats == null || c.leagueStats == null || lensMetrics == null || lensMetrics.isEmpty()) return 0d;
        double total = 0d;
        int count = 0;
        for (Metric m : lensMetrics) {
            Double value = c.seasonStats.get(m.key);
            Double league = c.leagueStats.get(m.key);
            Double signed = signedLeagueEdge(value, league, m);
            if (signed == null || Double.isNaN(signed)) continue;
            total += Math.max(-1d, Math.min(1d, signed));
            count++;
        }
        return count == 0 ? 0d : total / count;
    }

    private Double signedLeagueEdge(Double value, Double league, Metric m) {
        if (value == null || league == null || m == null || m.higherGood == null || Double.isNaN(value) || Double.isNaN(league)) return null;
        double raw = value - league;
        double goodDirection = m.higherGood ? raw : -raw;
        double dead = profileStatDeadZone(m);
        if (Math.abs(goodDirection) <= dead) return 0d;
        double benchmark = profileMeaningfulGapBenchmark(m);
        if (benchmark <= 0d) return 0d;
        double mag = Math.min(1d, (Math.abs(goodDirection) - dead) / benchmark);
        double eased = 1d - Math.pow(1d - mag, 1.20d);
        return goodDirection > 0 ? eased : -eased;
    }

    private String leagueDeltaLabel(Double value, Double league, Metric m) {
        if (value == null || league == null || m == null || Double.isNaN(value) || Double.isNaN(league)) return "";
        Double signedEdge = signedLeagueEdge(value, league, m);
        Double raw = value - league;
        if (signedEdge == null || Math.abs(signedEdge) < 0.035d) return "even vs avg";
        return signedFormat(raw, m) + " vs avg · " + (signedEdge > 0 ? "better" : "below");
    }

    private int profilePositiveColor() { return Color.rgb(78, 229, 235); }
    private int profileNegativeColor() { return Color.rgb(255, 135, 112); }
    private int profileNeutralColor() { return Color.rgb(210, 220, 235); }

    private int profileSparkColor(Double signedEdge) {
        if (signedEdge == null || Double.isNaN(signedEdge) || Math.abs(signedEdge) < 0.08d) return profileNeutralColor();
        return signedEdge > 0 ? profilePositiveColor() : profileNegativeColor();
    }

    private double profileStatDeadZone(Metric m) {
        if (m == null) return 0d;
        if (m.decimals <= 0) return 0.000001d;
        return 0.5d * Math.pow(10d, -m.decimals);
    }

    private double profileMeaningfulGapBenchmark(Metric m) {
        if (m == null || m.key == null) return 0.10d;
        switch (m.key) {
            case "avg": return 0.045d;
            case "obp": return 0.035d;
            case "slg": return 0.080d;
            case "ops": return 0.075d;
            case "wOBA": return 0.055d;
            case "xwOBA": return 0.065d;
            case "xBA": return 0.040d;
            case "xOBP": return 0.040d;
            case "xSLG": return 0.080d;
            case "xISO": return 0.060d;
            case "wOBAcon":
            case "xwOBAcon": return 0.070d;
            case "hr": return 12d;
            case "xbh": return 18d;
            case "rbi":
            case "r": return 20d;
            case "sb": return 10d;
            case "bbPct":
            case "kPct":
            case "bbMinusKPct":
            case "barrelPct":
            case "hardHitPct":
            case "whiffPct":
            case "chasePct":
            case "zoneContactPct":
            case "sweetSpotPct": return 5d;
            case "avgEV": return 4.5d;
            case "sprintSpeed": return 1.8d;
            case "era": return 1.35d;
            case "whip": return 0.38d;
            case "k9": return 4.00d;
            case "bb9": return 1.75d;
            case "kbb": return 1.50d;
            case "pitchKPct":
            case "pitchBBPct":
            case "pitchKMinusBBPct":
            case "pWhiffPct":
            case "pChasePct":
            case "pHardHitPct":
            case "pBarrelPct": return 5d;
            case "pxwOBA":
            case "pwOBA": return 0.065d;
            case "pxBA": return 0.040d;
            case "pxSLG": return 0.080d;
            case "pAvgEV": return 4.5d;
            case "pOppAvg": return 0.045d;
            case "pOppOps": return 0.075d;
            case "ip": return 45d;
            case "pitchK": return 40d;
            case "pitchBB": return 12d;
            case "saves": return 10d;
            default:
                if (m.unit != null && m.unit.equals("%")) return 5d;
                if (m.isCount()) return 20d;
                return 0.10d;
        }
    }

    private String currentLensNameForUi() {
        return metricPresetNameForRole(selectedMetricKeys, allowedMetricRoleForCurrentContext());
    }

    private void addLensStatsControl(LinearLayout target, String title, ArrayList<Metric> lensMetrics, ArrayList<Metric> allMetrics, Runnable refreshAction) {
        if (target == null) return;
        int lensCount = lensMetrics == null ? 0 : lensMetrics.size();
        int allCount = allMetrics == null ? 0 : allMetrics.size();
        String lensName = currentLensNameForUi();

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(10), dp(10), dp(10), dp(10));
        shell.setBackground(roundedStroke(Color.argb(18, 255, 255, 255), Color.argb(58, 190, 214, 236), 18, 1));
        LinearLayout.LayoutParams shellLp = matchWrap();
        shellLp.setMargins(0, dp(2), 0, dp(9));
        target.addView(shell, shellLp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView label = text(showAllResultsStats ? "All stats shown" : title, 13, Color.WHITE, true);
        copy.addView(label);
        String desc = showAllResultsStats
                ? "Card scored by " + lensName + " · " + lensCount + " Lens stats · " + allCount + " available rows"
                : "Lens: " + lensName + " · card score and rows use the same " + lensCount + " stats";
        TextView sub = text(desc, 11, Color.rgb(178, 195, 216), false);
        sub.setPadding(0, dp(2), 0, 0);
        copy.addView(sub);
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView change = filterChip("Change Lens", () -> showMetricPicker());
        LinearLayout.LayoutParams changeLp = new LinearLayout.LayoutParams(dp(118), dp(36));
        changeLp.setMargins(dp(8), 0, 0, 0);
        top.addView(change, changeLp);
        shell.addView(top, matchWrap());

        if (allCount > lensCount && allCount > 0) {
            TextView toggle = filterChip(showAllResultsStats ? "Show Lens Stats" : "Show All Stats", () -> {
                showAllResultsStats = !showAllResultsStats;
                suppressNextAutoScroll = true;
                if (refreshAction != null) refreshAction.run();
            });
            LinearLayout.LayoutParams toggleLp = matchWrap();
            toggleLp.setMargins(0, dp(8), 0, 0);
            shell.addView(toggle, toggleLp);
        }
    }

    private void addProfileCategoryBrowser(Comparison c, ArrayList<Metric> rowMetrics) {
        if (c == null || rowMetrics == null || rowMetrics.size() < 4) return;
        String[] cats = categoryKeysForRole(c.isTeam ? "both" : allowedMetricRoleForCurrentContext());
        if (!hasKey(cats, activeResultsStatCategory)) activeResultsStatCategory = "all";

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(2), 0, dp(5));
        TextView label = text("Stat tabs", 12, Color.rgb(166, 182, 205), true);
        header.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        int matching = 0;
        for (Metric m : rowMetrics) if (metricMatchesCategory(m, activeResultsStatCategory)) matching++;
        TextView status = text(("all".equals(activeResultsStatCategory) ? "Selected" : categoryLabel(activeResultsStatCategory)) + " · " + matching + " stats", 11, Color.rgb(132, 146, 166), false);
        header.addView(status);
        metricBox.addView(header, matchWrap());

        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String cat : cats) {
            TextView chip = profileCategoryChip(cat);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
            chipLp.setMargins(0, 0, dp(7), 0);
            chipRow.addView(chip, chipLp);
        }
        LinearLayout.LayoutParams tabsLp = matchWrap();
        tabsLp.setMargins(0, 0, 0, dp(8));
        metricBox.addView(horizontalChipScroller(chipRow), tabsLp);
    }

    private TextView profileCategoryChip(String key) {
        boolean active = key.equals(activeResultsStatCategory);
        TextView tv = text(categoryLabel(key), 12, active ? Color.rgb(7, 18, 28) : Color.rgb(220, 229, 242), true);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        if (active) tv.setBackground(roundedGradientStroke(new int[] { Color.rgb(255, 243, 140), Color.rgb(88, 210, 232) }, 999, Color.argb(170, 255, 255, 255), 1));
        else tv.setBackground(roundedStroke(Color.argb(24, 255, 255, 255), Color.argb(58, 255, 255, 255), 999, 1));
        tv.setOnClickListener(v -> {
            int keepY = mainScroll == null ? 0 : mainScroll.getScrollY();
            activeResultsStatCategory = key;
            suppressNextAutoScroll = true;
            if (lastComparison != null) renderComparison(lastComparison);
            if (mainScroll != null) mainScroll.post(() -> mainScroll.scrollTo(0, keepY));
        });
        return tv;
    }

    private void addBaseballCardSummary(LinearLayout card, Comparison c, TeamPalette palette) {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams shellLp = matchWrap();
        shellLp.setMargins(0, dp(10), 0, 0);
        shell.setBackground(roundedStroke(Color.argb(204, 10, 14, 22), Color.argb(74, 255, 255, 255), 20, 1));
        card.addView(shell, shellLp);
        View accent = new View(this);
        accent.setBackground(roundedGradient(new int[] { palette.primary, palette.secondary }, 8));
        LinearLayout.LayoutParams accentLp = new LinearLayout.LayoutParams(-1, dp(5));
        accentLp.setMargins(0, 0, 0, dp(8));
        shell.addView(accent, accentLp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        shell.addView(head, matchWrap());
        LinearLayout headText = new LinearLayout(this);
        headText.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(c.isTeam ? "TEAM CARD" : "PLAYER CARD", 10, softColor(palette.primary, 0.18f), true);
        title.setLetterSpacing(0.08f);
        headText.addView(title);
        headText.addView(text(c.isTeam ? "Recent seasons + 2015+ avg" : "Recent seasons + career", 12, Color.rgb(244, 248, 252), true));
        head.addView(headText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chip = text(String.valueOf(c.season), 10, Color.WHITE, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(9), dp(4), dp(9), dp(4));
        chip.setBackground(roundedGradient(new int[] { softColor(palette.primary, 0.14f), softColor(palette.secondary, 0.20f) }, 14));
        head.addView(chip);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout table = new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        hsv.addView(table, new HorizontalScrollView.LayoutParams(-2, -2));
        LinearLayout.LayoutParams hsvLp = matchWrap();
        hsvLp.setMargins(0, dp(7), 0, 0);
        shell.addView(hsv, hsvLp);

        Metric[] cols = summaryMetricsForCard(c);
        table.addView(baseballCardHeaderRow(cols, palette));
        TreeMap<Integer, Stats> seasonsForCard = new TreeMap<>();
        if (c.recentSeasons != null) seasonsForCard.putAll(c.recentSeasons);
        seasonsForCard.put(c.season, c.seasonStats);
        ArrayList<Integer> yearsForCard = new ArrayList<>(seasonsForCard.keySet());
        if (yearsForCard.size() > 3) yearsForCard = new ArrayList<>(yearsForCard.subList(yearsForCard.size() - 3, yearsForCard.size()));
        if (yearsForCard.isEmpty()) {
            table.addView(baseballCardStatRow("MLB", c.leagueStats, cols, palette, false, false));
        } else {
            for (Integer y : yearsForCard) {
                table.addView(baseballCardStatRow(String.valueOf(y), seasonsForCard.get(y), cols, palette, y == c.season, false));
            }
        }
        table.addView(baseballCardStatRow(c.isTeam ? "2015+" : "Career", c.careerStats, cols, palette, false, true));
        addRecentWindowsSummary(shell, c, palette);
    }

    private void addRecentWindowsSummary(LinearLayout parent, Comparison c, TeamPalette palette) {
        if (c == null || c.isTeam || c.season != Calendar.getInstance().get(Calendar.YEAR) || c.recentWindows == null || c.recentWindows.isEmpty()) return;
        Metric[] cols = recentWindowMetricsForCard(c);
        if (cols.length == 0) return;

        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, dp(8), 0, 0);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Recent form", 10, Color.rgb(212, 222, 234), true);
        title.setLetterSpacing(0.08f);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        section.addView(header, matchWrap());

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(9), dp(7), dp(9), dp(7));
        panel.setBackground(roundedStroke(Color.argb(182, 12, 16, 24), Color.argb(58, 255, 255, 255), 16, 1));
        LinearLayout.LayoutParams panelLp = matchWrap();
        panelLp.setMargins(0, dp(4), 0, 0);

        ArrayList<Map.Entry<String, Stats>> windows = new ArrayList<>(c.recentWindows.entrySet());
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(text("", 9, MUTED, true), new LinearLayout.LayoutParams(dp(52), -2));
        for (Map.Entry<String, Stats> e : windows) {
            TextView w = text(cleanRecentWindowLabel(e.getKey()), 9, palette.primary, true);
            w.setGravity(Gravity.CENTER);
            w.setLetterSpacing(0.08f);
            top.addView(w, new LinearLayout.LayoutParams(0, -2, 1));
        }
        panel.addView(top, matchWrap());

        for (Metric m : cols) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(0, dp(5), 0, 0);
            TextView lab = text(m.label, 9, Color.rgb(184, 196, 212), true);
            lab.setLetterSpacing(0.06f);
            line.addView(lab, new LinearLayout.LayoutParams(dp(52), -2));
            for (Map.Entry<String, Stats> e : windows) {
                Double v = e.getValue() == null ? null : e.getValue().get(m.key);
                TextView val = text(format(v, m), 11, Color.rgb(240, 245, 252), true);
                val.setGravity(Gravity.CENTER);
                val.setPadding(dp(2), dp(3), dp(2), dp(3));
                val.setBackground(roundedStroke(Color.argb(32, 255, 255, 255), Color.argb(58, 255, 255, 255), 12, 1));
                LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(0, -2, 1);
                vlp.setMargins(dp(4), 0, 0, 0);
                line.addView(val, vlp);
            }
            panel.addView(line, matchWrap());
        }
        section.addView(panel, panelLp);
        parent.addView(section, matchWrap());
    }

    private String cleanRecentWindowLabel(String raw) {
        String s = safe(raw).replace("Last", "").replace("last", "").trim();
        s = s.replace("days", "d").replace("day", "d").replace(" ", "");
        return s.toUpperCase(Locale.US);
    }

    private Metric[] recentWindowMetricsForCard(Comparison c) {
        boolean pitch = c != null && c.player != null && isPitcher(c.player);
        String[] keys = pitch ? new String[] { "era", "whip", "k9" } : new String[] { "avg", "ops", "hr" };
        ArrayList<Metric> cols = new ArrayList<>();
        for (String k : keys) {
            Metric m = metricByKey(k);
            if (m != null) cols.add(m);
        }
        return cols.toArray(new Metric[0]);
    }

    private Metric[] summaryMetricsForCard(Comparison c) {
        boolean pitch = !c.isTeam && c.player != null && isPitcher(c.player);
        if (!pitch && c.isTeam) {
            int pitchCount = 0, hitCount = 0;
            for (Metric m : selectedMetricsForRows()) { if ("pitch".equals(m.side)) pitchCount++; else hitCount++; }
            pitch = pitchCount > hitCount;
        }
        String[] keys = pitch ? new String[] { "era", "whip", "k9", "kbb" } : new String[] { "avg", "ops", "wOBA", "xwOBA" };
        ArrayList<Metric> cols = new ArrayList<>();
        for (String k : keys) {
            Metric m = metricByKey(k);
            if (m != null) cols.add(m);
        }
        return cols.toArray(new Metric[0]);
    }

    private LinearLayout baseballCardHeaderRow(Metric[] cols, TeamPalette palette) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(5));
        TextView season = tableCell("Season", 56, Color.rgb(186, 199, 216), true, Gravity.LEFT);
        row.addView(season);
        for (Metric m : cols) row.addView(tableCell(m.label, 68, Color.rgb(186, 199, 216), true, Gravity.CENTER));
        return row;
    }

    private LinearLayout baseballCardStatRow(String label, Stats stats, Metric[] cols, TeamPalette palette, boolean current, boolean career) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(6), dp(7), dp(6));
        LinearLayout.LayoutParams rowLp = matchWrap();
        rowLp.setMargins(0, dp(3), 0, dp(3));
        row.setLayoutParams(rowLp);
        int fill = current ? Color.argb(188, 18, 22, 30) : (career ? Color.argb(146, 28, 32, 40) : Color.argb(28, 255, 255, 255));
        int stroke = current ? softColor(palette.primary, 0.28f) : (career ? softColor(palette.primary, 0.50f) : Color.argb(44, 255, 255, 255));
        row.setBackground(roundedStroke(fill, stroke, 14, 1));
        int labelColor = current ? softColor(palette.primary, 0.18f) : (career ? Color.rgb(248, 236, 210) : Color.rgb(235, 242, 250));
        int valueColor = current ? Color.WHITE : (career ? Color.rgb(246, 239, 221) : Color.rgb(235, 242, 250));
        row.addView(tableCell(label, 56, labelColor, true, Gravity.LEFT));
        for (Metric m : cols) {
            Double val = stats == null ? null : stats.get(m.key);
            row.addView(tableCell(format(val, m), 68, valueColor, current || career, Gravity.CENTER));
        }
        return row;
    }

    private TextView tableCell(String value, int widthDp, int color, boolean bold, int gravity) {
        TextView tv = text(value == null ? "—" : value, 11, color, bold);
        tv.setGravity(gravity);
        tv.setSingleLine(true);
        tv.setPadding(dp(3), dp(1), dp(3), dp(1));
        tv.setMinWidth(dp(widthDp));
        tv.setFontFeatureSettings("'tnum' 1");  // v29: tabular figures for baseball-card cells
        return tv;
    }

    private void renderExpectedComparison(Comparison c) {
        renderComparison(c);
        TeamPalette palette = paletteForComparison(c);
        metricBox.removeAllViews();

        LinearLayout sectionRow = new LinearLayout(this);
        sectionRow.setOrientation(LinearLayout.HORIZONTAL);
        sectionRow.setGravity(Gravity.CENTER_VERTICAL);
        sectionRow.setPadding(0, dp(9), 0, dp(3));
        sectionRow.addView(text("Actual vs expected", 18, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = text("xStats", 11, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(9), dp(5), dp(9), dp(5));
        badge.setBackground(roundedGradient(new int[] { palette.primary, palette.secondary }, 14));
        sectionRow.addView(badge);
        metricBox.addView(sectionRow, matchWrap());

        TextView hint = text("This mode compares what happened to Statcast's expected version of the same stat. Positive gap = outperforming expected; negative gap = underperforming expected.", 11, MUTED, false);
        hint.setPadding(dp(1), 0, dp(1), dp(6));
        metricBox.addView(hint, matchWrap());

        int count = 0;
        count += renderExpectedActualRow(c, "avg", "xBA", "Batting average", palette) ? 1 : 0;
        count += renderExpectedActualRow(c, "slg", "xSLG", "Slugging", palette) ? 1 : 0;
        count += renderExpectedActualRow(c, "wOBA", "xwOBA", "wOBA", palette) ? 1 : 0;
        count += renderLuckExplainerRow(c, palette) ? 1 : 0;
        if (count == 0) {
            TextView empty = text("No actual/expected stat pairs are available for this selection. Try a hitter or team with Statcast hitting data.", 14, MUTED, false);
            empty.setPadding(0, dp(10), 0, dp(10));
            metricBox.addView(empty);
        }
    }

    private boolean renderExpectedActualRow(Comparison c, String actualKey, String expectedKey, String label, TeamPalette palette) {
        Metric actualMetric = metricByKey(actualKey);
        Metric expectedMetric = metricByKey(expectedKey);
        Double actual = c.seasonStats.get(actualKey);
        Double expected = c.seasonStats.get(expectedKey);
        if (actual == null && expected == null) return false;
        Double gap = diff(actual, expected);
        Double actualPct = c.percentile == null ? null : c.percentile.get(actualKey);
        Double expectedPct = c.percentile == null ? null : c.percentile.get(expectedKey);

        LinearLayout row = verticalCard(18, null);
        row.setPadding(dp(14), dp(12), dp(14), dp(13));
        int rowAccent = boostNeonColor(readableTeamColor(palette.primary, palette.secondary, true), 1.12f, 1.05f);
        row.setBackground(roundedGradientStroke(new int[] {
                Color.rgb(4, 8, 15),
                mixColor(rowAccent, Color.rgb(6, 10, 18), 0.90f),
                Color.rgb(5, 10, 18)
        }, 22, Color.argb(78, Color.red(rowAccent), Color.green(rowAccent), Color.blue(rowAccent)), 1));
        LinearLayout.LayoutParams rowLp = matchWrap();
        rowLp.setMargins(0, dp(7), 0, 0);
        metricBox.addView(row, rowLp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.addView(text(label, 16, INK, true));
        titleCol.addView(text(actualMetric.label + " vs " + expectedMetric.label, 11, MUTED, false));
        top.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));
        TextView gapBadge = text(gap == null ? "No gap" : "Gap " + signedFormat(gap, actualMetric), 11, Color.WHITE, true);
        gapBadge.setGravity(Gravity.CENTER);
        gapBadge.setPadding(dp(9), dp(5), dp(9), dp(5));
        int gapColor = gap == null || Math.abs(gap) < 0.000001 ? Color.rgb(70, 88, 125) : (gap > 0 ? palette.primary : palette.secondary);
        gapBadge.setBackground(roundedGradient(new int[] { gapColor, softColor(gapColor, 0.10f) }, 14));
        top.addView(gapBadge);
        row.addView(top);

        ExpectedActualBarView bar = new ExpectedActualBarView(this, actualMetric, actual, expected, actualPct, expectedPct, palette);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, dp(54));
        barLp.setMargins(0, dp(7), 0, 0);
        row.addView(bar, barLp);

        LinearLayout values = new LinearLayout(this);
        values.setOrientation(LinearLayout.HORIZONTAL);
        values.setPadding(0, dp(5), 0, 0);
        values.addView(statValueColumn("Actual", format(actual, actualMetric), actualPct == null ? "Observed" : percentileBigLabel(actualPct) + " percentile", palette.primary, true), weightLp());
        values.addView(statValueColumn("Expected", format(expected, expectedMetric), expectedPct == null ? "xStat" : percentileBigLabel(expectedPct) + " percentile", palette.secondary, false), weightLp());
        values.addView(statValueColumn("Gap", gap == null ? "—" : signedFormat(gap, actualMetric), gap == null ? "Actual - expected" : (gap > 0 ? "Over expected" : (gap < 0 ? "Under expected" : "Even")), Color.rgb(70, 88, 125), false), weightLp());
        row.addView(values);
        return true;
    }

    private boolean renderLuckExplainerRow(Comparison c, TeamPalette palette) {
        Double luck = c.seasonStats.get("luck");
        if (luck == null) return false;
        Metric m = metricByKey("luck");
        LinearLayout row = verticalCard(18, null);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(roundedStroke(Color.WHITE, softColor(palette.primary, 0.70f), 18, 1));
        LinearLayout.LayoutParams rowLp = matchWrap();
        rowLp.setMargins(0, dp(7), 0, 0);
        metricBox.addView(row, rowLp);
        row.addView(text("Luck index", 16, INK, true));
        TextView detail = text("wOBA - xwOBA. Positive means results are running ahead of expected quality of contact; negative means results are lagging expected quality.", 11, MUTED, false);
        detail.setPadding(0, dp(3), 0, dp(6));
        row.addView(detail);
        LinearLayout values = new LinearLayout(this);
        values.setOrientation(LinearLayout.HORIZONTAL);
        values.addView(statValueColumn("wOBA", format(c.seasonStats.get("wOBA"), metricByKey("wOBA")), "Actual", palette.primary, true), weightLp());
        values.addView(statValueColumn("xwOBA", format(c.seasonStats.get("xwOBA"), metricByKey("xwOBA")), "Expected", palette.secondary, false), weightLp());
        values.addView(statValueColumn("Luck", signedFormat(luck, m), luck > 0 ? "Lucky" : (luck < 0 ? "Unlucky" : "Neutral"), Color.rgb(70, 88, 125), false), weightLp());
        row.addView(values);
        return true;
    }



    private void renderHeadToHead(HeadToHeadComparison h) {
        hideProfileSkeleton();  // v29: remove skeleton before rendering content
        resultsBox.setVisibility(View.VISIBLE);
        headerBox.removeAllViews();
        metricBox.removeAllViews();

        TeamPalette paletteA = paletteForHeadToHeadSide(h, true);
        TeamPalette paletteB = paletteForHeadToHeadSide(h, false);

        // v30.1: Unified dark battle card replaces old white header + separate duel card
        addBattleCard(h, paletteA, paletteB);

        LinearLayout sectionRow = new LinearLayout(this);
        sectionRow.setOrientation(LinearLayout.HORIZONTAL);
        sectionRow.setGravity(Gravity.CENTER_VERTICAL);
        sectionRow.setPadding(0, dp(9), 0, dp(3));
        sectionRow.addView(text("Side-by-side stats", 18, Color.rgb(202, 214, 232), true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView statsChip = filterChip("Stats", () -> showMetricPicker());
        LinearLayout.LayoutParams statsChipLp = new LinearLayout.LayoutParams(dp(82), dp(34));
        statsChipLp.setMargins(0, 0, dp(7), 0);
        sectionRow.addView(statsChip, statsChipLp);
        sectionRow.addView(headToHeadLegend(h, paletteA, paletteB));
        metricBox.addView(sectionRow, matchWrap());

        ArrayList<Metric> selectedRowMetrics = (h.selectedMetricsSnapshot == null || h.selectedMetricsSnapshot.isEmpty()) ? selectedMetricsForScope(h.scope) : h.selectedMetricsSnapshot;
        ArrayList<Metric> rowMetrics = metricsWithHeadToHeadValues(h, selectedRowMetrics);
        if (rowMetrics.isEmpty()) rowMetrics = selectedRowMetrics;
        ArrayList<Metric> availableRowMetrics = metricsWithHeadToHeadValues(h, availableMetricsForScope(h.isTeam ? StatScope.BOTH : h.scope));
        addLensStatsControl(metricBox, "Lens stats", rowMetrics, availableRowMetrics, () -> {
            if (lastHeadToHead != null) renderHeadToHead(lastHeadToHead);
        });

        ArrayList<Metric> visibleMetrics = showAllResultsStats ? availableRowMetrics : rowMetrics;
        if (visibleMetrics.isEmpty() && showAllResultsStats) visibleMetrics = rowMetrics;

        int count = 0;
        String lastSection = "";
        int sepAccent = mixColor(boostNeonColor(paletteA.primary, 1.08f, 1.03f), boostNeonColor(paletteB.primary, 1.08f, 1.03f), 0.50f);
        for (Metric m : visibleMetrics) {
            String section = metricSectionLabel(m);
            if (count > 0 && !section.equals(lastSection)) metricBox.addView(statGroupSeparator(section, sepAccent), matchWrap());
            renderHeadToHeadMetricRow(h, m, paletteA, paletteB);
            lastSection = section;
            count++;
        }
        if (count == 0) {
            TextView empty = text("No stats available for this selection.", 14, MUTED, false);
            empty.setPadding(0, dp(10), 0, dp(10));
            metricBox.addView(empty);
        }
        if (suppressNextAutoScroll) suppressNextAutoScroll = false;
        else scrollToResultsTop();
    }

    private void addHeadToHeadCategoryBrowser(HeadToHeadComparison h, ArrayList<Metric> rowMetrics) {
        if (h == null || rowMetrics == null || rowMetrics.size() < 4) return;
        String[] cats = categoryKeysForRole(h.isTeam ? "both" : roleForScope(h.scope));
        if (!hasKey(cats, activeResultsStatCategory)) activeResultsStatCategory = "all";

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(2), 0, dp(5));
        TextView label = text("Stat tabs", 12, Color.rgb(166, 182, 205), true);
        header.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        int matching = 0;
        for (Metric m : rowMetrics) if (metricMatchesCategory(m, activeResultsStatCategory)) matching++;
        TextView status = text(("all".equals(activeResultsStatCategory) ? "Selected" : categoryLabel(activeResultsStatCategory)) + " · " + matching + " stats", 11, Color.rgb(129, 147, 174), false);
        header.addView(status);
        metricBox.addView(header, matchWrap());

        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String cat : cats) {
            TextView chip = headToHeadCategoryChip(cat);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
            chipLp.setMargins(0, 0, dp(7), 0);
            chipRow.addView(chip, chipLp);
        }
        LinearLayout.LayoutParams tabsLp = matchWrap();
        tabsLp.setMargins(0, 0, 0, dp(8));
        metricBox.addView(horizontalChipScroller(chipRow), tabsLp);
    }

    private TextView headToHeadCategoryChip(String key) {
        boolean active = key.equals(activeResultsStatCategory);
        TextView tv = text(categoryLabel(key), 12, active ? Color.rgb(7, 18, 28) : Color.rgb(220, 229, 242), true);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        if (active) {
            tv.setBackground(roundedGradientStroke(new int[] { Color.rgb(255, 243, 140), Color.rgb(88, 210, 232) }, 999, Color.argb(180, 255, 255, 255), 1));
        } else {
            tv.setBackground(roundedStroke(Color.argb(28, 255, 255, 255), Color.argb(62, 255, 255, 255), 999, 1));
        }
        tv.setOnClickListener(v -> {
            int keepY = mainScroll == null ? 0 : mainScroll.getScrollY();
            activeResultsStatCategory = key;
            suppressNextAutoScroll = true;
            if (lastHeadToHead != null) renderHeadToHead(lastHeadToHead);
            if (mainScroll != null) mainScroll.post(() -> mainScroll.scrollTo(0, keepY));
        });
        return tv;
    }

    private View headToHeadSideTile(HeadToHeadComparison h, boolean leftSide, TeamPalette palette) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(9), dp(9), dp(9), dp(9));
        tile.setBackground(roundedStroke(Color.WHITE, softColor(palette.primary, 0.55f), 18, 1));
        if (h.isTeam) {
            View logo = teamLogoView(leftSide ? h.teamA : h.teamB, 44);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            lp.setMargins(0, 0, dp(8), 0);
            tile.addView(logo, lp);
        } else {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setAdjustViewBounds(true);
            img.setBackground(roundedStroke(Color.rgb(235, 241, 248), LINE, 18, 1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            lp.setMargins(0, 0, dp(8), 0);
            tile.addView(img, lp);
            loadPlayerImage(leftSide ? h.idA : h.idB, img);
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(leftSide ? h.nameA : h.nameB, 14, INK, true));
        col.addView(text(leftSide ? h.metaA : h.metaB, 10, MUTED, false));
        tile.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        return tile;
    }

    private View headToHeadLegend(HeadToHeadComparison h, TeamPalette paletteA, TeamPalette paletteB) {
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.VERTICAL);
        legend.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        legend.addView(legendMini(shortName(h.nameA), paletteA.primary));
        legend.addView(legendMini("MLB", Color.rgb(70, 88, 125)));
        legend.addView(legendMini(shortName(h.nameB), paletteB.primary));
        return legend;
    }

    // v43: electric-energy polish - VS arcs and winner-side stat rail sparks.
    private void addBattleCard(HeadToHeadComparison h, TeamPalette paletteA, TeamPalette paletteB) {
        PremiumShareCardView card = new PremiumShareCardView(this, h, paletteA, paletteB);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        headerBox.addView(card, lp);
    }

    class PremiumShareCardView extends View {
        final HeadToHeadComparison h;
        final TeamPalette paletteA, paletteB;
        final ArrayList<Metric> allMetrics;
        final ArrayList<Metric> shareMetrics;
        final int aWins, bWins, ties, decided;
        final int overallAWins, overallBWins, overallTies;
        final StatScoreSummary keyScore, overallScore;
        Bitmap iconA, iconB;
        Bitmap logoA, logoB;
        final RectF keyEdgePickerHotspot = new RectF();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

        PremiumShareCardView(Context context, HeadToHeadComparison h, TeamPalette paletteA, TeamPalette paletteB) {
            super(context);
            this.h = h;
            this.paletteA = paletteA;
            this.paletteB = paletteB;
            this.allMetrics = collectHeadToHeadMetrics(h, 999, false);
            this.shareMetrics = collectHeadToHeadMetrics(h, 8, true);
            this.keyScore = summarizeHeadToHeadEdges(h, shareMetrics);
            this.overallScore = summarizeHeadToHeadEdges(h, allMetrics);
            int[] keyWins = scoreSummaryToInts(keyScore);
            int[] overallWins = scoreSummaryToInts(overallScore);
            this.aWins = keyWins[0];
            this.bWins = keyWins[1];
            this.ties = keyWins.length > 2 ? keyWins[2] : 0;
            this.overallAWins = overallWins[0];
            this.overallBWins = overallWins[1];
            this.overallTies = overallWins.length > 2 ? overallWins[2] : 0;
            this.decided = Math.max(0, aWins + bWins + ties);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setClickable(true);
            Team logoTeamA = h.isTeam ? h.teamA : findTeamByName(h.playerA == null ? "" : h.playerA.teamAbbr);
            Team logoTeamB = h.isTeam ? h.teamB : findTeamByName(h.playerB == null ? "" : h.playerB.teamAbbr);
            if (logoTeamA != null) loadTeamLogoBitmap(logoTeamA, bitmap -> { logoA = bitmap; invalidate(); });
            if (logoTeamB != null) loadTeamLogoBitmap(logoTeamB, bitmap -> { logoB = bitmap; invalidate(); });
            if (h.isTeam) {
                loadTeamLogoBitmap(h.teamA, bitmap -> { iconA = bitmap; invalidate(); });
                loadTeamLogoBitmap(h.teamB, bitmap -> { iconB = bitmap; invalidate(); });
            } else {
                loadPlayerImageBitmap(h.idA, bitmap -> { iconA = bitmap; invalidate(); });
                loadPlayerImageBitmap(h.idB, bitmap -> { iconB = bitmap; invalidate(); });
            }
        }

        @Override public boolean performClick() {
            super.performClick();
            showMetricPicker();
            return true;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event == null) return false;
            boolean inPicker = keyEdgePickerHotspot.contains(event.getX(), event.getY());
            if (event.getAction() == MotionEvent.ACTION_DOWN && inPicker) return true;
            if (event.getAction() == MotionEvent.ACTION_UP && inPicker) {
                performClick();
                return true;
            }
            return false;
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            if (w <= 0) w = dp(360);
            int hPx = Math.max(dp(720), Math.round(w * 1.94f));
            setMeasuredDimension(w, hPx);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float hgt = getHeight();
            float pad = dp(4);
            RectF card = new RectF(pad, pad, w - pad, hgt - pad);
            float radius = dp(28);

            int accentA = battleTeamColor(paletteA, paletteB, true);
            int accentB = battleTeamColor(paletteB, paletteA, false);
            int midBlend = Color.rgb((Color.red(accentA) + Color.red(accentB)) / 2, (Color.green(accentA) + Color.green(accentB)) / 2, (Color.blue(accentA) + Color.blue(accentB)) / 2);

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(card.left, card.top, card.right, card.bottom,
                    new int[] {
                            mixColor(boostNeonColor(accentA, 1.14f, 1.06f), Color.rgb(2, 4, 9), 0.74f),
                            mixColor(boostNeonColor(paletteA.secondary, 1.08f, 1.03f), Color.rgb(4, 7, 13), 0.78f),
                            mixColor(boostNeonColor(midBlend, 1.03f, 1.01f), Color.rgb(5, 8, 15), 0.84f),
                            mixColor(boostNeonColor(paletteB.secondary, 1.08f, 1.03f), Color.rgb(4, 7, 13), 0.78f),
                            mixColor(boostNeonColor(accentB, 1.14f, 1.06f), Color.rgb(2, 4, 9), 0.74f)
                    },
                    new float[] {0f, 0.24f, 0.50f, 0.76f, 1f}, Shader.TileMode.CLAMP));
            paint.setShadowLayer(dp(10), 0, dp(5), Color.argb(38, 0, 0, 0));
            canvas.drawRoundRect(card, radius, radius, paint);
            paint.clearShadowLayer();
            paint.setShader(null);

            int save = canvas.save();
            Path clip = new Path();
            clip.addRoundRect(card, radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);
            drawAtmosphere(canvas, card, true, paletteA, sideAbbr(true));
            drawAtmosphere(canvas, card, false, paletteB, sideAbbr(false));
            drawVignette(canvas, card);

            float titleY = card.top + dp(28);
            drawText(canvas, h.season + " STATCAST MATCHUP", card.centerX(), titleY, dp(10), Color.rgb(204, 215, 230), true, Paint.Align.CENTER, 0.13f);
            String statPill = keyScore.scoredRows == allMetrics.size()
                    ? keyScore.scoredRows + " SCORING STATS"
                    : allMetrics.size() + " SELECTED · " + keyScore.scoredRows + " SCORED";
            drawPill(canvas, statPill, card.centerX(), titleY + dp(24), dp(keyScore.scoredRows == allMetrics.size() ? 104 : 138), dp(24), Color.argb(36, 255, 255, 255), Color.argb(72, 255, 255, 255), Color.rgb(218, 228, 241), dp(8));
            if (keyScore.sampleAdjustedRows > 0) {
                drawPill(canvas, "SAMPLE WEIGHTED", card.centerX(), titleY + dp(47), dp(110), dp(17), Color.argb(24, 255, 255, 255), Color.argb(52, 255, 255, 255), Color.rgb(174, 190, 212), dp(6));
            }

            drawCornerTeamLogo(canvas, logoA, sideAbbr(true), card.left + dp(34), card.top + dp(52), dp(70), accentA, true);
            drawCornerTeamLogo(canvas, logoB, sideAbbr(false), card.right - dp(34), card.top + dp(52), dp(70), Color.WHITE, false);

            float portraitR = Math.min(dp(72), w * 0.19f);
            float leftCx = card.left + w * 0.25f;
            float rightCx = card.right - w * 0.25f;
            float portraitCy = card.top + dp(134);
            float vsCx = card.centerX();
            float vsCy = portraitCy + dp(2);

            drawBattleBeam(canvas, leftCx + portraitR, vsCy, vsCx - dp(24), vsCy, accentA, true);
            drawBattleBeam(canvas, rightCx - portraitR, vsCy, vsCx + dp(24), vsCy, accentB, false);
            drawPortrait(canvas, iconA, leftCx, portraitCy, portraitR, paletteA, initials(h.nameA), true);
            drawPortrait(canvas, iconB, rightCx, portraitCy, portraitR, paletteB, initials(h.nameB), false);
            drawVsBadge(canvas, vsCx, vsCy, dp(25));

            drawPlayerNameBlock(canvas, h.nameA, h.metaA, leftCx, portraitCy + portraitR + dp(31), paletteA, true);
            drawPlayerNameBlock(canvas, h.nameB, h.metaB, rightCx, portraitCy + portraitR + dp(31), paletteB, false);

            float scoreTop = card.top + dp(324);
            RectF score = new RectF(card.left + dp(30), scoreTop, card.right - dp(30), scoreTop + dp(106));
            drawScoreBlock(canvas, score);

            float keyY = score.bottom + dp(27);
            drawKeyStatBody(canvas, card, keyY);
            canvas.restoreToCount(save);

            strokePaint.setShader(null);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1));
            strokePaint.setColor(Color.argb(70, 255, 255, 255));
            canvas.drawRoundRect(card, radius, radius, strokePaint);
        }

        private void drawAtmosphere(Canvas canvas, RectF card, boolean left, TeamPalette palette, String teamAbbr) {
            float sideLeft = left ? card.left : card.centerX();
            float sideRight = left ? card.centerX() : card.right;
            RectF zone = new RectF(sideLeft, card.top, sideRight, card.bottom);
            drawCityBackdrop(canvas, zone, left, palette, teamAbbr);

            paint.setStyle(Paint.Style.FILL);
            int neonPrimary = boostNeonColor(palette.primary, 1.30f, 1.14f);
            int neonSecondary = boostNeonColor(palette.secondary, 1.18f, 1.08f);
            int stripColor = boostNeonColor(readableTeamColor(palette.primary, palette.secondary, left), 1.18f, 1.06f);

            // Keep the base dark: a restrained tint supports the card without lifting the whole background.
            paint.setShader(new LinearGradient(sideLeft, card.top, sideRight, card.bottom,
                    new int[] {
                            Color.argb(left ? 16 : 18, Color.red(neonPrimary), Color.green(neonPrimary), Color.blue(neonPrimary)),
                            Color.argb(8, Color.red(neonSecondary), Color.green(neonSecondary), Color.blue(neonSecondary)),
                            Color.argb(4, Color.red(neonPrimary), Color.green(neonPrimary), Color.blue(neonPrimary)),
                            Color.TRANSPARENT
                    }, new float[] {0f, 0.22f, 0.56f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(sideLeft, card.top, sideRight, card.bottom, paint);

            // Thinner premium neon strip near the top of each side: compact, bright, and restrained.
            float stripTop = card.top + dp(43);
            RectF strip = new RectF(sideLeft + dp(22), stripTop, sideRight - dp(22), stripTop + dp(6));
            int warmEdge = mixColor(stripColor, Color.WHITE, 0.08f);
            paint.setShader(new LinearGradient(strip.left, strip.centerY(), strip.right, strip.centerY(),
                    left
                            ? new int[] { Color.TRANSPARENT, Color.argb(104, Color.red(stripColor), Color.green(stripColor), Color.blue(stripColor)), Color.argb(216, Color.red(warmEdge), Color.green(warmEdge), Color.blue(warmEdge)), Color.argb(48, Color.red(stripColor), Color.green(stripColor), Color.blue(stripColor)), Color.TRANSPARENT }
                            : new int[] { Color.TRANSPARENT, Color.argb(48, Color.red(stripColor), Color.green(stripColor), Color.blue(stripColor)), Color.argb(216, Color.red(warmEdge), Color.green(warmEdge), Color.blue(warmEdge)), Color.argb(104, Color.red(stripColor), Color.green(stripColor), Color.blue(stripColor)), Color.TRANSPARENT },
                    new float[] {0f, 0.20f, 0.50f, 0.80f, 1f}, Shader.TileMode.CLAMP));
            paint.setShadowLayer(dp(6), 0, 0, Color.argb(112, Color.red(stripColor), Color.green(stripColor), Color.blue(stripColor)));
            canvas.drawRoundRect(strip, dp(5), dp(5), paint);
            paint.clearShadowLayer();

            // Localized portrait-area glow: tighter radius and smaller halo so the effect pops without washing out.
            float glowCx = left ? sideLeft + zone.width() * 0.42f : sideRight - zone.width() * 0.42f;
            float glowCy = card.top + dp(136);
            float outerR = zone.width() * 0.19f;
            paint.setShader(new RadialGradient(glowCx, glowCy, outerR,
                    new int[] {
                            Color.argb(118, Color.red(neonPrimary), Color.green(neonPrimary), Color.blue(neonPrimary)),
                            Color.argb(38, Color.red(neonSecondary), Color.green(neonSecondary), Color.blue(neonSecondary)),
                            Color.argb(8, Color.red(neonPrimary), Color.green(neonPrimary), Color.blue(neonPrimary)),
                            Color.TRANSPARENT
                    }, new float[] {0f, 0.26f, 0.62f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(glowCx, glowCy, outerR, paint);

            float coreR = zone.width() * 0.09f;
            int hotCore = mixColor(Color.WHITE, neonPrimary, 0.08f);
            paint.setShader(new RadialGradient(glowCx, glowCy, coreR,
                    new int[] {
                            Color.argb(82, Color.red(hotCore), Color.green(hotCore), Color.blue(hotCore)),
                            Color.argb(44, Color.red(neonPrimary), Color.green(neonPrimary), Color.blue(neonPrimary)),
                            Color.argb(10, Color.red(neonSecondary), Color.green(neonSecondary), Color.blue(neonSecondary)),
                            Color.TRANSPARENT
                    }, new float[] {0f, 0.18f, 0.52f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(glowCx, glowCy, coreR, paint);
            paint.setShader(null);
        }

        private void drawCityBackdrop(Canvas canvas, RectF zone, boolean left, TeamPalette palette, String teamAbbr) {
            String key = safe(teamAbbr).toUpperCase(Locale.US);
            int accent = readableTeamColor(palette.primary, palette.secondary, left);
            int lineColor = Color.argb(42, Color.red(accent), Color.green(accent), Color.blue(accent));
            int fillColor = Color.argb(20, Color.red(accent), Color.green(accent), Color.blue(accent));
            int softLine = Color.argb(26, 255, 255, 255);
            float base = zone.top + dp(276);

            drawWatermarkSkyline(canvas, zone, base, fillColor, Color.argb(30, 0, 0, 0));

            switch (key) {
                case "SD":
                    drawCoronadoBridge(canvas, zone, base - dp(6), lineColor, softLine, left);
                    drawPalm(canvas, left ? zone.left + dp(34) : zone.right - dp(44), base + dp(5), left ? 0.70f : 0.74f, lineColor);
                    drawPalm(canvas, left ? zone.left + dp(62) : zone.right - dp(76), base + dp(10), left ? 0.46f : 0.50f, Color.argb(30, Color.red(accent), Color.green(accent), Color.blue(accent)));
                    break;
                case "LA":
                case "LAA":
                    drawLaSkyline(canvas, zone, base - dp(2), fillColor, lineColor, left);
                    drawPalm(canvas, left ? zone.left + dp(34) : zone.right - dp(44), base + dp(6), left ? 0.74f : 0.78f, lineColor);
                    drawPalm(canvas, left ? zone.left + dp(70) : zone.right - dp(78), base + dp(10), left ? 0.50f : 0.54f, Color.argb(28, Color.red(accent), Color.green(accent), Color.blue(accent)));
                    break;
                case "SF":
                    drawGoldenGateBackdrop(canvas, zone, base - dp(2), fillColor, lineColor, left);
                    break;
                case "SEA":
                    drawSeattleBackdrop(canvas, zone, base - dp(2), fillColor, lineColor, left);
                    break;
                case "STL":
                    drawStLouisBackdrop(canvas, zone, base - dp(4), fillColor, lineColor, left);
                    break;
                case "NYY":
                case "NYM":
                    drawNewYorkBackdrop(canvas, zone, base - dp(2), fillColor, lineColor, left);
                    break;
                case "CHC":
                case "CWS":
                    drawChicagoBackdrop(canvas, zone, base - dp(2), fillColor, lineColor, left);
                    break;
                default:
                    drawGenericLandmarkBackdrop(canvas, zone, base - dp(2), fillColor, lineColor, left);
                    break;
            }
        }

        private void drawWatermarkSkyline(Canvas canvas, RectF zone, float base, int fillColor, int shadeColor) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            float[] widths = new float[] {14, 18, 12, 20, 16, 24, 14, 18, 13};
            float[] heights = new float[] {30, 44, 28, 58, 38, 66, 32, 48, 36};
            float gap = (zone.width() - dp(46) - dp(14) * widths.length) / (widths.length - 1);
            float x = zone.left + dp(18);
            for (int i = 0; i < widths.length; i++) {
                float w = dp(widths[i]);
                float h = dp(heights[i]);
                paint.setColor(shadeColor);
                canvas.drawRect(x, base - h, x + w, base, paint);
                paint.setColor(fillColor);
                canvas.drawRect(x, base - h * 0.92f, x + w, base, paint);
                x += w + gap;
            }
        }

        private void drawPalm(Canvas c, float x, float y, float scale, int color) {
            strokePaint.setShader(null);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1.4f));
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setColor(color);
            float h = dp(74) * scale;
            c.drawLine(x, y, x + dp(8) * scale, y - h, strokePaint);
            float cx = x + dp(8) * scale, cy = y - h;
            for (int i = -3; i <= 3; i++) {
                float ang = (float) (-Math.PI / 2 + i * 0.35f);
                float len = dp(28) * scale * (1f - Math.abs(i) * 0.06f);
                c.drawLine(cx, cy, cx + (float)Math.cos(ang) * len, cy + (float)Math.sin(ang) * len, strokePaint);
            }
        }

        private void drawCoronadoBridge(Canvas canvas, RectF zone, float base, int color, int accent, boolean left) {
            strokePaint.setShader(null);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeWidth(dp(1.6f));
            strokePaint.setColor(color);
            float startX = left ? zone.left + dp(12) : zone.left + dp(26);
            float endX = left ? zone.right - dp(18) : zone.right - dp(12);
            Path p = new Path();
            p.moveTo(startX, base);
            p.cubicTo(zone.left + zone.width() * 0.30f, base - dp(34), zone.left + zone.width() * 0.62f, base - dp(34), endX, base - dp(2));
            canvas.drawPath(p, strokePaint);
            float t1 = zone.left + zone.width() * 0.34f;
            float t2 = zone.left + zone.width() * 0.62f;
            canvas.drawLine(t1, base - dp(2), t1, base - dp(28), strokePaint);
            canvas.drawLine(t2, base - dp(2), t2, base - dp(30), strokePaint);
            strokePaint.setColor(accent);
            canvas.drawLine(startX, base + dp(3), endX, base + dp(1), strokePaint);
        }

        private void drawLaSkyline(Canvas canvas, RectF zone, float base, int fillColor, int lineColor, boolean left) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fillColor);
            float x = zone.left + dp(34);
            float[] ws = new float[] {18, 12, 20, 14, 22};
            float[] hs = new float[] {38, 52, 68, 42, 56};
            for (int i = 0; i < ws.length; i++) {
                canvas.drawRect(x, base - dp(hs[i]), x + dp(ws[i]), base, paint);
                x += dp(ws[i] + 7);
            }
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1.4f));
            strokePaint.setColor(lineColor);
            canvas.drawLine(zone.left + dp(18), base + dp(2), zone.right - dp(18), base + dp(2), strokePaint);
            Path hill = new Path();
            hill.moveTo(zone.left + dp(10), base + dp(8));
            hill.quadTo(zone.left + zone.width() * 0.35f, base - dp(2), zone.left + zone.width() * 0.70f, base + dp(10));
            hill.quadTo(zone.right - dp(30), base + dp(14), zone.right - dp(10), base + dp(8));
            canvas.drawPath(hill, strokePaint);
        }

        private void drawGoldenGateBackdrop(Canvas canvas, RectF zone, float base, int fillColor, int lineColor, boolean left) {
            strokePaint.setShader(null);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeWidth(dp(1.5f));
            strokePaint.setColor(lineColor);
            float tower1 = zone.left + zone.width() * 0.28f;
            float tower2 = zone.left + zone.width() * 0.56f;
            float top = base - dp(56);
            canvas.drawLine(tower1, base + dp(4), tower1, top, strokePaint);
            canvas.drawLine(tower2, base + dp(4), tower2, top + dp(4), strokePaint);
            Path cable = new Path();
            cable.moveTo(zone.left + dp(8), base + dp(2));
            cable.cubicTo(zone.left + zone.width() * 0.18f, base - dp(34), zone.left + zone.width() * 0.44f, base - dp(34), zone.right - dp(10), base + dp(2));
            canvas.drawPath(cable, strokePaint);
            canvas.drawLine(zone.left + dp(8), base + dp(2), zone.right - dp(10), base + dp(2), strokePaint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fillColor);
            float sx = zone.left + zone.width() * 0.64f;
            canvas.drawRect(sx, base - dp(52), sx + dp(16), base, paint);
            Path pyramid = new Path();
            pyramid.moveTo(sx + dp(28), base);
            pyramid.lineTo(sx + dp(39), base - dp(48));
            pyramid.lineTo(sx + dp(50), base);
            pyramid.close();
            canvas.drawPath(pyramid, paint);
        }

        private void drawSeattleBackdrop(Canvas canvas, RectF zone, float base, int fillColor, int lineColor, boolean left) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fillColor);
            float cx = zone.left + zone.width() * 0.36f;
            canvas.drawRect(cx - dp(5), base - dp(46), cx + dp(5), base, paint);
            canvas.drawOval(new RectF(cx - dp(18), base - dp(58), cx + dp(18), base - dp(48)), paint);
            canvas.drawRect(cx - dp(1.5f), base - dp(68), cx + dp(1.5f), base - dp(58), paint);
            float x = cx + dp(32);
            canvas.drawRect(x, base - dp(56), x + dp(18), base, paint);
            canvas.drawRect(x + dp(24), base - dp(40), x + dp(38), base, paint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1.4f));
            strokePaint.setColor(lineColor);
            canvas.drawLine(zone.left + dp(14), base + dp(2), zone.right - dp(14), base + dp(2), strokePaint);
        }

        private void drawStLouisBackdrop(Canvas canvas, RectF zone, float base, int fillColor, int lineColor, boolean left) {
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1.7f));
            strokePaint.setColor(lineColor);
            float cx = zone.left + zone.width() * 0.44f;
            Path arch = new Path();
            arch.moveTo(cx - dp(34), base + dp(2));
            arch.cubicTo(cx - dp(26), base - dp(48), cx + dp(26), base - dp(48), cx + dp(34), base + dp(2));
            canvas.drawPath(arch, strokePaint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fillColor);
            canvas.drawRect(zone.left + dp(22), base - dp(28), zone.left + dp(38), base, paint);
            canvas.drawRect(zone.right - dp(40), base - dp(38), zone.right - dp(24), base, paint);
        }

        private void drawNewYorkBackdrop(Canvas canvas, RectF zone, float base, int fillColor, int lineColor, boolean left) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fillColor);
            float x = zone.left + dp(18);
            float[] ws = new float[] {14, 18, 24, 16, 22, 18};
            float[] hs = new float[] {42, 56, 78, 48, 64, 52};
            for (int i = 0; i < ws.length; i++) {
                canvas.drawRect(x, base - dp(hs[i]), x + dp(ws[i]), base, paint);
                if (i == 2) {
                    Path spire = new Path();
                    spire.moveTo(x + dp(ws[i] / 2f), base - dp(hs[i]) - dp(16));
                    spire.lineTo(x + dp(ws[i] / 2f) - dp(5), base - dp(hs[i]));
                    spire.lineTo(x + dp(ws[i] / 2f) + dp(5), base - dp(hs[i]));
                    spire.close();
                    canvas.drawPath(spire, paint);
                }
                x += dp(ws[i] + 6);
            }
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1.4f));
            strokePaint.setColor(lineColor);
            canvas.drawLine(zone.left + dp(12), base + dp(2), zone.right - dp(12), base + dp(2), strokePaint);
        }

        private void drawChicagoBackdrop(Canvas canvas, RectF zone, float base, int fillColor, int lineColor, boolean left) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fillColor);
            float x = zone.left + dp(26);
            canvas.drawRect(x, base - dp(56), x + dp(16), base, paint);
            canvas.drawRect(x + dp(24), base - dp(74), x + dp(44), base, paint);
            canvas.drawRect(x + dp(52), base - dp(46), x + dp(68), base, paint);
            canvas.drawRect(x + dp(76), base - dp(62), x + dp(94), base, paint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1.4f));
            strokePaint.setColor(lineColor);
            canvas.drawLine(zone.left + dp(14), base + dp(2), zone.right - dp(14), base + dp(2), strokePaint);
        }

        private void drawGenericLandmarkBackdrop(Canvas canvas, RectF zone, float base, int fillColor, int lineColor, boolean left) {
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1.3f));
            strokePaint.setColor(lineColor);
            Path wave = new Path();
            wave.moveTo(zone.left + dp(10), base + dp(4));
            wave.quadTo(zone.left + zone.width() * 0.28f, base - dp(8), zone.left + zone.width() * 0.55f, base + dp(2));
            wave.quadTo(zone.left + zone.width() * 0.78f, base + dp(10), zone.right - dp(10), base + dp(2));
            canvas.drawPath(wave, strokePaint);
        }

        private void drawVignette(Canvas canvas, RectF card) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(card.left, card.top, card.left, card.bottom,
                    new int[] { Color.argb(18, 255, 255, 255), Color.TRANSPARENT, Color.argb(118, 0, 0, 0) },
                    new float[] {0f, 0.42f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(card, paint);
            paint.setShader(null);
        }

        private void drawCornerTeamLogo(Canvas canvas, Bitmap logo, String fallback, float x, float y, float size, int color, boolean left) {
            // v41: use the transparent logo asset as a true watermark — no circle/badge behind it.
            if (logo != null && logo.getWidth() > 0 && logo.getHeight() > 0) {
                float leftX = left ? x : x - size;
                RectF box = new RectF(leftX, y - size * 0.78f, leftX + size, y + size * 0.22f);
                Paint bp = paintForBitmap();
                bp.setAlpha(left ? 38 : 42);
                bp.setShadowLayer(dp(3), 0, 0, Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)));
                float bw = logo.getWidth(), bh = logo.getHeight();
                float scale = Math.min(box.width() / bw, box.height() / bh);
                float dw = bw * scale, dh = bh * scale;
                Rect src = new Rect(0, 0, logo.getWidth(), logo.getHeight());
                RectF dst = new RectF(box.centerX() - dw / 2f, box.centerY() - dh / 2f, box.centerX() + dw / 2f, box.centerY() + dh / 2f);
                canvas.drawBitmap(logo, src, dst, bp);
                return;
            }
            drawCornerTeamMark(canvas, fallback, x, y, color, left ? Paint.Align.LEFT : Paint.Align.RIGHT);
        }

        private void drawCornerTeamMark(Canvas canvas, String mark, float x, float y, int color, Paint.Align align) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(tfBold);
            paint.setTextAlign(align);
            paint.setTextSize(dp(34));
            paint.setColor(Color.argb(58, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawText(mark, x, y, paint);
        }

        private String sideAbbr(boolean left) {
            if (h.isTeam) {
                Team t = left ? h.teamA : h.teamB;
                return t == null ? "" : prettyAbbr(t.abbr);
            }
            Player p = left ? h.playerA : h.playerB;
            return p == null ? "" : prettyAbbr(p.teamAbbr);
        }

        private String prettyAbbr(String abbr) {
            String s = safe(abbr).toUpperCase(Locale.US);
            if (s.equals("LAD")) return "LA";
            if (s.equals("SDP")) return "SD";
            if (s.equals("SFG")) return "SF";
            return s;
        }

        private void drawBattleBeam(Canvas canvas, float x1, float y1, float x2, float y2, int color, boolean left) {
            color = boostNeonColor(color, 1.36f, 1.20f);
            int hot = mixColor(Color.WHITE, color, 0.08f);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);

            strokePaint.setStrokeWidth(dp(22));
            strokePaint.setShader(new LinearGradient(x1, y1, x2, y2,
                    left ? new int[] { Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)), Color.argb(176, Color.red(color), Color.green(color), Color.blue(color)), Color.argb(246, Color.red(color), Color.green(color), Color.blue(color)), Color.argb(78, Color.red(hot), Color.green(hot), Color.blue(hot)) }
                         : new int[] { Color.argb(78, Color.red(hot), Color.green(hot), Color.blue(hot)), Color.argb(246, Color.red(color), Color.green(color), Color.blue(color)), Color.argb(176, Color.red(color), Color.green(color), Color.blue(color)), Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)) },
                    new float[] {0f, 0.40f, 0.84f, 1f}, Shader.TileMode.CLAMP));
            strokePaint.setShadowLayer(dp(22), 0, 0, Color.argb(255, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawLine(x1, y1, x2, y2, strokePaint);

            strokePaint.setStrokeWidth(dp(5.6f));
            strokePaint.setShader(new LinearGradient(x1, y1, x2, y2,
                    left ? new int[] { Color.argb(22, Color.red(color), Color.green(color), Color.blue(color)), color, hot }
                         : new int[] { hot, color, Color.argb(22, Color.red(color), Color.green(color), Color.blue(color)) },
                    null, Shader.TileMode.CLAMP));
            strokePaint.setShadowLayer(dp(14), 0, 0, Color.argb(248, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawLine(x1, y1, x2, y2, strokePaint);

            strokePaint.clearShadowLayer();
            strokePaint.setShader(null);
            strokePaint.setStrokeWidth(dp(2.2f));
            strokePaint.setColor(Color.argb(164, Color.red(hot), Color.green(hot), Color.blue(hot)));
            strokePaint.setShadowLayer(dp(4.5f), 0, 0, Color.argb(120, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawLine(x1, y1, x2, y2, strokePaint);
            strokePaint.clearShadowLayer();

            drawElectricBolt(canvas, left ? x2 - dp(22) : x2 + dp(22), y2, x2, y2, color, dp(1.18f), 5, dp(1.2f), 228);
            drawElectricStar(canvas, x2, y2, color, dp(10.8f), 0.98f);

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(x2, y2, dp(26),
                    new int[] { Color.argb(148, Color.red(hot), Color.green(hot), Color.blue(hot)), Color.argb(196, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT },
                    new float[] {0f, 0.30f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(x2, y2, dp(26), paint);
            paint.setShader(null);
        }

        private void drawPortrait(Canvas canvas, Bitmap bmp, float cx, float cy, float r, TeamPalette palette, String fallback, boolean leftSide) {
            int accent = boostNeonColor(readableTeamColor(palette.primary, palette.secondary, leftSide), 1.34f, 1.18f);
            int secondary = boostNeonColor(palette.secondary, 1.18f, 1.08f);
            int deep = darkColor(palette.primary, 0.58f);
            int hot = mixColor(Color.WHITE, accent, 0.34f);

            // v107: even tighter portrait halo with a stronger, cleaner neon rim.
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx, cy, r + dp(18),
                    new int[] {
                            Color.TRANSPARENT,
                            Color.argb(24, Color.red(accent), Color.green(accent), Color.blue(accent)),
                            Color.argb(94, Color.red(accent), Color.green(accent), Color.blue(accent)),
                            Color.argb(28, Color.red(accent), Color.green(accent), Color.blue(accent)),
                            Color.TRANSPARENT
                    },
                    new float[] {0.70f, 0.82f, 0.90f, 0.96f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r + dp(18), paint);
            paint.setShader(null);

            drawPortraitCrown(canvas, cx, cy, r, accent);

            paint.setColor(Color.argb(248, 3, 7, 14));
            canvas.drawCircle(cx, cy, r + dp(5), paint);

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeWidth(dp(7.4f));
            strokePaint.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r,
                    new int[] {
                            Color.argb(194, Color.red(accent), Color.green(accent), Color.blue(accent)),
                            Color.argb(250, Color.red(hot), Color.green(hot), Color.blue(hot)),
                            Color.argb(194, Color.red(accent), Color.green(accent), Color.blue(accent))
                    }, null, Shader.TileMode.CLAMP));
            strokePaint.setShadowLayer(dp(10), 0, 0, Color.argb(226, Color.red(accent), Color.green(accent), Color.blue(accent)));
            canvas.drawCircle(cx, cy, r + dp(3), strokePaint);
            strokePaint.clearShadowLayer();
            strokePaint.setShader(null);

            strokePaint.setStrokeWidth(dp(0.46f));
            strokePaint.setColor(Color.argb(48, Color.red(hot), Color.green(hot), Color.blue(hot)));
            strokePaint.setShadowLayer(dp(1.2f), 0, 0, Color.argb(42, Color.red(accent), Color.green(accent), Color.blue(accent)));
            canvas.drawCircle(cx, cy, r + dp(5), strokePaint);
            strokePaint.clearShadowLayer();

            drawConnectorBurst(canvas, cx, cy, r + dp(4), accent, leftSide);

            RectF box = new RectF(cx - r, cy - r, cx + r, cy + r);
            Path clip = new Path();
            clip.addCircle(cx, cy, r, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(clip);

            // Darker, team-colored inner disc for logo visibility without pastel washout.
            paint.setStyle(Paint.Style.FILL);
            if (h.isTeam && isPadresPalette(palette)) {
                paint.setShader(new RadialGradient(cx, cy, r,
                        new int[] {
                                Color.rgb(82, 58, 39),
                                Color.rgb(47, 36, 29),
                                Color.rgb(25, 16, 11),
                                Color.rgb(8, 5, 3)
                        },
                        new float[] {0f, 0.48f, 0.80f, 1f}, Shader.TileMode.CLAMP));
            } else {
                paint.setShader(new RadialGradient(cx, cy, r,
                        new int[] {
                                mixColor(accent, Color.rgb(4, 9, 17), 0.36f),
                                mixColor(secondary, Color.rgb(4, 9, 17), 0.44f),
                                deep,
                                Color.rgb(3, 6, 12)
                        },
                        new float[] {0f, 0.44f, 0.78f, 1f}, Shader.TileMode.CLAMP));
            }
            canvas.drawCircle(cx, cy, r, paint);
            paint.setShader(null);

            if (bmp != null) drawBitmapFitInside(canvas, bmp, box, 1.02f);
            else drawText(canvas, fallback, cx, cy + dp(8), dp(24), accent, true, Paint.Align.CENTER, 0.02f);

            // Keep edge dark and glossy so the neon rim, not the background, carries the energy.
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx, cy, r,
                    new int[] { Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(172, 2, 5, 10) },
                    new float[] {0f, 0.68f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, paint);
            paint.setShader(null);
            canvas.restoreToCount(save);
        }

        private void drawBitmapCenterCrop(Canvas canvas, Bitmap bitmap, RectF box) {
            if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return;
            float bw = bitmap.getWidth(), bh = bitmap.getHeight();
            float scale = Math.max(box.width() / bw, box.height() / bh);
            float dw = bw * scale, dh = bh * scale;
            Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
            RectF dst = new RectF(box.centerX() - dw / 2f, box.centerY() - dh / 2f, box.centerX() + dw / 2f, box.centerY() + dh / 2f);
            canvas.drawBitmap(bitmap, src, dst, paintForBitmap());
        }

        private void drawBitmapFitInside(Canvas canvas, Bitmap bitmap, RectF box, float fill) {
            if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return;
            float bw = bitmap.getWidth(), bh = bitmap.getHeight();
            float scale = Math.min((box.width() * fill) / bw, (box.height() * fill) / bh);
            float dw = bw * scale, dh = bh * scale;
            Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
            // Slight vertical bias keeps hats/faces better framed for MLB headshots/spots.
            RectF dst = new RectF(box.centerX() - dw / 2f, box.centerY() - dh / 2f + dp(2), box.centerX() + dw / 2f, box.centerY() + dh / 2f + dp(2));
            canvas.drawBitmap(bitmap, src, dst, paintForBitmap());
        }

        private void drawPortraitCrown(Canvas canvas, float cx, float cy, float r, int color) {
            // v66: smoother continuous corona with much subtler toothing.
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx, cy - r * 0.84f, r * 0.58f,
                    new int[] {
                            Color.argb(34, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(12, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.TRANSPARENT
                    }, new float[] {0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy - r * 0.58f, r * 0.50f, paint);
            paint.setShader(null);

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            // broad soft under-arc for a continuous glow rim.
            strokePaint.setStrokeWidth(dp(4.8f));
            strokePaint.setShader(new SweepGradient(cx, cy,
                    new int[] {
                            Color.TRANSPARENT,
                            Color.argb(54, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(102, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(170, 255, 248, 228),
                            Color.argb(106, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(54, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.TRANSPARENT
                    },
                    new float[] {0f, 0.17f, 0.31f, 0.50f, 0.69f, 0.83f, 1f}));
            strokePaint.setShadowLayer(dp(6.0f), 0, 0, Color.argb(118, Color.red(color), Color.green(color), Color.blue(color)));
            RectF arc = new RectF(cx - r - dp(4.5f), cy - r - dp(5.5f), cx + r + dp(4.5f), cy + r + dp(2.5f));
            canvas.drawArc(arc, 205f, 130f, false, strokePaint);
            strokePaint.clearShadowLayer();
            strokePaint.setShader(null);

            // crisp highlight arc on top of the soft glow.
            strokePaint.setStrokeWidth(dp(2.6f));
            strokePaint.setShader(new SweepGradient(cx, cy,
                    new int[] {
                            Color.TRANSPARENT,
                            Color.argb(84, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(188, 255, 248, 228),
                            Color.argb(90, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.TRANSPARENT
                    },
                    new float[] {0f, 0.26f, 0.50f, 0.74f, 1f}));
            strokePaint.setShadowLayer(dp(4.0f), 0, 0, Color.argb(96, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawArc(arc, 212f, 116f, false, strokePaint);
            strokePaint.clearShadowLayer();
            strokePaint.setShader(null);

            // keep only tiny micro-flickers so it reads as energy, not teeth.
            for (int i = -4; i <= 4; i++) {
                float ang = (float) (-Math.PI / 2 + i * 0.105f);
                float lift = 4 - Math.abs(i);
                float baseX = cx + (float)Math.cos(ang) * (r + dp(0.8f));
                float baseY = cy + (float)Math.sin(ang) * (r + dp(0.8f));
                float tipX = cx + (float)Math.cos(ang) * (r + dp(2.8f + lift * 0.75f));
                float tipY = cy + (float)Math.sin(ang) * (r + dp(2.8f + lift * 0.75f)) - dp(0.18f + lift * 0.20f);
                float scale = 0.20f + lift * 0.035f;
                drawFlameFlicker(canvas, baseX, baseY, tipX, tipY, color, scale);
            }
        }

        private void drawFlameFlicker(Canvas canvas, float baseX, float baseY, float tipX, float tipY, int color, float scale) {
            float dx = tipX - baseX;
            float dy = tipY - baseY;
            float len = (float)Math.sqrt(dx * dx + dy * dy);
            if (len < 1f) return;
            float uy = dy / len;
            float nx = -uy;
            float ny = dx / len;
            float baseW = dp(1.7f) * scale;
            float midW = dp(3.3f) * scale;
            float mx = baseX + dx * 0.50f;
            float my = baseY + dy * 0.50f;

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(mx, my, dp(5.6f) * scale,
                    new int[] {
                            Color.argb(62, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(22, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.TRANSPARENT
                    }, new float[] {0f, 0.58f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(mx, my, dp(5.6f) * scale, paint);

            Path flame = new Path();
            flame.moveTo(baseX - nx * baseW, baseY - ny * baseW);
            flame.cubicTo(baseX - nx * midW, my - ny * midW * 0.66f, mx - nx * midW, my - ny * midW * 0.20f, tipX, tipY);
            flame.cubicTo(mx + nx * midW, my + ny * midW * 0.20f, baseX + nx * midW, my + ny * midW * 0.66f, baseX + nx * baseW, baseY + ny * baseW);
            flame.close();
            paint.setShader(new LinearGradient(baseX, baseY, tipX, tipY,
                    new int[] {
                            Color.argb(8, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(62, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(156, 255, 248, 228),
                            Color.argb(0, 255, 255, 255)
                    }, new float[] {0f, 0.34f, 0.76f, 1f}, Shader.TileMode.CLAMP));
            paint.setShadowLayer(dp(2.2f) * scale, 0, 0, Color.argb(44, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawPath(flame, paint);
            paint.clearShadowLayer();
            paint.setShader(null);
        }

        private void drawConnectorBurst(Canvas canvas, float cx, float cy, float ringR, int color, boolean leftSide) {
            float dir = leftSide ? 1f : -1f;
            float anchorX = cx + dir * ringR;
            float anchorY = cy;

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(anchorX, anchorY, dp(32),
                    new int[] {
                            Color.argb(176, 255, 255, 255),
                            Color.argb(138, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(54, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.TRANSPARENT
                    }, new float[] {0f, 0.18f, 0.58f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(anchorX, anchorY, dp(32), paint);
            paint.setShader(null);

            float[] offs = new float[] {-0.96f, -0.72f, -0.48f, -0.20f, 0f, 0.20f, 0.48f, 0.72f, 0.96f};
            for (int i = 0; i < offs.length; i++) {
                float ang = offs[i];
                float ex = anchorX + dir * dp(11 + (i % 4) * 2.2f);
                float ey = anchorY + dp(ang * 17.5f);
                drawElectricBolt(canvas,
                        anchorX - dir * dp(2.8f), anchorY + dp(ang * 1.3f),
                        ex, ey,
                        color, dp(0.96f), 5, dp(1.02f), 194);
            }
            // Extra local branching to create a clearer impact point at the beam/ring contact.
            float[][] micro = new float[][] {
                    { -4.6f, -2.4f,  7.5f, -13.2f },
                    { -4.3f,  0.0f,  9.8f,   0.0f },
                    { -4.6f,  2.4f,  7.5f,  13.2f },
                    { -2.0f, -5.4f,  4.8f, -15.6f },
                    { -2.0f,  5.4f,  4.8f,  15.6f }
            };
            for (float[] m : micro) {
                drawElectricBolt(canvas,
                        anchorX + dir * dp(m[0]), anchorY + dp(m[1]),
                        anchorX + dir * dp(m[2]), anchorY + dp(m[3]),
                        color, dp(0.88f), 4, dp(0.96f), 184);
            }
            drawElectricStar(canvas, anchorX + dir * dp(1.6f), anchorY, color, dp(10.2f), 0.96f);
            drawElectricStar(canvas, anchorX + dir * dp(5.2f), anchorY, Color.WHITE, dp(5.0f), 0.40f);
        }

        private void drawVsEnergyHalo(Canvas canvas, float cx, float cy, float r, int leftColor, int rightColor) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx, cy, r * 3.08f,
                    new int[] {
                            Color.argb(62, 255, 255, 255),
                            Color.argb(44, 255, 255, 255),
                            Color.TRANSPARENT
                    }, new float[] {0f, 0.34f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r * 2.86f, paint);
            paint.setShader(null);

            paint.setShader(new RadialGradient(cx, cy, r * 1.92f,
                    new int[] {
                            Color.argb(24, Color.red(leftColor), Color.green(leftColor), Color.blue(leftColor)),
                            Color.argb(18, 255, 255, 255),
                            Color.TRANSPARENT
                    }, new float[] {0f, 0.56f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r * 1.82f, paint);
            paint.setShader(null);

            // Denser, more asymmetrical crackling halo around the VS orb.
            float[][] bursts = new float[][] {
                    { -1.02f, -0.92f, -1.56f, -1.22f, 0f },
                    { -1.34f, -0.36f, -1.88f, -0.52f, 0f },
                    { -1.36f,  0.18f, -1.90f,  0.06f, 0f },
                    { -1.06f,  0.74f, -1.56f,  1.06f, 0f },
                    { -0.42f,  1.06f, -0.76f,  1.52f, 0f },
                    {  1.00f, -0.92f,  1.54f, -1.22f, 1f },
                    {  1.34f, -0.34f,  1.90f, -0.48f, 1f },
                    {  1.36f,  0.20f,  1.92f,  0.08f, 1f },
                    {  1.08f,  0.74f,  1.58f,  1.08f, 1f },
                    {  0.42f,  1.06f,  0.78f,  1.52f, 1f },
                    { -0.20f, -1.08f, -0.46f, -1.54f, 0f },
                    {  0.20f, -1.10f,  0.48f, -1.56f, 1f }
            };
            for (float[] b : bursts) {
                boolean right = b[4] > 0.5f;
                int c = right ? rightColor : leftColor;
                float x1 = cx + b[0] * r;
                float y1 = cy + b[1] * r;
                float x2 = cx + b[2] * r;
                float y2 = cy + b[3] * r;
                drawElectricBolt(canvas, x1, y1, x2, y2, c, dp(1.02f), 5, dp(1.02f), 186);
                drawElectricStar(canvas, x2, y2, c, dp(4.4f), 0.46f);
            }

            // Local crackle ring hugging the orb itself.
            float[][] inner = new float[][] {
                    { -1.02f, -0.42f, -0.68f, -0.78f, 0f },
                    { -1.08f,  0.08f, -0.70f,  0.42f, 0f },
                    { -0.54f,  1.00f, -0.20f,  1.26f, 0f },
                    {  1.02f, -0.40f,  0.68f, -0.76f, 1f },
                    {  1.08f,  0.08f,  0.70f,  0.42f, 1f },
                    {  0.54f,  1.00f,  0.20f,  1.26f, 1f }
            };
            for (float[] b : inner) {
                boolean right = b[4] > 0.5f;
                int c = right ? rightColor : leftColor;
                drawElectricBolt(canvas, cx + b[0] * r, cy + b[1] * r, cx + b[2] * r, cy + b[3] * r, c, dp(0.90f), 4, dp(0.92f), 162);
            }

            drawElectricBolt(canvas, cx - r * 0.46f, cy - r * 1.00f, cx, cy - r * 1.44f, leftColor, dp(0.96f), 4, dp(0.96f), 150);
            drawElectricBolt(canvas, cx + r * 0.46f, cy - r * 1.00f, cx, cy - r * 1.44f, rightColor, dp(0.96f), 4, dp(0.96f), 150);
            drawElectricStar(canvas, cx, cy - r * 1.44f, Color.WHITE, dp(5.8f), 0.62f);
        }

        private void drawVsBadge(Canvas canvas, float cx, float cy, float r) {
            int leftColor = boostNeonColor(readableTeamColor(paletteA.primary, paletteA.secondary, true), 1.30f, 1.16f);
            int rightColor = boostNeonColor(readableTeamColor(paletteB.primary, paletteB.secondary, false), 1.30f, 1.16f);
            int blendColor = Color.rgb((Color.red(leftColor) + Color.red(rightColor)) / 2,
                    (Color.green(leftColor) + Color.green(rightColor)) / 2,
                    (Color.blue(leftColor) + Color.blue(rightColor)) / 2);

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx - r * 0.44f, cy, r * 1.72f,
                    new int[] { Color.argb(168, Color.red(leftColor), Color.green(leftColor), Color.blue(leftColor)), Color.argb(68, Color.red(leftColor), Color.green(leftColor), Color.blue(leftColor)), Color.TRANSPARENT },
                    new float[] {0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx - r * 0.18f, cy, r * 1.56f, paint);
            paint.setShader(new RadialGradient(cx + r * 0.44f, cy, r * 1.72f,
                    new int[] { Color.argb(168, Color.red(rightColor), Color.green(rightColor), Color.blue(rightColor)), Color.argb(68, Color.red(rightColor), Color.green(rightColor), Color.blue(rightColor)), Color.TRANSPARENT },
                    new float[] {0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx + r * 0.18f, cy, r * 1.56f, paint);
            paint.setShader(new RadialGradient(cx, cy, r * 1.62f,
                    new int[] { Color.argb(72, Color.red(blendColor), Color.green(blendColor), Color.blue(blendColor)), Color.argb(20, 255, 255, 255), Color.TRANSPARENT },
                    new float[] {0f, 0.44f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r * 1.56f, paint);
            paint.setShader(null);

            drawElectricBolt(canvas, cx - r * 2.64f, cy - dp(1), cx - r * 0.46f, cy - dp(1), leftColor, dp(2.08f), 9, dp(1.90f), 246);
            drawElectricBolt(canvas, cx + r * 2.64f, cy + dp(1), cx + r * 0.46f, cy + dp(1), rightColor, dp(2.08f), 9, dp(1.90f), 246);
            drawElectricBolt(canvas, cx - r * 1.18f, cy - dp(8), cx - r * 0.36f, cy - dp(2), leftColor, dp(1.10f), 5, dp(1.04f), 184);
            drawElectricBolt(canvas, cx + r * 1.18f, cy + dp(8), cx + r * 0.36f, cy + dp(2), rightColor, dp(1.10f), 5, dp(1.04f), 184);
            drawElectricBolt(canvas, cx - r * 0.88f, cy - r * 1.08f, cx - r * 0.10f, cy - r * 0.64f, leftColor, dp(1.04f), 5, dp(1.04f), 170);
            drawElectricBolt(canvas, cx + r * 0.88f, cy - r * 1.08f, cx + r * 0.10f, cy - r * 0.64f, rightColor, dp(1.04f), 5, dp(1.04f), 170);
            drawElectricBolt(canvas, cx - r * 0.88f, cy + r * 1.08f, cx - r * 0.10f, cy + r * 0.64f, leftColor, dp(1.04f), 5, dp(1.04f), 170);
            drawElectricBolt(canvas, cx + r * 0.88f, cy + r * 1.08f, cx + r * 0.10f, cy + r * 0.64f, rightColor, dp(1.04f), 5, dp(1.04f), 170);
            drawElectricStar(canvas, cx - r * 0.32f, cy, leftColor, dp(9.6f), 0.82f);
            drawElectricStar(canvas, cx + r * 0.32f, cy, rightColor, dp(9.6f), 0.82f);
            drawElectricStar(canvas, cx, cy, Color.WHITE, dp(6.0f), 0.42f);

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(4.2f));
            strokePaint.setShader(new LinearGradient(cx - r, cy, cx + r, cy,
                    new int[] { leftColor, blendColor, rightColor }, new float[] {0f, 0.50f, 1f}, Shader.TileMode.CLAMP));
            strokePaint.setShadowLayer(dp(16), 0, 0, Color.argb(210, Color.red(blendColor), Color.green(blendColor), Color.blue(blendColor)));
            canvas.drawCircle(cx, cy, r + dp(5), strokePaint);
            strokePaint.setStrokeWidth(dp(1.8f));
            strokePaint.setColor(Color.argb(148, 255, 255, 255));
            strokePaint.setShader(null);
            strokePaint.setShadowLayer(dp(4), 0, 0, Color.argb(110, 255, 255, 255));
            canvas.drawCircle(cx, cy, r + dp(10), strokePaint);
            strokePaint.clearShadowLayer();

            paint.setShader(new LinearGradient(cx - r, cy, cx + r, cy,
                    new int[] {
                            mixColor(leftColor, Color.rgb(3, 7, 14), 0.48f),
                            mixColor(leftColor, Color.rgb(8, 12, 20), 0.22f),
                            mixColor(blendColor, Color.rgb(7, 10, 18), 0.46f),
                            mixColor(rightColor, Color.rgb(8, 12, 20), 0.22f),
                            mixColor(rightColor, Color.rgb(3, 7, 14), 0.48f)
                    }, new float[] {0f, 0.22f, 0.50f, 0.78f, 1f}, Shader.TileMode.CLAMP));
            paint.setShadowLayer(dp(12), 0, 0, Color.argb(152, Color.red(blendColor), Color.green(blendColor), Color.blue(blendColor)));
            canvas.drawCircle(cx, cy, r, paint);
            paint.clearShadowLayer();
            paint.setShader(new RadialGradient(cx, cy, r,
                    new int[] { Color.argb(110, 255, 255, 255), Color.argb(26, 255, 255, 255), Color.TRANSPARENT },
                    new float[] {0f, 0.50f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, paint);
            paint.setShader(null);

            drawText(canvas, "VS", cx, cy + dp(5), dp(13), Color.WHITE, true, Paint.Align.CENTER, 0.10f);
        }

        private void drawPlayerNameBlock(Canvas canvas, String name, String meta, float cx, float y, TeamPalette palette, boolean left) {
            String first;
            String last;
            if (h.isTeam) {
                Team sideTeam = left ? h.teamA : h.teamB;
                String[] teamParts = teamDisplayNameParts(sideTeam == null ? name : sideTeam.name);
                first = teamParts[0];
                last = teamParts[1];
            } else {
                String[] parts = safe(name).trim().split("\\s+");
                first = parts.length > 1 ? parts[0] : safe(name);
                last = parts.length > 1 ? parts[parts.length - 1] : "";
                if (last.equalsIgnoreCase("Jr.") && parts.length > 2) last = parts[parts.length - 2] + " JR.";
            }
            String firstUpper = first.toUpperCase(Locale.US);
            String lastUpper = last.toUpperCase(Locale.US);
            float firstSpacing = h.isTeam ? 0.14f : 0.18f;
            float lastSpacing = h.isTeam && lastUpper.length() > 11 ? 0.04f : 0.07f;
            float firstSize = fitTextSize(paint, firstUpper, dp(12), dp(10), h.isTeam ? dp(170) : dp(128), firstSpacing, false);
            float lastSize = fitTextSize(paint, lastUpper, h.isTeam ? dp(21) : dp(23), h.isTeam ? dp(15) : dp(18), h.isTeam ? dp(184) : dp(140), lastSpacing, true);
            drawText(canvas, firstUpper, cx, y, firstSize, Color.rgb(207, 218, 232), false, Paint.Align.CENTER, firstSpacing);
            drawText(canvas, lastUpper, cx, y + dp(30), lastSize, Color.rgb(239, 243, 249), true, Paint.Align.CENTER, lastSpacing);
            int metaColor = mixColor(ensureReadableColor(readableTeamColor(palette.primary, palette.secondary, left), 178), Color.rgb(218, 226, 238), 0.08f);
            paint.setShadowLayer(dp(6), 0, 0, Color.argb(120, 0, 0, 0));
            drawText(canvas, safe(meta).toUpperCase(Locale.US), cx, y + dp(58), dp(11), metaColor, true, Paint.Align.CENTER, 0.11f);
            paint.clearShadowLayer();
        }

        private void drawScoreBlock(Canvas canvas, RectF score) {
            int leftEdge = boostNeonColor(readableTeamColor(paletteA.primary, paletteA.secondary, true), 1.18f, 1.08f);
            int rightEdge = boostNeonColor(readableTeamColor(paletteB.primary, paletteB.secondary, false), 1.18f, 1.08f);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(score.left, score.top, score.right, score.bottom,
                    new int[] { Color.argb(174, 7, 12, 21), Color.argb(136, 12, 18, 30), Color.argb(168, 5, 10, 18) },
                    new float[] {0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
            paint.setShadowLayer(dp(14), 0, dp(4), Color.argb(108, 0, 0, 0));
            canvas.drawRoundRect(score, dp(20), dp(20), paint);
            paint.clearShadowLayer();
            paint.setShader(null);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1));
            strokePaint.setColor(Color.argb(74, 255, 255, 255));
            canvas.drawRoundRect(score, dp(20), dp(20), strokePaint);
            strokePaint.setShader(new LinearGradient(score.left, score.centerY(), score.right, score.centerY(),
                    new int[] { Color.argb(168, Color.red(leftEdge), Color.green(leftEdge), Color.blue(leftEdge)), Color.argb(28, 255, 255, 255), Color.argb(176, Color.red(rightEdge), Color.green(rightEdge), Color.blue(rightEdge)) },
                    new float[] {0f, 0.50f, 1f}, Shader.TileMode.CLAMP));
            strokePaint.setShadowLayer(dp(8), 0, 0, Color.argb(84, Color.red(rightEdge), Color.green(rightEdge), Color.blue(rightEdge)));
            canvas.drawRoundRect(score, dp(20), dp(20), strokePaint);
            strokePaint.clearShadowLayer();
            strokePaint.setShader(null);

            int leftBase = battleTeamColor(paletteA, paletteB, true);
            int rightBase = battleTeamColor(paletteB, paletteA, false);
            int leftPct = keyScore.edgePctA();
            int rightPct = keyScore.edgePctB();
            boolean aAhead = leftPct > rightPct;
            boolean bAhead = rightPct > leftPct;
            int leftColor = mixColor(ensureReadableColor(boostNeonColor(leftBase, aAhead ? 1.22f : 1.08f, aAhead ? 1.14f : 1.05f), aAhead ? 176 : 164), Color.rgb(216, 224, 236), 0.06f);
            int rightColor = mixColor(ensureReadableColor(boostNeonColor(rightBase, bAhead ? 1.22f : 1.08f, bAhead ? 1.14f : 1.05f), bAhead ? 176 : 164), Color.rgb(216, 224, 236), 0.06f);
            int leaderGlow = aAhead ? leftColor : (bAhead ? rightColor : Color.WHITE);
            String leader = aAhead ? shortName(h.nameA).toUpperCase(Locale.US) : (bAhead ? shortName(h.nameB).toUpperCase(Locale.US) : "EVEN");
            int pctDiff = Math.abs(leftPct - rightPct);
            String leadWord = pctDiff <= 2 ? "EVEN" : (pctDiff <= 10 ? "SLIGHTLY LEADS" : (pctDiff <= 22 ? "LEADS" : "BIG EDGE"));
            String title = pctDiff <= 2 ? "WEIGHTED EDGE · EVEN" : "WEIGHTED EDGE · " + leader + " " + leadWord;

            float titleSize = fitTextSize(paint, title, dp(9), dp(7), score.width() - dp(30), 0.10f, true);
            drawText(canvas, title, score.centerX(), score.top + dp(23), titleSize, Color.rgb(232, 240, 249), true, Paint.Align.CENTER, 0.10f);
            drawText(canvas, "SCORED EDGE SHARE", score.centerX(), score.top + dp(41), dp(7), Color.rgb(166, 181, 202), true, Paint.Align.CENTER, 0.11f);

            float midY = score.top + dp(74);
            float scoreSize = dp(35);
            float hyphenSize = dp(29);
            float xA = score.centerX() - dp(68);
            float xB = score.centerX() + dp(68);
            String leftScoreText = leftPct + "%";
            String rightScoreText = rightPct + "%";

            paint.setShadowLayer(dp(4), 0, dp(2), Color.argb(150, 0, 0, 0));
            drawText(canvas, leftScoreText, xA, midY, scoreSize, leftColor, true, Paint.Align.CENTER, 0.00f);
            paint.setShadowLayer(dp(8), 0, 0, Color.argb(126, Color.red(leftColor), Color.green(leftColor), Color.blue(leftColor)));
            drawText(canvas, leftScoreText, xA, midY, scoreSize, leftColor, true, Paint.Align.CENTER, 0.00f);
            paint.clearShadowLayer();

            drawText(canvas, "–", score.centerX(), midY - dp(2), hyphenSize, Color.rgb(226, 234, 244), true, Paint.Align.CENTER, 0.00f);

            paint.setShadowLayer(dp(4), 0, dp(2), Color.argb(150, 0, 0, 0));
            drawText(canvas, rightScoreText, xB, midY, scoreSize, rightColor, true, Paint.Align.CENTER, 0.00f);
            paint.setShadowLayer(dp(8), 0, 0, Color.argb(126, Color.red(rightColor), Color.green(rightColor), Color.blue(rightColor)));
            drawText(canvas, rightScoreText, xB, midY, scoreSize, rightColor, true, Paint.Align.CENTER, 0.00f);
            paint.clearShadowLayer();

            float barY = score.bottom - dp(18);
            RectF track = new RectF(score.left + dp(28), barY - dp(5), score.right - dp(28), barY + dp(5));
            paint.setShader(null);
            paint.setColor(Color.argb(74, 255, 255, 255));
            canvas.drawRoundRect(track, dp(7), dp(7), paint);

            float merge = Math.max(0.12f, Math.min(0.88f, leftPct / 100f));
            float blendHalf = 0.12f;
            float leftStop = Math.max(0.02f, merge - blendHalf);
            float rightStop = Math.min(0.98f, merge + blendHalf);
            int midEdge = mixColor(leftBase, rightBase, 0.50f);
            paint.setShader(new LinearGradient(track.left, track.top, track.right, track.bottom,
                    new int[] { leftBase, softColor(leftBase, 0.15f), softColor(midEdge, 0.10f), softColor(rightBase, 0.15f), rightBase },
                    new float[] {0f, leftStop, merge, rightStop, 1f}, Shader.TileMode.CLAMP));
            paint.setShadowLayer(dp(6), 0, 0, Color.argb(88, Color.red(leaderGlow), Color.green(leaderGlow), Color.blue(leaderGlow)));
            canvas.drawRoundRect(track, dp(7), dp(7), paint);
            paint.clearShadowLayer();
            paint.setShader(null);

            float centerX = track.left + track.width() * 0.50f;
            strokePaint.setShader(null);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeWidth(dp(1));
            strokePaint.setColor(Color.argb(78, 238, 244, 252));
            canvas.drawLine(centerX, track.top - dp(2), centerX, track.bottom + dp(2), strokePaint);

            float mergeX = track.left + track.width() * merge;
            strokePaint.setStrokeWidth(dp(2));
            strokePaint.setColor(Color.argb(132, Color.red(leaderGlow), Color.green(leaderGlow), Color.blue(leaderGlow)));
            strokePaint.setShadowLayer(dp(5), 0, 0, Color.argb(118, Color.red(leaderGlow), Color.green(leaderGlow), Color.blue(leaderGlow)));
            canvas.drawLine(mergeX, track.top - dp(3), mergeX, track.bottom + dp(3), strokePaint);
            strokePaint.clearShadowLayer();
        }

        private void drawKeyStatBody(Canvas canvas, RectF card, float keyY) {
            keyEdgePickerHotspot.set(card.left + dp(10), keyY - dp(26), card.left + dp(190), keyY + dp(16));
            drawText(canvas, "SCORED STAT EDGE", card.left + dp(48), keyY, dp(11), Color.rgb(228, 235, 245), true, Paint.Align.LEFT, 0.12f);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(Color.argb(48, 255, 255, 255));
            canvas.drawCircle(card.left + dp(28), keyY - dp(5), dp(14), paint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(3));
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setColor(Color.WHITE);
            float bx = card.left + dp(22), by = keyY - dp(10);
            canvas.drawLine(bx, by + dp(9), bx, by + dp(15), strokePaint);
            canvas.drawLine(bx + dp(6), by + dp(5), bx + dp(6), by + dp(15), strokePaint);
            canvas.drawLine(bx + dp(12), by + dp(1), bx + dp(12), by + dp(15), strokePaint);

            float panelTop = keyY + dp(20);
            float panelBottom = card.bottom - dp(18);
            RectF panel = new RectF(card.left + dp(24), panelTop, card.right - dp(24), panelBottom);
            paint.setShader(new LinearGradient(panel.left, panel.top, panel.right, panel.bottom,
                    new int[] { Color.argb(178, 6, 12, 22), Color.argb(164, 8, 18, 34), Color.argb(184, 5, 14, 28) },
                    null, Shader.TileMode.CLAMP));
            paint.setStyle(Paint.Style.FILL);
            paint.setShadowLayer(dp(8), 0, dp(4), Color.argb(55, 0, 0, 0));
            canvas.drawRoundRect(panel, dp(16), dp(16), paint);
            paint.clearShadowLayer();
            paint.setShader(null);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1));
            strokePaint.setColor(Color.argb(58, 255, 255, 255));
            canvas.drawRoundRect(panel, dp(16), dp(16), strokePaint);

            int rowCount = Math.max(1, shareMetrics.size());
            float rowH = panel.height() / rowCount;
            for (int i = 0; i < shareMetrics.size(); i++) {
                Metric m = shareMetrics.get(i);
                float top = panel.top + i * rowH;
                float bottom = top + rowH;
                if (i > 0) {
                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setStrokeWidth(dp(1));
                    strokePaint.setShader(null);
                    strokePaint.setColor(Color.argb(34, 255, 255, 255));
                    canvas.drawLine(panel.left + dp(2), top, panel.right - dp(2), top, strokePaint);
                }
                drawShareMetric(canvas, m, panel, top, bottom);
            }
        }

        private void drawShareMetric(Canvas canvas, Metric m, RectF panel, float top, float bottom) {
            Double a = h.statsA.get(m.key), b = h.statsB.get(m.key);
            StatEdgeResult edge = statEdgeForMetric(h, m);
            int winner = edge == null ? 0 : edge.winner;
            int aBase = battleTeamColor(paletteA, paletteB, true);
            int bBase = battleTeamColor(paletteB, paletteA, false);
            int aStrong = boostNeonColor(aBase, 1.58f, 1.18f);
            int bStrong = boostNeonColor(bBase, 1.58f, 1.18f);
            // Keep stat text hues stable row-to-row so the same team does not drift between different purples/blues.
            // Winners get emphasis from glow/rail treatment, not from random text hue shifts.
            int aColor = mixColor(ensureReadableColor(boostNeonColor(aBase, 1.16f, 1.05f), 168), Color.rgb(216, 224, 236), 0.07f);
            int bColor = mixColor(ensureReadableColor(boostNeonColor(bBase, 1.16f, 1.05f), 168), Color.rgb(216, 224, 236), 0.07f);
            float rowH = bottom - top;
            float cy = (top + bottom) / 2f;

            // v42: strict, repeatable vertical rhythm. Values and stat labels share one optical center;
            // the blended rail sits on a separate fixed baseline below them, so rows don't drift.
            float valueSize = dp(winner != 0 ? 20 : 19);
            float labelSize = dp(11);
            // v60: tiny extra downward nudge so the numbers sit more centrally within each row.
            float valueCenterY = top + rowH * 0.468f;
            float labelCenterY = top + rowH * 0.412f;
            float valueBaseline = centeredTextBaseline(valueCenterY, valueSize, true);
            float labelBaseline = centeredTextBaseline(labelCenterY, labelSize, true);
            float railY = top + rowH * 0.75f;

            // v60: push values outward a touch more and keep a fixed-width slot so every row
            // gets the same longer rail, independent of actual rendered string width.
            float innerPad = dp(22);
            float leftValueX = panel.left + innerPad;
            float rightValueX = panel.right - innerPad;
            String leftText = shareFormat(a, m);
            String rightText = shareFormat(b, m);
            paint.setTextSize(valueSize);
            paint.setTypeface(tfBold);
            float valueSlotWidth = Math.max(paint.measureText("0.000"), paint.measureText("99.9"));
            float railGap = dp(5);
            float railLeft = leftValueX + valueSlotWidth + railGap;
            float railRight = rightValueX - valueSlotWidth - railGap;
            if (railRight - railLeft < dp(170)) {
                float deficit = dp(170) - (railRight - railLeft);
                railLeft = Math.max(panel.left + dp(80), railLeft - deficit / 2f);
                railRight = Math.min(panel.right - dp(80), railRight + deficit / 2f);
            }
            float cX = (railLeft + railRight) / 2f;

            paint.setShadowLayer(dp(4), 0, 0, Color.argb(104, 0, 0, 0));
            drawText(canvas, leftText, leftValueX, valueBaseline, valueSize, aColor, true, Paint.Align.LEFT, 0.00f);
            drawText(canvas, rightText, rightValueX, valueBaseline, valueSize, bColor, true, Paint.Align.RIGHT, 0.00f);
            paint.clearShadowLayer();

            drawText(canvas, m.label, cX, labelBaseline, labelSize, Color.rgb(224, 232, 242), true, Paint.Align.CENTER, 0.10f);
            if (edge != null && edge.badge != null && !edge.badge.isEmpty()) {
                paint.setTextSize(labelSize);
                paint.setTypeface(tfBold);
                float labelW = paint.measureText(m.label);
                float badgeX = Math.min(railRight - dp(21), cX + labelW / 2f + dp(18));
                int badgeColor = edge.contextOnly ? Color.rgb(176, 188, 206) : (edge.volumeSensitive ? Color.rgb(188, 198, 215) : Color.rgb(199, 211, 231));
                drawShareMiniBadge(canvas, edge.badge, badgeX, labelCenterY, badgeColor);
            }

            float half = Math.max(dp(40), (railRight - railLeft) / 2f);
            float leftMid = cX;
            float rightMid = cX;

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);

            // v61: smooth the full-rail fades so there are no abrupt color handoffs.
            strokePaint.setStrokeWidth(dp(3.8f));
            strokePaint.setShader(new LinearGradient(railLeft, railY, railRight, railY,
                    new int[] {
                            Color.argb(0, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)),
                            Color.argb(36, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)),
                            Color.argb(66, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)),
                            Color.argb(112, 244, 249, 255),
                            Color.argb(68, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong)),
                            Color.argb(38, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong)),
                            Color.argb(0, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong))
                    },
                    new float[] {0f, 0.10f, 0.24f, 0.50f, 0.76f, 0.90f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawLine(railLeft, railY, railRight, railY, strokePaint);

            // Primary blended color rail over the under-rail for a more readable continuous line.
            strokePaint.setStrokeWidth(dp(5.0f));
            strokePaint.setShader(new LinearGradient(railLeft, railY, railRight, railY,
                    new int[] {
                            Color.argb(0, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)),
                            Color.argb(52, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)),
                            Color.argb(96, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)),
                            Color.argb(112, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)),
                            Color.argb(100, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong)),
                            Color.argb(114, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong)),
                            Color.argb(58, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong)),
                            Color.argb(0, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong))
                    },
                    new float[] {0f, 0.08f, 0.22f, 0.44f, 0.56f, 0.78f, 0.92f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawLine(railLeft, railY, railRight, railY, strokePaint);

            double[] domain = metricScaleDomain(m, new Double[] { a, b });
            float pa = normalizedForRail(a, domain, m);
            float pb = normalizedForRail(b, domain, m);
            float leftLen = Math.max(dp(14), half * pa);
            float rightLen = Math.max(dp(14), half * pb);
            leftLen = Math.min(leftLen, half);
            rightLen = Math.min(rightLen, half);

            boolean leftWinsRow = winner < 0;
            boolean rightWinsRow = winner > 0;

            // v129: spark travel and glow now come from the unified StatEdgeEngine:
            // quality percentile gap -> sample reliability -> dead zone -> eased visual strength.
            float edgeNorm = edge == null ? 0f : (float)Math.max(0d, Math.min(1d, edge.visualStrength));
            int direction = winner < 0 ? -1 : (winner > 0 ? 1 : 0);
            int sparkColor = direction < 0 ? aStrong : bStrong;
            float maxTravel = Math.max(dp(20), half - dp(5));
            float sparkX = cX + direction * maxTravel * edgeNorm;
            sparkX = Math.max(railLeft + dp(4), Math.min(railRight - dp(4), sparkX));
            float sparkStrength = edge == null ? 0f : (float)Math.max(0d, Math.min(1d, edge.glowStrength));

            // v108: loser side stays visible almost the full half-rail, then softly fades only at the far tip.
            // This gives every row a consistent comparison baseline while the winner owns the bright neon.
            float loserFade = Math.min(dp(18), half * 0.16f);
            strokePaint.setStrokeWidth(dp(4.7f));
            if (!leftWinsRow) {
                strokePaint.setShader(new LinearGradient(railLeft, railY, cX, railY,
                        new int[] {
                                Color.argb(0, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)),
                                Color.argb(46, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)),
                                Color.argb(66, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)),
                                Color.argb(72, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)),
                                Color.argb(62, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong))
                        },
                        new float[] {0f, Math.min(0.18f, loserFade / half), 0.42f, 0.78f, 1f}, Shader.TileMode.CLAMP));
                strokePaint.setShadowLayer(dp(2.0f), 0, 0, Color.argb(30, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)));
                canvas.drawLine(railLeft, railY, cX, railY, strokePaint);
            }
            if (!rightWinsRow) {
                strokePaint.setShader(new LinearGradient(cX, railY, railRight, railY,
                        new int[] {
                                Color.argb(62, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong)),
                                Color.argb(72, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong)),
                                Color.argb(66, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong)),
                                Color.argb(46, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong)),
                                Color.argb(0, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong))
                        },
                        new float[] {0f, 0.22f, 0.58f, Math.max(0.82f, 1f - loserFade / half), 1f}, Shader.TileMode.CLAMP));
                strokePaint.setShadowLayer(dp(2.0f), 0, 0, Color.argb(30, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong)));
                canvas.drawLine(cX, railY, railRight, railY, strokePaint);
            }
            strokePaint.clearShadowLayer();

            // v61: slightly smoother center blend before the winner trail, still tight enough to read directionally.
            strokePaint.setStrokeWidth(dp(2.9f));
            strokePaint.setShader(new LinearGradient(cX - dp(34), railY, cX + dp(34), railY,
                    new int[] {
                            Color.argb(78, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)),
                            Color.argb(110, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)),
                            Color.argb(166, 247, 251, 255),
                            Color.argb(112, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong)),
                            Color.argb(82, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong))
                    },
                    new float[] {0f, 0.28f, 0.50f, 0.72f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawLine(cX - dp(32), railY, cX + dp(32), railY, strokePaint);
            strokePaint.setShader(null);

            // v124: removed the always-full winner half-rail. The bright segment now ends at sparkX,
            // so each row's visual length reflects the actual stat-relative gap.

            // Winner side: bright but controlled approach line to the spark. Keep the spark tail itself
            // deterministic so long winning rows do not turn into oversized glows.
            if (leftWinsRow && winner != 0) {
                strokePaint.setStrokeWidth(dp(5.6f));
                strokePaint.setShader(new LinearGradient(cX, railY, sparkX, railY,
                        new int[] {
                                Color.argb(112, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)),
                                Color.argb(150, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)),
                                Color.argb(188, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)),
                                Color.argb(214, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)),
                                Color.argb(222, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong))
                        },
                        new float[] {0f, 0.24f, 0.54f, 0.80f, 1f}, Shader.TileMode.CLAMP));
                strokePaint.setShadowLayer(dp(3.6f), 0, 0, Color.argb(76, Color.red(aStrong), Color.green(aStrong), Color.blue(aStrong)));
                canvas.drawLine(cX, railY, sparkX, railY, strokePaint);
                strokePaint.clearShadowLayer();
            }
            if (rightWinsRow && winner != 0) {
                strokePaint.setStrokeWidth(dp(5.6f));
                strokePaint.setShader(new LinearGradient(cX, railY, sparkX, railY,
                        new int[] {
                                Color.argb(112, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong)),
                                Color.argb(150, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong)),
                                Color.argb(188, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong)),
                                Color.argb(214, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong)),
                                Color.argb(222, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong))
                        },
                        new float[] {0f, 0.24f, 0.54f, 0.80f, 1f}, Shader.TileMode.CLAMP));
                strokePaint.setShadowLayer(dp(3.6f), 0, 0, Color.argb(76, Color.red(bStrong), Color.green(bStrong), Color.blue(bStrong)));
                canvas.drawLine(cX, railY, sparkX, railY, strokePaint);
                strokePaint.clearShadowLayer();
            }

            // v111: restore a vivid electric body across the whole active line, not just a tiny endpoint spark.
            // Loser rails remain visible but subdued; winner rails get a strong neon channel that culminates in the spark.
            drawRailNeonBody(canvas, railLeft, cX, railY, aStrong, 0.18f, false);
            drawRailNeonBody(canvas, cX, railRight, railY, bStrong, 0.18f, false);
            if (winner != 0) {
                if (direction < 0) {
                    drawRailNeonBody(canvas, cX, sparkX, railY, aStrong, 0.96f * sparkStrength, true);
                } else {
                    drawRailNeonBody(canvas, cX, sparkX, railY, bStrong, 0.96f * sparkStrength, true);
                }
                drawRailGlowRamp(canvas, cX, sparkX, railY, sparkColor, sparkStrength);
                drawRailElectricPulse(canvas, sparkX, railY, sparkColor, direction < 0, sparkStrength);
            }
        }

        private void drawShareMiniBadge(Canvas canvas, String label, float x, float centerY, int color) {
            if (label == null || label.isEmpty()) return;
            paint.setTextSize(dp(6.4f));
            paint.setTypeface(tfBold);
            paint.setTextAlign(Paint.Align.CENTER);
            float textW = paint.measureText(label);
            float w = Math.max(dp(16), textW + dp(8));
            float h = dp(10);
            RectF r = new RectF(x, centerY - h / 2f, x + w, centerY + h / 2f);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(Color.argb(36, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawRoundRect(r, h / 2f, h / 2f, paint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(0.7f));
            strokePaint.setColor(Color.argb(96, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawRoundRect(r, h / 2f, h / 2f, strokePaint);
            drawText(canvas, label, r.centerX(), centeredTextBaseline(r.centerY(), dp(6.4f), true), dp(6.4f), color, true, Paint.Align.CENTER, 0.06f);
        }

        private boolean isPadresPalette(TeamPalette palette) {
            if (palette == null) return false;
            return colorDistance(palette.primary, Color.rgb(255, 205, 40)) < 70f
                    && colorDistance(palette.secondary, Color.rgb(47, 36, 29)) < 70f;
        }

        private float statRelativeGapNorm(Metric m, Double a, Double b) {
            if (m == null || a == null || b == null || Double.isNaN(a) || Double.isNaN(b)) return 0f;
            if (format(a, m).equals(format(b, m))) return 0f;
            double benchmark = meaningfulGapBenchmark(m);
            if (benchmark <= 0d) return 0f;
            double gap = Math.abs(a - b);
            double dead = statDeadZone(m);
            if (gap <= dead) return 0f;
            float raw = (float)Math.min(1d, (gap - dead) / benchmark);
            // gentle ease-out so moderate but meaningful gaps already read clearly.
            return 1f - (float)Math.pow(1f - raw, 1.20f);
        }

        private double statDeadZone(Metric m) {
            if (m == null) return 0d;
            if (m.decimals <= 0) return 0.000001d;
            return 0.5d * Math.pow(10d, -m.decimals);
        }

        private double meaningfulGapBenchmark(Metric m) {
            if (m == null || m.key == null) return 0.10d;
            switch (m.key) {
                case "avg":
            case "teamAVG": return 0.045d;
                case "obp":
            case "teamOBP": return 0.035d;
                case "slg":
            case "teamSLG": return 0.080d;
                case "ops":
            case "teamOPS": return 0.075d;
                case "wOBA":
            case "teamWOBA": return 0.055d;
                case "xwOBA":
            case "teamXWOBA": return 0.065d;
                case "hr": return 12d;
                case "rbi":
            case "teamRBI": return 20d;
                case "r": return 20d;
                case "sb":
            case "teamSB": return 10d;
                case "bbPct":
                case "kPct":
                case "barrelPct":
                case "hardHitPct": return 5d;
                case "era":
            case "teamERA": return 1.35d;
                case "whip":
            case "teamWHIP": return 0.38d;
                case "k9":
            case "teamK9": return 4.00d;
                case "bb9":
            case "teamBB9": return 1.75d;
                case "kbb":
            case "teamKBB": return 1.50d;
                case "ip": return 45d;
                case "pitchK": return 40d;
                case "pitchBB": return 12d;
                case "saves": return 10d;
                default: return 0.10d;
            }
        }

        private void drawRailNeonBody(Canvas canvas, float startX, float endX, float y, int color, float intensity, boolean winnerSegment) {
            float len = Math.abs(endX - startX);
            if (len < dp(2f)) return;
            float s = Math.max(0.16f, Math.min(1.0f, intensity));
            int glowAlphaA = (int)((winnerSegment ? 126 : 54) + (winnerSegment ? 84 : 34) * s);
            int glowAlphaB = (int)((winnerSegment ? 164 : 74) + (winnerSegment ? 74 : 28) * s);
            int coreAlphaA = (int)((winnerSegment ? 170 : 74) + (winnerSegment ? 64 : 20) * s);
            int coreAlphaB = (int)((winnerSegment ? 228 : 108) + (winnerSegment ? 22 : 12) * s);
            int hot = mixColor(Color.WHITE, color, winnerSegment ? 0.16f : 0.08f);

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);

            strokePaint.setStrokeWidth(dp((winnerSegment ? 7.4f : 4.8f) + (winnerSegment ? 1.2f : 0.5f) * s));
            strokePaint.setShader(new LinearGradient(startX, y, endX, y,
                    new int[] {
                            Color.argb((int)(glowAlphaA * 0.55f), Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(glowAlphaA, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(glowAlphaB, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(glowAlphaA, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb((int)(glowAlphaA * 0.55f), Color.red(color), Color.green(color), Color.blue(color))
                    },
                    new float[] {0f, 0.14f, 0.50f, 0.86f, 1f}, Shader.TileMode.CLAMP));
            strokePaint.setShadowLayer(dp((winnerSegment ? 5.4f : 2.8f) + (winnerSegment ? 1.8f : 0.6f) * s), 0, 0,
                    Color.argb((int)((winnerSegment ? 116 : 40) + (winnerSegment ? 46 : 14) * s), Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawLine(startX, y, endX, y, strokePaint);
            strokePaint.clearShadowLayer();

            strokePaint.setStrokeWidth(dp((winnerSegment ? 3.1f : 2.0f) + (winnerSegment ? 0.4f : 0.2f) * s));
            strokePaint.setShader(new LinearGradient(startX, y, endX, y,
                    new int[] {
                            Color.argb((int)(coreAlphaA * 0.40f), Color.red(hot), Color.green(hot), Color.blue(hot)),
                            Color.argb(coreAlphaA, Color.red(hot), Color.green(hot), Color.blue(hot)),
                            Color.argb(coreAlphaB, Color.red(hot), Color.green(hot), Color.blue(hot)),
                            Color.argb(coreAlphaA, Color.red(hot), Color.green(hot), Color.blue(hot)),
                            Color.argb((int)(coreAlphaA * 0.40f), Color.red(hot), Color.green(hot), Color.blue(hot))
                    },
                    new float[] {0f, 0.16f, 0.50f, 0.84f, 1f}, Shader.TileMode.CLAMP));
            strokePaint.setShadowLayer(dp(winnerSegment ? 2.6f : 1.4f), 0, 0,
                    Color.argb((int)((winnerSegment ? 88 : 30) + (winnerSegment ? 18 : 8) * s), Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawLine(startX, y, endX, y, strokePaint);
            strokePaint.clearShadowLayer();
            strokePaint.setShader(null);
        }

        private void drawRailGlowRamp(Canvas canvas, float centerX, float sparkX, float y, int color, float marginStrength) {
            // v109: keep the post-spark glow compact and fixed so long winning rows do not create oversized neon tails.
            float s = Math.max(0.18f, Math.min(1.0f, marginStrength));
            if (Math.abs(sparkX - centerX) < dp(1.25f)) return;
            float towardCenter = sparkX < centerX ? dp(4.5f) : -dp(4.5f);
            float tail = sparkX < centerX ? -dp(16f) : dp(16f);
            float glowStart = sparkX + towardCenter;
            float glowEnd = sparkX + tail;
            float totalLen = Math.max(dp(16), Math.abs(glowEnd - glowStart));
            float peakPos = Math.max(0.18f, Math.min(0.72f, Math.abs(sparkX - glowStart) / totalLen));
            float postPeak = Math.min(0.985f, peakPos + 0.24f);
            int hot = mixColor(Color.WHITE, color, 0.08f);

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeWidth(dp(7.6f + 1.2f * s));
            strokePaint.setShader(new LinearGradient(glowStart, y, glowEnd, y,
                    new int[] {
                            Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb((int)(92 + 34 * s), Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb((int)(236 + 18 * s), Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb((int)(108 + 24 * s), Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
                    },
                    new float[] {0f, Math.max(0.08f, peakPos * 0.52f), peakPos, postPeak, 1f}, Shader.TileMode.CLAMP));
            strokePaint.setShadowLayer(dp(5.2f + 2.4f * s), 0, 0, Color.argb((int)(118 + 44 * s), Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawLine(glowStart, y, glowEnd, y, strokePaint);
            strokePaint.clearShadowLayer();

            strokePaint.setStrokeWidth(dp(2.3f + 0.5f * s));
            strokePaint.setShader(new LinearGradient(glowStart, y, glowEnd, y,
                    new int[] {
                            Color.argb(0, Color.red(hot), Color.green(hot), Color.blue(hot)),
                            Color.argb((int)(42 + 18 * s), Color.red(hot), Color.green(hot), Color.blue(hot)),
                            Color.argb((int)(182 + 24 * s), Color.red(hot), Color.green(hot), Color.blue(hot)),
                            Color.argb((int)(40 + 14 * s), Color.red(hot), Color.green(hot), Color.blue(hot)),
                            Color.argb(0, Color.red(hot), Color.green(hot), Color.blue(hot))
                    },
                    new float[] {0f, Math.max(0.06f, peakPos * 0.60f), peakPos, postPeak, 1f}, Shader.TileMode.CLAMP));
            strokePaint.setShadowLayer(dp(2.2f), 0, 0, Color.argb((int)(86 + 18 * s), Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawLine(glowStart, y, glowEnd, y, strokePaint);
            strokePaint.clearShadowLayer();
            strokePaint.setShader(null);
        }

        private void drawRailElectricPulse(Canvas canvas, float x, float y, int color, boolean leftWinner, float marginStrength) {
            // v55: slightly brighter/tighter endpoint spark, still compact.
            float s = Math.max(0.18f, Math.min(1.0f, marginStrength));
            float len = dp(7.8f + 6.2f * s);
            float tailX = leftWinner ? x + len : x - len;
            int mainAlpha = (int)(172 + 58 * s);
            int subAlpha = (int)(116 + 36 * s);
            drawElectricBolt(canvas, tailX, y, x, y, color, dp(0.62f + 0.18f * s), 4, dp(0.82f + 0.14f * s), mainAlpha);
            drawElectricBolt(canvas, tailX - (leftWinner ? dp(0.7f) : -dp(0.7f)), y + dp(0.7f), x, y + dp(0.7f), color, dp(0.20f + 0.08f * s), 4, dp(0.48f + 0.08f * s), subAlpha);
            drawElectricStar(canvas, x, y, color, dp(5.4f + 1.2f * s), 0.82f + 0.18f * s);
        }

        private void drawElectricStar(Canvas canvas, float x, float y, int color, float radius, float strength) {
            float glowR = radius * 1.65f;
            int hot = mixColor(Color.WHITE, color, 0.08f);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(x, y, glowR,
                    new int[] {
                            Color.argb((int)(148 * strength), Color.red(hot), Color.green(hot), Color.blue(hot)),
                            Color.argb((int)(214 * strength), Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb((int)(144 * strength), Color.red(color), Color.green(color), Color.blue(color)),
                            Color.TRANSPARENT
                    }, new float[] {0f, 0.18f, 0.52f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, glowR, paint);
            paint.setShader(null);

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeWidth(dp(1.05f));
            strokePaint.setColor(Color.argb((int)(198 * strength), Color.red(hot), Color.green(hot), Color.blue(hot)));
            strokePaint.setShadowLayer(dp(3.2f), 0, 0, Color.argb((int)(168 * strength), Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawLine(x - radius * 0.82f, y, x + radius * 0.82f, y, strokePaint);
            canvas.drawLine(x, y - radius * 0.38f, x, y + radius * 0.38f, strokePaint);
            strokePaint.clearShadowLayer();
        }

        private void drawElectricBolt(Canvas canvas, float x1, float y1, float x2, float y2, int color, float amplitude, int segments, float width, int alpha) {
            Path path = new Path();
            float dx = x2 - x1;
            float dy = y2 - y1;
            float len = (float)Math.sqrt(dx * dx + dy * dy);
            if (len <= 1f) return;
            float nx = -dy / len;
            float ny = dx / len;
            path.moveTo(x1, y1);
            for (int i = 1; i < segments; i++) {
                float t = i / (float)segments;
                float wave = (float)Math.sin(t * Math.PI);
                float jitter = ((i % 2 == 0) ? 1f : -1f) * amplitude * wave * (0.72f + 0.28f * (float)Math.sin(i * 1.35f));
                float px = x1 + dx * t + nx * jitter;
                float py = y1 + dy * t + ny * jitter;
                path.lineTo(px, py);
            }
            path.lineTo(x2, y2);

            int hot = mixColor(Color.WHITE, color, 0.08f);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
            strokePaint.setShader(null);
            strokePaint.setStrokeWidth(width * 2.55f);
            strokePaint.setColor(Color.argb(Math.max(26, alpha / 4), Color.red(color), Color.green(color), Color.blue(color)));
            strokePaint.setShadowLayer(width * 3.55f, 0, 0, Color.argb(Math.min(198, alpha), Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawPath(path, strokePaint);
            strokePaint.clearShadowLayer();

            strokePaint.setStrokeWidth(width * 1.04f);
            strokePaint.setColor(Color.argb(Math.max(74, (int)(alpha * 0.90f)), Color.red(hot), Color.green(hot), Color.blue(hot)));
            strokePaint.setShadowLayer(width * 1.70f, 0, 0, Color.argb(Math.min(182, alpha), Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawPath(path, strokePaint);
            strokePaint.clearShadowLayer();
        }

        private int readableTeamColor(int primary, int secondary, boolean leftSide) {
            // v110: strongly prefer the actual team identity color so neon effects stay
            // recognizable (Rockies purple, A's green, Cubs blue, etc.) instead of drifting
            // toward whichever palette swatch is brightest.
            float[] hsvPrimary = new float[3];
            float[] hsvSecondary = new float[3];
            Color.colorToHSV(primary, hsvPrimary);
            Color.colorToHSV(secondary, hsvSecondary);

            int base = primary;
            if (hsvPrimary[2] < 0.42f) {
                base = mixColor(primary, secondary, 0.24f);
            } else if (hsvPrimary[1] < 0.32f && hsvSecondary[1] > hsvPrimary[1] + 0.08f) {
                base = mixColor(primary, secondary, 0.18f);
            }

            base = ensureReadableColor(base, leftSide ? 146 : 142);
            return boostNeonColor(base, 1.24f, leftSide ? 1.12f : 1.10f);
        }

        private boolean palettesTooSimilar(TeamPalette a, TeamPalette b) {
            if (a == null || b == null) return false;
            return colorDistance(a.primary, b.primary) < 88f;
        }

        private float colorDistance(int a, int b) {
            float dr = Color.red(a) - Color.red(b);
            float dg = Color.green(a) - Color.green(b);
            float db = Color.blue(a) - Color.blue(b);
            return (float)Math.sqrt(dr * dr + dg * dg + db * db);
        }

        private int battleTeamColor(TeamPalette own, TeamPalette other, boolean leftSide) {
            if (own == null) return Color.WHITE;
            int primary = own.primary;
            int secondary = own.secondary;
            if (!leftSide && palettesTooSimilar(own, other)) {
                primary = own.secondary;
                secondary = own.primary;
            }
            return readableTeamColor(primary, secondary, leftSide);
        }

        private int colorLuminance(int color) {
            return Math.round(0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color));
        }

        private int ensureReadableColor(int color, int minLuminance) {
            int lum = colorLuminance(color);
            if (lum >= minLuminance) return color;
            float deficit = Math.min(1f, Math.max(0f, (minLuminance - lum) / 255f));
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            hsv[1] = Math.min(1f, Math.max(0.18f, hsv[1] + 0.08f + deficit * 0.22f));
            hsv[2] = Math.min(1f, Math.max(0.22f, hsv[2] + 0.16f + deficit * 0.52f));
            return Color.HSVToColor(hsv);
        }

        private int mixColor(int a, int b, float amountB) {
            float t = Math.max(0f, Math.min(1f, amountB));
            int r = Math.round(Color.red(a) * (1f - t) + Color.red(b) * t);
            int g = Math.round(Color.green(a) * (1f - t) + Color.green(b) * t);
            int bb = Math.round(Color.blue(a) * (1f - t) + Color.blue(b) * t);
            return Color.rgb(r, g, bb);
        }

        private String shareFormat(Double v, Metric m) {
            String s = format(v, m);
            if (s.startsWith("0.")) return s.substring(1);
            if (s.startsWith("-0.")) return "-" + s.substring(2);
            return s;
        }

        private float normalizedForRail(Double v, double[] domain, Metric m) {
            if (v == null || Double.isNaN(v)) return 0.05f;
            double lo = domain[0], hi = domain[1];
            if (hi <= lo) return 0.5f;
            double norm = (v - lo) / (hi - lo);
            if (m.higherGood != null && !m.higherGood) norm = 1.0 - norm;
            return (float)Math.max(0.08, Math.min(1.0, norm));
        }

        private float centeredTextBaseline(float centerY, float sizePx, boolean bold) {
            paint.setTextSize(sizePx);
            paint.setTypeface(bold ? tfBold : tfRegular);
            Paint.FontMetrics fm = paint.getFontMetrics();
            return centerY - (fm.ascent + fm.descent) / 2f;
        }

        private void drawText(Canvas canvas, String value, float x, float baseline, float sizePx, int color, boolean bold, Paint.Align align, float letterSpacing) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextSize(sizePx);
            paint.setTypeface(bold ? tfBold : tfRegular);
            paint.setTextAlign(align);
            if (letterSpacing <= 0.01f || value == null || value.length() <= 1) {
                canvas.drawText(value == null ? "" : value, x, baseline, paint);
                return;
            }
            String s = value;
            float extra = sizePx * letterSpacing;
            float total = paint.measureText(s) + extra * (s.length() - 1);
            float start = x;
            if (align == Paint.Align.CENTER) start = x - total / 2f;
            else if (align == Paint.Align.RIGHT) start = x - total;
            paint.setTextAlign(Paint.Align.LEFT);
            for (int i = 0; i < s.length(); i++) {
                String ch = s.substring(i, i + 1);
                canvas.drawText(ch, start, baseline, paint);
                start += paint.measureText(ch) + extra;
            }
            paint.setTextAlign(align);
        }

        private void drawPill(Canvas canvas, String label, float cx, float cy, float w, float h, int fill, int stroke, int textColor, float sp) {
            RectF r = new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(fill);
            canvas.drawRoundRect(r, h / 2f, h / 2f, paint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1));
            strokePaint.setColor(stroke);
            canvas.drawRoundRect(r, h / 2f, h / 2f, strokePaint);
            drawText(canvas, label, cx, cy + dp(4), sp, textColor, true, Paint.Align.CENTER, 0.05f);
        }
    }

    private ArrayList<Metric> collectHeadToHeadMetrics(HeadToHeadComparison h, int max) {
        return collectHeadToHeadMetrics(h, max, false);
    }

    private ArrayList<Metric> collectHeadToHeadMetrics(HeadToHeadComparison h, int max, boolean keyEdgeOnly) {
        ArrayList<Metric> ordered = new ArrayList<>();
        ArrayList<Metric> snapshot = keyEdgeOnly ? h.keyEdgeMetricsSnapshot : h.selectedMetricsSnapshot;
        if (snapshot != null && !snapshot.isEmpty()) {
            for (Metric m : snapshot) {
                if (m == null || ordered.contains(m)) continue;
                ordered.add(m);
                if (ordered.size() >= max) return ordered;
            }
            return ordered;
        }
        LinkedHashSet<String> sourceKeys = keyEdgeOnly ? keyEdgeMetricKeys : selectedMetricKeys;
        if (sourceKeys == null || sourceKeys.isEmpty()) sourceKeys = selectedMetricKeys;
        for (String key : sourceKeys) {
            Metric m = findMetricByKey(key);
            if (m == null || ordered.contains(m)) continue;
            if (!roleAllowsMetric(roleForScope(h.scope), m)) continue;
            ordered.add(m);
            if (ordered.size() >= max) return ordered;
        }
        if (keyEdgeOnly && ordered.isEmpty()) {
            for (Metric m : selectedMetricsForScope(h.scope)) {
                if (ordered.contains(m)) continue;
                ordered.add(m);
                if (ordered.size() >= max) return ordered;
            }
        }
        return ordered;
    }

    private int[] countHeadToHeadWins(HeadToHeadComparison h, ArrayList<Metric> metricList) {
        return scoreSummaryToInts(summarizeHeadToHeadEdges(h, metricList));
    }

    private int[] scoreSummaryToInts(StatScoreSummary summary) {
        if (summary == null || summary.scoredRows <= 0) return new int[] { 0, 0, 0 };
        return new int[] { (int)Math.round(summary.aPts), (int)Math.round(summary.bPts), 0 };
    }

    private StatScoreSummary summarizeHeadToHeadEdges(HeadToHeadComparison h, ArrayList<Metric> metricList) {
        // v130: keep the math weighted, but expose it as a clearer edge-share visual.
        // This keeps quick-glance behavior while avoiding a misleading row-count scoreboard.
        StatScoreSummary summary = new StatScoreSummary();
        if (metricList == null) return summary;
        for (Metric m : metricList) {
            StatEdgeResult edge = statEdgeForMetric(h, m);
            if (edge == null || !edge.valid) continue;
            summary.displayedRows++;
            if (edge.sampleFlag) summary.sampleAdjustedRows++;
            if (edge.badge != null && edge.badge.contains("S")) summary.sampleBadgeRows++;
            if (!edge.scoring) {
                summary.contextRows++;
                continue;
            }
            summary.scoredRows++;
            if (edge.winner == 0) {
                summary.tossUpRows++;
                summary.aPts += 0.5d;
                summary.bPts += 0.5d;
            } else {
                double winnerShare = 0.5d + 0.5d * Math.max(0d, Math.min(1d, edge.scoreStrength));
                double loserShare = 1d - winnerShare;
                if (edge.winner < 0) {
                    summary.aPts += winnerShare;
                    summary.bPts += loserShare;
                } else {
                    summary.aPts += loserShare;
                    summary.bPts += winnerShare;
                }
            }
        }
        return summary;
    }

    static class StatScoreSummary {
        double aPts = 0d, bPts = 0d;
        int scoredRows = 0, contextRows = 0, displayedRows = 0, sampleAdjustedRows = 0, sampleBadgeRows = 0, tossUpRows = 0;
        int edgePctA() {
            double total = aPts + bPts;
            if (total <= 0d) return 50;
            int pct = (int)Math.round(100d * aPts / total);
            if (pct < 0) pct = 0;
            if (pct > 100) pct = 100;
            return pct;
        }
        int edgePctB() {
            return 100 - edgePctA();
        }
    }

    // returns -1 for left/A, +1 for right/B, 0 for tie/unknown/context
    private int headToHeadWinner(HeadToHeadComparison h, Metric m) {
        if (h == null || m == null || isContextOnlyMetric(m)) return 0;
        Double vA = h.statsA == null ? null : h.statsA.get(m.key);
        Double vB = h.statsB == null ? null : h.statsB.get(m.key);
        if (vA == null || vB == null) return 0;
        if (format(vA, m).equals(format(vB, m))) return 0;
        if (isTargetRangeMetric(m)) {
            double qA = launchAngleQuality(vA);
            double qB = launchAngleQuality(vB);
            if (Math.abs(qA - qB) < 0.000001) return 0;
            return qA > qB ? -1 : 1;
        }
        double d = vA - vB;
        if (Math.abs(d) < 0.000001) return 0;
        if (m.higherGood == null) return 0;
        boolean aLeads = m.higherGood ? d > 0 : d < 0;
        return aLeads ? -1 : 1;
    }

    static class StatEdgeResult {
        boolean valid = false;
        boolean scoring = true;
        boolean tossUp = false;
        boolean volumeSensitive = false;
        boolean sampleFlag = false;
        boolean contextOnly = false;
        boolean targetRange = false;
        int winner = 0; // -1 A, +1 B, 0 toss-up/unknown
        double qualityA = 50d, qualityB = 50d;
        double rawEdge = 0d;
        double adjustedEdge = 0d;
        double reliability = 1d;
        double visualStrength = 0d;
        double glowStrength = 0d;
        double scoreStrength = 0d;
        String badge = "";
    }

    private StatEdgeResult statEdgeForMetric(HeadToHeadComparison h, Metric m) {
        StatEdgeResult out = new StatEdgeResult();
        if (h == null || m == null || h.statsA == null || h.statsB == null) return out;
        Double a = h.statsA.get(m.key);
        Double b = h.statsB.get(m.key);
        if (a == null || b == null || Double.isNaN(a) || Double.isNaN(b)) return out;
        out.valid = true;
        out.volumeSensitive = isVolumeSensitiveMetric(m);
        out.contextOnly = isContextOnlyMetric(m);
        out.targetRange = isTargetRangeMetric(m);
        out.scoring = !out.contextOnly;

        out.qualityA = statQualityScore(h, m, a, true);
        out.qualityB = statQualityScore(h, m, b, false);
        int qualityWinner = Math.abs(out.qualityA - out.qualityB) < 0.0001d ? 0 : (out.qualityA > out.qualityB ? -1 : 1);
        int rawWinner = headToHeadWinner(h, m);
        out.winner = out.contextOnly ? 0 : (out.targetRange ? qualityWinner : (m.isCount() ? rawWinner : qualityWinner));

        double pctEdge = Math.abs(out.qualityA - out.qualityB);
        double statEdge = 100d * statRelativeGapNormForEdge(m, a, b);
        if (m.isCount()) {
            // Counts use absolute, stat-specific thresholds so 2 HR vs 1 HR does not look
            // like a giant 100% win. Percentile still contributes lightly when available.
            out.rawEdge = Math.min(55d, statEdge * 0.72d + pctEdge * 0.28d);
        } else if (out.targetRange) {
            out.rawEdge = pctEdge;
        } else if (out.contextOnly) {
            out.rawEdge = Math.min(35d, statEdge * 0.65d + pctEdge * 0.35d);
        } else {
            // Percentile/z-score style gap is the primary driver; raw stat benchmarks only smooth
            // cases where percentile spacing is sparse or unavailable.
            out.rawEdge = Math.min(100d, pctEdge * 0.82d + statEdge * 0.18d);
        }

        out.reliability = sampleReliabilityForEdge(h, m);
        double opportunityBalance = opportunityBalanceForEdge(h, m);
        // v130: sample adjustment can still soften the math, but the row-level "S"
        // badge is reserved for material caveats. Normal sample adjustment is shown once
        // in the hero instead of repeating on every rate row.
        out.sampleFlag = out.reliability < 0.86d || opportunityBalance < 0.58d;
        boolean showSampleBadge = out.reliability < 0.68d || opportunityBalance < 0.40d;
        out.adjustedEdge = out.rawEdge * out.reliability;

        double deadZone = m.isCount() ? 5.0d : (out.targetRange ? 4.0d : 3.0d);
        if (out.winner == 0 || out.adjustedEdge <= deadZone) {
            out.winner = 0;
            out.tossUp = true;
            out.visualStrength = 0d;
            out.glowStrength = 0d;
            out.scoreStrength = 0d;
        } else {
            double span = m.isCount() ? 42d : 35d;
            double linear = Math.max(0d, Math.min(1d, (out.adjustedEdge - deadZone) / span));
            double eased = 1d - Math.pow(1d - linear, 1.55d);
            if (m.isCount()) eased = Math.min(eased, countStatVisualCap(m, a, b, h));
            if (out.sampleFlag) eased = Math.min(eased, 0.82d);
            if (out.contextOnly) eased = Math.min(eased, 0.52d);
            out.visualStrength = Math.max(0d, Math.min(1d, eased));
            out.glowStrength = out.visualStrength * (0.58d + 0.42d * out.reliability);
            out.scoreStrength = out.contextOnly ? 0d : Math.max(0d, Math.min(1d, 0.20d + 0.80d * out.visualStrength));
        }

        if (out.contextOnly) out.badge = "CTX";
        else if (out.targetRange) out.badge = "TGT";
        else if (out.volumeSensitive) out.badge = "VOL";
        if (showSampleBadge) out.badge = out.badge.isEmpty() ? "S" : out.badge + "/S";
        return out;
    }

    private boolean isContextOnlyMetric(Metric m) {
        return m != null && ("luck".equals(m.key) || "context".equals(m.type));
    }

    private boolean isTargetRangeMetric(Metric m) {
        return m != null && ("avgLA".equals(m.key) || "target".equals(m.type));
    }

    private boolean isVolumeSensitiveMetric(Metric m) {
        if (m == null) return false;
        if (m.isCount()) return true;
        return "ip".equals(m.key);
    }

    private double statQualityScore(HeadToHeadComparison h, Metric m, Double value, boolean sideA) {
        if (value == null || Double.isNaN(value)) return 50d;
        if (isTargetRangeMetric(m)) return launchAngleQuality(value);
        Map<String, Double> map = sideA ? h.percentileA : h.percentileB;
        Double pct = map == null ? null : map.get(m.key);
        if (pct != null && !Double.isNaN(pct)) return clampDouble(pct, 0d, 100d);
        double[] domain = metricScaleDomain(m, new Double[] { value });
        double lo = domain[0], hi = domain[1];
        if (hi <= lo) return 50d;
        double q = 100d * (value - lo) / (hi - lo);
        if (m.higherGood != null && !m.higherGood) q = 100d - q;
        return clampDouble(q, 0d, 100d);
    }

    private double launchAngleQuality(double value) {
        // Higher launch angle is not automatically better. For hitters this uses a broad
        // productive target band around line-drive/fly-ball contact, then eases down outside it.
        double low = 8d, high = 16d;
        if (value >= low && value <= high) return 100d;
        double distance = value < low ? low - value : value - high;
        return clampDouble(100d - distance * 5.0d, 0d, 100d);
    }

    private double sampleReliabilityForEdge(HeadToHeadComparison h, Metric m) {
        if (h == null || m == null || h.isTeam || h.statsA == null || h.statsB == null) return 1d;
        double sampleA = sampleSizeForMetric(h.statsA, m);
        double sampleB = sampleSizeForMetric(h.statsB, m);
        double minSample = Math.max(0d, Math.min(sampleA, sampleB));
        double target = targetSampleForMetric(m);
        double reliability = target <= 0d ? 1d : Math.sqrt(Math.min(1d, minSample / target));
        if (m.isCount() || "ip".equals(m.key)) {
            double balance = opportunityBalanceForEdge(h, m);
            reliability *= (0.72d + 0.28d * balance);
        }
        return clampDouble(reliability, 0.35d, 1d);
    }

    private double opportunityBalanceForEdge(HeadToHeadComparison h, Metric m) {
        if (h == null || m == null || h.statsA == null || h.statsB == null) return 1d;
        double a = sampleSizeForMetric(h.statsA, m);
        double b = sampleSizeForMetric(h.statsB, m);
        double hi = Math.max(a, b);
        double lo = Math.min(a, b);
        if (hi <= 0d) return 1d;
        return clampDouble(lo / hi, 0d, 1d);
    }

    private double sampleSizeForMetric(Stats s, Metric m) {
        if (s == null || m == null) return 0d;
        if ("pitch".equals(m.side)) return s.ip > 0d ? s.ip : s.pa;
        if (isBattedBallSampleMetric(m)) return s.bbe > 0 ? s.bbe : s.pa;
        return s.pa;
    }

    private boolean isBattedBallSampleMetric(Metric m) {
        if (m == null) return false;
        return "contact".equals(m.type) || "avgEV".equals(m.key) || "avgLA".equals(m.key) || "barrelPct".equals(m.key) || "hardHitPct".equals(m.key) || "sweetSpotPct".equals(m.key);
    }

    private double targetSampleForMetric(Metric m) {
        if (m == null) return 1d;
        if ("pitch".equals(m.side)) return (m.isCount() || "ip".equals(m.key)) ? 65d : 50d;
        if (isBattedBallSampleMetric(m)) return 120d;
        return m.isCount() ? 250d : 200d;
    }

    private double countStatVisualCap(Metric m, Double a, Double b, HeadToHeadComparison h) {
        if (m == null || a == null || b == null) return 0.55d;
        double maxValue = Math.max(Math.abs(a), Math.abs(b));
        double gap = Math.abs(a - b);
        double base;
        if (maxValue <= 2d && gap <= 1.1d) base = 0.22d;
        else if (maxValue <= 5d && gap <= 2.1d) base = 0.36d;
        else if (maxValue <= 10d && gap <= 3.1d) base = 0.50d;
        else base = 0.78d;
        double balance = opportunityBalanceForEdge(h, m);
        if (balance < 0.55d) base = Math.min(base, 0.58d);
        return clampDouble(base, 0.18d, 0.82d);
    }

    private double statRelativeGapNormForEdge(Metric m, Double a, Double b) {
        if (m == null || a == null || b == null || Double.isNaN(a) || Double.isNaN(b)) return 0d;
        if (format(a, m).equals(format(b, m))) return 0d;
        double benchmark = meaningfulGapBenchmarkForEdge(m);
        if (benchmark <= 0d) return 0d;
        double gap = Math.abs(a - b);
        double dead = statDeadZoneForEdge(m);
        if (gap <= dead) return 0d;
        double raw = Math.min(1d, (gap - dead) / benchmark);
        return 1d - Math.pow(1d - raw, 1.20d);
    }

    private double statDeadZoneForEdge(Metric m) {
        if (m == null) return 0d;
        if (m.decimals <= 0) return 0.000001d;
        return 0.5d * Math.pow(10d, -m.decimals);
    }

    private double meaningfulGapBenchmarkForEdge(Metric m) {
        if (m == null || m.key == null) return 0.10d;
        switch (m.key) {
            case "avg":
            case "teamAVG": return 0.045d;
            case "obp":
            case "teamOBP": return 0.035d;
            case "slg":
            case "teamSLG": return 0.080d;
            case "ops":
            case "teamOPS": return 0.075d;
            case "iso":
            case "teamISO": return 0.055d;
            case "babip":
            case "teamBABIP": return 0.045d;
            case "wOBA":
            case "teamWOBA": return 0.055d;
            case "xwOBA":
            case "teamXWOBA": return 0.065d;
            case "xBA":
            case "teamXBA": return 0.045d;
            case "xOBP":
            case "teamXOBP": return 0.040d;
            case "xSLG":
            case "teamXSLG": return 0.085d;
            case "xISO":
            case "teamXISO": return 0.060d;
            case "wOBAcon":
            case "xwOBAcon": return 0.075d;
            case "avgEV":
            case "pAvgEV":
            case "teamAvgEV":
            case "teamPAvgEV": return 3.8d;
            case "avgLA": return 10d;
            case "h":
            case "teamHits": return 30d;
            case "doubles":
            case "teamDoubles": return 10d;
            case "triples":
            case "teamTriples": return 4d;
            case "hr":
            case "teamHR": return 12d;
            case "xbh":
            case "teamXbh": return 18d;
            case "rbi":
            case "teamRBI": return 20d;
            case "r": return 20d;
            case "sb":
            case "teamSB": return 10d;
            case "bb":
            case "teamWalks": return 18d;
            case "so":
            case "teamStrikeouts": return 25d;
            case "tb":
            case "teamTB": return 55d;
            case "bbPct":
            case "kPct":
            case "bbMinusKPct":
            case "pitchKPct":
            case "pitchBBPct":
            case "pitchKMinusBBPct":
            case "barrelPct":
            case "hardHitPct":
            case "sweetSpotPct":
            case "whiffPct":
            case "chasePct":
            case "zoneContactPct":
            case "pWhiffPct":
            case "pChasePct":
            case "pFirstStrikePct":
            case "pZonePct":
            case "pBarrelPct":
            case "pHardHitPct":
            case "pGbPct":
            case "pFbPct":
            case "pLdPct":
            case "teamKPct":
            case "teamBBPct":
            case "teamBBMinusKPct":
            case "teamWhiffPct":
            case "teamSwingPct":
            case "teamChasePct":
            case "teamZoneContactPct":
            case "teamBarrelPct":
            case "teamHardHitPct":
            case "teamSweetSpotPct":
            case "teamGbPct":
            case "teamFbPct":
            case "teamLdPct":
            case "teamPullPct":
            case "teamOppoPct":
            case "teamPitchKPct":
            case "teamPitchBBPct":
            case "teamPitchKMinusBBPct":
            case "teamPWhiffPct":
            case "teamPChasePct":
            case "teamPFirstStrikePct":
            case "teamPZonePct":
            case "teamPBarrelPct":
            case "teamPHardHitPct":
            case "teamPGbPct":
            case "teamPFbPct":
            case "teamPLdPct": return 5d;
            case "era":
            case "teamERA": return 1.35d;
            case "whip":
            case "teamWHIP": return 0.38d;
            case "k9":
            case "teamK9": return 4.00d;
            case "bb9":
            case "teamBB9": return 1.75d;
            case "kbb":
            case "teamKBB": return 1.50d;
            case "pOppAvg":
            case "teamOppAvg":
            case "pxBA":
            case "teamPXBA": return 0.045d;
            case "pOppOps":
            case "teamOppOps": return 0.075d;
            case "pxSLG":
            case "teamPXSLG": return 0.085d;
            case "pwOBA":
            case "pxwOBA":
            case "teamPWOBA":
            case "teamPXWOBA": return 0.060d;
            case "ip": return 45d;
            case "pitchK":
            case "teamPitchStrikeouts": return 40d;
            case "pitchBB":
            case "teamWalksAllowed": return 12d;
            case "pHitsAllowed":
            case "teamHitsAllowed": return 30d;
            case "pHrAllowed":
            case "teamHrAllowed": return 12d;
            case "saves": return 10d;
            case "teamWinPct": return 0.080d;
            case "teamRunsScored":
            case "teamRunsAllowed": return 40d;
            case "teamRunDiff": return 65d;
            case "teamRPG":
            case "teamRAPG": return 0.45d;
            default: return m.isCount() ? 10d : 0.10d;
        }
    }

    private double clampDouble(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    private int darkColor(int color, float amountBlack) {
        int r = Math.round(Color.red(color) * (1f - amountBlack));
        int g = Math.round(Color.green(color) * (1f - amountBlack));
        int b = Math.round(Color.blue(color) * (1f - amountBlack));
        return Color.rgb(Math.max(0, r), Math.max(0, g), Math.max(0, b));
    }

    private void applyRoundedClip(View v, int radiusDp) {
        if (Build.VERSION.SDK_INT >= 21) {
            final float radius = dp(radiusDp);
            v.setClipToOutline(true);
            v.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
        }
    }

    private View sharePlayerTile(HeadToHeadComparison h, boolean left, TeamPalette palette, boolean onDark) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(left ? Gravity.LEFT : Gravity.RIGHT);
        tile.setPadding(dp(2), 0, dp(2), 0);

        FrameLayout outer = new FrameLayout(this);
        outer.setPadding(dp(4), dp(4), dp(4), dp(4));
        outer.setBackground(roundedGradient(new int[] { palette.primary, softColor(palette.secondary, 0.12f) }, 28));
        outer.setElevation(dp(3));

        FrameLayout matte = new FrameLayout(this);
        matte.setPadding(dp(3), dp(3), dp(3), dp(3));
        matte.setBackground(rounded(Color.argb(248, 255, 255, 255), 24));
        applyRoundedClip(matte, 24);
        outer.addView(matte, new FrameLayout.LayoutParams(-1, -1));

        if (h.isTeam) {
            View logo = teamLogoView(left ? h.teamA : h.teamB, 56);
            FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(-2, -2);
            logoLp.gravity = Gravity.CENTER;
            matte.addView(logo, logoLp);
        } else {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setAdjustViewBounds(false);
            img.setBackground(rounded(Color.WHITE, 22));
            applyRoundedClip(img, 22);
            matte.addView(img, new FrameLayout.LayoutParams(-1, -1));
            loadPlayerImage(left ? h.idA : h.idB, img);
        }

        LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(dp(76), dp(76));
        frameLp.gravity = left ? Gravity.LEFT : Gravity.RIGHT;
        tile.addView(outer, frameLp);

        int nameColor = onDark ? Color.WHITE : INK;
        int metaColor = onDark ? Color.rgb(225, 234, 246) : Color.rgb(98, 110, 130);
        TextView name = text(twoLineName(left ? h.nameA : h.nameB), 16, nameColor, true);
        name.setPadding(0, dp(8), 0, 0);
        name.setGravity(left ? Gravity.LEFT : Gravity.RIGHT);
        name.setMaxLines(2);
        tile.addView(name, matchWrap());
        TextView meta = text(left ? h.metaA : h.metaB, 10, metaColor, false);
        meta.setGravity(left ? Gravity.LEFT : Gravity.RIGHT);
        meta.setPadding(0, dp(3), 0, 0);
        tile.addView(meta, matchWrap());
        return tile;
    }

    private String twoLineName(String name) {
        String s = safe(name).trim();
        String[] parts = s.split("\\s+");
        if (parts.length >= 3 && s.length() > 15) return parts[0] + "\n" + s.substring(parts[0].length()).trim();
        if (parts.length == 2 && s.length() > 14) return parts[0] + "\n" + parts[1];
        return s;
    }

    private LinearLayout shareScoreBlock(String name, String wins, int color, boolean active, boolean onDark) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(4), dp(2), dp(4), dp(2));

        TextView num = text(wins, active ? 32 : 24, active ? Color.WHITE : Color.rgb(225, 233, 244), true);
        num.setGravity(Gravity.CENTER);
        num.setFontFeatureSettings("'tnum' 1");
        if (active) {
            num.setPadding(dp(12), dp(5), dp(12), dp(5));
            num.setBackground(roundedGradient(new int[] { color, softColor(color, 0.12f) }, 18));
        }
        box.addView(num, matchWrap());

        TextView label = text(name, 9, onDark ? Color.rgb(214, 224, 239) : Color.rgb(108, 119, 138), true);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setPadding(0, dp(4), 0, 0);
        box.addView(label, matchWrap());
        return box;
    }

    private View shareMetricRow(HeadToHeadComparison h, Metric m, TeamPalette paletteA, TeamPalette paletteB) {
        Double a = h.statsA.get(m.key);
        Double b = h.statsB.get(m.key);
        int winner = headToHeadWinner(h, m);
        int winnerColor = winner < 0 ? paletteA.primary : (winner > 0 ? paletteB.primary : Color.rgb(112, 122, 138));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(11), dp(9), dp(11), dp(9));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(5), 0, 0);
        row.setLayoutParams(lp);
        row.setBackground(roundedStroke(Color.rgb(252, 253, 255), Color.rgb(229, 235, 245), 18, 1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView leftValue = shareMetricValue(format(a, m), winner < 0 ? paletteA.primary : Color.rgb(42, 49, 63), winner < 0, Gravity.LEFT);
        top.addView(leftValue, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        TextView label = text(m.label.toUpperCase(Locale.US), 11, Color.rgb(54, 63, 78), true);
        label.setGravity(Gravity.CENTER);
        label.setLetterSpacing(0.08f);
        center.addView(label, matchWrap());
        TextView edge = text(winner == 0 ? "EVEN" : (winner < 0 ? shortName(h.nameA).toUpperCase(Locale.US) : shortName(h.nameB).toUpperCase(Locale.US)), 8, winnerColor, true);
        edge.setGravity(Gravity.CENTER);
        edge.setSingleLine(true);
        center.addView(edge, matchWrap());
        top.addView(center, new LinearLayout.LayoutParams(0, -2, 0.9f));

        TextView rightValue = shareMetricValue(format(b, m), winner > 0 ? paletteB.primary : Color.rgb(42, 49, 63), winner > 0, Gravity.RIGHT);
        top.addView(rightValue, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(top, matchWrap());

        View rail = new View(this);
        int[] railColors;
        if (winner < 0) {
            railColors = new int[] { softColor(paletteA.primary, 0.18f), softColor(paletteA.primary, 0.62f), Color.rgb(226, 232, 242), softColor(paletteB.primary, 0.78f) };
        } else if (winner > 0) {
            railColors = new int[] { softColor(paletteA.primary, 0.78f), Color.rgb(226, 232, 242), softColor(paletteB.primary, 0.60f), softColor(paletteB.primary, 0.18f) };
        } else {
            railColors = new int[] { softColor(paletteA.primary, 0.70f), Color.rgb(226, 232, 242), softColor(paletteB.primary, 0.70f) };
        }
        rail.setBackground(roundedGradient(railColors, 6));
        LinearLayout.LayoutParams railLp = new LinearLayout.LayoutParams(-1, dp(6));
        railLp.setMargins(0, dp(8), 0, 0);
        row.addView(rail, railLp);
        return row;
    }

    private TextView shareMetricValue(String value, int color, boolean winner, int gravity) {
        TextView tv = text(value, winner ? 20 : 18, color, true);
        tv.setGravity(gravity | Gravity.CENTER_VERTICAL);
        tv.setFontFeatureSettings("'tnum' 1");
        tv.setSingleLine(true);
        tv.setPadding(winner ? dp(9) : 0, winner ? dp(5) : 0, winner ? dp(9) : 0, winner ? dp(5) : 0);
        if (winner) tv.setBackground(roundedStroke(softColor(color, 0.90f), softColor(color, 0.62f), 15, 1));
        return tv;
    }

    private View categoryPill(String label, String winner, TeamPalette paletteA, TeamPalette paletteB) {
        int color = NAVY;
        if (winner.startsWith("A:")) { color = paletteA.primary; winner = winner.substring(2); }
        else if (winner.startsWith("B:")) { color = paletteB.primary; winner = winner.substring(2); }
        else if (winner.startsWith("T:")) { winner = winner.substring(2); }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(5), dp(8), dp(5), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(2), 0, dp(2), 0);
        box.setLayoutParams(lp);
        box.setBackground(roundedStroke(softColor(color, 0.93f), softColor(color, 0.65f), 15, 1));
        TextView top = text(label.toUpperCase(Locale.US), 8, Color.rgb(112, 124, 144), true);
        top.setGravity(Gravity.CENTER);
        top.setLetterSpacing(0.05f);
        box.addView(top, matchWrap());
        TextView bottom = text(winner, 10, color, true);
        bottom.setGravity(Gravity.CENTER);
        bottom.setSingleLine(true);
        bottom.setPadding(0, dp(2), 0, 0);
        box.addView(bottom, matchWrap());
        return box;
    }

    private String categoryWinner(HeadToHeadComparison h, String[] keys) {
        int a = 0, b = 0;
        for (String key : keys) {
            Metric m = findMetricByKey(key);
            if (m == null) continue;
            if (!selectedMetricKeys.contains(m.key)) continue;
            int w = headToHeadWinner(h, m);
            if (w < 0) a++; else if (w > 0) b++;
        }
        if (a == 0 && b == 0) return "T:—";
        if (a == b) return "T:Split";
        return (a > b ? "A:" + shortName(h.nameA) : "B:" + shortName(h.nameB));
    }

    // v30: Face-off layout for H2H stat cards — value+tier columns flanking the bar, gap significance footer
    private void renderHeadToHeadMetricRow(HeadToHeadComparison h, Metric m, TeamPalette paletteA, TeamPalette paletteB) {
        Double valueA = h.statsA.get(m.key);
        Double valueB = h.statsB.get(m.key);
        Double leagueValue = h.leagueStats == null ? null : h.leagueStats.get(m.key);
        Double delta = diff(valueA, valueB);
        // v128: Stats Control owns the lower list. Render every selected row even
        // when a data source did not return values, using em dashes instead of
        // hiding the row and making it look like the selection was dropped.

        Integer rankA = h.rankA == null ? null : h.rankA.get(m.key);
        Integer rankB = h.rankB == null ? null : h.rankB.get(m.key);
        Integer totalA = h.rankTotalA == null ? null : h.rankTotalA.get(m.key);
        Integer totalB = h.rankTotalB == null ? null : h.rankTotalB.get(m.key);
        Double pctA = h.percentileA == null ? null : h.percentileA.get(m.key);
        Double pctB = h.percentileB == null ? null : h.percentileB.get(m.key);

        boolean aLeads = false, bLeads = false;
        String leader = isContextOnlyMetric(m) ? "Context" : "Even";
        int accentA = boostNeonColor(readableTeamColor(paletteA.primary, paletteA.secondary, true), 1.14f, 1.05f);
        int accentB = boostNeonColor(readableTeamColor(paletteB.primary, paletteB.secondary, false), 1.14f, 1.05f);
        int leaderColor = Color.rgb(116, 130, 154);
        StatEdgeResult rowEdge = statEdgeForMetric(h, m);
        if (rowEdge != null && rowEdge.valid && !rowEdge.contextOnly && rowEdge.winner != 0) {
            aLeads = rowEdge.winner < 0;
            bLeads = rowEdge.winner > 0;
            leader = shortName(aLeads ? h.nameA : h.nameB) + " leads";
            leaderColor = aLeads ? accentA : accentB;
        }

        int shellTintA = mixColor(accentA, Color.rgb(4, 8, 16), 0.95f);
        int shellTintB = mixColor(accentB, Color.rgb(4, 8, 16), 0.95f);
        int shellTintMid = Color.rgb(4, 8, 15);
        int shellBorder = Color.argb(74,
                Color.red(mixColor(accentA, accentB, 0.50f)),
                Color.green(mixColor(accentA, accentB, 0.50f)),
                Color.blue(mixColor(accentA, accentB, 0.50f)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(13));
        LinearLayout.LayoutParams rowLp = matchWrap();
        rowLp.setMargins(0, dp(7), 0, 0);
        row.setBackground(roundedGradientStroke(new int[] {
                shellTintA,
                shellTintMid,
                shellTintB
        }, 22, shellBorder, 1));
        metricBox.addView(row, rowLp);

        View topSheen = new View(this);
        topSheen.setBackground(roundedGradient(new int[] {
                Color.argb(0, 255, 255, 255),
                Color.argb(62, Color.red(mixColor(accentA, accentB, 0.50f)), Color.green(mixColor(accentA, accentB, 0.50f)), Color.blue(mixColor(accentA, accentB, 0.50f))),
                Color.argb(0, 255, 255, 255)
        }, 999));
        LinearLayout.LayoutParams sheenLp = new LinearLayout.LayoutParams(-1, dp(2));
        sheenLp.setMargins(dp(2), 0, dp(2), dp(10));
        row.addView(topSheen, sheenLp);

        // ── Top: metric label + leader badge ────────────────────────────────
        LinearLayout topLine = new LinearLayout(this);
        topLine.setOrientation(LinearLayout.HORIZONTAL);
        topLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView metricTitle = text(m.label, 16, Color.rgb(239, 244, 251), true);
        metricTitle.setLetterSpacing(0.01f);
        topLine.addView(metricTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView leaderBadge = text(leader, 11, Color.WHITE, true);
        leaderBadge.setGravity(Gravity.CENTER);
        leaderBadge.setPadding(dp(11), dp(5), dp(11), dp(5));
        leaderBadge.setBackground(roundedGradientStroke(new int[] {
                mixColor(leaderColor, Color.rgb(8, 13, 22), 0.18f),
                mixColor(leaderColor, Color.rgb(8, 13, 22), 0.34f),
                Color.rgb(8, 13, 22)
        }, 15, Color.argb(108, Color.red(leaderColor), Color.green(leaderColor), Color.blue(leaderColor)), 1));
        topLine.addView(leaderBadge);
        row.addView(topLine, matchWrap());

        // ── Face-off: Player A | vs | Player B ──────────────────────────────
        LinearLayout faceOff = new LinearLayout(this);
        faceOff.setOrientation(LinearLayout.HORIZONTAL);
        faceOff.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams foLp = matchWrap();
        foLp.setMargins(0, dp(11), 0, dp(8));

        int panelADark = aLeads ? mixColor(accentA, Color.rgb(8, 14, 24), 0.76f) : mixColor(accentA, Color.rgb(8, 14, 24), 0.92f);
        int panelBDark = bLeads ? mixColor(accentB, Color.rgb(8, 14, 24), 0.76f) : mixColor(accentB, Color.rgb(8, 14, 24), 0.92f);

        // Side A box
        LinearLayout sideA = new LinearLayout(this);
        sideA.setOrientation(LinearLayout.VERTICAL);
        sideA.setGravity(Gravity.CENTER);
        sideA.setPadding(dp(10), dp(10), dp(10), dp(10));
        sideA.setBackground(roundedGradientStroke(new int[] {
                mixColor(panelADark, Color.WHITE, 0.05f),
                panelADark,
                mixColor(accentA, Color.rgb(8, 14, 24), aLeads ? 0.82f : 0.90f)
        }, 16,
                Color.argb(aLeads ? 118 : 54, Color.red(accentA), Color.green(accentA), Color.blue(accentA)), 1));
        View sideASheen = new View(this);
        sideASheen.setBackground(roundedGradient(new int[] {
                Color.argb(aLeads ? 22 : 10, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
        }, 999));
        LinearLayout.LayoutParams sideSheenLp = new LinearLayout.LayoutParams(-1, dp(2));
        sideSheenLp.setMargins(dp(3), 0, dp(3), dp(8));
        sideA.addView(sideASheen, sideSheenLp);
        TextView valA = text(valueA == null ? "—" : format(valueA, m), aLeads ? 22 : 20, aLeads ? softColor(accentA, 0.10f) : Color.rgb(226, 234, 245), true);
        valA.setGravity(Gravity.CENTER);
        valA.setFontFeatureSettings("'tnum' 1");
        sideA.addView(valA, matchWrap());
        TextView ctxA = text(leagueContextLabel(pctA, m), 9, aLeads ? softColor(accentA, 0.18f) : Color.rgb(142, 154, 174), true);
        ctxA.setGravity(Gravity.CENTER);
        ctxA.setPadding(0, dp(2), 0, 0);
        sideA.addView(ctxA, matchWrap());
        faceOff.addView(sideA, new LinearLayout.LayoutParams(0, -2, 1));

        // Center "vs"
        TextView vsTv = text("vs", 10, Color.rgb(138, 150, 170), true);
        vsTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vsLp = new LinearLayout.LayoutParams(dp(30), -2);
        vsLp.gravity = Gravity.CENTER_VERTICAL;
        faceOff.addView(vsTv, vsLp);

        // Side B box
        LinearLayout sideB = new LinearLayout(this);
        sideB.setOrientation(LinearLayout.VERTICAL);
        sideB.setGravity(Gravity.CENTER);
        sideB.setPadding(dp(10), dp(10), dp(10), dp(10));
        sideB.setBackground(roundedGradientStroke(new int[] {
                mixColor(panelBDark, Color.WHITE, 0.05f),
                panelBDark,
                mixColor(accentB, Color.rgb(8, 14, 24), bLeads ? 0.82f : 0.90f)
        }, 16,
                Color.argb(bLeads ? 118 : 54, Color.red(accentB), Color.green(accentB), Color.blue(accentB)), 1));
        View sideBSheen = new View(this);
        sideBSheen.setBackground(roundedGradient(new int[] {
                Color.argb(bLeads ? 22 : 10, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
        }, 999));
        LinearLayout.LayoutParams sideSheenLpB = new LinearLayout.LayoutParams(-1, dp(2));
        sideSheenLpB.setMargins(dp(3), 0, dp(3), dp(8));
        sideB.addView(sideBSheen, sideSheenLpB);
        TextView valB = text(valueB == null ? "—" : format(valueB, m), bLeads ? 22 : 20, bLeads ? softColor(accentB, 0.10f) : Color.rgb(226, 234, 245), true);
        valB.setGravity(Gravity.CENTER);
        valB.setFontFeatureSettings("'tnum' 1");
        sideB.addView(valB, matchWrap());
        TextView ctxB = text(leagueContextLabel(pctB, m), 9, bLeads ? softColor(accentB, 0.18f) : Color.rgb(142, 154, 174), true);
        ctxB.setGravity(Gravity.CENTER);
        ctxB.setPadding(0, dp(2), 0, 0);
        sideB.addView(ctxB, matchWrap());
        faceOff.addView(sideB, new LinearLayout.LayoutParams(0, -2, 1));

        row.addView(faceOff, foLp);

        // ── Percentile bar ───────────────────────────────────────────────────
        HeadToHeadBarView bar = new HeadToHeadBarView(this, m,
                new Double[] { valueA, valueB, leagueValue },
                new Double[] { pctA, pctB, 50.0 },
                paletteA, paletteB, h.nameA, h.nameB);
        FrameLayout barShell = new FrameLayout(this);
        barShell.setPadding(dp(8), dp(6), dp(8), dp(6));
        barShell.setBackground(roundedGradientStroke(new int[] {
                Color.rgb(4, 8, 15),
                mixColor(accentA, Color.rgb(4, 8, 15), 0.94f),
                mixColor(accentB, Color.rgb(4, 8, 15), 0.94f)
        }, 18, Color.argb(34, 255, 255, 255), 1));
        View barSheen = new View(this);
        barSheen.setBackground(roundedGradient(new int[] {
                Color.argb(12, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
        }, 18));
        FrameLayout.LayoutParams barSheenLp = new FrameLayout.LayoutParams(-1, dp(16));
        barSheenLp.gravity = Gravity.TOP;
        barShell.addView(barSheen, barSheenLp);
        FrameLayout.LayoutParams barInnerLp = new FrameLayout.LayoutParams(-1, dp(64));
        barInnerLp.gravity = Gravity.CENTER;
        barShell.addView(bar, barInnerLp);
        LinearLayout.LayoutParams barShellLp = new LinearLayout.LayoutParams(-1, -2);
        barShellLp.setMargins(0, 0, 0, 0);
        row.addView(barShell, barShellLp);
        if (h.isTeam) {
            loadTeamLogoBitmap(h.teamA, bar::setIconA);
            loadTeamLogoBitmap(h.teamB, bar::setIconB);
        } else {
            loadPlayerImageBitmap(h.idA, bar::setIconA);
            loadPlayerImageBitmap(h.idB, bar::setIconB);
        }

        View divider = new View(this);
        divider.setBackground(roundedGradient(new int[] {
                Color.argb(0, 255, 255, 255),
                Color.argb(28, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
        }, 999));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
        dividerLp.setMargins(dp(2), dp(8), dp(2), dp(7));
        row.addView(divider, dividerLp);

        // ── League-rank footer. v137 removes the raw matchup-difference text to keep
        // side-by-side rows focused on values, winner, and league context.
        LinearLayout gapRow = new LinearLayout(this);
        gapRow.setOrientation(LinearLayout.HORIZONTAL);
        gapRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams gapLp = matchWrap();
        gapRow.setPadding(dp(2), 0, dp(2), 0);

        TextView rankTvA = text(displayRankLabel(rankA, totalA, h.isTeam), 10, softColor(accentA, 0.14f), true);
        rankTvA.setFontFeatureSettings("'tnum' 1");
        gapRow.addView(rankTvA, new LinearLayout.LayoutParams(0, -2, 1));

        TextView rankMid = text(rankTypeLabel(h.isTeam), 10, Color.rgb(132, 146, 166), false);
        rankMid.setGravity(Gravity.CENTER);
        gapRow.addView(rankMid, new LinearLayout.LayoutParams(0, -2, 1));

        TextView rankTvB = text(displayRankLabel(rankB, totalB, h.isTeam), 10, softColor(accentB, 0.14f), true);
        rankTvB.setGravity(Gravity.RIGHT);
        rankTvB.setFontFeatureSettings("'tnum' 1");
        gapRow.addView(rankTvB, new LinearLayout.LayoutParams(0, -2, 1));

        row.addView(gapRow, gapLp);
    }

    private TeamPalette paletteForHeadToHeadSide(HeadToHeadComparison h, boolean leftSide) {
        if (h == null) return defaultPalette();
        if (h.isTeam) return paletteForTeam(leftSide ? h.teamA : h.teamB);
        Player p = leftSide ? h.playerA : h.playerB;
        return paletteForAbbr(p == null ? "" : p.teamAbbr);
    }

    private int deltaColorForHeadToHead(Double delta, Metric m, TeamPalette paletteA, TeamPalette paletteB) {
        if (delta == null || delta == 0) return MUTED;
        boolean leftBetter = delta > 0;
        if (m.higherGood != null && !m.higherGood) leftBetter = delta < 0;
        return leftBetter ? paletteA.primary : paletteB.primary;
    }

    private String[] teamDisplayNameParts(String name) {
        String s = safe(name).trim();
        if (s.isEmpty()) return new String[] { "", "" };
        String[] nicknames = new String[] {
                "Diamondbacks", "White Sox", "Red Sox", "Blue Jays", "Yankees", "Mets",
                "Dodgers", "Angels", "Padres", "Giants", "Athletics", "Braves", "Orioles",
                "Cubs", "Reds", "Guardians", "Rockies", "Tigers", "Astros", "Royals",
                "Marlins", "Brewers", "Twins", "Phillies", "Pirates", "Mariners",
                "Cardinals", "Rays", "Rangers", "Nationals"
        };
        String upper = s.toUpperCase(Locale.US);
        for (String nick : nicknames) {
            String suffix = " " + nick.toUpperCase(Locale.US);
            if (upper.endsWith(suffix)) {
                String city = s.substring(0, s.length() - nick.length()).trim();
                return new String[] { city, nick };
            }
            if (upper.equals(nick.toUpperCase(Locale.US))) return new String[] { "", nick };
        }
        String[] parts = s.split("\\s+");
        if (parts.length >= 2) {
            String nick = parts[parts.length - 1];
            String city = s.substring(0, s.length() - nick.length()).trim();
            return new String[] { city, nick };
        }
        return new String[] { "", s };
    }

    private String shortName(String name) {
        String s = safe(name).trim();
        if (s.length() <= 12) return s;
        String[] parts = s.split("\\s+");
        if (parts.length >= 2) return parts[parts.length - 1];
        return s.substring(0, 12);
    }


    private boolean hasHeadToHeadMetricValue(HeadToHeadComparison h, Metric m) {
        if (h == null || m == null) return false;
        // Scores should only count comparable rows. Missing one/both sides may
        // still render below the card, but should not become fake ties.
        return h.statsA != null && h.statsB != null && h.statsA.get(m.key) != null && h.statsB.get(m.key) != null;
    }

    private boolean hasComparisonMetricValue(Comparison c, Metric m) {
        if (c == null || m == null) return false;
        return (c.seasonStats != null && c.seasonStats.get(m.key) != null)
                || (c.leagueStats != null && c.leagueStats.get(m.key) != null)
                || (c.careerStats != null && c.careerStats.get(m.key) != null);
    }

    private View statGroupSeparator(String label, int accent) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setPadding(dp(4), dp(10), dp(4), dp(4));

        View lineL = new View(this);
        lineL.setBackground(roundedGradient(new int[] {
                Color.argb(0, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.argb(82, Color.red(accent), Color.green(accent), Color.blue(accent))
        }, 999));
        wrap.addView(lineL, new LinearLayout.LayoutParams(0, dp(2), 1));

        TextView chip = text(label.toUpperCase(Locale.US), 10, Color.rgb(222, 233, 246), true);
        chip.setGravity(Gravity.CENTER);
        chip.setLetterSpacing(0.14f);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        chip.setBackground(roundedGradientStroke(new int[] {
                Color.rgb(6, 10, 18),
                mixColor(boostNeonColor(accent, 1.06f, 1.03f), Color.rgb(6, 10, 18), 0.72f),
                Color.rgb(6, 10, 18)
        }, 14, Color.argb(92, Color.red(accent), Color.green(accent), Color.blue(accent)), 1));
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
        chipLp.setMargins(dp(8), 0, dp(8), 0);
        wrap.addView(chip, chipLp);

        View lineR = new View(this);
        lineR.setBackground(roundedGradient(new int[] {
                Color.argb(82, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.argb(0, Color.red(accent), Color.green(accent), Color.blue(accent))
        }, 999));
        wrap.addView(lineR, new LinearLayout.LayoutParams(0, dp(2), 1));
        return wrap;
    }

    private String metricSectionLabel(Metric m) {
        if (m == null) return "Stats";
        if ("team".equals(m.side)) {
            String g = safe(m.group).toLowerCase(Locale.US);
            if (g.contains("result")) return "Team Results";
            if (g.contains("pitch") || g.contains("allowed")) return "Team Pitching";
            if (g.contains("discipline")) return "Team Discipline";
            if (g.contains("contact") || g.contains("batted")) return "Team Contact";
            return "Team Offense";
        }
        return "pitch".equals(m.side) ? "Pitching" : "Offense";
    }

    private void refreshCurrentResults() {
        if (resultsBox.getVisibility() != View.VISIBLE) return;

        // v121: stats selection changes must rebuild the underlying comparison data,
        // not just re-render the cached screen. The cached object may have been built
        // from a hitter-only or pitcher-only leaderboard, which is why newly-selected
        // pitching stats could never appear on the matchup page.
        if (lastHeadToHead != null) {
            HeadToHeadComparison h = lastHeadToHead;
            if (h.isTeam && h.teamA != null && h.teamB != null) {
                compareTeamsSideBySide(h.teamA, h.teamB, currentSeason());
                return;
            }
            if (!h.isTeam && h.playerA != null && h.playerB != null) {
                comparePlayersSideBySide(h.playerA, h.playerB, currentSeason());
                return;
            }
            renderHeadToHead(h);
            return;
        }

        if (lastComparison != null) {
            Comparison c = lastComparison;
            if (c.isTeam && c.team != null) {
                compareTeam(c.team, currentSeason());
                return;
            }
            if (!c.isTeam && c.player != null) {
                comparePlayer(c.player, currentSeason());
                return;
            }
            if (expectedMode) renderExpectedComparison(c); else renderComparison(c);
        }
    }

    private TextView summaryPill(String label, String value) {
        TextView tv = text(label + "\n" + value, 12, INK, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(6), dp(9), dp(6), dp(9));
        tv.setBackground(roundedStroke(Color.rgb(248, 250, 253), LINE, 16, 1));
        tv.setLineSpacing(dp(2), 1.0f);
        return tv;
    }

    private TextView profileDataPill(String value, String label, TeamPalette palette) {
        TextView tv = text(label + "\n" + value, 11, Color.WHITE, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(7), dp(6), dp(7), dp(6));
        tv.setLineSpacing(dp(1), 1.0f);
        tv.setBackground(roundedStroke(Color.argb(176, 15, 20, 28), Color.argb(82, 255, 255, 255), 14, 1));
        tv.setFontFeatureSettings("'tnum' 1");
        return tv;
    }

    private TextView smallDataPill(String value) {
        TextView tv = text(value, 11, Color.rgb(64, 75, 94), true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(6), dp(4), dp(6), dp(4));
        tv.setBackground(roundedStroke(Color.WHITE, Color.rgb(222, 231, 242), 12, 1));
        tv.setFontFeatureSettings("'tnum' 1");  // v29: tabular figures for count pills
        return tv;
    }

    private TextView insightTile(String label, String value, int accent) {
        TextView tv = text(label + "\n" + value, 12, accent, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(6), dp(8), dp(6), dp(8));
        tv.setBackground(roundedStroke(Color.WHITE, Color.rgb(224, 232, 243), 16, 1));
        tv.setLineSpacing(dp(2), 1.0f);
        return tv;
    }

    private String keyStatSummary(Stats s) {
        if (s == null) return "—";
        Double w = s.get("wOBA");
        Double x = s.get("xwOBA");
        Double l = s.get("luck");
        String luckText = l == null ? "" : " · Luck " + new DecimalFormat("+0.000;-0.000").format(l);
        return "wOBA " + format(w, metricByKey("wOBA")) + "\nxwOBA " + format(x, metricByKey("xwOBA")) + luckText;
    }

    private Metric metricByKey(String key) {
        for (Metric m : metrics) if (m.key.equals(key)) return m;
        return metrics[0];
    }

    private Metric findMetricByKey(String key) {
        for (Metric m : metrics) if (m.key.equals(key)) return m;
        return null;
    }


    private View comparisonLegend(String thirdLabelShort, TeamPalette palette) {
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        legend.addView(legendMini("Selected", palette.primary));
        legend.addView(legendMini("League", Color.rgb(70, 88, 125)));
        legend.addView(legendMini(thirdLabelShort, palette.secondary));
        return legend;
    }


    private TextView legendMini(String label, int color) {
        TextView tv = text("● " + label, 9, color, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(3), dp(2), dp(3), dp(2));
        return tv;
    }


    private void renderMetricRow(Comparison c, Metric m, TeamPalette palette) {
        Double seasonValue = c.seasonStats.get(m.key);
        Double leagueValue = c.leagueStats == null ? null : c.leagueStats.get(m.key);
        Double careerValue = c.careerStats.get(m.key);
        if (seasonValue == null && leagueValue == null && careerValue == null) return;
        Double vsLeague = diff(seasonValue, leagueValue);

        LinearLayout row = verticalCard(20, null);
        row.setPadding(dp(14), dp(12), dp(14), dp(13));
        int rowAccent = boostNeonColor(readableTeamColor(palette.primary, palette.secondary, true), 1.12f, 1.05f);
        row.setBackground(roundedGradientStroke(new int[] {
                Color.rgb(4, 8, 15),
                mixColor(rowAccent, Color.rgb(6, 10, 18), 0.90f),
                Color.rgb(5, 10, 18)
        }, 22, Color.argb(78, Color.red(rowAccent), Color.green(rowAccent), Color.blue(rowAccent)), 1));
        LinearLayout.LayoutParams rowLp = matchWrap();
        rowLp.setMargins(0, dp(7), 0, 0);
        metricBox.addView(row, rowLp);

        Integer rank = c.rank == null ? null : c.rank.get(m.key);
        Integer total = c.rankTotal == null ? null : c.rankTotal.get(m.key);
        Double pct = c.percentile == null ? null : c.percentile.get(m.key);
        Double thirdPct = c.thirdPercentile == null ? null : c.thirdPercentile.get(m.key);

        LinearLayout topLine = new LinearLayout(this);
        topLine.setOrientation(LinearLayout.HORIZONTAL);
        topLine.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout metricTitle = new LinearLayout(this);
        metricTitle.setOrientation(LinearLayout.HORIZONTAL);
        metricTitle.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(m.label, 16, Color.rgb(235, 242, 250), true);
        metricTitle.addView(label);
        String desc = metricDescription(m.key);
        if (!desc.isEmpty()) {
            TextView info = text("i", 9, MUTED, true);
            info.setGravity(Gravity.CENTER);
            info.setPadding(dp(5), 0, dp(5), 0);
            info.setBackground(roundedStroke(Color.argb(24, 255, 255, 255), Color.argb(66, 255, 255, 255), 9, 1));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(18), dp(18));
            ip.setMargins(dp(6), 0, 0, 0);
            metricTitle.addView(info, ip);
            info.setOnClickListener(v -> showMetricGlossary(m));
            info.setForeground(ripple(false));
        }
        topLine.addView(metricTitle, new LinearLayout.LayoutParams(0, -2, 1));

        TextView pctBadge = text(pct == null ? "No percentile" : percentileLabel(pct), 11, Color.WHITE, true);
        pctBadge.setGravity(Gravity.CENTER);
        pctBadge.setPadding(dp(9), dp(5), dp(9), dp(5));
        pctBadge.setBackground(roundedGradientStroke(new int[] {
                mixColor(rowAccent, Color.rgb(8, 13, 22), 0.34f),
                mixColor(rowAccent, Color.rgb(8, 13, 22), 0.58f)
        }, 15, Color.argb(118, Color.red(rowAccent), Color.green(rowAccent), Color.blue(rowAccent)), 1));
        topLine.addView(pctBadge);
        row.addView(topLine);

        String rankLabel = rankTypeLabel(c.isTeam);
        String rankLine = rank == null ? rankLabel + " unavailable for this stat" : rankLabel + ": " + displayRankLabel(rank, total, c.isTeam);
        String leagueDelta = leagueDeltaLabel(seasonValue, leagueValue, m);
        if (!c.isTeam && !leagueDelta.isEmpty()) rankLine += " · " + leagueDelta;
        TextView rankTv = text(rankLine, 11, Color.rgb(146, 160, 182), false);
        rankTv.setPadding(0, dp(5), 0, 0);
        row.addView(rankTv);

        View bar;
        if (!c.isTeam) {
            bar = new LeagueSparkBarView(this, m, seasonValue, leagueValue, pct, palette);
        } else {
            ComparisonBarView comparisonBar = new ComparisonBarView(this, m,
                    new Double[] { seasonValue, leagueValue, careerValue },
                    new Double[] { pct, 50.0, thirdPct },
                    c.thirdLabelShort(), palette, c.isTeam ? c.meta : c.name);
            loadTeamLogoBitmap(c.team, comparisonBar::setCurrentIcon);
            bar = comparisonBar;
        }
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, dp(c.isTeam ? 72 : 64));
        barLp.setMargins(0, dp(5), 0, 0);
        row.addView(bar, barLp);

        LinearLayout valueLine = new LinearLayout(this);
        valueLine.setOrientation(LinearLayout.HORIZONTAL);
        valueLine.setPadding(0, dp(4), 0, 0);
        valueLine.addView(statValueColumn("This year", format(seasonValue, m), "", palette.primary, true), weightLp());
        valueLine.addView(statValueColumn("MLB avg", format(leagueValue, m), "", Color.rgb(70, 88, 125), false), weightLp());
        valueLine.addView(statValueColumn(c.thirdLabelShort(), format(careerValue, m), "", palette.secondary, false), weightLp());
        row.addView(valueLine);

        renderTrendForMetric(row, c, m, palette);
    }

    private void addTrendModeControl(Comparison c, TeamPalette palette) {
        boolean hasSeason = c != null && c.seasonTrends != null && !c.seasonTrends.isEmpty();
        boolean hasSeasons = c != null && c.recentSeasons != null && c.recentSeasons.size() >= 2;
        if (!hasSeason && !hasSeasons) return;

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, 0, 0, dp(7));
        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(text("Trend", 11, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView hint = text(trendModeCaption(), 9, MUTED, false);
        hint.setGravity(Gravity.RIGHT);
        title.addView(hint);
        wrap.addView(title, matchWrap());

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(5), 0, 0);
        addTrendTab(tabs, "Season", "season", palette, hasSeason);
        addTrendTab(tabs, "30d", "30d", palette, hasSeason);
        addTrendTab(tabs, "15d", "15d", palette, hasSeason);
        addTrendTab(tabs, "7d", "7d", palette, hasSeason);
        addTrendTab(tabs, "Years", "years", palette, hasSeasons);
        scroller.addView(tabs, new HorizontalScrollView.LayoutParams(-2, -2));
        wrap.addView(scroller, matchWrap());
        metricBox.addView(wrap, matchWrap());
    }

    private void addTrendTab(LinearLayout tabs, String label, String mode, TeamPalette palette, boolean enabled) {
        TextView tv = text(label, 11, enabled ? (mode.equals(trendWindowMode) ? Color.WHITE : palette.primary) : Color.rgb(150, 158, 172), true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(11), dp(6), dp(11), dp(6));
        tv.setBackground(mode.equals(trendWindowMode) && enabled ? roundedGradient(new int[] { palette.primary, palette.secondary }, 14) : roundedStroke(Color.WHITE, Color.rgb(218, 226, 238), 14, 1));
        tv.setEnabled(enabled);
        if (enabled) tv.setOnClickListener(v -> {
            trendWindowMode = mode;
            if (lastComparison != null) {
                if (expectedMode) renderExpectedComparison(lastComparison); else renderComparison(lastComparison);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, dp(6), 0);
        tabs.addView(tv, lp);
    }

    private String trendModeCaption() {
        if ("years".equals(trendWindowMode)) return "season-to-season";
        if ("season".equals(trendWindowMode)) return "cumulative season";
        return trendWindowMode + " window average";
    }

    private String trendModeFor(Metric m) {
        if (m == null) return trendWindowMode;
        String mode = metricTrendModes.get(m.key);
        return mode == null ? trendWindowMode : mode;
    }

    private boolean isCurrentSeason(int season) {
        return season == Calendar.getInstance().get(Calendar.YEAR);
    }

    private boolean isRollingTrendMode(String mode) {
        return "7d".equals(mode) || "15d".equals(mode) || "30d".equals(mode);
    }

    private void addMetricTrendTabs(LinearLayout parent, Comparison c, Metric m, TeamPalette palette, boolean hasSeason, boolean hasSeasons, String mode) {
        boolean currentSeason = c != null && isCurrentSeason(c.season);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(0, dp(6), 0, 0);
        tabs.addView(text("Trend", 10, Color.rgb(148, 161, 181), true), new LinearLayout.LayoutParams(0, -2, 1));
        addInlineTrendTab(tabs, m, "Season", "season", palette, hasTrendForMode(c, m, "season", hasSeasons), mode);
        if (currentSeason) {
            addInlineTrendTab(tabs, m, "30d", "30d", palette, hasTrendForMode(c, m, "30d", hasSeasons), mode);
            addInlineTrendTab(tabs, m, "15d", "15d", palette, hasTrendForMode(c, m, "15d", hasSeasons), mode);
            addInlineTrendTab(tabs, m, "7d", "7d", palette, hasTrendForMode(c, m, "7d", hasSeasons), mode);
        }
        addInlineTrendTab(tabs, m, "Years", "years", palette, hasSeasons, mode);
        parent.addView(tabs, matchWrap());
    }

    private boolean hasTrendForMode(Comparison c, Metric m, String mode, boolean hasSeasons) {
        if ("years".equals(mode)) return hasSeasons;
        if (c == null || c.seasonTrends == null || m == null) return false;
        return trendPointCount(c.seasonTrends.get(trendMetricKey(m.key, mode))) >= 2;
    }

    private void addInlineTrendTab(LinearLayout tabs, Metric m, String label, String mode, TeamPalette palette, boolean enabled, String activeMode) {
        TextView tv = text(label, 9, enabled ? (mode.equals(activeMode) ? Color.WHITE : palette.primary) : Color.rgb(160, 168, 180), true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(7), dp(4), dp(7), dp(4));
        tv.setBackground(mode.equals(activeMode) && enabled ? roundedGradientStroke(new int[] { boostNeonColor(palette.primary, 1.06f, 1.03f), mixColor(boostNeonColor(palette.secondary, 1.04f, 1.02f), Color.rgb(8, 13, 22), 0.20f) }, 12, Color.argb(124, 255, 255, 255), 1) : roundedStroke(Color.argb(138, 8, 13, 22), Color.argb(70, 255, 255, 255), 12, 1));
        tv.setEnabled(enabled);
        if (enabled) tv.setOnClickListener(v -> {
            int keepY = mainScroll == null ? 0 : mainScroll.getScrollY();
            metricTrendModes.put(m.key, mode);
            suppressNextAutoScroll = true;
            if (lastComparison != null) {
                if (expectedMode) renderExpectedComparison(lastComparison); else renderComparison(lastComparison);
            }
            if (mainScroll != null) mainScroll.post(() -> mainScroll.scrollTo(0, keepY));
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(4), 0, 0, 0);
        tabs.addView(tv, lp);
    }

    private void renderTrendForMetric(LinearLayout parent, Comparison c, Metric m, TeamPalette palette) {
        if (c == null || m == null) return;
        boolean hasSeason = c.seasonTrends != null && !c.seasonTrends.isEmpty();
        boolean hasSeasons = c.recentSeasons != null && c.recentSeasons.size() >= 2;
        if (!hasSeason && !hasSeasons) return;
        String mode = trendModeFor(m);
        boolean currentSeason = isCurrentSeason(c.season);
        if (!currentSeason && isRollingTrendMode(mode)) mode = hasTrendForMode(c, m, "season", hasSeasons) ? "season" : "years";
        if (!hasTrendForMode(c, m, mode, hasSeasons)) {
            if (hasTrendForMode(c, m, "season", hasSeasons)) mode = "season";
            else if (hasSeasons) mode = "years";
        }
        addMetricTrendTabs(parent, c, m, palette, hasSeason, hasSeasons, mode);
        if ("years".equals(mode)) {
            if (hasSeasons) renderSparklineRow(parent, c.recentSeasons, m, palette);
            return;
        }
        ArrayList<TrendPoint> points = c.seasonTrends == null ? null : c.seasonTrends.get(trendMetricKey(m.key, mode));
        if (trendPointCount(points) >= 2) {
            String title = "season".equals(mode) ? "Season-to-date" : mode + (m.isCount() ? " cumulative total" : " period rate");
            renderTrendPointRow(parent, title, points, m, palette, mode);
        } else if (hasSeasons) {
            renderSparklineRow(parent, c.recentSeasons, m, palette);
        }
    }

    private String trendMetricKey(String metricKey, String mode) {
        if ("7d".equals(mode) || "15d".equals(mode) || "30d".equals(mode)) return metricKey + "__" + mode;
        return metricKey;
    }

    private int trendPointCount(ArrayList<TrendPoint> pts) {
        if (pts == null) return 0;
        int n = 0;
        for (TrendPoint p : pts) if (p != null && p.value != null && !Double.isNaN(p.value)) n++;
        return n;
    }

    private void renderTrendPointRow(LinearLayout parent, String title, ArrayList<TrendPoint> points, Metric m, TeamPalette palette, String mode) {
        if (trendPointCount(points) < 2) return;
        ArrayList<Double> values = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        for (TrendPoint p : points) {
            values.add(p == null ? null : p.value);
            labels.add(p == null ? "" : p.label);
        }
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(9), dp(8), dp(9), dp(8));
        shell.setBackground(roundedGradientStroke(new int[] { Color.rgb(5, 9, 17), mixColor(boostNeonColor(palette.primary, 1.04f, 1.02f), Color.rgb(5, 9, 17), 0.90f), Color.rgb(4, 8, 15) }, 17, Color.argb(78, Color.red(palette.primary), Color.green(palette.primary), Color.blue(palette.primary)), 1));
        LinearLayout.LayoutParams shellLp = matchWrap();
        shellLp.setMargins(0, dp(7), 0, 0);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView sparkTitle = text(title, 10, Color.rgb(184, 198, 218), true);
        sparkTitle.setLetterSpacing(0.06f);
        titleRow.addView(sparkTitle, new LinearLayout.LayoutParams(0, -2, 1));
        String chipText;
        if ("season".equals(mode) || "years".equals(mode)) chipText = "Current " + format(lastNonNull(values), m);
        else chipText = (m.isCount() ? "Total " : "Window ") + format(lastNonNull(values), m);
        TextView avgChip = text(chipText, 9, palette.primary, true);
        avgChip.setGravity(Gravity.CENTER);
        avgChip.setPadding(dp(7), dp(3), dp(7), dp(3));
        avgChip.setBackground(roundedStroke(Color.argb(110, 8, 13, 22), Color.argb(110, Color.red(palette.primary), Color.green(palette.primary), Color.blue(palette.primary)), 12, 1));
        titleRow.addView(avgChip);
        shell.addView(titleRow, matchWrap());

        SparklineView spark = new SparklineView(this, values, labels, m, palette.primary);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(76));
        slp.setMargins(0, dp(5), 0, 0);
        shell.addView(spark, slp);
        parent.addView(shell, shellLp);
    }

    private LinearLayout trendReferenceRow(ArrayList<Double> values, ArrayList<String> labels, Metric m) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Double min = null, max = null;
        for (Double v : values) {
            if (v == null || Double.isNaN(v)) continue;
            min = min == null ? v : Math.min(min, v);
            max = max == null ? v : Math.max(max, v);
        }
        String firstLabel = labels == null || labels.isEmpty() ? "Start" : safe(labels.get(0));
        String lastLabel = labels == null || labels.isEmpty() ? "Now" : safe(labels.get(labels.size() - 1));
        row.addView(trendRefText(firstLabel, Gravity.LEFT), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(trendRefText("Range " + format(min, m) + "–" + format(max, m), Gravity.CENTER), new LinearLayout.LayoutParams(0, -2, 2));
        row.addView(trendRefText(lastLabel, Gravity.RIGHT), new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private TextView trendRefText(String s, int gravity) {
        TextView tv = text(s, 8, MUTED, false);
        tv.setGravity(gravity);
        tv.setSingleLine(true);
        return tv;
    }

    private Double lastNonNull(ArrayList<Double> values) {
        if (values == null) return null;
        for (int i = values.size() - 1; i >= 0; i--) {
            Double v = values.get(i);
            if (v != null && !Double.isNaN(v)) return v;
        }
        return null;
    }

    private Double avgNonNull(ArrayList<Double> values) {
        if (values == null) return null;
        double sum = 0;
        int n = 0;
        for (Double v : values) {
            if (v == null || Double.isNaN(v)) continue;
            sum += v;
            n++;
        }
        return n == 0 ? null : sum / n;
    }

    private LinearLayout statValueColumn(String title, String value, String detail, int color, boolean emphasized) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(6), dp(7), dp(6), dp(7));
        int accent = boostNeonColor(color, emphasized ? 1.08f : 1.02f, emphasized ? 1.04f : 1.02f);
        int fillA = emphasized ? mixColor(accent, Color.rgb(8, 13, 22), 0.76f) : Color.rgb(8, 13, 22);
        int fillB = emphasized ? mixColor(accent, Color.rgb(4, 8, 15), 0.86f) : Color.rgb(5, 9, 17);
        box.setBackground(roundedGradientStroke(new int[] { mixColor(fillA, Color.WHITE, emphasized ? 0.04f : 0.02f), fillB }, 15,
                Color.argb(emphasized ? 118 : 62, Color.red(accent), Color.green(accent), Color.blue(accent)), 1));

        TextView top = text(title.toUpperCase(Locale.US), 8, emphasized ? softColor(accent, 0.18f) : Color.rgb(142, 154, 174), true);
        top.setGravity(Gravity.CENTER);
        top.setLetterSpacing(0.08f);
        box.addView(top, matchWrap());

        TextView primary = text(value == null ? "—" : value, 16, emphasized ? softColor(accent, 0.10f) : Color.rgb(232, 239, 248), true);
        primary.setGravity(Gravity.CENTER);
        primary.setPadding(0, dp(2), 0, 0);
        primary.setFontFeatureSettings("'tnum' 1");  // v29: tabular figures for stat values
        box.addView(primary, matchWrap());
        return box;
    }

    private View percentileDuelView(String nameA, Double pctA, int colorA, String nameB, Double pctB, int colorB) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(7), 0, dp(1));
        row.addView(percentileTile(shortName(nameA), pctA, colorA), weightLp());
        TextView mid = text("percentile", 9, MUTED, true);
        mid.setGravity(Gravity.CENTER);
        row.addView(mid, new LinearLayout.LayoutParams(dp(68), -1));
        row.addView(percentileTile(shortName(nameB), pctB, colorB), weightLp());
        return row;
    }

    private View percentileTile(String label, Double pct, int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(6), dp(8), dp(6));
        box.setBackground(roundedStroke(Color.rgb(250, 252, 255), softColor(color, 0.55f), 14, 1));
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label, 10, color, true);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView pctText = text(percentileBigLabel(pct), 16, INK, true);
        pctText.setGravity(Gravity.RIGHT);
        pctText.setFontFeatureSettings("'tnum' 1");  // v29: tabular figures for percentile
        top.addView(pctText);
        box.addView(top, matchWrap());
        PercentileMiniBarView mini = new PercentileMiniBarView(this, color, pct);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(8));
        lp.setMargins(0, dp(5), 0, 0);
        box.addView(mini, lp);
        return box;
    }

    private TextView tinyValue(String label, String value, int color) {
        TextView tv = text(label + " " + value, 10, color, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(2), dp(1), dp(2), dp(1));
        return tv;
    }



    private int metricAccentColor(Metric m, Double delta) {
        if (delta == null || m.higherGood == null) return NAVY;
        boolean good = delta > 0;
        if (!m.higherGood) good = delta < 0;
        return good ? TEAL_DARK : SALMON;
    }

    private TextView compactValue(String label, String value, int color) {
        TextView tv = text(label + " " + value, 11, color, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(3), dp(1), dp(3), dp(1));
        return tv;
    }

    private void renderPlayerStandings(ArrayList<LeaderboardEntry> entries, int season, Metric metric) {
        standingsBox.setVisibility(View.VISIBLE);
        standingsBox.removeAllViews();
        LinearLayout card = premiumPanelCard(24);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        standingsBox.addView(card, matchWrap());
        card.addView(text("Player leaders · " + metric.label, 20, Color.WHITE, true));
        addRankingScopeToggle(card, false);
        boolean luckMode = metric.key.equals("luck");
        TextView sub = text(season + " season · top " + Math.min(30, entries.size()), 12, Color.rgb(178, 195, 216), false);
        sub.setPadding(0, dp(3), 0, dp(8));
        card.addView(sub);

        lastStandingsText = season + " player standings by " + metric.label + "\nRank\tPlayer\tTeam\t" + metric.label + "\tPA\tBBE\n";
        int selectedRank = generalRankingsMode ? -1 : selectedPlayerRank(entries);
        if (selectedRank > 0) {
            card.addView(sectionLabel("Selected player rank"));
            addPlayerStandingRow(card, selectedRank, entries.get(selectedRank - 1), metric);
            card.addView(sectionLabel(luckMode ? "Overall leaderboard" : "Top rankings"));
        }
        if (luckMode && entries.size() > 20) {
            card.addView(sectionLabel("Luckiest"));
            int topLimit = Math.min(12, entries.size());
            for (int i = 0; i < topLimit; i++) addPlayerStandingRow(card, i + 1, entries.get(i), metric);
            card.addView(sectionLabel("Unluckiest"));
            for (int i = entries.size() - 1, rank = entries.size(); i >= Math.max(topLimit, entries.size() - 12); i--, rank--) addPlayerStandingRow(card, rank, entries.get(i), metric);
        } else {
            int limit = Math.min(30, entries.size());
            for (int i = 0; i < limit; i++) addPlayerStandingRow(card, i + 1, entries.get(i), metric);
        }
        resultsBox.setVisibility(View.GONE);
    }

    private void addPlayerStandingRow(LinearLayout card, int rank, LeaderboardEntry e, Metric metric) {
        boolean highlight = !generalRankingsMode && selectedPlayer != null && e.playerId == selectedPlayer.id;
        card.addView(standingRow(rank, e.name, e.teamAbbrOrName(), format(e.stats.get(metric.key), metric), e.stats, highlight, e.playerId, null));
        lastStandingsText += rank + "\t" + e.name + "\t" + e.teamAbbrOrName() + "\t" + format(e.stats.get(metric.key), metric) + "\t" + e.stats.pa + "\t" + e.stats.bbe + "\n";
    }

    private TextView sectionLabel(String value) {
        TextView tv = text(value, 12, Color.rgb(126, 235, 226), true);
        tv.setLetterSpacing(0.08f);
        tv.setPadding(0, dp(10), 0, dp(3));
        return tv;
    }

    private void renderTeamStandings(ArrayList<TeamStanding> teams, int season, Metric metric) {
        standingsBox.setVisibility(View.VISIBLE);
        standingsBox.removeAllViews();
        LinearLayout card = premiumPanelCard(24);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        standingsBox.addView(card, matchWrap());
        card.addView(text("Team leaders · " + metric.label, 20, Color.WHITE, true));
        addRankingScopeToggle(card, true);
        boolean luckMode = metric.key.equals("luck");
        TextView sub = text(season + " season · all MLB teams", 12, Color.rgb(178, 195, 216), false);
        sub.setPadding(0, dp(3), 0, dp(8));
        card.addView(sub);

        lastStandingsText = season + " team standings by " + metric.label + "\nRank\tTeam\t" + metric.label + "\tPA\tBBE\n";
        int selectedRank = generalRankingsMode ? -1 : selectedTeamRank(teams);
        if (selectedRank > 0) {
            card.addView(sectionLabel("Selected team rank"));
            addTeamStandingRow(card, selectedRank, teams.get(selectedRank - 1), metric);
            card.addView(sectionLabel(luckMode ? "Overall leaderboard" : "League table"));
        }
        if (luckMode && teams.size() > 20) {
            card.addView(sectionLabel("Luckiest"));
            int topLimit = Math.min(10, teams.size());
            for (int i = 0; i < topLimit; i++) addTeamStandingRow(card, i + 1, teams.get(i), metric);
            card.addView(sectionLabel("Unluckiest"));
            for (int i = teams.size() - 1, rank = teams.size(); i >= Math.max(topLimit, teams.size() - 10); i--, rank--) addTeamStandingRow(card, rank, teams.get(i), metric);
        } else {
            for (int i = 0; i < teams.size(); i++) addTeamStandingRow(card, i + 1, teams.get(i), metric);
        }
        resultsBox.setVisibility(View.GONE);
    }

    private void addTeamStandingRow(LinearLayout card, int rank, TeamStanding t, Metric metric) {
        boolean highlight = !generalRankingsMode && selectedTeam != null && t.team.key().equals(selectedTeam.key());
        card.addView(standingRow(rank, t.team.name, t.team.abbr, format(t.stats.get(metric.key), metric), t.stats, highlight, 0, t.team));
        lastStandingsText += rank + "\t" + t.team.name + "\t" + format(t.stats.get(metric.key), metric) + "\t" + t.stats.pa + "\t" + t.stats.bbe + "\n";
    }

    private int selectedPlayerRank(ArrayList<LeaderboardEntry> entries) {
        if (selectedPlayer == null) return -1;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).playerId == selectedPlayer.id) return i + 1;
        return -1;
    }

    private int selectedTeamRank(ArrayList<TeamStanding> teams) {
        if (selectedTeam == null) return -1;
        for (int i = 0; i < teams.size(); i++) if (teams.get(i).team.key().equals(selectedTeam.key())) return i + 1;
        return -1;
    }

    private View standingRow(int rank, String name, String meta, String value, Stats stats, boolean highlight, int playerId, Team team) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(7), dp(7), dp(7));
        row.setBackground(highlight ? roundedGradientStroke(new int[] { Color.argb(78, 13, 178, 163), Color.argb(42, 99, 166, 255) }, 15, Color.argb(130, 255, 245, 150), 1) : roundedStroke(Color.argb(22, 255, 255, 255), Color.argb(60, 190, 214, 236), 15, 1));

        TextView rankTv = text(String.valueOf(rank), 13, highlight ? Color.rgb(255, 229, 96) : Color.rgb(166, 183, 205), true);
        rankTv.setGravity(Gravity.CENTER);
        row.addView(rankTv, new LinearLayout.LayoutParams(dp(30), -2));

        if (playerId > 0) {
            ImageView avatar = new ImageView(this);
            avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
            avatar.setBackground(roundedStroke(Color.rgb(235, 241, 248), LINE, 18, 1));
            LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(dp(36), dp(36));
            aLp.setMargins(0, 0, dp(8), 0);
            row.addView(avatar, aLp);
            loadPlayerImage(playerId, avatar);
        } else if (team != null) {
            View logo = teamLogoView(team, 36);
            LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(dp(36), dp(36));
            aLp.setMargins(0, 0, dp(8), 0);
            row.addView(logo, aLp);
        }

        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        nameCol.addView(text(name, 13, Color.WHITE, true));
        nameCol.addView(text(meta + " · " + statLineSummary(stats), 10, Color.rgb(178, 195, 216), false));
        row.addView(nameCol, new LinearLayout.LayoutParams(0, -2, 1));

        TextView val = text(value, 14, Color.rgb(88, 210, 232), true);
        val.setGravity(Gravity.RIGHT);
        row.addView(val);

        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(3), 0, dp(3));
        if (playerId > 0 || team != null) {
            row.setForeground(ripple(false));
            row.setClickable(true);
            row.setOnClickListener(v -> {
                rankingsModeActive = false;
                if (team != null) {
                    teamMode = true;
                    selectedTeam = team;
                    setMode(true);
                } else {
                    Player p = findPlayerById(playerId);
                    if (p != null) {
                        teamMode = false;
                        selectedPlayer = p;
                        applySmartDefaultForSelection(p);
                        setMode(false);
                    }
                }
                renderSelectionPreview();
                openProfileForCurrentSelection();
            });
        }
        row.setLayoutParams(lp);
        return row;
    }

    private Player findPlayerById(int id) {
        for (Player p : allPlayers) if (p != null && p.id == id) return p;
        return null;
    }

    private Team findTeamByName(String name) {
        String q = safe(name).toLowerCase(Locale.US).trim();
        if (q.isEmpty()) return null;
        for (Team t : allTeams) {
            if (t == null) continue;
            if (safe(t.name).toLowerCase(Locale.US).equals(q) || safe(t.abbr).toLowerCase(Locale.US).equals(q) || t.key().toLowerCase(Locale.US).equals(q)) return t;
        }
        return null;
    }

    private void copyCurrentTable() {
        String text;
        if (resultsBox.getVisibility() == View.VISIBLE && lastHeadToHead != null) text = headToHeadText(lastHeadToHead);
        else if (resultsBox.getVisibility() == View.VISIBLE && lastComparison != null) text = comparisonText(lastComparison);
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

    private String headToHeadText(HeadToHeadComparison h) {
        StringBuilder sb = new StringBuilder();
        sb.append(h.nameA).append(" vs ").append(h.nameB).append(" · ").append(h.season).append(" Statcast side-by-side\n");
        sb.append("Metric\t").append(h.nameA).append("\tMLB Avg\t").append(h.nameB).append("\tDiff A-B\n");
        for (Metric m : metrics) {
            if (!selectedMetricKeys.contains(m.key)) continue;
            Double a = h.statsA.get(m.key);
            Double league = h.leagueStats == null ? null : h.leagueStats.get(m.key);
            Double b = h.statsB.get(m.key);
            sb.append(m.label).append('\t')
                    .append(format(a, m)).append('\t')
                    .append(format(league, m)).append('\t')
                    .append(format(b, m)).append('\t')
                    .append(signedFormat(diff(a, b), m)).append('\n');
        }
        return sb.toString();
    }

    // Data loading -----------------------------------------------------------------------------

    private LoadedData fetchTeamsAndActivePlayers() throws Exception {
        String teamsText = httpGet("https://statsapi.mlb.com/api/v1/teams?sportId=1&activeStatus=Y");
        JSONArray teams = new JSONObject(teamsText).optJSONArray("teams");
        ArrayList<Team> teamList = new ArrayList<>();
        LinkedHashMap<Integer, Team> teamsById = new LinkedHashMap<>();
        if (teams == null) return new LoadedData(teamList, new ArrayList<>());

        for (int i = 0; i < teams.length(); i++) {
            JSONObject teamJson = teams.getJSONObject(i);
            int teamId = teamJson.optInt("id");
            String name = teamJson.optString("name", "MLB");
            String abbr = teamJson.optString("abbreviation", teamJson.optString("teamCode", name));
            Team t = new Team(teamId, name, abbr);
            teamList.add(t);
            teamsById.put(teamId, t);
        }
        teamList.sort((a, b) -> a.name.compareToIgnoreCase(b.name));

        ArrayList<Player> fastPlayers = fetchPlayersFromSingleEndpoint(teamsById);
        if (!fastPlayers.isEmpty()) return new LoadedData(teamList, fastPlayers);

        // Fallback: roster-by-team, but in parallel. The original version did this sequentially and felt very slow.
        LinkedHashMap<Integer, Player> playersById = new LinkedHashMap<>();
        ExecutorService rosterPool = Executors.newFixedThreadPool(8);
        ArrayList<Future<ArrayList<Player>>> futures = new ArrayList<>();
        for (Team t : teamList) {
            futures.add(rosterPool.submit(() -> fetchRosterForTeam(t)));
        }
        for (Future<ArrayList<Player>> f : futures) {
            try {
                for (Player p : f.get()) playersById.put(p.id, p);
            } catch (Exception ignored) {}
        }
        rosterPool.shutdown();
        ArrayList<Player> players = new ArrayList<>(playersById.values());
        players.sort((a, b) -> a.fullName.compareToIgnoreCase(b.fullName));
        return new LoadedData(teamList, players);
    }

    private ArrayList<Player> fetchPlayersFromSingleEndpoint(Map<Integer, Team> teamsById) {
        ArrayList<Player> players = new ArrayList<>();
        try {
            int season = Calendar.getInstance().get(Calendar.YEAR);
            String url = "https://statsapi.mlb.com/api/v1/sports/1/players?season=" + season + "&activeStatus=Y&hydrate=currentTeam,primaryPosition";
            String text = httpGet(url);
            JSONArray people = new JSONObject(text).optJSONArray("people");
            if (people == null) return players;
            for (int i = 0; i < people.length(); i++) {
                JSONObject person = people.getJSONObject(i);
                int id = person.optInt("id");
                String fullName = person.optString("fullName", "");
                JSONObject currentTeam = person.optJSONObject("currentTeam");
                int teamId = currentTeam == null ? 0 : currentTeam.optInt("id");
                Team team = teamsById.get(teamId);
                String teamName = team == null ? (currentTeam == null ? "" : currentTeam.optString("name", "")) : team.name;
                String teamAbbr = team == null ? (currentTeam == null ? "" : currentTeam.optString("abbreviation", "")) : team.abbr;
                JSONObject pos = person.optJSONObject("primaryPosition");
                String position = pos == null ? "" : pos.optString("abbreviation", pos.optString("name", ""));
                if (id > 0 && !fullName.isEmpty() && !teamName.isEmpty()) players.add(new Player(id, fullName, teamName, teamAbbr, position));
            }
            players.sort((a, b) -> a.fullName.compareToIgnoreCase(b.fullName));
        } catch (Exception ignored) {}
        return players;
    }

    private ArrayList<Player> fetchRosterForTeam(Team team) {
        ArrayList<Player> players = new ArrayList<>();
        try {
            String rosterText = httpGet("https://statsapi.mlb.com/api/v1/teams/" + team.id + "/roster/active?hydrate=person");
            JSONArray roster = new JSONObject(rosterText).optJSONArray("roster");
            if (roster == null) return players;
            for (int j = 0; j < roster.length(); j++) {
                JSONObject item = roster.getJSONObject(j);
                JSONObject person = item.optJSONObject("person");
                JSONObject pos = item.optJSONObject("position");
                if (person == null) continue;
                int id = person.optInt("id");
                String fullName = person.optString("fullName", "");
                String position = pos == null ? "" : pos.optString("abbreviation", pos.optString("name", ""));
                if (id > 0 && !fullName.isEmpty()) players.add(new Player(id, fullName, team.name, team.abbr, position));
            }
        } catch (Exception ignored) {}
        return players;
    }

    private ArrayList<LeaderboardEntry> fetchLeaderboardForMetric(int season, Metric metric) throws Exception {
        if (metric != null && "team".equals(metric.side)) return fetchLeaderboardForScope(season, StatScope.BOTH);
        return metric != null && "pitch".equals(metric.side) ? fetchPitchingLeaderboard(season) : fetchLeaderboard(season);
    }

    private ArrayList<LeaderboardEntry> fetchLeaderboardForPlayer(Player p, int season) throws Exception {
        return isPitcher(p) ? fetchPitchingLeaderboard(season) : fetchLeaderboard(season);
    }

    private boolean selectedSideHasNoValues(Stats stats, String side) {
        if (stats == null) return selectedHasMetricSide(side);
        boolean hasSelectedSide = false;
        for (Metric m : metrics) {
            if (!selectedMetricKeys.contains(m.key) || !side.equals(m.side)) continue;
            hasSelectedSide = true;
            Double v = stats.get(m.key);
            if (v != null && !Double.isNaN(v)) return false;
        }
        return hasSelectedSide;
    }

    private void ensureDirectPlayerStatsIfNeeded(Player player, int season, Stats stats) {
        ensureDirectPlayerStatsForScope(player, season, stats, currentStatScope());
    }

    private void ensureDirectPlayerStatsForScope(Player player, int season, Stats stats, StatScope scope) {
        if (player == null || stats == null) return;
        if (scope == StatScope.PITCH_ONLY || scope == StatScope.BOTH || selectedSideHasNoValues(stats, "pitch")) {
            if (scope != StatScope.HIT_ONLY) {
                Stats directPitch = fetchDirectPlayerSeasonStats(player, season, true);
                if (directPitch != null && directPitch.anyValue()) stats.mergeFrom(directPitch);
            }
        }
        if (scope == StatScope.HIT_ONLY || scope == StatScope.BOTH || selectedSideHasNoValues(stats, "hit")) {
            if (scope != StatScope.PITCH_ONLY) {
                Stats directHit = fetchDirectPlayerSeasonStats(player, season, false);
                if (directHit != null && directHit.anyValue()) stats.mergeFrom(directHit);
            }
        }
    }

    private void ensureDirectTeamStatsIfNeeded(Team team, int season, Stats stats) {
        ensureDirectTeamStatsForScope(team, season, stats, currentStatScope());
    }

    private void ensureDirectTeamStatsForScope(Team team, int season, Stats stats, StatScope scope) {
        if (team == null || stats == null) return;
        if (scope == StatScope.PITCH_ONLY || scope == StatScope.BOTH || selectedSideHasNoValues(stats, "pitch")) {
            if (scope != StatScope.HIT_ONLY) {
                Stats directPitch = fetchDirectTeamSeasonStats(team, season, true);
                if (directPitch != null && directPitch.anyValue()) stats.mergeFrom(directPitch);
            }
        }
        if (scope == StatScope.HIT_ONLY || scope == StatScope.BOTH || selectedSideHasNoValues(stats, "hit")) {
            if (scope != StatScope.PITCH_ONLY) {
                Stats directHit = fetchDirectTeamSeasonStats(team, season, false);
                if (directHit != null && directHit.anyValue()) stats.mergeFrom(directHit);
            }
        }
    }

    private Map<String, Stats> fetchLeagueTeamStatsForScope(int season, StatScope scope, Map<String, Stats> aggregateSeeds) {
        String cacheKey = season + ":" + (scope == null ? "BOTH" : scope.name());
        Map<String, Stats> cached = leagueTeamStatsCache.get(cacheKey);
        if (cached != null && !cached.isEmpty()) return new LinkedHashMap<>(cached);

        Map<String, Stats> out = Collections.synchronizedMap(new LinkedHashMap<>());
        if (aggregateSeeds != null) {
            for (Map.Entry<String, Stats> e : aggregateSeeds.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) out.put(e.getKey(), copyStats(e.getValue()));
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        ArrayList<Future<?>> futures = new ArrayList<>();
        for (Team t : allTeams) {
            if (t == null) continue;
            futures.add(pool.submit(() -> {
                Stats seed = aggregateSeeds == null ? null : aggregateSeeds.get(t.key());
                Stats direct = fetchTeamStatsForScope(t, season, scope, seed);
                if (direct != null && direct.anyValue()) out.put(t.key(), direct);
                else if (seed != null && seed.anyValue() && !out.containsKey(t.key())) out.put(t.key(), copyStats(seed));
            }));
        }
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }
        pool.shutdown();

        LinkedHashMap<String, Stats> ordered = new LinkedHashMap<>();
        for (Team t : allTeams) {
            if (t == null) continue;
            Stats s = out.get(t.key());
            if (s != null && s.anyValue()) ordered.put(t.key(), s);
        }
        leagueTeamStatsCache.put(cacheKey, new LinkedHashMap<>(ordered));
        return ordered;
    }

    private Stats fetchTeamStatsForScope(Team team, int season, StatScope scope, Stats aggregateSeed) {
        Stats merged = new Stats();
        if (aggregateSeed != null) merged.mergeFrom(aggregateSeed);
        if (scope == StatScope.HIT_ONLY || scope == StatScope.BOTH) {
            Stats hit = fetchDirectTeamSeasonStats(team, season, false);
            if (hit != null && hit.anyValue()) merged.mergeFrom(hit);
        }
        if (scope == StatScope.PITCH_ONLY || scope == StatScope.BOTH) {
            Stats pitch = fetchDirectTeamSeasonStats(team, season, true);
            if (pitch != null && pitch.anyValue()) merged.mergeFrom(pitch);
        }
        repairTeamDerivedStats(merged);
        return merged.anyValue() ? merged : (aggregateSeed == null ? new Stats() : copyStats(aggregateSeed));
    }

    private void repairTeamDerivedStats(Stats s) {
        if (s == null) return;
        Double r = s.get("r");
        Double hr = s.get("hr");
        if ((s.vals.get("teamRunsScored") == null || Math.abs(s.vals.get("teamRunsScored")) < 0.0000001d) && r != null && Math.abs(r) > 0.0000001d) s.put("teamRunsScored", r);
        if ((s.vals.get("teamHR") == null || Math.abs(s.vals.get("teamHR")) < 0.0000001d) && hr != null && Math.abs(hr) > 0.0000001d) s.put("teamHR", hr);
        Double rs = s.get("teamRunsScored");
        Double ra = s.get("teamRunsAllowed");
        if (rs != null && ra != null) s.put("teamRunDiff", rs - ra);
        Double games = s.vals.get("__games");
        if (games != null && games > 0 && rs != null) s.put("teamRPG", rs / games);
        Double pgames = s.vals.get("__pgames");
        if (pgames != null && pgames > 0 && ra != null) s.put("teamRAPG", ra / pgames);
    }

    private Stats fetchDirectPlayerSeasonStats(Player player, int season, boolean pitching) {
        if (player == null || player.id <= 0) return null;
        String group = pitching ? "pitching" : "hitting";
        String[] urls = new String[] {
                "https://statsapi.mlb.com/api/v1/people/" + player.id + "/stats?stats=season&group=" + group + "&season=" + season,
                "https://statsapi.mlb.com/api/v1/stats?stats=season&group=" + group + "&playerPool=ALL&season=" + season + "&personId=" + player.id + "&limit=10000"
        };
        for (String url : urls) {
            try {
                Stats s = statsFromStatsEndpointJson(httpGet(url), pitching, player.id, null);
                if (s != null && s.anyValue()) return s;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Stats fetchDirectTeamSeasonStats(Team team, int season, boolean pitching) {
        if (team == null || team.id <= 0) return null;
        String group = pitching ? "pitching" : "hitting";
        String[] urls = new String[] {
                "https://statsapi.mlb.com/api/v1/teams/" + team.id + "/stats?stats=season&group=" + group + "&season=" + season,
                // Team endpoints sometimes return nice rate fields but omit or zero out basic counts.
                // The playerPool route gives the same team rolled up from player splits, so merge both.
                "https://statsapi.mlb.com/api/v1/stats?stats=season&group=" + group + "&playerPool=ALL&season=" + season + "&teamId=" + team.id + "&limit=10000"
        };
        Stats merged = new Stats();
        for (String url : urls) {
            try {
                Stats s = statsFromStatsEndpointJson(httpGet(url), pitching, 0, team);
                if (s != null && s.anyValue()) merged.mergeFrom(s);
            } catch (Exception ignored) {}
        }
        repairTeamDerivedStats(merged);
        return merged.anyValue() ? merged : null;
    }

    private Stats statsFromStatsEndpointJson(String text, boolean pitching, int playerId, Team team) throws Exception {
        if (text == null || text.trim().isEmpty()) return null;
        JSONObject root = new JSONObject(text);
        JSONArray statsArr = root.optJSONArray("stats");
        if (statsArr == null) return null;

        WeightedStatsBuilder teamBuilder = team == null ? null : new WeightedStatsBuilder(metrics, true);
        for (int i = 0; i < statsArr.length(); i++) {
            JSONObject bucket = statsArr.optJSONObject(i);
            if (bucket == null) continue;
            JSONArray splits = bucket.optJSONArray("splits");
            if (splits == null) continue;
            for (int j = 0; j < splits.length(); j++) {
                JSONObject split = splits.optJSONObject(j);
                if (split == null) continue;
                if (playerId > 0) {
                    JSONObject player = split.optJSONObject("player");
                    JSONObject person = split.optJSONObject("person");
                    int id = player == null ? 0 : player.optInt("id");
                    if (id <= 0 && person != null) id = person.optInt("id");
                    // /people/{id}/stats often omits a nested player object; in that case the endpoint itself is scoped.
                    if (id > 0 && id != playerId) continue;
                }
                if (team != null) {
                    JSONObject splitTeam = split.optJSONObject("team");
                    if (splitTeam != null) {
                        int tid = splitTeam.optInt("id");
                        String abbr = splitTeam.optString("abbreviation", splitTeam.optString("teamCode", ""));
                        if (tid > 0 && tid != team.id && !safe(abbr).equalsIgnoreCase(team.abbr)) continue;
                    }
                }
                JSONObject stat = split.optJSONObject("stat");
                if (stat == null) continue;
                Stats s = pitching ? statsFromPitchingJson(stat) : statsFromHittingJson(stat);
                if (s == null || !s.anyValue()) continue;
                if (teamBuilder != null) teamBuilder.add(s);
                else return s;
            }
        }
        if (teamBuilder != null) {
            Stats built = teamBuilder.build();
            return built.anyValue() ? built : null;
        }
        return null;
    }

    private boolean selectedHasMetricSide(String side) {
        for (Metric m : metrics) if (selectedMetricKeys.contains(m.key) && side.equals(m.side)) return true;
        return false;
    }

    private boolean selectedHasBothHitAndPitch() {
        return selectedHasMetricSide("hit") && selectedHasMetricSide("pitch");
    }

    private ArrayList<LeaderboardEntry> fetchCombinedPlayerLeaderboard(int season) throws Exception {
        LinkedHashMap<Integer, LeaderboardEntry> byId = new LinkedHashMap<>();
        ArrayList<LeaderboardEntry> fallback = new ArrayList<>();
        addEntriesToCombinedLeaderboard(byId, fallback, fetchLeaderboard(season));
        addEntriesToCombinedLeaderboard(byId, fallback, fetchPitchingLeaderboard(season));
        ArrayList<LeaderboardEntry> combined = new ArrayList<>(byId.values());
        combined.addAll(fallback);
        return combined;
    }

    private void addEntriesToCombinedLeaderboard(LinkedHashMap<Integer, LeaderboardEntry> byId, ArrayList<LeaderboardEntry> fallback, ArrayList<LeaderboardEntry> source) {
        if (source == null) return;
        for (LeaderboardEntry e : source) {
            if (e == null) continue;
            Stats copy = copyStats(e.stats);
            if (e.playerId > 0) {
                LeaderboardEntry existing = byId.get(e.playerId);
                if (existing == null) {
                    byId.put(e.playerId, new LeaderboardEntry(e.playerId, e.name, e.teamName, e.teamAbbr, copy));
                } else {
                    Stats merged = copyStats(existing.stats);
                    merged.mergeFrom(copy);
                    String mergedName = safe(existing.name).isEmpty() ? e.name : existing.name;
                    String mergedTeamName = safe(existing.teamName).isEmpty() ? e.teamName : existing.teamName;
                    String mergedTeamAbbr = safe(existing.teamAbbr).isEmpty() ? e.teamAbbr : existing.teamAbbr;
                    byId.put(e.playerId, new LeaderboardEntry(e.playerId, mergedName, mergedTeamName, mergedTeamAbbr, merged));
                }
            } else {
                fallback.add(new LeaderboardEntry(e.playerId, e.name, e.teamName, e.teamAbbr, copy));
            }
        }
    }

    private Stats copyStats(Stats source) {
        Stats copy = new Stats();
        copy.mergeFrom(source);
        return copy;
    }

    private ArrayList<LeaderboardEntry> fetchLeaderboardForScope(int season, StatScope scope) throws Exception {
        if (scope == StatScope.PITCH_ONLY) return fetchPitchingLeaderboard(season);
        if (scope == StatScope.HIT_ONLY) return fetchLeaderboard(season);
        return fetchCombinedPlayerLeaderboard(season);
    }

    private ArrayList<LeaderboardEntry> fetchLeaderboardForPlayerContext(Player player, int season) throws Exception {
        return fetchLeaderboardForScope(season, scopeForPlayer(player));
    }

    private ArrayList<LeaderboardEntry> fetchLeaderboardForHeadToHeadPlayers(Player a, Player b, int season) throws Exception {
        return fetchLeaderboardForScope(season, scopeForPlayers(a, b));
    }

    private ArrayList<LeaderboardEntry> fetchLeaderboardForSelectedMetrics(int season) throws Exception {
        return fetchLeaderboardForScope(season, currentStatScope());
    }

    private ArrayList<LeaderboardEntry> fetchLeaderboard(int season) throws Exception {
        ArrayList<LeaderboardEntry> cached = leaderboardCache.get(season);
        if (cached != null) return cached;
        String csv;
        List<Map<String, String>> rows;
        try {
            csv = httpGet(customLeaderboardUrl(season, true));
            rows = parseCsv(csv);
            if (rows.isEmpty()) throw new Exception("Expanded custom leaderboard returned no rows");
        } catch (Exception expandedFailed) {
            csv = httpGet(customLeaderboardUrl(season, false));
            rows = parseCsv(csv);
        }
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
        mergeStandardStatsIntoEntries(entries, fetchStandardLeaderboard(season, false));
        leaderboardCache.put(season, entries);
        return entries;
    }

    private ArrayList<LeaderboardEntry> fetchPitchingLeaderboard(int season) throws Exception {
        ArrayList<LeaderboardEntry> cached = pitchingLeaderboardCache.get(season);
        if (cached != null) return cached;
        ArrayList<LeaderboardEntry> entries = fetchStandardLeaderboard(season, true);
        try {
            mergeStandardStatsIntoEntries(entries, fetchPitchingSavantLeaderboard(season));
        } catch (Exception ignored) {}
        if (entries.isEmpty()) throw new Exception("No pitching rows returned for " + season + ".");
        pitchingLeaderboardCache.put(season, entries);
        return entries;
    }

    private String batterSavantSelections(boolean expanded) {
        String base = "pa,xba,xslg,woba,xwoba,exit_velocity_avg,launch_angle_avg,sweet_spot_percent,barrel_batted_rate,hard_hit_percent,k_percent,bb_percent";
        if (!expanded) return base;
        return base + ",xobp,xiso,wobacon,xwobacon,whiff_percent,swing_percent,oz_swing_percent,iz_contact_percent,groundballs_percent,flyballs_percent,linedrives_percent,pull_percent,opposite_percent,sprint_speed";
    }

    private String pitcherSavantSelections(boolean expanded) {
        String base = "pa,xba,xslg,woba,xwoba,exit_velocity_avg,launch_angle_avg,sweet_spot_percent,barrel_batted_rate,hard_hit_percent,k_percent,bb_percent";
        if (!expanded) return base;
        return base + ",whiff_percent,oz_swing_percent,in_zone_percent,f_strike_percent,groundballs_percent,flyballs_percent,linedrives_percent";
    }

    private String customLeaderboardUrl(int season, boolean expanded) throws Exception {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("year", String.valueOf(season));
        params.put("type", "batter");
        params.put("filter", "");
        params.put("min", "1");
        params.put("selections", batterSavantSelections(expanded));
        params.put("sort", "xwoba");
        params.put("sortDir", "desc");
        params.put("csv", "true");
        return "https://baseballsavant.mlb.com/leaderboard/custom" + toQuery(params);
    }

    private String pitchingCustomLeaderboardUrl(int season, boolean expanded) throws Exception {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("year", String.valueOf(season));
        params.put("type", "pitcher");
        params.put("filter", "");
        params.put("min", "1");
        params.put("selections", pitcherSavantSelections(expanded));
        params.put("sort", "xwoba");
        params.put("sortDir", "asc");
        params.put("csv", "true");
        return "https://baseballsavant.mlb.com/leaderboard/custom" + toQuery(params);
    }

    private ArrayList<LeaderboardEntry> fetchPitchingSavantLeaderboard(int season) throws Exception {
        String csv;
        List<Map<String, String>> rows;
        try {
            csv = httpGet(pitchingCustomLeaderboardUrl(season, true));
            rows = parseCsv(csv);
            if (rows.isEmpty()) throw new Exception("Expanded pitching custom leaderboard returned no rows");
        } catch (Exception expandedFailed) {
            csv = httpGet(pitchingCustomLeaderboardUrl(season, false));
            rows = parseCsv(csv);
        }
        ArrayList<LeaderboardEntry> entries = new ArrayList<>();
        for (Map<String, String> row : rows) {
            int id = intVal(pick(row, "player_id", "playerid", "player id", "pitcher", "entity_id"));
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
            Stats stats = statsFromPitchingSavantRow(row);
            if ((!name.isEmpty() || id > 0) && (stats.pa > 0 || stats.bbe > 0 || stats.ip > 0 || stats.anyValue())) {
                entries.add(new LeaderboardEntry(id, name, teamName, teamAbbr, stats));
            }
        }
        return entries;
    }

    private Stats statsFromLeaderboardRow(Map<String, String> row) {
        Stats s = new Stats();
        s.pa = intVal(pick(row, "pa", "PA"));
        s.bbe = intVal(pick(row, "batted_ball", "batted_balls", "batted balls", "bbe", "Batted Balls", "batted_ball_events"));
        if (s.bbe <= 0) s.bbe = intVal(pick(row, "bip", "balls in play"));
        if (s.bbe <= 0 && s.pa > 0) s.bbe = Math.max(1, (int) Math.round(s.pa * 0.68));
        s.put("wOBA", pick(row, "woba", "wOBA"));
        s.put("xBA", pick(row, "xba", "xBA"));
        s.put("xOBP", pick(row, "xobp", "xOBP"));
        s.put("xSLG", pick(row, "xslg", "xSLG"));
        s.put("xwOBA", pick(row, "xwoba", "xwOBA"));
        s.put("wOBAcon", pick(row, "wobacon", "wOBAcon", "woba_con"));
        s.put("xwOBAcon", pick(row, "xwobacon", "xwOBAcon", "xwoba_con"));
        s.put("avgEV", pick(row, "exit_velocity_avg", "Avg EV (MPH)", "avg_ev", "avg exit velocity"));
        s.put("avgLA", pick(row, "launch_angle_avg", "Avg LA (°)", "avg_la", "avg launch angle"));
        s.put("hardHitPct", pick(row, "hard_hit_percent", "Hard Hit %", "hardhit_percent"));
        s.put("barrelPct", pick(row, "barrel_batted_rate", "Barrel%", "barrel_percent", "barrel_batted_rate"));
        s.put("sweetSpotPct", pick(row, "sweet_spot_percent", "LA Sweet-Spot %", "sweet spot %"));
        s.put("kPct", pick(row, "k_percent", "K%", "strikeout_percent", "k %"));
        s.put("bbPct", pick(row, "bb_percent", "BB%", "walk_percent", "bb %"));
        s.put("whiffPct", pick(row, "whiff_percent", "Whiff %", "whiff %"));
        s.put("swingPct", pick(row, "swing_percent", "Swing %", "swing %"));
        s.put("chasePct", pick(row, "oz_swing_percent", "O-Swing%", "Chase %", "out_zone_swing_percent"));
        s.put("zoneContactPct", pick(row, "iz_contact_percent", "Zone Contact %", "in_zone_contact_percent"));
        s.put("gbPct", pick(row, "groundballs_percent", "gb_percent", "ground_ball_percent", "GB%", "GB %", "gb %", "groundball %"));
        s.put("fbPct", pick(row, "flyballs_percent", "fb_percent", "fly_ball_percent", "FB%", "FB %", "fb %", "flyball %"));
        s.put("ldPct", pick(row, "linedrives_percent", "ld_percent", "line_drive_percent", "LD%", "LD %", "ld %", "line drive %"));
        s.put("pullPct", pick(row, "pull_percent", "Pull %", "pull %"));
        s.put("oppoPct", pick(row, "opposite_percent", "oppo_percent", "opposite_field_percent", "Oppo %", "oppo %", "opposite %"));
        s.put("sprintSpeed", pick(row, "sprint_speed", "Sprint Speed"));
        Double xslg = s.get("xSLG"), xba = s.get("xBA");
        Double xiso = pick(row, "xiso", "xISO");
        if (xiso == null && xslg != null && xba != null) xiso = xslg - xba;
        s.put("xISO", xiso);
        Double bbp = s.get("bbPct"), kp = s.get("kPct");
        if (bbp != null && kp != null) s.put("bbMinusKPct", bbp - kp);
        return s;
    }

    private Stats statsFromPitchingSavantRow(Map<String, String> row) {
        Stats s = new Stats();
        s.pa = intVal(pick(row, "pa", "PA", "batters_faced", "bf"));
        s.bbe = intVal(pick(row, "batted_ball", "batted_balls", "batted balls", "bbe", "Batted Balls", "batted_ball_events"));
        if (s.bbe <= 0) s.bbe = intVal(pick(row, "bip", "balls in play"));
        if (s.bbe <= 0 && s.pa > 0) s.bbe = Math.max(1, (int) Math.round(s.pa * 0.68));
        s.put("pwOBA", pick(row, "woba", "wOBA"));
        s.put("pxBA", pick(row, "xba", "xBA"));
        s.put("pxSLG", pick(row, "xslg", "xSLG"));
        s.put("pxwOBA", pick(row, "xwoba", "xwOBA"));
        s.put("pAvgEV", pick(row, "exit_velocity_avg", "Avg EV (MPH)", "avg_ev", "avg exit velocity"));
        s.put("pBarrelPct", pick(row, "barrel_batted_rate", "Barrel%", "barrel_percent", "barrel_batted_rate"));
        s.put("pHardHitPct", pick(row, "hard_hit_percent", "Hard Hit %", "hardhit_percent"));
        s.put("pitchKPct", pick(row, "k_percent", "K%", "strikeout_percent", "k %"));
        s.put("pitchBBPct", pick(row, "bb_percent", "BB%", "walk_percent", "bb %"));
        s.put("pWhiffPct", pick(row, "whiff_percent", "Whiff %", "whiff %"));
        s.put("pChasePct", pick(row, "oz_swing_percent", "O-Swing%", "Chase %", "out_zone_swing_percent"));
        s.put("pZonePct", pick(row, "in_zone_percent", "zone_percent", "zone_rate", "Zone %", "zone %", "In Zone %"));
        s.put("pFirstStrikePct", pick(row, "f_strike_percent", "first_pitch_strike_percent", "first_strike_percent", "f_strike_pct", "First Strike %", "first strike %"));
        s.put("pGbPct", pick(row, "groundballs_percent", "gb_percent", "ground_ball_percent", "GB%", "GB %", "gb %", "groundball %"));
        s.put("pFbPct", pick(row, "flyballs_percent", "fb_percent", "fly_ball_percent", "FB%", "FB %", "fb %", "flyball %"));
        s.put("pLdPct", pick(row, "linedrives_percent", "ld_percent", "line_drive_percent", "LD%", "LD %", "ld %", "line drive %"));
        Double pk = s.get("pitchKPct"), pbb = s.get("pitchBBPct");
        if (pk != null && pbb != null) s.put("pitchKMinusBBPct", pk - pbb);
        return s;
    }

    private ArrayList<LeaderboardEntry> fetchStandardLeaderboard(int season, boolean pitching) {
        ArrayList<LeaderboardEntry> out = new ArrayList<>();
        try {
            String group = pitching ? "pitching" : "hitting";
            String url = "https://statsapi.mlb.com/api/v1/stats?stats=season&group=" + group + "&playerPool=ALL&season=" + season + "&limit=10000";
            String text = httpGet(url);
            JSONArray statsArr = new JSONObject(text).optJSONArray("stats");
            if (statsArr == null || statsArr.length() == 0) return out;
            JSONArray splits = statsArr.getJSONObject(0).optJSONArray("splits");
            if (splits == null) return out;
            for (int i = 0; i < splits.length(); i++) {
                JSONObject split = splits.getJSONObject(i);
                JSONObject player = split.optJSONObject("player");
                JSONObject team = split.optJSONObject("team");
                JSONObject stat = split.optJSONObject("stat");
                if (player == null || stat == null) continue;
                int id = player.optInt("id");
                String name = player.optString("fullName", "");
                String teamName = team == null ? "" : team.optString("name", "");
                String teamAbbr = team == null ? "" : team.optString("abbreviation", team.optString("teamCode", ""));
                Player known = playerById(id);
                if (known != null) {
                    if (name.isEmpty()) name = known.fullName;
                    if (teamName.isEmpty()) teamName = known.teamName;
                    if (teamAbbr.isEmpty()) teamAbbr = known.teamAbbr;
                }
                Stats s = pitching ? statsFromPitchingJson(stat) : statsFromHittingJson(stat);
                if (id > 0 && !name.isEmpty() && (s.pa > 0 || s.bbe > 0 || s.ip > 0 || s.anyValue())) out.add(new LeaderboardEntry(id, name, teamName, teamAbbr, s));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private Stats statsFromHittingJson(JSONObject stat) {
        Stats s = new Stats();
        int ab = intFromJsonAny(stat, "atBats", "ab", "AB");
        int hits = intFromJsonAny(stat, "hits", "h");
        int doubles = intFromJsonAny(stat, "doubles", "2B", "twoBaseHits");
        int triples = intFromJsonAny(stat, "triples", "3B", "threeBaseHits");
        int hr = intFromJsonAny(stat, "homeRuns", "homeRunsScored", "hr", "HR");
        int bb = intFromJsonAny(stat, "baseOnBalls", "walks", "bb", "BB");
        int hbp = intFromJsonAny(stat, "hitByPitch", "hbp", "HBP");
        int sf = intFromJsonAny(stat, "sacFlies", "sacrificeFlies", "sf", "SF");
        int so = intFromJsonAny(stat, "strikeOuts", "strikeouts", "so", "SO");
        int tb = intFromJsonAny(stat, "totalBases", "tb", "TB");
        int runs = intFromJsonAny(stat, "runs", "runsScored", "r", "R");
        int rbi = intFromJsonAny(stat, "rbi", "runsBattedIn", "RBI");
        int sb = intFromJsonAny(stat, "stolenBases", "sb", "SB");
        int games = intFromJsonAny(stat, "gamesPlayed", "games", "g", "G");
        if (tb <= 0 && (hits > 0 || hr > 0 || doubles > 0 || triples > 0)) {
            int singles = Math.max(0, hits - doubles - triples - hr);
            tb = singles + 2 * doubles + 3 * triples + 4 * hr;
        }
        int pa = intFromJsonAny(stat, "plateAppearances", "pa", "PA");
        if (pa <= 0) pa = ab + bb + hbp + sf + intFromJsonAny(stat, "sacBunts", "sacBunts", "sacrificeBunts", "sh", "SH");
        s.pa = pa;
        s.bbe = Math.max(1, ab - so + sf);

        // Keep raw game-log inputs so rolling windows are calculated from ONLY that window,
        // not by averaging season-to-date rate fields returned by MLB's API.
        s.put("__ab", (double) ab);
        s.put("__h", (double) hits);
        s.put("__tb", (double) tb);
        s.put("__bb", (double) bb);
        s.put("__hbp", (double) hbp);
        s.put("__sf", (double) sf);
        s.put("__so", (double) so);
        s.put("__2b", (double) doubles);
        s.put("__3b", (double) triples);
        s.put("__hr", (double) hr);
        s.put("__runs", (double) runs);
        s.put("__rbi", (double) rbi);
        s.put("__sb", (double) sb);
        s.put("__games", (double) games);

        Double avg = ab > 0 ? hits / (double) ab : numFromJsonAny(stat, "avg", "battingAverage");
        Double obp = (ab + bb + hbp + sf) > 0 ? (hits + bb + hbp) / (double) (ab + bb + hbp + sf) : numFromJsonAny(stat, "obp", "onBasePercentage");
        Double slg = ab > 0 ? tb / (double) ab : numFromJsonAny(stat, "slg", "sluggingPercentage");
        Double ops = obp != null && slg != null ? obp + slg : numFromJsonAny(stat, "ops", "onBasePlusSlugging");

        s.put("avg", avg);
        s.put("obp", obp);
        s.put("slg", slg);
        s.put("ops", ops);
        s.put("iso", slg != null && avg != null ? slg - avg : null);
        double babipDen = ab - so - hr + sf;
        s.put("babip", babipDen > 0 ? (hits - hr) / babipDen : null);
        s.put("h", (double) hits);
        s.put("doubles", (double) doubles);
        s.put("triples", (double) triples);
        s.put("hr", (double) hr);
        s.put("xbh", (double) (doubles + triples + hr));
        s.put("rbi", (double) rbi);
        s.put("r", (double) runs);
        s.put("sb", (double) sb);
        s.put("bb", (double) bb);
        s.put("so", (double) so);
        s.put("tb", (double) tb);
        s.put("teamRunsScored", (double) runs);
        s.put("teamHits", (double) hits);
        s.put("teamDoubles", (double) doubles);
        s.put("teamTriples", (double) triples);
        s.put("teamXbh", (double) (doubles + triples + hr));
        s.put("teamHR", (double) hr);
        s.put("teamRBI", (double) rbi);
        s.put("teamSB", (double) sb);
        s.put("teamTB", (double) tb);
        s.put("teamWalks", (double) bb);
        s.put("teamStrikeouts", (double) so);
        if (games > 0) {
            s.put("teamRPG", runs / (double) games);
            s.put("teamWinPct", numFromJsonAny(stat, "winPercentage", "winningPercentage", "winPct"));
        }
        if (pa > 0) {
            s.put("kPct", so * 100.0 / pa);
            s.put("bbPct", bb * 100.0 / pa);
            s.put("bbMinusKPct", (bb - so) * 100.0 / pa);
        }
        return s;
    }

    private Stats statsFromPitchingJson(JSONObject stat) {
        Stats s = new Stats();
        double ip = inningsToDouble(stat.optString("inningsPitched", "0"));
        int bf = intFromJsonAny(stat, "battersFaced", "bf", "BF");
        int k = intFromJsonAny(stat, "strikeOuts", "strikeouts", "so", "SO");
        int bb = intFromJsonAny(stat, "baseOnBalls", "walks", "bb", "BB");
        int hits = intFromJsonAny(stat, "hits", "hitsAllowed", "h", "H");
        int er = intFromJsonAny(stat, "earnedRuns", "earnedRunsAllowed", "er", "ER");
        int runs = intFromJsonAny(stat, "runs", "runsAllowed", "r", "R");
        int hr = intFromJsonAny(stat, "homeRuns", "homeRunsAllowed", "hr", "HR");
        int saves = intFromJsonAny(stat, "saves", "sv", "SV");
        int games = intFromJsonAny(stat, "gamesPlayed", "games", "g", "G");
        int wins = intFromJsonAny(stat, "wins", "w", "W");
        int losses = intFromJsonAny(stat, "losses", "l", "L");

        s.ip = ip;
        s.pa = bf;
        s.bbe = (int) Math.round(ip * 3.0);

        // Raw inputs for true rolling pitching rates over the selected window.
        s.put("__pip", ip);
        s.put("__bf", (double) bf);
        s.put("__pk", (double) k);
        s.put("__pbb", (double) bb);
        s.put("__ph", (double) hits);
        s.put("__er", (double) er);
        s.put("__pr", (double) runs);
        s.put("__phr", (double) hr);
        s.put("__pgames", (double) games);

        s.put("era", ip > 0 ? er * 9.0 / ip : num(stat.optString("era", "")));
        s.put("whip", ip > 0 ? (hits + bb) / ip : num(stat.optString("whip", "")));
        s.put("k9", ip > 0 ? k * 9.0 / ip : num(stat.optString("strikeoutsPer9Inn", "")));
        s.put("bb9", ip > 0 ? bb * 9.0 / ip : num(stat.optString("walksPer9Inn", "")));
        s.put("kbb", bb > 0 ? k / (double) bb : (k > 0 ? (double) k : num(stat.optString("strikeoutWalkRatio", ""))));
        s.put("pitchK", (double) k);
        s.put("pitchBB", (double) bb);
        if (bf > 0) {
            s.put("pitchKPct", k * 100.0 / bf);
            s.put("pitchBBPct", bb * 100.0 / bf);
            s.put("pitchKMinusBBPct", (k - bb) * 100.0 / bf);
        }
        s.put("saves", (double) saves);
        s.put("ip", ip);
        s.put("pHitsAllowed", (double) hits);
        s.put("pHrAllowed", (double) hr);
        s.put("pOppAvg", numFromJsonAny(stat, "avg", "battingAverageAgainst", "opponentAvg", "oppAvg"));
        s.put("pOppOps", numFromJsonAny(stat, "ops", "opponentOps", "oppOps", "opsAgainst"));
        s.put("teamERA", s.get("era"));
        s.put("teamWHIP", s.get("whip"));
        s.put("teamK9", s.get("k9"));
        s.put("teamBB9", s.get("bb9"));
        s.put("teamKBB", s.get("kbb"));
        s.put("teamPitchKPct", s.get("pitchKPct"));
        s.put("teamPitchBBPct", s.get("pitchBBPct"));
        s.put("teamPitchKMinusBBPct", s.get("pitchKMinusBBPct"));
        s.put("teamRunsAllowed", (double) runs);
        s.put("teamHitsAllowed", (double) hits);
        s.put("teamHrAllowed", (double) hr);
        s.put("teamWalksAllowed", (double) bb);
        s.put("teamPitchStrikeouts", (double) k);
        s.put("teamOppAvg", s.get("pOppAvg"));
        s.put("teamOppOps", s.get("pOppOps"));
        if (games > 0) s.put("teamRAPG", runs / (double) games);
        if (wins + losses > 0) s.put("teamWinPct", wins / (double) (wins + losses));
        return s;
    }

    private int intFromJson(JSONObject obj, String key) {
        try { return Integer.parseInt(obj.optString(key, "0").replace(",", "")); } catch (Exception e) { return obj.optInt(key, 0); }
    }

    private int intFromJsonAny(JSONObject obj, String... keys) {
        if (obj == null || keys == null) return 0;
        for (String key : keys) {
            if (key == null || key.isEmpty() || !obj.has(key)) continue;
            try {
                String raw = obj.optString(key, "");
                if (raw != null) raw = raw.replace(",", "").trim();
                if (raw != null && !raw.isEmpty() && !"null".equalsIgnoreCase(raw)) return (int) Math.round(Double.parseDouble(raw));
            } catch (Exception ignored) {
                try { return obj.optInt(key, 0); } catch (Exception ignored2) {}
            }
        }
        return 0;
    }

    private Double numFromJsonAny(JSONObject obj, String... keys) {
        if (obj == null || keys == null) return null;
        for (String key : keys) {
            if (key == null || key.isEmpty() || !obj.has(key)) continue;
            Double v = num(obj.optString(key, ""));
            if (v != null && !Double.isNaN(v)) return v;
        }
        return null;
    }

    private double inningsToDouble(String ipText) {
        try {
            String[] parts = safe(ipText).split("\\.");
            double whole = parts.length > 0 && !parts[0].isEmpty() ? Double.parseDouble(parts[0]) : 0;
            double outs = parts.length > 1 && !parts[1].isEmpty() ? Double.parseDouble(parts[1]) : 0;
            return whole + outs / 3.0;
        } catch (Exception e) { return 0; }
    }

    private void mergeStandardStatsIntoEntries(ArrayList<LeaderboardEntry> entries, ArrayList<LeaderboardEntry> standard) {
        LinkedHashMap<Integer, LeaderboardEntry> byId = new LinkedHashMap<>();
        for (LeaderboardEntry e : entries) if (e.playerId > 0) byId.put(e.playerId, e);
        for (LeaderboardEntry std : standard) {
            LeaderboardEntry existing = byId.get(std.playerId);
            if (existing != null) existing.stats.mergeFrom(std.stats);
            else {
                entries.add(std);
                if (std.playerId > 0) byId.put(std.playerId, std);
            }
        }
    }

    private Stats computeLeagueAverage(ArrayList<LeaderboardEntry> entries) {
        WeightedStatsBuilder b = new WeightedStatsBuilder(metrics);
        for (LeaderboardEntry e : entries) b.add(e.stats);
        return b.build();
    }

    private LinkedHashMap<Integer, Stats> fetchPlayerRecentSeasonStats(Player player, int throughSeason) {
        LinkedHashMap<Integer, Stats> out = new LinkedHashMap<>();
        int first = Math.max(STATCAST_START_YEAR, throughSeason - 3);
        for (int y = throughSeason; y >= first; y--) {
            try {
                LeaderboardEntry e = findPlayerEntry(fetchLeaderboardForPlayer(player, y), player);
                if (e != null && e.stats != null && e.stats.anyValue()) out.put(y, e.stats);
            } catch (Exception ignored) {}
        }
        return out;
    }

    private LinkedHashMap<Integer, Stats> fetchTeamRecentSeasonStats(Team team, int throughSeason) {
        LinkedHashMap<Integer, Stats> out = new LinkedHashMap<>();
        int first = Math.max(STATCAST_START_YEAR, throughSeason - 3);
        for (int y = throughSeason; y >= first; y--) {
            try {
                Stats s = aggregateTeamStats(fetchLeaderboardForSelectedMetrics(y)).get(team.key());
                if (s != null && s.anyValue()) out.put(y, s);
            } catch (Exception ignored) {}
        }
        return out;
    }

    private Stats fetchPlayerCareerStats(Player player, int throughSeason) {
        WeightedStatsBuilder b = new WeightedStatsBuilder(metrics, true);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        ArrayList<Future<Stats>> futures = new ArrayList<>();
        for (int y = STATCAST_START_YEAR; y <= throughSeason; y++) {
            final int year = y;
            futures.add(pool.submit(() -> {
                try {
                    LeaderboardEntry e = findPlayerEntry(fetchLeaderboardForPlayer(player, year), player);
                    return e == null ? null : e.stats;
                } catch (Exception ignored) { return null; }
            }));
        }
        for (Future<Stats> f : futures) {
            try { b.add(f.get()); } catch (Exception ignored) {}
        }
        pool.shutdown();
        return b.build();
    }

    private Stats fetchTeamHistoryStats(Team team, int throughSeason) {
        WeightedStatsBuilder b = new WeightedStatsBuilder(metrics, true);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        ArrayList<Future<Stats>> futures = new ArrayList<>();
        for (int y = STATCAST_START_YEAR; y <= throughSeason; y++) {
            final int year = y;
            futures.add(pool.submit(() -> {
                try { return aggregateTeamStats(fetchLeaderboardForSelectedMetrics(year)).get(team.key()); }
                catch (Exception ignored) { return null; }
            }));
        }
        for (Future<Stats> f : futures) {
            try { b.add(f.get()); } catch (Exception ignored) {}
        }
        pool.shutdown();
        return b.build();
    }

    private LinkedHashMap<String, Stats> fetchPlayerRecentWindows(Player player, int season) {
        LinkedHashMap<String, Stats> out = new LinkedHashMap<>();
        ArrayList<GameLogEntry> logs = fetchPlayerGameLogs(player, season);
        if (logs.isEmpty()) return out;
        Date latest = logs.get(logs.size() - 1).date;
        addRecentWindow(out, logs, latest, 7, "Last 7d");
        addRecentWindow(out, logs, latest, 15, "Last 15d");
        addRecentWindow(out, logs, latest, 30, "Last 30d");
        return out;
    }

    private void addRecentWindow(LinkedHashMap<String, Stats> out, ArrayList<GameLogEntry> logs, Date latest, int days, String label) {
        if (latest == null) return;
        long cutoff = latest.getTime() - (long) Math.max(0, days - 1) * 24L * 60L * 60L * 1000L;
        WeightedStatsBuilder b = new WeightedStatsBuilder(metrics, true);
        int games = 0;
        for (GameLogEntry g : logs) {
            if (g.date != null && g.date.getTime() >= cutoff) { b.add(g.stats); games++; }
        }
        if (games > 0) out.put(label, b.build());
    }

    private Map<String, ArrayList<TrendPoint>> fetchTeamSeasonTrendMap(Team team, int season) {
        LinkedHashMap<String, ArrayList<TrendPoint>> out = new LinkedHashMap<>();
        ArrayList<GameLogEntry> logs = dailyGameLogs(fetchTeamGameLogs(team, season));
        if (logs.size() < 2) return out;
        addSeasonTrend(out, logs);
        addWindowTrend(out, logs, 7);
        addWindowTrend(out, logs, 15);
        addWindowTrend(out, logs, 30);
        return out;
    }

    private ArrayList<GameLogEntry> fetchTeamGameLogs(Team team, int season) {
        String cacheKey = "team:" + (team == null ? 0 : team.id) + ":" + season;
        ArrayList<GameLogEntry> cached = gameLogCache.get(cacheKey);
        if (cached != null) return cached;
        ArrayList<GameLogEntry> out = new ArrayList<>();
        if (team == null || team.id <= 0) return out;
        fetchTeamGameLogsForGroup(team, season, "hitting", out);
        fetchTeamGameLogsForGroup(team, season, "pitching", out);
        out.sort((a, b) -> a.date.compareTo(b.date));
        gameLogCache.put(cacheKey, out);
        return out;
    }

    private void fetchTeamGameLogsForGroup(Team team, int season, String group, ArrayList<GameLogEntry> out) {
        try {
            String url = "https://statsapi.mlb.com/api/v1/teams/" + team.id + "/stats?stats=gameLog&group=" + group + "&season=" + season;
            String text = httpGet(url);
            JSONArray statsArr = new JSONObject(text).optJSONArray("stats");
            if (statsArr != null && statsArr.length() > 0) {
                JSONArray splits = statsArr.getJSONObject(0).optJSONArray("splits");
                if (splits != null) {
                    for (int i = 0; i < splits.length(); i++) {
                        JSONObject split = splits.getJSONObject(i);
                        JSONObject stat = split.optJSONObject("stat");
                        if (stat == null) continue;
                        Date date = parseMlbDate(split.optString("date", ""));
                        Stats s = "pitching".equals(group) ? statsFromPitchingJson(stat) : statsFromHittingJson(stat);
                        if (date != null && s != null && (s.anyValue() || s.pa > 0 || s.ip > 0)) out.add(new GameLogEntry(date, shortDate(date), s));
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private Map<String, ArrayList<TrendPoint>> fetchPlayerSeasonTrendMap(Player player, int season) {
        LinkedHashMap<String, ArrayList<TrendPoint>> out = new LinkedHashMap<>();
        ArrayList<GameLogEntry> logs = dailyGameLogs(fetchPlayerGameLogs(player, season));
        if (logs.size() < 2) return out;
        addSeasonTrend(out, logs);
        addWindowTrend(out, logs, 7);
        addWindowTrend(out, logs, 15);
        addWindowTrend(out, logs, 30);
        return out;
    }

    private ArrayList<GameLogEntry> dailyGameLogs(ArrayList<GameLogEntry> logs) {
        ArrayList<GameLogEntry> out = new ArrayList<>();
        if (logs == null || logs.isEmpty()) return out;
        LinkedHashMap<String, WeightedStatsBuilder> byDay = new LinkedHashMap<>();
        LinkedHashMap<String, Date> dates = new LinkedHashMap<>();
        SimpleDateFormat keyFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        ArrayList<GameLogEntry> sorted = new ArrayList<>(logs);
        sorted.sort((a, b) -> a.date.compareTo(b.date));
        for (GameLogEntry g : sorted) {
            if (g == null || g.date == null || g.stats == null) continue;
            String key = keyFmt.format(g.date);
            WeightedStatsBuilder b = byDay.get(key);
            if (b == null) { b = new WeightedStatsBuilder(metrics, true); byDay.put(key, b); dates.put(key, g.date); }
            b.add(g.stats);
        }
        for (String key : byDay.keySet()) out.add(new GameLogEntry(dates.get(key), shortDate(dates.get(key)), byDay.get(key).build()));
        return out;
    }

    private void addSeasonTrend(Map<String, ArrayList<TrendPoint>> out, ArrayList<GameLogEntry> logs) {
        WeightedStatsBuilder cumulative = new WeightedStatsBuilder(metrics, true);
        int target = Math.min(14, Math.max(5, logs.size()));
        int step = Math.max(1, (int) Math.ceil(logs.size() / (double) target));
        for (int i = 0; i < logs.size(); i++) {
            GameLogEntry g = logs.get(i);
            cumulative.add(g.stats);
            boolean checkpoint = i == logs.size() - 1 || i == 0 || ((i + 1) % step == 0);
            if (checkpoint) addTrendSnapshot(out, "", shortDate(g.date), cumulative.build());
        }
    }

    private void addWindowTrend(Map<String, ArrayList<TrendPoint>> out, ArrayList<GameLogEntry> logs, int days) {
        if (logs == null || logs.size() < 2) return;
        Date latest = logs.get(logs.size() - 1).date;
        if (latest == null) return;
        long oneDay = 24L * 60L * 60L * 1000L;
        long start = latest.getTime() - (long) Math.max(0, days - 1) * oneDay;
        WeightedStatsBuilder running = new WeightedStatsBuilder(metrics, true);
        int points = 0;
        for (GameLogEntry g : logs) {
            if (g == null || g.date == null || g.stats == null) continue;
            long t = g.date.getTime();
            if (t < start || t > latest.getTime()) continue;
            running.add(g.stats);
            addTrendSnapshot(out, "__" + days + "d", shortDate(g.date), running.build());
            points++;
        }
        // If there was only one game in the window, the line is intentionally hidden.
        // The value cards/recent-form strip still show the final window total/rate.
    }

    private void addTrendSnapshot(Map<String, ArrayList<TrendPoint>> out, String suffix, String label, Stats snapshot) {
        if (snapshot == null) return;
        for (Metric m : metrics) {
            Double v = snapshot.get(m.key);
            if (v == null || Double.isNaN(v)) continue;
            String key = m.key + (suffix == null ? "" : suffix);
            ArrayList<TrendPoint> pts = out.get(key);
            if (pts == null) { pts = new ArrayList<>(); out.put(key, pts); }
            pts.add(new TrendPoint(label, v));
        }
    }

    private Stats statsForWindowEnding(ArrayList<GameLogEntry> logs, Date end, int days) {
        if (logs == null || logs.isEmpty() || end == null) return null;
        ArrayList<GameLogEntry> daily = dailyGameLogs(logs);
        long cutoff = end.getTime() - (long) Math.max(0, days - 1) * 24L * 60L * 60L * 1000L;
        WeightedStatsBuilder b = new WeightedStatsBuilder(metrics, true);
        int games = 0;
        for (GameLogEntry g : daily) {
            if (g == null || g.date == null) continue;
            long t = g.date.getTime();
            if (t <= end.getTime() && t >= cutoff) { b.add(g.stats); games++; }
        }
        return games == 0 ? null : b.build();
    }

    private ArrayList<GameLogEntry> fetchPlayerGameLogs(Player player, int season) {
        ArrayList<GameLogEntry> cached = gameLogCache.get(player.id + ":" + season + ":" + (isPitcher(player) ? "pitch" : "hit"));
        if (cached != null) return cached;
        ArrayList<GameLogEntry> out = new ArrayList<>();
        try {
            boolean pitching = isPitcher(player);
            String group = pitching ? "pitching" : "hitting";
            String url = "https://statsapi.mlb.com/api/v1/people/" + player.id + "/stats?stats=gameLog&group=" + group + "&season=" + season;
            String text = httpGet(url);
            JSONArray statsArr = new JSONObject(text).optJSONArray("stats");
            if (statsArr != null && statsArr.length() > 0) {
                JSONArray splits = statsArr.getJSONObject(0).optJSONArray("splits");
                if (splits != null) {
                    for (int i = 0; i < splits.length(); i++) {
                        JSONObject split = splits.getJSONObject(i);
                        JSONObject stat = split.optJSONObject("stat");
                        if (stat == null) continue;
                        Date date = parseMlbDate(split.optString("date", ""));
                        Stats s = pitching ? statsFromPitchingJson(stat) : statsFromHittingJson(stat);
                        if (date != null && s != null && (s.anyValue() || s.pa > 0 || s.ip > 0)) out.add(new GameLogEntry(date, shortDate(date), s));
                    }
                }
            }
        } catch (Exception ignored) {}
        out.sort((a, b) -> a.date.compareTo(b.date));
        gameLogCache.put(player.id + ":" + season + ":" + (isPitcher(player) ? "pitch" : "hit"), out);
        return out;
    }

    private Date parseMlbDate(String value) {
        try { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value); } catch (Exception e) { return null; }
    }

    private String shortDate(Date d) {
        if (d == null) return "";
        try { return new SimpleDateFormat("M/d", Locale.US).format(d); } catch (Exception e) { return ""; }
    }

    private Map<String, Stats> aggregateTeamStats(ArrayList<LeaderboardEntry> entries) {
        LinkedHashMap<String, WeightedStatsBuilder> builders = new LinkedHashMap<>();
        for (LeaderboardEntry e : entries) {
            String key = teamKeyFromEntry(e);
            if (key.isEmpty()) continue;
            WeightedStatsBuilder b = builders.get(key);
            if (b == null) {
                b = new WeightedStatsBuilder(metrics, true);
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

    private View teamLogoView(Team team, int sizeDp) {
        TeamPalette palette = paletteForTeam(team);
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(dp(4), dp(4), dp(4), dp(4));
        String logoAbbr = team == null ? "" : safe(team.abbr).toUpperCase(Locale.US);
        if (logoAbbr.equals("SD") || logoAbbr.equals("SDP")) {
            frame.setBackground(roundedGradient(new int[] { Color.rgb(82, 58, 39), Color.rgb(47, 36, 29), Color.rgb(16, 10, 7) }, Math.max(14, sizeDp / 2)));
        } else if (logoAbbr.equals("LAD")) {
            frame.setBackground(roundedGradient(new int[] { Color.rgb(0, 108, 220), Color.rgb(4, 34, 84) }, Math.max(14, sizeDp / 2)));
        } else if (logoAbbr.equals("STL")) {
            frame.setBackground(roundedGradient(new int[] { Color.rgb(178, 20, 48), Color.rgb(72, 8, 22) }, Math.max(14, sizeDp / 2)));
        } else {
            frame.setBackground(roundedGradient(new int[] { softColor(palette.primary, 0.30f), softColor(palette.secondary, 0.48f) }, Math.max(14, sizeDp / 2)));
        }

        ImageView logo = new ImageView(this);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setAdjustViewBounds(true);
        logo.setPadding(dp(2), dp(2), dp(2), dp(2));
        frame.addView(logo, new FrameLayout.LayoutParams(-1, -1));
        loadTeamLogo(team, logo);
        return frame;
    }

    private TextView teamBadge(String abbr, int sizeDp, int sp, TeamPalette palette) {
        // Kept only for older fallback references. Team logo surfaces now use a logo/shape without text.
        TextView badge = text("", sp, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundedGradient(new int[] { palette.primary, palette.secondary }, Math.max(12, sizeDp / 2)));
        return badge;
    }

    private void loadPlayerImage(int playerId, ImageView imageView) {
        if (playerId <= 0) return;
        imageView.setImageDrawable(roundedGradient(new int[] { Color.rgb(5, 9, 17), Color.rgb(13, 20, 34) }, 24));
        imageView.setContentDescription("Player headshot");
        loadPlayerImageBitmap(playerId, bitmap -> {
            imageView.setImageBitmap(bitmap);
            imageView.setContentDescription("Player headshot");
        });
    }

    private void loadTeamLogo(Team team, ImageView imageView) {
        if (team == null || team.id <= 0) return;
        loadTeamLogoBitmap(team, bitmap -> imageView.setImageBitmap(bitmap));
    }

    private void loadPlayerImageBitmap(int playerId, BitmapCallback callback) {
        if (playerId <= 0 || callback == null) return;
        String[] urls = new String[] {
                // v97: fetch a smaller headshot first to reduce visible blank/placeholder time on phone.
                "https://img.mlbstatic.com/mlb-photos/image/upload/w_240,q_auto:good/v1/people/" + playerId + "/headshot/current",
                "https://midfield.mlbstatic.com/v1/people/" + playerId + "/spots/360",
                "https://img.mlbstatic.com/mlb-photos/image/upload/w_360,q_auto:best/v1/people/" + playerId + "/headshot/current",
                "https://midfield.mlbstatic.com/v1/people/" + playerId + "/spots/720",
                "https://img.mlbstatic.com/mlb-photos/image/upload/w_240,d_people:generic:headshot:silo:current.png,q_auto:best/v1/people/" + playerId + "/headshot/67/current"
        };
        loadBitmapFromUrls(urls, callback);
    }

    private void loadTeamLogoBitmap(Team team, BitmapCallback callback) {
        if (team == null || callback == null) return;
        String code = espnTeamCode(team);
        String abbr = safe(team.abbr).toUpperCase(Locale.US).replaceAll("[^A-Z]", "");
        String preparedKey = "prepared_team_logo_" + team.id + "_" + abbr;
        Bitmap preparedCached = imageCache.get(preparedKey);
        if (preparedCached != null) {
            callback.onBitmap(preparedCached);
            return;
        }

        ArrayList<String> urls = new ArrayList<>();
        if (!code.isEmpty()) {
            // v95: avoid broken/near-black marks on the dark home circles. Some teams need the
            // light mark first; SD still needs the bright gold dark mark.
            boolean preferLight = abbr.equals("COL") || abbr.equals("CWS") || abbr.equals("CHW") || abbr.equals("NYY") || abbr.equals("TB") || abbr.equals("PIT") || abbr.equals("SF") || abbr.equals("LAD") || abbr.equals("STL");
            boolean preferDark = abbr.equals("SD") || abbr.equals("SDP") || abbr.equals("BAL");
            if (preferLight) {
                urls.add("https://a.espncdn.com/i/teamlogos/mlb/500-light/" + code + ".png");
                urls.add("https://a.espncdn.com/i/teamlogos/mlb/500/" + code + ".png");
                urls.add("https://a.espncdn.com/i/teamlogos/mlb/500-dark/" + code + ".png");
            } else if (preferDark) {
                urls.add("https://a.espncdn.com/i/teamlogos/mlb/500-dark/" + code + ".png");
                urls.add("https://a.espncdn.com/i/teamlogos/mlb/500/" + code + ".png");
                urls.add("https://a.espncdn.com/i/teamlogos/mlb/500-light/" + code + ".png");
            } else {
                urls.add("https://a.espncdn.com/i/teamlogos/mlb/500/" + code + ".png");
                urls.add("https://a.espncdn.com/i/teamlogos/mlb/500-light/" + code + ".png");
                urls.add("https://a.espncdn.com/i/teamlogos/mlb/500-dark/" + code + ".png");
            }
        }

        synchronized (preparedTeamLogoWaiters) {
            ArrayList<BitmapCallback> waiters = preparedTeamLogoWaiters.get(preparedKey);
            if (waiters != null) {
                waiters.add(callback);
                return;
            }
            waiters = new ArrayList<>();
            waiters.add(callback);
            preparedTeamLogoWaiters.put(preparedKey, waiters);
        }

        loadBitmapFromUrls(urls.toArray(new String[0]), rawBitmap -> {
            // Prepare/tint once off the main thread, then fan out the shared result.
            io.execute(() -> {
                Bitmap prepared = prepareTeamLogoBitmap(team, rawBitmap);
                if (prepared != null) imageCache.put(preparedKey, prepared);
                ArrayList<BitmapCallback> callbacks;
                synchronized (preparedTeamLogoWaiters) { callbacks = preparedTeamLogoWaiters.remove(preparedKey); }
                if (prepared != null && callbacks != null) {
                    main.post(() -> { for (BitmapCallback cb : callbacks) cb.onBitmap(prepared); });
                }
            });
        });
    }

    private Bitmap prepareTeamLogoBitmap(Team team, Bitmap bitmap) {
        if (team == null || bitmap == null) return bitmap;
        String abbr = safe(team.abbr).toUpperCase(Locale.US).replaceAll("[^A-Z]", "");
        if (abbr.equals("LAD") || abbr.equals("STL")) return tintLogoBitmapKeepingAlpha(bitmap, Color.WHITE);
        if (abbr.equals("SD") || abbr.equals("SDP")) return tintLogoBitmapKeepingAlpha(bitmap, Color.rgb(255, 205, 40));
        return bitmap;
    }

    private Bitmap tintLogoBitmapKeepingAlpha(Bitmap src, int tint) {
        if (src == null) return null;
        Bitmap in = src.getConfig() == Bitmap.Config.ARGB_8888 ? src : src.copy(Bitmap.Config.ARGB_8888, false);
        if (in == null) in = src;
        Bitmap out = Bitmap.createBitmap(in.getWidth(), in.getHeight(), Bitmap.Config.ARGB_8888);
        int[] pixels = new int[in.getWidth() * in.getHeight()];
        in.getPixels(pixels, 0, in.getWidth(), 0, 0, in.getWidth(), in.getHeight());
        int tr = Color.red(tint), tg = Color.green(tint), tb = Color.blue(tint);
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int a = Color.alpha(p);
            if (a == 0) continue;
            pixels[i] = Color.argb(a, tr, tg, tb);
        }
        out.setPixels(pixels, 0, in.getWidth(), 0, 0, in.getWidth(), in.getHeight());
        return out;
    }

    private void loadBitmapFromUrls(String[] urls, BitmapCallback callback) {
        if (urls == null || urls.length == 0 || callback == null) return;
        for (String url : urls) {
            Bitmap cached = imageCache.get(url);
            if (cached != null) { callback.onBitmap(cached); return; }
        }
        String primary = urls[0];
        // Check disk cache
        File imgDir = new File(getCacheDir(), "img");
        imgDir.mkdirs();
        String diskKey = "img_" + Integer.toHexString(primary.hashCode()) + ".webp";
        File diskFile = new File(imgDir, diskKey);
        if (diskFile.exists()) {
            io.execute(() -> {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap b = BitmapFactory.decodeFile(diskFile.getAbsolutePath(), opts);
                if (b != null) {
                    for (String url : urls) imageCache.put(url, b);
                    main.post(() -> callback.onBitmap(b));
                    return;
                }
                fetchAndCacheBitmap(urls, primary, diskFile, callback);
            });
            return;
        }
        synchronized (imageWaiters) {
            ArrayList<BitmapCallback> waiters = imageWaiters.get(primary);
            if (waiters != null) { waiters.add(callback); return; }
            waiters = new ArrayList<>();
            waiters.add(callback);
            imageWaiters.put(primary, waiters);
        }
        io.execute(() -> fetchAndCacheBitmap(urls, primary, diskFile, null));
    }

    private void fetchAndCacheBitmap(String[] urls, String primary, File diskFile, BitmapCallback directCallback) {
        Bitmap bitmap = null;
        for (String url : urls) {
            if (url == null || url.isEmpty()) continue;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(3500);
                conn.setReadTimeout(6000);
                conn.setUseCaches(true);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 Statcast Compare Android");
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                opts.inDither = true;
                Bitmap b = BitmapFactory.decodeStream(conn.getInputStream(), null, opts);
                if (b != null) {
                    bitmap = b;
                    for (String alt : urls) imageCache.put(alt, b);
                    try (FileOutputStream fos = new FileOutputStream(diskFile)) {
                        b.compress(Bitmap.CompressFormat.WEBP, 82, fos);
                    } catch (Exception ignored) {}
                    break;
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        Bitmap finalBitmap = bitmap;
        if (directCallback != null) {
            if (finalBitmap != null) main.post(() -> directCallback.onBitmap(finalBitmap));
            return;
        }
        ArrayList<BitmapCallback> callbacks;
        synchronized (imageWaiters) { callbacks = imageWaiters.remove(primary); }
        if (finalBitmap != null && callbacks != null) {
            main.post(() -> { for (BitmapCallback cb : callbacks) cb.onBitmap(finalBitmap); });
        }
    }

    private String espnTeamCode(Team team) {
        String abbr = team == null ? "" : safe(team.abbr).toUpperCase(Locale.US).replaceAll("[^A-Z]", "");
        String teamName = team == null ? "" : safe(team.name).toLowerCase(Locale.US);
        if (abbr.equals("ARI") || abbr.equals("AZ") || teamName.contains("diamondback")) return "ari";
        if (abbr.equals("ATL")) return "atl";
        if (abbr.equals("BAL")) return "bal";
        if (abbr.equals("BOS")) return "bos";
        if (abbr.equals("CHC")) return "chc";
        if (abbr.equals("CWS") || abbr.equals("CHW")) return "chw";
        if (abbr.equals("CIN")) return "cin";
        if (abbr.equals("CLE")) return "cle";
        if (abbr.equals("COL")) return "col";
        if (abbr.equals("DET")) return "det";
        if (abbr.equals("HOU")) return "hou";
        if (abbr.equals("KC") || abbr.equals("KCR")) return "kc";
        if (abbr.equals("LAA")) return "laa";
        if (abbr.equals("LAD")) return "lad";
        if (abbr.equals("MIA")) return "mia";
        if (abbr.equals("MIL")) return "mil";
        if (abbr.equals("MIN")) return "min";
        if (abbr.equals("NYM")) return "nym";
        if (abbr.equals("NYY")) return "nyy";
        if (abbr.equals("ATH") || abbr.equals("OAK")) return "ath";
        if (abbr.equals("PHI")) return "phi";
        if (abbr.equals("PIT")) return "pit";
        if (abbr.equals("SD") || abbr.equals("SDP")) return "sd";
        if (abbr.equals("SF") || abbr.equals("SFG")) return "sf";
        if (abbr.equals("SEA")) return "sea";
        if (abbr.equals("STL")) return "stl";
        if (abbr.equals("TB") || abbr.equals("TBR")) return "tb";
        if (abbr.equals("TEX")) return "tex";
        if (abbr.equals("TOR")) return "tor";
        if (abbr.equals("WSH") || abbr.equals("WSN")) return "wsh";
        return abbr.toLowerCase(Locale.US);
    }

    private TeamPalette paletteForComparison(Comparison c) {
        if (c == null) return defaultPalette();
        if (c.isTeam) return paletteForTeam(c.team);
        String abbr = c.player == null ? "" : c.player.teamAbbr;
        return paletteForAbbr(abbr);
    }

    private TeamPalette paletteForTeam(Team team) {
        return paletteForAbbr(team == null ? "" : team.abbr);
    }

    private TeamPalette paletteForAbbr(String rawAbbr) {
        String a = safe(rawAbbr).toUpperCase(Locale.US);
        if (a.equals("ARI") || a.equals("AZ")) return new TeamPalette(Color.rgb(46, 226, 239), Color.rgb(235, 43, 78));
        if (a.equals("ATL")) return new TeamPalette(Color.rgb(230, 29, 78), Color.rgb(19, 39, 79));
        if (a.equals("BAL")) return new TeamPalette(Color.rgb(255, 110, 24), Color.rgb(39, 37, 31));
        if (a.equals("BOS")) return new TeamPalette(Color.rgb(222, 52, 63), Color.rgb(12, 35, 64));
        if (a.equals("CHC")) return new TeamPalette(Color.rgb(0, 94, 184), Color.rgb(233, 70, 63));
        if (a.equals("CWS") || a.equals("CHW")) return new TeamPalette(Color.rgb(235, 239, 245), Color.rgb(35, 35, 35));
        if (a.equals("CIN")) return new TeamPalette(Color.rgb(227, 15, 56), Color.rgb(20, 20, 20));
        if (a.equals("CLE")) return new TeamPalette(Color.rgb(234, 31, 63), Color.rgb(0, 43, 92));
        if (a.equals("COL")) return new TeamPalette(Color.rgb(150, 78, 255), Color.rgb(214, 196, 255));
        if (a.equals("DET")) return new TeamPalette(Color.rgb(255, 106, 32), Color.rgb(12, 35, 64));
        if (a.equals("HOU")) return new TeamPalette(Color.rgb(255, 122, 35), Color.rgb(0, 45, 98));
        if (a.equals("KC") || a.equals("KCR")) return new TeamPalette(Color.rgb(44, 132, 255), Color.rgb(189, 155, 96));
        if (a.equals("LAA")) return new TeamPalette(Color.rgb(224, 23, 56), Color.rgb(0, 50, 99));
        if (a.equals("LAD")) return new TeamPalette(Color.rgb(0, 148, 255), Color.rgb(122, 204, 255));
        if (a.equals("MIA")) return new TeamPalette(Color.rgb(0, 211, 224), Color.rgb(255, 92, 114));
        if (a.equals("MIL")) return new TeamPalette(Color.rgb(255, 205, 52), Color.rgb(18, 40, 75));
        if (a.equals("MIN")) return new TeamPalette(Color.rgb(229, 32, 87), Color.rgb(0, 43, 92));
        if (a.equals("NYM")) return new TeamPalette(Color.rgb(255, 116, 26), Color.rgb(0, 76, 151));
        if (a.equals("NYY")) return new TeamPalette(Color.rgb(203, 214, 232), Color.rgb(12, 35, 64));
        if (a.equals("ATH") || a.equals("OAK")) return new TeamPalette(Color.rgb(0, 122, 71), Color.rgb(255, 199, 44));
        if (a.equals("PHI")) return new TeamPalette(Color.rgb(255, 41, 63), Color.rgb(0, 45, 114));
        if (a.equals("PIT")) return new TeamPalette(Color.rgb(255, 205, 52), Color.rgb(39, 37, 31));
        if (a.equals("SD") || a.equals("SDP")) return new TeamPalette(Color.rgb(255, 205, 40), Color.rgb(47, 36, 29));
        if (a.equals("SF") || a.equals("SFG")) return new TeamPalette(Color.rgb(255, 108, 36), Color.rgb(39, 37, 31));
        if (a.equals("SEA")) return new TeamPalette(Color.rgb(0, 201, 168), Color.rgb(12, 44, 86));
        if (a.equals("STL")) return new TeamPalette(Color.rgb(225, 34, 68), Color.rgb(12, 35, 64));
        if (a.equals("TB") || a.equals("TBR")) return new TeamPalette(Color.rgb(143, 203, 255), Color.rgb(9, 44, 92));
        if (a.equals("TEX")) return new TeamPalette(Color.rgb(44, 128, 255), Color.rgb(214, 27, 42));
        if (a.equals("TOR")) return new TeamPalette(Color.rgb(40, 132, 255), Color.rgb(232, 41, 28));
        if (a.equals("WSH") || a.equals("WSN")) return new TeamPalette(Color.rgb(221, 20, 42), Color.rgb(20, 34, 90));
        return defaultPalette();
    }

    private TeamPalette defaultPalette() { return new TeamPalette(NAVY, TEAL_DARK); }

    private int softColor(int color, float amountWhite) {
        int r = Math.round(Color.red(color) * (1f - amountWhite) + 255 * amountWhite);
        int g = Math.round(Color.green(color) * (1f - amountWhite) + 255 * amountWhite);
        int b = Math.round(Color.blue(color) * (1f - amountWhite) + 255 * amountWhite);
        return Color.rgb(r, g, b);
    }

    private int boostNeonColor(int color, float saturationBoost, float valueBoost) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(1f, Math.max(0f, hsv[1] * saturationBoost + 0.05f));
        hsv[2] = Math.min(1f, Math.max(0f, hsv[2] * valueBoost + 0.04f));
        return Color.HSVToColor(hsv);
    }

    private float measureLetterspacedText(Paint targetPaint, String value, float sizePx, float letterSpacing, boolean bold) {
        String s = value == null ? "" : value;
        targetPaint.setTextSize(sizePx);
        targetPaint.setTypeface(bold ? tfBold : tfRegular);
        if (s.length() <= 1 || letterSpacing <= 0.01f) return targetPaint.measureText(s);
        float extra = sizePx * letterSpacing;
        return targetPaint.measureText(s) + extra * (s.length() - 1);
    }

    private float fitTextSize(Paint targetPaint, String value, float baseSizePx, float minSizePx, float maxWidthPx, float letterSpacing, boolean bold) {
        float size = baseSizePx;
        while (size > minSizePx && measureLetterspacedText(targetPaint, value, size, letterSpacing, bold) > maxWidthPx) size -= dp(0.5f);
        return Math.max(minSizePx, size);
    }

    private int readableTeamColor(int primary, int secondary, boolean leftSide) {
        // v110: strongly prefer the team's primary identity color; only lean toward the
        // secondary when the primary is too dark to read cleanly. This keeps teams like
        // COL purple, OAK green, CHC blue, etc. from washing out into lighter accents.
        float[] hsvPrimary = new float[3];
        float[] hsvSecondary = new float[3];
        Color.colorToHSV(primary, hsvPrimary);
        Color.colorToHSV(secondary, hsvSecondary);

        int base = primary;
        if (hsvPrimary[2] < 0.42f) {
            base = mixColor(primary, secondary, 0.24f);
        } else if (hsvPrimary[1] < 0.32f && hsvSecondary[1] > hsvPrimary[1] + 0.08f) {
            base = mixColor(primary, secondary, 0.18f);
        }

        base = ensureReadableColor(base, leftSide ? 146 : 142);
        return boostNeonColor(base, 1.24f, leftSide ? 1.12f : 1.10f);
    }

    private int colorLuminance(int color) {
        return Math.round(0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color));
    }

    private int ensureReadableColor(int color, int minLuminance) {
        int lum = colorLuminance(color);
        if (lum >= minLuminance) return color;
        float deficit = Math.min(1f, Math.max(0f, (minLuminance - lum) / 255f));
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(1f, Math.max(0.18f, hsv[1] + 0.08f + deficit * 0.22f));
        hsv[2] = Math.min(1f, Math.max(0.22f, hsv[2] + 0.16f + deficit * 0.52f));
        return Color.HSVToColor(hsv);
    }

    private int mixColor(int a, int b, float amountB) {
        float t = Math.max(0f, Math.min(1f, amountB));
        int r = Math.round(Color.red(a) * (1f - t) + Color.red(b) * t);
        int g = Math.round(Color.green(a) * (1f - t) + Color.green(b) * t);
        int bb = Math.round(Color.blue(a) * (1f - t) + Color.blue(b) * t);
        return Color.rgb(r, g, bb);
    }

    private String initials(String value) {
        String cleaned = safe(value).replaceAll("[^A-Za-z0-9 ]", " ").trim();
        if (cleaned.isEmpty()) return "•";
        String[] parts = cleaned.split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.US);
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(Locale.US);
    }

    private String httpGet(String urlString) throws Exception {
        String cached = textCache.get(urlString);
        if (cached != null) return cached;

        String fileKey = "hc_" + Integer.toHexString(urlString.hashCode()) + ".txt";
        File cacheFile = new File(getHttpCacheDir(), fileKey);
        long now = System.currentTimeMillis();
        long ttl = cacheTtlMs(urlString);
        if (cacheFile.exists() && now - cacheFile.lastModified() < ttl) {
            String diskText = readCacheFile(cacheFile);
            if (diskText != null && !diskText.isEmpty()) {
                textCache.put(urlString, diskText);
                return diskText;
            }
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(25000);
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
        writeCacheFile(cacheFile, text);
        return text;
    }

    private File getHttpCacheDir() {
        File dir = new File(getCacheDir(), "http");
        dir.mkdirs();
        return dir;
    }

    private String readCacheFile(File f) {
        try (FileInputStream fis = new FileInputStream(f);
             BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    private void writeCacheFile(File f, String text) {
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(text);
        } catch (Exception ignored) {}
    }

    private long cacheTtlMs(String urlString) {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        String currentYearText = "year=" + currentYear;
        if (urlString.contains("/roster/") || urlString.contains("/sports/1/players") || urlString.contains("/teams?")) return 12L * 60L * 60L * 1000L;
        if (urlString.contains(currentYearText)) return 6L * 60L * 60L * 1000L;
        return 21L * 24L * 60L * 60L * 1000L;
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

    private String statLineSummary(Stats s) {
        if (s == null) return "—";
        if (s.ip > 0 && (s.get("era") != null || s.get("whip") != null)) return new DecimalFormat("0.0").format(s.ip) + " IP" + (s.pa > 0 ? " · " + fmtCount(s.pa) + " BF" : "");
        return fmtCount(s.pa) + " PA · " + fmtCount(s.bbe) + " BBE";
    }

    private String format(Double v, Metric m) {
        if (v == null || Double.isNaN(v)) return "—";
        String pattern = m.decimals <= 0 ? "0" : (m.decimals == 1 ? "0.0" : (m.decimals == 2 ? "0.00" : "0.000"));
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
        double[] domain = metricScaleDomain(m, values);
        double lo = domain[0], hi = domain[1];
        if (hi <= lo) { lo -= 1; hi += 1; }
        for (int i = 0; i < values.length; i++) {
            Double v = values[i];
            if (v == null || Double.isNaN(v)) out[i] = 0;
            else out[i] = Math.max(7, Math.min(100, ((v - lo) / (hi - lo)) * 100));
        }
        return out;
    }

    private double[] metricScaleDomain(Metric m, Double[] values) {
        ArrayList<Double> valid = new ArrayList<>();
        for (Double v : values) if (v != null && !Double.isNaN(v)) valid.add(v);
        if (valid.isEmpty()) return new double[] {0, 1};

        double min = Collections.min(valid);
        double max = Collections.max(valid);
        double lo;
        double hi;

        switch (m.key) {
            case "avg":
            case "xBA":
                lo = 0.180; hi = 0.360; break;
            case "obp":
                lo = 0.240; hi = 0.460; break;
            case "slg":
            case "xSLG":
                lo = 0.280; hi = 0.700; break;
            case "ops":
                lo = 0.500; hi = 1.100; break;
            case "wOBA":
            case "xwOBA":
                lo = 0.240; hi = 0.460; break;
            case "luck":
                lo = -0.060; hi = 0.060; break;
            case "avgEV":
                lo = 82.0; hi = 98.0; break;
            case "avgLA":
                lo = -10.0; hi = 35.0; break;
            case "hardHitPct":
                lo = 20.0; hi = 65.0; break;
            case "barrelPct":
                lo = 0.0; hi = 25.0; break;
            case "sweetSpotPct":
                lo = 20.0; hi = 45.0; break;
            case "kPct":
                lo = 5.0; hi = 40.0; break;
            case "bbPct":
                lo = 2.0; hi = 20.0; break;
            case "era":
                lo = 1.50; hi = 7.50; break;
            case "whip":
                lo = 0.80; hi = 1.80; break;
            case "k9":
                lo = 5.0; hi = 14.0; break;
            case "bb9":
                lo = 1.0; hi = 6.0; break;
            case "kbb":
                lo = 1.0; hi = 8.0; break;
            case "ip":
                lo = 0.0; hi = max > 400 ? 1600.0 : 220.0; break;
            case "hr":
                lo = 0.0; hi = max > 120 ? 320.0 : 60.0; break;
            case "rbi":
            case "r":
                lo = 0.0; hi = max > 200 ? 950.0 : 150.0; break;
            case "sb":
                lo = 0.0; hi = max > 120 ? 220.0 : 80.0; break;
            case "pitchK":
                lo = 0.0; hi = max > 400 ? 1600.0 : 320.0; break;
            case "pitchBB":
                lo = 0.0; hi = max > 180 ? 700.0 : 110.0; break;
            case "saves":
                lo = 0.0; hi = max > 90 ? 90.0 : 55.0; break;
            default:
                if (m.isCount()) {
                    lo = 0.0;
                    hi = Math.max(10.0, max * 1.18);
                } else if ("expected".equals(m.type)) {
                    lo = min - 0.010; hi = max + 0.010;
                } else if ("rate".equals(m.type)) {
                    lo = min - 1.5; hi = max + 1.5;
                } else {
                    lo = min - 0.5; hi = max + 0.5;
                }
                break;
        }

        double span = hi - lo;
        double extra = Math.max(span * 0.04, m.isCount() ? 1.0 : ("luck".equals(m.key) ? 0.003 : 0.0));
        lo = Math.min(lo, min - extra);
        hi = Math.max(hi, max + extra);

        if ("luck".equals(m.key)) {
            double abs = Math.max(Math.abs(lo), Math.abs(hi));
            lo = -abs;
            hi = abs;
        }

        return new double[] { lo, hi };
    }


    private void drawBitmapFitCenter(Canvas canvas, Bitmap bitmap, RectF box, boolean roundedClip, float cornerRadius) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return;
        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();
        float scale = Math.min(box.width() / bw, box.height() / bh);
        float w = bw * scale;
        float h = bh * scale;
        Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        RectF dst = new RectF(box.centerX() - w / 2f, box.centerY() - h / 2f, box.centerX() + w / 2f, box.centerY() + h / 2f);
        if (roundedClip) {
            Path clip = new Path();
            clip.addRoundRect(box, cornerRadius, cornerRadius, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(clip);
            canvas.drawBitmap(bitmap, src, dst, paintForBitmap());
            canvas.restoreToCount(save);
        } else {
            canvas.drawBitmap(bitmap, src, dst, paintForBitmap());
        }
    }

    private Paint paintForBitmap() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        p.setStyle(Paint.Style.FILL);
        return p;
    }


    class PercentileMiniBarView extends View {
        final int color;
        final Double pct;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        PercentileMiniBarView(Context context, int color, Double pct) {
            super(context);
            this.color = color;
            this.pct = pct;
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = 0, right = getWidth(), midY = getHeight() / 2f;
            RectF track = new RectF(left, midY - dp(3), right, midY + dp(3));
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(Color.rgb(228, 235, 245));
            canvas.drawRoundRect(track, dp(8), dp(8), paint);
            if (pct != null && !Double.isNaN(pct)) {
                float x = (float) (left + (Math.max(0, Math.min(100, pct)) / 100.0) * (right - left));
                RectF fill = new RectF(left, track.top, x, track.bottom);
                paint.setColor(color);
                canvas.drawRoundRect(fill, dp(8), dp(8), paint);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(x, midY, dp(4), paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(color);
                canvas.drawCircle(x, midY, dp(4), paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
    }

    class ExpectedActualBarView extends View {
        final Metric metric;
        final Double actual, expected, actualPct, expectedPct;
        final TeamPalette palette;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ExpectedActualBarView(Context context, Metric metric, Double actual, Double expected, Double actualPct, Double expectedPct, TeamPalette palette) {
            super(context);
            this.metric = metric;
            this.actual = actual;
            this.expected = expected;
            this.actualPct = actualPct;
            this.expectedPct = expectedPct;
            this.palette = palette == null ? defaultPalette() : palette;
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = dp(18);
            float right = getWidth() - dp(18);
            float y = dp(20);
            float h = dp(10);
            RectF track = new RectF(left, y, right, y + h);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(Color.rgb(229, 235, 245));
            canvas.drawRoundRect(track, dp(10), dp(10), paint);
            paint.setColor(softColor(palette.primary, 0.78f));
            canvas.drawRoundRect(track, dp(10), dp(10), paint);
            drawPercentTick(canvas, left, y + h + dp(14), "0%");
            drawPercentTick(canvas, left + (right - left) / 2f, y + h + dp(14), "50%");
            drawPercentTick(canvas, right, y + h + dp(14), "100%");

            float actualX = markerX(actual, actualPct, left, right);
            float expectedX = markerX(expected, expectedPct, left, right);
            if (actualX >= 0 && expectedX >= 0) {
                paint.setStrokeWidth(dp(3));
                paint.setColor(Color.argb(150, 42, 47, 61));
                canvas.drawLine(actualX, y + h / 2f, expectedX, y + h / 2f, paint);
            }
            if (expectedX >= 0) drawMarker(canvas, expectedX, y + h / 2f, palette.secondary, "x");
            if (actualX >= 0) drawMarker(canvas, actualX, y + h / 2f, palette.primary, "A");
        }
        private float markerX(Double value, Double pct, float left, float right) {
            if (pct != null && !Double.isNaN(pct)) return (float) (left + (Math.max(0, Math.min(100, pct)) / 100.0) * (right - left));
            if (value == null || Double.isNaN(value)) return -1;
            double[] domain = metricScaleDomain(metric, new Double[] { actual, expected });
            double lo = domain[0], hi = domain[1];
            if (hi <= lo) return -1;
            return (float) Math.max(left + dp(10), Math.min(right - dp(10), left + ((value - lo) / (hi - lo)) * (right - left)));
        }
        private void drawPercentTick(Canvas canvas, float x, float y, String label) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(126, 141, 165));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(10));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(label, x, y, paint);
        }
        private void drawMarker(Canvas canvas, float cx, float cy, int color, String label) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx, cy, dp(13), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(color);
            canvas.drawCircle(cx, cy, dp(12), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(10));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(label, cx, cy + dp(4), paint);
        }
    }


    class HeadToHeadBarView extends View {
        final Metric metric;
        final Double[] values;
        final Double[] percentiles;
        final TeamPalette paletteA;
        final TeamPalette paletteB;
        final String labelA;
        final String labelB;
        Bitmap iconA;
        Bitmap iconB;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float animProgress = 0f;    // v29: marker animation (0 → 1)
        private boolean animStarted = false;

        HeadToHeadBarView(Context context, Metric metric, Double[] values, Double[] percentiles, TeamPalette paletteA, TeamPalette paletteB, String labelA, String labelB) {
            super(context);
            this.metric = metric;
            this.values = values;
            this.percentiles = percentiles;
            this.paletteA = paletteA == null ? defaultPalette() : paletteA;
            this.paletteB = paletteB == null ? defaultPalette() : paletteB;
            this.labelA = labelA == null ? "A" : labelA;
            this.labelB = labelB == null ? "B" : labelB;
        }

        void setIconA(Bitmap bitmap) { this.iconA = bitmap; invalidate(); }
        void setIconB(Bitmap bitmap) { this.iconB = bitmap; invalidate(); }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!animStarted) {
                animStarted = true;
                ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
                anim.setDuration(420);
                anim.setInterpolator(new DecelerateInterpolator(2.0f));
                anim.addUpdateListener(a -> {
                    animProgress = (float) a.getAnimatedValue();
                    invalidate();
                });
                anim.start();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            ArrayList<Double> valid = new ArrayList<>();
            for (Double v : values) if (v != null && !Double.isNaN(v)) valid.add(v);
            if (valid.isEmpty()) return;

            double[] domain = metricScaleDomain(metric, values);
            double lo = domain[0];
            double hi = domain[1];
            if (hi <= lo) { lo -= 1; hi += 1; }

            float left = dp(18);
            float right = getWidth() - dp(18);
            float y = dp(22);
            float h = dp(10);
            RectF track = new RectF(left, y, right, y + h);

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(Color.rgb(7, 12, 22));
            canvas.drawRoundRect(track, dp(10), dp(10), paint);
            LinearGradient gradient = new LinearGradient(left, y, right, y + h,
                    new int[] {
                            mixColor(boostNeonColor(paletteA.primary, 1.08f, 1.03f), Color.rgb(8, 12, 20), 0.26f),
                            Color.rgb(86, 90, 120),
                            mixColor(boostNeonColor(paletteB.primary, 1.08f, 1.03f), Color.rgb(8, 12, 20), 0.26f)
                    },
                    new float[] { 0f, 0.50f, 1f }, Shader.TileMode.CLAMP);
            paint.setShader(gradient);
            canvas.drawRoundRect(track, dp(10), dp(10), paint);
            paint.setShader(null);

            paint.setColor(Color.argb(42, 255, 255, 255));
            for (int i = 1; i < 4; i++) {
                float gx = left + (right - left) * i / 4f;
                canvas.drawRoundRect(new RectF(gx - dp(1), y - dp(3), gx + dp(1), y + h + dp(3)), dp(1), dp(1), paint);
            }

            if (lo < 0 && hi > 0 && metric.key.equals("luck")) {
                float zx = (float) (left + ((0 - lo) / (hi - lo)) * (right - left));
                paint.setColor(Color.argb(190, 180, 190, 215));
                canvas.drawRoundRect(new RectF(zx - dp(1), y - dp(8), zx + dp(1), y + h + dp(8)), dp(2), dp(2), paint);
            }

            drawPercentileScale(canvas, left, right, y + h + dp(20));

            float[] xs = new float[values.length];
            for (int i = 0; i < values.length; i++) {
                Double v = values[i];
                if (v == null || Double.isNaN(v)) { xs[i] = -1; continue; }
                float x;
                if (percentiles != null && i < percentiles.length && percentiles[i] != null && !Double.isNaN(percentiles[i])) {
                    x = (float) (left + (Math.max(0, Math.min(100, percentiles[i])) / 100.0) * (right - left));
                } else {
                    x = (float) (left + ((v - lo) / (hi - lo)) * (right - left));
                }
                xs[i] = Math.max(left + dp(14), Math.min(right - dp(14), x));
            }

            // v29: Ease markers from league-average (50%) out to their final percentile
            float centerX = left + (right - left) * 0.5f;
            for (int i = 0; i < xs.length; i++) {
                if (xs[i] >= 0) xs[i] = centerX + (xs[i] - centerX) * animProgress;
            }

            drawMidpointTick(canvas, centerX, y + h / 2);
            if (xs.length > 0 && xs[0] >= 0) drawSideMarker(canvas, xs[0], y + h / 2, iconA, paletteA, labelA, true);
            if (xs.length > 1 && xs[1] >= 0) drawSideMarker(canvas, xs[1], y + h / 2, iconB, paletteB, labelB, false);
        }

        private void drawPercentileScale(Canvas canvas, float left, float right, float baseline) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(126, 141, 165));
            paint.setTextSize(dp(8));
            paint.setTypeface(tfBold);                  // v29: weight ladder
            paint.setFontFeatureSettings("'tnum' 1");   // v29: tabular figures
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("0%", left, baseline, paint);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("50%", (left + right) / 2f, baseline, paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("100%", right, baseline, paint);
        }

        private void drawMidpointTick(Canvas canvas, float cx, float cy) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(150, 142, 154, 178));
            canvas.drawRoundRect(new RectF(cx - dp(1), cy - dp(10), cx + dp(1), cy + dp(10)), dp(2), dp(2), paint);
            paint.setColor(Color.argb(42, 255, 255, 255));
            canvas.drawCircle(cx, cy, dp(4), paint);
        }

        private void drawSideMarker(Canvas canvas, float cx, float cy, Bitmap icon, TeamPalette palette, String label, boolean leftSide) {
            float w = dp(32), h = dp(28);
            RectF outer = new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
            int markerColor = boostNeonColor(palette.primary, 1.10f, 1.04f);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(7, 12, 22));
            canvas.drawRoundRect(new RectF(outer.left - dp(1), outer.top - dp(1), outer.right + dp(1), outer.bottom + dp(1)), dp(10), dp(10), paint);
            paint.setColor(mixColor(markerColor, Color.rgb(7, 12, 22), 0.72f));
            canvas.drawRoundRect(outer, dp(9), dp(9), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(softColor(markerColor, 0.08f));
            canvas.drawRoundRect(outer, dp(9), dp(9), paint);
            paint.setStyle(Paint.Style.FILL);
            RectF inner = new RectF(outer.left + dp(3), outer.top + dp(3), outer.right - dp(3), outer.bottom - dp(3));
            if (icon != null) {
                drawBitmapFitCenter(canvas, icon, inner, true, dp(6));
            } else {
                paint.setColor(mixColor(markerColor, Color.rgb(7, 12, 22), 0.52f));
                canvas.drawRoundRect(inner, dp(6), dp(6), paint);
                paint.setColor(Color.WHITE);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(dp(8));
                paint.setTypeface(tfBold);  // v29
                canvas.drawText(leftSide ? "A" : "B", inner.centerX(), inner.centerY() + dp(3), paint);
            }
        }

        private void drawLeagueMarker(Canvas canvas, float cx, float cy) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(112, 218, 226, 242));
            canvas.drawRoundRect(new RectF(cx - dp(1.15f), cy - dp(12), cx + dp(1.15f), cy + dp(12)), dp(2), dp(2), paint);
            paint.setColor(Color.argb(80, 255, 255, 255));
            canvas.drawCircle(cx, cy, dp(3.2f), paint);
        }
    }



    class PlayerLeagueMatchupCardView extends View {
        final Comparison c;
        final ArrayList<Metric> lensMetrics;
        final TeamPalette palette;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        Bitmap playerIcon;
        Bitmap teamLogo;
        private float animProgress = 0f;
        private boolean animStarted = false;

        PlayerLeagueMatchupCardView(Context context, Comparison c, ArrayList<Metric> lensMetrics, TeamPalette palette) {
            super(context);
            this.c = c;
            this.lensMetrics = lensMetrics == null ? new ArrayList<>() : lensMetrics;
            this.palette = palette == null ? defaultPalette() : palette;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setClickable(true);
            if (c != null) loadPlayerImageBitmap(c.mlbId, bitmap -> { playerIcon = bitmap; invalidate(); });
            Team t = c == null ? null : (c.team != null ? c.team : findTeamByName(c.player == null ? "" : c.player.teamAbbr));
            if (t != null) loadTeamLogoBitmap(t, bitmap -> { teamLogo = bitmap; invalidate(); });
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!animStarted) {
                animStarted = true;
                ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
                anim.setDuration(640);
                anim.setInterpolator(new DecelerateInterpolator(2.0f));
                anim.addUpdateListener(a -> { animProgress = (float)a.getAnimatedValue(); invalidate(); });
                anim.start();
            }
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            if (w <= 0) w = dp(360);
            int h = Math.max(dp(760), Math.round(w * 1.94f));
            setMeasuredDimension(w, h);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float pad = dp(4);
            RectF card = new RectF(pad, pad, w - pad, h - pad);
            float radius = dp(28);
            int playerAccent = boostNeonColor(readableTeamColor(palette.primary, palette.secondary, true), 1.20f, 1.08f);
            int leagueAccent = profileSparkColor(averageLensSignedEdge(c, lensMetrics));
            if (leagueAccent == profileNeutralColor()) leagueAccent = Color.rgb(210, 220, 235);
            int neutralBlue = Color.rgb(112, 181, 255);
            int blend = mixColor(playerAccent, leagueAccent == profileNeutralColor() ? neutralBlue : leagueAccent, 0.50f);

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(card.left, card.top, card.right, card.bottom,
                    new int[] {
                            mixColor(playerAccent, Color.rgb(2, 4, 9), 0.76f),
                            mixColor(palette.secondary, Color.rgb(4, 7, 13), 0.82f),
                            Color.rgb(5, 8, 15),
                            mixColor(neutralBlue, Color.rgb(5, 8, 15), 0.86f),
                            mixColor(leagueAccent, Color.rgb(2, 4, 9), 0.78f)
                    }, new float[] {0f, .24f, .50f, .76f, 1f}, Shader.TileMode.CLAMP));
            paint.setShadowLayer(dp(10), 0, dp(5), Color.argb(42, 0, 0, 0));
            canvas.drawRoundRect(card, radius, radius, paint);
            paint.clearShadowLayer();
            paint.setShader(null);

            int save = canvas.save();
            Path clip = new Path();
            clip.addRoundRect(card, radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);
            drawProfileAtmosphere(canvas, card, playerAccent, neutralBlue);
            drawVignette(canvas, card);

            float titleY = card.top + dp(28);
            drawCardText(canvas, c.season + " PLAYER LENS CARD", card.centerX(), titleY, dp(10), Color.rgb(204, 215, 230), true, Paint.Align.CENTER);
            String lensName = currentLensNameForUi();
            String statPill = lensName.toUpperCase(Locale.US) + " · " + lensMetrics.size() + " SCORED STATS";
            drawPill(canvas, statPill, card.centerX(), titleY + dp(24), Math.min(card.width() - dp(56), dp(206)), dp(25), Color.argb(38, 255, 255, 255), Color.argb(78, 255, 255, 255), Color.rgb(220, 230, 244), dp(9));

            drawCornerLogo(canvas, card.left + dp(34), card.top + dp(54), dp(66), playerAccent, true);
            drawMlbCornerMark(canvas, card.right - dp(34), card.top + dp(54), dp(66), neutralBlue);

            float portraitR = Math.min(dp(74), w * 0.20f);
            float leftCx = card.left + w * 0.25f;
            float rightCx = card.right - w * 0.25f;
            float portraitCy = card.top + dp(142);
            float vsCx = card.centerX();
            float vsCy = portraitCy + dp(1);

            drawBattleBeam(canvas, leftCx + portraitR, vsCy, vsCx - dp(25), vsCy, playerAccent, true);
            drawBattleBeam(canvas, rightCx - portraitR, vsCy, vsCx + dp(25), vsCy, neutralBlue, false);
            drawPortrait(canvas, playerIcon, leftCx, portraitCy, portraitR, playerAccent, initials(c.name));
            drawLeagueOrb(canvas, rightCx, portraitCy, portraitR, neutralBlue);
            drawVsAverageBadge(canvas, vsCx, vsCy, dp(27), playerAccent, neutralBlue, blend);

            drawNameBlock(canvas, c.name, c.meta, leftCx, portraitCy + portraitR + dp(32), playerAccent, true);
            drawNameBlock(canvas, "MLB Average", "League baseline", rightCx, portraitCy + portraitR + dp(32), neutralBlue, false);

            float scoreTop = card.top + dp(326);
            RectF score = new RectF(card.left + dp(26), scoreTop, card.right - dp(26), scoreTop + dp(112));
            drawScoreBlock(canvas, score, playerAccent, neutralBlue);

            drawKeyStats(canvas, card, score.bottom + dp(30), playerAccent, neutralBlue);
            canvas.restoreToCount(save);

            stroke.setShader(null);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1));
            stroke.setColor(Color.argb(78, 255, 255, 255));
            canvas.drawRoundRect(card, radius, radius, stroke);
        }

        private void drawProfileAtmosphere(Canvas canvas, RectF card, int playerAccent, int leagueAccent) {
            RectF left = new RectF(card.left, card.top, card.centerX(), card.bottom);
            RectF right = new RectF(card.centerX(), card.top, card.right, card.bottom);

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(left.centerX(), card.top + dp(146), left.width() * 0.64f,
                    new int[] { Color.argb(128, Color.red(playerAccent), Color.green(playerAccent), Color.blue(playerAccent)), Color.argb(38, Color.red(palette.secondary), Color.green(palette.secondary), Color.blue(palette.secondary)), Color.TRANSPARENT },
                    new float[] {0f, .35f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(left.centerX(), card.top + dp(146), left.width() * 0.66f, paint);
            paint.setShader(new RadialGradient(right.centerX(), card.top + dp(146), right.width() * 0.62f,
                    new int[] { Color.argb(94, Color.red(leagueAccent), Color.green(leagueAccent), Color.blue(leagueAccent)), Color.argb(28, 255, 255, 255), Color.TRANSPARENT },
                    new float[] {0f, .38f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(right.centerX(), card.top + dp(146), right.width() * 0.64f, paint);
            paint.setShader(null);

            if (teamLogo != null) {
                paint.setAlpha(32);
                RectF logoBox = new RectF(card.left + dp(18), card.top + dp(72), card.left + dp(198), card.top + dp(252));
                drawBitmapFitCenter(canvas, teamLogo, logoBox, false, 0);
                paint.setAlpha(255);
            }

            drawSkyline(canvas, left, card.top + dp(304), Color.argb(34, Color.red(playerAccent), Color.green(playerAccent), Color.blue(playerAccent)));
            drawSkyline(canvas, right, card.top + dp(304), Color.argb(28, 255, 255, 255));

            paint.setShader(new LinearGradient(card.centerX() - dp(20), card.top, card.centerX() + dp(20), card.bottom,
                    new int[] { Color.TRANSPARENT, Color.argb(34, 255, 255, 255), Color.TRANSPARENT },
                    new float[] {0f, .50f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(card.centerX() - dp(1.3f), card.top + dp(54), card.centerX() + dp(1.3f), card.bottom - dp(26), paint);
            paint.setShader(null);
        }

        private void drawSkyline(Canvas canvas, RectF zone, float base, int color) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            float x = zone.left + dp(14);
            float[] heights = new float[] {34, 52, 28, 66, 42, 58, 36, 48};
            float gap = Math.max(dp(4), (zone.width() - dp(28)) / 8f - dp(13));
            for (int i = 0; i < heights.length; i++) {
                float ww = dp(11 + (i % 3) * 3);
                float hh = dp(heights[i]);
                canvas.drawRoundRect(new RectF(x, base - hh, x + ww, base), dp(2), dp(2), paint);
                x += ww + gap;
            }
        }

        private void drawVignette(Canvas canvas, RectF card) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(card.centerX(), card.centerY(), card.width() * .74f,
                    new int[] { Color.TRANSPARENT, Color.argb(20, 0, 0, 0), Color.argb(112, 0, 0, 0) },
                    new float[] {0f, .55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(card, paint);
            paint.setShader(null);
        }

        private void drawScoreBlock(Canvas canvas, RectF score, int playerAccent, int leagueAccent) {
            Double pct = averageLensPercentile(c, lensMetrics);
            double signed = averageLensSignedEdge(c, lensMetrics);
            int sparkColor = profileSparkColor(signed);
            if (sparkColor == profileNeutralColor()) sparkColor = Color.rgb(218, 228, 242);
            String big = pct == null ? "—" : Math.round(Math.max(0, Math.min(100, pct))) + "%";
            String tier = pct == null ? "League context" : percentileTierLabel(pct);
            String edge = signedEdgeSummary(signed);

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(score.left, score.top, score.right, score.bottom,
                    new int[] { Color.argb(222, 6, 10, 18), Color.argb(238, 12, 18, 30), Color.argb(218, 6, 10, 18) },
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(score, dp(24), dp(24), paint);
            paint.setShader(null);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1));
            stroke.setColor(Color.argb(82, 255, 255, 255));
            canvas.drawRoundRect(score, dp(24), dp(24), stroke);

            drawCardText(canvas, "LENS EDGE", score.left + dp(18), score.top + dp(24), dp(10), Color.rgb(155, 171, 195), true, Paint.Align.LEFT);
            drawCardText(canvas, edge, score.left + dp(18), score.top + dp(47), dp(16), Color.WHITE, true, Paint.Align.LEFT);
            drawCardText(canvas, currentLensNameForUi(), score.left + dp(18), score.top + dp(69), dp(10), Color.rgb(156, 172, 196), false, Paint.Align.LEFT);

            drawCardText(canvas, big, score.right - dp(18), score.top + dp(42), dp(31), sparkColor, true, Paint.Align.RIGHT);
            drawCardText(canvas, tier, score.right - dp(18), score.top + dp(66), dp(12), Color.rgb(226, 235, 247), true, Paint.Align.RIGHT);

            float railLeft = score.left + dp(18);
            float railRight = score.right - dp(18);
            drawCenteredSparkRail(canvas, railLeft, railRight, score.bottom - dp(24), signed, sparkColor, true);
        }

        private String signedEdgeSummary(double signed) {
            double a = Math.abs(signed);
            if (a < .08d) return "Even with MLB avg";
            if (signed > 0) {
                if (a >= .72d) return "Major edge vs avg";
                if (a >= .38d) return "Clear edge vs avg";
                return "Slight edge vs avg";
            }
            if (a >= .72d) return "Well below avg";
            if (a >= .38d) return "Below avg";
            return "Slightly below avg";
        }

        private void drawKeyStats(Canvas canvas, RectF card, float startY, int playerAccent, int leagueAccent) {
            ArrayList<Metric> metrics = displayMetrics();
            drawCardText(canvas, "KEY LENS STATS", card.left + dp(24), startY, dp(10), Color.rgb(190, 205, 224), true, Paint.Align.LEFT);
            drawCardText(canvas, "PLAYER", card.left + dp(24), startY + dp(22), dp(8), softColor(playerAccent, 0.14f), true, Paint.Align.LEFT);
            drawCardText(canvas, "MLB AVG", card.right - dp(24), startY + dp(22), dp(8), Color.rgb(186, 205, 232), true, Paint.Align.RIGHT);
            if (metrics.isEmpty()) {
                drawCardText(canvas, "No comparable stats in this lens yet.", card.centerX(), startY + dp(78), dp(13), Color.rgb(172, 186, 208), false, Paint.Align.CENTER);
                return;
            }
            float y = startY + dp(48);
            float rowH = dp(48);
            for (int i = 0; i < metrics.size(); i++) {
                Metric m = metrics.get(i);
                Double value = c.seasonStats == null ? null : c.seasonStats.get(m.key);
                Double league = c.leagueStats == null ? null : c.leagueStats.get(m.key);
                Double signed = signedLeagueEdge(value, league, m);
                if (signed == null) signed = 0d;
                int color = profileSparkColor(signed);
                if (color == profileNeutralColor()) color = Color.rgb(218, 228, 242);
                RectF row = new RectF(card.left + dp(18), y - dp(21), card.right - dp(18), y + dp(20));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(i % 2 == 0 ? Color.argb(74, 255, 255, 255) : Color.argb(40, 255, 255, 255));
                canvas.drawRoundRect(row, dp(14), dp(14), paint);

                drawCardText(canvas, m.label, card.left + dp(28), y - dp(2), dp(11), Color.rgb(236, 243, 251), true, Paint.Align.LEFT);
                drawCardText(canvas, format(value, m), card.left + dp(28), y + dp(14), dp(10), softColor(playerAccent, 0.08f), true, Paint.Align.LEFT);
                drawCardText(canvas, format(league, m), card.right - dp(28), y + dp(7), dp(11), Color.rgb(222, 232, 246), true, Paint.Align.RIGHT);

                float railLeft = card.left + dp(132);
                float railRight = card.right - dp(88);
                drawMiniSparkRail(canvas, railLeft, railRight, y + dp(5), signed, color);

                Double pct = c.percentile == null ? null : c.percentile.get(m.key);
                String pctText = pct == null ? "" : Math.round(Math.max(0, Math.min(100, pct))) + "%";
                drawCardText(canvas, pctText, railRight + dp(7), y + dp(7), dp(9), color, true, Paint.Align.LEFT);
                y += rowH;
            }
        }

        private ArrayList<Metric> displayMetrics() {
            ArrayList<Metric> out = new ArrayList<>();
            if (lensMetrics == null) return out;
            for (Metric m : lensMetrics) {
                if (out.size() >= 5) break;
                if (m == null || c == null || c.seasonStats == null || c.leagueStats == null) continue;
                if (c.seasonStats.get(m.key) == null || c.leagueStats.get(m.key) == null) continue;
                out.add(m);
            }
            return out;
        }

        private void drawCenteredSparkRail(Canvas canvas, float left, float right, float y, double signed, int sparkColor, boolean large) {
            float cx = (left + right) / 2f;
            float travel = (right - left) / 2f - dp(13);
            float norm = (float)Math.max(-1d, Math.min(1d, signed));
            float sparkX = cx + travel * norm * animProgress;
            float abs = Math.abs(norm) * animProgress;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(large ? 7.8f : 5.6f));
            paint.setShader(new LinearGradient(left, y, right, y,
                    new int[] { Color.argb(82, Color.red(profileNegativeColor()), Color.green(profileNegativeColor()), Color.blue(profileNegativeColor())), Color.argb(76, 224, 232, 244), Color.argb(82, Color.red(profilePositiveColor()), Color.green(profilePositiveColor()), Color.blue(profilePositiveColor())) },
                    new float[] {0f, .50f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawLine(left, y, right, y, paint);
            paint.setShader(null);
            paint.setStrokeWidth(dp(1.2f));
            paint.setColor(Color.argb(185, 235, 242, 250));
            canvas.drawLine(cx, y - dp(11), cx, y + dp(11), paint);

            if (Math.abs(sparkX - cx) > dp(1.2f)) {
                paint.setStrokeWidth(dp(large ? 5.2f : 4.0f));
                paint.setShader(new LinearGradient(cx, y, sparkX, y,
                        new int[] { Color.argb(34, Color.red(sparkColor), Color.green(sparkColor), Color.blue(sparkColor)), Color.argb((int)(126 + 82 * abs), Color.red(sparkColor), Color.green(sparkColor), Color.blue(sparkColor)), Color.argb((int)(192 + 42 * abs), Color.red(sparkColor), Color.green(sparkColor), Color.blue(sparkColor)) },
                        new float[] {0f, .58f, 1f}, Shader.TileMode.CLAMP));
                paint.setShadowLayer(dp(3 + 4 * abs), 0, 0, Color.argb((int)(90 + 74 * abs), Color.red(sparkColor), Color.green(sparkColor), Color.blue(sparkColor)));
                canvas.drawLine(cx, y, sparkX, y, paint);
                paint.clearShadowLayer();
                paint.setShader(null);
            }
            drawSpark(canvas, sparkX, y, sparkColor, Math.max(0.20f, abs));
            paint.setStrokeCap(Paint.Cap.BUTT);
        }

        private void drawMiniSparkRail(Canvas canvas, float left, float right, float y, double signed, int sparkColor) {
            drawCenteredSparkRail(canvas, left, right, y, signed, sparkColor, false);
        }

        private void drawSpark(Canvas canvas, float x, float y, int color, float strength) {
            int hot = mixColor(Color.WHITE, color, 0.12f);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(x, y, dp(14 + 8 * strength),
                    new int[] { Color.argb((int)(164 + 56 * strength), Color.red(color), Color.green(color), Color.blue(color)), Color.argb((int)(54 + 42 * strength), Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT },
                    new float[] {0f, .42f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, dp(14 + 8 * strength), paint);
            paint.setShader(null);
            paint.setColor(Color.rgb(6, 10, 18));
            canvas.drawCircle(x, y, dp(5.4f), paint);
            paint.setColor(hot);
            canvas.drawCircle(x, y, dp(3.7f), paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x - dp(1.0f), y - dp(1.0f), dp(1.1f), paint);
        }

        private void drawPortrait(Canvas canvas, Bitmap bmp, float cx, float cy, float r, int accent, String fallback) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx, cy, r * 1.55f,
                    new int[] { Color.argb(170, Color.red(accent), Color.green(accent), Color.blue(accent)), Color.argb(42, Color.red(accent), Color.green(accent), Color.blue(accent)), Color.TRANSPARENT },
                    new float[] {0f, .46f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r * 1.34f, paint);
            paint.setShader(null);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(5));
            stroke.setColor(Color.argb(232, Color.red(accent), Color.green(accent), Color.blue(accent)));
            stroke.setShadowLayer(dp(14), 0, 0, Color.argb(210, Color.red(accent), Color.green(accent), Color.blue(accent)));
            canvas.drawCircle(cx, cy, r, stroke);
            stroke.clearShadowLayer();
            RectF box = new RectF(cx - r + dp(6), cy - r + dp(6), cx + r - dp(6), cy + r - dp(6));
            if (bmp != null) drawBitmapFitCenter(canvas, bmp, box, true, r - dp(6));
            else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(mixColor(accent, Color.rgb(7, 12, 22), .62f));
                canvas.drawCircle(cx, cy, r - dp(7), paint);
                drawCardText(canvas, fallback, cx, cy + dp(8), dp(22), Color.WHITE, true, Paint.Align.CENTER);
            }
        }

        private void drawLeagueOrb(Canvas canvas, float cx, float cy, float r, int accent) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx, cy, r * 1.55f,
                    new int[] { Color.argb(138, Color.red(accent), Color.green(accent), Color.blue(accent)), Color.argb(36, 255, 255, 255), Color.TRANSPARENT },
                    new float[] {0f, .45f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r * 1.34f, paint);
            paint.setShader(null);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(5));
            stroke.setColor(Color.argb(220, Color.red(accent), Color.green(accent), Color.blue(accent)));
            stroke.setShadowLayer(dp(14), 0, 0, Color.argb(172, Color.red(accent), Color.green(accent), Color.blue(accent)));
            canvas.drawCircle(cx, cy, r, stroke);
            stroke.clearShadowLayer();
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r,
                    new int[] { Color.rgb(8, 13, 23), Color.rgb(17, 28, 45), Color.rgb(8, 13, 23) }, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r - dp(7), paint);
            paint.setShader(null);
            drawCardText(canvas, "MLB", cx, cy - dp(3), dp(20), Color.WHITE, true, Paint.Align.CENTER);
            drawCardText(canvas, "AVG", cx, cy + dp(19), dp(13), Color.rgb(190, 212, 240), true, Paint.Align.CENTER);
        }

        private void drawVsAverageBadge(Canvas canvas, float cx, float cy, float r, int leftColor, int rightColor, int blend) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx, cy, r * 2.6f,
                    new int[] { Color.argb(78, Color.red(blend), Color.green(blend), Color.blue(blend)), Color.argb(32, 255, 255, 255), Color.TRANSPARENT },
                    new float[] {0f, .40f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r * 2.2f, paint);
            paint.setShader(null);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(4.2f));
            stroke.setShader(new LinearGradient(cx - r, cy, cx + r, cy, new int[] { leftColor, Color.WHITE, rightColor }, null, Shader.TileMode.CLAMP));
            stroke.setShadowLayer(dp(16), 0, 0, Color.argb(184, Color.red(blend), Color.green(blend), Color.blue(blend)));
            canvas.drawCircle(cx, cy, r + dp(5), stroke);
            stroke.clearShadowLayer();
            stroke.setShader(null);
            paint.setShader(new LinearGradient(cx - r, cy, cx + r, cy,
                    new int[] { mixColor(leftColor, Color.rgb(3, 7, 14), .46f), Color.rgb(9, 14, 22), mixColor(rightColor, Color.rgb(3, 7, 14), .46f) }, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, paint);
            paint.setShader(null);
            drawCardText(canvas, "VS", cx, cy - dp(1), dp(12), Color.WHITE, true, Paint.Align.CENTER);
            drawCardText(canvas, "AVG", cx, cy + dp(12), dp(8), Color.rgb(222, 232, 246), true, Paint.Align.CENTER);
        }

        private void drawBattleBeam(Canvas canvas, float x1, float y1, float x2, float y2, int color, boolean fromLeft) {
            Path beam = new Path();
            beam.moveTo(x1, y1);
            float mid = (x1 + x2) / 2f;
            beam.cubicTo(mid, y1 + dp(fromLeft ? -7 : 7), mid, y2 + dp(fromLeft ? 7 : -7), x2, y2);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(9));
            paint.setShader(new LinearGradient(x1, y1, x2, y2,
                    new int[] { Color.TRANSPARENT, Color.argb(194, Color.red(color), Color.green(color), Color.blue(color)), Color.argb(220, 255, 255, 255), Color.TRANSPARENT },
                    new float[] {0f, .34f, .82f, 1f}, Shader.TileMode.CLAMP));
            paint.setShadowLayer(dp(15), 0, 0, Color.argb(168, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawPath(beam, paint);
            paint.clearShadowLayer();
            paint.setShader(null);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.argb(212, 255, 250, 230));
            canvas.drawPath(beam, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
        }

        private void drawNameBlock(Canvas canvas, String title, String subtitle, float cx, float y, int accent, boolean leftSide) {
            drawCardText(canvas, title, cx, y, dp(18), Color.WHITE, true, Paint.Align.CENTER);
            String sub = safe(subtitle);
            if (sub.length() > 28) sub = sub.substring(0, 28) + "…";
            drawCardText(canvas, sub, cx, y + dp(20), dp(10), Color.rgb(192, 205, 224), true, Paint.Align.CENTER);
        }

        private void drawCornerLogo(Canvas canvas, float cx, float cy, float size, int accent, boolean left) {
            if (teamLogo != null) {
                RectF box = new RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
                paint.setAlpha(168);
                drawBitmapFitCenter(canvas, teamLogo, box, false, 0);
                paint.setAlpha(255);
            } else {
                drawCardText(canvas, safe(c.player == null ? "" : c.player.teamAbbr), cx, cy + dp(4), dp(14), softColor(accent, .12f), true, Paint.Align.CENTER);
            }
        }

        private void drawMlbCornerMark(Canvas canvas, float cx, float cy, float size, int accent) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.4f));
            paint.setColor(Color.argb(92, Color.red(accent), Color.green(accent), Color.blue(accent)));
            canvas.drawRoundRect(new RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), dp(18), dp(18), paint);
            drawCardText(canvas, "MLB", cx, cy - dp(2), dp(13), Color.rgb(208, 224, 244), true, Paint.Align.CENTER);
            drawCardText(canvas, "AVG", cx, cy + dp(15), dp(9), Color.rgb(152, 172, 198), true, Paint.Align.CENTER);
        }

        private void drawPill(Canvas canvas, String text, float cx, float cy, float width, float height, int fill, int strokeColor, int textColor, float radius) {
            RectF r = new RectF(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(fill);
            canvas.drawRoundRect(r, radius, radius, paint);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1));
            stroke.setColor(strokeColor);
            canvas.drawRoundRect(r, radius, radius, stroke);
            drawCardText(canvas, text, cx, cy + dp(4), dp(9), textColor, true, Paint.Align.CENTER);
        }

        private void drawCardText(Canvas canvas, String text, float x, float y, float size, int color, boolean bold, Paint.Align align) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTypeface(bold ? tfBold : tfMedium);
            paint.setTextAlign(align);
            paint.setFontFeatureSettings("'tnum' 1");
            canvas.drawText(text == null ? "" : text, x, y, paint);
        }
    }

    class PlayerLensSummaryView extends View {
        final Comparison c;
        final ArrayList<Metric> lensMetrics;
        final TeamPalette palette;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private float animProgress = 0f;
        private boolean animStarted = false;

        PlayerLensSummaryView(Context context, Comparison c, ArrayList<Metric> lensMetrics, TeamPalette palette) {
            super(context);
            this.c = c;
            this.lensMetrics = lensMetrics == null ? new ArrayList<>() : lensMetrics;
            this.palette = palette == null ? defaultPalette() : palette;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!animStarted) {
                animStarted = true;
                ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
                anim.setDuration(540);
                anim.setInterpolator(new DecelerateInterpolator(2.0f));
                anim.addUpdateListener(a -> { animProgress = (float)a.getAnimatedValue(); invalidate(); });
                anim.start();
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            RectF card = new RectF(dp(1), dp(1), w - dp(1), h - dp(1));
            int teamAccent = boostNeonColor(readableTeamColor(palette.primary, palette.secondary, true), 1.12f, 1.04f);

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(card.left, card.top, card.right, card.bottom,
                    new int[] {
                            Color.rgb(5, 9, 17),
                            mixColor(teamAccent, Color.rgb(6, 10, 18), 0.76f),
                            Color.rgb(5, 9, 17)
                    }, null, Shader.TileMode.CLAMP));
            paint.setShadowLayer(dp(5), 0, dp(2), Color.argb(48, 0, 0, 0));
            canvas.drawRoundRect(card, dp(22), dp(22), paint);
            paint.clearShadowLayer();
            paint.setShader(null);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.argb(86, Color.red(teamAccent), Color.green(teamAccent), Color.blue(teamAccent)));
            canvas.drawRoundRect(card, dp(22), dp(22), paint);
            paint.setStyle(Paint.Style.FILL);

            Double pct = averageLensPercentile(c, lensMetrics);
            double signed = averageLensSignedEdge(c, lensMetrics);
            int sparkColor = profileSparkColor(signed);
            String lensName = currentLensNameForUi();
            String score = pct == null ? "—" : Math.round(Math.max(0, Math.min(100, pct))) + "%";
            String tier = pct == null ? "League context" : percentileTierLabel(pct);

            drawSmallText(canvas, "PLAYER LENS", card.left + dp(16), card.top + dp(24), dp(10), softColor(teamAccent, 0.18f), true, Paint.Align.LEFT);
            drawSmallText(canvas, lensName + " · vs league average", card.left + dp(16), card.top + dp(45), dp(15), Color.WHITE, true, Paint.Align.LEFT);
            drawSmallText(canvas, lensMetrics.size() + " scored stats", card.left + dp(16), card.top + dp(66), dp(10), Color.rgb(170, 186, 208), false, Paint.Align.LEFT);

            drawSmallText(canvas, score, card.right - dp(18), card.top + dp(41), dp(28), sparkColor, true, Paint.Align.RIGHT);
            drawSmallText(canvas, tier, card.right - dp(18), card.top + dp(63), dp(11), Color.rgb(220, 229, 242), true, Paint.Align.RIGHT);

            float railLeft = card.left + dp(18);
            float railRight = card.right - dp(18);
            float railY = card.top + dp(104);
            drawCenteredSparkRail(canvas, railLeft, railRight, railY, signed, sparkColor, true);

            drawSmallText(canvas, "Below avg", railLeft, railY + dp(30), dp(9), Color.rgb(163, 177, 198), false, Paint.Align.LEFT);
            drawSmallText(canvas, "League avg", (railLeft + railRight) / 2f, railY + dp(30), dp(9), Color.rgb(190, 202, 222), true, Paint.Align.CENTER);
            drawSmallText(canvas, "Better", railRight, railY + dp(30), dp(9), profilePositiveColor(), true, Paint.Align.RIGHT);
        }

        private void drawCenteredSparkRail(Canvas canvas, float left, float right, float y, double signed, int sparkColor, boolean large) {
            float cx = (left + right) / 2f;
            float travel = (right - left) / 2f - dp(12);
            float norm = (float)Math.max(-1d, Math.min(1d, signed));
            float sparkX = cx + travel * norm * animProgress;
            float abs = Math.abs(norm) * animProgress;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(large ? 8.0f : 6.4f));
            paint.setShader(new LinearGradient(left, y, right, y,
                    new int[] {
                            Color.argb(82, Color.red(profileNegativeColor()), Color.green(profileNegativeColor()), Color.blue(profileNegativeColor())),
                            Color.argb(76, 224, 232, 244),
                            Color.argb(82, Color.red(profilePositiveColor()), Color.green(profilePositiveColor()), Color.blue(profilePositiveColor()))
                    }, new float[] {0f, 0.50f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawLine(left, y, right, y, paint);
            paint.setShader(null);

            paint.setStrokeWidth(dp(1.2f));
            paint.setColor(Color.argb(178, 234, 240, 248));
            canvas.drawLine(cx, y - dp(13), cx, y + dp(13), paint);

            if (Math.abs(sparkX - cx) > dp(1.2f)) {
                paint.setStrokeWidth(dp(large ? 5.4f : 4.4f));
                paint.setShader(new LinearGradient(cx, y, sparkX, y,
                        new int[] {
                                Color.argb(52, Color.red(sparkColor), Color.green(sparkColor), Color.blue(sparkColor)),
                                Color.argb((int)(128 + 96 * abs), Color.red(sparkColor), Color.green(sparkColor), Color.blue(sparkColor)),
                                Color.argb((int)(196 + 42 * abs), Color.red(sparkColor), Color.green(sparkColor), Color.blue(sparkColor))
                        }, new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                paint.setShadowLayer(dp(3 + 3 * abs), 0, 0, Color.argb((int)(82 + 66 * abs), Color.red(sparkColor), Color.green(sparkColor), Color.blue(sparkColor)));
                canvas.drawLine(cx, y, sparkX, y, paint);
                paint.clearShadowLayer();
                paint.setShader(null);
            }

            drawSpark(canvas, sparkX, y, sparkColor, Math.max(0.18f, abs));
            paint.setStrokeCap(Paint.Cap.BUTT);
        }

        private void drawSpark(Canvas canvas, float x, float y, int color, float strength) {
            int hot = mixColor(Color.WHITE, color, 0.12f);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(x, y, dp(18 + 8 * strength),
                    new int[] {
                            Color.argb((int)(168 + 52 * strength), Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb((int)(58 + 40 * strength), Color.red(color), Color.green(color), Color.blue(color)),
                            Color.TRANSPARENT
                    }, new float[] {0f, 0.42f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, dp(18 + 8 * strength), paint);
            paint.setShader(null);
            paint.setColor(Color.rgb(6, 10, 18));
            canvas.drawCircle(x, y, dp(6.4f), paint);
            paint.setColor(hot);
            canvas.drawCircle(x, y, dp(4.4f), paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x - dp(1.2f), y - dp(1.2f), dp(1.25f), paint);
        }

        private void drawSmallText(Canvas canvas, String text, float x, float y, float size, int color, boolean bold, Paint.Align align) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTypeface(bold ? tfBold : tfMedium);
            paint.setTextAlign(align);
            paint.setFontFeatureSettings("'tnum' 1");
            canvas.drawText(text == null ? "" : text, x, y, paint);
        }
    }

    class LeagueSparkBarView extends View {
        final Metric metric;
        final Double value;
        final Double league;
        final Double percentile;
        final TeamPalette palette;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private float animProgress = 0f;
        private boolean animStarted = false;

        LeagueSparkBarView(Context context, Metric metric, Double value, Double league, Double percentile, TeamPalette palette) {
            super(context);
            this.metric = metric;
            this.value = value;
            this.league = league;
            this.percentile = percentile;
            this.palette = palette == null ? defaultPalette() : palette;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!animStarted) {
                animStarted = true;
                ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
                anim.setDuration(430);
                anim.setInterpolator(new DecelerateInterpolator(2.0f));
                anim.addUpdateListener(a -> { animProgress = (float)a.getAnimatedValue(); invalidate(); });
                anim.start();
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = dp(16);
            float right = getWidth() - dp(16);
            float y = dp(26);
            Double signedEdge = signedLeagueEdge(value, league, metric);
            if (signedEdge == null && percentile != null) signedEdge = (Math.max(0d, Math.min(100d, percentile)) - 50d) / 50d;
            if (signedEdge == null) signedEdge = 0d;
            int sparkColor = profileSparkColor(signedEdge);
            drawCenteredSparkRail(canvas, left, right, y, signedEdge, sparkColor);

            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(tfBold);
            paint.setTextSize(dp(8));
            paint.setFontFeatureSettings("'tnum' 1");
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(Color.rgb(155, 169, 191));
            canvas.drawText("Worse", left, y + dp(28), paint);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Color.rgb(196, 208, 226));
            canvas.drawText("Avg", (left + right) / 2f, y + dp(28), paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setColor(profilePositiveColor());
            canvas.drawText("Better", right, y + dp(28), paint);
        }

        private void drawCenteredSparkRail(Canvas canvas, float left, float right, float y, double signedEdge, int sparkColor) {
            float cx = (left + right) / 2f;
            float travel = (right - left) / 2f - dp(11);
            float norm = (float)Math.max(-1d, Math.min(1d, signedEdge));
            float sparkX = cx + travel * norm * animProgress;
            float abs = Math.abs(norm) * animProgress;
            int negative = profileNegativeColor();
            int positive = profilePositiveColor();

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(5.6f));
            paint.setShader(new LinearGradient(left, y, right, y,
                    new int[] {
                            Color.argb(66, Color.red(negative), Color.green(negative), Color.blue(negative)),
                            Color.argb(72, 222, 231, 244),
                            Color.argb(66, Color.red(positive), Color.green(positive), Color.blue(positive))
                    }, new float[] {0f, 0.50f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawLine(left, y, right, y, paint);
            paint.setShader(null);

            paint.setStrokeWidth(dp(1.1f));
            paint.setColor(Color.argb(170, 235, 242, 250));
            canvas.drawLine(cx, y - dp(10), cx, y + dp(10), paint);

            if (Math.abs(sparkX - cx) > dp(1.2f)) {
                paint.setStrokeWidth(dp(4.4f));
                paint.setShader(new LinearGradient(cx, y, sparkX, y,
                        new int[] {
                                Color.argb(36, Color.red(sparkColor), Color.green(sparkColor), Color.blue(sparkColor)),
                                Color.argb((int)(126 + 84 * abs), Color.red(sparkColor), Color.green(sparkColor), Color.blue(sparkColor)),
                                Color.argb((int)(190 + 36 * abs), Color.red(sparkColor), Color.green(sparkColor), Color.blue(sparkColor))
                        }, new float[] {0f, 0.58f, 1f}, Shader.TileMode.CLAMP));
                paint.setShadowLayer(dp(2.8f + 3.0f * abs), 0, 0, Color.argb((int)(72 + 70 * abs), Color.red(sparkColor), Color.green(sparkColor), Color.blue(sparkColor)));
                canvas.drawLine(cx, y, sparkX, y, paint);
                paint.clearShadowLayer();
                paint.setShader(null);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(sparkX, y, dp(13 + 6 * abs),
                    new int[] {
                            Color.argb((int)(150 + 62 * abs), Color.red(sparkColor), Color.green(sparkColor), Color.blue(sparkColor)),
                            Color.argb((int)(52 + 42 * abs), Color.red(sparkColor), Color.green(sparkColor), Color.blue(sparkColor)),
                            Color.TRANSPARENT
                    }, new float[] {0f, 0.44f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(sparkX, y, dp(13 + 6 * abs), paint);
            paint.setShader(null);
            paint.setColor(Color.rgb(6, 10, 18));
            canvas.drawCircle(sparkX, y, dp(5.5f), paint);
            paint.setColor(mixColor(Color.WHITE, sparkColor, 0.12f));
            canvas.drawCircle(sparkX, y, dp(3.8f), paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
        }
    }

    class ComparisonBarView extends View {
        final Metric metric;
        final Double[] values;
        final Double[] percentiles;
        final String thirdLabel;
        final TeamPalette palette;
        final String currentLabel;
        Bitmap currentIcon;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float animProgress = 0f;    // v29: marker animation (0 → 1)
        private boolean animStarted = false;

        ComparisonBarView(Context context, Metric metric, Double[] values, Double[] percentiles, String thirdLabel, TeamPalette palette, String currentLabel) {
            super(context);
            this.metric = metric;
            this.values = values;
            this.percentiles = percentiles;
            this.thirdLabel = thirdLabel == null ? "Career" : thirdLabel;
            this.palette = palette == null ? defaultPalette() : palette;
            this.currentLabel = currentLabel == null ? "" : currentLabel;
        }

        void setCurrentIcon(Bitmap bitmap) {
            this.currentIcon = bitmap;
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!animStarted) {
                animStarted = true;
                ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
                anim.setDuration(420);
                anim.setInterpolator(new DecelerateInterpolator(2.0f));
                anim.addUpdateListener(a -> {
                    animProgress = (float) a.getAnimatedValue();
                    invalidate();
                });
                anim.start();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            ArrayList<Double> valid = new ArrayList<>();
            for (Double v : values) if (v != null && !Double.isNaN(v)) valid.add(v);
            if (valid.isEmpty()) return;

            double[] domain = metricScaleDomain(metric, values);
            double lo = domain[0];
            double hi = domain[1];
            if (hi <= lo) { lo -= 1; hi += 1; }

            float left = dp(18);
            float right = getWidth() - dp(18);
            float y = dp(24);
            float h = dp(12);
            RectF track = new RectF(left, y, right, y + h);

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(Color.rgb(7, 12, 22));
            canvas.drawRoundRect(track, dp(10), dp(10), paint);

            LinearGradient gradient = new LinearGradient(left, y, right, y + h,
                    new int[] {
                            mixColor(boostNeonColor(palette.secondary, 1.06f, 1.02f), Color.rgb(8, 12, 20), 0.28f),
                            mixColor(boostNeonColor(palette.primary, 1.10f, 1.04f), Color.rgb(8, 12, 20), 0.24f),
                            mixColor(boostNeonColor(palette.secondary, 1.06f, 1.02f), Color.rgb(8, 12, 20), 0.30f)
                    },
                    new float[] { 0f, 0.55f, 1f }, Shader.TileMode.CLAMP);
            paint.setShader(gradient);
            canvas.drawRoundRect(track, dp(10), dp(10), paint);
            paint.setShader(null);

            paint.setColor(Color.argb(42, 255, 255, 255));
            for (int i = 1; i < 4; i++) {
                float gx = left + (right - left) * i / 4f;
                canvas.drawRoundRect(new RectF(gx - dp(1), y - dp(3), gx + dp(1), y + h + dp(3)), dp(1), dp(1), paint);
            }

            if (lo < 0 && hi > 0 && metric.key.equals("luck")) {
                float zx = (float) (left + ((0 - lo) / (hi - lo)) * (right - left));
                paint.setColor(Color.argb(210, 45, 54, 74));
                canvas.drawRoundRect(new RectF(zx - dp(1), y - dp(8), zx + dp(1), y + h + dp(8)), dp(2), dp(2), paint);
            }

            drawPercentileScale(canvas, left, right, y + h + dp(20));

            float[] xs = new float[values.length];
            for (int i = 0; i < values.length; i++) {
                Double v = values[i];
                if (v == null || Double.isNaN(v)) { xs[i] = -1; continue; }
                float x;
                if (percentiles != null && i < percentiles.length && percentiles[i] != null && !Double.isNaN(percentiles[i])) {
                    x = (float) (left + (Math.max(0, Math.min(100, percentiles[i])) / 100.0) * (right - left));
                } else {
                    x = (float) (left + ((v - lo) / (hi - lo)) * (right - left));
                }
                xs[i] = Math.max(left + dp(12), Math.min(right - dp(12), x));
            }

            // v29: Ease markers from league-average (50%) out to their final percentile
            float centerX = left + (right - left) * 0.5f;
            for (int i = 0; i < xs.length; i++) {
                if (xs[i] >= 0) xs[i] = centerX + (xs[i] - centerX) * animProgress;
            }

            if (xs.length > 1 && xs[1] >= 0) drawLeagueMarker(canvas, xs[1], y + h / 2);
            if (xs.length > 2 && xs[2] >= 0) drawCareerMarker(canvas, xs[2], y + h / 2);
            if (xs.length > 0 && xs[0] >= 0) drawCurrentMarker(canvas, xs[0], y + h / 2);
        }

        private void drawPercentileScale(Canvas canvas, float left, float right, float baseline) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(138, 151, 174));
            paint.setTextSize(dp(8));
            paint.setTypeface(tfBold);                  // v29: weight ladder
            paint.setFontFeatureSettings("'tnum' 1");   // v29: tabular figures
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("0%", left, baseline, paint);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("50%", (left + right) / 2f, baseline, paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("100%", right, baseline, paint);
        }

        private void drawCurrentMarker(Canvas canvas, float cx, float cy) {
            float w = dp(38), h = dp(34);
            RectF outer = new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
            int markerColor = boostNeonColor(palette.primary, 1.10f, 1.04f);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(7, 12, 22));
            canvas.drawRoundRect(new RectF(outer.left - dp(1), outer.top - dp(1), outer.right + dp(1), outer.bottom + dp(1)), dp(10), dp(10), paint);
            paint.setColor(mixColor(markerColor, Color.rgb(7, 12, 22), 0.72f));
            canvas.drawRoundRect(outer, dp(9), dp(9), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(softColor(markerColor, 0.08f));
            canvas.drawRoundRect(outer, dp(9), dp(9), paint);
            paint.setStyle(Paint.Style.FILL);
            RectF inner = new RectF(outer.left + dp(3), outer.top + dp(3), outer.right - dp(3), outer.bottom - dp(3));
            if (currentIcon != null) {
                drawBitmapFitCenter(canvas, currentIcon, inner, true, dp(6));
            } else {
                paint.setColor(mixColor(markerColor, Color.rgb(7, 12, 22), 0.52f));
                canvas.drawRoundRect(inner, dp(6), dp(6), paint);
                paint.setColor(Color.WHITE);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(dp(8));
                paint.setTypeface(tfBold);  // v29
                canvas.drawText(initials(currentLabel), inner.centerX(), inner.centerY() + dp(3), paint);
            }
        }

        private void drawLeagueMarker(Canvas canvas, float cx, float cy) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(112, 218, 226, 242));
            canvas.drawRoundRect(new RectF(cx - dp(1.15f), cy - dp(12), cx + dp(1.15f), cy + dp(12)), dp(2), dp(2), paint);
            paint.setColor(Color.argb(80, 255, 255, 255));
            canvas.drawCircle(cx, cy, dp(3.2f), paint);
        }

        private void drawCareerMarker(Canvas canvas, float cx, float cy) {
            float r = dp(13);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(6, 10, 18));
            canvas.drawCircle(cx, cy, r + dp(2), paint);
            paint.setColor(mixColor(boostNeonColor(palette.secondary, 1.08f, 1.03f), Color.rgb(7, 12, 22), 0.58f));
            canvas.drawCircle(cx, cy, r + dp(1), paint);
            paint.setColor(Color.rgb(8, 13, 23));
            canvas.drawCircle(cx, cy, r - dp(1), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(palette.secondary);
            canvas.drawCircle(cx, cy, r - dp(1), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(palette.secondary);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(13));
            paint.setTypeface(tfBold);  // v29
            canvas.drawText(thirdLabel.equals("Hist") ? "H" : "★", cx, cy + dp(4), paint);
        }
    }


    // ── v30.1: Advantage bar — proportional split showing stat-win ratio ──────────────────────────

    class AdvantageBarView extends View {
        final int aWins, bWins;
        final TeamPalette paletteA, paletteB;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        AdvantageBarView(Context ctx, int aWins, int bWins, TeamPalette paletteA, TeamPalette paletteB) {
            super(ctx);
            this.aWins = aWins; this.bWins = bWins;
            this.paletteA = paletteA; this.paletteB = paletteB;
        }
        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            int total = aWins + bWins;
            paint.setStyle(Paint.Style.FILL);
            if (total == 0) {
                paint.setColor(Color.argb(50, 200, 215, 235));
                canvas.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, paint);
                return;
            }
            float split = w * ((float) aWins / total);
            if (split > dp(4)) { paint.setColor(paletteA.primary); canvas.drawRoundRect(new RectF(0, 0, split, h), h / 2, h / 2, paint); }
            if (split < w - dp(4)) { paint.setColor(paletteB.primary); canvas.drawRoundRect(new RectF(split, 0, w, h), h / 2, h / 2, paint); }
        }
    }

    // ── v30: Stat Duel overview — animated outward-fill bars per stat ─────────────────────────────

    class StatDuelView extends View {
        final HeadToHeadComparison h;
        final Metric[] rowMetrics;
        final TeamPalette paletteA, paletteB;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float animProgress = 0f;
        private boolean animStarted = false;

        StatDuelView(Context context, HeadToHeadComparison h, Metric[] rowMetrics, TeamPalette paletteA, TeamPalette paletteB) {
            super(context);
            this.h = h;
            this.rowMetrics = rowMetrics;
            this.paletteA = paletteA == null ? defaultPalette() : paletteA;
            this.paletteB = paletteB == null ? defaultPalette() : paletteB;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!animStarted) {
                animStarted = true;
                ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
                anim.setDuration(540);
                anim.setInterpolator(new DecelerateInterpolator(2.0f));
                anim.addUpdateListener(a -> { animProgress = (float) a.getAnimatedValue(); invalidate(); });
                anim.start();
            }
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), rowMetrics.length * dp(50) + dp(6));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float padH = dp(4);
            float trackLeft  = padH;
            float trackRight = w - padH;
            float halfW = (trackRight - trackLeft) / 2f;
            float cx = trackLeft + halfW;

            for (int i = 0; i < rowMetrics.length; i++) {
                Metric m = rowMetrics[i];
                Double pctA = h.percentileA == null ? null : h.percentileA.get(m.key);
                Double pctB = h.percentileB == null ? null : h.percentileB.get(m.key);
                Double valA = h.statsA.get(m.key);
                Double valB = h.statsB.get(m.key);

                float rowTop = i * dp(50);
                float textY  = rowTop + dp(17);
                float trackY = rowTop + dp(27);
                float trackH = dp(6);
                float pctY   = rowTop + dp(43);

                boolean aLeads = false, bLeads = false;
                if (valA != null && valB != null) {
                    double d = valA - valB;
                    if (Math.abs(d) > 0.000001) {
                        aLeads = m.higherGood == null ? d > 0 : (m.higherGood ? d > 0 : d < 0);
                        bLeads = !aLeads;
                    }
                }

                // Track background — subtle on dark card
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(45, 200, 215, 240));
                canvas.drawRoundRect(new RectF(trackLeft, trackY, trackRight, trackY + trackH), dp(3), dp(3), paint);

                int aC = paletteA.primary;
                int bC = paletteB.primary;

                // A fill: LEFT edge → pctA% of left half, alpha-fading start
                if (pctA != null) {
                    float pN = (float) Math.max(0, Math.min(100, pctA));
                    float fillEnd = trackLeft + (pN / 100f) * halfW * animProgress;
                    if (fillEnd > trackLeft + dp(2)) {
                        LinearGradient ga = new LinearGradient(trackLeft, 0, fillEnd, 0,
                            new int[] { Color.argb(80, Color.red(aC), Color.green(aC), Color.blue(aC)),
                                        Color.argb(255, Color.red(aC), Color.green(aC), Color.blue(aC)) },
                            null, Shader.TileMode.CLAMP);
                        paint.setShader(ga);
                        canvas.drawRoundRect(new RectF(trackLeft, trackY, fillEnd, trackY + trackH), dp(3), dp(3), paint);
                        paint.setShader(null);
                    }
                }

                // B fill: RIGHT edge ← pctB% of right half, alpha-fading end
                if (pctB != null) {
                    float pN = (float) Math.max(0, Math.min(100, pctB));
                    float fillStart = trackRight - (pN / 100f) * halfW * animProgress;
                    if (fillStart < trackRight - dp(2)) {
                        LinearGradient gb = new LinearGradient(fillStart, 0, trackRight, 0,
                            new int[] { Color.argb(255, Color.red(bC), Color.green(bC), Color.blue(bC)),
                                        Color.argb(80, Color.red(bC), Color.green(bC), Color.blue(bC)) },
                            null, Shader.TileMode.CLAMP);
                        paint.setShader(gb);
                        canvas.drawRoundRect(new RectF(fillStart, trackY, trackRight, trackY + trackH), dp(3), dp(3), paint);
                        paint.setShader(null);
                    }
                }

                // Center tick
                paint.setColor(Color.argb(130, 200, 215, 240));
                canvas.drawRoundRect(new RectF(cx - dp(1), trackY - dp(2), cx + dp(1), trackY + trackH + dp(2)), dp(1), dp(1), paint);

                // A value — full color if winning, 100 alpha if losing
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setTypeface(tfBold);
                paint.setFontFeatureSettings("'tnum' 1");
                paint.setTextSize(dp(aLeads ? 15 : 12));
                paint.setTextAlign(Paint.Align.LEFT);
                paint.setColor(aLeads ? aC : Color.argb(100, Color.red(aC), Color.green(aC), Color.blue(aC)));
                canvas.drawText(valA == null ? "—" : format(valA, m), trackLeft + dp(2), textY, paint);

                // Metric label — muted white on dark background
                paint.setTypeface(tfBold);
                paint.setTextSize(dp(9));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setColor(Color.argb(100, 200, 212, 232));
                paint.setLetterSpacing(0.06f);
                canvas.drawText(m.label.toUpperCase(Locale.US), cx, textY, paint);
                paint.setLetterSpacing(0f);

                // B value — full color if winning, 100 alpha if losing
                paint.setTextSize(dp(bLeads ? 15 : 12));
                paint.setTypeface(tfBold);
                paint.setTextAlign(Paint.Align.RIGHT);
                paint.setColor(bLeads ? bC : Color.argb(100, Color.red(bC), Color.green(bC), Color.blue(bC)));
                canvas.drawText(valB == null ? "—" : format(valB, m), trackRight - dp(2), textY, paint);

                // Percentile % — visible for winner, subdued for loser
                paint.setTypeface(tfRegular);
                paint.setTextSize(dp(8));
                paint.setTextAlign(Paint.Align.LEFT);
                paint.setColor(aLeads ? Color.argb(200, Color.red(aC), Color.green(aC), Color.blue(aC))
                                      : Color.argb(80, Color.red(aC), Color.green(aC), Color.blue(aC)));
                if (pctA != null) canvas.drawText(Math.round(pctA) + "%", trackLeft + dp(2), pctY, paint);

                paint.setTextAlign(Paint.Align.RIGHT);
                paint.setColor(bLeads ? Color.argb(200, Color.red(bC), Color.green(bC), Color.blue(bC))
                                      : Color.argb(80, Color.red(bC), Color.green(bC), Color.blue(bC)));
                if (pctB != null) canvas.drawText(Math.round(pctB) + "%", trackRight - dp(2), pctY, paint);

                // Row divider — very subtle on dark
                if (i < rowMetrics.length - 1) {
                    paint.setShader(null);
                    paint.setColor(Color.argb(25, 200, 215, 240));
                    canvas.drawLine(trackLeft + dp(8), rowTop + dp(49), trackRight - dp(8), rowTop + dp(49), paint);
                }
            }
        }
    }

    // ── v29: Shimmer skeleton loading view ────────────────────────────────────────────────────────

    class SkeletonLoadingView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float shimmerPhase = 0f;
        private ValueAnimator shimmerAnim;

        SkeletonLoadingView(Context context) { super(context); }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            shimmerAnim = ValueAnimator.ofFloat(0f, 1f);
            shimmerAnim.setDuration(1300);
            shimmerAnim.setRepeatCount(ValueAnimator.INFINITE);
            shimmerAnim.setInterpolator(new LinearInterpolator());
            shimmerAnim.addUpdateListener(a -> {
                shimmerPhase = (float) a.getAnimatedValue();
                invalidate();
            });
            shimmerAnim.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (shimmerAnim != null) { shimmerAnim.cancel(); shimmerAnim = null; }
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), dp(560));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            if (w <= 0) return;

            // Shimmer band sweeps left-to-right, repeating
            float bandW = w * 0.55f;
            float bandStart = -bandW + (w + bandW) * shimmerPhase;
            LinearGradient shimmer = new LinearGradient(
                bandStart, 0, bandStart + bandW, 0,
                new int[] {
                    Color.argb(0,   120, 190, 255),
                    Color.argb(105, 170, 220, 255),
                    Color.argb(0,   120, 190, 255)
                },
                new float[] { 0f, 0.5f, 1f },
                Shader.TileMode.CLAMP
            );

            float px  = dp(4);   // horizontal padding matching form card
            float iy  = dp(8);   // current y cursor

            // ── Header card placeholder ──────────────────────────────────────────
            float cardBot = iy + dp(198);
            skelCard(canvas, px, iy, w - px, cardBot, dp(20), shimmer);

            float ax = px + dp(14);  // avatar / content left edge
            float tx = px + dp(84);  // text column x

            // Avatar circle
            skelItem(canvas, ax, iy + dp(14), ax + dp(60), iy + dp(74), dp(30), shimmer);

            // Name bar
            skelItem(canvas, tx, iy + dp(18), tx + dp(155), iy + dp(32), dp(7), shimmer);
            // Meta bar
            skelItem(canvas, tx, iy + dp(38), tx + dp(105), iy + dp(48), dp(5), shimmer);
            // Stat pills row
            skelItem(canvas, tx,         iy + dp(55), tx + dp(68),  iy + dp(74), dp(11), shimmer);
            skelItem(canvas, tx + dp(76), iy + dp(55), tx + dp(144), iy + dp(74), dp(11), shimmer);

            // Accent bar (like the gradient stripe on baseball card)
            skelItem(canvas, ax, iy + dp(90), w - px - dp(14), iy + dp(95), dp(4), shimmer);

            // Card table section header
            skelItem(canvas, ax, iy + dp(103), ax + dp(115), iy + dp(113), dp(5), shimmer);

            // Three table rows
            float rh = dp(22), rg = dp(5);
            for (int i = 0; i < 3; i++) {
                float ry = iy + dp(120) + (rh + rg) * i;
                skelItem(canvas, ax, ry, w - px - dp(14), ry + rh, dp(10), shimmer);
            }

            iy = cardBot + dp(10);

            // ── 4 metric row placeholders ────────────────────────────────────────
            for (int r = 0; r < 4; r++) {
                float rt  = iy;
                float rb  = rt + dp(84);
                float il  = px + dp(10);
                float ir  = w - px - dp(10);
                float mid = rt;

                skelCard(canvas, px, rt, w - px, rb, dp(16), shimmer);

                // Metric label
                skelItem(canvas, il, mid + dp(10), il + dp(115), mid + dp(23), dp(6), shimmer);
                // Percentile badge (top right)
                skelItem(canvas, ir - dp(54), mid + dp(8), ir, mid + dp(26), dp(9), shimmer);
                // Rank text
                skelItem(canvas, il, mid + dp(30), il + dp(165), mid + dp(40), dp(5), shimmer);
                // Percentile track bar
                skelItem(canvas, il, mid + dp(46), ir, mid + dp(58), dp(9), shimmer);
                // Three value columns
                float cw = (ir - il - dp(8)) / 3f;
                for (int c = 0; c < 3; c++) {
                    float cx = il + c * (cw + dp(4));
                    skelItem(canvas, cx, mid + dp(63), cx + cw, mid + dp(80), dp(8), shimmer);
                }

                iy = rb + dp(8);
            }
        }

        /** Full-card background (slightly darker base) */
        private void skelCard(Canvas canvas, float l, float t, float r, float b, float radius, LinearGradient shimmer) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(7, 13, 24));
            canvas.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
            paint.setShader(shimmer);
            canvas.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
            paint.setShader(null);
        }

        /** Inner element (slightly lighter) */
        private void skelItem(Canvas canvas, float l, float t, float r, float b, float radius, LinearGradient shimmer) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(22, 34, 54));
            canvas.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
            paint.setShader(shimmer);
            canvas.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
            paint.setShader(null);
        }
    }

    class TeamPickerAdapter extends BaseAdapter {
        final ArrayList<Team> data;
        final boolean primarySide;
        TeamPickerAdapter(ArrayList<Team> data, boolean primarySide) { this.data = data; this.primarySide = primarySide; }
        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int position) { return data.get(position); }
        @Override public long getItemId(int position) { return data.get(position).id; }
        @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
            Team t = data.get(position);
            TeamPalette palette = paletteForTeam(t);
            LinearLayout row;
            if (convertView instanceof LinearLayout) row = (LinearLayout) convertView;
            else {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(10), dp(10), dp(10));
            }
            row.removeAllViews();
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            row.setBackground(roundedGradient(new int[] {
                    softColor(palette.primary, 0.22f),
                    Color.rgb(7, 12, 22),
                    softColor(palette.secondary, 0.16f)
            }, 22));
            View logoShell = new FrameLayout(MainActivity.this);
            logoShell.setBackground(roundedStroke(softColor(palette.primary, 0.24f), Color.argb(92, 255, 255, 255), 32, 1));
            logoShell.setPadding(dp(3), dp(3), dp(3), dp(3));
            View logo = teamLogoView(t, 54);
            FrameLayout.LayoutParams innerLogoLp = new FrameLayout.LayoutParams(-1, -1);
            ((FrameLayout) logoShell).addView(logo, innerLogoLp);
            LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(58), dp(58));
            logoLp.setMargins(0, 0, dp(12), 0);
            row.addView(logoShell, logoLp);
            LinearLayout col = new LinearLayout(MainActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView title = text(t.name, 17, Color.WHITE, true);
            col.addView(title);
            col.addView(text((primarySide ? "Matchup side A" : "Matchup side B") + " · " + t.abbr, 11, Color.rgb(184, 198, 220), false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout chipWrap = new LinearLayout(MainActivity.this);
            chipWrap.setOrientation(LinearLayout.VERTICAL);
            chipWrap.setGravity(Gravity.END);
            TextView chip = text(t.abbr, 11, readableTeamColor(palette.primary, palette.secondary, true), true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(8), dp(4), dp(8), dp(4));
            chip.setBackground(roundedStroke(Color.argb(28, 255, 255, 255), Color.argb(92, 255, 255, 255), 12, 1));
            chipWrap.addView(chip);
            TextView side = text(primarySide ? "A" : "B", 10, Color.rgb(186, 200, 220), true);
            side.setPadding(dp(7), dp(4), dp(7), dp(4));
            side.setBackground(rounded(Color.argb(12, 255, 255, 255), 11));
            LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams(-2, -2);
            sideLp.topMargin = dp(6);
            chipWrap.addView(side, sideLp);
            row.addView(chipWrap);
            return row;
        }
    }

    class PlayerSuggestionAdapter extends BaseAdapter {
        final ArrayList<Player> data;
        PlayerSuggestionAdapter(ArrayList<Player> data) { this.data = data; }
        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int position) { return data.get(position); }
        @Override public long getItemId(int position) { return data.get(position).id; }
        @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
            Player p = data.get(position);
            LinearLayout row;
            if (convertView instanceof LinearLayout) row = (LinearLayout) convertView;
            else {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(9), dp(10), dp(9));
            }
            row.removeAllViews();
            TeamPalette palette = paletteForAbbr(p.teamAbbr);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            row.setBackground(roundedGradient(new int[] {
                    softColor(palette.primary, 0.18f),
                    Color.rgb(7, 12, 22),
                    softColor(palette.secondary, 0.12f)
            }, 22));
            FrameLayout avatar = new FrameLayout(MainActivity.this);
            avatar.setPadding(dp(3), dp(3), dp(3), dp(3));
            avatar.setBackground(roundedGradient(new int[] { softColor(palette.primary, 0.20f), softColor(palette.secondary, 0.26f) }, 34));
            ImageView img = new ImageView(MainActivity.this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setAdjustViewBounds(false);
            img.setBackground(rounded(Color.WHITE, 30));
            applyRoundedClip(img, 30);
            avatar.addView(img, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(68), dp(68));
            imgLp.setMargins(0, 0, dp(12), 0);
            row.addView(avatar, imgLp);
            loadPlayerImage(p.id, img);
            LinearLayout col = new LinearLayout(MainActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(text(p.fullName, 17, Color.WHITE, true));
            col.addView(text(p.teamAbbr + " · " + p.position + (isPitcher(p) ? " · Pitcher" : " · Hitter"), 11, Color.rgb(180, 194, 214), false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout chipWrap = new LinearLayout(MainActivity.this);
            chipWrap.setOrientation(LinearLayout.VERTICAL);
            chipWrap.setGravity(Gravity.END);
            TextView chip = text(isPitcher(p) ? "P" : "BAT", 10, isPitcher(p) ? Color.rgb(132, 188, 255) : readableTeamColor(palette.primary, palette.secondary, true), true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(8), dp(4), dp(8), dp(4));
            chip.setBackground(roundedStroke(Color.argb(28, 255, 255, 255), Color.argb(92, 255, 255, 255), 12, 1));
            chipWrap.addView(chip);
            TextView abbr = text(p.teamAbbr, 10, readableTeamColor(palette.primary, palette.secondary, true), true);
            abbr.setPadding(dp(7), dp(4), dp(7), dp(4));
            abbr.setBackground(rounded(Color.argb(12, 255, 255, 255), 11));
            LinearLayout.LayoutParams abbrLp = new LinearLayout.LayoutParams(-2, -2);
            abbrLp.topMargin = dp(6);
            chipWrap.addView(abbr, abbrLp);
            row.addView(chipWrap);
            return row;
        }
    }

    class HomeEnergyView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean ready = false;
        private int accentA = Color.rgb(247, 197, 77);
        private int accentB = Color.rgb(84, 142, 247);

        HomeEnergyView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setEnergy(boolean ready, int a, int b) {
            this.ready = ready;
            this.accentA = a;
            this.accentB = b;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            // v126: use the exact same centers as the home circle FrameLayouts so
            // rings, arcs, energy lanes, and the VS badge stay in register.
            float cy = dp(77);
            float vx = w * 0.50f;
            float leftCx = dp(75);
            float rightCx = w - dp(75);
            float circleR = dp(63);
            float vsR = dp(21);
            int mid = mixColor(accentA, accentB, .50f);

            // Let the outer shell own the main team-color gradient. Inside the card we only add
            // premium atmospheric glows so the selected team colors still dominate edge-to-edge.
            accentA = boostNeonColor(accentA, 1.18f, 1.10f);
            accentB = boostNeonColor(accentB, 1.18f, 1.10f);
            mid = boostNeonColor(mid, 1.08f, 1.05f);
            drawAura(canvas, leftCx, cy, circleR + dp(34), accentA, ready ? 82 : 34);
            drawAura(canvas, rightCx, cy, circleR + dp(34), accentB, ready ? 82 : 34);
            drawAura(canvas, vx, cy, dp(42), mid, ready ? 48 : 22);

            drawGlowRing(canvas, leftCx, cy, circleR + dp(4), accentA, ready);
            drawGlowRing(canvas, rightCx, cy, circleR + dp(4), accentB, ready);

            drawEnergyLane(canvas, leftCx + circleR - dp(2), cy, vx - vsR - dp(3), cy, accentA, mid, true);
            drawEnergyLane(canvas, vx + vsR + dp(3), cy, rightCx - circleR + dp(2), cy, accentB, mid, false);

            drawRingSparks(canvas, leftCx, cy, circleR + dp(8), accentA, true);
            drawRingSparks(canvas, rightCx, cy, circleR + dp(8), accentB, false);
            drawSplitVsHalo(canvas, vx, cy, accentA, accentB, ready);
        }

        private void drawAura(Canvas canvas, float cx, float cy, float r, int color, int alpha) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(cx, cy, r,
                    new int[] {
                            Color.argb(Math.max(0, alpha * 2 / 3), Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(Math.max(0, alpha / 2), Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(Math.max(0, alpha / 5), Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(Math.max(0, alpha / 12), Color.red(color), Color.green(color), Color.blue(color)),
                            Color.TRANSPARENT
                    },
                    new float[] { 0f, .18f, .44f, .72f, 1f }, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, p);
            p.setShader(null);
        }

        private void drawGlowRing(Canvas canvas, float cx, float cy, float r, int color, boolean active) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            RectF outer = new RectF(cx - r, cy - r, cx + r, cy + r);
            RectF inner = new RectF(cx - r + dp(4), cy - r + dp(4), cx + r - dp(4), cy + r - dp(4));

            p.setStrokeWidth(dp(10));
            p.setColor(Color.argb(active ? 88 : 36, Color.red(color), Color.green(color), Color.blue(color)));
            p.setShadowLayer(dp(14), 0, 0, Color.argb(active ? 230 : 110, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawOval(outer, p);

            p.setStrokeWidth(dp(5));
            p.setColor(Color.argb(active ? 238 : 146, Color.red(color), Color.green(color), Color.blue(color)));
            p.setShadowLayer(dp(10), 0, 0, Color.argb(active ? 246 : 132, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawOval(outer, p);

            p.setStrokeWidth(dp(1.8f));
            p.setColor(Color.argb(active ? 178 : 110, 255, 250, 232));
            p.setShadowLayer(dp(5), 0, 0, Color.argb(active ? 116 : 70, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawOval(inner, p);
            p.clearShadowLayer();
        }

        private void drawEnergyLane(Canvas canvas, float x1, float y1, float x2, float y2, int color, int blend, boolean left) {
            if (x2 <= x1) return;
            Path lane = new Path();
            lane.moveTo(x1, y1);
            float midX = (x1 + x2) / 2f;
            lane.cubicTo(midX, y1 - dp(left ? 8 : -8), midX, y2 + dp(left ? 6 : -6), x2, y2);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(dp(10.2f));
            p.setShadowLayer(dp(22), 0, 0, Color.argb(ready ? 255 : 148, Color.red(color), Color.green(color), Color.blue(color)));
            p.setShader(new LinearGradient(x1, y1, x2, y2,
                    new int[] {
                            Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(ready ? 240 : 148, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(ready ? 255 : 182, Color.red(blend), Color.green(blend), Color.blue(blend)),
                            Color.argb(0, Color.red(blend), Color.green(blend), Color.blue(blend))
                    },
                    new float[] { 0f, .34f, .82f, 1f }, Shader.TileMode.CLAMP));
            canvas.drawPath(lane, p);
            p.setShader(null);

            p.setStrokeWidth(dp(3f));
            int hotLane = mixColor(Color.WHITE, color, 0.18f);
            p.setColor(Color.argb(ready ? 210 : 132, Color.red(hotLane), Color.green(hotLane), Color.blue(hotLane)));
            p.setShadowLayer(dp(8), 0, 0, Color.argb(ready ? 174 : 82, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawPath(lane, p);

            Path spark = new Path();
            float span = x2 - x1;
            spark.moveTo(x1, y1);
            spark.lineTo(x1 + span * .16f, y1 + dp(left ? -5 : 5));
            spark.lineTo(x1 + span * .30f, y1 + dp(left ? 6 : -6));
            spark.lineTo(x1 + span * .48f, y1 + dp(left ? -4 : 4));
            spark.lineTo(x1 + span * .66f, y1 + dp(left ? 3 : -3));
            spark.lineTo(x1 + span * .82f, y1 + dp(left ? -2 : 2));
            spark.lineTo(x2, y2);
            p.setStrokeWidth(dp(1.9f));
            p.setColor(Color.argb(ready ? 222 : 146, Color.red(hotLane), Color.green(hotLane), Color.blue(hotLane)));
            p.setShadowLayer(dp(12), 0, 0, Color.argb(ready ? 216 : 104, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawPath(spark, p);
            p.clearShadowLayer();
        }

        private void drawRingSparks(Canvas canvas, float cx, float cy, float r, int color, boolean left) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            float[] base = left ? new float[] { 198f, 220f, 244f, 268f, 292f, 316f } : new float[] { -18f, -42f, -66f, -92f, -118f, -142f };
            for (int i = 0; i < base.length; i++) {
                float deg = base[i];
                float start = deg - 10f;
                float sweep = 20f;
                RectF arc = new RectF(cx - r - dp(9), cy - r - dp(9), cx + r + dp(9), cy + r + dp(9));
                p.setStrokeWidth(dp(i % 2 == 0 ? 3.0f : 2.0f));
                p.setColor(Color.argb(ready ? 224 : 136, Color.red(color), Color.green(color), Color.blue(color)));
                p.setShadowLayer(dp(14), 0, 0, Color.argb(ready ? 248 : 132, Color.red(color), Color.green(color), Color.blue(color)));
                canvas.drawArc(arc, start, sweep, false, p);

                double rad = Math.toRadians(deg);
                float x = cx + (float) Math.cos(rad) * (r + dp(14));
                float y = cy + (float) Math.sin(rad) * (r + dp(14));
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb(ready ? 245 : 154, 255, 250, 226));
                p.setShadowLayer(dp(11), 0, 0, Color.argb(ready ? 198 : 104, Color.red(color), Color.green(color), Color.blue(color)));
                canvas.drawCircle(x, y, dp(i % 2 == 0 ? 1.9f : 1.3f), p);
                p.setStyle(Paint.Style.STROKE);
            }
            p.clearShadowLayer();
        }

        private void drawSplitVsHalo(Canvas canvas, float cx, float cy, int leftColor, int rightColor, boolean active) {
            int blend = boostNeonColor(mixColor(leftColor, rightColor, .5f), 1.08f, 1.04f);
            float innerR = dp(28);
            float outerR = dp(39);

            drawAura(canvas, cx, cy, dp(active ? 62 : 46), blend, active ? 96 : 34);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            int[] sweepColors = new int[] { leftColor, leftColor, blend, rightColor, rightColor, blend, leftColor };
            float[] pos = new float[] { 0f, .20f, .49f, .50f, .78f, .98f, 1f };
            SweepGradient sg = new SweepGradient(cx, cy, sweepColors, pos);

            p.setStrokeWidth(dp(4.4f));
            p.setShader(sg);
            p.setShadowLayer(dp(14), 0, 0, Color.argb(active ? 210 : 98, Color.red(blend), Color.green(blend), Color.blue(blend)));
            canvas.drawCircle(cx, cy, innerR, p);

            p.setStrokeWidth(dp(1.6f));
            p.setColor(Color.argb(active ? 214 : 118, 255, 250, 230));
            p.setShader(null);
            p.setShadowLayer(dp(6), 0, 0, Color.argb(active ? 125 : 62, 255, 246, 220));
            canvas.drawCircle(cx, cy, innerR - dp(4), p);

            p.setStrokeWidth(dp(2.4f));
            p.setShader(sg);
            p.setShadowLayer(dp(10), 0, 0, Color.argb(active ? 150 : 72, Color.red(blend), Color.green(blend), Color.blue(blend)));
            canvas.drawArc(new RectF(cx - outerR, cy - outerR, cx + outerR, cy + outerR), 174, 34, false, p);
            canvas.drawArc(new RectF(cx - outerR, cy - outerR, cx + outerR, cy + outerR), -28, 34, false, p);
            p.setShader(null);

            // Centered little collision sparks around VS.
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.8f));
            p.setColor(Color.argb(active ? 230 : 132, 255, 248, 228));
            p.setShadowLayer(dp(8), 0, 0, Color.argb(active ? 160 : 80, Color.red(blend), Color.green(blend), Color.blue(blend)));
            Path leftSpark = new Path();
            leftSpark.moveTo(cx - dp(20), cy);
            leftSpark.lineTo(cx - dp(30), cy - dp(5));
            leftSpark.lineTo(cx - dp(38), cy + dp(3));
            leftSpark.lineTo(cx - dp(47), cy - dp(2));
            canvas.drawPath(leftSpark, p);
            Path rightSpark = new Path();
            rightSpark.moveTo(cx + dp(20), cy);
            rightSpark.lineTo(cx + dp(30), cy + dp(5));
            rightSpark.lineTo(cx + dp(38), cy - dp(3));
            rightSpark.lineTo(cx + dp(47), cy + dp(2));
            canvas.drawPath(rightSpark, p);
            p.clearShadowLayer();
        }
    }

    // UI helpers -------------------------------------------------------------------------------

    private boolean isBusy() {
        // v29: skeleton also counts as busy (blocks double-loads)
        return skeletonView != null || (loading != null && loading.getVisibility() == View.VISIBLE);
    }

    /** v29: Show shimmer skeleton in results area while profile data loads. */
    private void showProfileSkeleton() {
        if (resultsBox != null) resultsBox.setBackgroundColor(Color.rgb(2, 6, 13));
        if (headerBox != null) headerBox.removeAllViews();
        if (metricBox != null) metricBox.removeAllViews();
        if (skeletonView == null) {
            skeletonView = new SkeletonLoadingView(this);
            resultsBox.addView(skeletonView, 0, new LinearLayout.LayoutParams(-1, -2));
        }
        resultsBox.setVisibility(View.VISIBLE);
    }

    /** v29: Remove shimmer skeleton when real content is ready. */
    private void hideProfileSkeleton() {
        if (skeletonView != null) {
            resultsBox.removeView(skeletonView);
            skeletonView = null;
        }
    }

    private void setBusy(boolean busy, String message) {
        loading.setVisibility(busy ? View.VISIBLE : View.GONE);
        compareButton.setEnabled(!busy);
        standingsButton.setEnabled(!busy);
        if (singleViewButton != null) singleViewButton.setEnabled(!busy);
        if (headToHeadButton != null) headToHeadButton.setEnabled(!busy);
        if (message != null) statusView.setText(message);
    }
    private void showError(String message) {
        boolean hasMsg = message != null && !message.isEmpty();
        errorView.setText(hasMsg ? message : "");
        errorView.setVisibility(hasMsg ? View.VISIBLE : View.GONE);
        if (retryButton != null) retryButton.setVisibility(hasMsg ? View.VISIBLE : View.GONE);
    }
    private void hideKeyboard() {
        setBottomDockVisible(true);
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    private void showKeyboard(View v) {
        if (v == null) return;
        v.postDelayed(() -> {
            try {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
            } catch (Exception ignored) {}
        }, 120);
    }
    private TextView text(String value, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setTypeface(bold ? tfBold : tfRegular);  // v29: explicit weight ladder
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
    private GradientDrawable roundedGradientStroke(int[] colors, int radius, int strokeColor, int strokeDp) {
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        gd.setCornerRadius(dp(radius));
        gd.setStroke(dp(strokeDp), strokeColor);
        return gd;
    }

    private GradientDrawable roundedGradient(int[] colors, int radius) {
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        gd.setCornerRadius(dp(radius));
        return gd;
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    // ── Ripple helpers ──────────────────────────────────────────────────────────────────────────

    /** Returns a ripple drawable suitable for use as a view foreground. */
    private RippleDrawable ripple(boolean darkBackground) {
        int rippleColor = darkBackground
                ? Color.argb(56, 255, 255, 255)   // white ripple on dark bg
                : Color.argb(38, 10, 23, 55);      // navy ripple on light bg
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), null, null);
    }

    // ── Today's games badge ─────────────────────────────────────────────────────────────────────

    private void fetchTodayGames() {
        io.execute(() -> {
            try {
                String today = new SimpleDateFormat("MM/dd/yyyy", Locale.US).format(new Date());
                String url = "https://statsapi.mlb.com/api/v1/schedule?sportId=1&date=" + today;
                String text = httpGet(url);
                JSONObject root = new JSONObject(text);
                JSONArray dates = root.optJSONArray("dates");
                int total = 0;
                if (dates != null && dates.length() > 0) {
                    JSONObject dateObj = dates.getJSONObject(0);
                    total = dateObj.optInt("totalGames", 0);
                }
                int gameCount = total;
                main.post(() -> {
                    if (liveBadge == null) return;
                    if (gameCount > 0) {
                        liveBadge.setText(gameCount + " games today");
                        liveBadge.setBackground(roundedStroke(Color.argb(60, 13, 178, 163), Color.argb(140, 13, 178, 163), 14, 1));
                    } else {
                        liveBadge.setText("Off day");
                        liveBadge.setBackground(roundedStroke(Color.argb(40, 255, 255, 255), Color.argb(92, 255, 255, 255), 14, 1));
                    }
                });
            } catch (Exception ignored) {}
        });
    }

    // ── Recent players ───────────────────────────────────────────────────────────────────────────

    private static final int MAX_RECENTS = 5;
    private static final String PREFS_RECENTS = "statcast_recents_v28";

    private void saveToRecents(Player p) {
        if (p == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_RECENTS, MODE_PRIVATE);
        // Load existing as ordered list
        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < MAX_RECENTS; i++) {
            String id = prefs.getString("rid_" + i, null);
            String name = prefs.getString("rname_" + i, null);
            if (id != null && !String.valueOf(p.id).equals(id)) { ids.add(id); names.add(name); }
        }
        ids.add(0, String.valueOf(p.id));
        names.add(0, p.fullName);
        SharedPreferences.Editor ed = prefs.edit();
        for (int i = 0; i < Math.min(MAX_RECENTS, ids.size()); i++) {
            ed.putString("rid_" + i, ids.get(i));
            ed.putString("rname_" + i, names.get(i));
        }
        ed.apply();
    }

    private void rebuildRecentsBox() {
        if (recentsBox == null || allPlayers.isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_RECENTS, MODE_PRIVATE);
        ArrayList<Player> recents = new ArrayList<>();
        for (int i = 0; i < MAX_RECENTS; i++) {
            String idStr = prefs.getString("rid_" + i, null);
            if (idStr == null) break;
            try {
                int id = Integer.parseInt(idStr);
                Player p = playerById(id);
                if (p != null) recents.add(p);
            } catch (Exception ignored) {}
        }
        recentsBox.removeAllViews();
        if (recents.isEmpty()) { recentsBox.setVisibility(View.GONE); return; }
        recentsBox.setVisibility(View.VISIBLE);
        recentsBox.setOrientation(LinearLayout.VERTICAL);

        TextView recentLabel = text("Recent", 10, MUTED, true);
        recentLabel.setLetterSpacing(0.10f);
        recentLabel.setPadding(dp(2), 0, 0, dp(4));
        recentsBox.addView(recentLabel);

        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (Player p : recents) {
            Button chip = new Button(this);
            chip.setText(p.fullName.split(" ").length > 1 ? p.fullName.split(" ")[p.fullName.split(" ").length - 1] : p.fullName);
            chip.setAllCaps(false);
            chip.setTextSize(12);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setTextColor(NAVY);
            chip.setMinHeight(0); chip.setMinWidth(0);
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            chip.setBackground(roundedStroke(isDark ? Color.rgb(26, 38, 62) : Color.WHITE, isDark ? TEAL_DARK : LINE, 16, 1));
            chip.setForeground(ripple(isDark));
            chip.setContentDescription("Recent: " + p.fullName);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
            chipLp.setMargins(0, 0, dp(7), 0);
            chip.setOnClickListener(v -> {
                selectedPlayer = p;
                searchInput.setText("");
                searchInput.clearFocus();
                suggestionsList.setVisibility(View.GONE);
                applySmartDefaultForSelection(p);
                renderSelectionPreview();
                hideKeyboard();
                refreshAfterPrimarySelection();
            });
            chipRow.addView(chip, chipLp);
        }
        recentsBox.addView(chipRow, matchWrap());
    }

    // ── Metric glossary ──────────────────────────────────────────────────────────────────────────

    private void showMetricGlossary(Metric m) {
        String desc = metricDescription(m.key);
        if (desc.isEmpty()) return;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(14));
        panel.setBackground(roundedGradient(new int[] { Color.WHITE, Color.rgb(248, 251, 254) }, 22));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(m.label, 20, INK, true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView type = text(m.group == null ? "Stat" : m.group, 10, TEAL_DARK, true);
        type.setGravity(Gravity.CENTER);
        type.setPadding(dp(9), dp(4), dp(9), dp(4));
        type.setBackground(roundedStroke(softColor(TEAL_DARK, 0.92f), softColor(TEAL_DARK, 0.50f), 13, 1));
        top.addView(type);
        panel.addView(top, matchWrap());

        TextView body = text(desc, 14, Color.rgb(55, 65, 82), false);
        body.setLineSpacing(dp(3), 1.0f);
        body.setPadding(0, dp(10), 0, dp(14));
        panel.addView(body, matchWrap());

        TextView close = text("Done", 14, Color.WHITE, true);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(12), dp(9), dp(12), dp(9));
        close.setBackground(roundedGradient(new int[] { TEAL, TEAL_DARK }, 16));
        panel.addView(close, matchWrap());

        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setView(panel);
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private String metricDescription(String key) {
        switch (key) {
            case "avg":    return "Batting Average — hits divided by at-bats. The classic measure of how often a batter gets a hit.";
            case "obp":    return "On-Base Percentage — how often a batter reaches base via hit, walk, or hit-by-pitch. Better than AVG because walks matter.";
            case "slg":    return "Slugging Percentage — total bases divided by at-bats. Rewards extra-base hits more than AVG.";
            case "ops":    return "On-Base Plus Slugging — OBP + SLG. A quick all-in-one offensive metric. League average is around .720.";
            case "hr":
            case "teamHR": return "Home Runs — total home runs hit in the season.";
            case "rbi":    return "Runs Batted In — how many runs a batter drove in. Affected by teammates and lineup spot.";
            case "r":      return "Runs Scored — how many times the batter crossed home plate.";
            case "sb":     return "Stolen Bases — successful steals of a base.";
            case "wOBA":   return "Weighted On-Base Average — like OBP but weights each way of reaching base by its run value. League average is around .320. One of the best offensive rate stats.";
            case "xwOBA":  return "Expected wOBA — what wOBA would be based purely on exit velocity and launch angle, ignoring luck, defense, and ballpark. Predicts future performance better than actual wOBA.";
            case "luck":   return "Luck (wOBA − xwOBA) — the gap between what actually happened and what the contact quality predicted. Positive means outperforming the ball-tracking; negative means underperforming it.";
            case "xBA":    return "Expected Batting Average — what AVG should be based on exit velocity and launch angle. Filters out luck and defense.";
            case "xSLG":   return "Expected Slugging — what SLG should be based on contact quality. Great for spotting hitters due for a power surge or correction.";
            case "avgEV":  return "Average Exit Velocity — average speed off the bat in mph across all balls in play. Higher is harder contact. Elite is 92+ mph.";
            case "avgLA":  return "Average Launch Angle — average vertical angle off the bat in degrees. 10–30° is the ideal 'sweet spot' range for line drives and home runs.";
            case "hardHitPct": return "Hard-Hit % — share of batted balls hit at 95+ mph. A proxy for raw power and contact quality. League average ~38%.";
            case "barrelPct":  return "Barrel % — share of batted balls that were both hard (95+ mph) AND in the ideal launch angle range. Barrels produce a .900+ slugging rate. Elite is 10%+.";
            case "sweetSpotPct": return "Sweet-Spot % — balls hit at 8–32° launch angle. That window produces the most hits and extra bases. Roughly corresponds to line drives.";
            case "kPct":   return "Strikeout % — share of plate appearances ending in a strikeout. Lower is better for hitters. League average ~22%.";
            case "bbPct":  return "Walk % — share of plate appearances ending in a walk. Higher is better. League average ~8.5%.";
            case "era":    return "Earned Run Average — earned runs allowed per 9 innings. The classic pitching summary stat. League average ~4.20.";
            case "whip":   return "Walks + Hits per Inning Pitched — measures baserunner traffic allowed. Lower is better. League average ~1.30.";
            case "k9":     return "Strikeouts per 9 innings — how often a pitcher misses bats. Elite starters reach 10+.";
            case "bb9":    return "Walks per 9 innings — control and command. Lower is better.";
            case "kbb":    return "K/BB ratio — strikeouts per walk. Combines stuff and command. Anything above 3.0 is excellent.";
            case "pitchK": return "Strikeouts — total batters struck out in the season.";
            case "pitchBB": return "Walks issued — total batters walked in the season.";
            case "saves":  return "Saves — a reliever earns a save by finishing a close win under specific conditions. Primarily tracks closers.";
            case "ip":     return "Innings Pitched — total innings a pitcher worked. Reflects workload and durability.";
            default:       return "";
        }
    }

    // ── Sparkline (season trend) ─────────────────────────────────────────────────────────────────

    private void renderSparklineRow(LinearLayout parent, LinkedHashMap<Integer, Stats> seasons, Metric m, TeamPalette palette) {
        ArrayList<Integer> years = new ArrayList<>(seasons.keySet());
        if (years.size() > 1 && years.get(0) > years.get(years.size() - 1)) {
            Collections.reverse(years); // sparklines read oldest → newest, left to right
        }
        ArrayList<TrendPoint> pts = new ArrayList<>();
        for (int y : years) {
            Double v = seasons.get(y) == null ? null : seasons.get(y).get(m.key);
            pts.add(new TrendPoint(String.valueOf(y), v));
        }
        renderTrendPointRow(parent, "Trend · seasons", pts, m, palette, "years");
    }

    class SparklineView extends View {
        final ArrayList<Double> values;
        final ArrayList<String> labels;
        final Metric metric;
        final int lineColor;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SparklineView(Context ctx, ArrayList<Double> values, ArrayList<String> labels, Metric metric, int lineColor) {
            super(ctx);
            this.values = values;
            this.labels = labels;
            this.metric = metric;
            this.lineColor = lineColor;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int n = values.size();
            if (n < 2) return;
            float w = getWidth(), h = getHeight();
            float padL = dp(8), padR = dp(54), padT = dp(12), padB = dp(20);

            double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            for (Double v : values) {
                if (v != null && !Double.isNaN(v)) {
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
            }
            if (min == Double.MAX_VALUE) return;
            if (max == min) { max = min + Math.max(Math.abs(min) * 0.02, 0.001); min = min - Math.max(Math.abs(min) * 0.02, 0.001); }
            double span = max - min;
            min -= span * 0.08;
            max += span * 0.08;
            double mid = (min + max) / 2.0;

            float plotW = w - padL - padR;
            float plotH = h - padT - padB;

            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.rgb(232, 237, 246));
            for (int i = 0; i < 3; i++) {
                float y = padT + plotH * i / 2f;
                canvas.drawLine(padL, y, padL + plotW, y, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(8));
            paint.setTypeface(tfBold);                 // v29: weight ladder
            paint.setFontFeatureSettings("'tnum' 1");  // v29: tabular figures for axis values
            paint.setColor(Color.rgb(116, 128, 146));
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(format(max, metric), padL + plotW + dp(5), padT + dp(3), paint);
            canvas.drawText(format(mid, metric), padL + plotW + dp(5), padT + plotH / 2f + dp(3), paint);
            canvas.drawText(format(min, metric), padL + plotW + dp(5), padT + plotH + dp(3), paint);

            ArrayList<float[]> pts = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Double v = values.get(i);
                if (v == null || Double.isNaN(v)) continue;
                float x = padL + plotW * i / (n - 1);
                float y = padT + plotH - (float) ((v - min) / (max - min)) * plotH;
                pts.add(new float[]{x, y});
            }
            if (pts.size() < 2) return;

            Path area = new Path();
            area.moveTo(pts.get(0)[0], padT + plotH);
            for (float[] p : pts) area.lineTo(p[0], p[1]);
            area.lineTo(pts.get(pts.size() - 1)[0], padT + plotH);
            area.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, padT, 0, padT + plotH, Color.argb(52, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor)), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawPath(area, paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(lineColor);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            Path line = new Path();
            line.moveTo(pts.get(0)[0], pts.get(0)[1]);
            for (int i = 1; i < pts.size(); i++) line.lineTo(pts.get(i)[0], pts.get(i)[1]);
            canvas.drawPath(line, paint);

            // No point markers: the line represents a continuous rolling/cumulative trend.

            paint.setTextSize(dp(9));
            paint.setTypeface(tfBold);  // v29
            paint.setColor(Color.rgb(138, 151, 174));
            paint.setTextAlign(Paint.Align.LEFT);
            if (labels != null && !labels.isEmpty()) canvas.drawText(safe(labels.get(0)), padL, h - dp(3), paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            if (labels != null && !labels.isEmpty()) canvas.drawText(safe(labels.get(labels.size() - 1)), padL + plotW, h - dp(3), paint);
        }
    }

    // ── Share as image ───────────────────────────────────────────────────────────────────────────

    private void shareComparisonAsImage() {
        if (headerBox == null || headerBox.getChildCount() == 0) {
            Toast.makeText(this, "Run a profile first", Toast.LENGTH_SHORT).show();
            return;
        }
        // Render the profile card to a bitmap
        View card = headerBox.getChildAt(0);
        if (card.getWidth() <= 0) { copyCurrentTable(); return; }
        card.setDrawingCacheEnabled(true);
        card.buildDrawingCache(true);
        Bitmap src = card.getDrawingCache();
        if (src == null) { copyCurrentTable(); return; }
        Bitmap bmp = Bitmap.createBitmap(src);
        card.setDrawingCacheEnabled(false);

        try {
            // Write to app cache dir and share via file:// URI (API 24+ allows this for our own files)
            File shareDir = new File(getCacheDir(), "share");
            shareDir.mkdirs();
            File f = new File(shareDir, "statcast_card.png");
            FileOutputStream fos = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

            // For sharing we use MediaStore to get a content URI without needing FileProvider
            String inserted = android.provider.MediaStore.Images.Media.insertImage(
                    getContentResolver(), bmp,
                    "Statcast_" + (lastComparison != null ? lastComparison.name.replaceAll("[^A-Za-z0-9]", "_") : "card"),
                    "Statcast Compare card");
            Uri shareUri = inserted != null ? Uri.parse(inserted) : null;

            if (shareUri != null) {
                String caption = lastHeadToHead != null
                        ? lastHeadToHead.nameA + " vs " + lastHeadToHead.nameB + " · " + lastHeadToHead.season + " Statcast"
                        : (lastComparison != null ? lastComparison.name + " · " + lastComparison.season + " Statcast" : "Statcast Compare");
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("image/*");
                share.putExtra(Intent.EXTRA_STREAM, shareUri);
                share.putExtra(Intent.EXTRA_TEXT, caption);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share, "Share stat card"));
            } else {
                copyCurrentTable();
                Toast.makeText(this, "Copied as text instead", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            copyCurrentTable();
            Toast.makeText(this, "Copied comparison as text", Toast.LENGTH_SHORT).show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────

    static class TeamPalette {
        final int primary; final int secondary;
        TeamPalette(int primary, int secondary) { this.primary = primary; this.secondary = secondary; }
    }

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
        final String searchKey; // pre-lowercased for fast filtering
        Player(int id, String fullName, String teamName, String teamAbbr, String position) {
            this.id = id; this.fullName = fullName; this.teamName = teamName; this.teamAbbr = teamAbbr; this.position = position;
            this.searchKey = (fullName + " " + teamAbbr + " " + position).toLowerCase(Locale.US);
        }
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
        int pa = 0; int bbe = 0; double ip = 0; final Map<String, Double> vals = new HashMap<>();
        void put(String key, Double value) {
            if (key == null || key.isEmpty()) return;
            if (value == null || Double.isNaN(value)) return;
            vals.put(key, value);
        }
        void mergeFrom(Stats other) {
            if (other == null) return;
            if (other.pa > 0) pa = Math.max(pa, other.pa);
            if (other.bbe > 0) bbe = Math.max(bbe, other.bbe);
            if (other.ip > 0) ip = Math.max(ip, other.ip);
            for (Map.Entry<String, Double> e : other.vals.entrySet()) {
                Double value = e.getValue();
                if (value != null && !Double.isNaN(value)) {
                    Double old = vals.get(e.getKey());
                    if (isTeamCriticalCountKey(e.getKey()) && old != null && Math.abs(old) > 0.0000001d && Math.abs(value) < 0.0000001d) continue;
                    vals.put(e.getKey(), value);
                }
            }
        }
        private boolean isTeamCriticalCountKey(String key) {
            return "teamRunsScored".equals(key) || "teamRunsAllowed".equals(key) || "teamRunDiff".equals(key) ||
                    "teamRPG".equals(key) || "teamRAPG".equals(key) || "teamHits".equals(key) || "teamHR".equals(key) ||
                    "teamDoubles".equals(key) || "teamTriples".equals(key) || "teamXbh".equals(key) || "teamRBI".equals(key) ||
                    "teamSB".equals(key) || "teamTB".equals(key) || "teamWalks".equals(key) || "teamStrikeouts".equals(key) ||
                    "teamHitsAllowed".equals(key) || "teamHrAllowed".equals(key) || "teamWalksAllowed".equals(key) ||
                    "teamPitchStrikeouts".equals(key);
        }
        private String teamAliasKey(String key) {
            if (key == null) return null;
            if ("teamAVG".equals(key)) return "avg";
            if ("teamOBP".equals(key)) return "obp";
            if ("teamSLG".equals(key)) return "slg";
            if ("teamOPS".equals(key)) return "ops";
            if ("teamISO".equals(key)) return "iso";
            if ("teamBABIP".equals(key)) return "babip";
            if ("teamDoubles".equals(key)) return "doubles";
            if ("teamTriples".equals(key)) return "triples";
            if ("teamRBI".equals(key)) return "rbi";
            if ("teamSB".equals(key)) return "sb";
            if ("teamTB".equals(key)) return "tb";
            if ("teamKPct".equals(key)) return "kPct";
            if ("teamBBPct".equals(key)) return "bbPct";
            if ("teamBBMinusKPct".equals(key)) return "bbMinusKPct";
            if ("teamWhiffPct".equals(key)) return "whiffPct";
            if ("teamSwingPct".equals(key)) return "swingPct";
            if ("teamChasePct".equals(key)) return "chasePct";
            if ("teamZoneContactPct".equals(key)) return "zoneContactPct";
            if ("teamWOBA".equals(key)) return "wOBA";
            if ("teamXWOBA".equals(key)) return "xwOBA";
            if ("teamXBA".equals(key)) return "xBA";
            if ("teamXSLG".equals(key)) return "xSLG";
            if ("teamXOBP".equals(key)) return "xOBP";
            if ("teamXISO".equals(key)) return "xISO";
            if ("teamAvgEV".equals(key)) return "avgEV";
            if ("teamHardHitPct".equals(key)) return "hardHitPct";
            if ("teamBarrelPct".equals(key)) return "barrelPct";
            if ("teamSweetSpotPct".equals(key)) return "sweetSpotPct";
            if ("teamGbPct".equals(key)) return "gbPct";
            if ("teamFbPct".equals(key)) return "fbPct";
            if ("teamLdPct".equals(key)) return "ldPct";
            if ("teamPullPct".equals(key)) return "pullPct";
            if ("teamOppoPct".equals(key)) return "oppoPct";
            if ("teamERA".equals(key)) return "era";
            if ("teamWHIP".equals(key)) return "whip";
            if ("teamK9".equals(key)) return "k9";
            if ("teamBB9".equals(key)) return "bb9";
            if ("teamKBB".equals(key)) return "kbb";
            if ("teamPitchKPct".equals(key)) return "pitchKPct";
            if ("teamPitchBBPct".equals(key)) return "pitchBBPct";
            if ("teamPitchKMinusBBPct".equals(key)) return "pitchKMinusBBPct";
            if ("teamPXBA".equals(key)) return "pxBA";
            if ("teamPXSLG".equals(key)) return "pxSLG";
            if ("teamPWOBA".equals(key)) return "pwOBA";
            if ("teamPXWOBA".equals(key)) return "pxwOBA";
            if ("teamPAvgEV".equals(key)) return "pAvgEV";
            if ("teamPHardHitPct".equals(key)) return "pHardHitPct";
            if ("teamPBarrelPct".equals(key)) return "pBarrelPct";
            if ("teamPWhiffPct".equals(key)) return "pWhiffPct";
            if ("teamPChasePct".equals(key)) return "pChasePct";
            if ("teamPFirstStrikePct".equals(key)) return "pFirstStrikePct";
            if ("teamPZonePct".equals(key)) return "pZonePct";
            if ("teamPGbPct".equals(key)) return "pGbPct";
            if ("teamPFbPct".equals(key)) return "pFbPct";
            if ("teamPLdPct".equals(key)) return "pLdPct";
            return null;
        }
        Double get(String key) {
            if ("teamHR".equals(key)) {
                Double v = vals.get("teamHR");
                if (v == null || Math.abs(v) < 0.0000001d) {
                    Double hr = vals.get("hr");
                    if (hr == null || Math.abs(hr) < 0.0000001d) hr = vals.get("__hr");
                    if (hr != null && !Double.isNaN(hr)) return hr;
                }
                return v;
            }
            if ("teamRunsScored".equals(key)) {
                Double v = vals.get("teamRunsScored");
                if (v == null || Math.abs(v) < 0.0000001d) {
                    Double r = vals.get("r");
                    if (r == null || Math.abs(r) < 0.0000001d) r = vals.get("__runs");
                    if (r != null && !Double.isNaN(r)) return r;
                }
                return v;
            }
            if ("teamRunsAllowed".equals(key)) {
                Double v = vals.get("teamRunsAllowed");
                if (v == null || Math.abs(v) < 0.0000001d) {
                    Double pr = vals.get("__pr");
                    if (pr != null && !Double.isNaN(pr)) return pr;
                }
                return v;
            }
            if ("teamHrAllowed".equals(key)) {
                Double v = vals.get("teamHrAllowed");
                if (v == null || Math.abs(v) < 0.0000001d) {
                    Double phr = vals.get("pHrAllowed");
                    if (phr == null || Math.abs(phr) < 0.0000001d) phr = vals.get("__phr");
                    if (phr != null && !Double.isNaN(phr)) return phr;
                }
                return v;
            }
            String alias = teamAliasKey(key);
            if (alias != null) {
                Double v = vals.get(key);
                if (v != null && !Double.isNaN(v)) return v;
                Double a = get(alias);
                if (a != null && !Double.isNaN(a)) return a;
            }
            if ("luck".equals(key)) {
                Double woba = vals.get("wOBA");
                Double xwoba = vals.get("xwOBA");
                return woba == null || xwoba == null ? null : woba - xwoba;
            }
            if ("xISO".equals(key)) {
                Double xslg = vals.get("xSLG"), xba = vals.get("xBA");
                if (xslg != null && xba != null) return xslg - xba;
            }
            if ("bbMinusKPct".equals(key)) {
                Double bb = vals.get("bbPct"), k = vals.get("kPct");
                if (bb != null && k != null) return bb - k;
            }
            if ("pitchKMinusBBPct".equals(key)) {
                Double k = vals.get("pitchKPct"), bb = vals.get("pitchBBPct");
                if (k != null && bb != null) return k - bb;
            }
            if ("teamRunDiff".equals(key)) {
                Double rs = get("teamRunsScored"), ra = get("teamRunsAllowed");
                if (rs != null && ra != null) return rs - ra;
            }
            return vals.get(key);
        }
        boolean anyValue() { for (Double d : vals.values()) if (d != null && !Double.isNaN(d)) return true; return false; }
    }
    static class Metric {
        final String key, label, unit, type, group, side; final int decimals; final Boolean higherGood;
        Metric(String key, String label, String unit, int decimals, Boolean higherGood, String type, String group) {
            this(key, label, unit, decimals, higherGood, type, group, "hit");
        }
        Metric(String key, String label, String unit, int decimals, Boolean higherGood, String type, String group, String side) {
            this.key = key; this.label = label; this.unit = unit; this.decimals = decimals; this.higherGood = higherGood; this.type = type; this.group = group; this.side = side == null ? "hit" : side;
        }
        boolean isCount() { return "count".equals(type); }
    }
    static class Comparison {
        final boolean isTeam; final String name, meta; final int mlbId, season; final Stats seasonStats, careerStats, leagueStats; final Date updated; final String thirdLabel; final Player player; final Team team;
        final Map<String, Integer> rank, rankTotal; final Map<String, Double> percentile, thirdPercentile; final LinkedHashMap<Integer, Stats> recentSeasons; final Map<String, ArrayList<TrendPoint>> seasonTrends; final LinkedHashMap<String, Stats> recentWindows;
        Comparison(boolean isTeam, String name, String meta, String role, int mlbId, int season, Stats seasonStats, Stats careerStats, Stats leagueStats, Date updated, String thirdLabel, Player player, Team team) {
            this(isTeam, name, meta, role, mlbId, season, seasonStats, careerStats, leagueStats, updated, thirdLabel, player, team, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new LinkedHashMap<>(), new HashMap<>(), new LinkedHashMap<>());
        }
        Comparison(boolean isTeam, String name, String meta, String role, int mlbId, int season, Stats seasonStats, Stats careerStats, Stats leagueStats, Date updated, String thirdLabel, Player player, Team team, Map<String, Integer> rank, Map<String, Integer> rankTotal, Map<String, Double> percentile, Map<String, Double> thirdPercentile) {
            this(isTeam, name, meta, role, mlbId, season, seasonStats, careerStats, leagueStats, updated, thirdLabel, player, team, rank, rankTotal, percentile, thirdPercentile, new LinkedHashMap<>(), new HashMap<>(), new LinkedHashMap<>());
        }
        Comparison(boolean isTeam, String name, String meta, String role, int mlbId, int season, Stats seasonStats, Stats careerStats, Stats leagueStats, Date updated, String thirdLabel, Player player, Team team, Map<String, Integer> rank, Map<String, Integer> rankTotal, Map<String, Double> percentile, Map<String, Double> thirdPercentile, LinkedHashMap<Integer, Stats> recentSeasons) {
            this(isTeam, name, meta, role, mlbId, season, seasonStats, careerStats, leagueStats, updated, thirdLabel, player, team, rank, rankTotal, percentile, thirdPercentile, recentSeasons, new HashMap<>(), new LinkedHashMap<>());
        }
        Comparison(boolean isTeam, String name, String meta, String role, int mlbId, int season, Stats seasonStats, Stats careerStats, Stats leagueStats, Date updated, String thirdLabel, Player player, Team team, Map<String, Integer> rank, Map<String, Integer> rankTotal, Map<String, Double> percentile, Map<String, Double> thirdPercentile, LinkedHashMap<Integer, Stats> recentSeasons, Map<String, ArrayList<TrendPoint>> seasonTrends, LinkedHashMap<String, Stats> recentWindows) {
            this.isTeam = isTeam; this.name = name; this.meta = meta + (role == null || role.isEmpty() ? "" : " · " + role); this.mlbId = mlbId; this.season = season; this.seasonStats = seasonStats; this.careerStats = careerStats; this.leagueStats = leagueStats; this.updated = updated; this.thirdLabel = thirdLabel; this.player = player; this.team = team; this.rank = rank == null ? new HashMap<>() : rank; this.rankTotal = rankTotal == null ? new HashMap<>() : rankTotal; this.percentile = percentile == null ? new HashMap<>() : percentile; this.thirdPercentile = thirdPercentile == null ? new HashMap<>() : thirdPercentile; this.recentSeasons = recentSeasons == null ? new LinkedHashMap<>() : recentSeasons; this.seasonTrends = seasonTrends == null ? new HashMap<>() : seasonTrends; this.recentWindows = recentWindows == null ? new LinkedHashMap<>() : recentWindows;
        }
        String thirdLabelShort() { return isTeam ? "2015+" : "Career"; }
    }
    static class HeadToHeadComparison {
        final boolean isTeam; final String nameA, nameB, metaA, metaB; final int idA, idB, season; final Stats statsA, statsB, leagueStats; final Player playerA, playerB; final Team teamA, teamB; final Map<String, Integer> rankA, rankB, rankTotalA, rankTotalB; final Map<String, Double> percentileA, percentileB; final StatScope scope; final ArrayList<Metric> selectedMetricsSnapshot, keyEdgeMetricsSnapshot;
        HeadToHeadComparison(boolean isTeam, String nameA, String nameB, String metaA, String metaB, int idA, int idB, int season, Stats statsA, Stats statsB, Stats leagueStats, Player playerA, Player playerB, Team teamA, Team teamB, Map<String, Integer> rankA, Map<String, Integer> rankB) {
            this(isTeam, nameA, nameB, metaA, metaB, idA, idB, season, statsA, statsB, leagueStats, playerA, playerB, teamA, teamB, rankA, rankB, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), StatScope.BOTH, new ArrayList<>(), new ArrayList<>());
        }
        HeadToHeadComparison(boolean isTeam, String nameA, String nameB, String metaA, String metaB, int idA, int idB, int season, Stats statsA, Stats statsB, Stats leagueStats, Player playerA, Player playerB, Team teamA, Team teamB, Map<String, Integer> rankA, Map<String, Integer> rankB, Map<String, Integer> rankTotalA, Map<String, Integer> rankTotalB, Map<String, Double> percentileA, Map<String, Double> percentileB) {
            this(isTeam, nameA, nameB, metaA, metaB, idA, idB, season, statsA, statsB, leagueStats, playerA, playerB, teamA, teamB, rankA, rankB, rankTotalA, rankTotalB, percentileA, percentileB, StatScope.BOTH, new ArrayList<>(), new ArrayList<>());
        }
        HeadToHeadComparison(boolean isTeam, String nameA, String nameB, String metaA, String metaB, int idA, int idB, int season, Stats statsA, Stats statsB, Stats leagueStats, Player playerA, Player playerB, Team teamA, Team teamB, Map<String, Integer> rankA, Map<String, Integer> rankB, Map<String, Integer> rankTotalA, Map<String, Integer> rankTotalB, Map<String, Double> percentileA, Map<String, Double> percentileB, StatScope scope, ArrayList<Metric> selectedMetricsSnapshot, ArrayList<Metric> keyEdgeMetricsSnapshot) {
            this.isTeam = isTeam; this.nameA = nameA; this.nameB = nameB; this.metaA = metaA; this.metaB = metaB; this.idA = idA; this.idB = idB; this.season = season; this.statsA = statsA; this.statsB = statsB; this.leagueStats = leagueStats; this.playerA = playerA; this.playerB = playerB; this.teamA = teamA; this.teamB = teamB; this.rankA = rankA == null ? new HashMap<>() : rankA; this.rankB = rankB == null ? new HashMap<>() : rankB; this.rankTotalA = rankTotalA == null ? new HashMap<>() : rankTotalA; this.rankTotalB = rankTotalB == null ? new HashMap<>() : rankTotalB; this.percentileA = percentileA == null ? new HashMap<>() : percentileA; this.percentileB = percentileB == null ? new HashMap<>() : percentileB; this.scope = scope == null ? StatScope.BOTH : scope; this.selectedMetricsSnapshot = selectedMetricsSnapshot == null ? new ArrayList<>() : selectedMetricsSnapshot; this.keyEdgeMetricsSnapshot = keyEdgeMetricsSnapshot == null ? new ArrayList<>() : keyEdgeMetricsSnapshot;
        }
    }
    static class AllRankRow {
        final Metric metric; final int rank, total, playerId; final String subjectName, leaderName; final Double value, leaderValue; final Team team;
        AllRankRow(Metric metric, int rank, int total, String subjectName, Double value, String leaderName, Double leaderValue, int playerId, Team team) {
            this.metric = metric; this.rank = rank; this.total = total; this.subjectName = subjectName; this.value = value; this.leaderName = leaderName; this.leaderValue = leaderValue; this.playerId = playerId; this.team = team;
        }
    }
    static class TrendPoint {
        final String label; final Double value;
        TrendPoint(String label, Double value) { this.label = label; this.value = value; }
    }
    static class GameLogEntry {
        final Date date; final String label; final Stats stats;
        GameLogEntry(Date date, String label, Stats stats) { this.date = date; this.label = label; this.stats = stats; }
    }
    static class WeightedStatsBuilder {
        final Metric[] metrics; final boolean sumCounts; int pa = 0; int bbe = 0; double ip = 0; final Map<String, Double> sums = new HashMap<>(); final Map<String, Double> weights = new HashMap<>();
        static final String[] RAW_KEYS = new String[] { "__ab", "__h", "__tb", "__bb", "__hbp", "__sf", "__so", "__2b", "__3b", "__hr", "__runs", "__rbi", "__sb", "__games", "__pip", "__bf", "__pk", "__pbb", "__ph", "__er", "__pr", "__phr", "__pgames" };
        WeightedStatsBuilder(Metric[] metrics) { this(metrics, false); }
        WeightedStatsBuilder(Metric[] metrics, boolean sumCounts) { this.metrics = metrics; this.sumCounts = sumCounts; }
        private static boolean isTeamPitchMetricKey(String key) {
            return key != null && (key.startsWith("teamP") || key.equals("teamERA") || key.equals("teamWHIP") || key.equals("teamK9") || key.equals("teamBB9") || key.equals("teamKBB") || key.equals("teamOppAvg") || key.equals("teamOppOps") || key.equals("teamRunsAllowed") || key.equals("teamRAPG") || key.equals("teamHitsAllowed") || key.equals("teamHrAllowed") || key.equals("teamWalksAllowed") || key.equals("teamPitchStrikeouts"));
        }
        void add(Stats s) {
            if (s == null) return;
            pa += s.pa; bbe += s.bbe; ip += s.ip;

            // Raw totals let game-log windows produce true period stats:
            // AVG = hits in window / AB in window, OPS = OBP+SLG for the window,
            // ERA/WHIP/K9/etc. use only the innings/events inside that window.
            for (String raw : RAW_KEYS) {
                Double rawVal = s.vals.get(raw);
                if (rawVal != null && !Double.isNaN(rawVal)) sums.put(raw, sums.getOrDefault(raw, 0.0) + rawVal);
            }

            for (Metric m : metrics) {
                if (m.key.equals("luck")) continue;
                Double val = s.get(m.key);
                if (val == null || Double.isNaN(val)) continue;
                if (m.isCount()) {
                    sums.put(m.key, sums.getOrDefault(m.key, 0.0) + val);
                    weights.put(m.key, weights.getOrDefault(m.key, 0.0) + (sumCounts ? 1.0 : 1.0));
                    continue;
                }
                double w;
                if ("pitch".equals(m.side) || isTeamPitchMetricKey(m.key)) w = s.ip > 0 ? s.ip : (s.pa > 0 ? s.pa : s.bbe);
                else w = (m.type.equals("expected") || m.key.equals("kPct") || m.key.equals("bbPct") || m.key.equals("bbMinusKPct") || m.key.startsWith("teamX") || m.key.startsWith("teamW") || m.type.equals("standard")) ? s.pa : s.bbe;
                if (w <= 0) w = s.pa > 0 ? s.pa : (s.bbe > 0 ? s.bbe : s.ip);
                if (w <= 0) continue;
                sums.put(m.key, sums.getOrDefault(m.key, 0.0) + val * w);
                weights.put(m.key, weights.getOrDefault(m.key, 0.0) + w);
            }
        }
        private double raw(String key) { return sums.getOrDefault(key, 0.0); }
        Stats build() {
            Stats s = new Stats(); s.pa = pa; s.bbe = bbe; s.ip = ip;
            for (String raw : RAW_KEYS) if (sums.containsKey(raw)) s.put(raw, sums.get(raw));

            double ab = raw("__ab"), hits = raw("__h"), tb = raw("__tb"), bb = raw("__bb"), hbp = raw("__hbp"), sf = raw("__sf"), so = raw("__so");
            double doubles = raw("__2b"), triples = raw("__3b"), hr = raw("__hr"), runs = raw("__runs"), rbi = raw("__rbi"), sb = raw("__sb"), games = raw("__games");
            double obpDen = ab + bb + hbp + sf;
            if (ab > 0) {
                s.put("avg", hits / ab);
                s.put("slg", tb / ab);
                s.put("iso", (tb - hits) / ab);
            }
            double babipDen = ab - so - hr + sf;
            if (babipDen > 0) s.put("babip", (hits - hr) / babipDen);
            if (obpDen > 0) s.put("obp", (hits + bb + hbp) / obpDen);
            Double obp = s.get("obp"), slg = s.get("slg");
            if (obp != null && slg != null) s.put("ops", obp + slg);
            s.put("h", hits);
            s.put("doubles", doubles);
            s.put("triples", triples);
            s.put("hr", hr);
            s.put("xbh", doubles + triples + hr);
            s.put("rbi", rbi);
            s.put("r", runs);
            s.put("sb", sb);
            s.put("bb", bb);
            s.put("so", so);
            s.put("tb", tb);
            s.put("teamRunsScored", runs);
            s.put("teamHits", hits);
            s.put("teamDoubles", doubles);
            s.put("teamTriples", triples);
            s.put("teamXbh", doubles + triples + hr);
            s.put("teamHR", hr);
            s.put("teamRBI", rbi);
            s.put("teamSB", sb);
            s.put("teamTB", tb);
            s.put("teamWalks", bb);
            s.put("teamStrikeouts", so);
            if (games > 0) s.put("teamRPG", runs / games);
            if (pa > 0) {
                s.put("kPct", so * 100.0 / pa);
                s.put("bbPct", bb * 100.0 / pa);
                s.put("bbMinusKPct", (bb - so) * 100.0 / pa);
            }

            double pip = raw("__pip"), pk = raw("__pk"), pbb = raw("__pbb"), ph = raw("__ph"), er = raw("__er"), pr = raw("__pr"), phr = raw("__phr"), pgames = raw("__pgames");
            if (pip > 0) {
                s.put("era", er * 9.0 / pip);
                s.put("whip", (ph + pbb) / pip);
                s.put("k9", pk * 9.0 / pip);
                s.put("bb9", pbb * 9.0 / pip);
                s.put("ip", pip);
            }
            if (pbb > 0) s.put("kbb", pk / pbb);
            else if (pk > 0) s.put("kbb", pk);
            if (raw("__bf") > 0) {
                double bf = raw("__bf");
                s.put("pitchKPct", pk * 100.0 / bf);
                s.put("pitchBBPct", pbb * 100.0 / bf);
                s.put("pitchKMinusBBPct", (pk - pbb) * 100.0 / bf);
            }
            s.put("pitchK", pk);
            s.put("pitchBB", pbb);
            s.put("pHitsAllowed", ph);
            s.put("pHrAllowed", phr);
            s.put("teamERA", s.get("era"));
            s.put("teamWHIP", s.get("whip"));
            s.put("teamK9", s.get("k9"));
            s.put("teamBB9", s.get("bb9"));
            s.put("teamKBB", s.get("kbb"));
            s.put("teamPitchKPct", s.get("pitchKPct"));
            s.put("teamPitchBBPct", s.get("pitchBBPct"));
            s.put("teamPitchKMinusBBPct", s.get("pitchKMinusBBPct"));
            s.put("teamRunsAllowed", pr);
            s.put("teamHitsAllowed", ph);
            s.put("teamHrAllowed", phr);
            s.put("teamWalksAllowed", pbb);
            s.put("teamPitchStrikeouts", pk);
            if (pgames > 0) s.put("teamRAPG", pr / pgames);
            Double rs = s.get("teamRunsScored"), ra = s.get("teamRunsAllowed");
            if (rs != null && ra != null) s.put("teamRunDiff", rs - ra);

            for (Metric m : metrics) {
                // Prefer raw-derived period rates when available.
                if (s.get(m.key) != null && (m.type.equals("standard") || m.type.equals("pitching") || m.type.equals("team") || m.key.equals("kPct") || m.key.equals("bbPct") || m.key.equals("bbMinusKPct") || m.key.equals("pitchKPct") || m.key.equals("pitchBBPct") || m.key.equals("pitchKMinusBBPct") || m.key.equals("ops"))) continue;
                Double w = weights.get(m.key);
                if (w == null || w <= 0) {
                    if (s.get(m.key) == null) s.put(m.key, null);
                } else if (m.isCount() && sumCounts) s.put(m.key, sums.get(m.key));
                else s.put(m.key, sums.get(m.key) / w);
            }
            return s;
        }
    }
    interface BitmapCallback { void onBitmap(Bitmap bitmap); }

}
