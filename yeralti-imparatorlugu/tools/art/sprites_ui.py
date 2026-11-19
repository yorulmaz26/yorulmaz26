"""Arayüz: beyaz (boyanabilir) ikonlar, renkli hava ikonları, 9 parçalı panel ve düğmeler."""
import math
from artlib import *

W = "#ffffff"


def ic(name, body):
    add(f"ic_{name}", 64, 64, body)


def build():
    ic("dig", f'<g transform="rotate(40 32 32)"><rect x="29" y="4" width="6" height="34" rx="3" fill="{W}"/><rect x="22" y="4" width="20" height="6" rx="3" fill="{W}"/><path d="M20,38 H44 V46 C44,56 38,62 32,62 C26,62 20,56 20,46 Z" fill="{W}"/></g>')
    ic("build", f'<g transform="rotate(-35 32 32)"><rect x="28" y="22" width="8" height="40" rx="4" fill="{W}"/><path d="M12,8 H46 C52,8 56,12 56,18 V22 H12 Z" fill="{W}"/><rect x="40" y="14" width="18" height="12" rx="3" fill="{W}"/></g>')
    ic("mark", f'<path d="M32,6 C40,18 46,26 46,34 C46,42 40,48 32,48 C24,48 18,42 18,34 C18,26 24,18 32,6 Z" fill="{W}"/><path d="M8,50 Q20,44 32,52 T56,50" stroke="{W}" stroke-width="4" fill="none" stroke-linecap="round"/><path d="M14,60 Q24,55 32,60 T50,60" stroke="{W}" stroke-width="3" fill="none" stroke-linecap="round" opacity="0.8"/>')
    ic("colony", f'<ellipse cx="20" cy="38" rx="12" ry="10" fill="{W}"/><ellipse cx="36" cy="36" rx="7" ry="6" fill="{W}"/><circle cx="48" cy="32" r="8" fill="{W}"/><path d="M50,24 L56,12 M54,28 L62,22" stroke="{W}" stroke-width="3" stroke-linecap="round"/><path d="M32,40 L26,54 M38,40 L40,56 M30,32 L22,20 M38,31 L40,18" stroke="{W}" stroke-width="3" stroke-linecap="round"/>')
    ic("more", "".join(f'<rect x="{8 + (i % 2) * 26}" y="{8 + (i // 2) * 26}" width="22" height="22" rx="6" fill="{W}"/>' for i in range(4)))
    ic("army", f'<path d="M32,4 L54,12 V30 C54,46 44,56 32,62 C20,56 10,46 10,30 V12 Z" fill="{W}"/><path d="M32,16 V48 M22,30 H42" stroke="#000" stroke-opacity="0.25" stroke-width="5" stroke-linecap="round"/>')
    ic("research", f'<path d="M24,4 H40 V8 H37 V24 L54,52 C57,58 53,62 47,62 H17 C11,62 7,58 10,52 L27,24 V8 H24 Z" fill="{W}"/><path d="M18,46 H46" stroke="#000" stroke-opacity="0.25" stroke-width="3"/>')
    ic("close", f'<path d="M14,14 L50,50 M50,14 L14,50" stroke="{W}" stroke-width="9" stroke-linecap="round"/>')
    ic("pause", f'<rect x="14" y="10" width="13" height="44" rx="4" fill="{W}"/><rect x="37" y="10" width="13" height="44" rx="4" fill="{W}"/>')
    ic("play", f'<path d="M18,10 L52,32 L18,54 Z" fill="{W}" stroke="{W}" stroke-width="4" stroke-linejoin="round"/>')
    ic("fast", f'<path d="M6,12 L30,32 L6,52 Z M32,12 L56,32 L32,52 Z" fill="{W}" stroke="{W}" stroke-width="3" stroke-linejoin="round"/>')
    ic("faster", f'<path d="M2,14 L20,32 L2,50 Z M22,14 L40,32 L22,50 Z M42,14 L60,32 L42,50 Z" fill="{W}" stroke="{W}" stroke-width="3" stroke-linejoin="round"/>')
    ic("erase", f'<g transform="rotate(-40 32 32)"><rect x="12" y="20" width="40" height="24" rx="5" fill="{W}"/><rect x="12" y="20" width="14" height="24" rx="5" fill="#000" fill-opacity="0.25"/></g><rect x="10" y="54" width="44" height="5" rx="2" fill="{W}"/>')
    ic("fill", "".join(f'<rect x="{6 + (i % 2) * 13 + j * 26 - (13 if i % 2 else 0)}" y="{8 + i * 13}" width="24" height="11" rx="2" fill="{W}"/>' for i in range(4) for j in range(3) if 6 + (i % 2) * 13 + j * 26 - (13 if i % 2 else 0) < 52))
    ic("check", f'<path d="M10,34 L26,50 L54,16" stroke="{W}" stroke-width="9" fill="none" stroke-linecap="round" stroke-linejoin="round"/>')
    ic("lock", f'<rect x="12" y="28" width="40" height="30" rx="6" fill="{W}"/><path d="M20,28 V20 C20,12 26,8 32,8 C38,8 44,12 44,20 V28" stroke="{W}" stroke-width="7" fill="none"/>')
    ic("warning", f'<path d="M32,6 L60,56 H4 Z" fill="{W}" stroke="{W}" stroke-width="4" stroke-linejoin="round"/><rect x="29" y="22" width="6" height="18" rx="3" fill="#000" fill-opacity="0.45"/><circle cx="32" cy="48" r="3.5" fill="#000" fill-opacity="0.45"/>')
    ic("up", f'<path d="M32,10 L54,36 H40 V56 H24 V36 H10 Z" fill="{W}" stroke="{W}" stroke-width="3" stroke-linejoin="round"/>')
    ic("down", f'<path d="M32,54 L54,28 H40 V8 H24 V28 H10 Z" fill="{W}" stroke="{W}" stroke-width="3" stroke-linejoin="round"/>')
    ic("plus", f'<path d="M32,10 V54 M10,32 H54" stroke="{W}" stroke-width="10" stroke-linecap="round"/>')
    ic("minus", f'<path d="M10,32 H54" stroke="{W}" stroke-width="10" stroke-linecap="round"/>')
    star = " ".join(f"{32 + (26 if k % 2 == 0 else 11) * math.cos(-math.pi / 2 + k * math.pi / 5):.1f},{33 + (26 if k % 2 == 0 else 11) * math.sin(-math.pi / 2 + k * math.pi / 5):.1f}" for k in range(10))
    ic("star", f'<polygon points="{star}" fill="{W}" stroke="{W}" stroke-width="3" stroke-linejoin="round"/>')
    ic("people", f'<circle cx="22" cy="20" r="9" fill="{W}"/><path d="M6,52 C6,38 14,32 22,32 C30,32 38,38 38,52 Z" fill="{W}"/><circle cx="44" cy="22" r="8" fill="{W}" opacity="0.8"/><path d="M34,52 C36,40 40,34 46,34 C54,34 60,40 60,52 Z" fill="{W}" opacity="0.8"/>')
    gear = "".join(f'<rect x="28" y="2" width="8" height="14" rx="2" fill="{W}" transform="rotate({k * 45} 32 32)"/>' for k in range(8))
    ic("gear", gear + f'<circle cx="32" cy="32" r="19" fill="{W}"/><circle cx="32" cy="32" r="7" fill="#000" fill-opacity="0.35"/>')
    ic("map", f'<path d="M6,14 L22,8 L42,14 L58,8 V50 L42,56 L22,50 L6,56 Z" fill="{W}"/><path d="M22,8 V50 M42,14 V56" stroke="#000" stroke-opacity="0.25" stroke-width="3"/>')
    ic("trophy", f'<path d="M18,6 H46 V22 C46,32 40,38 32,38 C24,38 18,32 18,22 Z" fill="{W}"/><path d="M18,12 H8 C8,22 12,26 18,26 M46,12 H56 C56,22 52,26 46,26" stroke="{W}" stroke-width="4" fill="none"/><rect x="28" y="38" width="8" height="12" fill="{W}"/><rect x="18" y="50" width="28" height="8" rx="3" fill="{W}"/>')
    ic("dna", "".join(f'<line x1="{32 + math.sin(i / 9 * math.pi * 2) * 14:.1f}" y1="{6 + i * 5.6:.1f}" x2="{32 - math.sin(i / 9 * math.pi * 2) * 14:.1f}" y2="{6 + i * 5.6:.1f}" stroke="{W}" stroke-width="3"/>' for i in range(10))
       + f'<polyline points="{" ".join(f"{32 + math.sin(i / 9 * math.pi * 2) * 14:.1f},{6 + i * 5.6:.1f}" for i in range(10))}" stroke="{W}" stroke-width="5" fill="none" stroke-linecap="round"/>'
       + f'<polyline points="{" ".join(f"{32 - math.sin(i / 9 * math.pi * 2) * 14:.1f},{6 + i * 5.6:.1f}" for i in range(10))}" stroke="{W}" stroke-width="5" fill="none" stroke-linecap="round"/>')
    ic("bone", bone(32, 32, 1.9, -35, W).replace('stroke="#b2a699"', 'stroke="none"'))
    ic("cart", f'<path d="M4,10 H14 L22,40 H50 L58,18 H18" stroke="{W}" stroke-width="6" fill="none" stroke-linejoin="round" stroke-linecap="round"/><circle cx="26" cy="52" r="6" fill="{W}"/><circle cx="46" cy="52" r="6" fill="{W}"/>')
    ic("bag", f'<path d="M14,22 H50 L54,58 H10 Z" fill="{W}" stroke="{W}" stroke-width="4" stroke-linejoin="round"/><path d="M22,22 C22,10 28,6 32,6 C36,6 42,10 42,22" stroke="{W}" stroke-width="5" fill="none"/>')
    ic("book", f'<path d="M6,10 C16,6 26,8 31,14 V58 C26,52 16,50 6,54 Z M58,10 C48,6 38,8 33,14 V58 C38,52 48,50 58,54 Z" fill="{W}"/>')
    ic("chart", f'<rect x="8" y="36" width="10" height="20" rx="2" fill="{W}"/><rect x="24" y="24" width="10" height="32" rx="2" fill="{W}"/><rect x="40" y="10" width="10" height="46" rx="2" fill="{W}"/><rect x="4" y="56" width="56" height="4" rx="2" fill="{W}"/>')
    ic("target", f'<circle cx="32" cy="32" r="26" stroke="{W}" stroke-width="6" fill="none"/><circle cx="32" cy="32" r="14" stroke="{W}" stroke-width="6" fill="none"/><circle cx="32" cy="32" r="5" fill="{W}"/>')
    ic("handshake", f'<path d="M4,26 L16,14 L30,20 L44,12 L60,26 L46,44 C42,48 38,48 34,44 L26,50 C22,52 18,50 16,46 Z" fill="{W}"/><path d="M30,20 L22,30 C26,34 30,32 34,28 L40,34" stroke="#000" stroke-opacity="0.3" stroke-width="3" fill="none"/>')
    ic("sword", f'<g transform="rotate(45 32 32)"><path d="M28,4 H36 V42 H28 Z" fill="{W}"/><path d="M28,4 L32,0 L36,4 Z" fill="{W}"/><rect x="18" y="42" width="28" height="6" rx="3" fill="{W}"/><rect x="29" y="48" width="6" height="12" rx="2" fill="{W}"/></g>')
    ic("leaf", f'<path d="M10,54 C8,26 26,8 56,8 C56,38 40,56 10,54 Z" fill="{W}"/><path d="M12,52 C26,38 38,26 52,12" stroke="#000" stroke-opacity="0.3" stroke-width="3" fill="none"/>')
    ic("home", f'<path d="M6,30 L32,8 L58,30 H50 V56 H14 V30 Z" fill="{W}" stroke="{W}" stroke-width="3" stroke-linejoin="round"/><rect x="26" y="38" width="12" height="18" fill="#000" fill-opacity="0.3"/>')
    ic("eye", f'<path d="M4,32 C14,16 50,16 60,32 C50,48 14,48 4,32 Z" fill="{W}"/><circle cx="32" cy="32" r="10" fill="#000" fill-opacity="0.35"/>')
    ic("info", f'<circle cx="32" cy="32" r="28" fill="{W}"/><rect x="28" y="26" width="8" height="24" rx="3" fill="#000" fill-opacity="0.4"/><circle cx="32" cy="17" r="5" fill="#000" fill-opacity="0.4"/>')
    ic("egg", f'<ellipse cx="32" cy="36" rx="18" ry="24" fill="{W}"/>')
    ic("crown", f'<path d="M6,52 L8,16 L22,30 L32,10 L42,30 L56,16 L58,52 Z" fill="{W}" stroke="{W}" stroke-width="3" stroke-linejoin="round"/>')
    ic("coin", f'<circle cx="32" cy="32" r="27" fill="{W}"/><circle cx="32" cy="32" r="20" fill="none" stroke="#000" stroke-opacity="0.25" stroke-width="3"/>')
    ic("arrow", f'<path d="M10,32 H50 M34,14 L52,32 L34,50" stroke="{W}" stroke-width="8" fill="none" stroke-linecap="round" stroke-linejoin="round"/>')
    ic("food", f'<path d="M12,38 C10,22 26,12 40,16 C54,20 56,38 46,48 C36,56 14,54 12,38 Z" fill="{W}"/>')

    # Renkli ikonlar: jeton, hava durumu, uyarı
    add("coin", 64, 64, '<circle cx="32" cy="34" r="26" fill="#C98A10"/><circle cx="32" cy="30" r="26" fill="url(#ID_g)" stroke="#9A6A08" stroke-width="2.4"/><circle cx="32" cy="30" r="18" fill="none" stroke="#FFF2B0" stroke-width="2.4"/>'
        f'<polygon points="{" ".join(f"{32 + (11 if k % 2 == 0 else 5) * math.cos(-math.pi / 2 + k * math.pi / 5):.1f},{31 + (11 if k % 2 == 0 else 5) * math.sin(-math.pi / 2 + k * math.pi / 5):.1f}" for k in range(10))}" fill="#FFF2B0"/>',
        rad("ID_g", [(0, "#FFF0A0", 1), (1, "#F0B429", 1)], cx=0.4, cy=0.35))
    sun = rad("ID_s", [(0, "#FFF6B0", 1), (1, "#FFC93C", 1)], cx=0.4, cy=0.4)
    rays = "".join(f'<rect x="30" y="2" width="4" height="12" rx="2" fill="#FFB020" transform="rotate({k * 45} 32 32)"/>' for k in range(8))
    cloud = '<path d="M14,48 C4,48 4,34 14,34 C14,22 30,18 36,28 C42,20 58,24 56,36 C64,38 62,48 54,48 Z" fill="url(#ID_c)" stroke="#9AB0C0" stroke-width="2"/>'
    cdef = lin("ID_c", [(0, "#ffffff", 1), (1, "#DCE6EE", 1)])
    add("wx_CLEAR", 64, 64, rays + '<circle cx="32" cy="32" r="16" fill="url(#ID_s)" stroke="#E8A020" stroke-width="2"/>', sun)
    add("wx_CLOUDY", 64, 64, '<g transform="translate(10,-8) scale(0.7)">' + rays + '<circle cx="32" cy="32" r="16" fill="url(#ID_s)"/></g>' + cloud, sun + cdef)
    add("wx_RAIN", 64, 64, cloud.replace("48 C4,48", "42 C4,42").replace("H54 Z", "H54 Z") + "".join(f'<path d="M{18 + i * 12},50 l-3,9" stroke="#3E9EE0" stroke-width="3.4" stroke-linecap="round"/>' for i in range(4)), cdef)
    add("wx_STORM", 64, 64, cloud.replace("#DCE6EE", "#B0BCC8") + '<path d="M34,44 L26,56 H34 L30,64 L44,50 H36 L40,44 Z" fill="#FFD23C" stroke="#C99A10" stroke-width="1.4"/>'
        + "".join(f'<path d="M{14 + i * 30},50 l-3,9" stroke="#3E9EE0" stroke-width="3.4" stroke-linecap="round"/>' for i in range(2)), lin("ID_c", [(0, "#E8EEF4", 1), (1, "#A8B8C6", 1)]))
    add("wx_DROUGHT", 64, 64, rays.replace("#FFB020", "#F07020") + '<circle cx="32" cy="32" r="17" fill="url(#ID_s)" stroke="#D85A10" stroke-width="2"/>', rad("ID_s", [(0, "#FFE08A", 1), (1, "#FF8A2A", 1)]))
    add("wx_SNOW", 64, 64, cloud + "".join(f'<circle cx="{18 + i * 12}" cy="{54 + (i % 2) * 4}" r="3.2" fill="#fff" stroke="#9AB0C0"/>' for i in range(4)), cdef)
    add("season_SPRING", 64, 64, '<path d="M32,58 V30" stroke="#3E9E44" stroke-width="4"/><path d="M32,40 C20,40 14,30 16,22 C26,22 32,30 32,40 Z M32,34 C42,34 48,24 46,16 C36,16 32,24 32,34 Z" fill="#7ED45E" stroke="#2A6E30" stroke-width="2"/>'
        + "".join(f'<circle cx="{32 + 8 * math.cos(a):.1f}" cy="{14 + 8 * math.sin(a):.1f}" r="5" fill="#FF9FC0"/>' for a in [k * 1.256 for k in range(5)]) + '<circle cx="32" cy="14" r="4" fill="#FFD25A"/>')
    add("season_SUMMER", 64, 64, rays + '<circle cx="32" cy="32" r="16" fill="url(#ID_s)" stroke="#E8A020" stroke-width="2"/>', sun)
    add("season_AUTUMN", 64, 64, '<path d="M32,6 L38,20 L52,16 L46,30 L58,36 L44,42 L46,56 L32,48 L18,56 L20,42 L6,36 L18,30 L12,16 L26,20 Z" fill="url(#ID_g)" stroke="#9A4A10" stroke-width="2" stroke-linejoin="round"/><path d="M32,62 V26" stroke="#9A4A10" stroke-width="3"/>',
        lin("ID_g", [(0, "#FFB84A", 1), (1, "#E0602A", 1)]))
    add("season_WINTER", 64, 64, "".join(f'<path d="M32,32 L{32 + 26 * math.cos(a):.1f},{32 + 26 * math.sin(a):.1f}" stroke="#7EC8F0" stroke-width="5" stroke-linecap="round"/>' for a in [k * math.pi / 3 for k in range(6)])
        + "".join(f'<path d="M{32 + 16 * math.cos(a) + 6 * math.cos(a + 1.3):.1f},{32 + 16 * math.sin(a) + 6 * math.sin(a + 1.3):.1f} L{32 + 16 * math.cos(a):.1f},{32 + 16 * math.sin(a):.1f} L{32 + 16 * math.cos(a) + 6 * math.cos(a - 1.3):.1f},{32 + 16 * math.sin(a) + 6 * math.sin(a - 1.3):.1f}" stroke="#7EC8F0" stroke-width="3.4" fill="none" stroke-linecap="round"/>' for a in [k * math.pi / 3 for k in range(6)])
        + '<circle cx="32" cy="32" r="6" fill="#E8F6FF" stroke="#7EC8F0" stroke-width="2"/>')

    # 9 parçalı arayüz zeminleri
    def nine(name, w, h, body, defs, split):
        add(name, w, h, body, defs, kind="sprite", split=split)
    nine("ui_panel", 96, 104, '<rect x="10" y="8" width="76" height="84" rx="26" fill="#3a2414" opacity="0.18" filter="url(#ID_b)"/><rect x="8" y="4" width="80" height="84" rx="26" fill="url(#ID_g)" stroke="#EAD8BC" stroke-width="1.5"/>',
         lin("ID_g", [(0, "#FFFFFF", 1), (1, "#FFF8EC", 1)]) + '<filter id="ID_b" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="4"/></filter>', (36, 36, 34, 40))
    nine("ui_card", 64, 64, '<rect x="2" y="2" width="60" height="60" rx="18" fill="#FFF3E0" stroke="#EFDDBF" stroke-width="2"/>', "", (22, 22, 22, 22))
    nine("ui_card_hi", 64, 64, '<rect x="2" y="2" width="60" height="60" rx="18" fill="#E3F7F3" stroke="#9EDDD2" stroke-width="2"/>', "", (22, 22, 22, 22))
    nine("ui_card_warn", 64, 64, '<rect x="2" y="2" width="60" height="60" rx="18" fill="#FFE9DF" stroke="#F4B39A" stroke-width="2"/>', "", (22, 22, 22, 22))

    def button(name, top, bottom, lip, border):
        nine(name, 64, 72, f'<rect x="2" y="8" width="60" height="62" rx="22" fill="{lip}"/><rect x="2" y="2" width="60" height="62" rx="22" fill="url(#ID_g)" stroke="{border}" stroke-width="1.5"/>'
             f'<rect x="10" y="7" width="44" height="14" rx="7" fill="#fff" opacity="0.22"/>', lin("ID_g", [(0, top, 1), (1, bottom, 1)]), (24, 24, 24, 30))
    button("ui_btn_primary", "#43D2BE", "#1FA894", "#15806F", "#1A9583")
    button("ui_btn", "#FFFFFF", "#FFF0D8", "#E4C99E", "#EAD3AC")
    button("ui_btn_down", "#FFE8C4", "#F7D3A0", "#D9B27A", "#E2BE8C")
    button("ui_btn_danger", "#FF9C86", "#EE6A56", "#C0483A", "#D9584A")
    button("ui_btn_selected", "#2BB3A3", "#1C9282", "#13705F", "#167E70")
    button("ui_btn_disabled", "#F1ECE4", "#E2DCD2", "#CFC7BA", "#D8D0C4")
    button("ui_btn_gold", "#FFE07A", "#F5B630", "#C98A10", "#D9A020")
    nine("ui_chip", 48, 48, '<rect x="2" y="4" width="44" height="40" rx="20" fill="#FFF7EA" stroke="#EAD3AC" stroke-width="1.5"/>', "", (20, 20, 20, 20))
    nine("ui_bar_track", 32, 16, '<rect x="1" y="1" width="30" height="14" rx="7" fill="#EADBC4"/>', "", (8, 8, 7, 7))
    nine("ui_bar_fill", 32, 16, '<rect x="1" y="1" width="30" height="14" rx="7" fill="url(#ID_g)"/><rect x="5" y="3" width="22" height="4" rx="2" fill="#fff" opacity="0.35"/>', lin("ID_g", [(0, "#5BE0C8", 1), (1, "#22A893", 1)]), (8, 8, 7, 7))
    nine("ui_bar_warn", 32, 16, '<rect x="1" y="1" width="30" height="14" rx="7" fill="url(#ID_g)"/>', lin("ID_g", [(0, "#FFB08A", 1), (1, "#E8604A", 1)]), (8, 8, 7, 7))
    nine("ui_toast", 96, 72, '<rect x="8" y="10" width="80" height="54" rx="27" fill="#3a2414" opacity="0.2" filter="url(#ID_b)"/><rect x="6" y="4" width="84" height="56" rx="28" fill="#FFFBF2" stroke="#F0D9A8" stroke-width="2"/>',
         '<filter id="ID_b" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="3"/></filter>', (34, 34, 30, 36))
    nine("ui_alert", 96, 72, '<rect x="6" y="4" width="84" height="60" rx="28" fill="url(#ID_g)" stroke="#C0483A" stroke-width="2"/>', lin("ID_g", [(0, "#FF8A76", 1), (1, "#E5584A", 1)]), (34, 34, 30, 32))
    nine("ui_bottom", 96, 96, '<rect x="6" y="10" width="84" height="84" rx="30" fill="#3a2414" opacity="0.2" filter="url(#ID_b)"/><rect x="4" y="4" width="88" height="88" rx="30" fill="#FFFFFF" stroke="#EAD8BC" stroke-width="1.5"/>',
         '<filter id="ID_b" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="4"/></filter>', (38, 38, 38, 38))
    add("ui_circle", 96, 96, '<circle cx="48" cy="50" r="40" fill="#3a2414" opacity="0.15"/><circle cx="48" cy="46" r="40" fill="url(#ID_g)"/>', lin("ID_g", [(0, "#43D2BE", 1), (1, "#1FA894", 1)]))
    add("ui_circle_light", 96, 96, '<circle cx="48" cy="46" r="40" fill="#FFF1DA"/>')
    add("ui_dot", 32, 32, '<circle cx="16" cy="16" r="12" fill="url(#ID_g)" stroke="#fff" stroke-width="3"/>', lin("ID_g", [(0, "#FF8A76", 1), (1, "#E5584A", 1)]))
