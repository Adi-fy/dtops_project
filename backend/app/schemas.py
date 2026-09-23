from pydantic import BaseModel
from typing import List, Optional
from enum import Enum

class TransactionStatus(str, Enum):
    PENDING = "PENDING"
    COMMITTED = "COMMITTED"
    REJECTED = "REJECTED"

class RegisterRequest(BaseModel):
    device_id: str
    public_key_pem: str

class TransactionSchema(BaseModel):
    tx_id: str
    sender_id: str
    receiver_id: str
    amount: float
    sequence_counter: int
    nonce: str
    timestamp: int
    signature: str

    class Config:
        from_attributes = True

class SyncRequest(BaseModel):
    transactions: List[TransactionSchema]

class SyncReportItem(BaseModel):
    tx_id: str
    status: str
    message: Optional[str] = None

class SyncResponse(BaseModel):
    report: List[SyncReportItem]

