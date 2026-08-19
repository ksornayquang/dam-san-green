import { useEffect, useMemo, useRef, useState, type ChangeEvent, type ReactNode } from "react";
import {
  Bell,
  Camera,
  Check,
  ChevronRight,
  CircleUserRound,
  CloudSun,
  Home,
  Info,
  Download,
  Leaf,
  ListChecks,
  LocateFixed,
  LogOut,
  MapPin,
  Medal,
  Menu,
  Pause,
  Recycle,
  RotateCcw,
  Send,
  Settings,
  ShieldCheck,
  Sparkles,
  Trophy,
  UploadCloud,
  Volume2,
  VolumeX,
  WifiOff,
  X
} from "lucide-react";
import {
  createReport,
  loadProfile,
  login,
  logout,
  observeAuth,
  observeReports,
  updateReportStatus
} from "./lib/firebase";
import { analyzeImage, pointsForEstimatedWaste, uploadImage } from "./lib/api";
import { clearDraft, loadDraft, saveDraft } from "./lib/drafts";
import type { AiReview, Ranking, ReportDraft, TrashReport, TrashType, UserProfile } from "./types";
import CampusMap from "./components/CampusMap";

type View = "home" | "report" | "ranking" | "profile";
type InstallEvent = Event & { prompt: () => Promise<void>; userChoice: Promise<{ outcome: string }> };

const SCHOOL = { lat: 12.900868056693273, lon: 108.2911159047231 };
const MAX_DISTANCE = 500;
const ASSET = "/assets/";

const demoProfile: UserProfile = {
  uid: "demo-judge",
  className: "11A1",
  displayName: "Lớp 11A1",
  email: "demo@damsan.edu.vn",
  role: "student",
  demo: true
};

const initialDemoReports: TrashReport[] = [
  demoReport("demo-1", "12A1", 15, "Chai nhựa PET", 0.92, Date.now() - 42 * 60 * 1000),
  demoReport("demo-2", "11A1", 12, "Lon nước nhôm", 0.58, Date.now() - 86 * 60 * 1000),
  demoReport("demo-3", "10A2", 9, "Giấy và bìa carton", 0.28, Date.now() - 3 * 60 * 60 * 1000),
  demoReport("demo-4", "12A1", 7, "Vỏ hộp xốp", 0.12, Date.now() - 5 * 60 * 60 * 1000),
  demoReport("demo-5", "11A1", 5, "Vỏ kẹo", 0.04, Date.now() - 7 * 60 * 60 * 1000)
];

function demoReport(id: string, className: string, points: number, name: string, kg: number, timestamp: number): TrashReport {
  return {
    id,
    className,
    reporterName: "Học sinh Đam San",
    imageUrl: `${ASSET}img_placeholder_upload.png`,
    image_before_url: `${ASSET}img_placeholder_upload.png`,
    image_after_url: `${ASSET}img_placeholder_upload.png`,
    trash_type: name.includes("Chai") || name.includes("Lon") || name.includes("Giấy") ? "recyclable" : "household",
    latitude: SCHOOL.lat,
    longitude: SCHOOL.lon,
    timestamp,
    address: "Trường PTDTNT THPT Đam San",
    demoMode: false,
    points,
    status: "approved",
    aiIsTrash: true,
    aiTrashName: name,
    aiCategory: name.includes("Chai") || name.includes("Lon") || name.includes("Giấy") ? "RECYCLABLE" : "NON_RECYCLABLE",
    aiReviewStatus: "auto_approved",
    aiEstimatedKg: kg,
    aiReason: "Ảnh minh chứng hợp lệ",
    aiAutoApproved: true,
    aiAnalyzedAt: timestamp
  };
}

