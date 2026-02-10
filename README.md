# BIST Stock Picker Microservice

A simple Spring Boot microservice that exposes one GET endpoint to suggest a **high-risk** BIST portfolio of 10 stocks.

## What it does

1. Fetches market data from Yahoo Finance for candidate BIST symbols.
2. Calculates performance over:
   - 1 month
   - 3 months
   - 6 months
3. Fetches industry information for each symbol.
4. Sends this dataset to Groq with a prompt to produce a 10-stock high-risk portfolio with industry diversity.
5. If Groq is unavailable or not configured, it returns a local fallback selection strategy.

## Endpoint

```http
GET /api/v1/stocks/suggestions/high-risk
```

## Run

```bash
mvn spring-boot:run
```

Optional env var:

```bash
export GROQ_API_KEY=your_key_here
```

Then call:

```bash
curl http://localhost:8080/api/v1/stocks/suggestions/high-risk
```
