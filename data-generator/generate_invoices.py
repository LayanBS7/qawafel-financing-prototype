"""
Generate 12 months of realistic (made-up) sales invoices for a fictional
Saudi FMCG wholesaler and export them to Excel.

All names, VAT numbers and amounts are invented. Any resemblance to a real
company is accidental.

Usage:
    python3 generate_invoices.py                 # writes ../sample-data/invoices_12m.xlsx
    python3 generate_invoices.py --clean         # same data without injected data-quality issues
    python3 generate_invoices.py --seed 7 --out my.xlsx

See README.md in this folder for every assumption behind the numbers.
"""

import argparse
import random
from datetime import date, timedelta
from pathlib import Path

from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill
from openpyxl.utils import get_column_letter

# --------------------------------------------------------------------------
# Business profile
# --------------------------------------------------------------------------
SELLER = {
    "name": "Wadi Al-Khair Wholesale Trading Co. (fictional)",
    "cr_number": "1010999001",
    "vat_number": "399999999900003",
    "city": "Riyadh",
}

PERIOD_START = date(2025, 11, 1)
PERIOD_END = date(2026, 10, 31)   # 12 full months
AS_OF = date(2026, 10, 31)        # export date: anything unpaid by now is open

VAT_RATE = 0.15

CATEGORIES = [
    # (category, share of a basket)
    ("Rice & Grains", 0.24),
    ("Cooking Oil", 0.14),
    ("Sugar & Flour", 0.12),
    ("Dairy & Cheese", 0.15),
    ("Canned Food", 0.10),
    ("Beverages", 0.13),
    ("Cleaning Supplies", 0.07),
    ("Dates", 0.05),
]

# Customer segments: invoices per month, average invoice (SAR, before VAT),
# payment terms in days.
SEGMENTS = {
    "Hypermarket":    {"inv_per_month": (6, 9),   "avg_invoice": (20000, 32000), "terms": 60},
    "Supermarket":    {"inv_per_month": (6, 10),  "avg_invoice": (9000, 15000),  "terms": 45},
    "Mini-market":    {"inv_per_month": (5, 9),   "avg_invoice": (2500, 5000),   "terms": 30},
    "HoReCa":         {"inv_per_month": (4, 7),   "avg_invoice": (4000, 8000),   "terms": 30},
    "Catering":       {"inv_per_month": (3, 6),   "avg_invoice": (8000, 14000),  "terms": 45},
}

# (name, segment, city, payment behaviour, special story)
# Payment behaviour = (mean days late after due date, std dev, chance of never paying by AS_OF)
CUSTOMERS = [
    ("Al-Rayyan Hypermarkets",       "Hypermarket", "Riyadh",  (12, 8, 0.004), "anchor"),
    ("Sahara Value Mart",            "Hypermarket", "Riyadh",  (6, 6, 0.003),  None),
    ("Najd Family Hyper",            "Hypermarket", "Qassim",  (20, 10, 0.006), "slowing"),
    ("Golden Basket Supermarkets",   "Supermarket", "Riyadh",  (3, 5, 0.002),  None),
    ("Bait Al-Tamimi Markets",       "Supermarket", "Riyadh",  (8, 7, 0.004),  None),
    ("Al-Wafra Supermarket",         "Supermarket", "Kharj",   (5, 6, 0.003),  None),
    ("Green Oasis Market",           "Supermarket", "Riyadh",  (15, 9, 0.006), None),
    ("Durrat Al-Sharq Supermarket",  "Supermarket", "Dammam",  (10, 8, 0.004), None),
    ("Al-Bustan Fresh",              "Supermarket", "Riyadh",  (4, 5, 0.002),  "new"),
    ("Qimmat Al-Jawda Supermarket",  "Supermarket", "Qassim",  (25, 15, 0.02), "churned"),
    ("Abu Saud Mini-Market",         "Mini-market", "Riyadh",  (2, 4, 0.002),  None),
    ("Al-Hadaf Grocery",             "Mini-market", "Riyadh",  (7, 6, 0.004),  None),
    ("Nakheel Corner Store",         "Mini-market", "Kharj",   (35, 20, 0.05), "bad_payer"),
    ("Al-Manar Baqala",              "Mini-market", "Riyadh",  (4, 5, 0.003),  None),
    ("Rawabi Mini-Mart",             "Mini-market", "Riyadh",  (9, 7, 0.005),  None),
    ("Zahrat Al-Madina Grocery",     "Mini-market", "Riyadh",  (6, 6, 0.004),  None),
    ("Saffron House Restaurant",     "HoReCa",      "Riyadh",  (10, 8, 0.005), None),
    ("Al-Diwan Restaurants Group",   "HoReCa",      "Riyadh",  (14, 10, 0.006), None),
    ("Layali Najd Hotel",            "HoReCa",      "Riyadh",  (18, 10, 0.006), None),
    ("Bukhari Express",              "HoReCa",      "Kharj",   (5, 6, 0.004),  None),
    ("Urban Shawarma Co.",           "HoReCa",      "Riyadh",  (8, 7, 0.005),  None),
    ("Al-Majlis Catering",           "Catering",    "Riyadh",  (16, 10, 0.006), None),
    ("Desert Rose Catering",         "Catering",    "Dammam",  (12, 9, 0.006), None),
    ("Smart Kitchen Catering",       "Catering",    "Riyadh",  (20, 12, 0.01), None),
]

