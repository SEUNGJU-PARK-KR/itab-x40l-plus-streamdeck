from PIL import Image, ImageDraw, ImageFont
from datetime import datetime
import math
import os

OUT = r"D:\project\새 폴더\itab-firmware\server-status-diagram.png"
W, H = 1600, 1000

FONT_PATH = r"C:\Windows\Fonts\malgun.ttf"
FONT_BOLD_PATH = r"C:\Windows\Fonts\malgunbd.ttf"

def font(size, bold=False):
    path = FONT_BOLD_PATH if bold and os.path.exists(FONT_BOLD_PATH) else FONT_PATH
    return ImageFont.truetype(path, size)

F_TITLE = font(38, True)
F_SUB = font(18)
F_H1 = font(24, True)
F_H2 = font(19, True)
F_BODY = font(17)
F_SMALL = font(14)
F_TINY = font(12)
F_BIG = font(30, True)

BG_TOP = (5, 13, 27)
BG_BOTTOM = (8, 22, 42)
CARD = (15, 28, 47)
CARD2 = (19, 35, 58)
STROKE = (53, 75, 106)
TEXT = (239, 246, 255)
MUTED = (153, 169, 199)
DIM = (105, 122, 153)
CYAN = (62, 214, 232)
AMBER = (255, 169, 61)
GREEN = (74, 222, 128)
RED = (255, 107, 107)
GRAY = (154, 154, 162)
PURPLE = (141, 119, 255)

img = Image.new("RGB", (W, H), BG_TOP)
d = ImageDraw.Draw(img)

# vertical gradient background
for y in range(H):
    t = y / max(1, H - 1)
    col = tuple(int(BG_TOP[i] * (1 - t) + BG_BOTTOM[i] * t) for i in range(3))
    d.line([(0, y), (W, y)], fill=col)

# soft glow accents
for cx, cy, r, col in [
    (1270, 170, 300, (17, 95, 130)),
    (250, 720, 260, (21, 74, 116)),
    (850, 280, 260, (44, 48, 110)),
]:
    for rr in range(r, 0, -10):
        alpha = int(5 * (rr / r))
        overlay = tuple(min(255, c + alpha) for c in BG_BOTTOM)
        d.ellipse((cx-rr, cy-rr, cx+rr, cy+rr), outline=overlay)


def rounded(xy, fill=CARD, outline=STROKE, width=1, radius=22):
    d.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=width)


def text(x, y, s, f=F_BODY, fill=TEXT):
    d.text((x, y), s, font=f, fill=fill)


def center_text(x0, y0, x1, y1, s, f=F_BODY, fill=TEXT):
    box = d.textbbox((0, 0), s, font=f)
    tw, th = box[2] - box[0], box[3] - box[1]
    d.text((x0 + (x1-x0-tw)/2, y0 + (y1-y0-th)/2 - 1), s, font=f, fill=fill)


def arrow(x0, y0, x1, y1, color=CYAN, width=3, dashed=False):
    if dashed:
        # draw small segments along the vector
        dx, dy = x1-x0, y1-y0
        dist = math.hypot(dx, dy)
        if dist == 0:
            return
        ux, uy = dx/dist, dy/dist
        pos, dash, gap = 0, 14, 10
        while pos < dist:
            end = min(dist, pos+dash)
            d.line((x0+ux*pos, y0+uy*pos, x0+ux*end, y0+uy*end), fill=color, width=width)
            pos += dash + gap
    else:
        d.line((x0, y0, x1, y1), fill=color, width=width)
    ang = math.atan2(y1-y0, x1-x0)
    size = 10
    p1 = (x1, y1)
    p2 = (x1-size*math.cos(ang-math.pi/6), y1-size*math.sin(ang-math.pi/6))
    p3 = (x1-size*math.cos(ang+math.pi/6), y1-size*math.sin(ang+math.pi/6))
    d.polygon([p1, p2, p3], fill=color)


def bar(x, y, w, h, pct, color, label, value):
    text(x, y-24, label, F_SMALL, MUTED)
    rounded((x, y, x+w, y+h), fill=(9, 19, 34), outline=(42, 61, 88), width=1, radius=10)
    fillw = int((w-4) * max(0, min(100, pct)) / 100)
    if fillw > 0:
        d.rounded_rectangle((x+2, y+2, x+2+fillw, y+h-2), radius=8, fill=color)
    text(x+w+14, y-2, value, F_SMALL, TEXT)

# Header
text(58, 34, "ITAB Linux Server 현황", F_TITLE, TEXT)
text(60, 84, "ITAB_X40L_Plus · Android 11 · Alpine Linux userland · snapshot 2026-11-26 15:13", F_SUB, MUTED)
rounded((1338, 38, 1540, 86), fill=(12, 46, 40), outline=GREEN, width=2, radius=18)
center_text(1338, 38, 1540, 86, "ALL SERVICES UP", F_SMALL, GREEN)

# Left card
rounded((55, 130, 455, 655))
text(82, 154, "DEVICE / NETWORK", F_H1, TEXT)
text(82, 196, "Model", F_SMALL, DIM)
text(82, 218, "ITAB_X40L_Plus", F_H2, TEXT)
text(82, 252, "Android 11 · uptime 2h 40m", F_BODY, MUTED)
text(82, 282, "Battery 71% · 29.5°C", F_BODY, MUTED)

text(82, 330, "Wi-Fi / LAN IP", F_SMALL, DIM)
text(82, 354, "172.30.6.238", F_BIG, CYAN)
text(82, 398, "서비스는 0.0.0.0에 바인딩되어 있어", F_SMALL, MUTED)
text(82, 421, "Wi-Fi 변경 후 새 IP로 바로 접속 가능", F_SMALL, MUTED)

