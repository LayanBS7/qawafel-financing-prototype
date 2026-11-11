# Qawafel Technical Assessment: written answers

**Scenario.** An SME wholesaler wants financing. They upload 12 months of invoices (Excel) and bank statements (PDF). Qawafel turns them into a structured package and sends it to a bank partner for review.

**Prototype (Task 3):** https://github.com/LayanBS7/qawafel-financing-prototype

---

# Task 1: Requirements

## Who the users are

| User | Who they are | What they need |
|---|---|---|
| **Wholesaler** (applicant) | The owner or finance person at the SME. Busy, not a finance expert, uses Excel or a basic accounting system. | Apply with as little effort as possible, know what's wrong with their files, and know where the request stands. |
| **Qawafel credit analyst** | Qawafel's operations or credit team. Prepares the package before it goes to the bank. | Trust the data, understand the business quickly, and send the bank a package that won't come back with questions. |
| **Bank credit officer** | Works at the partner bank and makes the final lending decision. | A consistent package they can verify against the source documents and defend internally. |

I chose these three because each has a different goal and a different reason to reject the process. The wholesaler gives up if uploading is painful, the analyst can't work with data they don't trust, and the bank says no if it can't verify the numbers. The bank's API is a system, not a user; it's covered in Tasks 2 and 4.

## User stories

### Wholesaler

**W1. Upload my documents**
*As a wholesaler, I want to upload my invoices and bank statements in the formats I already have, so that I don't have to reformat anything.*
- Accepts invoices as `.xlsx`, `.xls` or `.csv`, and bank statements as `.pdf`, up to 20 MB per file.
- Columns are recognized by name, not position. If a required column is missing, the message names it and links to a template.
- A file that can't be read is rejected immediately, with the reason in plain language (not "Error 500").
- After upload, I see the period covered (e.g. "Nov 2025 – Oct 2026") and, if the 12 months aren't complete, which months are missing.

**W2. See and fix problems before I submit**
*As a wholesaler, I want to see what's wrong with my files, so that I can fix them instead of being rejected later.*
- Every problem is listed with its row number, what's wrong, and what happens if I don't fix it (excluded or kept with a warning).
- I can upload a corrected file, and the problem list updates.
- I can't submit while a blocking problem exists (no readable invoices, or months missing). Warnings don't block.

**W3. Give consent and know who sees my data**
*As a wholesaler, I want to choose which bank receives my data, so that I stay in control of it.*
- Before submission I must confirm consent naming the specific bank and what will be shared.
- Nothing is sent to a bank without that consent being recorded (who, when, which bank).
- I can withdraw before the bank has opened the package.

**W4. Track my request**
*As a wholesaler, I want to know where my request is, so that I don't have to call anyone to ask.*
- Statuses: Draft → Submitted → Under review (Qawafel) → Sent to bank → More info needed → Approved / Declined.
- I get a notification (SMS or email) on every status change.
- If the bank or analyst asks for more information, the request says exactly what is needed.

### Qawafel credit analyst

**A1. Trust the data**
*As an analyst, I want to see every data problem and what the system did about it, so that I can trust the numbers I'm sending.*
- Each problem shows its row, type, details and the action taken (excluded / corrected / flagged).
- No value changes without a recorded reason.
- A summary shows rows read, rows used and rows excluded.

**A2. Understand the business in minutes**
*As an analyst, I want the key financial indicators calculated for me, so that I don't have to rebuild them in Excel for every applicant.*
- Shows monthly net sales (excl. VAT, after credit notes), the trend, customer concentration, days sales outstanding (DSO) and aging of unpaid invoices.
- Every figure can be traced back to the invoices behind it.
- Results are ready within 30 seconds of upload for a 12-month file of up to 50,000 rows.

**A3. Spot risky customers**
*As an analyst, I want customers who could hurt repayment to be flagged, so that I don't miss them in a long list.*
- Flags: largest customer above 15% of sales, paying noticeably slower than before, chronic late payer, stopped buying, new customer.
- Each flag shows the numbers behind it (e.g. "20 → 39 days late").
- Flags are thresholds the credit team can change, not hard-coded guesses.

**A4. Get an explainable recommendation**
*As an analyst, I want a suggested score and financing limit with reasons, so that I can make a consistent decision and defend it.*
- The score is broken into factors. Each factor shows the measured value, its points and a one-line reason.
- The suggested limit is shown step by step, from open receivables to the final amount.
- If the requested amount is above the limit, this is stated clearly.

**A5. Decide and send the package**
*As an analyst, I want to record my decision and send a standard package to the bank, so that every application is handled the same way.*
- The decision is mandatory before sending: recommend, recommend with conditions, need more info, or decline. A note is optional.
- The package contains the summary, key figures, score breakdown, limit calculation, flagged customers, conditions, and the original files.
- Every action (upload, decision, sent) is logged with user and time.

### Bank credit officer

**B1. Receive a consistent package**
*As a bank officer, I want every package in the same structure, so that I can review it quickly and compare applicants.*
- Delivered through the bank's API, with the same fields and sections every time and a version number.
- Includes both the analysis and the original documents.

**B2. Verify the numbers**
*As a bank officer, I want to check the figures against the source documents, so that I'm not relying only on Qawafel's word.*
- Every headline figure links to the invoices behind it.
- The share of paid invoices matched to a deposit in the bank statements is shown (see the data risks below).