function App() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [authReady, setAuthReady] = useState(false);
  const [reports, setReports] = useState<TrashReport[]>([]);
  const [view, setView] = useState<View>("home");
  const [toast, setToast] = useState("");
  const [soundEnabled, setSoundEnabled] = useState(() => localStorage.getItem("dsg-sound") !== "off");
  const [installEvent, setInstallEvent] = useState<InstallEvent | null>(null);
  const [showMenu, setShowMenu] = useState(false);

  useEffect(() => {
    const onInstall = (event: Event) => {
      event.preventDefault();
      setInstallEvent(event as InstallEvent);
    };
    window.addEventListener("beforeinstallprompt", onInstall);
    return () => window.removeEventListener("beforeinstallprompt", onInstall);
  }, []);

  useEffect(() => observeAuth(async (user) => {
    if (user) {
      try {
        setProfile(await loadProfile(user));
      } catch {
        setProfile({ uid: user.uid, className: "Unknown", displayName: "Người dùng", email: user.email ?? "", role: "student" });
      }
    } else if (sessionStorage.getItem("dsg-demo") === "true") {
      setProfile(demoProfile);
    } else {
      setProfile(null);
    }
    setAuthReady(true);
  }), []);

  useEffect(() => {
    if (!profile) return;
    if (profile.demo) {
      const local = JSON.parse(localStorage.getItem("dsg-demo-reports") || "[]") as TrashReport[];
      setReports([...local, ...initialDemoReports]);
      return;
    }
    return observeReports(setReports);
  }, [profile]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(""), 4200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const playSound = (kind: "tap" | "success" | "error" = "tap") => {
    if (!soundEnabled) return;
    try {
      const audio = new AudioContext();
      const oscillator = audio.createOscillator();
      const gain = audio.createGain();
      oscillator.connect(gain);
      gain.connect(audio.destination);
      oscillator.frequency.value = kind === "success" ? 760 : kind === "error" ? 190 : 420;
      gain.gain.setValueAtTime(0.05, audio.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, audio.currentTime + (kind === "success" ? 0.22 : 0.08));
      oscillator.start();
      oscillator.stop(audio.currentTime + (kind === "success" ? 0.22 : 0.08));
    } catch {
      // Browsers may block audio before the first gesture.
    }
  };

  const notify = (message: string, kind: "tap" | "success" | "error" = "tap") => {
    setToast(message);
    playSound(kind);
  };

  const handleLogout = async () => {
    sessionStorage.removeItem("dsg-demo");
    if (!profile?.demo) await logout();
    setProfile(null);
    setView("home");
  };

  const handleInstall = async () => {
    if (installEvent) {
      await installEvent.prompt();
      setInstallEvent(null);
    } else {
      notify("iPhone: mở bằng Safari, bấm Chia sẻ rồi chọn Thêm vào Màn hình chính.");
    }
  };

  if (!authReady) return <Splash />;
  if (!profile) {
    return <LoginScreen onDemo={() => {
      sessionStorage.setItem("dsg-demo", "true");
      setProfile(demoProfile);
    }} />;
  }

  const addDemoReport = (report: TrashReport) => {
    const current = JSON.parse(localStorage.getItem("dsg-demo-reports") || "[]") as TrashReport[];
    const next = [report, ...current].slice(0, 20);
    localStorage.setItem("dsg-demo-reports", JSON.stringify(next));
    setReports([...next, ...initialDemoReports]);
  };

  return (
    <div className="app-shell">
      <Header
        profile={profile}
        reports={reports}
        onMenu={() => setShowMenu(true)}
        onNotify={() => notify("Bạn không có thông báo mới.")}
      />
      <main className="page-stage">
        {view === "home" && <HomePage profile={profile} reports={reports} onReport={() => setView("report")} onRanking={() => setView("ranking")} />}
        {view === "report" && (
          <ReportPage
            profile={profile}
            onDone={(report) => {
              if (profile.demo) addDemoReport(report);
              notify(`Hoàn thành! Lớp ${report.className} được cộng ${report.points} điểm.`, "success");
              setView("home");
            }}
            notify={notify}
          />
        )}
        {view === "ranking" && <RankingPage reports={reports} />}
        {view === "profile" && (
          <ProfilePage
            profile={profile}
            reports={reports}
            soundEnabled={soundEnabled}
            onSound={(enabled) => {
              setSoundEnabled(enabled);
              localStorage.setItem("dsg-sound", enabled ? "on" : "off");
            }}
            onInstall={handleInstall}
            onLogout={handleLogout}
            notify={notify}
          />
        )}
      </main>
      {showMenu && (
        <AppMenu
          active={view}
          onClose={() => setShowMenu(false)}
          onChange={(next) => {
            playSound();
            setView(next);
            setShowMenu(false);
          }}
          onInstall={() => {
            setShowMenu(false);
            void handleInstall();
          }}
          onLogout={() => {
            setShowMenu(false);
            void handleLogout();
          }}
        />
      )}
      {toast && <div className="toast" role="status">{toast}</div>}
    </div>
  );
}

function Splash() {
  return (
    <div className="splash">
      <img src={`${ASSET}logo_damsan_green.png`} alt="Dam San Green" />
      <strong>Dam San Green</strong>
      <span>Đang kết nối hệ thống...</span>
    </div>
  );
}

