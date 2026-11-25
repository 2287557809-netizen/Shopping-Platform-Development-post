"""Render the ShopStream architecture diagram as SVG.

The diagram is drawn from data (boxes + routes), so ports, labels or services can be
edited here and the picture regenerated with:

    python scripts/render_architecture.py

The SVG is then rasterised with a headless browser (see docs/DESIGN-REPORT.md).
"""

from pathlib import Path

W, H = 1500, 940
OUT = Path(__file__).resolve().parent.parent / "docs" / "architecture.svg"

PALETTE = {
    "client": ("#EEF2FF", "#4F46E5", "#312E81"),
    "gateway": ("#E0F2FE", "#0284C7", "#0C4A6E"),
    "platform": ("#ECFDF5", "#059669", "#064E3B"),
    "service": ("#F8FAFC", "#334155", "#0F172A"),
    "mysql": ("#FFF7ED", "#C2410C", "#7C2D12"),
    "redis": ("#FEF2F2", "#DC2626", "#7F1D1D"),
    "es": ("#F5F3FF", "#7C3AED", "#4C1D95"),
    "kafka": ("#FFFBEB", "#D97706", "#78350F"),
}

SOLID = "#94A3B8"
EVENT = "#EA580C"
MUTED = "#64748B"

parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="Segoe UI, Arial, sans-serif">']
parts.append("<defs>")
for name, colour in (("s", SOLID), ("e", EVENT), ("m", MUTED)):
    parts.append(
        f'<marker id="a{name}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" '
        f'markerHeight="7" orient="auto-start-reverse">'
        f'<path d="M0,0 L10,5 L0,10 z" fill="{colour}"/></marker>'
    )
parts.append("</defs>")
parts.append(f'<rect x="0" y="0" width="{W}" height="{H}" fill="#FFFFFF"/>')


def box(x, y, w, h, kind, title, subs=()):
    fill, stroke, ink = PALETTE[kind]
    parts.append(
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" fill="{fill}" '
        f'stroke="{stroke}" stroke-width="2"/>'
    )
    cx = x + w / 2
    parts.append(
        f'<text x="{cx}" y="{y + 31}" text-anchor="middle" font-size="18" '
        f'font-weight="600" fill="{ink}">{title}</text>'
    )
    for i, text in enumerate(subs):
        parts.append(
            f'<text x="{cx}" y="{y + 54 + i * 20}" text-anchor="middle" font-size="12.5" '
            f'fill="#475569">{text}</text>'
        )
    return (x, y, w, h)


def route(points, kind="s", both=False):
    marker = f'a{kind}'
    start_marker = f' marker-start="url(#{marker})"' if both else ""
    d = "M " + " L ".join(f"{px},{py}" for px, py in points)
    stroke = {"s": SOLID, "e": EVENT, "m": MUTED}[kind]
    dash = ' stroke-dasharray="7 5"' if kind == "e" else ""
    parts.append(
        f'<path d="{d}" fill="none" stroke="{stroke}" stroke-width="2.5"{dash} '
        f'stroke-linejoin="round" marker-end="url(#{marker})"{start_marker}/>'
    )


def label(x, y, text, size=11.5, colour=MUTED, anchor="middle", italic=True):
    style = ' font-style="italic"' if italic else ""
    parts.append(
        f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" '
        f'fill="{colour}"{style}>{text}</text>'
    )


# --- row 0: client -----------------------------------------------------------
box(190, 34, 220, 70, "client", "Client", ("Browser · Postman · curl",))

# --- row 1: entry tier -------------------------------------------------------
box(60, 150, 580, 80, "gateway", "API Gateway  :8080",
    ("Spring Cloud Gateway · X-Request-Id filter", "routes /api/** → lb://service"))
box(700, 150, 340, 80, "platform", "Eureka  :8761", ("service registry", "7 applications · 2 product instances"))
box(1100, 150, 340, 80, "platform", "Config Server  :8888", ("Git-backed properties", "fetched at bootstrap"))

