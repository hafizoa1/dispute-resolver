#!/usr/bin/env python3
"""Send a signed SYNTHETIC purchase event to our local receiver, not to Moniepoint."""
import argparse
import base64
import hashlib
import hmac
import json
import os
import time
import urllib.error
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://localhost:8080/api/webhooks/moniepoint")
    parser.add_argument("--webhook-id", default="synthetic-purchase-1",
                        help="Repeat the same ID to check deduplication")
    parser.add_argument("--transaction-reference", default="synthetic-txn-1")
    parser.add_argument("--merchant-reference", default="synthetic-booking-1")
    parser.add_argument("--amount", type=int, default=5000000, help="Minor units, no currency/unit conversion done")
    parser.add_argument("--transaction-status", default="APPROVED")
    parser.add_argument("--tamper", action="store_true", help="Change the amount after signing; expect 401")
    args = parser.parse_args()
    secret = os.environ.get("MONIEPOINT_WEBHOOK_SECRET", "")
    business_id = os.environ.get("MONIEPOINT_BUSINESS_ID", "")
    if not secret or not business_id.isdecimal() or int(business_id) <= 0:
        parser.error("Set MONIEPOINT_WEBHOOK_SECRET and a positive MONIEPOINT_BUSINESS_ID")

    # Shape follows public documentation. Values are invented, not a captured sandbox transaction.
    payload = {
        "eventId": args.webhook_id,
        "eventType": "V1_POS_PURCHASE_TRANSACTION",
        "data": {
            "businessId": int(business_id),
            "amount": args.amount,
            "transactionReference": args.transaction_reference,
            "merchantReference": args.merchant_reference,
            "transactionStatus": args.transaction_status,
            "responseCode": "00",
        },
    }
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    timestamp = str(int(time.time() * 1000))
    signed = (args.webhook_id + "__" + timestamp + "__").encode() + body
    signature = base64.b64encode(hmac.new(secret.encode(), signed, hashlib.sha256).digest()).decode()
    if args.tamper:
        body = body.replace(str(args.amount).encode(), str(args.amount + 4000000).encode())
    request = urllib.request.Request(args.url, data=body, method="POST", headers={
        "Content-Type": "application/json",
        "moniepoint-webhook-id": args.webhook_id,
        "moniepoint-webhook-timestamp": timestamp,
        "moniepoint-webhook-signature": signature,
    })
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            print("Synthetic event:", response.status)
    except urllib.error.HTTPError as error:
        print("Synthetic event:", error.code, error.read().decode())
        raise SystemExit(1)


if __name__ == "__main__":
    main()
