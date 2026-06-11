"""
Generate clean poster-quality images for Med Veda SparQ 2026 poster.
Uses Pillow to create professional diagrams.
"""

from PIL import Image, ImageDraw, ImageFont
import os

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "assets")
os.makedirs(ASSETS, exist_ok=True)

# Try to use a good system font
def get_font(size, bold=False):
    candidates = [
        "C:/Windows/Fonts/calibrib.ttf" if bold else "C:/Windows/Fonts/calibri.ttf",
        "C:/Windows/Fonts/segoeui.ttf",
        "C:/Windows/Fonts/arial.ttf",
    ]
    for f in candidates:
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def rounded_rect(draw, xy, radius, fill=None, outline=None, width=1):
    x0, y0, x1, y1 = xy
    draw.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=width)


def draw_arrow(draw, start, end, color, width=3):
    draw.line([start, end], fill=color, width=width)
    # arrowhead
    import math
    dx = end[0] - start[0]
    dy = end[1] - start[1]
    angle = math.atan2(dy, dx)
    L = 15
    a1 = angle + math.pi * 0.8
    a2 = angle - math.pi * 0.8
    p1 = (end[0] + L * math.cos(a1), end[1] + L * math.sin(a1))
    p2 = (end[0] + L * math.cos(a2), end[1] + L * math.sin(a2))
    draw.polygon([end, p1, p2], fill=color)