function LoginScreen({ onDemo }: { onDemo: () => void }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const submit = async () => {
    if (!email || !password) return setError("Hãy nhập đầy đủ email và mật khẩu.");
    setBusy(true);
    setError("");
    try {
      await login(email.trim(), password);
    } catch {
      setError("Không đăng nhập được. Kiểm tra tài khoản lớp hoặc kết nối mạng.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login-screen">
      <section className="login-brand">
        <img src={`${ASSET}logo_damsan_green.png`} alt="Logo Dam San Green" />
        <div>
          <p className="eyebrow">TRƯỜNG PTDTNT THPT ĐAM SAN</p>
          <h1>Dam San Green</h1>
          <p>Biến mỗi hành động xanh thành minh chứng và điểm thi đua minh bạch.</p>
        </div>
      </section>
      <section className="login-panel">
        <div className="login-heading">
          <span className="icon-well"><Leaf size={22} /></span>
          <div><h2>Đăng nhập lớp</h2><p>Dùng tài khoản đã được Đoàn trường cấp</p></div>
        </div>
        <label>Email<input value={email} onChange={(e) => setEmail(e.target.value)} type="email" inputMode="email" placeholder="11a1@damsan.edu.vn" /></label>
        <label>Mật khẩu<input value={password} onChange={(e) => setPassword(e.target.value)} type="password" placeholder="••••••••" onKeyDown={(e) => e.key === "Enter" && void submit()} /></label>
        {error && <p className="form-error"><Info size={16} />{error}</p>}
        <button className="primary-button" onClick={() => void submit()} disabled={busy}>{busy ? "Đang đăng nhập..." : "ĐĂNG NHẬP"}<ChevronRight size={18} /></button>
        <div className="or"><span />hoặc<span /></div>
        <button className="demo-button" onClick={onDemo}><Sparkles size={18} />Xem bản trình diễn dành cho BGK</button>
        <p className="security-note"><ShieldCheck size={16} />Bản trình diễn không làm thay đổi dữ liệu thi đua thật.</p>
      </section>
    </div>
  );
}

function Header({ profile, reports, onMenu, onNotify }: { profile: UserProfile; reports: TrashReport[]; onMenu: () => void; onNotify: () => void }) {
  const points = calculateRankings(reports).find((item) => item.className === profile.className)?.totalPoints ?? 0;
  return (
    <header className="topbar">
      <div className="topbar-main">
        <div className="brand-lockup">
          <img src={`${ASSET}logo_damsan_green.png`} alt="Logo Đam San Green" />
          <div><strong>Trường PTDTNT THPT Đam San</strong><span>Dam San Green</span></div>
        </div>
        <div className="header-actions">
          <button className="icon-button bell-button" onClick={onNotify} aria-label="Thông báo"><img src={`${ASSET}ic_bell_3d.png`} alt="" /></button>
          <button className="icon-button menu-button" onClick={onMenu} aria-label="Mở menu"><Menu size={22} /></button>
        </div>
      </div>
      <div className="header-info-row">
        <span className="temperature"><CloudSun size={21} /><strong>24°C</strong></span>
        <span className="weather-chip">Mây đen u ám</span>
        <span className="location-chip"><img src={`${ASSET}ic_pin_3d.png`} alt="" />Ea Hiao, Đắk Lắk</span>
        <span className="header-points">{points} Điểm</span>
      </div>
    </header>
  );
}

function HomePage({ profile, reports, onReport, onRanking }: { profile: UserProfile; reports: TrashReport[]; onReport: () => void; onRanking: () => void }) {
  const [mapMode, setMapMode] = useState<"gps" | "3d">("3d");
  const [zoom, setZoom] = useState(1);
  const rankings = calculateRankings(reports);
  const approved = reports.filter((item) => item.status === "approved");
  const waste = approved.reduce((sum, item) => sum + (item.aiEstimatedKg || 0), 0);
  const classes = new Set(approved.map((item) => item.className)).size;

  return (
    <div className="home-page">
      <section className="campus-panel">
        <div className="map-mode" aria-label="Chế độ bản đồ">
          <button className={mapMode === "gps" ? "active" : ""} onClick={() => setMapMode("gps")}>GPS</button>
          <button className={mapMode === "3d" ? "active" : ""} onClick={() => setMapMode("3d")}>3D</button>
        </div>
        <div className={`map-school-label ${mapMode === "gps" ? "on-google-map" : ""}`}>
          <strong>Trường PTDTNT THPT Đam San</strong>
          <span>Điểm xuất phát · Hãy nhặt rác!</span>
        </div>
        <div className="map-zoom-controls">
          <button onClick={() => setZoom((value) => Math.min(1.28, value + .08))} aria-label="Phóng to"><img src={`${ASSET}ic_zoom_in_3d.png`} alt="" /></button>
          <button onClick={() => setZoom((value) => Math.max(.78, value - .08))} aria-label="Thu nhỏ"><img src={`${ASSET}ic_zoom_out_3d.png`} alt="" /></button>
        </div>
        <div className="campus-viewport">
          <CampusMap mode={mapMode} reports={reports} zoom={zoom} />
        </div>
      </section>

      <section className="ranking-preview" onClick={onRanking} role="button" tabIndex={0}>
        <img src={`${ASSET}ic_crown_gold_3d.png`} alt="" />
        <div><span>Bảng Xếp Hạng Lớp</span><strong><i>1</i>{rankings[0] ? rankings[0].className : "Đang tải..."}</strong></div>
        <div className="rank-score">{rankings[0]?.totalPoints ?? 0}<small>điểm</small></div>
        <ChevronRight size={21} />
      </section>

      <section className="impact-section">
        <div className="section-title"><span className="icon-well"><img src={`${ASSET}ic_leaf_3d.png`} alt="" /></span><div><h2>Tác động xanh</h2><p>Số liệu tự động từ các báo cáo đã duyệt</p></div></div>
        <div className="impact-grid">
          <Stat value={approved.length.toString()} label="lượt dọn" tone="green" />
          <Stat value={`${formatNumber(waste)} kg`} label="rác thu gom" tone="red" />
          <Stat value={`${formatNumber(waste * 1.8)} kg`} label="CO₂ tránh phát thải" tone="cyan" />
          <Stat value={classes.toString()} label="lớp tham gia" tone="gold" />
        </div>
      </section>

      <section className="mission-strip">
        <div className="mission-copy"><span><ListChecks size={18} />NHIỆM VỤ HÔM NAY</span><h2>Nhặt rác sân trường</h2><p>Thu gom rác vương vãi quanh sân và xác thực bằng hai ảnh.</p><div className="progress"><i style={{ width: `${Math.min(100, (approved.length % 3) * 33)}%` }} /></div></div>
        <button className="scan-button" onClick={onReport}><span>QUÉT RÁC</span><small>3–15 điểm</small><img src={`${ASSET}ic_camera_3d.png`} alt="" /></button>
      </section>
    </div>
  );
}

function CampusIllustration() {
  return (
    <div className="campus-art" aria-hidden="true">
      <div className="road road-one" /><div className="road road-two" />
      <div className="building building-a"><i /><i /><i /></div>
      <div className="building building-b"><i /><i /></div>
      <div className="building building-c"><i /><i /><i /></div>
      <div className="field"><span /><span /><span /></div>
      <img className="map-pin-3d" src={`${ASSET}ic_pin_3d.png`} alt="" />
      <div className="tree t1" /><div className="tree t2" /><div className="tree t3" /><div className="tree t4" /><div className="tree t5" />
    </div>
  );
}

function Stat({ value, label, tone }: { value: string; label: string; tone: string }) {
  return <div className={`stat-card ${tone}`}><strong>{value}</strong><span>{label}</span></div>;
}

function ReportPage({ profile, onDone, notify }: { profile: UserProfile; onDone: (report: TrashReport) => void; notify: (message: string, kind?: "tap" | "success" | "error") => void }) {
  const [draft, setDraft] = useState<ReportDraft>({ savedAt: Date.now(), reporterName: "", trashType: null, beforeFile: null, afterFile: null, latitude: null, longitude: null });
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState("Bắt đầu bằng ảnh rác tại hiện trường.");
  const [demoLocation, setDemoLocation] = useState(() => localStorage.getItem("dsg-location-demo") === "true" || !!profile.demo);
  const [showPin, setShowPin] = useState(false);
  const [pin, setPin] = useState("");
  const beforeRef = useRef<HTMLInputElement>(null);
  const afterRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    void loadDraft().then((saved) => {
      if (saved) {
        setDraft(saved);
        setStatus(saved.afterFile ? "Đã khôi phục đủ hai ảnh. Bạn có thể gửi báo cáo." : "Đã khôi phục bản nháp. Hãy tiếp tục khi tới thùng rác.");
      }
    });
  }, []);

  useEffect(() => {
    if (draft.beforeFile || draft.reporterName || draft.trashType) void saveDraft({ ...draft, savedAt: Date.now() });
  }, [draft]);

  const setPhoto = (stage: "before" | "after") => (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setDraft((current) => ({ ...current, [stage === "before" ? "beforeFile" : "afterFile"]: file, savedAt: Date.now() }));
    setStatus(stage === "before" ? "Ảnh hiện trạng đã lưu. Chọn loại rác để tiếp tục." : "Đã đủ hai ảnh xác thực. Kiểm tra và gửi báo cáo.");
  };

  const getLocation = () => {
    if (!navigator.geolocation) return notify("Thiết bị không hỗ trợ GPS.", "error");
    setStatus("Đang lấy tọa độ GPS...");
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setDraft((current) => ({ ...current, latitude: position.coords.latitude, longitude: position.coords.longitude }));
        setStatus(`Đã lấy GPS chính xác ±${Math.round(position.coords.accuracy)} m.`);
      },
      () => { setStatus("Không lấy được GPS. Hãy cấp quyền vị trí trong trình duyệt."); notify("Cần quyền GPS để gửi báo cáo.", "error"); },
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 30000 }
    );
  };

  const toggleDemo = () => {
    if (demoLocation && !profile.demo) {
      setDemoLocation(false);
      localStorage.setItem("dsg-location-demo", "false");
    } else if (profile.demo) {
      setDemoLocation(true);
    } else {
      setShowPin(true);
    }
  };

  const confirmPin = () => {
    if (pin === (import.meta.env.VITE_DEMO_PIN || "2026")) {
      setDemoLocation(true);
      localStorage.setItem("dsg-location-demo", "true");
      setShowPin(false);
      setPin("");
      notify("Đã bật chế độ BGK ngoài trường.");
    } else notify("Mã trình diễn chưa đúng.", "error");
  };

  const submit = async () => {
    if (!navigator.onLine && !profile.demo) return notify("Không có Internet. Bản nháp vẫn được giữ trên máy.", "error");
    if (!draft.reporterName.trim()) return notify("Hãy nhập tên người báo cáo.", "error");
    if (!draft.beforeFile || !draft.afterFile) return notify("Cần đủ ảnh trước và ảnh sau.", "error");
    if (!draft.trashType) return notify("Hãy chọn loại rác.", "error");
    if ((draft.latitude == null || draft.longitude == null) && !profile.demo) return notify("Hãy lấy tọa độ GPS trước khi gửi.", "error");
    const distance = draft.latitude != null && draft.longitude != null ? distanceMeters(draft.latitude, draft.longitude, SCHOOL.lat, SCHOOL.lon) : 0;
    if (!demoLocation && distance > MAX_DISTANCE) return notify(`Bạn đang cách trường ${Math.round(distance)} m. Chỉ bật chế độ BGK khi trình diễn.`, "error");

    setBusy(true);
    try {
      let review: AiReview;
      let beforeUrl = `${ASSET}img_placeholder_upload.png`;
      let afterUrl = `${ASSET}img_placeholder_upload.png`;
      if (profile.demo) {
        await new Promise((resolve) => setTimeout(resolve, 950));
        review = {
          is_trash: true,
          trash_name: draft.trashType === "recyclable" ? "Chai nhựa và lon nước" : "Rác sinh hoạt",
          category: draft.trashType === "recyclable" ? "RECYCLABLE" : "NON_RECYCLABLE",
          estimated_kg: draft.trashType === "recyclable" ? 0.42 : 0.18
        };
      } else {
        setStatus("Đang tải hai ảnh minh chứng...");
        [beforeUrl, afterUrl] = await Promise.all([uploadImage(draft.beforeFile, "before"), uploadImage(draft.afterFile, "after")]);
        setStatus("AI đang nhận diện và ước lượng khối lượng...");
        review = await analyzeImage(draft.beforeFile);
      }
      const expected = draft.trashType === "recyclable" ? "RECYCLABLE" : "NON_RECYCLABLE";
      const approved = review.is_trash && review.category === expected;
      const points = pointsForEstimatedWaste(review.estimated_kg);
      const report: Omit<TrashReport, "id"> = {
        className: profile.className,
        reporterName: draft.reporterName.trim(),
        imageUrl: beforeUrl,
        image_before_url: beforeUrl,
        image_after_url: afterUrl,
        trash_type: draft.trashType,
        latitude: draft.latitude ?? SCHOOL.lat,
        longitude: draft.longitude ?? SCHOOL.lon,
        timestamp: Date.now(),
        address: demoLocation ? "Trình diễn ngoài trường" : "Trường PTDTNT THPT Đam San",
        demoMode: demoLocation,
        points,
        status: approved ? "approved" : "pending",
        aiIsTrash: review.is_trash,
        aiTrashName: review.trash_name,
        aiCategory: review.category,
        aiReviewStatus: approved ? "auto_approved" : "needs_review",
        aiEstimatedKg: review.estimated_kg,
        aiReason: approved ? "AI xác nhận ảnh và phân loại phù hợp." : "Cần Ban thi đua kiểm tra lại.",
        aiAutoApproved: approved,
        aiAnalyzedAt: Date.now()
      };
      const id = profile.demo ? `demo-${Date.now()}` : await createReport(report);
      await clearDraft();
      onDone({ ...report, id });
    } catch (error) {
      notify(error instanceof Error ? error.message : "Không gửi được báo cáo.", "error");
      setStatus("Gửi chưa thành công. Bản nháp vẫn được giữ trên thiết bị.");
    } finally {
      setBusy(false);
    }
  };

  const reset = async () => {
    await clearDraft();
    setDraft({ savedAt: Date.now(), reporterName: "", trashType: null, beforeFile: null, afterFile: null, latitude: null, longitude: null });
    setStatus("Bản nháp đã được xóa.");
  };

  const step = !draft.beforeFile ? 1 : !draft.trashType ? 2 : !draft.afterFile ? 3 : 4;
  return (
    <div className="report-page">
      <section className="report-intro">
        <span className="eyebrow">XÁC THỰC THU GOM</span>
        <h1>Hai ảnh. Một hành động thật.</h1>
        <p>Bản nháp được giữ 48 giờ, vì vậy bạn có thể chụp hiện trạng rồi tiếp tục khi đã tới đúng thùng rác.</p>
        <div className="stepper">{["Hiện trạng", "Phân loại", "Trong thùng", "Hoàn tất"].map((label, index) => <div className={step > index ? "active" : ""} key={label}><i>{step > index + 1 ? <Check size={14} /> : index + 1}</i><span>{label}</span></div>)}</div>
      </section>

      <section className="report-form">
        <label className="field-label">Tên người báo cáo<input value={draft.reporterName} onChange={(e) => setDraft((current) => ({ ...current, reporterName: e.target.value }))} placeholder="Nhập họ và tên" /></label>
        <div className="photo-grid">
          <PhotoCard title="Ảnh trước" subtitle="Rác tại hiện trường" file={draft.beforeFile} onClick={() => beforeRef.current?.click()} done={!!draft.beforeFile} />
          <PhotoCard title="Ảnh sau" subtitle={draft.trashType === "recyclable" ? "Trong thùng tái chế" : "Trong thùng lớp/hố rác"} file={draft.afterFile} onClick={() => draft.beforeFile && draft.trashType ? afterRef.current?.click() : notify("Chụp ảnh trước và chọn loại rác trước.", "error")} done={!!draft.afterFile} disabled={!draft.beforeFile || !draft.trashType} />
          <input ref={beforeRef} className="visually-hidden" type="file" accept="image/*" capture="environment" onChange={setPhoto("before")} />
          <input ref={afterRef} className="visually-hidden" type="file" accept="image/*" capture="environment" onChange={setPhoto("after")} />
        </div>

        <div className="trash-choice">
          <h2>Chọn loại rác</h2>
          <div className="choice-grid">
            <button className={draft.trashType === "recyclable" ? "selected" : ""} onClick={() => setDraft((current) => ({ ...current, trashType: "recyclable" }))}><Recycle size={24} /><span><strong>Rác tái chế</strong><small>Chai nhựa, lon, giấy, carton</small></span><Check size={18} /></button>
            <button className={draft.trashType === "household" ? "selected" : ""} onClick={() => setDraft((current) => ({ ...current, trashType: "household" }))}><Leaf size={24} /><span><strong>Rác sinh hoạt</strong><small>Vỏ kẹo, hộp xốp, thức ăn</small></span><Check size={18} /></button>
          </div>
        </div>

        <div className="verification-row">
          <button className="location-button" onClick={getLocation}><LocateFixed size={20} /><span><strong>{draft.latitude ? "Đã lấy tọa độ" : "Lấy vị trí GPS"}</strong><small>{draft.latitude ? `${draft.latitude.toFixed(5)}, ${draft.longitude?.toFixed(5)}` : "Bắt buộc khi gửi báo cáo thật"}</small></span></button>
          <button className={`switch-row ${demoLocation ? "on" : ""}`} onClick={toggleDemo}><span><strong>Chế độ BGK</strong><small>Cho phép ngoài 500 m</small></span><i /></button>
        </div>

        <div className="draft-notice"><Pause size={18} /><div><strong>Có thể tạm dừng</strong><span>{status}</span></div></div>
        <div className="form-actions"><button className="text-button" onClick={() => void reset()}><RotateCcw size={18} />Xóa bản nháp</button><button className="submit-button" disabled={busy} onClick={() => void submit()}>{busy ? <UploadCloud className="spin" size={19} /> : <Send size={19} />}{busy ? "ĐANG XỬ LÝ..." : "GỬI BÁO CÁO"}</button></div>
      </section>

      {showPin && <Modal onClose={() => setShowPin(false)}><div className="pin-modal"><ShieldCheck size={30} /><h2>Mở chế độ trình diễn</h2><p>Nhập mã BGK để gửi báo cáo ngoài bán kính 500 m. Tọa độ thật vẫn được lưu và báo cáo sẽ mang nhãn DEMO.</p><input autoFocus value={pin} onChange={(e) => setPin(e.target.value)} inputMode="numeric" type="password" placeholder="Mã BGK" /><button className="primary-button" onClick={confirmPin}>XÁC NHẬN</button></div></Modal>}
    </div>
  );
}

