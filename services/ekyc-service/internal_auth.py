import hmac
import os
from typing import Optional

from fastapi import Header, HTTPException, status


INTERNAL_TOKEN = os.getenv("EKYC_INTERNAL_TOKEN", "")


def require_internal_token(x_internal_token: Optional[str] = Header(default=None)) -> None:
    if not INTERNAL_TOKEN:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Internal authentication is not configured.",
        )
    if x_internal_token is None or not hmac.compare_digest(x_internal_token, INTERNAL_TOKEN):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid internal credential.",
        )
