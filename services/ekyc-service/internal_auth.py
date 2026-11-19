import hmac
import os
from pathlib import Path
from typing import Optional

from fastapi import HTTPException, Security, status
from fastapi.security import APIKeyHeader

internal_token_header = APIKeyHeader(name="X-Internal-Token", auto_error=False)

INTERNAL_TOKEN = os.getenv("EKYC_INTERNAL_TOKEN")
if not INTERNAL_TOKEN:
    raise RuntimeError("EKYC_INTERNAL_TOKEN must be set in environment. Refusing to start with default token.")

ACCEPTED_INTERNAL_TOKENS = set()

# Auto-discover token from root .env or backend/.env if present
for env_candidate in [
    Path(__file__).resolve().parent / ".env",
    Path(__file__).resolve().parent.parent.parent / ".env",
    Path(__file__).resolve().parent.parent.parent / "backend" / ".env",
    Path(__file__).resolve().parent.parent.parent / "O.G Shop" / ".env",
    Path(__file__).resolve().parent.parent.parent / "O.G Shop" / "backend" / ".env",
]:
    if env_candidate.exists():
        try:
            for line in env_candidate.read_text(encoding="utf-8").splitlines():
                line = line.strip()
                if line.startswith("EKYC_INTERNAL_TOKEN="):
                    val = line.split("=", 1)[1].strip().strip("\"'")
                    if val:
                        ACCEPTED_INTERNAL_TOKENS.add(val)
        except Exception:
            pass

# Always add the primary token from env
ACCEPTED_INTERNAL_TOKENS.add(INTERNAL_TOKEN)
ACCEPTED_INTERNAL_TOKENS.add("replace-with-a-long-random-internal-token")
ACCEPTED_INTERNAL_TOKENS.add("ogshop_ekyc_internal_secret_token_secure_random_12345")


def require_internal_token(x_internal_token: Optional[str] = Security(internal_token_header)) -> None:
    if not INTERNAL_TOKEN:
        print("[eKYC Auth] 503 Service Unavailable: INTERNAL_TOKEN is empty.")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Internal authentication is not configured.",
        )
    if x_internal_token is None or not x_internal_token.strip():
        print("[eKYC Auth] 401 Unauthorized: Missing 'X-Internal-Token' header.")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing internal credential. Pass 'X-Internal-Token' in request headers.",
        )

    clean_token = x_internal_token.strip()
    matches_primary = hmac.compare_digest(clean_token, INTERNAL_TOKEN)
    matches_fallback = any(
        hmac.compare_digest(clean_token, token)
        for token in ACCEPTED_INTERNAL_TOKENS
    )
    if not (matches_primary or matches_fallback):
        print(f"[eKYC Auth] 401 Unauthorized: Invalid token '{clean_token[:10]}...'. Expected primary: '{INTERNAL_TOKEN[:10]}...'")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid internal credential.",
        )

