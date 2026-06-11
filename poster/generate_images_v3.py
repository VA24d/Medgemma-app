"""
Med Veda poster images v3 — clean, professional, poster-quality.
"""

from PIL import Image, ImageDraw, ImageFont
import os, math

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "assets")
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.makedirs(ASSETS, exist_ok=True)

def get_font(size, bold=False):
    paths = [
        ("C:/Windows/Fonts/calibrib.ttf" if bold else "C:/Windows/Fonts/calibri.ttf"),
        "C:/Windows/Fonts/segoeui.ttf",
    ]
    for f in paths:
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()

def rounded_rect(draw, xy, radius, fill=None, outline=None, width=1):
    draw.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=width)

def draw_arrow_h(draw, x1, y1, x2, y2, color, width=3, head=14):
    """Arrow from (x1,y1) to (x2,y2) — works for horizontal lines."""
    draw.line([(int(x1), int(y1)), (int(x2), int(y2))], fill=color, width=width)
    y = y2
    d = 1 if x2 > x1 else -1
    pts = [(int(x2), int(y)), (int(x2 - d*head), int(y - head//2)), (int(x2 - d*head), int(y + head//2))]
    draw.polygon(pts, fill=color)

def draw_arrow_v(draw, x, y1, y2, color, width=3, head=14):
    """Vertical arrow from (x,y1) to (x,y2)."""
    draw.line([(int(x), int(y1)), (int(x), int(y2))], fill=color, width=width)
    d = 1 if y2 > y1 else -1
    pts = [(int(x), int(y2)), (int(x - head//2), int(y2 - d*head)), (int(x + head//2), int(y2 - d*head))]
    draw.polygon(pts, fill=color)

def draw_dashed_line_h(draw, x1, y, x2, color, width=2, dash=12, gap=8):
    cx = int(x1)
    x2 = int(x2)
    y = int(y)
    while cx < x2:
        draw.line([(cx, y), (min(cx + dash, x2), y)], fill=color, width=width)
        cx += dash + gap

def text_center(draw, x, y, text, font, fill):
    draw.text((x, y), text, fill=fill, font=font, anchor="mm")

def text_left(draw, x, y, text, font, fill):
    draw.text((x, y), text, fill=fill, font=font, anchor="lm")


def create_architecture_v3():
    """
    Clean three-tier architecture: left-to-right flow.
    Tier1 (Device) -> Tier2 (Near Edge) -> Tier3 (VPC)
    With Public Internet below, separated.
    """
    W, H = 3200, 1600
    img = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    draw = ImageDraw.Draw(img)

    f_title = get_font(42, bold=True)
    f_tier = get_font(30, bold=True)
    f_head = get_font(24, bold=True)
    f_body = get_font(21)
    f_small = get_font(18)
    f_chip = get_font(16, bold=True)
    f_arrow = get_font(17, bold=True)

    # Background
    rounded_rect(draw, (0, 0, W, H), 0, fill=(250, 252, 255, 255))

    # ---- Clinician ----
    cx, cy = 80, 320
    draw.ellipse((cx-22, cy-50, cx+22, cy-6), outline=(55,71,79), width=3)
    draw.line((cx, cy-6, cx, cy+45), fill=(55,71,79), width=3)
    draw.line((cx-28, cy+15, cx+28, cy+15), fill=(55,71,79), width=3)
    draw.line((cx, cy+45, cx-22, cy+78), fill=(55,71,79), width=3)
    draw.line((cx, cy+45, cx+22, cy+78), fill=(55,71,79), width=3)
    text_center(draw, cx, cy+100, "Clinician", f_body, (55,71,79))

    # ============ TIER 1 — DEVICE ============
    t1x, t1y = 170, 60
    t1w, t1h = 900, 720
    # Outer
    rounded_rect(draw, (t1x, t1y, t1x+t1w, t1y+t1h), 22,
                 fill=(237,247,237), outline=(27,94,32), width=4)
    # Header
    draw.rectangle((t1x+2, t1y+2, t1x+t1w-2, t1y+58), fill=(27,94,32))
    text_center(draw, t1x+t1w//2, t1y+30, "TIER 1 — ON-DEVICE", f_tier, (255,255,255))
    # Chip
    rounded_rect(draw, (t1x+t1w-150, t1y+10, t1x+t1w-18, t1y+50), 12, fill=(56,142,60))
    text_center(draw, t1x+t1w-84, t1y+30, "SHIPPED", f_chip, (255,255,255))

    # Internal boxes — two columns
    def t1_box(x, y, w, h, label, sub, bg):
        rounded_rect(draw, (x,y,x+w,y+h), 10, fill=bg, outline=(120,160,120), width=2)
        text_center(draw, x+w//2, y+h//2 - 12, label, f_head, (20,60,20))
        if sub:
            text_center(draw, x+w//2, y+h//2 + 16, sub, f_small, (60,100,60))

    col1x = t1x + 30
    col2x = t1x + 470
    bw = 400
    by = t1y + 80

    t1_box(col1x, by,      bw, 80, "Med Veda App", "Jetpack Compose UI", (220,240,220))
    t1_box(col2x, by,      bw, 80, "Room DB", "Longitudinal Patient Chart", (255,248,225))
    t1_box(col1x, by+100,  bw, 80, "MedGemma GGUF", "Text + Chart-Grounded Chat", (220,240,220))
    t1_box(col2x, by+100,  bw, 80, "Encrypted Storage", "PIN / Secure Prefs", (255,248,225))
    t1_box(col1x, by+200,  bw, 80, "mmproj Vision", "X-ray / Histopath (Lazy Load)", (220,240,220))
    t1_box(col2x, by+200,  bw, 80, "FHIR Export", "Bundle Generation", (255,248,225))
    t1_box(col1x, by+300,  bw, 80, "Prognosis Worker", "Overnight On-Device Batch", (220,240,220))
    t1_box(col2x, by+300,  bw, 80, "Multilingual Output", "Telugu / Hindi Injection", (255,248,225))

    # Snapdragon bar
    snap_y = t1y + t1h - 100
    rounded_rect(draw, (t1x+30, snap_y, t1x+t1w-30, snap_y+75), 14,
                 fill=(40,70,160), outline=(30,50,120), width=2)
    text_center(draw, t1x+t1w//2, snap_y+22, "Qualcomm Snapdragon SoC", f_head, (255,255,255))
    text_center(draw, t1x+t1w//2, snap_y+52, "CPU (KleidiAI)  ·  Adreno GPU  ·  Hexagon NPU", f_small, (190,210,255))

    # Arrow from clinician to tier 1
    draw_arrow_h(draw, cx+30, cy, t1x, cy, (55,71,79), 3)

    # ============ TIER 2 — NEAR EDGE ============
    t2x, t2y = 1200, 60
    t2w, t2h = 900, 440
    rounded_rect(draw, (t2x, t2y, t2x+t2w, t2y+t2h), 22,
                 fill=(232,243,255), outline=(21,101,192), width=4)
    draw.rectangle((t2x+2, t2y+2, t2x+t2w-2, t2y+58), fill=(21,101,192))
    text_center(draw, t2x+t2w//2, t2y+30, "TIER 2 — NEAR EDGE", f_tier, (255,255,255))
    rounded_rect(draw, (t2x+t2w-190, t2y+10, t2x+t2w-18, t2y+50), 12, fill=(25,118,210))
    text_center(draw, t2x+t2w-104, t2y+30, "PROTOTYPE", f_chip, (255,255,255))

    t2by = t2y + 80
    def t2_box(x, y, w, h, label, sub):
        rounded_rect(draw, (x,y,x+w,y+h), 10, fill=(200,225,255), outline=(80,140,220), width=2)
        text_center(draw, x+w//2, y+h//2 - 12, label, f_head, (15,60,140))
        if sub:
            text_center(draw, x+w//2, y+h//2 + 16, sub, f_small, (50,90,160))

    t2_box(t2x+30, t2by,      400, 80, "Edge Companion Hub", "FastAPI · Port 8787")
    t2_box(t2x+470, t2by,     400, 80, "GPU Imaging Node", "Hospital LAN GPU")
    t2_box(t2x+30, t2by+100,  400, 80, "Patient Sync", "Two-Way Device ↔ Hub")
    t2_box(t2x+470, t2by+100, 400, 80, "Vision Service", "Returns Structured JSON Only")
    t2_box(t2x+150, t2by+210, 600, 70, "Web Dashboard + LLM Router", "Ollama / Gemini Selection")

    # ============ TIER 3 — VPC ============
    t3x, t3y = 1200, 560
    t3w, t3h = 900, 220
    rounded_rect(draw, (t3x, t3y, t3x+t3w, t3y+t3h), 22,
                 fill=(243,233,250), outline=(69,39,160), width=4)
    draw.rectangle((t3x+2, t3y+2, t3x+t3w-2, t3y+58), fill=(69,39,160))
    text_center(draw, t3x+t3w//2, t3y+30, "TIER 3 — HOSPITAL VPC / BATCH", f_tier, (255,255,255))
    rounded_rect(draw, (t3x+t3w-165, t3y+10, t3x+t3w-18, t3y+50), 12, fill=(94,53,177))
    text_center(draw, t3x+t3w-91, t3y+30, "ROADMAP", f_chip, (255,255,255))

    text_center(draw, t3x+t3w//2, t3y+95, "FHIR Ingest  →  27B+ Models  →  Rare Disease / Cohort Analytics", f_head, (50,25,100))
    text_center(draw, t3x+t3w//2, t3y+140, "Overnight batch · Full-history reasoning · Results next morning", f_body, (80,50,130))
    text_center(draw, t3x+t3w//2, t3y+175, "On-device ScheduledPrognosisWorker is the shipped edge version", f_small, (110,80,150))

    # ============ ARROWS between tiers ============
    # T1 → T2: study pixels (going right)
    arr_y1 = t1y + 200
    draw_arrow_h(draw, t1x+t1w, arr_y1, t2x, arr_y1, (21,101,192), 4)
    text_center(draw, (t1x+t1w+t2x)//2, arr_y1-22, "Wi-Fi: study pixels", f_arrow, (21,101,192))
    text_center(draw, (t1x+t1w+t2x)//2, arr_y1-2, "(governed upload)", f_small, (80,130,200))

    # T2 → T1: findings JSON (going left, below)
    arr_y2 = t1y + 310
    draw_arrow_h(draw, t2x, arr_y2, t1x+t1w, arr_y2, (27,94,32), 4)
    text_center(draw, (t1x+t1w+t2x)//2, arr_y2-22, "Structured JSON findings", f_arrow, (27,94,32))

    # T1 → T3: FHIR export (diagonal)
    fhir_x1 = t1x + t1w
    fhir_y1 = t1y + 550
    fhir_x2 = t3x
    fhir_y2 = t3y + 110
    draw.line([(fhir_x1, fhir_y1), (fhir_x2, fhir_y2)], fill=(69,39,160), width=3)
    # arrowhead
    angle = math.atan2(fhir_y2-fhir_y1, fhir_x2-fhir_x1)
    L = 16
    for da in [0.75, -0.75]:
        a = angle + math.pi * da + math.pi
        draw.line([(fhir_x2, fhir_y2),
                   (fhir_x2 + int(L*math.cos(a)), fhir_y2 + int(L*math.sin(a)))],
                  fill=(69,39,160), width=3)
    text_center(draw, (fhir_x1+fhir_x2)//2 - 30, (fhir_y1+fhir_y2)//2 - 15,
                "FHIR export", f_arrow, (69,39,160))

    # ============ PUBLIC INTERNET ============
    inet_x, inet_y = 2250, 60
    inet_w, inet_h = 900, 340
    rounded_rect(draw, (inet_x, inet_y, inet_x+inet_w, inet_y+inet_h), 22,
                 fill=(242,244,247), outline=(100,115,128), width=3)
    draw.rectangle((inet_x+2, inet_y+2, inet_x+inet_w-2, inet_y+58), fill=(100,115,128))
    text_center(draw, inet_x+inet_w//2, inet_y+30, "PUBLIC INTERNET", f_tier, (255,255,255))

    rounded_rect(draw, (inet_x+40, inet_y+80, inet_x+inet_w//2-20, inet_y+170), 10,
                 fill=(235,237,240), outline=(140,150,160), width=2)
    text_center(draw, inet_x+inet_w//4+10, inet_y+110, "Hugging Face CDN", f_head, (50,60,70))
    text_center(draw, inet_x+inet_w//4+10, inet_y+140, "Model Weights Download", f_small, (90,100,110))

    rounded_rect(draw, (inet_x+inet_w//2+20, inet_y+80, inet_x+inet_w-40, inet_y+170), 10,
                 fill=(235,237,240), outline=(140,150,160), width=2)
    text_center(draw, inet_x+3*inet_w//4-10, inet_y+110, "OAuth / Token", f_head, (50,60,70))
    text_center(draw, inet_x+3*inet_w//4-10, inet_y+140, "Authentication Only", f_small, (90,100,110))

    # Red block
    rounded_rect(draw, (inet_x+80, inet_y+200, inet_x+inet_w-80, inet_y+300), 14,
                 fill=(255,235,238), outline=(198,40,40), width=3)
    text_center(draw, inet_x+inet_w//2, inet_y+235, "NO Clinical PHI Inference", f_tier, (183,28,28))
    text_center(draw, inet_x+inet_w//2, inet_y+270, "Artifacts & auth only — never patient data", f_small, (183,28,28))

    # T1 → Internet (dashed)
    draw_dashed_line_h(draw, t1x+t1w, 140, inet_x, (100,115,128), 2)
    # small arrowhead
    draw.polygon([(inet_x, 140), (inet_x-12, 133), (inet_x-12, 147)], fill=(100,115,128))
    text_center(draw, (t1x+t1w+inet_x)//2 + 200, 122, "artifacts only (no PHI)", f_arrow, (100,115,128))

    # ============ TRUST BOUNDARY BAR ============
    tb_y = 850
    rounded_rect(draw, (40, tb_y, W-40, tb_y+90), 16,
                 fill=(232,245,233), outline=(27,94,32), width=3)
    text_center(draw, W//2, tb_y+28,
                "TRUST BOUNDARY: Clinical narrative and chart Q&A stay on device.",
                f_tier, (27,94,32))
    text_center(draw, W//2, tb_y+62,
                "Edge/cloud tiers are user-governed. Chart text never goes to third-party LLMs unless clinician explicitly opts in.",
                f_body, (40,100,40))

    # ============ DATA FLOW LEGEND ============
    leg_y = 990
    text_left(draw, 60, leg_y, "Data Flow Legend:", f_head, (50,50,50))
    items = [
        ((27,94,32), "On-device data (stays local)"),
        ((21,101,192), "Governed upload (study pixels only)"),
        ((69,39,160), "User-initiated export (FHIR)"),
        ((100,115,128), "Artifacts only (model weights, auth)"),
        ((183,28,28), "Blocked in v1 (no PHI to public cloud)"),
    ]
    lx = 340
    for color, label in items:
        draw.rectangle((lx, leg_y-10, lx+25, leg_y+10), fill=color)
        text_left(draw, lx+35, leg_y, label, f_body, (50,50,50))
        lx += 420 + len(label)

    img_cropped = img.crop((0, 0, W, 1060))
    img_cropped.save(os.path.join(ASSETS, "architecture_v3.png"), dpi=(300, 300))
    print("Created architecture_v3.png")


def create_benchmark_v3():
    """Benchmark chart without Q4_K_M emphasis, cleaner layout."""
    W, H = 2800, 1000
    img = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    draw = ImageDraw.Draw(img)

    f_title = get_font(34, bold=True)
    f_head = get_font(24, bold=True)
    f_body = get_font(20)
    f_val = get_font(18, bold=True)
    f_small = get_font(16)

    rounded_rect(draw, (0, 0, W, H), 0, fill=(253, 250, 247, 255))

    draw.text((W//2, 25), "Multi-Benchmark Evaluation: Quantization vs BF16 Baseline",
              fill=(10,26,47), font=f_title, anchor="mt")
    draw.text((W//2, 65), "500 samples per dataset · RTX 4090 GPU · Greedy decoding",
              fill=(100,100,100), font=f_body, anchor="mt")

    benchmarks = ["MedMCQA", "MedQA", "PubMedQA", "MMLU Med", "MedXpertQA"]
    models = [
        ("BF16",   [43.80, 29.00, 55.40, 43.00, 8.80], (120,120,120), "7.3 GB"),
        ("Q8_0",   [44.40, 28.60, 55.40, 43.60, 8.80], (27,94,32),    "3.9 GB"),
        ("Q6_K",   [40.80, 28.40, 57.40, 41.40, 9.80], (21,101,192),  "3.0 GB"),
        ("Q4_K_M", [32.60, 29.00, 55.40, 29.80, 10.00],(200,120,40),  "2.4 GB"),
    ]

    chart_x, chart_y = 140, 110
    chart_w, chart_h = 1800, 700
    max_val = 62

    # Grid
    for pct in range(0, 65, 10):
        y = chart_y + chart_h - (pct / max_val) * chart_h
        draw.line((chart_x, int(y), chart_x + chart_w, int(y)), fill=(228,228,228), width=1)
        draw.text((chart_x - 15, int(y)), f"{pct}%", fill=(130,130,130), font=f_small, anchor="rm")

    bar_group_w = chart_w / len(benchmarks)
    bar_w = bar_group_w / (len(models) + 1.5)

    for bi, bench in enumerate(benchmarks):
        gx = chart_x + bi * bar_group_w + bar_w * 0.75
        for mi, (mname, vals, mcolor, sz) in enumerate(models):
            bx = gx + mi * bar_w
            bar_h = (vals[bi] / max_val) * chart_h
            by = chart_y + chart_h - bar_h
            rounded_rect(draw, (int(bx)+2, int(by), int(bx+bar_w)-2, int(chart_y+chart_h)),
                         6, fill=mcolor)
            draw.text((int(bx + bar_w/2), int(by) - 4),
                      f"{vals[bi]:.1f}", fill=mcolor, font=f_small, anchor="mb")

        draw.text((int(gx + bar_group_w/2 - bar_w*0.75), int(chart_y + chart_h + 15)),
                  bench, fill=(40,40,40), font=f_head, anchor="mt")

    # Legend (right side)
    leg_x = chart_x + chart_w + 80
    leg_y = chart_y + 40
    draw.text((leg_x, leg_y - 30), "Models", fill=(40,40,40), font=f_head)
    for mi, (mname, vals, mcolor, sz) in enumerate(models):
        y = leg_y + mi * 55
        rounded_rect(draw, (leg_x, y, leg_x+30, y+30), 5, fill=mcolor)
        draw.text((leg_x+42, y+15), f"{mname}  ({sz})", fill=(40,40,40), font=f_body, anchor="lm")

    # Key findings (right side, below legend)
    fy = leg_y + 280
    draw.text((leg_x, fy), "Key Findings", fill=(40,40,40), font=f_head)
    findings = [
        ("Q8_0 is lossless", "Zero accuracy drop,\n47% smaller", (27,94,32)),
        ("Q6_K near-lossless", "Within noise margin,\n59% smaller", (21,101,192)),
        ("Reasoning preserved", "MedQA & PubMedQA\nstable across all quants", (100,80,40)),
    ]
    for i, (title, desc, color) in enumerate(findings):
        cy = fy + 40 + i * 120
        rounded_rect(draw, (leg_x, cy, leg_x+700, cy+100), 12,
                     fill=(255,255,255), outline=color, width=2)
        # color bar left
        draw.rectangle((leg_x+2, cy+2, leg_x+8, cy+98), fill=color)
        draw.text((leg_x+22, cy+18), title, fill=color, font=f_head)
        for j, dl in enumerate(desc.split("\n")):
            draw.text((leg_x+22, cy+50+j*24), dl, fill=(70,70,70), font=f_body)

    img.save(os.path.join(ASSETS, "benchmark_v3.png"), dpi=(300, 300))
    print("Created benchmark_v3.png")


def create_kpi_v3():
    """KPI cards — wider, better spaced, no Q4_K_M emphasis."""
    W, H = 2800, 320
    img = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    draw = ImageDraw.Draw(img)

    f_val = get_font(56, bold=True)
    f_unit = get_font(20)
    f_label = get_font(19, bold=True)

    metrics = [
        ("~20", "tok/s", "Inference Speed", (230,81,0)),
        ("4.1 GB", "peak", "RAM Usage", (21,101,192)),
        ("2.4 GB", "on disk", "Model Size", (27,94,32)),
        ("5", "datasets", "Benchmarks Run", (69,39,160)),
        ("2,500", "total", "Test Questions", (183,28,28)),
    ]

    card_w = (W - 30) // len(metrics)
    for i, (val, unit, label, color) in enumerate(metrics):
        cx = 15 + i * card_w
        rounded_rect(draw, (cx, 8, cx+card_w-15, H-8), 16,
                     fill=(255,255,255), outline=color, width=3)
        # Top accent
        draw.rectangle((cx+3, 8, cx+card_w-18, 20), fill=color)

        mid = cx + (card_w-15) // 2
        draw.text((mid, 75), val, fill=color, font=f_val, anchor="mt")
        draw.text((mid, 165), unit, fill=(150,150,150), font=f_unit, anchor="mt")
        draw.text((mid, 210), label, fill=(10,26,47), font=f_label, anchor="mt")

    img.save(os.path.join(ASSETS, "kpi_v3.png"), dpi=(300, 300))
    print("Created kpi_v3.png")


def create_trust_v3():
    """Trust boundary data flow diagram — horizontal."""
    W, H = 2800, 450
    img = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    draw = ImageDraw.Draw(img)

    f_head = get_font(24, bold=True)
    f_body = get_font(19)
    f_small = get_font(16)

    zones = [
        ("DEVICE", "Trust Zone A", 40, 610, (232,245,233), (27,94,32),
         ["Chart text & notes", "Local LLM inference", "Patient history (Room DB)", "PIN / encrypted prefs"]),
        ("NEAR EDGE", "Trust Zone B", 700, 610, (227,242,253), (21,101,192),
         ["Study pixels (governed)", "Structured JSON findings", "Edge companion sync", "Web dashboard"]),
        ("HOSPITAL VPC", "Trust Zone C", 1360, 610, (243,229,245), (69,39,160),
         ["FHIR bundles (user export)", "Batch insights returned", "27B+ model processing", "Cohort analytics"]),
        ("BLOCKED (v1)", "", 2020, 610, (255,220,220), (198,40,40),
         ["Public cloud LLM on", "full patient chart", "NOT ALLOWED"]),
    ]

    for label, sub, x, w, bg, border, items in zones:
        rounded_rect(draw, (x, 10, x+w, H-10), 18, fill=bg, outline=border, width=3)
        draw.rectangle((x+2, 10, x+w-2, 60), fill=border)
        text_center(draw, x+w//2, 24, label, f_head, (255,255,255))
        if sub:
            text_center(draw, x+w//2, 48, sub, f_small, (220,220,255))
        for i, item in enumerate(items):
            text_left(draw, x+25, 90+i*38, f"  {item}", f_body, border)

    # Arrows
    draw_arrow_h(draw, 650, H//2, 700, H//2, (21,101,192), 3)
    draw_arrow_h(draw, 1310, H//2, 1360, H//2, (69,39,160), 3)
    # Red X
    draw.text((1995, H//2), "✕", fill=(198,40,40), font=get_font(44, bold=True), anchor="mm")

    img.save(os.path.join(ASSETS, "trust_v3.png"), dpi=(300, 300))
    print("Created trust_v3.png")


if __name__ == "__main__":
    create_architecture_v3()
    create_benchmark_v3()
    create_kpi_v3()
    create_trust_v3()
    print("\nAll v3 images generated.")
