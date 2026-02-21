# ActualBudget Widget

Display budget and account information from a self-hosted [Actual Budget](https://actualbudget.org/) instance.

## Details

| Property | Value |
|----------|-------|
| **Type** | `widget` |
| **Allowed Locations** | `sidebar` |
| **Widget Name** | `actualbudget` |

## Configuration

Add `"actualbudget"` to `layout.sidebar` and configure it under `widgets.actualbudget`:

```json
{
  "layout": {
    "sidebar": ["calendar", "actualbudget", "updater"]
  },
  "widgets": {
    "actualbudget": {
      "actualHttpApiUrl": "http://192.168.1.10:5006",
      "syncInterval": 60,
      "budgets": [
        {
          "syncId": "your-sync-id",
          "accounts": [
            {
              "id": "account-uuid",
              "name": "Current Account",
              "icon": "https://example.com/bank-logo.png"
            }
          ]
        }
      ]
    }
  }
}
```

## Options Reference

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `actualHttpApiUrl` | URL or `""` | `""` | Base URL of the Actual Budget HTTP API |
| `syncInterval` | number | `60` | Sync interval in seconds |
| `budgets` | array | `[]` | List of budget objects to display |
| `budgets[].syncId` | string | — | Actual Budget sync ID |
| `budgets[].accounts` | array | `[]` | List of accounts within this budget |
| `budgets[].accounts[].id` | string | `""` | Account UUID |
| `budgets[].accounts[].name` | string | `"Account"` | Display name for the account |
| `budgets[].accounts[].icon` | URL or `""` | `""` | URL of an icon/logo image |