function PhotoCard({ title, subtitle, file, onClick, done, disabled }: { title: string; subtitle: string; file: File | null; onClick: () => void; done: boolean; disabled?: boolean }) {
  const url = useMemo(() => file ? URL.createObjectURL(file) : "", [file]);
  useEffect(() => () => { if (url) URL.revokeObjectURL(url); }, [url]);
  return (
    <button className={`photo-card ${done ? "done" : ""}`} onClick={onClick} disabled={disabled}>
      {url ? <img src={url} alt={title} /> : <div className="photo-placeholder"><img src={`${ASSET}ic_camera_3d.png`} alt="" /></div>}
      <span className="photo-caption"><strong>{done && <Check size={15} />}{title}</strong><small>{subtitle}</small></span>
    </button>
  );
}

function RankingPage({ reports }: { reports: TrashReport[] }) {
  const ranking = calculateRankings(reports);
  const podium = [ranking[1], ranking[0], ranking[2]];
  return (
    <div className="ranking-page">
      <section className="page-heading"><span className="eyebrow">THI ĐUA TOÀN TRƯỜNG</span><h1>Bảng vàng Đam San</h1><p>Cập nhật trực tiếp từ các báo cáo đủ hai ảnh và đã được xác nhận.</p></section>
      <section className="podium">
        {podium.map((item, index) => {
          const actualRank = index === 0 ? 2 : index === 1 ? 1 : 3;
          const icon = actualRank === 1 ? "ic_crown_gold_3d.png" : actualRank === 2 ? "ic_medal_silver_3d.png" : "ic_medal_bronze_3d.png";
          return <div className={`podium-card rank-${actualRank}`} key={actualRank}><img src={`${ASSET}${icon}`} alt="" /><span>HẠNG {actualRank}</span><strong>{item?.className ?? "Chưa có"}</strong><b>{item?.totalPoints ?? 0} điểm</b></div>;
        })}
      </section>
      <section className="ranking-list">
        <div className="list-head"><h2>Xếp hạng đầy đủ</h2><span><span className="live-dot" />Realtime</span></div>
        {ranking.length ? ranking.map((item, index) => <div className="ranking-row" key={item.className}><i>{index + 1}</i><div className="class-avatar">{item.className.slice(-2)}</div><div><strong>Lớp {item.className}</strong><span>{item.reportCount} báo cáo hợp lệ</span></div><b>{item.totalPoints}<small>điểm</small></b></div>) : <EmptyState icon={<Leaf size={52} />} title="Trường đang rất sạch!" text="Chưa có lớp nào ghi điểm nhặt rác." />}
      </section>
    </div>
  );
}