# The "anchor" customer gets its volume scaled up so it becomes the largest
# buyer (~20% of revenue): a realistic concentration risk for an SME.
ANCHOR_MULTIPLIER = 2.2

# Month index (1-12) -> demand multiplier.
# Ramadan 1447 ~ 18 Feb - 19 Mar 2026, Eid al-Fitr ~ 20 Mar,
# Eid al-Adha ~ 27 May 2026. Retailers stock up in the weeks before Ramadan.
SEASONALITY = {
    11: 0.95,  # Nov 2025
    12: 1.00,  # Dec 2025
    1: 1.20,   # Jan 2026 - pre-Ramadan stocking starts
    2: 1.45,   # Feb 2026 - Ramadan stocking peak
    3: 1.15,   # Mar 2026 - Ramadan + Eid al-Fitr
    4: 0.85,   # Apr 2026 - post-Eid slowdown
    5: 1.10,   # May 2026 - Eid al-Adha
    6: 0.90,   # Jun 2026
    7: 0.75,   # Jul 2026 - summer, families travel
    8: 0.85,   # Aug 2026 - back to school starts
    9: 1.00,   # Sep 2026 - National Day (23 Sep)
    10: 1.05,  # Oct 2026
}

# Mild underlying growth across the year (~8% end to end).
def growth_factor(d: date) -> float:
    months_in = (d.year - PERIOD_START.year) * 12 + d.month - PERIOD_START.month
    return 1.0 + 0.007 * months_in


# --------------------------------------------------------------------------
# Helpers
# --------------------------------------------------------------------------
def fake_vat_number(rng: random.Random) -> str:
    # Saudi VAT numbers are 15 digits, starting and ending with 3.
    middle = "".join(str(rng.randint(0, 9)) for _ in range(13))
    return f"3{middle}3"


