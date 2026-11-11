# Sample data generator

Creates 12 months of **made-up** sales invoices for a fictional Saudi FMCG wholesaler
and writes them to `../sample-data/invoices_12m.xlsx`.

```bash
pip install openpyxl
python3 generate_invoices.py            # with deliberate data-quality issues (default)
python3 generate_invoices.py --clean    # same data, no issues
```

The generator is seeded (`--seed 42` by default), so it produces the same file on every run.

## The business

| | |
|---|---|
| Seller | Wadi Al-Khair Wholesale Trading Co. (fictional), Riyadh |
| Sells | Food and grocery staples (rice, oil, sugar, dairy, canned food, beverages, cleaning supplies, dates) |
| Period | 1 Nov 2025 to 31 Oct 2026 (12 full months) |
| Export date | 31 Oct 2026, so anything unpaid by then is an open receivable |
| Size | About SAR 24M a year in net sales and about 2,000 invoices, a typical SME wholesaler |
| Currency and tax | SAR, 15% Saudi VAT |

## Assumptions

**Customers (24 in total, in five segments)**

| Segment | Invoices per month | Invoice size before VAT (SAR) | Payment terms |
|---|---|---|---|
| Hypermarket | 6–9 | 20k–32k | 60 days |
| Supermarket | 6–10 | 9k–15k | 45 days |
| Mini-market (baqala) | 5–9 | 2.5k–5k | 30 days |
| HoReCa (restaurants, hotels) | 4–7 | 4k–8k | 30 days |
| Catering | 3–6 | 8k–14k | 45 days |

Large retailers get longer terms because they have negotiating power, which is normal in Saudi FMCG distribution.

**Seasonality**, a demand multiplier per month:
- Jan–Feb 2026: stocking up before Ramadan (Ramadan 1447 runs about 18 Feb to 19 Mar 2026). February is the peak month.
- Mar 2026: Ramadan and Eid al-Fitr.
- Apr 2026: post-Eid slowdown.
- May 2026: Eid al-Adha (about 27 May).
- Jul–Aug 2026: summer dip while families travel.
- Sep–Oct 2026: recovery, including National Day on 23 Sep.
- On top of that, mild underlying growth of about 8% across the year.

**Invoice dates** fall on Sunday to Thursday. Friday and Saturday are the Saudi weekend.

**Payment behavior.** Each customer has its own average number of days late, a spread around it, and a small chance of never paying. Some customers pay a few days early.

**Built-in stories**, so the analysis has something real to find:

| Story | Customer | What a good tool should notice |
|---|---|---|
| Anchor customer | Al-Rayyan Hypermarkets | About 24% of revenue: **concentration risk** |
| Slowing payer | Najd Family Hyper | Pays about 25 days later on invoices from May 2026: **deteriorating collections** |
| Bad payer | Nakheel Corner Store | Pays about 35 days late on average, with more unpaid invoices |
| Lost customer | Qimmat Al-Jawda Supermarket | Stops buying after May 2026: **churn** |
| New customer | Al-Bustan Fresh | Starts buying in May 2026 |

**Partial payments.** About 3% of invoices are only partly paid.

**Credit notes.** About 1.5% of invoices get a credit note for returns or damaged goods, worth 5–25% of the invoice, with negative amounts and a reference to the original invoice.

## Deliberate data-quality issues

Real exports are messy, so by default the generator injects 32 problems. The full answer key is in
[`../sample-data/injected_issues.md`](../sample-data/injected_issues.md).

| Issue | Count | Why it's realistic |
|---|---|---|
| Exact duplicate rows | 6 | Copy-paste or double export |
| Missing customer VAT number | 5 | Field left blank in the accounting system |
| VAT is not 15% | 4 | Wrong tax code, or the old 5% rate |
| Total doesn't equal subtotal + VAT | 3 | Manual edit or typo |
| Paid date before invoice date | 3 | Data-entry error |
| Status "Paid" but no paid date | 3 | Status updated without recording the payment |
| Invoice date outside the 12 months | 2 | Wrong year typed |
| Same customer, name spelled differently | 4 | Free-text customer names |
| Missing subtotal | 2 | Incomplete row |

## Excel layout

**Sheet `Invoices`**, one row per invoice or credit note:

`Invoice Number, Invoice Date, Due Date, Customer ID, Customer Name, Customer VAT Number,
Customer Segment, City, Product Category, Payment Terms (days), Subtotal (SAR), VAT (SAR),
Total (SAR), Status, Paid Date, Amount Paid (SAR), Notes`

`Status` is one of `Paid`, `Unpaid`, `Partially Paid` or `Credit Note`.

**Sheet `Company Info`** holds the seller details and the export period.

## Simplifications

- One row per invoice with a single product category, instead of full line items.
- No cash or immediate-payment sales. Every sale is on credit terms.
- The VAT and CR numbers are random 15-digit and 10-digit values in the right format, not real registrations.