rounded((82, 466, 428, 625), fill=(9, 19, 34), outline=(42, 61, 88), radius=16)
text(100, 484, "LAN 접속", F_H2, TEXT)
text(100, 520, "SSH  ssh -p 8022 u0_a176@172.30.6.238", F_TINY, MUTED)
text(100, 548, "Dashboard  http://172.30.6.238:8080", F_TINY, MUTED)
text(100, 576, "Linux HTTP  http://172.30.6.238:8081", F_TINY, MUTED)

# Center stack card
rounded((500, 130, 1120, 655))
text(528, 154, "SERVER STACK", F_H1, TEXT)

# Android layer
rounded((530, 195, 1090, 292), fill=(18, 35, 59), outline=(69, 95, 130), width=2, radius=18)
text(555, 214, "Android 11 + ITAB Deck App", F_H2, TEXT)
text(555, 247, "Dashboard WebView :8080   ·   AgentDeck client   ·   ttyd WebView", F_SMALL, MUTED)
rounded((905, 214, 1066, 254), fill=(10, 54, 61), outline=CYAN, width=1, radius=14)
center_text(905, 214, 1066, 254, "control plane", F_TINY, CYAN)

arrow(810, 292, 810, 332, CYAN, 3)

# Termux layer
rounded((530, 334, 1090, 452), fill=(14, 42, 55), outline=(39, 145, 157), width=2, radius=18)
text(555, 352, "Termux userland", F_H2, TEXT)
text(555, 386, "sshd :8022      ttyd :7681(localhost)      itab-watchdog", F_BODY, TEXT)
text(555, 418, "boot script: ~/.termux/boot/start-itab.sh", F_SMALL, MUTED)
rounded((935, 350, 1065, 392), fill=(9, 56, 43), outline=GREEN, width=1, radius=14)
center_text(935, 350, 1065, 392, "watchdog ON", F_TINY, GREEN)

arrow(810, 452, 810, 492, CYAN, 3)

# Alpine layer
rounded((530, 494, 1090, 625), fill=(31, 33, 70), outline=(98, 87, 181), width=2, radius=18)
text(555, 512, "Alpine Linux proot container", F_H2, TEXT)
text(555, 546, "python3 -m http.server :8081", F_BODY, TEXT)
text(555, 578, "proot-distro 63M · Python 3.14.10 · musl/BusyBox userland", F_SMALL, MUTED)
rounded((936, 512, 1065, 552), fill=(42, 32, 83), outline=PURPLE, width=1, radius=14)
center_text(936, 512, 1065, 552, "proot", F_TINY, PURPLE)

# Arrows between cards
arrow(455, 390, 500, 390, CYAN, 4)
arrow(1120, 390, 1160, 390, CYAN, 4)

# Right card
rounded((1165, 130, 1545, 655))
text(1192, 154, "SERVICE STATUS", F_H1, TEXT)

services = [
    ("Dashboard", "8080", "running", GREEN),
    ("SSH", "8022", "running", GREEN),
    ("Linux HTTP", "8081", "running", GREEN),
    ("Web terminal", "7681", "localhost only", CYAN),
    ("AgentDeck bridge", "9120", "미설정/외부 PC", AMBER),
]
y = 205
for name, port, state, col in services:
    rounded((1192, y, 1518, y+67), fill=(9, 19, 34), outline=(42, 61, 88), radius=15)
    d.ellipse((1212, y+24, 1230, y+42), fill=col)
    text(1244, y+13, name, F_BODY, TEXT)
    text(1244, y+39, f":{port} · {state}", F_SMALL, MUTED)
    y += 82

rounded((1192, 600, 1518, 630), fill=(44, 31, 20), outline=AMBER, radius=10)
center_text(1192, 600, 1518, 630, "ttyd는 보안상 localhost 전용", F_TINY, AMBER)

# Bottom resource card
rounded((55, 700, 1545, 955))
text(82, 724, "RESOURCE USAGE", F_H1, TEXT)
text(410, 729, "CPU는 1초 샘플 · loadavg 5.16", F_SMALL, DIM)

bar(90, 785, 320, 22, 3.5, GREEN, "CPU", "약 3.5%")
bar(90, 840, 320, 22, 42.0, CYAN, "Memory", "1.58 / 3.77 GiB · 42%")
bar(90, 895, 320, 22, 7.1, PURPLE, "Storage", "1.71 / 24.17 GiB · 7%")

# Footprint / runtime details
rounded((920, 762, 1518, 930), fill=(9, 19, 34), outline=(42, 61, 88), radius=18)
text(945, 783, "설치 footprint", F_H2, TEXT)
text(945, 820, "Termux usr", F_BODY, MUTED)
text(1125, 820, "249 MB", F_BODY, TEXT)
text(945, 852, "Alpine/proot", F_BODY, MUTED)
text(1125, 852, "63 MB", F_BODY, TEXT)
text(945, 884, "Termux home", F_BODY, MUTED)
text(1125, 884, "50 KB", F_BODY, TEXT)

text(1288, 783, "실행 프로세스", F_H2, TEXT)
text(1288, 820, "sshd", F_BODY, GREEN)
text(1288, 852, "ttyd", F_BODY, GREEN)
text(1288, 884, "python http.server", F_BODY, GREEN)
text(1428, 852, "watchdog", F_BODY, GREEN)

# Footer
text(60, 968, "Generated from live tablet measurements · Wi-Fi IP는 변경되면 대시보드가 자동 갱신", F_TINY, DIM)

img.save(OUT, "PNG", optimize=True)
print(OUT)