# --- row 2: services ---------------------------------------------------------
SERVICES = [
    ("product-service", ["2 instances", "catalog CRUD + stock"]),
    ("cart-service", ["Redis cart, TTL 1800 s", "checkout hand-off"]),
    ("order-service", ["order state machine", "saga coordinator"]),
    ("search-service", ["index writer", "query API"]),
    ("notification-service", ["Kafka consumer", "logs every event"]),
]
SVC_Y, SVC_H, SVC_W, SVC_X0, SVC_GAP = 330, 95, 228, 60, 60
svc = {}
for i, (name, subs) in enumerate(SERVICES):
    x = SVC_X0 + i * (SVC_W + SVC_GAP)
    svc[name] = box(x, SVC_Y, SVC_W, SVC_H, "service", name, subs)

# --- row 3: data tier (one owner per column) ---------------------------------
INF_Y, INF_H = 560, 90
infra = {}
for i, (kind, title, subs) in enumerate([
    ("mysql", "MySQL 8.4  :3306", ["productdb", "products, stock_reservations"]),
    ("redis", "Redis 7  :6379", ["carts hash", "@Cacheable products"]),
    ("mysql", "MySQL 8.4  :3307", ["orderdb", "orders, order_items"]),
    ("es", "Elasticsearch  :9200", ["product documents", "written by product-service"]),
]):
    x = SVC_X0 + i * (SVC_W + SVC_GAP)
    infra[i] = box(x, INF_Y, SVC_W, INF_H, kind, title, subs)

# --- row 4: event backbone ---------------------------------------------------
box(60, 730, 1380, 105, "kafka", "Apache Kafka  (KRaft, no ZooKeeper)  :9092",
    ("product-events · inventory-events · order-events  —  3 partitions each, replication factor 1",
     "record key = aggregate id, so events of one order or product stay on one partition"))

# --- arrows ------------------------------------------------------------------
route([(300, 104), (300, 150)], "s")                     # client -> gateway
for name, _ in SERVICES:                                 # gateway -> services
    b = svc[name]
    route([(300, 230), (b[0] + b[2] / 2, SVC_Y)], "s")

route([(640, 190), (700, 190)], "m")                     # gateway -> eureka
label(670, 178, "resolve")
route([(870, 230), (884, 328)], "m")                     # registration
label(900, 264, "register", anchor="start")
route([(1270, 230), (1252, 328)], "m")                   # config
label(1240, 264, "config", anchor="end")

route([(174, 425), (174, 560)], "s")                     # product -> productdb
route([(288, 425), (400, 556)], "e")                     # product -> redis
label(300, 548, "@Cacheable")
route([(462, 425), (462, 560)], "s")                     # cart -> redis
route([(588, 400), (636, 400)], "s", both=True)           # cart -> order
label(612, 452, "checkout")
route([(750, 425), (750, 560)], "s")                     # order -> orderdb
route([(1038, 425), (1038, 560)], "s")                   # search -> elasticsearch

# event flows: leave the service box, travel in the free band, drop through a gap
route([(174, 425), (174, 500), (318, 500), (318, 726)], "e", both=True)
label(306, 700, "publish ProductCreated, StockReserved", anchor="end")
label(306, 718, "consume OrderPlaced", anchor="end")
route([(750, 425), (750, 505), (894, 505), (894, 726)], "e")
label(882, 700, "order publishes OrderPlaced / OrderConfirmed / OrderRejected", anchor="end")
route([(1326, 726), (1326, 425)], "e")                   # kafka -> notification
label(1314, 700, "notification consumes every order and inventory event", anchor="end")

# --- legend ------------------------------------------------------------------
lx, ly = 60, 866
route([(lx, ly), (lx + 60, ly)], "s")
label(lx + 70, ly + 4, "synchronous call (REST / JDBC)", 13, anchor="start", italic=False)
route([(lx + 320, ly), (lx + 378, ly)], "e")
label(lx + 388, ly + 4, "asynchronous event (Kafka)", 13, anchor="start", italic=False)
label(W - 60, ly + 4, "ShopStream — distributed e-commerce platform", 13, "#334155", anchor="end", italic=False)

parts.append("</svg>")
OUT.write_text("\n".join(parts), encoding="utf-8")
print(f"wrote {OUT}")

