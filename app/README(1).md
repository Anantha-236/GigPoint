# GigPoint

GigPoint is an **offline-first, multilingual, voice-driven inventory management app** for small merchants. It is designed for shops that may have unreliable internet, limited digital literacy, and a preference for regional or mixed-language speech over typing.

## Core Goal

Help merchants answer four daily questions:

1. What stock do I have?
2. What is selling fast or slowly?
3. What should I reorder?
4. Where is my money tied up?

## Current Prototype

Implemented or demonstrated in the current Android prototype:

- Persistent local inventory storage
- Add products
- Stock in / stock out
- Current stock quantity
- Low-stock thresholds
- Out-of-stock detection
- Transaction history
- English command parsing
- Telugu-English mixed command parsing
- Hindi-English mixed command parsing
- Confirmation before stock updates
- Stock questions
- Low-stock questions
- Pending / synced state
- Offline-first local persistence

Current voice entry is still simulated by text/sample commands. The selected offline ASR model is:

`Whisper Tiny Multilingual Q5_1` (`ggml-tiny-q5_1.bin`)

## Offline-First Architecture

```text
Merchant action
    ↓
Local database write
    ↓
UI updates immediately
    ↓
sync_status = PENDING
    ↓
Internet available?
    ├── No  → keep data locally
    └── Yes → sync to Supabase
```

Local data must remain available after:

- app restart
- force close
- phone reboot
- temporary network loss
- cloud/backend outage

Successful cloud synchronization must **not delete local history**.

## Planned Architecture

```text
Android App
├── Whisper.cpp
│   └── Whisper Tiny Multilingual Q5_1
├── Multilingual Command Parser
├── OCR / Image-to-Text Import
├── Local SQLite / Room Database
├── WorkManager Sync
└── Supabase
    ├── Auth
    └── PostgreSQL
```

## Authentication and User Data

Planned features:

- Register
- Login
- Logout
- Persistent session
- View profile
- Update profile
- Change password
- Shop profile
- Preferred language
- User/shop-scoped local inventory
- Secure cloud synchronization

Each user/shop must have isolated inventory data so that one merchant cannot access another merchant's records.

## Voice Inventory Management

Example commands:

```text
Rice five bags add cheyyi
Sugar rendu kg teesey
Rice stock entha undi?
Low stock items enti?

Rice five bags add karo
Sugar do kilo nikalo

Add 5 bags of rice
How much rice is available?
```

Target voice flow:

```text
Microphone
    ↓
Whisper Tiny Q5_1
    ↓
Transcript
    ↓
Intent + product + quantity + unit parser
    ↓
Merchant confirmation
    ↓
Local database
    ↓
Background sync
```

## Past Records Import Using Image-to-Text

Many merchants already have inventory records in:

- notebooks
- handwritten registers
- bills
- purchase invoices
- stock sheets
- supplier receipts

GigPoint will provide an image-import workflow:

```text
Take photo / choose image
    ↓
OCR / text extraction
    ↓
Detect products, quantities, prices and dates
    ↓
Editable review screen
    ↓
Merchant confirms
    ↓
Save locally
    ↓
Sync later
```

Possible fields:

- Product name
- Quantity
- Unit
- Purchase price
- Selling price
- Supplier
- Bill/invoice number
- Purchase date
- Transaction date
- Expiry date

**OCR must never automatically modify inventory without user confirmation.**

On-device OCR options to evaluate:

- Google ML Kit Text Recognition
- Tesseract OCR
- Lightweight offline OCR models

## Stock Health Management

GigPoint should classify inventory into useful states such as:

- Healthy Stock
- Low Stock
- Critical Stock
- Out of Stock
- Overstock
- Fast Moving
- Slow Moving
- Old Stock
- Near Expiry
- Dead Stock

## Smart Reorder Suggestions

Suggested reorder quantity can use:

- current quantity
- minimum stock
- recent sales velocity
- supplier lead time
- pending purchase orders
- expected demand

Example:

```text
Sunflower Oil

Current stock: 4 cartons
Average weekly sales: 8 cartons
Minimum reserve: 3 cartons

Suggested reorder: 12 cartons
```

The merchant always makes the final purchase decision.

## Fast-Moving and Slow-Moving Products

The app will calculate product movement from transaction history.

Fast-moving products help merchants:

- prevent stockouts
- increase reorder quantities
- prepare for weekends or festivals

Slow-moving products help merchants:

- avoid over-ordering
- identify old inventory
- plan discounts
- reduce dead stock

## Old Stock and Dead Stock

Possible default classification:

```text
No sales for 30 days → Slow
No sales for 60 days → Very Slow
No sales for 90 days → Dead Stock
```

Thresholds should be configurable.

## Expiry and Batch Tracking

For products with expiry dates:

- batch tracking
- purchase date
- expiry date
- quantity by batch
- supplier
- purchase price

Alerts:

- Expires in 30 days
- Expires in 7 days
- Expired

FEFO (First Expire, First Out) can be used where relevant.

## Purchase and Sales Tracking

Planned transaction types:

- PURCHASE
- SALE
- RETURN_IN
- RETURN_OUT
- DAMAGE
- EXPIRED
- ADJUSTMENT
- OPENING_STOCK

## Income, Cost and Margin Insights