function ProfilePage({ profile, reports, soundEnabled, onSound, onInstall, onLogout, notify }: { profile: UserProfile; reports: TrashReport[]; soundEnabled: boolean; onSound: (value: boolean) => void; onInstall: () => void; onLogout: () => void; notify: (message: string, kind?: "tap" | "success" | "error") => void }) {
  const own = reports.filter((item) => item.className === profile.className);
  const approved = own.filter((item) => item.status === "approved");
  const points = approved.reduce((sum, item) => sum + item.points, 0);
  const pending = reports.filter((item) => item.status === "pending");
  const review = async (id: string, status: "approved" | "rejected") => {
    try {
      await updateReportStatus(id, status);
      notify(status === "approved" ? "Đã duyệt báo cáo và cập nhật điểm." : "Đã từ chối báo cáo.", status === "approved" ? "success" : "tap");
    } catch {
      notify("Không cập nhật được báo cáo. Kiểm tra quyền quản trị Firebase.", "error");
    }
  };
  return (
    <div className="profile-page">
      <section className="profile-hero">
        <img src={`${ASSET}ic_profile_3d.png`} alt="" />
        <div><span>{profile.demo ? "TÀI KHOẢN TRÌNH DIỄN" : "TÀI KHOẢN LỚP"}</span><h1>{profile.displayName}</h1><p>{profile.email}</p></div>
        <div className="profile-points"><strong>{points}</strong><span>điểm xanh</span></div>
      </section>
      <section className="profile-stats"><div><strong>{approved.length}</strong><span>báo cáo duyệt</span></div><div><strong>{formatNumber(approved.reduce((s, r) => s + r.aiEstimatedKg, 0))} kg</strong><span>rác thu gom</span></div><div><strong>{calculateRankings(reports).findIndex((r) => r.className === profile.className) + 1 || "–"}</strong><span>hạng toàn trường</span></div></section>
      <section className="settings-panel">
        <h2>Tiện ích</h2>
        <MenuRow icon={<Download />} title="Cài lên màn hình chính" subtitle="Mở toàn màn hình như một ứng dụng" onClick={onInstall} />
        <MenuRow icon={soundEnabled ? <Volume2 /> : <VolumeX />} title="Âm thanh phản hồi" subtitle="Âm báo nhẹ khi thao tác và hoàn thành" action={<button className={`toggle ${soundEnabled ? "on" : ""}`} onClick={() => onSound(!soundEnabled)}><i /></button>} />
        <MenuRow icon={<Bell />} title="Thông báo" subtitle="Nhắc nhiệm vụ và kết quả duyệt" action={<span className="soon">Sắp có</span>} />
        <MenuRow icon={<Settings />} title="Quyền Camera và GPS" subtitle="Quản lý trong cài đặt trình duyệt" />
      </section>
      {profile.role === "admin" && <section className="admin-review"><div className="list-head"><h2>Duyệt báo cáo</h2><span>{pending.length} đang chờ</span></div>{pending.length ? pending.slice(0, 10).map((item) => <div className="review-row" key={item.id}><img src={item.image_before_url || `${ASSET}img_placeholder_upload.png`} alt="" /><div><strong>{item.className} · {item.reporterName}</strong><span>{item.aiTrashName || "AI chưa xác định"} · {item.points} điểm đề xuất</span></div><div><button aria-label={`Từ chối báo cáo ${item.id}`} onClick={() => void review(item.id, "rejected")}><X size={16} /></button><button className="approve" aria-label={`Duyệt báo cáo ${item.id}`} onClick={() => void review(item.id, "approved")}><Check size={16} /></button></div></div>) : <EmptyState icon={<ShieldCheck size={44} />} title="Đã xử lý hết" text="Không còn báo cáo chờ duyệt." />}</section>}
      <section className="history-panel"><div className="list-head"><h2>Lịch sử gần đây</h2><span>{own.length} báo cáo</span></div>{own.slice(0, 5).map((item) => <div className="history-row" key={item.id}><img src={item.image_before_url || `${ASSET}img_placeholder_upload.png`} alt="" /><div><strong>{item.aiTrashName || (item.trash_type === "recyclable" ? "Rác tái chế" : "Rác sinh hoạt")}</strong><span>{new Date(item.timestamp).toLocaleString("vi-VN")}</span></div><b className={item.status}>{item.status === "approved" ? `+${item.points}` : item.status === "pending" ? "Chờ duyệt" : "Từ chối"}</b></div>)}</section>
      <button className="logout-button" onClick={onLogout}><LogOut size={20} />Đăng xuất</button>
      <p className="version">Dam San Green PWA · Phiên bản 1.0</p>
    </div>
  );
}