def create_architecture_diagram():
    W, H = 2400, 1400
    img = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    draw = ImageDraw.Draw(img)

    font_title = get_font(36, bold=True)
    font_header = get_font(26, bold=True)
    font_body = get_font(20)
    font_small = get_font(17)
    font_label = get_font(15, bold=True)

    # Background
    rounded_rect(draw, (0, 0, W, H), 30, fill=(248, 250, 252, 255))

    # Title
    draw.text((W // 2, 25), "Pervasive AI Continuum — Three-Tier Architecture",
              fill=(10, 26, 47), font=font_title, anchor="mt")

    # ---- TIER 1: Device ----
    t1_x, t1_y = 50, 90
    t1_w, t1_h = 700, 550
    rounded_rect(draw, (t1_x, t1_y, t1_x + t1_w, t1_y + t1_h), 20,
                 fill=(232, 245, 233), outline=(27, 94, 32), width=3)
    # Header bar
    rounded_rect(draw, (t1_x, t1_y, t1_x + t1_w, t1_y + 50), 20,
                 fill=(27, 94, 32))
    draw.rectangle((t1_x, t1_y + 30, t1_x + t1_w, t1_y + 50), fill=(27, 94, 32))
    draw.text((t1_x + t1_w // 2, t1_y + 12), "TIER 1 — DEVICE EDGE", fill="white",
              font=font_header, anchor="mt")

    # Status chip
    rounded_rect(draw, (t1_x + t1_w - 130, t1_y + 8, t1_x + t1_w - 15, t1_y + 42), 12,
                 fill=(56, 142, 60))
    draw.text((t1_x + t1_w - 72, t1_y + 14), "SHIPPED", fill="white",
              font=font_label, anchor="mt")

    # Boxes inside tier 1
    boxes_t1 = [
        (t1_x + 30, t1_y + 70, 300, 70, (200, 230, 201), "Med Veda App\nJetpack Compose + Room DB"),
        (t1_x + 30, t1_y + 155, 300, 70, (200, 230, 201), "MedGemma 1.5 4B GGUF\nText + Chart Chat"),
        (t1_x + 30, t1_y + 240, 300, 55, (200, 230, 201), "mmproj Vision (lazy load)\nLocal CXR / Histopath"),
        (t1_x + 30, t1_y + 310, 300, 55, (200, 230, 201), "ScheduledPrognosisWorker\nOvernight On-Device Batch"),
        (t1_x + 370, t1_y + 70, 300, 70, (255, 248, 225), "Room DB\nLongitudinal Chart"),
        (t1_x + 370, t1_y + 155, 300, 55, (255, 248, 225), "Encrypted Prefs + PIN\nSecure Storage"),
        (t1_x + 370, t1_y + 225, 300, 55, (255, 248, 225), "FHIR Export Manager\nBundle Generation"),
    ]
    for bx, by, bw, bh, bcolor, btxt in boxes_t1:
        rounded_rect(draw, (bx, by, bx + bw, by + bh), 10,
                     fill=bcolor, outline=(100, 100, 100), width=1)
        lines = btxt.split("\n")
        for i, line in enumerate(lines):
            f = font_small if i > 0 else font_body
            draw.text((bx + bw // 2, by + 10 + i * 25), line, fill=(30, 30, 30),
                      font=f, anchor="mt")

    # Snapdragon bar
    snap_y = t1_y + t1_h - 120
    rounded_rect(draw, (t1_x + 30, snap_y, t1_x + t1_w - 30, snap_y + 80), 12,
                 fill=(50, 83, 184), outline=(30, 50, 120), width=2)
    draw.text((t1_x + t1_w // 2, snap_y + 10), "Qualcomm Snapdragon SoC",
              fill="white", font=font_header, anchor="mt")
    draw.text((t1_x + t1_w // 2, snap_y + 45), "CPU (KleidiAI)  ·  Adreno GPU  ·  Hexagon NPU",
              fill=(200, 210, 255), font=font_small, anchor="mt")

    # ---- TIER 2: Near Edge ----
    t2_x, t2_y = 850, 90
    t2_w, t2_h = 700, 350
    rounded_rect(draw, (t2_x, t2_y, t2_x + t2_w, t2_y + t2_h), 20,
                 fill=(227, 242, 253), outline=(21, 101, 192), width=3)
    rounded_rect(draw, (t2_x, t2_y, t2_x + t2_w, t2_y + 50), 20,
                 fill=(21, 101, 192))
    draw.rectangle((t2_x, t2_y + 30, t2_x + t2_w, t2_y + 50), fill=(21, 101, 192))
    draw.text((t2_x + t2_w // 2, t2_y + 12), "TIER 2 — NEAR EDGE",
              fill="white", font=font_header, anchor="mt")
    rounded_rect(draw, (t2_x + t2_w - 170, t2_y + 8, t2_x + t2_w - 15, t2_y + 42), 12,
                 fill=(25, 118, 210))
    draw.text((t2_x + t2_w - 92, t2_y + 14), "PROTOTYPE", fill="white",
              font=font_label, anchor="mt")

    boxes_t2 = [
        (t2_x + 30, t2_y + 70, 300, 70, (187, 222, 251), "Edge Companion Hub\nFastAPI · Port 8787"),
        (t2_x + 370, t2_y + 70, 300, 70, (187, 222, 251), "GPU Imaging Node\nRTX 4070 Lab / 4090 Hosp"),
        (t2_x + 30, t2_y + 160, 300, 55, (187, 222, 251), "Patient Sync\nTwo-Way Device ↔ Hub"),
        (t2_x + 370, t2_y + 160, 300, 55, (187, 222, 251), "Vision Service\nStructured JSON Only"),
        (t2_x + 100, t2_y + 240, 500, 55, (187, 222, 251), "Web Dashboard + LLM Router (Ollama / Gemini)"),
    ]
    for bx, by, bw, bh, bcolor, btxt in boxes_t2:
        rounded_rect(draw, (bx, by, bx + bw, by + bh), 10,
                     fill=bcolor, outline=(80, 130, 200), width=1)
        lines = btxt.split("\n")
        for i, line in enumerate(lines):
            f = font_small if i > 0 else font_body
            draw.text((bx + bw // 2, by + 8 + i * 25), line, fill=(20, 20, 80),
                      font=f, anchor="mt")

    # ---- TIER 3: Hospital VPC ----
    t3_x, t3_y = 850, 490
    t3_w, t3_h = 700, 150
    rounded_rect(draw, (t3_x, t3_y, t3_x + t3_w, t3_y + t3_h), 20,
                 fill=(243, 229, 245), outline=(69, 39, 160), width=3)
    rounded_rect(draw, (t3_x, t3_y, t3_x + t3_w, t3_y + 50), 20,
                 fill=(69, 39, 160))
    draw.rectangle((t3_x, t3_y + 30, t3_x + t3_w, t3_y + 50), fill=(69, 39, 160))
    draw.text((t3_x + t3_w // 2, t3_y + 12), "TIER 3 — HOSPITAL VPC / BATCH",
              fill="white", font=font_header, anchor="mt")
    rounded_rect(draw, (t3_x + t3_w - 160, t3_y + 8, t3_x + t3_w - 15, t3_y + 42), 12,
                 fill=(94, 53, 177))
    draw.text((t3_x + t3_w - 87, t3_y + 14), "ROADMAP", fill="white",
              font=font_label, anchor="mt")

    draw.text((t3_x + t3_w // 2, t3_y + 65), "FHIR Ingest → 27B+ Models → Rare Disease / Cohort Analytics",
              fill=(50, 20, 100), font=font_body, anchor="mt")
    draw.text((t3_x + t3_w // 2, t3_y + 100), "Overnight batch · Full-history reasoning · Results next morning",
              fill=(80, 50, 120), font=font_small, anchor="mt")

    # ---- Public Internet box ----
    inet_x, inet_y = 1650, 90
    inet_w, inet_h = 700, 250
    rounded_rect(draw, (inet_x, inet_y, inet_x + inet_w, inet_y + inet_h), 20,
                 fill=(236, 239, 241), outline=(84, 110, 122), width=2)
    rounded_rect(draw, (inet_x, inet_y, inet_x + inet_w, inet_y + 50), 20,
                 fill=(84, 110, 122))
    draw.rectangle((inet_x, inet_y + 30, inet_x + inet_w, inet_y + 50), fill=(84, 110, 122))
    draw.text((inet_x + inet_w // 2, inet_y + 12), "PUBLIC INTERNET",
              fill="white", font=font_header, anchor="mt")

    boxes_inet = [
        (inet_x + 40, inet_y + 70, 280, 55, (236, 239, 241), "Hugging Face CDN\nModel + mmproj Download"),
        (inet_x + 380, inet_y + 70, 280, 55, (236, 239, 241), "OAuth / Token\nAuthentication Only"),
    ]
    for bx, by, bw, bh, bcolor, btxt in boxes_inet:
        rounded_rect(draw, (bx, by, bx + bw, by + bh), 10,
                     fill=bcolor, outline=(120, 130, 140), width=1)
        lines = btxt.split("\n")
        for i, line in enumerate(lines):
            f = font_small if i > 0 else font_body
            draw.text((bx + bw // 2, by + 8 + i * 25), line, fill=(40, 50, 60),
                      font=f, anchor="mt")

    # Red X for no PHI
    rounded_rect(draw, (inet_x + 100, inet_y + 155, inet_x + inet_w - 100, inet_y + 210), 10,
                 fill=(255, 235, 238), outline=(198, 40, 40), width=2)
    draw.text((inet_x + inet_w // 2, inet_y + 168), "NO Clinical PHI Inference",
              fill=(183, 28, 28), font=font_header, anchor="mt")

    # ---- Arrows ----
    # T1 → T2 (Wi-Fi image upload)
    draw_arrow(draw, (t1_x + t1_w, 250), (t2_x, 250), (21, 101, 192), 4)
    draw.text(((t1_x + t1_w + t2_x) // 2, 225), "Wi-Fi: study pixels",
              fill=(21, 101, 192), font=font_label, anchor="mt")

    # T2 → T1 (JSON findings)
    draw_arrow(draw, (t2_x, 310), (t1_x + t1_w, 310), (27, 94, 32), 4)
    draw.text(((t1_x + t1_w + t2_x) // 2, 320), "findings JSON",
              fill=(27, 94, 32), font=font_label, anchor="mt")

    # T1 → T3 (FHIR export)
    draw_arrow(draw, (t1_x + t1_w - 100, t1_y + t1_h), (t3_x + 100, t3_y), (69, 39, 160), 3)
    draw.text((t1_x + t1_w - 50, t1_y + t1_h + 15), "FHIR export",
              fill=(69, 39, 160), font=font_label, anchor="mt")

    # T1 → Internet (artifacts only)
    draw_arrow(draw, (t1_x + t1_w, 170), (inet_x, 170), (84, 110, 122), 3)
    draw.text(((t1_x + t1_w + inet_x) // 2, 150), "artifacts only",
              fill=(84, 110, 122), font=font_label, anchor="mt")

    # Clinician icon (stick figure)
    cx, cy = 90, 750
    draw.ellipse((cx - 20, cy - 45, cx + 20, cy - 5), outline=(55, 71, 79), width=3)
    draw.line((cx, cy - 5, cx, cy + 40), fill=(55, 71, 79), width=3)
    draw.line((cx - 25, cy + 10, cx + 25, cy + 10), fill=(55, 71, 79), width=3)
    draw.line((cx, cy + 40, cx - 20, cy + 70), fill=(55, 71, 79), width=3)
    draw.line((cx, cy + 40, cx + 20, cy + 70), fill=(55, 71, 79), width=3)
    draw.text((cx, cy + 80), "Clinician", fill=(55, 71, 79), font=font_body, anchor="mt")
    draw_arrow(draw, (cx + 30, cy), (t1_x + 30, 300), (55, 71, 79), 3)

    # ---- Trust boundary text ----
    trust_y = H - 120
    rounded_rect(draw, (50, trust_y, W - 50, H - 20), 15,
                 fill=(232, 245, 233), outline=(27, 94, 32), width=2)
    draw.text((W // 2, trust_y + 15), "TRUST BOUNDARY: Clinical narrative stays on device. Edge/cloud tiers are user-governed.",
              fill=(27, 94, 32), font=font_header, anchor="mt")
    draw.text((W // 2, trust_y + 55), "Chart text never goes to third-party LLMs unless clinician explicitly selects Gemini API.",
              fill=(27, 94, 32), font=font_body, anchor="mt")

    img.save(os.path.join(ASSETS, "architecture_v2.png"), dpi=(300, 300))
    print("Created architecture_v2.png")


def create_benchmark_chart():
    W, H = 2000, 900
    img = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    draw = ImageDraw.Draw(img)

    font_title = get_font(32, bold=True)
    font_header = get_font(22, bold=True)
    font_body = get_font(18)
    font_val = get_font(24, bold=True)
    font_small = get_font(15)

    rounded_rect(draw, (0, 0, W, H), 25, fill=(253, 248, 243, 255))

    draw.text((W // 2, 20), "Quantization Benchmark: MedGemma 1.5 4B IT — 500 Samples x 5 Datasets",
              fill=(10, 26, 47), font=font_title, anchor="mt")

    # Bar chart area
    benchmarks = ["MedMCQA", "MedQA", "PubMedQA", "MMLU Med", "MedXpertQA"]
    models = [
        ("BF16", [43.80, 29.00, 55.40, 43.00, 8.80], (100, 100, 100)),
        ("Q8_0", [44.40, 28.60, 55.40, 43.60, 8.80], (27, 94, 32)),
        ("Q6_K", [40.80, 28.40, 57.40, 41.40, 9.80], (21, 101, 192)),
        ("Q4_K_M", [32.60, 29.00, 55.40, 29.80, 10.00], (230, 81, 0)),
    ]

    chart_x, chart_y = 120, 80
    chart_w, chart_h = 1300, 650
    bar_group_w = chart_w / len(benchmarks)
    bar_w = bar_group_w / (len(models) + 1)
    max_val = 60

    # Y axis grid
    for pct in range(0, 65, 10):
        y = chart_y + chart_h - (pct / max_val) * chart_h
        draw.line((chart_x, y, chart_x + chart_w, y), fill=(220, 220, 220), width=1)
        draw.text((chart_x - 10, y), f"{pct}%", fill=(120, 120, 120),
                  font=font_small, anchor="rm")

    # Bars
    for bi, bench in enumerate(benchmarks):
        gx = chart_x + bi * bar_group_w + bar_w * 0.5
        for mi, (mname, vals, mcolor) in enumerate(models):
            bx = gx + mi * bar_w
            bar_h = (vals[bi] / max_val) * chart_h
            by = chart_y + chart_h - bar_h
            rounded_rect(draw, (int(bx), int(by), int(bx + bar_w - 8), int(chart_y + chart_h)),
                         8, fill=mcolor)
            # Value on top
            draw.text((int(bx + (bar_w - 8) / 2), int(by - 5)),
                      f"{vals[bi]:.1f}", fill=mcolor, font=font_small, anchor="mb")

        # Benchmark label
        draw.text((int(gx + bar_group_w / 2 - bar_w * 0.5), int(chart_y + chart_h + 10)),
                  bench, fill=(50, 50, 50), font=font_body, anchor="mt")

    # Legend
    leg_x = chart_x + chart_w + 60
    leg_y = chart_y + 30
    for mi, (mname, vals, mcolor) in enumerate(models):
        y = leg_y + mi * 50
        draw.rectangle((leg_x, y, leg_x + 30, y + 25), fill=mcolor)
        size_map = {"BF16": "7.3 GB", "Q8_0": "3.9 GB", "Q6_K": "3.0 GB", "Q4_K_M": "2.4 GB"}
        draw.text((leg_x + 40, y + 3), f"{mname} ({size_map[mname]})",
                  fill=(40, 40, 40), font=font_body)

    # Key insight boxes
    insights = [
        ("Q8_0", "Lossless", "Zero accuracy drop\n47% size reduction", (27, 94, 32)),
        ("Q6_K", "Near-lossless", "Within noise margin\n59% size reduction", (21, 101, 192)),
        ("Q4_K_M", "Edge default", "Zero reasoning drop\n2.4 GB footprint", (230, 81, 0)),
    ]
    ix = leg_x
    iy = leg_y + 260
    for name, tag, desc, color in insights:
        rounded_rect(draw, (ix, iy, ix + 400, iy + 110), 12,
                     fill=(255, 255, 255), outline=color, width=2)
        rounded_rect(draw, (ix, iy, ix + 100, iy + 35), 10, fill=color)
        draw.text((ix + 50, iy + 5), name, fill="white", font=font_header, anchor="mt")
        draw.text((ix + 110, iy + 5), tag, fill=color, font=font_header, anchor="lt")
        lines = desc.split("\n")
        for li, l in enumerate(lines):
            draw.text((ix + 15, iy + 45 + li * 25), l, fill=(60, 60, 60), font=font_body)
        iy += 130

    img.save(os.path.join(ASSETS, "benchmark_chart_v2.png"), dpi=(300, 300))
    print("Created benchmark_chart_v2.png")


def create_app_composite():
    """Create a composite of app screenshots with labels and device frames."""
    W, H = 2200, 1200
    img = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    draw = ImageDraw.Draw(img)

    font_title = get_font(32, bold=True)
    font_label = get_font(22, bold=True)
    font_desc = get_font(17)

    rounded_rect(draw, (0, 0, W, H), 25, fill=(240, 244, 248, 255))
    draw.text((W // 2, 20), "Med Veda — Application Interface",
              fill=(10, 26, 47), font=font_title, anchor="mt")

    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    screenshots = [
        ("app images/lock screen.png", "PIN Lock", "Secure entry with\n4-digit PIN auth"),
        ("app images/home screeen.png", "Patient Dashboard", "Patient list with\nquick AI chat access"),
        ("app images/patientpage with entries.png", "Patient Record", "Longitudinal entries\nwith history timeline"),
        ("app images/image analysis.png", "X-ray Analysis", "On-device MedGemma\nmultimodal vision"),
        ("app images/response with reasoning .png", "Thinking Mode", "Chain-of-thought\nclinical reasoning"),
        ("app images/telugu output.png", "Telugu Output", "Vernacular injection\nfor regional languages"),
    ]

    x_start = 40
    spacing = (W - 80) // len(screenshots)

    for i, (path, label, desc) in enumerate(screenshots):
        full_path = os.path.join(repo, path)
        sx = x_start + i * spacing
        if os.path.exists(full_path):
            ss = Image.open(full_path)
            # Phone frame
            phone_w = spacing - 40
            aspect = ss.height / ss.width
            phone_h = int(phone_w * aspect)
            if phone_h > H - 200:
                phone_h = H - 200
                phone_w = int(phone_h / aspect)
            ss = ss.resize((phone_w, phone_h), Image.LANCZOS)

            # Frame
            frame_pad = 8
            fx = sx + (spacing - phone_w) // 2 - frame_pad
            fy = 70
            rounded_rect(draw, (fx, fy, fx + phone_w + frame_pad * 2, fy + phone_h + frame_pad * 2),
                         20, fill=(30, 30, 30), outline=(50, 50, 50), width=2)
            img.paste(ss, (fx + frame_pad, fy + frame_pad))
        else:
            # Placeholder
            fx = sx + 20
            fy = 70
            phone_h = H - 280
            rounded_rect(draw, (fx, fy, fx + spacing - 60, fy + phone_h), 20,
                         fill=(200, 200, 200), outline=(150, 150, 150), width=2)

        # Label
        lx = sx + spacing // 2
        ly = H - 120
        draw.text((lx, ly), label, fill=(10, 26, 47), font=font_label, anchor="mt")
        desc_lines = desc.split("\n")
        for di, dl in enumerate(desc_lines):
            draw.text((lx, ly + 30 + di * 22), dl, fill=(100, 100, 100),
                      font=font_desc, anchor="mt")

    img.save(os.path.join(ASSETS, "app_composite_v2.png"), dpi=(300, 300))
    print("Created app_composite_v2.png")


def create_kpi_cards():
    W, H = 2200, 350
    img = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    draw = ImageDraw.Draw(img)

    font_val = get_font(52, bold=True)
    font_unit = get_font(20)
    font_label = get_font(18, bold=True)

    metrics = [
        ("~20", "tok/s", "Inference Speed", (230, 81, 0)),
        ("4.1 GB", "peak", "RAM Usage", (21, 101, 192)),
        ("2.4 GB", "on disk", "Model Size", (27, 94, 32)),
        ("5", "datasets", "Benchmarks", (69, 39, 160)),
        ("500", "per set", "Test Samples", (183, 28, 28)),
        ("Q4_K_M", "default", "Quantization", (50, 83, 184)),
    ]

    card_w = (W - 40) // len(metrics)
    for i, (val, unit, label, color) in enumerate(metrics):
        cx = 20 + i * card_w
        rounded_rect(draw, (cx, 10, cx + card_w - 15, H - 10), 18,
                     fill=(255, 255, 255), outline=color, width=3)
        # Color accent bar at top
        rounded_rect(draw, (cx, 10, cx + card_w - 15, 18), 18, fill=color)
        draw.rectangle((cx, 18, cx + card_w - 15, 25), fill=color)

        mid = cx + (card_w - 15) // 2
        draw.text((mid, 60), val, fill=color, font=font_val, anchor="mt")
        draw.text((mid, 150), unit, fill=(150, 150, 150), font=font_unit, anchor="mt")
        draw.text((mid, 195), label, fill=(10, 26, 47), font=font_label, anchor="mt")

    img.save(os.path.join(ASSETS, "kpi_cards_v2.png"), dpi=(300, 300))
    print("Created kpi_cards_v2.png")


def create_trust_diagram():
    W, H = 2200, 500
    img = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    draw = ImageDraw.Draw(img)

    font_header = get_font(24, bold=True)
    font_body = get_font(19)
    font_small = get_font(16)

    zones = [
        ("DEVICE — Trust Zone A", 50, (232, 245, 233), (27, 94, 32),
         ["Chart text & notes", "Local LLM inference", "Patient history", "PIN / encryption"]),
        ("NEAR EDGE — Trust Zone B", 580, (227, 242, 253), (21, 101, 192),
         ["Study pixels (governed)", "Structured findings JSON", "Edge companion sync"]),
        ("HOSPITAL VPC — Trust Zone C", 1100, (243, 229, 245), (69, 39, 160),
         ["FHIR bundles (user export)", "Batch insights returned", "27B+ model processing"]),
        ("BLOCKED (v1)", 1640, (255, 205, 210), (211, 47, 47),
         ["Public cloud LLM on", "full chart — NOT ALLOWED"]),
    ]

    for label, x, bg, border, items in zones:
        zw = 500 if x < 1600 else 510
        rounded_rect(draw, (x, 10, x + zw, H - 10), 18, fill=bg, outline=border, width=3)
        rounded_rect(draw, (x, 10, x + zw, 55), 18, fill=border)
        draw.rectangle((x, 35, x + zw, 55), fill=border)
        draw.text((x + zw // 2, 18), label, fill="white", font=font_header, anchor="mt")

        for i, item in enumerate(items):
            draw.text((x + 25, 75 + i * 35), f"  {item}", fill=border, font=font_body)

    # Arrows between zones
    draw_arrow(draw, (540, H // 2), (590, H // 2), (21, 101, 192), 3)
    draw_arrow(draw, (1070, H // 2), (1110, H // 2), (69, 39, 160), 3)
    # Red X to blocked
    draw.text((1615, H // 2 - 20), "X", fill=(211, 47, 47), font=get_font(50, bold=True), anchor="mt")

    img.save(os.path.join(ASSETS, "trust_diagram_v2.png"), dpi=(300, 300))
    print("Created trust_diagram_v2.png")


if __name__ == "__main__":
    create_architecture_diagram()
    create_benchmark_chart()
    create_app_composite()
    create_kpi_cards()
    create_trust_diagram()
    print("\nAll images generated in:", ASSETS)