When purchase and selling prices are available, GigPoint can calculate:

- daily sales
- weekly sales
- monthly sales
- estimated cost of goods sold
- gross margin
- highest revenue products
- lowest-margin products
- inventory value
- unsold inventory value

These are management estimates, not formal tax/accounting statements.

## Inventory Value

The app should answer:

> How much money is currently tied up in stock?

Example:

```text
Rice                 ₹48,000
Oil                  ₹31,500
Biscuits             ₹17,800
Soft Drinks          ₹12,200

Total inventory value: ₹109,500
```

## Purchase Planning

The system can generate a recommended purchase list from:

- low-stock products
- fast-moving products
- supplier data
- sales velocity
- current inventory
- purchase budget

## Reminders and Notifications

### Daily

- low-stock summary
- out-of-stock products
- pending sync
- today's sales summary

### Weekly

- reorder recommendations
- fast-moving products
- slow-moving products
- inventory valuation
- old-stock report

### Event-Based

- product becomes low
- product becomes out of stock
- expiry approaching
- old-stock threshold reached
- unusual increase/decrease in sales
- sync has remained pending too long

## Business Insights

Future insights may include:

- “Milk usually sells faster on Saturdays.”
- “Rice sales increased compared with last week.”
- “Oil may run out in 3 days at the current sales rate.”
- “Product X has not sold for 47 days.”
- “₹8,200 of stock has not moved in 60 days.”

## Area-Level Demand Trends

A future optional feature can show what products are selling quickly in an area.

This cannot be derived from one merchant alone. It would require **opt-in, anonymized, aggregated sales data** from participating stores.

Privacy requirements:

- explicit opt-in
- aggregated statistics only
- no exposure of individual shop sales
- no customer-level data
- minimum aggregation thresholds
- opt-out available at any time

## Supplier Management

Planned supplier data:

- supplier name
- phone
- products supplied
- last purchase
- usual purchase price
- average delivery time
- pending orders
- outstanding payment
- preferred supplier

## Stock Questions

The merchant should eventually be able to ask:

```text
How much rice do I have?
What is running low?
What is out of stock?
What should I order?
Which products sold fastest this week?
Which products are not selling?
What stock is older than 60 days?
What expires this month?
How much inventory value do I have?
How much did I sell today?
Which product generated the highest revenue?
```

Answers must come from actual local/cloud inventory data.

## Technology Direction

| Layer | Technology |
|---|---|
| Mobile App | Native Android |
| Language | Kotlin |
| UI | Android Views / Material Components |
| Local Database | SQLite now, Room planned |
| Offline Speech | whisper.cpp |
| ASR Model | Whisper Tiny Multilingual Q5_1 |
| OCR | On-device OCR to be benchmarked |
| Command Parsing | Deterministic multilingual parser |
| Cloud Auth | Supabase Auth |
| Cloud Database | Supabase PostgreSQL |
| Security | Row Level Security |
| Background Sync | Android WorkManager |
| TTS | Android TextToSpeech |
| Future Backend | Spring Boot if needed |

## Development Roadmap

### Phase 1 — Prototype Foundation
- [x] Android project
- [x] Persistent local inventory
- [x] Product management
- [x] Stock in/out
- [x] Transaction history
- [x] Low-stock alerts
- [x] Multilingual parser prototype
- [x] Offline/sync-state prototype
- [ ] Responsive UI redesign

### Phase 2 — Authentication and Cloud
- [ ] Supabase project
- [ ] PostgreSQL schema
- [ ] Row Level Security
- [ ] Register/login/logout
- [ ] Persistent authenticated session
- [ ] Merchant profile
- [ ] Shop profile
- [ ] User/shop scoped local data
- [ ] WorkManager cloud sync

### Phase 3 — Offline Voice
- [ ] whisper.cpp integration
- [ ] Whisper Tiny Multilingual Q5_1
- [ ] Microphone recording
- [ ] Telugu-English testing
- [ ] Hindi-English testing
- [ ] Error/confidence handling
- [ ] Android TTS

### Phase 4 — Historical Record Import
- [ ] Camera/gallery import
- [ ] OCR pipeline
- [ ] Invoice/register parsing
- [ ] Editable extraction review
- [ ] Import confirmation

### Phase 5 — Inventory Intelligence
- [ ] Reorder calculation
- [ ] Fast-moving products
- [ ] Slow-moving products
- [ ] Old/dead stock
- [ ] Sales velocity
- [ ] Stock-out estimation
- [ ] Inventory valuation
- [ ] Expiry tracking

### Phase 6 — Business Management
- [ ] Purchase tracking
- [ ] Sales tracking
- [ ] Supplier management
- [ ] Purchase planner
- [ ] Gross-margin estimates
- [ ] Daily/weekly/monthly summaries
- [ ] Reminders and notifications

### Phase 7 — Advanced Insights
- [ ] Demand forecasting
- [ ] Seasonal/festival trends
- [ ] Optional anonymized regional trends
- [ ] Multi-device support
- [ ] Owner dashboard

## Project Vision

GigPoint is not intended to be only a digital stock register.

The long-term goal is a **low-cost multilingual business assistant for small merchants** that helps them manage stock, understand sales, plan purchases, preserve historical records, and make better day-to-day inventory decisions while remaining useful even when internet connectivity is unavailable.