function MenuRow({ icon, title, subtitle, action, onClick }: { icon: ReactNode; title: string; subtitle: string; action?: ReactNode; onClick?: () => void }) {
  return <div className="menu-row" onClick={onClick} role={onClick ? "button" : undefined}><span className="menu-icon">{icon}</span><div><strong>{title}</strong><span>{subtitle}</span></div>{action ?? <ChevronRight size={19} />}</div>;
}

function BottomNav({ active, onChange }: { active: View; onChange: (view: View) => void }) {
  const items: { id: View; label: string; icon: ReactNode }[] = [
    { id: "home", label: "Trang chủ", icon: <Home /> },
    { id: "ranking", label: "Xếp hạng", icon: <Trophy /> },
    { id: "report", label: "Quét rác", icon: <Camera /> },
    { id: "profile", label: "Hồ sơ", icon: <CircleUserRound /> }
  ];
  return <nav className="bottom-nav">{items.map((item) => <button key={item.id} className={`${active === item.id ? "active" : ""} ${item.id === "report" ? "nav-scan" : ""}`} onClick={() => onChange(item.id)}>{item.id === "report" ? <span><img src={`${ASSET}ic_camera_3d.png`} alt="" /></span> : item.icon}<small>{item.label}</small></button>)}</nav>;
}

