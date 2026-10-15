import base64
from sqlalchemy.orm import Session
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.serialization import load_pem_public_key
from cryptography.exceptions import InvalidSignature
from . import models, schemas

def verify_offline_transaction(tx: schemas.TransactionSchema, db: Session) -> tuple[bool, str]:
    # 1. Signature Validity
    sender = db.query(models.User).filter(models.User.id == tx.sender_id).first()
    if not sender:
        return False, "Sender not found"

    payload = f"{tx.tx_id}|{tx.sender_id}|{tx.receiver_id}|{tx.amount}|{tx.sequence_counter}|{tx.nonce}|{tx.timestamp}"
    
    try:
        public_key = load_pem_public_key(sender.public_key_pem.encode('utf-8'))
        signature_bytes = base64.b64decode(tx.signature)
        public_key.verify(
            signature_bytes,
            payload.encode('utf-8'),
            ec.ECDSA(hashes.SHA256())
        )
    except (ValueError, InvalidSignature, TypeError):
        return False, "Invalid signature"

    # 2. Solvency Check (Sender must have sufficient balance for the transaction)
    if sender.online_balance < tx.amount:
        return False, "Insufficient global balance"

    # 3. Nonce Uniqueness (Replay Attack Prevention)
    existing_nonce = db.query(models.Transaction).filter(models.Transaction.nonce == tx.nonce).first()
    if existing_nonce:
        return False, "Replay Attack: Nonce already exists"

    # 4. Monotonic Counter Check (Double-Spend Detection)
    highest_tx = db.query(models.Transaction).filter(
        models.Transaction.sender_id == tx.sender_id,
        models.Transaction.status == models.TransactionStatus.COMMITTED
    ).order_by(models.Transaction.sequence_counter.desc()).first()
    
    highest_counter = highest_tx.sequence_counter if highest_tx else 0
    
    if tx.sequence_counter <= highest_counter:
        # Double Spend detected! Log to ConflictLog and reject
        conflict = models.ConflictLog(
            offending_sender_id=tx.sender_id,
            duplicate_sequence=tx.sequence_counter,
            attempted_tx_id=tx.tx_id,
            resolved_action="REJECTED_DOUBLE_SPEND"
        )
        db.add(conflict)
        db.commit()
        return False, "REJECTED_DOUBLE_SPEND"
    
    return True, "Valid"