def month_iter(start: date, end: date):
    d = date(start.year, start.month, 1)
    while d <= end:
        yield d
        d = date(d.year + (d.month // 12), d.month % 12 + 1, 1)


def days_in_month(d: date) -> int:
    nxt = date(d.year + (d.month // 12), d.month % 12 + 1, 1)
    return (nxt - d).days


def random_business_day(rng: random.Random, month_start: date) -> date:
    # Saudi weekend is Friday + Saturday. Wholesalers deliver Sun-Thu mostly.
    for _ in range(50):
        d = month_start + timedelta(days=rng.randrange(days_in_month(month_start)))
        if d.weekday() not in (4, 5):  # 4 = Friday, 5 = Saturday
            return d
    return month_start


def pick_category(rng: random.Random) -> str:
    r = rng.random()
    acc = 0.0
    for name, share in CATEGORIES:
        acc += share
        if r <= acc:
            return name
    return CATEGORIES[-1][0]


# --------------------------------------------------------------------------
# Generation
# --------------------------------------------------------------------------
def customer_active(story, d: date) -> bool:
    if story == "new":
        return d >= date(2026, 5, 1)          # won in May 2026
    if story == "churned":
        return d < date(2026, 6, 1)           # stopped buying after May 2026
    return True


def days_late_for(rng, behaviour, story, invoice_date) -> int:
    mean, std, _ = behaviour
    if story == "slowing" and invoice_date >= date(2026, 7, 1):
        mean += 25                            # pays noticeably slower in the last months
    return max(-5, int(rng.gauss(mean, std)))  # some pay a few days early


def generate(seed: int, inject_issues: bool):
    rng = random.Random(seed)

    customers = []
    for i, (name, seg, city, behaviour, story) in enumerate(CUSTOMERS, start=1):
        customers.append({
            "id": f"C{i:03d}",
            "name": name,
            "segment": seg,
            "city": city,
            "vat": fake_vat_number(rng),
            "terms": SEGMENTS[seg]["terms"],
            "behaviour": behaviour,
            "story": story,
        })

    invoices = []
    for m in month_iter(PERIOD_START, PERIOD_END):
        season = SEASONALITY[m.month]
        for c in customers:
            if not customer_active(c["story"], m):
                continue
            seg = SEGMENTS[c["segment"]]
            n = rng.randint(*seg["inv_per_month"])
            n = round(n * season * (ANCHOR_MULTIPLIER if c["story"] == "anchor" else 1))
            for _ in range(n):
                inv_date = random_business_day(rng, m)
                lo, hi = seg["avg_invoice"]
                subtotal = rng.uniform(lo, hi) * growth_factor(inv_date) * rng.uniform(0.85, 1.15)
                subtotal = round(subtotal, 2)
                vat = round(subtotal * VAT_RATE, 2)
                total = round(subtotal + vat, 2)
                due = inv_date + timedelta(days=c["terms"])

                # Payment outcome
                never_pays = rng.random() < c["behaviour"][2]
                late = days_late_for(rng, c["behaviour"], c["story"], inv_date)
                pay_date = due + timedelta(days=late)
                partial = rng.random() < 0.03

                if never_pays or pay_date > AS_OF:
                    status, paid_date, paid = "Unpaid", None, 0.0
                    if partial and pay_date > AS_OF:
                        status = "Partially Paid"
                        paid = round(total * rng.choice([0.3, 0.5, 0.6]), 2)
                        paid_date = min(AS_OF, inv_date + timedelta(days=c["terms"] // 2))
                elif partial:
                    status = "Partially Paid"
                    paid = round(total * rng.choice([0.3, 0.5, 0.6]), 2)
                    paid_date = pay_date
                else:
                    status, paid_date, paid = "Paid", pay_date, total

                invoices.append({
                    "invoice_date": inv_date,
                    "customer": c,
                    "category": pick_category(rng),
                    "subtotal": subtotal,
                    "vat": vat,
                    "total": total,
                    "due_date": due,
                    "status": status,
                    "paid_date": paid_date,
                    "amount_paid": paid,
                    "notes": "",
                })

    # Credit notes: ~1.5% of invoices get a return/discount credit note.
    credit_notes = []
    for inv in rng.sample(invoices, k=int(len(invoices) * 0.015)):
        cn_sub = -round(inv["subtotal"] * rng.uniform(0.05, 0.25), 2)
        cn_vat = round(cn_sub * VAT_RATE, 2)
        cn_date = min(AS_OF, inv["invoice_date"] + timedelta(days=rng.randint(3, 20)))
        credit_notes.append({
            "invoice_date": cn_date,
            "customer": inv["customer"],
            "category": inv["category"],
            "subtotal": cn_sub,
            "vat": cn_vat,
            "total": round(cn_sub + cn_vat, 2),
            "due_date": cn_date,
            "status": "Credit Note",
            "paid_date": None,
            "amount_paid": 0.0,
            "notes": "Return / damaged goods",
            "ref": inv,
        })
    invoices.extend(credit_notes)

    invoices.sort(key=lambda r: (r["invoice_date"], r["customer"]["id"]))

    # Number them in date order like a real accounting system would.
    seq = {"INV": 0, "CN": 0}
    for r in invoices:
        prefix = "CN" if r["status"] == "Credit Note" else "INV"
        seq[prefix] += 1
        r["number"] = f"{prefix}-{r['invoice_date'].year}-{seq[prefix]:05d}"
    for r in invoices:
        if "ref" in r:
            r["notes"] = f"Return / damaged goods - ref {r['ref']['number']}"

    issues = []
    if inject_issues:
        issues = inject_data_issues(rng, invoices)

    return customers, invoices, issues


def inject_data_issues(rng: random.Random, invoices):
    """Add the kind of mess real exports contain, so the app has something to catch.
    Returns a log of exactly what was injected (kept OUT of the Excel file)."""
    log = []
    normal = [r for r in invoices if r["status"] != "Credit Note"]

    # 1. Exact duplicate rows (export / copy-paste mistake)
    for r in rng.sample(normal, 6):
        dup = dict(r)
        invoices.insert(invoices.index(r) + 1, dup)
        log.append(("Duplicate row", r["number"]))

    # 2. Missing customer VAT number
    for r in rng.sample(normal, 5):
        r["vat_override"] = ""
        log.append(("Missing customer VAT number", r["number"]))

    # 3. VAT not equal to 15% of subtotal
    for r in rng.sample(normal, 4):
        r["vat"] = round(r["subtotal"] * rng.choice([0.05, 0.10, 0.0]), 2)
        r["total"] = round(r["subtotal"] + r["vat"], 2)
        log.append(("VAT is not 15%", r["number"]))

    # 4. Total does not equal subtotal + VAT (typo)
    for r in rng.sample(normal, 3):
        r["total"] = round(r["total"] + rng.choice([1000, -500, 90]), 2)
        log.append(("Total != subtotal + VAT", r["number"]))

    # 5. Paid date before invoice date
    paid = [r for r in normal if r["status"] == "Paid"]
    for r in rng.sample(paid, 3):
        r["paid_date"] = r["invoice_date"] - timedelta(days=rng.randint(5, 40))
        log.append(("Paid date before invoice date", r["number"]))

    # 6. Status says Paid but no paid date / amount
    for r in rng.sample(paid, 3):
        r["paid_date"] = None
        log.append(("Status Paid but no paid date", r["number"]))

    # 7. Date outside the 12-month window (wrong year typed)
    for r in rng.sample(normal, 2):
        r["invoice_date"] = r["invoice_date"].replace(year=r["invoice_date"].year - 1)
        log.append(("Invoice date outside 12-month period", r["number"]))

    # 8. Customer name spelled differently for the same customer
    for r in rng.sample(normal, 4):
        r["name_override"] = r["customer"]["name"].upper() + " "
        log.append(("Customer name variant (same customer)", r["number"]))

    # 9. Missing amount
    for r in rng.sample(normal, 2):
        r["subtotal"] = None
        log.append(("Missing subtotal", r["number"]))

    return log


# --------------------------------------------------------------------------
# Excel output
# --------------------------------------------------------------------------
HEADERS = [
    "Invoice Number", "Invoice Date", "Due Date", "Customer ID", "Customer Name",
    "Customer VAT Number", "Customer Segment", "City", "Product Category",
    "Payment Terms (days)", "Subtotal (SAR)", "VAT (SAR)", "Total (SAR)",
    "Status", "Paid Date", "Amount Paid (SAR)", "Notes",
]


def write_excel(path: Path, customers, invoices):
    wb = Workbook()
    ws = wb.active
    ws.title = "Invoices"
    ws.append(HEADERS)
    for r in invoices:
        c = r["customer"]
        ws.append([
            r["number"],
            r["invoice_date"],
            r["due_date"],
            c["id"],
            r.get("name_override", c["name"]),
            r.get("vat_override", c["vat"]),
            c["segment"],
            c["city"],
            r["category"],
            c["terms"],
            r["subtotal"],
            r["vat"],
            r["total"],
            r["status"],
            r["paid_date"],
            r["amount_paid"],
            r["notes"],
        ])

    header_fill = PatternFill("solid", fgColor="1F2937")
    for cell in ws[1]:
        cell.font = Font(bold=True, color="FFFFFF")
        cell.fill = header_fill
    for col in (2, 3, 15):
        for row in ws.iter_rows(min_row=2, min_col=col, max_col=col):
            row[0].number_format = "yyyy-mm-dd"
    for col in (11, 12, 13, 16):
        for row in ws.iter_rows(min_row=2, min_col=col, max_col=col):
            row[0].number_format = "#,##0.00"
    widths = [18, 13, 13, 11, 32, 20, 16, 10, 18, 10, 14, 12, 14, 15, 13, 16, 45]
    for i, w in enumerate(widths, start=1):
        ws.column_dimensions[get_column_letter(i)].width = w
    ws.freeze_panes = "A2"

    info = wb.create_sheet("Company Info")
    info.append(["Field", "Value"])
    info.append(["Seller", SELLER["name"]])
    info.append(["CR Number", SELLER["cr_number"]])
    info.append(["VAT Number", SELLER["vat_number"]])
    info.append(["City", SELLER["city"]])
    info.append(["Period", f"{PERIOD_START} to {PERIOD_END}"])
    info.append(["Exported On", AS_OF])
    info.append(["Currency", "SAR"])
    info.append(["Note", "Fictional data generated for a prototype. Not a real company."])
    for cell in info[1]:
        cell.font = Font(bold=True)
    info.column_dimensions["A"].width = 14
    info.column_dimensions["B"].width = 60

    path.parent.mkdir(parents=True, exist_ok=True)
    wb.save(path)


def print_summary(customers, invoices, issues):
    real = [r for r in invoices if r["subtotal"] is not None]
    sales = [r for r in real if r["status"] != "Credit Note"]
    revenue = sum(r["subtotal"] for r in real)
    print(f"Rows: {len(invoices)}  (invoices {len(sales)}, credit notes "
          f"{sum(1 for r in invoices if r['status'] == 'Credit Note')})")
    print(f"Net revenue excl. VAT: SAR {revenue:,.0f}")

    by_month = {}
    for r in real:
        k = r["invoice_date"].strftime("%Y-%m")
        by_month[k] = by_month.get(k, 0) + r["subtotal"]
    for k in sorted(by_month):
        print(f"  {k}: SAR {by_month[k]:>12,.0f}")

    by_cust = {}
    for r in real:
        by_cust[r["customer"]["name"]] = by_cust.get(r["customer"]["name"], 0) + r["subtotal"]
    top = sorted(by_cust.items(), key=lambda kv: -kv[1])[:5]
    print("Top 5 customers:")
    for name, v in top:
        print(f"  {name:32s} {v / revenue:6.1%}")

    open_amt = sum(r["total"] - r["amount_paid"] for r in sales if r["status"] in ("Unpaid", "Partially Paid"))
    print(f"Open receivables at {AS_OF}: SAR {open_amt:,.0f}")
    if issues:
        print(f"Injected data issues: {len(issues)}")


def write_issue_log(path: Path, issues):
    lines = ["# Injected data-quality issues (answer key)", "",
             "These problems were added on purpose so the app's validation has something to catch.",
             "", "| # | Issue | Invoice |", "|---|---|---|"]
    for i, (kind, num) in enumerate(issues, start=1):
        lines.append(f"| {i} | {kind} | {num} |")
    path.write_text("\n".join(lines) + "\n")


def main():
    here = Path(__file__).resolve().parent
    ap = argparse.ArgumentParser()
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--clean", action="store_true", help="do not inject data-quality issues")
    ap.add_argument("--out", type=Path, default=here.parent / "sample-data" / "invoices_12m.xlsx")
    args = ap.parse_args()

    customers, invoices, issues = generate(args.seed, inject_issues=not args.clean)
    write_excel(args.out, customers, invoices)
    if issues:
        write_issue_log(args.out.with_name("injected_issues.md"), issues)
    print_summary(customers, invoices, issues)
    print(f"Wrote {args.out}")


if __name__ == "__main__":
    main()