function AppMenu({ active, onClose, onChange, onInstall, onLogout }: { active: View; onClose: () => void; onChange: (view: View) => void; onInstall: () => void; onLogout: () => void }) {
  return (
    <div className="menu-backdrop" onClick={onClose}>
      <aside className="app-menu" onClick={(event) => event.stopPropagation()} aria-label="Menu ứng dụng">
        <div className="menu-handle" />
        <div className="app-menu-heading">
          <img src={`${ASSET}logo_damsan_green.png`} alt="" />
          <div><strong>Dam San Green</strong><span>Hành động xanh · Thi đua minh bạch</span></div>
          <button onClick={onClose} aria-label="Đóng menu"><X size={20} /></button>
        </div>
        <nav>
          <button className={active === "home" ? "active" : ""} onClick={() => onChange("home")}><Home size={20} /><span>Trang chủ<small>Bản đồ và nhiệm vụ hôm nay</small></span><ChevronRight size={18} /></button>
          <button className={active === "report" ? "active" : ""} onClick={() => onChange("report")}><Camera size={20} /><span>Báo cáo rác<small>Chụp ảnh trước và sau</small></span><ChevronRight size={18} /></button>
          <button className={active === "ranking" ? "active" : ""} onClick={() => onChange("ranking")}><Trophy size={20} /><span>Bảng xếp hạng<small>Điểm thi đua toàn trường</small></span><ChevronRight size={18} /></button>
          <button className={active === "profile" ? "active" : ""} onClick={() => onChange("profile")}><CircleUserRound size={20} /><span>Trang cá nhân<small>Hồ sơ, lịch sử và cài đặt</small></span><ChevronRight size={18} /></button>
        </nav>
        <div className="app-menu-actions">
          <button onClick={onInstall}><Download size={18} />Cài lên màn hình chính</button>
          <button className="menu-logout" onClick={onLogout}><LogOut size={18} />Đăng xuất</button>
        </div>
      </aside>
    </div>
  );
}

