
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

/**
 * CYBER GRAND PRIX - versão COMPUTADOR (tela larga 16:9, corrida na horizontal).
 * Java 11+ / Swing, sem bibliotecas externas.
 *
 * Compilar:  javac CyberGrandPrixPC.java
 * Rodar:     java CyberGrandPrixPC
 *
 * Você só controla a TROCA DE MARCHA (SHIFT ou ESPAÇO).
 * Troca de pista, desvios, nitro e trapaças (EMP, óleo, golpe) são automáticos.
 * P / ESC = pausa | F11 = tela cheia
 */
public class CyberGrandPrixPC extends JPanel implements KeyListener, MouseListener, MouseMotionListener {

    // ---------- constantes do jogo ----------
    static final int W = 1280, H = 720;      // tela virtual 16:9 (a janela escala sozinha)
    static final int RY = 180, RH = 360;     // pista horizontal: topo e altura
    static final int PX = 380;               // posição X do robô do jogador na tela
    static final int SC = 5;                 // pixels por metro
    static final double[] GM = {0, 70, 120, 170, 220, 270};   // velocidade máxima de cada marcha (km/h)
    static final Color PK = new Color(255, 45, 170), CY = new Color(0, 229, 255), PU = new Color(184, 77, 255),
            GOLD = new Color(255, 211, 77), GREEN = new Color(77, 255, 136), RED = new Color(255, 59, 92),
            LIL = new Color(185, 168, 255);
    static final String[] BC = {"#ff2daa", "#168dff", "#8d38ff", "#ff7a00"};
    static final String[] BN = {"#00e5ff", "#ff2daa", "#4dff88", "#ffd34d"};
    static final String[] ACN = {"Antena Pulse", "Halo Neon", "Visor Cyber", "Asas", "Coroa", "Chamas"};
    static final int[] ACP = {10, 15, 18, 20, 25, 30};
    static final String[] NN = {"NEXUS-9", "VOLT.EXE", "KRYPT0"};   // nomes padrão dos adversários
    static final Color[] NC = {new Color(255, 59, 92), new Color(50, 255, 126), new Color(255, 211, 42)};
    static final double[] NS = {.97, .9, .83};
    static final Random RND = new Random();
    static JFrame frame;

    static class Track {
        int id, len, unl; String n, sub, fx; Color bg, road, blk, ac, ac2; double grip;
        Track(int id, String n, String sub, String fx, String bg, String road, String blk, String ac, String ac2, double grip, int len, int unl) {
            this.id = id; this.n = n; this.sub = sub; this.fx = fx; this.bg = Color.decode(bg); this.road = Color.decode(road);
            this.blk = Color.decode(blk); this.ac = Color.decode(ac); this.ac2 = Color.decode(ac2); this.grip = grip; this.len = len; this.unl = unl;
        }
    }
    static final Track[] TR = {
        new Track(0, "Neon City", "Noite elétrica • pista seca", "c", "#0a0620", "#141030", "#1a1040", "#ff2daa", "#00e5ff", 1.0, 3000, 0),
        new Track(1, "Overheat Dunes", "Calor extremo • areia solta", "d", "#2a1020", "#2e1b2c", "#5a2a34", "#ff9e3d", "#ffd34d", .9, 3200, 1),
        new Track(2, "Acid Rain", "Chuva ácida • tração baixa", "r", "#041a2c", "#0c1f33", "#0a2f44", "#2dffb0", "#00b7ff", .72, 3400, 3),
        new Track(3, "Glitch Void", "Grande final • realidade bugada", "g", "#12041f", "#1a0a2e", "#2a0f4a", "#b84dff", "#ff3b5c", .85, 3800, 6)};

    /** Configuração salva de cada robô do jogador (garagem). */
    static class Cfg { String n; Color c, ne; List<String> a = new ArrayList<>(); int m, g; }
    /** Robô em corrida (jogador ou adversário). */
    static class R {
        String n; Color c, ne; List<String> a; int m, gr, idx, g = 1; boolean isP, nton; double sk;
        double d, x = .5, vx, v, nt = 100, stun, spin, pen, imm, cdE, cdO, cdS, heat, fin, tx = .5, fl, ai, nitT;
        R(String n, Color c, Color ne, List<String> a, int m, int gr, boolean isP, double sk, int idx) {
            this.n = n; this.c = c; this.ne = ne; this.a = a; this.m = m; this.gr = gr; this.isP = isP; this.sk = sk; this.idx = idx;
        }
    }
    static class Hz { boolean oil, dead; double d, x, im; R o; }                 // perigo: óleo ou mina
    static class Pt { double y, d, vy, vd, life, sz; Color col; }                // partícula (y = pixel na tela)
    static class Ring { R r; double t; }
    static class Msg { String t; Color c; double life = 1.8; }
    static class Btn { Rectangle r; Runnable a; boolean en; Btn(Rectangle r, Runnable a, boolean en) { this.r = r; this.a = a; this.en = en; } }

    // ---------- progresso do jogador ----------
    int pts, wins, races, pod, champs, best, tricks, dist, sel, chR = -1;
    int[] chPts = new int[4];
    List<String> owned = new ArrayList<>();
    Cfg[] bots = new Cfg[4];
    String[] nn = NN.clone();   // nomes dos 3 adversários (editáveis)

    // ---------- estado da corrida ----------
    String scr = "menu", state = "cd";
    Track T; int ti, ls; boolean champRace;
    R P; List<R> rs = new ArrayList<>(), resOrder = new ArrayList<>();
    List<Hz> hz = new ArrayList<>(); List<Pt> parts = new ArrayList<>();
    List<Ring> rings = new ArrayList<>(); List<Msg> msgs = new ArrayList<>();
    double rt, cd, maxv, endT, shake, camD, tm; String prevState = "run";
    int resPos, resGain, resBonus; boolean resUnl, resDone; int[] resRows;
    int idleTrack = RND.nextInt(4);

    // ---------- interface ----------
    List<Btn> btns = new ArrayList<>();
    Set<Integer> held = new HashSet<>();
    double sc = 1, ox, oy, mx = -1, my = -1; long lastNs = System.nanoTime();
    final Map<Integer, Font> fonts = new HashMap<>();

