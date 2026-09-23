import enum
from sqlalchemy import Column, String, Float, Integer, Enum, BigInteger
from .database import Base

class TransactionStatus(enum.Enum):
    PENDING = "PENDING"
    COMMITTED = "COMMITTED"
    REJECTED = "REJECTED"

class User(Base):
    __tablename__ = "users"

    id = Column(String, primary_key=True, index=True)
    public_key_pem = Column(String, nullable=False)
    online_balance = Column(Float, default=0.0)

class Transaction(Base):
    __tablename__ = "transactions"

    tx_id = Column(String, primary_key=True, index=True)
    sender_id = Column(String, index=True, nullable=False)
    receiver_id = Column(String, index=True, nullable=False)
    amount = Column(Float, nullable=False)
    sequence_counter = Column(Integer, nullable=False)
    nonce = Column(String, unique=True, index=True, nullable=False)
    timestamp = Column(BigInteger, nullable=False)
    signature = Column(String, nullable=False)
    status = Column(Enum(TransactionStatus), default=TransactionStatus.PENDING)

class ConflictLog(Base):
    __tablename__ = "conflict_logs"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    offending_sender_id = Column(String, index=True)
    duplicate_sequence = Column(Integer)
    attempted_tx_id = Column(String)
    resolved_action = Column(String)