function Modal({ children, onClose }: { children: ReactNode; onClose: () => void }) {
  return <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}><div className="modal"><button className="modal-close" onClick={onClose}><X size={20} /></button>{children}</div></div>;
}

function EmptyState({ icon, title, text }: { icon: ReactNode; title: string; text: string }) {
  return <div className="empty-state">{icon}<strong>{title}</strong><span>{text}</span></div>;
}

function calculateRankings(reports: TrashReport[]): Ranking[] {
  const classes = new Map<string, Ranking>();
  reports.filter((item) => item.status === "approved" && item.className && item.image_before_url && item.image_after_url && item.trash_type).forEach((report) => {
    const current = classes.get(report.className) ?? { className: report.className, totalPoints: 0, reportCount: 0, lastActivity: 0 };
    current.totalPoints += Math.max(0, report.points || 0);
    current.reportCount += 1;
    current.lastActivity = Math.max(current.lastActivity, report.timestamp);
    classes.set(report.className, current);
  });
  return [...classes.values()].sort((a, b) => b.totalPoints - a.totalPoints || b.reportCount - a.reportCount || a.className.localeCompare(b.className));
}

function distanceMeters(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const rad = (value: number) => value * Math.PI / 180;
  const dLat = rad(lat2 - lat1);
  const dLon = rad(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat("vi-VN", { maximumFractionDigits: value < 10 ? 2 : 1 }).format(value);
}

export default App;