    public CyberGrandPrixPC() {
        setPreferredSize(new Dimension(W, H)); setBackground(Color.BLACK); setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(this); addMouseListener(this); addMouseMotionListener(this);
        addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent e) { held.clear(); } });
        load();
        new javax.swing.Timer(16, e -> tick()).start();
    }

    // =====================================================================
    //  SALVAR / CARREGAR (mesmo arquivo da versão anterior: o progresso continua)
    // =====================================================================
    File saveFile() { return new File(System.getProperty("user.home"), ".cybergrandprix.properties"); }
    static String hex(Color c) { return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()); }
    static Color col(String s, Color d) { try { return s == null ? d : Color.decode(s); } catch (Exception e) { return d; } }
    static List<String> split(String s) { return s == null || s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(","))); }
    static int gi(Properties p, String k, int d) { try { return Integer.parseInt(p.getProperty(k, "" + d)); } catch (Exception e) { return d; } }

    void load() {
        for (int i = 0; i < 4; i++) { Cfg c = new Cfg(); c.n = "ROBO-0" + (i + 1); c.c = Color.decode(BC[i]); c.ne = Color.decode(BN[i]); bots[i] = c; }
        Properties p = new Properties();
        try (Reader in = new InputStreamReader(new FileInputStream(saveFile()), StandardCharsets.UTF_8)) { p.load(in); } catch (IOException e) { return; }
        pts = gi(p, "pts", 0); wins = gi(p, "wins", 0); races = gi(p, "races", 0); pod = gi(p, "pod", 0); champs = gi(p, "champs", 0);
        best = gi(p, "best", 0); tricks = gi(p, "tricks", 0); dist = gi(p, "dist", 0); sel = Math.max(0, Math.min(3, gi(p, "sel", 0)));
        owned = split(p.getProperty("owned")); chR = gi(p, "chR", -1);
        for (int i = 0; i < 4; i++) chPts[i] = gi(p, "chP" + i, 0);
        for (int i = 0; i < 3; i++) { String s = p.getProperty("nn" + i, nn[i]).trim(); if (!s.isEmpty()) nn[i] = s; }
        for (int i = 0; i < 4; i++) {
            Cfg c = bots[i]; c.n = p.getProperty("b" + i + "n", c.n); c.c = col(p.getProperty("b" + i + "c"), c.c);
            c.ne = col(p.getProperty("b" + i + "ne"), c.ne); c.a = split(p.getProperty("b" + i + "a")); c.m = gi(p, "b" + i + "m", 0); c.g = gi(p, "b" + i + "g", 0);
        }
    }

    void save() {
        Properties p = new Properties();
        p.setProperty("pts", "" + pts); p.setProperty("wins", "" + wins); p.setProperty("races", "" + races); p.setProperty("pod", "" + pod);
        p.setProperty("champs", "" + champs); p.setProperty("best", "" + best); p.setProperty("tricks", "" + tricks); p.setProperty("dist", "" + dist);
        p.setProperty("sel", "" + sel); p.setProperty("owned", String.join(",", owned)); p.setProperty("chR", "" + chR);
        for (int i = 0; i < 4; i++) p.setProperty("chP" + i, "" + chPts[i]);
        for (int i = 0; i < 3; i++) p.setProperty("nn" + i, nn[i]);
        for (int i = 0; i < 4; i++) {
            Cfg c = bots[i]; p.setProperty("b" + i + "n", c.n); p.setProperty("b" + i + "c", hex(c.c)); p.setProperty("b" + i + "ne", hex(c.ne));
            p.setProperty("b" + i + "a", String.join(",", c.a)); p.setProperty("b" + i + "m", "" + c.m); p.setProperty("b" + i + "g", "" + c.g);
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(saveFile()), StandardCharsets.UTF_8)) { p.store(w, "Cyber Grand Prix"); } catch (IOException e) { /* ignora */ }
    }

    /** Pergunta o novo nome. i = 0 -> robô do jogador; i = 1..3 -> adversários. */
    void rename(int i) {
        String cur = i == 0 ? bots[sel].n : nn[i - 1];
        String s = JOptionPane.showInputDialog(this, "Novo nome (máx. 12 letras):", cur);
        if (s == null) return;
        s = s.trim(); if (s.isEmpty()) return;
        s = s.substring(0, Math.min(12, s.length()));
        if (i == 0) bots[sel].n = s; else nn[i - 1] = s;
        save();
    }

    // =====================================================================
    //  CORRIDA: início, regras e física
    // =====================================================================
    void startRace(int t, boolean ch) {
        if (ch && chR < 0) { chR = 0; chPts = new int[4]; save(); }
        T = TR[t]; ti = t; champRace = ch; Cfg b = bots[sel];
        rs = new ArrayList<>();
        P = new R(b.n.trim().isEmpty() ? "ROBO" : b.n, b.c, b.ne, new ArrayList<>(b.a), b.m, b.g, true, 1, 0); rs.add(P);
        for (int i = 0; i < 3; i++) rs.add(new R(nn[i], NC[i], NC[i], new ArrayList<>(), 0, 0, false, NS[i], i + 1));
        double[] xs = {.38, .14, .62, .86}, ds = {-12, -20, -12, -20};
        for (int i = 0; i < 4; i++) { rs.get(i).x = rs.get(i).tx = xs[i]; rs.get(i).d = ds[i]; }
        hz = new ArrayList<>();
        for (double d = 220; d < T.len - 80; d += 70 + RND.nextDouble() * 60) {
            Hz h = new Hz(); h.oil = RND.nextDouble() < .6; h.d = d; h.x = .14 + RND.nextDouble() * .72; hz.add(h);
        }
        state = "cd"; cd = 3.4; ls = 4; rt = 0; maxv = 0; endT = 0; shake = 0; camD = P.d;
        msgs.clear(); parts.clear(); rings.clear(); scr = "race";
    }

    List<R> rk() {
        List<R> l = new ArrayList<>(rs);
        l.sort((a, b) -> a.fin > 0 && b.fin > 0 ? Double.compare(a.fin, b.fin) : a.fin > 0 ? -1 : b.fin > 0 ? 1 : Double.compare(b.d, a.d));
        return l;
    }

    void msg(String t, Color c) { Msg m = new Msg(); m.t = t; m.c = c; msgs.add(m); if (msgs.size() > 3) msgs.remove(0); }

    void burst(R r, Color col, int n, double sp) {
        for (int i = 0; i < n; i++) {
            Pt p = new Pt(); p.y = LY(r.x); p.d = r.d; p.vy = (RND.nextDouble() - .5) * sp * 2; p.vd = (RND.nextDouble() - .5) * sp * .25;
            p.life = .4 + RND.nextDouble() * .5; p.col = col; p.sz = 2 + RND.nextDouble() * 3; parts.add(p);
        }
    }

    /** Passa a marcha (ÚNICO controle manual): giro alto dá turbo; cedo demais perde velocidade. */
    void shiftUp() {
        if (!state.equals("run") || P.g >= 5) return;
        double rpm = P.v / (GM[P.g] * (1 + P.m * .04)); P.g++;
        if (rpm >= .9) { P.fl = 1.3; P.v *= 1.03; msg("SHIFT PERFEITO!", GREEN); }
        else if (rpm < .6) { P.v *= .9; msg("Cedo demais...", new Color(255, 107, 107)); }
        else msg("Marcha " + P.g, CY);
    }

    /** Trapaças: E = EMP, O = óleo, S = golpe lateral. Cada uma tem recarga e aumenta o calor. */
    void trick(R r, char k) {
        if (k == 'E' && r.cdE <= 0) {
            r.cdE = 7; r.heat += 24; Ring q = new Ring(); q.r = r; q.t = .7; rings.add(q);
            for (R o : rs) if (o != r && Math.abs(o.d - r.d) < 110) { o.stun = 1.4; o.v *= .7; burst(o, CY, 14, 150); if (o.isP) msg("VOCÊ FOI HACKEADO!", CY); }
            if (r.isP) { tricks++; msg("PULSO EMP!", CY); }
        } else if (k == 'O' && r.cdO <= 0) {
            r.cdO = 4; r.heat += 14; Hz h = new Hz(); h.oil = true; h.d = r.d - 9; h.x = r.x; h.o = r; h.im = 2.5; hz.add(h);
            if (r.isP) { tricks++; msg("ÓLEO LARGADO", new Color(183, 132, 255)); }
        } else if (k == 'S' && r.cdS <= 0) {
            r.cdS = 2.5; r.heat += 15; boolean hit = false;
            for (R o : rs) if (o != r && Math.abs(o.d - r.d) < 14 && Math.abs(o.x - r.x) < .22) {
                hit = true; o.vx += (o.x >= r.x ? 1 : -1) * 1.4; o.stun = .6; o.v *= .82; burst(o, GOLD, 10, 160);
                if (o.isP) { msg("BATERAM EM VOCÊ!", GOLD); shake = .6; }
            }
            if (r.isP) { tricks++; msg(hit ? "GOLPE!" : "Golpe no vazio", GOLD); }
        }
    }

    /** Rival mais próximo de r (usado pelo piloto automático do jogador). */
    R nearest(R r) {
        R b = null; double bd = 1e9;
        for (R o : rs) { if (o == r) continue; double s = Math.abs(o.d - r.d) + Math.abs(o.x - r.x) * 60; if (s < bd) { bd = s; b = o; } }
        return b;
    }

    /**
     * Piloto automático (vale para os adversários E para o jogador):
     * escolhe a faixa com menos perigo, usa nitro e joga sujo.
     * O piloto do jogador desvia com mais antecedência e nunca estoura o calor (sem multa).
     * Retorna {direção, nitro}.
     */
    double[] ai(R r, double dt) {
        r.ai -= dt;
        if (r.ai <= 0) {
            r.ai = r.isP ? .1 + RND.nextDouble() * .1 : .3 + RND.nextDouble() * .3;
            double look = r.isP ? 40 + r.v * .3 : 55, jit = r.isP ? .05 : .4, best = 1e9, bx = r.tx;
            for (double c : new double[]{.14, .3, .5, .7, .86}) {
                double cost = Math.abs(c - r.x) * 2 + RND.nextDouble() * jit;
                for (Hz h : hz) { double dd = h.d - r.d; if (!h.dead && dd > 0 && dd < look && Math.abs(h.x - c) < .12 && !(h.o == r && h.im > 0)) cost += 6; }
                for (R o : rs) { if (o == r) continue; double dd = o.d - r.d; if (dd > 0 && dd < 30 && Math.abs(o.x - c) < .12) cost += 3; }
                if (cost < best) { best = cost; bx = c; }
            }
            r.tx = bx;
        }
        R tg = r.isP ? nearest(r) : P;
        double dp = tg.d - r.d, dx = Math.abs(tg.x - r.x);
        boolean can = !r.isP || r.heat < 60;                       // jogador nunca leva multa do Fiscal
        if (can && RND.nextDouble() < dt * (r.isP ? .4 : .12)) {
            if (r.cdO <= 0 && dp < 0 && dp > -90 && (!r.isP || dx < .3)) trick(r, 'O');
            else if (r.cdS <= 0 && Math.abs(dp) < 14 && dx < .22) trick(r, 'S');
            else if (r.cdE <= 0 && Math.abs(dp) < 100 && Math.abs(dp) > 20) trick(r, 'E');
        }
        if (r.nitT > 0) r.nitT -= dt;
        else if (r.nt > (r.isP ? 50 : 60) && RND.nextDouble() < dt * (r.isP ? .6 : .3)) r.nitT = 1.2;
        return new double[]{Math.max(-1, Math.min(1, (r.tx - r.x) * 4)), r.nitT > 0 ? 1 : 0};
    }

    void update(double dt) {
        for (Msg m : msgs) m.life -= dt;
        msgs.removeIf(m -> m.life <= 0);
        for (Pt p : parts) { p.y += p.vy * dt; p.d += p.vd * dt; p.life -= dt; }
        parts.removeIf(p -> p.life <= 0); while (parts.size() > 300) parts.remove(0);
        for (Ring q : rings) q.t -= dt;
        rings.removeIf(q -> q.t <= 0);
        if (state.equals("cd")) {
            cd -= dt; ls = (int) Math.ceil(cd);
            if (cd <= 0) { state = "run"; msg("LARGOU!", GREEN); }
            return;
        }
        rt += dt; for (Hz h : hz) h.im = Math.max(0, h.im - dt);
        for (R r : rs) {
            r.stun = Math.max(0, r.stun - dt); r.spin = Math.max(0, r.spin - dt); r.pen = Math.max(0, r.pen - dt); r.imm = Math.max(0, r.imm - dt);
            r.cdE = Math.max(0, r.cdE - dt); r.cdO = Math.max(0, r.cdO - dt); r.cdS = Math.max(0, r.cdS - dt); r.fl = Math.max(0, r.fl - dt);
            r.heat = Math.max(0, r.heat - 6 * dt);
            if (r.heat >= 100) { r.pen = 2.5; r.heat = 0; if (r.isP) msg("FISCAL CYBER! MULTA", RED); }
            double mm = 1 + r.m * .04, gf = Math.min(1.15, T.grip * (1 + r.gr * .07));
            double[] c = ai(r, dt); double dir = c[0]; boolean nit = c[1] > 0;   // todos pilotam sozinhos
            r.nton = nit && r.nt > (r.nton ? 0 : 12);
            r.nt = r.nton ? Math.max(0, r.nt - 36 * dt) : Math.min(100, r.nt + 7 * dt);
            double gm;
            if (r.isP) gm = GM[r.g] * mm * (r.nton ? 1.25 : 1) * (r.fl > 0 ? 1.05 : 1);   // a marcha limita a velocidade
            else { double df = r.d - P.d; gm = (165 + r.sk * 100) * (r.nton ? 1.12 : 1) * (df > 150 ? .93 : df < -150 ? 1.07 : 1); }
            gm *= (r.stun > 0 ? .35 : 1) * (r.pen > 0 ? .6 : 1) * (r.spin > 0 ? .55 : 1);
            double rate = r.isP ? (r.g < 3 ? 1.5 : 1.1) : 1; if (r.nton) rate *= 1.6; if (r.fl > 0) rate *= 1.5;
            r.v += (gm - r.v) * Math.min(1, dt * (r.v < gm ? rate : 3));
            if (r.isP) { while (r.g > 1 && r.v < GM[r.g - 1] * mm * .72) r.g--; }
            else { r.g = 1; while (r.g < 5 && r.v > GM[r.g] * .9) r.g++; }
            double tvx = r.spin > 0 ? Math.sin(rt * 16 + r.idx) * .7 : dir * .8;
            r.vx += (tvx - r.vx) * Math.min(1, dt * 7 * gf);
            r.x += r.vx * dt * Math.min(1, r.v / 50);
            if (r.x < .08 || r.x > .92) {
                r.x = Math.min(.92, Math.max(.08, r.x)); r.vx *= -.3; r.v *= .985;
                if (r.isP && r.v > 80 && RND.nextDouble() < .3) { burst(r, GOLD, 3, 90); shake = Math.max(shake, .15); }
            }
            r.d += r.v / 3.6 * dt;
            if (r.isP) maxv = Math.max(maxv, r.v);
            if (r.nton && RND.nextDouble() < .7) {
                Pt p = new Pt(); p.y = LY(r.x) + (RND.nextDouble() - .5) * 14; p.d = r.d - 7; p.life = .5;
                p.col = RND.nextBoolean() ? CY : PK; p.sz = 3 + RND.nextDouble() * 3; parts.add(p);
            }
            if (r.imm <= 0) for (Hz h : hz) {
                if (h.dead || Math.abs(h.d - r.d) > 6 || Math.abs(h.x - r.x) > .095 || (h.o == r && h.im > 0)) continue;
                r.imm = .9;
                if (h.oil) { r.spin = .9; r.v *= .65; if (r.isP) { msg("ÓLEO! DERRAPOU", new Color(183, 132, 255)); shake = .4; } }
                else { h.dead = true; r.stun = 1.2; r.v *= .45; burst(r, new Color(255, 106, 61), 24, 200); if (r.isP) { msg("MINA!", new Color(255, 106, 61)); shake = .8; } }
                break;
            }
            if (r.d >= T.len && r.fin <= 0) {
                r.fin = rt;
                if (r.isP) { state = "end"; endT = 2.2; resOrder = rk(); msg("BANDEIRADA!", GOLD); }
            }
        }
        // colisões entre robôs
        for (int i = 0; i < 4; i++) for (int j = i + 1; j < 4; j++) {
            R a = rs.get(i), b = rs.get(j);
            if (Math.abs(a.d - b.d) < 10 && Math.abs(a.x - b.x) < .115) {
                double s = a.x < b.x ? -1 : 1; a.x += s * .012; b.x -= s * .012; (a.d < b.d ? a : b).v *= .985;
                if ((a.isP || b.isP) && RND.nextDouble() < .25) { burst(a.isP ? a : b, GOLD, 3, 80); shake = Math.max(shake, .12); }
            }
        }
        double back = 1e9; for (R r : rs) back = Math.min(back, r.d);
        final double lim = back - 40;
        hz.removeIf(h -> h.dead || h.d < lim);
        if (state.equals("end")) { endT -= dt; if (endT <= 0) { state = "done"; finish(); } }
    }

    /** Fecha a corrida: pontos só para quem vence; campeonato soma 10/6/3/1 por etapa. */
    void finish() {
        resPos = resOrder.indexOf(P) + 1; resGain = 0; resBonus = 0; resUnl = false; resDone = false; resRows = null;
        races++; dist += (int) Math.min(P.d, T.len); best = Math.max(best, (int) maxv);
        if (resPos == 1) { wins++; resGain = 10; pts += 10; for (Track t : TR) if (t.unl > 0 && t.unl == wins) resUnl = true; }
        if (resPos <= 3) pod++;
        if (champRace && chR >= 0) {
            int[] tb = {10, 6, 3, 1};
            for (int i = 0; i < 4; i++) chPts[resOrder.get(i).idx] += tb[i];
            chR++; resRows = chPts.clone();
            if (chR >= 4) {
                resDone = true; int mxo = Math.max(chPts[1], Math.max(chPts[2], chPts[3]));
                if (chPts[0] >= mxo) { champs++; pts += 30; resBonus = 30; }
                chR = -1; chPts = new int[4];
            }
        }
        save(); scr = "res";
    }

    // =====================================================================
    //  LOOP
    // =====================================================================
    void tick() {
        long n = System.nanoTime(); double dt = Math.min(.05, (n - lastNs) / 1e9); lastNs = n; tm += dt;
        if (scr.equals("race")) { if (!state.equals("pause")) update(dt); }
        else camD += 80 * dt;
        repaint();
    }

    // =====================================================================
    //  DESENHO
    // =====================================================================
    static Color al(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a))); }
    static double hash(double i) { double x = Math.sin(i * 127.1) * 43758.5453; return x - Math.floor(x); }
    double X(double d) { return PX + (d - camD) * SC; }      // distância (m) -> pixel X
    static double LY(double x) { return RY + x * RH; }        // faixa (0..1) -> pixel Y

    void tx(Graphics2D g, String s, double x, double y, int sz, Color c, int an) {
        Font f = fonts.computeIfAbsent(sz, k -> new Font("SansSerif", Font.BOLD, k)); g.setFont(f);
        int w = g.getFontMetrics().stringWidth(s); g.setColor(c);
        g.drawString(s, (float) (an == 1 ? x - w / 2.0 : an == 2 ? x - w : x), (float) y);
    }
    void glow(Graphics2D g, String s, double x, double y, int sz, Color c) {
        tx(g, s, x - 3, y, sz, PK, 1); tx(g, s, x + 3, y, sz, CY, 1); tx(g, s, x, y, sz, c, 1);
    }
    void box(Graphics2D g, int x, int y, int w, int h, Color c) {
        g.setColor(new Color(8, 4, 24, 195)); g.fillRect(x, y, w, h); g.setColor(c); g.setStroke(new BasicStroke(1.5f)); g.drawRect(x, y, w, h);
    }
    void bar(Graphics2D g, int x, int y, int w, int h, double p, Color c) {
        g.setColor(new Color(255, 255, 255, 40)); g.fillRect(x, y, w, h); g.setColor(c); g.fillRect(x, y, (int) (w * Math.max(0, Math.min(1, p))), h);
    }
    void panel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(14, 8, 32, 238)); g.fillRoundRect(x, y, w, h, 18, 18);
        g.setColor(PU); g.setStroke(new BasicStroke(2)); g.drawRoundRect(x, y, w, h, 18, 18);
    }
    void btn(Graphics2D g, String t, int x, int y, int w, int h, boolean en, boolean pri, Runnable a) {
        Rectangle r = new Rectangle(x, y, w, h); btns.add(new Btn(r, a, en));
        boolean hov = en && r.contains(mx, my);
        g.setPaint(pri ? new GradientPaint(x, y, PK, x + w, y, PU) : new GradientPaint(x, y, new Color(27, 15, 61), x, y + h, new Color(14, 8, 36)));
        g.fillRoundRect(x, y, w, h, 12, 12);
        if (hov) { g.setColor(new Color(255, 255, 255, 40)); g.fillRoundRect(x, y, w, h, 12, 12); }
        g.setColor(en ? (pri ? Color.WHITE : CY) : new Color(90, 90, 110)); g.setStroke(new BasicStroke(1.5f)); g.drawRoundRect(x, y, w, h, 12, 12);
        tx(g, t, x + w / 2.0, y + h / 2.0 + 5, 15, en ? Color.WHITE : new Color(120, 120, 140), 1);
    }

    /** Cenário horizontal: prédios acima/abaixo, pista com zebras, faixas, linha de largada/chegada e efeitos da pista. */
    void scene(Graphics2D g, Track t, double cd0) {
        camD = cd0;
        g.setColor(t.bg); g.fillRect(0, 0, W, H);
        int a = (int) Math.floor((cd0 - 95) / 16), b = (int) Math.floor((cd0 + 190) / 16);
        for (int i = a; i <= b; i++) {
            double x = X(i * 16);
            for (int s = 0; s < 2; s++) {
                double h = hash(i * 2 + s + t.id * 97), bh = 50 + h * 70, w = 74;
                double y = s == 0 ? RY - 12 - bh : RY + RH + 12;
                g.setColor(t.blk);
                if (t.fx.equals("d")) g.fill(new Ellipse2D.Double(x + 2, y, w - 4, bh));
                else {
                    g.fill(new Rectangle2D.Double(x + 2, y, w - 4, bh)); g.setColor(al(h > .5 ? t.ac : t.ac2, 150)); g.setStroke(new BasicStroke(1)); g.draw(new Rectangle2D.Double(x + 2, y, w - 5, bh - 1));
                    for (int k = 0; k < 12; k++) {
                        double wx = x + 10 + (k % 4) * 15, wy = y + 10 + (k / 4) * 22;
                        if (wy + 10 < y + bh - 4 && hash(i * 13 + k + s * 7) > .4) { g.setColor((k + i) % 3 != 0 ? t.ac2 : t.ac); g.fill(new Rectangle2D.Double(wx, wy, 8, 10)); }
                    }
                }
            }
        }
        g.setColor(t.road); g.fillRect(0, RY, W, RH);
        g.setPaint(new GradientPaint(0, RY, al(t.ac, 37), 0, RY + RH, al(t.ac2, 37))); g.fillRect(0, RY, W, RH);
        int m0 = (int) Math.floor((cd0 - 95) / 8);
        for (int i = m0; i <= m0 + 38; i++) {
            double x = X(i * 8);
            g.setColor(i % 2 != 0 ? t.ac : Color.WHITE); g.fill(new Rectangle2D.Double(x, RY - 9, 40, 7));
            g.setColor(i % 2 != 0 ? Color.WHITE : t.ac2); g.fill(new Rectangle2D.Double(x, RY + RH + 2, 40, 7));
        }
        if (t.fx.equals("r"))
            for (int i = (int) Math.floor((cd0 - 95) / 40); i <= Math.floor((cd0 + 190) / 40); i++) {
                g.setColor(al(t.ac2, 34)); g.fill(new Ellipse2D.Double(X(i * 40) - 28, RY + 30 + hash(i + 300) * (RH - 60) - 12, 56, 24));
            }
        g.setColor(new Color(255, 255, 255, 80));
        int d0 = (int) Math.floor((cd0 - 95) / 10);
        for (int i = d0; i <= d0 + 30; i++) { double x = X(i * 10); for (double f : new double[]{.25, .5, .75}) g.fill(new Rectangle2D.Double(x - 20, RY + RH * f - 1.5, 20, 3)); }
        g.setStroke(new BasicStroke(10)); g.setColor(al(t.ac, 55)); g.drawLine(0, RY, W, RY); g.setColor(al(t.ac2, 55)); g.drawLine(0, RY + RH, W, RY + RH);
        g.setStroke(new BasicStroke(3)); g.setColor(t.ac); g.drawLine(0, RY, W, RY); g.setColor(t.ac2); g.drawLine(0, RY + RH, W, RY + RH);
        checker(g, 0); checker(g, t.len);
        if (t.fx.equals("g") && RND.nextDouble() < .2) { g.setColor(RND.nextBoolean() ? new Color(255, 59, 92, 50) : new Color(0, 229, 255, 50)); g.fillRect(RND.nextInt(W), 0, 4 + RND.nextInt(14), H); }
    }
    void checker(Graphics2D g, double d) {
        double x = X(d); if (x < -24 || x > W + 24) return;
        for (int i = 0; i < RH / 20; i++) {
            g.setColor(i % 2 != 0 ? Color.WHITE : Color.BLACK); g.fill(new Rectangle2D.Double(x - 10, RY + i * 20, 10, 20));
            g.setColor(i % 2 != 0 ? Color.BLACK : Color.WHITE); g.fill(new Rectangle2D.Double(x, RY + i * 20, 10, 20));
        }
    }

    /** Robô visto de cima (frente para CIMA com ang = 0; na corrida usamos ang = 90° para ele andar para a DIREITA). */
    void drawBot(Graphics2D g0, Color c, Color ne, List<String> acc, double x, double y, double s, double ang, boolean nit) {
        Graphics2D g = (Graphics2D) g0.create(); g.translate(x, y); g.scale(s, s); g.rotate(ang);
        for (int i = 3; i >= 1; i--) { g.setColor(al(ne, 22 + i * 8)); g.fill(new Ellipse2D.Double(-27 - i * 5, 2 - 42 - i * 6, 54 + i * 10, 84 + i * 12)); }
        double L = (nit ? 30 : 0) + (acc.contains("Chamas") ? 18 : 0);
        if (L > 0) for (int sx : new int[]{-9, 9}) {
            double l = L * (.7 + RND.nextDouble() * .5);
            g.setColor(new Color(255, 106, 26)); g.fill(new Polygon(new int[]{sx - 6, sx + 6, sx}, new int[]{30, 30, (int) (30 + l)}, 3));
            g.setColor(new Color(255, 243, 176)); g.fill(new Polygon(new int[]{sx - 3, sx + 3, sx}, new int[]{30, 30, (int) (30 + l * .55)}, 3));
        }
        if (acc.contains("Asas")) { g.setColor(GOLD); for (int sg : new int[]{-1, 1}) g.fill(new Polygon(new int[]{sg * 15, sg * 38, sg * 15}, new int[]{6, 24, 26}, 3)); }
        g.setColor(new Color(8, 8, 15));
        for (int[] w : new int[][]{{-22, -24}, {13, -24}, {-22, 10}, {13, 10}}) g.fillRect(w[0], w[1], 9, 18);
        GeneralPath body = new GeneralPath();
        body.moveTo(0, -37); body.curveTo(15, -30, 17, -12, 17, 0); body.lineTo(19, 25); body.quadTo(0, 36, -19, 25); body.lineTo(-17, 0); body.curveTo(-17, -12, -15, -30, 0, -37); body.closePath();
        g.setColor(c); g.fill(body); g.setColor(ne); g.setStroke(new BasicStroke(2)); g.draw(body);
        g.setColor(new Color(255, 255, 255, 50)); g.fillRect(-3, -33, 6, 40); g.setColor(new Color(0, 0, 0, 75)); g.fillRect(-15, 14, 30, 10);
        g.setColor(new Color(255, 23, 68)); g.fillRect(-14, 24, 8, 3); g.fillRect(6, 24, 8, 3);
        g.setColor(new Color(9, 6, 20)); g.fillRoundRect(-10, -12, 20, 17, 6, 6);
        g.setColor(ne); g.fillRect(-7, -7, 5, 4); g.fillRect(2, -7, 5, 4);
        if (acc.contains("Visor Cyber")) { g.setColor(CY); g.fillRect(-12, -9, 24, 4); }
        if (acc.contains("Halo Neon")) { g.setColor(GOLD); g.setStroke(new BasicStroke(3)); g.draw(new Ellipse2D.Double(-14, -17, 28, 28)); }
        if (acc.contains("Antena Pulse")) { g.setColor(Color.WHITE); g.setStroke(new BasicStroke(2)); g.drawLine(0, -12, 0, -30); g.setColor(PK); g.fill(new Ellipse2D.Double(-3.5, -34.5, 7, 7)); }
        if (acc.contains("Coroa")) { g.setColor(GOLD); g.fill(new Polygon(new int[]{-8, -9, -4, 0, 4, 9, 8}, new int[]{-12, -23, -17, -25, -17, -23, -12}, 7)); }
        g.dispose();
    }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        sc = Math.min(getWidth() / (double) W, getHeight() / (double) H);
        ox = (getWidth() - W * sc) / 2; oy = (getHeight() - H * sc) / 2;
        g.translate(ox, oy); g.scale(sc, sc); g.setClip(0, 0, W, H);
        btns = new ArrayList<>();
        switch (scr) {
            case "race": drawRace(g); break;
            case "tracks": drawTracks(g); break;
            case "champ": drawChamp(g); break;
            case "garage": drawGarage(g); break;
            case "names": drawNames(g); break;
            case "shop": drawShop(g); break;
            case "stats": drawStats(g); break;
            case "help": drawHelp(g); break;
            case "res": drawResult(g); break;
            default: drawMenu(g);
        }
        g.dispose();
    }

    // ---------- corrida ----------
    void drawRace(Graphics2D g0) {
        Graphics2D g = (Graphics2D) g0.create();
        camD = P.d;
        if (shake > 0) { g.translate((RND.nextDouble() - .5) * shake * 10, (RND.nextDouble() - .5) * shake * 10); shake = Math.max(0, shake - .04); }
        scene(g, T, camD);
        for (Hz h : hz) {
            double x = X(h.d), y = LY(h.x); if (x < -40 || x > W + 40) continue;
            if (h.oil) {
                g.setColor(new Color(0, 0, 0, 200)); g.fill(new Ellipse2D.Double(x - 26, y - 16, 52, 32));
                g.setColor(new Color(138, 43, 226)); g.setStroke(new BasicStroke(2)); g.draw(new Ellipse2D.Double(x - 26, y - 16, 52, 32));
                g.setColor(new Color(0, 229, 255, 120)); g.draw(new Ellipse2D.Double(x - 16, y - 8, 24, 12));
            } else {
                g.setColor(new Color(255, 59, 59, 70)); g.fill(new Ellipse2D.Double(x - 16, y - 16, 32, 32));
                g.setColor(((int) (tm * 6)) % 2 == 0 ? new Color(255, 59, 59) : new Color(122, 16, 32)); g.fill(new Ellipse2D.Double(x - 9, y - 9, 18, 18));
                g.setColor(new Color(17, 17, 17)); g.fillRect((int) x - 3, (int) y - 3, 6, 6);
            }
        }
        for (Pt p : parts) { g.setColor(al(p.col, (int) (255 * Math.min(1, p.life * 2)))); g.fill(new Rectangle2D.Double(X(p.d), p.y, p.sz, p.sz)); }
        for (Ring q : rings) {
            double rad = (.7 - q.t) / .7 * 260; g.setStroke(new BasicStroke(4)); g.setColor(al(CY, (int) (255 * q.t / .7)));
            g.draw(new Ellipse2D.Double(X(q.r.d) - rad, LY(q.r.x) - rad, rad * 2, rad * 2));
        }
        List<R> sorted = new ArrayList<>(rs); sorted.sort(Comparator.comparingDouble(r -> r.d));
        for (R r : sorted) {
            double x = X(r.d), y = LY(r.x); if (x < -100 || x > W + 100) continue;
            drawBot(g, r.c, r.ne, r.a, x, y, .85, Math.PI / 2 + (r.spin > 0 ? r.spin / .9 * Math.PI * 4 : 0), r.nton);
            String label = r.n + " • " + Math.round(r.v); g.setFont(fonts.computeIfAbsent(12, k -> new Font("SansSerif", Font.BOLD, k)));
            int w = g.getFontMetrics().stringWidth(label) + 12; double lx = Math.max(70, Math.min(W - 70, x));
            g.setColor(new Color(8, 4, 24, 190)); g.fillRect((int) (lx - w / 2.0), (int) y - 62, w, 18); tx(g, label, lx, y - 48, 12, r.isP ? Color.WHITE : r.c, 1);
            if (r.stun > 0) tx(g, "EMP!", x, y + 6, 16, CY, 1);
        }
        if (P.v > 140) for (int i = 0; i < 14; i++) {
            double xx = W - ((tm * P.v * 2.2 + i * 113) % (W + 80)); g.setColor(new Color(255, 255, 255, (int) Math.min(120, 10 + (P.v - 140) / 4)));
            g.fill(new Rectangle2D.Double(xx, RY + hash(i + 9) * RH, 40 + P.v / 6, 2));
        }
        if (P.nton) {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(W / 2.0, H / 2.0), 800f, new float[]{.4f, 1f}, new Color[]{new Color(0, 0, 0, 0), al(T.ac2, 100)}));
            g.fillRect(0, 0, W, H);
        }
        if (T.fx.equals("r")) { g.setStroke(new BasicStroke(1)); g.setColor(new Color(160, 220, 255, 90)); for (int i = 0; i < 110; i++) { double x = (i * 67.3) % W, y = ((tm * 900 + i * 131) % (H + 40)) - 20; g.draw(new Line2D.Double(x, y, x - 3, y + 14)); } }
        if (T.fx.equals("d")) { g.setColor(new Color(255, 140, 40, 18)); g.fillRect(0, 0, W, H); }
        g.dispose();
        hud(g0);
        if (state.equals("pause")) {
            g0.setColor(new Color(0, 0, 0, 150)); g0.fillRect(0, 0, W, H); panel(g0, 440, 190, 400, 300);
            tx(g0, "PAUSADO", W / 2.0, 245, 26, CY, 1);
            btn(g0, "CONTINUAR", 470, 275, 340, 48, true, true, this::togglePause);
            btn(g0, "REINICIAR", 470, 331, 340, 48, true, false, () -> startRace(ti, champRace));
            btn(g0, "SAIR PARA O MENU", 470, 387, 340, 48, true, false, () -> scr = "menu");
        }
    }

    void hud(Graphics2D g) {
        List<R> o = rk(); int pos = o.indexOf(P) + 1; double mm = 1 + P.m * .04, rpm = Math.min(1, P.v / (GM[P.g] * mm));
        // posição
        box(g, 10, 10, 100, 58, PU); tx(g, pos + "º", 20, 56, 40, pos == 1 ? GOLD : Color.WHITE, 0); tx(g, "/4", 78, 56, 14, Color.GRAY, 0);
        // progresso da corrida
        bar(g, 220, 22, 840, 10, P.d / T.len, T.ac);
        for (R r : rs) { g.setColor(r.c); double px = 220 + 840 * Math.max(0, Math.min(1, r.d / T.len)); g.fill(new Ellipse2D.Double(px - 6, 21, 12, 12)); }
        tx(g, T.n + "  •  " + Math.round(Math.max(0, Math.min(P.d, T.len))) + " / " + T.len + " m", W / 2.0, 58, 13, Color.LIGHT_GRAY, 1);
        // ranking
        box(g, W - 250, 10, 240, 20 + 4 * 24, PU);
        for (int i = 0; i < 4; i++) { R r = o.get(i); int y = 34 + i * 24; tx(g, "" + (i + 1), W - 240, y, 13, Color.WHITE, 0); tx(g, r.n, W - 220, y, 13, r.c, 0); tx(g, "" + Math.round(r.v), W - 20, y, 13, Color.WHITE, 2); }
        // velocímetro, nitro e calor
        box(g, 10, 590, 300, 120, PU);
        tx(g, "" + Math.round(P.v), 24, 648, 52, Color.WHITE, 0); tx(g, "km/h", 150, 648, 14, LIL, 0);
        tx(g, "NITRO", 24, 676, 11, LIL, 0); bar(g, 24, 682, 125, 8, P.nt / 100, CY);
        tx(g, "CALOR", 165, 676, 11, LIL, 0); bar(g, 165, 682, 125, 8, P.heat / 100, P.heat > 70 ? RED : new Color(255, 158, 61));
        // MARCHA (único controle manual)
        boolean sh = rpm > .88 && P.g < 5 && ((int) (tm * 8)) % 2 == 0;
        box(g, 330, 590, 620, 120, sh ? RED : PU);
        tx(g, "MARCHA", 348, 618, 13, LIL, 0); tx(g, "" + P.g, 358, 690, 64, CY, 0);
        int bx = 430, by = 640, bw = 500, bh = 26;
        g.setColor(new Color(255, 255, 255, 40)); g.fillRect(bx, by, bw, bh);
        g.setColor(rpm > .9 ? RED : rpm > .8 ? GREEN : CY); g.fillRect(bx, by, (int) (bw * rpm), bh);
        g.setColor(al(GREEN, 90)); g.fillRect(bx + (int) (bw * .9), by, bw - (int) (bw * .9), bh);
        g.setColor(Color.WHITE); g.setStroke(new BasicStroke(1.5f)); g.drawRect(bx, by, bw, bh); g.drawLine(bx + (int) (bw * .9), by - 4, bx + (int) (bw * .9), by + bh + 4);
        tx(g, P.g >= 5 ? "MARCHA MÁXIMA" : sh ? "SHIFT!  (aperte SHIFT ou ESPAÇO)" : "RPM", 680, 632, 17, P.g >= 5 ? GOLD : sh ? RED : LIL, 1);
        tx(g, "Solte a marcha na zona verde para ganhar turbo", 680, 696, 12, LIL, 1);
        // piloto automático
        box(g, 970, 590, 300, 120, PU);
        tx(g, "PILOTO AUTOMÁTICO", 985, 612, 12, LIL, 0);
        String[] ks = {"EMP", "ÓLEO", "GOLPE"}; double[] cds = {P.cdE, P.cdO, P.cdS}, mxs = {7, 4, 2.5}; Color[] cs = {CY, new Color(183, 132, 255), GOLD};
        for (int i = 0; i < 3; i++) {
            int x = 985 + i * 93; box(g, x, 622, 84, 44, cs[i]); g.setColor(al(cs[i], 100)); g.fillRect(x, 622, 84, (int) (44 * cds[i] / mxs[i]));
            tx(g, ks[i], x + 42, 650, 14, cds[i] > 0 ? Color.GRAY : Color.WHITE, 1);
        }
        tx(g, "Pista, nitro e trapaças: automáticos", 1120, 692, 11, LIL, 1);
        // mensagens
        for (int i = 0; i < msgs.size(); i++) { Msg m = msgs.get(i); tx(g, m.t, W / 2.0, 125 + i * 30, 24, al(m.c, (int) (255 * Math.min(1, m.life * 2))), 1); }
        if (state.equals("cd")) {
            int n = (int) Math.ceil(cd);
            for (int i = 0; i < 3; i++) { g.setColor(n > 0 && n <= i + 1 ? new Color(255, 23, 68) : new Color(42, 10, 20)); g.fill(new Ellipse2D.Double(W / 2.0 - 68 + i * 50, 80, 36, 36)); }
            tx(g, n > 3 ? "PREPARAR" : "" + n, W / 2.0, 360, n > 3 ? 48 : 80, Color.WHITE, 1);
            tx(g, "SHIFT ou ESPAÇO = passar marcha. O resto é automático!", W / 2.0, 400, 16, CY, 1);
        }
    }

    // ---------- telas de menu ----------
    void idleBackground(Graphics2D g, boolean showFleet) {
        scene(g, TR[idleTrack], camD);
        if (showFleet) for (int i = 0; i < 4; i++) { Cfg b = bots[i]; drawBot(g, b.c, b.ne, b.a, 110 + i * 165, 390 + Math.sin(tm * 1.6 + i * 1.7) * 30, .95, Math.PI / 2, false); }
    }
    void drawMenu(Graphics2D g) {
        idleBackground(g, true);
        box(g, 40, 30, 660, 190, PU);
        glow(g, "CYBER", 370, 108, 66, Color.WHITE); glow(g, "GRAND PRIX", 370, 168, 50, Color.WHITE);
        tx(g, "Corrida de robôs • jogo sujo liberado • só a marcha é com você", 370, 202, 14, CY, 1);
        panel(g, 760, 30, 490, 660);
        tx(g, "Pontos: " + pts + "   Vitórias: " + wins, 1005, 90, 16, Color.WHITE, 1);
        String[] names = {"CORRIDA RÁPIDA", "CAMPEONATO", "GARAGEM", "LOJA", "NOMES DOS CARROS", "ESTATÍSTICAS", "CONTROLES", "SAIR"};
        Runnable[] acts = {() -> scr = "tracks", () -> { if (chR < 0) { chR = 0; chPts = new int[4]; save(); } scr = "champ"; },
            () -> scr = "garage", () -> scr = "shop", () -> scr = "names", () -> scr = "stats", () -> scr = "help", () -> { save(); System.exit(0); }};
        for (int i = 0; i < names.length; i++) btn(g, names[i], 800, 120 + i * 68, 410, 54, true, i == 0, acts[i]);
    }
    void drawTracks(Graphics2D g) {
        idleBackground(g, false); panel(g, 20, 40, 1240, 640); tx(g, "ESCOLHA A PISTA", W / 2.0, 95, 26, CY, 1);
        for (int i = 0; i < 4; i++) {
            final int ix = i; Track t = TR[i]; int x = 45 + i * 300, y = 125; boolean ok = wins >= t.unl;
            g.setColor(new Color(18, 10, 42)); g.fillRoundRect(x, y, 280, 440, 14, 14); g.setColor(t.ac); g.setStroke(new BasicStroke(2)); g.drawRoundRect(x, y, 280, 440, 14, 14);
            g.setColor(t.bg); g.fillRect(x + 14, y + 14, 252, 110); g.setColor(t.road); g.fillRect(x + 14, y + 38, 252, 62);
            g.setColor(t.ac); g.fillRect(x + 14, y + 36, 252, 3); g.setColor(t.ac2); g.fillRect(x + 14, y + 99, 252, 3);
            tx(g, t.n, x + 16, y + 165, 22, t.ac, 0); tx(g, t.sub, x + 16, y + 195, 12, LIL, 0);
            tx(g, "Aderência " + Math.round(t.grip * 100) + "%", x + 16, y + 240, 14, Color.WHITE, 0);
            tx(g, "Extensão " + t.len + " m", x + 16, y + 266, 14, Color.WHITE, 0);
            if (!ok) tx(g, "Bloqueada: precisa de " + t.unl + " vitórias", x + 16, y + 310, 12, RED, 0);
            btn(g, ok ? "CORRER" : "BLOQUEADA", x + 20, y + 370, 240, 50, ok, ok, () -> startRace(ix, false));
        }
        btn(g, "NOMES DOS CARROS", 45, 600, 400, 52, true, false, () -> scr = "names");
        btn(g, "VOLTAR", 835, 600, 400, 52, true, false, () -> scr = "menu");
    }
    void drawNames(Graphics2D g) {
        idleBackground(g, false); panel(g, 300, 30, 680, 660); tx(g, "NOMES DOS CARROS", W / 2.0, 85, 26, CY, 1);
        tx(g, "Escolha o nome de cada robô que vai participar da corrida (máx. 12 letras).", W / 2.0, 114, 13, LIL, 1);
        Cfg b = bots[sel];
        for (int i = 0; i < 4; i++) {
            final int ix = i; int y = 138 + i * 100;
            Color c = i == 0 ? b.c : NC[i - 1], ne = i == 0 ? b.ne : NC[i - 1]; String n = i == 0 ? b.n : nn[i - 1];
            g.setColor(new Color(18, 10, 42)); g.fillRoundRect(325, y, 630, 84, 14, 14); g.setColor(c); g.setStroke(new BasicStroke(2)); g.drawRoundRect(325, y, 630, 84, 14, 14);
            drawBot(g, c, ne, i == 0 ? b.a : new ArrayList<>(), 375, y + 42, .6, Math.PI / 2, false);
            tx(g, i == 0 ? "SEU ROBÔ (Robô " + (sel + 1) + " da garagem)" : "ADVERSÁRIO " + i, 430, y + 32, 12, LIL, 0);
            tx(g, n, 430, y + 64, 24, c, 0);
            btn(g, "EDITAR NOME", 760, y + 20, 180, 44, true, false, () -> rename(ix));
        }
        btn(g, "NOMES PADRÃO DOS ADVERSÁRIOS", 325, 560, 400, 48, true, false, () -> { nn = NN.clone(); save(); });
        btn(g, "VOLTAR", 745, 560, 210, 48, true, true, () -> scr = "menu");
    }
    void drawChamp(Graphics2D g) {
        idleBackground(g, false); panel(g, 340, 30, 600, 660); tx(g, "CAMPEONATO CYBER", W / 2.0, 85, 26, CY, 1);
        int r = Math.max(0, chR); tx(g, "Etapa " + (r + 1) + "/4 • " + TR[r].n, W / 2.0, 118, 16, TR[r].ac, 1);
        String[] names = {bots[sel].n, nn[0], nn[1], nn[2]}; Integer[] idx = {0, 1, 2, 3};
        Arrays.sort(idx, (a, b) -> chPts[b] - chPts[a]);
        for (int i = 0; i < 4; i++) {
            int y = 140 + i * 58; g.setColor(new Color(18, 10, 42)); g.fillRoundRect(375, y, 530, 46, 10, 10);
            tx(g, (i + 1) + "º " + names[idx[i]], 392, y + 30, 16, Color.WHITE, 0); tx(g, chPts[idx[i]] + " pts", 890, y + 30, 16, GOLD, 2);
        }
        tx(g, "Pontos por etapa: 10 / 6 / 3 / 1. Campeão ganha +30 pontos.", W / 2.0, 400, 13, LIL, 1);
        btn(g, "CORRER ETAPA", 380, 425, 520, 52, true, true, () -> startRace(Math.max(0, chR), true));
        btn(g, "NOMES DOS CARROS", 380, 489, 520, 46, true, false, () -> scr = "names");
        btn(g, "ABANDONAR CAMPEONATO", 380, 545, 520, 46, true, false, () -> { chR = -1; chPts = new int[4]; save(); scr = "menu"; });
        btn(g, "VOLTAR", 380, 601, 520, 46, true, false, () -> scr = "menu");
    }
    void drawGarage(Graphics2D g) {
        idleBackground(g, false); Cfg b = bots[sel];
        panel(g, 30, 40, 380, 640);
        drawBot(g, b.c, b.ne, b.a, 220, 290, 3.0, Math.sin(tm) * .12, false);
        tx(g, b.n, 220, 530, 22, b.ne, 1); tx(g, "Motor Nv " + b.m + "   |   Pneus Nv " + b.g, 220, 570, 14, Color.WHITE, 1);
        panel(g, 430, 40, 820, 640); tx(g, "GARAGEM", 460, 92, 26, CY, 0);
        for (int i = 0; i < 4; i++) { final int ix = i; btn(g, "Robô " + (i + 1), 460 + i * 195, 115, 180, 44, i != sel, i == sel, () -> { sel = ix; save(); }); }
        btn(g, "Nome: " + b.n + "  (clique para editar)", 460, 175, 760, 48, true, false, () -> rename(0));
        btn(g, "Cor do veículo", 460, 235, 375, 48, true, false, () -> { Color c = JColorChooser.showDialog(this, "Cor do veículo", b.c); if (c != null) { b.c = c; save(); } });
        btn(g, "Cor do neon", 845, 235, 375, 48, true, false, () -> { Color c = JColorChooser.showDialog(this, "Cor do neon", b.ne); if (c != null) { b.ne = c; save(); } });
        tx(g, "Acessórios (máx. 3) - compre na loja", 460, 330, 15, LIL, 0);
        if (owned.isEmpty()) tx(g, "Vença corridas para ganhar pontos e comprar acessórios.", 460, 365, 14, Color.WHITE, 0);
        for (int i = 0; i < owned.size(); i++) {
            final String n = owned.get(i); boolean on = b.a.contains(n);
            btn(g, (on ? "[x] " : "[ ] ") + n, 460 + (i % 3) * 255, 345 + (i / 3) * 56, 245, 46, true, on, () -> {
                if (b.a.contains(n)) b.a.remove(n); else if (b.a.size() < 3) b.a.add(n); save();
            });
        }
        btn(g, "PRONTO", 460, 610, 760, 52, true, true, () -> scr = "menu");
    }
    void drawShop(Graphics2D g) {
        idleBackground(g, false); Cfg b = bots[sel];
        panel(g, 30, 40, 380, 640);
        drawBot(g, b.c, b.ne, b.a, 220, 290, 3.0, 0, false);
        tx(g, b.n, 220, 530, 22, b.ne, 1); tx(g, "Motor Nv " + b.m + "   |   Pneus Nv " + b.g, 220, 570, 14, Color.WHITE, 1);
        panel(g, 430, 40, 820, 640); tx(g, "LOJINHA", 460, 92, 26, CY, 0); tx(g, "Pontos: " + pts, 1220, 92, 20, GOLD, 2);
        tx(g, "Pontos só vêm de vitórias. Compras vão para o robô " + b.n + ".", 460, 122, 13, LIL, 0);
        for (int i = 0; i < 6; i++) {
            final int ix = i; boolean has = owned.contains(ACN[i]); int y = 145 + i * 52;
            g.setColor(new Color(18, 10, 42)); g.fillRoundRect(455, y, 770, 46, 10, 10);
            tx(g, ACN[i], 475, y + 30, 16, Color.WHITE, 0);
            btn(g, has ? "Seu" : ACP[i] + " pts", 1065, y + 4, 150, 38, !has && pts >= ACP[i], false, () -> {
                pts -= ACP[ix]; owned.add(ACN[ix]); if (b.a.size() < 3) b.a.add(ACN[ix]); save();
            });
        }
        String[] ul = {"Motor +4% velocidade", "Pneus +aderência"};
        for (int k = 0; k < 2; k++) {
            final int kk = k; int lv = k == 0 ? b.m : b.g, cost = 12 * (lv + 1), y = 475 + k * 52;
            g.setColor(new Color(18, 10, 42)); g.fillRoundRect(455, y, 770, 46, 10, 10);
            tx(g, ul[k] + " (Nv " + lv + "/5)", 475, y + 30, 16, Color.WHITE, 0);
            btn(g, lv >= 5 ? "MAX" : cost + " pts", 1065, y + 4, 150, 38, lv < 5 && pts >= cost, false, () -> { pts -= cost; if (kk == 0) b.m++; else b.g++; save(); });
        }
        btn(g, "VOLTAR", 460, 610, 760, 52, true, true, () -> scr = "menu");
    }
    void drawStats(Graphics2D g) {
        idleBackground(g, false); panel(g, 340, 30, 600, 660); tx(g, "ESTATÍSTICAS", W / 2.0, 85, 26, CY, 1);
        int wr = races > 0 ? Math.round(wins * 100f / races) : 0;
        String[][] rows = {{"Partidas jogadas", "" + races}, {"Vitórias", "" + wins}, {"Pódios", "" + pod}, {"Aproveitamento", wr + "%"}, {"Campeonatos ganhos", "" + champs},
            {"Velocidade máxima", best + " km/h"}, {"Distância corrida", String.format("%.1f km", dist / 1000.0)}, {"Trapaças aplicadas", "" + tricks}, {"Pontos na loja", "" + pts}};
        for (int i = 0; i < rows.length; i++) {
            int y = 110 + i * 50; g.setColor(new Color(18, 10, 42)); g.fillRoundRect(375, y, 530, 42, 10, 10);
            tx(g, rows[i][0], 395, y + 28, 16, Color.WHITE, 0); tx(g, rows[i][1], 885, y + 28, 16, GOLD, 2);
        }
        btn(g, "VOLTAR", 380, 618, 520, 52, true, true, () -> scr = "menu");
    }
    void drawHelp(Graphics2D g) {
        idleBackground(g, false); panel(g, 240, 30, 800, 660); tx(g, "CONTROLES", W / 2.0, 85, 26, CY, 1);
        String[] l = {"SHIFT ou ESPAÇO  -  passar marcha (solte na zona verde!)", "P ou ESC  -  pausar", "F11  -  maximizar / tela cheia", "",
            "TUDO O RESTO É AUTOMÁTICO:", "•  troca de pista e desvio de óleo e minas", "•  nitro", "•  trapaças: EMP, óleo e golpe lateral", "",
            "A sua marcha decide a corrida: troque cedo demais e perde", "velocidade; troque no giro alto e ganhe turbo.",
            "Minas explodem, óleo faz derrapar.", "Só vitórias rendem pontos para a loja."};
        for (int i = 0; i < l.length; i++) tx(g, l[i], 290, 135 + i * 36, 16, i == 4 ? GREEN : i >= 9 ? GOLD : Color.WHITE, 0);
        btn(g, "VOLTAR", 380, 618, 520, 52, true, true, () -> scr = "menu");
    }
    void drawResult(Graphics2D g) {
        idleBackground(g, false); panel(g, 340, 30, 600, 660);
        glow(g, resPos == 1 ? "VITÓRIA!" : resPos + "º LUGAR", W / 2.0, 100, 44, Color.WHITE);
        for (int i = 0; i < 4; i++) {
            R r = resOrder.get(i); int y = 130 + i * 52; g.setColor(new Color(18, 10, 42)); g.fillRoundRect(375, y, 530, 42, 10, 10); g.setColor(r.c); g.setStroke(new BasicStroke(1.5f)); g.drawRoundRect(375, y, 530, 42, 10, 10);
            tx(g, (i + 1) + "º " + r.n, 392, y + 28, 16, r.c, 0);
            tx(g, r.fin > 0 && r.fin <= P.fin ? String.format("%.1f s", r.fin) : "-", 890, y + 28, 15, Color.WHITE, 2);
        }
        int y = 370;
        tx(g, resGain > 0 ? "+" + resGain + " pontos de vitória" : "Só vitórias rendem pontos. Revanche!", W / 2.0, y, 16, resGain > 0 ? GOLD : Color.WHITE, 1);
        if (resBonus > 0) tx(g, "CAMPEÃO! +" + resBonus + " pontos", W / 2.0, y += 28, 16, GOLD, 1);
        if (resUnl) tx(g, "Nova pista desbloqueada!", W / 2.0, y += 28, 16, GREEN, 1);
        if (resRows != null) tx(g, "Campeonato: " + bots[sel].n + " " + resRows[0] + " | " + nn[0] + " " + resRows[1] + " | " + nn[1] + " " + resRows[2] + " | " + nn[2] + " " + resRows[3], W / 2.0, y += 30, 12, LIL, 1);
        if (champRace) { if (!resDone) btn(g, "CONTINUAR CAMPEONATO", 380, 520, 520, 52, true, true, () -> scr = "champ"); }
        else { btn(g, "CORRER DE NOVO", 380, 520, 520, 52, true, true, () -> startRace(ti, false)); btn(g, "TROCAR PISTA", 380, 580, 520, 46, true, false, () -> scr = "tracks"); }
        btn(g, "LOJA", 380, 634, 250, 44, true, false, () -> scr = "shop"); btn(g, "MENU", 650, 634, 250, 44, true, false, () -> scr = "menu");
    }

    // =====================================================================
    //  ENTRADA
    // =====================================================================
    void togglePause() {
        if (!scr.equals("race")) return;
        if (state.equals("pause")) state = prevState;
        else if (state.equals("run") || state.equals("cd")) { prevState = state; state = "pause"; }
    }
    @Override public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode(); boolean first = held.add(k); if (!first) return;
        if (k == KeyEvent.VK_P || k == KeyEvent.VK_ESCAPE) togglePause();
        else if (k == KeyEvent.VK_F11 && frame != null) frame.setExtendedState(frame.getExtendedState() ^ JFrame.MAXIMIZED_BOTH);
        if (!scr.equals("race") || !state.equals("run")) return;
        if (k == KeyEvent.VK_SHIFT || k == KeyEvent.VK_SPACE) shiftUp();   // único controle manual
    }
    @Override public void keyReleased(KeyEvent e) { held.remove(e.getKeyCode()); }
    @Override public void keyTyped(KeyEvent e) { }
    @Override public void mousePressed(MouseEvent e) {
        double lx = (e.getX() - ox) / sc, ly = (e.getY() - oy) / sc; requestFocusInWindow();
        for (Btn b : new ArrayList<>(btns)) if (b.en && b.r.contains(lx, ly)) { b.a.run(); break; }
    }
    @Override public void mouseMoved(MouseEvent e) { mx = (e.getX() - ox) / sc; my = (e.getY() - oy) / sc; }
    @Override public void mouseDragged(MouseEvent e) { mouseMoved(e); }
    @Override public void mouseClicked(MouseEvent e) { }
    @Override public void mouseReleased(MouseEvent e) { }
    @Override public void mouseEntered(MouseEvent e) { }
    @Override public void mouseExited(MouseEvent e) { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("Cyber Grand Prix");
            CyberGrandPrixPC game = new CyberGrandPrixPC();
            Dimension s = Toolkit.getDefaultToolkit().getScreenSize();
            int w = Math.min(W, s.width - 40), h = w * H / W;
            if (h > s.height - 90) { h = s.height - 90; w = h * W / H; }
            game.setPreferredSize(new Dimension(w, h));
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { game.save(); } });
            frame.setContentPane(game); frame.pack(); frame.setLocationRelativeTo(null); frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}