**B3. Ask for more information**
*As a bank officer, I want to request missing information through the same channel, so that the request doesn't get lost in email.*
- A request from the bank changes the status to "More info needed" and reaches the wholesaler and the analyst.
- The response is attached to the same application.

## Questions I'd ask before building

These are the questions whose answers would change what I build. I've grouped them by who can answer them.

**For the bank partner**
1. **What product is this?** Invoice financing (lending against specific unpaid invoices) or a general working-capital loan? This changes the whole analysis: a borrowing base on receivables vs. cash-flow capacity.
2. **What does the bank decide on?** Their credit criteria, minimum and maximum amounts, required documents, and any hard rules (e.g. minimum time in business, sectors they won't finance).
3. **What format and channel do they want?** API, portal or email; which fields; PDF or structured data; and whether they need the original files.
4. **How does their API behave?** Documentation, a test environment, status codes, rate limits, how they confirm receipt. (Task 4 assumes the worst case here.)
5. **Who makes the decision, and how long does it take?** And what do they need from Qawafel when they ask for more information?

**For Qawafel (the business)**

6. **What role does Qawafel play?** Do we only prepare the package, or do we also recommend or pre-approve? This decides how much the score matters and who is liable for it.
7. **How many applications, and how fast do they need answers?** 10 a month and 1,000 a month need different systems.
8. **Which data do we already have?** Wholesalers that sell through Qawafel may already have order and payment history with us, which is more trustworthy than an uploaded file.

**For the wholesaler**

9. **Where do the invoices come from?** Which accounting system (Zoho, Qoyod, Daftra, Excel), and are they already issuing ZATCA e-invoices (Fatoora)? If yes, we could pull invoices directly instead of asking for uploads.
10. **How many bank accounts and legal entities?** Sales across several accounts or CRs need all statements, or the numbers won't match.
11. **Do they sell to related companies?** Sales to a sister company inflate revenue and are a known fraud pattern.

**Compliance**

12. **What are the rules on consent, data retention and data location** under the PDPL and SAMA? For example: how long can we keep bank statements, and can data leave Saudi Arabia?

## What could go wrong with the data

I've split these by source, because each source fails differently. The ones marked ✅ are detected by the prototype; the rest are designed for in Task 2.

### Invoices (Excel)

| Problem | Why it matters | How to handle it |
|---|---|---|
| ✅ Duplicate rows | Sales counted twice | Exclude exact duplicates; flag the same invoice number with different details |
| ✅ Invoices outside the 12 months (e.g. year typed wrong) | Distorts the trend | Exclude and report |
| ✅ VAT not 15%, or total ≠ subtotal + VAT | Data-entry error, or a sign the file was edited | Keep, flag, recalculate the total |
| ✅ Paid date missing or before the invoice date | Wrong payment-speed metrics | Count the sale, leave it out of payment-speed metrics |
| ✅ Same customer spelled differently | Concentration looks lower than it is | Group by customer ID or normalized name |
| ✅ Missing customer VAT number | Buyer can't be verified | Flag; verify against ZATCA |
| **Fake or inflated invoices** | The biggest risk: lending against sales that never happened | Match invoices to bank deposits; check against ZATCA e-invoice records |
| Sales to related parties | Revenue inflated within a group | Ask for related-party disclosure; flag customers with the same owner or address |
| Credit notes or returns not exported | Revenue overstated | Ask whether credit notes are included; compare credit-note rates with similar wholesalers |
| Stale payment status (invoice marked unpaid but already paid, or the reverse) | Wrong receivables figure | Confirm with bank statements; prefer data pulled directly from the accounting system |
| Different layouts, Arabic headers, Hijri dates, DD/MM vs MM/DD, Arabic-Indic digits | File can't be read, or dates are silently misread | Map headers in both languages; reject ambiguous dates instead of guessing |
| Cash sales missing | Business looks smaller than it is | Ask; bank statements show cash deposits |
| Only part of the business (one branch or CR) | Incomplete picture | Ask for every CR and account |

### Bank statements (PDF)

| Problem | Why it matters | How to handle it |
|---|---|---|
| Scanned images, not text | Can't be read reliably | OCR with a confidence score; manual review below a threshold. Better: Open Banking |
| **Edited PDF** (amounts changed) | Fraud | Check PDF metadata and fonts, and that opening balance + transactions = closing balance; prefer data directly from the bank |
| Missing months or accounts | Cash flow looks wrong | Check date continuity; ask for every account |
| Transfers between the company's own accounts | Inflows counted twice | Detect matching in/out amounts between known accounts |
| Loan disbursements or capital injections counted as income | Revenue looks bigger | Classify transactions by description; exclude non-trading inflows |
| Every bank has a different layout | Parser breaks for each new bank | One parser per bank format, or Open Banking |

### Invoices vs. bank statements

| Problem | Why it matters | How to handle it |
|---|---|---|
| Paid invoices with no matching deposit | Possible fake invoices, or payment into another account | Report the match rate; investigate if it's low |
| One transfer pays many invoices, or one invoice is paid in parts | Simple 1:1 matching fails | Match by customer, amount combinations and dates within a window |
| Customer name in the bank differs from the invoice | Matching fails | Match on IBAN or VAT number when available, then fuzzy name matching |
